# tools/t92/fixtures · synthetic 判定夹具

本目录全部文件为 **synthetic（合成样例）**：数值为构造值，非任何设备测量产物。

用途：`python3 tools/t92/t92_report.py --selftest` 验证判定脚本六条路径（全过 / 超预算 /
劣化超限 / 样本不足 / 零泄漏防线 / 报告总结论）。不放进 `docs/perf/`，不冒充基线
（真机基线为 `docs/perf/baseline-mitv4.json`，待真机批次，本轮不存在）。

| 文件 | 内容 |
| --- | --- |
| `synthetic-baseline.json` | synthetic 基线 JSON（schema 同真机基线，`synthetic: true`） |
| `synthetic-samples-pass.logcat` | 60 行 PerfTrace 合成样本（三指标各 20，全部应 PASS） |
| `synthetic-samples-fail.logcat` | key_feedback 合成超预算样本 + 2 行带内容字段的零泄漏防线样本 + 1 行设备端 FAIL 场景行 |
