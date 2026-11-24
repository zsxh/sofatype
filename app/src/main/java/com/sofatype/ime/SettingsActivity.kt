package com.sofatype.ime

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.FocusFinder
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.sofatype.ime.debug.DebugLogStore
import com.sofatype.ime.update.InstallChannel
import com.sofatype.ime.update.InstallChannels
import com.sofatype.ime.update.InstallReceipt
import com.sofatype.ime.update.InstallReceiptDecision
import com.sofatype.ime.update.UpdateCheck
import com.sofatype.ime.update.UpdateCheckResult
import com.sofatype.ime.update.UpdateEndpoint
import com.sofatype.ime.update.UpdateFailureReason
import com.sofatype.ime.update.UpdateHttp
import com.sofatype.ime.update.UiInstallDecision
import com.sofatype.ime.update.UiInstallMarker
import com.sofatype.ime.update.UiInstallReceipt
import com.sofatype.ime.update.UpdateRelease
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors
import timber.log.Timber

class SettingsActivity : Activity() {

    private enum class UiState {

        IDLE,
        CHECKING,
        UP_TO_DATE,
        NEWER,
        DOWNLOADING,

        PREPARING_INSTALL,
        FAILED,
        INSTALL_SUBMITTED,

        INSTALL_SUCCESS,
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val installStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

