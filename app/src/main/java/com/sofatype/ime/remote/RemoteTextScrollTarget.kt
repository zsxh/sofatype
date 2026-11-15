// T76 · 远程大字区装载滚动目标（纯 Kotlin，无 Android 依赖）：决策 31「折行内容超出
// 显示区高度时纵向滚动保持末尾与光标可见」的目标偏移计算单一事实源。
// T76（焦点保持口径）：装载滚动只算目标、不改焦点——ScrollView.fullScroll(FOCUS_DOWN)
// 会顺带执行焦点查找并把焦点移给滚动容器自身（大字区内无可聚焦子项时），夺走
// 「退出远程」按钮焦点，OK 到达时 performClick 无目标（T74 坑 2）。服务层接线改为
// smoothScrollTo 纯滚动（不触碰焦点树），本模型只负责算术。
package com.sofatype.ime.remote

object RemoteTextScrollTarget {

    /**
     * 滚动到底（保持末尾与光标可见）的目标纵向偏移：内容高度超出视口高度 → 差值；
     * 未超出（含恰好占满）→ 0（不滚动）。高度缺失或非法（≤ 0，未测量 / 视图缺子项）→ 0。
     */
    fun bottomOffset(contentHeightPx: Int, viewportHeightPx: Int): Int {
        if (contentHeightPx <= 0 || viewportHeightPx <= 0) return 0
        return (contentHeightPx - viewportHeightPx).coerceAtLeast(0)
    }
}
