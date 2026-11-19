package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CursorComposerSyncTest {

    private fun serviceBody(signature: String): String =
        File("src/main/java/com/sofatype/ime/SofatypeImeService.kt")
            .readText()
            .substringAfter(signature)
            .substringBefore("\n    }")

    @Test
    fun `光标移动落定组合时同步终结composer`() {
        val body = serviceBody("private fun moveCursor(delta: Int)")

        assertFalse(
            "moveCursor 不得直挂 ic.finishComposingText()（须改经 finishComposition 同步复位 composer）",
            body.contains("ic.finishComposingText()"),
        )
        assertTrue(
            "moveCursor 落定组合须走 finishComposition()（落定与 composer 复位成对）",
            body.contains("finishComposition()"),
        )
        assertTrue(
            "moveCursor 落定组合后须刷新候选栏（清陈旧 chips，composer 复位后候选集失效）",
            body.contains("refreshCandidatesBar()"),
        )

        assertTrue(
            "moveCursor 的组合同步须先于 beginBatchEdit 光标计算",
            body.indexOf("finishComposition()") < body.indexOf("beginBatchEdit"),
        )
    }

    @Test
    fun `finishComposition成对终结组合串与composer复位`() {
        val body = serviceBody("private fun finishComposition()")

        assertTrue(
            "finishComposition 须调用 finishComposingText() 落定框架组合区间",
            body.contains("finishComposingText()"),
        )
        assertTrue(
            "finishComposition 须调用 composer.reset() 同步复位状态机",
            body.contains("composer.reset()"),
        )
        assertTrue(
            "finishComposition 内复位须在落定之后（先落定框架区间再清状态机）",
            body.indexOf("finishComposingText()") < body.indexOf("composer.reset()"),
        )
    }
}
