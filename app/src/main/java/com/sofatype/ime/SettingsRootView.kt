package com.sofatype.ime

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.FrameLayout

class SettingsRootView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var preImeKeyHandler: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (preImeKeyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEventPreIme(event)
    }
}
