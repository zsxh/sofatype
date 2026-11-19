package com.sofatype.ime

sealed interface CandidatePagerMove {

    data class To(val index: Int) : CandidatePagerMove

    data class Flip(val page: Int, val index: Int) : CandidatePagerMove

    object ExitDown : CandidatePagerMove

    object Stay : CandidatePagerMove
}

object CandidatePager {

    fun paginate(widthsPx: List<Int>, gapPx: Int, availablePx: Int): List<IntRange> {
        if (widthsPx.isEmpty() || availablePx <= 0) return emptyList()
        val pages = mutableListOf<IntRange>()
        var start = 0
        while (start < widthsPx.size) {
            var used = 0
            var end = start
            for (i in start until widthsPx.size) {
                val need = if (i == start) widthsPx[i] else widthsPx[i] + gapPx

                if (i > start && used + need > availablePx) break
                used += need
                end = i
            }
            pages += start..end
            start = end + 1
        }
        return pages
    }

    fun move(currentIndex: Int, direction: FocusDirection, pages: List<IntRange>): CandidatePagerMove {
        if (pages.isEmpty()) return CandidatePagerMove.Stay
        val page = pages.indexOfFirst { currentIndex in it }
        if (page < 0) return CandidatePagerMove.Stay
        return when (direction) {
            FocusDirection.LEFT -> when {
                currentIndex > pages[page].first -> CandidatePagerMove.To(currentIndex - 1)

                page > 0 -> CandidatePagerMove.Flip(page - 1, pages[page - 1].last)
                else -> CandidatePagerMove.Stay
            }
            FocusDirection.RIGHT -> when {
                currentIndex < pages[page].last -> CandidatePagerMove.To(currentIndex + 1)

                page < pages.size - 1 -> CandidatePagerMove.Flip(page + 1, pages[page + 1].first)
                else -> CandidatePagerMove.Stay
            }

            FocusDirection.UP -> CandidatePagerMove.Stay
            FocusDirection.DOWN -> CandidatePagerMove.ExitDown
        }
    }
}
