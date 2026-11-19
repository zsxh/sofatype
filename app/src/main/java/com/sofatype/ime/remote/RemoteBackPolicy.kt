package com.sofatype.ime.remote

object RemoteBackPolicy {

    enum class Action {

        EXIT_REMOTE,

        HIDE_KEEP_ALIVE,

        KEYBOARD_DEFAULT,
    }

    fun resolve(
        serviceRunning: Boolean,
        pairedSessionActive: Boolean,
        inputViewShown: Boolean,
    ): Action = when {
        !serviceRunning -> Action.KEYBOARD_DEFAULT
        !pairedSessionActive -> Action.EXIT_REMOTE
        inputViewShown -> Action.HIDE_KEEP_ALIVE
        else -> Action.KEYBOARD_DEFAULT
    }
}
