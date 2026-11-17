// T5 · 键盘悬浮层（FR-A2 静态布局，画板①「字母键盘·小写」）+ T6 · 遥控器焦点导航基座（FR-A1/A10/A11）
// + T7 · 英文模式完整输入（FR-A3 大小写切换 / FR-A9 特殊键）+ T10 · 中文模式接入 + 候选词栏（FR-A4/A7/A8）。
// 键帽由 KeyboardLayout 模型驱动程序化生成（键位结构单一事实源在模型，单测锁定）；
// T6 接线：DPAD 焦点导航（FocusNavigator 模型 + onKeyDown 层处理，见 onKeyDown 注释）、
// 初始焦点、OK 触发、系统返回键收起、「完成」键确认收起；「下屏」收起（T5 已有）。
// T7 接线：字母/数字/.com/@ 直出、空格、删除、光标移动、大小写切换与键面/态提示刷新。
// T8 接线：符号键盘进出与全/半切换。
// T10 接线：中/英切换（默认中文，T66 · 决策 51-①：开机后首次打开 = 中文输入、会话内记住
// 上次中/英）；中文模式字母入拼音组合（组合串经 setComposingText 上屏显示，候选栏只显示
// 候选词 chips，决策 1/2）；空格 R-2（有候选选当前页第一候选、无候选输入空格，决策 8 × 决策 53）；
// 中文模式行 1 头键 = 分词键（决策 51-②：组合态内插入切分符、无组合态忽略、上屏剥离）；
// 候选行焦点进出与行内移动 + 行缘翻页（CandidatePager 模型，决策 53；翻页落点分向
// 随 T67 · 决策 55：下翻落新页首、上翻落回翻页尾）。
// T11 接线（候选分页原「更多 ⌄」展开行）随 T66 · 决策 53 改造废止：候选栏单行 + 行内翻页
//（CandidatePager 模型，页区间 = 候选全局下标，坑 10 相对坐标系消除；「更多⌄」入口与
// more_bar 第二行、展开态主卡高度切换一并移除）。
// T15 接线：远程输入实时显示（FR-B3 显示部分 / FR-B5）——Manager 回调驱动主卡大字区显隐
//（40px 文本 + 超宽滚动末尾 + 青色光标；候选栏/键区随显隐切换）；完整远程模式状态机随 T17。
// T16 接线：上屏闭环（FR-B6/C6 · 决策 9/10）——Manager.onCommitRequested 注册上屏写入：
// commit 在电视输入框光标处插入（不覆盖其余文字，有选中区间替换该区间，InputConnection
// commitText 框架语义）；~~写入成功 Manager 停服，大字区随 onRemoteTextChanged(null) 隐藏
// 回键盘态、副卡配对卡随之清空（R-4 自动收起远程）~~（→ 被 T70 · 决策 59 取代：写入成功
// 不再停服，WS 连接与远程模式保持——大字区随 onRemoteTextChanged(null) 清空回等待输入态，
// 远程模式框架与状态行「已连接」保持，手机端可继续编辑再次上屏；退出回键盘态沿既有停服回调链）。
// T70 接线（决策 59 · 上屏保活的远程模式渲染适配）：上屏成功的大字区清空经既有
// onRemoteTextChanged(null) 回调承载（renderRemoteText 只清内容、不动模式显隐），
// 退出回键盘态沿既有 stop 回调链路；本层无新增分支——保活语义全部收口在 Manager。
// T17 接线：引导副卡与跨屏状态机（FR-B1/B2/B9 · 决策 32/33/34）——副卡三卡面整卡切换
//（引导卡 GUIDE / 配对卡 PAIRING / 已连接卡面 CONNECTED，RemoteUiState 由 Manager 可观测值推导）；
// 引导卡「远程输入」主按钮启动服务进入远程模式（FR-B2），主卡切远程模式框架（FR-B3：
// 状态行三态 灰/青/绿 + 来自·连接代号胶囊（T72 · 决策 61）+ 大字折行区 + 底部提示 +
// 「退出远程」按钮接既有 stop 链路，FR-B7 完整清理语义仍归 T18）；主按钮遥控器可达
//（T67 · 决策 56：字母/符号两键盘最右列 col12
// 全部按键表缘 RIGHT 进入，LEFT 返回，FR-A10/A11）。
// T36 接线：候选装载完成 debug 专用 trace（决策 22 · FR-A8 ≤200ms 直测）——onKeyDown 入口落
// 计时起点，bindCandidates 完成处出耗时 trace（trace 内 BuildConfig.DEBUG 门控，release 静默，
// 只记耗时与结构计数不含输入内容）。
// T37 接线：候选行可用宽度口径改为刷新当轮（T12 坑 3）——candidatesAvailablePx 读固定 Token 同源；
// T68 · 决策 58 单板修订：候选栏宽 = 键区列宽静态 Token（父容器已是全宽板体，不再作可用宽来源），
// 首行 chips 数不随上轮行内容漂移。
// T22 接线：删除键长按连删（FR-A12「先慢后快」）——确认键按下命中删除键（主键盘 backspace /
// 符号键盘 delete，两处同源 KeyAction.Delete）即武装连删排程：首删后 400ms 出第二删，其后间隔
// 自 200ms 分级加速至 50ms 下限（节奏单一事实源 DeleteRepeatRhythm 纯模型，单测锁定；
// 框架键重复事件不消费——系统重复率不可控、不满足先慢后快口径）。键抬起 / 任一新按下 /
// 键盘隐藏 / 服务销毁解除（会话重开不解除：连删绑定仍在按下的物理键，见 onStartInputView 注释）；
// 连删逐次复用单击删除路径 performDelete（中文组合态先删组合串、
// 再删已上屏文本，与单击语义一致）。仅删除键获得长按重复，光标移动等其他键维持原单击语义。
// T25 接线：远程空闲自动退出（第 8 章电量资源 P1）——远程模式空闲（无手机连接）满阈值
//（判定在 Manager/RemoteIdlePolicy，正式 30 分钟）自动停服（既有完整清理链路）后，经
// Manager.onRemoteIdleExpired 主线程回调 requestHideSelf 收起整个键盘窗口（主卡回键盘态
// 由停服回调驱动，见 renderMainCardMode）。回调在 onCreateInputView 注册 / onDestroy 解除。
// T74 接线（决策 62 · BACK 已连接保活）：远程服务运行中 BACK 按连接态分流（判定收口
// RemoteBackPolicy 纯模型，单测锁定）——已连接态只收起键盘窗口保活（服务 / 会话 / 远程
// 缓冲 / 连接代号全保持、手机端无感知，重入沿 T17 路径自动回远程模式）；未连接态维持
// 既有 exitRemoteMode() 完整清理退出；键盘态分支不变。见 dispatchKeyDown BACK 分支。
// T76 接线（焦点保持口径 · 决策 50/62）：远程大字区装载滚动改 smoothScrollTo 纯滚动
//（目标偏移收口 RemoteTextScrollTarget 纯模型，单测锁定），废止 fullScroll(FOCUS_DOWN)
// 对「退出远程」按钮焦点的连带夺取（T74 坑 2）——上屏后按钮焦点保持可触发，OK 一击
// 完整退出远程。见 renderRemoteText。
// T78 接线（决策 64 · 保活重显沿焦点策略）：保活收起沿（BACK HIDE_KEEP_ALIVE 分支）暂抑
//「退出远程」聚焦性并经 RemoteReentryFocusPolicy 纯模型（单测锁定）标记重显沿——宿主切框
// 沿 show 自动重显时默认焦点不落「退出远程」键（首发 OK 不误杀会话，设备档案坑 25；落窗沿
// 框架会对触屏模式首个可聚焦视图自动赋焦且时机在框架内部，不可聚焦是结构性保证）；显式唤起
// 沿（隐藏态确认键 requestShowSelf）恢复聚焦性并清除标记，维持决策 50-① 默认持焦；无焦点态
// 下任一 DPAD 方向键导航到「退出远程」（FR-B7 显式退出通道可达）；远程模式退出沿复位标记。
package com.sofatype.ime

import android.inputmethodservice.InputMethodService
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
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
import android.widget.Toast
import com.sofatype.ime.debug.DebugLogStore
import com.sofatype.ime.remote.RemoteBackPolicy
import com.sofatype.ime.remote.RemoteInputServer
import com.sofatype.ime.remote.RemoteInputServerManager
import com.sofatype.ime.remote.RemoteReentryFocusPolicy
import com.sofatype.ime.remote.RemoteTextScrollTarget
import com.sofatype.pinyin.PinyinEngine
import timber.log.Timber

// 「更多⌄」入口无障碍标注常量随 T66 · 决策 53 废止删除（FR-A15 展开 mechanism废止，
// 候选栏单行行内翻页，翻页箭头为装饰件不进无障碍标注）。

class SofatypeImeService : InputMethodService() {

    private companion object {
        /** CENTER 键宿主侧 showSoftInput 与 IME 收起的时序间隔（ms，实测取值见 hideSelfAfterKeyDispatch）。 */
        const val HIDE_DELAY_MS = 120L

        /**
         * T38 · 收起后显示抑制窗长（ms，Bug A）：「完成/下屏」收起后，宿主侧同一确认键触发的
         * showSoftInput 迟到到达会重新弹出键盘（T12 Round 1 户主复验 fail；真机实测竞争窗口
         * 大于 HIDE_DELAY_MS=120ms，量级未定，取 800ms 覆盖余量）。窗内显示请求被拒绝
         *（API 34 裁决回调）或落窗即复位（API 23 兜底层，见 onWindowShown）。
         * 注意：该窗会连带抑制窗内的一切唤起（含注入），走查注入间隔需大于本值。
         */
        const val SHOW_GUARD_MS = 800L

        /**
         * T38 · 唤起后确认键抑制窗长（ms，Bug B）：键盘收起后 OK 唤起，同一按键的转发副本
         * 在重弹完成后送达 IME，落在初始焦点键直出字符（T12 坑 1 / 设备档案坑 6，3 次复现）。
         * 窗内首个确认键按转发副本吞掉、吞一次即解除（见 ImeShowGuard），窗长需覆盖
         * 「重弹完成 → 副本送达」的管线间隔；方向键与打字键不在抑制范围。
         */
        const val WAKE_GUARD_MS = 500L

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
         * 候选查询上限（T11 提至 24）：多于可见数查询；单行候选栏需多页候选支撑
         * T66 · 决策 53 行内翻页（12 时候选不足一页，翻页无从演示）。
         */
        const val CANDIDATE_QUERY_LIMIT = 24

        /** 候选 chip 视图 tag 前缀（候选行焦点路由识别用；下标 = 候选全局下标）。 */
        const val CANDIDATE_ID_PREFIX = "cand_"

        /** 上档键容器内图标视图 tag（applyCaseState 触发态刷新用，T65 · 决策 47-①）。 */
        const val SHIFT_ICON_TAG = "shift_icon"

        /** 上档键容器内下划线条视图 tag（触发态可见性刷新用，T65 · 决策 47-①）。 */
        const val SHIFT_BAR_TAG = "shift_bar"

        /** 「退出远程」按钮焦点 tag（T76 取证判读：trace 焦点行区分按钮持焦与被夺形态）。 */
        const val REMOTE_EXIT_TAG = "remote_exit"
    }

    /**
     * TV 恒横屏：默认的全屏提取模式会用自带编辑界面盖住宿主应用
     * （T2 测试输入框将不可见，验收链路走不通），恒关闭。
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    /**
     * T71 · 决策 60 新增日志点：IME 服务 onCreate（trace 门控 = debug 或开关开启；
     * 只记结构信息，走查取证用——历轮「服务重启实例」类问题可从文件日志定位进程重建）。
     */
    override fun onCreate() {
        super.onCreate()
        trace("onCreate")
    }

    /** 键位视图注册表：key id → 视图（焦点导航定位用），onCreateInputView 重建时清空重建。 */
    private val keyViews = mutableMapOf<String, View>()

    /**
     * 焦点导航模型（纯 Kotlin，路径正确性由 FocusNavigatorTest/SymbolsLayoutTest 锁定）。
     * 初始键口径（T40）：字母键盘 = 字母区首键 [a]（KeyboardLayout.initialCell）；
     * 符号键盘 = 键区左上角 digit_1（SymbolsLayout.initialCell，rebuildKeys 显式传入，维持现状）。
     * 字母键盘格网随语言模式显式传参（T66 · 决策 51：行 1 头键随模式分化，格网同构）。
     */
    private var navigator = FocusNavigator(
        KeyboardLayout.rows(LanguageMode.CHINESE),
        initialCell = KeyboardLayout.initialCell,
    )

    /** 自建键盘视图引用（初始焦点 post 用；不依赖框架 getInputView 的可见性差异）。 */
    private var keyboardView: View? = null

    /**
     * T38 · 收起-唤起时序守卫（Bug A 收起后显示抑制 / Bug B 唤起确认键抑制；
     * 窗口状态判定抽为纯 Kotlin 类，边界语义由 ImeShowGuardTest 锁定）。
     */
    private val showGuard = ImeShowGuard(SHOW_GUARD_MS, WAKE_GUARD_MS)

    /**
     * T78 · 决策 64：保活收起后自动重显沿默认焦点决策（纯 Kotlin 状态机，
     * 转移语义由 RemoteReentryFocusPolicyTest 锁定）——保活重显沿无默认焦点（首发 OK 不命中
     * 「退出远程」）；首次进入 / 显式唤起沿维持决策 50-① 默认持焦。接线见 dispatchKeyDown
     * BACK 分支 / 隐藏态确认键分支 / onStartInputView 远程分支 / renderMainCardMode 退出沿。
     */
    private val remoteReentryFocusPolicy = RemoteReentryFocusPolicy()

    /**
     * T78 · 决策 64：「退出远程」按钮聚焦性暂抑标记。保活收起沿置为不可聚焦（远程模式唯一
     * focusableInTouchMode 件）——框架在落窗沿会对触屏模式首个可聚焦视图自动赋焦
     *（模拟器 tap 通道实测在案，赋值时机晚于即时 clearFocus 且在框架内部），不可聚焦使
     * 「重入渲染默认焦点不落退出键」成为结构性保证，与输入通道 / 时序无关。恢复点：
     * onStartInputView 默认持焦分支（首次进入 / 显式唤起沿）、DPAD 无焦点导航分支、
     * renderMainCardMode 模式转换沿（进入远程 / 回键盘），恢复前 requestFocus 前置。
     */
    private var remoteExitFocusSuppressed = false

