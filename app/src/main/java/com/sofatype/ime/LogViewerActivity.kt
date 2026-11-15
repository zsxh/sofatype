// T71 · 决策 60 画板 15 · 查看日志页：只读浏览（旧→新，进入定位最新即底部）、上下键滚动、
// [刷新] 手动刷新、[清空日志] 需确认（系统确认框，默认焦点落「取消」防误清）；无导出
//（取证走 adb pull）。BACK 返回日志子页（决策 60「BACK 逐级返回」）。
// 遥控焦点路由沿设置页既有 pre-IME 分路（T54/T59/T67 口径）：上下键 = 浏览卡滚动
//（不依赖焦点位置，画板 15 提示口径）、左右键 = [刷新]↔[清空日志] 按钮间移焦（Round 2 · B2）、
// OK = 焦点按钮激活、BACK 返回。
// 文件读取与清空后的重读全部在后台线程执行（严禁主线程磁盘 IO）。
package com.sofatype.ime

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import com.sofatype.ime.debug.DebugLogController
import com.sofatype.ime.debug.DebugLogStore
import com.sofatype.ime.debug.LogUiText
import com.sofatype.ime.debug.LogViewerContent
import java.util.concurrent.Executors

class LogViewerActivity : Activity() {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var destroyed = false

    /** 递增读取代号：刷新竞态时只让最新一轮上屏。 */
    @Volatile
    private var loadGeneration = 0

    private lateinit var logScroll: ScrollView
    private lateinit var logText: TextView
    private lateinit var statsText: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnClear: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)

        logScroll = findViewById(R.id.log_scroll)
        logText = findViewById(R.id.log_text)
        statsText = findViewById(R.id.log_stats)
        btnRefresh = findViewById(R.id.btn_log_refresh)
        btnClear = findViewById(R.id.btn_log_clear)

        btnRefresh.setOnClickListener { reloadLog() }
        btnClear.setOnClickListener { confirmClear() }

        // 画板 15 初始焦点 = [刷新]
        btnRefresh.requestFocus()

        // pre-IME 键分路（与设置页 / 测试打字页同构）
        findViewById<SettingsRootView>(R.id.log_viewer_root).preImeKeyHandler = { handlePreImeKey(it) }
    }

    override fun onResume() {
        super.onResume()
        // 进入定位最新（底部）：每次回前台（含从确认框返回）重读 + 滚底
        reloadLog()
    }

    override fun onDestroy() {
        destroyed = true
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    // ═══ 加载与滚动 ═══

    /** 重读日志（后台线程）并刷新浏览卡与统计行，完成后滚动到底部（最新）。 */
    private fun reloadLog() {
        val generation = ++loadGeneration
        ioExecutor.execute {
            val file = DebugLogController.logFile()
            val data = LogViewerContent.read(file)
            runOnUiThread {
                if (destroyed || generation != loadGeneration) return@runOnUiThread
                if (!data.ok) {
                    logText.text = getString(R.string.log_load_failed_hint)
                    statsText.text = getString(R.string.log_empty_stats)
                    return@runOnUiThread
                }
                logText.text = if (data.lineCount == 0L) getString(R.string.log_empty_hint) else data.displayText
                statsText.text = LogUiText.stats(data.lineCount, data.usedBytes, DebugLogStore.sizeMb)
                // 进入定位最新（底部）：下一帧再滚，等待文本排版完成
                logScroll.post {
                    logScroll.scrollTo(0, (logScroll.getChildAt(0)?.height ?: 0) - logScroll.height)
                }
            }
        }
    }

    /** 上下键滚动浏览卡（整页步进，画板 15「上下键滚动」）。 */
    private fun scrollLog(up: Boolean) {
        val page = (logScroll.height * 4) / 5
        if (page <= 0) return
        logScroll.smoothScrollBy(0, if (up) -page else page)
    }

    // ═══ 清空（需确认，决策 60） ═══

    private fun confirmClear() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.log_clear_confirm_title)
            .setMessage(R.string.log_clear_confirm_message)
            .setPositiveButton(R.string.log_clear_confirm_ok) { _, _ ->
                DebugLogController.clearLogs()
                reloadLog()
            }
            .setNegativeButton(R.string.log_clear_confirm_cancel, null)
            .create()
        // 默认焦点落「取消」：确认框直按 OK 不清日志（防误清），需左右移焦到「清空」再确认
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
        }
        dialog.show()
    }

    // ═══ pre-IME 键分路（T54/T59/T67 口径） ═══

    /**
     * 键裁决：上下键 = 浏览卡滚动（焦点位置无关，画板 15 提示口径）；左右键 = 按钮间移焦
     *（[刷新] ↔ [清空日志]，画板 15「焦点在按钮时 OK 刷新 / 清空」——qa Round 1 B2 修正：
     * Round 1 左右键消费无动作致 [清空日志] 遥控不可达）；OK = 焦点按钮激活（performClick，
     * T54 既有口径）；BACK 返回日志子页。动作仅在 DOWN 执行（UP 仅消费）。
     */
    private fun handlePreImeKey(event: KeyEvent): Boolean {
        val route = SettingsKeyRouter.route(event.keyCode)
        if (event.action != KeyEvent.ACTION_DOWN) return route != SettingsKeyRouter.Route.PASS
        return when (route) {
            SettingsKeyRouter.Route.BACK -> {
                finish()
                true
            }
            SettingsKeyRouter.Route.FOCUS_UP -> {
                scrollLog(up = true)
                true
            }
            SettingsKeyRouter.Route.FOCUS_DOWN -> {
                scrollLog(up = false)
                true
            }
            SettingsKeyRouter.Route.CONFIRM -> {
                window.decorView.findFocus()?.performClick()
                true
            }
            SettingsKeyRouter.Route.FOCUS_LEFT,
            SettingsKeyRouter.Route.FOCUS_RIGHT,
            -> {
                // 按钮行内左右移焦（FocusFinder 框架搜索，设置页既有口径同构）
                val root = window.decorView as? ViewGroup
                val next = root?.let {
                    FocusFinder.getInstance()
                        .findNextFocus(it, window.decorView.findFocus(), SettingsKeyRouter.focusDirection(route))
                }
                next?.requestFocusFromTouch()
                true
            }
            else -> true // 未识别键消费不透传（pre-IME 分路同口径）
        }
    }
}
