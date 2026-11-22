package com.sofatype.ime

import android.app.Activity
import android.os.Bundle
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.sofatype.ime.dicts.DictPackCatalog
import com.sofatype.ime.dicts.DictPackCatalogResult
import com.sofatype.ime.dicts.DictPackEndpoint
import com.sofatype.ime.dicts.DictPackEntry
import com.sofatype.ime.dicts.DictPackFailure
import com.sofatype.ime.dicts.DictPackRefresher
import com.sofatype.ime.dicts.DictPackStore
import com.sofatype.ime.update.UpdateFailureReason
import com.sofatype.ime.update.UpdateHttp
import java.io.File
import java.util.concurrent.Executors
import timber.log.Timber

class DictPackSettingsActivity : Activity() {

    private class PackRow(view: View) {

        val row: View = view.findViewById(R.id.dict_pack_row)
        val name: TextView = view.findViewById(R.id.dict_pack_name)
        val meta: TextView = view.findViewById(R.id.dict_pack_meta)
        val state: TextView = view.findViewById(R.id.dict_pack_state)
        val switch: LogSwitchView = view.findViewById(R.id.dict_pack_switch)
        val downloadButton: Button = view.findViewById(R.id.btn_dict_pack_download)
        val progress: ProgressBar = view.findViewById(R.id.dict_pack_progress)
        val status: TextView = view.findViewById(R.id.dict_pack_status)

        var entry: DictPackEntry? = null
        var lastPostedPercent = -1
    }

    private val ioExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var destroyed = false

    @Volatile
    private var ioGeneration = 0

    private lateinit var store: DictPackStore
    private lateinit var catalogDot: View
    private lateinit var catalogText: TextView
    private lateinit var btnDictRefresh: Button
    private lateinit var packList: LinearLayout
    private lateinit var emptyHint: TextView
    private lateinit var imeTakeoverPrompt: ImeTakeoverPrompt

    private val rows = mutableListOf<PackRow>()

