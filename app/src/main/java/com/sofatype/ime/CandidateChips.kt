// T10 · 候选 chips 可见数计算（FR-A7 候选行）。
// 纯 Kotlin，不依赖 Android 类：候选按序装载，放不下的候选不展示
// （「更多 ⌄」第二行候选展开属 T11 · FR-A15，本任务不做分页/展开）。
// 宽度口径：chips 在候选栏内左对齐依次排布，chip 间留等宽间距；可用宽度不足即止。
package com.sofatype.ime

object CandidateChips {

    /**
     * 依可用宽度计算可完整展示的 chip 数。
     * @param chipWidthsPx 各候选 chip 的测量宽度（px，按候选序）
     * @param gapPx 相邻 chip 间距（px）
     * @param availablePx 候选栏可用宽度（px）
     */
    fun fitCount(chipWidthsPx: List<Int>, gapPx: Int, availablePx: Int): Int {
        if (availablePx <= 0) return 0
        var used = 0
        var count = 0
        for (width in chipWidthsPx) {
            val need = if (count == 0) width else width + gapPx
            if (used + need > availablePx) break
            used += need
            count++
        }
        return count
    }
}
