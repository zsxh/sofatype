package com.sofatype.ime.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteCommitAftermathTest {

    @Test
    fun `上屏成功-回执成功`() {
        val aftermath = RemoteCommitAftermath.of(commitOk = true)
        assertTrue("成功须回执 committed ok=true（手机端清草稿并提示已上屏）", aftermath.acknowledge)
    }

    @Test
    fun `上屏成功-不停服`() {

        val aftermath = RemoteCommitAftermath.of(commitOk = true)
        assertFalse("上屏成功不得触发停服（决策 59）", aftermath.stopService)
    }

    @Test
    fun `上屏成功-清空远程缓冲`() {

        val aftermath = RemoteCommitAftermath.of(commitOk = true)
        assertTrue("上屏成功须清空远程缓冲并通知 UI 清大字区", aftermath.clearRemoteBuffer)
    }

    @Test
    fun `上屏失败-回执失败`() {
        val aftermath = RemoteCommitAftermath.of(commitOk = false)
        assertFalse("失败须回执 committed ok=false（手机端提示失败可重试）", aftermath.acknowledge)
    }

    @Test
    fun `上屏失败-不停服`() {

        val aftermath = RemoteCommitAftermath.of(commitOk = false)
        assertFalse("上屏失败不得触发停服", aftermath.stopService)
    }

    @Test
    fun `上屏失败-不清缓冲`() {

        val aftermath = RemoteCommitAftermath.of(commitOk = false)
        assertFalse("上屏失败须保持远程缓冲（草稿不丢）", aftermath.clearRemoteBuffer)
    }
}
