// T54 缺陷修复 Round 1 ＋ T62 Round 1 · 守卫单测（2026-11-12 并案轮）：
// ① T54 缺陷①（键盘自发重显劫持遥控键，设备档案坑 23）：收起沿焦点接管目标 = test_input
//    （会话保活），且仅在可见性跳变沿执行；接管决策语义修订以纯函数四象限锁定
//    （SettingsFocusTakeoverTest）；本文件锁接线与禁区。
// ② T62（IC 输出黑洞，设备档案坑 22）：输入法切换广播接收接线（restartInput 重建会话）
//    + onDestroy 注销（零残留）+ 门控块接线（~~release 设置页无输入框~~ → 被 T79 · 决策 65
//    取代：debug / release 均接线，release 也有输入框、黑洞面存在）。
// T63（docs/PRD_20261112.md）：调试三件套迁入独立测试打字页，T54 接线与 T62 接线承载面
// 随迁——扫描目标由 SettingsActivity 改为 TestTypingActivity（pre-IME 分路设置页保留项
// 与 IME 服务禁区不变）。
// SettingsActivity 为 Android 交互层，纯 JVM 单测无法实例化（无 Robolectric，仓库口径），
// 接线以源码扫描锁定——先例：SettingsFocusTakeoverTest（T54 R2）、T59HiddenKeySemanticsTest（T59）。
// T77（docs/issues/open/T77.md）：测试打字页双输入框表单——收起沿接管目标与 T62 restartInput
// 目标适配为「当前持焦输入框优先，无则回落 test_input」（SettingsFocusTakeover.takeoverTarget
// 纯函数 + T77DualInputFormTest 锁定），本文件两处接线断言同步修订（先例：T63 承载面随迁改扫描目标）。
package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T54DefectFixTest {

    /** 路径口径 = app/ 模块目录（SettingsFocusTakeoverTest 同口径）。 */
    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    /** T63：T54/T62 接线承载页 = 测试打字页（调试三件套所在页）。 */
    private val carrier: String get() = mainSource("TestTypingActivity.kt")
    private val activity: String get() = mainSource("SettingsActivity.kt")
    private val service: String get() = mainSource("SofatypeImeService.kt")

    // ── ① T54 缺陷修复：收起沿焦点回编辑框 ──

    @Test
    fun `焦点接管仅在可见性跳变沿执行且目标为编辑框`() {
        // T63：扫描目标 = TestTypingActivity（接线承载面随调试区迁移）
        val layoutIdx = carrier.indexOf("imeShown != lastImeShown")
        assertTrue("可见性跳变沿判定存在（防非沿布局抢焦点）", layoutIdx >= 0)
        val edgeEnd = carrier.indexOf("lastImeShown = imeShown", layoutIdx)
        assertTrue("跳变沿分支闭合存在", edgeEnd > layoutIdx)
        val edgeBody = carrier.substring(layoutIdx, edgeEnd)
        assertTrue(
            "接管须在跳变沿分支内（T54 缺陷修复 R1）",
            edgeBody.contains("SettingsFocusTakeover.shouldTakeFocus("),
        )
        assertTrue(
            "收起沿须无条件 requestFocusFromTouch（焦点回编辑框 + 退出触摸模式：触摸模式下 FocusFinder 移焦静默失效，走查实测；T77 双框目标经 takeoverTarget 判定，锁定见 T77DualInputFormTest）",
            edgeBody.contains("requestFocusFromTouch()") &&
                edgeBody.contains("退出触摸模式"),
        )
        assertFalse(
            "接管目标不得再指向 btn_check_update（T54 R2 旧目标废止）",
            edgeBody.contains("btnCheckUpdate.requestFocusFromTouch()"),
        )
    }

    @Test
    fun `收起沿接管日志与会话保活语义落源码`() {
        assertTrue(
            "收起沿接管日志存在（走查取证锚点）",
            carrier.contains("焦点接管：键盘已收起，焦点回 test_input（输入会话保活）"),
        )
        assertTrue(
            "接管决策语义 = 收起且编辑框不持焦点（tap 过按钮态）",
            mainSource("SettingsFocusTakeover.kt").contains("!imeShown && !editorHasFocus"),
        )
    }

    @Test
    fun `preIME 分路与 T59 宿主分流零改动保留`() {
        // 显示态透传 IME 门控（打字链路零改动）保留
        assertTrue(
            "键盘显示态须透传 IME（T54 R2 既有门控）",
            activity.contains("if (lastImeShown == true) return false"),
        )
        // T59 拍板 5 宿主分流保留
        assertTrue(
            "CONFIRM 按焦点分流（拍板 5）保留",
            activity.contains("SettingsKeyRouter.confirmActivatesIme(focused)"),
        )
        assertTrue("可输入控件分支 showSoftInput 保留（拍板 5）", activity.contains("showSoftInput("))
        assertTrue("其余控件分支 performClick 保留（T54 口径）", activity.contains("performClick()"))
    }

    // ── ② T62：输入法切换广播重建会话 ──

    @Test
    fun `输入法切换广播接线重建输入会话`() {
        // T63：扫描目标 = TestTypingActivity（接线承载面随调试区迁移）
        assertTrue(
            "须注册 ACTION_INPUT_METHOD_CHANGED 广播（T62）",
            carrier.contains("IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED)"),
        )
        assertTrue(
            "收到广播须 restartInput 重建输入会话（T62；T77 双框：目标 = 当前持焦输入框，无则回落 test_input，锁定见 T77DualInputFormTest）",
            carrier.contains("imm?.restartInput(restartTarget)"),
        )
        assertTrue(
            "切换广播日志存在（走查取证锚点）",
            carrier.contains("输入法切换广播 -> 重建输入会话（T62）"),
        )
    }

    @Test
    fun `T62 接线须在门控块内且随窗口注销`() {
        // T63：扫描目标 = TestTypingActivity（接线承载面随调试区迁移）
        val debugIdx = carrier.indexOf("TestTypingGate.contentVisible(BuildConfig.DEBUG)")
        // 门控块之后的代码级接线（绕过类头注释中的 action 文字说明）
        val imeIdx = carrier.indexOf("Intent.ACTION_INPUT_METHOD_CHANGED)", debugIdx)
        assertTrue("门控块存在（TestTypingGate 承载，T79 · 决策 65 起 debug / release 均放行）", debugIdx >= 0)
        assertTrue(
            "T62 广播接线须在门控块内（T79 · 决策 65：release 同样接线生效——release 也有输入框，黑洞面存在）",
            imeIdx > debugIdx,
        )
        assertTrue(
            "onDestroy 须注销输入法切换接收器（退出零残留，AGENTS 禁止事项 3）",
            carrier.contains("imeChangedReceiver?.let { unregisterReceiver(it) }"),
        )
    }

    // ── ③ IME 服务禁区（T59 拍板口径零改动面）──

    @Test
    fun `IME服务保持零改动且T59口径接线在位`() {
        assertFalse(
            "SofatypeImeService 不得引用设置页侧本轮新接线（T59 挂账禁区）",
            service.contains("SettingsFocusTakeover") ||
                service.contains("SettingsKeyRouter") ||
                service.contains("SettingsRootView"),
        )
        assertTrue(
            "隐藏态确认键 requestShowSelf 唤起保留（拍板 3）",
            service.contains("requestShowSelf(0)"),
        )
        assertTrue(
            "隐藏态 BACK 透传保留（拍板 1）",
            service.contains("return super.onKeyDown(keyCode, ev)"),
        )
        assertTrue(
            "T38 两窗接线保留（shouldVetoShowRequest / shouldSwallowConfirmKey）",
            service.contains("showGuard.shouldVetoShowRequest(") &&
                service.contains("showGuard.shouldSwallowConfirmKey("),
        )
    }
}
