package com.callgalli.rules

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class DayKeysTest {
    private val ist = ZoneId.of("Asia/Kolkata")

    private fun at(text: String): Long = LocalDateTime.parse(text).atZone(ist).toInstant().toEpochMilli()

    @Test
    fun `midnight reset changes the key`() {
        assertEquals("2026-11-20", DayKeys.of(at("2026-11-20T23:59:59"), ist))
        assertEquals("2026-11-21", DayKeys.of(at("2026-11-21T00:00:00"), ist))
    }

    @Test
    fun `custom reset time keeps early-morning calls on the previous day`() {
        val fiveAm = 5 * 60
        assertEquals("2026-11-20", DayKeys.of(at("2026-11-21T04:59:00"), ist, fiveAm))
        assertEquals("2026-11-21", DayKeys.of(at("2026-11-21T05:00:00"), ist, fiveAm))
    }

    @Test
    fun `next reset is the coming midnight`() {
        assertEquals(at("2026-11-21T00:00:00"), DayKeys.nextResetMillis(at("2026-11-20T10:15:00"), ist))
    }

    @Test
    fun `last seven days oldest first`() {
        val days = DayKeys.lastDays("2026-03-02", 7)
        assertEquals(listOf("2026-02-24", "2026-02-25", "2026-02-26", "2026-02-27", "2026-02-28", "2026-03-01", "2026-03-02"), days)
    }
}
