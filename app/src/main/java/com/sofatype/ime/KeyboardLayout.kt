// T5 · 字母键盘键位模型（designs/20261110 画板 1「键盘 · 默认态」，FR-A2）。
// T7 · 大小写状态语义（FR-A3）与文本输出值（FR-A9）。
// T65 · 决策 46：默认态英文输入 + 大写、[完成] 键常态不高亮、[中/英] 状态键方案 C 当前态指示。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（TASKS T5 施工口径）。
// 范围边界：符号键盘属 T8；拼音与候选词属 T9/T10。本模型只描述画板①键位结构、键面语义
// 与大小写相关的键面/输出变换；Android 侧视图生成与事件处理在 SofatypeImeService。
package com.sofatype.ime

/**
 * 大小写状态（FR-A3，T7）。上档键在两态间切换；⇧ 触发态是唯一大小写指示（决策 48-②）。
 * 默认大写（T65 · 决策 48-①）；键盘记忆属 FR-A13（P2）不做，每次键盘视图创建复位默认态。
 */
enum class CaseState { LOWER, UPPER }

/** 键帽视觉变体（PRD 6.3 / 画板①）：普通字符键 / 功能键 / 强调键（完成键）。 */
enum class KeyVariant {
    /** 普通字符键：key 底 + 键帽字符色。 */
    CHARACTER,

    /** 功能键：key 底 + 功能键文字色（画板① .bkey-fn）。 */
    FUNCTION,

    /**
     * 强调键（完成键）：T65 · 决策 46-① 常态不高亮（键帽同款 key 底、文字 #E8EEF5），
     * 焦点态 = accent-wash 上界底 + border-accent 描边（决策 50-②，bg_key_done 承载）。
     */
    ACCENT,
}

/** 键面文字规格（PRD 6.3：键帽字符 30px / 功能键文字 22px；1080p ÷ 2 = sp Token）。 */
enum class KeyTextSize {
    /** 键帽字符字号（画板① .bkey 30px）。 */
    NORMAL,

    /** 功能键文字字号（画板① 22px：功能键与「空格」键面）。 */
    SMALL,

    /** 密排字符字号（T64 · 画板 4 半角「...」「--」多字符键面 24px）。 */
    DENSE,
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

    /** 全/半角切换（FR-A6，T8，仅符号键盘）。 */
    object ToggleWidth : KeyAction()

    /** 返回字母键盘（FR-A5，T8，仅符号键盘）。 */
    object BackToLetters : KeyAction()

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
     * 无障碍标注（PRD 第 8 章，T26 全键位口径）。
     * 全部键位必填：图标键与功能键为动作语义描述（画板 aria-label）；
     * 字符键 = 键面文字（TalkBack 朗读同文）。字母键标注随键面恒显大写形态
     *（T65 · 决策 48-②），视图层经 [displayLabel] 统一生成，
     * [KeyboardLayout.langContentDescription] / [SymbolsLayout.widthContentDescription] 提供
     *（T65 · 决策 46-②：含当前态 + 动作）。
     */
    val contentDescription: String,

    /**
     * 状态键两态字符（T65 · 决策 46-② 方案 C）：first / second 按「中/英」「全/半」斜杠排版，
     * 当前态字符亮青加粗 + 下划线、另一态压暗，指示随切换实时随动；null = 普通文字键。
     * 当前态下标由视图层依服务状态（语言模式 / 全半角）判定。
     */
    val stateOptions: Pair<String, String>? = null,
)

/** 行内格子：键位或列间隔占位（画板① kspacer 42px）。 */
sealed interface Cell {
    /** 一个键位。 */
    data class KeyCell(val key: Key) : Cell

    /** 列间隔占位：数字列｜字母区｜功能键区之间的空隙。 */
    object Spacer : Cell

    /** 空格位：占一个键格但无键（布局缺键位，x 步进与键一致；T8 符号键盘半角行 4；
     *  焦点导航左右越空直达最近实键，T58 语义，见 FocusNavigator.next）。 */
    object EmptyCell : Cell
}

/** 一行键位：cells 依序水平排布。 */
data class KeyRow(val cells: List<Cell>)

