// T70 · 已连接无活动空闲判定单元测试（JUnit 4，纯 Kotlin · 决策 59 / PRD 第 8 章电量与资源行）。
// 锁定：正式阈值常量 = 10 分钟、活动窗口边界、活动刷新重置窗口（超时与活动竞态不误退）、
// 会话结束（断连 / 停服）解除跟踪、重连后从新活动起算、非法阈值拒绝。
// 时间由测试注入，不依赖真实等待。接线（排程 / 停服 / 收起键盘）在 RemoteInputServerManager，
// 行为级取证走模拟器 WS 全链走查（同 T25 施工口径）。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConnectedIdlePolicyTest {

    /** 被测策略：短阈值便于边界构造（正式 10 分钟常量另专门断言）。 */
    private fun policy(timeoutMs: Long = 1_000L) = RemoteConnectedIdlePolicy(connectedIdleTimeoutMs = timeoutMs)

    // ---- 正式阈值 ----

    @Test
    fun `正式阈值等于10分钟`() {
        assertEquals(600_000L, RemoteConnectedIdlePolicy.DEFAULT_CONNECTED_IDLE_TIMEOUT_MS)
        assertEquals(600_000L, RemoteConnectedIdlePolicy().connectedIdleTimeoutMs)
    }

    // ---- 活动窗口边界 ----

    @Test
    fun `无活动记录恒不退出`() {
        // 未配对 / 会话已结束 / 服务未运行时无跟踪窗口：任何时刻都不判退出
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

    // ---- 活动刷新重置窗口（超时与活动竞态不误退） ----

    @Test
    fun `活动刷新重置窗口-旧窗口满阈值不退出`() {
        // 决策 59 竞态口径：窗口将满时到达新活动（input / commit 等），应自新活动重新起算，
        // 不因旧窗口时长判退（超时与连接活动同刻竞争不误退）
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

    // ---- 会话结束解除跟踪 ----

    @Test
    fun `会话结束后不退出`() {
        // 已配对连接断开 / 服务停止即解除：经过再长时间也不判「已连接无活动」退出
        //（断连回退与清缓冲归断连链路，空闲退出归 T25 无连接口径，互不越界）
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

    // ---- 参数防御 ----

    @Test
    fun `阈值必须为正数`() {
        assertThrows(IllegalArgumentException::class.java) { RemoteConnectedIdlePolicy(connectedIdleTimeoutMs = 0L) }
        assertThrows(IllegalArgumentException::class.java) { RemoteConnectedIdlePolicy(connectedIdleTimeoutMs = -1L) }
    }
}
