package com.callgalli.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallClockTest {

    @Test
    fun `clock counts only active time and pauses on hold`() {
        val clock = CallClock(limitMillis = 600_000)
        clock.start(0)
        assertEquals(60_000, clock.elapsed(60_000))
        clock.pause(60_000)            // put on hold for 5 minutes
        assertEquals(60_000, clock.elapsed(360_000))
        clock.resume(360_000)
        assertEquals(120_000, clock.elapsed(420_000))
        assertEquals(480_000L, clock.remaining(420_000))
    }

    @Test
    fun `double pause or resume is harmless`() {
        val clock = CallClock(limitMillis = 10_000)
        clock.resume(0); clock.resume(5_000)
        assertEquals(6_000, clock.elapsed(6_000))
        clock.pause(6_000); clock.pause(9_000)
        assertEquals(6_000, clock.elapsed(20_000))
    }

    @Test
    fun `expires exactly at the limit`() {
        val clock = CallClock(limitMillis = 600_000)
        clock.start(1_000)
        assertFalse(clock.isExpired(600_999))
        assertTrue(clock.isExpired(601_000))
        assertEquals(0L, clock.remaining(700_000))
    }

    @Test
    fun `untimed clock never expires`() {
        val clock = CallClock(limitMillis = null)
        clock.start(0)
        assertFalse(clock.isExpired(10L * 3_600_000))
        assertNull(clock.remaining(1_000))
    }

    @Test
    fun `restored clock continues from saved elapsed time`() {
        val clock = CallClock(limitMillis = 300_000, initialElapsedMillis = 200_000)
        clock.start(0)
        assertEquals(100_000L, clock.remaining(0))
        assertTrue(clock.isExpired(100_000))
    }

    @Test
    fun `colour bands`() {
        assertEquals(TimerBand.GREEN, TimerDisplay.band(462_000))
        assertEquals(TimerBand.GREEN, TimerDisplay.band(120_001))
        assertEquals(TimerBand.AMBER, TimerDisplay.band(120_000))
        assertEquals(TimerBand.AMBER, TimerDisplay.band(30_001))
        assertEquals(TimerBand.RED, TimerDisplay.band(30_000))
        assertEquals(TimerBand.RED, TimerDisplay.band(0))
    }

    @Test
    fun `pill text`() {
        assertEquals("07:42", TimerDisplay.mmss(462_000))
        assertEquals("10:00", TimerDisplay.mmss(600_000))
        assertEquals("00:01", TimerDisplay.mmss(1))
        assertEquals("00:00", TimerDisplay.mmss(0))
    }

    @Test
    fun `warnings fire once each at 60 and 30 seconds`() {
        val tracker = WarningTracker(listOf(60, 30))
        assertNull(tracker.check(61_000))
        assertEquals(60, tracker.check(60_000))
        assertNull(tracker.check(45_000))
        assertEquals(30, tracker.check(29_500))
        assertNull(tracker.check(10_000))
    }

    @Test
    fun `jumping past both warnings fires only the smaller one`() {
        val tracker = WarningTracker(listOf(60, 30))
        assertEquals(30, tracker.check(20_000))
        assertNull(tracker.check(10_000))
    }
}
