// T5 · 键盘悬浮层（FR-A2 静态布局，画板①「字母键盘·小写」）+ T6 · 遥控器焦点导航基座（FR-A1/A10/A11）
// + T7 · 英文模式完整输入（FR-A3 大小写切换 / FR-A9 特殊键）+ T10 · 中文模式接入 + 候选词栏（FR-A4/A7/A8）。
// 键帽由 KeyboardLayout 模型驱动程序化生成（键位结构单一事实源在模型，单测锁定）；
// T6 接线：DPAD 焦点导航（FocusNavigator 模型 + onKeyDown 层处理，见 onKeyDown 注释）、
// 初始焦点、OK 触发、系统返回键收起、「完成」键确认收起；「下屏」收起（T5 已有）。
// T7 接线：字母/数字/.com/@ 直出、空格、删除、光标移动、大小写切换与键面/态提示刷新。
// T8 接线：符号键盘进出与全/半切换。
// T10 接线：中/英切换（默认中文，PRD 5.2「弹出键盘（中文·小写）」）；中文模式字母入拼音组合
//（组合串经 setComposingText 上屏显示，候选栏只显示候选词 chips，决策 1/2）；
// 空格 R-2（有候选选首候选、无候选输入空格）；候选行焦点进出与行内移动（CandidateRow 模型）；
// 英文/数字模式候选栏 GONE、键盘区上移占满主卡片。组合态状态机在 ChineseComposer（纯模型，单测锁定），
// 候选查询接 :pinyin 引擎（决策 28/29，assets 内置 SPD1 产物，加载失败降级为无候选不崩溃）。
// T11 接线：候选栏右侧「更多 ⌄」入口（首行放不下时出现）展开第二行候选（候选栏下方 more_bar，
// 分页展示、页缘左右翻页，CandidateMoreRow 模型）；再次点按收起；展开行焦点导航
//（候选行 DOWN 进展开行、展开行 UP 回候选行、DOWN 出候选区回键盘）；展开态主卡加高
//（ime_card_height_expanded，键位结构与列节奏不变）。
// T15 接线：远程输入实时显示（FR-B3 显示部分 / FR-B5）——Manager 回调驱动主卡大字区显隐
//（40px 文本 + 超宽滚动末尾 + 青色光标；候选栏/键区随显隐切换）；完整远程模式状态机随 T17。
// T16 接线：上屏闭环（FR-B6/C6 · 决策 9/10）——Manager.onCommitRequested 注册上屏写入：
// commit 在电视输入框光标处插入（不覆盖其余文字，有选中区间替换该区间，InputConnection
// commitText 框架语义）；写入成功 Manager 停服（清缓冲 + token 作废），
// 大字区随 onRemoteTextChanged(null) 隐藏回键盘态、副卡配对卡随之清空（R-4 自动收起远程）。
// T17 接线：引导副卡与跨屏状态机（FR-B1/B2/B9 · 决策 32/33/34）——副卡三卡面整卡切换
//（引导卡 GUIDE / 配对卡 PAIRING / 已连接卡面 CONNECTED，RemoteUiState 由 Manager 可观测值推导）；
// 引导卡「远程输入」主按钮启动服务进入远程模式（FR-B2），主卡切远程模式框架（FR-B3：
// 状态行三态 灰/青/绿 + 来自·设备名 + 大字折行区 + 底部提示 + 「退出远程」按钮接既有 stop 链路，
// FR-B7 完整清理语义仍归 T18）；主按钮遥控器可达（行 3/4 表缘 RIGHT 进入，LEFT 返回，FR-A10/A11）。
package com.sofatype.ime

import android.inputmethodservice.InputMethodService
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.sofatype.ime.remote.RemoteInputServer
import com.sofatype.ime.remote.RemoteInputServerManager
import com.sofatype.pinyin.PinyinEngine

class SofatypeImeService : InputMethodService() {

    private companion object {
        /** CENTER 键宿主侧 showSoftInput 与 IME 收起的时序间隔（ms，实测取值见 hideSelfAfterKeyDispatch）。 */
        const val HIDE_DELAY_MS = 120L

        /**
         * 键事件诊断日志 tag（T6 评审遗留跟进：跟踪偶发焦点卡死，单次未复现）。
         * 只记键码/焦点 key id 等结构信息，禁止输出用户输入内容（隐私红线，AGENTS 禁止事项 2）。
         */
        const val TRACE_TAG = "ImeKeyTrace"

        /** 光标移动时向宿主探测文本的窗口长度（IC 光标估算 fallback 用）。 */
        const val CURSOR_PROBE_CHARS = 1000

        /** assets 内置拼音词库产物路径（T9 接线，决策 28/29）。 */
        const val DICT_ASSET_PATH = "dict/pinyin_dict.bin"

        /** 远程大字区末尾光标 Span 的锚点字符（\u00A0 不换行空格：不与末字折行拆开；宽度/绘制由 RemoteCursorSpan 提供，T33）。 */
        const val CURSOR_ANCHOR = "\u00A0"

        /**
         * 候选查询上限（T11 提至 24）：多于可见数查询；首行 + 展开行需多页候选支撑
         * FR-A15 分页翻页（12 时溢出候选不足一页，翻页无从演示）。
         */
        const val CANDIDATE_QUERY_LIMIT = 24

        /** 候选 chip 视图 tag 前缀（候选行焦点路由识别用；下标接后缀）。 */
        const val CANDIDATE_ID_PREFIX = "cand_"

        /** 「更多 ⌄」入口视图 tag（T11 · FR-A15，候选行末尾、展开/收起开关）。 */
        const val MORE_TOGGLE_ID = "more_toggle"

        /** 展开行 chip 视图 tag 前缀（下标 = 展开行候选全局下标）。 */
        const val MORE_ID_PREFIX = "more_"

        /** 「更多⌄」入口键面：收起态（画板① 原文）与展开态（chevron 翻转，FR-A15「再次点按收起」）。 */
        const val MORE_LABEL_COLLAPSED = "更多 ⌄"
        const val MORE_LABEL_EXPANDED = "更多 ⌃"

        /** 「更多⌄」入口无障碍标注（PRD 第 8 章）。 */
        const val MORE_DESC_COLLAPSED = "更多候选，点按展开"
        const val MORE_DESC_EXPANDED = "更多候选，点按收起"
    }

    /**
     * TV 恒横屏：默认的全屏提取模式会用自带编辑界面盖住宿主应用
     * （T2 测试输入框将不可见，验收链路走不通），恒关闭。
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /** 键位视图注册表：key id → 视图（焦点导航定位用），onCreateInputView 重建时清空重建。 */
    private val keyViews = mutableMapOf<String, View>()

    /** 焦点导航模型（纯 Kotlin，路径正确性由 FocusNavigatorTest/SymbolsLayoutTest 锁定）。 */
    private var navigator = FocusNavigator()

    /** 自建键盘视图引用（初始焦点 post 用；不依赖框架 getInputView 的可见性差异）。 */
    private var keyboardView: View? = null

    /** 大小写状态（FR-A3）；键盘视图重建时复位小写（键盘记忆属 FR-A13 P2，不做）。 */
    private var caseState = CaseState.LOWER

    /** 键盘布局模式：字母键盘 ↔ 符号键盘（FR-A5）。 */
    private enum class KeyboardMode { LETTERS, SYMBOLS }

    /** 当前布局模式（会话内状态）。 */
    private var keyboardMode = KeyboardMode.LETTERS

    /** 符号键盘全/半角状态（FR-A6）；仅符号键盘内有效。 */
    private var symbolsWidth = SymbolsWidth.FULL_WIDTH

    /** 语言模式（FR-A4）：中文（拼音组合）↔ 英文（直出）。键盘重建复位中文（PRD 5.2「弹出键盘（中文 · 小写）」；记忆属 FR-A13 P2 不做）。 */
    private enum class LanguageMode { CHINESE, ENGLISH }

    /** 当前语言模式（会话内状态）。 */
    private var languageMode = LanguageMode.CHINESE

    /** 中文组合态状态机（T10，纯模型）：候选查询接真实词库引擎（见 queryPinyinCandidates）。 */
    private val composer = ChineseComposer(::queryPinyinCandidates)

    /**
     * 拼音引擎（懒加载，首次查询触发）：读取 assets 内置 SPD1 产物（1.5 MB 量级，一次性成本）。
     * 加载失败置 null，中文模式退化为无候选（组合串仍可经空格上屏，不崩溃不阻塞宿主，可靠性口径）。
     */
    private val pinyinEngine: PinyinEngine? by lazy {
        try {
            assets.open(DICT_ASSET_PATH).use { PinyinEngine.load(it) }
        } catch (t: Throwable) {
            Log.w(TRACE_TAG, "拼音词库加载失败，中文模式退化为无候选", t)
            null
        }
    }

    /** 候选 chip 视图（与候选序一致）；候选行焦点路由与上屏用，不入 keyViews 键盘注册表。 */
    private val candidateViews = mutableListOf<View>()

    /** 焦点进入候选行前的键盘键 id（向下出行 / 选中上屏后的焦点回归落点）。 */
    private var candidateReturnKey: String? = null

    /** 候选词栏引用（onCreateInputView 绑定；chips 由 refreshCandidatesBar 装载）。 */
    private var candidatesBar: LinearLayout? = null

