package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class BackExitGateTest {

    private fun gate() = BackExitGate(windowMs = BackExitGate.WINDOW_MS)

    @Test
    fun `首击只提示不退出`() {
        val g = gate()
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(1_000L))
    }

    @Test
    fun `未按过键的守卫任意时刻首击都只提示`() {
        val g = gate()
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(0L))
        assertEquals(BackExitGate.Decision.SHOW_HINT, BackExitGate(BackExitGate.WINDOW_MS).onBack(60_000L))
    }

    @Test
    fun `窗口内次击直接退出`() {
        val g = gate()
        g.onBack(1_000L)

        assertEquals(BackExitGate.Decision.EXIT, g.onBack(1_500L))
    }

    @Test
    fun `窗长边界内最后一刻次击退出`() {
        val g = gate()
        g.onBack(1_000L)

        assertEquals(BackExitGate.Decision.EXIT, g.onBack(2_999L))
    }

    @Test
    fun `恰满窗长即超窗不退出`() {
        val g = gate()
        g.onBack(1_000L)

        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(3_000L))
    }

    @Test
    fun `超窗击重新计窗且窗内次击照常退出`() {
        val g = gate()
        g.onBack(1_000L)
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(5_000L))

        assertEquals(BackExitGate.Decision.EXIT, g.onBack(6_999L))
    }

    @Test
    fun `连续超窗反复重新计`() {
        val g = gate()
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(0L))
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(10_000L))
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(20_000L))

        assertEquals(BackExitGate.Decision.EXIT, g.onBack(21_000L))
    }

    @Test
    fun `退出后守卫关窗再击回首击口径`() {
        val g = gate()
        g.onBack(0L)
        assertEquals(BackExitGate.Decision.EXIT, g.onBack(500L))
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(600L))
    }
}
