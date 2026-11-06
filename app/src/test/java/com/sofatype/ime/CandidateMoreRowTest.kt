// T11 · 更多候选展开行模型单元测试（JUnit 4，纯 Kotlin）。
// 锁定 FR-A15：溢出口径、按宽度分页、页缘翻页、行内移动、行顶/表缘不动。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateMoreRowTest {

    // ---- overflowCount（更多入口可见判定项） ----

    @Test
    fun `溢出数为总数减首行数`() {
        assertEquals(3, CandidateMoreRow.overflowCount(10, 7))
    }

    @Test
    fun `首行装下全部时溢出为0`() {
        assertEquals(0, CandidateMoreRow.overflowCount(5, 5))
        assertEquals(0, CandidateMoreRow.overflowCount(3, 7))
    }

    @Test
    fun `负值钳为0`() {
        assertEquals(0, CandidateMoreRow.overflowCount(5, 8))
    }

    // ---- paginate（按宽度分页） ----

    @Test
    fun `等宽候选按容量切多页`() {
        // 宽 100、间距 20、可用 340：每页 3 个（100/120/120）；8 个 → 3 页
        assertEquals(
            listOf(0..2, 3..5, 6..7),
            CandidateMoreRow.paginate(List(8) { 100 }, 20, 340),
        )
    }

    @Test
    fun `恰好装满时单页收纳`() {
        assertEquals(listOf(0..1), CandidateMoreRow.paginate(listOf(100, 100), 20, 220))
    }

    @Test
    fun `首chip超宽独占一页不丢候选`() {
        // 500 超 340 可用：独占第 1 页，后续照常切页
        assertEquals(listOf(0..0, 1..2), CandidateMoreRow.paginate(listOf(500, 100, 100), 20, 340))
    }

    @Test
    fun `可用宽度非法或空列表返回空页`() {
        assertEquals(emptyList<IntRange>(), CandidateMoreRow.paginate(listOf(100), 20, 0))
        assertEquals(emptyList<IntRange>(), CandidateMoreRow.paginate(listOf(100), 20, -1))
        assertEquals(emptyList<IntRange>(), CandidateMoreRow.paginate(emptyList(), 20, 1000))
    }

    @Test
    fun `宽度不一按能放下的前缀切页`() {
        // 60/80/100/120、间距 10、可用 200：60+90=150 放前 2，+110=260 超宽 → 页 1 = 0..1；
        // 页 2 = 2..2（100 起步，120 需 130 → 230 超宽）；页 3 = 3..3（首 chip 必收，120 ≤ 200）
        assertEquals(
            listOf(0..1, 2..2, 3..3),
            CandidateMoreRow.paginate(listOf(60, 80, 100, 120), 10, 200),
        )
    }

    // ---- move（页内移动 + 页缘翻页 + 行顶/表缘） ----

    private val pages = listOf(0..2, 3..5, 6..7)

    @Test
    fun `页内左右移动`() {
        assertEquals(CandidateMoreMove.To(4), CandidateMoreRow.move(3, FocusDirection.RIGHT, pages))
        assertEquals(CandidateMoreMove.To(3), CandidateMoreRow.move(4, FocusDirection.LEFT, pages))
    }

    @Test
    fun `右缘向后翻页落新页首chip`() {
        assertEquals(CandidateMoreMove.Flip(1, 3), CandidateMoreRow.move(2, FocusDirection.RIGHT, pages))
        assertEquals(CandidateMoreMove.Flip(2, 6), CandidateMoreRow.move(5, FocusDirection.RIGHT, pages))
    }

    @Test
    fun `左缘向前翻页落新页末chip`() {
        assertEquals(CandidateMoreMove.Flip(0, 2), CandidateMoreRow.move(3, FocusDirection.LEFT, pages))
        assertEquals(CandidateMoreMove.Flip(1, 5), CandidateMoreRow.move(6, FocusDirection.LEFT, pages))
    }

    @Test
    fun `末页右缘与首页左缘不动`() {
        assertEquals(CandidateMoreMove.Stay, CandidateMoreRow.move(7, FocusDirection.RIGHT, pages))
        assertEquals(CandidateMoreMove.Stay, CandidateMoreRow.move(0, FocusDirection.LEFT, pages))
    }

    @Test
    fun `向上回首行向下出候选区`() {
        assertEquals(CandidateMoreMove.ExitUp, CandidateMoreRow.move(4, FocusDirection.UP, pages))
        assertEquals(CandidateMoreMove.ExitDown, CandidateMoreRow.move(4, FocusDirection.DOWN, pages))
    }

    @Test
    fun `非法下标与空页保持不动`() {
        assertEquals(CandidateMoreMove.Stay, CandidateMoreRow.move(-1, FocusDirection.RIGHT, pages))
        assertEquals(CandidateMoreMove.Stay, CandidateMoreRow.move(99, FocusDirection.LEFT, pages))
        assertEquals(CandidateMoreMove.Stay, CandidateMoreRow.move(0, FocusDirection.RIGHT, emptyList()))
    }

    @Test
    fun `单页行内移动无翻页`() {
        val single = listOf(0..1)
        assertEquals(CandidateMoreMove.To(1), CandidateMoreRow.move(0, FocusDirection.RIGHT, single))
        assertEquals(CandidateMoreMove.Stay, CandidateMoreRow.move(1, FocusDirection.RIGHT, single))
        assertEquals(CandidateMoreMove.Stay, CandidateMoreRow.move(0, FocusDirection.LEFT, single))
    }
}
