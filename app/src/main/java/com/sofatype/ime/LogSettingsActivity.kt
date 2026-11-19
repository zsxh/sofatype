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

    private lateinit var imeTakeoverPrompt: ImeTakeoverPrompt

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
        rowSize.setOnClickListener {  }
        rowView.setOnClickListener { openViewer() }
        btnViewLogs.setOnClickListener { openViewer() }

        findViewById<SettingsRootView>(R.id.log_settings_root).preImeKeyHandler = { handlePreImeKey(it) }

        imeTakeoverPrompt = ImeTakeoverPrompt(this)

        refreshUi()
    }

    override fun onResume() {
        super.onResume()

        refreshUi()

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

    override fun onBackPressed() {

        finish()
    }

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

    private fun refreshUi() {
        val enabled = DebugLogStore.isEnabled
        logSwitch.checked = enabled

        rowSize.isEnabled = enabled
        rowView.isEnabled = enabled
        rowSize.isFocusable = enabled
        rowView.isFocusable = enabled
        if (!enabled && (currentFocus === rowSize || currentFocus === rowView)) {
            rowSwitch.requestFocus()
        }

        refreshSizeSelection()
        applyGrey(enabled, sizeOptionTexts)
        applyGrey(enabled, listOf(usageHint, btnViewLogs))
        refreshRowDescriptions()
        refreshUsageHint(enabled)
    }

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

    private fun refreshRowDescriptions() {
        val switchState = getString(if (DebugLogStore.isEnabled) R.string.log_switch_state_on else R.string.log_switch_state_off)
        rowSwitch.contentDescription = getString(R.string.cd_log_switch_row, switchState)
        rowSize.contentDescription = getString(
            R.string.cd_log_size_row,
            DebugLogOptions.label(DebugLogStore.sizeMb),
        )
    }

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

        return true
    }
}
