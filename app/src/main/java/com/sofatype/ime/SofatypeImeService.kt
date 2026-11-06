// T5 · 键盘悬浮层（FR-A2 静态布局，画板①「字母键盘·小写」）+ T6 · 遥控器焦点导航基座（FR-A1/A10/A11）
// + T7 · 英文模式完整输入（FR-A3 大小写切换 / FR-A9 特殊键）。
// 键帽由 KeyboardLayout 模型驱动程序化生成（键位结构单一事实源在模型，单测锁定）；
// T6 接线：DPAD 焦点导航（FocusNavigator 模型 + onKeyDown 层处理，见 onKeyDown 注释）、
// 初始焦点、OK 触发、系统返回键收起、「完成」键确认收起；「下屏」收起（T5 已有）。
// T7 接线：字母/数字/.com/@ 直出、空格、删除、光标移动、大小写切换与键面/态提示刷新。
// 范围边界：符号键盘属 T8；拼音与候选词属 T9/T10；副卡引导内容与状态机属 T17。
package com.sofatype.ime

import android.inputmethodservice.InputMethodService
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

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

    /** 跨行浮层键视图（空格/完成键，绝对定位挂键区）；布局切换时移除重建。 */
    private val overlayViews = mutableListOf<View>()

    /** 诊断日志：仅 debug 构建输出（T7 评审跟进项），release 静默；只记结构信息不含输入内容。 */
    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.d(TRACE_TAG, message)
    }

    override fun onCreateInputView(): View {
        keyViews.clear()
        overlayViews.clear()
        caseState = CaseState.LOWER
        keyboardMode = KeyboardMode.LETTERS
        symbolsWidth = SymbolsWidth.FULL_WIDTH
        navigator = FocusNavigator()
        val root = LayoutInflater.from(this).inflate(R.layout.ime_keyboard, null) as ViewGroup
        keyboardView = root
        rebuildKeys(root, requestInitialFocus = false)
        return root
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
        if (requestInitialFocus) {
            keyboardView?.post { keyViews[navigator.initialKey()]?.requestFocus() }
        }
    }

    /**
     * 键盘显示：初始焦点落到键区左上角第一键（FR-A11 焦点导航起点）。
     * post 到 attach 完成后执行；focusableInTouchMode 保证触摸模式下焦点机制同样可用
     * （模拟器 `input tap` 唤起后紧跟 `input keyevent` 走查的场景）。
     */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
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
                    val nextId = navigator.next(currentId, direction)
                    trace("focus $currentId $direction -> $nextId")
                    val target = nextId?.let { keyViews[it] }
                    if (target != null) target.requestFocus(View.FOCUS_DOWN)
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
     * 文本/切换类动作留待 T7/T8/T10 接线。
     * 无焦点连接时静默忽略，不崩溃（可靠性要求：输入法异常不得阻塞宿主应用）。
     */
    private fun execute(key: Key) {
        when (val action = key.action) {
            KeyAction.HideSelf -> hideSelfAfterKeyDispatch()
            KeyAction.Done -> {
                // FR-A10「确认当前输入并收起」：先结束可能存在的组合态（无组合时为无操作；
                // 组合态产生路径属 T9/T10，此处为语义插点），再收起
                currentInputConnection?.finishComposingText()
                hideSelfAfterKeyDispatch()
            }
            // FR-A9：字母/数字/.com/@ 直出；字母输出值随大小写状态（FR-A3），符号键盘直出符号（FR-A6）
            is KeyAction.Text -> {
                val text = if (keyboardMode == KeyboardMode.SYMBOLS) {
                    action.value
                } else {
                    KeyboardLayout.outputValue(key, caseState)
                }
                commitText(text)
            }
            // FR-A9：空格。中文组合态「有候选选首候选」语义属 T9/T10，此处直出空格（英文模式）
            KeyAction.Space -> commitText(" ")
            // FR-A9：删除（退格）；存在选区时删除选区（标准 IME 行为），否则删光标前一字符
            KeyAction.Delete -> deleteBackward()
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
            // 中/英切换属 T10 接线前静默忽略（可靠性：不崩溃不阻塞宿主）
            KeyAction.ToggleLanguage -> Unit
        }
    }

    /** 字母键盘 ↔ 符号键盘切换（FR-A5）：重建键区视图与导航格网，焦点落新布局初始键。 */
    private fun switchKeyboardMode(mode: KeyboardMode) {
        keyboardMode = mode
        trace("keyboardMode -> $mode")
        rebuildKeys(requireNotNull(keyboardView), requestInitialFocus = true)
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
