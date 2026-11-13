// T63 · FR-D1 本批次（docs/PRD_20261112.md）：独立测试打字页。
// 自设置页顶部调试走查区整体迁入三件套（测试输入框 + 填入示例文本 + 全选），
// 门控沿用决策 21（BuildConfig.DEBUG 可见性，核对记录见 TestTypingGate 与 issue T63）。
// T54 Round 2 机制随迁（原接线在 SettingsActivity debug 块，承载面随调试区移至本页）：
// - pre-IME 键分路（SettingsRootView + SettingsKeyRouter）：键盘显示态透传 IME，
//   隐藏态按宿主语义路由——方向键=FocusFinder 焦点搜索、OK=焦点控件激活（EditText 分流
//   showSoftInput，T59 拍板 5 口径）、BACK=结束本页返回设置页；
// - 焦点接管（SettingsFocusTakeover）：键盘收起沿把焦点移回 test_input，保持输入会话绑定
//   （pre-IME 分路通道保活），仅可见性跳变沿执行；
// - T62 输入法切换广播：收到 ACTION_INPUT_METHOD_CHANGED 即 restartInput 重建输入会话
//   （IC 输出黑洞修复），随窗口销毁注销（零残留，AGENTS 禁止事项 3）。
// 无 LAUNCHER 入口（清单未配 intent-filter，不从桌面可达）；BACK 返回设置页。
// SofatypeImeService 零改动（T59 拍板口径不触碰）。
package com.sofatype.ime

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText

class TestTypingActivity : Activity() {

    /** T54 Round 2 · 焦点接管全局布局监听（接线见 onCreate，onDestroy 移除）。 */
    private var focusTakeoverListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    /** T54 Round 2 · 输入法可见性去重（可见性日志仅在跳变沿写一条）。 */
    private var lastImeShown: Boolean? = null