            when (InstallReceipt.decisionFor(status)) {
                InstallReceiptDecision.PENDING_USER_ACTION -> {

                    val confirm = if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                    if (confirm != null) {
                        Timber.tag(UPDATE_LOG_TAG).i("安装确认框转交系统 startActivity")
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        Timber.tag(UPDATE_LOG_TAG).i("安装确认 intent 缺失")
                    }
                }
                InstallReceiptDecision.SUCCESS -> Timber.tag(UPDATE_LOG_TAG).i("安装成功回执")
                InstallReceiptDecision.FAILURE -> {

                    Timber.tag(UPDATE_LOG_TAG).i("安装回执失败 status=$status message=$message")
                    showFailed(
                        R.string.failed_prefix_install,
                        UpdateCheckResult.Failed(
                            UpdateFailureReason.INSTALL_COMMIT,
                            httpStatus = status,
                        ),
                    )
                }
            }
        }
    }

    @Volatile
    private var ioGeneration = 0

    @Volatile
    private var destroyed = false

    private var uiState = UiState.IDLE
    private var latestRelease: UpdateRelease? = null
    private var lastPostedPercent = -1

    private var pendingUiChannelDecision: UiInstallDecision? = null

    private var lastUiChannelDecision: UiInstallDecision? = null

    private var healRecreatePending = false

    private lateinit var btnCheckUpdate: Button
    private lateinit var statusRow: View
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var updateNotes: TextView
    private lateinit var updateProxyCheck: CheckBox
    private lateinit var downloadProgress: ProgressBar
    private lateinit var logStatusDot: View
    private lateinit var logStatusText: TextView

    private var lastImeShown: Boolean? = null

    private val backExitGate = BackExitGate(windowMs = BackExitGate.WINDOW_MS)

    private lateinit var imeTakeoverPrompt: ImeTakeoverPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        pendingUiChannelDecision = savedInstanceState
            ?.getString(KEY_SAVED_UI_CHANNEL_DECISION)
            ?.let { raw -> runCatching { UiInstallDecision.valueOf(raw) }.getOrNull() }

        findViewById<TextView>(R.id.version_value).text = readVersionText()

        btnCheckUpdate = findViewById(R.id.btn_check_update)
        statusRow = findViewById(R.id.status_row)
        statusDot = findViewById(R.id.status_dot)
        statusText = findViewById(R.id.status_text)
        updateNotes = findViewById(R.id.update_notes)
        updateProxyCheck = findViewById(R.id.check_update_proxy)
        updateProxyCheck.isChecked = UpdateEndpoint.proxyEnabled(this)
        updateProxyCheck.setOnCheckedChangeListener { _, checked ->
            UpdateEndpoint.setProxyEnabled(this, checked)
            Timber.tag(UPDATE_LOG_TAG).i("下载代理开关：$checked")
        }
        downloadProgress = findViewById(R.id.download_progress)

        logStatusDot = findViewById(R.id.log_status_dot)
        logStatusText = findViewById(R.id.log_status_text)

        btnCheckUpdate.setOnClickListener { onUpdateAction() }

        findViewById<Button>(R.id.btn_test_typing).setOnClickListener {
            startActivity(Intent(this, TestTypingActivity::class.java))
        }

        findViewById<Button>(R.id.btn_logs).setOnClickListener {
            startActivity(Intent(this, LogSettingsActivity::class.java))
        }

        findViewById<SettingsRootView>(R.id.settings_root).preImeKeyHandler = { handlePreImeKey(it) }

        imeTakeoverPrompt = ImeTakeoverPrompt(this)
        applyState(UiState.IDLE)

        val statusFilter = IntentFilter(ACTION_INSTALL_STATUS)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(installStatusReceiver, statusFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(installStatusReceiver, statusFilter)
        }
    }

    override fun onDestroy() {
        destroyed = true
        ioGeneration++
        unregisterReceiver(installStatusReceiver)

        ioExecutor.shutdownNow()
        updateApkFile().delete()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        evaluateUiChannelInstallResult()

        refreshLogStatusRow()

        imeTakeoverPrompt.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) imeTakeoverPrompt.refresh()
    }

    private fun refreshLogStatusRow() {
        val enabled = DebugLogStore.isEnabled
        logStatusDot.setBackgroundResource(
            if (enabled) R.drawable.bg_status_dot_success else R.drawable.bg_status_dot_idle,
        )
        logStatusText.text = getString(if (enabled) R.string.log_status_on else R.string.log_status_off)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastUiChannelDecision?.let { outState.putString(KEY_SAVED_UI_CHANNEL_DECISION, it.name) }
    }

    private fun evaluateUiChannelInstallResult() {
        pendingUiChannelDecision?.let { saved ->
            restoreUiChannelDecision(saved)
            return
        }
        val beforeCode = readUiInstallMarker() ?: return
        clearUiInstallMarker()
        val afterCode = readVersionCode()
        val decision = UiInstallReceipt.decisionFor(beforeCode, afterCode)
        Timber.tag(UPDATE_LOG_TAG).i("UI 渠道结果判定 before=$beforeCode after=$afterCode decision=$decision")
        updateApkFile().delete()
        applyDecision(decision)
        scheduleHealRecreate()
    }

    private fun restoreUiChannelDecision(decision: UiInstallDecision) {
        pendingUiChannelDecision = null
        Timber.tag(UPDATE_LOG_TAG).i("UI 渠道判定终态自恢复 decision=$decision")
        applyDecision(decision)
    }

    private fun applyDecision(decision: UiInstallDecision) {
        lastUiChannelDecision = decision
        when (decision) {
            UiInstallDecision.UPDATED -> applyState(UiState.INSTALL_SUCCESS)
            UiInstallDecision.NOT_UPDATED ->
                showFailed(
                    R.string.failed_prefix_install,
                    UpdateCheckResult.Failed(UpdateFailureReason.UI_INSTALL_NOT_UPDATED),
                )
        }
    }

    private fun scheduleHealRecreate() {
        healRecreatePending = true
        mainHandler.post {
            if (!healRecreatePending || destroyed) return@post
            healRecreatePending = false
            Timber.tag(UPDATE_LOG_TAG).i("判定终态自愈：重建实例呈现（T50）")
            recreate()
        }
    }

    private fun readUiInstallMarker(): Int? =
        UiInstallMarker.decode(
            getSharedPreferences(PREFS_UI_INSTALL, MODE_PRIVATE)
                .getString(KEY_UI_INSTALL_BEFORE_CODE, null),
        )

    private fun writeUiInstallMarker(versionCodeBefore: Int) {
        getSharedPreferences(PREFS_UI_INSTALL, MODE_PRIVATE).edit()
            .putString(KEY_UI_INSTALL_BEFORE_CODE, UiInstallMarker.encode(versionCodeBefore))
            .apply()
    }

    private fun clearUiInstallMarker() {
        getSharedPreferences(PREFS_UI_INSTALL, MODE_PRIVATE).edit()
            .remove(KEY_UI_INSTALL_BEFORE_CODE)
            .apply()
    }

    private fun handlePreImeKey(event: KeyEvent): Boolean {
        if (lastImeShown == true) return false
        val route = SettingsKeyRouter.route(event.keyCode)

        if (event.action != KeyEvent.ACTION_DOWN) return route != SettingsKeyRouter.Route.PASS
        return when (route) {
            SettingsKeyRouter.Route.BACK -> {

                handleBackForExit("pre-IME")
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

    private fun handleBackForExit(fromPath: String) {
        if (backExitGate.onBack(SystemClock.uptimeMillis()) == BackExitGate.Decision.EXIT) {
            Timber.tag(FOCUS_LOG_TAG).i("$fromPath BACK：二次返回退出设置页")
            finish()
        } else {
            Timber.tag(FOCUS_LOG_TAG).i("$fromPath BACK：首击提示（二次退出窗口开启）")
            Toast.makeText(this, R.string.back_exit_hint, Toast.LENGTH_SHORT).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBackForExit("默认 BACK")
    }

    private fun onUpdateAction() {
        when (uiState) {
            UiState.IDLE,
            UiState.UP_TO_DATE,
            UiState.INSTALL_SUBMITTED,
            UiState.INSTALL_SUCCESS,
            -> startCheck()
            UiState.NEWER -> startDownload()
            UiState.FAILED -> startCheck()
            UiState.CHECKING,
            UiState.DOWNLOADING,
            UiState.PREPARING_INSTALL,
            -> Unit
        }
    }

    private fun startCheck() {
        latestRelease = null
        val generation = ++ioGeneration
        lastPostedPercent = -1
        applyState(UiState.CHECKING)
        val config = UpdateEndpoint.config(this)

        Timber.tag(UPDATE_LOG_TAG).i(
            "手动检查发起 url=${config.url} atom=${config.atom} proxy=${config.proxyPrefix}",
        )
        ioExecutor.execute {
            val result = UpdateCheck.check(config, currentVersionName()) { url ->
                UpdateHttp.fetchText(url)
            }
            postIfCurrent(generation) { onCheckResult(result) }
        }
    }

    private fun onCheckResult(result: UpdateCheckResult) {

        Timber.tag(UPDATE_LOG_TAG).i("手动检查结果 ${checkResultLabel(result)}")
        when (result) {
            is UpdateCheckResult.UpToDate -> applyState(UiState.UP_TO_DATE)
            is UpdateCheckResult.Newer -> {
                latestRelease = result.release
                updateNotes.text = result.release.notes
                applyState(UiState.NEWER)
            }
            is UpdateCheckResult.Failed -> showFailed(R.string.failed_prefix_check, result)
        }
    }

    private fun checkResultLabel(result: UpdateCheckResult): String =
        when (result) {
            is UpdateCheckResult.UpToDate -> "result=UP_TO_DATE"
            is UpdateCheckResult.Newer -> "result=NEWER tag=${result.release.tag}"
            is UpdateCheckResult.Failed -> "result=FAILED reason=${result.reason} http=${result.httpStatus ?: "-"}"
        }

    private fun startDownload() {
        val release = latestRelease ?: return
        val generation = ++ioGeneration
        lastPostedPercent = -1
        applyState(UiState.DOWNLOADING, progressBytes = 0, progressTotal = null)
        ioExecutor.execute {
            try {
                val file = updateApkFile()
                file.delete()
                UpdateHttp.downloadToFile(
                    url = release.apkUrl,
                    dest = file,
                    onProgress = { read, total ->
                        postIfCurrent(generation) {
                            showProgress(read, total)
                        }
                    },
                    isCancelled = { generation != ioGeneration || destroyed },
                )

                postIfCurrent(generation) { submitInstall(release, file) }
            } catch (e: UpdateHttp.CancelledException) {

            } catch (e: UpdateHttp.HttpException) {
                postIfCurrent(generation) {
                    showFailed(R.string.failed_prefix_download, wrap(e))
                }
            }
        }
    }

    private fun submitInstall(release: UpdateRelease, file: File) {
        when (InstallChannels.channelFor(Build.VERSION.SDK_INT)) {
            InstallChannel.SESSION -> submitSessionInstall(release, file)
            InstallChannel.UI_INTENT -> launchUiChannelInstall(release, file)
        }
    }

    private fun submitSessionInstall(release: UpdateRelease, file: File) {
        val generation = ++ioGeneration
        applyState(UiState.PREPARING_INSTALL)
        ioExecutor.execute {
            try {
                val installer = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                val sessionId = installer.createSession(params)
                Timber.tag(UPDATE_LOG_TAG).i("安装会话创建 sessionId=$sessionId bytes=${file.length()}")
                installer.openSession(sessionId).use { session ->
                    file.inputStream().use { input ->
                        session.openWrite("sofatype_update", 0, file.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    val statusIntent = Intent(ACTION_INSTALL_STATUS).setPackage(packageName)

                    val callbackFlags = if (Build.VERSION.SDK_INT >= 31) {
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    } else {
                        PendingIntent.FLAG_UPDATE_CURRENT
                    }
                    val callback = PendingIntent.getBroadcast(
                        this@SettingsActivity,
                        sessionId,
                        statusIntent,
                        callbackFlags,
                    )
                    session.commit(callback.intentSender)
                    Timber.tag(UPDATE_LOG_TAG).i("安装会话已提交 sessionId=$sessionId")
                }
                file.delete()
                postIfCurrent(generation) { applyState(UiState.INSTALL_SUBMITTED) }
            } catch (e: Exception) {
                Timber.tag(UPDATE_LOG_TAG).i("安装会话失败：${e.javaClass.simpleName} ${e.message}")
                file.delete()
                postIfCurrent(generation) {
                    showFailed(
                        R.string.failed_prefix_install,
                        UpdateCheckResult.Failed(UpdateFailureReason.INSTALL_PREPARE),
                    )
                }
            }
        }
    }

    private fun launchUiChannelInstall(release: UpdateRelease, file: File) {
        val generation = ++ioGeneration
        val beforeCode = readVersionCode()
        applyState(UiState.PREPARING_INSTALL)
        ioExecutor.execute {
            try {
                if (!file.setReadable(true, false)) {
                    throw IOException("安装器读权限放开失败")
                }
                Timber.tag(UPDATE_LOG_TAG).i(
                    "UI 渠道转交系统安装器 tag=${release.tag} bytes=${file.length()} beforeCode=$beforeCode",
                )
                postIfCurrent(generation) {

                    writeUiInstallMarker(beforeCode)
                    try {
                        startActivity(uiChannelInstallIntent(file))
                    } catch (e: ActivityNotFoundException) {
                        clearUiInstallMarker()
                        Timber.tag(UPDATE_LOG_TAG).i("系统安装器缺失：${e.message}")
                        showFailed(
                            R.string.failed_prefix_install,
                            UpdateCheckResult.Failed(UpdateFailureReason.UI_INSTALL_NOT_UPDATED),
                        )
                        return@postIfCurrent
                    }
                    applyState(UiState.INSTALL_SUBMITTED)
                }
            } catch (e: Exception) {
                Timber.tag(UPDATE_LOG_TAG).i("UI 渠道安装准备失败：${e.javaClass.simpleName} ${e.message}")
                postIfCurrent(generation) {
                    showFailed(
                        R.string.failed_prefix_install,
                        UpdateCheckResult.Failed(UpdateFailureReason.INSTALL_PREPARE),
                    )
                }
            }
        }
    }

    private fun uiChannelInstallIntent(file: File): Intent =
        Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setData(Uri.fromFile(file))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun applyState(
        state: UiState,
        progressBytes: Long? = null,
        progressTotal: Long? = null,
    ) {
        uiState = state
        latestRelease?.let { release ->
            if (state == UiState.NEWER || state == UiState.DOWNLOADING) {
                updateNotes.text = release.notes
                updateNotes.visibility = View.VISIBLE
            } else {
                updateNotes.visibility = View.GONE
            }
        } ?: run { updateNotes.visibility = View.GONE }

        statusRow.visibility =
            if (state == UiState.IDLE) View.GONE else View.VISIBLE
        downloadProgress.visibility =
            if (state == UiState.DOWNLOADING) View.VISIBLE else View.GONE

        when (state) {
            UiState.IDLE -> Unit
            UiState.CHECKING -> {
                dot(R.drawable.bg_status_dot_idle)
                statusText.text = getString(R.string.status_checking)
                setButton(R.string.btn_check_update, enabled = false)
            }
            UiState.UP_TO_DATE -> {
                dot(R.drawable.bg_status_dot_success)
                statusText.text = getString(R.string.status_up_to_date)
                setButton(R.string.btn_check_update, enabled = true)
            }
            UiState.NEWER -> {
                dot(R.drawable.bg_status_dot_waiting)
                latestRelease?.let {
                    statusText.text = getString(R.string.status_new_version, it.tag)
                }
                setButton(R.string.btn_download_update, enabled = true)
            }
            UiState.DOWNLOADING -> {
                dot(R.drawable.bg_status_dot_waiting)
                showProgress(progressBytes ?: 0, progressTotal)
                setButton(R.string.btn_download_update, enabled = false)
            }
            UiState.PREPARING_INSTALL -> {
                dot(R.drawable.bg_status_dot_waiting)
                statusText.text = getString(R.string.status_preparing_install)
                setButton(R.string.btn_download_update, enabled = false)
            }
            UiState.FAILED -> setButton(R.string.btn_retry, enabled = true)
            UiState.INSTALL_SUBMITTED -> {
                dot(R.drawable.bg_status_dot_success)
                statusText.text = getString(R.string.status_install_submitted)
                setButton(R.string.btn_check_update, enabled = true)
            }
            UiState.INSTALL_SUCCESS -> {
                dot(R.drawable.bg_status_dot_success)
                statusText.text = getString(R.string.status_install_success)
                setButton(R.string.btn_check_update, enabled = true)
            }
        }
    }

    private fun showProgress(bytesRead: Long, totalBytes: Long?) {
        if (uiState != UiState.DOWNLOADING) return
        downloadProgress.isIndeterminate = totalBytes == null
        if (totalBytes != null && totalBytes > 0) {
            val percent = ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
            if (percent != lastPostedPercent) {
                lastPostedPercent = percent
                downloadProgress.progress = percent
                statusText.text = getString(R.string.status_downloading, percent)
            }
        } else {
            statusText.text = getString(R.string.status_downloading_indeterminate)
        }
    }

    private fun showFailed(prefixRes: Int, result: UpdateCheckResult.Failed) {
        latestRelease = null
        applyState(UiState.FAILED)
        statusDot.setBackgroundResource(R.drawable.bg_status_dot_error)
        statusText.text = getString(prefixRes) + reasonText(result)
    }

    private fun reasonText(result: UpdateCheckResult.Failed): String =
        when (result.reason) {
            UpdateFailureReason.EMPTY_ENDPOINT -> getString(R.string.reason_endpoint_empty)
            UpdateFailureReason.CHECK_NETWORK, UpdateFailureReason.DOWNLOAD_NETWORK ->
                result.httpStatus?.let { getString(R.string.reason_http, it) }
                    ?: getString(R.string.reason_network)
            UpdateFailureReason.CHECK_HTTP -> getString(R.string.reason_http, result.httpStatus ?: 0)
            UpdateFailureReason.PARSE -> getString(R.string.reason_parse)
            UpdateFailureReason.NO_APK_ASSET -> getString(R.string.reason_no_apk)
            UpdateFailureReason.DOWNLOAD_IO -> getString(R.string.reason_download_io)
            UpdateFailureReason.INSTALL_PREPARE -> getString(R.string.reason_install_prepare)
            UpdateFailureReason.INSTALL_COMMIT ->
                getString(R.string.reason_install_commit, result.httpStatus ?: -1)
            UpdateFailureReason.UI_INSTALL_NOT_UPDATED ->
                getString(R.string.reason_ui_install_not_updated)
        }

    private fun wrap(e: UpdateHttp.HttpException): UpdateCheckResult.Failed =
        UpdateCheckResult.Failed(e.reason, e.httpStatus)

    private fun dot(drawableRes: Int) {
        statusDot.setBackgroundResource(drawableRes)
    }

    private fun setButton(textRes: Int, enabled: Boolean) {
        btnCheckUpdate.setText(textRes)
        btnCheckUpdate.isEnabled = enabled
        btnCheckUpdate.contentDescription = btnCheckUpdate.text.toString()
    }

    private fun postIfCurrent(generation: Int, update: () -> Unit) {
        mainHandler.post {
            if (generation == ioGeneration && !destroyed) update()
        }
    }

    private fun updateApkFile(): File = File(cacheDir, UPDATE_APK_NAME)

    private fun readVersionText(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        return VersionFormatter.format(info.versionName)
    }

    @Suppress("DEPRECATION")
    private fun currentVersionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    @Suppress("DEPRECATION")
    private fun readVersionCode(): Int =
        packageManager.getPackageInfo(packageName, 0).versionCode

    private companion object {
        const val UPDATE_LOG_TAG = "UpdateCheck"
        const val UPDATE_APK_NAME = "update_download.apk"

        const val FOCUS_LOG_TAG = "SettingsFocus"

        const val PREFS_UI_INSTALL = "update_ui_install"
        const val KEY_UI_INSTALL_BEFORE_CODE = "before_code"

        const val KEY_SAVED_UI_CHANNEL_DECISION = "ui_channel_decision"

        const val ACTION_INSTALL_STATUS = "com.sofatype.ime.action.INSTALL_STATUS"
    }
}
