# tools/perf · 性能回归采样编排与判定报告（T92）

宿主侧薄 wrapper（口径 10：宿主只做采样编排与报告汇总；预算 PASS/FAIL 断言在 T91 instrument
层设备端完成）。工具口径：python3（标准库）+ adb；WS 通道复用 `tools/integration` 既有封装。

## 组成

| 文件 | 职责 |
| --- | --- |
| `perf_sample.py` | 采样编排：`ws-driver`（WS commit 样本驱动）/ `collect`（logcat 落盘）/ `phone-e2e`（手机段 E2E，真机浏览器优先、模拟器仅 python3 直连逻辑核验） |
| `perf_report.py` | 判定与报告汇总（口径 4）：样本 × 基线 → 指标 × median/p95 × PASS/FAIL；`--selftest` 用 synthetic 夹具自测 |
| `jmh_to_baseline.py` | JMH results.json → 引擎基线 JSON（口径 9，宿主侧产物入 `docs/perf/`） |
| `fixtures/` | synthetic 判定夹具（明确标注 synthetic，不进 `docs/perf/`，不冒充基线） |

## JMH 引擎基准复现（口径 9，宿主侧）

```bash
cd <工作区>
export HOME="$PWD/.emulator-adbhome"
export GRADLE_USER_HOME="$PWD/.gradle-home"
export XDG_CACHE_HOME="$PWD/.nix-cache"
export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"
nix develop -c ./gradlew --no-daemon --rerun-tasks :pinyin:jmh
python3 tools/perf/jmh_to_baseline.py \
  --results pinyin/build/reports/jmh/results.json \
  --out docs/perf/baseline-pinyin-jmh-host-$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m).json
```

`--rerun-tasks` 必带：不带时已跑过的环境任务 UP-TO-DATE 假成功（仓库本地开发指引 2.3 同款坑）。


## 埋点与断言分层

- **埋点**：`PerfTrace`（debug 变体源集 `app/src/debug/.../PerfTrace.kt`；release 变体为空实现桩
  `app/src/release/.../PerfTrace.kt`，release 产物无埋点日志路径）。三段跨度：
  - `key_feedback`：onKeyDown → 该键引发的视图绘制完成（键盘根视图 post 终点标记，排在含本次
    视觉变更的 traversal 之后）；
  - `candidate_refresh`：键码进入 → 候选列表发布（沿用 traceCandidateLoadCompleted 调用点）；
  - `ws_commit`：WS 收帧（`RemoteInputServerManager.onRemoteCommit`，WS 读线程同步回调链内）
    → 上屏成功写入 IC（`commitRemoteText` 成功返回）。
  - 行格式（logcat tag=`PerfTrace`，只含耗时与计数，无任何输入内容）：
    `key_feedback seq=N durationMs=X.XXX`、`candidate_refresh seq=N durationMs=X.XXX total=T pages=P`、
    `ws_commit seq=N durationMs=X.XXX committed=true|false`。
- **设备端断言**：`app/src/androidTest/java/com/sofatype/ime/test/PerfBudgetTest.kt`
  （`p00` synthetic 自测 / `p01` 键段采样+断言 / `p02` WS 段采样+断言；逐行格式白名单校验 =
  零泄漏防线；基线文件经 `-e perfBaselinePath <设备路径>` 可选注入，无则只判预算绝对值）。
- **宿主报告**：`perf_report.py` 聚合 PerfTrace 样本 + 基线 + 设备端结果行出报告。

## 模拟器一轮（逻辑回归；产物不作性能结论，口径 6）

前置：仓库已构建 `app-debug.apk` / `app-debug-androidTest.apk`；无在线模拟器实例
（活实例不可跨回合依赖）。冷启与收尾纪律见仓库本地开发指引 2.2。

```bash
cd <工作区>
export HOME="$PWD/.emulator-adbhome"   # adb 用模拟器钥匙目录
adb devices                             # 就绪判据：sofatype-tv 显示 device

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell ime set com.sofatype.ime/.SofatypeImeService
adb shell am start -n com.sofatype.ime/.TestTypingActivity   # 解 stopped 态
adb shell settings put secure default_input_method com.sofatype.ime/.SofatypeImeService

# 1) instrument 断言（后台）与 2) WS 采样驱动（前台）并发：
adb shell am instrument -w -e class com.sofatype.ime.test.PerfBudgetTest \
  -e perfMinSamples 20 -e perfWsTimeoutMs 240000 \
  -e perfBaselinePath /data/local/tmp/perf/synthetic-baseline.json \
  com.sofatype.ime.test/androidx.test.runner.AndroidJUnitRunner &
adb push app/src/androidTest/assets/perf/synthetic-baseline.json /data/local/tmp/perf/synthetic-baseline.json
python3 tools/perf/perf_sample.py ws-driver --device emulator --commits 20 --rhythm 2.2

# 3) 取证与判定报告（synthetic 基线仅验证判定机制）：
python3 tools/perf/perf_sample.py collect --device emulator --out reports/perf/perf-samples.logcat
python3 tools/perf/perf_report.py --samples reports/perf/perf-samples.logcat \
  --baseline app/src/androidTest/assets/perf/synthetic-baseline.json \
  --device emulator --out reports/perf/perf-report.md

# 4) 收尾：核对 default_input_method 恢复、删除 /data/local/tmp/perf/、adb emu kill + 端口复核
```

可选 `phone-e2e`（模拟器仅逻辑核验，输出 logicOnly=true）：
`python3 tools/perf/perf_sample.py phone-e2e --device emulator --out reports/perf/phone-e2e.json`
（须与 instrument p02 并发，即远程服务运行期。）

## instrument 参数

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `perfMinSamples` | 20 | 每指标最少样本数（口径 4：N≥20；真机批次保持 20） |
| `perfWsTimeoutMs` | 240000 | p02 等待 ws_commit 样本的上限 |
| `perfBaselinePath` | 空 | 设备侧基线 JSON 路径（真机批次 push `docs/perf/baseline-mitv4.json`）；缺省只判预算绝对值 |

## 真机批次（本轮未执行）

- 前置：户主批准（口径 5/6：真机执行与基线采集一律先询问户主）；`perf_sample.py` 对
  `--device mitv4` 硬性要求 `--i-have-owner-approval`。
- 步骤概要：装机（debug 包）→ 核默认输入法 → `am instrument`（p00/p01/p02，≥2s 注入节奏由
  设备端 sampling 内置）+ `perf_sample.py ws-driver --device mitv4` 并发 → 收尾恢复设置态 →
  `perf_report.py --samples ... --baseline docs/perf/baseline-mitv4.json --device mitv4`。
- 判定：median 与 p95 均不超预算 且 较基线中位数劣化 ≤20%；任一不满足即 FAIL。
