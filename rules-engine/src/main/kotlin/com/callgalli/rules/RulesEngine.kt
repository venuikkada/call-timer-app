package com.callgalli.rules

/** Where a number stands today. */
enum class Stage {
    /** No answered call yet today. Next call gets the first-call limit (10 min). */
    FRESH,
    /** One answered call today. Next call gets the repeat-call limit (5 min). */
    LIMITED,
    /** Two answered calls today. Incoming calls are rejected until the reset. */
    BLOCKED,
}

/** One number's counts for one day. Mirrors the DailyUsage table. */
data class NumberDay(
    val number: String,
    val dayKey: String,
    val answeredCalls: Int = 0,
    val blockedCalls: Int = 0,
    val talkMillis: Long = 0,
)

enum class UntimedReason { EMERGENCY, ALWAYS_ALLOWED, UNKNOWN_NUMBER }

enum class Direction { INCOMING, OUTGOING }

/** What to do with a call. */
sealed interface CallDecision {
    /** Let it ring/connect, never time it. */
    data class Untimed(val reason: UntimedReason) : CallDecision

    /** Let it connect and cut it after [limitMillis] of active talk time. */
    data class Timed(val limitMillis: Long, val stage: Stage) : CallDecision

    /** Incoming call from a Stage 3 number: reject before it rings. */
    data object Reject : CallDecision

    /** Outgoing call to a Stage 3 number: warn the user first. If they go ahead it is timed with [limitMillis]. */
    data class ConfirmNeeded(val limitMillis: Long) : CallDecision
}

/**
 * The whole rule book, as pure functions. No Android, no database, no clock:
 * callers pass in today's key and the stored [NumberDay], and store what comes back.
 */
class RulesEngine(val settings: RuleSettings = RuleSettings()) {

    /** Counts from an earlier day do not apply today (this is the midnight reset). */
    fun usageForToday(usage: NumberDay?, number: String, todayKey: String): NumberDay =
        if (usage != null && usage.dayKey == todayKey && usage.number == number) usage
        else NumberDay(number = number, dayKey = todayKey)

    fun stageOf(usage: NumberDay?, todayKey: String): Stage {
        if (usage == null || usage.dayKey != todayKey) return Stage.FRESH
        return when (usage.answeredCalls) {
            0 -> Stage.FRESH
            1 -> Stage.LIMITED
            else -> Stage.BLOCKED
        }
    }

    fun limitFor(stage: Stage): Long = when (stage) {
        Stage.FRESH -> settings.firstCallMillis
        // A Stage 3 call can only connect if the user confirmed an outgoing call.
        // It keeps the shorter repeat-call limit.
        Stage.LIMITED, Stage.BLOCKED -> settings.repeatCallMillis
    }

    private fun exemption(number: String, alwaysAllowed: Boolean): UntimedReason? = when {
        EmergencyNumbers.isEmergency(number) -> UntimedReason.EMERGENCY
        alwaysAllowed -> UntimedReason.ALWAYS_ALLOWED
        // Private / hidden numbers can't be counted per person; never cut them blindly.
        number.isEmpty() -> UntimedReason.UNKNOWN_NUMBER
        else -> null
    }

    /** Called while an incoming call is ringing (call screening). */
    fun decideIncoming(number: String, usage: NumberDay?, alwaysAllowed: Boolean, todayKey: String): CallDecision {
        exemption(number, alwaysAllowed)?.let { return CallDecision.Untimed(it) }
        val stage = stageOf(usage, todayKey)
        return if (stage == Stage.BLOCKED) CallDecision.Reject
        else CallDecision.Timed(limitFor(stage), stage)
    }

    /** Called before the user dials out. */
    fun decideOutgoing(number: String, usage: NumberDay?, alwaysAllowed: Boolean, todayKey: String): CallDecision {
        exemption(number, alwaysAllowed)?.let { return CallDecision.Untimed(it) }
        val stage = stageOf(usage, todayKey)
        return if (stage == Stage.BLOCKED) CallDecision.ConfirmNeeded(limitFor(stage))
        else CallDecision.Timed(limitFor(stage), stage)
    }

    /**
     * Called the moment a call becomes ACTIVE (picked up). Returns the limit to use
     * for this call and the updated counts to save. Counting at connect time (not at
     * hang-up) means a phone reboot in the middle of a call still uses up the stage.
     *
     * Exempt calls (emergency / always allowed) return a null limit and unchanged usage.
     */
    fun onConnected(number: String, usage: NumberDay?, alwaysAllowed: Boolean, todayKey: String): Connected {
        val today = usageForToday(usage, number, todayKey)
        exemption(number, alwaysAllowed)?.let { return Connected(limitMillis = null, stage = null, usage = today, reason = it) }
        val stage = stageOf(today, todayKey)
        return Connected(
            limitMillis = limitFor(stage),
            stage = stage,
            usage = today.copy(answeredCalls = today.answeredCalls + 1),
            reason = null,
        )
    }

    data class Connected(val limitMillis: Long?, val stage: Stage?, val usage: NumberDay, val reason: UntimedReason?)

    /** Called when a connected call ends. Adds talk time to the day it connected on. */
    fun onEnded(usage: NumberDay, talkMillis: Long): NumberDay =
        usage.copy(talkMillis = usage.talkMillis + talkMillis.coerceAtLeast(0))

    /** Called when an incoming call was rejected because the number is at Stage 3. */
    fun onBlocked(number: String, usage: NumberDay?, todayKey: String): NumberDay {
        val today = usageForToday(usage, number, todayKey)
        return today.copy(blockedCalls = today.blockedCalls + 1)
    }

    /**
     * Share of today's allowance this number has used, 0.0..1.0, for the progress bar.
     * The allowance is first-call + repeat-call minutes (15 by default).
     */
    fun progress(usage: NumberDay?, todayKey: String): Float {
        if (stageOf(usage, todayKey) == Stage.BLOCKED) return 1f
        val total = settings.firstCallMillis + settings.repeatCallMillis
        val used = usage?.takeIf { it.dayKey == todayKey }?.talkMillis ?: 0
        return (used.toFloat() / total).coerceIn(0f, 1f)
    }
}
