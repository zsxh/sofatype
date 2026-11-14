// T67 · 设置页 BACK 二次退出窗口纯决策单元测试（决策 57，JUnit 4，纯 Kotlin）。
// 锁定 BackExitGate 状态机四件事：
// ① 首击只提示不退出（SHOW_HINT）；
// ② 2 秒窗口内次击直接退出（EXIT）；
// ③ 超窗重新计：恰满窗长即超窗，超窗击视同新首击（再提示并重开窗口、不累计退出）；
// ④ 退出后状态机关窗：其后再击回到首击口径（状态机确定性；宿主 finish 后实例随之销毁）。
// 窗长接线值 = BackExitGate.WINDOW_MS（单一事实源，参照 ImeShowGuardTest 同模式）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class BackExitGateTest {

    /** 被测守卫：窗长取接线同口径值（BackExitGate.WINDOW_MS = 2 秒）。 */
    private fun gate() = BackExitGate(windowMs = BackExitGate.WINDOW_MS)

    // ---- ① 首击只提示不退出 ----

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

    // ---- ② 窗口内次击直接退出 ----

    @Test
    fun `窗口内次击直接退出`() {
        val g = gate()
        g.onBack(1_000L)
        // 距首击 500ms < 2000ms：窗内次击直接退出
        assertEquals(BackExitGate.Decision.EXIT, g.onBack(1_500L))
    }

    @Test
    fun `窗长边界内最后一刻次击退出`() {
        val g = gate()
        g.onBack(1_000L)
        // 1999ms 距首击 < 2000ms：仍在窗内
        assertEquals(BackExitGate.Decision.EXIT, g.onBack(2_999L))
    }

    // ---- ③ 超窗重新计 ----

    @Test
    fun `恰满窗长即超窗不退出`() {
        val g = gate()
        g.onBack(1_000L)
        // now - 首击 = 2000ms ≥ 窗长：超窗，重新计，只提示不退出
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(3_000L))
    }

    @Test
    fun `超窗击重新计窗且窗内次击照常退出`() {
        val g = gate()
        g.onBack(1_000L)
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(5_000L))
        // 新窗口自 5000ms 起：窗内（< 7000ms）次击直接退出，不因首段超窗累计
        assertEquals(BackExitGate.Decision.EXIT, g.onBack(6_999L))
    }

    @Test
    fun `连续超窗反复重新计`() {
        val g = gate()
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(0L))
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(10_000L))
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(20_000L))
        // 末次重开窗口内次击照常退出
        assertEquals(BackExitGate.Decision.EXIT, g.onBack(21_000L))
    }

    // ---- ④ 退出后状态机关窗 ----

    @Test
    fun `退出后守卫关窗再击回首击口径`() {
        val g = gate()
        g.onBack(0L)
        assertEquals(BackExitGate.Decision.EXIT, g.onBack(500L))
        assertEquals(BackExitGate.Decision.SHOW_HINT, g.onBack(600L))
    }
}
