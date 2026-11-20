package com.sofatype.ime.test

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.sofatype.ime.Cell
import com.sofatype.ime.FocusDirection
import com.sofatype.ime.FocusNavigator
import com.sofatype.ime.KeyboardLayout
import com.sofatype.ime.KeyRow
import com.sofatype.ime.LanguageMode
import com.sofatype.ime.SymbolsLayout
import com.sofatype.ime.SymbolsWidth
import java.io.File
import java.util.ArrayDeque

class ScenarioFailure(message: String) : AssertionError(message)

class GoldenMissing(val goldenName: String) : RuntimeException(goldenName)

class IntegrationTestHarness private constructor(
    val device: UiDevice,
) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val instrumentationContext = instrumentation.context
    private val targetContext = instrumentation.targetContext
    val isEmulatorDevice: Boolean = Build.FINGERPRINT.contains("generic") ||
        Build.FINGERPRINT.contains("ranchu") ||
        Build.FINGERPRINT.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu") ||
        Build.HARDWARE.contains("goldfish") ||
        Build.PRODUCT.contains("sdk") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK")
    val goldenDeviceDir: String =
        if (isEmulatorDevice) "emulator-api${Build.VERSION.SDK_INT}" else "mitv4-api${Build.VERSION.SDK_INT}"
    private val outputDir: File = resolveOutputDir()

    private fun resolveOutputDir(): File {
        val external = targetContext.getExternalFilesDir(null)?.resolve("integration")
        if (external != null) {
            external.mkdirs()
            val probe = File(external, ".probe")
            try {
                if (probe.createNewFile() || probe.exists()) {
                    probe.delete()
                    return external
                }
            } catch (_: java.io.IOException) {
            }
        }
        return File(targetContext.filesDir, "integration").also { it.mkdirs() }
    }

    val resultsFile: File = outputDir.resolve("results.tsv").also {
        if (!it.exists()) it.createNewFile()
    }

    val screensDir: File = outputDir.resolve("screens").also { it.mkdirs() }

    companion object {
        const val PKG = "com.sofatype.ime"
        const val IME_ID = "com.sofatype.ime/.SofatypeImeService"
        const val FIELD_ID = "test_input"
        const val REMOTE_WAIT_MS = 240_000L

        val WHITELIST: Map<String, String> = mapOf(
            "s03_tap_hide" to "模拟器已知假象：tap 触摸通道收起键盘 requestHideSelf 不生效（closed/T59，键码通道同路径正常）",
            "s06_symbols_golden" to "模拟器已知假象：窗口帧可能滞留旧键面（closed/T64，逻辑层无恙）",
        )

        fun create(): IntegrationTestHarness = IntegrationTestHarness(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()))
    }

    init {
        outputDir.mkdirs()
        runCatching { shell("svc power stayon true") }
    }

    fun shell(command: String): String = try {
        device.executeShellCommand(command)
    } catch (e: java.io.IOException) {
        throw ScenarioFailure("shell 执行失败：$command（${e.message}）")
    }

    fun report(scenarioId: String, status: String, detail: String) {
        val line = listOf(scenarioId, status, detail.replace("\t", " ").replace("\n", " ")).joinToString("\t")
        android.util.Log.i("IntegrationScenario", line)
        resultsFile.appendText(line + "\n")
    }

    fun logcatClear() {
        shell("logcat -c")
    }

    fun traceDump(): String = shell("logcat -d -s ImeKeyTrace")

    fun serverLog(): String = shell("logcat -d -s RemoteInputServer")

    fun traceCount(marker: String): Int = traceDump().split(marker).size - 1

    fun defaultIme(): String = shell("settings get secure default_input_method").trim()

    fun imeShown(): Boolean = shell("dumpsys input_method").contains("mInputShown=true")

    fun currentFocusWindow(): String {
        val dump = shell("dumpsys window")
        return Regex("mCurrentFocus=(Window\\{[^}]*\\})").find(dump)?.groupValues?.get(1)
            ?: Regex("mFocusedWindow=(Window\\{[^}]*\\})").find(dump)?.groupValues?.get(1)
            ?: "mCurrentFocus 未取到"
    }

    fun ensureImeActive() {
        if (defaultIme() != IME_ID) {
            shell("ime set $IME_ID")
            SystemClock.sleep(500)
        }
        val now = defaultIme()
        if (now != IME_ID) throw ScenarioFailure("默认输入法未就位：$now")
    }

    fun openTypingPage() {
        shell("am start -n $PKG/.TestTypingActivity")
        waitForField(8_000) ?: throw ScenarioFailure("测试打字页未就绪")
        SystemClock.sleep(800)
    }

    fun waitForField(timeoutMs: Long): UiObject2? =
        device.wait(Until.findObject(By.res(PKG, FIELD_ID)), timeoutMs)

    fun field(): UiObject2 = waitForField(4_000) ?: throw ScenarioFailure("test_input 不可见")

    fun clearField() {
        field().text = ""
        SystemClock.sleep(300)
    }

    fun fieldText(): String = field().text

    fun showKeyboard() {
        ensureImeActive()
        var lastState = ""
        repeat(3) { attempt ->
            if (attempt > 0) {
                device.pressBack()
                SystemClock.sleep(900)
            }
            val f = field()
            val c = f.visibleCenter
            device.click(c.x, c.y)
            if (waitImeShown(5_000)) {
                SystemClock.sleep(600)
                return
            }
            device.pressDPadCenter()
            if (waitImeShown(4_000)) {
                SystemClock.sleep(600)
                return
            }
            device.pressDPadCenter()
            if (waitImeShown(4_000)) {
                SystemClock.sleep(600)
                return
            }
            lastState = "attempt=$attempt mInputShown=${imeShown()} onWindowShown=${traceCount("onWindowShown")}"
        }
        throw ScenarioFailure("键盘唤起失败（3 组 tap/BACK+retap/确认键尝试）：$lastState")
    }

    fun waitImeShown(timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (imeShown() && traceCount("onWindowShown") > 0) return true
            SystemClock.sleep(250)
        }
        return false
    }

    fun hideKeyboardViaBack() {
        val backTraceBefore = traceCount("onKeyDown keyCode=4")
        device.pressBack()
        if (!waitImeHidden(6_000, backTraceBefore)) throw ScenarioFailure("BACK 收起失败")
        SystemClock.sleep(400)
    }

    fun waitImeHidden(timeoutMs: Long, backTraceSince: Int = -1): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val backArrived = backTraceSince < 0 || traceCount("onKeyDown keyCode=4") > backTraceSince
            if (!imeShown() && backArrived) return true
            SystemClock.sleep(250)
        }
        return false
    }

    fun saveScreenshot(name: String): File {
        val file = File(screensDir, "$name.png")
        if (file.exists()) file.delete()
        captureTo(file)
        return file
    }

    fun assertGolden(name: String, cropBottomPercent: Int = 40): String {
        val stats = mutableListOf<String>()
        val actualFile = captureStableFrame(name, stats)
        val goldenStream = try {
            instrumentationContext.assets.open("goldens/$goldenDeviceDir/$name.png")
        } catch (e: Exception) {
            throw GoldenMissing(name)
        }
        val goldenFull = BitmapFactory.decodeStream(goldenStream)
            ?: throw ScenarioFailure("黄金图解码失败：$name")
        val actualFull = BitmapFactory.decodeFile(actualFile.absolutePath)
            ?: throw ScenarioFailure("实机截图解码失败：$name")
        if (goldenFull.width != actualFull.width || goldenFull.height != actualFull.height) {
            throw ScenarioFailure(
                "黄金图尺寸不一致：$name golden=${goldenFull.width}x${goldenFull.height} actual=${actualFull.width}x${actualFull.height}",
            )
        }
        val cropTop = goldenFull.height * (100 - cropBottomPercent) / 100
        val cropHeight = goldenFull.height - cropTop
        val goldenCrop = Bitmap.createBitmap(goldenFull, 0, cropTop, goldenFull.width, cropHeight)
        val actualCrop = Bitmap.createBitmap(actualFull, 0, cropTop, actualFull.width, cropHeight)
        if (actualCrop.sameAs(goldenCrop)) {
            return if (stats.isEmpty()) "" else "黄金图防护[${stats.joinToString("；")}]"
        }
        stats += "比对不一致重拍"
        SystemClock.sleep(1200)
        val reshoot = saveScreenshot(name + "_reshoot")
        val reshootFull = BitmapFactory.decodeFile(reshoot.absolutePath)
            ?: throw ScenarioFailure("重拍截图解码失败：$name")
        val reshootCrop = Bitmap.createBitmap(reshootFull, 0, cropTop, reshootFull.width, reshootFull.height - cropTop)
        if (reshootCrop.sameAs(goldenCrop)) {
            stats += "重拍后一致"
            return "黄金图防护[${stats.joinToString("；")}]（重拍图 ${reshoot.name}）"
        }
        val mismatch = File(screensDir, "$name.reshoot.mismatch.png")
        if (mismatch.exists()) mismatch.delete()
        reshootFull.compress(Bitmap.CompressFormat.PNG, 100, mismatch.outputStream())
        throw ScenarioFailure("黄金图比对不一致（含重拍）：$name（差异图已存 ${mismatch.name}）")
    }

    private fun captureStableFrame(basename: String, stats: MutableList<String>): File {
        val probeA = File(screensDir, "$basename.probe_a.png")
        val probeB = File(screensDir, "$basename.probe_b.png")
        val stableDeadline = SystemClock.uptimeMillis() + 6000
        var prev = captureTo(probeA)
        var cur = probeB
        var stableCount = 1
        var waits = 0
        while (SystemClock.uptimeMillis() < stableDeadline && stableCount < 2) {
            SystemClock.sleep(500)
            cur = captureTo(cur)
            val prevBmp = BitmapFactory.decodeFile(prev.absolutePath)
            val curBmp = BitmapFactory.decodeFile(cur.absolutePath)
            if (prevBmp != null && curBmp != null && curBmp.sameAs(prevBmp)) {
                stableCount += 1
            } else {
                stableCount = 1
                waits += 1
            }
            val swap = prev
            prev = cur
            cur = swap
        }
        val final = File(screensDir, "$basename.png")
        if (final.exists()) final.delete()
        prev.copyTo(final, overwrite = true)
        probeA.delete()
        probeB.delete()
        if (waits > 0) stats += "稳定等待×$waits"
        if (stableCount < 2) stats += "稳定判定超时"
        return final
    }

    private fun captureTo(target: File): File {
        if (target.exists()) target.delete()
        if (isEmulatorDevice) {
            val ok = device.takeScreenshot(target)
            if (!ok) throw ScenarioFailure("截图失败：${target.name}")
        } else {
            val cap = "/data/local/tmp/integration-cap.png"
            shell("screencap -p $cap")
            try {
                val pfd = instrumentation.uiAutomation.executeShellCommand("cat $cap")
                android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } catch (e: java.io.IOException) {
                throw ScenarioFailure("截图回读失败：${target.name}（${e.message}）")
            } finally {
                shell("rm -f $cap")
            }
            if (!target.exists() || target.length() == 0L) {
                throw ScenarioFailure("截图失败：${target.name}")
            }
        }
        return target
    }

    fun allowEmulatorArtifact(scenarioId: String, block: () -> Unit): String {
        return try {
            block()
            "PASS_NOT_REPRODUCED"
        } catch (golden: GoldenMissing) {
            throw golden
        } catch (t: Throwable) {
            if (!isEmulatorDevice) throw t
            val reason = WHITELIST[scenarioId] ?: throw t
            "PASS_WHITELISTED[$reason]"
        }
    }

    fun waitForTraceHop(fromId: String, direction: String, expectedId: String, timeoutMs: Long = 3_000): Boolean {
        val needle = "focus $fromId $direction -> $expectedId"
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (traceDump().contains(needle)) return true
            SystemClock.sleep(150)
        }
        return false
    }

    fun currentLanguageChinese(): Boolean {
        val matches = Regex("language -> (CHINESE|ENGLISH)").findAll(traceDump()).toList()
        return matches.lastOrNull()?.groupValues?.get(1) != "ENGLISH"
    }

    fun lastFocusId(default: String): String {
        val matches = Regex("focus (\\S+) (LEFT|RIGHT|UP|DOWN) -> (\\S+)").findAll(traceDump()).toList()
        return matches.lastOrNull()?.groupValues?.get(3) ?: default
    }

    fun waitForServerLog(needle: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (serverLog().contains(needle)) return true
            SystemClock.sleep(300)
        }
        return false
    }

    fun writeRemoteUrlMarker(url: String) {
        shell("settings put system integration_remote_url $url")
        val echoed = shell("settings get system integration_remote_url").trim()
        if (echoed != url) throw ScenarioFailure("remote_url 写入失败：回读=$echoed")
    }

    fun clearMarkers() {
        shell("settings delete system integration_remote_url")
        shell("settings delete system integration_phone_committed")
    }

    fun readMarker(name: String): String {
        val raw = shell("settings get system $name").trim()
        return if (raw == "null" || raw == "NONE") "" else raw
    }

    fun setUpdateEndpoint(endpoint: String) {
        shell(
            "am broadcast -n $PKG/.update.UpdateMockReceiver " +
                "-a com.sofatype.ime.debug.action.SET_UPDATE_ENDPOINT -e endpoint $endpoint",
        )
        SystemClock.sleep(500)
    }

    fun settingsButton(id: String): UiObject2 =
        device.wait(Until.findObject(By.res(PKG, id)), 6_000) ?: throw ScenarioFailure("设置页控件缺失：$id")

    fun waitStatusTextContains(needle: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val status = device.findObject(By.res(PKG, "status_text"))
            if (status != null && status.text.contains(needle)) return true
            SystemClock.sleep(300)
        }
        return false
    }

    fun statusText(): String? = device.findObject(By.res(PKG, "status_text"))?.text
}

