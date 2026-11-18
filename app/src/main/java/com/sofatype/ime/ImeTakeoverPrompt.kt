// T82 · 决策 66 · B2 设置页检测引导（修复段 Round 1）：三个设置页 Activity
//（SettingsActivity / LogSettingsActivity / LogViewerActivity）共用的检测接线件。
// onResume / 窗口重新获得焦点时读 Settings.Secure.DEFAULT_INPUT_METHOD（公开读取零权限），
// 夺回态（ImeTakeoverCheck.needsPrompt = true）显示页面横幅提示；「一键选回」按钮调
// InputMethodManager.showInputMethodPicker()（公开 API 零权限）打开系统输入法选择器，
// 用户选回 sofatype 后横幅随窗口焦点回归自动隐藏。默认态横幅恒 GONE：布局零位移、
// 行为零变化。日志仅记检测读值与按钮动作（TAG=ImeTakeover，走查取证用，不含用户输入内容）。
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

/**
 * 设置页默认输入法夺回检测横幅（T82）。
 *
 * @param activity 宿主设置页 Activity（三页布局各含同构横幅：id = ime_takeover_banner，
 * 按钮id = btn_recover_ime）；须在 setContentView 之后构造。
 */
class ImeTakeoverPrompt(private val activity: Activity) {

    private val banner: View = activity.findViewById(R.id.ime_takeover_banner)
    private val handler = Handler(Looper.getMainLooper())

    init {
        activity.findViewById<Button>(R.id.btn_recover_ime).setOnClickListener { showPicker() }
    }

    /**
     * 检测刷新：onResume 与窗口重新获得焦点（onWindowFocusChanged=true）时由宿主调用。
     * 夺回态显示横幅、默认态隐藏（横幅默认布局即 GONE，默认态零变化）。
     * 焦点回归沿覆盖「选回后横幅即时消失」——系统选择器关闭不触发宿主 onResume，
     * 仅在焦点回到本页时刷新。
     */
    fun refresh() {
        handler.post {
            val current = currentDefaultIme()
            val takeover = ImeTakeoverCheck.needsPrompt(current)
            banner.visibility = if (takeover) View.VISIBLE else View.GONE
            Timber.tag(LOG_TAG).i("默认输入法检测 current=%s 夺回态=%s", current ?: "null", takeover)
        }
    }

    /** 「一键选回」：打开系统输入法选择器（公开 API，零权限），选回动作由系统选择器承载。 */
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
        /** 走查取证日志 TAG（检测读值 + 按钮动作，均非用户输入内容）。 */
        const val LOG_TAG = "ImeTakeover"
    }
}
