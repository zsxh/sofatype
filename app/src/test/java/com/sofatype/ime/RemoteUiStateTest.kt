// T17 · 跨屏连接状态机单元测试（FR-B9 三态 / 决策 32 三卡面 / 决策 33 主卡模式）：
// 状态完全由 Manager 可观测值（服务运行 / 配对 token 有效 / 配对设备名）推导，
// 锁定三种输入组合 → 模式 / 连接状态 / 副卡卡面的映射与防御性规则。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteUiStateTest {

    @Test
    fun serviceNotRunningYieldsKeyboardNotConnectedGuideFace() {
        val state = RemoteUiState.of(serviceRunning = false, pairingTokenActive = true, deviceName = null)
        assertEquals(RemoteMode.KEYBOARD, state.mode)
        assertEquals(RemoteStatus.NOT_CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.GUIDE, state.sideCardFace)
    }

    @Test
    fun runningWithActiveTokenYieldsRemoteWaitingPairingFace() {
        // 等待扫码态：配对卡整卡本身即「等待扫码」卡面（决策 32，不加状态行）
        val state = RemoteUiState.of(serviceRunning = true, pairingTokenActive = true, deviceName = null)
        assertEquals(RemoteMode.REMOTE, state.mode)
        assertEquals(RemoteStatus.WAITING_SCAN, state.status)
        assertEquals(RemoteSideCardFace.PAIRING, state.sideCardFace)
        assertNull("等待态不携带设备名", state.deviceName)
    }

    @Test
    fun runningWithConsumedTokenYieldsRemoteConnectedFaceWithDeviceName() {
        // 已连接态：副卡切已连接卡面（绿点 + 设备名，不显示已失效二维码，决策 32）
        val state = RemoteUiState.of(serviceRunning = true, pairingTokenActive = false, deviceName = "browser")
        assertEquals(RemoteMode.REMOTE, state.mode)
        assertEquals(RemoteStatus.CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.CONNECTED, state.sideCardFace)
        assertEquals("browser", state.deviceName)
    }

    @Test
    fun connectedStateKeepsNullDeviceNameWhenHelloLackedIt() {
        // hello 未携带 deviceName：仍为已连接态（UI 只显示「已连接」），卡面不回退
        val state = RemoteUiState.of(serviceRunning = true, pairingTokenActive = false, deviceName = null)
        assertEquals(RemoteStatus.CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.CONNECTED, state.sideCardFace)
        assertNull(state.deviceName)
    }

    @Test
    fun stoppedServiceIgnoresLingeringDeviceName() {
        // 防御：服务已停止但设备名尚未清理（异常时序）——键盘态/未连接不展示残留值
        val state = RemoteUiState.of(serviceRunning = false, pairingTokenActive = false, deviceName = "browser")
        assertEquals(RemoteMode.KEYBOARD, state.mode)
        assertEquals(RemoteStatus.NOT_CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.GUIDE, state.sideCardFace)
        assertNull(state.deviceName)
    }

    @Test
    fun statusToFaceMappingIsOneToOne() {
        // FR-B9 三态 → 决策 32 三卡面一一对应（三态各自推导卡面）
        val cases = mapOf(
            RemoteStatus.NOT_CONNECTED to RemoteSideCardFace.GUIDE,
            RemoteStatus.WAITING_SCAN to RemoteSideCardFace.PAIRING,
            RemoteStatus.CONNECTED to RemoteSideCardFace.CONNECTED,
        )
        for ((status, face) in cases) {
            val state = RemoteUiState.of(
                serviceRunning = status != RemoteStatus.NOT_CONNECTED,
                pairingTokenActive = status == RemoteStatus.WAITING_SCAN,
                deviceName = null,
            )
            assertEquals(status, state.status)
            assertEquals("状态 $status 应对应卡面 $face", face, state.sideCardFace)
        }
    }

    @Test
    fun keyboardModeImpliesNotConnectedAndRemoteModeImpliesLiveStatus() {
        // 模式与连接状态一致性：键盘态恒未连接；远程模式恒为等待 / 已连接之一
        val keyboard = RemoteUiState.of(serviceRunning = false, pairingTokenActive = false, deviceName = null)
        assertEquals(RemoteMode.KEYBOARD, keyboard.mode)
        assertEquals(RemoteStatus.NOT_CONNECTED, keyboard.status)

        val waiting = RemoteUiState.of(serviceRunning = true, pairingTokenActive = true, deviceName = null)
        val connected = RemoteUiState.of(serviceRunning = true, pairingTokenActive = false, deviceName = "x")
        for (state in listOf(waiting, connected)) {
            assertEquals(RemoteMode.REMOTE, state.mode)
        }
    }
}
