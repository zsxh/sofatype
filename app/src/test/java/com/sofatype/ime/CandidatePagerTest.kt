package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidatePagerTest {

    private fun widths(count: Int, w: Int): List<Int> = List(count) { w }

    @Test
    fun `页区间即候选全局下标无需偏移换算`() {

        val pages = CandidatePager.paginate(widths(8, 100), 10, 320)
        assertEquals(listOf(0..2, 3..5, 6..7), pages)

        assertEquals(3, pages[1].first)
        assertEquals(7, pages.last().last)
    }

    @Test
    fun `空候选与零宽度返回空`() {
        assertTrue(CandidatePager.paginate(emptyList(), 10, 320).isEmpty())
        assertTrue(CandidatePager.paginate(widths(3, 100), 10, 0).isEmpty())
        assertTrue(CandidatePager.paginate(widths(0, 100), 10, 320).isEmpty())
    }

    @Test
    fun `页容量随可用宽度自适应`() {

        assertEquals(listOf(0..2, 3..5, 6..7), CandidatePager.paginate(widths(8, 100), 10, 320))
        assertEquals(listOf(0..7), CandidatePager.paginate(widths(8, 100), 10, 870))
    }

    @Test
    fun `放不下的候选不进本页且超宽chip独占一页`() {

        val pages = CandidatePager.paginate(widths(5, 100), 10, 320)
        assertEquals(listOf(0..2, 3..4), pages)

        val mixed = listOf(400, 100, 100, 100)
        val paged = CandidatePager.paginate(mixed, 10, 310)
        assertEquals(listOf(0..0, 1..2, 3..3), paged)
    }

    @Test
    fun `行尾按右翻下一页行首按左回上一页`() {
        val pages = CandidatePager.paginate(widths(9, 100), 10, 320)

        assertEquals(CandidatePagerMove.To(1), CandidatePager.move(0, FocusDirection.RIGHT, pages))
        assertEquals(CandidatePagerMove.To(0), CandidatePager.move(1, FocusDirection.LEFT, pages))

        assertEquals(CandidatePagerMove.Flip(1, 3), CandidatePager.move(2, FocusDirection.RIGHT, pages))

        assertEquals(CandidatePagerMove.Flip(0, 2), CandidatePager.move(3, FocusDirection.LEFT, pages))
    }

    @Test
    fun `翻页落点随方向分向下翻落页首上翻落页尾`() {
        val pages = CandidatePager.paginate(widths(9, 100), 10, 320)

        assertEquals(CandidatePagerMove.Flip(1, 3), CandidatePager.move(2, FocusDirection.RIGHT, pages))
        assertEquals(CandidatePagerMove.Flip(2, 6), CandidatePager.move(5, FocusDirection.RIGHT, pages))
        assertEquals(CandidatePagerMove.Flip(1, 5), CandidatePager.move(6, FocusDirection.LEFT, pages))
        assertEquals(CandidatePagerMove.Flip(0, 2), CandidatePager.move(3, FocusDirection.LEFT, pages))

        val single = CandidatePager.paginate(listOf(400, 100, 100, 100), 10, 310)
        assertEquals(CandidatePagerMove.Flip(1, 2), CandidatePager.move(3, FocusDirection.LEFT, single))
        assertEquals(CandidatePagerMove.Flip(0, 0), CandidatePager.move(1, FocusDirection.LEFT, single))
    }

    @Test
    fun `表缘不越界保持不动`() {
        val pages = CandidatePager.paginate(widths(6, 100), 10, 320)

        assertEquals(CandidatePagerMove.Stay, CandidatePager.move(0, FocusDirection.LEFT, pages))
        assertEquals(CandidatePagerMove.Stay, CandidatePager.move(5, FocusDirection.RIGHT, pages))

        assertEquals(CandidatePagerMove.Stay, CandidatePager.move(99, FocusDirection.RIGHT, pages))
        assertEquals(CandidatePagerMove.Stay, CandidatePager.move(0, FocusDirection.RIGHT, emptyList()))
    }

    @Test
    fun `向上到顶不动向下出候选区`() {
        val pages = CandidatePager.paginate(widths(3, 100), 10, 320)
        assertEquals(CandidatePagerMove.Stay, CandidatePager.move(1, FocusDirection.UP, pages))
        assertEquals(CandidatePagerMove.ExitDown, CandidatePager.move(1, FocusDirection.DOWN, pages))
    }
}
