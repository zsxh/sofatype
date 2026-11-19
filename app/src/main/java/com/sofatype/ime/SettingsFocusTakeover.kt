package com.sofatype.ime

object SettingsFocusTakeover {

    const val IME_FRAME_THRESHOLD_PX = 80

    fun isImeShown(visibleFrameBottom: Int, screenHeightPx: Int): Boolean =
        visibleFrameBottom < screenHeightPx - IME_FRAME_THRESHOLD_PX

    fun shouldTakeFocus(imeShown: Boolean, editorHasFocus: Boolean): Boolean =
        !imeShown && !editorHasFocus

    fun <T> takeoverTarget(focusedEditor: T?, firstEditor: T): T = focusedEditor ?: firstEditor
}
