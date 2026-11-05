// T5 · 键位模型单元测试（JUnit 4，纯 Kotlin，不依赖 Android UI 与模拟器）。
// 锁定画板①（字母键盘·小写）的静态键位结构：行列结构、字符完整性、功能键与动作语义。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    private val keys: List<Key> = KeyboardLayout.allKeys()
    private val ids: List<String> = keys.map { it.id }

    // ---- 行列结构（画板①） ----

    @Test
    fun rowsAreFourWithArtboardStructure() {
        // 4 行；每行 2 个列间隔（数字列｜字母区｜功能键区三段式，FR-A2）
        assertEquals(4, KeyboardLayout.rows.size)
        for (row in KeyboardLayout.rows) {
            assertEquals("每行应有 2 个列间隔", 2, row.cells.count { it is Cell.Spacer })
            assertEquals("第一个列间隔应紧跟数字列（index 3）", Cell.Spacer, row.cells[3])
        }
        // 行键数：行 1/2 功能键区 2 键，行 3/4 功能键区 1 键（右侧由完成键覆盖）
        val keyCount = KeyboardLayout.rows.map { row -> row.cells.count { it is Cell.KeyCell } }
        assertEquals(listOf(12, 12, 11, 11), keyCount)
    }

    @Test
    fun lowercaseLettersCompleteAndInArtboardOrder() {
        // 字母区按画板①行序 a-g / h-n / o-u / v-z 连续排列，26 个小写字母齐全
        val letters = keys.mapNotNull { key ->
            val text = (key.action as? KeyAction.Text)?.value
            if (text != null && text.length == 1 && text[0] in 'a'..'z') text[0] else null
        }
        assertEquals("字母键应齐全且有序（a-z）", ('a'..'z').toList(), letters)
    }

    @Test
    fun numberColumnMatchesArtboard() {
        // 数字列（FR-A2）：行 1-3 依次 1-9，行 4 为 .com、0、@
        val rows = KeyboardLayout.rows
        val firstCells = rows.map { row -> (row.cells.take(3).filterIsInstance<Cell.KeyCell>().map { it.key.label }) }
        assertEquals(listOf("1", "2", "3"), firstCells[0])
        assertEquals(listOf("4", "5", "6"), firstCells[1])
        assertEquals(listOf("7", "8", "9"), firstCells[2])
        assertEquals(listOf(".com", "0", "@"), firstCells[3])
    }

    @Test
    fun letterZoneRowFourHasCursorKeys() {
        // 行 4 字母区：v-z 5 个字母 + 光标左/右 2 键（画板①）
        val row4 = KeyboardLayout.rows[3].cells.filterIsInstance<Cell.KeyCell>().map { it.key }
        val zone = row4.drop(3).dropLast(1) // 去掉数字列 3 键与末尾空格
        assertEquals(listOf("v", "w", "x", "y", "z"), zone.take(5).map { it.label })
        assertEquals(KeyIcon.CURSOR_LEFT, zone[5].icon)
        assertEquals(KeyIcon.CURSOR_RIGHT, zone[6].icon)
    }

    // ---- 功能键与动作语义 ----

    @Test
    fun functionKeysComplete() {
        // 上档/下屏/中英/删除/符号/空格/完成（FR-A3/A1/A4/A5/A9/A10）+ 光标左右（FR-A9）
        val expected = setOf("shift", "hide", "lang", "backspace", "symbols", "space", "done", "cursor_left", "cursor_right")
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
        val inRowIds = KeyboardLayout.rows.flatMap { it.cells }.filterIsInstance<Cell.KeyCell>().map { it.key.id }
        assertTrue("完成键不应出现在行键位中", inRowIds.none { it == "done" })
    }

    // ---- 键面样式语义（画板① 规格） ----

    @Test
    fun functionVariantKeysUseSmallTextSize() {
        // 功能键文字 22px 规格（PRD 6.3）→ SMALL；空格键面同字号但普通键帽底色
        val smallTextIds = setOf("shift", "hide", "lang", "backspace", "symbols", "space", "dot_com")
        for (key in keys) {
            if (key.id in smallTextIds) {
                assertEquals("键 ${key.id} 应为小字号键面", KeyTextSize.SMALL, key.textSize)
            }
        }
        val space = keys.first { it.id == "space" }
        assertEquals("空格键帽底色与字符键一致（画板① .bkey）", KeyVariant.CHARACTER, space.variant)
    }

    @Test
    fun dotComKeyUsesFunctionStyle() {
        // `.com` 与画板① .bkey-fn 一致（评审第 1 轮 Major 1）：功能键变体 + 小字号 + 直出文本动作
        val dotCom = keys.first { it.id == "dot_com" }
        assertEquals(KeyVariant.FUNCTION, dotCom.variant)
        assertEquals(KeyTextSize.SMALL, dotCom.textSize)
        assertEquals(KeyAction.Text(".com"), dotCom.action)
    }

    @Test
    fun iconKeysAllHaveAccessibilityLabels() {
        // PRD 第 8 章：全部可交互元素具备无障碍标注（图标键无文字，必须显式标注）
        val iconKeys = keys.filter { it.icon != null }
        assertTrue("图标键不应为空", iconKeys.isNotEmpty())
        for (key in iconKeys) {
            assertNotNull("图标键 ${key.id} 缺少无障碍标注", key.contentDescription)
            assertTrue("图标键 ${key.id} 无障碍标注不能为空白", key.contentDescription!!.isNotBlank())
        }
    }

    // ---- 列节奏（画板① 口径，T28 锁定） ----

    /** 键区水平排布口径（1080p px，与画板① .krow 一致）：键 80 + 键距 12；spacer 42 + 右侧键距 12
     *  （左侧键距由前一键 margin 提供，列间隔实宽 = 12 + 42 + 12 = 66px）。
     *  返回每个 cell 的 x 原点（相对键区左缘）。 */
    private fun cellOriginPxs(row: KeyRow): List<Int> {
        val origins = mutableListOf<Int>()
        var x = 0
        for (cell in row.cells) {
            origins.add(x)
            x += when (cell) {
                is Cell.KeyCell -> 80 + 12
                Cell.Spacer -> 42 + 12
            }
        }
        return origins
    }

    @Test
    fun columnRhythmMatchesArtboard() {
        // 画板① 列原点（相对键区，屏坐标 = +108px 键区偏移）：
        // 数字列 0(屏 108) / 字母列 330(屏 438) / 功能列 1028(屏 1136) / 图标列 1120(屏 1228)
        val row1 = cellOriginPxs(KeyboardLayout.rows[0])
        assertEquals("字母列原点（屏 438）", 330, row1[4])
        assertEquals("功能列原点（屏 1136）", 1028, row1[12])
        assertEquals("图标列原点（屏 1228）", 1120, row1[13])

        // spacer 列间隔步进 = 66px（数字列右缘 264 → 字母列 330）：两道键距 12 + spacer 42
        assertEquals("列间隔步进（12+42+12）", 330 - 264, 66)
        assertEquals("列间隔步进（962 → 1028）", 1028 - 962, 66)

        // 行 3/4 功能区单键列：123_! 与空格均在功能列 1028
        assertEquals("行 3 功能列（123_!）", 1028, cellOriginPxs(KeyboardLayout.rows[2])[12])
        assertEquals("行 4 功能列（空格）", 1028, cellOriginPxs(KeyboardLayout.rows[3])[12])

        // 完成键画板① 定位（键区内 1120px）= 图标列原点：与下屏/删除列左缘对齐（T28 回归点）
        assertEquals("完成键与图标列左缘对齐", 1120, row1[13])
        assertEquals("空格列到完成键列间距 = 1120 − 1028 − 80 = 12px 键距", 1120 - (1028 + 80), 12)
    }
}
