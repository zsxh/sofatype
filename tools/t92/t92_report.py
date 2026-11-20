#!/usr/bin/env python3
"""T92 · 判定与报告汇总脚本（issue T92 口径 4/10：预算 PASS/FAIL 断言已在 T91 instrument 层设备端完成，
本脚本为宿主侧报告汇总——对 PerfTrace 样本与基线 JSON 输出对比报告，口径与设备端一致）。

判定口径（与设备端 T92PerfSupport 一致）：
  每指标每轮 N>=minSamples（默认 20）；取中位数与 p95；
  判定 = 预算绝对值达标（median 与 p95 均不超预算） 且 较基线中位数劣化 <=20%（baseline median x 1.2）；
  任一不满足即 FAIL；样本数不足即 FAIL。无基线时只判预算绝对值（真机首轮即此形态）。

用法：
  python3 tools/t92/t92_report.py --samples <logcat 落盘> --baseline <json> [--e2e-phone <json>] \
      [--device mitv4] [--out <report.md>] [--min-samples 20]
  python3 tools/t92/t92_report.py --selftest

--selftest 用 tools/t92/fixtures/ 下明确标注 synthetic 的合成样例验证判定路径，不触碰任何设备数据。
隐私红线：PerfTrace 行只允许耗时与计数（白名单字段），含内容字段的行一律判 malformed 并 FAIL。
"""
import argparse
import json
import math
import os
import sys
from datetime import datetime

SPANS = ("key_feedback", "candidate_refresh", "ws_commit")
BUDGETS_MS = {"key_feedback": 100.0, "candidate_refresh": 200.0, "ws_commit": 300.0}
E2E_BUDGET_MS = 3000.0
DEGRADE_LIMIT = 1.2
ALLOWED_EXTRA = {"total", "pages", "committed", "dropped"}
TAG_PREFIX = "PerfTrace:"
FIXTURE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")


def parse_line(line):
    idx = line.find(TAG_PREFIX)
    if idx < 0:
        return None
    body = line[idx + len(TAG_PREFIX):].strip()
    if not body:
        return None
    if not all(32 <= ord(c) <= 126 for c in body):
        return None
    tokens = body.split()
    span = tokens[0]
    if span not in SPANS:
        return None
    seq = -1
    duration_ms = None
    extra = {}
    for token in tokens[1:]:
        if "=" not in token:
            return None
        key, value = token.split("=", 1)
        if key == "seq":
            seq = int(value)
        elif key == "durationMs":
            duration_ms = float(value)
        elif key in ALLOWED_EXTRA:
            extra[key] = value
        else:
            return None
    if seq < 0:
        return None
    if duration_ms is None and extra.get("dropped") != "true":
        return None
    return {"span": span, "seq": seq, "durationMs": duration_ms, "extra": extra}


