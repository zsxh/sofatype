package com.sofatype.ime

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import timber.log.Timber

class TestTypingActivity : Activity() {

    private var focusTakeoverListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private var lastImeShown: Boolean? = null

    private var imeChangedReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_typing)

        val content = findViewById<View>(R.id.test_typing_content)

        if (TestTypingGate.contentVisible(BuildConfig.DEBUG)) {
            content.visibility = View.VISIBLE
            val input = findViewById<EditText>(R.id.test_input)
            val input2 = findViewById<EditText>(R.id.test_input_2)

            val editors = listOf(input, input2)

            findViewById<Button>(R.id.btn_fill_sample).setOnClickListener {
                input.setText(R.string.sample_text)
                input.setSelection(input.text.length)
            }

            findViewById<Button>(R.id.btn_select_all).setOnClickListener {
                input.selectAll()
            }

            input.requestFocus()

            focusTakeoverListener = ViewTreeObserver.OnGlobalLayoutListener {
                val frame = Rect()
                window.decorView.getWindowVisibleDisplayFrame(frame)
                val imeShown = SettingsFocusTakeover.isImeShown(
                    frame.bottom,
                    resources.displayMetrics.heightPixels,
                )
                if (imeShown != lastImeShown) {
                    Timber.tag(FOCUS_LOG_TAG).i("输入法可见性 imeShown=$imeShown visibleBottom=${frame.bottom}")
                    if (!imeShown) {

                        val focusedEditor = editors.firstOrNull { it.hasFocus() }
                        val target = SettingsFocusTakeover.takeoverTarget(focusedEditor, input)
                        if (SettingsFocusTakeover.shouldTakeFocus(imeShown, focusedEditor != null)) {
                            Timber.tag(FOCUS_LOG_TAG).i("焦点接管：键盘已收起，焦点回 test_input（输入会话保活）")
                        } else if (target === input2) {
                            Timber.tag(FOCUS_LOG_TAG).i("焦点保持：键盘已收起，焦点仍在 test_input_2（输入会话保活）")
                        }
                        target.requestFocusFromTouch()
                    }
                    lastImeShown = imeShown
                }
            }
            window.decorView.viewTreeObserver.addOnGlobalLayoutListener(focusTakeoverListener)

            val imeChangedFilter = IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED)
            imeChangedReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    Timber.tag(FOCUS_LOG_TAG).i("输入法切换广播 -> 重建输入会话（T62）")
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

                    val restartTarget = SettingsFocusTakeover.takeoverTarget(
                        editors.firstOrNull { it.hasFocus() },
                        input,
                    )
                    imm?.restartInput(restartTarget)
                }
            }
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(imeChangedReceiver, imeChangedFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(imeChangedReceiver, imeChangedFilter)
            }
        } else {
            content.visibility = View.GONE
        }

        findViewById<SettingsRootView>(R.id.test_typing_root).preImeKeyHandler = { handlePreImeKey(it) }
    }

    override fun onDestroy() {

        imeChangedReceiver?.let { unregisterReceiver(it) }
        imeChangedReceiver = null

        focusTakeoverListener?.let { listener ->
            window.decorView.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(listener)
        }
        focusTakeoverListener = null
        super.onDestroy()
    }

    private fun handlePreImeKey(event: KeyEvent): Boolean {
        if (lastImeShown == true) return false
        val route = SettingsKeyRouter.route(event.keyCode)

        if (event.action != KeyEvent.ACTION_DOWN) return route != SettingsKeyRouter.Route.PASS
        return when (route) {
            SettingsKeyRouter.Route.BACK -> {
                Timber.tag(FOCUS_LOG_TAG).i("pre-IME BACK：返回设置页")
                finish()
                true
            }
            SettingsKeyRouter.Route.CONFIRM -> {

                val focused = window.decorView.findFocus()
                if (SettingsKeyRouter.confirmActivatesIme(focused) && focused is EditText) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    val shown = imm?.showSoftInput(focused, InputMethodManager.SHOW_IMPLICIT) ?: false
                    Timber.tag(FOCUS_LOG_TAG).i("pre-IME CONFIRM：焦点在输入框 -> showSoftInput=$shown")
                } else {
                    focused?.performClick()
                }
                true
            }
            SettingsKeyRouter.Route.PASS -> false
            else -> {
                val root = window.decorView as? ViewGroup ?: return false
                val focused = root.findFocus() ?: return false
                FocusFinder.getInstance().findNextFocus(
                    root,
                    focused,
                    SettingsKeyRouter.focusDirection(route),
                )?.requestFocusFromTouch()
                true
            }
        }
    }

    private companion object {

        const val FOCUS_LOG_TAG = "SettingsFocus"
    }
}
