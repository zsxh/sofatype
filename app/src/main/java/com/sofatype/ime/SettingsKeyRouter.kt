package com.sofatype.ime

import android.view.KeyEvent
import android.view.View

object SettingsKeyRouter {

    enum class Route { FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT, CONFIRM, BACK, PASS }

    fun route(keyCode: Int): Route = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> Route.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Route.FOCUS_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Route.FOCUS_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Route.FOCUS_RIGHT
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> Route.CONFIRM
        KeyEvent.KEYCODE_BACK -> Route.BACK
        else -> Route.PASS
    }

    fun focusDirection(route: Route): Int = when (route) {
        Route.FOCUS_UP -> View.FOCUS_UP
        Route.FOCUS_DOWN -> View.FOCUS_DOWN
        Route.FOCUS_LEFT -> View.FOCUS_LEFT
        Route.FOCUS_RIGHT -> View.FOCUS_RIGHT
        else -> 0
    }

    fun confirmActivatesIme(focused: View?): Boolean = focused is android.widget.EditText
}
