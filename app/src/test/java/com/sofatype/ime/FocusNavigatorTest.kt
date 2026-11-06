// T6 · 遥控器焦点导航模型单元测试（JUnit 4，纯 Kotlin，不依赖 Android UI 与模拟器）。
// 锁定 FR-A11 焦点导航口径：网格完整（12 列 × 4 行每格一键）、移动路径符合视觉行列关系（不跳变）、
// 表缘不动、完成键跨行导航行为、初始焦点、以及从初始键出发全键可达（遥控器无操作死角）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusNavigatorTest {

    private val navigator = FocusNavigator()
    private val allKeyIds: List<String> = KeyboardLayout.allKeys().map { it.id }

    // ---- 网格完整性（画板① = 12 列 × 4 行完整网格） ----

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
        // 完成键跨两行（FR-A10）：占据行 3、行 4 的图标列（与行 1 下屏键同列，T28 对齐口径）
        val iconCol = navigator.positionsOf("hide").single().second
        assertEquals(12, iconCol)
        assertEquals(listOf(3 to iconCol, 4 to iconCol), navigator.positionsOf("done").sortedBy { it.first })
    }

    @Test
    fun initialFocusIsTopLeftDigitKey() {
        assertEquals("digit_1", navigator.initialKey())
        assertEquals(1 to 1, navigator.positionsOf("digit_1").single())
    }

    // ---- 移动路径符合视觉行列关系（FR-A11 不跳变） ----

    @Test
    fun horizontalMovesStayInSameRow() {
        // 不跳变的精确定义：左右一步 = 移到相邻列的同格位（目标键跨行时，取其占据的相邻格位即可）
        for (keyId in allKeyIds) {
            val from = navigator.positionsOf(keyId).minByOrNull { it.first }!!
            for (dir in listOf(FocusDirection.LEFT, FocusDirection.RIGHT)) {
                val next = navigator.next(keyId, dir) ?: continue
                val deltaCol = if (dir == FocusDirection.LEFT) -1 else 1
                val landsAdjacent = navigator.positionsOf(next)
                    .any { it.first == from.first && it.second == from.second + deltaCol }
                assertTrue("键 $keyId $dir 应落在相邻格位 (${from.first}, ${from.second + deltaCol})，实际 $next", landsAdjacent)
            }
        }
    }

    @Test
    fun verticalMovesStayInSameColumn() {
        // 不跳变的精确定义：上下一步 = 移到相邻行的同格位（目标键跨行时，取其占据的相邻格位即可）
        for (keyId in allKeyIds) {
            val from = navigator.positionsOf(keyId).minByOrNull { it.first }!!
            for (dir in listOf(FocusDirection.UP, FocusDirection.DOWN)) {
                val next = navigator.next(keyId, dir) ?: continue
                val deltaRow = if (dir == FocusDirection.UP) -1 else 1
                val landsAdjacent = navigator.positionsOf(next)
                    .any { it.first == from.first + deltaRow && it.second == from.second }
                assertTrue("键 $keyId $dir 应落在相邻格位 (${from.first + deltaRow}, ${from.second})，实际 $next", landsAdjacent)
            }
        }
    }

    @Test
    fun edgeMovesKeepFocusInsteadOfJumping() {
        // 表缘消费方向键但不动（视图层拦截，不走系统 focus search，防跳变）
        assertNull("行 1 首键 LEFT", navigator.next("digit_1", FocusDirection.LEFT))
        assertNull("行 1 首键 UP", navigator.next("digit_1", FocusDirection.UP))
        assertNull("行 1 尾键（下屏）RIGHT", navigator.next("hide", FocusDirection.RIGHT))
        assertNull("行 4 末列键（空格）DOWN", navigator.next("space", FocusDirection.DOWN))
        assertNull("完成键 DOWN（跨行高键，下方仍是自身）", navigator.next("done", FocusDirection.DOWN))
    }

    // ---- 完成键跨行导航（FR-A10 键位在导航层的行为） ----

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

    // ---- 关键路径显式锁定（画板① 行列关系抽样） ----

    @Test
    fun explicitCriticalPathsLocked() {
        val paths = mapOf(
            // 数字列纵向链 1→4→7→.com；横向链 1→2→3→a→g→上档→下屏
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
            // 字母区纵向对齐：v→o、z→s、光标键→t/u
            "v" to mapOf(FocusDirection.DOWN to null, FocusDirection.UP to "o"),
            "z" to mapOf(FocusDirection.UP to "s"),
            "cursor_left" to mapOf(FocusDirection.UP to "t", FocusDirection.DOWN to null),
            "cursor_right" to mapOf(FocusDirection.UP to "u"),
            "y" to mapOf(FocusDirection.UP to "r"),
            // 数字列对齐行 4 首段
            "digit_8" to mapOf(FocusDirection.DOWN to "digit_0"),
            "digit_9" to mapOf(FocusDirection.DOWN to "at"),
            // 功能列：123_! ↔ 空格
            "symbols" to mapOf(FocusDirection.DOWN to "space", FocusDirection.UP to "lang"),
            "space" to mapOf(FocusDirection.UP to "symbols"),
        )
        for ((keyId, expectations) in paths) {
            for ((dir, expected) in expectations) {
                assertEquals("键 $keyId $dir", expected, navigator.next(keyId, dir))
            }
        }
    }

    // ---- 全键可达（遥控器无操作死角，PRD 1.3 目标） ----

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
