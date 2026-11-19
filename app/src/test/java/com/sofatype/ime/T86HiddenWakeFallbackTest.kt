// T86 Round 1 · 隐藏态确认键唤起分层兜底单测（PRD 决策 67 / 68，docs/issues/open/T86.md）：
// ① 行为锁定：HiddenConfirmWake 三层序列——第一层 requestShowSelf 成功零变化（正常框架）、
//    第一层固件缺陷型 IllegalAccessError / LinkageError 降级第二层、veto 复检拦下不唤起
//    （T38 收起抑制窗语义保留）、第二层再失败落第三层不崩；每层降级写 trace。
// ② 源码扫描守卫：服务隐藏态 OK 分支接线分层兜底，第一层字面调用 requestShowSelf(0)
//    保留在原分支（T59HiddenKeySemanticsTest 拍板 3 断言同源）。
// SofatypeImeService 为 Android 服务层，纯 JVM 单测无法实例化（无 Robolectric，仓库口径），
// 动作经函数注入（ImeShowGuard 同口径）+ 接线以源码扫描锁定。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T86HiddenWakeFallbackTest {

    /** 动作注入台架：记录 veto / showWindow 触发次数与 trace 留痕。 */
    private class Harness(
        val layer1: () -> Unit = {},
        val veto: Boolean = false,
        val layer2: () -> Unit = {},
    ) {
        val traces = mutableListOf<String>()
        var vetoCalls = 0
        var showWindowCalls = 0

        fun wake(): HiddenConfirmWake.Result = HiddenConfirmWake(
            requestShowSelf = layer1,
            vetoed = { vetoCalls++; veto },
            showWindow = { showWindowCalls++; layer2() },
            trace = { traces.add(it) },
        ).wake()
    }

    // ── ① 正常框架：第一层成功，行为零变化 ──

    @Test
    fun `第一层成功走requestShowSelf且不触碰复检与本地通道`() {
        val h = Harness()
        val result = h.wake()
        assertEquals(HiddenConfirmWake.Result.REQUEST_SHOW_SELF, result)
        assertEquals("正常框架不得复检 veto（onShowInputRequested 裁决链原样）", 0, h.vetoCalls)
        assertEquals("正常框架不得走第二层 showWindow", 0, h.showWindowCalls)
        assertTrue("第一层成功无降级 trace", h.traces.isEmpty())
    }

    // ── ② 降级序列：第一层固件缺陷型抛错 → veto 复检 → 第二层 showWindow ──

    @Test
    fun `第一层IllegalAccessError降级第二层showWindow唤起`() {
        val h = Harness(
            layer1 = { throw IllegalAccessError("MiTV4 API 23 固件不可达") },
            veto = false,
        )
        val result = h.wake()
        assertEquals(HiddenConfirmWake.Result.SHOW_WINDOW, result)
        assertEquals("降级前须复检 veto", 1, h.vetoCalls)
        assertEquals("veto 通过后走第二层通道", 1, h.showWindowCalls)
    }

    @Test
    fun `第一层LinkageError同族降级第二层showWindow唤起`() {
        val h = Harness(
            layer1 = { throw LinkageError("固件缺陷型同族") },
            veto = false,
        )
        assertEquals(HiddenConfirmWake.Result.SHOW_WINDOW, h.wake())
        assertEquals(1, h.showWindowCalls)
    }

    @Test
    fun `降级序列trace留痕两级`() {
        val h = Harness(layer1 = { throw IllegalAccessError("x") }, veto = false)
        h.wake()
        assertTrue(
            "第一层降级须写 trace",
            h.traces.any { it.contains("requestShowSelf 不可达") && it.contains("IllegalAccessError") },
        )
        assertTrue(
            "第二层成功须写 trace",
            h.traces.any { it.contains("showWindow 兜底唤起成功") },
        )
    }

    // ── ③ T38 veto 语义保留：veto 拦下时不唤起 ──

    @Test
    fun `veto拦下时不走本地通道不崩`() {
        val h = Harness(
            layer1 = { throw IllegalAccessError("x") },
            veto = true,
        )
        val result = h.wake()
        assertEquals(HiddenConfirmWake.Result.VETOED, result)
        assertEquals("收起抑制窗内不得唤起（「完成」收起后立即 OK 不唤起零回归）", 0, h.showWindowCalls)
        assertTrue(
            "veto 拦下须写 trace",
            h.traces.any { it.contains("veto") },
        )
    }

    // ── ④ 第三层：第二层再失败 = trace 留痕 + 不崩安全降级 ──

    @Test
    fun `第二层IllegalAccessError失败落第三层不崩`() {
        val h = Harness(
            layer1 = { throw IllegalAccessError("x") },
            veto = false,
            layer2 = { throw IllegalAccessError("y") },
        )
        val result = h.wake()
        assertEquals(HiddenConfirmWake.Result.DEGRADED, result)
        assertTrue(
            "第三层须写 trace 留痕",
            h.traces.any { it.contains("showWindow 兜底亦失败") && it.contains("第三层") },
        )
    }

    @Test
    fun `第二层LinkageError失败同样不崩`() {
        val h = Harness(
            layer1 = { throw IllegalAccessError("x") },
            veto = false,
            layer2 = { throw LinkageError("y") },
        )
        assertEquals(HiddenConfirmWake.Result.DEGRADED, h.wake())
    }

    // ── ⑤ 接线源码扫描守卫：服务分支第一层字面调用保留 + 三动作注入 ──

    /** 路径口径 = app/ 模块目录（T59HiddenKeySemanticsTest 同口径）。 */
    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    @Test
    fun `服务隐藏态分支接线分层兜底且第一层字面调用保留`() {
        val service = mainSource("SofatypeImeService.kt")
        val hiddenTraceIdx = service.indexOf("swallowed（输入窗隐藏态）")
        assertTrue("隐藏态确认键分支 trace 保留", hiddenTraceIdx >= 0)
        val returnIdx = service.indexOf("return true", hiddenTraceIdx)
        assertTrue("分支吞键返回存在", returnIdx > hiddenTraceIdx)
        val branch = service.substring(hiddenTraceIdx, returnIdx)
        assertTrue(
            "分支须接线 HiddenConfirmWake（T86 分层兜底）",
            branch.contains("HiddenConfirmWake("),
        )
        assertTrue(
            "第一层字面调用 requestShowSelf(0) 保留在分支内（T59 拍板 3 同源，不迁就实现改断言）",
            branch.contains("requestShowSelf = { requestShowSelf(0) }"),
        )
        assertTrue(
            "第二层 veto 复检接 showGuard.shouldVetoShowRequest（与 onShowInputRequested 同口径）",
            branch.contains("showGuard.shouldVetoShowRequest(SystemClock.uptimeMillis())"),
        )
        assertTrue(
            "第二层通道接 showWindow(true)（SDK 公开本地唤起，免 IPC 免反射）",
            branch.contains("showWindow = { showWindow(true) }"),
        )
        assertTrue(
            "降级留痕接 ImeKeyTrace（::trace）",
            branch.contains("trace = ::trace"),
        )
    }

    @Test
    fun `隐藏态确认分支先核无会话透传再接线分层兜底`() {
        // T88（户主拍板 2026-11-19，修订 T59 口径 3）：隐藏态 + 无输入会话 → 透传宿主，
        // 分层兜底唤起仅在会话活跃（confirmWakeSessionActive 门控已过）时接线执行——
        // 透传分支须先于 HiddenConfirmWake。
        val service = mainSource("SofatypeImeService.kt")
        val gateIdx = service.indexOf("if (!isInputViewShown() && !confirmWakeSessionActive())")
        assertTrue("确认键隐藏态分支须含无会话门控（T88）", gateIdx >= 0)
        val passIdx = service.indexOf("确认键隐藏态无输入会话 -> 透传宿主", gateIdx)
        val wakeIdx = service.indexOf("HiddenConfirmWake(", gateIdx)
        assertTrue("无会话须 trace 留痕透传宿主", passIdx > gateIdx)
        assertTrue(
            "分层兜底接线须在透传分支之后（会话活跃才唤起，无会话不触碰唤起链）",
            wakeIdx > passIdx,
        )
    }

    @Test
    fun `策略类窄捕获不外扩到宽泛异常族`() {
        val source = mainSource("HiddenConfirmWake.kt")
        assertTrue("策略类存在于 main 源", source.contains("class HiddenConfirmWake"))
        val caughtTypes = Regex("""catch \(\w+: (\w+)\)""").findAll(source).map { it.groupValues[1] }.toList()
        assertTrue("窄捕获子句存在", caughtTypes.isNotEmpty())
        assertTrue(
            "仅捕获固件缺陷型 LinkageError（IllegalAccessError 为其子类），不做宽泛 catch",
            caughtTypes.all { it == "LinkageError" },
        )
    }
}
