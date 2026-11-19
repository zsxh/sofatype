package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteBackPolicyTest {

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

        val action = RemoteBackPolicy.resolve(
            serviceRunning = true,
            pairedSessionActive = false,
            inputViewShown = false,
        )
        assertEquals(RemoteBackPolicy.Action.EXIT_REMOTE, action)
    }

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

        val action = RemoteBackPolicy.resolve(
            serviceRunning = true,
            pairedSessionActive = true,
            inputViewShown = false,
        )
        assertEquals(RemoteBackPolicy.Action.KEYBOARD_DEFAULT, action)
    }
}
