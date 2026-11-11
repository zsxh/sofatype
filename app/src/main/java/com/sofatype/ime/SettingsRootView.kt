// T54 Round 2 · 设置页 pre-IME 键分路根视图：
// ViewRootImpl 的 ViewPreImeInputStage 在把键派发给 IME 之前先调用根视图
// dispatchKeyEventPreIme——本视图把该阶段交给 Activity 接线的分路器裁决：
// 键盘隐藏态返回 true 即消费，键不再进入 IME（隐藏态消费分支属 T59 挂账面，不改 IME）；
// 键盘显示态放行（super），打字与键盘内导航链路与既有行为一致。
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

    /** pre-IME 键裁决（SettingsActivity 接线）。返回 true=已消费，不再派发给 IME。 */
    var preImeKeyHandler: ((KeyEvent) -> Boolean)? = null

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (preImeKeyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEventPreIme(event)
    }
}
