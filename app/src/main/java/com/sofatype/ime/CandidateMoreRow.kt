// T11 · 更多候选展开行模型（FR-A15：第二行候选分页展示、左右翻页、焦点导航）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（参照 CandidateRow/CandidateChips 模式）。
// 口径：
//   展开行候选 = 首行（候选栏）装不下的候选（CandidateChips.fitCount 截断后的剩余序列）；
//   分页 = 依 chip 测量宽度把展开行候选切成页，页内装载口径与 CandidateChips.fitCount 一致
//   （首 chip 不留间距、后续 chip 加间距、放不下的不进本页）；单个超宽 chip 强制独占一页（兜底不丢候选，防切页死循环）；
//   翻页 = 行内焦点移动到页缘时继续同方向翻页：右缘向后翻（落新页首 chip）、左缘向前翻（落新页末 chip）；
//   行内上下语义：向上回候选首行、向下出候选区回键盘（进出落点由服务层处理，本类只管行内语义与页切换）。
package com.sofatype.ime

/** 展开行焦点移动结果（FR-A15）。 */
sealed interface CandidateMoreMove {

    /** 页内焦点移到另一 chip（展开行全局下标，与展开行候选序一致）。 */
    data class To(val index: Int) : CandidateMoreMove

    /** 翻页：目标页号 + 翻页后焦点落点（展开行全局下标）。 */
    data class Flip(val page: Int, val index: Int) : CandidateMoreMove

    /** 向上回候选首行（落点由服务层决定）。 */
    object ExitUp : CandidateMoreMove

    /** 向下出候选区，回键盘区（落点由服务层决定）。 */
    object ExitDown : CandidateMoreMove

    /** 保持焦点不动（表缘 / 非法下标 / 无页）。 */
    object Stay : CandidateMoreMove
}

object CandidateMoreRow {

    /**
     * 首行装不下时剩余的候选数（「更多 ⌄」入口可见判定项：> 0 时入口可见）。
     * @param total 组合态候选总数
     * @param firstRowCount 首行已展示候选数（CandidateChips.fitCount 结果）
     */
    fun overflowCount(total: Int, firstRowCount: Int): Int = (total - firstRowCount).coerceAtLeast(0)

    /**
     * 依测量宽度把展开行候选切成页。
     * @param widthsPx 各展开行候选 chip 的测量宽度（px，按展开行候选序，即首行装不下的溢出段序）
     * @param gapPx 相邻 chip 间距（px）
     * @param availablePx 展开行可用宽度（px）
     * @return 每页覆盖的下标区间（闭区间，与 widthsPx 同一相对坐标系）；无候选 / 无可用宽度返回空
     */
    fun paginate(widthsPx: List<Int>, gapPx: Int, availablePx: Int): List<IntRange> {
        if (widthsPx.isEmpty() || availablePx <= 0) return emptyList()
        val pages = mutableListOf<IntRange>()
        var start = 0
        while (start < widthsPx.size) {
            var used = 0
            var end = start
            for (i in start until widthsPx.size) {
                val need = if (i == start) widthsPx[i] else widthsPx[i] + gapPx
                // 首 chip 超宽也收入本页（独占），防切页死循环；后续 chip 需完整放下
                if (i > start && used + need > availablePx) break
                used += need
                end = i
            }
            pages += start..end
            start = end + 1
        }
        return pages
    }

    /**
     * 展开行内焦点移动（FR-A15「展开行同样支持焦点导航」+「左右翻页」）。
     * @param currentIndex 当前焦点 chip 的展开行全局下标
     * @param direction 遥控器方向
     * @param pages 分页区间（[paginate] 结果）
     */
    fun move(currentIndex: Int, direction: FocusDirection, pages: List<IntRange>): CandidateMoreMove {
        if (pages.isEmpty()) return CandidateMoreMove.Stay
        val page = pages.indexOfFirst { currentIndex in it }
        if (page < 0) return CandidateMoreMove.Stay
        return when (direction) {
            FocusDirection.LEFT -> when {
                currentIndex > pages[page].first -> CandidateMoreMove.To(currentIndex - 1)
                page > 0 -> CandidateMoreMove.Flip(page - 1, pages[page - 1].last)
                else -> CandidateMoreMove.Stay
            }
            FocusDirection.RIGHT -> when {
                currentIndex < pages[page].last -> CandidateMoreMove.To(currentIndex + 1)
                page < pages.size - 1 -> CandidateMoreMove.Flip(page + 1, pages[page + 1].first)
                else -> CandidateMoreMove.Stay
            }
            FocusDirection.UP -> CandidateMoreMove.ExitUp
            FocusDirection.DOWN -> CandidateMoreMove.ExitDown
        }
    }
}