    /**
     * T38 · 输入窗可见性自跟踪：onWindowShown 在 API 34 会话更新时重复回调（非跳变沿），
     * 以 onWindowShown/onWindowHidden 维护跳变沿判定，服务内守卫只在真实显示沿触发。
     */
    private var windowShown = false

    /**
     * T36 · 候选刷新计时起点（onKeyDown 到达主线程时刻，SystemClock.uptimeMillis）：
     * 候选装载完成处（bindCandidates）读差值得「键到达 → 候选装载完成」耗时；
     * onKeyDown finally 归位 -1，计时只在本键事件栈内有效，非键事件触发的装载不计时。
     * 仅 debug 构建消费（trace 内 BuildConfig.DEBUG 门控，release 静默）。
     */
    private var candidateLoadStartMs = -1L

    // ---- T22 · 删除键长按连删（FR-A12） ----

    /** T22 连删节奏模型（出厂节奏 = 默认参数；节奏值由 DeleteRepeatRhythmTest 锁定）。 */
    private val deleteRepeatRhythm = DeleteRepeatRhythm()

    /** T22 连删排程 Handler（主线程；回调生命周期由 cancelDeleteRepeat 显式管理）。 */
    private val deleteRepeatHandler = Handler(Looper.getMainLooper())

    /** T22 在途连删任务；null = 无连删在途（解除/重武装置空，游离回调靠身份比对作废）。 */
    private var deleteRepeatJob: Runnable? = null

    /** T22 已发出的重复删除计数（不含单击首删；仅 trace 结构信息用，不含输入内容）。 */
    private var deleteRepeatIndex = 0

    /**
     * 大小写状态（FR-A3）。默认小写（T66 · 决策 51-③：英文态默认小写输出）；
     * 英文模式 ⇧ 触发态是唯一大小写指示（键面随大小写输出状态）；中文模式无 ⇧ 键位、
     * 本状态不参与（键面恒显大写，决策 51-②）。会话内记忆（FR-A13 按会话），
     * 进程重启即回默认态（开机后首次打开 = 中文 + 小写基准）。
     */
    private var caseState = CaseState.LOWER

    /** 键盘布局模式：字母键盘 ↔ 符号键盘（FR-A5）。 */
    private enum class KeyboardMode { LETTERS, SYMBOLS }

    /** 当前布局模式（会话内状态）。 */
    private var keyboardMode = KeyboardMode.LETTERS

    /**
     * 符号键盘全/半角状态（FR-A6）；仅符号键盘内有效。
     * T66 · 决策 52：进入符号键盘时随入口模式赋值（中文态 → 全角、英文态 → 半角），
     * [全/半] 手动切换仅本次停留生效、返回字母键盘后再进入重新跟随（不记忆手动选择）；
     * 字段初值仅作占位，任何进入路径都会先赋值。
     */
    private var symbolsWidth = SymbolsWidth.HALF_WIDTH

    /**
     * T64 · 符号键盘行步进窗口偏移（0 起）：显示窗顶行在真实矩阵（5 行）中的行下标，
     * 界面恒显示 4 行；全/半角两页同位语义，偏移跨全/半切换保留（画板 10/11 两页各有下移态）。
     * 进出符号键盘 / 键盘视图重建复位 0（默认态）。
     */
    private var symbolsWindowOffset = 0