def median(values):
    if not values:
        return float("nan")
    s = sorted(values)
    n = len(s)
    return s[n // 2] if n % 2 == 1 else (s[n // 2 - 1] + s[n // 2]) / 2.0


def percentile(values, p):
    if not values:
        return float("nan")
    s = sorted(values)
    if len(s) == 1:
        return s[0]
    rank = (p / 100.0) * (len(s) - 1)
    lo = math.floor(rank)
    hi = math.ceil(rank)
    if lo == hi:
        return s[lo]
    return s[lo] + (s[hi] - s[lo]) * (rank - lo)


def load_baseline(path):
    with open(path) as fh:
        root = json.load(fh)
    metrics = {}
    for span in SPANS:
        m = root.get("metrics", {}).get(span)
        if m:
            metrics[span] = {"medianMs": float(m["medianMs"]),
                             "budgetMs": float(m.get("budgetMs", BUDGETS_MS[span]))}
    return {"synthetic": bool(root.get("synthetic", False)), "metrics": metrics}


def judge(span, durations, min_samples, baseline_metric):
    budget = BUDGETS_MS[span]
    reasons = []
    if len(durations) < min_samples:
        reasons.append("采样数不足：%d < %d" % (len(durations), min_samples))
    med = median(durations)
    p95 = percentile(durations, 95.0)
    if med > budget:
        reasons.append("中位数超预算：median=%.1fms > budget=%.0fms" % (med, budget))
    if p95 > budget:
        reasons.append("p95 超预算：p95=%.1fms > budget=%.0fms" % (p95, budget))
    if baseline_metric is not None and med > baseline_metric["medianMs"] * DEGRADE_LIMIT:
        reasons.append("较基线劣化超限：median=%.1fms > baseline=%.1fms x %.1f"
                       % (med, baseline_metric["medianMs"], DEGRADE_LIMIT))
    return {
        "span": span,
        "n": len(durations),
        "medianMs": med,
        "p95Ms": p95,
        "budgetMs": budget,
        "baselineMedianMs": None if baseline_metric is None else baseline_metric["medianMs"],
        "pass": not reasons,
        "reasons": reasons,
    }


def aggregate(sample_files):
    samples = {span: [] for span in SPANS}
    dropped = {span: 0 for span in SPANS}
    malformed = []
    scenario_rows = []
    perf_lines = 0
    for path in sample_files:
        with open(path) as fh:
            for line in fh:
                line = line.rstrip("\n")
                if "T91Scenario:" in line:
                    scenario_rows.append(line.split("T91Scenario:", 1)[1].strip())
                    continue
                if TAG_PREFIX not in line:
                    continue
                perf_lines += 1
                parsed = parse_line(line)
                if parsed is None:
                    malformed.append(line)
                elif parsed["span"] in samples:
                    if parsed["durationMs"] is None:
                        dropped[parsed["span"]] += 1
                    else:
                        samples[parsed["span"]].append(parsed["durationMs"])
    return samples, malformed, scenario_rows, perf_lines, dropped


def device_section(scenario_rows):
    rows = [r for r in scenario_rows if r.startswith("t92_")]
    if not rows:
        return "（本轮采集未含设备端 instrument t92_* 结果行）"
    out = []
    for row in rows:
        parts = row.split("\t")
        if len(parts) >= 2:
            out.append("- `%s` %s%s" % (parts[0], parts[1],
                                        ("：" + parts[2]) if len(parts) > 2 else ""))
    return "\n".join(out)


def e2e_section(ws_median, e2e_file, min_samples):
    if not e2e_file or ws_median is None or math.isnan(ws_median):
        return "- 未采样或 TV 段中位数缺失，e2e_phone_to_tv 本轮不判。", None
    with open(e2e_file) as fh:
        rec = json.load(fh)
    phone = rec.get("phoneSegmentMs")
    channel = rec.get("channel", "unknown")
    logic_only = bool(rec.get("logicOnly", False))
    if logic_only or phone is None:
        return ("- 通道：%s（logicOnly=%s，模拟器逻辑核验，不作为性能结论）\n"
                "- TV 段 ws_commit 中位数：%.1fms；e2e 判定待真机批次（浏览器优先路径保留真机）。"
                % (channel, logic_only, ws_median)), None
    total = phone + ws_median
    ok = total <= E2E_BUDGET_MS
    return ("- 通道：%s\n- 手机段：%.1fms；TV 段（ws_commit 中位数）：%.1fms；合计：%.1fms\n"
            "- 预算 <=%.0fms；判定：%s" % (channel, phone, ws_median, total, E2E_BUDGET_MS,
                                          "PASS" if ok else "FAIL")), ok


def build_report(sample_files, baseline_path, e2e_file, min_samples, device_label, out_path):
    samples, malformed, scenario_rows, perf_lines, dropped = aggregate(sample_files)
    baseline = load_baseline(baseline_path) if baseline_path else {"synthetic": False, "metrics": {}}
    results = []
    for span in SPANS:
        results.append(judge(span, samples[span], min_samples, baseline["metrics"].get(span)))
    e2e_text, e2e_verdict = e2e_section(median(samples["ws_commit"]), e2e_file, min_samples)
    overall_pass = all(r["pass"] for r in results) and not malformed and e2e_verdict is not False
    judged_spans = sum(1 for r in results if r["n"] > 0)
    if overall_pass:
        conclusion = ("**PASS**（四项预算全部达标）" if e2e_verdict is True
                      else "**PASS**（已判 %d/4 项预算指标达标；e2e_phone_to_tv 本轮未判，不计入结论，待后续轮次）"
                      % judged_spans)
    else:
        conclusion = "**FAIL**（任一指标 FAIL 即 FAIL，需复测定位）"
    lines = [
        "# T92 · 性能对比判定报告",
        "",
        "- 生成时间：%s" % datetime.now().isoformat(timespec="seconds"),
        "- 设备：%s（口径 6：模拟器数据不作为性能结论）" % device_label,
        "- 样本来源：%s（PerfTrace 行 %d）" % ("、".join(sample_files), perf_lines),
        "- 基线：%s%s" % (baseline_path or "无（首轮仅判预算绝对值）",
                          ("（synthetic 合成基线，仅验证判定机制）" if baseline["synthetic"]
                           else "（真机基线）") if baseline_path else ""),
        "- 判定口径：median 与 p95 均不超预算 且 较基线中位数劣化 <=%.0f%%；样本数 >=%d" % (DEGRADE_LIMIT * 100 - 100, min_samples),
        "",
        "## 指标判定",
        "",
        "| 指标 | n | median(ms) | p95(ms) | 预算(ms) | 基线 median(ms) | 判定 |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for r in results:
        lines.append(
            "| %s | %d | %.1f | %.1f | %.0f | %s | %s |" % (
                r["span"], r["n"], r["medianMs"], r["p95Ms"], r["budgetMs"],
                "无" if r["baselineMedianMs"] is None else "%.1f" % r["baselineMedianMs"],
                "PASS" if r["pass"] else "FAIL（" + "; ".join(r["reasons"]) + "）",
            ))
    lines += ["", "## e2e_phone_to_tv（口径 3：手机段 + TV 段 <=3000ms）", "",
              e2e_text,
              "", "## 零泄漏核对（隐私红线）", ""]
    if malformed:
        lines.append("- FAIL：检出 %d 行非白名单 PerfTrace 行（含内容字段或格式异常）：" % len(malformed))
        for m in malformed[:3]:
            lines.append("  - `%s`" % m[:200])
    else:
        lines.append("- PASS：%d 行 PerfTrace 全部为白名单字段（seq/durationMs/total/pages/committed/dropped），无内容字段。" % perf_lines)
    dropped_note = "、".join("%s=%d" % (s, dropped[s]) for s in SPANS)
    lines.append("- 计数事件（dropped，无耗时、不计入样本）：%s。" % dropped_note)
    lines += ["", "## 设备端 instrument 断言结果", "", device_section(scenario_rows),
              "", "## 总结论", "",
              conclusion,
              ""]
    text = "\n".join(lines)
    if out_path:
        with open(out_path, "w") as fh:
            fh.write(text)
    return text, overall_pass


def selftest():
    ok = True
    pass_file = os.path.join(FIXTURE_DIR, "synthetic-samples-pass.logcat")
    fail_file = os.path.join(FIXTURE_DIR, "synthetic-samples-fail.logcat")
    baseline_file = os.path.join(FIXTURE_DIR, "synthetic-baseline.json")

    samples, malformed, _, perf_lines, dropped = aggregate([pass_file])
    assert perf_lines == 62, "synthetic pass 样本应 62 行（含 2 行 dropped 计数事件），实际 %d" % perf_lines
    assert not malformed, "synthetic pass 样本不应有 malformed：%r" % malformed[:2]
    assert dropped["key_feedback"] == 1 and dropped["candidate_refresh"] == 0, "dropped 计数应归位：%r" % dropped
    base = load_baseline(baseline_file)
    for span in SPANS:
        r = judge(span, samples[span], 20, base["metrics"].get(span))
        assert r["pass"], "synthetic %s 应 PASS：%r" % (span, r["reasons"])
        r_abs = judge(span, samples[span], 20, None)
        assert r_abs["pass"], "synthetic %s 无基线应 PASS（绝对值）：%r" % (span, r_abs["reasons"])

    samples2, malformed2, _, _, _ = aggregate([fail_file])
    assert samples2["key_feedback"], "fail 样本应含 key_feedback"
    r = judge("key_feedback", samples2["key_feedback"], 20, None)
    assert not r["pass"], "synthetic key_feedback 超预算应 FAIL：%r" % r
    assert any("超预算" in x for x in r["reasons"]), "应有超预算原因：%r" % r["reasons"]
    assert malformed2, "fail 样本应检出含内容字段的 malformed 行（零泄漏防线）"

    r = judge("candidate_refresh", [60.0] * 20, 20, {"medianMs": 40.0, "budgetMs": 200.0})
    assert not r["pass"] and any("劣化" in x for x in r["reasons"]), "劣化 >20%% 应 FAIL：%r" % r["reasons"]
    r = judge("candidate_refresh", [40.0] * 20, 20, {"medianMs": 40.0, "budgetMs": 200.0})
    assert r["pass"], "劣化恰好 0%% 应 PASS：%r" % r["reasons"]
    r = judge("ws_commit", [100.0] * 5, 20, None)
    assert not r["pass"] and any("采样数不足" in x for x in r["reasons"]), "样本不足应 FAIL：%r" % r["reasons"]

    text, overall = build_report(
        [pass_file], baseline_file, None, 20, "synthetic-selftest", None)
    assert overall, "synthetic 全 pass 报告总结论应 PASS"
    assert "SYNTHETIC" in text or "synthetic" in text
    assert "四项预算全部达标" not in text, "无 e2e 数据的轮次不得宣称四项预算全部达标"
    assert "本轮未判" in text, "无 e2e 数据的轮次总结论应标注 e2e 未判"
    text2, overall2 = build_report(
        [fail_file], baseline_file, None, 20, "synthetic-selftest", None)
    assert not overall2, "synthetic fail 报告总结论应 FAIL"
    print("selftest PASS：判定脚本六条合成路径（全过/超预算/劣化/样本不足/零泄漏防线/报告结论）全部符合预期")
    return 0


def main():
    parser = argparse.ArgumentParser(description="T92 判定与报告汇总")
    parser.add_argument("--samples", action="append", help="logcat 落盘文件（可多次）")
    parser.add_argument("--baseline", help="基线 JSON（docs/perf/ 下真机基线或 synthetic 夹具）")
    parser.add_argument("--e2e-phone", dest="e2e_phone", help="phone-e2e 记录 JSON（可选）")
    parser.add_argument("--device", default="emulator")
    parser.add_argument("--min-samples", dest="min_samples", type=int, default=20)
    parser.add_argument("--out", help="报告输出路径（缺省打印 stdout）")
    parser.add_argument("--selftest", action="store_true")
    args = parser.parse_args()
    if args.selftest:
        return selftest()
    if not args.samples:
        parser.error("--samples 必填（或用 --selftest）")
    text, overall = build_report(args.samples, args.baseline, args.e2e_phone,
                                 args.min_samples, args.device, args.out)
    print(text)
    return 0 if overall else 1


if __name__ == "__main__":
    sys.exit(main())
