package com.callgalli.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import android.os.SystemClock
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import com.callgalli.data.CallOutcome
import com.callgalli.data.CallRepository
import com.callgalli.data.ContactsRepository
import com.callgalli.data.SettingsStore
import com.callgalli.rules.CallClock
import com.callgalli.rules.CallDecision
import com.callgalli.rules.Direction
import com.callgalli.rules.PhoneNumbers
import com.callgalli.rules.TimerBand
import com.callgalli.rules.TimerDisplay
import com.callgalli.rules.UntimedReason
import com.callgalli.rules.WarningTracker
import com.callgalli.system.Haptics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screens, notification and overlay show for the call in progress. */
data class LiveCall(
    val number: String,
    val rawNumber: String,
    val name: String?,
    val direction: Direction,
    val ringing: Boolean,
    val timerRunning: Boolean,
    val elapsedMillis: Long,
    val limitMillis: Long?,
    val remainingMillis: Long?,
    val band: TimerBand?,
    val exemptReason: UntimedReason?,
    /** Outgoing call to a number that already used up today's calls. */
    val overLimitWarning: Boolean,
) {
    val label: String get() = name ?: rawNumber.ifBlank { number }.ifBlank { "?" }
}

data class WarningBanner(val label: String, val secondsLeft: Int)

data class InCallState(val call: LiveCall? = null, val banner: WarningBanner? = null) {
    val hasCall: Boolean get() = call != null
}

/**
 * Watches calls made with the phone's own dialer (Google Phone, Samsung Phone, …).
 *
 * CallGalli is NOT the phone app. It learns about calls from Android's phone-state
 * broadcasts (RINGING → OFFHOOK → IDLE), gets the number from the call log permission
 * or the outgoing-call broadcast, and ends a call with [TelecomManager.endCall].
 *
 * Limits of this mode (Android does not tell normal apps more):
 * - Outgoing calls: the timer starts when you dial, not when the other person answers.
 * - Hold and call waiting are not visible, so the timer does not pause on hold.
 * - A saved contact at Stage 3 may ring for a split second before it is rejected.
 */
