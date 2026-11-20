# docs/perf · 性能基线目录

T92（issue `docs/issues/closed/T92.md`）基线与测量产物的唯一存放处。

## 文件清单与状态

| 文件 | 内容 | 状态 |
| --- | --- | --- |
| `baseline-pinyin-jmh-host-<os>-<arch>.json` | `:pinyin` 引擎 JMH 基线（口径 9，宿主侧 JVM 产物，环境指纹随文件） | 本轮实跑产出 |
| `baseline-mitv4.json` | 真机（MiTV4）四项指标基线：中位数 / p95 / 环境指纹（uptime / meminfo / 前台态） | 已入库（2026-11-20 真机批次实跑产出；重定须户主拍板，口径 5）；禁止用合成或模拟器数据冒充真机基线 |

## 基线 JSON schema（perf-baseline/1）

- `schema`：`sofatype-perf-baseline/1`（JMH 引擎基线为 `sofatype-perf-baseline-jmh/1`）。
- `synthetic`：必须显式；`true` = 合成样例，仅验证判定机制，禁止用于任何结论。
- `env`：环境指纹（真机基线必含 uptime / meminfo / 前台态 + buildFingerprint）。
- `metrics`：键 `key_feedback` / `candidate_refresh` / `ws_commit` / `e2e_phone_to_tv`；
  每项含 `budgetMs` / `n` / `medianMs` / `p95Ms`。

## 判定口径（口径 4，设备端与宿主脚本一致）

- 每指标每轮 N≥20 次采样，取中位数与 p95。
- 判定 = 预算绝对值达标（median 与 p95 均不超预算）且较基线中位数劣化 ≤20%；
  任一不满足即 FAIL。无基线时只判预算绝对值（真机首轮形态）。
- 预算：键帽反馈 ≤100ms、候选刷新 ≤200ms、WS 上屏 TV 段 ≤300ms、
  e2e 手机段+TV 段 ≤3000ms（`docs/PRD_20261104.md` 第 8 章，只读基线）。

## 纪律

- 模拟器数据不作为性能结论（口径 6）；本目录不收模拟器测量产物。
- 基线重定须户主拍板（口径 5）。
- 判定脚本：`tools/perf/perf_report.py`（`--selftest` 用 `tools/perf/fixtures/` synthetic 夹具）。
