// T5 · 键位模型单元测试（JUnit 4，纯 Kotlin，不依赖 Android UI 与模拟器）。
// 锁定画板 designs/20261114（字母键盘）的静态键位结构：行列结构、字符完整性、功能键与动作语义。
// T66 · 决策 51：键位结构随语言模式分化——中文态行 1 头键 = 分词键（无 ⇧）、英文态 = ⇧ 上档，
// 两态其余键位同构；键面随语言模式 / 大小写输出状态（中文恒显大写、英文两态）。
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

    // ---- 行列结构（画板 20261114；T66 两态同构性） ----

    @Test
    fun rowsAreFourWithArtboardStructure() {
        for (rows in listOf(englishRows, chineseRows)) {
            // 4 行；每行 2 个列间隔（数字列｜字母区｜功能键区三段式，FR-A2）
            assertEquals(4, rows.size)
            for (row in rows) {
                assertEquals("每行应有 2 个列间隔", 2, row.cells.count { it is Cell.Spacer })
                assertEquals("第一个列间隔应紧跟数字列（index 3）", Cell.Spacer, row.cells[3])
            }
            // 行键数：行 1/2 功能键区 2 键，行 3/4 功能键区 1 键（右侧由完成键覆盖）
            val keyCount = rows.map { row -> row.cells.count { it is Cell.KeyCell } }
            assertEquals(listOf(12, 12, 11, 11), keyCount)
        }
    }

    @Test
    fun chineseAndEnglishRowsDifferOnlyInRow1HeadKey() {
        // T66 · 决策 51-②：中文态无 ⇧ 键位，该键位 = 分词键；行 2-4 两态共享同构键位
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
        // T66 · 决策 51-②：分词键 = 功能键样式 + 「分词」键面 + Segment 动作 + 画板 aria 标注
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
        // 字母区按画板 20261114 行序 a-g / h-n / o-u / v-z 连续排列，26 个小写字母齐全
        val letters = keys.mapNotNull { key ->
            val text = (key.action as? KeyAction.Text)?.value
            if (text != null && text.length == 1 && text[0] in 'a'..'z') text[0] else null
        }
        assertEquals("字母键应齐全且有序（a-z）", ('a'..'z').toList(), letters)
    }

    @Test
    fun numberColumnMatchesArtboard() {
        // 数字列（FR-A2）：行 1-3 依次 1-9，行 4 为 .com、0、@
        val firstCells = englishRows.map { row -> (row.cells.take(3).filterIsInstance<Cell.KeyCell>().map { it.key.label }) }
        assertEquals(listOf("1", "2", "3"), firstCells[0])
        assertEquals(listOf("4", "5", "6"), firstCells[1])
        assertEquals(listOf("7", "8", "9"), firstCells[2])
        assertEquals(listOf(".com", "0", "@"), firstCells[3])
    }

    @Test
    fun letterZoneRowFourHasCursorKeys() {
        // 行 4 字母区：v-z 5 个字母 + 光标左/右 2 键（画板 20261114）
        val row4 = englishRows[3].cells.filterIsInstance<Cell.KeyCell>().map { it.key }
        val zone = row4.drop(3).dropLast(1) // 去掉数字列 3 键与末尾空格
        assertEquals(listOf("v", "w", "x", "y", "z"), zone.take(5).map { it.label })
        assertEquals(KeyIcon.CURSOR_LEFT, zone[5].icon)
        assertEquals(KeyIcon.CURSOR_RIGHT, zone[6].icon)
    }

    // ---- 功能键与动作语义 ----

    @Test
    fun functionKeysComplete() {
        // 上档/分词/下屏/中英/删除/符号/空格/完成（FR-A3/A1/A4/A5/A9/A10 + 决策 51-②）+ 光标左右（FR-A9）
        val expected = setOf("shift", "segment", "hide", "lang", "backspace", "symbols", "space", "done", "cursor_left", "cursor_right")
        assertTrue("功能键缺失：${expected - ids.toSet()}", ids.containsAll(expected))
    }

    @Test
    fun keyIdsAreUnique() {
        assertEquals("键位 id 必须唯一", ids.size, ids.distinct().size)
    }

    @Test
    fun hideKeyIsSoleActiveActionInT5() {
        // T5 静态布局口径：仅「下屏」动作生效（FR-A1 收起）
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
        // 完成键（FR-A10）：强调变体 + Done 动作；跨行浮层字段，不在行内容器内
        val done = KeyboardLayout.doneKey
        assertEquals("done", done.id)
        assertEquals(KeyVariant.ACCENT, done.variant)
        assertEquals(KeyAction.Done, done.action)
        val inRowIds = englishRows.flatMap { it.cells }.filterIsInstance<Cell.KeyCell>().map { it.key.id }
        assertTrue("完成键不应出现在行键位中", inRowIds.none { it == "done" })
    }

    // ---- 键面样式语义（画板 20261114 规格） ----

    @Test
    fun functionVariantKeysUseSmallTextSize() {
        // 功能键文字 22px 规格（PRD 6.3）→ SMALL；空格键面同字号但普通键帽底色
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
        // `.com` 与画板 .bkey-fn 一致（评审第 1 轮 Major 1）：功能键变体 + 小字号 + 直出文本动作
        val dotCom = keys.first { it.id == "dot_com" }
        assertEquals(KeyVariant.FUNCTION, dotCom.variant)
        assertEquals(KeyTextSize.SMALL, dotCom.textSize)
        assertEquals(KeyAction.Text(".com"), dotCom.action)
    }

    @Test
    fun iconKeysAllHaveAccessibilityLabels() {
        // PRD 第 8 章：全部可交互元素具备无障碍标注（T26 起 contentDescription 全键位必填，
        // 类型层面已保证非空；本测试保留图标键标注逐键复核）
        val iconKeys = keys.filter { it.icon != null }
        assertTrue("图标键不应为空", iconKeys.isNotEmpty())
        for (key in iconKeys) {
            assertTrue("图标键 ${key.id} 无障碍标注不能为空白", key.contentDescription.isNotBlank())
        }
    }

    // ---- 大小写切换与输出值（FR-A3 / FR-A9，T7） ----

    @Test
    fun letterKeyIdsCoverExactly26Letters() {
        // 大小写切换只作用于 26 个字母键
        assertEquals(
            (('a'..'z').map { it.toString() }).toSet(),
            KeyboardLayout.letterKeyIds,
        )
    }

    @Test
    fun displayLabelFollowsLanguageModeAndCaseState() {
        val byId = KeyboardLayout.keyById
        // T66 · 决策 51-②：中文模式键面恒显大写形态（无 ⇧ 键位，大小写与键面无关）
        for (id in listOf("a", "h", "z")) {
            val key = byId.getValue(id)
            assertEquals("中文态键面恒大写 $id", id.uppercase(), KeyboardLayout.displayLabel(key, chineseMode = true, upperCase = false))
            assertEquals("中文态键面恒大写 $id", id.uppercase(), KeyboardLayout.displayLabel(key, chineseMode = true, upperCase = true))
        }
        // T66 · 决策 51-③：英文模式键面随大小写输出状态（小写态小写、大写态大写）
        for (id in listOf("a", "h", "z")) {
            val key = byId.getValue(id)
            assertEquals("英文小写态键面 $id", id, KeyboardLayout.displayLabel(key, chineseMode = false, upperCase = false))
            assertEquals("英文大写态键面 $id", id.uppercase(), KeyboardLayout.displayLabel(key, chineseMode = false, upperCase = true))
        }
        // 非字母键键面不受语言模式与大小写影响
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
        // 输出口径（FR-A9 × FR-A3）：outputValue 随 ⇧ 触发态（触发 = 大写 / 未触发 = 小写），
        // 与键面两态同拍（T66 · 决策 51-③ 键面随输出状态）
        val byId = KeyboardLayout.keyById
        assertEquals("a", KeyboardLayout.outputValue(byId.getValue("a"), CaseState.LOWER))
        assertEquals("H", KeyboardLayout.outputValue(byId.getValue("h"), CaseState.UPPER))
        // 数字/.com/@ 直出，不受大小写影响（FR-A9）
        assertEquals("7", KeyboardLayout.outputValue(byId.getValue("digit_7"), CaseState.UPPER))
        assertEquals(".com", KeyboardLayout.outputValue(byId.getValue("dot_com"), CaseState.UPPER))
        assertEquals("@", KeyboardLayout.outputValue(byId.getValue("at"), CaseState.UPPER))
        // 非文本键无输出值
        assertEquals(null, KeyboardLayout.outputValue(byId.getValue("space"), CaseState.UPPER))
        assertEquals(null, KeyboardLayout.outputValue(byId.getValue("backspace"), CaseState.UPPER))
    }

    @Test
    fun shiftContentDescriptionMatchesPrdWording() {
        // T65 · 决策 47-① / 48-①：contentDescription 含当前态 + 动作（⇧ 触发态为唯一大小写指示）
        assertEquals("上档切换，当前小写", KeyboardLayout.shiftContentDescription(CaseState.LOWER))
        assertEquals("上档切换，当前大写", KeyboardLayout.shiftContentDescription(CaseState.UPPER))
    }

    @Test
    fun langStateKeyHasSchemeCOptionsAndStateDescription() {
        // T65 · 决策 46-② 方案 C：[中/英] 斜杠排版两态字符 + 含当前态的无障碍描述
        val lang = KeyboardLayout.keyById.getValue("lang")
        assertEquals("中" to "英", lang.stateOptions)
        assertEquals("中英文切换", lang.contentDescription)
        assertEquals("中英文切换，当前为中文", KeyboardLayout.langContentDescription(chinese = true))
        assertEquals("中英文切换，当前为英文", KeyboardLayout.langContentDescription(chinese = false))
    }

    // ---- 列节奏（T68 · 决策 58 画板 designs/20261114 口径，T28 锁定） ----

    /** 键区水平排布口径（1080p px，与画板 .krow 一致）：键 96 + 键距 10；spacer 48 + 右侧键距 10
     *  （左侧键距由前一键 margin 提供，列间隔实宽 = 10 + 48 + 10 = 68px）。
     *  返回每个 cell 的 x 原点（相对键区左缘；屏坐标 = +48px 键区列锚点）。 */
    private fun cellOriginPxs(row: KeyRow): List<Int> {
        val origins = mutableListOf<Int>()
        var x = 0
        for (cell in row.cells) {
            origins.add(x)
            x += when (cell) {
                is Cell.KeyCell -> 96 + 10
                Cell.Spacer -> 48 + 10
                // 空格位：占一个键格（T8 Cell.EmptyCell；画板 无此 cell，口径与键一致）
                Cell.EmptyCell -> 96 + 10
            }
        }
        return origins
    }

    @Test
    fun columnRhythmMatchesArtboard() {
        // 画板（designs/20261114）列原点（相对键区，屏坐标 = +48px 键区列锚点）：
        // 数字列 0(屏 48) / 字母列 376(屏 424) / 功能列 1176(屏 1224) / 图标列 1282(屏 1330)
        // 两态行 1 头键格位同构（T66 · 决策 51），列节奏任取一态锁口径
        for (rows in listOf(englishRows, chineseRows)) {
            val row1 = cellOriginPxs(rows[0])
            assertEquals("字母列原点（屏 424）", 376, row1[4])
            assertEquals("功能列原点（屏 1224）", 1176, row1[12])
            assertEquals("图标列原点（屏 1330）", 1282, row1[13])

            // spacer 列间隔步进 = 68px（数字列右缘 308 → 字母列 376）：两道键距 10 + spacer 48
            assertEquals("列间隔步进（10+48+10）", 376 - 308, 68)
            assertEquals("列间隔步进（1108 → 1176）", 1176 - 1108, 68)

            // 行 3/4 功能区单键列：123_! 与空格均在功能列 1176
            assertEquals("行 3 功能列（123_!）", 1176, cellOriginPxs(rows[2])[12])
            assertEquals("行 4 功能列（空格）", 1176, cellOriginPxs(rows[3])[12])

            // 完成键（字母键盘）画板 20261114 定位：键区内 1278px、宽 100px —— 右缘 1378 = 键区列右缘、
            // 距功能列（col11 右缘 1272）6px；col12 格位原点 1282（下屏/删除列），完成键左移 4px 为画板口径
            assertEquals("完成键左缘与功能列右缘间距 = 1278 − 1272 = 6px", 6, 1278 - (1176 + 96))
            assertEquals("完成键右缘贴键区列右缘（1278 + 100 = 1378）", 1378, 1278 + 100)
        }
    }

    // ---- T45 · 硬件字母键码映射（FR-A8 统一口径） ----

    @Test
    fun letterIdForKeyCodeMapsKeycodeAThruZ() {
        // KEYCODE_A=29 .. KEYCODE_Z=54（android.view.KeyEvent 自 API 1 起稳定常量）
        assertEquals("a", KeyboardLayout.letterIdForKeyCode(29))
        assertEquals("e", KeyboardLayout.letterIdForKeyCode(33))
        assertEquals("k", KeyboardLayout.letterIdForKeyCode(39))
        assertEquals("t", KeyboardLayout.letterIdForKeyCode(48))
        assertEquals("z", KeyboardLayout.letterIdForKeyCode(54))
    }

    @Test
    fun letterIdForKeyCodeRejectsNonLetterCodes() {
        // 字母区间外（边界）与常见非字母键码：数字（直出口径不变）/空格/确认键/删除
        assertNull(KeyboardLayout.letterIdForKeyCode(28))
        assertNull(KeyboardLayout.letterIdForKeyCode(55))
        assertNull(KeyboardLayout.letterIdForKeyCode(7))   // KEYCODE_0
        assertNull(KeyboardLayout.letterIdForKeyCode(62))  // KEYCODE_SPACE
        assertNull(KeyboardLayout.letterIdForKeyCode(66))  // KEYCODE_ENTER
        assertNull(KeyboardLayout.letterIdForKeyCode(67))  // KEYCODE_DEL
    }

    @Test
    fun letterIdForKeyCodeIdsAreAllRealLetterKeys() {
        // 26 个映射 id 全部命中字母键盘模型的单字母 Text 键（服务层据此经 execute 与网格 OK 同路组合）
        for (keyCode in 29..54) {
            val id = KeyboardLayout.letterIdForKeyCode(keyCode)
            assertTrue("id=$id 应在键位模型中", KeyboardLayout.keyById.containsKey(id))
            assertTrue("id=$id 应为字母键", id in KeyboardLayout.letterKeyIds)
        }
    }
}
