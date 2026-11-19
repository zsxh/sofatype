package com.sofatype.ime

class ImeShowGuard(private val showGuardMs: Long, private val wakeGuardMs: Long) {

    private var showGuardUntilMs = Long.MIN_VALUE

    private var wakeGuardUntilMs = Long.MIN_VALUE

    fun onHideRequested(nowMs: Long) {
        showGuardUntilMs = nowMs + showGuardMs
    }

    fun shouldVetoShowRequest(nowMs: Long): Boolean = nowMs < showGuardUntilMs

    fun shouldRehideOnShown(nowMs: Long): Boolean = nowMs < showGuardUntilMs

    fun onWindowShown(nowMs: Long) {
        wakeGuardUntilMs = nowMs + wakeGuardMs
    }

    fun shouldSwallowConfirmKey(nowMs: Long): Boolean {
        if (nowMs >= wakeGuardUntilMs) return false
        wakeGuardUntilMs = Long.MIN_VALUE
        return true
    }
}
