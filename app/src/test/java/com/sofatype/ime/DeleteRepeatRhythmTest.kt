package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeleteRepeatRhythmTest {

    private val rhythm = DeleteRepeatRhythm()

    @Test
    fun firstRepeatWaitsInitialDelay() {

        assertEquals(400L, rhythm.delayBefore(0))
    }

    @Test
    fun negativeRepeatIndexFallsBackToInitialDelay() {

        assertEquals(400L, rhythm.delayBefore(-1))
    }

    @Test
    fun repeatIntervalsAccelerateStepwiseToFloor() {

        val expected = listOf(200L, 175L, 150L, 125L, 100L, 75L, 50L, 50L)
        expected.forEachIndexed { index, delay ->
            assertEquals("重复删 #${index + 1} 前间隔应为 $delay", delay, rhythm.delayBefore(index + 1))
        }
    }

    @Test
    fun intervalsNeverGoBelowFloor() {
        for (index in 1..200) {
            assertTrue("重复删 #$index 间隔不得低于下限", rhythm.delayBefore(index) >= 50L)
        }
        assertEquals(50L, rhythm.delayBefore(100))
    }

    @Test
    fun intervalsAreMonotonicallyNonIncreasing() {
        var previous = rhythm.delayBefore(0)
        for (index in 1..100) {
            val current = rhythm.delayBefore(index)
            assertTrue("间隔序列应单调不增：#$index $current > $previous", current <= previous)
            previous = current
        }
    }

    private fun repeatDeletesWithin(holdMs: Long): Int {
        var elapsed = 0L
        var index = 0
        var count = 0
        while (true) {
            elapsed += rhythm.delayBefore(index)
            index += 1
            if (elapsed > holdMs) break
            count += 1
        }
        return count
    }

    @Test
    fun holdTimelineDeletesSlowlyThenFast() {

        assertEquals(4, repeatDeletesWithin(1000))
        assertEquals(18, repeatDeletesWithin(2000) - repeatDeletesWithin(1000))
    }

    @Test
    fun lateHoldReachesFloorRate() {

        assertEquals(20, repeatDeletesWithin(4000) - repeatDeletesWithin(3000))
    }

    @Test
    fun invalidParametersRejected() {
        val invalidConstructions = listOf(
            { DeleteRepeatRhythm(initialDelayMs = 0) },
            { DeleteRepeatRhythm(floorIntervalMs = 0) },
            { DeleteRepeatRhythm(firstIntervalMs = 40, floorIntervalMs = 50) },
            { DeleteRepeatRhythm(stepMs = 0) },
        )
        for (build in invalidConstructions) {
            try {
                build()
                fail("非法节奏参数应抛出 IllegalArgumentException")
            } catch (expected: IllegalArgumentException) {

            }
        }
    }
}
