// T71 · 决策 60 画板 14 · 日志子页：①开启调试日志开关（默认关）②日志大小限制四档
// （默认 2 MB，选中档亮青加粗 + 3px 下划线，决策 47 触发态口径）③查看日志入口；
// 开关关闭时②③置灰不可调（已写文件保留，清空只走查看页按钮）；所有变更立即生效。
// 遥控焦点路由沿设置页既有 pre-IME 分路（T54/T59/T67 口径，SettingsRootView 接线）：
// 上下键移焦、OK 切换开关 / 进查看页、左右键调档、BACK 返回设置页（决策 60「BACK 逐级返回」）。
// 无障碍：三个行容器均有 contentDescription（状态随动，可读性要求）。
// 用量提示（当前 X MB / Y MB）读文件长度在后台线程执行（严禁主线程磁盘 IO）。
package com.sofatype.ime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.sofatype.ime.debug.DebugLogController
import com.sofatype.ime.debug.DebugLogOptions
import com.sofatype.ime.debug.DebugLogStore
import com.sofatype.ime.debug.LogUiText
import java.io.File
import java.util.concurrent.Executors

class LogSettingsActivity : Activity() {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var destroyed = false

    private lateinit var rowSwitch: View
    private lateinit var rowSize: View
    private lateinit var rowView: View
    private lateinit var logSwitch: LogSwitchView
    private lateinit var usageHint: TextView
    private lateinit var btnViewLogs: Button
    private lateinit var sizeOptionBoxes: List<View>
    private lateinit var sizeOptionTexts: List<TextView>
    private lateinit var sizeOptionBars: List<View>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_settings)

        rowSwitch = findViewById(R.id.row_log_switch)
        rowSize = findViewById(R.id.row_log_size)
        rowView = findViewById(R.id.row_log_view)
        logSwitch = findViewById(R.id.log_switch)
        usageHint = findViewById(R.id.log_usage_hint)
        btnViewLogs = findViewById(R.id.btn_view_logs)
        sizeOptionBoxes = listOf(
            findViewById(R.id.log_size_opt_1),
            findViewById(R.id.log_size_opt_2),
            findViewById(R.id.log_size_opt_5),
            findViewById(R.id.log_size_opt_10),
        )
        sizeOptionTexts = listOf(
            findViewById(R.id.log_size_text_1),
            findViewById(R.id.log_size_text_2),
            findViewById(R.id.log_size_text_5),
            findViewById(R.id.log_size_text_10),
        )
        sizeOptionBars = listOf(
            findViewById(R.id.log_size_bar_1),
            findViewById(R.id.log_size_bar_2),
            findViewById(R.id.log_size_bar_5),
            findViewById(R.id.log_size_bar_10),
        )

        rowSwitch.setOnClickListener { toggleSwitch() }
        rowSize.setOnClickListener { /* 档位行：左右键调档，OK 无动作（画板 14 提示口径） */ }
        rowView.setOnClickListener { openViewer() }
        btnViewLogs.setOnClickListener { openViewer() }

        // pre-IME 键分路（与设置页 / 测试打字页同构）：本页无输入框，键盘隐藏态语义全部在此路由
        findViewById<SettingsRootView>(R.id.log_settings_root).preImeKeyHandler = { handlePreImeKey(it) }

        refreshUi()
    }

    override fun onResume() {
        super.onResume()
        // 返回本页（查看页清空后 / 设置页重进）刷新行态与用量提示
        refreshUi()
    }

    override fun onDestroy() {
        destroyed = true
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onBackPressed() {
        // BACK 返回设置页（决策 60「BACK 逐级返回」；pre-IME 分路 BACK 与默认 BACK 同路径）
        finish()
    }

    // ═══ 交互动作 ═══

    private fun toggleSwitch() {
        DebugLogController.setEnabled(this, !DebugLogStore.isEnabled)
        refreshUi()
    }

    private fun adjustSize(step: Int) {
        val choices = DebugLogOptions.MB_CHOICES
        val current = DebugLogStore.sizeMb
        val index = choices.indexOf(current).coerceAtLeast(0)
        val next = (index + step).coerceIn(0, choices.size - 1)
        if (choices[next] != current) {
            DebugLogController.setSizeMb(this, choices[next])
            refreshUi()
        }
    }

    private fun openViewer() {
        startActivity(Intent(this, LogViewerActivity::class.java))
    }

    // ═══ 呈现刷新 ═══

    /** 全页刷新：开关可视态、行 2/3 可调性（置灰）、档位选中态、行标注、用量提示。 */
    private fun refreshUi() {
        val enabled = DebugLogStore.isEnabled
        logSwitch.checked = enabled
        // ②③ 置灰不可调：焦点收回行 1，行容器禁用（bg_log_row 焦点态不再命中）
        rowSize.isEnabled = enabled
        rowView.isEnabled = enabled
        rowSize.isFocusable = enabled
        rowView.isFocusable = enabled
        if (!enabled && (currentFocus === rowSize || currentFocus === rowView)) {
            rowSwitch.requestFocus()
        }
        // 先定档位选中色，再按开关态统一置灰（关闭态置灰优先于选中高亮）
        refreshSizeSelection()
        applyGrey(enabled, sizeOptionTexts)
        applyGrey(enabled, listOf(usageHint, btnViewLogs))
        refreshRowDescriptions()
        refreshUsageHint(enabled)
    }

    /** 档位选中态：选中档 = 亮青加粗 + 3px 下划线（决策 47）；非选中档压暗、无下划线。 */
    private fun refreshSizeSelection() {
        val current = DebugLogStore.sizeMb
        val choices = DebugLogOptions.MB_CHOICES
        for (i in choices.indices) {
            val selected = choices[i] == current
            sizeOptionTexts[i].setTextColor(getColor(if (selected) R.color.accent_text else R.color.text_dim))
            sizeOptionTexts[i].setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            sizeOptionBars[i].visibility = if (selected) View.VISIBLE else View.GONE
        }
    }

    /** 行容器无障碍标注随状态刷新（可读性要求：状态 + 动作）。 */
    private fun refreshRowDescriptions() {
        val switchState = getString(if (DebugLogStore.isEnabled) R.string.log_switch_state_on else R.string.log_switch_state_off)
        rowSwitch.contentDescription = getString(R.string.cd_log_switch_row, switchState)
        rowSize.contentDescription = getString(
            R.string.cd_log_size_row,
            DebugLogOptions.label(DebugLogStore.sizeMb),
        )
    }

    /** 行 3 右侧用量提示：开启 = 文件实际用量（后台读），关闭 = 静态提示（不读盘）。 */
    private fun refreshUsageHint(enabled: Boolean) {
        if (!enabled) {
            usageHint.text = getString(R.string.log_usage_hint_off)
            return
        }
        val file: File = DebugLogController.logFile()
        ioExecutor.execute {
            val used = if (file.exists()) file.length() else 0L
            runOnUiThread {
                if (!destroyed) usageHint.text = LogUiText.usageHint(used, DebugLogStore.sizeMb)
            }
        }
    }

    private fun applyGrey(enabled: Boolean, views: List<View>) {
        val color = getColor(if (enabled) R.color.text_dim else R.color.text_hint_faint)
        for (view in views) {
            when (view) {
                is TextView -> view.setTextColor(color)
                is Button -> view.setTextColor(color)
            }
            view.alpha = if (enabled) 1f else 0.6f
        }
    }

    // ═══ pre-IME 键分路（T54/T59/T67 口径） ═══

    /**
     * 键裁决：上下键 FocusFinder 移焦；OK 行内动作（行 1 切开关 / 行 3 进查看页）；
     * 左右键仅在行 2（档位行）内调档；BACK 返回设置页。动作仅在 DOWN 执行（UP 仅消费，
     * 按键对完整，T63 修正口径）。
     */
    private fun handlePreImeKey(event: KeyEvent): Boolean {
        val route = SettingsKeyRouter.route(event.keyCode)
        if (event.action != KeyEvent.ACTION_DOWN) return route != SettingsKeyRouter.Route.PASS
        return when (route) {
            SettingsKeyRouter.Route.BACK -> {
                finish()
                true
            }
            SettingsKeyRouter.Route.CONFIRM -> {
                val focused = window.decorView.findFocus()
                if (focused === rowSwitch) {
                    toggleSwitch()
                } else if (focused === rowView) {
                    openViewer()
                }
                true
            }
            SettingsKeyRouter.Route.FOCUS_LEFT -> moveFocusOrAdjustSize(event, step = -1)
            SettingsKeyRouter.Route.FOCUS_RIGHT -> moveFocusOrAdjustSize(event, step = +1)
            else -> moveFocus(event, route)
        }
    }

    /** 左右键：焦点在档位行 = 调档；否则框架焦点搜索（本页竖排通常无横向邻位，落空即消费）。 */
    private fun moveFocusOrAdjustSize(event: KeyEvent, step: Int): Boolean {
        val focused = window.decorView.findFocus()
        if (focused === rowSize) {
            adjustSize(step)
            return true
        }
        return consumeOrMoveFocus(event, SettingsKeyRouter.route(event.keyCode))
    }

    private fun moveFocus(event: KeyEvent, route: SettingsKeyRouter.Route): Boolean =
        consumeOrMoveFocus(event, route)

    private fun consumeOrMoveFocus(event: KeyEvent, route: SettingsKeyRouter.Route): Boolean {
        val root = window.decorView as? ViewGroup ?: return true
        val focused = root.findFocus()
        val next = FocusFinder.getInstance().findNextFocus(root, focused, SettingsKeyRouter.focusDirection(route))
        next?.requestFocusFromTouch()
        // 本页键位全部宿主语义消费（不透传 IME：pre-IME 分路同口径）
        return true
    }
}
