// T74 · 远程模式 BACK 分流决策模型单元测试（JUnit 4，纯 Kotlin · 决策 62 / FR-B7 修订）。
// 锁定四分支：服务未运行 = 键盘态既有分支；运行中无已配对会话（等待扫码）= 完整清理退出
//（FR-B7 既有，不残留监听端口）；运行中有已配对会话 + 键盘显示态 = 只收起保活（FR-A1 语义，
// 服务 / 会话 / 远程缓冲 / 连接代号全保持、手机端无感知）；运行中有已配对会话 + 键盘已收起 =
// 键盘态既有分支（隐藏态透传宿主，T59 口径同族，服务保活不受影响）。
// 接线在 SofatypeImeService.dispatchKeyDown BACK 分支，行为级取证走模拟器走查。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteBackPolicyTest {

    // ---- 服务未运行（键盘态） ----

    @Test
    fun `服务未运行-显示态归键盘态分支`() {
        val action = RemoteBackPolicy.resolve(
            serviceRunning = false,
            pairedSessionActive = false,
            inputViewShown = true,
        )
        assertEquals(RemoteBackPolicy.Action.KEYBOARD_DEFAULT, action)
    }

    @Test
    fun `服务未运行-隐藏态归键盘态分支`() {
        val action = RemoteBackPolicy.resolve(
            serviceRunning = false,
            pairedSessionActive = false,
            inputViewShown = false,
        )
        assertEquals(RemoteBackPolicy.Action.KEYBOARD_DEFAULT, action)
    }

    // ---- 运行中 · 未连接（等待扫码，FR-B7 既有完整清理退出） ----

    @Test
    fun `运行中未连接-显示态完整清理退出`() {
        val action = RemoteBackPolicy.resolve(
            serviceRunning = true,
            pairedSessionActive = false,
            inputViewShown = true,
        )
        assertEquals(RemoteBackPolicy.Action.EXIT_REMOTE, action)
    }

    @Test
    fun `运行中未连接-隐藏态仍完整清理退出`() {
        // 现行口径不变：未连接分支先于隐藏态判定执行（T18 既有），不区分键盘显示态
        val action = RemoteBackPolicy.resolve(
            serviceRunning = true,
            pairedSessionActive = false,
            inputViewShown = false,
        )
        assertEquals(RemoteBackPolicy.Action.EXIT_REMOTE, action)
    }

    // ---- 运行中 · 已连接（决策 62 保活分流） ----

    @Test
    fun `运行中已连接-显示态只收起保活`() {
        val action = RemoteBackPolicy.resolve(
            serviceRunning = true,
            pairedSessionActive = true,
            inputViewShown = true,
        )
        assertEquals(RemoteBackPolicy.Action.HIDE_KEEP_ALIVE, action)
    }

    @Test
    fun `运行中已连接-隐藏态归键盘态分支透传宿主`() {
        // T59 口径同族：键盘已收起时收起无效果且拦截宿主返回语义，透传宿主；
        // 服务保活不受影响，重入沿 T17 路径自动回远程模式
        val action = RemoteBackPolicy.resolve(
            serviceRunning = true,
            pairedSessionActive = true,
            inputViewShown = false,
        )
        assertEquals(RemoteBackPolicy.Action.KEYBOARD_DEFAULT, action)
    }
}
