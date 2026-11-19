package com.sofatype.ime.remote

class RemoteIdlePolicy(

    val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
) {

    init {
        require(idleTimeoutMs > 0) { "空闲阈值必须为正数：$idleTimeoutMs" }
    }

    private var idleSinceMs: Long? = null

    fun onIdleStarted(nowMs: Long) {
        idleSinceMs = nowMs
    }

    fun onIdleEnded() {
        idleSinceMs = null
    }

    fun shouldExit(nowMs: Long): Boolean {
        val since = idleSinceMs ?: return false
        return nowMs - since >= idleTimeoutMs
    }

    companion object {

        const val DEFAULT_IDLE_TIMEOUT_MS = 30L * 60L * 1000L
    }
}
