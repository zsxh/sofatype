// T33 · 远程大字区光标纵向居中纯函数单元测试（决策 31 折行口径，FR-B3 末尾光标）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCursorGeometryTest {

    @Test
    fun cursorCentersVerticallyInLineBox() {
        // 行盒 34px、光标 22px → 顶缘 6px（上下各留 6px）
        assertEquals(6, RemoteCursorGeometry.centeredTop(top = 0, bottom = 34, cursorHeight = 22))
    }

    @Test
    fun offsetShiftsWithLineBoxTop() {
        // 行盒整体下移：top=40, bottom=74, 高 34 盒 / 22 光标 → 顶缘 46
        assertEquals(46, RemoteCursorGeometry.centeredTop(top = 40, bottom = 74, cursorHeight = 22))
    }

    @Test
    fun oddRemainderRoundsDownwardLikeIntDivision() {
        // 余数奇数时按整除向零取整（Kotlin Int 除法截断）：盒 33 / 光标 21 → 差 12 → 顶 6
        assertEquals(6, RemoteCursorGeometry.centeredTop(top = 0, bottom = 33, cursorHeight = 21))
    }

    @Test
    fun cursorTallerThanBoxGoesNegativeTop() {
        // 光标高于行盒：按算术得负顶缘（当前口径 22dp 光标 < 折行行高，不触此路径；行为锁定防静默变更）
        assertEquals(-4, RemoteCursorGeometry.centeredTop(top = 0, bottom = 14, cursorHeight = 22))
    }

    @Test
    fun cursorExactBoxHeightAnchorsAtTop() {
        assertEquals(0, RemoteCursorGeometry.centeredTop(top = 0, bottom = 22, cursorHeight = 22))
    }
}
