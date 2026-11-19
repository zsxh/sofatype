package com.sofatype.ime.remote

class RemoteConnectedIdlePolicy(

    val connectedIdleTimeoutMs: Long = DEFAULT_CONNECTED_IDLE_TIMEOUT_MS,
) {

    init {
        require(connectedIdleTimeoutMs > 0) { "已连接无活动阈值必须为正数：$connectedIdleTimeoutMs" }
    }

    private var lastActivityMs: Long? = null

    fun onActivity(nowMs: Long) {
        lastActivityMs = nowMs
    }

    fun onSessionEnded() {
        lastActivityMs = null
    }

    fun shouldExit(nowMs: Long): Boolean {
        val since = lastActivityMs ?: return false
        return nowMs - since >= connectedIdleTimeoutMs
    }

    companion object {

        const val DEFAULT_CONNECTED_IDLE_TIMEOUT_MS = 10L * 60L * 1000L
    }
}