    /**
     * 语言模式（FR-A4 · T66 · 决策 51-①）：中文（拼音组合）↔ 英文（直出）。
     * 默认中文 = 开机后首次打开固定中文输入；会话内记住上次中/英（FR-A13 记忆口径保留）——
     * 字段为服务进程级状态，键盘视图重建 / 收起重弹不重置，仅进程重启（含开机）回默认中文。
     * 中文 → 英文切换唯一路径 = [中/英] 键（决策 43-② 复位口径不变）。
     */
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
            Timber.tag(TRACE_TAG).w(t, "拼音词库加载失败，中文模式退化为无候选")
            null
        }
    }

    // ---- 候选栏单行行内翻页（T66 · 决策 53，原 T11「更多⌄」展开行字段废止） ----

    /** 当前页号（0 起；候选集变化即复位 0）。 */
    private var candPage = 0

    /**
     * 候选单行分页区间（CandidatePager.paginate 结果，候选全局下标闭区间——
     * 页区间即全局下标，直接可用，HANDOFF 坑 10 相对坐标系随本模型消除）。
     */
    private var candPages: List<IntRange> = emptyList()

    /** 当前页 chip 视图（页内按候选序；候选行焦点路由与上屏用）。 */
    private val candidateViews = mutableListOf<View>()

    /** 焦点进入候选行前的键盘键 id（向下出行 / 选中上屏后的焦点回归落点）。 */
    private var candidateReturnKey: String? = null

    /** 候选词栏引用（onCreateInputView 绑定；chips 由 refreshCandidatesBar 装载）。 */
    private var candidatesBar: LinearLayout? = null

    /**
     * 板内右分区容器（T17 三卡面 + T68 · 决策 58 单板右分区）：跨屏引导分区（FR-B1）/
     * 配对分区（T14 · FR-B4）/ 已连接分区（FR-B9 / 决策 32）；卡面内容由 renderRemoteUi
     * 依状态机装配，分区几何随卡面切换（applySideZoneGeometry）。
     */
    private var sideCard: FrameLayout? = null

    /** 远程模式主卡框架容器（T17 · FR-B3 / 决策 33）：状态行 + 大字折行区，随远程模式显隐。 */
    private var remoteModeArea: LinearLayout? = null

    /** 远程模式状态行四件套（T17 · FR-B9；T72 · 决策 61）：状态点（灰/青/绿）+ 状态文字 + 「来自 ·」+ 连接代号胶囊。 */
    private var remoteStatusDot: View? = null
    private var remoteStatusText: TextView? = null
    private var remoteStatusFrom: TextView? = null
    private var remoteStatusPill: LinearLayout? = null

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

    /** 键盘区引用（顶部边距恒为布局静态值 ime_key_area_margin_top，决策 42：键区顶全模式恒定）。 */
    private var keyArea: FrameLayout? = null

    /** 跨行浮层键视图（空格/完成键，绝对定位挂键区）；布局切换时移除重建。 */
    private val overlayViews = mutableListOf<View>()

    /**
     * 诊断日志（T71 · 决策 60 机械迁移到 Timber，文案与 TAG 不变）：门控自 T71 起放宽为
     * 「debug 构建 或 调试日志开关开启」（release 开关即记键控轨迹；键码/焦点 id 无隐私）。
     * logcat 树（DebugTree）+ 文件树（开关开启才挂载）双通道同步；TAG=ImeKeyTrace 既有口径。
     * 只记键码/焦点 key id 等结构信息，禁止输出用户输入内容（隐私红线，AGENTS 禁止事项 2）。
     */
    private fun trace(message: String) {
        if (BuildConfig.DEBUG || DebugLogStore.isEnabled) Timber.tag(TRACE_TAG).d(message)
    }

    override fun onCreateInputView(): View {
        keyViews.clear()
        overlayViews.clear()
        candidateViews.clear()
        candidateReturnKey = null
        // 会话内记忆（T66 · 决策 51-① × FR-A13）：语言模式与大小写为服务进程级状态，
        // 键盘视图重建不重置——开机后进程首次进入即字段默认态（中文 + 小写基准），
        // 会话内记住上次中/英。键区几何态仍复位：字母键盘 + 行步进窗口默认态；
        // 符号角态占位初值（决策 52：进入符号键盘时随入口模式赋值）。
        keyboardMode = KeyboardMode.LETTERS
        symbolsWidth = SymbolsWidth.HALF_WIDTH
        symbolsWindowOffset = 0
        composer.reset()
        navigator = FocusNavigator(
            KeyboardLayout.rows(languageMode),
            initialCell = KeyboardLayout.initialCell,
        )
        // 候选行内翻页状态复位（决策 53：候选集变化即回首页）
        candPage = 0
        candPages = emptyList()
        val root = LayoutInflater.from(this).inflate(R.layout.ime_keyboard, null) as ViewGroup
        keyboardView = root
        candidatesBar = root.findViewById(R.id.candidates_bar)
        keyArea = root.findViewById(R.id.key_area)
        sideCard = root.findViewById(R.id.side_card)
        remoteModeArea = root.findViewById(R.id.remote_mode_area)
        remoteStatusDot = root.findViewById(R.id.remote_status_dot)
        remoteStatusText = root.findViewById(R.id.remote_status_text)
        remoteStatusFrom = root.findViewById(R.id.remote_status_from)
        remoteStatusPill = root.findViewById(R.id.remote_status_pill)
        remoteExitButton = root.findViewById(R.id.remote_exit_button)
        remoteBottomHint = root.findViewById(R.id.remote_bottom_hint)
        remoteTextScroll = root.findViewById(R.id.remote_text_scroll)
        remoteTextView = root.findViewById(R.id.remote_text)
        // T17 「退出远程」按钮（FR-B3 / 决策 33）：文案与无障碍标注、可聚焦（远程模式内唯一焦点件）、
        // OK 触发（onKeyDown 确认键统一 performClick）接既有 stop 链路（FR-B7 完整清理语义仍归 T18）。
        // T76：补焦点 tag（REMOTE_EXIT_TAG）——ImeKeyTrace 焦点行从此可区分「按钮持焦
        //（focus=remote_exit）」与「焦点被滚动容器夺走（focus=null，T74 坑 2 取证形态）」，
        // 仅取证判读用途；DPAD 路径未知 tag 走格网查表空表 no-op，行为无影响。
        remoteExitButton?.apply {
            tag = REMOTE_EXIT_TAG
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
        // T16 上屏写入（FR-B6）：commit 经 Manager 主线程派发，写入结果决定回执；
        // T70（决策 59）：成功不再触发停服——Manager 回执后清缓冲并通知本层清大字区，
        // 远程模式与连接保持；键盘重建后旧闭包随引用失效，此处重新绑定（与 onRemoteTextChanged 同口径）
        RemoteInputServerManager.onCommitRequested = { text -> commitRemoteText(text) }
        // T25 空闲自动退出（第 8 章电量资源）：远程模式空闲（无手机连接）满阈值时 Manager 走
        // stop() 既有完整清理链路（通知手机 / 关监听 / 清 token·缓冲·设备名，主卡随停服回调
        // 回键盘态），随后经本回调（主线程契约）收起整个键盘窗口（requestHideSelf）；
        // 窗口未显示时为无害调用。键盘重建后旧闭包随引用失效，此处重新绑定（同上口径）。
        RemoteInputServerManager.onRemoteIdleExpired = {
            trace("remote idle expired -> hide keyboard")
            requestHideSelf(0)
        }
        return root
    }

    /** 服务销毁：解除配对状态回调（Manager 为进程级单例，防悬挂引用）；清连删排程（T22）。 */
    override fun onDestroy() {
        // T71 · 决策 60 新增日志点：IME 服务 onDestroy（与 onCreate 成对，服务重建链取证用）
        trace("onDestroy")
        deleteRepeatHandler.removeCallbacksAndMessages(null)
        RemoteInputServerManager.onPairingStateChanged = null
        RemoteInputServerManager.onRemoteTextChanged = null
        RemoteInputServerManager.onCommitRequested = null
        RemoteInputServerManager.onRemoteIdleExpired = null // T25：空闲退出收起键盘回调一并解除
        super.onDestroy()
    }

    /**
     * 渲染跨屏 UI（T17/T35 · FR-B1/B2/B9 · 决策 32/33/35）：状态由 Manager 可观测值
     *（服务运行 / 已配对会话存在 / 配对设备名）经 [RemoteUiState.of] 推导——
     * ① 副卡按卡面装配（引导卡 / 配对卡 / 二维码卡 + 已连接状态行）；
     * ② 主卡按模式显隐远程模式框架（FR-B3：状态行三态 + 大字区 + 底部提示 + 「退出远程」），
     *    模式切换沿迁移焦点（进远程 → 退出远程按钮；回键盘 → 初始键，FR-A11 不丢焦点）。
     * 触发点 = 服务启停 / 配对成功 / 已配对连接断开回调、主按钮与退出按钮点击、键盘重建补渲染；幂等可重复调用。
     */
    private fun renderRemoteUi() {
        val state = RemoteUiState.of(
            serviceRunning = RemoteInputServerManager.isRunning(),
            pairedSessionActive = RemoteInputServerManager.hasPairedSession(),
            clientCode = RemoteInputServerManager.pairedClientCode(),
        )
        renderSideCard(state)
        renderMainCardMode(state)
    }

    /**
     * 副卡三卡面装配（T17/T35/T68 · 决策 32/35/58）：
     * 未连接 → 引导分区（FR-B1）；等待扫码 → 配对二维码整分区（FR-B4 / 决策 4，该卡面即等待态）；
     * 已连接 → 二维码分区保留：二维码为配对成功后滚动签发的新 token 有效码，
     * 网址行原位换成绿点状态行「已连接 · 设备名」（决策 32 独立已连接卡面废止）。
     * 分区几何随卡面切换（T68 · 决策 58 单板右分区）：引导分区宽 366px 纵向撑满、
     * 二维码分区宽 316px 顶部锚定高度随内容。
     */
    private fun renderSideCard(state: RemoteUiState) {
        val container = sideCard ?: return
        applySideZoneGeometry(state.sideCardFace)
        guideButtonView = null
        when (state.sideCardFace) {
            RemoteSideCardFace.GUIDE -> showGuideFace(container)
            RemoteSideCardFace.PAIRING -> {
                val url = RemoteInputServerManager.pairingUrl()
                if (url != null) {
                    PairingCard.show(this, container, url)
                } else {
                    // 防御：运行中无配对地址（理论不可达）回引导分区，不留空分区
                    showGuideFace(container)
                }
            }
            RemoteSideCardFace.CONNECTED -> {
                val url = RemoteInputServerManager.pairingUrl()
                if (url != null) {
                    // T72（决策 61）：已连接状态行 = 绿点「已连接」+ 连接代号胶囊（两端同显）
                    PairingCard.show(
                        this,
                        container,
                        url,
                        clientCode = state.clientCode,
                    )
                } else {
                    // 防御：运行中无配对地址（理论不可达，配对成功即滚动签发）回引导分区
                    showGuideFace(container)
                }
            }
        }
        if (state.sideCardFace != RemoteSideCardFace.GUIDE) guideReturnKey = null
    }

    /**
     * 板内右分区几何随卡面切换（T68 · 决策 58）：引导分区 = 宽 366px + 上下距板缘 24px 纵向撑满
     *（画板 Main aside top/bottom 24px）；二维码分区 = 宽 316px + 顶距板顶 24px、高度随内容
     *（画板 RemotePairing aside top 24px，二维码 316px 随板高自适应）。幂等可重复调用。
     */
    private fun applySideZoneGeometry(face: RemoteSideCardFace) {
        val zone = sideCard ?: return
        val lp = zone.layoutParams as? FrameLayout.LayoutParams ?: return
        when (face) {
            RemoteSideCardFace.GUIDE -> {
                lp.width = resources.getDimensionPixelSize(R.dimen.ime_guide_zone_width)
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                lp.topMargin = resources.getDimensionPixelSize(R.dimen.ime_side_zone_margin_v)
                lp.bottomMargin = resources.getDimensionPixelSize(R.dimen.ime_side_zone_margin_v)
                lp.gravity = Gravity.END or Gravity.FILL_VERTICAL
            }
            RemoteSideCardFace.PAIRING, RemoteSideCardFace.CONNECTED -> {
                lp.width = resources.getDimensionPixelSize(R.dimen.ime_qr_zone_width)
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                lp.topMargin = resources.getDimensionPixelSize(R.dimen.ime_side_zone_margin_v)
                lp.bottomMargin = 0
                lp.gravity = Gravity.END or Gravity.TOP
            }
        }
        zone.layoutParams = lp
    }

    /** 装配引导卡面（FR-B1）并捕获主按钮视图（焦点导航进出用）。 */
    private fun showGuideFace(container: FrameLayout) {
        GuideCard.show(this, container) { enterRemoteMode() }
        guideButtonView = container.findViewWithTag<View>(GuideCard.BUTTON_TAG)
    }

    /**
     * 主卡模式与状态行渲染（T17 · FR-B3 / 决策 32/33，T72 · 决策 61）：远程模式显示框架区
     *（候选栏 / 展开行 / 键区隐藏），键盘态隐藏回键盘；状态行三态 = 未连接灰点 / 等待扫码青点 /
     * 已连接绿点 + 「来自 ·」+ 连接代号胶囊（FR-B9；决策 61：设备名展示口径由代号胶囊替代）。
     * 焦点迁移只在模式切换沿触发：进远程 → 「退出远程」按钮；回键盘 → 初始键（远程上屏自动收起
     * 与手动退出共用此沿，R-4）。
     */
    private fun renderMainCardMode(state: RemoteUiState) {
        val area = remoteModeArea ?: return
        val prevMode = lastRenderedMode
        // 状态行三态（FR-B9 / PRD 6.4 连接状态色）：点随状态换色，连接态加「来自 ·」+ 代号胶囊；
        // T18：等待扫码态叠加「连接已断开」瞬时提示（已配对连接断开后 3s 内，FR-B8 / Q3 lead 拍板），
        // 到期自动回青点「等待手机扫码」终态；连接态代号缺失（防御分支）时隐藏「来自」行与胶囊
        when (state.status) {
            RemoteStatus.NOT_CONNECTED -> {
                remoteStatusDot?.setBackgroundResource(R.drawable.bg_status_dot_idle)
                remoteStatusText?.text = RemoteUiText.STATUS_NOT_CONNECTED
                remoteStatusText?.contentDescription = null
                hideStatusFromAndPill()
            }
            RemoteStatus.WAITING_SCAN -> {
                val noticeActive = RemoteInputServerManager.disconnectNoticeActive()
                if (noticeActive) {
                    remoteStatusDot?.setBackgroundResource(R.drawable.bg_status_dot_idle)
                    remoteStatusText?.text = RemoteUiText.STATUS_DISCONNECTED
                    // 过渡态无障碍标注（Q3 lead 拍板约束）：标注完整语义，提示是瞬时过渡
                    remoteStatusText?.contentDescription = "连接已断开，等待手机重新扫码"
                } else {
                    remoteStatusDot?.setBackgroundResource(R.drawable.bg_status_dot_waiting)
                    remoteStatusText?.text = RemoteUiText.STATUS_WAITING_SCAN
                    remoteStatusText?.contentDescription = null
                }
                hideStatusFromAndPill()
            }
            RemoteStatus.CONNECTED -> {
                remoteStatusDot?.setBackgroundResource(R.drawable.bg_status_dot_connected)
                remoteStatusText?.text = RemoteUiText.STATUS_CONNECTED
                remoteStatusText?.contentDescription = null
                val code = state.clientCode
                if (code != null) {
                    // T72（决策 61）：「来自 ·」+ 连接代号胶囊（画板 6 口径）
                    remoteStatusFrom?.text = RemoteUiText.CONNECTED_FROM_LABEL
                    remoteStatusFrom?.visibility = View.VISIBLE
                    val pill = remoteStatusPill
                    if (pill != null) {
                        ClientCodePill.show(this, pill, code)
                        pill.visibility = View.VISIBLE
                    }
                } else {
                    // 防御分支：连接态无代号（理论不可达，配对成功即签发）不渲染悬空「来自 ·」
                    hideStatusFromAndPill()
                }
            }
        }
        if (state.mode == RemoteMode.REMOTE) {
            area.visibility = View.VISIBLE
            remoteBottomHint?.visibility = View.VISIBLE
            keyArea?.visibility = View.GONE
            candidatesBar?.visibility = View.GONE
            resetCandidatePageState()
            if (prevMode != RemoteMode.REMOTE) {
                trace("remote mode -> REMOTE（status=${state.status}）")
                // T78 · 决策 64：进入远程沿恢复聚焦性（防御：上一保活周期暂抑残留；幂等）
                restoreRemoteExitFocusability()
                keyboardView?.post { remoteExitButton?.requestFocus(View.FOCUS_DOWN) }
            }
        } else {
            area.visibility = View.GONE
            remoteBottomHint?.visibility = View.GONE
            if (prevMode == RemoteMode.REMOTE) {
                trace("remote mode -> KEYBOARD")
                // T78 · 决策 64：远程模式退出沿复位保活重显标记（BACK 未连接退出 / 按钮 / 超时
                // 自退共用此沿）——防陈旧标记跨越完整远程生命周期，吞掉下次进入的默认持焦；
                // 聚焦性暂抑成对恢复。
                remoteReentryFocusPolicy.reset()
                restoreRemoteExitFocusability()
                keyArea?.visibility = View.VISIBLE
                // 候选栏内容按当前模式与组合态重算（空态占位恒在 + 展开行回收；键区几何静态恒定，
                // 决策 42 起键区上边距为布局静态值，无运行期顶距切换）
                refreshCandidatesBar()
                keyboardView?.post { keyViews[navigator.initialKey()]?.requestFocus(View.FOCUS_DOWN) }
            }
        }
        lastRenderedMode = state.mode
    }

    /** 隐藏「来自 ·」前缀与连接代号胶囊（T72 · 决策 61：等待 / 未连接 / 防御分支共用，幂等）。 */
    private fun hideStatusFromAndPill() {
        remoteStatusFrom?.visibility = View.GONE
        remoteStatusPill?.let {
            it.removeAllViews()
            it.visibility = View.GONE
        }
    }

    /**
     * 进入远程模式（FR-B2 · 决策 32/33）：引导卡主按钮触发——启动服务（随机端口 + 签发 token）；
     * 成功后 Manager 经 onPairingStateChanged 通知本层切远程模式（副卡切配对卡 / 主卡切远程框架）。
     * 失败（如端口分配失败）保持键盘态引导卡，不崩溃不阻塞宿主（可靠性口径）；
     * 失败弹系统 Toast 提示（T79 · 决策 65：不再静默；文案走通用资源，零失败原因细节——
     * reason 可能含配对地址与 token，不得上屏暴露，隐私红线）。
     */
    private fun enterRemoteMode() {
        trace("remote enter requested")
        val result = RemoteInputServerManager.start(this)
        if (result is RemoteInputServer.StartResult.Failed) {
            // 只记失败事件，不记任何输入内容（隐私红线）；成功路径 start() 内已同步触发
            // onPairingStateChanged → renderRemoteUi（切远程模式 / 副卡切配对卡）
            trace("remote enter failed: ${result.reason}")
            Toast.makeText(this, R.string.remote_start_failed_hint, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 退出远程模式（T18 · FR-B7 完整语义 / 决策 33 接线完成）：「退出远程」按钮或系统返回键触发。
     * 调 Manager 停服链路——存在已配对会话时先向手机回发 disconnect 通知（手机端提示
     * 「电视已退出远程输入」并禁输入）再关 HTTP/WS 监听、清 token / 远程缓冲 / 设备名
     * （已接收未上屏内容按丢弃处理，提示由手机端断连通知承载），停止回调把状态机切回键盘态
     * （副卡回引导卡、焦点回初始键）。停服编排异步执行，本方法立即返回。
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
        // 折行内容超出显示区高度时纵向滚动保持末尾（决策 31）：装载后滚动到底（光标恒可见）。
        // T76（焦点保持口径）：滚动走 smoothScrollTo 纯滚动、不再走 fullScroll(FOCUS_DOWN)——
        // ScrollView.fullScroll 会顺带执行焦点查找，大字区内无可聚焦子项时把焦点移给滚动容器
        // 自身，「退出远程」按钮焦点被夺走（T74 坑 2），OK 到达时 performClick 无目标，
        // 「上屏后直接 OK 退出远程」路径失效。装载滚动全程不触碰焦点树，按钮焦点保持可触发
        //（决策 50 / 62：上屏后直接 OK 一击完整退出）；目标偏移经 RemoteTextScrollTarget
        // 纯模型计算（未溢出归 0 不滚动）。
        val scroll = remoteTextScroll ?: return
        scroll.post {
            val bottom = RemoteTextScrollTarget.bottomOffset(
                contentHeightPx = scroll.getChildAt(0)?.height ?: 0,
                viewportHeightPx = scroll.height,
            )
            if (bottom > 0) scroll.smoothScrollTo(0, bottom)
        }
    }

    /**
     * 远程上屏写入（T16 · FR-B6/C6 · 决策 9/10 · T70 决策 59 保活适配）：把手机上屏内容写入
     * 电视输入框光标处。commitText 框架语义即满足 R-3 口径：无选中区间 = 光标处插入、
     * 不覆盖其余已有文字；有选中区间 = 替换该区间。写入前先落定本地组合串
     *（finishComposingText）：组合串是 IME 自身挂起的编辑区，不先落定会与上屏文本在光标处
     * 交叠；随后组合态复位。会话保持下连续上屏（决策 59）重复走本函数：每次写入都落在
     * 「当前焦点输入连接」的光标处——焦点切到另一输入框后，IC 随输入会话切换、内容落新框。
     * 候选栏显隐不在此重算（T17：远程模式候选栏由 renderRemoteUi 驱动保持隐藏，防止重算把
     * 空态占位顶出来压低状态行；退出远程回键盘态时由 renderRemoteUi 按无组合态重算清空 chips）。
     * 返回 true = 已写入（Manager 据此回执并清缓冲，不停服——决策 59）；
     * false = 无焦点输入框或写入异常（远程模式与缓冲保持，手机端提示失败可重试）。
     * 任何异常吞掉不外抛（可靠性口径：不得阻塞宿主应用）；内容不入日志（隐私红线）。
     */
    private fun commitRemoteText(text: String): Boolean {
        val ic = currentInputConnection ?: return false
        return try {
            ic.beginBatchEdit()
            try {
                ic.finishComposingText()
                composer.reset()
                ic.commitText(text, 1)
                true
            } finally {
                ic.endBatchEdit()
            }
        } catch (t: Throwable) {
            Timber.tag(TRACE_TAG).w("远程上屏写入失败：${t.javaClass.simpleName}")
            false
        }
    }

    /**
     * 依当前布局模式重建键区（字母键盘 ↔ 符号键盘，FR-A5/T8）：
     * 清空行容器与跨行浮层键、按模型重绑、焦点落当前布局初始键。
     * 行容器（4 行）与键区 FrameLayout 复用，仅替换内容。
     */
    private fun rebuildKeys(root: View, requestInitialFocus: Boolean) {
        // 字母键盘格网随语言模式装配（T66 · 决策 51：中文态行 1 头键 = 分词、英文态 = ⇧）；
        // 初始键口径（T40）：字母键盘 = 字母区首键 [a]；符号键盘 = 键区左上角 digit_1（维持现状）；
        // T64：符号键盘按行步进窗口偏移装配显示窗（界面恒 4 行，仅符号区 7 列随窗口切换）
        navigator = when (keyboardMode) {
            KeyboardMode.LETTERS -> FocusNavigator(
                KeyboardLayout.rows(languageMode),
                initialCell = KeyboardLayout.initialCell,
            )
            KeyboardMode.SYMBOLS -> FocusNavigator(
                SymbolsLayout.rows(symbolsWidth, symbolsWindowOffset),
                SymbolsLayout.overlayCells(),
                SymbolsLayout.initialCell,
            )
        }
        val rows = when (keyboardMode) {
            KeyboardMode.LETTERS -> KeyboardLayout.rows(languageMode)
            KeyboardMode.SYMBOLS -> SymbolsLayout.rows(symbolsWidth, symbolsWindowOffset)
        }
        bindRows(root, rows)
        bindOverlayKeys(root)
        // T64 走查实证（模拟器 API 34）：行步进后的全/半切换，视图树与逻辑状态已正确重建
        //（bindRows 行容器子视图与焦点导航 keyViews 均为新键位），但窗口帧可能滞留旧键面——
        // 对根容器强制失效重绘（幂等、无副作用），保证 rebuild 后尽快取得新帧
        root.invalidate()
        // 候选栏内容随布局模式刷新（决策 42：空态占位全模式恒定，键区顶不随键盘类型/语言模式变化）
        refreshCandidatesBar()
        if (requestInitialFocus) {
            keyboardView?.post { keyViews[navigator.initialKey()]?.requestFocus() }
        }
    }

    /**
     * 键盘显示：初始焦点落到当前布局初始键（FR-A11 焦点导航起点；T40：字母键盘 = 字母区首键 [a]，
     * 含收起后唤起重弹路径；符号键盘 = 键区左上角 digit_1，维持现状）。
     * post 到 attach 完成后执行；focusableInTouchMode 保证触摸模式下焦点机制同样可用
     * （模拟器 `input tap` 唤起后紧跟 `input keyevent` 走查的场景）。
     * 新输入框重置组合态（T10）：组合串属上一输入框，chips 清空，候选栏保持空态占位
     * （决策 42：占位全模式恒定，键区顶不随语言/键盘模式变化）。
     * T17：远程模式运行中（键盘重建补渲染 / 切换输入框重连）——候选栏保持隐藏
     *（远程模式显隐由 renderRemoteUi 驱动，此处不重算，防空候选栏顶低状态行），
     * 焦点落「退出远程」按钮（远程模式唯一可聚焦元素，FR-B3）；服务未运行走键盘态常规路径。
     * T78 · 决策 64：保活收起后的宿主自发重显沿例外——默认焦点不落「退出远程」键
     *（RemoteReentryFocusPolicy 裁决，首发 OK 不误杀会话）；显式唤起沿默认持焦不变。
     */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.reset()
        candidateReturnKey = null
        // T22：此处不解除在途连删——实测（模拟器 API 34）应用侧注入类文本变更（如硬件键直录）
        // 会使 TextView 在下一次 IC 变更时重开输入会话（onStartInputView restarting=false），
        // 按住删除键期间首删可能命中该一次性标记；连删绑定的是仍在按下的物理键
        //（解除点 = 键抬起 / 任一新按下 / 键盘隐藏 / 服务销毁，见 cancelDeleteRepeat 各接线处），
        // 会话重开不改变「键仍被按住」的事实，解除会误杀长按连删（FR-A12）。
        // T38 诊断：输入会话起点（restarting 标记只记结构信息）
        trace("onStartInputView restarting=$restarting")
        if (RemoteInputServerManager.isRunning()) {
            // T78 · 决策 64：保活收起后的宿主自发重显沿（切框 show 请求，T17 路径）默认焦点
            // 不落「退出远程」键——首发 OK 不触发退出，会话 / 缓冲 / 连接代号全程保持；
            // 显式唤起沿（唤醒护栏已清除标记）与首次进入沿维持决策 50-① 默认持焦。
            // 无焦点态下「退出远程」仍可 DPAD 导航到达（dispatchKeyDown 方向键分支，FR-B7）。
            when (remoteReentryFocusPolicy.resolveOnShow()) {
                RemoteReentryFocusPolicy.ReentryFocus.NONE ->
                    // 保活重显沿：默认焦点不落「退出远程」（按钮不可聚焦，框架无从赋焦）
                    trace("onStartInputView keep-alive re-entry -> no default focus（决策 64）")
                RemoteReentryFocusPolicy.ReentryFocus.EXIT_BUTTON -> {
                    restoreRemoteExitFocusability()
                    keyboardView?.post { remoteExitButton?.requestFocus(View.FOCUS_DOWN) }
                }
            }
            return
        }
        refreshCandidatesBar()
        keyboardView?.post {
            keyViews[navigator.initialKey()]?.requestFocus()
        }
    }

    /**
     * T78 · 决策 64：恢复「退出远程」按钮可聚焦（幂等）。恢复点 = 默认持焦分支（首次进入 /
     * 显式唤起沿）、DPAD 无焦点导航分支、renderMainCardMode 模式转换沿；收起沿暂抑成对。
     */
    private fun restoreRemoteExitFocusability() {
        if (!remoteExitFocusSuppressed) return
        remoteExitFocusSuppressed = false
        remoteExitButton?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
    }

    /** T78 · 决策 64：暂抑「退出远程」按钮可聚焦（保活收起沿接线；先清焦点再摘聚焦性）。 */
    private fun suppressRemoteExitFocusability() {
        keyboardView?.findFocus()?.clearFocus()
        remoteExitFocusSuppressed = true
        remoteExitButton?.apply {
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    /**
     * 键事件（FR-A1 / FR-A11）。实测（模拟器 API 34）：框架把转发来的 DPAD 事件直接递到
     * InputMethodService.onKeyDown，不经过 IME view 树（键帽视图的 OnKeyListener 收不到），
     * 因此方向键导航在本层处理。
     * BACK 收起键盘（FR-A1）：已上屏文本保留在宿主输入连接中，收起不丢失；
     * 显式实现（与框架默认行为一致），不依赖各 API 级别的默认口径差异。
     * T36：入口落候选刷新计时起点（[candidateLoadStartMs]），正文抽至 [dispatchKeyDown]，
     * finally 归位计时字段——导航等不产生候选装载的键事件不留陈旧起点。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val ev = event ?: return super.onKeyDown(keyCode, event)
        candidateLoadStartMs = SystemClock.uptimeMillis()
        try {
            return dispatchKeyDown(keyCode, ev)
        } finally {
            candidateLoadStartMs = -1L
        }
    }

    /** 键事件正文（T36 抽取：计时起点与归位在 [onKeyDown]，本方法处理逻辑不变）。 */
    private fun dispatchKeyDown(keyCode: Int, ev: KeyEvent): Boolean {
        // T6 评审遗留跟进：偶发焦点卡死诊断（T7 已定位为注入层事件丢失）。只记键码/焦点 key id
        // 结构信息，不输出用户输入内容（隐私红线）
        trace("onKeyDown keyCode=$keyCode action=${ev.action} focus=${(keyboardView?.findFocus() as? View)?.tag}")
        // T22：任一新按下（repeatCount=0，含非确认键）即解除在途连删——键抬起事件偶发丢失
        //（DEV-GUIDE 注入通道坑 4）时不留游离删除任务；删除键长按在本方法确认键分支随后重新武装。
        if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
            cancelDeleteRepeat()
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // T74 · 决策 62（修订 T18 · FR-B7 系统返回键条款）：远程服务运行中 BACK 按连接态
            // 分流（判定单一事实源 RemoteBackPolicy）——
            // 已连接态（有已配对会话）= 只收起键盘窗口保活（FR-A1 语义），服务 / 会话 /
            // 远程缓冲 / 连接代号全保持、手机端无感知（无 disconnect 通知）；键盘已收起时
            // 归键盘态既有分支（隐藏态透传宿主，T59 口径同族），重入沿 onStartInputView
            // T17 路径自动回远程模式；保活期两类空闲自退（T25 无连接 30 分钟 / 决策 59
            // 已连接无活动 10 分钟）照常兜底，不因收起顺延。收起动作本身不落任何输入
            // 内容日志（隐私红线）。
            // 未连接态（等待扫码）= 既有 exitRemoteMode() 完整清理链路（通知手机 + 关
            // HTTP/WS 监听 + 清 token / 缓冲 / 设备名，回键盘态），不残留监听端口；
            // 远程分支先于隐藏态判定执行，T18 退出远程链路不受 T59 口径影响（模拟器取证
            // 见 issue T59）。
            if (RemoteInputServerManager.isRunning()) {
                when (RemoteBackPolicy.resolve(
                    serviceRunning = true,
                    pairedSessionActive = RemoteInputServerManager.hasPairedSession(),
                    inputViewShown = isInputViewShown(),
                )) {
                    RemoteBackPolicy.Action.EXIT_REMOTE -> {
                        trace("remote exit via BACK")
                        exitRemoteMode()
                        return true
                    }
                    RemoteBackPolicy.Action.HIDE_KEEP_ALIVE -> {
                        // 远程模式无组合态，不涉及 finishComposition（与键盘态 BACK 收起唯一差异）；
                        // BACK 路径不开抑制窗（hideSelfAfterKeyDispatch 注释既有口径，直接收起）。
                        trace("remote keep-alive hide via BACK")
                        remoteReentryFocusPolicy.onKeepAliveHide()
                        // T78 · 决策 64：收起沿暂抑「退出远程」聚焦性（含清焦点）——宿主切框沿
                        // show 自动重显（T17 路径）时默认焦点不落「退出远程」键，首发 OK 不误杀
                        // 会话（设备档案坑 25 缺陷链修复，方向 B）：焦点若不清除，视图树在收起
                        // 期间保留按钮持焦；聚焦性若不摘除，落窗沿框架触屏工件仍会自动赋焦。
                        suppressRemoteExitFocusability()
                        requestHideSelf(0)
                        return true
                    }
                    RemoteBackPolicy.Action.KEYBOARD_DEFAULT -> Unit
                    // 落键盘态既有分支：已连接隐藏态透传宿主（服务保活不受影响）
                }
            }
            // T59（户主拍板 2026-11-11）：隐藏态 BACK 透传宿主——键盘已收起时本服务收起
            // 无效果且拦截宿主返回语义（T54 Round 1 死态根因之一：BACK 退出设置页失效；
            // 第三方宿主同理，真机 T53 tv-c1~c3b 佐证隐藏态键到达 IME）。显示态维持 FR-A1
            // 收起键盘，收起前按拍板增量统一 finishComposition（与 [完成] 收起同源落定组合串）。
            if (!isInputViewShown()) {
                return super.onKeyDown(keyCode, ev)
            }
            finishComposition()
            requestHideSelf(0)
            return true
        }
        val direction = DPAD_DIRECTIONS[keyCode]
        if (direction != null) {
            // T59（户主拍板 2026-11-11）：隐藏态方向键透传宿主（DOWN/UP 对称落 super）——
            // 隐藏态无键面焦点可导航（keyboardView 无焦点，导航空转），消费即吞宿主导航
            //（T54 Round 1「导航键群死」根因）。显示态维持 FR-A11 格网导航：
            // 导航仅响应 ACTION_DOWN 首次按下（长按重复不连续移动）；DOWN 分支返回 true 消费。
            // onKeyUp 未覆盖方向键：ACTION_UP 落到 super（super 对方向键默认无焦点行为），
            // 与隐藏态透传对称。不进系统默认焦点处理，防 focus search 跳变。
            if (!isInputViewShown()) {
                return super.onKeyDown(keyCode, ev)
            }
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                val currentId = (keyboardView?.findFocus() as? View)?.tag as? String
                if (currentId != null) {
                    // 候选区与键盘格网分层路由（T10 · FR-A7 / T66 · 决策 53 行内翻页 / T17 · FR-B1）：
                    // 焦点在候选区（chip）走候选行语义（页缘翻页），引导卡主按钮走引导语义，
                    // 其余（键盘键）走 FocusNavigator 格网语义，含从行 1 键向上进入候选行
                    when {
                        currentId == GuideCard.BUTTON_TAG -> handleGuideButtonDirection(direction)
                        currentId.isCandidateId() -> handleCandidateRowDirection(currentId, direction)
                        else -> handleKeyboardDirection(currentId, direction)
                    }
                } else if (RemoteInputServerManager.isRunning()) {
                    // T78 · 决策 64：保活重显无默认焦点态下任一方向键 = 导航到「退出远程」
                    //（远程模式唯一可聚焦元素，显式退出通道 FR-B7 可达性不回归）；
                    // 键盘态（服务未运行）无焦点维持既有 no-op 不变。
                    restoreRemoteExitFocusability()
                    trace("focus null $direction -> remote_exit（保活重显无焦点态导航）")
                    remoteExitButton?.requestFocus(View.FOCUS_DOWN)
                }
            }
            return true
        }
        if (keyCode.isConfirmKey()) {
            // OK/回车触发焦点键（FR-A11）：框架转发路径不经过 view 树，焦点视图默认 click
            // 不可用，在此显式派发 performClick
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                // T38：输入窗隐藏期间确认键不落键面。模拟器 API 34 实测隐藏态键事件仍路由进
                // IME（真机 API 23 隐藏态 trace 实证同样到达，T53 tv-c1~c3b），焦点残留收起前
                // 键位——隐形点击会在错误键位直出字符或重开收起链路。吞键语义保留。
                // T59（户主拍板 2026-11-11）：隐藏态确认键 = 消费 + 唤起键盘（FR-A1「在可输入
                // 焦点处确认后弹出键盘」；真机收起后 CENTER 被吞无法唤起，closed/T53 坑 1 同族）。
                // requestShowSelf 经 onShowInputRequested 裁决：收起抑制窗内被 veto（「完成」
                // 收起后立即 OK 不唤起 = T38 既有收起语义）；唤起成功沿 onWindowShown 开唤起
                // 抑制窗，本键已吞无转发副本，其后第二发确认键被 wake guard 吞一次（T38
                // 真机复验既有语义）。
                if (!isInputViewShown()) {
                    // T78 · 决策 64：显式唤起路径（唤醒护栏）清除保活重显标记——决策 50-①
                    // 「显式唤起『退出远程』键仍默认持焦（OK 一击即退）」条款不变（T74 真机
                    // 轮 2/3 已验证路径零回归）；保活重显标记只服务宿主自发 show 沿。
                    // 聚焦性暂抑同步恢复（重显沿默认持焦分支的前置条件）。
                    restoreRemoteExitFocusability()
                    remoteReentryFocusPolicy.onExplicitWake()
                    trace("onKeyDown keyCode=$keyCode swallowed（输入窗隐藏态）-> requestShowSelf")
                    requestShowSelf(0)
                    return true
                }
                // T38（Bug B · T12 坑 1 / 设备档案坑 6）：键盘收起后 OK 唤起时，同一按键的转发
                // 副本在重弹完成后送达 IME，落在初始焦点键直出字符。唤起抑制窗内首个确认键按
                // 副本吞掉，吞一次即解除——其后确认键（快速连按 / 候选选词）照常触发。
                if (showGuard.shouldSwallowConfirmKey(SystemClock.uptimeMillis())) {
                    trace("onKeyDown keyCode=$keyCode swallowed（唤起转发副本抑制）")
                    return true
                }
                keyboardView?.findFocus()?.performClick()
                // T22（FR-A12）：焦点键为删除键时武装长按连删（首删已在本次 performClick 完成）
                armDeleteRepeatForFocusedKey()
            }
            return true
        }
        // T48（组合删除复活根因修复）· FR-A9 删除统一口径：中文 × 字母键盘下，硬件删除键码
        // （含 adb 注入通道）与网格 backspace 同路走 performDelete。默认链路对删除键码不消费
        // （本方法此前只处理 BACK/方向/确认键/字母键码），事件经框架转发宿主由 EditText 直删
        // 平台组合区文本，IME composer 不知情（组合串残留已删字母）——下次字母入组经
        // setComposingText 把残留字母拼回组合串，正文复活累积（2026-11-09 模拟器二分实证：
        // 硬件 DEL 删 g 后重按 e，候选装载与选词上屏都按「ge」词族出「个」；网格 backspace
        // 同序列按「e」词族出「额」，见 issue T48 与 screenshots/T48/）。组合态内外都拦、
        // 与网格 backspace 语义完全同路（组合串内删组合串，删空后接删宿主文本）；含系统长按
        // 重复（repeatCount>0 逐次 performDelete，节奏归系统重复率；网格长按节奏归 T22 排程）。
        // 英文模式 / 符号键盘 / 远程模式 / 输入窗隐藏维持框架直删（无组合串可失同步，直删语义
        // 与 deleteBackward 等价，口径不变）。ACTION_UP 不改：删除键抬起无文本语义（T44/T45 的
        // 确认键/字母键 UP 直写病理对删除键不存在），保持最小改动。
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (ev.action == KeyEvent.ACTION_DOWN &&
                keyboardMode == KeyboardMode.LETTERS &&
                languageMode == LanguageMode.CHINESE &&
                !RemoteInputServerManager.isRunning() &&
                isInputViewShown()
            ) {
                performDelete()
                return true
            }
            return super.onKeyDown(keyCode, ev)
        }
        // T45（T27 缺陷 B 根因修复）· FR-A8 统一口径：中文 × 字母键盘下，硬件字母键码（含 adb
        // 注入通道）与网格 OK 同路进入拼音组合。默认链路对打印键码不消费（本方法此前只处理
        // BACK/方向/确认键），事件经框架直写宿主文本，绕过组合态与候选——中文模式表现为字母
        // 直落不进组（真机 2026-11-09 实证「kde」直落且无 candidates 装载行）。仅拦 ACTION_DOWN
        // 首按；英文模式 / 符号键盘 / 远程模式 / 输入窗隐藏维持框架直写（英文直出与网格行为一致，
        // 数字直出口径不变）。字母键动作经 execute 与网格 OK 完全同路——组合态外 appendLetter
        // 即起新组合，删除键清空组合后无需任何恢复操作即回到组合态。
        if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0 &&
            keyboardMode == KeyboardMode.LETTERS &&
            languageMode == LanguageMode.CHINESE &&
            !RemoteInputServerManager.isRunning() &&
            isInputViewShown()
        ) {
            val letterKey = KeyboardLayout.letterIdForKeyCode(keyCode)?.let { KeyboardLayout.keyById[it] }
            if (letterKey != null) {
                execute(letterKey)
                return true
            }
            // T80（户主拍板 2026-11-16）· FR-A9 数字统一口径：硬件数字键码（KEYCODE_0-9，含 adb
            // 注入通道）与网格数字键 OK 同路走 execute——框架默认直写不进组，数字插在组合区之后
            // 且 composer 不同步（组合态 × 数字失同步，T80 实测 4 项：顺序错乱 nie5、旧拼音复活、
            // 删除反长、光标锚死）。映射到对应数字键后经 execute 直出分支：组合态先终结组合串
            // 再直出数字（"ni" + 5 → "ni5"，拍板语义），无组合态与框架直写等价。守卫条件与上方
            // T45 字母拦截完全同构（同一 if 块）；英文模式 / 符号键盘 / 远程模式 / 输入窗隐藏
            // 维持框架直写，口径不变。KEYCODE_SPACE（62）不在 digitIdForKeyCode 映射内，
            // R-2 空格键面语义另行评估（T80 本轮不动空格键码）。
            val digitKey = KeyboardLayout.digitIdForKeyCode(keyCode)?.let { KeyboardLayout.keyById[it] }
            if (digitKey != null) {
                execute(digitKey)
                return true
            }
        }
        return super.onKeyDown(keyCode, ev)
    }

    /**
     * T22：确认键抬起解除在途连删（长按松开即停，连删只覆盖按住期间）。
     * T44（T27 缺陷 A 根因修复）：确认键 UP 一律消费、不下发视图树——DOWN 已由 [onKeyDown]
     * 消费，迟到的 UP（真机无线链路实测落后主按下约 3.5s）落回视图树会按确认键点击语义触发
     * 当前焦点键的 click：选词上屏后焦点刚回归字母键即被幽灵点击重新入组（composer 残留、
     * 再按叠加「ee」并混入正文，真机 17/17 复现，t27-a2-fulltrace-raw.txt 的 fromKey=false
     * 幽灵装载行即此）。确认键 UP 在 InputMethodService 默认链路无语义，消费无副作用；
     * 连删解除判定先行，语义与 T22 口径一致。
     * T45（T27 缺陷 B 同族）：字母键码 UP 同样一律消费——DOWN 已在 [dispatchKeyDown] 拦截入组，
     * 孤儿 UP 经框架转发路径直写宿主文本会与组合串并存（真机实测组合「e」+直落「e」双写，
     * qa r2-b.xml「额额e」）；UP 在按键语义上无独立文本效应，消费无副作用。
     * T80：数字键码（KEYCODE_0-9）UP 同口径消费——中文模式显示态 DOWN 已拦截走 execute
     *（数字统一口径），孤儿 UP 直写宿主有双写风险；隐藏态透传对称（T59 口径同族）。
     * 真机补充实证（Round 3 指纹实验，2026-11-09）：motion 触摸点击（input tap）命中字母键
     * 同样经 click → execute 入组且无 onKeyDown 行（fromKey=false 装载行首行数可指纹区分被
     * tap 的键：'a'=9 / 'e'=10），属触摸输入通道设计行为，非状态缺陷。
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && keyCode.isConfirmKey()) {
            if (deleteRepeatJob != null) {
                trace("onKeyUp keyCode=$keyCode（连删解除）")
                cancelDeleteRepeat()
            }
            return true
        }
        if (event != null && KeyboardLayout.letterIdForKeyCode(keyCode) != null && isInputViewShown()) {
            // T45：显示态字母键码 UP 一律消费——DOWN 已入组（中文模式）或已透传（英文直出），
            // 孤儿 UP 经框架转发路径直写宿主文本有双写风险。
            // T59（户主拍板 2026-11-11）：隐藏态字母 UP 透传对称——隐藏态 DOWN 已透传宿主
            //（T45 拦截分支含 isInputViewShown 门控，隐藏态未入组），UP 同样透传宿主，
            // 消除 DOWN/UP 分裂派发。
            return true
        }
        if (event != null && KeyboardLayout.digitIdForKeyCode(keyCode) != null && isInputViewShown()) {
            // T80：数字键码 UP 与字母 UP 同款口径（T45/T59 对称）——中文模式显示态 DOWN 已在
            // [dispatchKeyDown] 拦截走 execute（数字统一口径），孤儿 UP 经框架转发直写宿主有双写
            // 风险；UP 无独立文本效应，消费无副作用。隐藏态 DOWN 未拦截（守卫含显示态门控），
            // UP 同样透传宿主，消除 DOWN/UP 分裂派发。
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /** 遥控器 OK / 回车键集合（FR-A11「OK 确认」）。 */
    private fun Int.isConfirmKey(): Boolean = this == KeyEvent.KEYCODE_DPAD_CENTER ||
        this == KeyEvent.KEYCODE_ENTER ||
        this == KeyEvent.KEYCODE_NUMPAD_ENTER

    /** 候选 chip 视图 tag 判定（候选行焦点路由识别）。 */
    private fun String.isCandidateId(): Boolean = startsWith(CANDIDATE_ID_PREFIX)

    /** 候选 chip 视图 tag → 候选全局下标。 */
    private fun String.candidateIndex(): Int? = removePrefix(CANDIDATE_ID_PREFIX).toIntOrNull()

    /** 焦点是否在候选区任一视图（chip）；候选区移除后的焦点回归判定用。 */
    private fun String.isCandidateLayerId(): Boolean = isCandidateId()

    /**
     * 键盘键方向导航（T6 格网语义 + T10 候选行进入 + T17/T67 引导卡主按钮进入）：
     * 行 1 键向上且候选行有 chips 时进入候选行（FR-A7「上下键进出候选行」，进入落点 = 首候选 chip）；
     * 最右列（第 12 列）表缘向右且引导卡主按钮可达时进入主按钮——T67 · 决策 56（修订 FR-A11
     * 键盘右缘表缘不动口径）：字母/符号两键盘最右列全部按键（行 1 [下屏]、行 2 [删除]、
     * 行 3/4 [完成]，两键盘该列同构）按[右]直达「远程输入」主按钮，从按钮按[左]回进入前键位
     *（guideReturnKey 既有口径）不变；副卡无该按钮（配对/已连接卡面）或远程模式主卡时
     * 判否维持表缘不动；其余方向走 FocusNavigator 格网（表缘消费不动，不跳变）。
     */
    private fun handleKeyboardDirection(currentId: String, direction: FocusDirection) {
        val onTopRow = navigator.positionsOf(currentId).any { it.first == 1 }
        if (direction == FocusDirection.UP && onTopRow && candidateViews.isNotEmpty()) {
            candidateReturnKey = currentId
            trace("focus $currentId UP -> cand_0（进入候选行）")
            candidateViews[0].requestFocus(View.FOCUS_DOWN)
            return
        }
        // T64 · 行步进窗口（符号键盘）：焦点锚在显示窗口底/顶行、该方向到显示格网表缘、
        // 且该侧还有真实行时，窗口步进一行；符号内容切换但光标屏上位置不动（步进方法内落焦点）。
        // 字母键盘不触发（格网表缘维持不动口径）；跨行浮层键锚行为 3 不满足触发行，语义不变。
        if (keyboardMode == KeyboardMode.SYMBOLS) {
            val anchor = navigator.positionsOf(currentId).minByOrNull { it.first }
            val newOffset = anchor?.let {
                SymbolsLayout.windowStep(it.first, direction, symbolsWindowOffset)
            }
            if (anchor != null && newOffset != null) {
                stepSymbolsWindow(newOffset, anchor)
                return
            }
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

    /**
     * 引导卡主按钮当前是否可进入（引导卡面呈现 + 焦点键占据最右列格位）。
     * T67 · 决策 56（修订 FR-A11 键盘右缘口径）：进入条件从「行 3/4 纵向带」改为「第 12 列」——
     * 字母/符号两键盘最右列全部按键按[右]均可直达主按钮；副卡为配对卡 / 已连接卡（无该按钮，
     * guideButtonView 为 null）或远程模式主卡时判否，维持表缘不动。
     */
    private fun canEnterGuideButton(currentId: String): Boolean =
        guideButtonView != null &&
            navigator.positionsOf(currentId).any { it.second == FocusNavigator.GRID_COLS }

    /**
     * T64 · 行步进窗口执行（符号键盘）：窗口偏移切到 [newOffset] 重建键区（符号区 7 列切换内容、
     * 数字列与功能键列固定），焦点屏上位置不动——落新窗口同一显示格位的键（原下一/上一行同列键；
     * 符号区换语义，数字列/功能列同键身份）。格网与键位 id 随窗口重建，该格位必存在，缺失兜底回初始键。
     */
    private fun stepSymbolsWindow(newOffset: Int, anchor: Pair<Int, Int>) {
        val previousOffset = symbolsWindowOffset
        symbolsWindowOffset = newOffset
        trace("symbols window $previousOffset -> $newOffset（${if (newOffset > previousOffset) "DOWN" else "UP"} 步进）")
        rebuildKeys(requireNotNull(keyboardView), requestInitialFocus = false)
        val targetId = navigator.keyAt(anchor.first, anchor.second) ?: navigator.initialKey()
        trace("focus step -> $targetId（屏上位置 (${anchor.first}, ${anchor.second}) 不动）")
        keyViews[targetId]?.requestFocus(View.FOCUS_DOWN)
    }

    /** 引导卡主按钮方向导航（T17 · FR-A11）：LEFT 回进入前的键盘键（丢失回初始键）；其余表缘不动。 */
    private fun handleGuideButtonDirection(direction: FocusDirection) {
        if (direction != FocusDirection.LEFT) return
        val targetId = guideReturnKey?.takeIf { keyViews.containsKey(it) } ?: navigator.initialKey()
        trace("focus ${GuideCard.BUTTON_TAG} LEFT -> $targetId（回键盘）")
        keyViews[targetId]?.requestFocus(View.FOCUS_DOWN)
    }

    /**
     * 候选行方向导航（T66 · 决策 53 单行行内翻页 × T67 · 决策 55 翻页落点分向）：左右在页内
     * 候选间移动、页缘同方向翻页——行尾候选按[右]翻下一页落新页首候选、行首候选按[左]回上一页
     * 落回翻页尾候选（修订决策 53「翻页后一律落页首」条款；模型见 CandidatePager，单测锁定）；
     * 向上到顶不动（行顶）、向下出候选区回键盘（落点 = 进入前的键盘键，丢失时回初始键）。
     */
    private fun handleCandidateRowDirection(currentId: String, direction: FocusDirection) {
        val index = currentId.candidateIndex() ?: return
        when (val move = CandidatePager.move(index, direction, candPages)) {
            is CandidatePagerMove.To -> {
                trace("focus $currentId $direction -> cand_${move.index}")
                focusCandidateChip(move.index)
            }
            is CandidatePagerMove.Flip -> {
                // 决策 55 落点分向：trace 文案随落点分向（下翻落页首 / 上翻落页尾），走查取证锚点
                val landing = if (direction == FocusDirection.LEFT) "页尾" else "页首"
                trace("focus $currentId $direction -> page ${move.page}（翻页，落$landing cand_${move.index}）")
                flipCandidatePage(move.page, move.index)
            }
            CandidatePagerMove.ExitDown -> exitCandidatesLayerToKeyboard(currentId)
            CandidatePagerMove.Stay -> Unit
        }
    }

    /** 焦点落候选 chip（候选全局下标；越界 / 不在当前页不落）。 */
    private fun focusCandidateChip(globalIndex: Int) {
        val page = candPages.getOrNull(candPage) ?: return
        if (globalIndex !in page) return
        candidateViews.getOrNull(globalIndex - page.first)?.requestFocus(View.FOCUS_DOWN)
    }

    /**
     * 翻页并装载新页 chips（决策 53 × T67 · 决策 55 落点分向）：页号切换 → 重渲染候选行 →
     * 焦点落 [focusGlobalIndex]（调用方传落点候选全局下标：下翻 = 新页首、上翻 = 回翻页尾）。
     */
    private fun flipCandidatePage(page: Int, focusGlobalIndex: Int) {
        val previous = candPage
        candPage = page
        renderCandidatesRow()
        trace("candidates page $previous -> $page")
        focusCandidateChip(focusGlobalIndex)
    }

    /** 候选行内翻页状态复位（候选集变化 / 候选区整体移除时回首页）。 */
    private fun resetCandidatePageState() {
        candPage = 0
        candPages = emptyList()
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
     * （决策 3：跨行 3/4，col11/12；T68 · 决策 58 画板 20261114 几何：字母完成键 100×138 @1278px、
     * 符号空格 96×138 @1178px、符号完成键 96×138 @1282px，键区右缘 1378px）
     * + T64 行步进边界暗示箭头（下方还有真实行 = 显示第 4 行底部下箭头；上方还有真实行 =
     * 显示第 1 行上方上箭头；灰色装饰件、无障碍不标注、不进焦点链）。
     * 布局切换时先移除旧浮层视图。
     */
    private fun bindOverlayKeys(root: View) {
        val keyArea = root.findViewById<FrameLayout>(R.id.key_area)
        for (view in overlayViews) {
            keyArea.removeView(view)
        }
        overlayViews.clear()
        // 浮层键：键 + 键区内 x 偏移 Token + 键宽 Token（键高/顶距两板一致，共用浮层键 Token）
        val overlays: List<Triple<Key, Int, Int>> = when (keyboardMode) {
            KeyboardMode.LETTERS -> listOf(
                Triple(KeyboardLayout.doneKey, R.dimen.ime_done_key_start, R.dimen.ime_done_key_width),
            )
            KeyboardMode.SYMBOLS -> listOf(
                Triple(SymbolsLayout.spaceKey, R.dimen.ime_space_key_start, R.dimen.ime_space_key_width),
                Triple(SymbolsLayout.doneKey, R.dimen.ime_symbols_done_key_start, R.dimen.ime_symbols_done_key_width),
            )
        }
        for ((key, startRes, widthRes) in overlays) {
            val view = createKeyView(key)
            overlayViews.add(view)
            keyArea.addView(
                view,
                FrameLayout.LayoutParams(
                    resources.getDimensionPixelSize(widthRes),
                    resources.getDimensionPixelSize(R.dimen.ime_overlay_key_height),
                    Gravity.TOP or Gravity.START,
                ).apply {
                    marginStart = resources.getDimensionPixelSize(startRes)
                    topMargin = resources.getDimensionPixelSize(R.dimen.ime_overlay_key_top)
                },
            )
        }
        // T64 边界暗示箭头：显隐随窗口偏移判定（默认态只下箭头、下移态只上箭头、字母键盘两箭头皆无）；
        // 与浮层键同入 overlayViews 注册表，布局/窗口切换重建时随旧视图一并移除，不残留
        if (keyboardMode == KeyboardMode.SYMBOLS) {
            if (SymbolsLayout.canStepDown(symbolsWindowOffset)) {
                val down = createRowArrowView(R.drawable.ic_row_arrow_down)
                overlayViews.add(down)
                keyArea.addView(
                    down,
                    FrameLayout.LayoutParams(
                        resources.getDimensionPixelSize(R.dimen.ime_row_arrow_width),
                        resources.getDimensionPixelSize(R.dimen.ime_row_arrow_height),
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    ).apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.ime_row_arrow_down_top)
                    },
                )
            }
            if (SymbolsLayout.canStepUp(symbolsWindowOffset)) {
                val up = createRowArrowView(R.drawable.ic_row_arrow_up)
                overlayViews.add(up)
                keyArea.addView(
                    up,
                    FrameLayout.LayoutParams(
                        resources.getDimensionPixelSize(R.dimen.ime_row_arrow_width),
                        resources.getDimensionPixelSize(R.dimen.ime_row_arrow_height),
                        Gravity.TOP or Gravity.CENTER_HORIZONTAL,
                    ).apply {
                        topMargin = resources.getDimensionPixelSize(R.dimen.ime_row_arrow_up_top)
                    },
                )
            }
        }
    }

    /**
     * 行步进边界暗示箭头视图（T64 · 画板 3/10）：纯装饰件——无障碍标注置空 + 不参与无障碍、
     * 不可聚焦（不进焦点链，方向导航仍走 onKeyDown 格网语义）。
     */
    private fun createRowArrowView(drawableRes: Int): View = ImageView(this).apply {
        setImageResource(drawableRes)
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
        isFocusableInTouchMode = false
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
        // 状态键创建即按当前模式渲染当前态指示 + 运行态无障碍描述（决策 46-② / 47-①；
        // 依赖 tag 锚定键 id，须在注册表登记后执行）
        if (key.stateOptions != null) applyStateKeyAppearance(view)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnClickListener { execute(key) }
        return view
    }

    /**
     * 键帽内容视图装配（T6 基座 + T65 扩展）：状态键 = 斜杠排版两态容器（决策 46-② 方案 C）；
     * 上档键 = 图标 + 下划线条容器（决策 47-①）；其余文字键 = TextView、图标键 = ImageView
     *（图标尺寸按画板 Token）。无障碍标注全键位必设（T26）。
     */
    private fun createKeyContentView(key: Key): View {
        val view: View = when {
            key.stateOptions != null -> createStateKeyView(key)
            key.icon == KeyIcon.SHIFT -> createShiftKey(key)
            key.icon != null -> createIconKey(key)
            else -> createTextKey(key)
        }
        // 字母键标注随键面形态（T66 · 决策 51-②/③：中文恒显大写、英文随大小写输出状态，
        // 朗读同文）；其余键位用模型动作语义描述
        view.contentDescription =
            if (key.id in KeyboardLayout.letterKeyIds) {
                KeyboardLayout.displayLabel(
                    key,
                    languageMode == LanguageMode.CHINESE,
                    caseState == CaseState.UPPER,
                )
            } else {
                key.contentDescription
            }
        // 上档键创建即带当前态标注（决策 47-① / 48-①：含当前态 + 动作；后续随动走 applyCaseState）
        if (key.icon == KeyIcon.SHIFT) {
            view.contentDescription = KeyboardLayout.shiftContentDescription(caseState)
        }
        return view
    }

    private fun createTextKey(key: Key): TextView {
        val sizeRes = when (key.textSize) {
            KeyTextSize.SMALL -> R.dimen.ime_key_fn_text
            // 半角多字符符号键面（T64 · 画板 4「...」「--」24px）
            KeyTextSize.DENSE -> R.dimen.ime_key_dense_text
            KeyTextSize.NORMAL -> R.dimen.ime_key_text
        }
        return TextView(this).apply {
            // 键面随语言模式与大小写输出状态（T66 · 决策 51-②/③）：
            // 中文模式恒显大写形态、英文模式随 ⇧ 触发态
            text = KeyboardLayout.displayLabel(
                key,
                languageMode == LanguageMode.CHINESE,
                caseState == CaseState.UPPER,
            )
            gravity = Gravity.CENTER
            maxLines = 1
            // dimens 中以 sp 声明，getDimension 已换算为 px，按 px 设置避免二次缩放
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(sizeRes))
            setTextColor(getColor(textColorOf(key.variant)))
            background = getDrawable(backgroundOf(key.variant))
        }
    }

    /**
     * 状态键视图（T65 · 决策 46-② 方案 C）：保留「中/英」「全/半」斜杠排版，当前态字符亮青
     *（#7DD3FC）加粗 + 3px 下划线、另一态压暗（半透明白 50%），指示随切换实时随动
     *（applyStateKeyAppearance）；焦点态叠加按决策 50-②（焦点底 wash + 描边、焦点文字 #E8EEF5）。
     * 容器可聚焦，焦点底由 bg_key selector 表达；字符/分隔线为展示件（容器整体承载朗读语义）。
     */
    private fun createStateKeyView(key: Key): View {
        val options = requireNotNull(key.stateOptions) { "状态键缺少两态字符：${key.id}" }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = getDrawable(backgroundOf(key.variant))
            setOnFocusChangeListener { v, _ -> applyStateKeyAppearance(v) }
        }
        val textPx = resources.getDimension(R.dimen.ime_key_fn_text)
        // 状态键当前态下划线 2px（T68 画板 .st-on text-decoration-thickness:2px；与 ⇧ 触发态
        // 3px 短条分列两 Token，见 ime_state_char_underline_height / ime_state_underline_height）
        val barHeight = resources.getDimensionPixelSize(R.dimen.ime_state_char_underline_height)
        val barOffset = resources.getDimensionPixelSize(R.dimen.ime_state_underline_offset)
        val charOptions = listOf(options.first, options.second)
        charOptions.forEachIndexed { index, option ->
            if (index > 0) {
                val sep = TextView(this).apply {
                    text = "/"
                    setTextColor(getColor(R.color.text_hint_faint))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                container.addView(
                    sep,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        marginStart = resources.getDimensionPixelSize(R.dimen.ime_state_sep_margin)
                        marginEnd = resources.getDimensionPixelSize(R.dimen.ime_state_sep_margin)
                    },
                )
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            cell.addView(
                TextView(this).apply {
                    text = option
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    tag = stateCharTag(index)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            cell.addView(
                View(this).apply {
                    setBackgroundResource(R.drawable.bg_key_underline)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    tag = stateBarTag(index)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    barHeight,
                ).apply { topMargin = barOffset },
            )
            container.addView(
                cell,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        return container
    }

    /**
     * 上档键视图（T65 · 决策 47-① 触发态统一 / 决策 46-④；尺寸随 T68 · 决策 58 画板 20261114）：
     * ⇧ 图标 26px + 图标下方 3px 短下划线条（宽 30px、圆角 2px、间距 5px）；
     * 触发态（大写）= 图标亮青 + 下划线条可见（激活底色与描边废止，bg 恒为 bg_key，焦点态由
     * selector 表达），未触发 = 图标功能键色、条隐藏（applyCaseState 刷新）。
     */
    private fun createShiftKey(key: Key): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = getDrawable(backgroundOf(key.variant))
        }
        val iconSize = resources.getDimensionPixelSize(R.dimen.ime_icon_shift)
        val icon = ImageView(this).apply {
            setImageResource(R.drawable.ic_key_shift)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            tag = SHIFT_ICON_TAG
        }
        container.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
        val bar = View(this).apply {
            setBackgroundResource(R.drawable.bg_key_underline)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            tag = SHIFT_BAR_TAG
        }
        container.addView(
            bar,
            LinearLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.ime_shift_underline_width),
                resources.getDimensionPixelSize(R.dimen.ime_state_underline_height),
            ).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.ime_shift_underline_gap)
            },
        )
        // 创建即按当前触发态着色（决策 48-①：键盘打开默认 ⇧ 触发态；后续随动走 applyCaseState）
        val upper = caseState == CaseState.UPPER
        icon.setColorFilter(getColor(if (upper) R.color.accent_text else R.color.text_secondary))
        bar.visibility = if (upper) View.VISIBLE else View.GONE
        return container
    }

    /** 图标键视图（下屏 / 删除 / 光标左右；上档键走 [createShiftKey] 容器，T65 · 决策 47-①）。 */
    private fun createIconKey(key: Key): ImageView {
        val icon = key.icon ?: error("图标键缺少图标定义：${key.id}")
        val keyWidth = resources.getDimensionPixelSize(R.dimen.ime_key_width)
        val keyHeight = resources.getDimensionPixelSize(R.dimen.ime_key_height)
        val iconSize = resources.getDimensionPixelSize(iconSizeOf(icon))
        return ImageView(this).apply {
            setImageResource(iconResOf(icon))
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = getDrawable(backgroundOf(key.variant))
            // padding 限定图标绘制区 = 键帽中心 iconSize 见方（画板 图标尺寸）
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
            // T59（户主拍板 2026-11-11）：[下屏] 收起与 [完成] 收起统一组合态落定——收起前
            // finishComposition（组合串原样上屏，无组合时为无操作），消除「BACK/[下屏] 收起
            // 残留组合串、重弹时被 onStartInputView reset 丢弃」的脱节面。
            KeyAction.HideSelf -> {
                finishComposition()
                hideSelfAfterKeyDispatch()
            }
            KeyAction.Done -> {
                // FR-A10「确认当前输入并收起」：结束组合态（组合串原样上屏，无组合时为无操作），再收起。
                // T49 评审建议 3（户主 2026-11-10 批准）：落定+复位收敛到 finishComposition 单一实现点，
                // 全部落定路径同源，避免同族缺陷复发（某处落定未带复位）
                finishComposition()
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
                    // T80（户主拍板 2026-11-16）：字母键盘 × 中文模式 × 非字母直出键（数字列 /
                    // .com / @）先终结组合态再直出——组合串原样上屏（finishComposition 既有语义：
                    // setComposingText + finishComposingText 剥离切分符 + composer 复位），数字紧随
                    // 其后直出（组合 "ni" 按 5 → 正文 "ni5"）。不终结时框架 commitText 语义是替换
                    // 当前组合区文本：拼音被吞、composer 残留幻影组合串（后续字母复活、删除反长），
                    // 网格 OK 与拦截后的硬件数字键码两通道同走本分支，修一处两通道同治。
                    // 终结后候选栏同步清空（refreshCandidatesBar 按 composer 空态清 chips，与
                    // moveCursor 落定路径同口径）；英文模式 / 符号键盘 / 无组合态不进本分支
                    //（空组合终结为无操作，行为不变）。
                    if (languageMode == LanguageMode.CHINESE && composer.composition.isNotEmpty()) {
                        finishComposition()
                        refreshCandidatesBar()
                    }
                    commitText(KeyboardLayout.outputValue(key, caseState))
                }
            }
            // 决策 8（R-2）× T66 · 决策 53 组合态空格：有候选时选【当前页第一候选】（单行行内
            // 翻页口径，「首候选」修订为当前页第一候选）；无候选时组合串原样上屏（剥离切分符）
            // 后再输入空格；英文模式 / 无组合态直出空格（FR-A9）
            KeyAction.Space -> {
                val pageFirst = candPages.getOrNull(candPage)?.first
                val pageFirstCandidate = pageFirst?.let { composer.candidates.getOrNull(it) }
                if (languageMode == LanguageMode.CHINESE && composer.hasCandidates && pageFirstCandidate != null) {
                    selectCandidate(pageFirstCandidate)
                } else if (languageMode == LanguageMode.CHINESE && composer.composition.isNotEmpty()) {
                    finishComposition()
                    commitText(" ")
                    refreshCandidatesBar()
                } else {
                    commitText(" ")
                }
            }
            // FR-A9 删除：组合态内删组合串末位并刷新候选；组合态外删宿主文本（选区优先）。
            // T22：单击与长按连删共用本路径（performDelete），语义一致。
            KeyAction.Delete -> performDelete()
            // FR-A9：光标在输入框内移动（插入点随移动生效，走查覆盖）
            KeyAction.CursorLeft -> moveCursor(-1)
            KeyAction.CursorRight -> moveCursor(1)
            // T66 · 决策 51-② 分词键（中文模式 ⇧ 键位替代键）三细节：
            // ①组合态内插入拼音切分符（如 xi'an）；②无组合态按键忽略；③选词后切分符不落正文、
            // 组合串原样上屏不带切分符（分别由 selectCandidate 区域替换与 finishComposition
            // 剥离承载，见两处实现）。
            KeyAction.Segment -> {
                if (composer.composition.isNotEmpty()) {
                    composer.appendSegment()
                    updateComposingText()
                    refreshCandidatesBar()
                } else {
                    trace("segment ignored（无组合态）")
                }
            }
            // FR-A3 大小写切换（T66 · 决策 51-②/③ 修订）：中文模式无 ⇧ 键位（该键位为分词键，
            // KeyAction.Segment），本分支仅英文态可达——纯大小写翻转 + 触发态样式与键面随动
            //（决策 47-① 触发态样式不变；键面随大小写输出状态，决策 51-③）。
            // 决策 49「中文态按[上档]」路径随决策 51 废止删除；模式切换复位语义保留于
            // [中/英] 键（决策 43-② 不变）。
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
                // T64：行步进窗口偏移跨全/半切换保留（两页同位语义，画板 10/11 两页各有下移态）；
                // T81：焦点保持 [全/半] 键（2026-11-17 户主拍板修订既有「焦点回初始键」口径，
                // 与 [中/英] 键既有「焦点保持本键」接线同型）
                rebuildKeys(requireNotNull(keyboardView), requestInitialFocus = false)
                // 焦点保持 [全/半] 键（键区重建后视图重建，该键两页恒存在）
                keyViews[SymbolsLayout.TOGGLE_WIDTH_ID]?.requestFocus(View.FOCUS_DOWN)
            }
            // FR-A4 中/英切换 × T55（户主拍板 2026-11-11「模式切换键必须同时复位组合与候选」）
            // × T66 · 决策 51：中文 → 英文切换唯一路径 = 本键（决策 43-② 复位口径不变——组合串
            // 与候选取消、不落输入框，落零候选词干净态）；英文态默认小写（决策 51-③，落 ⇧ 未触发）。
            // 行 1 头键随语言模式分化（中文 = 分词 / 英文 = ⇧），键区重建装配 + 焦点保持本键；
            // 候选栏占位不变、chips 清空（决策 42：占位全模式恒定，键区顶不随语言切换移动）。
            KeyAction.ToggleLanguage -> {
                cancelComposition()
                languageMode =
                    if (languageMode == LanguageMode.CHINESE) LanguageMode.ENGLISH else LanguageMode.CHINESE
                if (languageMode == LanguageMode.ENGLISH) caseState = CaseState.LOWER
                trace("language -> $languageMode case -> $caseState")
                rebuildKeys(requireNotNull(keyboardView), requestInitialFocus = false)
                // 焦点保持 [中/英] 键（键区重建后视图重建，格位两态同构必存在）
                keyViews["lang"]?.requestFocus(View.FOCUS_DOWN)
            }
        }
    }

    /**
     * 字母键盘 ↔ 符号键盘切换（FR-A5 × T66 · 决策 52）：先结束组合态（组合串原样上屏，
     * 剥离切分符），再重建键区视图与导航格网，焦点落新布局初始键。进符号键盘复位行步进窗口
     * 默认态（T64）；初始角态随入口模式赋值——中文态 → 全角、英文态 → 半角，手动 [全/半]
     * 仅本次停留生效，返回字母键盘后再进入重新跟随（不记忆手动选择）。
     */
    private fun switchKeyboardMode(mode: KeyboardMode) {
        finishComposition()
        keyboardMode = mode
        if (mode == KeyboardMode.SYMBOLS) {
            symbolsWidth = when (languageMode) {
                LanguageMode.CHINESE -> SymbolsWidth.FULL_WIDTH
                LanguageMode.ENGLISH -> SymbolsWidth.HALF_WIDTH
            }
            symbolsWindowOffset = 0
        }
        trace("keyboardMode -> $mode symbolsWidth -> $symbolsWidth")
        rebuildKeys(requireNotNull(keyboardView), requestInitialFocus = true)
    }

    /**
     * 结束组合态（T10 × T66 · 决策 51-②）：组合串原样上屏（不丢已按键内容），状态机复位；
     * 无组合时为无操作。上屏前剥离分词切分符（决策 51-②「组合串无候选原样上屏且不带切分符」）——
     * 组合区内容先替换为剥离后的纯字母串再终结组合区间；选词上屏经区域替换天然不带切分符。
     */
    private fun finishComposition() {
        if (composer.composition.isEmpty()) return
        currentInputConnection?.let { ic ->
            ic.beginBatchEdit()
            try {
                ic.setComposingText(composer.committedText(), 1)
                ic.finishComposingText()
            } finally {
                ic.endBatchEdit()
            }
        }
        composer.reset()
    }

    /**
     * 取消组合态（T55 · 户主拍板 2026-11-11「模式切换键必须同时复位组合与候选」）：
     * 组合串与候选一并作废、不落输入框——先 setComposingText("") 清空框架组合区间
     *（与 updateComposingText 同通道，空串即移除区间内容），再 finishComposingText 终结
     * 空区间（不留悬空组合态），状态机复位；无组合时为无操作。与 [finishComposition]
     *（组合串原样上屏，剥离切分符）相对：模式切换键（[中/英]，中文 → 英文唯一路径，决策 51-②）
     * 走本路径，切换后的输入框不残留组合字母与候选词。IC 缺失时仍复位状态机（防幻影组合串，T48 同口径）。
     */
    private fun cancelComposition() {
        if (composer.composition.isEmpty()) return
        currentInputConnection?.let { ic ->
            ic.beginBatchEdit()
            try {
                ic.setComposingText("", 1)
                ic.finishComposingText()
            } finally {
                ic.endBatchEdit()
            }
        }
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
     * 刷新候选栏内容（组合态变化、模式切换、键盘重建后调用）。
     * 栏区空态几何占位无条件保留（决策 42：中/英模式 × 字母/符号键盘全组合键区顶恒定，
     * FR-A7「英文/数字模式下候选栏隐藏、键盘区上移」条款废止——中/英切换不再整栏隐藏、
     * 键区不上移，键区上边距恒为布局静态值）；
     * chips 仅字母键盘 × 组合态有候选时装载（决策 1：只显示候选词，无组合态时栏区留空；
     * 英文/符号键盘恒空态占位——英文字母直出、符号直出均不进组合态，决策 42 内容口径）；
     * 候选集变化即回首页并重分页（T66 · 决策 53：单行行内翻页，页态只在同一候选集内有效）。
     * 远程模式不在本函数口径内：候选栏显隐由 renderRemoteUi 驱动（T17，候选栏保持隐藏、
     * 防空态占位顶低状态行）。
     */
    private fun refreshCandidatesBar() {
        val bar = candidatesBar ?: return
        bar.visibility = View.VISIBLE
        // 装载口径（决策 42 内容条款）：候选装载仅由中文组合态触发、且仅字母键盘——英文字母
        // 直出、符号键盘直出符号，均不进组合态，候选栏恒空态占位
        val loadCandidates = keyboardMode == KeyboardMode.LETTERS && composer.hasCandidates
        if (loadCandidates) {
            bindCandidates()
        } else {
            bar.removeAllViews()
            candidateViews.clear()
            resetCandidatePageState()
            // T45（T27 缺陷 B · R3 残留症状定位）：组合串非空但引擎无候选（如声母独用 f/ff/fff）
            // 时，候选栏留空且全链此前无任何埋点行——黑盒走查只能观测到「OK 落字母、无装载行、
            // 字母出现在输入框」，无法区分「组合态无候选」与「字母直出未入组」（T27 R1 缺陷 B
            // 误判与 R3「直落 f×3」复观测的共同签名；模拟器网格通道复现实证：组合串实际带下划线
            // 存活，状态机无损坏）。此处补齐装载行 total=0，与有候选装载同一埋点口径（只记耗时与
            // 结构计数，不含输入内容）：键事件路径带 T36 计时起点（fromKey=true），非键事件路径
            // （会话启动 / 键盘重建 / 选词复位）candidateLoadStartMs 已归位，fromKey=false 既有
            // 口径自动成立。组合串为空（无组合态）维持静默，避免选词复位等高频路径新增噪音行。
            if (composer.composition.isNotEmpty()) {
                traceCandidateLoadCompleted(totalCount = 0, pageCount = 0)
            }
        }
    }

    /**
     * 依候选装载单行候选栏（T66 · 决策 53）：先测宽，依可用宽度（扣除两端翻页箭头占位）
     * 经 CandidatePager.paginate 把全部候选切成页（页区间 = 候选全局下标，坑 10 相对坐标系
     * 消除），装载首页 chips；两端固定 `<` `>` 翻页箭头（装饰件：可翻方向提亮 #E8EEF5、
     * 不可翻方向变暗 35% 白，不进焦点链、无障碍不标注）。
     */
    private fun bindCandidates() {
        resetCandidatePageState()
        val candidates = composer.candidates
        if (candidates.isEmpty()) return
        val gap = resources.getDimensionPixelSize(R.dimen.ime_chip_gap)
        // 未挂载状态下先量宽再装载（TextView measure UNSPECIFIED = 文字自适应宽，结果已含 padding）。
        // 未挂载 TextView 也必须有 LayoutParams：setText 触发 checkForRelayout 会解引用
        // layoutParams，为 null 时 NPE（模拟器走查实测崩溃点，已修）
        val measurer = createChipMeasurer()
        val widths = candidates.map { word ->
            measurer.text = word
            measurer.measure(0, 0)
            measurer.measuredWidth
        }
        candPages = CandidatePager.paginate(
            widths,
            gap,
            candidatesAvailablePx() - candidateArrowReservedPx(),
        )
        renderCandidatesRow()
        // T36 · 候选装载完成埋点（debug 专用 trace，release 静默）：只记耗时与结构计数，
        // 不含任何输入内容（隐私红线，AGENTS 禁止事项 2）
        traceCandidateLoadCompleted(candidates.size, candPages.size)
    }

    /**
     * 渲染候选行当前页（决策 53）：行结构 = [< 箭头] [当前页 chips…] [弹性占位] [> 箭头]，
     * 箭头固定候选框两侧（画板 2）；翻页与候选集变化共用本渲染入口。
     */
    private fun renderCandidatesRow() {
        val bar = candidatesBar ?: return
        val gap = resources.getDimensionPixelSize(R.dimen.ime_chip_gap)
        bar.removeAllViews()
        candidateViews.clear()
        bar.addView(createCandArrowView())
        val page = candPages.getOrNull(candPage)
        val candidates = composer.candidates
        if (page != null) {
            for (index in page) {
                if (index !in candidates.indices) break
                val view = createChipView(candidates[index], CANDIDATE_ID_PREFIX + index)
                candidateViews.add(view)
                bar.addView(
                    view,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ).apply {
                        // 末 chip 免 marginEnd：与右箭头的间距由弹性占位承担，不叠加双倍 gap
                        if (index != page.last) marginEnd = gap
                    },
                )
            }
        }
        // 弹性占位：chips 左对齐（画板 2 flex spacer），右箭头钉在候选框右缘
        bar.addView(
            View(this),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        bar.addView(createCandArrowView())
        refreshCandArrowAppearance()
    }

    /**
     * 翻页箭头占位宽度（分页可用宽度收缩量）：两端箭头本体 + 各自与 chips 的间距，
     * 全额计入防 chips 贴箭头溢出（画板 2 布局口径）。
     */
    private fun candidateArrowReservedPx(): Int =
        2 * (resources.getDimensionPixelSize(R.dimen.ime_cand_arrow_width) +
            resources.getDimensionPixelSize(R.dimen.ime_cand_arrow_gap))

    /**
     * 行内翻页箭头视图（决策 53 / 画板 2）：`<` `>` 字符样式（约 30px 加粗），纯装饰暗示——
     * 无障碍不标注（contentDescription 空 + importantForAccessibility=NO）、不可聚焦
     *（不进焦点链，方向导航仍走 onKeyDown 候选行语义，同决策 45 符号键盘边界箭头口径）。
     */
    private fun createCandArrowView(): TextView = TextView(this).apply {
        gravity = Gravity.CENTER
        maxLines = 1
        includeFontPadding = false
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.ime_cand_arrow_text))
        setTypeface(typeface, Typeface.BOLD)
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
        isFocusableInTouchMode = false
        layoutParams = barLayoutParamsForArrow()
    }

    /** 箭头 LayoutParams：固定宽（箭头 Token）+ 与 chips 侧间距，行高随候选栏。 */
    private fun barLayoutParamsForArrow(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.ime_cand_arrow_width),
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.ime_cand_arrow_gap) }

    /**
     * 箭头明暗随当前页可翻方向刷新（决策 53）：可翻页方向提亮 #E8EEF5（text_body）、
     * 不可翻方向变暗 rgba(232,239,246,.35)（text_hint_faint）；单页时两向均暗。
     */
    private fun refreshCandArrowAppearance() {
        val bar = candidatesBar ?: return
        val canPageBack = candPage > 0
        val canPageForward = candPage < candPages.size - 1
        // 行结构固定：首视图 = 左箭头、末视图 = 右箭头（renderCandidatesRow 装配口径）
        val left = bar.getChildAt(0) as? TextView ?: return
        val right = bar.getChildAt(bar.childCount - 1) as? TextView ?: return
        left.text = "<"
        right.text = ">"
        left.setTextColor(getColor(if (canPageBack) R.color.text_body else R.color.text_hint_faint))
        right.setTextColor(getColor(if (canPageForward) R.color.text_body else R.color.text_hint_faint))
    }

    /**
     * T36 · 候选装载完成 trace（debug 专用，trace 内 BuildConfig.DEBUG 门控，release 静默）。
     * 口径：fromKey=true 时 elapsedMs = onKeyDown 到达主线程 → 候选 chips 与翻页箭头装载完成，
     * 全程同步同栈（含引擎查询、组合串写入宿主、分页计算），不含后续布局/渲染帧；
     * elapsedMs=-1 = 非键事件触发的装载（键盘重建 / 会话启动 / 远程上屏回写等），不计时。
     * 只记耗时与结构计数（可用宽、页数、总数），不含输入内容（隐私红线）。
     */
    private fun traceCandidateLoadCompleted(totalCount: Int, pageCount: Int) {
        val startMs = candidateLoadStartMs
        candidateLoadStartMs = -1L
        val elapsedMs = if (startMs > 0) SystemClock.uptimeMillis() - startMs else -1L
        trace(
            "candidates loaded fromKey=${startMs > 0} elapsedMs=$elapsedMs" +
                " availablePx=${candidatesAvailablePx()} pages=$pageCount" +
                " total=$totalCount",
        )
    }

    /**
     * 候选 chip 视图（候选行，T10 口径延续）：可聚焦（遥控器候选行导航）、OK 选中上屏。
     * 无常亮高亮（决策 30：含首候选，高亮仅表达遥控器焦点态，样式见 applyChipAppearance）。
     */
    private fun createChipView(word: String, tag: String): TextView {
        val view = TextView(this).apply {
            text = word
            // 无障碍标注 = 候选词（T26 全可交互元素口径；与键面同文，TalkBack 朗读一致）
            contentDescription = word
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

    /**
     * 候选行可用宽度（T37 口径 · T68 单板修订）：候选栏宽 = 键区列宽 Token（栏宽为固定静态值，
     * 与行内容无关）——栏已布局取其实宽；未布局（键盘重建后首次装载）按 Token 推算，数值一致。
     * 单板布局下候选栏父容器 = 全宽板体（1920px），不可再作可用宽来源（父容器宽 ≠ 候选行宽，
     * T68 模拟器实测 availablePx 误得 1920、chips 溢出键区列）——改读候选栏自身宽：
     * 栏宽内容无关，无 T12 坑 3 的「随上轮行内容漂移」问题。
     */
    private fun candidatesAvailablePx(): Int {
        val bar = candidatesBar
        if (bar != null && bar.width > 0) {
            return bar.width
        }
        return resources.getDimensionPixelSize(R.dimen.ime_key_zone_width)
    }

    /**
     * 候选 chip 底形与文字色（全部走 Token）：高亮仅表达遥控器焦点态（决策 30，无常亮高亮）——
     * 焦点态 = accent-wash 淡青底（rgba(56,189,248,.28)，决策 54 加深）+ border-accent 描边
     *（PRD 6.4「聚焦态 = 主色描边」）+ 焦点文字 #E8EEF5（#7DD3FC 焦点青字废止，决策 50-②）；
     * 非焦点 chip 无底色 + 键帽字符正文色。
     */
    private fun applyChipAppearance(view: View) {
        if (view.isFocused) {
            view.background = getDrawable(R.drawable.bg_chip_highlight)
            (view as? TextView)?.setTextColor(getColor(R.color.text_body))
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
     * 删除一步（FR-A9 单击 / T22 长按连删共用路径）：组合态内删组合串末位并刷新候选；
     * 组合态外删宿主文本（选区优先）。连删逐次复用本路径——组合串删空后自动接删已上屏文本，
     * 与单击语义一致（FR-A12 中文组合态口径）。
     */
    private fun performDelete() {
        if (composer.composition.isNotEmpty()) {
            composer.deleteLastLetter()
            updateComposingText()
            refreshCandidatesBar()
        } else {
            deleteBackward()
        }
    }

    // ---- T22 · 删除键长按连删排程（FR-A12） ----

    /**
     * 焦点键为删除键时武装长按连删（FR-A12；主键盘 backspace 与符号键盘 delete 同为
     * KeyAction.Delete，两处均生效）：单击已在此前 performClick 完成首删，本方法只排程后续
     * 重复删除——延迟节奏全部取自 [deleteRepeatRhythm]（先慢后快单一事实源）。系统键重复事件
     * （ACTION_DOWN repeatCount>0，节奏由系统重复率决定）不消费、不参与本排程。
     * 焦点键非删除键时为无操作：光标移动等其他键不获得长按重复（FR-A12 范围边界）。
     */
    private fun armDeleteRepeatForFocusedKey() {
        val focusId = (keyboardView?.findFocus() as? View)?.tag as? String ?: return
        val focusedKey = when (keyboardMode) {
            KeyboardMode.LETTERS -> KeyboardLayout.keyById[focusId]
            // T64：按当前显示窗口查键（焦点键恒在显示窗口内；全矩阵见 SymbolsLayout.allMatrixKeys）
            KeyboardMode.SYMBOLS -> SymbolsLayout.allKeys(symbolsWidth, symbolsWindowOffset)
                .firstOrNull { it.id == focusId }
        }
        if (focusedKey?.action != KeyAction.Delete) return
        deleteRepeatIndex = 0
        val job = object : Runnable {
            override fun run() {
                if (deleteRepeatJob !== this) return // 已解除或被新武装替换，本次回调作废
                performDelete()
                deleteRepeatIndex += 1
                trace("delete repeat #$deleteRepeatIndex")
                deleteRepeatHandler.postDelayed(this, deleteRepeatRhythm.delayBefore(deleteRepeatIndex))
            }
        }
        deleteRepeatJob = job
        deleteRepeatHandler.postDelayed(job, deleteRepeatRhythm.delayBefore(0))
    }

    /** 解除在途连删（幂等）：取消已排程回调并复位计数；无在途时为无操作。接线点见各调用处注释。 */
    private fun cancelDeleteRepeat() {
        deleteRepeatJob?.let { deleteRepeatHandler.removeCallbacks(it) }
        deleteRepeatJob = null
        deleteRepeatIndex = 0
    }

    /**
     * 光标在输入框内移动（FR-A9）。经 InputConnection 直接 setSelection，
     * 不产生按键事件（避免与焦点导航的方向键拦截相互干扰）。
     * T49：移动前先同步终结组合态——组合态移动光标时，框架 finishComposingText 会把组合串
     * 落为正文（组合区间消失），composer 若不同步复位则残留幻影组合串；其后删除键经 T48
     * 拦截分支按幻影组合判定走 performDelete → setComposingText 把幻影末字母闪插进正文
     * （正文 ke→kke），次针白按删插入字母，第三针起才真正删正文。落定与复位必须成对
     * （finishComposition，与完成键 / 中英切换 / 符号切换同一口径），落定后候选栏同步清空。
     */
    private fun moveCursor(delta: Int) {
        val ic = currentInputConnection ?: return
        if (composer.composition.isNotEmpty()) {
            finishComposition()
            refreshCandidatesBar()
        }
        ic.beginBatchEdit()
        try {
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
     * 刷新大小写相关键面（T65 · 决策 47-① / T66 · 决策 51-③）：⇧ 触发态（大写）= 图标亮青 +
     * 图标下方 3px 短下划线条（激活底色与描边废止，键帽背景恒为 bg_key，焦点态由 selector 表达）；
     * 未触发 = 图标功能键色、下划线条隐藏。英文模式键面随大小写输出状态
     *（决策 51-③：小写态键面小写、大写态键面大写）——字母键面与无障碍标注随触发态刷新；
     * 中文模式键面恒显大写（决策 51-②），随动刷新对中文态无可见变化（幂等）。
     */
    private fun applyCaseState() {
        val upper = caseState == CaseState.UPPER
        keyViews["shift"]?.let { shift ->
            shift.findViewWithTag<ImageView>(SHIFT_ICON_TAG)?.setColorFilter(
                getColor(if (upper) R.color.accent_text else R.color.text_secondary),
            )
            shift.findViewWithTag<View>(SHIFT_BAR_TAG)?.visibility =
                if (upper) View.VISIBLE else View.GONE
            shift.contentDescription = KeyboardLayout.shiftContentDescription(caseState)
        }
        refreshLetterKeyFaces()
    }

    /**
     * 字母键面与无障碍标注随语言模式 / 大小写状态刷新（T66 · 决策 51-②/③）：
     * 中文模式恒显大写形态（A）；英文模式随 ⇧ 触发态（小写 a / 大写 A），朗读同文。
     * 仅字母键受影响（[⇧] 切换不重建键区，定点刷新）。
     */
    private fun refreshLetterKeyFaces() {
        val chineseMode = languageMode == LanguageMode.CHINESE
        val upperCase = caseState == CaseState.UPPER
        for (id in KeyboardLayout.letterKeyIds) {
            val view = keyViews[id] as? TextView ?: continue
            val label = KeyboardLayout.displayLabel(
                KeyboardLayout.keyById.getValue(id),
                chineseMode,
                upperCase,
            )
            view.text = label
            view.contentDescription = label
        }
    }

    /**
     * 状态键当前态渲染（T65 · 决策 46-② 方案 C / 47-①）：当前态字符亮青（#7DD3FC）加粗 +
     * 3px 下划线，另一态压暗（半透明白 50%），分隔线中性淡色；无障碍标注含当前态 + 动作。
     * 焦点态叠加（决策 50-②）：焦点文字 #E8EEF5（#7DD3FC 焦点青字废止），加粗与下划线指示保留。
     */
    private fun applyStateKeyAppearance(view: View) {
        val keyId = view.tag as? String ?: return
        val activeIndex = when (keyId) {
            "lang" -> if (languageMode == LanguageMode.CHINESE) 0 else 1
            SymbolsLayout.TOGGLE_WIDTH_ID -> if (symbolsWidth == SymbolsWidth.HALF_WIDTH) 1 else 0
            else -> return
        }
        for (index in 0..1) {
            val charView = view.findViewWithTag<TextView>(stateCharTag(index)) ?: continue
            val active = index == activeIndex
            charView.typeface = Typeface.create(charView.typeface, if (active) Typeface.BOLD else Typeface.NORMAL)
            charView.setTextColor(
                getColor(
                    when {
                        !active -> R.color.text_faint
                        view.isFocused -> R.color.text_body // 决策 50-②：焦点文字废止青字
                        else -> R.color.accent_text
                    },
                ),
            )
            view.findViewWithTag<View>(stateBarTag(index))?.visibility =
                if (active) View.VISIBLE else View.GONE
        }
        view.contentDescription = when (keyId) {
            "lang" -> KeyboardLayout.langContentDescription(languageMode == LanguageMode.CHINESE)
            SymbolsLayout.TOGGLE_WIDTH_ID -> SymbolsLayout.widthContentDescription(symbolsWidth == SymbolsWidth.HALF_WIDTH)
            else -> view.contentDescription
        }
    }

    /** [中/英] 状态键指示随语言模式刷新（决策 46-② 实时随动；键不存在时无操作）。 */
    private fun applyStateKeyAppearance() {
        keyViews["lang"]?.let { applyStateKeyAppearance(it) }
    }

    /** 状态键当前态字符视图 tag（index = 0/1，对应 first/second 态字符）。 */
    private fun stateCharTag(index: Int): String = "state_char_$index"

    /** 状态键当前态下划线条视图 tag（index = 0/1，与 [stateCharTag] 同下标）。 */
    private fun stateBarTag(index: Int): String = "state_bar_$index"

    /**
     * 收起键盘（FR-A1 / FR-A10）。CENTER 键的转发广播会让宿主 EditText 同拍触发
     * showSoftInput（模拟器 API 34 实测覆盖同步收起），延迟一拍保证收起在其后生效；
     * 系统返回键路径不经此逻辑（policy 层直接收起，无竞态）。
     * T38（Bug A）：延迟一拍只赢过模拟器时序，T12 真机实测竞争窗口 >120ms——收起同时开启
     * [SHOW_GUARD_MS] 显示抑制窗（窗内宿主迟到的显示请求被拒绝 / 落窗即复位，
     * 见 onShowInputRequested / onWindowShown）。BACK 路径不开抑制窗：
     * 真机 BACK 收起已验证无此竞争，且 BACK 后快速 OK 唤起是正常使用流。
     */
    private fun hideSelfAfterKeyDispatch() {
        showGuard.onHideRequested(SystemClock.uptimeMillis())
        keyboardView?.postDelayed({ requestHideSelf(0) }, HIDE_DELAY_MS)
    }

    /**
     * T38（Bug A 第一层 · API 34）：宿主 showSoftInput 请求的裁决回调。收起抑制窗内
     * 直接拒绝（不落窗，无闪现）。双参签名是 compileSdk 34 口径；API 23 真机无此覆写点，
     * 由 onWindowShown 兜底层覆盖。
     */
    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        if (showGuard.shouldVetoShowRequest(SystemClock.uptimeMillis())) {
            trace("show request vetoed flags=$flags（收起后显示抑制窗内）")
            return false
        }
        return super.onShowInputRequested(flags, configChange)
    }

    /**
     * T38（Bug A 第二层 · 全 API 兜底 / Bug B 唤起抑制窗开启点）。
     * API 34 实测：会话更新（如键面提交后的 show 流程重跑）会重复回调本方法，
     * 并非只在「隐藏 → 显示」跳变沿——服务层自跟踪可见性（[windowShown]，onWindowHidden 复位），
     * 只在真实跳变沿触发守卫：开唤起确认键抑制窗 / 迟到显示兜底复位。
     */
    override fun onWindowShown() {
        super.onWindowShown()
        val now = SystemClock.uptimeMillis()
        val firstShow = !windowShown
        windowShown = true
        if (!firstShow) return
        val rehide = showGuard.shouldRehideOnShown(now)
        if (rehide) trace("onWindowShown within hide guard -> re-hide（收起后迟到显示兜底）")
        else trace("onWindowShown（arm wake guard）")
        showGuard.onWindowShown(now)
        if (rehide) requestHideSelf(0)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        windowShown = false
        cancelDeleteRepeat() // T22：键盘隐藏即解除连删（含收起路径，防游离删除）
    }

    // ---- 键面样式映射（全部走 Token，不在此散落值） ----

    private fun textColorOf(variant: KeyVariant): Int = when (variant) {
        KeyVariant.CHARACTER -> R.color.text_body
        KeyVariant.FUNCTION -> R.color.text_secondary
        // 完成键常态不高亮（T65 · 决策 46-①）：文字与普通键一致 #E8EEF5（青字废止，决策 47-②）
        KeyVariant.ACCENT -> R.color.text_body
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
