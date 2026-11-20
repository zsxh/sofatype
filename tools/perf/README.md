# tools/perf · 性能回归工具

本目录三个宿主侧工具：`perf_sample.py`（采样编排）、`perf_report.py`（判定报告）、`jmh_to_baseline.py`（JMH 基线转换）。
分层口径：宿主只做采样编排与报告汇总；预算 PASS/FAIL 断言在设备端 instrument（`PerfBudgetTest`）内完成。
工具为 python3 标准库 + adb；WS 通道复用 `tools/integration/phone_driver.py` 封装。adb home 走通用机制
（`ANDROID_USER_HOME` / 调用方 `HOME`，工具不注入、不设专用变量；详见 [tools/integration/README.md](../integration/README.md)「环境变量」节）。

---

## PERF_SAMPLE(1) · perf_sample.py

**名称** — PerfTrace 埋点采样编排。

**概要**

```bash
python3 tools/perf/perf_sample.py ws-driver [--device {emulator|mitv4}] [--commits N]
      [--rhythm SEC] [--text STR] [--timeout SEC] [--retries N] [--i-have-owner-approval]
python3 tools/perf/perf_sample.py collect --out PATH [--device {emulator|mitv4}] [--i-have-owner-approval]
python3 tools/perf/perf_sample.py phone-e2e --out PATH [--device {emulator|mitv4}]
      [--text STR] [--timeout SEC] [--cycles N] [--i-have-owner-approval]
```

**描述**

- `ws-driver`：与设备端 `PerfBudgetTest#p02` 并发运行。轮询 `settings system perf_remote_url`（p02 写入）取远程页 URL，经 WS 逐条发送 commit，驱动 `ws_commit` 采样。
- `collect`：读取设备 logcat 的 PerfTrace 行，落盘为 `--out` 文件，供 perf_report 判定。
- `phone-e2e`：手机段到上屏的端到端计时。真机浏览器优先；模拟器仅逻辑核验，输出 `logicOnly=true`。`--cycles` 大于 1 走多轮。
- **互斥约束**：远程服务只有单连接槽位且配对 token 滚动签发（新连接顶替旧连接），`phone-e2e` 与 `ws-driver` 不可同窗并发——phone-e2e 先行会顶掉 ws-driver 配对、ws-driver 先行结束后服务随 p02 关闭会使 phone-e2e 轮询扑空；如需两者都有产物，各起一轮 instrument 分别独占运行。

**选项**

| 子命令 | 选项 | 默认 | 说明 |
| --- | --- | --- | --- |
| 全部 | `--device` | `emulator` | `emulator` 或 `mitv4` |
| ws-driver | `--commits` | `20` | 发送的 commit 条数 |
| ws-driver | `--rhythm` | `2.2` | 每条间隔秒数 |
| ws-driver | `--text` | 性能采样 | 注入文本 |
| ws-driver | `--timeout` | `240` | 等待远程页 URL 的秒数上限 |
| ws-driver | `--retries` | `5` | 失败重试次数 |
| collect | `--out` | 必填 | logcat 落盘路径 |
| phone-e2e | `--out` | 必填 | 计时记录 JSON 路径 |
| phone-e2e | `--cycles` | `1` | 执行轮数 |
| phone-e2e | `--text` / `--timeout` | 同上 | 注入文本 / 等待上限 |

**真机门控**：`--device mitv4` 时，全部子命令必须加 `--i-have-owner-approval`；缺参脚本以 SystemExit 拒绝执行。真机操作先经设备所有者逐次确认。

**退出状态**：`0` 完成；非 0 失败（未批准、限时内未读到 `perf_remote_url` marker 等）。

---

## PERF_REPORT(1) · perf_report.py

**名称** — 采样判定与报告汇总。

**概要**

```bash
python3 tools/perf/perf_report.py --samples FILE [--samples FILE ...] [--baseline JSON]
      [--e2e-phone JSON] [--device NAME] [--min-samples N] [--out FILE]
python3 tools/perf/perf_report.py --selftest
```

