package com.sofatype.ime

import com.sofatype.ime.remote.ClientCode
import com.sofatype.ime.remote.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteUiStateTest {

    private val code = ClientCode(DeviceType.WECHAT, 1)

    @Test
    fun serviceNotRunningYieldsKeyboardNotConnectedGuideFace() {
        val state = RemoteUiState.of(serviceRunning = false, pairedSessionActive = true, clientCode = code)
        assertEquals(RemoteMode.KEYBOARD, state.mode)
        assertEquals(RemoteStatus.NOT_CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.GUIDE, state.sideCardFace)
    }

    @Test
    fun runningWithoutPairedSessionYieldsRemoteWaitingPairingFace() {

        val state = RemoteUiState.of(serviceRunning = true, pairedSessionActive = false, clientCode = code)
        assertEquals(RemoteMode.REMOTE, state.mode)
        assertEquals(RemoteStatus.WAITING_SCAN, state.status)
        assertEquals(RemoteSideCardFace.PAIRING, state.sideCardFace)
        assertNull("等待态不携带连接代号", state.clientCode)
    }

    @Test
    fun runningWithPairedSessionYieldsConnectedFaceWithClientCode() {

        val state = RemoteUiState.of(serviceRunning = true, pairedSessionActive = true, clientCode = code)
        assertEquals(RemoteMode.REMOTE, state.mode)
        assertEquals(RemoteStatus.CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.CONNECTED, state.sideCardFace)
        assertEquals(code, state.clientCode)
    }

    @Test
    fun connectedStateKeepsNullClientCodeInDefensiveBranch() {

        val state = RemoteUiState.of(serviceRunning = true, pairedSessionActive = true, clientCode = null)
        assertEquals(RemoteStatus.CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.CONNECTED, state.sideCardFace)
        assertNull(state.clientCode)
    }

    @Test
    fun pairedSessionActiveTakesPrecedenceOverTokenState() {

        val state = RemoteUiState.of(serviceRunning = true, pairedSessionActive = true, clientCode = code)
        assertEquals(RemoteStatus.CONNECTED, state.status)
    }

    @Test
    fun pairedSessionCloseWithFreshTokenYieldsBackToWaitingScan() {

        val state = RemoteUiState.of(serviceRunning = true, pairedSessionActive = false, clientCode = code)
        assertEquals(RemoteStatus.WAITING_SCAN, state.status)
        assertEquals(RemoteSideCardFace.PAIRING, state.sideCardFace)
        assertNull("断连回等待态不展示残留连接代号", state.clientCode)
    }

    @Test
    fun stoppedServiceIgnoresLingeringClientCode() {

        val state = RemoteUiState.of(serviceRunning = false, pairedSessionActive = false, clientCode = code)
        assertEquals(RemoteMode.KEYBOARD, state.mode)
        assertEquals(RemoteStatus.NOT_CONNECTED, state.status)
        assertEquals(RemoteSideCardFace.GUIDE, state.sideCardFace)
        assertNull(state.clientCode)
    }

    @Test
    fun statusToFaceMappingIsOneToOne() {

        val cases = mapOf(
            RemoteStatus.NOT_CONNECTED to RemoteSideCardFace.GUIDE,
            RemoteStatus.WAITING_SCAN to RemoteSideCardFace.PAIRING,
            RemoteStatus.CONNECTED to RemoteSideCardFace.CONNECTED,
        )
        for ((status, face) in cases) {
            val state = RemoteUiState.of(
                serviceRunning = status != RemoteStatus.NOT_CONNECTED,
                pairedSessionActive = status == RemoteStatus.CONNECTED,
                clientCode = null,
            )
            assertEquals(status, state.status)
            assertEquals("状态 $status 应对应卡面 $face", face, state.sideCardFace)
        }
    }

    @Test
    fun keyboardModeImpliesNotConnectedAndRemoteModeImpliesLiveStatus() {

        val keyboard = RemoteUiState.of(serviceRunning = false, pairedSessionActive = false, clientCode = null)
        assertEquals(RemoteMode.KEYBOARD, keyboard.mode)
        assertEquals(RemoteStatus.NOT_CONNECTED, keyboard.status)

        val waiting = RemoteUiState.of(serviceRunning = true, pairedSessionActive = false, clientCode = null)
        val connected = RemoteUiState.of(serviceRunning = true, pairedSessionActive = true, clientCode = code)
        for (state in listOf(waiting, connected)) {
            assertEquals(RemoteMode.REMOTE, state.mode)
        }
    }
}