/** 字母键盘完整键位（designs/20261110 画板 1「键盘 · 默认态」）。键帽视图由本模型驱动生成。 */
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
                    Cell.KeyCell(
                        Key(
                            id = "lang",
                            action = KeyAction.ToggleLanguage,
                            variant = KeyVariant.FUNCTION,
                            label = "中/英",
                            textSize = KeyTextSize.SMALL,
                            contentDescription = "中英文切换",
                            // T65 · 决策 46-② 方案 C：斜杠排版显示当前态（当前态由视图层依语言模式判定）
                            stateOptions = "中" to "英",
                        ),
                    ),
                    iconCell("backspace", KeyIcon.BACKSPACE, "删除"),
                ),
        ),
        // 行 3：数字 7-9 ｜ o-u ｜ 123_!（右侧为完成键上半段）
        KeyRow(
            digitCells('7', '9') + Cell.Spacer + letterCells('o', 'u') + Cell.Spacer +
                listOf(fnCell("symbols", "123_!", KeyAction.OpenSymbols, "进入符号键盘")),
        ),
        // 行 4：.com、0、@ ｜ v-z、光标左右 ｜ 空格（右侧为完成键下半段）
        // `.com` 为功能键样式（画板① .bkey-fn：22px / 功能键文字色，PRD 6.3）
        KeyRow(
            listOf(
                fnCell("dot_com", ".com", KeyAction.Text(".com"), ".com"),
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
                            contentDescription = "空格",
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
        contentDescription = "完成",
    )

    /**
     * 完成键格位（FR-A10 跨行 3/4）：列 = 行 1 图标键（下屏）所在列
     * （T28「完成键与图标列左缘对齐」口径），供焦点导航格网使用。
     */
    val doneKeyCells: List<Pair<Int, Int>> = run {
        var index = 0
        var hideCol = -1
        for (cell in rows[0].cells) {
            if (cell is Cell.KeyCell) {
                index += 1
                if (cell.key.id == "hide") hideCol = index
            }
        }
        check(hideCol > 0) { "行 1 未找到下屏键，无法推导完成键列" }
        listOf(3 to hideCol, 4 to hideCol)
    }

    /**
     * 键盘显示时的初始焦点格位（T40 户主拍板 2026-11-08，FR-A11）：字母区首键 [a]。
     * 格位 (1, 4) = 行 1 数字列 1-3（col1-3）+ 列间隔后的字母区首键；弹出初始焦点
     * 直落字母便于直接开始打字。符号键盘初始键维持键区左上角 digit_1（SymbolsLayout.initialCell，
     * T40 范围口径：符号键盘无字母键，不联动）。与 [rows] 的键位结构一致性由 FocusNavigatorTest 锁定。
     */
    val initialCell: Pair<Int, Int> = 1 to 4

    /** 全部键位（含完成键）；单测与遍历辅助。 */
    fun allKeys(): List<Key> =
        rows.flatMap { row -> row.cells.filterIsInstance<Cell.KeyCell>().map { it.key } } + doneKey

    /** 键 id → 键位；视图刷新与输出值计算用。 */
    val keyById: Map<String, Key> = allKeys().associateBy { it.id }

    /** 字母键 id 集合（单字母 Text 动作键）；大小写切换只作用于这批键（FR-A3）。 */
    val letterKeyIds: Set<String> =
        allKeys().filter { key -> isSingleLetter(key.action) }.map { it.id }.toSet()

    /**
     * 硬件字母键码 → 字母键 id（T45 · FR-A8 统一口径）：KEYCODE_A(29)..KEYCODE_Z(54) 映射
     * "a".."z"，与 [letterKeyIds] 的单字母 id 同名；非字母键码返回 null（数字/符号/功能键码
     * 不在此映射，行为不变）。键码用字面量保持本模型无 Android 依赖（KEYCODE_A=29 / KEYCODE_Z=54
     * 为 android.view.KeyEvent 自 API 1 起的稳定常量）。服务层据此把硬件字母键事件接入与
     * 网格 OK 相同的组合路径——框架默认直写会绕过组合态，表现为中文模式字母直落不进组
     * （真机 2026-11-09 实证，T27 缺陷 B）。
     */
    fun letterIdForKeyCode(keyCode: Int): String? {
        if (keyCode < 29 || keyCode > 54) return null
        return ('a' + (keyCode - 29)).toString()
    }

    /**
     * 键面文字（T65 · 决策 48-②）：字母键恒显大写形态——中 / 英两模式键面一律大写，
     * 键面不随大小写输出状态或输入模式变化，大小写输出仅由 ⇧ 触发态表达；非字母键键面原样。
     */
    fun displayLabel(key: Key): String? = when {
        key.id in letterKeyIds -> key.label?.uppercase()
        else -> key.label
    }

    /**
     * 大写态输出值（FR-A9 直出文本 × FR-A3）：字母键 Text 动作输出大写字符，
     * 数字、`.com`、`@` 不受大小写影响。非文本键返回 null。
     */
    fun outputValue(key: Key, case: CaseState): String? {
        val value = (key.action as? KeyAction.Text)?.value ?: return null
        return if (case == CaseState.UPPER && key.id in letterKeyIds) value.uppercase() else value
    }

    /**
     * 上档键态提示（T65 · 决策 47-① 触发态统一 / 决策 48-① ⇧ 触发态为唯一大小写指示）：
     * contentDescription 含当前态 + 动作。
     */
    fun shiftContentDescription(case: CaseState): String = when (case) {
        CaseState.LOWER -> "上档切换，当前小写"
        CaseState.UPPER -> "上档切换，当前大写"
    }

    /**
     * [中/英] 状态键态提示（T65 · 决策 46-② 方案 C：含当前态 + 动作，指示随切换实时随动）。
     */
    fun langContentDescription(chinese: Boolean): String =
        if (chinese) "中英文切换，当前为中文" else "中英文切换，当前为英文"

    // ---- 键位构建辅助（保持键位表紧凑；均返回 Cell） ----

    /** 单字母直出文本动作（字母键判定，FR-A3 变换范围）。 */
    private fun isSingleLetter(action: KeyAction): Boolean {
        val value = (action as? KeyAction.Text)?.value ?: return false
        return value.length == 1 && value[0] in 'a'..'z'
    }

    /**
     * 字符键（普通变体）：无障碍标注默认 = 键面文字（T26 全键位口径；
     * 字母键大写态由视图层随键面同步，模型侧存小写基准）。
     */
    private fun charCell(id: String, label: String, description: String = label): Cell =
        Cell.KeyCell(Key(id = id, action = KeyAction.Text(label), label = label, contentDescription = description))

    /** 数字列连续段（'1'..'3' 等）。 */
    private fun digitCells(from: Char, to: Char): List<Cell> = (from..to).map { charCell("digit_$it", it.toString()) }

    /** 字母区连续段。 */
    private fun letterCells(from: Char, to: Char): List<Cell> = (from..to).map { charCell(it.toString(), it.toString()) }

    /** 功能键（文字键面，小字号规格）；[description] = 无障碍动作语义描述。 */
    private fun fnCell(id: String, label: String, action: KeyAction, description: String): Cell = Cell.KeyCell(
        Key(
            id = id,
            action = action,
            variant = KeyVariant.FUNCTION,
            label = label,
            textSize = KeyTextSize.SMALL,
            contentDescription = description,
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
