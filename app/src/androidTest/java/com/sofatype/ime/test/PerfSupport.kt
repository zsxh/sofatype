package com.sofatype.ime.test

import java.util.Locale

data class PerfSample(
    val span: String,
    val seq: Int,
    val durationMs: Double,
    val extra: Map<String, String>,
)

data class BaselineMetric(
    val budgetMs: Double,
    val n: Int,
    val medianMs: Double,
    val p95Ms: Double,
)

data class ParsedBaseline(
    val synthetic: Boolean,
    val metrics: Map<String, BaselineMetric>,
)

data class MetricStats(
    val span: String,
    val n: Int,
    val medianMs: Double,
    val p95Ms: Double,
)

sealed class PerfVerdict {
    data class Pass(val detail: String) : PerfVerdict()
    data class Fail(val reasons: List<String>) : PerfVerdict()
}

object PerfSupport {

    const val SPAN_KEY_FEEDBACK = "key_feedback"
    const val SPAN_CANDIDATE_REFRESH = "candidate_refresh"
    const val SPAN_WS_COMMIT = "ws_commit"

    val SPANS = listOf(SPAN_KEY_FEEDBACK, SPAN_CANDIDATE_REFRESH, SPAN_WS_COMMIT)

    const val BUDGET_KEY_FEEDBACK_MS = 100.0
    const val BUDGET_CANDIDATE_REFRESH_MS = 200.0
    const val BUDGET_WS_COMMIT_MS = 300.0
    const val BUDGET_E2E_PHONE_TO_TV_MS = 3000.0
    const val DEGRADATION_LIMIT = 1.2

    val BUDGETS_MS: Map<String, Double> = mapOf(
        SPAN_KEY_FEEDBACK to BUDGET_KEY_FEEDBACK_MS,
        SPAN_CANDIDATE_REFRESH to BUDGET_CANDIDATE_REFRESH_MS,
        SPAN_WS_COMMIT to BUDGET_WS_COMMIT_MS,
    )

    private val ALLOWED_EXTRA_KEYS = setOf("total", "pages", "committed", "dropped")

    private const val TAG_PREFIX = "PerfTrace:"

    fun payloadOf(line: String): String? {
        val idx = line.indexOf(TAG_PREFIX)
        return if (idx < 0) null else line.substring(idx + TAG_PREFIX.length).trim()
    }

    fun parseLine(line: String): PerfSample? {
        val body = payloadOf(line) ?: return null
        if (body.isEmpty()) return null
        if (!body.all { it.code in 32..126 }) return null
        val tokens = body.split(Regex("\\s+"))
        val span = tokens.firstOrNull() ?: return null
        if (span !in SPANS) return null
        var seq = -1
        var durationMs = Double.NaN
        val extra = LinkedHashMap<String, String>()
        for (token in tokens.drop(1)) {
            val eq = token.indexOf('=')
            if (eq <= 0) return null
            val key = token.substring(0, eq)
            val value = token.substring(eq + 1)
            when (key) {
                "seq" -> seq = value.toIntOrNull() ?: return null
                "durationMs" -> durationMs = value.toDoubleOrNull() ?: return null
                else -> {
                    if (key !in ALLOWED_EXTRA_KEYS) return null
                    extra[key] = value
                }
            }
        }
        if (seq < 0) return null
        if (durationMs.isNaN() && extra["dropped"] != "true") return null
        return PerfSample(span, seq, durationMs, extra)
    }

    fun isDropped(sample: PerfSample): Boolean = sample.extra["dropped"] == "true"

    fun hasDuration(sample: PerfSample): Boolean = !sample.durationMs.isNaN()

    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return Double.NaN
        val sorted = values.sorted()
        if (sorted.size == 1) return sorted[0]
        val rank = (p / 100.0) * (sorted.size - 1)
        val lo = kotlin.math.floor(rank).toInt()
        val hi = kotlin.math.ceil(rank).toInt()
        return if (lo == hi) sorted[lo] else sorted[lo] + (sorted[hi] - sorted[lo]) * (rank - lo)
    }

    fun stats(span: String, durationsMs: List<Double>): MetricStats =
        MetricStats(span, durationsMs.size, median(durationsMs), percentile(durationsMs, 95.0))

    fun judge(
        span: String,
        durationsMs: List<Double>,
        minSamples: Int,
        baseline: BaselineMetric?,
    ): PerfVerdict {
        val budget = BUDGETS_MS[span] ?: return PerfVerdict.Fail(listOf("未知指标：$span"))
        val reasons = mutableListOf<String>()
        if (durationsMs.size < minSamples) {
            reasons.add("采样数不足：${durationsMs.size} < $minSamples")
        }
        val st = stats(span, durationsMs)
        if (st.medianMs > budget) {
            reasons.add(
                String.format(Locale.US, "中位数超预算：median=%.1fms > budget=%.0fms", st.medianMs, budget),
            )
        }
        if (st.p95Ms > budget) {
            reasons.add(
                String.format(Locale.US, "p95 超预算：p95=%.1fms > budget=%.0fms", st.p95Ms, budget),
            )
        }
        if (baseline != null && st.medianMs > baseline.medianMs * DEGRADATION_LIMIT) {
            reasons.add(
                String.format(
                    Locale.US,
                    "较基线劣化超限：median=%.1fms > baseline=%.1fms x %.1f",
                    st.medianMs,
                    baseline.medianMs,
                    DEGRADATION_LIMIT,
                ),
            )
        }
        val detail = String.format(
            Locale.US,
            "n=%d median=%.1fms p95=%.1fms budget=%.0fms baseline=%s",
            st.n,
            st.medianMs,
            st.p95Ms,
            budget,
            baseline?.let { String.format(Locale.US, "%.1fms", it.medianMs) } ?: "无",
        )
        return if (reasons.isEmpty()) PerfVerdict.Pass(detail) else PerfVerdict.Fail(reasons)
    }

    fun parseBaseline(json: String): ParsedBaseline {
        val root = org.json.JSONObject(json)
        val metricsJson = root.getJSONObject("metrics")
        val metrics = LinkedHashMap<String, BaselineMetric>()
        for (span in SPANS) {
            if (!metricsJson.has(span)) continue
            val m = metricsJson.getJSONObject(span)
            metrics[span] = BaselineMetric(
                budgetMs = m.optDouble("budgetMs", BUDGETS_MS[span] ?: Double.NaN),
                n = m.optInt("n", -1),
                medianMs = m.getDouble("medianMs"),
                p95Ms = m.optDouble("p95Ms", Double.NaN),
            )
        }
        return ParsedBaseline(root.optBoolean("synthetic", false), metrics)
    }
}
