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
import timber.log.Timber

class SofatypeImeService : InputMethodService() {

    private companion object {

        const val HIDE_DELAY_MS = 120L

        const val SHOW_GUARD_MS = 800L

        const val WAKE_GUARD_MS = 500L

        const val TRACE_TAG = "ImeKeyTrace"

        const val CURSOR_PROBE_CHARS = 1000

        const val DICT_ASSET_PATH = "dict/pinyin_dict.bin"

        const val DICT_HIGH_FREQ_ASSET_PATH = "dict/pinyin_dict_highfreq.bin"

        const val CURSOR_ANCHOR = "\u00A0"

        const val CANDIDATE_QUERY_LIMIT = 24

        const val CANDIDATE_ID_PREFIX = "cand_"

        const val SHIFT_ICON_TAG = "shift_icon"

        const val SHIFT_BAR_TAG = "shift_bar"

        const val REMOTE_EXIT_TAG = "remote_exit"
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreate() {
        super.onCreate()
        trace("onCreate")

        pinyinEngineLoader.startPreload()
    }

    private val keyViews = mutableMapOf<String, View>()

    private var navigator = FocusNavigator(
        KeyboardLayout.rows(LanguageMode.CHINESE),
        initialCell = KeyboardLayout.initialCell,
    )

    private var keyboardView: View? = null

    private val showGuard = ImeShowGuard(SHOW_GUARD_MS, WAKE_GUARD_MS)

    private val remoteReentryFocusPolicy = RemoteReentryFocusPolicy()

    private var remoteExitFocusSuppressed = false

    private var windowShown = false

    private var candidateLoadStartMs = -1L

    private val deleteRepeatRhythm = DeleteRepeatRhythm()

    private val deleteRepeatHandler = Handler(Looper.getMainLooper())

    private var deleteRepeatJob: Runnable? = null

    private var deleteRepeatIndex = 0

    private var caseState = CaseState.LOWER

    private enum class KeyboardMode { LETTERS, SYMBOLS }

    private var keyboardMode = KeyboardMode.LETTERS

    private var symbolsWidth = SymbolsWidth.HALF_WIDTH

    private var symbolsWindowOffset = 0

    private var languageMode = LanguageMode.CHINESE

    private val composer = ChineseComposer(::queryPinyinCandidates)

    private val pinyinEngineLoader: PinyinEngineLoader by lazy {
        PinyinEngineLoader(
            openAsset = assets::open,
            fullAssetPath = DICT_ASSET_PATH,
            highFreqAssetPath = DICT_HIGH_FREQ_ASSET_PATH,
            onEngineReady = { scope, entryCount ->
                trace("pinyin engine ready scope=$scope entryCount=$entryCount")
            },
            onLoadFailure = { scope, error ->
                trace("pinyin engine load failed scope=$scope reason=${error.javaClass.simpleName}")
            },

            alwaysLog = { message -> Timber.tag(TRACE_TAG).w(message) },
        )
    }

    private var candPage = 0

    private var candPages: List<IntRange> = emptyList()

    private val candidateViews = mutableListOf<View>()

    private var candidateReturnKey: String? = null

    private var candidatesBar: LinearLayout? = null

    private var sideCard: FrameLayout? = null

    private var remoteModeArea: LinearLayout? = null

    private var remoteStatusDot: View? = null
    private var remoteStatusText: TextView? = null
    private var remoteStatusFrom: TextView? = null
    private var remoteStatusPill: LinearLayout? = null

    private var remoteExitButton: TextView? = null

    private var remoteBottomHint: TextView? = null

    private var guideButtonView: View? = null

    private var guideReturnKey: String? = null

    private var lastRenderedMode: RemoteMode? = null

    private var remoteTextScroll: ScrollView? = null

    private var remoteTextView: TextView? = null

    private var keyArea: FrameLayout? = null

    private val overlayViews = mutableListOf<View>()

    private fun trace(message: String) {
        if (BuildConfig.DEBUG || DebugLogStore.isEnabled) Timber.tag(TRACE_TAG).d(message)
    }

    override fun onCreateInputView(): View {
        keyViews.clear()
        overlayViews.clear()
        candidateViews.clear()
        candidateReturnKey = null

        keyboardMode = KeyboardMode.LETTERS
        symbolsWidth = SymbolsWidth.HALF_WIDTH
        symbolsWindowOffset = 0
        composer.reset()
        navigator = FocusNavigator(
            KeyboardLayout.rows(languageMode),
            initialCell = KeyboardLayout.initialCell,
        )

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

        remoteExitButton?.apply {
            tag = REMOTE_EXIT_TAG
            text = RemoteUiText.EXIT_BUTTON_LABEL
            contentDescription = RemoteUiText.EXIT_BUTTON_DESCRIPTION
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { exitRemoteMode() }
        }

        remoteBottomHint?.text = RemoteUiText.REMOTE_BOTTOM_HINT
        rebuildKeys(root, requestInitialFocus = false)

        renderRemoteUi()

        RemoteInputServerManager.onPairingStateChanged = { renderRemoteUi() }

        renderRemoteText(RemoteInputServerManager.remoteTextSnapshot())
        RemoteInputServerManager.onRemoteTextChanged = { renderRemoteText(it) }

        RemoteInputServerManager.onCommitRequested = { text -> commitRemoteText(text) }

        RemoteInputServerManager.onRemoteIdleExpired = {
            trace("remote idle expired -> hide keyboard")
            requestHideSelf(0)
        }
        return root
    }

    override fun onDestroy() {

        trace("onDestroy")
        deleteRepeatHandler.removeCallbacksAndMessages(null)
        RemoteInputServerManager.onPairingStateChanged = null
        RemoteInputServerManager.onRemoteTextChanged = null
        RemoteInputServerManager.onCommitRequested = null
        RemoteInputServerManager.onRemoteIdleExpired = null
        super.onDestroy()
    }

    private fun renderRemoteUi() {
        val state = RemoteUiState.of(
            serviceRunning = RemoteInputServerManager.isRunning(),
            pairedSessionActive = RemoteInputServerManager.hasPairedSession(),
            clientCode = RemoteInputServerManager.pairedClientCode(),
        )
        renderSideCard(state)
        renderMainCardMode(state)
    }

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

                    showGuideFace(container)
                }
            }
            RemoteSideCardFace.CONNECTED -> {
                val url = RemoteInputServerManager.pairingUrl()
                if (url != null) {

                    PairingCard.show(
                        this,
                        container,
                        url,
                        clientCode = state.clientCode,
                    )
                } else {

                    showGuideFace(container)
                }
            }
        }
        if (state.sideCardFace != RemoteSideCardFace.GUIDE) guideReturnKey = null
    }

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

    private fun showGuideFace(container: FrameLayout) {
        GuideCard.show(this, container) { enterRemoteMode() }
        guideButtonView = container.findViewWithTag<View>(GuideCard.BUTTON_TAG)
    }

    private fun renderMainCardMode(state: RemoteUiState) {
        val area = remoteModeArea ?: return
        val prevMode = lastRenderedMode

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

                    remoteStatusFrom?.text = RemoteUiText.CONNECTED_FROM_LABEL
                    remoteStatusFrom?.visibility = View.VISIBLE
                    val pill = remoteStatusPill
                    if (pill != null) {
                        ClientCodePill.show(this, pill, code)
                        pill.visibility = View.VISIBLE
                    }
                } else {

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

                restoreRemoteExitFocusability()
                keyboardView?.post { remoteExitButton?.requestFocus(View.FOCUS_DOWN) }
            }
        } else {
            area.visibility = View.GONE
            remoteBottomHint?.visibility = View.GONE
            if (prevMode == RemoteMode.REMOTE) {
                trace("remote mode -> KEYBOARD")

                remoteReentryFocusPolicy.reset()
                restoreRemoteExitFocusability()
                keyArea?.visibility = View.VISIBLE

                refreshCandidatesBar()
                keyboardView?.post { keyViews[navigator.initialKey()]?.requestFocus(View.FOCUS_DOWN) }
            }
        }
        lastRenderedMode = state.mode
    }

    private fun hideStatusFromAndPill() {
        remoteStatusFrom?.visibility = View.GONE
        remoteStatusPill?.let {
            it.removeAllViews()
            it.visibility = View.GONE
        }
    }

    private fun enterRemoteMode() {
        trace("remote enter requested")
        val result = RemoteInputServerManager.start(this)
        if (result is RemoteInputServer.StartResult.Failed) {

            trace("remote enter failed: ${result.reason}")
            Toast.makeText(this, R.string.remote_start_failed_hint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun exitRemoteMode() {
        trace("remote exit requested")
        RemoteInputServerManager.stop()
    }

    private fun renderRemoteText(text: String?) {
        val textView = remoteTextView ?: return
        if (text.isNullOrEmpty()) {
            textView.text = null
            return
        }

        val spannable = SpannableStringBuilder.valueOf(text)
        spannable.append(CURSOR_ANCHOR)
        spannable.setSpan(
            RemoteCursorSpan(
                widthPx = resources.getDimensionPixelSize(R.dimen.ime_remote_cursor_width),
                heightPx = resources.getDimensionPixelSize(R.dimen.ime_remote_cursor_height),
                color = getColor(R.color.accent),

                radiusPx = resources.getDimension(R.dimen.ime_remote_cursor_gap),
            ),
            spannable.length - 1,
            spannable.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        textView.text = spannable

        val scroll = remoteTextScroll ?: return
        scroll.post {
            val bottom = RemoteTextScrollTarget.bottomOffset(
                contentHeightPx = scroll.getChildAt(0)?.height ?: 0,
                viewportHeightPx = scroll.height,
            )
            if (bottom > 0) scroll.smoothScrollTo(0, bottom)
        }
    }

    private fun commitRemoteText(text: String): Boolean {
        val ic = currentInputConnection
        if (ic == null) {
            PerfTrace.endRemoteCommit(committed = false)
            return false
        }
        return try {
            ic.beginBatchEdit()
            try {
                ic.finishComposingText()
                composer.reset()
                ic.commitText(text, 1)
                PerfTrace.endRemoteCommit(committed = true)
                true
            } finally {
                ic.endBatchEdit()
            }
        } catch (t: Throwable) {
            PerfTrace.endRemoteCommit(committed = false)
            Timber.tag(TRACE_TAG).w("远程上屏写入失败：${t.javaClass.simpleName}")
            false
        }
    }

    private fun rebuildKeys(root: View, requestInitialFocus: Boolean) {

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

        root.invalidate()

        refreshCandidatesBar()
        if (requestInitialFocus) {
            keyboardView?.post { keyViews[navigator.initialKey()]?.requestFocus() }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composer.reset()
        candidateReturnKey = null

        trace("onStartInputView restarting=$restarting")
        if (RemoteInputServerManager.isRunning()) {

            when (remoteReentryFocusPolicy.resolveOnShow()) {
                RemoteReentryFocusPolicy.ReentryFocus.NONE ->

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

    private fun restoreRemoteExitFocusability() {
        if (!remoteExitFocusSuppressed) return
        remoteExitFocusSuppressed = false
        remoteExitButton?.apply {
            isFocusable = true
            isFocusableInTouchMode = true
        }
    }

    private fun suppressRemoteExitFocusability() {
        keyboardView?.findFocus()?.clearFocus()
        remoteExitFocusSuppressed = true
        remoteExitButton?.apply {
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val ev = event ?: return super.onKeyDown(keyCode, event)
        candidateLoadStartMs = SystemClock.uptimeMillis()
        PerfTrace.markKeyDown()
        try {
            val handled = dispatchKeyDown(keyCode, ev)
            if (handled) PerfTrace.endKeyFeedbackOnDraw(keyboardView)
            return handled
        } finally {
            candidateLoadStartMs = -1L
        }
    }

    private fun dispatchKeyDown(keyCode: Int, ev: KeyEvent): Boolean {

        trace("onKeyDown keyCode=$keyCode action=${ev.action} focus=${(keyboardView?.findFocus() as? View)?.tag}")

        if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
            cancelDeleteRepeat()
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {

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

                        trace("remote keep-alive hide via BACK")
                        remoteReentryFocusPolicy.onKeepAliveHide()

                        suppressRemoteExitFocusability()
                        requestHideSelf(0)
                        return true
                    }
                    RemoteBackPolicy.Action.KEYBOARD_DEFAULT -> Unit

                }
            }

            if (!isInputViewShown()) {
                return super.onKeyDown(keyCode, ev)
            }
            finishComposition()
            requestHideSelf(0)
            return true
        }
        val direction = DPAD_DIRECTIONS[keyCode]
        if (direction != null) {

            if (!isInputViewShown()) {
                return super.onKeyDown(keyCode, ev)
            }
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                val currentId = (keyboardView?.findFocus() as? View)?.tag as? String
                if (currentId != null) {

                    when {
                        currentId == GuideCard.BUTTON_TAG -> handleGuideButtonDirection(direction)
                        currentId.isCandidateId() -> handleCandidateRowDirection(currentId, direction)
                        else -> handleKeyboardDirection(currentId, direction)
                    }
                } else if (RemoteInputServerManager.isRunning()) {

                    restoreRemoteExitFocusability()
                    trace("focus null $direction -> remote_exit（保活重显无焦点态导航）")
                    remoteExitButton?.requestFocus(View.FOCUS_DOWN)
                }
            }
            return true
        }
        if (keyCode.isConfirmKey()) {

            if (!isInputViewShown() && !confirmWakeSessionActive()) {
                trace("onKeyDown keyCode=$keyCode 确认键隐藏态无输入会话 -> 透传宿主")
                return super.onKeyDown(keyCode, ev)
            }
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {

                if (!isInputViewShown()) {

                    restoreRemoteExitFocusability()
                    remoteReentryFocusPolicy.onExplicitWake()
                    trace("onKeyDown keyCode=$keyCode swallowed（输入窗隐藏态）-> requestShowSelf")

                    HiddenConfirmWake(
                        requestShowSelf = { requestShowSelf(0) },
                        vetoed = { showGuard.shouldVetoShowRequest(SystemClock.uptimeMillis()) },
                        showWindow = { showWindow(true) },
                        trace = ::trace,
                    ).wake()
                    return true
                }

                if (showGuard.shouldSwallowConfirmKey(SystemClock.uptimeMillis())) {
                    trace("onKeyDown keyCode=$keyCode swallowed（唤起转发副本抑制）")
                    return true
                }
                keyboardView?.findFocus()?.performClick()

                armDeleteRepeatForFocusedKey()
            }
            return true
        }

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

            val digitKey = KeyboardLayout.digitIdForKeyCode(keyCode)?.let { KeyboardLayout.keyById[it] }
            if (digitKey != null) {
                execute(digitKey)
                return true
            }
        }
        return super.onKeyDown(keyCode, ev)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && keyCode.isConfirmKey()) {

            if (!isInputViewShown() && !confirmWakeSessionActive()) {
                trace("onKeyUp keyCode=$keyCode 确认键隐藏态无输入会话 -> 透传宿主（UP 对称）")
                return super.onKeyUp(keyCode, event)
            }
            if (deleteRepeatJob != null) {
                trace("onKeyUp keyCode=$keyCode（连删解除）")
                cancelDeleteRepeat()
            }
            return true
        }
        if (event != null && KeyboardLayout.letterIdForKeyCode(keyCode) != null && isInputViewShown()) {

            return true
        }
        if (event != null && KeyboardLayout.digitIdForKeyCode(keyCode) != null && isInputViewShown()) {

            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun Int.isConfirmKey(): Boolean = this == KeyEvent.KEYCODE_DPAD_CENTER ||
        this == KeyEvent.KEYCODE_ENTER ||
        this == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun confirmWakeSessionActive(): Boolean {
        val info = currentInputEditorInfo ?: return false
        return getCurrentInputStarted() && info.inputType != EditorInfo.TYPE_NULL
    }

    private fun String.isCandidateId(): Boolean = startsWith(CANDIDATE_ID_PREFIX)

    private fun String.candidateIndex(): Int? = removePrefix(CANDIDATE_ID_PREFIX).toIntOrNull()

    private fun String.isCandidateLayerId(): Boolean = isCandidateId()

    private fun handleKeyboardDirection(currentId: String, direction: FocusDirection) {
        val onTopRow = navigator.positionsOf(currentId).any { it.first == 1 }
        if (direction == FocusDirection.UP && onTopRow && candidateViews.isNotEmpty()) {
            candidateReturnKey = currentId
            trace("focus $currentId UP -> cand_0（进入候选行）")
            candidateViews[0].requestFocus(View.FOCUS_DOWN)
            return
        }

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

    private fun canEnterGuideButton(currentId: String): Boolean =
        guideButtonView != null &&
            navigator.positionsOf(currentId).any { it.second == FocusNavigator.GRID_COLS }

    private fun stepSymbolsWindow(newOffset: Int, anchor: Pair<Int, Int>) {
        val previousOffset = symbolsWindowOffset
        symbolsWindowOffset = newOffset
        trace("symbols window $previousOffset -> $newOffset（${if (newOffset > previousOffset) "DOWN" else "UP"} 步进）")
        rebuildKeys(requireNotNull(keyboardView), requestInitialFocus = false)
        val targetId = navigator.keyAt(anchor.first, anchor.second) ?: navigator.initialKey()
        trace("focus step -> $targetId（屏上位置 (${anchor.first}, ${anchor.second}) 不动）")
        keyViews[targetId]?.requestFocus(View.FOCUS_DOWN)
    }

    private fun handleGuideButtonDirection(direction: FocusDirection) {
        if (direction != FocusDirection.LEFT) return
        val targetId = guideReturnKey?.takeIf { keyViews.containsKey(it) } ?: navigator.initialKey()
        trace("focus ${GuideCard.BUTTON_TAG} LEFT -> $targetId（回键盘）")
        keyViews[targetId]?.requestFocus(View.FOCUS_DOWN)
    }

    private fun handleCandidateRowDirection(currentId: String, direction: FocusDirection) {
        val index = currentId.candidateIndex() ?: return
        when (val move = CandidatePager.move(index, direction, candPages)) {
            is CandidatePagerMove.To -> {
                trace("focus $currentId $direction -> cand_${move.index}")
                focusCandidateChip(move.index)
            }
            is CandidatePagerMove.Flip -> {

                val landing = if (direction == FocusDirection.LEFT) "页尾" else "页首"
                trace("focus $currentId $direction -> page ${move.page}（翻页，落$landing cand_${move.index}）")
                flipCandidatePage(move.page, move.index)
            }
            CandidatePagerMove.ExitDown -> exitCandidatesLayerToKeyboard(currentId)
            CandidatePagerMove.Stay -> Unit
        }
    }

    private fun focusCandidateChip(globalIndex: Int) {
        val page = candPages.getOrNull(candPage) ?: return
        if (globalIndex !in page) return
        candidateViews.getOrNull(globalIndex - page.first)?.requestFocus(View.FOCUS_DOWN)
    }

    private fun flipCandidatePage(page: Int, focusGlobalIndex: Int) {
        val previous = candPage
        candPage = page
        renderCandidatesRow()
        trace("candidates page $previous -> $page")
        focusCandidateChip(focusGlobalIndex)
    }

    private fun resetCandidatePageState() {
        candPage = 0
        candPages = emptyList()
    }

    private fun exitCandidatesLayerToKeyboard(fromId: String) {
        val targetId = candidateReturnKey?.takeIf { keyViews.containsKey(it) }
            ?: navigator.initialKey()
        trace("focus $fromId DOWN -> $targetId（出候选区）")
        keyViews[targetId]?.requestFocus(View.FOCUS_DOWN)
    }

    private val rowIds = intArrayOf(R.id.key_row_1, R.id.key_row_2, R.id.key_row_3, R.id.key_row_4)

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

                            marginEnd = spacing
                        },
                    )

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

    private fun bindOverlayKeys(root: View) {
        val keyArea = root.findViewById<FrameLayout>(R.id.key_area)
        for (view in overlayViews) {
            keyArea.removeView(view)
        }
        overlayViews.clear()

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

    private fun createRowArrowView(drawableRes: Int): View = ImageView(this).apply {
        setImageResource(drawableRes)
        contentDescription = null
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        isFocusable = false
        isFocusableInTouchMode = false
    }

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

    private fun createKeyView(key: Key): View {
        val view = createKeyContentView(key)
        view.tag = key.id
        keyViews[key.id] = view

        if (key.stateOptions != null) applyStateKeyAppearance(view)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.setOnClickListener { execute(key) }
        return view
    }

    private fun createKeyContentView(key: Key): View {
        val view: View = when {
            key.stateOptions != null -> createStateKeyView(key)
            key.icon == KeyIcon.SHIFT -> createShiftKey(key)
            key.icon != null -> createIconKey(key)
            else -> createTextKey(key)
        }

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

        if (key.icon == KeyIcon.SHIFT) {
            view.contentDescription = KeyboardLayout.shiftContentDescription(caseState)
        }
        return view
    }

    private fun createTextKey(key: Key): TextView {
        val sizeRes = when (key.textSize) {
            KeyTextSize.SMALL -> R.dimen.ime_key_fn_text

            KeyTextSize.DENSE -> R.dimen.ime_key_dense_text
            KeyTextSize.NORMAL -> R.dimen.ime_key_text
        }
        return TextView(this).apply {

            text = KeyboardLayout.displayLabel(
                key,
                languageMode == LanguageMode.CHINESE,
                caseState == CaseState.UPPER,
            )
            gravity = Gravity.CENTER
            maxLines = 1

            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(sizeRes))
            setTextColor(getColor(textColorOf(key.variant)))
            background = getDrawable(backgroundOf(key.variant))
        }
    }

    private fun createStateKeyView(key: Key): View {
        val options = requireNotNull(key.stateOptions) { "状态键缺少两态字符：${key.id}" }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = getDrawable(backgroundOf(key.variant))
            setOnFocusChangeListener { v, _ -> applyStateKeyAppearance(v) }
        }
        val textPx = resources.getDimension(R.dimen.ime_key_fn_text)

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

        val upper = caseState == CaseState.UPPER
        icon.setColorFilter(getColor(if (upper) R.color.accent_text else R.color.text_secondary))
        bar.visibility = if (upper) View.VISIBLE else View.GONE
        return container
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

            setPadding((keyWidth - iconSize) / 2, (keyHeight - iconSize) / 2, (keyWidth - iconSize) / 2, (keyHeight - iconSize) / 2)
        }
    }

    private fun execute(key: Key) {
        when (val action = key.action) {

            KeyAction.HideSelf -> {
                finishComposition()
                hideSelfAfterKeyDispatch()
            }
            KeyAction.Done -> {

                finishComposition()
                hideSelfAfterKeyDispatch()
            }

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

                    if (languageMode == LanguageMode.CHINESE && composer.composition.isNotEmpty()) {
                        finishComposition()
                        refreshCandidatesBar()
                    }
                    commitText(KeyboardLayout.outputValue(key, caseState))
                }
            }

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

            KeyAction.Delete -> performDelete()

            KeyAction.CursorLeft -> moveCursor(-1)
            KeyAction.CursorRight -> moveCursor(1)

            KeyAction.Segment -> {
                if (composer.composition.isNotEmpty()) {
                    composer.appendSegment()
                    updateComposingText()
                    refreshCandidatesBar()
                } else {
                    trace("segment ignored（无组合态）")
                }
            }

            KeyAction.ToggleCase -> {
                caseState = if (caseState == CaseState.UPPER) CaseState.LOWER else CaseState.UPPER
                trace("case -> $caseState")
                applyCaseState()
            }

            KeyAction.OpenSymbols -> switchKeyboardMode(KeyboardMode.SYMBOLS)
            KeyAction.BackToLetters -> switchKeyboardMode(KeyboardMode.LETTERS)
            KeyAction.ToggleWidth -> {
                symbolsWidth =
                    if (symbolsWidth == SymbolsWidth.FULL_WIDTH) SymbolsWidth.HALF_WIDTH
                    else SymbolsWidth.FULL_WIDTH
                trace("symbolsWidth -> $symbolsWidth")

                rebuildKeys(requireNotNull(keyboardView), requestInitialFocus = false)

                keyViews[SymbolsLayout.TOGGLE_WIDTH_ID]?.requestFocus(View.FOCUS_DOWN)
            }

            KeyAction.ToggleLanguage -> {
                cancelComposition()
                languageMode =
                    if (languageMode == LanguageMode.CHINESE) LanguageMode.ENGLISH else LanguageMode.CHINESE
                if (languageMode == LanguageMode.ENGLISH) caseState = CaseState.LOWER
                trace("language -> $languageMode case -> $caseState")
                rebuildKeys(requireNotNull(keyboardView), requestInitialFocus = false)

                keyViews["lang"]?.requestFocus(View.FOCUS_DOWN)
            }
        }
    }

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

    private fun updateComposingText() {
        val ic = currentInputConnection ?: return
        ic.setComposingText(composer.composition, 1)
    }

    private fun selectCandidate(word: String) {

        val focusTag = (keyboardView?.findFocus() as? View)?.tag as? String
        composer.reset()
        commitText(word)
        refreshCandidatesBar()
        if (focusTag?.isCandidateLayerId() == true) {
            refocusKeyboardAfterBarHidden()
        }
    }

    private fun queryPinyinCandidates(input: String): List<String> {
        val engine = pinyinEngineLoader.engineForQuery() ?: return emptyList()
        return engine.query(input, CANDIDATE_QUERY_LIMIT).map { it.word }
    }

    private fun refreshCandidatesBar() {
        val bar = candidatesBar ?: return
        bar.visibility = View.VISIBLE

        val loadCandidates = keyboardMode == KeyboardMode.LETTERS && composer.hasCandidates
        if (loadCandidates) {
            bindCandidates()
        } else {
            bar.removeAllViews()
            candidateViews.clear()
            resetCandidatePageState()

            if (composer.composition.isNotEmpty()) {
                traceCandidateLoadCompleted(totalCount = 0, pageCount = 0)
            }
        }
    }

    private fun bindCandidates() {
        resetCandidatePageState()
        val candidates = composer.candidates
        if (candidates.isEmpty()) return
        val gap = resources.getDimensionPixelSize(R.dimen.ime_chip_gap)

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

        traceCandidateLoadCompleted(candidates.size, candPages.size)
    }

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

                        if (index != page.last) marginEnd = gap
                    },
                )
            }
        }

        bar.addView(
            View(this),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f),
        )
        bar.addView(createCandArrowView())
        refreshCandArrowAppearance()
    }

    private fun candidateArrowReservedPx(): Int =
        2 * (resources.getDimensionPixelSize(R.dimen.ime_cand_arrow_width) +
            resources.getDimensionPixelSize(R.dimen.ime_cand_arrow_gap))

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

    private fun barLayoutParamsForArrow(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.ime_cand_arrow_width),
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply { marginEnd = resources.getDimensionPixelSize(R.dimen.ime_cand_arrow_gap) }

    private fun refreshCandArrowAppearance() {
        val bar = candidatesBar ?: return
        val canPageBack = candPage > 0
        val canPageForward = candPage < candPages.size - 1

        val left = bar.getChildAt(0) as? TextView ?: return
        val right = bar.getChildAt(bar.childCount - 1) as? TextView ?: return
        left.text = "<"
        right.text = ">"
        left.setTextColor(getColor(if (canPageBack) R.color.text_body else R.color.text_hint_faint))
        right.setTextColor(getColor(if (canPageForward) R.color.text_body else R.color.text_hint_faint))
    }

    private fun traceCandidateLoadCompleted(totalCount: Int, pageCount: Int) {
        val startMs = candidateLoadStartMs
        candidateLoadStartMs = -1L
        val elapsedMs = if (startMs > 0) SystemClock.uptimeMillis() - startMs else -1L
        PerfTrace.endCandidate(totalCount, pageCount)
        trace(
            "candidates loaded fromKey=${startMs > 0} elapsedMs=$elapsedMs" +
                " availablePx=${candidatesAvailablePx()} pages=$pageCount" +
                " total=$totalCount",
        )
    }

    private fun createChipView(word: String, tag: String): TextView {
        val view = TextView(this).apply {
            text = word

            contentDescription = word
            gravity = Gravity.CENTER
            maxLines = 1

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

    private fun candidatesAvailablePx(): Int {
        val bar = candidatesBar
        if (bar != null && bar.width > 0) {
            return bar.width
        }
        return resources.getDimensionPixelSize(R.dimen.ime_key_zone_width)
    }

    private fun applyChipAppearance(view: View) {
        if (view.isFocused) {
            view.background = getDrawable(R.drawable.bg_chip_highlight)
            (view as? TextView)?.setTextColor(getColor(R.color.text_body))
        } else {
            view.background = null
            (view as? TextView)?.setTextColor(getColor(R.color.text_body))
        }
    }

    private fun refocusKeyboardAfterBarHidden() {
        val targetId = candidateReturnKey?.takeIf { keyViews.containsKey(it) }
            ?: navigator.initialKey()
        candidateReturnKey = null
        keyboardView?.post { keyViews[targetId]?.requestFocus(View.FOCUS_DOWN) }
    }

    private fun commitText(text: String?) {
        if (text.isNullOrEmpty()) return
        currentInputConnection?.commitText(text, 1)
    }

    private fun deleteBackward() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.commitText("", 1)
        }
    }

    private fun performDelete() {
        if (composer.composition.isNotEmpty()) {
            composer.deleteLastLetter()
            updateComposingText()
            refreshCandidatesBar()
        } else {
            deleteBackward()
        }
    }

    private fun armDeleteRepeatForFocusedKey() {
        val focusId = (keyboardView?.findFocus() as? View)?.tag as? String ?: return
        val focusedKey = when (keyboardMode) {
            KeyboardMode.LETTERS -> KeyboardLayout.keyById[focusId]

            KeyboardMode.SYMBOLS -> SymbolsLayout.allKeys(symbolsWidth, symbolsWindowOffset)
                .firstOrNull { it.id == focusId }
        }
        if (focusedKey?.action != KeyAction.Delete) return
        deleteRepeatIndex = 0
        val job = object : Runnable {
            override fun run() {
                if (deleteRepeatJob !== this) return
                performDelete()
                deleteRepeatIndex += 1
                trace("delete repeat #$deleteRepeatIndex")
                deleteRepeatHandler.postDelayed(this, deleteRepeatRhythm.delayBefore(deleteRepeatIndex))
            }
        }
        deleteRepeatJob = job
        deleteRepeatHandler.postDelayed(job, deleteRepeatRhythm.delayBefore(0))
    }

    private fun cancelDeleteRepeat() {
        deleteRepeatJob?.let { deleteRepeatHandler.removeCallbacks(it) }
        deleteRepeatJob = null
        deleteRepeatIndex = 0
    }

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

                val beforeLength = ic.getTextBeforeCursor(CURSOR_PROBE_CHARS, 0)?.length ?: return
                val target = (beforeLength + delta).coerceIn(0, beforeLength)
                ic.setSelection(target, target)
            }
        } finally {
            ic.endBatchEdit()
        }
    }

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
                        view.isFocused -> R.color.text_body
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

    private fun applyStateKeyAppearance() {
        keyViews["lang"]?.let { applyStateKeyAppearance(it) }
    }

    private fun stateCharTag(index: Int): String = "state_char_$index"

    private fun stateBarTag(index: Int): String = "state_bar_$index"

    private fun hideSelfAfterKeyDispatch() {
        showGuard.onHideRequested(SystemClock.uptimeMillis())
        keyboardView?.postDelayed({ requestHideSelf(0) }, HIDE_DELAY_MS)
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean): Boolean {
        if (showGuard.shouldVetoShowRequest(SystemClock.uptimeMillis())) {
            trace("show request vetoed flags=$flags（收起后显示抑制窗内）")
            return false
        }
        return super.onShowInputRequested(flags, configChange)
    }

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
        cancelDeleteRepeat()
    }

    private fun textColorOf(variant: KeyVariant): Int = when (variant) {
        KeyVariant.CHARACTER -> R.color.text_body
        KeyVariant.FUNCTION -> R.color.text_secondary

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

    private val DPAD_DIRECTIONS = mapOf(
        KeyEvent.KEYCODE_DPAD_LEFT to FocusDirection.LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT to FocusDirection.RIGHT,
        KeyEvent.KEYCODE_DPAD_UP to FocusDirection.UP,
        KeyEvent.KEYCODE_DPAD_DOWN to FocusDirection.DOWN,
    )
}
