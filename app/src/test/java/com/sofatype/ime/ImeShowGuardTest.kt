// T38 · 收起-唤起时序守卫单元测试（JUnit 4，纯 Kotlin）。
// 锁定 ImeShowGuard 两个抑制窗的边界与吞键语义：收起后显示抑制（Bug A）、
// 唤起后首个确认键吞键并解除（Bug B）、双窗独立互不影响。
package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeShowGuardTest {

    /** 被测守卫：窗长取服务层同口径值（收起 800ms / 唤起 500ms）。 */
    private fun guard() = ImeShowGuard(showGuardMs = 800L, wakeGuardMs = 500L)

    // ---- 收起抑制窗（Bug A） ----

    @Test
    fun `未收起时不拒绝显示请求`() {
        val g = guard()
        assertFalse(g.shouldVetoShowRequest(10_000L))
        assertFalse(g.shouldRehideOnShown(10_000L))
    }

    @Test
    fun `收起抑制窗内拒绝显示请求`() {
        val g = guard()
        g.onHideRequested(1_000L)
        assertTrue(g.shouldVetoShowRequest(1_000L))
        assertTrue(g.shouldVetoShowRequest(1_500L))
        assertTrue(g.shouldRehideOnShown(1_799L))
    }

    @Test
    fun `收起抑制窗到期后放行显示`() {
        val g = guard()
        g.onHideRequested(1_000L)
        assertFalse(g.shouldVetoShowRequest(1_800L))
        assertFalse(g.shouldRehideOnShown(1_800L))
    }

    @Test
    fun `重复收起刷新抑制窗`() {
        val g = guard()
        g.onHideRequested(1_000L)
        g.onHideRequested(1_500L)
        assertTrue(g.shouldVetoShowRequest(2_200L))
        assertFalse(g.shouldVetoShowRequest(2_300L))
    }

    // ---- 唤起抑制窗（Bug B） ----

    @Test
    fun `窗口显示后吞首个确认键`() {
        val g = guard()
        g.onWindowShown(500L)
        assertTrue(g.shouldSwallowConfirmKey(600L))
    }

    @Test
    fun `吞键一次即解除同窗后续确认键放行`() {
        val g = guard()
        g.onWindowShown(500L)
        assertTrue(g.shouldSwallowConfirmKey(510L))
        assertFalse(g.shouldSwallowConfirmKey(520L))
        assertFalse(g.shouldSwallowConfirmKey(990L))
    }

    @Test
    fun `唤起抑制窗到期后确认键放行`() {
        val g = guard()
        g.onWindowShown(500L)
        assertFalse(g.shouldSwallowConfirmKey(1_000L))
    }

    @Test
    fun `未显示过窗口时不吞确认键`() {
        val g = guard()
        assertFalse(g.shouldSwallowConfirmKey(10_000L))
    }

    // ---- 双窗独立 ----

    @Test
    fun `唤起吞键解除不影响收起抑制窗`() {
        val g = guard()
        g.onHideRequested(1_000L)
        g.onWindowShown(1_100L)
        assertTrue(g.shouldSwallowConfirmKey(1_110L))
        assertTrue(g.shouldVetoShowRequest(1_200L))
        assertTrue(g.shouldRehideOnShown(1_200L))
    }

    @Test
    fun `收起抑制窗到期不影响唤起抑制窗吞键`() {
        val g = guard()
        g.onHideRequested(1_000L)
        g.onWindowShown(1_100L)
        assertFalse(g.shouldVetoShowRequest(1_801L))
        assertTrue(g.shouldSwallowConfirmKey(1_550L))
    }

    @Test
    fun `吞键解除后新窗口显示重新开窗`() {
        val g = guard()
        g.onWindowShown(500L)
        assertTrue(g.shouldSwallowConfirmKey(505L))
        g.onWindowShown(1_200L)
        assertTrue(g.shouldSwallowConfirmKey(1_250L))
    }
}
