// T78 · 决策 64：保活收起后自动重显沿默认焦点决策模型单元测试（JUnit 4，纯 Kotlin）。
// 锁定状态机全部转移：
//   初始态 = EXIT_BUTTON（决策 50-①：首次进入 / 显式唤起沿「退出远程」默认持焦）；
//   保活收起沿置位后首个 show 沿 = NONE（保活重显无默认焦点，首发 OK 不误杀会话，
//   设备档案坑 25 缺陷链）且一次性消费（其后 show 沿回 EXIT_BUTTON，不吞会话重开沿）；
//   显式唤起沿清除标记（唤醒护栏路径默认持焦不回归，T74 真机轮 2/3 已验证路径）；
//   远程模式退出沿复位（防陈旧标记跨越完整远程生命周期）；
//   重复收起沿置位幂等（不叠加、仍单次消费）。
// 接线在 SofatypeImeService（BACK HIDE_KEEP_ALIVE 分支 / 隐藏态确认键分支 /
// onStartInputView 远程分支 / renderMainCardMode REMOTE→KEYBOARD 转换沿），
// 行为级取证走模拟器走查。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteReentryFocusPolicyTest {

    // ---- 初始态与未标记 show 沿 ----

    @Test
    fun `初始态-show沿判默认持焦退出按钮`() {
        val policy = RemoteReentryFocusPolicy()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.EXIT_BUTTON, policy.resolveOnShow())
    }

    @Test
    fun `未标记保活收起-连续show沿恒判默认持焦`() {
        val policy = RemoteReentryFocusPolicy()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.EXIT_BUTTON, policy.resolveOnShow())
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.EXIT_BUTTON, policy.resolveOnShow())
    }

    // ---- 保活收起沿 → 重显沿一次性无默认焦点（决策 64 主链） ----

    @Test
    fun `保活收起后首个show沿判无默认焦点`() {
        val policy = RemoteReentryFocusPolicy()
        policy.onKeepAliveHide()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.NONE, policy.resolveOnShow())
    }

    @Test
    fun `无默认焦点一次性消费-其后show沿回默认持焦`() {
        val policy = RemoteReentryFocusPolicy()
        policy.onKeepAliveHide()
        policy.resolveOnShow()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.EXIT_BUTTON, policy.resolveOnShow())
    }

    @Test
    fun `重复保活收起沿置位幂等-仍只消费一次`() {
        val policy = RemoteReentryFocusPolicy()
        policy.onKeepAliveHide()
        policy.onKeepAliveHide()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.NONE, policy.resolveOnShow())
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.EXIT_BUTTON, policy.resolveOnShow())
    }

    // ---- 显式唤起沿（唤醒护栏路径 · 决策 50-① 不回归） ----

    @Test
    fun `保活收起后显式唤起-show沿判默认持焦`() {
        val policy = RemoteReentryFocusPolicy()
        policy.onKeepAliveHide()
        policy.onExplicitWake()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.EXIT_BUTTON, policy.resolveOnShow())
    }

    @Test
    fun `显式唤起对未标记态为无害清除`() {
        val policy = RemoteReentryFocusPolicy()
        policy.onExplicitWake()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.EXIT_BUTTON, policy.resolveOnShow())
    }

    // ---- 保活周期复用（重显无焦点态再次收起 → 再次无焦点） ----

    @Test
    fun `消费后再次保活收起-新周期首个show沿仍判无默认焦点`() {
        val policy = RemoteReentryFocusPolicy()
        policy.onKeepAliveHide()
        policy.resolveOnShow()
        policy.onKeepAliveHide()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.NONE, policy.resolveOnShow())
    }

    // ---- 远程模式退出沿复位 ----

    @Test
    fun `退出沿复位-陈旧标记不影响下一周期默认持焦`() {
        val policy = RemoteReentryFocusPolicy()
        policy.onKeepAliveHide()
        policy.reset()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.EXIT_BUTTON, policy.resolveOnShow())
    }
}
