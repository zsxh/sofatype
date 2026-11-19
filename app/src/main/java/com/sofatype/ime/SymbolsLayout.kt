package com.sofatype.ime

enum class SymbolsWidth { FULL_WIDTH, HALF_WIDTH }

object SymbolsLayout {

    const val TOGGLE_WIDTH_ID = "toggle_width"

    const val BACK_ID = "back"

    const val SPACE_ID = "space"

    const val DONE_ID = "done"

    const val VISIBLE_ROWS = 4

    const val MATRIX_ROWS = 5

    const val SYMBOL_COLS = 7

    const val MAX_WINDOW_OFFSET = MATRIX_ROWS - VISIBLE_ROWS

    val spaceKeyCells: List<Pair<Int, Int>> = listOf(3 to 11, 4 to 11)

    val doneKeyCells: List<Pair<Int, Int>> = listOf(3 to 12, 4 to 12)

    fun overlayCells(): Map<String, List<Pair<Int, Int>>> = mapOf(
        SPACE_ID to spaceKeyCells,
        DONE_ID to doneKeyCells,
    )

    fun widthContentDescription(halfWidth: Boolean): String =
        if (halfWidth) "全角半角切换，当前为半角" else "全角半角切换，当前为全角"

    val initialCell: Pair<Int, Int> = 1 to 1

    val spaceKey: Key = Key(
        id = SPACE_ID,
        action = KeyAction.Space,
        label = "空格",
        textSize = KeyTextSize.SMALL,
        contentDescription = "空格",
    )

    val doneKey: Key = Key(
        id = DONE_ID,
        action = KeyAction.Done,
        variant = KeyVariant.ACCENT,
        label = "完成",
        contentDescription = "完成",
    )

    private fun toggleWidthKey(): Cell = Cell.KeyCell(
        Key(
            id = TOGGLE_WIDTH_ID,
            action = KeyAction.ToggleWidth,
            variant = KeyVariant.FUNCTION,
            label = "全/半",
            textSize = KeyTextSize.SMALL,
            contentDescription = "全角半角切换",

            stateOptions = "全" to "半",
        ),
    )

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

    private fun hideKey(): Cell = iconKey("hide", KeyIcon.HIDE, "下屏，收起键盘")

    private fun deleteKey(): Cell = iconKey("delete", KeyIcon.BACKSPACE, "删除")

    private val fullWidthSymbols: List<List<String>> = listOf(
        listOf("，", "。", "、", "？", "！", "；", "："),
        listOf("“", "”", "‘", "’", "（", "）", "《"),
        listOf("》", "【", "】", "…", "—", "～", "·"),
        listOf("／", "＿", "｜", "＠", "＃", "％", "＆"),
        listOf("＋", "＝", "＊", "＄", "＾", "｀", "￥"),
    )

    private val halfWidthSymbols: List<List<String>> = listOf(
        listOf(",", ".", "\\", "?", "!", ";", ":"),
        listOf("\"", "\"", "'", "'", "(", ")", "<"),
        listOf(">", "[", "]", "...", "--", "~", "·"),
        listOf("/", "_", "|", "@", "#", "%", "&"),
        listOf("+", "=", "*", "$", "^", "`", "¥"),
    )

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

    fun rows(width: SymbolsWidth, windowOffset: Int = 0): List<KeyRow> {
        require(windowOffset in 0..MAX_WINDOW_OFFSET) {
            "窗口偏移越界：$windowOffset（允许 0..$MAX_WINDOW_OFFSET）"
        }
        val windowed = matrixKeys(width).drop(windowOffset).take(VISIBLE_ROWS)
        return listOf(

            KeyRow(
                digitCells('1', '3') + Cell.Spacer + symbolCells(windowed[0]) + Cell.Spacer +
                    listOf(toggleWidthKey(), hideKey()),
            ),

            KeyRow(
                digitCells('4', '6') + Cell.Spacer + symbolCells(windowed[1]) + Cell.Spacer +
                    listOf(backKey(), deleteKey()),
            ),

            KeyRow(
                digitCells('7', '9') + Cell.Spacer + symbolCells(windowed[2]),
            ),

            KeyRow(
                listOf(dotComCell(), Cell.KeyCell(digitZeroKey()), Cell.KeyCell(atKey())) +
                    Cell.Spacer + symbolCells(windowed[3]),
            ),
        )
    }

    fun allKeys(width: SymbolsWidth, windowOffset: Int = 0): List<Key> =
        rows(width, windowOffset).flatMap { row -> row.cells.filterIsInstance<Cell.KeyCell>().map { it.key } } +
            listOf(spaceKey, doneKey)

    fun allMatrixKeys(width: SymbolsWidth): List<Key> = matrixKeys(width).flatten()

    fun canStepDown(windowOffset: Int): Boolean = windowOffset < MAX_WINDOW_OFFSET

    fun canStepUp(windowOffset: Int): Boolean = windowOffset > 0

    fun windowStep(anchorRow: Int, direction: FocusDirection, windowOffset: Int): Int? = when {
        direction == FocusDirection.DOWN && anchorRow == VISIBLE_ROWS && canStepDown(windowOffset) ->
            windowOffset + 1
        direction == FocusDirection.UP && anchorRow == 1 && canStepUp(windowOffset) ->
            windowOffset - 1
        else -> null
    }

    fun realRowIndex(windowRow: Int, windowOffset: Int): Int = windowRow - 1 + windowOffset

    private fun symbolKey(id: String, symbol: String): Key = Key(
        id = id,
        action = KeyAction.Text(symbol),
        label = symbol,
        textSize = if (symbol.length >= 2) KeyTextSize.DENSE else KeyTextSize.NORMAL,
        contentDescription = symbol,
    )

    private fun symbolCells(keys: List<Key>): List<Cell> = keys.map { Cell.KeyCell(it) }

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

    private fun digitZeroKey(): Key = Key(id = "digit_0", action = KeyAction.Text("0"), label = "0", contentDescription = "0")

    private fun atKey(): Key = Key(id = "at", action = KeyAction.Text("@"), label = "@", contentDescription = "@")

    private fun digitCells(from: Char, to: Char): List<Cell> =
        (from..to).map { charCell("digit_$it", it.toString()) }

    private fun charCell(id: String, label: String): Cell =
        Cell.KeyCell(Key(id = id, action = KeyAction.Text(label), label = label, contentDescription = label))

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

    private fun actionOfIcon(icon: KeyIcon): KeyAction = when (icon) {
        KeyIcon.SHIFT -> KeyAction.ToggleCase
        KeyIcon.HIDE -> KeyAction.HideSelf
        KeyIcon.BACKSPACE -> KeyAction.Delete
        KeyIcon.CURSOR_LEFT -> KeyAction.CursorLeft
        KeyIcon.CURSOR_RIGHT -> KeyAction.CursorRight
    }
}
