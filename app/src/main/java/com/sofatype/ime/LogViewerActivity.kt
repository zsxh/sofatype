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

    @Volatile
    private var loadGeneration = 0

    private lateinit var logScroll: ScrollView
    private lateinit var logText: TextView
    private lateinit var statsText: TextView
    private lateinit var btnRefresh: Button
    private lateinit var btnClear: Button

    private lateinit var imeTakeoverPrompt: ImeTakeoverPrompt

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

        btnRefresh.requestFocus()

        findViewById<SettingsRootView>(R.id.log_viewer_root).preImeKeyHandler = { handlePreImeKey(it) }

        imeTakeoverPrompt = ImeTakeoverPrompt(this)
    }

    override fun onResume() {
        super.onResume()

        reloadLog()

        imeTakeoverPrompt.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) imeTakeoverPrompt.refresh()
    }

    override fun onDestroy() {
        destroyed = true
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

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

                logScroll.post {
                    logScroll.scrollTo(0, (logScroll.getChildAt(0)?.height ?: 0) - logScroll.height)
                }
            }
        }
    }

    private fun scrollLog(up: Boolean) {
        val page = (logScroll.height * 4) / 5
        if (page <= 0) return
        logScroll.smoothScrollBy(0, if (up) -page else page)
    }

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

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).requestFocus()
        }
        dialog.show()
    }

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

                val root = window.decorView as? ViewGroup
                val next = root?.let {
                    FocusFinder.getInstance()
                        .findNextFocus(it, window.decorView.findFocus(), SettingsKeyRouter.focusDirection(route))
                }
                next?.requestFocusFromTouch()
                true
            }
            else -> true
        }
    }
}
