package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusNavigatorTest {

    private val navigator = FocusNavigator(initialCell = KeyboardLayout.initialCell)

    private val allKeyIds: List<String> =
        (1..4).flatMap { row -> (1..12).mapNotNull { col -> navigator.keyAt(row, col) } }.distinct()

    @Test
    fun gridCoversAllCellsExactlyOnce() {
        for (row in 1..4) {
            for (col in 1..12) {
                val keyId = navigator.keyAt(row, col)
                assertTrue("格位 ($row, $col) 应恰有一个键", keyId != null)
            }
        }
        assertEquals("键位总数 = 行内键 + 完成键", 47, allKeyIds.size)
    }

    @Test
    fun doneKeySpansRowThreeAndFourOfIconColumn() {

        val iconCol = navigator.positionsOf("hide").single().second
        assertEquals(12, iconCol)
        assertEquals(listOf(3 to iconCol, 4 to iconCol), navigator.positionsOf("done").sortedBy { it.first })
    }

    @Test
    fun initialFocusIsLetterAKey() {

        assertEquals("a", navigator.initialKey())
        assertEquals(1 to 4, navigator.positionsOf("a").single())

        assertEquals("a", navigator.keyAt(KeyboardLayout.initialCell.first, KeyboardLayout.initialCell.second))
    }

    @Test
    fun horizontalMovesStayInSameRow() {

        for (keyId in allKeyIds) {
            val from = navigator.positionsOf(keyId).minByOrNull { it.first }!!
            for (dir in listOf(FocusDirection.LEFT, FocusDirection.RIGHT)) {
                val next = navigator.next(keyId, dir) ?: continue
                val delta = if (dir == FocusDirection.LEFT) -1 else 1
                val target = navigator.positionsOf(next)
                    .firstOrNull { it.first == from.first && (it.second - from.second) * delta > 0 }
                assertTrue("键 $keyId $dir 目标 $next 应落在同行方向侧", target != null)
                val between = if (delta > 0) (from.second + 1) until target!!.second
                    else (target!!.second + 1) until from.second
                assertTrue(
                    "键 $keyId $dir 与目标 $next 之间不得夹实键（不跳变）",
                    between.all { navigator.keyAt(from.first, it) == null },
                )
            }
        }
    }

    @Test
    fun verticalMovesStayInSameColumn() {

        for (keyId in allKeyIds) {
            val from = navigator.positionsOf(keyId).minByOrNull { it.first }!!
            for (dir in listOf(FocusDirection.UP, FocusDirection.DOWN)) {
                val next = navigator.next(keyId, dir) ?: continue
                val delta = if (dir == FocusDirection.UP) -1 else 1
                val target = navigator.positionsOf(next)
                    .firstOrNull { it.second == from.second && (it.first - from.first) * delta > 0 }
                assertTrue("键 $keyId $dir 目标 $next 应落在同列方向侧", target != null)
                val between = if (delta > 0) (from.first + 1) until target!!.first
                    else (target!!.first + 1) until from.first
                assertTrue(
                    "键 $keyId $dir 与目标 $next 之间不得夹实键（不跳变）",
                    between.all { navigator.keyAt(it, from.second) == null },
                )
            }
        }
    }

    @Test
    fun horizontalMoveSkipsEmptyCellsToNearestKey() {

        fun key(id: String) = Cell.KeyCell(Key(id, KeyAction.Text("x"), contentDescription = id))
        val rows = listOf(
            KeyRow(listOf(key("a1"), key("b1"), key("c1"), key("d1"))),
            KeyRow(listOf(key("a2"), Cell.EmptyCell, Cell.EmptyCell, key("d2"))),
        )
        val nav = FocusNavigator(rows, overlayCells = emptyMap(), initialCell = 1 to 1)
        assertEquals("右越两个空格位直达最近实键", "d2", nav.next("a2", FocusDirection.RIGHT))
        assertEquals("左越两个空格位直达最近实键", "a2", nav.next("d2", FocusDirection.LEFT))
        assertEquals("无空格位逐格不变", "b1", nav.next("a1", FocusDirection.RIGHT))
        assertEquals("无空格位逐格不变", "a1", nav.next("b1", FocusDirection.LEFT))
        assertNull("表缘（行尾）越空到边仍不动", nav.next("d2", FocusDirection.RIGHT))
        assertNull(nav.next("a2", FocusDirection.LEFT))
    }

    @Test
    fun edgeMovesKeepFocusInsteadOfJumping() {

        assertNull("行 1 首键 LEFT", navigator.next("digit_1", FocusDirection.LEFT))
        assertNull("行 1 首键 UP", navigator.next("digit_1", FocusDirection.UP))
        assertNull("行 1 尾键（下屏）RIGHT", navigator.next("hide", FocusDirection.RIGHT))
        assertNull("行 4 末列键（空格）DOWN", navigator.next("space", FocusDirection.DOWN))
        assertNull("完成键 DOWN（跨行高键，下方仍是自身）", navigator.next("done", FocusDirection.DOWN))
    }

    @Test
    fun doneKeyNavigationAnchoredToTopRow() {
        assertEquals("完成键 UP → 删除键", "backspace", navigator.next("done", FocusDirection.UP))
        assertEquals("完成键 LEFT → 123_!（顶行锚）", "symbols", navigator.next("done", FocusDirection.LEFT))
        assertNull("完成键 RIGHT（表缘）", navigator.next("done", FocusDirection.RIGHT))
    }

    @Test
    fun keysEnteringDoneKey() {
        assertEquals("删除 DOWN → 完成键", "done", navigator.next("backspace", FocusDirection.DOWN))
        assertEquals("123_! RIGHT → 完成键", "done", navigator.next("symbols", FocusDirection.RIGHT))
        assertEquals("空格 RIGHT → 完成键", "done", navigator.next("space", FocusDirection.RIGHT))
    }

    @Test
    fun explicitCriticalPathsLocked() {
        val paths = mapOf(

            "digit_1" to mapOf(FocusDirection.RIGHT to "digit_2", FocusDirection.DOWN to "digit_4"),
            "digit_4" to mapOf(FocusDirection.DOWN to "digit_7", FocusDirection.UP to "digit_1"),
            "digit_7" to mapOf(FocusDirection.DOWN to "dot_com", FocusDirection.UP to "digit_4"),
            "dot_com" to mapOf(FocusDirection.UP to "digit_7"),
            "digit_3" to mapOf(FocusDirection.RIGHT to "a", FocusDirection.LEFT to "digit_2"),
            "a" to mapOf(FocusDirection.RIGHT to "b", FocusDirection.LEFT to "digit_3", FocusDirection.DOWN to "h", FocusDirection.UP to null),
            "g" to mapOf(FocusDirection.RIGHT to "shift", FocusDirection.DOWN to "n"),
            "shift" to mapOf(FocusDirection.RIGHT to "hide", FocusDirection.DOWN to "lang"),
            "hide" to mapOf(FocusDirection.DOWN to "backspace"),
            "backspace" to mapOf(FocusDirection.DOWN to "done"),

            "v" to mapOf(FocusDirection.DOWN to null, FocusDirection.UP to "o"),
            "z" to mapOf(FocusDirection.UP to "s"),
            "cursor_left" to mapOf(FocusDirection.UP to "t", FocusDirection.DOWN to null),
            "cursor_right" to mapOf(FocusDirection.UP to "u"),
            "y" to mapOf(FocusDirection.UP to "r"),

            "digit_8" to mapOf(FocusDirection.DOWN to "digit_0"),
            "digit_9" to mapOf(FocusDirection.DOWN to "at"),

            "symbols" to mapOf(FocusDirection.DOWN to "space", FocusDirection.UP to "lang"),
            "space" to mapOf(FocusDirection.UP to "symbols"),
        )
        for ((keyId, expectations) in paths) {
            for ((dir, expected) in expectations) {
                assertEquals("键 $keyId $dir", expected, navigator.next(keyId, dir))
            }
        }
    }

    @Test
    fun allKeysReachableFromInitialFocus() {
        val visited = mutableSetOf(navigator.initialKey())
        val queue = ArrayDeque(visited)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (dir in FocusDirection.entries) {
                val next = navigator.next(current, dir)
                if (next != null && visited.add(next)) queue.addLast(next)
            }
        }
        assertEquals("从初始键出发应可达全部键位", allKeyIds.toSet(), visited)
    }

    @Test
    fun unknownKeyHasNoPositionsAndNoMoves() {
        assertTrue(navigator.positionsOf("不存在的键").isEmpty())
        assertNull(navigator.next("不存在的键", FocusDirection.LEFT))
    }
}
