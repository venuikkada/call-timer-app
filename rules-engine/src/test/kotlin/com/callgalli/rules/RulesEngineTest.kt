package com.callgalli.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesEngineTest {
    private val engine = RulesEngine()
    private val today = "2026-11-20"
    private val ravi = "9848022338"

    @Test
    fun `fresh number gets the 10 minute first-call limit`() {
        assertEquals(CallDecision.Timed(10 * 60_000L, Stage.FRESH), engine.decideIncoming(ravi, null, false, today))
    }

    @Test
    fun `stages go fresh, limited, blocked as calls are answered`() {
        val first = engine.onConnected(ravi, null, false, today)
        assertEquals(10 * 60_000L, first.limitMillis)
        assertEquals(Stage.FRESH, first.stage)
        assertEquals(Stage.LIMITED, engine.stageOf(first.usage, today))

        val second = engine.onConnected(ravi, first.usage, false, today)
        assertEquals(5 * 60_000L, second.limitMillis)
        assertEquals(Stage.LIMITED, second.stage)
        assertEquals(Stage.BLOCKED, engine.stageOf(second.usage, today))

        assertEquals(CallDecision.Reject, engine.decideIncoming(ravi, second.usage, false, today))
    }

    @Test
    fun `missed calls do not use up a stage`() {
        // Only onConnected advances the stage; a ringing call that is never picked up changes nothing.
        engine.decideIncoming(ravi, null, false, today)
        assertEquals(Stage.FRESH, engine.stageOf(null, today))
    }

    @Test
    fun `yesterday's counts are ignored after the reset`() {
        val blockedYesterday = NumberDay(ravi, "2026-11-19", answeredCalls = 2, blockedCalls = 4, talkMillis = 900_000)
        assertEquals(Stage.FRESH, engine.stageOf(blockedYesterday, today))
        assertEquals(CallDecision.Timed(600_000, Stage.FRESH), engine.decideIncoming(ravi, blockedYesterday, false, today))
        val connected = engine.onConnected(ravi, blockedYesterday, false, today)
        assertEquals(NumberDay(ravi, today, answeredCalls = 1), connected.usage)
    }

    @Test
    fun `emergency numbers are never timed or rejected, even with heavy use`() {
        val heavy = NumberDay("112", today, answeredCalls = 10)
        EmergencyNumbers.ALL.forEach { number ->
            assertEquals(CallDecision.Untimed(UntimedReason.EMERGENCY), engine.decideIncoming(number, heavy.copy(number = number), false, today))
            assertEquals(CallDecision.Untimed(UntimedReason.EMERGENCY), engine.decideOutgoing(number, heavy.copy(number = number), false, today))
            val connected = engine.onConnected(number, null, false, today)
            assertNull(connected.limitMillis)
            assertEquals(0, connected.usage.answeredCalls)
        }
    }

    @Test
    fun `always allowed contacts are never timed or rejected`() {
        val heavy = NumberDay(ravi, today, answeredCalls = 5)
        assertEquals(CallDecision.Untimed(UntimedReason.ALWAYS_ALLOWED), engine.decideIncoming(ravi, heavy, true, today))
        assertEquals(CallDecision.Untimed(UntimedReason.ALWAYS_ALLOWED), engine.decideOutgoing(ravi, heavy, true, today))
        assertNull(engine.onConnected(ravi, heavy, true, today).limitMillis)
    }

    @Test
    fun `hidden numbers are not timed`() {
        assertEquals(CallDecision.Untimed(UntimedReason.UNKNOWN_NUMBER), engine.decideIncoming("", null, false, today))
    }

    @Test
    fun `outgoing to a blocked number asks for confirmation and keeps the repeat limit`() {
        val blocked = NumberDay(ravi, today, answeredCalls = 2)
        assertEquals(CallDecision.ConfirmNeeded(5 * 60_000L), engine.decideOutgoing(ravi, blocked, false, today))
        val connected = engine.onConnected(ravi, blocked, false, today)
        assertEquals(5 * 60_000L, connected.limitMillis)
    }

    @Test
    fun `outgoing and incoming share the same count`() {
        val afterOutgoing = engine.onConnected(ravi, null, false, today).usage
        assertEquals(CallDecision.Timed(5 * 60_000L, Stage.LIMITED), engine.decideIncoming(ravi, afterOutgoing, false, today))
    }

    @Test
    fun `blocked count and talk time accumulate`() {
        var usage = engine.onConnected(ravi, null, false, today).usage
        usage = engine.onEnded(usage, 600_000)
        usage = engine.onConnected(ravi, usage, false, today).usage
        usage = engine.onEnded(usage, 300_000)
        usage = engine.onBlocked(ravi, usage, today)
        usage = engine.onBlocked(ravi, usage, today)
        assertEquals(NumberDay(ravi, today, answeredCalls = 2, blockedCalls = 2, talkMillis = 900_000), usage)
        assertEquals(1f, engine.progress(usage, today))
    }

    @Test
    fun `custom limits are used`() {
        val custom = RulesEngine(RuleSettings(firstCallMinutes = 20, repeatCallMinutes = 3))
        val first = custom.onConnected(ravi, null, false, today)
        assertEquals(20 * 60_000L, first.limitMillis)
        assertEquals(3 * 60_000L, custom.onConnected(ravi, first.usage, false, today).limitMillis)
    }

    @Test
    fun `settings outside 1 to 60 minutes are refused`() {
        listOf(0, 61, -5).forEach { bad ->
            val failed = runCatching { RuleSettings(firstCallMinutes = bad) }.isFailure
            assertTrue("first=$bad should fail", failed)
            assertTrue("repeat=$bad should fail", runCatching { RuleSettings(repeatCallMinutes = bad) }.isFailure)
        }
        RuleSettings(firstCallMinutes = 1, repeatCallMinutes = 60) // edges are fine
    }

    @Test
    fun `progress bar uses talk time against total allowance`() {
        val usage = NumberDay(ravi, today, answeredCalls = 1, talkMillis = 450_000) // 7.5 of 15 min
        assertEquals(0.5f, engine.progress(usage, today), 0.001f)
        assertEquals(0f, engine.progress(null, today))
    }
}
