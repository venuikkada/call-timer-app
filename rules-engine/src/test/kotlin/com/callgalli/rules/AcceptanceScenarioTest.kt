package com.callgalli.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Plays the acceptance tests from the spec against the pure engine, the same way
 * the Android InCallService drives it: decide while ringing, onConnected when
 * picked up, tick the clock, cut at the limit, onEnded when the call finishes.
 */
class AcceptanceScenarioTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val engine = RulesEngine()
    private val ravi = "9848022338"
    private fun at(text: String) = LocalDateTime.parse(text).atZone(ist).toInstant().toEpochMilli()

    /** Simulates an answered call; returns (usage after, millis at which it was cut or null). */
    private fun answeredCall(usage: NumberDay?, connectAt: Long, wantsToTalkMillis: Long, alwaysAllowed: Boolean = false, number: String = ravi): Pair<NumberDay, Long?> {
        val today = DayKeys.of(connectAt, ist)
        val decision = engine.decideIncoming(number, usage, alwaysAllowed, today)
        assertTrue("call should ring, got $decision", decision !is CallDecision.Reject)
        val connected = engine.onConnected(number, usage, alwaysAllowed, today)
        val clock = CallClock(connected.limitMillis).apply { start(connectAt) }
        var now = connectAt
        var cutAt: Long? = null
        val tick = 500L // the app ticks every 500 ms
        while (now - connectAt < wantsToTalkMillis) {
            now += tick
            if (clock.isExpired(now)) { cutAt = now; break }
        }
        clock.pause(now)
        return engine.onEnded(connected.usage, clock.elapsed(now)) to cutAt?.minus(connectAt)
    }

    @Test
    fun `full day - 10 min, then 5 min, then rejected, then reset at midnight`() {
        // 1) First call wants 25 min, is cut at 10:00 (within 2 s).
        val (afterFirst, firstCut) = answeredCall(null, at("2026-11-20T09:00:00"), 25 * 60_000L)
        assertTrue(firstCut != null && firstCut in 600_000L..602_000L)

        // 2) Same number later, user picks up: cut at 5:00.
        val (afterSecond, secondCut) = answeredCall(afterFirst, at("2026-11-20T13:00:00"), 25 * 60_000L)
        assertTrue(secondCut != null && secondCut in 300_000L..302_000L)

        // 3) Third call never rings.
        val today = DayKeys.of(at("2026-11-20T18:00:00"), ist)
        assertEquals(CallDecision.Reject, engine.decideIncoming(ravi, afterSecond, false, today))
        val afterBlock = engine.onBlocked(ravi, afterSecond, today)
        assertEquals(1, afterBlock.blockedCalls)
        assertEquals(15 * 60_000L, afterBlock.talkMillis)

        // 4) After midnight everything is fresh.
        val tomorrow = DayKeys.of(at("2026-11-21T00:00:01"), ist)
        assertEquals(CallDecision.Timed(600_000, Stage.FRESH), engine.decideIncoming(ravi, afterBlock, false, tomorrow))
    }

    @Test
    fun `short first call still moves to the 5 minute stage`() {
        val (after, cut) = answeredCall(null, at("2026-11-20T09:00:00"), 2 * 60_000L)
        assertEquals(null, cut)
        assertEquals(Stage.LIMITED, engine.stageOf(after, "2026-11-20"))
    }

    @Test
    fun `112 is never cut`() {
        val (_, cut) = answeredCall(null, at("2026-11-20T09:00:00"), 60 * 60_000L, number = "112")
        assertEquals(null, cut)
    }

    @Test
    fun `always allowed contact is never cut or blocked`() {
        var usage: NumberDay? = null
        repeat(4) {
            val (after, cut) = answeredCall(usage, at("2026-11-20T09:00:00") + it * 3_600_000L, 30 * 60_000L, alwaysAllowed = true)
            assertEquals(null, cut)
            usage = after
        }
    }
}
