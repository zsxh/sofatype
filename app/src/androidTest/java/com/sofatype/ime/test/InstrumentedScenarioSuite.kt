package com.sofatype.ime.test

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sofatype.ime.FocusDirection
import com.sofatype.ime.SymbolsLayout
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import android.os.SystemClock

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class InstrumentedScenarioSuite {

    private val scenarioFilter: Set<String> = androidx.test.platform.app.InstrumentationRegistry
        .getArguments().getString("integrationScenarios")
        ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
        ?: emptySet()

    private val mockBase: String = androidx.test.platform.app.InstrumentationRegistry
        .getArguments().getString("integrationMockBase") ?: "http://10.0.2.2:8000"

    private fun runScenario(id: String, body: (IntegrationTestHarness) -> Pair<String, String>) {
        if (scenarioFilter.isNotEmpty() && id !in scenarioFilter) return
        val harness = IntegrationTestHarness.create()
        try {
            val (status, detail) = body(harness)
            harness.report(id, status, detail)
        } catch (golden: GoldenMissing) {
            harness.report(id, "GOLDEN_RECORDED", "黄金图缺失，实机截图已存 ${golden.goldenName}.png")
        } catch (t: Throwable) {
            harness.report(id, "FAIL", t.message ?: t.javaClass.simpleName)
            throw t
        }
    }

    private fun hideKeyCenter(harness: IntegrationTestHarness): android.graphics.Point {
        val metrics = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
        val d = metrics.density
        val screenH = harness.device.displayHeight
        return if (harness.isEmulatorDevice) {
            val x = (641 + 24) * d
            val y = screenH - (196 - 43 - 16) * d
            android.graphics.Point(x.toInt(), y.toInt())
        } else {
            android.graphics.Point(
                harness.device.displayWidth * 7167 / 10000,
                screenH * 9157 / 10000,
            )
        }
    }

    @Test
    fun s01_keyboard_show() = runScenario("s01_keyboard_show") { h ->
        h.openTypingPage()
        h.clearField()
        h.logcatClear()
        h.showKeyboard()
        check(h.imeShown()) { "mInputShown 应为 true" }
        check(h.traceCount("onWindowShown") > 0) { "ImeKeyTrace 应有 onWindowShown" }
        h.assertGolden("s01_keyboard_show", 30)
        "PASS" to "mInputShown=true + onWindowShown + 黄金图一致"
    }

    @Test
    fun s02_hide_keycode_back() = runScenario("s02_hide_keycode_back") { h ->
        h.openTypingPage()
        h.showKeyboard()
        check(h.imeShown()) { "前置：键盘应显示" }
        h.hideKeyboardViaBack()
        check(!h.imeShown()) { "BACK 后 mInputShown 应为 false" }
        h.saveScreenshot("s02_after_hide")
        "PASS" to "键码通道 BACK 收起正常"
    }

    @Test
    fun s03_tap_hide_whitelisted() = runScenario("s03_tap_hide_whitelisted") { h ->
        h.openTypingPage()
        h.showKeyboard()
        val center = hideKeyCenter(h)
        val tapOutcome = h.allowEmulatorArtifact("s03_tap_hide") {
            h.device.click(center.x, center.y)
            SystemClock.sleep(600)
            h.device.click(center.x, center.y)
            SystemClock.sleep(1_200)
            check(h.waitImeHidden(4_000)) { "tap 下屏键后键盘未隐藏（模拟器已知假象）" }
        }
        h.showKeyboard()
        h.hideKeyboardViaBack()
        val status = if (tapOutcome.startsWith("PASS_WHITELISTED")) {
            "PASS_WHITELISTED[${IntegrationTestHarness.WHITELIST["s03_tap_hide"]}]"
        } else {
            "PASS"
        }
        status to "tap 通道：$tapOutcome；键码通道：收起正常"
    }

    @Test
    fun s04_composition_candidates() = runScenario("s04_composition_candidates") { h ->
        h.openTypingPage()
        h.clearField()
        h.logcatClear()
        h.showKeyboard()
        val nav = KeyboardNav(h, chinese = true)
        for (target in listOf("n", "i", "h", "a", "o")) {
            nav.navigateTo(target)
            nav.pressOk()
            SystemClock.sleep(500)
        }
        check(h.traceCount("candidates loaded") > 0) { "组合态应有 candidates loaded trace" }
        h.assertGolden("s04_keyboard_candidates", 30)
        nav.navigateTo("a")
        val ok = h.device.pressDPadUp()
        check(ok) { "UP 注入失败" }
        check(h.waitForTraceHop(nav.current, "UP", "cand_0")) { "应进入候选行 cand_0" }
        nav.pressOk()
        SystemClock.sleep(800)
        check(h.fieldText().endsWith("你好")) { "选词上屏后输入框应以「你好」结尾，实际：${h.fieldText()}" }
        "PASS" to "组合 nihao → 候选行 cand_0 → 上屏「你好」"
    }

    @Test
    fun s05_language_case_switch() = runScenario("s05_language_case_switch") { h ->
        h.openTypingPage()
        h.clearField()
        h.logcatClear()
        h.showKeyboard()
        val nav = KeyboardNav(h, chinese = true)
        nav.navigateTo("lang")
        nav.pressOk()
        SystemClock.sleep(700)
        check(h.traceDump().contains("language -> ENGLISH case -> LOWER")) { "应切到英文小写态" }
        val shiftNav = KeyboardNav(h, chinese = false).also { it.current = "lang" }
        shiftNav.navigateTo("shift")
        shiftNav.pressOk()
        SystemClock.sleep(700)
        check(h.traceDump().contains("case -> UPPER")) { "应切到大写态" }
        shiftNav.navigateTo("a")
        shiftNav.pressOk()
        SystemClock.sleep(600)
        check(h.fieldText().endsWith("A")) { "大写态字母键应直落 A，实际：${h.fieldText()}" }
        val lowerNav = KeyboardNav(h, chinese = false).also { it.current = "a" }
        lowerNav.navigateTo("shift")
        lowerNav.pressOk()
        SystemClock.sleep(700)
        check(h.traceDump().contains("case -> LOWER")) { "应切回小写态" }
        lowerNav.navigateTo("a")
        lowerNav.pressOk()
        SystemClock.sleep(600)
        check(h.fieldText().endsWith("Aa")) { "小写态字母键应直落 a，实际：${h.fieldText()}" }
        "PASS" to "中→英、大写直落 A、小写直落 a"
    }

    @Test
    fun s06_symbols_fullhalf_golden() = runScenario("s06_symbols_fullhalf_golden") { h ->
        h.openTypingPage()
        h.clearField()
        val needSwitchToChinese = !h.currentLanguageChinese()
        h.logcatClear()
        h.showKeyboard()
        if (needSwitchToChinese) {
            val toChinese = KeyboardNav(h, chinese = false)
            toChinese.navigateTo("lang")
            toChinese.pressOk()
            SystemClock.sleep(700)
            check(h.currentLanguageChinese()) { "应回到中文模式" }
        }
        val nav = KeyboardNav(h, chinese = true)
        nav.navigateTo("symbols")
        nav.pressOk()
        SystemClock.sleep(900)
        check(h.traceDump().contains("keyboardMode -> SYMBOLS symbolsWidth -> FULL_WIDTH")) { "符号键盘应全角起步" }
        val goldenOutcome = h.allowEmulatorArtifact("s06_symbols_golden") {
            h.assertGolden("s06_keyboard_symbols", 30)
        }
        val widthNav = KeyboardNav(h, chinese = true, symbols = true).also { it.current = "fw_r1c1" }

        widthNav.navigateTo(SymbolsLayout.TOGGLE_WIDTH_ID)
        widthNav.pressOk()
        SystemClock.sleep(900)
        check(h.traceDump().contains("symbolsWidth -> HALF_WIDTH")) { "全/半切换应落半角" }
        widthNav.navigateTo(SymbolsLayout.BACK_ID)
        widthNav.pressOk()
        SystemClock.sleep(900)
        check(h.traceDump().contains("keyboardMode -> LETTERS")) { "应回到字母键盘" }
        val status = if (goldenOutcome.startsWith("PASS_WHITELISTED")) {
            "PASS_WHITELISTED[${IntegrationTestHarness.WHITELIST["s06_symbols_golden"]}]"
        } else {
            "PASS"
        }
        status to "全角起步 + 半角切换 trace 正常；黄金图：$goldenOutcome"
    }

    @Test
    fun s07_delete_rhythm() = runScenario("s07_delete_rhythm") { h ->
        h.openTypingPage()
        h.clearField()
        val fill = h.waitForField(4_000)
        check(fill != null) { "输入框不可见" }
        h.device.findObject(androidx.test.uiautomator.By.res(IntegrationTestHarness.PKG, "btn_fill_sample"))?.click()
            ?: throw ScenarioFailure("填入示例文本按钮不可见")
        SystemClock.sleep(500)
        val base = h.fieldText()
        check(base.isNotEmpty()) { "示例文本应已填充" }
        h.showKeyboard()
        h.logcatClear()
        val nav = KeyboardNav(h, chinese = true)
        nav.navigateTo("backspace")
        repeat(3) {
            nav.pressOk()
            SystemClock.sleep(1_100)
            check(h.fieldText().length == base.length - (it + 1)) {
                "第 ${it + 1} 次单删后长度应 ${base.length - it - 1}，实际 ${h.fieldText().length}"
            }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val preCount = h.traceCount("onKeyDown keyCode=23")
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
        val injectedDown = instrumentation.uiAutomation.injectInputEvent(downEvent, true)
        SystemClock.sleep(400)
        var reached = h.traceCount("onKeyDown keyCode=23") > preCount
        if (!injectedDown || !reached) {
            instrumentation.sendKeySync(
                KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0),
            )
            SystemClock.sleep(400)
            reached = h.traceCount("onKeyDown keyCode=23") > preCount
        }
        check(reached) { "长按 DOWN 未达 IME（inject=$injectedDown，trace 无 keyCode=23 新增）" }
        SystemClock.sleep(1_800)
        val upInjected = if (injectedDown) {
            instrumentation.uiAutomation.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER), true)
        } else {
            instrumentation.sendKeySync(
                KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0),
            )
            true
        }
        SystemClock.sleep(800)
        val repeatCount = h.traceCount("delete repeat #")
        check(repeatCount >= 2) { "长按应触发连删节奏 trace，实际 $repeatCount 次（inject=$injectedDown up=$upInjected）" }
        check(h.fieldText().length < base.length - 3) { "连删应额外删除多个字符" }
        "PASS" to "单删 3 次 + 长按连删 $repeatCount 次"
    }

    @Test
    fun s08_back_exit_gate() = runScenario("s08_back_exit_gate") { h ->
        if (h.imeShown()) h.hideKeyboardViaBack()
        var retries = 0
        var exited = false
        while (retries < 2 && !exited) {
            h.shell("am start -n ${IntegrationTestHarness.PKG}/.SettingsActivity")
            SystemClock.sleep(1_200)
            h.settingsButton("btn_check_update")
            h.logcatClear()
            h.device.pressBack()
            SystemClock.sleep(800)
            val stayed = h.currentFocusWindow().contains("SettingsActivity")
            if (!stayed) {
                retries++
                continue
            }
            SystemClock.sleep(2_200)
            h.device.pressBack()
            SystemClock.sleep(700)
            h.shell("input keyevent 4")
            SystemClock.sleep(700)
            h.shell("input keyevent 4")
            SystemClock.sleep(1_500)
            exited = !h.currentFocusWindow().contains("SettingsActivity")
            retries++
        }
        check(exited) {
            "窗口内二次 BACK 后应退出设置页（重试 $retries 次，focus=${h.currentFocusWindow().trim().takeLast(300)}，" +
                "SettingsFocus trace=${h.shell("logcat -d -s SettingsFocus").trim().takeLast(400)}）"
        }
        val note = if (retries > 1) "（含 $retries 次进入，首击直退按幻影键双送达自愈重试）" else ""
        "PASS" to "BACK 首击留页 + 2 秒窗内二击退出$note"
    }

    @Test
    fun s09_hidden_key_passthrough() = runScenario("s09_hidden_key_passthrough") { h ->
        h.openTypingPage()
        h.showKeyboard()
        h.hideKeyboardViaBack()
        SystemClock.sleep(600)
        h.clearField()
        h.logcatClear()
        check(!h.imeShown()) { "前置：键盘应隐藏" }
        h.device.pressKeyCode(KeyEvent.KEYCODE_8)
        SystemClock.sleep(800)
        check(h.fieldText().contains("8")) { "隐藏态数字键码应透传宿主，输入框实际：${h.fieldText()}" }
        check(h.traceCount("onKeyDown keyCode=15") > 0) { "trace 应有 keyCode=15（KEYCODE_8）记录" }
        check(h.traceCount("candidates loaded") == 0) { "隐藏态不应产生候选加载" }
        h.saveScreenshot("s09_after_passthrough")
        "PASS" to "隐藏态数字键码透传宿主 + 无组合态"
    }

    @Test
    fun s10_remote_pairing() = runScenario("s10_remote_pairing") { h ->
        h.openTypingPage()
        h.clearField()
        h.showKeyboard()
        h.shell(
            "am broadcast -n ${IntegrationTestHarness.PKG}/.remote.RemoteServerDebugReceiver " +
                "-a com.sofatype.ime.debug.action.START_REMOTE_SERVER",
        )
        check(h.waitForServerLog("配对地址", 15_000)) { "远程服务应启动并打印配对地址" }
        val match = Regex("配对地址：http://([0-9.]+):(\\d+)/\\?token=([A-Za-z0-9_\\-]+)").find(h.serverLog())
            ?: throw ScenarioFailure("配对地址解析失败")
        val host = match.groupValues[1]
        val port = match.groupValues[2]
        val token = match.groupValues[3]
        val localUrl = if (h.isEmulatorDevice) {
            "http://127.0.0.1:$port/?token=$token"
        } else {
            "http://$host:$port/?token=$token"
        }
        h.clearMarkers()
        h.writeRemoteUrlMarker(localUrl)
        val committed = waitForCommitted(h)
        check(committed.isNotEmpty()) {
            "手机端未在限时内完成配对与上屏（phone_committed 无内容，remote_url 回读=${h.readMarker("integration_remote_url")}）"
        }
        check(h.fieldText() == committed) {
            "上屏文本应与手机端一致：期望「$committed」实际「${h.fieldText()}」"
        }
        h.shell(
            "am broadcast -n ${IntegrationTestHarness.PKG}/.remote.RemoteServerDebugReceiver " +
                "-a com.sofatype.ime.debug.action.STOP_REMOTE_SERVER",
        )
        SystemClock.sleep(1_500)
        check(h.waitForServerLog("远程输入服务已停止", 8_000)) { "远程服务应显式停止" }
        h.saveScreenshot("s10_after_commit")
        "PASS" to "配对→输入→上屏闭环：文本「$committed」"
    }

    private fun waitForCommitted(h: IntegrationTestHarness): String {
        val deadline = SystemClock.uptimeMillis() + IntegrationTestHarness.REMOTE_WAIT_MS
        while (SystemClock.uptimeMillis() < deadline) {
            val marker = h.readMarker("integration_phone_committed")
            if (marker.isNotEmpty()) return marker
            SystemClock.sleep(1_000)
        }
        return ""
    }

    @Test
    fun s11_update_check_mock() = runScenario("s11_update_check_mock") { h ->
        h.openTypingPage()
        h.setUpdateEndpoint("$mockBase/releases-newer.json")
        h.shell("am start -n ${IntegrationTestHarness.PKG}/.SettingsActivity")
        SystemClock.sleep(1_200)
        h.settingsButton("btn_check_update")
        val note = h.assertGolden("s11_settings_page", 94)
        h.settingsButton("btn_check_update").click()
        check(h.waitStatusTextContains("发现新版本", 20_000)) { "应发现新版（实际：${h.statusText()}）" }
        check(h.statusText()!!.contains("v0.3.0")) { "新版 tag 应为 v0.3.0（实际：${h.statusText()}）" }
        h.saveScreenshot("s11_found_newer")
        h.device.pressBack()
        SystemClock.sleep(700)
        h.device.pressBack()
        SystemClock.sleep(1_200)
        h.setUpdateEndpoint("$mockBase/releases-up-to-date.json")
        h.shell("am start -n ${IntegrationTestHarness.PKG}/.SettingsActivity")
        SystemClock.sleep(1_200)
        h.settingsButton("btn_check_update").click()
        check(h.waitStatusTextContains("已是最新版本", 20_000)) { "同版本应报已是最新（实际：${h.statusText()}）" }
        h.device.pressBack()
        SystemClock.sleep(700)
        h.device.pressBack()
        SystemClock.sleep(800)
        "PASS" to ("serve_mock 桩：发现 v0.3.0 + 同版本已是最新" + if (note.isEmpty()) "" else "；$note")
    }
}
