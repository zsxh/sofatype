// T49 · 组合态光标移动落定组合与 composer 同步（docs/issues/open/T49.md）守卫单元测试：
// 组合态按网格「←/→」移动光标时，框架 finishComposingText 把组合串落为正文，composer 必须
// 同步终结（finishComposition 成对落定+复位）——否则残留幻影组合串，其后删除键经 T48 拦截
// 分支被幻影误导（首针闪插字母、次针白按）。
// SofatypeImeService 为 Android 交互层，纯 JVM 单测无法实例化（无 Robolectric，仓库口径），
// 接线以源码扫描锁定回归——源码扫描先例：DirectInstallFlowTest（T51）、UpdateApiCompatTest（T46）、
// AccessibilityLabelsTest（T26，直读源文件口径）。composer 状态机侧「reset 清空组合串与候选」
// 契约已由 ChineseComposerTest 锁定；行为级取证走模拟器/真机走查。
package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CursorComposerSyncTest {

    /** 路径口径 = app/ 模块目录（DirectInstallFlowTest 同口径）。 */
    private fun serviceBody(signature: String): String =
        File("src/main/java/com/sofatype/ime/SofatypeImeService.kt")
            .readText()
            .substringAfter(signature)
            .substringBefore("\n    /**")

    @Test
    fun `光标移动落定组合时同步终结composer`() {
        val body = serviceBody("private fun moveCursor(delta: Int)")
        // 幻影组合根因模式不得回归：moveCursor 体内不得再直挂 IC finishComposingText
        // （组合区间消失而 composer 不同步 = 幻影组合串，T49 缺陷签名）
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
        // 同步须发生在光标计算之前：先落定组合（组合串落为正文），再按落定后文本算光标目标位
        assertTrue(
            "moveCursor 的组合同步须先于 beginBatchEdit 光标计算",
            body.indexOf("finishComposition()") < body.indexOf("beginBatchEdit"),
        )
    }

    @Test
    fun `finishComposition成对终结组合串与composer复位`() {
        val body = serviceBody("private fun finishComposition()")
        // 落定（框架组合区间消失）与状态机复位必须成对出现在同一辅助函数内，
        // 供全部落定路径（光标移动/完成键/中英切换/符号切换/空格无候选）共用
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
