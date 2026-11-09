// T8 · 符号键盘键位模型（画板③「符号·全角」④「符号·半角」+ 决策 3 / 决策 27）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（TASKS T5 施工口径，同 KeyboardLayout）。
// 结构：4 行 12 列格网，与字母键盘同构（col1-3 数字列、col4-10 符号区、col11/12 功能区）。
// 决策 3：「返回」键位于第二行（col11）；空格为跨两行高键（col11 行 3/4）、与完成键（col12 行 3/4）对称。
// 决策 27：符号键盘不含 .com 键与 @ 键（字母键盘已直出）；全角 30 符号键位微调如下——
//   行 3 放 ；：‘’""，（7 个），。／＊「」《》｜＼（9 个）与 0 排入行 4（0 col1 数字列贯通）；
//   半角 28 符号按画板④原样（行 3 ;:'",. /、行 4 *[]{}|\），行 4 col2/3 为空格位。
// 无大写键、无中/英键（FR-A5 评审决策）。
package com.sofatype.ime

/** 符号键盘全/半角状态（FR-A6）。会话内切换即时生效；记忆属 FR-A13（P2）不做。 */
enum class SymbolsWidth { FULL_WIDTH, HALF_WIDTH }

/** 符号键盘（全角/半角）键位模型。键帽视图由本模型驱动生成（同 KeyboardLayout）。 */
object SymbolsLayout {

    /** 全/半键 id；态提示随当前宽度切换。 */
    const val TOGGLE_WIDTH_ID = "toggle_width"

    /** 返回键 id（FR-A5：回到字母键盘）。 */
    const val BACK_ID = "back"

    /** 空格键 id（画板③④ 跨两行高键，决策 3）。 */
    const val SPACE_ID = "space"

    /** 完成键 id（跨两行高键，与空格对称，决策 3）。 */
    const val DONE_ID = "done"

    /** 空格键格位（col11 跨行 3/4，画板③ ABS 1028,208 84×196）。 */
    val spaceKeyCells: List<Pair<Int, Int>> = listOf(3 to 11, 4 to 11)

    /** 完成键格位（col12 跨行 3/4，画板③ ABS 1120,208 84×196）。 */
    val doneKeyCells: List<Pair<Int, Int>> = listOf(3 to 12, 4 to 12)

    /** 焦点导航浮层格位（空格/完成键跨行 3/4，供 FocusNavigator 构造）。 */
    fun overlayCells(): Map<String, List<Pair<Int, Int>>> = mapOf(
        SPACE_ID to spaceKeyCells,
        DONE_ID to doneKeyCells,
    )

    /** 键盘显示时的初始焦点格位：键区左上角（数字「1」）。 */
    val initialCell: Pair<Int, Int> = 1 to 1

    /** 空格键（画板③：普通键帽样式 + 功能键字号，跨两行 84×196）。 */
    val spaceKey: Key = Key(
        id = SPACE_ID,
        action = KeyAction.Space,
        label = "空格",
        textSize = KeyTextSize.SMALL,
        contentDescription = "空格",
    )

    /** 完成键（FR-A10 语义不变：确认当前输入并收起）。 */
    val doneKey: Key = Key(
        id = DONE_ID,
        action = KeyAction.Done,
        variant = KeyVariant.ACCENT,
        label = "完成",
        contentDescription = "完成",
    )

    /** 全/半切换键（FR-A5：符号键盘仅有此切换键，无大写键与中/英键）。 */
    private fun toggleWidthKey(): Cell = Cell.KeyCell(
        Key(
            id = TOGGLE_WIDTH_ID,
            action = KeyAction.ToggleWidth,
            variant = KeyVariant.FUNCTION,
            label = "全/半",
            textSize = KeyTextSize.SMALL,
            contentDescription = "全角半角切换",
        ),
    )

    /** 返回键（FR-A5：回到字母键盘；决策 3：位于第二行）。 */
    private fun backKey(): Cell = Cell.KeyCell(
        Key(
            id = BACK_ID,
            action = KeyAction.BackToLetters,
            variant = KeyVariant.FUNCTION,
            label = "返回",
            textSize = KeyTextSize.SMALL,
            contentDescription = "返回字母键盘",
        ),
    )

    /** 下屏键（FR-A1，图标键，同字母键盘）。 */
    private fun hideKey(): Cell = iconKey("hide", KeyIcon.HIDE, "下屏，收起键盘")

    /** 删除键（FR-A9，图标键，同字母键盘）。 */
    private fun deleteKey(): Cell = iconKey("delete", KeyIcon.BACKSPACE, "删除")

    /**
     * 符号集（FR-A6，PRD 97~98 行原文逐字符）：
     * 全角 30 个（含 ‘ ’ “ ” 四引号），半角 28 个。
     */
    private val fullWidthSymbols: List<List<String>> = listOf(
        listOf("～", "！", "＃", "￥", "％", "……", "＆"),
        listOf("？", "（", "）", "——", "＋", "＝", "×"),
        listOf("；", "：", "‘", "’", "“", "”", "，"),
        listOf("。", "／", "＊", "「", "」", "《", "》", "｜", "＼"),
    )

