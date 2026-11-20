package com.callgalli.rules

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * "Which day does this moment belong to?" as a string like "2026-11-20".
 *
 * Counts are stored per day key, so the midnight reset needs no background job:
 * when the clock passes the reset time the key changes and every number is Fresh again.
 * With a reset time of 05:00, a call at 02:00 still belongs to the previous day.
 */
object DayKeys {

    fun of(epochMillis: Long, zone: ZoneId, resetMinuteOfDay: Int = 0): String =
        dateOf(epochMillis, zone, resetMinuteOfDay).toString()

    fun dateOf(epochMillis: Long, zone: ZoneId, resetMinuteOfDay: Int = 0): LocalDate =
        Instant.ofEpochMilli(epochMillis)
            .atZone(zone)
            .minusMinutes(resetMinuteOfDay.toLong())
            .toLocalDate()

    /** The last [days] day keys ending with [todayKey], oldest first. */
    fun lastDays(todayKey: String, days: Int): List<String> {
        val today = LocalDate.parse(todayKey)
        return (days - 1 downTo 0).map { today.minusDays(it.toLong()).toString() }
    }

    /** Epoch millis of the next reset (e.g. the coming midnight). */
    fun nextResetMillis(epochMillis: Long, zone: ZoneId, resetMinuteOfDay: Int = 0): Long {
        val day = dateOf(epochMillis, zone, resetMinuteOfDay)
        return day.plusDays(1).atStartOfDay(zone).plusMinutes(resetMinuteOfDay.toLong()).toInstant().toEpochMilli()
    }
}
