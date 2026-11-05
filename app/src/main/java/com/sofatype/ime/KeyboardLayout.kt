// T5 · 字母键盘（小写）键位模型（画板①「字母键盘·小写」，FR-A2）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（TASKS T5 施工口径）。
// 范围边界：焦点导航与按键触发属 T6；大小写/完整输入属 T7；符号键盘属 T8；
// 拼音与候选词属 T9/T10。本模型只描述画板①的静态键位结构与键面语义。
package com.sofatype.ime

/** 键帽视觉变体（PRD 6.3 / 画板①）：普通字符键 / 功能键 / 强调键（完成键）。 */
enum class KeyVariant {
    /** 普通字符键：key 底 + 键帽字符色。 */
    CHARACTER,

    /** 功能键：key 底 + 功能键文字色（画板① .bkey-fn）。 */
    FUNCTION,

    /** 强调键（完成键）：accent-wash 底 + 主色描边 + 描边文字色（PRD 6.3）。 */
    ACCENT,
}

/** 键面文字规格（PRD 6.3：键帽字符 30px / 功能键文字 22px；1080p ÷ 2 = sp Token）。 */
enum class KeyTextSize {
    /** 键帽字符字号（画板① .bkey 30px）。 */
    NORMAL,

    /** 功能键文字字号（画板① 22px：功能键与「空格」键面）。 */
    SMALL,
}

/** 图标键图标集（画板① 内联 SVG 图标）。 */
enum class KeyIcon { SHIFT, HIDE, BACKSPACE, CURSOR_LEFT, CURSOR_RIGHT }

/**
 * 按键动作语义（对应设计稿 aria-label 标注集）。
 * T5 静态布局仅 [HideSelf] 生效（FR-A1 收起键盘）；
 * 其余动作由 T6（焦点导航/完成键）、T7（文本输入）、T8（符号键盘）、T10（中/英）接线。
 */
sealed class KeyAction {
    /** 直出文本：字母、数字、`.com`、`@`。 */
    data class Text(val value: String) : KeyAction()

    /** 上档切换（FR-A3，T7）。 */
    object ToggleCase : KeyAction()

    /** 下屏：收起键盘（FR-A1，T5 生效）。 */
    object HideSelf : KeyAction()

    /** 中/英切换（FR-A4，T10）。 */
    object ToggleLanguage : KeyAction()

    /** 进入符号键盘（FR-A5，T8）。 */
    object OpenSymbols : KeyAction()

    /** 删除（FR-A9，T7）。 */
    object Delete : KeyAction()

    /** 光标左移（FR-A9，T7）。 */
    object CursorLeft : KeyAction()

    /** 光标右移（FR-A9，T7）。 */
    object CursorRight : KeyAction()

    /** 空格（中文组合态行为见 PRD 决策 8，T9/T10 接线）。 */
    object Space : KeyAction()

    /** 完成：确认当前输入并收起（FR-A10，T6）。 */
    object Done : KeyAction()
}

/** 单个键位定义。 */
data class Key(
    /** 稳定标识：单测与后续任务定位用，全布局唯一。 */
    val id: String,
    /** 按键动作语义。 */
    val action: KeyAction,
    /** 视觉变体。 */
    val variant: KeyVariant = KeyVariant.CHARACTER,
    /** 键面文字；图标键为 null。 */
    val label: String? = null,
    /** 图标；文字键为 null。 */
    val icon: KeyIcon? = null,
    /** 键面文字规格。 */
    val textSize: KeyTextSize = KeyTextSize.NORMAL,
    /**
     * 无障碍标注（PRD 第 8 章，设计稿 aria-label）。
     * 图标键必填；文字键为 null（TalkBack 朗读键面文字）。
     */
    val contentDescription: String? = null,
)

/** 行内格子：键位或列间隔占位（画板① kspacer 42px）。 */
sealed interface Cell {
    /** 一个键位。 */
    data class KeyCell(val key: Key) : Cell

    /** 列间隔占位：数字列｜字母区｜功能键区之间的空隙。 */
    object Spacer : Cell
}

/** 一行键位：cells 依序水平排布。 */
data class KeyRow(val cells: List<Cell>)

/** 字母键盘（小写）完整键位（画板①）。键帽视图由本模型驱动生成。 */
object KeyboardLayout {

