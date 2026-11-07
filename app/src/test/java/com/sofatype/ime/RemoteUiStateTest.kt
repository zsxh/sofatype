// T17/T35 · 跨屏连接状态机单元测试（FR-B9 三态 / 决策 32/35 卡面 / 决策 33 主卡模式）：
// 状态完全由 Manager 可观测值（服务运行 / 已配对会话存在 / 配对设备名）推导，
// 锁定输入组合 → 模式 / 连接状态 / 副卡卡面的映射与防御性规则。
// 决策 35 修订：「已连接」判定 = 存在已配对会话（token 滚动签发后配对成功即出新码，
// token 状态不再是「已连接」判定源）；断连（会话关、token 仍有效）回等待扫码卡。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteUiStateTest {

    @Test
    fun serviceNotRunningYieldsKeyboardNotConnectedGuideFace() {
        val state = RemoteUiState.of(serviceRunning = false, pairedSessionActive = true, deviceName = null)
        assertEquals(RemoteMode.KEYBOARD, state.mode)
        assertEquals(RemoteStatus.NOT_CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.GUIDE, state.sideCardFace)
    }

    @Test
    fun runningWithoutPairedSessionYieldsRemoteWaitingPairingFace() {
        // 等待扫码态：配对卡整卡本身即「等待扫码」卡面（决策 32，不加状态行）
        val state = RemoteUiState.of(serviceRunning = true, pairedSessionActive = false, deviceName = null)
        assertEquals(RemoteMode.REMOTE, state.mode)
        assertEquals(RemoteStatus.WAITING_SCAN, state.status)
        assertEquals(RemoteSideCardFace.PAIRING, state.sideCardFace)
        assertNull("等待态不携带设备名", state.deviceName)
    }

    @Test
    fun runningWithPairedSessionYieldsConnectedFaceWithDeviceName() {
        // 已连接态（决策 35）：二维码卡保留（滚动新码），网址行原位换绿点状态行 + 设备名
        val state = RemoteUiState.of(serviceRunning = true, pairedSessionActive = true, deviceName = "browser")
        assertEquals(RemoteMode.REMOTE, state.mode)
        assertEquals(RemoteStatus.CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.CONNECTED, state.sideCardFace)
        assertEquals("browser", state.deviceName)
    }

    @Test
    fun connectedStateKeepsNullDeviceNameWhenHelloLackedIt() {
        // hello 未携带 deviceName：仍为已连接态（UI 只显示「已连接」），卡面不回退
        val state = RemoteUiState.of(serviceRunning = true, pairedSessionActive = true, deviceName = null)
        assertEquals(RemoteStatus.CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.CONNECTED, state.sideCardFace)
        assertNull(state.deviceName)
    }

    @Test
    fun pairedSessionActiveTakesPrecedenceOverTokenState() {
        // 决策 35：配对成功后 token 滚动签发（有效）且有已配对会话 → 仍是已连接，不误判等待态
        val state = RemoteUiState.of(serviceRunning = true, pairedSessionActive = true, deviceName = "x")
        assertEquals(RemoteStatus.CONNECTED, state.status)
    }

    @Test
    fun pairedSessionCloseWithFreshTokenYieldsBackToWaitingScan() {
        // 决策 35：已配对连接断开（非被顶替）→ 滚动新码仍有效 → 副卡回等待扫码卡
        val state = RemoteUiState.of(serviceRunning = true, pairedSessionActive = false, deviceName = "browser")
        assertEquals(RemoteStatus.WAITING_SCAN, state.status)
        assertEquals(RemoteSideCardFace.PAIRING, state.sideCardFace)
        assertNull("断连回等待态不展示残留设备名", state.deviceName)
    }

    @Test
    fun stoppedServiceIgnoresLingeringDeviceName() {
        // 防御：服务已停止但设备名尚未清理（异常时序）——键盘态/未连接不展示残留值
        val state = RemoteUiState.of(serviceRunning = false, pairedSessionActive = false, deviceName = "browser")
        assertEquals(RemoteMode.KEYBOARD, state.mode)
        assertEquals(RemoteStatus.NOT_CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.GUIDE, state.sideCardFace)
        assertNull(state.deviceName)
    }

    @Test
    fun statusToFaceMappingIsOneToOne() {
        // FR-B9 三态 → 三卡面一一对应（三态各自推导卡面）
        val cases = mapOf(
            RemoteStatus.NOT_CONNECTED to RemoteSideCardFace.GUIDE,
            RemoteStatus.WAITING_SCAN to RemoteSideCardFace.PAIRING,
            RemoteStatus.CONNECTED to RemoteSideCardFace.CONNECTED,
        )
        for ((status, face) in cases) {
            val state = RemoteUiState.of(
                serviceRunning = status != RemoteStatus.NOT_CONNECTED,
                pairedSessionActive = status == RemoteStatus.CONNECTED,
                deviceName = null,
            )
            assertEquals(status, state.status)
            assertEquals("状态 $status 应对应卡面 $face", face, state.sideCardFace)
        }
    }

    @Test
    fun keyboardModeImpliesNotConnectedAndRemoteModeImpliesLiveStatus() {
        // 模式与连接状态一致性：键盘态恒未连接；远程模式恒为等待 / 已连接之一
        val keyboard = RemoteUiState.of(serviceRunning = false, pairedSessionActive = false, deviceName = null)
        assertEquals(RemoteMode.KEYBOARD, keyboard.mode)
        assertEquals(RemoteStatus.NOT_CONNECTED, keyboard.status)

        val waiting = RemoteUiState.of(serviceRunning = true, pairedSessionActive = false, deviceName = null)
        val connected = RemoteUiState.of(serviceRunning = true, pairedSessionActive = true, deviceName = "x")
        for (state in listOf(waiting, connected)) {
            assertEquals(RemoteMode.REMOTE, state.mode)
        }
    }
}
