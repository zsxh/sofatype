package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T86HiddenWakeFallbackTest {

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

    @Test
    fun `第一层成功走requestShowSelf且不触碰复检与本地通道`() {
        val h = Harness()
        val result = h.wake()
        assertEquals(HiddenConfirmWake.Result.REQUEST_SHOW_SELF, result)
        assertEquals("正常框架不得复检 veto（onShowInputRequested 裁决链原样）", 0, h.vetoCalls)
        assertEquals("正常框架不得走第二层 showWindow", 0, h.showWindowCalls)
        assertTrue("第一层成功无降级 trace", h.traces.isEmpty())
    }

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
