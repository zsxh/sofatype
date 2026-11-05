// T5 · 键盘悬浮层（FR-A2 静态布局，画板①「字母键盘·小写」）。
// 键帽由 KeyboardLayout 模型驱动程序化生成（键位结构单一事实源在模型，单测锁定）；
// T5 仅「下屏」动作生效（FR-A1 收起，不丢宿主内容）。
// 范围边界：焦点导航与按键触发属 T6；大小写/完整输入属 T7；符号键盘属 T8；
// 拼音与候选词属 T9/T10；副卡引导内容与状态机属 T17。
package com.sofatype.ime

import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class SofatypeImeService : InputMethodService() {

    /**
     * TV 恒横屏：默认的全屏提取模式会用自带编辑界面盖住宿主应用
     * （T2 测试输入框将不可见，验收链路走不通），恒关闭。
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val root = LayoutInflater.from(this).inflate(R.layout.ime_keyboard, null) as ViewGroup
        bindRows(root)
        bindDoneKey(root)
        return root
    }

    /** 行容器 id 与 KeyboardLayout.rows 一一对应（顺序即行序）。 */
    private val rowIds = intArrayOf(R.id.key_row_1, R.id.key_row_2, R.id.key_row_3, R.id.key_row_4)

    /** 依模型逐行生成键帽与列间隔（键位结构一致性由 KeyboardLayoutTest 锁定）。 */
    private fun bindRows(root: View) {
        check(KeyboardLayout.rows.size == rowIds.size) { "行容器数量与模型行数不一致" }
        val spacing = resources.getDimensionPixelSize(R.dimen.ime_key_spacing)
        val spacerWidth = resources.getDimensionPixelSize(R.dimen.ime_column_spacer)
        KeyboardLayout.rows.forEachIndexed { rowIndex, row ->
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
                }
            }
        }
    }

    /** 完成键：跨第 3、4 行的强调键，绝对定位挂载到键区（画板① 84×196 高键，FR-A10）。 */
    private fun bindDoneKey(root: View) {
        val keyArea = root.findViewById<FrameLayout>(R.id.key_area)
        val key = KeyboardLayout.doneKey
        val view = createKeyContentView(key)
        view.setOnClickListener { execute(key.action) }
        val width = resources.getDimensionPixelSize(R.dimen.ime_done_key_width)
        val height = resources.getDimensionPixelSize(R.dimen.ime_done_key_height)
        keyArea.addView(
            view,
            FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.START).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.ime_done_key_start)
                topMargin = resources.getDimensionPixelSize(R.dimen.ime_done_key_top)
            },
        )
    }

    /** 行内键帽包装：固定键帽尺寸，键间留键距（行尾不留）。 */
    private fun wrapForRow(key: Key, isRowEnd: Boolean, spacing: Int): View {
        val view = createKeyContentView(key)
        view.setOnClickListener { execute(key.action) }
        view.layoutParams = LinearLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.ime_key_width),
            resources.getDimensionPixelSize(R.dimen.ime_key_height),
        ).apply {
            if (!isRowEnd) marginEnd = spacing
        }
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
            text = key.label
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
            background = getDrawable(R.drawable.bg_key)
            // padding 限定图标绘制区 = 键帽中心 iconSize 见方（画板① 图标尺寸）
            setPadding((keyWidth - iconSize) / 2, (keyHeight - iconSize) / 2, (keyWidth - iconSize) / 2, (keyHeight - iconSize) / 2)
        }
    }

    /**
     * 执行按键动作。T5 静态布局仅「下屏」生效；其余动作留待 T6/T7/T8/T10 接线。
     * 无焦点连接时静默忽略，不崩溃（可靠性要求：输入法异常不得阻塞宿主应用）。
     */
    private fun execute(action: KeyAction) {
        when (action) {
            KeyAction.HideSelf -> requestHideSelf(0)
            is KeyAction.Text,
            KeyAction.ToggleCase,
            KeyAction.ToggleLanguage,
            KeyAction.OpenSymbols,
            KeyAction.Delete,
            KeyAction.CursorLeft,
            KeyAction.CursorRight,
            KeyAction.Space,
            KeyAction.Done,
            -> Unit
        }
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
}
