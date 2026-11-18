// T21 · FR-D4 / 决策 17/18：「检查更新」接成真实三态（已是最新 / 发现新版 → 下载进度 /
// 失败红点可重试）；纯手动触发；更新源接口地址可配置（决策 17，正式地址后补）；
// 下载走 cacheDir，安装提交经系统层完成，装后清理；
// 全部网络与安装准备在后台线程执行，失败不阻塞设置页与 IME 服务。
// T47 · 决策 36：API 23 基线安装链路改走 UI 渠道——转交系统安装器（install Intent），
// 回前台 onResume 比对自身 versionCode 判定（升=成功 / 未升=安装器取消或失败→失败可重试）；
// in-flight 标记持久化（SharedPreferences，消费即清）：升级成功替换包必杀进程，内存标记活不到判定。
// T51 · FR-D4 安装确认去重（2026-11-10 户主拍板，docs/issues/open/T51.md）：下载完成不再弹
// 应用内「是否立即安装？」确认框，直接提交安装；用户确认语义由系统层完整承载
//（API 23 基线 = 系统安装器包确认 + 小米商店安全审核两道系统级确认，设备档案坑 16）。
// 「失败可重试」与版本判定链不变。
// T50 · UI 渠道判定态入口自愈（docs/issues/open/T50.md）：升级替换包杀进程后 am start 重建的
// 判定实例在本机（MiTV API 23）实测窗口输入派发整体失活——tap/OK/BACK 全不达，视图树与渲染
// 正常（2026-11-10 T27 批次真机 B3 段观测）；FR-D4 手动触发语义下该实例「检查更新」必须可用。
// 处置：判定终态落地后立即 recreate() 换新实例呈现（新实例 = 新窗口 = 新输入通道）；判定结果
// 经 onSaveInstanceState/savedInstanceState 一次性传递恢复，恢复实例不再二次重建。
// T50 Round 2 · 手动检查路径补日志点（2026-11-10 户主指令，review-qa Round 1 非阻塞建议 1）：
// startCheck 发起一条、onCheckResult 落态一条（三态标识 + 失败原因），仅走查取证用，
// 不改变任何行为逻辑；日志不含用户输入内容。
// T63 · FR-D1 本批次（docs/PRD_20261112.md）：版本卡「检查更新」行下新增第三行「测试打字」
// 按钮（画板 7，样式与检查更新同款），点击跳转独立测试打字页（TestTypingActivity）；
// 顶部 test_input_area 调试区整体迁出本页，T54 焦点接管 / T62 切换广播接线随迁至
// TestTypingActivity（承载面 = 调试三件套所在页面）；本页 pre-IME 键分路保留
// （BACK 退出 / OK 激活 / 方向键移焦为设置页既有宿主语义，与调试区无关）。
// 范围边界：检查更新逻辑与三态呈现零改动（FR-D4 / 决策 36/39/40 口径不动）。
// T67 · 决策 57：设置页 BACK 二次退出（修订 BACK 单击直退行为）——两条 BACK 路径
//（pre-IME 分路 BACK 分支；冷实例经 IME 隐藏态透传落到 Activity 默认 BACK）收敛到同一
// BackExitGate 裁决（纯决策，单测锁定）：首击只在屏幕下方弹系统 Toast 提示「再按一次返回
// 退出sofatype设置页」（文案 back_exit_hint）、不退出；2 秒窗口内再击直接退出；超窗重新计。
// T82 · 决策 66 · B2 设置页检测引导（docs/issues/open/T82.md）：onResume / 窗口焦点回归读
// Settings.Secure.DEFAULT_INPUT_METHOD（公开读取零权限），夺回态显示页面横幅提示 +
// 「一键选回」调 showInputMethodPicker()（公开 API 零权限）；默认态横幅恒 GONE
// （零新增提示、零行为变化）。接线共用件 ImeTakeoverPrompt（日志设置 / 查看日志页同构接线）。
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

    /** 版本卡检查更新区状态（FR-D4 三态 + 过程态）。 */
    private enum class UiState {
        /** 初始未检查：状态行隐藏。 */
        IDLE,
        CHECKING,
        UP_TO_DATE,
        NEWER,
        DOWNLOADING,
        /** 安装提交准备与转交进行中（T51：下载完成即进入，无应用内确认环节）。 */
        PREPARING_INSTALL,
        FAILED,
        INSTALL_SUBMITTED,
        /** UI 渠道（T47）回前台判定：versionCode 已升，更新成功。 */
        INSTALL_SUCCESS,
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioExecutor = Executors.newSingleThreadExecutor()

    /** 系统安装器回执接收（会话状态码写入 logcat，TAG=UpdateCheck，走查与排障取证用）。 */
    private val installStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            // 决策映射走纯模型 InstallReceipt（InstallReceiptTest 锁定），本处按决策分派 UI/转交。
            when (InstallReceipt.decisionFor(status)) {
                InstallReceiptDecision.PENDING_USER_ACTION -> {
                    // PENDING_USER_ACTION：系统将确认框启动 Intent 放在 EXTRA_INTENT，由应用转交 startActivity
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
                    // T46 附加（T21「失败可重试」口径）：安装回执失败必须切 UI 失败态——
                    // 红点 + 原因文案（携带系统状态码）+ 按钮变「重试」。修复前仅写 log，
                    // 界面滞留「已提交系统安装」成功态（真机 INSTALL_FAILED_USER_RESTRICTED
                    // 实证，qa Round 2 r2-t46z.xml）。onReceive 在主线程，可直接刷新 UI。
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

    /** 递增操作代号：新操作使旧操作的迟到回调失效；销毁时递增并置 destroyed。 */
    @Volatile
    private var ioGeneration = 0

    @Volatile
    private var destroyed = false

    private var uiState = UiState.IDLE
    private var latestRelease: UpdateRelease? = null
    private var lastPostedPercent = -1

    /**
     * T50 · 待恢复的 UI 渠道判定终态：本实例由「判定后自愈重建」链路创建时，经
     * savedInstanceState 携带；onResume 应用终态后清空（一次性，不跨进程）。
     */
    private var pendingUiChannelDecision: UiInstallDecision? = null

    /** T50 · 本实例已落定的 UI 渠道判定终态（供 onSaveInstanceState 向自愈重建实例传递）。 */
    private var lastUiChannelDecision: UiInstallDecision? = null

    /** T50 · 自愈重建排队标记：主线程 post 消费一次，防销毁后重复重建。 */
    private var healRecreatePending = false

    private lateinit var btnCheckUpdate: Button
    private lateinit var statusRow: View
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var updateNotes: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var logStatusDot: View
    private lateinit var logStatusText: TextView

    /**
     * T54 Round 2 · 输入法可见性去重标记：T63 起焦点接管接线随调试区迁至 TestTypingActivity，
     * 本页无输入框、无监听写值（恒 null = 一律按隐藏态路由，与既有 release 行为一致）；
     * 字段保留维持 pre-IME 分路「显示态透传」门控线与 T54 守卫测试锚点。
     */
    private var lastImeShown: Boolean? = null

    /**
     * T67 · 决策 57 · BACK 二次退出守卫：两条 BACK 路径（pre-IME 分路 / Activity 默认 BACK）
     * 共用同一实例，收敛同一裁决（首击 Toast 提示不退出、2 秒内次击退出、超窗重新计）。
     */
    private val backExitGate = BackExitGate(windowMs = BackExitGate.WINDOW_MS)

    /**
     * T82 · 决策 66 · 默认输入法夺回检测横幅（接线见 onCreate，刷新见 onResume 与
     * onWindowFocusChanged）：夺回态显示横幅 + 「一键选回」；默认态横幅 GONE
     * （布局零位移、零行为变化）。
     */
    private lateinit var imeTakeoverPrompt: ImeTakeoverPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // T50 · 自愈重建链路：恢复实例从 savedInstanceState 读回判定终态（一次性传递；
        // 载荷缺失或非法时保持 null，走普通启动路径，行为与修复前一致）。
        pendingUiChannelDecision = savedInstanceState
            ?.getString(KEY_SAVED_UI_CHANNEL_DECISION)
            ?.let { raw -> runCatching { UiInstallDecision.valueOf(raw) }.getOrNull() }

        // FR-D3：版本信息卡读真实版本（决策 19：versionName/versionCode 以构建配置为准）
        findViewById<TextView>(R.id.version_value).text = readVersionText()

        btnCheckUpdate = findViewById(R.id.btn_check_update)
        statusRow = findViewById(R.id.status_row)
        statusDot = findViewById(R.id.status_dot)
        statusText = findViewById(R.id.status_text)
        updateNotes = findViewById(R.id.update_notes)
        downloadProgress = findViewById(R.id.download_progress)
        // T71 · 决策 60：日志入口行状态位（点 + 文字，onResume 刷新）
        logStatusDot = findViewById(R.id.log_status_dot)
        logStatusText = findViewById(R.id.log_status_text)

        btnCheckUpdate.setOnClickListener { onUpdateAction() }
        // T63 · 「测试打字」入口（画板 7 第三行）：点击进入独立测试打字页。
        // 同应用内 startActivity，BACK 由测试打字页 finish 后回到本页（系统返回栈）。
        findViewById<Button>(R.id.btn_test_typing).setOnClickListener {
            startActivity(Intent(this, TestTypingActivity::class.java))
        }
        // T71 · 决策 60 画板 7 第四行：「日志」入口 → 日志子页（BACK 由子页 finish 返回本页）。
        findViewById<Button>(R.id.btn_logs).setOnClickListener {
            startActivity(Intent(this, LogSettingsActivity::class.java))
        }
        // T54 Round 2 · pre-IME 键分路接线（docs/issues/open/T54.md）：
        // 输入会话为窗口级绑定，键盘收起后遥控键仍先路由进隐藏态 IME 被消费（Round 1 定位；
        // IME 侧消费语义属 T59 挂账面，本侧零改动）。宿主侧在框架 pre-IME 阶段
        // （ViewRootImpl 先把键送到根视图 dispatchKeyPreIme）按键盘可见性分路：
        // 显示态透传 IME；隐藏态按宿主语义路由——方向键=FocusFinder 焦点搜索、
        // OK=焦点控件激活、BACK=二次退出裁决（T67 · 决策 57，与 Activity 默认 BACK 同守卫）。
        //（T63 起本页无输入框，调试走查链路移至 TestTypingActivity，本分路服务设置页
        // 既有控件语义。）
        findViewById<SettingsRootView>(R.id.settings_root).preImeKeyHandler = { handlePreImeKey(it) }
        // T82 · 决策 66：夺回检测横幅接线（「一键选回」按钮 → 系统输入法选择器）
        imeTakeoverPrompt = ImeTakeoverPrompt(this)
        applyState(UiState.IDLE)
        // 回执广播经本应用 PendingIntent 发出（发起身份=本应用），NOT_EXPORTED 可收；
        // targetSdk 34 起 registerReceiver 必须显式 export 标志，4 参重载 API 26 起可用，低版本走 2 参。
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
        // 退出清理：后台任务中断 + cache 下载文件不残留（AGENTS.md 禁止事项 3）
        ioExecutor.shutdownNow()
        updateApkFile().delete()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        evaluateUiChannelInstallResult()
        // T71 · 决策 60：日志入口行状态随动（子页开关切换后返回本页刷新）
        refreshLogStatusRow()
        // T82 · 决策 66：默认输入法夺回检测（夺回态显示横幅；默认态零变化）
        imeTakeoverPrompt.refresh()
    }

    /**
     * T82 · 决策 66：窗口焦点回归即重跑夺回检测——系统输入法选择器关闭不触发 onResume，
     * 选回 sofatype 后横幅在本页重新获得焦点时即时隐藏。
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) imeTakeoverPrompt.refresh()
    }

    /** T71 · 日志入口行状态：关 = 灰点「调试日志已关闭」（默认态）；开 = 绿点「调试日志已开启」。 */
    private fun refreshLogStatusRow() {
        val enabled = DebugLogStore.isEnabled
        logStatusDot.setBackgroundResource(
            if (enabled) R.drawable.bg_status_dot_success else R.drawable.bg_status_dot_idle,
        )
        logStatusText.text = getString(if (enabled) R.string.log_status_on else R.string.log_status_off)
    }

    /** T50 · 判定终态随实例状态保存，供自愈重建实例恢复（无判定终态时不写，零残留）。 */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        lastUiChannelDecision?.let { outState.putString(KEY_SAVED_UI_CHANNEL_DECISION, it.name) }
    }

    // ═══ T47 · UI 渠道结果判定（决策 36：回设置页比对自身 versionCode）═══

    /**
     * 存在 UI 渠道 in-flight 标记时判定一次：升 = 成功（绿点安装成功）；未升 = 安装器取消或
     * 失败（红点 + 原因文案 + 重试，T21 口径）。判定即清标记并清理下载 cache 文件（装后 /
     * 取消共用清理口径，AGENTS 禁止事项 3）。无标记即普通回前台，不动 UI。
     * API 24+ 会话分支不写标记，不受本判定影响（验收④行为不回归）。
     * T50 · 自愈重建：包替换杀进程后 am start 重建的本实例窗口输入派发实测整体失活
     * （docs/issues/open/T50.md，tap/OK/BACK 全不达），故判定终态先落地渲染，随即排队
     * recreate() 换新实例呈现——新实例经 savedInstanceState 读回终态（见
     * [restoreUiChannelDecision]），不再二次判定、不再二次重建。恢复实例走本方法
     * 恢复分支，与无标记普通回前台（修复前行为）互不影响。
     */
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

    /** T50 · 恢复分支：应用自愈重建实例带回的判定终态（一次性消费，不再排队重建）。 */
    private fun restoreUiChannelDecision(decision: UiInstallDecision) {
        pendingUiChannelDecision = null
        Timber.tag(UPDATE_LOG_TAG).i("UI 渠道判定终态自恢复 decision=$decision")
        applyDecision(decision)
    }

    /** T50 · 判定终态统一落地（首启判定实例与自愈恢复实例共用渲染口径）。 */
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

    /**
     * T50 · 自愈重建排队：post 一帧（让终态先渲染）后 recreate。一次性消费 + 销毁护栏；
     * 恢复实例（[restoreUiChannelDecision] 路径）不排队，无重建循环。
     */
    private fun scheduleHealRecreate() {
        healRecreatePending = true
        mainHandler.post {
            if (!healRecreatePending || destroyed) return@post
            healRecreatePending = false
            Timber.tag(UPDATE_LOG_TAG).i("判定终态自愈：重建实例呈现（T50）")
            recreate()
        }
    }

    /** UI 渠道 in-flight 标记读取；无标记 / 载荷非法返回 null。 */
    private fun readUiInstallMarker(): Int? =
        UiInstallMarker.decode(
            getSharedPreferences(PREFS_UI_INSTALL, MODE_PRIVATE)
                .getString(KEY_UI_INSTALL_BEFORE_CODE, null),
        )

    /** 标记写入（转交安装器前落盘：升级成功替换包必杀进程，须跨进程存活）。 */
    private fun writeUiInstallMarker(versionCodeBefore: Int) {
        getSharedPreferences(PREFS_UI_INSTALL, MODE_PRIVATE).edit()
            .putString(KEY_UI_INSTALL_BEFORE_CODE, UiInstallMarker.encode(versionCodeBefore))
            .apply()
    }

    /** 标记清除（判定消费即清，零残留）。 */
    private fun clearUiInstallMarker() {
        getSharedPreferences(PREFS_UI_INSTALL, MODE_PRIVATE).edit()
            .remove(KEY_UI_INSTALL_BEFORE_CODE)
            .apply()
    }

    // ═══ T54 Round 2 · pre-IME 键分路 ═══

    /**
     * pre-IME 键裁决（接线见 onCreate）：键盘显示态透传（打字链路零改动）；
     * 隐藏态宿主语义路由。PASS 交回框架与 IME 链路。
     * T63 修正：动作仅在 DOWN 执行，UP 仅消费——按键对完整、不漏进 IME 链路（T54 R2 口径
     * 保持），同时消除遥控 OK 单击的二次 performClick（DOWN/UP 各执行一次曾使 startActivity
     * 双发、堆叠两个测试打字页实例，模拟器实测复现；检查更新侧双触发为幂等浪费，同修）。
     */
    private fun handlePreImeKey(event: KeyEvent): Boolean {
        if (lastImeShown == true) return false
        val route = SettingsKeyRouter.route(event.keyCode)
        // 非 DOWN（UP 等）不执行动作：透传键（PASS）交回框架，动作键仅消费（按键对完整）
        if (event.action != KeyEvent.ACTION_DOWN) return route != SettingsKeyRouter.Route.PASS
        return when (route) {
            SettingsKeyRouter.Route.BACK -> {
                // T67 · 决策 57：首击只提示不退出、2 秒内次击退出（与 Activity 默认 BACK 同守卫）
                handleBackForExit("pre-IME")
                true
            }
            SettingsKeyRouter.Route.CONFIRM -> {
                // T59（户主拍板 2026-11-11）：CONFIRM 按焦点类型分流——焦点在可输入控件
                // （EditText）唤起键盘（showSoftInput，与 IME 隐藏态确认键口径同语义，
                // 消除「OK-on-EditText 无可见反馈不唤起」）；其余控件激活焦点控件
                //（T54 既有口径）。分流判定归 SettingsKeyRouter.confirmActivatesIme（纯决策单测锁定）。
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

    // ═══ T67 · 决策 57 · 设置页 BACK 二次退出（两条 BACK 路径统一裁决） ═══

    /**
     * BACK 二次退出统一裁决（决策 57 收敛点）：守卫判定首击 = 屏幕下方系统 Toast 提示
     * 「再按一次返回退出sofatype设置页」、不退出；2 秒窗口内次击 = finish 退出；
     * 超窗 = 重新计窗并再次提示（视同首击）。pre-IME 分路（[handlePreImeKey] BACK 分支）与
     * 冷实例路径（[onBackPressed]，键经 IME 隐藏态透传落到 Activity 默认 BACK）都落本方法，
     * 两路径行为结果一致。[fromPath] 仅入日志区分派发路径（走查取证用，不含用户输入内容）。
     */
    private fun handleBackForExit(fromPath: String) {
        if (backExitGate.onBack(SystemClock.uptimeMillis()) == BackExitGate.Decision.EXIT) {
            Timber.tag(FOCUS_LOG_TAG).i("$fromPath BACK：二次返回退出设置页")
            finish()
        } else {
            Timber.tag(FOCUS_LOG_TAG).i("$fromPath BACK：首击提示（二次退出窗口开启）")
            Toast.makeText(this, R.string.back_exit_hint, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 冷实例 BACK 路径（T59 派发口径）：am start 后从未弹过键盘时，键不经 pre-IME 分路，
     * 经 IME 隐藏态透传落回框架默认返回处理 → 本回调。与 pre-IME 分路共用同一守卫裁决。
     */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        handleBackForExit("默认 BACK")
    }

    // ═══ FR-D4 交互入口 ═══

    /** 「检查更新」按钮：按当前态分发（检查 / 下载更新 / 重试），过程态按钮禁用不响应。 */
    private fun onUpdateAction() {
        when (uiState) {
            UiState.IDLE,
            UiState.UP_TO_DATE,
            UiState.INSTALL_SUBMITTED,
            UiState.INSTALL_SUCCESS,
            -> startCheck()
            UiState.NEWER -> startDownload()
            UiState.FAILED -> startCheck() // 重试 = 重跑检查
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
        val endpoint = UpdateEndpoint.endpoint(this)
        // T50 Round 2 · 手动检查发起日志（走查取证用；endpoint 为配置的更新源地址，非用户输入内容）
        Timber.tag(UPDATE_LOG_TAG).i("手动检查发起 endpoint=$endpoint")
        ioExecutor.execute {
            val result = UpdateCheck.check(endpoint, currentVersionName()) { url ->
                UpdateHttp.fetchText(url)
            }
            postIfCurrent(generation) { onCheckResult(result) }
        }
    }

    private fun onCheckResult(result: UpdateCheckResult) {
        // T50 Round 2 · 手动检查落态日志（走查取证用）：含三态标识与失败原因，仅写日志不改分派
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

    /** T50 Round 2 · 检查结果日志摘要（三态标识 + 失败原因 + 版本 tag；均非用户输入内容）。 */
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
                file.delete() // 先清旧残留再下新
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
                // T51：下载完成直接提交安装（无应用内确认环节，确认语义由系统层承载）
                postIfCurrent(generation) { submitInstall(release, file) }
            } catch (e: UpdateHttp.CancelledException) {
                // 取消：态保持（销毁中无须刷新），半成品文件已由 UpdateHttp 删除
            } catch (e: UpdateHttp.HttpException) {
                postIfCurrent(generation) {
                    showFailed(R.string.failed_prefix_download, wrap(e))
                }
            }
        }
    }

    /**
     * 下载完成即提交（T51 去确认框）：按决策 36 渠道门控分发——
     * API 24+ 会话分支 / API 23 基线 UI 渠道（系统安装器包确认 + 商店安全审核承载用户确认，
     * 设备档案坑 16）。
     */
    private fun submitInstall(release: UpdateRelease, file: File) {
        when (InstallChannels.channelFor(Build.VERSION.SDK_INT)) {
            InstallChannel.SESSION -> submitSessionInstall(release, file)
            InstallChannel.UI_INTENT -> launchUiChannelInstall(release, file)
        }
    }

    /** 会话分支（API 24+，决策 36 保留）：写 PackageInstaller 会话并提交系统安装器；装后删 cache 文件。 */
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
                    // 回执 PendingIntent 必须可变（FLAG_MUTABLE）：系统提交后向其 fill-in EXTRA_STATUS 等
                    // 回执 extras；FLAG_IMMUTABLE 会拦掉全部 fill-in，回执变无 extras 空广播。
                    // FLAG_MUTABLE 常量 API 31 起才有；更低版本不存在不可变语义，直接 UPDATE_CURRENT。
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
                file.delete() // 装后清理：会话已接管数据，cache 副本即删
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

    /**
     * UI 渠道（API 23 基线，决策 36 / issue T47；T51 起下载完成后直接调本方法）：转交系统安装器
     * 完成安装，包确认与安全检测、继续/取消都在系统安装器界面；无会话回执，回前台 onResume
     * 按 versionCode 比对判定（evaluateUiChannelInstallResult）。APK 放开读权限供安装器独立
     * uid 读取（targetSdk 24+ 下 MODE_WORLD_READABLE 抛 SecurityException，
     * setReadable(true, false) 是本分支唯一可行的放开方式）；文件保留至判定后清理——
     * 安装器异步读取，不能转交即删（会话分支口径为提交即删，分支行为不同属预期）。
     * 启动前登记持久化 in-flight 标记。
     */
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
                    // 标记先落盘再转交：无论后续进程存活与否，回前台判定均有据（决策 36 口径）。
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

    /** UI 渠道 install Intent：API 23 基线直用 file://（FileUriExposed 检查仅存在于 API 24+ 设备）。 */
    private fun uiChannelInstallIntent(file: File): Intent =
        Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setData(Uri.fromFile(file))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // ═══ 状态渲染 ═══

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

    /** 下载与安装会话的 cache 文件（隐私红线：仅 cache 目录，装后 / 退出即清）。 */
    private fun updateApkFile(): File = File(cacheDir, UPDATE_APK_NAME)

    // FR-D3 · PRD 决策 40（2026-11-11 户主拍板）：版本行只显示 v<versionName>，
    // versionCode 不再对用户展示；versionCode 仅内部判定链（readVersionCode）使用。
    private fun readVersionText(): String {
        val info = packageManager.getPackageInfo(packageName, 0)
        return VersionFormatter.format(info.versionName)
    }

    /** 当前版本名（决策 19：与构建配置一致），供版本比较。 */
    @Suppress("DEPRECATION")
    private fun currentVersionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    /** 当前 versionCode（T47 UI 渠道结果判定：决策 36 按版本号变化判定）。 */
    @Suppress("DEPRECATION")
    private fun readVersionCode(): Int =
        packageManager.getPackageInfo(packageName, 0).versionCode

    private companion object {
        const val UPDATE_LOG_TAG = "UpdateCheck"
        const val UPDATE_APK_NAME = "update_download.apk"

        /** T54 Round 2 · 焦点接管与输入法可见性日志（走查取证用，TAG=SettingsFocus）。 */
        const val FOCUS_LOG_TAG = "SettingsFocus"

        /** UI 渠道 in-flight 标记存储（T47：跨进程存活、消费即清，零残留）。 */
        const val PREFS_UI_INSTALL = "update_ui_install"
        const val KEY_UI_INSTALL_BEFORE_CODE = "before_code"

        /** T50 · 自愈重建链路的判定终态 savedState 键（仅 recreate 同进程链路，零持久化残留）。 */
        const val KEY_SAVED_UI_CHANNEL_DECISION = "ui_channel_decision"

        /** 安装结果回执 action（系统安装器回调广播，installStatusReceiver 注册接收，onCreate 接线）。 */
        const val ACTION_INSTALL_STATUS = "com.sofatype.ime.action.INSTALL_STATUS"
    }
}
