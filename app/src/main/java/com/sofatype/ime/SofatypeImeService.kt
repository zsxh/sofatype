// T3 · 最小可用输入法服务（FR-A1 最小闭环）。
// 占位键盘仅两个键：示例字符键（上屏 1 个字符）+ 下屏键（收起）。
// 范围边界：真实键盘布局、样式、候选词属 T5+，勿在此扩展。
package com.sofatype.ime

import android.inputmethodservice.InputMethodService
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout

class SofatypeImeService : InputMethodService() {

    /**
     * TV 恒横屏：默认的全屏提取模式会用自带编辑界面盖住宿主应用
     * （T2 测试输入框将不可见，验收链路走不通），恒关闭。
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            // 占位样式：仅取卡片底色 Token；圆角/间距等真实样式属 T5
            setBackgroundColor(getColor(R.color.card))
            minimumHeight = resources.getDimensionPixelSize(R.dimen.ime_placeholder_height)
        }
        for (key in PlaceholderKey.entries) {
            row.addView(createKeyButton(key, isLast = key == PlaceholderKey.entries.last()))
        }
        return row
    }

    private fun createKeyButton(key: PlaceholderKey, isLast: Boolean): Button {
        val gap = resources.getDimensionPixelSize(R.dimen.ime_key_gap)
        val button = Button(this)
        button.text = keyLabel(key)
        button.contentDescription = keyDescription(key)
        button.setOnClickListener { execute(PlaceholderKeyboard.actionFor(key)) }
        button.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            if (!isLast) marginEnd = gap
        }
        return button
    }

    /**
     * 执行按键动作。无焦点连接时静默忽略，不崩溃
     * （可靠性要求：输入法异常不得阻塞宿主应用）。
     */
    private fun execute(action: KeyAction) {
        when (action) {
            is KeyAction.CommitText -> currentInputConnection?.commitText(action.text, 1)
            KeyAction.HideSelf -> requestHideSelf(0)
        }
    }

    private fun keyLabel(key: PlaceholderKey): String = getString(
        when (key) {
            PlaceholderKey.SAMPLE -> R.string.ime_key_sample
            PlaceholderKey.DISMISS -> R.string.ime_key_dismiss
        },
    )

    private fun keyDescription(key: PlaceholderKey): String = getString(
        when (key) {
            PlaceholderKey.SAMPLE -> R.string.cd_ime_key_sample
            PlaceholderKey.DISMISS -> R.string.cd_ime_key_dismiss
        },
    )
}
