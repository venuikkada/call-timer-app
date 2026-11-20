package com.callgalli.rules

/**
 * Counts active talk time for one call. Pauses while the call is on hold.
 * Time only runs between [start]/[resume] and [pause]. All times are passed in
 * (milliseconds from a monotonic clock) so this is fully testable.
 */
class CallClock(
    /** null = untimed (emergency / always allowed). */
    val limitMillis: Long?,
    initialElapsedMillis: Long = 0,
) {
    private var accumulated = initialElapsedMillis
    private var runningSince: Long? = null

    val isRunning: Boolean get() = runningSince != null

    fun start(now: Long) = resume(now)

    fun resume(now: Long) {
        if (runningSince == null) runningSince = now
    }

    fun pause(now: Long) {
        runningSince?.let { accumulated += (now - it).coerceAtLeast(0) }
        runningSince = null
    }

    fun elapsed(now: Long): Long = accumulated + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)

    /** Remaining time, or null if untimed. Never negative. */
    fun remaining(now: Long): Long? = limitMillis?.let { (it - elapsed(now)).coerceAtLeast(0) }

    fun isExpired(now: Long): Boolean = limitMillis != null && elapsed(now) >= limitMillis
}

/** Pill colour bands. */
enum class TimerBand { GREEN, AMBER, RED }

object TimerDisplay {
    const val AMBER_BELOW_MILLIS = 2 * 60_000L
    const val RED_BELOW_MILLIS = 30_000L

    fun band(remainingMillis: Long): TimerBand = when {
        remainingMillis <= RED_BELOW_MILLIS -> TimerBand.RED
        remainingMillis <= AMBER_BELOW_MILLIS -> TimerBand.AMBER
        else -> TimerBand.GREEN
    }

    /** "07:42". Rounds up so the pill reads 00:01 until the very end, never 00:00 early. */
    fun mmss(millis: Long): String {
        val totalSeconds = (millis.coerceAtLeast(0) + 999) / 1000
        return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}

/**
 * Fires each warning (e.g. 60 s and 30 s before the cut) exactly once.
 * If the call starts with less time than a warning point (e.g. a 1-minute limit
 * and a 60 s warning) that warning fires on the first check.
 * If several points are crossed at once, only the smallest one is returned.
 */
class WarningTracker(warningSeconds: List<Int>) {
    private val pending = warningSeconds.map { it * 1000L }.sortedDescending().toMutableList()

    /** Returns the warning (in seconds) to show now, or null. */
    fun check(remainingMillis: Long): Int? {
        var fired: Long? = null
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val point = iterator.next()
            if (remainingMillis <= point) {
                fired = point
                iterator.remove()
            }
        }
        return fired?.let { (it / 1000).toInt() }
    }
}
