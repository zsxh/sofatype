package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolsLayoutTest {

    private val expectedFullMatrix: List<List<String>> = listOf(
        listOf("，", "。", "、", "？", "！", "；", "："),
        listOf("“", "”", "‘", "’", "（", "）", "《"),
        listOf("》", "【", "】", "…", "—", "～", "·"),
        listOf("／", "＿", "｜", "＠", "＃", "％", "＆"),
        listOf("＋", "＝", "＊", "＄", "＾", "｀", "￥"),
    )

    private val expectedHalfMatrix: List<List<String>> = listOf(
        listOf(",", ".", "\\", "?", "!", ";", ":"),
        listOf("\"", "\"", "'", "'", "(", ")", "<"),
        listOf(">", "[", "]", "...", "--", "~", "·"),
        listOf("/", "_", "|", "@", "#", "%", "&"),
        listOf("+", "=", "*", "$", "^", "`", "¥"),
    )

    private fun keysOf(width: SymbolsWidth, offset: Int = 0): List<Key> = SymbolsLayout.allKeys(width, offset)
    private fun idsOf(width: SymbolsWidth, offset: Int = 0): Set<String> = keysOf(width, offset).map { it.id }.toSet()

    private fun navigator(width: SymbolsWidth, offset: Int = 0): FocusNavigator =
        FocusNavigator(SymbolsLayout.rows(width, offset), SymbolsLayout.overlayCells())

    @Test
    fun fullWidthMatrixMatchesArtboards() {
        val matrix = SymbolsLayout.matrixKeys(SymbolsWidth.FULL_WIDTH)
        assertEquals("全角矩阵应为 5 行 × 7 列（35 键位）", 5, matrix.size)
        matrix.forEachIndexed { rowIndex, row ->
            assertEquals("全角 R${rowIndex + 1} 应 7 键", 7, row.size)
            row.forEachIndexed { colIndex, key ->
                val expected = expectedFullMatrix[rowIndex][colIndex]
                assertEquals("全角 R${rowIndex + 1}C${colIndex + 1} 键面应与原型一致", expected, key.label)
                assertEquals("全角 R${rowIndex + 1}C${colIndex + 1} 输出值应与键面一致", expected, (key.action as KeyAction.Text).value)
            }
        }
    }

    @Test
    fun halfWidthMatrixMatchesArtboards() {
        val matrix = SymbolsLayout.matrixKeys(SymbolsWidth.HALF_WIDTH)
        assertEquals("半角矩阵应为 5 行 × 7 列（35 键位）", 5, matrix.size)
        matrix.forEachIndexed { rowIndex, row ->
            assertEquals("半角 R${rowIndex + 1} 应 7 键", 7, row.size)
            row.forEachIndexed { colIndex, key ->
                val expected = expectedHalfMatrix[rowIndex][colIndex]
                assertEquals("半角 R${rowIndex + 1}C${colIndex + 1} 键面应与原型一致", expected, key.label)
                assertEquals("半角 R${rowIndex + 1}C${colIndex + 1} 输出值应与键面一致", expected, (key.action as KeyAction.Text).value)
            }
        }
    }

    @Test
    fun halfWidthR5C7IsHalfWidthYenSign() {

        assertEquals('¥'.toString(), SymbolsLayout.matrixKeys(SymbolsWidth.HALF_WIDTH)[4][6].label)
        assertEquals("￥", SymbolsLayout.matrixKeys(SymbolsWidth.FULL_WIDTH)[4][6].label)
    }

    @Test
    fun matrixKeyIdsAreUniquePerPage() {

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val ids = SymbolsLayout.allMatrixKeys(width).map { it.id }
            assertEquals("页内矩阵键 id 必须唯一（$width）", ids.size, ids.distinct().size)
        }
    }

    @Test
    fun symbolKeysHaveAccessibilityLabels() {

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            for (key in SymbolsLayout.allMatrixKeys(width)) {
                assertTrue("键 ${key.id} 无障碍标注不能为空白", key.contentDescription.isNotBlank())
                assertEquals("键 ${key.id} 标注应与键面一致", key.label, key.contentDescription)
            }
        }
    }

    @Test
    fun digitColumnMatchesLettersKeyboard() {

        for (offset in 0..SymbolsLayout.MAX_WINDOW_OFFSET) {
            for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
                val nav = navigator(width, offset)
                assertEquals("digit_1", nav.keyAt(1, 1))
                assertEquals("digit_4", nav.keyAt(2, 1))
                assertEquals("digit_7", nav.keyAt(3, 1))
                assertEquals(".com 应在显示行 4 col1（决策 45）", "dot_com", nav.keyAt(4, 1))
                assertEquals("digit_0 应在显示行 4 col2", "digit_0", nav.keyAt(4, 2))
                assertEquals("@ 应在显示行 4 col3（决策 45）", "at", nav.keyAt(4, 3))
            }
        }
    }

    @Test
    fun dotComKeyMatchesLettersKeyboardStyle() {

        val dotCom = keysOf(SymbolsWidth.FULL_WIDTH).first { it.id == "dot_com" }
        assertEquals(KeyVariant.FUNCTION, dotCom.variant)
        assertEquals(KeyTextSize.SMALL, dotCom.textSize)
        assertEquals(KeyAction.Text(".com"), dotCom.action)
    }

    @Test
    fun digitAndFunctionColumnsFixedAcrossWindows() {

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val defaultNav = navigator(width, 0)
            val bottomNav = navigator(width, SymbolsLayout.MAX_WINDOW_OFFSET)
            val fixedCols = listOf(1, 2, 3, 11, 12)
            for (row in 1..SymbolsLayout.VISIBLE_ROWS) {
                for (col in fixedCols) {
                    assertEquals(
                        "格位 ($row, $col) 应跨窗口固定（$width）",
                        defaultNav.keyAt(row, col),
                        bottomNav.keyAt(row, col),
                    )
                }
            }

            assertEquals("space", defaultNav.keyAt(3, 11))
            assertEquals("space", bottomNav.keyAt(3, 11))
        }
    }

    @Test
    fun defaultWindowShowsRealRowsOneToFour() {

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val nav = navigator(width, 0)
            val keyById = keysOf(width, 0).associateBy { it.id }
            val expected = if (width == SymbolsWidth.FULL_WIDTH) expectedFullMatrix else expectedHalfMatrix
            for (windowRow in 1..4) {
                for (col in 4..10) {
                    val key = nav.keyAt(windowRow, col)?.let { keyById[it] }
                    val expectedSym = expected[windowRow - 1][col - 4]
                    assertEquals("默认态显示行 $windowRow col$col 应为 R${windowRow}C${col - 3}（$width）", expectedSym, key?.let { (it.action as? KeyAction.Text)?.value })
                }
            }
            assertTrue("默认态下方还有真实行（R5）", SymbolsLayout.canStepDown(0))
            assertTrue("默认态上方无真实行", !SymbolsLayout.canStepUp(0))
        }
    }

    @Test
    fun steppedWindowShowsRealRowsTwoToFive() {

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val nav = navigator(width, 1)
            val keyById = keysOf(width, 1).associateBy { it.id }
            val expected = if (width == SymbolsWidth.FULL_WIDTH) expectedFullMatrix else expectedHalfMatrix
            for (windowRow in 1..4) {
                for (col in 4..10) {
                    val key = nav.keyAt(windowRow, col)?.let { keyById[it] }
                    val expectedSym = expected[windowRow][col - 4]
                    assertEquals("下移态显示行 $windowRow col$col 应为 R${windowRow + 1}C${col - 3}（$width）", expectedSym, key?.let { (it.action as? KeyAction.Text)?.value })
                }
            }
            assertTrue("下移态下方无真实行", !SymbolsLayout.canStepDown(1))
            assertTrue("下移态上方还有真实行（R1）", SymbolsLayout.canStepUp(1))
        }
    }

    @Test
    fun windowOffsetOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { SymbolsLayout.rows(SymbolsWidth.FULL_WIDTH, -1) }
        assertThrows(IllegalArgumentException::class.java) { SymbolsLayout.rows(SymbolsWidth.HALF_WIDTH, SymbolsLayout.MAX_WINDOW_OFFSET + 1) }
    }

    @Test
    fun windowStepDecisionMatchesArtboardInteraction() {
        val bottom = SymbolsLayout.MAX_WINDOW_OFFSET

        assertEquals(1, SymbolsLayout.windowStep(4, FocusDirection.DOWN, 0))
        assertNull("边界外按[下]不越界", SymbolsLayout.windowStep(4, FocusDirection.DOWN, bottom))

        assertEquals(0, SymbolsLayout.windowStep(1, FocusDirection.UP, bottom))
        assertNull("边界外按[上]不越界", SymbolsLayout.windowStep(1, FocusDirection.UP, 0))

        for (anchorRow in 2..3) {
            assertNull(SymbolsLayout.windowStep(anchorRow, FocusDirection.DOWN, 0))
            assertNull(SymbolsLayout.windowStep(anchorRow, FocusDirection.UP, bottom))
        }

        assertNull(SymbolsLayout.windowStep(3, FocusDirection.DOWN, 0))
        assertNull(SymbolsLayout.windowStep(3, FocusDirection.UP, bottom))
    }

    @Test
    fun realRowIndexMapsWindowRowToMatrix() {
        assertEquals(0, SymbolsLayout.realRowIndex(1, 0))
        assertEquals(3, SymbolsLayout.realRowIndex(4, 0))
        assertEquals(1, SymbolsLayout.realRowIndex(1, 1))
        assertEquals(4, SymbolsLayout.realRowIndex(4, 1))
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

            assertTrue("符号键盘不应有大写键", "shift" !in ids)
            assertTrue("符号键盘不应有中/英键", "lang" !in ids)
        }
    }

    @Test
    fun toggleWidthAndBackActionsAreWired() {
        val byId = keysOf(SymbolsWidth.FULL_WIDTH).associateBy { it.id }
        assertEquals(KeyAction.ToggleWidth, byId.getValue(SymbolsLayout.TOGGLE_WIDTH_ID).action)
        assertEquals(KeyAction.BackToLetters, byId.getValue(SymbolsLayout.BACK_ID).action)
        assertEquals(KeyAction.Space, SymbolsLayout.spaceKey.action)
        assertEquals(KeyAction.Done, SymbolsLayout.doneKey.action)

        assertEquals("全角半角切换", byId.getValue(SymbolsLayout.TOGGLE_WIDTH_ID).contentDescription)
        assertEquals("返回字母键盘", byId.getValue(SymbolsLayout.BACK_ID).contentDescription)
    }

    @Test
    fun backKeyIsOnRowTwo() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val backCol = navigator(width).positionsOf(SymbolsLayout.BACK_ID).single()
            assertEquals("返回键应位于第二行（决策 3）", 2, backCol.first)
            assertEquals("返回键应位于功能列 col11（画板 20261114）", 11, backCol.second)
        }
    }

    @Test
    fun spaceAndDoneSpanRowsThreeAndFour() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val nav = navigator(width)
            assertEquals(
                "空格键应跨行 3/4 col11（决策 3）",
                SymbolsLayout.spaceKeyCells,
                nav.positionsOf(SymbolsLayout.SPACE_ID).sortedBy { it.first },
            )
            assertEquals(
                "完成键应跨行 3/4 col12（决策 3，与空格对称）",
                SymbolsLayout.doneKeyCells,
                nav.positionsOf(SymbolsLayout.DONE_ID).sortedBy { it.first },
            )
        }
    }

    @Test
    fun windowGridCoversAllCells() {

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            for (offset in 0..SymbolsLayout.MAX_WINDOW_OFFSET) {
                val nav = navigator(width, offset)
                for (row in 1..4) {
                    for (col in 1..12) {
                        assertTrue("格位 ($row, $col) 应有键（$width offset=$offset）", nav.keyAt(row, col) != null)
                    }
                }
            }
        }
    }

    @Test
    fun initialFocusIsDigitOne() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            for (offset in 0..SymbolsLayout.MAX_WINDOW_OFFSET) {
                assertEquals("digit_1", navigator(width, offset).initialKey())
            }
        }
    }

    @Test
    fun movesStayInLineToNearestKey() {

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            for (offset in 0..SymbolsLayout.MAX_WINDOW_OFFSET) {
                val nav = navigator(width, offset)
                val allKeys = keysOf(width, offset).map { it.id }
                for (keyId in allKeys) {
                    val from = nav.positionsOf(keyId).minByOrNull { it.first }!!
                    for (dir in FocusDirection.entries) {
                        val next = nav.next(keyId, dir) ?: continue
                        val horizontal = dir == FocusDirection.LEFT || dir == FocusDirection.RIGHT
                        val delta = if (dir == FocusDirection.LEFT || dir == FocusDirection.UP) -1 else 1
                        val target = nav.positionsOf(next).firstOrNull {
                            if (horizontal) it.first == from.first && (it.second - from.second) * delta > 0
                            else it.second == from.second && (it.first - from.first) * delta > 0
                        }
                        assertTrue("键 $keyId $dir 目标 $next 应落在方向侧同线", target != null)
                        val between = if (horizontal) {
                            if (delta > 0) (from.second + 1) until target!!.second else (target!!.second + 1) until from.second
                        } else {
                            if (delta > 0) (from.first + 1) until target!!.first else (target!!.first + 1) until from.first
                        }.map { if (horizontal) from.first to it else it to from.second }
                        assertTrue(
                            "键 $keyId $dir 与目标 $next 之间不得夹实键（不跳变）",
                            between.all { nav.keyAt(it.first, it.second) == null },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun rowFourNavigatesCellByCellWithoutEmptyCells() {

        val full = navigator(SymbolsWidth.FULL_WIDTH, 0)
        assertEquals("fw_r4c1", full.next("at", FocusDirection.RIGHT))
        assertEquals("at", full.next("fw_r4c1", FocusDirection.LEFT))
        assertEquals("fw_r4c3", full.next("fw_r4c2", FocusDirection.RIGHT))
        assertEquals("fw_r4c1", full.next("fw_r4c2", FocusDirection.LEFT))

        val halfBottom = navigator(SymbolsWidth.HALF_WIDTH, 1)
        assertEquals("hw_r5c2", halfBottom.next("hw_r5c1", FocusDirection.RIGHT))
        assertEquals("hw_r5c1", halfBottom.next("hw_r5c2", FocusDirection.LEFT))
    }

    @Test
    fun edgeMovesKeepFocus() {

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val nav = navigator(width)
            assertNull(nav.next("digit_1", FocusDirection.LEFT))
            assertNull(nav.next("digit_1", FocusDirection.UP))
            assertNull(nav.next("hide", FocusDirection.RIGHT))
            assertNull(nav.next(SymbolsLayout.DONE_ID, FocusDirection.DOWN))
            assertNull(nav.next(SymbolsLayout.DONE_ID, FocusDirection.RIGHT))

            val bottomNav = navigator(width, SymbolsLayout.MAX_WINDOW_OFFSET)
            assertNull(bottomNav.next("dot_com", FocusDirection.DOWN))
            assertNull(bottomNav.next(if (width == SymbolsWidth.HALF_WIDTH) "hw_r5c7" else "fw_r5c7", FocusDirection.DOWN))
            assertEquals(SymbolsLayout.SPACE_ID, bottomNav.next(if (width == SymbolsWidth.HALF_WIDTH) "hw_r5c7" else "fw_r5c7", FocusDirection.RIGHT))
            assertEquals(SymbolsLayout.DONE_ID, bottomNav.next(SymbolsLayout.SPACE_ID, FocusDirection.RIGHT))
            assertNull(bottomNav.next(SymbolsLayout.DONE_ID, FocusDirection.RIGHT))
        }
    }

    @Test
    fun spaceAndDoneReachableFromFunctionColumn() {

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val nav = navigator(width)
            assertEquals(SymbolsLayout.BACK_ID, nav.next(SymbolsLayout.TOGGLE_WIDTH_ID, FocusDirection.DOWN))
            assertEquals("delete", nav.next("hide", FocusDirection.DOWN))
            assertEquals(SymbolsLayout.DONE_ID, nav.next("delete", FocusDirection.DOWN))
            assertEquals(SymbolsLayout.SPACE_ID, nav.next(SymbolsLayout.DONE_ID, FocusDirection.LEFT))
        }
    }

    @Test
    fun allKeysReachableFromInitialFocus() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            for (offset in 0..SymbolsLayout.MAX_WINDOW_OFFSET) {
                val nav = navigator(width, offset)
                val visited = mutableSetOf(nav.initialKey())
                val queue = ArrayDeque(visited)
                while (queue.isNotEmpty()) {
                    val current = queue.removeFirst()
                    for (dir in FocusDirection.entries) {
                        val next = nav.next(current, dir)
                        if (next != null && visited.add(next)) queue.addLast(next)
                    }
                }
                assertEquals(
                    "从初始键应可达全部键位（$width offset=$offset）",
                    keysOf(width, offset).map { it.id }.toSet(),
                    visited,
                )
            }
        }
    }

    @Test
    fun denseTextSizeForMultiCharHalfWidthKeys() {

        val dense = SymbolsLayout.allMatrixKeys(SymbolsWidth.HALF_WIDTH).filter { it.label!!.length >= 2 }
        assertEquals(setOf("...", "--"), dense.map { it.label }.toSet())
        assertTrue(dense.all { it.textSize == KeyTextSize.DENSE })
        val normal = SymbolsLayout.allMatrixKeys(SymbolsWidth.HALF_WIDTH).filter { it.label!!.length == 1 }
        assertTrue(normal.all { it.textSize == KeyTextSize.NORMAL })
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

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            for (offset in 0..SymbolsLayout.MAX_WINDOW_OFFSET) {
                val rows = SymbolsLayout.rows(width, offset)
                val row1 = cellOriginPxs(rows[0])
                assertEquals("符号区首键应落 col4 原点 376（$width offset=$offset）", 376, row1[4])
                assertEquals("符号区末键应落 col10 原点 1012", 1012, row1[10])
                assertEquals("全/半应在功能列原点 1176（与浮层空格同列线近邻）", 1176, row1[12])
                assertEquals("下屏应在图标列原点 1282（与浮层完成键左缘近邻）", 1282, row1[13])
                val row2 = cellOriginPxs(rows[1])
                assertEquals("返回应在功能列原点 1176", 1176, row2[12])
                assertEquals("删除应在图标列原点 1282", 1282, row2[13])

                assertEquals("符号行 1 与字母行 1 列原点逐位一致", cellOriginPxs(KeyboardLayout.rows(LanguageMode.ENGLISH)[0]), row1)
                assertEquals("符号行 2 与字母行 2 列原点逐位一致", cellOriginPxs(KeyboardLayout.rows(LanguageMode.ENGLISH)[1]), row2)

                val lettersRow3 = cellOriginPxs(KeyboardLayout.rows(LanguageMode.ENGLISH)[2])
                assertEquals(
                    "符号行 3 与字母行 3 列原点前缀一致",
                    lettersRow3.take(rows[2].cells.size),
                    cellOriginPxs(rows[2]),
                )

                val lettersRow4 = cellOriginPxs(KeyboardLayout.rows(LanguageMode.ENGLISH)[3])
                assertEquals(
                    "符号行 4 数字列段与字母行 4 列原点前缀一致（.com 0 @ + 列间隔）",
                    lettersRow4.take(4),
                    cellOriginPxs(rows[3]).take(4),
                )
                assertEquals("行 4 符号区首键应落 col4 原点 376", 376, cellOriginPxs(rows[3])[4])
                assertEquals("行 4 符号区末键应落 col10 原点 1012", 1012, cellOriginPxs(rows[3])[10])
            }
        }
    }
}
