package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteReentryFocusPolicyTest {

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

    @Test
    fun `消费后再次保活收起-新周期首个show沿仍判无默认焦点`() {
        val policy = RemoteReentryFocusPolicy()
        policy.onKeepAliveHide()
        policy.resolveOnShow()
        policy.onKeepAliveHide()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.NONE, policy.resolveOnShow())
    }

    @Test
    fun `退出沿复位-陈旧标记不影响下一周期默认持焦`() {
        val policy = RemoteReentryFocusPolicy()
        policy.onKeepAliveHide()
        policy.reset()
        assertEquals(RemoteReentryFocusPolicy.ReentryFocus.EXIT_BUTTON, policy.resolveOnShow())
    }
}
