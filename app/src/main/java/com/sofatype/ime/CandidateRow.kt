// T10 · 候选行遥控器焦点（FR-A7：上下键进出候选行、左右键在候选间移动、OK 选中上屏）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试。
// 口径：候选行是键区顶层的独立行，不并入 FocusNavigator 键盘格网（chips 宽度自适应，
// 与键盘列无列对齐关系）；进出与键盘的衔接（进入落点 / 出行落点）由服务层处理，本类只管行内移动语义。
package com.sofatype.ime

/** 候选行焦点移动结果。 */
sealed interface CandidateRowMove {
    /** 焦点移到候选行内另一 chip（下标）。 */
    data class To(val index: Int) : CandidateRowMove

    /** 向下移出候选行，回键盘区（落点由服务层按进入前焦点决定）。 */
    object ExitDown : CandidateRowMove

    /** 保持焦点不动（行内表缘 / 向上到顶）。 */
    object Stay : CandidateRowMove
}

object CandidateRow {

    /**
     * 候选行内焦点移动。
     * @param currentIndex 当前焦点 chip 下标
     * @param count 候选行 chip 总数
     */
    fun move(currentIndex: Int, direction: FocusDirection, count: Int): CandidateRowMove {
        if (currentIndex !in 0 until count) return CandidateRowMove.Stay
        return when (direction) {
            FocusDirection.LEFT -> if (currentIndex > 0) CandidateRowMove.To(currentIndex - 1) else CandidateRowMove.Stay
            FocusDirection.RIGHT -> if (currentIndex < count - 1) CandidateRowMove.To(currentIndex + 1) else CandidateRowMove.Stay
            FocusDirection.UP -> CandidateRowMove.Stay // 候选行之上无焦点目标（行顶）
            FocusDirection.DOWN -> CandidateRowMove.ExitDown
        }
    }
}