    // ---- 更多候选展开行（T11 · FR-A15） ----

    /** 展开行容器（候选栏下方，默认 GONE；onCreateInputView 绑定）。 */
    private var moreBar: LinearLayout? = null

    /** 「更多⌄」入口视图（候选行末尾；无溢出候选时不存在）。 */
    private var moreToggleView: TextView? = null

    /** 展开态：第二行候选是否展开（「更多 ⌄」开关；候选集变化即收起）。 */
    private var moreExpanded = false

    /** 当前展开行页号（0 起）。 */
    private var morePage = 0

    /** 展开行分页区间（CandidateMoreRow.paginate 结果，溢出段相对坐标系，随展开行候选刷新）。 */
    private var morePages: List<IntRange> = emptyList()

    /** 当前页 chip 视图（页内按溢出段相对序）；展开行焦点路由与上屏用。 */
    private val moreViews = mutableListOf<View>()

    /** 最近一次候选装载的全量 chip 测量宽度（与 composer.candidates 序一致；展开行分页复用）。 */
    private var candidateWidths: List<Int> = emptyList()

    /** 最近一次候选装载的首行可见数（溢出 = 总数 - 此值）。 */
    private var firstRowCount = 0

    /** 焦点进入展开行前的候选行 chip tag（UP 回首行落点）。 */
    private var moreReturnChip: String? = null

    /** 主卡几何视图（展开态高度切换用；onCreateInputView 绑定）。 */
    private var mainCardWrap: FrameLayout? = null

    /** 主卡卡体（展开态高度切换用）。 */
    private var mainCard: LinearLayout? = null

    /** 主卡柔影垫层（展开态高度随卡同步）。 */
    private var mainCardShadow: View? = null

    /** 副卡容器（T17：跨屏三卡面容器，决策 32；卡面内容由 renderRemoteUi 依状态机装配）。 */
    private var sideCard: FrameLayout? = null

    /** 远程模式主卡框架容器（T17 · FR-B3 / 决策 33）：状态行 + 大字折行区，随远程模式显隐。 */
    private var remoteModeArea: LinearLayout? = null

    /** 远程模式状态行三件套（T17 · FR-B9）：状态点（灰/青/绿）+ 状态文字 + 「来自 · 设备名」。 */
    private var remoteStatusDot: View? = null
    private var remoteStatusText: TextView? = null
    private var remoteStatusFrom: TextView? = null

    /** 「退出远程」按钮（T17 · FR-B3 / 决策 33）：远程模式内唯一可聚焦元素，接既有 stop 链路。 */
    private var remoteExitButton: TextView? = null

    /** 远程模式主卡底部提示（T17 · FR-B3 原文），随远程模式显隐。 */
    private var remoteBottomHint: TextView? = null

    /** 引导卡「远程输入」主按钮（T17 · FR-B1）：引导卡面呈现时非 null（焦点导航进出用）。 */
    private var guideButtonView: View? = null

    /** 焦点进入引导主按钮前的键盘键 id（LEFT 返回落点；FR-A11 不丢焦点）。 */
    private var guideReturnKey: String? = null

    /** 最近一次渲染的主卡模式（T17）：模式切换沿的焦点迁移只在此沿触发。 */
    private var lastRenderedMode: RemoteMode? = null

    /** 远程文本滚动容器（决策 31：折行内容超出显示区高度时纵向滚动保持末尾）。 */
    private var remoteTextScroll: ScrollView? = null

    /** 远程文本视图（40px 大字折行显示，内容 = 手机 input 全量文本 + 末尾光标 Span）。 */
    private var remoteTextView: TextView? = null

    /** 键盘区引用（候选栏隐藏/显示时切换顶部边距，FR-A7「键盘区上移占满主卡片」）。 */
    private var keyArea: FrameLayout? = null

    /** 跨行浮层键视图（空格/完成键，绝对定位挂键区）；布局切换时移除重建。 */
    private val overlayViews = mutableListOf<View>()

