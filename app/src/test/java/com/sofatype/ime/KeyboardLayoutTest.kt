package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    private val keys: List<Key> = KeyboardLayout.allKeys()
    private val ids: List<String> = keys.map { it.id }
    private val englishRows = KeyboardLayout.rows(LanguageMode.ENGLISH)
    private val chineseRows = KeyboardLayout.rows(LanguageMode.CHINESE)

    @Test
    fun rowsAreFourWithArtboardStructure() {
        for (rows in listOf(englishRows, chineseRows)) {

            assertEquals(4, rows.size)
            for (row in rows) {
                assertEquals("每行应有 2 个列间隔", 2, row.cells.count { it is Cell.Spacer })
                assertEquals("第一个列间隔应紧跟数字列（index 3）", Cell.Spacer, row.cells[3])
            }

            val keyCount = rows.map { row -> row.cells.count { it is Cell.KeyCell } }
            assertEquals(listOf(12, 12, 11, 11), keyCount)
        }
    }

    @Test
    fun chineseAndEnglishRowsDifferOnlyInRow1HeadKey() {

        val cnRow1 = chineseRows[0].cells.filterIsInstance<Cell.KeyCell>().map { it.key }
        val enRow1 = englishRows[0].cells.filterIsInstance<Cell.KeyCell>().map { it.key }
        assertEquals("中文态行 1 头键 = 分词", "segment", cnRow1[cnRow1.size - 2].id)
        assertEquals("英文态行 1 头键 = ⇧", "shift", enRow1[enRow1.size - 2].id)
        assertEquals("hide", enRow1.last().id)
        assertEquals("hide", cnRow1.last().id)
        for (rowIndex in 1..3) {
            assertEquals(
                "行 ${rowIndex + 1} 两态键位 id 序一致",
                englishRows[rowIndex].cells.filterIsInstance<Cell.KeyCell>().map { it.key.id },
                chineseRows[rowIndex].cells.filterIsInstance<Cell.KeyCell>().map { it.key.id },
            )
        }
    }

    @Test
    fun segmentKeyModel() {

        val segment = KeyboardLayout.segmentKey
        assertEquals("segment", segment.id)
        assertEquals(KeyAction.Segment, segment.action)
        assertEquals(KeyVariant.FUNCTION, segment.variant)
        assertEquals("分词", segment.label)
        assertEquals(KeyTextSize.SMALL, segment.textSize)
        assertEquals(
            "分词：组合态内切分拼音（如 xi'an），选词后不上屏；无组合态时忽略",
            segment.contentDescription,
        )
    }

    @Test
    fun lowercaseLettersCompleteAndInArtboardOrder() {

        val letters = keys.mapNotNull { key ->
            val text = (key.action as? KeyAction.Text)?.value
            if (text != null && text.length == 1 && text[0] in 'a'..'z') text[0] else null
        }
        assertEquals("字母键应齐全且有序（a-z）", ('a'..'z').toList(), letters)
    }

    @Test
    fun numberColumnMatchesArtboard() {

        val firstCells = englishRows.map { row -> (row.cells.take(3).filterIsInstance<Cell.KeyCell>().map { it.key.label }) }
        assertEquals(listOf("1", "2", "3"), firstCells[0])
        assertEquals(listOf("4", "5", "6"), firstCells[1])
        assertEquals(listOf("7", "8", "9"), firstCells[2])
        assertEquals(listOf(".com", "0", "@"), firstCells[3])
    }

    @Test
    fun letterZoneRowFourHasCursorKeys() {

        val row4 = englishRows[3].cells.filterIsInstance<Cell.KeyCell>().map { it.key }
        val zone = row4.drop(3).dropLast(1)
        assertEquals(listOf("v", "w", "x", "y", "z"), zone.take(5).map { it.label })
        assertEquals(KeyIcon.CURSOR_LEFT, zone[5].icon)
        assertEquals(KeyIcon.CURSOR_RIGHT, zone[6].icon)
    }

    @Test
    fun functionKeysComplete() {

        val expected = setOf("shift", "segment", "hide", "lang", "backspace", "symbols", "space", "done", "cursor_left", "cursor_right")
        assertTrue("功能键缺失：${expected - ids.toSet()}", ids.containsAll(expected))
    }

    @Test
    fun keyIdsAreUnique() {
        assertEquals("键位 id 必须唯一", ids.size, ids.distinct().size)
    }

    @Test
    fun hideKeyIsSoleActiveActionInT5() {

        val hide = keys.first { it.id == "hide" }
        assertEquals(KeyAction.HideSelf, hide.action)
        assertEquals("下屏键无障碍标注（设计稿 aria-label）", "下屏，收起键盘", hide.contentDescription)
    }

    @Test
    fun iconToActionMappingIsCorrect() {
        val byId = keys.associateBy { it.id }
        assertEquals(KeyAction.ToggleCase, byId.getValue("shift").action)
        assertEquals(KeyAction.Segment, byId.getValue("segment").action)
        assertEquals(KeyAction.Delete, byId.getValue("backspace").action)
        assertEquals(KeyAction.ToggleLanguage, byId.getValue("lang").action)
        assertEquals(KeyAction.OpenSymbols, byId.getValue("symbols").action)
        assertEquals(KeyAction.CursorLeft, byId.getValue("cursor_left").action)
        assertEquals(KeyAction.CursorRight, byId.getValue("cursor_right").action)
        assertEquals(KeyAction.Space, byId.getValue("space").action)
    }

    @Test
    fun doneKeyIsAccentOverlayOutsideRows() {

        val done = KeyboardLayout.doneKey
        assertEquals("done", done.id)
        assertEquals(KeyVariant.ACCENT, done.variant)
        assertEquals(KeyAction.Done, done.action)
        val inRowIds = englishRows.flatMap { it.cells }.filterIsInstance<Cell.KeyCell>().map { it.key.id }
        assertTrue("完成键不应出现在行键位中", inRowIds.none { it == "done" })
    }

    @Test
    fun functionVariantKeysUseSmallTextSize() {

        val smallTextIds = setOf("shift", "segment", "hide", "lang", "backspace", "symbols", "space", "dot_com")
        for (key in keys) {
            if (key.id in smallTextIds) {
                assertEquals("键 ${key.id} 应为小字号键面", KeyTextSize.SMALL, key.textSize)
            }
        }
        val space = keys.first { it.id == "space" }
        assertEquals("空格键帽底色与字符键一致（画板 .bkey）", KeyVariant.CHARACTER, space.variant)
    }

    @Test
    fun dotComKeyUsesFunctionStyle() {

        val dotCom = keys.first { it.id == "dot_com" }
        assertEquals(KeyVariant.FUNCTION, dotCom.variant)
        assertEquals(KeyTextSize.SMALL, dotCom.textSize)
        assertEquals(KeyAction.Text(".com"), dotCom.action)
    }

    @Test
    fun iconKeysAllHaveAccessibilityLabels() {

        val iconKeys = keys.filter { it.icon != null }
        assertTrue("图标键不应为空", iconKeys.isNotEmpty())
        for (key in iconKeys) {
            assertTrue("图标键 ${key.id} 无障碍标注不能为空白", key.contentDescription.isNotBlank())
        }
    }

    @Test
    fun letterKeyIdsCoverExactly26Letters() {

        assertEquals(
            (('a'..'z').map { it.toString() }).toSet(),
            KeyboardLayout.letterKeyIds,
        )
    }

    @Test
    fun displayLabelFollowsLanguageModeAndCaseState() {
        val byId = KeyboardLayout.keyById

        for (id in listOf("a", "h", "z")) {
            val key = byId.getValue(id)
            assertEquals("中文态键面恒大写 $id", id.uppercase(), KeyboardLayout.displayLabel(key, chineseMode = true, upperCase = false))
            assertEquals("中文态键面恒大写 $id", id.uppercase(), KeyboardLayout.displayLabel(key, chineseMode = true, upperCase = true))
        }

        for (id in listOf("a", "h", "z")) {
            val key = byId.getValue(id)
            assertEquals("英文小写态键面 $id", id, KeyboardLayout.displayLabel(key, chineseMode = false, upperCase = false))
            assertEquals("英文大写态键面 $id", id.uppercase(), KeyboardLayout.displayLabel(key, chineseMode = false, upperCase = true))
        }

        for (chinese in listOf(true, false)) {
            for (upper in listOf(true, false)) {
                assertEquals("5", KeyboardLayout.displayLabel(byId.getValue("digit_5"), chinese, upper))
                assertEquals(".com", KeyboardLayout.displayLabel(byId.getValue("dot_com"), chinese, upper))
                assertEquals("@", KeyboardLayout.displayLabel(byId.getValue("at"), chinese, upper))
                assertEquals("空格", KeyboardLayout.displayLabel(byId.getValue("space"), chinese, upper))
                assertEquals("分词", KeyboardLayout.displayLabel(KeyboardLayout.segmentKey, chinese, upper))
            }
        }
    }

    @Test
    fun outputFollowsCaseStateInEnglishMode() {

        val byId = KeyboardLayout.keyById
        assertEquals("a", KeyboardLayout.outputValue(byId.getValue("a"), CaseState.LOWER))
        assertEquals("H", KeyboardLayout.outputValue(byId.getValue("h"), CaseState.UPPER))

        assertEquals("7", KeyboardLayout.outputValue(byId.getValue("digit_7"), CaseState.UPPER))
        assertEquals(".com", KeyboardLayout.outputValue(byId.getValue("dot_com"), CaseState.UPPER))
        assertEquals("@", KeyboardLayout.outputValue(byId.getValue("at"), CaseState.UPPER))

        assertEquals(null, KeyboardLayout.outputValue(byId.getValue("space"), CaseState.UPPER))
        assertEquals(null, KeyboardLayout.outputValue(byId.getValue("backspace"), CaseState.UPPER))
    }

    @Test
    fun shiftContentDescriptionMatchesPrdWording() {

        assertEquals("上档切换，当前小写", KeyboardLayout.shiftContentDescription(CaseState.LOWER))
        assertEquals("上档切换，当前大写", KeyboardLayout.shiftContentDescription(CaseState.UPPER))
    }

    @Test
    fun langStateKeyHasSchemeCOptionsAndStateDescription() {

        val lang = KeyboardLayout.keyById.getValue("lang")
        assertEquals("中" to "英", lang.stateOptions)
        assertEquals("中英文切换", lang.contentDescription)
        assertEquals("中英文切换，当前为中文", KeyboardLayout.langContentDescription(chinese = true))
        assertEquals("中英文切换，当前为英文", KeyboardLayout.langContentDescription(chinese = false))
    }

    private fun cellOriginPxs(row: KeyRow): List<Int> {
        val origins = mutableListOf<Int>()
        var x = 0
        for (cell in row.cells) {
            origins.add(x)
            x += when (cell) {
                is Cell.KeyCell -> 96 + 10
                Cell.Spacer -> 48 + 10

                Cell.EmptyCell -> 96 + 10
            }
        }
        return origins
    }

    @Test
    fun columnRhythmMatchesArtboard() {

        for (rows in listOf(englishRows, chineseRows)) {
            val row1 = cellOriginPxs(rows[0])
            assertEquals("字母列原点（屏 424）", 376, row1[4])
            assertEquals("功能列原点（屏 1224）", 1176, row1[12])
            assertEquals("图标列原点（屏 1330）", 1282, row1[13])

            assertEquals("列间隔步进（10+48+10）", 376 - 308, 68)
            assertEquals("列间隔步进（1108 → 1176）", 1176 - 1108, 68)

            assertEquals("行 3 功能列（123_!）", 1176, cellOriginPxs(rows[2])[12])
            assertEquals("行 4 功能列（空格）", 1176, cellOriginPxs(rows[3])[12])

            assertEquals("完成键左缘与功能列右缘间距 = 1278 − 1272 = 6px", 6, 1278 - (1176 + 96))
            assertEquals("完成键右缘贴键区列右缘（1278 + 100 = 1378）", 1378, 1278 + 100)
        }
    }

    @Test
    fun letterIdForKeyCodeMapsKeycodeAThruZ() {

        assertEquals("a", KeyboardLayout.letterIdForKeyCode(29))
        assertEquals("e", KeyboardLayout.letterIdForKeyCode(33))
        assertEquals("k", KeyboardLayout.letterIdForKeyCode(39))
        assertEquals("t", KeyboardLayout.letterIdForKeyCode(48))
        assertEquals("z", KeyboardLayout.letterIdForKeyCode(54))
    }

    @Test
    fun letterIdForKeyCodeRejectsNonLetterCodes() {

        assertNull(KeyboardLayout.letterIdForKeyCode(28))
        assertNull(KeyboardLayout.letterIdForKeyCode(55))
        assertNull(KeyboardLayout.letterIdForKeyCode(7))
        assertNull(KeyboardLayout.letterIdForKeyCode(62))
        assertNull(KeyboardLayout.letterIdForKeyCode(66))
        assertNull(KeyboardLayout.letterIdForKeyCode(67))
    }

    @Test
    fun letterIdForKeyCodeIdsAreAllRealLetterKeys() {

        for (keyCode in 29..54) {
            val id = KeyboardLayout.letterIdForKeyCode(keyCode)
            assertTrue("id=$id 应在键位模型中", KeyboardLayout.keyById.containsKey(id))
            assertTrue("id=$id 应为字母键", id in KeyboardLayout.letterKeyIds)
        }
    }
}
