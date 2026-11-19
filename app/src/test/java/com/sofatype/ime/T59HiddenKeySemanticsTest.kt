package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T59HiddenKeySemanticsTest {

    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    private val service: String get() = mainSource("SofatypeImeService.kt")

    @Test
    fun `CONFIRM 分流判定无焦点时落激活控件分支`() {
        assertFalse(SettingsKeyRouter.confirmActivatesIme(null))
    }

    @Test
    fun `BACK 隐藏态透传宿主且远程分支先于隐藏态判定`() {
        val backIdx = service.indexOf("if (keyCode == KeyEvent.KEYCODE_BACK)")
        assertTrue("BACK 分支存在", backIdx >= 0)
        val remoteIdx = service.indexOf("RemoteInputServerManager.isRunning()", backIdx)
        val hiddenIdx = service.indexOf("!isInputViewShown()", backIdx)
        assertTrue(
            "远程模式分支（T18 退出远程）须先于隐藏态判定执行（防误伤远程链路）",
            remoteIdx in backIdx until hiddenIdx,
        )
        val superIdx = service.indexOf("return super.onKeyDown(keyCode, ev)", hiddenIdx)
        val hideSelfIdx = service.indexOf("requestHideSelf(0)", hiddenIdx)
        assertTrue(
            "隐藏态 BACK 须透传宿主（return super，且在 requestHideSelf 之前）",
            superIdx > hiddenIdx && hideSelfIdx > superIdx,
        )
        assertTrue(
            "显示态 BACK 收起前须 finishComposition（拍板增量：收起路径统一组合态落定）",
            service.indexOf("finishComposition()", superIdx) in (superIdx + 1) until hideSelfIdx,
        )
    }

    @Test
    fun `方向键隐藏态透传宿主`() {
        val dirIdx = service.indexOf("val direction = DPAD_DIRECTIONS[keyCode]")
        assertTrue("方向键分支存在", dirIdx >= 0)
        val hiddenIdx = service.indexOf("!isInputViewShown()", dirIdx)
        assertTrue("方向键分支须含隐藏态 isInputViewShown 门控", hiddenIdx > dirIdx)
        val superIdx = service.indexOf("return super.onKeyDown(keyCode, ev)", hiddenIdx)
        val navIdx = service.indexOf("if (ev.action == KeyEvent.ACTION_DOWN", hiddenIdx)
        assertTrue(
            "隐藏态方向键须透传宿主（return super 在显示态导航分支之前）",
            superIdx > hiddenIdx && navIdx > superIdx,
        )

        val keyUpBody = service.substringAfter("override fun onKeyUp")
            .substringBefore("private fun Int.isConfirmKey()")
        assertFalse(
            "onKeyUp 不得新增方向键消费（DOWN/UP 对称落 super）",
            keyUpBody.contains("DPAD_DIRECTIONS"),
        )
    }

    @Test
    fun `确认键隐藏态消费并唤起键盘`() {

        val gateIdx = service.indexOf("!confirmWakeSessionActive()")
        assertTrue("确认键分支须含无会话门控（T88：confirmWakeSessionActive）", gateIdx >= 0)
        val hiddenTraceIdx = service.indexOf("swallowed（输入窗隐藏态）")
        assertTrue("确认键隐藏态分支须保留 T38 吞键 trace", hiddenTraceIdx >= 0)
        assertTrue(
            "唤起分支须在无会话门控之后（T88：会话活跃才走唤起）",
            hiddenTraceIdx > gateIdx,
        )
        val showSelfIdx = service.indexOf("requestShowSelf(0)", hiddenTraceIdx)
        val returnIdx = service.indexOf("return true", hiddenTraceIdx)
        assertTrue(
            "隐藏态确认键须 requestShowSelf 唤起键盘（拍板 3，T86 分层兜底首层字面调用），随后吞键返回",
            showSelfIdx > hiddenTraceIdx && returnIdx > showSelfIdx,
        )

        assertTrue(
            "T38 唤起抑制窗（shouldSwallowConfirmKey）接线保留（显示态语义零改动）",
            service.contains("showGuard.shouldSwallowConfirmKey("),
        )
    }

    @Test
    fun `会话活跃判定为双框架信号`() {

        val funIdx = service.indexOf("private fun confirmWakeSessionActive()")
        assertTrue("confirmWakeSessionActive 判定函数存在", funIdx >= 0)
        val body = service.substring(funIdx, service.indexOf("private fun", funIdx + 10))
        assertTrue(
            "判定须含会话级信号 getCurrentInputStarted",
            body.contains("getCurrentInputStarted()"),
        )
        assertTrue(
            "判定须含真实编辑器信号（EditorInfo.TYPE_NULL）",
            body.contains("EditorInfo.TYPE_NULL"),
        )
        assertTrue(
            "判定须读取 currentInputEditorInfo",
            body.contains("currentInputEditorInfo"),
        )
    }

    @Test
    fun `确认键隐藏态无会话透传宿主且先于唤起分支`() {

        val confirmIdx = service.indexOf("if (keyCode.isConfirmKey())")
        assertTrue("确认键分支存在", confirmIdx >= 0)
        val gateIdx = service.indexOf("if (!isInputViewShown() && !confirmWakeSessionActive())", confirmIdx)
        assertTrue("确认键分支须含隐藏态无会话门控（T88）", gateIdx > confirmIdx)
        val passIdx = service.indexOf("确认键隐藏态无输入会话 -> 透传宿主", gateIdx)
        val superIdx = service.indexOf("return super.onKeyDown(keyCode, ev)", gateIdx)
        val wakeTraceIdx = service.indexOf("swallowed（输入窗隐藏态）", gateIdx)
        val actionIdx = service.indexOf("if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0)", gateIdx)
        assertTrue(
            "无会话须 trace 留痕 + 透传宿主（return super），且先于唤起分支与显示态逻辑执行",
            passIdx > gateIdx && superIdx > passIdx && superIdx < wakeTraceIdx && actionIdx > superIdx,
        )
    }

    @Test
    fun `确认键无会话UP对称透传宿主`() {

        val keyUpIdx = service.indexOf("override fun onKeyUp")
        assertTrue("onKeyUp 存在", keyUpIdx >= 0)
        val confirmUpIdx = service.indexOf("keyCode.isConfirmKey()", keyUpIdx)
        val gateUpIdx = service.indexOf("if (!isInputViewShown() && !confirmWakeSessionActive())", confirmUpIdx)
        val traceUpIdx = service.indexOf("透传宿主（UP 对称）", gateUpIdx)
        val superUpIdx = service.indexOf("return super.onKeyUp(keyCode, event)", gateUpIdx)
        assertTrue(
            "onKeyUp 确认键分支须含无会话门控 + trace + UP 透传宿主（T88 对称）",
            confirmUpIdx > keyUpIdx && gateUpIdx > confirmUpIdx && traceUpIdx > gateUpIdx && superUpIdx > traceUpIdx,
        )
    }

    @Test
    fun `打字键码隐藏态透传（T45 与 T48 分支保留显示态门控）`() {

        val delIdx = service.indexOf("if (keyCode == KeyEvent.KEYCODE_DEL)")
        assertTrue("DEL 分支存在", delIdx >= 0)
        val delBody = service.substring(delIdx, service.indexOf("return super.onKeyDown", delIdx))
        assertTrue(
            "T48 DEL 分支 isInputViewShown 门控保留（隐藏态透传宿主 = 拍板 4 固化现状）",
            delBody.contains("isInputViewShown()"),
        )

        val letterCond = service.indexOf("KeyboardLayout.letterIdForKeyCode(keyCode)?.let")
        assertTrue("T45 字母 DOWN 分支存在", letterCond >= 0)
        val letterGate = service.lastIndexOf("isInputViewShown()", letterCond)
        assertTrue("T45 字母 DOWN 分支 isInputViewShown 门控保留", letterGate > 0)
    }

    @Test
    fun `字母键码UP隐藏态透传对称`() {
        val keyUpIdx = service.indexOf("override fun onKeyUp")
        assertTrue("onKeyUp 存在", keyUpIdx >= 0)
        val upBody = service.substring(keyUpIdx)
        val condIdx = upBody.indexOf("KeyboardLayout.letterIdForKeyCode(keyCode) != null && isInputViewShown()")
        assertTrue(
            "字母键码 UP 消费须加 isInputViewShown 门控（隐藏态透传对称 = 拍板 4；显示态消费保留防孤儿 UP 双写）",
            condIdx >= 0,
        )
    }

    @Test
    fun `下屏收起统一组合态落定`() {
        val hideSelfIdx = service.indexOf("KeyAction.HideSelf -> {")
        assertTrue("[下屏] 分支存在", hideSelfIdx >= 0)
        val finishIdx = service.indexOf("finishComposition()", hideSelfIdx)
        val hideIdx = service.indexOf("hideSelfAfterKeyDispatch()", hideSelfIdx)
        assertTrue(
            "[下屏] 收起前须 finishComposition（拍板 6：与 [完成] 收起一致）",
            finishIdx > hideSelfIdx && hideIdx > finishIdx,
        )
    }

    @Test
    fun `T38两窗与T53候选口径接线零改动`() {
        assertTrue(
            "T38 收起抑制窗（shouldVetoShowRequest）接线保留",
            service.contains("showGuard.shouldVetoShowRequest("),
        )
        assertTrue(
            "T38 迟到显示兜底（shouldRehideOnShown）接线保留",
            service.contains("showGuard.shouldRehideOnShown("),
        )
        assertTrue(
            "T38 收起开窗（onHideRequested）接线保留",
            service.contains("showGuard.onHideRequested("),
        )
        assertTrue(
            "显示态格网导航（handleKeyboardDirection）保留（FR-A11 零改动）",
            service.contains("handleKeyboardDirection(currentId, direction)"),
        )
        val occupancy = File("src/test/java/com/sofatype/ime/CandidatesBarOccupancyTest.kt").readText()
        assertTrue("T53 候选口径守卫测试文件存在（本测试不改动其锁定面）", occupancy.contains("决策 42"))
    }

    @Test
    fun `宿主侧CONFIRM按焦点类型分流`() {
        val router = mainSource("SettingsKeyRouter.kt")
        assertTrue(
            "SettingsKeyRouter 须定义 confirmActivatesIme 纯决策（拍板 5）",
            router.contains("fun confirmActivatesIme(focused: View?): Boolean"),
        )
        assertTrue(
            "分流判定须以 EditText 类型为准（可输入控件 = 唤起输入法）",
            router.contains("EditText"),
        )
        val activity = mainSource("SettingsActivity.kt")
        assertTrue(
            "SettingsActivity CONFIRM 分支须调用 confirmActivatesIme 分流",
            activity.contains("SettingsKeyRouter.confirmActivatesIme(focused)"),
        )
        assertTrue(
            "可输入控件分支须 showSoftInput 唤起键盘（拍板 5）",
            activity.contains("showSoftInput("),
        )
        assertTrue(
            "其余控件分支保留 performClick（T54 既有口径）",
            activity.contains("performClick()"),
        )
    }
}
