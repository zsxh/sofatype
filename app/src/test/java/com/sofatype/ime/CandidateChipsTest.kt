// T10 · 候选 chips 可见数计算单元测试（JUnit 4，纯 Kotlin）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateChipsTest {

    @Test
    fun `按序完整装载不超可用宽度`() {
        // 宽 100 的 chip、间距 20、可用 340：100 / 120 / 120 → 3 个（第 4 个需 140 超宽）
        assertEquals(3, CandidateChips.fitCount(listOf(100, 100, 100, 100), 20, 340))
    }

    @Test
    fun `首chip不需要间距`() {
        // 单个 chip 恰好占满可用宽度
        assertEquals(1, CandidateChips.fitCount(listOf(200), 20, 200))
        // 两个则超宽
        assertEquals(1, CandidateChips.fitCount(listOf(200, 200), 20, 200))
    }

    @Test
    fun `恰好装满时全部可见`() {
        assertEquals(2, CandidateChips.fitCount(listOf(100, 100), 20, 220))
    }

    @Test
    fun `可用宽度不足时一个都不装`() {
        assertEquals(0, CandidateChips.fitCount(listOf(100), 20, 99))
    }

    @Test
    fun `可用宽度非法或空列表返回0`() {
        assertEquals(0, CandidateChips.fitCount(listOf(100), 20, 0))
        assertEquals(0, CandidateChips.fitCount(listOf(100), 20, -1))
        assertEquals(0, CandidateChips.fitCount(emptyList(), 20, 1000))
    }

    @Test
    fun `宽度递增序列按能放下的前缀装载`() {
        // 60/80/100/120、间距 10、可用 270：60 + 90 + 110 = 260 → 3 个（第 4 个需 130 → 390 超宽）
        assertEquals(3, CandidateChips.fitCount(listOf(60, 80, 100, 120), 10, 270))
    }
}
