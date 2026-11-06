// T10 · 候选行遥控器焦点单元测试（JUnit 4，纯 Kotlin）。
// 锁定 FR-A7 候选行行内语义：左右键在候选间移动、行缘与行顶不动、向下出行。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class CandidateRowTest {

    @Test
    fun `左右键在候选间移动`() {
        assertEquals(CandidateRowMove.To(1), CandidateRow.move(0, FocusDirection.RIGHT, 4))
        assertEquals(CandidateRowMove.To(2), CandidateRow.move(1, FocusDirection.RIGHT, 4))
        assertEquals(CandidateRowMove.To(0), CandidateRow.move(1, FocusDirection.LEFT, 4))
    }

    @Test
    fun `行缘不动`() {
        assertEquals(CandidateRowMove.Stay, CandidateRow.move(0, FocusDirection.LEFT, 4))
        assertEquals(CandidateRowMove.Stay, CandidateRow.move(3, FocusDirection.RIGHT, 4))
    }

    @Test
    fun `向下出行向上到顶`() {
        assertEquals(CandidateRowMove.ExitDown, CandidateRow.move(2, FocusDirection.DOWN, 4))
        assertEquals(CandidateRowMove.Stay, CandidateRow.move(2, FocusDirection.UP, 4))
    }

    @Test
    fun `单候选行左右均不动`() {
        assertEquals(CandidateRowMove.Stay, CandidateRow.move(0, FocusDirection.LEFT, 1))
        assertEquals(CandidateRowMove.Stay, CandidateRow.move(0, FocusDirection.RIGHT, 1))
    }

    @Test
    fun `非法下标保持不动`() {
        assertEquals(CandidateRowMove.Stay, CandidateRow.move(-1, FocusDirection.LEFT, 4))
        assertEquals(CandidateRowMove.Stay, CandidateRow.move(4, FocusDirection.RIGHT, 4))
    }
}