**选项**

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| `--samples` | 必填 | logcat 落盘文件；可多次传入 |
| `--baseline` | 无 | 基线 JSON（`docs/perf/` 真机基线或 synthetic 夹具）；缺省只判预算绝对值 |
| `--e2e-phone` | 无 | phone-e2e 记录 JSON；并入 `e2e_phone_to_tv` 指标 |
| `--device` | `emulator` | 设备名，写入报告 |
| `--min-samples` | `20` | 每指标最少样本数 |
| `--out` | 打印 stdout | 报告输出路径 |
| `--selftest` | — | 用 `fixtures/` synthetic 夹具自测六条判定路径，不触设备 |

**判定口径**

- 每指标取 median 与 p95；样本数低于 `--min-samples` 即 FAIL。
- 预算：`key_feedback` ≤100ms；`candidate_refresh` ≤200ms；`ws_commit` ≤300ms；`e2e_phone_to_tv` ≤3000ms（基线 PRD 第 8 章）。
- 有基线：median 与 p95 均不超预算，且较基线中位数劣化 ≤20%；任一不满足即 FAIL。
- 无基线：只判预算绝对值（真机首轮形态）。
- 模拟器产物仅验证判定机制，不作性能结论；性能结论只认真机。

**数据格式**（logcat tag=`PerfTrace`；只含耗时与计数，无输入内容）

```text
key_feedback seq=N durationMs=X.XXX
candidate_refresh seq=N durationMs=X.XXX total=T pages=P
ws_commit seq=N durationMs=X.XXX committed=true|false
```

**退出状态**

- `0`：整体 PASS。
- `1`：整体 FAIL（含样本不足）。
- `2`：用法错误。

---

## JMH_TO_BASELINE(1) · jmh_to_baseline.py

**名称** — `:pinyin` JMH 结果转引擎基线 JSON（宿主侧产物）。

**概要**

```bash
python3 tools/perf/jmh_to_baseline.py --results pinyin/build/reports/jmh/results.json \
  --out docs/perf/baseline-pinyin-jmh-host-$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m).json
```

**选项**：`--results` 必填（JMH results.json）；`--out` 必填（基线路径，文件名体现宿主 OS / 架构）。

**输出**：schema `sofatype-perf-baseline-jmh/1`；环境指纹随文件记录。

---

## 输出文件

| 路径 | 内容 |
| --- | --- |
| `reports/perf/perf-samples.logcat` | PerfTrace 原始采样 |
| `reports/perf/perf-report.md` | 指标 × median/p95 × PASS/FAIL 判定报告 |
| `reports/perf/phone-e2e.json` | 手机端到端计时记录 |
| `docs/perf/` | 基线入库目录（引擎基线 + 真机基线 `baseline-mitv4.json`）；基线重定须项目所有者确认 |
| `fixtures/` | synthetic 判定夹具（明确标注 synthetic；不进 `docs/perf/`，不冒充基线） |

**示例**

模拟器一轮完整命令链见 [DEVELOP.md](../../DEVELOP.md)「测试」节。核心形态：

```bash
# （后台）设备端断言与（前台）WS 采样驱动并发，再取证判定：
adb shell am instrument -w -e class com.sofatype.ime.test.PerfBudgetTest ... &
python3 tools/perf/perf_sample.py ws-driver --device emulator --commits 20 --rhythm 2.2
python3 tools/perf/perf_sample.py collect --device emulator --out reports/perf/perf-samples.logcat
python3 tools/perf/perf_report.py --samples reports/perf/perf-samples.logcat \
  --baseline app/src/androidTest/assets/perf/synthetic-baseline.json \
  --device emulator --out reports/perf/perf-report.md
```

真机差异三处：`--device mitv4`；每条 perf_sample 命令加 `--i-have-owner-approval`；基线换 `docs/perf/baseline-mitv4.json`（push 到设备后经 `-e perfBaselinePath` 注入）。

---

## 参见

- [DEVELOP.md](../../DEVELOP.md)「测试」节：模拟器 / 真机完整操作链
- `app/src/androidTest/java/com/sofatype/ime/test/PerfBudgetTest.kt`：设备端断言与 PerfTrace 注入
- [docs/perf/README.md](../../docs/perf/README.md)：基线库 schema 与判定口径
- [docs/issues/closed/T92.md](../../docs/issues/closed/T92.md)：工具来源与拍板记录
