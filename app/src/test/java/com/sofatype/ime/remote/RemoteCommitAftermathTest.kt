// T70 · commit 上屏处置模型单元测试（JUnit 4，纯 Kotlin · 决策 59 / FR-B6 / FR-C6 修订）。
// 锁定 T70 核心语义三要素：上屏成功不停服（废止 R-4「上屏成功自动收起」）、上屏后缓冲清空、
// 上屏失败不停服且不清缓冲（失败可重试路径不变）。停服决策收口于本模型（单一事实源），
// 接线（主线程写入 + teardown 回执 + 缓冲清理 + UI 通知）在 RemoteInputServerManager，
// 行为级取证走模拟器 WS 全链走查。
package com.sofatype.ime.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommitAftermathTest {

    // ---- 写入成功（决策 59 核心语义） ----

    @Test
    fun `上屏成功-回执成功`() {
        val aftermath = RemoteCommitAftermath.of(commitOk = true)
        assertTrue("成功须回执 committed ok=true（手机端清草稿并提示已上屏）", aftermath.acknowledge)
    }

    @Test
    fun `上屏成功-不停服`() {
        // 决策 59：上屏成功不再停服收起——WS 连接与远程模式保持，可继续编辑再次上屏
        //（R-4「上屏成功自动收起」废止，本断言锁定不回退到 T16 旧口径）
        val aftermath = RemoteCommitAftermath.of(commitOk = true)
        assertFalse("上屏成功不得触发停服（决策 59）", aftermath.stopService)
    }

    @Test
    fun `上屏成功-清空远程缓冲`() {
        // 决策 59：TV 端上屏成功即清大字区与远程缓冲（立即回等待输入态），状态行保持已连接
        val aftermath = RemoteCommitAftermath.of(commitOk = true)
        assertTrue("上屏成功须清空远程缓冲并通知 UI 清大字区", aftermath.clearRemoteBuffer)
    }

    // ---- 写入失败（既有口径不变） ----

    @Test
    fun `上屏失败-回执失败`() {
        val aftermath = RemoteCommitAftermath.of(commitOk = false)
        assertFalse("失败须回执 committed ok=false（手机端提示失败可重试）", aftermath.acknowledge)
    }

    @Test
    fun `上屏失败-不停服`() {
        // FR-C6 既有口径：写入失败（无焦点输入框等）远程模式保持，可修正后重试
        val aftermath = RemoteCommitAftermath.of(commitOk = false)
        assertFalse("上屏失败不得触发停服", aftermath.stopService)
    }

    @Test
    fun `上屏失败-不清缓冲`() {
        // FR-C6 既有口径：失败时用户草稿不丢——远程缓冲保持，可修正后重试
        val aftermath = RemoteCommitAftermath.of(commitOk = false)
        assertFalse("上屏失败须保持远程缓冲（草稿不丢）", aftermath.clearRemoteBuffer)
    }
}
