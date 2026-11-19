package com.sofatype.ime

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import timber.log.Timber

class ImeTakeoverPrompt(private val activity: Activity) {

    private val banner: View = activity.findViewById(R.id.ime_takeover_banner)
    private val handler = Handler(Looper.getMainLooper())

    init {
        activity.findViewById<Button>(R.id.btn_recover_ime).setOnClickListener { showPicker() }
    }

    fun refresh() {
        handler.post {
            val current = currentDefaultIme()
            val takeover = ImeTakeoverCheck.needsPrompt(current)
            banner.visibility = if (takeover) View.VISIBLE else View.GONE
            Timber.tag(LOG_TAG).i("默认输入法检测 current=%s 夺回态=%s", current ?: "null", takeover)
        }
    }

    private fun showPicker() {
        val imm = getSystemServiceImm()
        if (imm != null) {
            Timber.tag(LOG_TAG).i("一键选回：打开系统输入法选择器")
            imm.showInputMethodPicker()
        } else {
            Timber.tag(LOG_TAG).i("一键选回失败：InputMethodManager 缺失")
        }
    }

    private fun currentDefaultIme(): String? =
        Settings.Secure.getString(
            activity.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD,
        )

    private fun getSystemServiceImm(): InputMethodManager? =
        activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    private companion object {

        const val LOG_TAG = "ImeTakeover"
    }
}
