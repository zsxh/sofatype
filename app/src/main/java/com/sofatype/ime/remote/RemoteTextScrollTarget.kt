package com.sofatype.ime.remote

object RemoteTextScrollTarget {

    fun bottomOffset(contentHeightPx: Int, viewportHeightPx: Int): Int {
        if (contentHeightPx <= 0 || viewportHeightPx <= 0) return 0
        return (contentHeightPx - viewportHeightPx).coerceAtLeast(0)
    }
}
