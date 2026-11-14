// T66 · 候选栏单行行内翻页模型单元测试（决策 53，废止 FR-A15「更多⌄」展开行模型）。
// 锁定五件事：
// ① 分页区间 = 候选全局下标（HANDOFF 坑 10「分页区间相对坐标系」随本模型消除，勿再做首行偏移）；
// ② 页容量按宽度自适应（首 chip 不留间距、后续 chip 加间距、放不下不进本页、超宽 chip 独占一页）；
// ③ 行尾按[右]翻下一页、行首按[左]回上一页；
// ④ 翻页后焦点一律落页首第一个候选（原展开行「回翻落页末」口径废止）；
// ⑤ 行内上下语义：向上到顶不动、向下出候选区回键盘。
// 纯 Kotlin，不依赖 Android UI 与模拟器（原 CandidateMoreRowTest 同口径）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidatePagerTest {

    /** 等宽 chip 序列构造：n 个宽度 [w] 的候选。 */
    private fun widths(count: Int, w: Int): List<Int> = List(count) { w }

    // ---- ① 分页区间 = 候选全局下标 ----

    @Test
    fun `页区间即候选全局下标无需偏移换算`() {
        // 每页恰容 3 chip（w=100、gap=10、available=320 → 100/310/320 恰满 3）
        val pages = CandidatePager.paginate(widths(8, 100), 10, 320)
        assertEquals(listOf(0..2, 3..5, 6..7), pages)
        // 区间值直接就是候选下标：第 2 页首 chip 即候选 3（对照坑 10 相对坐标系旧口径）
        assertEquals(3, pages[1].first)
        assertEquals(7, pages.last().last)
    }

    @Test
    fun `空候选与零宽度返回空`() {
        assertTrue(CandidatePager.paginate(emptyList(), 10, 320).isEmpty())
        assertTrue(CandidatePager.paginate(widths(3, 100), 10, 0).isEmpty())
        assertTrue(CandidatePager.paginate(widths(0, 100), 10, 320).isEmpty())
    }

    // ---- ② 页容量按宽度自适应 ----

    @Test
    fun `页容量随可用宽度自适应`() {
        // 同一候选集，可用宽加倍 → 每页容量加倍（单行页容量按候选栏宽度自适应，画板示意 5 chips）
        assertEquals(listOf(0..2, 3..5, 6..7), CandidatePager.paginate(widths(8, 100), 10, 320))
        assertEquals(listOf(0..7), CandidatePager.paginate(widths(8, 100), 10, 870))
    }

    @Test
    fun `放不下的候选不进本页且超宽chip独占一页`() {
        // 320 宽：100 + 10 + 100 = 210 再放 100 = 310 ≤ 320（3 个），第 4 个需 320 > 320 溢出
        val pages = CandidatePager.paginate(widths(5, 100), 10, 320)
        assertEquals(listOf(0..2, 3..4), pages)
        // 单个超宽 chip（> available）强制独占一页，兜底不丢候选、防切页死循环；
        // 310 宽下 3 枚等宽 chip（100+10+100+10+100=320 > 310）只容 2 枚
        val mixed = listOf(400, 100, 100, 100)
        val paged = CandidatePager.paginate(mixed, 10, 310)
        assertEquals(listOf(0..0, 1..2, 3..3), paged)
    }

    // ---- ③ 页缘翻页（行尾[右]翻下页、行首[左]回上页） ----

    @Test
    fun `行尾按右翻下一页行首按左回上一页`() {
        val pages = CandidatePager.paginate(widths(9, 100), 10, 320) // 0..2 / 3..5 / 6..8
        // 页内移动
        assertEquals(CandidatePagerMove.To(1), CandidatePager.move(0, FocusDirection.RIGHT, pages))
        assertEquals(CandidatePagerMove.To(0), CandidatePager.move(1, FocusDirection.LEFT, pages))
        // 行尾候选（chip 2）按[右] → 翻下一页
        assertEquals(CandidatePagerMove.Flip(1, 3), CandidatePager.move(2, FocusDirection.RIGHT, pages))
        // 行首候选（chip 3）按[左] → 回上一页
        assertEquals(CandidatePagerMove.Flip(0, 0), CandidatePager.move(3, FocusDirection.LEFT, pages))
    }

    // ---- ④ 翻页后焦点落页首第一个候选 ----

    @Test
    fun `翻页落点恒为页首第一个候选`() {
        val pages = CandidatePager.paginate(widths(9, 100), 10, 320)
        // 向后翻落新页首（3）、再向后落新页首（6）；向前翻同样落页首（3 / 0）——
        // 决策 53「翻页后焦点落页首第一个候选」，原展开行向前翻落页末口径废止
        assertEquals(CandidatePagerMove.Flip(1, 3), CandidatePager.move(2, FocusDirection.RIGHT, pages))
        assertEquals(CandidatePagerMove.Flip(2, 6), CandidatePager.move(5, FocusDirection.RIGHT, pages))
        assertEquals(CandidatePagerMove.Flip(1, 3), CandidatePager.move(6, FocusDirection.LEFT, pages))
        assertEquals(CandidatePagerMove.Flip(0, 0), CandidatePager.move(3, FocusDirection.LEFT, pages))
    }

    @Test
    fun `表缘不越界保持不动`() {
        val pages = CandidatePager.paginate(widths(6, 100), 10, 320) // 0..2 / 3..5
        // 首页行首按[左]、末页行尾按[右]：无更页可翻，保持不动
        assertEquals(CandidatePagerMove.Stay, CandidatePager.move(0, FocusDirection.LEFT, pages))
        assertEquals(CandidatePagerMove.Stay, CandidatePager.move(5, FocusDirection.RIGHT, pages))
        // 非法下标 / 无页
        assertEquals(CandidatePagerMove.Stay, CandidatePager.move(99, FocusDirection.RIGHT, pages))
        assertEquals(CandidatePagerMove.Stay, CandidatePager.move(0, FocusDirection.RIGHT, emptyList()))
    }

    // ---- ⑤ 行内上下语义 ----

    @Test
    fun `向上到顶不动向下出候选区`() {
        val pages = CandidatePager.paginate(widths(3, 100), 10, 320)
        assertEquals(CandidatePagerMove.Stay, CandidatePager.move(1, FocusDirection.UP, pages))
        assertEquals(CandidatePagerMove.ExitDown, CandidatePager.move(1, FocusDirection.DOWN, pages))
    }
}
