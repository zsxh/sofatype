package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteIdlePolicyTest {

    private fun policy(idleTimeoutMs: Long = 1_000L) = RemoteIdlePolicy(idleTimeoutMs = idleTimeoutMs)

    @Test
    fun `正式阈值等于30分钟`() {
        assertEquals(1_800_000L, RemoteIdlePolicy.DEFAULT_IDLE_TIMEOUT_MS)
        assertEquals(1_800_000L, RemoteIdlePolicy().idleTimeoutMs)
    }

    @Test
    fun `未进入空闲窗口恒不退出`() {
        assertFalse(policy().shouldExit(Long.MAX_VALUE))
    }

    @Test
    fun `无连接态达到阈值判定退出`() {
        val p = policy()
        p.onIdleStarted(0L)
        assertFalse("阈值前一刻不退出", p.shouldExit(999L))
        assertTrue("阈值整点退出", p.shouldExit(1_000L))
        assertTrue("阈值之后仍退出", p.shouldExit(1_500L))
    }

    @Test
    fun `窗口起点非零时刻同样成立`() {
        val p = policy()
        p.onIdleStarted(7_000L)
        assertFalse(p.shouldExit(7_999L))
        assertTrue(p.shouldExit(8_000L))
    }

    @Test
    fun `手机连接解除空闲-经过再长时间也不退出`() {
        val p = policy()
        p.onIdleStarted(0L)
        p.onIdleEnded()
        assertFalse(p.shouldExit(10_000L))
    }

    @Test
    fun `解除后不重进则保持解除`() {
        val p = policy()
        p.onIdleStarted(0L)
        p.onIdleEnded()
        assertFalse(p.shouldExit(100_000L))
    }

    @Test
    fun `断连重进空闲从新起点起算`() {
        val p = policy()
        p.onIdleStarted(0L)
        p.onIdleEnded()
        p.onIdleStarted(5_000L)
        assertFalse("新窗口阈值前不退出（旧窗口已结束）", p.shouldExit(5_999L))
        assertTrue("新窗口阈值整点退出", p.shouldExit(6_000L))
    }

    @Test
    fun `重复进入刷新起点-后一次覆盖前一次`() {
        val p = policy()
        p.onIdleStarted(0L)
        p.onIdleStarted(800L)
        assertFalse(p.shouldExit(1_799L))
        assertTrue(p.shouldExit(1_800L))
    }

    @Test
    fun `阈值必须为正数`() {
        assertThrows(IllegalArgumentException::class.java) { RemoteIdlePolicy(idleTimeoutMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) { RemoteIdlePolicy(idleTimeoutMs = -1L) }
    }
}
