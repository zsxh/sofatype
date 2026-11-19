package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConnectedIdlePolicyTest {

    private fun policy(timeoutMs: Long = 1_000L) = RemoteConnectedIdlePolicy(connectedIdleTimeoutMs = timeoutMs)

    @Test
    fun `正式阈值等于10分钟`() {
        assertEquals(600_000L, RemoteConnectedIdlePolicy.DEFAULT_CONNECTED_IDLE_TIMEOUT_MS)
        assertEquals(600_000L, RemoteConnectedIdlePolicy().connectedIdleTimeoutMs)
    }

    @Test
    fun `无活动记录恒不退出`() {

        assertFalse(policy().shouldExit(Long.MAX_VALUE))
    }

    @Test
    fun `活动后阈值内不退出整点退出`() {
        val p = policy()
        p.onActivity(0L)
        assertFalse("阈值前一刻不退出", p.shouldExit(999L))
        assertTrue("阈值整点退出", p.shouldExit(1_000L))
        assertTrue("阈值之后仍退出", p.shouldExit(1_500L))
    }

    @Test
    fun `活动起点非零时刻同样成立`() {
        val p = policy()
        p.onActivity(7_000L)
        assertFalse(p.shouldExit(7_999L))
        assertTrue(p.shouldExit(8_000L))
    }

    @Test
    fun `活动刷新重置窗口-旧窗口满阈值不退出`() {

        val p = policy()
        p.onActivity(0L)
        p.onActivity(800L)
        assertFalse("旧窗口阈值时刻不退出（活动已刷新）", p.shouldExit(999L))
        assertTrue("新窗口阈值整点退出", p.shouldExit(1_800L))
    }

    @Test
    fun `连续活动持续顺延`() {
        val p = policy()
        p.onActivity(0L)
        p.onActivity(600L)
        p.onActivity(1_200L)
        assertFalse(p.shouldExit(2_199L))
        assertTrue(p.shouldExit(2_200L))
    }

    @Test
    fun `会话结束后不退出`() {

        val p = policy()
        p.onActivity(0L)
        p.onSessionEnded()
        assertFalse(p.shouldExit(Long.MAX_VALUE))
    }

    @Test
    fun `重连后从新活动起算`() {
        val p = policy()
        p.onActivity(0L)
        p.onSessionEnded()
        p.onActivity(5_000L)
        assertFalse("新会话阈值前不退出（旧窗口已结束）", p.shouldExit(5_999L))
        assertTrue("新会话阈值整点退出", p.shouldExit(6_000L))
    }

    @Test
    fun `阈值必须为正数`() {
        assertThrows(IllegalArgumentException::class.java) { RemoteConnectedIdlePolicy(connectedIdleTimeoutMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) { RemoteConnectedIdlePolicy(connectedIdleTimeoutMs = -1L) }
    }
}
