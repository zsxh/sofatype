// T64 · 符号键盘 7×5 同位语义矩阵（原型定稿：designs/20261110 画板 3/4 默认态、画板 10/11 下移态）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（同 KeyboardLayout 口径）。
// 结构：真实矩阵 5 行 × 7 符号列（共 35 符号键位），全角/半角两页同位置同语义——
// 同一格位在两页只差全角/半角输出（如 R1C1 全角「，」/ 半角「,」）。
// 界面恒显示 4 行（行步进窗口，非自由滚动）：光标在显示第 4 行按[下]且下方还有真实行 →
// 窗口下移一行、符号内容切换但光标屏上位置不动（落显示第 4 行 = 原下一行同列键）；
// 显示第 1 行按[上]同理回移；边界外不越界。数字列与功能键列固定不随窗口滚动，仅符号区 7 列切换。
// 数字列与字母键盘对齐：1-9 + 第四行 `.com` `0` `@`（决策 27「符号页不含 .com/@」条款由
// PRD 第 12 章决策 45 推翻）。
// 决策 3 不变：返回键位于第二行；空格为跨两行高键（col11 行 3/4）、与完成键（col12 行 3/4）对称。
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

    /** 空格键 id（画板③ 跨两行高键，决策 3）。 */
    const val SPACE_ID = "space"

    /** 完成键 id（跨两行高键，与空格对称，决策 3）。 */
    const val DONE_ID = "done"

    /** 行步进窗口：界面恒显示行数（显示格网 4 行，与字母键盘同构）。 */
    const val VISIBLE_ROWS = 4

    /** 行步进窗口：真实矩阵行数（7×5 = 5 行 × 7 符号列）。 */
    const val MATRIX_ROWS = 5

    /** 行步进窗口：符号区列数。 */
    const val SYMBOL_COLS = 7

    /** 窗口偏移上限（0 起计）：显示窗顶行在真实矩阵中的行下标上限，边界外不越界。 */
    const val MAX_WINDOW_OFFSET = MATRIX_ROWS - VISIBLE_ROWS

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
     * 全角 7×5 真实矩阵（5 行 × 7 符号列；画板 3 显示 R1-R4 + 画板 10 显示 R5，原文逐字符）。
     */
    private val fullWidthSymbols: List<List<String>> = listOf(
        listOf("，", "。", "、", "？", "！", "；", "："),
        listOf("“", "”", "‘", "’", "（", "）", "《"),
        listOf("》", "【", "】", "…", "—", "～", "·"),
        listOf("／", "＿", "｜", "＠", "＃", "％", "＆"),
        listOf("＋", "＝", "＊", "＄", "＾", "｀", "￥"),
    )

    /**
     * 半角 7×5 真实矩阵（画板 4 显示 R1-R4 + 画板 11 显示 R5，原文逐字符）。
     * 与全角页同位置同语义：直引号无左右形态，R2C1-C4 同位输出 " " ' '（画板 4 原样）。
     */
    private val halfWidthSymbols: List<List<String>> = listOf(
        listOf(",", ".", "\\", "?", "!", ";", ":"),
        listOf("\"", "\"", "'", "'", "(", ")", "<"),
        listOf(">", "[", "]", "...", "--", "~", "·"),
        listOf("/", "_", "|", "@", "#", "%", "&"),
        listOf("+", "=", "*", "$", "^", "`", "¥"),
    )

    /**
     * 全矩阵符号键（5 行 × 7 列；键 id 按页 + 格位编号 fw_r{n}c{m} / hw_r{n}c{m}——
     * 半角页 R2 直引号同字符占两格，语义 id 会撞名，位置 id 保唯一）。
     */
    fun matrixKeys(width: SymbolsWidth): List<List<Key>> {
        val symbols = when (width) {
            SymbolsWidth.FULL_WIDTH -> fullWidthSymbols
            SymbolsWidth.HALF_WIDTH -> halfWidthSymbols
        }
        val prefix = if (width == SymbolsWidth.FULL_WIDTH) "fw" else "hw"
        return symbols.mapIndexed { rowIndex, row ->
            row.mapIndexed { colIndex, sym ->
                symbolKey("${prefix}_r${rowIndex + 1}c${colIndex + 1}", sym)
            }
        }
    }

    /**
     * 显示窗口键位（行步进窗口，T64）：[windowOffset] = 显示窗顶行在真实矩阵中的行下标（0 起），
     * 界面恒显示 [VISIBLE_ROWS] 行；数字列与功能键列固定（数字列 1-9 + 第四行 .com 0 @），
     * 仅符号区 7 列随窗口切换。偏移越界即抛（调用方先经 [windowStep] / [canStepDown] / [canStepUp] 判定）。
     */
    fun rows(width: SymbolsWidth, windowOffset: Int = 0): List<KeyRow> {
        require(windowOffset in 0..MAX_WINDOW_OFFSET) {
            "窗口偏移越界：$windowOffset（允许 0..$MAX_WINDOW_OFFSET）"
        }
        val windowed = matrixKeys(width).drop(windowOffset).take(VISIBLE_ROWS)
        return listOf(
            // 显示行 1：数字 1-3 ｜ 符号 7 ｜ 全/半 ｜ 下屏
            // 符号区后的 Spacer 与字母键盘同构（画板③ kspacer）：全/半、下屏回位功能列 1028 / 图标列 1120，
            // 与浮层空格（1028）/完成（1120）同列线（T29 回归修复口径延续）
            KeyRow(
                digitCells('1', '3') + Cell.Spacer + symbolCells(windowed[0]) + Cell.Spacer +
                    listOf(toggleWidthKey(), hideKey()),
            ),
            // 显示行 2：数字 4-6 ｜ 符号 7 ｜ 返回 ｜ 删除（决策 3：返回键位于第二行）
            KeyRow(
                digitCells('4', '6') + Cell.Spacer + symbolCells(windowed[1]) + Cell.Spacer +
                    listOf(backKey(), deleteKey()),
            ),
            // 显示行 3：数字 7-9 ｜ 符号 7 ｜ [空格 ABS col11] [完成 ABS col12]
            // （col11/12 由浮层键声明格位，行内无键；原型画板 3/10 行 3 同构）
            KeyRow(
                digitCells('7', '9') + Cell.Spacer + symbolCells(windowed[2]),
            ),
            // 显示行 4：.com 0 @（col1-3，与字母键盘行 4 一致，决策 45）｜ 符号 7（col4-10）
            // ｜ [空格 ABS col11] [完成 ABS col12]
            KeyRow(
                listOf(dotComCell(), Cell.KeyCell(digitZeroKey()), Cell.KeyCell(atKey())) +
                    Cell.Spacer + symbolCells(windowed[3]),
            ),
        )
    }

    /** 显示窗口内全部键位（含空格/完成 ABS 键）；单测与遍历辅助。 */
    fun allKeys(width: SymbolsWidth, windowOffset: Int = 0): List<Key> =
        rows(width, windowOffset).flatMap { row -> row.cells.filterIsInstance<Cell.KeyCell>().map { it.key } } +
            listOf(spaceKey, doneKey)

    /** 全矩阵符号键扁平列表（5×7，含显示窗口外行；长按连删焦点键查找等全量匹配用）。 */
    fun allMatrixKeys(width: SymbolsWidth): List<Key> = matrixKeys(width).flatten()

    // ---- 行步进窗口决策（T64，纯模型；服务层接线见 SofatypeImeService.stepSymbolsWindow） ----

    /** 显示窗下方是否还有真实行（offset 越小越多）：决定下箭头显隐与[下]步进可行性。 */
    fun canStepDown(windowOffset: Int): Boolean = windowOffset < MAX_WINDOW_OFFSET

    /** 显示窗上方是否还有真实行：决定上箭头显隐与[上]步进可行性。 */
    fun canStepUp(windowOffset: Int): Boolean = windowOffset > 0

    /**
     * 行步进决策（纯模型）：焦点锚格位行号（FocusNavigator.positionsOf 取顶行）+ 方向 + 当前偏移
     * → 新偏移；null = 不步进（格网内正常导航或边界外不越界）。
     * 锚行 = 显示第 [VISIBLE_ROWS] 行（窗口底缘）按[下]、或显示第 1 行（窗口顶缘）按[上]才触发；
     * 跨行浮层键（空格/完成）锚行为 3，不满足触发条件，维持既有「扫到自身不动」语义。
     */
    fun windowStep(anchorRow: Int, direction: FocusDirection, windowOffset: Int): Int? = when {
        direction == FocusDirection.DOWN && anchorRow == VISIBLE_ROWS && canStepDown(windowOffset) ->
            windowOffset + 1
        direction == FocusDirection.UP && anchorRow == 1 && canStepUp(windowOffset) ->
            windowOffset - 1
        else -> null
    }

    /** 显示行号 → 真实矩阵行下标（0 起；窗口顶行 = windowOffset）。 */
    fun realRowIndex(windowRow: Int, windowOffset: Int): Int = windowRow - 1 + windowOffset

    // ---- 键位构建辅助（同 KeyboardLayout 口径） ----

    /** 符号键（普通变体）：键面 = 输出 = 无障碍标注；半角「...」「--」为小字键面（画板 24px）。 */
    private fun symbolKey(id: String, symbol: String): Key = Key(
        id = id,
        action = KeyAction.Text(symbol),
        label = symbol,
        textSize = if (symbol.length >= 2) KeyTextSize.DENSE else KeyTextSize.NORMAL,
        contentDescription = symbol,
    )

    private fun symbolCells(keys: List<Key>): List<Cell> = keys.map { Cell.KeyCell(it) }

    /** `.com` 键（显示行 4 col1，与字母键盘行 4 同构：功能键样式 + 小字号，决策 45）。 */
    private fun dotComCell(): Cell = Cell.KeyCell(
        Key(
            id = "dot_com",
            action = KeyAction.Text(".com"),
            variant = KeyVariant.FUNCTION,
            label = ".com",
            textSize = KeyTextSize.SMALL,
            contentDescription = ".com",
        ),
    )

    /** 数字「0」键（显示行 4 col2，数字列 1-4-7-0 贯通；FR-A9）。 */
    private fun digitZeroKey(): Key = Key(id = "digit_0", action = KeyAction.Text("0"), label = "0", contentDescription = "0")

    /** `@` 键（显示行 4 col3，与字母键盘行 4 一致，决策 45）。 */
    private fun atKey(): Key = Key(id = "at", action = KeyAction.Text("@"), label = "@", contentDescription = "@")

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