class CallMonitor(
    context: Context,
    private val repository: CallRepository,
    private val contacts: ContactsRepository,
    private val settings: SettingsStore,
    private val notifications: CallNotifications,
) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Sessions started before this moment belong to an earlier process (e.g. before a reboot). */
    val processStartedAt: Long = System.currentTimeMillis()

    private val _state = MutableStateFlow(InCallState())
    val state: StateFlow<InCallState> = _state.asStateFlow()

    var overlay: TimerOverlay? = null

    private var phoneState = TelephonyManager.EXTRA_STATE_IDLE
    private var ringingRaw: String? = null
    private var ringHandled = false
    private var outgoingRaw: String? = null
    private var outgoingAt = 0L
    private var active: Active? = null
    private var tickJob: Job? = null
    private var bannerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var initialised = false

    /** Numbers the call screening service already rejected silently (number → time). */
    private val screenedBlocked = mutableMapOf<String, Long>()

    private class Active(val number: String, val raw: String, val direction: Direction) {
        var name: String? = null
        val session = CompletableDeferred<Long?>()
        val connectedAtElapsed = SystemClock.elapsedRealtime()
        var clock: CallClock? = null
        var warnings: WarningTracker? = null
        var exemptReason: UntimedReason? = null
        var endedByLimit = false
        var overLimitWarning = false
        var lastHeartbeatElapsed = 0L
    }

    // ---- Inputs ---------------------------------------------------------------------------

    /** NEW_OUTGOING_CALL: the number the user just dialled in their phone app. */
    fun onOutgoingNumber(raw: String?) {
        if (raw.isNullOrBlank() || !settings.current.enabled) return
        outgoingRaw = raw
        outgoingAt = System.currentTimeMillis()
    }

    /** Call screening already rejected this number before it rang (saved in the Blocked Log there). */
    fun onScreenedBlocked(number: String) {
        screenedBlocked[number] = System.currentTimeMillis()
    }

    /** PHONE_STATE broadcast. It can arrive twice per change (with and without the number). */
    fun onPhoneState(newState: String?, incomingRaw: String?) {
        newState ?: return
        if (!settings.current.enabled) return
        val previous = phoneState
        phoneState = newState
        when (newState) {
            TelephonyManager.EXTRA_STATE_RINGING -> onRinging(previous, incomingRaw)
            TelephonyManager.EXTRA_STATE_OFFHOOK -> onOffHook(previous)
            TelephonyManager.EXTRA_STATE_IDLE -> onIdle()
        }
        refresh()
    }

    private fun onRinging(previous: String, incomingRaw: String?) {
        if (previous != TelephonyManager.EXTRA_STATE_RINGING) {
            ringHandled = false
            ringingRaw = null
        }
        if (!incomingRaw.isNullOrBlank()) ringingRaw = incomingRaw
        val raw = ringingRaw ?: return // wait for the copy of the broadcast that has the number
        if (ringHandled) return
        ringHandled = true
        val number = PhoneNumbers.normalize(raw)
        scope.launch {
            val recentlyScreened = screenedBlocked[number]?.let { System.currentTimeMillis() - it < 15_000 } == true
            if (recentlyScreened) return@launch
            if (repository.decideIncoming(number) == CallDecision.Reject) {
                // Stage 3: reject it (also works for a waiting call during another call).
                if (CallEnder.end(app)) {
                    repository.recordBlocked(number, withContext(Dispatchers.IO) { contacts.lookupName(raw) })
                }
            }
        }
    }

    private fun onOffHook(previous: String) {
        if (active != null) return // call waiting / second call: keep timing the first call
        val direction: Direction
        val raw: String
        if (previous == TelephonyManager.EXTRA_STATE_RINGING) {
            direction = Direction.INCOMING
            raw = ringingRaw.orEmpty()
        } else {
            direction = Direction.OUTGOING
            raw = outgoingRaw?.takeIf { System.currentTimeMillis() - outgoingAt < 60_000 }.orEmpty()
        }
        outgoingRaw = null
        startCall(Active(PhoneNumbers.normalize(raw), raw, direction))
    }

    /**
     * Switched off: stop everything now. A call in progress is NOT ended; it is just no
     * longer timed (its talk time so far is saved).
     */
    fun stopAll() {
        phoneState = TelephonyManager.EXTRA_STATE_IDLE
        outgoingRaw = null
        onIdle()
    }

    private fun onIdle() {
        ringingRaw = null
        ringHandled = false
        active?.let { finish(it) }
        active = null
        stopTicking()
        releaseWakeLock()
        bannerJob?.cancel()
        _state.value = InCallState()
        overlay?.hide()
    }

    // ---- Call lifecycle -------------------------------------------------------------------

    private fun startCall(a: Active) {
        active = a
        startTicking()
        scope.launch {
            a.name = withContext(Dispatchers.IO) { contacts.lookupName(a.raw) }
            if (a.direction == Direction.OUTGOING && repository.decideOutgoing(a.number) is CallDecision.ConfirmNeeded) {
                a.overLimitWarning = true
                notifications.showOverLimitOutgoing(a.name ?: a.raw)
            }
            val sessionId = repository.startSession(a.number, a.name, a.direction)
            a.session.complete(sessionId)
            val result = repository.onConnected(sessionId, a.number, a.name)
            a.exemptReason = result.reason
            installClock(a, CallClock(result.limitMillis).also { it.start(a.connectedAtElapsed) })
        }
    }

    private fun installClock(a: Active, clock: CallClock) {
        a.clock = clock
        if (clock.limitMillis != null) {
            a.warnings = WarningTracker(settings.current.rules.warningSeconds)
            acquireWakeLock()
        }
        a.lastHeartbeatElapsed = SystemClock.elapsedRealtime()
        refresh()
    }

    private fun finish(a: Active) {
        val now = SystemClock.elapsedRealtime()
        a.clock?.pause(now)
        val talked = a.clock?.elapsed(now) ?: (now - a.connectedAtElapsed)
        val outcome = if (a.endedByLimit) CallOutcome.ENDED_BY_LIMIT else CallOutcome.ENDED
        if (a.endedByLimit) {
            val minutes = ((a.clock?.limitMillis ?: 0L) / 60_000L).toInt()
            notifications.showEndedByLimit(a.name ?: a.raw.ifBlank { a.number }, minutes)
        }
        notifications.cancelOverLimitOutgoing()
        scope.launch {
            val id = a.session.await() ?: return@launch
            repository.endSession(id, talked, outcome)
        }
    }

    /**
     * Called when the monitor (re)starts. If a call is already going on, e.g. Android
     * restarted CallGalli mid-call, continue its saved timer. Otherwise close sessions
     * left open by a reboot.
     */
    fun initialise() {
        if (initialised) return
        initialised = true
        scope.launch {
            if (settings.current.enabled && isInCall()) {
                val saved = repository.findLatestOpenConnectedSession()
                if (saved != null && active == null) {
                    phoneState = TelephonyManager.EXTRA_STATE_OFFHOOK
                    val a = Active(saved.number, saved.number, runCatching { Direction.valueOf(saved.direction) }.getOrDefault(Direction.INCOMING))
                    a.name = saved.displayName
                    a.session.complete(saved.id)
                    active = a
                    val missed = (System.currentTimeMillis() - saved.lastHeartbeatAt).coerceAtLeast(0)
                    a.exemptReason = if (saved.limitMillis == null) repository.exemptReason(saved.number) else null
                    installClock(a, CallClock(saved.limitMillis, saved.activeMillis + missed).also { it.start(SystemClock.elapsedRealtime()) })
                    startTicking()
                    return@launch
                }
            }
            repository.closeOrphanSessions(liveSessionIds(), processStartedAt)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun liveSessionIds(): Set<Long> =
        active?.session?.takeIf { it.isCompleted }?.getCompleted()?.let { setOf(it) } ?: emptySet()

    private fun isInCall(): Boolean {
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return false
        return runCatching { app.getSystemService(TelecomManager::class.java).isInCall }.getOrDefault(false)
    }

    // ---- Ticking --------------------------------------------------------------------------

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                tick()
                delay(TICK_MILLIS)
            }
        }
    }

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun tick() {
        val a = active ?: return
        val clock = a.clock ?: return
        val now = SystemClock.elapsedRealtime()
        val remaining = clock.remaining(now)
        if (remaining != null) {
            a.warnings?.check(remaining)?.let { if (remaining > 0) warn(a, it) }
            if (clock.isExpired(now) && !a.endedByLimit) {
                a.endedByLimit = true
                Log.i(TAG, "Limit reached, ending call")
                if (!CallEnder.end(app)) {
                    a.endedByLimit = false
                    notifications.showCouldNotEnd(a.name ?: a.raw)
                }
            }
        }
        if (now - a.lastHeartbeatElapsed >= HEARTBEAT_MILLIS) {
            a.lastHeartbeatElapsed = now
            val elapsed = clock.elapsed(now)
            scope.launch { a.session.await()?.let { repository.heartbeat(it, elapsed) } }
        }
        refresh()
    }

    private fun warn(a: Active, secondsLeft: Int) {
        val label = a.name ?: a.raw.ifBlank { a.number }
        Haptics.shortWarning(app)
        notifications.showWarning(label, secondsLeft)
        bannerJob?.cancel()
        _state.value = _state.value.copy(banner = WarningBanner(label, secondsLeft))
        bannerJob = scope.launch {
            delay(BANNER_MILLIS)
            _state.value = _state.value.copy(banner = null)
            overlay?.update(_state.value)
        }
    }

    private fun refresh() {
        val a = active
        val ringing = phoneState == TelephonyManager.EXTRA_STATE_RINGING && a == null
        val call = when {
            a != null -> {
                val now = SystemClock.elapsedRealtime()
                val remaining = a.clock?.remaining(now)
                LiveCall(
                    number = a.number, rawNumber = a.raw, name = a.name, direction = a.direction, ringing = false,
                    timerRunning = a.clock?.isRunning == true,
                    elapsedMillis = a.clock?.elapsed(now) ?: (now - a.connectedAtElapsed),
                    limitMillis = a.clock?.limitMillis,
                    remainingMillis = remaining,
                    band = remaining?.let { TimerDisplay.band(it) },
                    exemptReason = a.exemptReason,
                    overLimitWarning = a.overLimitWarning,
                )
            }
            ringing -> ringingRaw.orEmpty().let { raw ->
                LiveCall(PhoneNumbers.normalize(raw), raw, null, Direction.INCOMING, true, false, 0, null, null, null, null, false)
            }
            else -> null
        }
        _state.value = _state.value.copy(call = call)
        overlay?.update(_state.value)
    }

    // ---- User actions ---------------------------------------------------------------------

    fun hangUp() {
        CallEnder.end(app)
    }

    // ---- Keep the CPU awake while a timed call runs (screen off) --------------------------

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = app.getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CallGalli:callTimer").apply {
            setReferenceCounted(false)
            acquire(MAX_WAKE_LOCK_MILLIS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private companion object {
        const val TAG = "CallMonitor"
        const val TICK_MILLIS = 500L
        const val HEARTBEAT_MILLIS = 5_000L
        const val BANNER_MILLIS = 6_000L
        const val MAX_WAKE_LOCK_MILLIS = 3L * 60 * 60 * 1000
    }
}

/** Ends (or rejects) the current call without being the phone app. */
object CallEnder {
    fun canEnd(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED

    /**
     * Rejects a ringing call, otherwise ends the call in progress. Android never lets this end
     * an emergency call (and CallGalli never times one). Deprecated since Android 10 but it is
     * the only way for an app that is not the phone app.
     */
    fun end(context: Context): Boolean {
        if (context.checkSelfPermission(Manifest.permission.ANSWER_PHONE_CALLS) != PackageManager.PERMISSION_GRANTED) return false
        return try {
            @Suppress("DEPRECATION")
            context.getSystemService(TelecomManager::class.java).endCall()
        } catch (e: Exception) {
            Log.w("CallEnder", "endCall failed", e)
            false
        }
    }
}
