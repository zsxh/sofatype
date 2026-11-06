// T8 · 符号键盘模型单元测试（JUnit 4，纯 Kotlin，不依赖 Android UI 与模拟器）。
// 锁定 FR-A5（进出/无大写键无中英键）、FR-A6（全角 30/半角 28 符号集，PRD 97~98 行原文逐字符）、
// 决策 3（返回键行 2、空格/完成跨行对称）、决策 27（无 .com/@、全角 30 符号键位微调）、
// 以及符号键盘焦点导航格网（T6 基座口径：逐格移动不跳变、表缘不动、全键可达）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolsLayoutTest {

    /** PRD FR-A6 全角符号集（30 个，PRD 97 行原文逐字符）。 */
    private val prdFullWidth: Set<String> = setOf(
        "～", "！", "＃", "￥", "％", "……", "＆",
        "？", "（", "）", "——", "＋", "＝", "×",
        "；", "：", "‘", "’", "“", "”", "，",
        "。", "／", "＊", "「", "」", "《", "》", "｜", "＼",
    )

    /** PRD FR-A6 半角符号集（28 个，PRD 98 行原文逐字符）。 */
    private val prdHalfWidth: Set<String> = setOf(
        "~", "!", "#", "$", "^", "%", "&",
        "?", "(", ")", "_", "-", "+", "=",
        ";", ":", "'", "\"", ",", ".", "/",
        "*", "[", "]", "{", "}", "|", "\\",
    )

    private fun keysOf(width: SymbolsWidth): List<Key> = SymbolsLayout.allKeys(width)
    private fun idsOf(width: SymbolsWidth): Set<String> = keysOf(width).map { it.id }.toSet()

    // ---- FR-A6：全角 30 / 半角 28 符号集逐字符核对 ----

    @Test
    fun fullWidthCoversAll30PrdSymbols() {
        val symbols = keysOf(SymbolsWidth.FULL_WIDTH)
            .mapNotNull { (it.action as? KeyAction.Text)?.value }
            .filter { it.length != 1 || it[0] !in '0'..'9' }
            .toSet()
        assertEquals("全角符号应与 PRD 集合逐字符一致（30 个）", prdFullWidth, symbols)
        assertEquals(30, prdFullWidth.size)
    }

    @Test
    fun halfWidthCoversAll28PrdSymbols() {
        val symbols = keysOf(SymbolsWidth.HALF_WIDTH)
            .mapNotNull { (it.action as? KeyAction.Text)?.value }
            .filter { it.length != 1 || it[0] !in '0'..'9' }
            .toSet()
        assertEquals("半角符号应与 PRD 集合逐字符一致（28 个）", prdHalfWidth, symbols)
        assertEquals(28, prdHalfWidth.size)
    }

    @Test
    fun symbolKeysEmitTheirOwnCharacter() {
        // 符号键直出自身字符（FR-A6），不受字母键盘大小写状态影响
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            for (key in keysOf(width)) {
                val text = key.action as? KeyAction.Text ?: continue
                if (text.value == "0") continue
                assertEquals("符号键 ${key.id} 输出值应为键面字符", text.value, key.label)
            }
        }
    }

    // ---- FR-A5 / 决策 27：无大写键、无中/英键、无 .com、无 @ ----

    @Test
    fun symbolsKeyboardHasNoShiftLangDotComAtKeys() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val ids = idsOf(width)
            assertTrue("符号键盘不应有大写键", "shift" !in ids)
            assertTrue("符号键盘不应有中/英键", "lang" !in ids)
            assertTrue("符号键盘不应有 .com 键（决策 27）", ids.none { it.endsWith(".com") || it == "dot_com" })
            assertTrue("符号键盘不应有 @ 键（决策 27）", ids.none { it == "at" || it.endsWith("@") })
            val labels = keysOf(width).mapNotNull { it.label }
            assertTrue("键面不应出现 .com", ".com" !in labels)
            assertTrue("键面不应出现 @", "@" !in labels)
            assertTrue("键面不应出现大写字母（符号/数字除外）", labels.none { it.length == 1 && it[0] in 'A'..'Z' })
        }
    }

    @Test
    fun symbolsKeyboardHasToggleWidthBackSpaceDoneKeys() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val ids = idsOf(width)
            assertTrue("应有全/半切换键", SymbolsLayout.TOGGLE_WIDTH_ID in ids)
            assertTrue("应有返回键", SymbolsLayout.BACK_ID in ids)
            assertTrue("应有空格键", SymbolsLayout.SPACE_ID in ids)
            assertTrue("应有完成键", SymbolsLayout.DONE_ID in ids)
            assertTrue("应有下屏键", "hide" in ids)
            assertTrue("应有删除键", "delete" in ids)
        }
    }

    @Test
    fun toggleWidthAndBackActionsAreWired() {
        val byId = keysOf(SymbolsWidth.FULL_WIDTH).associateBy { it.id }
        assertEquals(KeyAction.ToggleWidth, byId.getValue(SymbolsLayout.TOGGLE_WIDTH_ID).action)
        assertEquals(KeyAction.BackToLetters, byId.getValue(SymbolsLayout.BACK_ID).action)
        assertEquals(KeyAction.Space, SymbolsLayout.spaceKey.action)
        assertEquals(KeyAction.Done, SymbolsLayout.doneKey.action)
        // FR-A5 画板 aria：全/半与返回键态提示
        assertEquals("全角半角切换", byId.getValue(SymbolsLayout.TOGGLE_WIDTH_ID).contentDescription)
        assertEquals("返回字母键盘", byId.getValue(SymbolsLayout.BACK_ID).contentDescription)
    }

    // ---- 决策 3：返回键行 2、空格/完成跨行对称（col11/col12 行 3/4） ----

    @Test
    fun backKeyIsOnRowTwo() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val navigator = FocusNavigator(SymbolsLayout.rows(width), SymbolsLayout.overlayCells())
            val backCol = navigator.positionsOf(SymbolsLayout.BACK_ID).single()
            assertEquals("返回键应位于第二行（决策 3）", 2, backCol.first)
            assertEquals("返回键应位于功能列 col11（画板③）", 11, backCol.second)
        }
    }

    @Test
    fun spaceAndDoneSpanRowsThreeAndFour() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val navigator = FocusNavigator(SymbolsLayout.rows(width), SymbolsLayout.overlayCells())
            assertEquals(
                "空格键应跨行 3/4 col11（决策 3）",
                SymbolsLayout.spaceKeyCells,
                navigator.positionsOf(SymbolsLayout.SPACE_ID).sortedBy { it.first },
            )
            assertEquals(
                "完成键应跨行 3/4 col12（决策 3，与空格对称）",
                SymbolsLayout.doneKeyCells,
                navigator.positionsOf(SymbolsLayout.DONE_ID).sortedBy { it.first },
            )
        }
    }

    // ---- 格网结构（4 行 12 列，与字母键盘同构） ----

    @Test
    fun fullWidthGridCoversAllCells() {
        val navigator = FocusNavigator(SymbolsLayout.rows(SymbolsWidth.FULL_WIDTH), SymbolsLayout.overlayCells())
        for (row in 1..4) {
            for (col in 1..12) {
                assertTrue("全角格位 ($row, $col) 应有键", navigator.keyAt(row, col) != null)
            }
        }
    }

    @Test
    fun halfWidthRowFourHasEmptyCellsAtCol2And3() {
        // 决策 27：.com/@ 移除后半角行 4 仅 0（col1）+ 7 符号（col4-10），col2/3 为空格位
        val navigator = FocusNavigator(SymbolsLayout.rows(SymbolsWidth.HALF_WIDTH), SymbolsLayout.overlayCells())
        assertTrue(navigator.keyAt(4, 1) == "digit_0")
        assertNull(navigator.keyAt(4, 2))
        assertNull(navigator.keyAt(4, 3))
        for (col in 4..10) assertTrue("半角行 4 col$col 应有符号", navigator.keyAt(4, col) != null)
    }

    @Test
    fun digitColumnRunsThroughOneToZero() {
        // 数字列贯通（FR-A9 数字输入 + 导航列关系）：1-4-7-0 同列
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val navigator = FocusNavigator(SymbolsLayout.rows(width), SymbolsLayout.overlayCells())
            assertEquals("digit_1", navigator.keyAt(1, 1))
            assertEquals("digit_4", navigator.keyAt(2, 1))
            assertEquals("digit_7", navigator.keyAt(3, 1))
            assertEquals("digit_0", navigator.keyAt(4, 1))
            assertEquals("digit_1", navigator.initialKey())
        }
    }

    @Test
    fun initialFocusIsDigitOne() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val navigator = FocusNavigator(SymbolsLayout.rows(width), SymbolsLayout.overlayCells())
            assertEquals("digit_1", navigator.initialKey())
        }
    }

    // ---- 焦点导航（T6 基座口径） ----

    @Test
    fun movesStayAdjacentInGrid() {
        // 左右同行、上下同列相邻格位；跨行键取相邻格位；表缘不动（FR-A11 不跳变）
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val navigator = FocusNavigator(SymbolsLayout.rows(width), SymbolsLayout.overlayCells())
            val allKeys = keysOf(width).map { it.id }
            for (keyId in allKeys) {
                val from = navigator.positionsOf(keyId).minByOrNull { it.first }!!
                for (dir in listOf(FocusDirection.LEFT, FocusDirection.RIGHT)) {
                    val next = navigator.next(keyId, dir) ?: continue
                    val dCol = if (dir == FocusDirection.LEFT) -1 else 1
                    assertTrue(
                        "键 $keyId $dir 应落在相邻格位",
                        navigator.positionsOf(next).any { it.first == from.first && it.second == from.second + dCol },
                    )
                }
                for (dir in listOf(FocusDirection.UP, FocusDirection.DOWN)) {
                    val next = navigator.next(keyId, dir) ?: continue
                    val dRow = if (dir == FocusDirection.UP) -1 else 1
                    assertTrue(
                        "键 $keyId $dir 应落在相邻格位",
                        navigator.positionsOf(next).any { it.first == from.first + dRow && it.second == from.second },
                    )
                }
            }
        }
    }

    @Test
    fun edgeMovesKeepFocus() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val navigator = FocusNavigator(SymbolsLayout.rows(width), SymbolsLayout.overlayCells())
            assertNull(navigator.next("digit_1", FocusDirection.LEFT))
            assertNull(navigator.next("digit_1", FocusDirection.UP))
            // 行 1 col12（下屏）RIGHT 为表缘
            assertNull(navigator.next("hide", FocusDirection.RIGHT))
            // 完成键 DOWN（行 4 格位为自身）与 RIGHT（表缘）均不动
            assertNull(navigator.next(SymbolsLayout.DONE_ID, FocusDirection.DOWN))
            assertNull(navigator.next(SymbolsLayout.DONE_ID, FocusDirection.RIGHT))
        }
    }

    @Test
    fun spaceAndDoneReachableFromFunctionColumn() {
        // 全/半 DOWN → 返回；下屏 DOWN → 删除；删除 DOWN → 完成；完成 LEFT → 空格
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val navigator = FocusNavigator(SymbolsLayout.rows(width), SymbolsLayout.overlayCells())
            assertEquals(SymbolsLayout.BACK_ID, navigator.next(SymbolsLayout.TOGGLE_WIDTH_ID, FocusDirection.DOWN))
            assertEquals("delete", navigator.next("hide", FocusDirection.DOWN))
            assertEquals(SymbolsLayout.DONE_ID, navigator.next("delete", FocusDirection.DOWN))
            assertEquals(SymbolsLayout.SPACE_ID, navigator.next(SymbolsLayout.DONE_ID, FocusDirection.LEFT))
        }
    }

    @Test
    fun allKeysReachableFromInitialFocus() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val navigator = FocusNavigator(SymbolsLayout.rows(width), SymbolsLayout.overlayCells())
            val visited = mutableSetOf(navigator.initialKey())
            val queue = ArrayDeque(visited)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (dir in FocusDirection.entries) {
                    val next = navigator.next(current, dir)
                    if (next != null && visited.add(next)) queue.addLast(next)
                }
            }
            assertEquals("从初始键应可达全部键位", keysOf(width).map { it.id }.toSet(), visited)
        }
    }

    // ---- 列节奏（画板③ 口径，T29 锁定：行 1/2 功能区与浮层键/字母键盘同列对齐） ----

    /** 键区水平排布口径（1080p px，与 KeyboardLayoutTest.cellOriginPxs 一致）：
     *  键 80 + 键距 12；spacer 42 + 右侧键距 12（列间隔实宽 = 12+42+12 = 66px）。
     *  返回每个 cell 的 x 原点（相对键区左缘；屏坐标 = +108px 键区偏移）。 */
    private fun cellOriginPxs(row: KeyRow): List<Int> {
        val origins = mutableListOf<Int>()
        var x = 0
        for (cell in row.cells) {
            origins.add(x)
            x += when (cell) {
                is Cell.KeyCell -> 80 + 12
                Cell.Spacer -> 42 + 12
                Cell.EmptyCell -> 80 + 12
            }
        }
        return origins
    }

    @Test
    fun functionColumnOriginsMatchLettersKeyboardAndOverlays() {
        // 画板③ 列原点（相对键区）：数字列 0 / 符号列 330-882（col4-10）/
        // 功能列 1028（屏 1136，= 浮层空格 1028）/ 图标列 1120（屏 1228，= 浮层完成键左缘）。
        // T8 回归根因：行 1/2 符号区后漏 Spacer，功能区流式键左移 54px 与浮层键错位。
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val rows = SymbolsLayout.rows(width)
            val row1 = cellOriginPxs(rows[0])
            assertEquals("符号区首键应落 col4 原点 330", 330, row1[4])
            assertEquals("符号区末键应落 col10 原点 882", 882, row1[10])
            assertEquals("全/半应在功能列原点 1028（与浮层空格同列线）", 1028, row1[12])
            assertEquals("下屏应在图标列原点 1120（与浮层完成键左缘对齐）", 1120, row1[13])
            val row2 = cellOriginPxs(rows[1])
            assertEquals("返回应在功能列原点 1028", 1028, row2[12])
            assertEquals("删除应在图标列原点 1120", 1120, row2[13])
            // 与字母键盘同构：行 1/2 列原点逐位一致（数字列/符号区/两道列间隔/功能区全同节奏）
            assertEquals("符号行 1 与字母行 1 列原点逐位一致", cellOriginPxs(KeyboardLayout.rows[0]), row1)
            assertEquals("符号行 2 与字母行 2 列原点逐位一致", cellOriginPxs(KeyboardLayout.rows[1]), row2)
            // 行 3 前缀节奏一致（col11/12 无行内键，由浮层键声明）
            val lettersRow3 = cellOriginPxs(KeyboardLayout.rows[2])
            assertEquals(
                "符号行 3 与字母行 3 列原点前缀一致",
                lettersRow3.take(rows[2].cells.size),
                cellOriginPxs(rows[2]),
            )
        }
    }

    // ---- 全/半切换的布局差异（决策 27 微调落点） ----

    @Test
    fun fullWidthRowFourHasMoreKeysThanHalfWidth() {
        // 全角行 4 = 0 + 9 符号（。／＊「」《》｜＼）＝ 10 键；半角行 4 = 0 + 7 符号（*[]{}|\）＝ 8 键
        val fullFlow = SymbolsLayout.rows(SymbolsWidth.FULL_WIDTH)[3].cells.count { it is Cell.KeyCell }
        val halfFlow = SymbolsLayout.rows(SymbolsWidth.HALF_WIDTH)[3].cells.count { it is Cell.KeyCell }
        assertEquals(10, fullFlow)
        assertEquals(8, halfFlow)
        val fullWidth = SymbolsLayout.rows(SymbolsWidth.FULL_WIDTH)[3]
        val symbolsInRow4 = fullWidth.cells.filterIsInstance<Cell.KeyCell>()
            .mapNotNull { (it.key.action as? KeyAction.Text)?.value }
            .filter { it != "0" }
        assertEquals(listOf("。", "／", "＊", "「", "」", "《", "》", "｜", "＼"), symbolsInRow4)
    }
}
