// T64 · 符号键盘 7×5 同位语义矩阵单元测试（JUnit 4，纯 Kotlin，不依赖 Android UI 与模拟器）。
// 键位唯一依据 = 原型定稿 designs/20261110 画板 3/4（默认态 R1-R4 + 下箭头）与画板 10/11
//（下移态 R2-R5 + 上箭头），本文按画板原文逐字符锁定（决策 27 布局由 PRD 决策 45 推翻取代）。
// 锁定五件事：
// ① 全角/半角两页 7×5 真实矩阵逐位与原型一致（35 键位、同位置同语义）；
// ② 数字列与字母键盘对齐（1-9 + 第四行 .com 0 @，决策 45）且不随行步进窗口滚动；
// ③ 行步进窗口模型（界面恒显示 4 行、窗口偏移装配、越界拒绝、步进决策与边界不动）；
// ④ 列节奏 x 原点断言（HANDOFF 模拟器坑 7 口径：格网列号断言拦不住视觉错位）随新布局补充；
// ⑤ 显示窗口格网焦点导航回归（T58 同线最近实键口径 + 表缘不动 + 全键可达 + 决策 3 浮层键）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolsLayoutTest {

    /** 全角 7×5 真实矩阵期望（画板 3 显示 R1-R4 + 画板 10 显示 R5，原文逐字符）。 */
    private val expectedFullMatrix: List<List<String>> = listOf(
        listOf("，", "。", "、", "？", "！", "；", "："),
        listOf("“", "”", "‘", "’", "（", "）", "《"),
        listOf("》", "【", "】", "…", "—", "～", "·"),
        listOf("／", "＿", "｜", "＠", "＃", "％", "＆"),
        listOf("＋", "＝", "＊", "＄", "＾", "｀", "￥"),
    )

    /** 半角 7×5 真实矩阵期望（画板 4 显示 R1-R4 + 画板 11 显示 R5，原文逐字符）。 */
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

    // ---- ① 真实矩阵逐位（画板 3/4/10/11 原文逐字符） ----

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
        // 画板 11 半角 R5C7 = U+00A5（半角 ¥），区别于全角页 R5C7 = U+FFE5（￥）
        assertEquals('¥'.toString(), SymbolsLayout.matrixKeys(SymbolsWidth.HALF_WIDTH)[4][6].label)
        assertEquals("￥", SymbolsLayout.matrixKeys(SymbolsWidth.FULL_WIDTH)[4][6].label)
    }

    @Test
    fun matrixKeyIdsAreUniquePerPage() {
        // 半角 R2 直引号同字符占两格，键 id 必须按格位编号保唯一
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val ids = SymbolsLayout.allMatrixKeys(width).map { it.id }
            assertEquals("页内矩阵键 id 必须唯一（$width）", ids.size, ids.distinct().size)
        }
    }

    @Test
    fun symbolKeysHaveAccessibilityLabels() {
        // T26 全键位口径：键面 = 输出 = 无障碍标注（含显示窗口外行）
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            for (key in SymbolsLayout.allMatrixKeys(width)) {
                assertTrue("键 ${key.id} 无障碍标注不能为空白", key.contentDescription.isNotBlank())
                assertEquals("键 ${key.id} 标注应与键面一致", key.label, key.contentDescription)
            }
        }
    }

    // ---- ② 数字列与字母键盘对齐（决策 45），且不随窗口滚动 ----

    @Test
    fun digitColumnMatchesLettersKeyboard() {
        // 显示 4 行数字列 = 1-3 / 4-6 / 7-9 / .com 0 @（与字母键盘逐行一致，决策 45）
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
        // .com 与字母键盘行 4 同构：功能键样式 + 小字号 + 直出文本（决策 45）
        val dotCom = keysOf(SymbolsWidth.FULL_WIDTH).first { it.id == "dot_com" }
        assertEquals(KeyVariant.FUNCTION, dotCom.variant)
        assertEquals(KeyTextSize.SMALL, dotCom.textSize)
        assertEquals(KeyAction.Text(".com"), dotCom.action)
    }

    @Test
    fun digitAndFunctionColumnsFixedAcrossWindows() {
        // 数字列与功能键列固定不随窗口滚动：两窗口态下同格位同键 id
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
            // 行 3 col11/12 由浮层键声明（空格/完成），两窗口一致
            assertEquals("space", defaultNav.keyAt(3, 11))
            assertEquals("space", bottomNav.keyAt(3, 11))
        }
    }

    // ---- ③ 行步进窗口模型 ----

    @Test
    fun defaultWindowShowsRealRowsOneToFour() {
        // 默认态（offset=0）= 原型画板 3/4：显示 R1-R4 + 仅下箭头
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
        // 下移态（offset=1）= 原型画板 10/11：显示 R2-R5 + 仅上箭头
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
        // 显示第 4 行按[下]：默认态步进下移、下移态（边界外）不动
        assertEquals(1, SymbolsLayout.windowStep(4, FocusDirection.DOWN, 0))
        assertNull("边界外按[下]不越界", SymbolsLayout.windowStep(4, FocusDirection.DOWN, bottom))
        // 显示第 1 行按[上]：下移态步进回移、默认态（边界外）不动
        assertEquals(0, SymbolsLayout.windowStep(1, FocusDirection.UP, bottom))
        assertNull("边界外按[上]不越界", SymbolsLayout.windowStep(1, FocusDirection.UP, 0))
        // 格网内正常导航不触发步进（锚行非窗口底/顶缘）
        for (anchorRow in 2..3) {
            assertNull(SymbolsLayout.windowStep(anchorRow, FocusDirection.DOWN, 0))
            assertNull(SymbolsLayout.windowStep(anchorRow, FocusDirection.UP, bottom))
        }
        // 跨行浮层键（空格/完成）锚行为 3：不满足触发行，语义不变
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

    // ---- 功能键与浮层键（决策 3 口径延续） ----

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
            // FR-A5：符号键盘无大写键、无中/英键
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
        // FR-A5 画板 aria：全/半与返回键态提示
        assertEquals("全角半角切换", byId.getValue(SymbolsLayout.TOGGLE_WIDTH_ID).contentDescription)
        assertEquals("返回字母键盘", byId.getValue(SymbolsLayout.BACK_ID).contentDescription)
    }

    @Test
    fun backKeyIsOnRowTwo() {
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val backCol = navigator(width).positionsOf(SymbolsLayout.BACK_ID).single()
            assertEquals("返回键应位于第二行（决策 3）", 2, backCol.first)
            assertEquals("返回键应位于功能列 col11（画板③）", 11, backCol.second)
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

    // ---- ④ 显示窗口格网完整性 + 焦点导航回归（T58 口径） ----

    @Test
    fun windowGridCoversAllCells() {
        // 显示窗口格网 4×12 全实键：新矩阵数字列贯通后无空格位（EmptyCell 消失），
        // col11/12 行 3/4 由浮层键声明
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
        // 左右 = 同行该方向最近实键（不越实键，T58）；上下 = 同列该方向最近实键；
        // 跨行键取方向侧格位；表缘不动（FR-A11 不跳变）。两页两窗口全键遍历。
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
        // T58 口径在新矩阵的形态：行 4 数字列贯通（.com 0 @）+ 符号区 7 键，逐格导航无空格位
        val full = navigator(SymbolsWidth.FULL_WIDTH, 0)
        assertEquals("fw_r4c1", full.next("at", FocusDirection.RIGHT))
        assertEquals("at", full.next("fw_r4c1", FocusDirection.LEFT))
        assertEquals("fw_r4c3", full.next("fw_r4c2", FocusDirection.RIGHT))
        assertEquals("fw_r4c1", full.next("fw_r4c2", FocusDirection.LEFT))
        // 半角下移态（画板 11）：显示行 4 = R5（＋半角 +），焦点落点逐格
        val halfBottom = navigator(SymbolsWidth.HALF_WIDTH, 1)
        assertEquals("hw_r5c2", halfBottom.next("hw_r5c1", FocusDirection.RIGHT))
        assertEquals("hw_r5c1", halfBottom.next("hw_r5c2", FocusDirection.LEFT))
    }

    @Test
    fun edgeMovesKeepFocus() {
        // 表缘（窗口格网缘）不动：步进由服务层窗口步进分支处理（模型决策见 windowStepDecisionMatchesArtboardInteraction）
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val nav = navigator(width)
            assertNull(nav.next("digit_1", FocusDirection.LEFT))
            assertNull(nav.next("digit_1", FocusDirection.UP))
            assertNull(nav.next("hide", FocusDirection.RIGHT))
            assertNull(nav.next(SymbolsLayout.DONE_ID, FocusDirection.DOWN))
            assertNull(nav.next(SymbolsLayout.DONE_ID, FocusDirection.RIGHT))
            // 下移态：显示行 4 = 真实 R5；显示第 4 行键 DOWN 为格网表缘 null（窗口步进由服务层
            // 决策分支承担，模型见 windowStepDecisionMatchesArtboardInteraction）；col10 RIGHT 落
            // 浮层空格（col11）为格网内正常导航；空格/完成 RIGHT 为格网表缘 null
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
        // 全/半 DOWN → 返回；下屏 DOWN → 删除；删除 DOWN → 完成；完成 LEFT → 空格
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
        // 画板 4：半角「...」「--」键面 24px（DENSE），其余矩阵键 30px（NORMAL）
        val dense = SymbolsLayout.allMatrixKeys(SymbolsWidth.HALF_WIDTH).filter { it.label!!.length >= 2 }
        assertEquals(setOf("...", "--"), dense.map { it.label }.toSet())
        assertTrue(dense.all { it.textSize == KeyTextSize.DENSE })
        val normal = SymbolsLayout.allMatrixKeys(SymbolsWidth.HALF_WIDTH).filter { it.label!!.length == 1 }
        assertTrue(normal.all { it.textSize == KeyTextSize.NORMAL })
    }

    // ---- ⑤ 列节奏 x 原点断言（HANDOFF 模拟器坑 7 口径，随新布局补充） ----

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
    fun columnRhythmMatchesArtboard() {
        // 画板③ 列原点（相对键区）：数字列 0 / 符号列 330-882（col4-10）/
        // 功能列 1028（屏 1136，= 浮层空格 1028）/ 图标列 1120（屏 1228，= 浮层完成键左缘）
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            for (offset in 0..SymbolsLayout.MAX_WINDOW_OFFSET) {
                val rows = SymbolsLayout.rows(width, offset)
                val row1 = cellOriginPxs(rows[0])
                assertEquals("符号区首键应落 col4 原点 330（$width offset=$offset）", 330, row1[4])
                assertEquals("符号区末键应落 col10 原点 882", 882, row1[10])
                assertEquals("全/半应在功能列原点 1028（与浮层空格同列线）", 1028, row1[12])
                assertEquals("下屏应在图标列原点 1120（与浮层完成键左缘对齐）", 1120, row1[13])
                val row2 = cellOriginPxs(rows[1])
                assertEquals("返回应在功能列原点 1028", 1028, row2[12])
                assertEquals("删除应在图标列原点 1120", 1120, row2[13])
                // 与字母键盘同构：行 1/2 列原点逐位一致（数字列/符号区/两道列间隔/功能区全同节奏）
                assertEquals("符号行 1 与字母行 1 列原点逐位一致", cellOriginPxs(KeyboardLayout.rows(LanguageMode.ENGLISH)[0]), row1)
                assertEquals("符号行 2 与字母行 2 列原点逐位一致", cellOriginPxs(KeyboardLayout.rows(LanguageMode.ENGLISH)[1]), row2)
                // 行 3 前缀节奏一致（col11/12 无行内键，由浮层键声明）
                val lettersRow3 = cellOriginPxs(KeyboardLayout.rows(LanguageMode.ENGLISH)[2])
                assertEquals(
                    "符号行 3 与字母行 3 列原点前缀一致",
                    lettersRow3.take(rows[2].cells.size),
                    cellOriginPxs(rows[2]),
                )
                // 行 4 数字列段与字母键盘行 4 前缀一致（.com 0 @，决策 45）
                val lettersRow4 = cellOriginPxs(KeyboardLayout.rows(LanguageMode.ENGLISH)[3])
                assertEquals(
                    "符号行 4 数字列段与字母行 4 列原点前缀一致（.com 0 @ + 列间隔）",
                    lettersRow4.take(4),
                    cellOriginPxs(rows[3]).take(4),
                )
                assertEquals("行 4 符号区首键应落 col4 原点 330", 330, cellOriginPxs(rows[3])[4])
                assertEquals("行 4 符号区末键应落 col10 原点 882", 882, cellOriginPxs(rows[3])[10])
            }
        }
    }
}