    /** 诊断日志：仅 debug 构建输出（T7 评审跟进项），release 静默；只记结构信息不含输入内容。 */
    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.d(TRACE_TAG, message)
    }

    override fun onCreateInputView(): View {
        keyViews.clear()
        overlayViews.clear()
        candidateViews.clear()
        candidateReturnKey = null
        caseState = CaseState.LOWER
        keyboardMode = KeyboardMode.LETTERS
        symbolsWidth = SymbolsWidth.FULL_WIDTH
        languageMode = LanguageMode.CHINESE
        composer.reset()
        navigator = FocusNavigator()
        // T11 展开行状态复位
        moreExpanded = false
        morePage = 0
        morePages = emptyList()
        moreViews.clear()
        candidateWidths = emptyList()
        firstRowCount = 0
        moreReturnChip = null
        val root = LayoutInflater.from(this).inflate(R.layout.ime_keyboard, null) as ViewGroup
        keyboardView = root
        candidatesBar = root.findViewById(R.id.candidates_bar)
        keyArea = root.findViewById(R.id.key_area)
        mainCardWrap = root.findViewById(R.id.main_card_wrap)
        mainCard = root.findViewById(R.id.main_card)
        mainCardShadow = root.findViewById(R.id.main_card_shadow)
        moreBar = root.findViewById(R.id.more_bar)
        sideCard = root.findViewById(R.id.side_card)
        remoteModeArea = root.findViewById(R.id.remote_mode_area)
        remoteStatusDot = root.findViewById(R.id.remote_status_dot)
        remoteStatusText = root.findViewById(R.id.remote_status_text)
        remoteStatusFrom = root.findViewById(R.id.remote_status_from)
        remoteExitButton = root.findViewById(R.id.remote_exit_button)
        remoteBottomHint = root.findViewById(R.id.remote_bottom_hint)
        remoteTextScroll = root.findViewById(R.id.remote_text_scroll)
        remoteTextView = root.findViewById(R.id.remote_text)
        // T17 「退出远程」按钮（FR-B3 / 决策 33）：文案与无障碍标注、可聚焦（远程模式内唯一焦点件）、
        // OK 触发（onKeyDown 确认键统一 performClick）接既有 stop 链路（FR-B7 完整清理语义仍归 T18）
        remoteExitButton?.apply {
            text = RemoteUiText.EXIT_BUTTON_LABEL
            contentDescription = RemoteUiText.EXIT_BUTTON_DESCRIPTION
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { exitRemoteMode() }
        }
        // T17 主卡底部提示（FR-B3 原文）
        remoteBottomHint?.text = RemoteUiText.REMOTE_BOTTOM_HINT
        rebuildKeys(root, requestInitialFocus = false)
        // T17 跨屏状态机渲染（副卡卡面 + 主卡模式 + 状态行）：从 Manager 可观测值推导，
        // 键盘重建（onCreateInputView）时补渲染当前会话状态（服务运行中则直接回到远程模式）
        renderRemoteUi()
        // 启动 / 停止 / 配对成功均经 onPairingStateChanged 通知（Manager 保证主线程）
        RemoteInputServerManager.onPairingStateChanged = { renderRemoteUi() }
        // T15 远程文本内容：先补渲染当前缓冲（键盘重建不丢正在显示的内容），再注册回调
        //（Manager 保证回调在主线程；键盘重建后旧视图引用随回调闭包失效，此处重新绑定）
        renderRemoteText(RemoteInputServerManager.remoteTextSnapshot())
        RemoteInputServerManager.onRemoteTextChanged = { renderRemoteText(it) }
        // T16 上屏写入（FR-B6）：commit 经 Manager 主线程派发，写入结果决定回执与停服；
        // 键盘重建后旧闭包随引用失效，此处重新绑定（与 onRemoteTextChanged 同口径）
        RemoteInputServerManager.onCommitRequested = { text -> commitRemoteText(text) }
        return root
    }

    /** 服务销毁：解除配对状态回调（Manager 为进程级单例，防悬挂引用）。 */
    override fun onDestroy() {
        RemoteInputServerManager.onPairingStateChanged = null
        RemoteInputServerManager.onRemoteTextChanged = null
        RemoteInputServerManager.onCommitRequested = null
        super.onDestroy()
    }

    /**
     * 渲染跨屏 UI（T17 · FR-B1/B2/B9 · 决策 32/33）：状态由 Manager 可观测值
     *（服务运行 / 配对 token 有效 / 配对设备名）经 [RemoteUiState.of] 推导——
     * ① 副卡按卡面装配（引导卡 / 配对卡 / 已连接卡面，整卡切换）；
     * ② 主卡按模式显隐远程模式框架（FR-B3：状态行三态 + 大字区 + 底部提示 + 「退出远程」），
     *    模式切换沿迁移焦点（进远程 → 退出远程按钮；回键盘 → 初始键，FR-A11 不丢焦点）。
     * 触发点 = 服务启停 / 配对成功回调、主按钮与退出按钮点击、键盘重建补渲染；幂等可重复调用。
     */
    private fun renderRemoteUi() {
        val state = RemoteUiState.of(
            serviceRunning = RemoteInputServerManager.isRunning(),
            pairingTokenActive = RemoteInputServerManager.isPairingTokenActive(),
            deviceName = RemoteInputServerManager.pairedDeviceName(),
        )
        renderSideCard(state)
        renderMainCardMode(state)
    }

    /**
     * 副卡三卡面装配（T17 · 决策 32 整卡切换）：
     * 未连接 → 引导卡（FR-B1）；等待扫码 → 配对卡整卡（FR-B4 / 决策 4，该卡面即等待态）；
     * 已连接 → 已连接卡面（绿点 + 设备名，不显示已失效二维码）。
     */
    private fun renderSideCard(state: RemoteUiState) {
        val container = sideCard ?: return
        guideButtonView = null
        when (state.sideCardFace) {
            RemoteSideCardFace.GUIDE -> showGuideFace(container)
            RemoteSideCardFace.PAIRING -> {
                val url = RemoteInputServerManager.pairingUrl()
                if (url != null) {
                    PairingCard.show(this, container, url)
                } else {
                    // 防御：运行中无配对地址（理论不可达）回引导卡，不留空卡
                    showGuideFace(container)
                }
            }
            RemoteSideCardFace.CONNECTED -> ConnectedCard.show(this, container, state.deviceName)
        }
        if (state.sideCardFace != RemoteSideCardFace.GUIDE) guideReturnKey = null
    }

    /** 装配引导卡面（FR-B1）并捕获主按钮视图（焦点导航进出用）。 */
    private fun showGuideFace(container: FrameLayout) {
        GuideCard.show(this, container) { enterRemoteMode() }
        guideButtonView = container.findViewWithTag<View>(GuideCard.BUTTON_TAG)
    }

    /**
     * 主卡模式与状态行渲染（T17 · FR-B3 / 决策 32/33）：远程模式显示框架区（候选栏 / 展开行 /
     * 键区隐藏），键盘态隐藏回键盘；状态行三态 = 未连接灰点 / 等待扫码青点 / 已连接绿点 +
     * 「来自 · 设备名」（FR-B9）。焦点迁移只在模式切换沿触发：进远程 → 「退出远程」按钮；
     * 回键盘 → 初始键（远程上屏自动收起与手动退出共用此沿，R-4）。
     */
    private fun renderMainCardMode(state: RemoteUiState) {
        val area = remoteModeArea ?: return
        val prevMode = lastRenderedMode
        // 状态行三态（FR-B9 / PRD 6.4 连接状态色）：点随状态换色，连接态加「来自 · 设备名」
        when (state.status) {
            RemoteStatus.NOT_CONNECTED -> {
                remoteStatusDot?.setBackgroundResource(R.drawable.bg_status_dot_idle)
                remoteStatusText?.text = RemoteUiText.STATUS_NOT_CONNECTED
                remoteStatusFrom?.visibility = View.GONE
            }
            RemoteStatus.WAITING_SCAN -> {
                remoteStatusDot?.setBackgroundResource(R.drawable.bg_status_dot_waiting)
                remoteStatusText?.text = RemoteUiText.STATUS_WAITING_SCAN
                remoteStatusFrom?.visibility = View.GONE
            }
            RemoteStatus.CONNECTED -> {
                remoteStatusDot?.setBackgroundResource(R.drawable.bg_status_dot_connected)
                remoteStatusText?.text = RemoteUiText.STATUS_CONNECTED
                remoteStatusFrom?.text = RemoteUiText.connectedFromText(state.deviceName)
                remoteStatusFrom?.visibility = View.VISIBLE
            }
        }
        if (state.mode == RemoteMode.REMOTE) {
            area.visibility = View.VISIBLE
            remoteBottomHint?.visibility = View.VISIBLE
            keyArea?.visibility = View.GONE
            candidatesBar?.visibility = View.GONE
            collapseMoreRow()
            if (prevMode != RemoteMode.REMOTE) {
                trace("remote mode -> REMOTE（status=${state.status}）")
                keyboardView?.post { remoteExitButton?.requestFocus(View.FOCUS_DOWN) }
            }
        } else {
            area.visibility = View.GONE
            remoteBottomHint?.visibility = View.GONE
            if (prevMode == RemoteMode.REMOTE) {
                trace("remote mode -> KEYBOARD")
                keyArea?.visibility = View.VISIBLE
                // 候选栏可见性按当前模式与组合态重算（含键区顶距 / 展开行回收）
                refreshCandidatesBar()
                keyboardView?.post { keyViews[navigator.initialKey()]?.requestFocus(View.FOCUS_DOWN) }
            }
        }
        lastRenderedMode = state.mode
    }

    /**
     * 进入远程模式（FR-B2 · 决策 32/33）：引导卡主按钮触发——启动服务（随机端口 + 签发 token）；
     * 成功后 Manager 经 onPairingStateChanged 通知本层切远程模式（副卡切配对卡 / 主卡切远程框架）。
     * 失败（如端口分配失败）保持键盘态引导卡，不崩溃不阻塞宿主（可靠性口径）。
     */
    private fun enterRemoteMode() {
        trace("remote enter requested")
        val result = RemoteInputServerManager.start(this)
        if (result is RemoteInputServer.StartResult.Failed) {
            // 只记失败事件，不记任何输入内容（隐私红线）；成功路径 start() 内已同步触发
            // onPairingStateChanged → renderRemoteUi（切远程模式 / 副卡切配对卡）
            trace("remote enter failed: ${result.reason}")
        }
    }

    /**
     * 退出远程模式（T17 · FR-B3 / 决策 33）：「退出远程」按钮触发，接既有服务停止链路
     *（关闭 HTTP/WS 监听、清 token / 缓冲 / 设备名）——停止回调把状态机切回键盘态
     *（副卡回引导卡、焦点回初始键）。FR-B7 完整清理语义（通知手机、已收未上屏内容丢弃提示）仍归 T18。
     */
    private fun exitRemoteMode() {
        trace("remote exit requested")
        RemoteInputServerManager.stop()
    }

    /**
     * 远程输入实时显示内容装载（T15/T33 · 决策 31 折行口径 / FR-B3）：装载 40px 大字 +
     * 末尾青色光标 Span，折行显示；折行内容超出显示区高度时纵向滚动保持末尾与光标可见。
     * T17 起显隐归属主卡模式渲染（renderMainCardMode）：本函数只负责内容，
     * null / 空 = 清空内容（远程模式框架保持；模式切换由状态机驱动）。
     * 回调由 Manager 保证在主线程；内容不入日志（隐私红线）。
     */
    private fun renderRemoteText(text: String?) {
        val textView = remoteTextView ?: return
        if (text.isNullOrEmpty()) {
            textView.text = null
            return
        }
        // 决策 31：折行显示 + 末尾青色光标（Span 锚定占位字符，随最后一行行尾；T15 横向滚动口径废止）
        val spannable = SpannableStringBuilder.valueOf(text)
        spannable.append(CURSOR_ANCHOR)
        spannable.setSpan(
            RemoteCursorSpan(
                widthPx = resources.getDimensionPixelSize(R.dimen.ime_remote_cursor_width),
                heightPx = resources.getDimensionPixelSize(R.dimen.ime_remote_cursor_height),
                color = getColor(R.color.accent),
                // 圆角半径复用 ime_remote_cursor_gap（1dp）：值同原 bg_remote_cursor 圆角（T33 评审确认无视觉偏差）；
                // 该 Token 名义语义是「光标与文字间隙」，此处按值复用作圆角——后续单独调 gap 不会破坏视觉（半径仅影响光标条两端圆润度）
                radiusPx = resources.getDimension(R.dimen.ime_remote_cursor_gap),
            ),
            spannable.length - 1,
            spannable.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        textView.text = spannable
        // 折行内容超出显示区高度时纵向滚动保持末尾（决策 31）：装载后滚动到底（光标恒可见）
        val scroll = remoteTextScroll
        scroll?.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    /**
     * 远程上屏写入（T16 · FR-B6/C6 · 决策 9/10）：把手机上屏内容写入电视输入框光标处。
     * commitText 框架语义即满足 R-3 口径：无选中区间 = 光标处插入、不覆盖其余已有文字；
     * 有选中区间 = 替换该区间。写入前先落定本地组合串（finishComposingText）：
     * 组合串是 IME 自身挂起的编辑区，不先落定会与上屏文本在光标处交叠；
     * 随后组合态复位、候选栏按无组合态重算。返回 true = 已写入（Manager 据此回执并停服）；
     * false = 无焦点输入框或写入异常（远程模式保持，手机端提示失败）。
     * 任何异常吞掉不外抛（可靠性口径：不得阻塞宿主应用）；内容不入日志（隐私红线）。
     */
    private fun commitRemoteText(text: String): Boolean {
        val ic = currentInputConnection ?: return false
        return try {
            ic.beginBatchEdit()
            try {
                ic.finishComposingText()
                composer.reset()
                refreshCandidatesBar()
                ic.commitText(text, 1)
                true
            } finally {
                ic.endBatchEdit()
            }
        } catch (t: Throwable) {
            Log.w(TRACE_TAG, "远程上屏写入失败：${t.javaClass.simpleName}")
            false
        }
    }

    /**
     * 依当前布局模式重建键区（字母键盘 ↔ 符号键盘，FR-A5/T8）：
     * 清空行容器与跨行浮层键、按模型重绑、焦点落当前布局初始键。
     * 行容器（4 行）与键区 FrameLayout 复用，仅替换内容。
     */
    private fun rebuildKeys(root: View, requestInitialFocus: Boolean) {
        navigator = when (keyboardMode) {
            KeyboardMode.LETTERS -> FocusNavigator()
            KeyboardMode.SYMBOLS -> FocusNavigator(
                SymbolsLayout.rows(symbolsWidth),
                SymbolsLayout.overlayCells(),
                SymbolsLayout.initialCell,
            )
        }
        val rows = when (keyboardMode) {
            KeyboardMode.LETTERS -> KeyboardLayout.rows
            KeyboardMode.SYMBOLS -> SymbolsLayout.rows(symbolsWidth)
        }
        bindRows(root, rows)
        bindOverlayKeys(root)
        // 候选栏可见性随布局模式刷新（FR-A7：符号键盘无组合态，候选栏隐藏）
        refreshCandidatesBar()
        if (requestInitialFocus) {
            keyboardView?.post { keyViews[navigator.initialKey()]?.requestFocus() }
        }
    }

    /**
     * 键盘显示：初始焦点落到键区左上角第一键（FR-A11 焦点导航起点）。
     * post 到 attach 完成后执行；focusableInTouchMode 保证触摸模式下焦点机制同样可用
     * （模拟器 `input tap` 唤起后紧跟 `input keyevent` 走查的场景）。
     * 新输入框重置组合态（T10）：组合串属上一输入框，候选栏随之隐藏。
     * T17：远程模式运行中（键盘重建补渲染 / 切换输入框重连）——候选栏保持隐藏
     *（远程模式显隐由 renderRemoteUi 驱动，此处不重算，防空候选栏顶低状态行），
     * 焦点落「退出远程」按钮（远程模式唯一可聚焦元素，FR-B3）；服务未运行走键盘态常规路径。
     */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.reset()
        candidateReturnKey = null
        if (RemoteInputServerManager.isRunning()) {
            keyboardView?.post { remoteExitButton?.requestFocus(View.FOCUS_DOWN) }
            return
        }
        refreshCandidatesBar()
        keyboardView?.post {
            keyViews[navigator.initialKey()]?.requestFocus()
        }
    }

    /**
     * 键事件（FR-A1 / FR-A11）。实测（模拟器 API 34）：框架把转发来的 DPAD 事件直接递到
     * InputMethodService.onKeyDown，不经过 IME view 树（键帽视图的 OnKeyListener 收不到），
     * 因此方向键导航在本层处理。
     * BACK 收起键盘（FR-A1）：已上屏文本保留在宿主输入连接中，收起不丢失；
     * 显式实现（与框架默认行为一致），不依赖各 API 级别的默认口径差异。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val ev = event ?: return super.onKeyDown(keyCode, event)
        // T6 评审遗留跟进：偶发焦点卡死诊断（T7 已定位为注入层事件丢失）。只记键码/焦点 key id
        // 结构信息，不输出用户输入内容（隐私红线）
        trace("onKeyDown keyCode=$keyCode action=${ev.action} focus=${(keyboardView?.findFocus() as? View)?.tag}")
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            requestHideSelf(0)
            return true
        }
        val direction = DPAD_DIRECTIONS[keyCode]
        if (direction != null) {
            // 导航仅响应 ACTION_DOWN 首次按下（长按重复不连续移动）；DOWN 分支返回 true 消费。
            // onKeyUp 未覆盖：ACTION_UP 落到 super（super 对方向键默认无焦点行为）。
            // 不进系统默认焦点处理，防 focus search 跳变。
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                val currentId = (keyboardView?.findFocus() as? View)?.tag as? String
                if (currentId != null) {
                    // 候选区与键盘格网分层路由（T10 · FR-A7 / T11 · FR-A15 / T17 · FR-B1）：焦点在候选区
                    //（首行 chip、「更多⌄」入口、展开行 chip）走候选区语义，引导卡主按钮走引导语义，
                    // 其余（键盘键）走 FocusNavigator 格网语义，含从行 1 键向上进入候选行
                    when {
                        currentId == GuideCard.BUTTON_TAG -> handleGuideButtonDirection(direction)
                        currentId == MORE_TOGGLE_ID -> handleMoreToggleDirection(direction)
                        currentId.isMoreChipId() -> handleMoreRowDirection(currentId, direction)
                        currentId.isCandidateId() -> handleCandidateRowDirection(currentId, direction)
                        else -> handleKeyboardDirection(currentId, direction)
                    }
                }
            }
            return true
        }
        if (keyCode.isConfirmKey()) {
            // OK/回车触发焦点键（FR-A11）：框架转发路径不经过 view 树，焦点视图默认 click
            // 不可用，在此显式派发 performClick
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                keyboardView?.findFocus()?.performClick()
            }
            return true
        }
        return super.onKeyDown(keyCode, ev)
    }

    /** 遥控器 OK / 回车键集合（FR-A11「OK 确认」）。 */
    private fun Int.isConfirmKey(): Boolean = this == KeyEvent.KEYCODE_DPAD_CENTER ||
        this == KeyEvent.KEYCODE_ENTER ||
        this == KeyEvent.KEYCODE_NUMPAD_ENTER

    /** 候选 chip 视图 tag 判定（候选行焦点路由识别）。 */
    private fun String.isCandidateId(): Boolean = startsWith(CANDIDATE_ID_PREFIX)

    /** 候选 chip 视图 tag → 候选下标。 */
    private fun String.candidateIndex(): Int? = removePrefix(CANDIDATE_ID_PREFIX).toIntOrNull()

    /** 展开行 chip 视图 tag 判定（T11 · FR-A15）。 */
    private fun String.isMoreChipId(): Boolean = startsWith(MORE_ID_PREFIX)

    /** 展开行 chip 视图 tag → 展开行全局下标。 */
    private fun String.moreChipIndex(): Int? = removePrefix(MORE_ID_PREFIX).toIntOrNull()

    /** 焦点是否在候选区任一视图（首行 chip /「更多⌄」入口 / 展开行 chip）；候选区移除后的焦点回归判定用。 */
    private fun String.isCandidateLayerId(): Boolean =
        isCandidateId() || this == MORE_TOGGLE_ID || isMoreChipId()

    /**
     * 键盘键方向导航（T6 格网语义 + T10 候选行进入 + T17 引导卡主按钮进入）：
     * 行 1 键向上且候选栏可见时进入候选行（FR-A7「上下键进出候选行」，进入落点 = 首候选 chip）；
     * 行 3/4 表缘向右且引导卡主按钮可达时进入主按钮（FR-B1「远程输入」主按钮遥控器可达——
     * 主按钮位于副卡下部，与完成键同处行 3/4 纵向带，视觉行列关系对齐，FR-A11 不跳变；
     * 行 1/2 表缘向右保持表缘不动口径）；其余方向走 FocusNavigator 格网（表缘消费不动，不跳变）。
     */
    private fun handleKeyboardDirection(currentId: String, direction: FocusDirection) {
        val onTopRow = navigator.positionsOf(currentId).any { it.first == 1 }
        if (direction == FocusDirection.UP && onTopRow && candidateViews.isNotEmpty()) {
            candidateReturnKey = currentId
            trace("focus $currentId UP -> cand_0（进入候选行）")
            candidateViews[0].requestFocus(View.FOCUS_DOWN)
            return
        }
        val nextId = navigator.next(currentId, direction)
        if (nextId == null && direction == FocusDirection.RIGHT && canEnterGuideButton(currentId)) {
            guideReturnKey = currentId
            trace("focus $currentId RIGHT -> ${GuideCard.BUTTON_TAG}（进入引导卡主按钮）")
            guideButtonView?.requestFocus(View.FOCUS_DOWN)
            return
        }
        trace("focus $currentId $direction -> $nextId")
        val target = nextId?.let { keyViews[it] }
        if (target != null) target.requestFocus(View.FOCUS_DOWN)
    }

    /** 引导卡主按钮当前是否可进入（引导卡面呈现 + 焦点键占据行 3/4 格位，与按钮纵向带对齐）。 */
    private fun canEnterGuideButton(currentId: String): Boolean =
        guideButtonView != null &&
            navigator.positionsOf(currentId).any { it.first == 3 || it.first == 4 }

    /** 引导卡主按钮方向导航（T17 · FR-A11）：LEFT 回进入前的键盘键（丢失回初始键）；其余表缘不动。 */
    private fun handleGuideButtonDirection(direction: FocusDirection) {
        if (direction != FocusDirection.LEFT) return
        val targetId = guideReturnKey?.takeIf { keyViews.containsKey(it) } ?: navigator.initialKey()
        trace("focus ${GuideCard.BUTTON_TAG} LEFT -> $targetId（回键盘）")
        keyViews[targetId]?.requestFocus(View.FOCUS_DOWN)
    }

    /**
     * 候选行方向导航（T10 · FR-A7 + T11 · FR-A15）：左右在候选间移动、向上到顶不动、
     * 向下 = 展开行可见时进入展开行（展开行在首行正下方，符合视觉行列关系）、否则出候选区回键盘
     *（落点 = 进入前的键盘键，丢失时回初始键）。行末 chip 向右 →「更多⌄」入口（入口可见时）。
     * 行内语义见 CandidateRow（纯模型，单测锁定）。
     */
    private fun handleCandidateRowDirection(currentId: String, direction: FocusDirection) {
        val index = currentId.candidateIndex() ?: return
        when (val move = CandidateRow.move(index, direction, candidateViews.size)) {
            is CandidateRowMove.To -> {
                trace("focus $currentId $direction -> cand_${move.index}")
                candidateViews[move.index].requestFocus(View.FOCUS_DOWN)
            }
            CandidateRowMove.ExitDown ->
                if (isMoreRowActive()) {
                    enterMoreRow(currentId)
                } else {
                    exitCandidatesLayerToKeyboard(currentId)
                }
            CandidateRowMove.Stay -> {
                // 行 1 末 chip 向右 → 「更多⌄」入口（T11）；其余表缘保持不动（T10 口径）
                if (direction == FocusDirection.RIGHT && index == candidateViews.size - 1) {
                    moreToggleView?.requestFocus(View.FOCUS_DOWN)
                }
            }
        }
    }

    /**
     * 「更多⌄」入口方向导航（T11 · FR-A15）：LEFT 回首行末 chip（与 chip 向右互进对称）；
     * 向上到顶不动、向右表缘不动；向下 = 展开行可见时进入展开行、收起时出候选区回键盘。
     */
    private fun handleMoreToggleDirection(direction: FocusDirection) {
        when (direction) {
            FocusDirection.LEFT -> candidateViews.lastOrNull()?.requestFocus(View.FOCUS_DOWN)
            FocusDirection.DOWN ->
                if (isMoreRowActive()) {
                    enterMoreRow(MORE_TOGGLE_ID)
                } else {
                    exitCandidatesLayerToKeyboard(MORE_TOGGLE_ID)
                }
            FocusDirection.RIGHT, FocusDirection.UP -> Unit
        }
    }

    /**
     * 展开行方向导航（T11 · FR-A15「展开行同样支持焦点导航」）：页内左右移动、页缘同方向翻页
     *（右缘向后翻落新页首 chip、左缘向前翻落新页末 chip，模型见 CandidateMoreRow，单测锁定）；
     * UP 回候选首行（落点 = 进入前 chip，丢失回「更多⌄」入口）；DOWN 出候选区回键盘。
     */
    private fun handleMoreRowDirection(currentId: String, direction: FocusDirection) {
        val index = currentId.moreChipIndex() ?: return
        when (val move = CandidateMoreRow.move(index, direction, morePages)) {
            is CandidateMoreMove.To -> {
                trace("focus $currentId $direction -> more_${move.index}")
                focusMoreChip(move.index)
            }
            is CandidateMoreMove.Flip -> {
                trace("focus $currentId $direction -> page ${move.page}（翻页，落 more_${move.index}）")
                morePage = move.page
                bindMorePage()
                focusMoreChip(move.index)
            }
            CandidateMoreMove.ExitUp -> {
                val returnTag = moreReturnChip
                    ?.takeIf { it == MORE_TOGGLE_ID || it.isCandidateId() }
                trace("focus $currentId UP -> ${returnTag ?: "cand_0"}（回首行）")
                if (returnTag == MORE_TOGGLE_ID) {
                    moreToggleView?.requestFocus(View.FOCUS_DOWN)
                } else {
                    val idx = returnTag?.candidateIndex()?.takeIf { it in candidateViews.indices } ?: 0
                    candidateViews.getOrNull(idx)?.requestFocus(View.FOCUS_DOWN)
                }
            }
            CandidateMoreMove.ExitDown -> exitCandidatesLayerToKeyboard(currentId)
            CandidateMoreMove.Stay -> Unit
        }
    }

    /** 展开行是否处于可用状态（展开态 + 容器可见 + 当前页有 chip）。 */
    private fun isMoreRowActive(): Boolean =
        moreExpanded && moreBar?.visibility == View.VISIBLE && moreViews.isNotEmpty()

    /** 进入展开行（落当前页首 chip），记录 UP 回首行的落点 chip。 */
    private fun enterMoreRow(returnChip: String) {
        moreReturnChip = returnChip
        trace("focus $returnChip DOWN -> more（进入展开行）")
        focusMoreChip(morePages.getOrNull(morePage)?.first ?: return)
    }

    /** 焦点落展开行 chip（展开行全局下标；越界不落）。 */
    private fun focusMoreChip(globalIndex: Int) {
        val page = morePages.getOrNull(morePage) ?: return
        if (globalIndex !in page) return
        moreViews.getOrNull(globalIndex - page.first)?.requestFocus(View.FOCUS_DOWN)
    }

    /** 出候选区回键盘（T10 落点口径：进入候选区前的键盘键，丢失时回初始键）。 */
    private fun exitCandidatesLayerToKeyboard(fromId: String) {
        val targetId = candidateReturnKey?.takeIf { keyViews.containsKey(it) }
            ?: navigator.initialKey()
        trace("focus $fromId DOWN -> $targetId（出候选区）")
        keyViews[targetId]?.requestFocus(View.FOCUS_DOWN)
    }

    /** 行容器 id 与 KeyboardLayout.rows 一一对应（顺序即行序）。 */
    private val rowIds = intArrayOf(R.id.key_row_1, R.id.key_row_2, R.id.key_row_3, R.id.key_row_4)

    /** 依模型逐行生成键帽与列间隔（键位结构一致性由 KeyboardLayoutTest/SymbolsLayoutTest 锁定）。 */
    private fun bindRows(root: View, rows: List<KeyRow>) {
        check(rows.size == rowIds.size) { "行容器数量与模型行数不一致" }
        val spacing = resources.getDimensionPixelSize(R.dimen.ime_key_spacing)
        val spacerWidth = resources.getDimensionPixelSize(R.dimen.ime_column_spacer)
        val keyWidth = resources.getDimensionPixelSize(R.dimen.ime_key_width)
        rows.forEachIndexed { rowIndex, row ->
            val container = root.findViewById<LinearLayout>(rowIds[rowIndex])
            container.removeAllViews()
            row.cells.forEachIndexed { cellIndex, cell ->
                when (cell) {
                    is Cell.KeyCell -> {
                        val isRowEnd = cellIndex == row.cells.lastIndex
                        container.addView(wrapForRow(cell.key, isRowEnd, spacing))
                    }
                    Cell.Spacer -> container.addView(
                        View(this),
                        LinearLayout.LayoutParams(spacerWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            // 画板① 列间隔 = 键距 12px + spacer 42px + 键距 12px（.krow flex gap 双侧生效）；
                            // spacer 自带右侧键距，与左键 marginEnd 合成 66px 列间隔
                            marginEnd = spacing
                        },
                    )
                    // 空格位：占一个键格但无键（透明占位，保持流式布局与格网对齐；T8 半角行 4）
                    Cell.EmptyCell -> container.addView(
                        View(this),
                        LinearLayout.LayoutParams(keyWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            if (cellIndex != row.cells.lastIndex) marginEnd = spacing
                        },
                    )
                }
            }
        }
    }

    /**
     * 跨行浮层键绑定（绝对定位挂键区）：字母键盘 = 完成键；符号键盘 = 空格键 + 完成键
     * （决策 3：跨行 3/4，col11/12，画板③ 84×196 对称）。布局切换时先移除旧浮层视图。
     */
    private fun bindOverlayKeys(root: View) {
        val keyArea = root.findViewById<FrameLayout>(R.id.key_area)
        for (view in overlayViews) {
            keyArea.removeView(view)
        }
        overlayViews.clear()
        val overlays: List<Pair<Key, Int>> = when (keyboardMode) {
            // 完成键：键区内偏移 1120px（T28 对齐口径）
            KeyboardMode.LETTERS -> listOf(KeyboardLayout.doneKey to R.dimen.ime_done_key_start)
            // 空格键：col11 原点 1028px；完成键：col12 原点 1120px
            KeyboardMode.SYMBOLS -> listOf(
                SymbolsLayout.spaceKey to R.dimen.ime_space_key_start,
                SymbolsLayout.doneKey to R.dimen.ime_done_key_start,
            )
        }
        for ((key, startRes) in overlays) {
            val view = createKeyView(key)
            overlayViews.add(view)
            keyArea.addView(
                view,
                FrameLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.ime_done_key_width),
                    resources.getDimensionPixelSize(R.dimen.ime_done_key_height),
                    Gravity.TOP or Gravity.START,
                ).apply {
                    marginStart = resources.getDimensionPixelSize(startRes)
                    topMargin = resources.getDimensionPixelSize(R.dimen.ime_done_key_top)
                },
            )
        }
    }

    /** 行内键帽包装：固定键帽尺寸，键间留键距（行尾不留）。 */
    private fun wrapForRow(key: Key, isRowEnd: Boolean, spacing: Int): View {
        val view = createKeyView(key)
        view.layoutParams = LinearLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.ime_key_width),
            resources.getDimensionPixelSize(R.dimen.ime_key_height),
        ).apply {
            if (!isRowEnd) marginEnd = spacing
        }
        return view
    }

    /**
     * 键帽视图装配（T6）：注册视图表、可聚焦、挂 click。
     * 方向键与 OK/回车由 onKeyDown 统一处理（框架转发路径不经过 view 树）。
     */
    private fun createKeyView(key: Key): View {
        val view = createKeyContentView(key)
        view.tag = key.id
        keyViews[key.id] = view
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnClickListener { execute(key) }
        return view
    }

    /** 键帽内容视图：文字键 = TextView；图标键 = ImageView（图标尺寸按画板① Token）。 */
    private fun createKeyContentView(key: Key): View {
        val view: View = if (key.icon != null) createIconKey(key) else createTextKey(key)
        key.contentDescription?.let { view.contentDescription = it }
        return view
    }

    private fun createTextKey(key: Key): TextView {
        val sizeRes = if (key.textSize == KeyTextSize.SMALL) R.dimen.ime_key_fn_text else R.dimen.ime_key_text
        return TextView(this).apply {
            // 键面随大小写状态（FR-A3）：字母键在小写态创建即显示小写
            text = KeyboardLayout.displayLabel(key, caseState)
            gravity = Gravity.CENTER
            maxLines = 1
            // dimens 中以 sp 声明，getDimension 已换算为 px，按 px 设置避免二次缩放
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(sizeRes))
            setTextColor(getColor(textColorOf(key.variant)))
            background = getDrawable(backgroundOf(key.variant))
        }
    }

    private fun createIconKey(key: Key): ImageView {
        val icon = key.icon ?: error("图标键缺少图标定义：${key.id}")
        val keyWidth = resources.getDimensionPixelSize(R.dimen.ime_key_width)
        val keyHeight = resources.getDimensionPixelSize(R.dimen.ime_key_height)
        val iconSize = resources.getDimensionPixelSize(iconSizeOf(icon))
        return ImageView(this).apply {
            setImageResource(iconResOf(icon))
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = getDrawable(backgroundOf(key.variant))
            // 上档键图标色随大小写状态（FR-A3 画板②激活态 = accent_text）；其余图标色即资源本色
            if (icon == KeyIcon.SHIFT) {
                setColorFilter(getColor(if (caseState == CaseState.UPPER) R.color.accent_text else R.color.text_secondary))
            }
            // padding 限定图标绘制区 = 键帽中心 iconSize 见方（画板① 图标尺寸）
            setPadding((keyWidth - iconSize) / 2, (keyHeight - iconSize) / 2, (keyWidth - iconSize) / 2, (keyHeight - iconSize) / 2)
        }
    }

    /**
     * 执行按键动作。T6 接线：下屏收起（FR-A1）、完成键确认收起（FR-A10）；
     * T7 接线：文本直出 / 空格 / 删除 / 光标；T8 接线：符号键盘；T10 接线：中文组合态与候选。
     * 无焦点连接时静默忽略，不崩溃（可靠性要求：输入法异常不得阻塞宿主应用）。
     */
    private fun execute(key: Key) {
        when (val action = key.action) {
            KeyAction.HideSelf -> hideSelfAfterKeyDispatch()
            KeyAction.Done -> {
                // FR-A10「确认当前输入并收起」：结束组合态（组合串原样上屏，无组合时为无操作），再收起
                currentInputConnection?.finishComposingText()
                composer.reset()
                hideSelfAfterKeyDispatch()
            }
            // FR-A9 文本输出 × FR-A4 语言模式：中文模式字母键进入拼音组合（FR-A8，组合串恒小写，
            // 大小写状态只作用于键面），数字/.com/@ 与英文模式全部直出（符号键盘直出符号，FR-A5/A6）
            is KeyAction.Text -> {
                if (keyboardMode == KeyboardMode.SYMBOLS) {
                    commitText(action.value)
                } else if (languageMode == LanguageMode.CHINESE &&
                    key.id in KeyboardLayout.letterKeyIds
                ) {
                    composer.appendLetter(action.value.first())
                    updateComposingText()
                    refreshCandidatesBar()
                } else {
                    commitText(KeyboardLayout.outputValue(key, caseState))
                }
            }
            // 决策 8（R-2）组合态空格：有候选选首候选；无候选时组合串以字母形式上屏后再输入空格；
            // 英文模式 / 无组合态直出空格（FR-A9）
            KeyAction.Space -> {
                if (languageMode == LanguageMode.CHINESE && composer.hasCandidates) {
                    selectCandidate(requireNotNull(composer.firstCandidate()))
                } else if (languageMode == LanguageMode.CHINESE && composer.composition.isNotEmpty()) {
                    finishComposition()
                    commitText(" ")
                    refreshCandidatesBar()
                } else {
                    commitText(" ")
                }
            }
            // FR-A9 删除：组合态内删组合串末位并刷新候选；组合态外删宿主文本（选区优先）
            KeyAction.Delete -> {
                if (composer.composition.isNotEmpty()) {
                    composer.deleteLastLetter()
                    updateComposingText()
                    refreshCandidatesBar()
                } else {
                    deleteBackward()
                }
            }
            // FR-A9：光标在输入框内移动（插入点随移动生效，走查覆盖）
            KeyAction.CursorLeft -> moveCursor(-1)
            KeyAction.CursorRight -> moveCursor(1)
            // FR-A3：大小写切换，刷新字母区键面与上档键态提示
            KeyAction.ToggleCase -> {
                caseState = if (caseState == CaseState.UPPER) CaseState.LOWER else CaseState.UPPER
                trace("case -> $caseState")
                applyCaseState()
            }
            // FR-A5：进入符号键盘 / 返回字母键盘；FR-A6：全/半角切换（符号键盘内）
            KeyAction.OpenSymbols -> switchKeyboardMode(KeyboardMode.SYMBOLS)
            KeyAction.BackToLetters -> switchKeyboardMode(KeyboardMode.LETTERS)
            KeyAction.ToggleWidth -> {
                symbolsWidth =
                    if (symbolsWidth == SymbolsWidth.FULL_WIDTH) SymbolsWidth.HALF_WIDTH
                    else SymbolsWidth.FULL_WIDTH
                trace("symbolsWidth -> $symbolsWidth")
                rebuildKeys(requireNotNull(keyboardView), requestInitialFocus = true)
            }
            // FR-A4 中/英切换：先结束组合态（组合串以字母形式上屏，不丢已按键内容），再切换；
            // 英文模式候选栏隐藏（FR-A7）
            KeyAction.ToggleLanguage -> {
                finishComposition()
                languageMode =
                    if (languageMode == LanguageMode.CHINESE) LanguageMode.ENGLISH else LanguageMode.CHINESE
                trace("language -> $languageMode")
                refreshCandidatesBar()
            }
        }
    }

    /** 字母键盘 ↔ 符号键盘切换（FR-A5）：先结束组合态（组合串原样上屏），再重建键区视图与导航格网，焦点落新布局初始键。 */
    private fun switchKeyboardMode(mode: KeyboardMode) {
        finishComposition()
        keyboardMode = mode
        trace("keyboardMode -> $mode")
        rebuildKeys(requireNotNull(keyboardView), requestInitialFocus = true)
    }

    /** 结束组合态（T10）：组合串以字母形式原样上屏（不丢已按键内容），状态机复位；无组合时为无操作。 */
    private fun finishComposition() {
        if (composer.composition.isEmpty()) return
        currentInputConnection?.finishComposingText()
        composer.reset()
    }

    /**
     * 组合串写入宿主（FR-A8 组合态）：经 setComposingText 显示在输入框光标处（下划线组合态样式）。
     * 选中候选时经 commitText 覆盖同区域（拼音串被候选词替换）。
     */
    private fun updateComposingText() {
        val ic = currentInputConnection ?: return
        ic.setComposingText(composer.composition, 1)
    }

    /** 选中候选上屏（FR-A8 选词上屏 / R-2 空格选首候选 / 候选行·展开行 OK 选中）：候选词替换组合串区域，组合态结束。 */
    private fun selectCandidate(word: String) {
        // 先记焦点：chips 将随组合态结束被清空，候选区上的焦点需回归键盘；键盘键上的焦点不动（继续输入）
        val focusTag = (keyboardView?.findFocus() as? View)?.tag as? String
        composer.reset()
        commitText(word)
        refreshCandidatesBar()
        if (focusTag?.isCandidateLayerId() == true) {
            refocusKeyboardAfterBarHidden()
        }
    }

    /** 拼音候选查询（ChineseComposer 注入点）：接 :pinyin 真实词库引擎；引擎未就绪时无候选（降级不崩溃）。 */
    private fun queryPinyinCandidates(input: String): List<String> {
        val engine = pinyinEngine ?: return emptyList()
        return engine.query(input, CANDIDATE_QUERY_LIMIT).map { it.word }
    }

    // ---- 候选词栏（T10 · FR-A7）与更多候选展开行（T11 · FR-A15） ----

    /**
     * 刷新候选栏可见性与内容（组合态变化、模式切换、键盘重建后调用）。
     * 栏区显隐 = 字母键盘 × 中文模式（英文/数字/符号键盘整栏 GONE、键盘区上移占满主卡片，FR-A7 评审补充）；
     * chips 仅组合态有候选时装载（决策 1：只显示候选词，无组合态时栏区留空）；
     * 候选区整体移除 / 候选集变化时展开行同步收起（T11，展开态只在同一候选集内有效）。
     */
    private fun refreshCandidatesBar() {
        val bar = candidatesBar ?: return
        val barVisible = keyboardMode == KeyboardMode.LETTERS &&
            languageMode == LanguageMode.CHINESE
        if (!barVisible) {
            // 先记焦点再清空：chip 视图移除时框架会清焦点，事后查不到
            val focusTag = (keyboardView?.findFocus() as? View)?.tag as? String
            bar.visibility = View.GONE
            setKeyAreaTopMargin(visible = false)
            bar.removeAllViews()
            candidateViews.clear()
            moreToggleView = null
            collapseMoreRow()
            // 焦点原在候选区（chip / 更多⌄ / 展开行）：随栏移除，焦点回落键盘
            if (focusTag?.isCandidateLayerId() == true) {
                refocusKeyboardAfterBarHidden()
            }
            return
        }
        bar.visibility = View.VISIBLE
        setKeyAreaTopMargin(visible = true)
        if (composer.hasCandidates) {
            bindCandidates(bar)
        } else {
            bar.removeAllViews()
            candidateViews.clear()
            moreToggleView = null
            collapseMoreRow()
        }
    }

    /**
     * 依候选装载首行 chips 与「更多 ⌄」入口（T11 · FR-A15）：
     * 先测宽、依可用宽度截断（CandidateChips.fitCount）；全宽截断后仍有溢出时，首行可用宽度按
     * 入口占位宽度收缩后再截断（防入口与 chips 重叠），行末追加「更多 ⌄」入口；
     * 入口可见 = 仍有候选未展示。候选集变化即收起展开行。
     */
    private fun bindCandidates(bar: LinearLayout) {
        bar.removeAllViews()
        candidateViews.clear()
        moreToggleView = null
        collapseMoreRow()
        val candidates = composer.candidates
        if (candidates.isEmpty()) return
        val gap = resources.getDimensionPixelSize(R.dimen.ime_chip_gap)
        val available = candidatesAvailablePx()
        // 未挂载状态下先量宽再装载（TextView measure UNSPECIFIED = 文字自适应宽，结果已含 padding）。
        // 未挂载 TextView 也必须有 LayoutParams：setText 触发 checkForRelayout 会解引用
        // layoutParams，为 null 时 NPE（模拟器走查实测崩溃点，已修）
        val measurer = createChipMeasurer()
        val widths = candidates.map { word ->
            measurer.text = word
            measurer.measure(0, 0)
            measurer.measuredWidth
        }
        candidateWidths = widths
        val fitFull = CandidateChips.fitCount(widths, gap, available)
        var count = fitFull
        var toggleVisible = false
        if (CandidateMoreRow.overflowCount(candidates.size, fitFull) > 0) {
            // 入口实际占位 = 测量宽 + 入口前间距（gap），收缩量按占位全额计（评审 Round 1 建议 2）
            count = CandidateChips.fitCount(widths, gap, available - moreToggleReservedPx())
            toggleVisible = count < candidates.size
        }
        firstRowCount = count
        for (index in 0 until count) {
            val view = createChipView(candidates[index], CANDIDATE_ID_PREFIX + index)
            candidateViews.add(view)
            bar.addView(
                view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply {
                    // 末 chip 免 marginEnd：与入口的间距由入口自身 marginStart 提供，
                    // 不叠加双倍 gap（评审 Round 1 建议 1）
                    if (index != count - 1) marginEnd = gap
                },
            )
        }
        if (toggleVisible) bindMoreToggle(bar, gap)
    }

    /**
     * 候选 chip 视图（首行 / 展开行共用，T10/T11）：可聚焦（遥控器候选行导航）、OK 选中上屏。
     * 无常亮高亮（决策 30：含首候选，高亮仅表达遥控器焦点态，样式见 applyChipAppearance）。
     */
    private fun createChipView(word: String, tag: String): TextView {
        val view = TextView(this).apply {
            text = word
            gravity = Gravity.CENTER
            maxLines = 1
            // dimens 中以 sp 声明，getDimension 已换算为 px，按 px 设置避免二次缩放
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ime_chip_text))
            setPadding(
                resources.getDimensionPixelSize(R.dimen.ime_chip_padding_h),
                0,
                resources.getDimensionPixelSize(R.dimen.ime_chip_padding_h),
                0,
            )
            this.tag = tag
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { selectCandidate(word) }
            onFocusChangeListener = View.OnFocusChangeListener { v, _ -> applyChipAppearance(v) }
        }
        applyChipAppearance(view)
        return view
    }

    /** 候选 chip 测量用 TextView（未挂载；先设 LayoutParams 防 NPE，T10 坑口径）。 */
    private fun createChipMeasurer(): TextView {
        val measurer = TextView(this)
        measurer.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        measurer.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ime_chip_text))
        measurer.setPadding(
            resources.getDimensionPixelSize(R.dimen.ime_chip_padding_h),
            0,
            resources.getDimensionPixelSize(R.dimen.ime_chip_padding_h),
            0,
        )
        return measurer
    }

    /** 候选行可用宽度（候选栏实宽，未挂载时按主卡 Token 推算，T10 口径）。 */
    private fun candidatesAvailablePx(): Int =
        candidatesBar?.takeIf { it.width > 0 }?.width
            ?: resources.getDimensionPixelSize(R.dimen.ime_main_card_width) -
                2 * resources.getDimensionPixelSize(R.dimen.ime_card_padding_h)

    /**
     * 「更多⌄」入口实际占位宽度（首行可用宽度收缩量）：
     * 测量文字宽 + 入口前间距（gap）——入口与末 chip 的间距由入口自身 marginStart 提供，
     * 末 chip 免 marginEnd，占位须全额计入防贴边溢出（评审 Round 1 建议 2）。
     */
    private fun moreToggleReservedPx(): Int {
        val measurer = TextView(this)
        measurer.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        measurer.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ime_key_fn_text))
        measurer.setPadding(
            resources.getDimensionPixelSize(R.dimen.ime_chip_padding_h),
            0,
            resources.getDimensionPixelSize(R.dimen.ime_chip_padding_h),
            0,
        )
        measurer.text = MORE_LABEL_COLLAPSED
        measurer.measure(0, 0)
        return measurer.measuredWidth + resources.getDimensionPixelSize(R.dimen.ime_chip_gap)
    }

    /** 「更多⌄」入口视图（T11 · FR-A15）：候选行末尾文字入口，OK 切换展开/收起。 */
    private fun bindMoreToggle(bar: LinearLayout, gap: Int) {
        val view = TextView(this).apply {
            text = MORE_LABEL_COLLAPSED
            contentDescription = MORE_DESC_COLLAPSED
            gravity = Gravity.CENTER
            maxLines = 1
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ime_key_fn_text))
            setPadding(
                resources.getDimensionPixelSize(R.dimen.ime_chip_padding_h),
                0,
                resources.getDimensionPixelSize(R.dimen.ime_chip_padding_h),
                0,
            )
            tag = MORE_TOGGLE_ID
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { toggleMoreCandidates() }
            onFocusChangeListener = View.OnFocusChangeListener { v, _ -> applyToggleAppearance(v as TextView) }
        }
        applyToggleAppearance(view)
        moreToggleView = view
        bar.addView(
            view,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ).apply { marginStart = gap },
        )
    }

    /**
     * 「更多⌄」入口底形与文字色：普通态 = 无底色 + 画板① 入口淡文字色（text_faint）；
     * 焦点态 = 主色描边 + accent-text 文字（PRD 6.4，与候选 chip 焦点态同形）。
     */
    private fun applyToggleAppearance(view: TextView) {
        if (view.isFocused) {
            view.background = getDrawable(R.drawable.bg_chip_highlight)
            view.setTextColor(getColor(R.color.accent_text))
        } else {
            view.background = null
            view.setTextColor(getColor(R.color.text_faint))
        }
    }

    /**
     * 「更多⌄」开关（FR-A15「点按展开、再次点按收起」）：切换展开态并刷新展开行；
     * 入口保持焦点，键面 chevron 翻转（收起 ⌄ / 展开 ⌃）与无障碍标注随态更新。
     */
    private fun toggleMoreCandidates() {
        moreExpanded = !moreExpanded
        morePage = 0
        moreReturnChip = null
        trace("more -> ${if (moreExpanded) "EXPANDED" else "COLLAPSED"}")
        refreshMoreRow()
        moreToggleView?.let { toggle ->
            toggle.text = if (moreExpanded) MORE_LABEL_EXPANDED else MORE_LABEL_COLLAPSED
            toggle.contentDescription = if (moreExpanded) MORE_DESC_EXPANDED else MORE_DESC_COLLAPSED
            applyToggleAppearance(toggle)
        }
    }

    /**
     * 刷新展开行（T11 · FR-A15）：展开态 = 主卡切展开态高度（展开行占候选栏下方一行）
     * + 依 CandidateMoreRow.paginate 分页装载当前页 chips；收起 / 无溢出候选 = 整行 GONE + 主卡回收起态。
     */
    private fun refreshMoreRow() {
        val bar = moreBar ?: return
        val overflow = CandidateMoreRow.overflowCount(composer.candidates.size, firstRowCount)
        if (!moreExpanded || overflow <= 0 || firstRowCount <= 0) {
            collapseMoreRow()
            return
        }
        val gap = resources.getDimensionPixelSize(R.dimen.ime_chip_gap)
        morePages = CandidateMoreRow.paginate(
            candidateWidths.drop(firstRowCount),
            gap,
            candidatesAvailablePx(),
        )
        if (morePages.isEmpty()) {
            collapseMoreRow()
            return
        }
        morePage = morePage.coerceIn(0, morePages.size - 1)
        bar.visibility = View.VISIBLE
        applyMoreGeometry(expanded = true)
        bindMorePage()
    }

    /**
     * 装载展开行当前页 chips（tag = 溢出段相对下标，与 CandidateMoreRow 模型同一坐标系；
     * 词条 = 首行可见数 + 相对下标；OK 选中上屏，无常亮高亮）。
     */
    private fun bindMorePage() {
        val bar = moreBar ?: return
        bar.removeAllViews()
        moreViews.clear()
        val page = morePages.getOrNull(morePage) ?: return
        val gap = resources.getDimensionPixelSize(R.dimen.ime_chip_gap)
        val candidates = composer.candidates
        for (relIndex in page) {
            val wordIndex = firstRowCount + relIndex
            if (wordIndex !in candidates.indices) break
            val view = createChipView(candidates[wordIndex], MORE_ID_PREFIX + relIndex)
            moreViews.add(view)
            bar.addView(
                view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ).apply {
                    if (relIndex != page.last) marginEnd = gap
                },
            )
        }
    }

    /**
     * 收起展开行：整行 GONE、主卡回收起态高度、页状态清零。
     * 不在此回归焦点：可能同时收起候选栏的调用方（refreshCandidatesBar 隐藏路径 / selectCandidate）
     * 各自按候选区焦点判定统一回归，避免双重回归二次消费 candidateReturnKey（落错初始键，走查实测）。
     */
    private fun collapseMoreRow() {
        val bar = moreBar ?: return
        if (bar.visibility == View.GONE && !moreExpanded && moreViews.isEmpty()) return
        bar.visibility = View.GONE
        applyMoreGeometry(expanded = false)
        bar.removeAllViews()
        moreViews.clear()
        moreExpanded = false
        morePage = 0
        morePages = emptyList()
        moreReturnChip = null
    }

    /**
     * 主卡高度随展开行显隐切换（T11）：展开态卡顶上移 15dp（ime_card_height_expanded，
     * 收起态内容余量 11dp 耗尽后的净增量），柔影随卡同步；键位结构不变（列节奏断言不受影响）。
     */
    private fun applyMoreGeometry(expanded: Boolean) {
        val wrap = mainCardWrap ?: return
        val card = mainCard ?: return
        val shadow = mainCardShadow ?: return
        val cardHeight = resources.getDimensionPixelSize(
            if (expanded) R.dimen.ime_card_height_expanded else R.dimen.ime_card_height,
        )
        val shadowHeight = resources.getDimensionPixelSize(
            if (expanded) R.dimen.ime_card_shadow_height_expanded else R.dimen.ime_card_shadow_height,
        )
        for (pair in listOf(wrap to cardHeight, card to cardHeight, shadow to shadowHeight)) {
            val params = pair.first.layoutParams ?: continue
            if (params.height != pair.second) {
                params.height = pair.second
                pair.first.layoutParams = params
            }
        }
    }

    /**
     * chip 底形与文字色（全部走 Token）：高亮仅表达遥控器焦点态（决策 30，无常亮高亮）——
     * 焦点态 = accent-wash 底 + border-accent 描边（PRD 6.4「聚焦态 = 主色描边」）+ accent-text 文字；
     * 非焦点 chip 无底色 + 键帽字符正文色（画板① .bchip）。
     */
    private fun applyChipAppearance(view: View) {
        if (view.isFocused) {
            view.background = getDrawable(R.drawable.bg_chip_highlight)
            (view as? TextView)?.setTextColor(getColor(R.color.accent_text))
        } else {
            view.background = null
            (view as? TextView)?.setTextColor(getColor(R.color.text_body))
        }
    }

    /** 候选栏隐藏后的键盘焦点回归（FR-A11 不丢焦点）：回进入候选行前的键盘键，丢失时回初始键。 */
    private fun refocusKeyboardAfterBarHidden() {
        val targetId = candidateReturnKey?.takeIf { keyViews.containsKey(it) }
            ?: navigator.initialKey()
        candidateReturnKey = null
        keyboardView?.post { keyViews[targetId]?.requestFocus(View.FOCUS_DOWN) }
    }

    /** 键盘区顶部边距随候选栏显隐切换（FR-A7「英文/数字模式下候选栏隐藏、键盘区上移占满主卡片」）。 */
    private fun setKeyAreaTopMargin(visible: Boolean) {
        val area = keyArea ?: return
        val params = area.layoutParams as? LinearLayout.LayoutParams ?: return
        val target = if (visible) {
            resources.getDimensionPixelSize(R.dimen.ime_key_area_margin_top)
        } else {
            0
        }
        if (params.topMargin != target) {
            params.topMargin = target
            area.layoutParams = params
        }
    }

    /** 直出文本到输入框光标处（FR-A9）。 */
    private fun commitText(text: String?) {
        if (text.isNullOrEmpty()) return
        currentInputConnection?.commitText(text, 1)
    }

    /** 删除（FR-A9）：存在选区删选区（commitText 空），否则删光标前一字符。 */
    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.commitText("", 1)
        }
    }

    /**
     * 光标在输入框内移动（FR-A9）。经 InputConnection 直接 setSelection，
     * 不产生按键事件（避免与焦点导航的方向键拦截相互干扰）。
     */
    private fun moveCursor(delta: Int) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            ic.finishComposingText()
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
            if (extracted?.text != null) {
                val length = extracted.text.length
                val target = (extracted.selectionEnd + delta).coerceIn(0, length)
                ic.setSelection(target, target)
            } else {
                // fallback：以光标前文本长度估算绝对位置（前文本在探测窗口内时准确）
                val beforeLength = ic.getTextBeforeCursor(CURSOR_PROBE_CHARS, 0)?.length ?: return
                val target = (beforeLength + delta).coerceIn(0, beforeLength)
                ic.setSelection(target, target)
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    /**
     * 刷新大小写相关键面（FR-A3）：字母区键面大小写、上档键激活视觉
     * （画板②：accent_wash 底 + accent_text 图标色，复用强调键 drawable）与态提示。
     */
    private fun applyCaseState() {
        val upper = caseState == CaseState.UPPER
        for ((id, view) in keyViews) {
            val key = KeyboardLayout.keyById[id] ?: continue
            if (id in KeyboardLayout.letterKeyIds) {
                (view as? TextView)?.text = KeyboardLayout.displayLabel(key, caseState)
            }
            if (id == "shift") {
                view.contentDescription = KeyboardLayout.shiftContentDescription(caseState)
                view.background = getDrawable(if (upper) R.drawable.bg_key_done else R.drawable.bg_key)
                (view as? ImageView)?.setColorFilter(
                    getColor(if (upper) R.color.accent_text else R.color.text_secondary),
                )
            }
        }
    }

    /**
     * 收起键盘（FR-A1 / FR-A10）。CENTER 键的转发广播会让宿主 EditText 同拍触发
     * showSoftInput（模拟器 API 34 实测覆盖同步收起），延迟一拍保证收起在其后生效；
     * 系统返回键路径不经此逻辑（policy 层直接收起，无竞态）。
     */
    private fun hideSelfAfterKeyDispatch() {
        keyboardView?.postDelayed({ requestHideSelf(0) }, HIDE_DELAY_MS)
    }

    // ---- 键面样式映射（全部走 Token，不在此散落值） ----

    private fun textColorOf(variant: KeyVariant): Int = when (variant) {
        KeyVariant.CHARACTER -> R.color.text_body
        KeyVariant.FUNCTION -> R.color.text_secondary
        KeyVariant.ACCENT -> R.color.accent_text
    }

    private fun backgroundOf(variant: KeyVariant): Int = when (variant) {
        KeyVariant.CHARACTER, KeyVariant.FUNCTION -> R.drawable.bg_key
        KeyVariant.ACCENT -> R.drawable.bg_key_done
    }

    private fun iconResOf(icon: KeyIcon): Int = when (icon) {
        KeyIcon.SHIFT -> R.drawable.ic_key_shift
        KeyIcon.HIDE -> R.drawable.ic_key_hide
        KeyIcon.BACKSPACE -> R.drawable.ic_key_backspace
        KeyIcon.CURSOR_LEFT -> R.drawable.ic_key_cursor_left
        KeyIcon.CURSOR_RIGHT -> R.drawable.ic_key_cursor_right
    }

    private fun iconSizeOf(icon: KeyIcon): Int = when (icon) {
        KeyIcon.SHIFT -> R.dimen.ime_icon_shift
        KeyIcon.HIDE -> R.dimen.ime_icon_hide
        KeyIcon.BACKSPACE -> R.dimen.ime_icon_backspace
        KeyIcon.CURSOR_LEFT, KeyIcon.CURSOR_RIGHT -> R.dimen.ime_icon_cursor
    }

    /** DPAD 方向键 → 焦点方向映射（拦截口径单一事实源）。 */
    private val DPAD_DIRECTIONS = mapOf(
        KeyEvent.KEYCODE_DPAD_LEFT to FocusDirection.LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to FocusDirection.RIGHT,
        KeyEvent.KEYCODE_DPAD_UP to FocusDirection.UP,
        KeyEvent.KEYCODE_DPAD_DOWN to FocusDirection.DOWN,
    )
}
