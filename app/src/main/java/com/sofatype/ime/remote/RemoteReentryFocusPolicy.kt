package com.sofatype.ime.remote

class RemoteReentryFocusPolicy {

    enum class ReentryFocus {

        EXIT_BUTTON,

        NONE,
    }

    private var pending = false

    fun onKeepAliveHide() {
        pending = true
    }

    fun onExplicitWake() {
        pending = false
    }

    fun resolveOnShow(): ReentryFocus {
        if (!pending) return ReentryFocus.EXIT_BUTTON
        pending = false
        return ReentryFocus.NONE
    }

    fun reset() {
        pending = false
    }
}
