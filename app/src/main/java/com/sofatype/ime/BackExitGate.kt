package com.sofatype.ime

class BackExitGate(private val windowMs: Long) {

    enum class Decision { SHOW_HINT, EXIT }

    private var windowUntilMs = Long.MIN_VALUE

    fun onBack(nowMs: Long): Decision {
        if (nowMs < windowUntilMs) {

            windowUntilMs = Long.MIN_VALUE
            return Decision.EXIT
        }

        windowUntilMs = nowMs + windowMs
        return Decision.SHOW_HINT
    }

    companion object {

        const val WINDOW_MS = 2_000L
    }
}