    /** T62 · 输入法切换广播接收（收到即重建输入会话；onDestroy 注销）。 */
    private var imeChangedReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_typing)

        val content = findViewById<View>(R.id.test_typing_content)
        // 决策 21 门控沿用（T63 迁移，口径见 TestTypingGate）：测试三件套仅 debug 构建显示
        if (TestTypingGate.contentVisible(BuildConfig.DEBUG)) {
            content.visibility = View.VISIBLE
            val input = findViewById<EditText>(R.id.test_input)
            // 「填入示例文本」：预置确定性文本，供键盘/上屏验收使用（原设置页调试区行为原样迁入）
            findViewById<Button>(R.id.btn_fill_sample).setOnClickListener {
                input.setText(R.string.sample_text)
                input.setSelection(input.text.length)
            }
            // 「全选」：制造选中区间，供替换选中区间类验收使用（原设置页调试区行为原样迁入）
            findViewById<Button>(R.id.btn_select_all).setOnClickListener {
                input.selectAll()
            }
            // 初始焦点落测试输入框：遥控 OK 直达唤起键盘链路（FR-A1 在可输入焦点处确认后弹键盘）
            input.requestFocus()

            // T54 Round 2 · 焦点接管（T20 P1 落地，docs/issues/open/T54.md 机制原样迁入）；
            // T54 缺陷修复 Round 1 修订接管目标（2026-11-12 真机 FAIL 项，设备档案坑 23）：
            // tap 按钮使焦点离开编辑框、IMM 输入会话解绑（API 23 视图级绑定）——会话解绑后
            // 键码不再经宿主窗口 pre-IME 分路。收起检测走 adjustResize 窗口缩放
            // （Manifest windowSoftInputMode）：可见底部帧回到全屏高度即判键盘已收起；
            // 仅在可见性跳变沿（收起沿）执行接管——把焦点移回 test_input，保持输入会话绑定；
            // 非跳变沿的布局变化不接管，防抢走用户已移到按钮上的焦点。API 23/34 通用。
            focusTakeoverListener = ViewTreeObserver.OnGlobalLayoutListener {
                val frame = Rect()
                window.decorView.getWindowVisibleDisplayFrame(frame)
                val imeShown = SettingsFocusTakeover.isImeShown(
                    frame.bottom,
                    resources.displayMetrics.heightPixels,
                )
                if (imeShown != lastImeShown) {
                    Log.i(FOCUS_LOG_TAG, "输入法可见性 imeShown=$imeShown visibleBottom=${frame.bottom}")
                    if (!imeShown) {
                        // T54 缺陷修复：收起沿焦点回编辑框（输入会话保活），并退出触摸模式——
                        // tap 链路遗留的触摸模式会让 FocusFinder 焦点搜索移焦静默失效。
                        // requestFocusFromTouch 对已持焦点的编辑框 = 仅退出触摸模式，焦点不变。
                        if (SettingsFocusTakeover.shouldTakeFocus(imeShown, input.hasFocus())) {
                            Log.i(FOCUS_LOG_TAG, "焦点接管：键盘已收起，焦点回 test_input（输入会话保活）")
                        }
                        input.requestFocusFromTouch()
                    }
                    lastImeShown = imeShown
                }
            }
            window.decorView.viewTreeObserver.addOnGlobalLayoutListener(focusTakeoverListener)

            // T62 · IC 输出黑洞修复（docs/issues/open/T62.md，设备档案坑 22）机制原样迁入：
            // default_input_method 被夺回后 `ime set` 切回会重启 IME 服务实例，既有输入会话的
            // IC 输出不落宿主输入框。修复 = 监听输入法切换系统广播，收到即 restartInput
            // 重建输入会话（新 IC），输出恢复落框。仅 debug 接线（三件套为 debug 门控，
            // release 页无输入框、黑洞面不存在）。
            val imeChangedFilter = IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED)
            imeChangedReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    Log.i(FOCUS_LOG_TAG, "输入法切换广播 -> 重建输入会话（T62）")
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.restartInput(input)
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

        // T54 Round 2 · pre-IME 键分路接线（与设置页同构，BACK 语义 = 结束本页返回设置页）：
        // 键盘隐藏态遥控键按宿主语义路由；显示态透传 IME（打字与键盘内导航零改动）。
        findViewById<SettingsRootView>(R.id.test_typing_root).preImeKeyHandler = { handlePreImeKey(it) }
    }

    override fun onDestroy() {
        // T62 · 输入法切换广播接收随窗口销毁注销（退出零残留，AGENTS 禁止事项 3）
        imeChangedReceiver?.let { unregisterReceiver(it) }
        imeChangedReceiver = null
        // T54 Round 2 · 焦点接管监听随窗口销毁移除（退出零残留，AGENTS 禁止事项 3）
        focusTakeoverListener?.let { listener ->
            window.decorView.viewTreeObserver.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(listener)
        }
        focusTakeoverListener = null
        super.onDestroy()
    }

    // ═══ T54 Round 2 · pre-IME 键分路（与 SettingsActivity 同构；BACK = 返回设置页） ═══

    /**
     * pre-IME 键裁决：键盘显示态透传（打字链路零改动）；隐藏态宿主语义路由。
     * T63 修正（与 SettingsActivity 同修）：动作仅在 DOWN 执行，UP 仅消费——按键对完整、
     * 不漏进 IME 链路（T54 R2 口径保持），消除遥控 OK 单击二次 performClick / finish。
     */
    private fun handlePreImeKey(event: KeyEvent): Boolean {
        if (lastImeShown == true) return false
        val route = SettingsKeyRouter.route(event.keyCode)
        // 非 DOWN（UP 等）不执行动作：透传键（PASS）交回框架，动作键仅消费（按键对完整）
        if (event.action != KeyEvent.ACTION_DOWN) return route != SettingsKeyRouter.Route.PASS
        return when (route) {
            SettingsKeyRouter.Route.BACK -> {
                Log.i(FOCUS_LOG_TAG, "pre-IME BACK：返回设置页")
                finish()
                true
            }
            SettingsKeyRouter.Route.CONFIRM -> {
                // T59（户主拍板 2026-11-11）分流口径与设置页一致：焦点在可输入控件（EditText）
                // 唤起键盘（showSoftInput）；其余控件激活焦点控件（performClick）。
                val focused = window.decorView.findFocus()
                if (SettingsKeyRouter.confirmActivatesIme(focused) && focused is EditText) {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    val shown = imm?.showSoftInput(focused, InputMethodManager.SHOW_IMPLICIT) ?: false
                    Log.i(FOCUS_LOG_TAG, "pre-IME CONFIRM：焦点在输入框 -> showSoftInput=$shown")
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
        /** 焦点接管与输入法可见性日志（走查取证用，TAG 沿用 SettingsFocus 取证锚点不变）。 */
        const val FOCUS_LOG_TAG = "SettingsFocus"
    }
}
