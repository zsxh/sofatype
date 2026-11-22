package com.sofatype.ime

enum class CaseState { LOWER, UPPER }

enum class LanguageMode { CHINESE, ENGLISH }

enum class KeyVariant {

    CHARACTER,

    FUNCTION,

    ACCENT,
}

enum class KeyTextSize {

    NORMAL,

    SMALL,

    DENSE,
}

enum class KeyIcon { SHIFT, HIDE, BACKSPACE, CURSOR_LEFT, CURSOR_RIGHT }

sealed class KeyAction {

    data class Text(val value: String) : KeyAction()

    object ToggleCase : KeyAction()

    object Segment : KeyAction()

    object HideSelf : KeyAction()

    object ToggleLanguage : KeyAction()

    object OpenSymbols : KeyAction()

    object ToggleWidth : KeyAction()

    object BackToLetters : KeyAction()

    object Delete : KeyAction()

    object CursorLeft : KeyAction()

    object CursorRight : KeyAction()

    object Space : KeyAction()

    object Done : KeyAction()
}

data class Key(

    val id: String,

    val action: KeyAction,

    val variant: KeyVariant = KeyVariant.CHARACTER,

    val label: String? = null,

    val icon: KeyIcon? = null,

    val textSize: KeyTextSize = KeyTextSize.NORMAL,

    val contentDescription: String,

    val stateOptions: Pair<String, String>? = null,
)

sealed interface Cell {

    data class KeyCell(val key: Key) : Cell

    object Spacer : Cell

    object EmptyCell : Cell
}

data class KeyRow(val cells: List<Cell>)

object KeyboardLayout {

    const val SYMBOLS_ENTRY_KEY_ID = "symbols"

    val segmentKey: Key = Key(
        id = "segment",
        action = KeyAction.Segment,
        variant = KeyVariant.FUNCTION,
        label = "分词",
        textSize = KeyTextSize.SMALL,
        contentDescription = "分词：组合态内切分拼音（如 xi'an），选词后不上屏；无组合态时忽略",
    )

    private fun row1TailCells(language: LanguageMode): List<Cell> = listOf(
        when (language) {
            LanguageMode.CHINESE -> Cell.KeyCell(segmentKey)
            LanguageMode.ENGLISH -> iconCell("shift", KeyIcon.SHIFT, "上档切换")
        },
        iconCell("hide", KeyIcon.HIDE, "下屏，收起键盘"),
    )

    fun rows(language: LanguageMode): List<KeyRow> = when (language) {
        LanguageMode.CHINESE -> listOf(
            KeyRow(digitCells('1', '3') + Cell.Spacer + letterCells('a', 'g') + Cell.Spacer + row1TailCells(language)),
            englishRows[1],
            englishRows[2],
            englishRows[3],
        )
        LanguageMode.ENGLISH -> englishRows
    }

    private val englishRows: List<KeyRow> = listOf(
        KeyRow(digitCells('1', '3') + Cell.Spacer + letterCells('a', 'g') + Cell.Spacer + row1TailCells(LanguageMode.ENGLISH)),

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

                            stateOptions = "中" to "英",
                        ),
                    ),
                    iconCell("backspace", KeyIcon.BACKSPACE, "删除"),
                ),
        ),

        KeyRow(
            digitCells('7', '9') + Cell.Spacer + letterCells('o', 'u') + Cell.Spacer +
                listOf(fnCell("symbols", "123_!", KeyAction.OpenSymbols, "进入符号键盘")),
        ),

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

                            label = "空格",
                            textSize = KeyTextSize.SMALL,
                            contentDescription = "空格",
                        ),
                    ),
                ),
        ),
    )

    val doneKey: Key = Key(
        id = "done",
        action = KeyAction.Done,
        variant = KeyVariant.ACCENT,
        label = "完成",
        contentDescription = "完成",
    )

    val doneKeyCells: List<Pair<Int, Int>> = run {
        var index = 0
        var hideCol = -1
        for (cell in englishRows[0].cells) {
            if (cell is Cell.KeyCell) {
                index += 1
                if (cell.key.id == "hide") hideCol = index
            }
        }
        check(hideCol > 0) { "行 1 未找到下屏键，无法推导完成键列" }
        listOf(3 to hideCol, 4 to hideCol)
    }

    val initialCell: Pair<Int, Int> = 1 to 4

    fun allKeys(): List<Key> =
        englishRows.flatMap { row -> row.cells.filterIsInstance<Cell.KeyCell>().map { it.key } } +
            listOf(segmentKey, doneKey)

    val keyById: Map<String, Key> = allKeys().associateBy { it.id }

    val letterKeyIds: Set<String> =
        allKeys().filter { key -> isSingleLetter(key.action) }.map { it.id }.toSet()

    fun letterIdForKeyCode(keyCode: Int): String? {
        if (keyCode < 29 || keyCode > 54) return null
        return ('a' + (keyCode - 29)).toString()
    }

    fun digitIdForKeyCode(keyCode: Int): String? {
        if (keyCode < 7 || keyCode > 16) return null
        return "digit_${keyCode - 7}"
    }

    fun displayLabel(key: Key, chineseMode: Boolean, upperCase: Boolean): String? = when {
        key.id in letterKeyIds -> if (chineseMode || upperCase) key.label?.uppercase() else key.label
        else -> key.label
    }

    fun outputValue(key: Key, case: CaseState): String? {
        val value = (key.action as? KeyAction.Text)?.value ?: return null
        return if (case == CaseState.UPPER && key.id in letterKeyIds) value.uppercase() else value
    }

    fun shiftContentDescription(case: CaseState): String = when (case) {
        CaseState.LOWER -> "上档切换，当前小写"
        CaseState.UPPER -> "上档切换，当前大写"
    }

    fun langContentDescription(chinese: Boolean): String =
        if (chinese) "中英文切换，当前为中文" else "中英文切换，当前为英文"

    private fun isSingleLetter(action: KeyAction): Boolean {
        val value = (action as? KeyAction.Text)?.value ?: return false
        return value.length == 1 && value[0] in 'a'..'z'
    }

    private fun charCell(id: String, label: String, description: String = label): Cell =
        Cell.KeyCell(Key(id = id, action = KeyAction.Text(label), label = label, contentDescription = description))

    private fun digitCells(from: Char, to: Char): List<Cell> = (from..to).map { charCell("digit_$it", it.toString()) }

    private fun letterCells(from: Char, to: Char): List<Cell> = (from..to).map { charCell(it.toString(), it.toString()) }

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

    private fun actionOfIcon(icon: KeyIcon): KeyAction = when (icon) {
        KeyIcon.SHIFT -> KeyAction.ToggleCase
        KeyIcon.HIDE -> KeyAction.HideSelf
        KeyIcon.BACKSPACE -> KeyAction.Delete
        KeyIcon.CURSOR_LEFT -> KeyAction.CursorLeft
        KeyIcon.CURSOR_RIGHT -> KeyAction.CursorRight
    }
}