    /**
     * 4 行键区（FR-A2：左侧数字列、中部字母区、右侧功能键区；键间与列间排布见画板①）。
     * 行 3/4 右侧功能键区由跨两行完成键覆盖（[doneKey]，FR-A10）。
     */
    val rows: List<KeyRow> = listOf(
        // 行 1：数字 1-3 ｜ a-g ｜ 上档、下屏
        KeyRow(
            digitCells('1', '3') + Cell.Spacer + letterCells('a', 'g') + Cell.Spacer +
                listOf(
                    iconCell("shift", KeyIcon.SHIFT, "上档切换"),
                    iconCell("hide", KeyIcon.HIDE, "下屏，收起键盘"),
                ),
        ),
        // 行 2：数字 4-6 ｜ h-n ｜ 中/英、删除
        KeyRow(
            digitCells('4', '6') + Cell.Spacer + letterCells('h', 'n') + Cell.Spacer +
                listOf(
                    fnCell("lang", "中/英", KeyAction.ToggleLanguage),
                    iconCell("backspace", KeyIcon.BACKSPACE, "删除"),
                ),
        ),
        // 行 3：数字 7-9 ｜ o-u ｜ 123_!（右侧为完成键上半段）
        KeyRow(
            digitCells('7', '9') + Cell.Spacer + letterCells('o', 'u') + Cell.Spacer +
                listOf(fnCell("symbols", "123_!", KeyAction.OpenSymbols)),
        ),
        // 行 4：.com、0、@ ｜ v-z、光标左右 ｜ 空格（右侧为完成键下半段）
        // `.com` 为功能键样式（画板① .bkey-fn：22px / 功能键文字色，PRD 6.3）
        KeyRow(
            listOf(
                fnCell("dot_com", ".com", KeyAction.Text(".com")),
                charCell("digit_0", "0"),
                charCell("at", "@"),
                Cell.Spacer,
            ) + letterCells('v', 'z') +
                listOf(
                    iconCell("cursor_left", KeyIcon.CURSOR_LEFT, "光标左移"),
                    iconCell("cursor_right", KeyIcon.CURSOR_RIGHT, "光标右移"),
                    Cell.Spacer,
                    Cell.KeyCell(
                        Key(
                            id = "space",
                            action = KeyAction.Space,
                            // 画板① 空格：普通键帽底色 + 功能键字号
                            label = "空格",
                            textSize = KeyTextSize.SMALL,
                        ),
                    ),
                ),
        ),
    )

    /** 完成键：跨第 3、4 行的强调键（FR-A10，画板① 84×196 高键、accent-wash 底）。 */
    val doneKey: Key = Key(
        id = "done",
        action = KeyAction.Done,
        variant = KeyVariant.ACCENT,
        label = "完成",
    )

    /** 全部键位（含完成键）；单测与遍历辅助。 */
    fun allKeys(): List<Key> =
        rows.flatMap { row -> row.cells.filterIsInstance<Cell.KeyCell>().map { it.key } } + doneKey

    // ---- 键位构建辅助（保持键位表紧凑；均返回 Cell） ----

    /** 字符键（普通变体）。 */
    private fun charCell(id: String, label: String): Cell =
        Cell.KeyCell(Key(id = id, action = KeyAction.Text(label), label = label))

    /** 数字列连续段（'1'..'3' 等）。 */
    private fun digitCells(from: Char, to: Char): List<Cell> = (from..to).map { charCell("digit_$it", it.toString()) }

    /** 字母区连续段。 */
    private fun letterCells(from: Char, to: Char): List<Cell> = (from..to).map { charCell(it.toString(), it.toString()) }

    /** 功能键（文字键面，小字号规格）。 */
    private fun fnCell(id: String, label: String, action: KeyAction): Cell = Cell.KeyCell(
        Key(
            id = id,
            action = action,
            variant = KeyVariant.FUNCTION,
            label = label,
            textSize = KeyTextSize.SMALL,
        ),
    )

    /** 图标键（功能变体，必带无障碍标注）。 */
    private fun iconCell(id: String, icon: KeyIcon, description: String): Cell = Cell.KeyCell(
        Key(
            id = id,
            action = actionOfIcon(icon),
            variant = KeyVariant.FUNCTION,
            icon = icon,
            textSize = KeyTextSize.SMALL,
            contentDescription = description,
        ),
    )

    /** 图标 → 动作语义映射（唯一决策点，单测锁定）。 */
    private fun actionOfIcon(icon: KeyIcon): KeyAction = when (icon) {
        KeyIcon.SHIFT -> KeyAction.ToggleCase
        KeyIcon.HIDE -> KeyAction.HideSelf
        KeyIcon.BACKSPACE -> KeyAction.Delete
        KeyIcon.CURSOR_LEFT -> KeyAction.CursorLeft
        KeyIcon.CURSOR_RIGHT -> KeyAction.CursorRight
    }
}