    private val halfWidthSymbols: List<List<String>> = listOf(
        listOf("~", "!", "#", "$", "^", "%", "&"),
        listOf("?", "(", ")", "_", "-", "+", "="),
        listOf(";", ":", "'", "\"", ",", ".", "/"),
        listOf("*", "[", "]", "{", "}", "|", "\\"),
    )

    /** 依全/半角状态返回 4 行键位（行 4 含数字「0」；FR-A6 符号全覆盖）。 */
    fun rows(width: SymbolsWidth): List<KeyRow> {
        val symbolRows = when (width) {
            SymbolsWidth.FULL_WIDTH -> fullWidthSymbols
            SymbolsWidth.HALF_WIDTH -> halfWidthSymbols
        }
        val prefix = if (width == SymbolsWidth.FULL_WIDTH) "fw_" else "hw_"
        fun symbolCells(symbols: List<String>): List<Cell> =
            symbols.map { sym -> Cell.KeyCell(Key(id = prefix + sym, action = KeyAction.Text(sym), label = sym, contentDescription = sym)) }

        return listOf(
            // 行 1：数字 1-3 ｜ 符号 7 ｜ 全/半 ｜ 下屏
            // 符号区后的 Spacer 与字母键盘同构（画板③ kspacer）：全/半、下屏回位功能列 1028 / 图标列 1120，
            // 与浮层空格（1028）/完成（1120）同列线（T29 回归修复）
            KeyRow(
                digitCells('1', '3') + Cell.Spacer + symbolCells(symbolRows[0]) + Cell.Spacer +
                    listOf(toggleWidthKey(), hideKey()),
            ),
            // 行 2：数字 4-6 ｜ 符号 7 ｜ 返回 ｜ 删除（决策 3：返回键位于第二行）
            KeyRow(
                digitCells('4', '6') + Cell.Spacer + symbolCells(symbolRows[1]) + Cell.Spacer +
                    listOf(backKey(), deleteKey()),
            ),
            // 行 3：数字 7-9 ｜ 符号 7 ｜ [空格 ABS col11] [完成 ABS col12]
            // 尾部 Spacer 与画板③行结构一致（col11/12 由浮层键声明，无行内键，几何不变）
            KeyRow(
                digitCells('7', '9') + Cell.Spacer + symbolCells(symbolRows[2]) + Cell.Spacer,
            ),
            // 行 4：0 。／（col1-3）｜ ＊「」《》｜＼（col4-10）｜ [空格 ABS col11] [完成 ABS col12]
            // 全角行 4 符号 9 个（。／＊「」《》｜＼），半角行 4 符号 7 个（*[]{}|\，col2/3 为空格位）
            KeyRow(
                when (width) {
                    SymbolsWidth.FULL_WIDTH ->
                        listOf(Cell.KeyCell(digitZeroKey())) + symbolCells(symbolRows[3].take(2)) +
                            listOf(Cell.Spacer) + symbolCells(symbolRows[3].drop(2))
                    SymbolsWidth.HALF_WIDTH ->
                        listOf(Cell.KeyCell(digitZeroKey()), Cell.EmptyCell, Cell.EmptyCell, Cell.Spacer) +
                            symbolCells(symbolRows[3])
                },
            ),
        )
    }

    /** 全部键位（含空格/完成 ABS 键）；单测与遍历辅助。 */
    fun allKeys(width: SymbolsWidth): List<Key> =
        rows(width).flatMap { row -> row.cells.filterIsInstance<Cell.KeyCell>().map { it.key } } +
            listOf(spaceKey, doneKey)

    /** 数字「0」键（行 4 col1，数字列 1-4-7-0 贯通；FR-A9）。 */
    private fun digitZeroKey(): Key = Key(id = "digit_0", action = KeyAction.Text("0"), label = "0", contentDescription = "0")

    // ---- 键位构建辅助（同 KeyboardLayout 口径） ----

    /** 数字列连续段（'1'..'3' 等）。 */
    private fun digitCells(from: Char, to: Char): List<Cell> =
        (from..to).map { charCell("digit_$it", it.toString()) }

    /** 字符键（普通变体）：无障碍标注 = 键面文字（T26 全键位口径，同 KeyboardLayout）。 */
    private fun charCell(id: String, label: String): Cell =
        Cell.KeyCell(Key(id = id, action = KeyAction.Text(label), label = label, contentDescription = label))

    /** 图标键（功能变体，必带无障碍标注）。 */
    private fun iconKey(id: String, icon: KeyIcon, description: String): Cell = Cell.KeyCell(
        Key(
            id = id,
            action = actionOfIcon(icon),
            variant = KeyVariant.FUNCTION,
            icon = icon,
            textSize = KeyTextSize.SMALL,
            contentDescription = description,
        ),
    )

    /** 图标 → 动作语义映射（同 KeyboardLayout 口径）。 */
    private fun actionOfIcon(icon: KeyIcon): KeyAction = when (icon) {
        KeyIcon.SHIFT -> KeyAction.ToggleCase
        KeyIcon.HIDE -> KeyAction.HideSelf
        KeyIcon.BACKSPACE -> KeyAction.Delete
        KeyIcon.CURSOR_LEFT -> KeyAction.CursorLeft
        KeyIcon.CURSOR_RIGHT -> KeyAction.CursorRight
    }
}
