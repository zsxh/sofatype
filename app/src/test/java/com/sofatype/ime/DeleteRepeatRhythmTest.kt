// T22 · 删除键长按连删节奏模型单元测试（JUnit 4，纯 Kotlin，不依赖 Android UI 与模拟器）。
// 锁定 FR-A12「先慢后快」节奏口径：首删后初始延迟 400ms；重复间隔自 200ms 分级（25ms 步进）
// 加速至下限 50ms 后恒定；按住时间轴前段稀疏、后段密集（走查「先慢后快」的模型级锁定）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeleteRepeatRhythmTest {

    /** 运行时口径实例：默认参数即出厂节奏（服务层同款）。 */
    private val rhythm = DeleteRepeatRhythm()

    // ---- 首删后初始延迟 ----

    @Test
    fun firstRepeatWaitsInitialDelay() {
        // 按住后第一次重复删除（index 0）= 首删后初始延迟 400ms
        assertEquals(400L, rhythm.delayBefore(0))
    }

    @Test
    fun negativeRepeatIndexFallsBackToInitialDelay() {
        // 重复计数边界防御：负值按初始延迟口径
        assertEquals(400L, rhythm.delayBefore(-1))
    }

    // ---- 分级加速至下限 ----

    @Test
    fun repeatIntervalsAccelerateStepwiseToFloor() {
        // 出厂节奏全序列：200 → 175 → 150 → 125 → 100 → 75 → 50 → 50（7 级到下限后恒定）
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

    // ---- 「先慢后快」时间轴（模型级走查口径） ----

    /** 依模型推演按住 [holdMs] 内发出的重复删除次数（不含单击首删；时间轴从首删后起算）。 */
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
        // 首个 1s：4 次重复删（400/600/775/925ms 处）；第二个 1s：18 次（下限 50ms 每秒 20 删的加速段）
        assertEquals(4, repeatDeletesWithin(1000))
        assertEquals(18, repeatDeletesWithin(2000) - repeatDeletesWithin(1000))
    }

    @Test
    fun lateHoldReachesFloorRate() {
        // 按住进入稳态后 = 下限 50ms/删，任意 1s 窗口恰 20 次重复删（先慢后快的「快」端锁定）
        assertEquals(20, repeatDeletesWithin(4000) - repeatDeletesWithin(3000))
    }

    // ---- 参数约束（非法节奏参数拒绝构造） ----

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
                // 预期路径
            }
        }
    }
}
