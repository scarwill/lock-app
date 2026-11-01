package com.quizlock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepGateTest {
    @Test
    fun normalWalkingCountsEveryStep() {
        val g = StepGate(100)
        g.onReading(5000f, 0)
        // 2 kadam har second, 50 second.
        for (s in 1..50) g.onReading(5000f + s * 2, s * 1000L)
        assertEquals(100, g.counted)
        assertTrue(g.done)
    }

    @Test
    fun shakingIsCappedAtTwoAndHalfPerSecond() {
        val g = StepGate(100)
        g.onReading(0f, 0)
        g.onReading(100f, 10_000) // 10 second mein 100 "kadam"
        assertEquals(25, g.counted)
        assertFalse(g.done)
    }

    @Test
    fun batchedReadingsAreFine() {
        val g = StepGate(100)
        g.onReading(0f, 0)
        g.onReading(20f, 10_000)
        g.onReading(60f, 30_000)
        g.onReading(110f, 55_000)
        assertEquals(100, g.counted)
    }

    @Test
    fun stepsDuringPauseAreIgnored() {
        val g = StepGate(100)
        g.onReading(0f, 0)
        g.onReading(20f, 10_000)
        g.pause()
        g.onReading(500f, 60_000) // call ke baad pehli reading
        g.onReading(510f, 65_000)
        assertEquals(30, g.counted)
    }
}
