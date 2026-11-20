package com.callgalli.rules

/**
 * The user's limits. All values are validated so a bad backup or bug can never
 * produce a 0-minute or 10-hour limit.
 */
data class RuleSettings(
    val firstCallMinutes: Int = 10,
    val repeatCallMinutes: Int = 5,
    /** Minutes after midnight when counts reset. 0 = midnight. */
    val resetMinuteOfDay: Int = 0,
    val firstWarningSeconds: Int = 60,
    val secondWarningSeconds: Int = 30,
) {
    init {
        require(firstCallMinutes in MINUTES_RANGE) { "firstCallMinutes must be 1..60" }
        require(repeatCallMinutes in MINUTES_RANGE) { "repeatCallMinutes must be 1..60" }
        require(resetMinuteOfDay in 0 until 24 * 60) { "resetMinuteOfDay must be 0..1439" }
        require(firstWarningSeconds in WARNING_RANGE) { "firstWarningSeconds must be 5..300" }
        require(secondWarningSeconds in WARNING_RANGE) { "secondWarningSeconds must be 5..300" }
    }

    val firstCallMillis: Long get() = firstCallMinutes * 60_000L
    val repeatCallMillis: Long get() = repeatCallMinutes * 60_000L

    /** Warning points in seconds, biggest first, without duplicates. */
    val warningSeconds: List<Int> get() = listOf(firstWarningSeconds, secondWarningSeconds).distinct().sortedDescending()

    companion object {
        val MINUTES_RANGE = 1..60
        val WARNING_RANGE = 5..300
    }
}
