package com.sofatype.ime.test

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class T92PerfBudgetTest {

    private fun arg(name: String, def: String): String =
        InstrumentationRegistry.getArguments().getString(name) ?: def

    private fun perfLines(harness: T91Harness): List<String> =
        harness.shell("logcat -d -s PerfTrace").lines().filter { it.contains("PerfTrace:") }

    private fun samplesOf(lines: List<String>, span: String): List<PerfSample> =
        lines.mapNotNull { T92PerfSupport.parseLine(it) }.filter { it.span == span }

    private fun assertAllWellFormed(lines: List<String>) {
        val bad = lines.filter { T92PerfSupport.parseLine(it) == null }
        check(bad.isEmpty()) {
            "PerfTrace 行格式异常（零泄漏防线，含内容字段的行会判非法）：${bad.take(3)}"
        }
    }

    private fun loadBaseline(harness: T91Harness): Pair<ParsedBaseline?, String> {
        val path = arg("t92BaselinePath", "")
        if (path.isEmpty()) return null to "未提供 t92BaselinePath，仅判预算绝对值"
        val raw = harness.shell("cat $path")
        if (raw.isBlank() || raw.contains("No such file") || raw.contains("Permission denied")) {
            return null to "基线文件不可读：$path"
        }
        return try {
            val parsed = T92PerfSupport.parseBaseline(raw)
            parsed to ("synthetic=" + parsed.synthetic)
        } catch (t: Throwable) {
            null to "基线解析失败（$path）：${t.message}"
        }
    }

    private fun requirePass(span: String, verdict: PerfVerdict): String =
        when (verdict) {
            is PerfVerdict.Pass -> verdict.detail
            is PerfVerdict.Fail -> throw ScenarioFailure("$span 判定 FAIL：${verdict.reasons.joinToString("；")}")
        }

    @Test
    fun p00_stats_selftest_synthetic() {
        val harness = T91Harness.create()
        val s = T92PerfSupport
        val line = "11-20 10:00:00.000  1000  1000 D PerfTrace: key_feedback seq=1 durationMs=42.500"
        val parsed = s.parseLine(line)
        check(parsed != null && parsed.span == s.SPAN_KEY_FEEDBACK && parsed.seq == 1) { "synthetic 行应可解析：$parsed" }
        check(Math.abs(parsed!!.durationMs - 42.5) < 1e-9) { "synthetic 耗时应为 42.5：${parsed.durationMs}" }
        val cand = s.parseLine("11-20 10:00:00.000  1000  1000 D PerfTrace: candidate_refresh seq=2 durationMs=18.000 total=10 pages=1")
        check(cand?.extra?.get("total") == "10" && cand.extra["pages"] == "1") { "synthetic candidate 行应可解析：$cand" }
        val ws = s.parseLine("11-20 10:00:00.000  1000  1000 D PerfTrace: ws_commit seq=3 durationMs=120.000 committed=true")
        check(ws?.extra?.get("committed") == "true") { "synthetic ws 行应可解析：$ws" }
        val dropped = s.parseLine("11-20 10:00:00.000  1000  1000 D PerfTrace: key_feedback seq=4 dropped=true")
        check(dropped != null && s.isDropped(dropped) && !s.hasDuration(dropped)) { "dropped 计数行应可解析且无耗时：$dropped" }
        check(s.parseLine("11-20 10:00:00.000  1000  1000 D PerfTrace: key_feedback seq=4 durationMs=1.000 text=你好") == null) {
            "带内容字段的行必须判非法（零泄漏防线）"
        }
        check(s.parseLine("11-20 10:00:00.000  1000  1000 D PerfTrace: bogus seq=4 durationMs=1.000") == null) {
            "未知 span 必须判非法"
        }
        check(s.median(listOf(3.0, 1.0, 2.0)) == 2.0) { "奇数中位数" }
        check(s.median(listOf(1.0, 2.0, 3.0, 4.0)) == 2.5) { "偶数中位数" }
        val p95 = s.percentile((1..100).map { it.toDouble() }, 95.0)
        check(Math.abs(p95 - 95.05) < 1e-9) { "synthetic p95 应为 95.05：$p95" }
        val syntheticDurations = (1..20).map { 80.0 + it }
        val pass = s.judge(s.SPAN_KEY_FEEDBACK, syntheticDurations, 20, BaselineMetric(100.0, 20, 100.0, 150.0))
        check(pass is PerfVerdict.Pass) { "synthetic 全达预期应 PASS：$pass" }
        val overBudget = s.judge(s.SPAN_KEY_FEEDBACK, List(20) { 150.0 }, 20, null)
        check(overBudget is PerfVerdict.Fail && overBudget.reasons.any { it.contains("中位数") }) { "synthetic 超预算应 FAIL：$overBudget" }
        val degraded = s.judge(s.SPAN_KEY_FEEDBACK, List(20) { 90.0 }, 20, BaselineMetric(100.0, 20, 70.0, 90.0))
        check(degraded is PerfVerdict.Fail && degraded.reasons.any { it.contains("劣化") }) { "synthetic 劣化应 FAIL：$degraded" }
        val few = s.judge(s.SPAN_KEY_FEEDBACK, List(5) { 50.0 }, 20, null)
        check(few is PerfVerdict.Fail && few.reasons.any { it.contains("采样数不足") }) { "synthetic 样本不足应 FAIL：$few" }
        harness.report("t92_p00_stats_selftest", "PASS", "synthetic 样例自测通过（全部判定分支）")
    }

    @Test
    fun p01_key_spans_sample_and_assert() {
        val harness = T91Harness.create()
        try {
            val minSamples = arg("t92MinSamples", "20").toInt().coerceAtLeast(1)
            harness.ensureImeActive()
            harness.openTypingPage()
            harness.clearField()
            harness.showKeyboard()
            if (!harness.currentLanguageChinese()) {
                val toChinese = KeyboardNav(harness, chinese = false)
                toChinese.navigateTo("lang")
                toChinese.pressOk()
                SystemClock.sleep(700)
                check(harness.currentLanguageChinese()) { "应回到中文模式" }
            }
            harness.logcatClear()
            val nav = KeyboardNav(harness, chinese = true)
            val letters = listOf("n", "i", "h", "a", "o")
            val cycles = (minSamples + 8) / 9
            repeat(cycles) {
                for (letter in letters) {
                    nav.navigateTo(letter)
                    nav.pressOk()
                    SystemClock.sleep(2_200)
                }
                repeat(5) {
                    nav.navigateTo("backspace")
                    nav.pressOk()
                    SystemClock.sleep(2_200)
                }
            }
            val lines = perfLines(harness)
            assertAllWellFormed(lines)
            val keyFeedback = samplesOf(lines, T92PerfSupport.SPAN_KEY_FEEDBACK).filter { T92PerfSupport.hasDuration(it) }
            val candidates = samplesOf(lines, T92PerfSupport.SPAN_CANDIDATE_REFRESH).filter { T92PerfSupport.hasDuration(it) }
            val candidateLoaded = candidates.count { (it.extra["total"]?.toIntOrNull() ?: 0) > 0 }
            val (baseline, baselineNote) = loadBaseline(harness)
            val keyVerdict = T92PerfSupport.judge(
                T92PerfSupport.SPAN_KEY_FEEDBACK,
                keyFeedback.map { it.durationMs },
                minSamples,
                baseline?.metrics?.get(T92PerfSupport.SPAN_KEY_FEEDBACK),
            )
            val candVerdict = T92PerfSupport.judge(
                T92PerfSupport.SPAN_CANDIDATE_REFRESH,
                candidates.map { it.durationMs },
                minSamples,
                baseline?.metrics?.get(T92PerfSupport.SPAN_CANDIDATE_REFRESH),
            )
            val keyDetail = requirePass("key_feedback", keyVerdict)
            val candDetail = requirePass("candidate_refresh", candVerdict)
            val detail = "key_feedback：$keyDetail；candidate_refresh：$candDetail；" +
                "候选非空样本=$candidateLoaded；基线=$baselineNote"
            harness.report("t92_p01_key_spans", "PASS", detail)
        } catch (t: Throwable) {
            harness.report("t92_p01_key_spans", "FAIL", t.message ?: t.javaClass.simpleName)
            throw t
        }
    }

    @Test
    fun p02_ws_commit_sample_and_assert() {
        val harness = T91Harness.create()
        try {
            val minSamples = arg("t92MinSamples", "20").toInt().coerceAtLeast(1)
            val timeoutMs = arg("t92WsTimeoutMs", "240000").toLong()
            val preCollected = arg("t92PreCollected", "0") == "1"
            harness.ensureImeActive()
            if (!preCollected) {
                harness.openTypingPage()
                harness.clearField()
                harness.showKeyboard()
                harness.shell("settings delete system t92_ws_done")
                harness.shell("settings delete system t92_ws_count")
                harness.shell(
                    "am broadcast -n ${T91Harness.PKG}/.remote.RemoteServerDebugReceiver " +
                        "-a com.sofatype.ime.debug.action.START_REMOTE_SERVER",
                )
                check(harness.waitForServerLog("配对地址", 15_000)) { "远程服务应启动并打印配对地址" }
                val match = Regex("配对地址：http://([0-9.]+):(\\d+)/\\?token=([A-Za-z0-9_\\-]+)").find(harness.serverLog())
                    ?: throw ScenarioFailure("配对地址解析失败")
                val localUrl = if (harness.isEmulatorDevice) {
                    "http://127.0.0.1:${match.groupValues[2]}/?token=${match.groupValues[3]}"
                } else {
                    "http://${match.groupValues[1]}:${match.groupValues[2]}/?token=${match.groupValues[3]}"
                }
                harness.shell("settings put system t92_remote_url $localUrl")
                check(harness.shell("settings get system t92_remote_url").trim() == localUrl) { "remote_url 写入失败" }

                fun committedCount(): Int = samplesOf(perfLines(harness), T92PerfSupport.SPAN_WS_COMMIT)
                    .count { it.extra["committed"] == "true" }

                var insufficient: String? = null
                val deadline = SystemClock.uptimeMillis() + timeoutMs
                while (SystemClock.uptimeMillis() < deadline) {
                    if (committedCount() >= minSamples) break
                    if (harness.shell("settings get system t92_ws_done").trim() == "1") {
                        val declared = harness.shell("settings get system t92_ws_count").trim().toIntOrNull() ?: -1
                        insufficient = "采样驱动已结束但 committed=true 样本 ${committedCount()} < $minSamples（驱动声明 $declared）"
                        break
                    }
                    SystemClock.sleep(1_000)
                }
                if (insufficient == null && committedCount() < minSamples) {
                    insufficient = "采样超时：committed=true 样本 ${committedCount()} < $minSamples（timeout=${timeoutMs}ms）"
                }

                harness.shell(
                    "am broadcast -n ${T91Harness.PKG}/.remote.RemoteServerDebugReceiver " +
                        "-a com.sofatype.ime.debug.action.STOP_REMOTE_SERVER",
                )
                check(harness.waitForServerLog("远程输入服务已停止", 8_000)) { "远程服务应显式停止" }
                harness.shell("settings delete system t92_remote_url")
                harness.shell("settings delete system t92_ws_done")
                harness.shell("settings delete system t92_ws_count")

                check(insufficient == null) { insufficient ?: "采样不足" }
            }

            val lines = perfLines(harness)
            assertAllWellFormed(lines)
            val wsOk = samplesOf(lines, T92PerfSupport.SPAN_WS_COMMIT)
                .filter { T92PerfSupport.hasDuration(it) && it.extra["committed"] == "true" }
            val wsFailed = samplesOf(lines, T92PerfSupport.SPAN_WS_COMMIT).count { it.extra["committed"] == "false" }
            val (baseline, baselineNote) = loadBaseline(harness)
            val verdict = T92PerfSupport.judge(
                T92PerfSupport.SPAN_WS_COMMIT,
                wsOk.map { it.durationMs },
                minSamples,
                baseline?.metrics?.get(T92PerfSupport.SPAN_WS_COMMIT),
            )
            val detail = requirePass("ws_commit", verdict) + "；写失败样本=$wsFailed；基线=$baselineNote"
            harness.report("t92_p02_ws_commit", "PASS", detail)
        } catch (t: Throwable) {
            harness.report("t92_p02_ws_commit", "FAIL", t.message ?: t.javaClass.simpleName)
            throw t
        }
    }
}