    private var catalogEntries: List<DictPackEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dict_settings)

        store = DictPackStore(File(filesDir, DICT_PACK_DIR_NAME))
        catalogDot = findViewById(R.id.dict_catalog_dot)
        catalogText = findViewById(R.id.dict_catalog_text)
        btnDictRefresh = findViewById(R.id.btn_dict_refresh)
        packList = findViewById(R.id.dict_pack_list)
        emptyHint = findViewById(R.id.dict_pack_empty_hint)

        btnDictRefresh.setOnClickListener { refreshCatalog() }

        findViewById<SettingsRootView>(R.id.dict_settings_root).preImeKeyHandler = { handlePreImeKey(it) }

        imeTakeoverPrompt = ImeTakeoverPrompt(this)

        refreshCatalog()
    }

    override fun onResume() {
        super.onResume()

        imeTakeoverPrompt.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) imeTakeoverPrompt.refresh()
    }

    override fun onDestroy() {
        destroyed = true
        ioGeneration++
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        finish()
    }

    private fun refreshCatalog() {
        val generation = ++ioGeneration
        applyCatalogState(
            dotRes = R.drawable.bg_status_dot_idle,
            text = getString(R.string.dict_catalog_loading),
            showRetry = false,
        )
        Timber.tag(LOG_TAG).i("词包列表获取发起 catalogUrl=${DictPackEndpoint.catalogUrl().ifBlank { "<empty>" }}")
        ioExecutor.execute {
            val result = DictPackCatalog.fetch(DictPackEndpoint.catalogUrl())
            postIfCurrent(generation) { onCatalogResult(result) }
        }
    }

    private fun onCatalogResult(result: DictPackCatalogResult) {
        when (result) {
            is DictPackCatalogResult.Catalog -> {
                Timber.tag(LOG_TAG).i("词包列表获取成功 count=${result.entries.size}")
                catalogEntries = result.entries
                applyCatalogState(
                    dotRes = R.drawable.bg_status_dot_success,
                    text = if (result.entries.isEmpty()) getString(R.string.dict_catalog_empty) else "",
                    showRetry = false,
                )
                renderRows()
            }
            is DictPackCatalogResult.Failed -> {
                Timber.tag(LOG_TAG).i(
                    "词包列表获取失败 reason=${result.reason} http=${result.httpStatus ?: "-"}",
                )
                catalogEntries = emptyList()
                applyCatalogState(
                    dotRes = R.drawable.bg_status_dot_error,
                    text = getString(R.string.dict_catalog_failed) + "：" + failureText(result.reason, result.httpStatus),
                    showRetry = true,
                )
                renderRows()
            }
        }
    }

    private fun applyCatalogState(dotRes: Int, text: String, showRetry: Boolean) {
        catalogDot.setBackgroundResource(dotRes)
        catalogText.text = text
        catalogText.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        btnDictRefresh.visibility = if (showRetry) View.VISIBLE else View.GONE
    }

    private fun renderRows() {
        packList.removeAllViews()
        rows.clear()
        emptyHint.visibility =
            if (catalogEntries.isEmpty()) View.VISIBLE else View.GONE
        for (entry in catalogEntries) {
            val view = layoutInflater.inflate(R.layout.item_dict_pack, packList, false)
            packList.addView(view)
            val row = PackRow(view)
            row.entry = entry
            bindRowStatic(row, entry)
            refreshRowState(row)
            rows.add(row)
        }
    }

    private fun bindRowStatic(row: PackRow, entry: DictPackEntry) {
        row.name.text = entry.name
        row.meta.text = getString(R.string.dict_pack_version_size, entry.version, formatBytes(entry.bytes))
        row.downloadButton.setOnClickListener { startDownload(entry) }
        row.row.setOnClickListener { togglePack(row) }
    }

    private fun refreshRowState(row: PackRow) {
        val entry = row.entry ?: return
        val installed = store.installedEntry(entry.name) != null
        val enabled = installed && store.isEnabled(entry.name)

        row.lastPostedPercent = -1
        row.progress.visibility = View.GONE
        row.status.text = ""
        row.state.text = getString(if (installed) R.string.dict_pack_downloaded else R.string.dict_pack_not_downloaded)

        row.switch.checked = enabled
        row.switch.isEnabled = installed
        row.switch.alpha = if (installed) 1f else 0.6f

        row.row.isEnabled = installed
        row.row.isFocusable = installed
        row.row.isClickable = installed
        row.row.alpha = if (installed) 1f else 0.6f
        val stateWord = getString(if (enabled) R.string.dict_switch_state_on else R.string.dict_switch_state_off)
        row.row.contentDescription = getString(R.string.cd_dict_pack_row_switch, entry.name, stateWord)

        val (labelRes: Int, cdRes: Int) =
            if (installed) {
                R.string.btn_dict_pack_update to R.string.cd_btn_dict_pack_update
            } else {
                R.string.btn_dict_pack_download to R.string.cd_btn_dict_pack_download
            }
        row.downloadButton.setText(labelRes)
        row.downloadButton.contentDescription = getString(cdRes, entry.name)
        row.downloadButton.isEnabled = true
    }

    private fun togglePack(row: PackRow) {
        val entry = row.entry ?: return
        if (store.installedEntry(entry.name) == null) return
        val next = !store.isEnabled(entry.name)
        store.setEnabled(entry.name, next)
        Timber.tag(LOG_TAG).i("词包勾选变化 name=${entry.name} enabled=$next")
        DictPackRefresher.notifyPacksChanged()
        refreshRowState(row)
    }

    private fun startDownload(entry: DictPackEntry) {
        val row = rowFor(entry) ?: return
        val generation = ++ioGeneration
        row.lastPostedPercent = -1
        row.downloadButton.isEnabled = false
        row.progress.visibility = View.VISIBLE
        row.progress.isIndeterminate = true
        row.status.text = getString(R.string.dict_pack_status_downloading_indeterminate)
        Timber.tag(LOG_TAG).i("词包下载发起 name=${entry.name} url=${entry.url}")
        ioExecutor.execute {
            try {
                val dest = store.downloadTarget(entry.name)
                dest.parentFile?.mkdirs()
                dest.delete()
                UpdateHttp.downloadToFile(
                    url = entry.url,
                    dest = dest,
                    onProgress = { read, total ->
                        postIfCurrent(generation) { showRowProgress(row, read, total) }
                    },
                    isCancelled = { generation != ioGeneration || destroyed },
                )
                val installed = store.install(entry, dest)
                postIfCurrent(generation) {
                    if (installed) onInstallSuccess(row) else onVerifyFailed(row)
                }
            } catch (e: UpdateHttp.CancelledException) {
                postIfCurrent(generation) { refreshRowState(row) }
            } catch (e: UpdateHttp.HttpException) {
                postIfCurrent(generation) { onDownloadFailed(row, e) }
            }
        }
    }

    private fun onInstallSuccess(row: PackRow) {
        val entry = row.entry ?: return
        Timber.tag(LOG_TAG).i("词包下载完成 name=${entry.name} bytes=${entry.bytes}")
        refreshRowState(row)
    }

    private fun onVerifyFailed(row: PackRow) {
        Timber.tag(LOG_TAG).i("词包校验未通过 name=${row.entry?.name}")
        row.progress.visibility = View.GONE
        row.state.text = getString(R.string.dict_pack_verify_failed)
        row.status.text = ""
        row.downloadButton.setText(R.string.btn_dict_pack_retry)
        row.downloadButton.contentDescription =
            getString(R.string.cd_btn_dict_pack_retry, row.entry?.name ?: "")
        row.downloadButton.isEnabled = true
    }

    private fun onDownloadFailed(row: PackRow, error: UpdateHttp.HttpException) {
        val entry = row.entry ?: return
        Timber.tag(LOG_TAG).i(
            "词包下载失败 name=${entry.name} reason=${error.reason} http=${error.httpStatus ?: "-"}",
        )
        row.progress.visibility = View.GONE
        row.downloadButton.setText(R.string.btn_dict_pack_retry)
        row.downloadButton.contentDescription = getString(R.string.cd_btn_dict_pack_retry, entry.name)
        row.downloadButton.isEnabled = true
        row.status.text =
            getString(R.string.dict_pack_download_failed_prefix) + failureReasonText(error)
    }

    private fun failureReasonText(error: UpdateHttp.HttpException): String =
        when (error.reason) {
            UpdateFailureReason.DOWNLOAD_IO, UpdateFailureReason.DOWNLOAD_NETWORK ->
                error.httpStatus?.let { getString(R.string.dict_reason_http, it) }
                    ?: getString(R.string.dict_reason_network)
            else -> getString(R.string.dict_reason_network)
        }

    private fun failureText(reason: DictPackFailure, httpStatus: Int?): String =
        when (reason) {
            DictPackFailure.EMPTY_ENDPOINT -> getString(R.string.dict_reason_endpoint_empty)
            DictPackFailure.NETWORK -> getString(R.string.dict_reason_network)
            DictPackFailure.HTTP -> getString(R.string.dict_reason_http, httpStatus ?: 0)
            DictPackFailure.PARSE -> getString(R.string.dict_reason_parse)
            DictPackFailure.VERIFY -> getString(R.string.dict_reason_verify)
            DictPackFailure.IO -> getString(R.string.dict_reason_io)
        }

    private fun showRowProgress(row: PackRow, bytesRead: Long, totalBytes: Long?) {
        row.progress.isIndeterminate = totalBytes == null
        if (totalBytes != null && totalBytes > 0) {
            val percent = ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
            if (percent != row.lastPostedPercent) {
                row.lastPostedPercent = percent
                row.progress.progress = percent
                row.status.text = getString(R.string.dict_pack_status_downloading, percent)
            }
        } else {
            row.status.text = getString(R.string.dict_pack_status_downloading_indeterminate)
        }
    }

    private fun rowFor(entry: DictPackEntry): PackRow? = rows.firstOrNull { it.entry === entry }

    private fun handlePreImeKey(event: KeyEvent): Boolean {
        val route = SettingsKeyRouter.route(event.keyCode)
        if (event.action != KeyEvent.ACTION_DOWN) return route != SettingsKeyRouter.Route.PASS
        return when (route) {
            SettingsKeyRouter.Route.BACK -> {
                finish()
                true
            }
            SettingsKeyRouter.Route.CONFIRM -> {
                window.decorView.findFocus()?.performClick()
                true
            }
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

    private fun formatBytes(bytes: Long): String =
        if (bytes >= BYTES_PER_KB) {
            val kb = (bytes + BYTES_PER_KB / 2) / BYTES_PER_KB
            getString(R.string.dict_size_kb, kb)
        } else {
            getString(R.string.dict_size_bytes, bytes)
        }

    private fun postIfCurrent(generation: Int, update: () -> Unit) {
        runOnUiThread {
            if (generation == ioGeneration && !destroyed) update()
        }
    }

    private companion object {
        const val LOG_TAG = "DictPack"
        const val DICT_PACK_DIR_NAME = "dicts"
        const val BYTES_PER_KB = 1024L
    }
}
