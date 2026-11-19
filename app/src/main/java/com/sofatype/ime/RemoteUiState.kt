package com.sofatype.ime

import com.sofatype.ime.remote.ClientCode

enum class RemoteStatus {

    NOT_CONNECTED,

    WAITING_SCAN,

    CONNECTED,
}

enum class RemoteMode { KEYBOARD, REMOTE }

enum class RemoteSideCardFace {

    GUIDE,

    PAIRING,

    CONNECTED,
}

data class RemoteUiState(
    val mode: RemoteMode,
    val status: RemoteStatus,

    val clientCode: ClientCode?,
) {

    val sideCardFace: RemoteSideCardFace = when (status) {
        RemoteStatus.NOT_CONNECTED -> RemoteSideCardFace.GUIDE
        RemoteStatus.WAITING_SCAN -> RemoteSideCardFace.PAIRING
        RemoteStatus.CONNECTED -> RemoteSideCardFace.CONNECTED
    }

    companion object {

        fun of(
            serviceRunning: Boolean,
            pairedSessionActive: Boolean,
            clientCode: ClientCode?,
        ): RemoteUiState = when {
            !serviceRunning -> RemoteUiState(RemoteMode.KEYBOARD, RemoteStatus.NOT_CONNECTED, null)
            pairedSessionActive -> RemoteUiState(RemoteMode.REMOTE, RemoteStatus.CONNECTED, clientCode)
            else -> RemoteUiState(RemoteMode.REMOTE, RemoteStatus.WAITING_SCAN, null)
        }
    }
}
