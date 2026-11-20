package com.callgalli.data

import com.callgalli.rules.CallDecision
import com.callgalli.rules.DayKeys
import com.callgalli.rules.Direction
import com.callgalli.rules.EmergencyNumbers
import com.callgalli.rules.NumberDay
import com.callgalli.rules.RulesEngine
import com.callgalli.rules.Stage
import com.callgalli.rules.UntimedReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/** Totals for the Home "Today" card. */
data class TodaySummary(
    val callsTaken: Int = 0,
    val talkMillis: Long = 0,
    val callsBlocked: Int = 0,
    val minutesSaved: Int = 0,
)

/** A number used today, for the Home list. */
data class NumberToday(
    val number: String,
    val displayName: String?,
    val stage: Stage,
    val progress: Float,
    val answeredCalls: Int,
    val blockedCalls: Int,
    val talkMillis: Long,
)

/**
 * The single place that reads/writes call data. Glues the pure [RulesEngine] to Room.
 * Every read-modify-write of usage goes through [lock] so two calls ending at the
 * same moment cannot overwrite each other's counts.
 */
class CallRepository(
    private val db: CallGalliDatabase,
    private val settingsStore: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val lock = Mutex()

    val engine: RulesEngine get() = RulesEngine(settingsStore.current.rules)

    fun todayKey(at: Long = now()): String = DayKeys.of(at, zone(), settingsStore.current.rules.resetMinuteOfDay)

    /** Emits today's key and re-emits when the reset time passes, so screens roll over at midnight. */
    val todayKeyFlow: Flow<String> = flow {
        while (true) {
            emit(todayKey())
            delay(30_000)
        }
    }.distinctUntilChanged()

    // ---- Decisions ------------------------------------------------------------------------

    suspend fun isAlwaysAllowed(number: String): Boolean = db.rules().get(number)?.alwaysAllowed == true

    suspend fun usageToday(number: String): NumberDay? = db.usage().get(todayKey(), number)?.toNumberDay()

    suspend fun decideIncoming(number: String): CallDecision =
        engine.decideIncoming(number, usageToday(number), isAlwaysAllowed(number), todayKey())

    suspend fun decideOutgoing(number: String): CallDecision =
        engine.decideOutgoing(number, usageToday(number), isAlwaysAllowed(number), todayKey())

    suspend fun stageToday(number: String): Stage = engine.stageOf(usageToday(number), todayKey())

    // ---- Call lifecycle -------------------------------------------------------------------

    /** A call appeared (ringing or dialing). */
    suspend fun startSession(number: String, displayName: String?, direction: Direction): Long {
        val at = now()
        return db.sessions().insert(
            CallSessionEntity(number = number, displayName = displayName, direction = direction.name, dayKey = todayKey(at), startedAt = at)
        )
    }

    /** The call was picked up. Uses up a stage and returns the limit (null = untimed). */
    suspend fun onConnected(sessionId: Long, number: String, displayName: String?): RulesEngine.Connected = lock.withLock {
        val at = now()
        val today = todayKey(at)
        val result = engine.onConnected(number, db.usage().get(today, number)?.toNumberDay(), isAlwaysAllowed(number), today)
        if (result.limitMillis != null) {
            db.usage().upsert(DailyUsageEntity.from(result.usage, displayName, at))
        }
        db.sessions().get(sessionId)?.let {
            db.sessions().update(it.copy(dayKey = today, connectedAt = at, limitMillis = result.limitMillis, lastHeartbeatAt = at))
        }
        result
    }

    /** The most recent answered call that never got an end time (CallGalli was restarted mid-call). */
    suspend fun findLatestOpenConnectedSession(): CallSessionEntity? =
        db.sessions().openSessions().filter { it.connectedAt != null }.maxByOrNull { it.startedAt }

    /** An answered call from an earlier app process that never got an end time. */
    suspend fun findResumableSession(number: String): CallSessionEntity? =
        db.sessions().openSessions().filter { it.number == number && it.connectedAt != null }.maxByOrNull { it.startedAt }

    suspend fun exemptReason(number: String): UntimedReason = when {
        EmergencyNumbers.isEmergency(number) -> UntimedReason.EMERGENCY
        isAlwaysAllowed(number) -> UntimedReason.ALWAYS_ALLOWED
        else -> UntimedReason.UNKNOWN_NUMBER
    }

    suspend fun heartbeat(sessionId: Long, activeMillis: Long) {
        db.sessions().heartbeat(sessionId, activeMillis, now())
    }

    suspend fun endSession(sessionId: Long, activeMillis: Long, outcome: CallOutcome) = lock.withLock {
        val session = db.sessions().get(sessionId) ?: return@withLock
        if (session.endedAt != null) return@withLock
        val at = now()
        db.sessions().update(session.copy(endedAt = at, activeMillis = activeMillis, outcome = outcome.name, lastHeartbeatAt = at))
        addTalkTime(session, activeMillis, at)
    }

    private suspend fun addTalkTime(session: CallSessionEntity, activeMillis: Long, at: Long) {
        if (session.connectedAt == null || session.limitMillis == null) return // exempt or never answered
        val usage = db.usage().get(session.dayKey, session.number) ?: return
        val updated = engine.onEnded(usage.toNumberDay(), activeMillis)
        db.usage().upsert(DailyUsageEntity.from(updated, usage.displayName ?: session.displayName, at))
    }

    /** An incoming call was rejected because the number is at Stage 3. */
    suspend fun recordBlocked(number: String, displayName: String?) = lock.withLock {
        val at = now()
        val today = todayKey(at)
        val updated = engine.onBlocked(number, db.usage().get(today, number)?.toNumberDay(), today)
        val name = displayName ?: db.usage().get(today, number)?.displayName
        db.usage().upsert(DailyUsageEntity.from(updated, name, at))
        db.sessions().insert(
            CallSessionEntity(
                number = number, displayName = name, direction = Direction.INCOMING.name, dayKey = today,
                startedAt = at, endedAt = at, outcome = CallOutcome.BLOCKED_BY_LIMIT.name,
            )
        )
    }

    /**
     * Closes sessions left open by a reboot or a killed process. Talk time is counted up to the
     * last heartbeat. [liveSessionIds] are calls that are still really going on and must be kept;
     * only sessions started before [startedBefore] (this app process's start) are touched, so a
     * call that is just being set up is never closed by mistake.
     */
    suspend fun closeOrphanSessions(liveSessionIds: Set<Long>, startedBefore: Long) = lock.withLock {
        db.sessions().openSessions().filter { it.id !in liveSessionIds && it.startedAt < startedBefore }.forEach { session ->
            val outcome = if (session.connectedAt == null) CallOutcome.NOT_CONNECTED else CallOutcome.INTERRUPTED
            db.sessions().update(session.copy(endedAt = session.lastHeartbeatAt, outcome = outcome.name))
            addTalkTime(session, session.activeMillis, session.lastHeartbeatAt)
        }
    }

    // ---- User actions ---------------------------------------------------------------------

    suspend fun setAlwaysAllowed(number: String, displayName: String?, allowed: Boolean) {
        if (number.isEmpty()) return
        if (allowed) db.rules().upsert(RuleEntity(number, displayName, alwaysAllowed = true, updatedAt = now()))
        else db.rules().delete(number)
    }

    /** Long-press "reset": the number is Fresh again for today. Its call history is kept. */
    suspend fun resetNumberToday(number: String) = lock.withLock {
        db.usage().delete(todayKey(), number)
    }

    suspend fun clearAllData() = lock.withLock {
        db.sessions().clear()
        db.usage().clear()
        db.rules().clear()
        settingsStore.resetToDefaults()
    }

    // ---- Screens --------------------------------------------------------------------------

    val alwaysAllowed: Flow<List<RuleEntity>> = db.rules().observeAlwaysAllowed()

    @OptIn(ExperimentalCoroutinesApi::class)
    val usageToday: Flow<List<DailyUsageEntity>> = todayKeyFlow.flatMapLatest { db.usage().observeDay(it) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val todaySummary: Flow<TodaySummary> = combine(todayKeyFlow, settingsStore.settings) { key, s -> key to s }
        .flatMapLatest { (key, s) ->
            db.sessions().observeDay(key).map { sessions ->
                val taken = sessions.count { it.connectedAt != null }
                val talk = sessions.filter { it.connectedAt != null }.sumOf { it.activeMillis }
                val blocked = sessions.count { it.outcome == CallOutcome.BLOCKED_BY_LIMIT.name }
                // Each blocked call could have run for up to the repeat-call limit.
                TodaySummary(taken, talk, blocked, blocked * s.rules.repeatCallMinutes)
            }
        }

    val numbersToday: Flow<List<NumberToday>> = combine(usageToday, todayKeyFlow, settingsStore.settings) { rows, key, s ->
        val e = RulesEngine(s.rules)
        rows.map { row ->
            val day = row.toNumberDay()
            NumberToday(row.number, row.displayName, e.stageOf(day, key), e.progress(day, key), row.answeredCalls, row.blockedCalls, row.talkMillis)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val blockedToday: Flow<List<BlockedSummary>> = todayKeyFlow.flatMapLatest { db.sessions().observeBlockedSummary(it) }

    /** Last 7 days for one number, newest first, with empty days filled in. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun history(number: String): Flow<List<DailyUsageEntity>> = todayKeyFlow.flatMapLatest { key ->
        val days = DayKeys.lastDays(key, 7)
        db.usage().observeHistory(number, days.first()).map { rows ->
            val byDay = rows.associateBy { it.dayKey }
            days.reversed().map { day -> byDay[day] ?: DailyUsageEntity(day, number, null, 0, 0, 0, 0) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun recentSessions(number: String): Flow<List<CallSessionEntity>> = todayKeyFlow.flatMapLatest { key ->
        db.sessions().observeNumberSessions(number, DayKeys.lastDays(key, 7).first())
    }
}
