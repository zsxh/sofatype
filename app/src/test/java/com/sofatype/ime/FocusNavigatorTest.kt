// T6 · 遥控器焦点导航模型单元测试（JUnit 4，纯 Kotlin，不依赖 Android UI 与模拟器）。
// 锁定 FR-A11 焦点导航口径：网格完整（12 列 × 4 行每格一键）、移动路径符合视觉行列关系（不跳变）、
// 表缘不动、完成键跨行导航行为、初始焦点、以及从初始键出发全键可达（遥控器无操作死角）。
// T40（户主拍板 2026-11-08）：弹出初始焦点 = 字母区首键 [a]（KeyboardLayout.initialCell，
// 含弹出与收起后唤起重弹路径的初始键口径；符号键盘维持 digit_1 不在本模型缺省口径内）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusNavigatorTest {

    // 运行时字母键盘实例口径（T40）：初始格位 = KeyboardLayout.initialCell（字母区首键 [a]）
    private val navigator = FocusNavigator(initialCell = KeyboardLayout.initialCell)
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
    fun initialFocusIsLetterAKey() {
        // T40（户主拍板 2026-11-08，FR-A11）：弹出初始焦点 = 字母区首键 [a]，
        // 格位 = KeyboardLayout.initialCell（行 1 数字列 1-3 + 列间隔后即字母区首键）
        assertEquals("a", navigator.initialKey())
        assertEquals(1 to 4, navigator.positionsOf("a").single())
        // KeyboardLayout.initialCell 与键位结构一致性：初始格位上的键就是 [a]
        assertEquals("a", navigator.keyAt(KeyboardLayout.initialCell.first, KeyboardLayout.initialCell.second))
    }

    // ---- 移动路径符合视觉行列关系（FR-A11 不跳变） ----

    @Test
    fun horizontalMovesStayInSameRow() {
        // 不跳变的精确定义（T58）：左右一步 = 同行移动到该方向最近实键
        //（空格位无键、越空直达，不越任何实键），即当前格位与目标格位之间不夹实键
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
        // 不跳变的精确定义（T58）：上下一步 = 同列移动到该方向最近实键
        //（空格位无键、越空直达，不越任何实键），即当前格位与目标格位之间不夹实键
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
        // T58：左/右越空格位（EmptyCell）直达最近实键、不越实键；合成布局锁定通用语义
        //（列号按 x 原点全局分配，首行铺满 4 列、次行挖空 col2/3，与半角符号行 4 同构）
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
