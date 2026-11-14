// T66 · 候选栏单行行内翻页模型（决策 53：FR-A15「更多⌄」展开第二行机制废止，
// 原CandidateMoreRow/CandidateRow/CandidateChips 三模型随之改造合并为本模型）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（参照原 CandidateMoreRow 模式）。
// 口径：
//   分页 = 依 chip 测量宽度把【全部候选】切成单行页（可用宽度扣除两端翻页箭头占位后计）；
//   坐标系 = 候选全局下标（HANDOFF 坑 10「分页区间相对坐标系」随本模型消除——页区间即全局
//   下标，服务层勿再做首行偏移换算）；
//   页内装载 = 首 chip 不留间距、后续 chip 加间距、放不下的不进本页；单个超宽 chip 强制
//   独占一页（兜底不丢候选，防切页死循环）；
//   翻页 = 行尾候选按[右]翻下一页、行首候选按[左]回上一页；翻页后焦点落点随方向分向
//   （T67 · 决策 55，修订决策 53「翻页后一律落页首」条款）：下翻落当页（新页）首候选、
//   上翻落当页（回翻页）尾候选；
//   行内上下语义 = 向上到顶不动（单行候选行之上无焦点目标）、向下出候选区回键盘（落点由
//   服务层处理，本模型只管行内语义与页切换）。
package com.sofatype.ime

/** 候选行行内焦点移动结果（T66 · 决策 53 单行行内翻页 × T67 · 决策 55 翻页落点分向）。 */
sealed interface CandidatePagerMove {

    /** 页内焦点移到另一 chip（候选全局下标）。 */
    data class To(val index: Int) : CandidatePagerMove

    /**
     * 翻页：目标页号 + 翻页后焦点落点（T67 · 决策 55 落点分向：下翻 = 目标页首候选、
     * 上翻 = 目标页尾候选；候选全局下标）。
     */
    data class Flip(val page: Int, val index: Int) : CandidatePagerMove

    /** 向下出候选区，回键盘区（落点由服务层决定）。 */
    object ExitDown : CandidatePagerMove

    /** 保持焦点不动（表缘 / 非法下标 / 无页 / 行顶向上）。 */
    object Stay : CandidatePagerMove
}

object CandidatePager {

    /**
     * 依测量宽度把全部候选切成单行页。
     * @param widthsPx 各候选 chip 的测量宽度（px，按候选全局序）
     * @param gapPx 相邻 chip 间距（px）
     * @param availablePx 候选行可用宽度（px；调用方先扣除两端翻页箭头占位）
     * @return 每页覆盖的候选全局下标区间（闭区间，与 widthsPx 同一坐标系，可直接作全局下标用）；
     *  无候选 / 无可用宽度返回空
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
     * 候选行内焦点移动 + 页缘翻页（决策 53 行内翻页 × T67 · 决策 55 落点分向：下翻落目标页首、
     * 上翻落目标页尾）。
     * @param currentIndex 当前焦点 chip 的候选全局下标
     * @param direction 遥控器方向
     * @param pages 分页区间（[paginate] 结果，候选全局下标坐标系）
     */
    fun move(currentIndex: Int, direction: FocusDirection, pages: List<IntRange>): CandidatePagerMove {
        if (pages.isEmpty()) return CandidatePagerMove.Stay
        val page = pages.indexOfFirst { currentIndex in it }
        if (page < 0) return CandidatePagerMove.Stay
        return when (direction) {
            FocusDirection.LEFT -> when {
                currentIndex > pages[page].first -> CandidatePagerMove.To(currentIndex - 1)
                // 行首按[左]回上一页：落回翻页尾候选（决策 55，修订决策 53 落点条款）
                page > 0 -> CandidatePagerMove.Flip(page - 1, pages[page - 1].last)
                else -> CandidatePagerMove.Stay
            }
            FocusDirection.RIGHT -> when {
                currentIndex < pages[page].last -> CandidatePagerMove.To(currentIndex + 1)
                // 行尾按[右]翻下一页：落新页首候选（决策 53 既有口径保留 × 决策 55）
                page < pages.size - 1 -> CandidatePagerMove.Flip(page + 1, pages[page + 1].first)
                else -> CandidatePagerMove.Stay
            }
            // 单行候选行之上无焦点目标（行顶不动，原 CandidateRow 口径延续）
            FocusDirection.UP -> CandidatePagerMove.Stay
            FocusDirection.DOWN -> CandidatePagerMove.ExitDown
        }
    }
}
