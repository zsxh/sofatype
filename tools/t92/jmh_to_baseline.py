#!/usr/bin/env python3
"""T92 口径 9：JMH 运行结果转引擎基线 JSON（宿主侧产物，入 docs/perf/；文件名体现引擎与宿主环境，
与真机基线 baseline-mitv4.json 明确区分；劣化阈值试点后另定，本基线本轮不设判定阈值）。

用法：
  python3 tools/t92/jmh_to_baseline.py --results pinyin/build/reports/jmh/results.json \
      --out docs/perf/baseline-pinyin-jmh-host-<os>-<arch>.json
"""
import argparse
import hashlib
import json
import platform
import subprocess
from datetime import datetime, timezone

JMH_VERSION = "1.37"
DICT_PATH = "pinyin/assets/dict/pinyin_dict.bin"
SCHEMA = "sofatype-perf-baseline-jmh/1"
PERCENTILE_KEYS = (("p50", "0.5"), ("p95", "0.95"), ("p99", "0.99"))


def sha256_of(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def java_version():
    try:
        out = subprocess.run(["java", "-version"], capture_output=True, text=True, timeout=15)
        lines = [ln.strip() for ln in ((out.stderr or "") + (out.stdout or "")).splitlines()]
        lines = [ln for ln in lines if ln and not ln.startswith(("Picked up", "NOTE: Picked up"))]
        return lines[0] if lines else "unknown"
    except Exception as exc:
        return "unknown (%s)" % exc


def main():
    parser = argparse.ArgumentParser(description="JMH results.json -> 引擎基线 JSON")
    parser.add_argument("--results", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    with open(args.results) as fh:
        records = json.load(fh)

    benchmarks = []
    for rec in records:
        name = rec["benchmark"].rsplit(".", 1)[-1]
        primary = rec.get("primaryMetric", {})
        percentiles = primary.get("scorePercentiles", {}) or {}
        entry = {
            "name": name,
            "mode": rec.get("mode"),
            "unit": primary.get("scoreUnit"),
            "score": primary.get("score"),
            "scoreError": primary.get("scoreError"),
        }
        for out_key, raw_key in PERCENTILE_KEYS:
            if raw_key in percentiles:
                entry[out_key] = percentiles[raw_key]
        params = rec.get("params") or {}
        if params:
            entry["params"] = params
        benchmarks.append(entry)

    baseline = {
        "schema": SCHEMA,
        "engine": "pinyin",
        "scope": "host-jvm",
        "synthetic": False,
        "jmhVersion": JMH_VERSION,
        "sampledAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "env": {
            "os": platform.system(),
            "osRelease": platform.release(),
            "arch": platform.machine(),
            "java": java_version(),
            "dictSha256": sha256_of(DICT_PATH),
        },
        "note": "T92 口径 9 引擎性能地板（宿主侧 JMH，:pinyin 真实词库 65105 条）；"
                "劣化阈值试点后另定（本轮不设判定阈值）；与真机基线 baseline-mitv4.json 互不替代",
        "benchmarks": benchmarks,
    }

    with open(args.out, "w") as fh:
        json.dump(baseline, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("已写出 %s（%d 个基准）" % (args.out, len(benchmarks)))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