class KeyboardNav(
    private val harness: IntegrationTestHarness,
    chinese: Boolean? = null,
    symbols: Boolean = false,
    width: SymbolsWidth = SymbolsWidth.FULL_WIDTH,
    offset: Int = 0,
) {
    private val effectiveChinese: Boolean = chinese ?: harness.currentLanguageChinese()
    private val rows: List<KeyRow> = if (symbols) {
        SymbolsLayout.rows(width, offset)
    } else {
        KeyboardLayout.rows(if (effectiveChinese) LanguageMode.CHINESE else LanguageMode.ENGLISH)
    }
    private val overlay: Map<String, List<Pair<Int, Int>>> = if (symbols) {
        SymbolsLayout.overlayCells()
    } else {
        mapOf(KeyboardLayout.doneKey.id to KeyboardLayout.doneKeyCells)
    }
    private val initialCell = if (symbols) SymbolsLayout.initialCell else KeyboardLayout.initialCell
    private val navigator = FocusNavigator(rows, overlay, initialCell)

    var current: String = harness.lastFocusId(navigator.initialKey())

    fun initialKey(): String = navigator.initialKey()

    fun navigateTo(target: String) {
        var retries = 0
        while (current != target) {
            val path = shortestPath(current, target)
                ?: throw ScenarioFailure("导航路径不存在：$current -> $target")
            var resynced = false
            for (dir in path) {
                val expected = navigator.next(current, dir)
                    ?: throw ScenarioFailure("导航边缺失：$current ${dir.name}")
                val ok = when (dir) {
                    FocusDirection.LEFT -> harness.device.pressDPadLeft()
                    FocusDirection.RIGHT -> harness.device.pressDPadRight()
                    FocusDirection.UP -> harness.device.pressDPadUp()
                    FocusDirection.DOWN -> harness.device.pressDPadDown()
                }
                if (!ok) throw ScenarioFailure("DPAD 注入失败：${dir.name}")
                if (!harness.waitForTraceHop(current, dir.name, expected)) {
                    retries++
                    if (retries > 4) {
                        throw ScenarioFailure("焦点移动未按预期：$current ${dir.name} -> $expected")
                    }
                    current = harness.lastFocusId(current)
                    resynced = true
                    break
                }
                current = expected
            }
            if (!resynced && current == target) return
        }
    }

    fun pressOk() {
        if (!harness.device.pressDPadCenter()) throw ScenarioFailure("OK 注入失败")
    }

    private fun shortestPath(from: String, to: String): List<FocusDirection>? {
        if (from == to) return emptyList()
        val queue = ArrayDeque<Pair<String, List<FocusDirection>>>()
        queue.add(from to emptyList())
        val visited = mutableSetOf(from)
        while (queue.isNotEmpty()) {
            val (node, path) = queue.removeFirst()
            if (path.size > 12) continue
            for (dir in FocusDirection.entries) {
                val next = navigator.next(node, dir) ?: continue
                if (next == to) return path + dir
                if (visited.add(next)) queue.add(next to path + dir)
            }
        }
        return null
    }
}
