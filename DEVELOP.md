# SofaType 开发指南

本指南讲四件事：本地构建、GitHub CI 与发布、本地开发、测试。面向首次上手或回归本项目的开发者（含 AI agent）。
协作规范（issue 制 / 提交信息 / 注释白名单 / 红线）见 [AGENTS.md](AGENTS.md)，本文不重复。

## 环境准备

- 前置：[Nix](https://nix.dev)（启用 flakes）。宿主支持 macOS（Apple Silicon）与 Linux（arm64 / x86_64）。
- 一切工具经 `nix develop` 进入开发 shell：JDK / Android SDK（含 adb、emulator、avdmanager）/ Gradle（日常构建用 `./gradlew` wrapper）全就绪。组件清单以 [flake.nix](flake.nix) 为准，勿混用系统 PATH 的 JDK / gradle / adb。
- 首次进入会自动下载 Android SDK 组件，需网络。此后构建零网络：拼音词库源文件随仓库入库 + sha256 门禁校验（见 `pinyin/build.gradle.kts`，决策 29）。词库清单 / 体量 / 更新方式与构建选择见 [docs/DICTS.md](docs/DICTS.md)。
- 首次搭模拟器执行一次 `nix run .#tv-avd`（创建 tv_1080p 形态 AVD）。
- adb home 与构建缓行走各自的标准环境变量（`ANDROID_USER_HOME` / `ANDROID_AVD_HOME` / `GRADLE_USER_HOME` / `XDG_CACHE_HOME`）：按需声明即可，工具不发明专用变量、不注入，详见 [tools/integration/README.md](tools/integration/README.md)「环境变量」节。

## 本地构建

| 命令 | 产出 |
| --- | --- |
| `./gradlew assembleDebug` | `app/build/outputs/apk/debug/sofatype-debug.apk` |
| `./gradlew assembleDebugAndroidTest` | `app/build/outputs/apk/androidTest/debug/sofatype-debug-androidTest.apk`（集成测试前置两包之一） |
| `./gradlew testDebugUnitTest` | app 单测（含 Paparazzi 键盘视觉回归，见「测试」节） |
| `./gradlew :pinyin:test` | 拼音引擎单测（纯 JVM 模块，不在 `testDebugUnitTest` 内，需单独跑） |
| `./gradlew --rerun-tasks :pinyin:jmh` | JMH 结果 `pinyin/build/reports/jmh/results.json` |
| `./gradlew assembleRelease` | `app/build/outputs/apk/release/sofatype-release-unsigned.apk`（无 signingConfig，未签名口径） |

一条全量：`./gradlew assembleDebug testDebugUnitTest assembleRelease`。

复测纪律：

- 复测统一加 `--rerun-tasks`：不带时已跑过的任务 UP-TO-DATE 跳过，呈假绿。
- 通过判据：任务全部 executed + `app/build/test-results/` 下 JUnit XML 时间戳为本轮产物。
- release 为未签名产物；真机升级安装涉及签名兼容处理，按设备档案与设备所有者现场指引执行（见 [docs/devices/](docs/devices/)）。

**词库与热词（构建期内置）**：

- APK 构建自动携带词库：`:pinyin` 的 `processResources` 先跑 `buildDict`（主词库 sha256 门禁 + 转换）与 `buildHotDict`（热词源 sha256 门禁 + 合并转换），无需单独执行。单独重建：`./gradlew --no-daemon :pinyin:buildDict :pinyin:buildHotDict`，产物在 `pinyin/assets/dict/`（构建产物不入库）。
- 逐词库开关：`./gradlew assembleDebug -PsofatypeHotDicts=thuocl-open,nlc-netlang`（逗号分隔热词源 id；默认全内置，空值全关回退纯主词库，未知 id 构建失败）。
- 更新热词库（年更）：`nix develop -c bash tools/wordpack/refresh_hotwords.sh`——自动重取 THUOCL 上游域词表并确定性派生 `thuocl-open.txt`（DF 前 12000 + 注音），重算 `sources.sha256` 门禁；语监中心 / 咬文嚼字年度榜发布后人工转录并入 `*_years.txt`，再重跑该脚本过门禁。
- 逐词库来源、词数、大小与合并总量详见 [docs/DICTS.md](docs/DICTS.md)。

## GitHub CI 与发布

workflow 在 `.github/workflows/`（T100）。CI 构建环境与本地 flake 不同源：CI 用 ubuntu runner 预装 Android SDK + Zulu 17（与 flake 的 JDK 发行版对齐），wrapper 与依赖版本由本仓库钉死；本地开发仍一律走 `nix develop`，两条通道不要混。

| workflow | 触发 | 干什么 |
| --- | --- | --- |
| `ci.yml` | push / PR 到 `master`，或手动 dispatch | `assembleDebug` + `testDebugUnitTest` + `:pinyin:test` + `assembleRelease`（不含视觉回归与模拟器测试） |
| `release.yml` | push `v*` tag | 门禁（tag = versionName）→ 抽取 CHANGELOG 版本节 → `assembleRelease` → 直发 GitHub Release，附件 `sofatype-<版本>-unsigned.apk` |

发版流程（版本号随发布批次人工递增，决策 19）：

1. 递增 `app/build.gradle.kts` 的 `versionName` / `versionCode`；
2. 把 `CHANGELOG.md`「未发布」节改名为版本号 + 日期（Release workflow 从这里抽取发布正文，找不到该节会失败）；
3. 提交后打 tag 推送：`git tag v<版本> && git push origin v<版本>`；Release 直发，无草稿环节。

workflow 验证依赖真实推送后的 GitHub Actions；CI 上的 Gradle 依赖缓存与 wrapper 校验由 `gradle/actions/setup-gradle`（basic provider）承担。

## 本地开发

- **TV 模拟器**：

  ```bash
  nix run .#tv-avd        # 创建 AVD（首次执行一次）
  nix run .#tv-emulator   # 启动模拟器
  ```

  遥控器对应：方向键 = 十字键，回车 = OK，退格 = 返回。
  就绪判据：启动后约 60 秒内 `adb devices` 显示 `device`；`unauthorized` = adb 钥匙不匹配，停手核对，勿继续装包。
  收尾：`adb emu kill`，确认 5554/5555 无监听。
  冷启、装机、输入法核对手动操作可直接复用集成测试编排器的单步 stage（自带核验）：`python3 tools/integration/integration_runner.py --stage boot` 等，详见 [tools/integration/README.md](tools/integration/README.md)。

- **H5 页调试**：用本机任一支持 CDP 的无头浏览器（chrome / chromium / brave / firefox 等）起调试端点：

  ```bash
  <浏览器> --headless=new --remote-debugging-port=9222 --user-data-dir=<临时目录>   # chrome 系；firefox 用 --headless
  ```

  Puppeteer 用 `puppeteer.connect({ browserWSEndpoint: 'ws://127.0.0.1:9222' })`，Playwright 用 `chromium.connectOverCDP`。调试完退出实例、删临时目录，不留监听端口。

- **检查更新 mock 源**：手动走查更新链路用 `tools/mock/github-releases/serve_mock.py`（版本值参数注入，JSON 零硬编码），用法见 [tools/mock/github-releases/README.md](tools/mock/github-releases/README.md)。

- **真机**：装机、地址、无线链路纪律、已踩坑一律先读 [docs/devices/](docs/devices/) 设备档案（一机一档）；集成 / 性能工具的真机段内置硬性确认门控（须设备所有者逐次确认），见两份 tools README。

- **代码接入点**：模块职责与入口速查见 [docs/HANDOFF.md](docs/HANDOFF.md)「代码接入速查」节（IME 服务 / 设置页 / 跨屏输入 / 测试基建）。

## 测试

测试分四层。下文命令均可照抄执行；真机场景必须先经设备所有者确认后执行。

**推荐回归顺序**（逐步全绿再进下一步）：

1. 引擎基准 + 判定脚本自测（宿主侧，分钟级）
2. 单元测试（含 Paparazzi 视觉回归）
3. 集成测试模拟器一轮（`--stage full`）
4. 性能埋点回归模拟器一轮（仅逻辑回归）
5. 真机批次（性能结论唯一来源；先经设备所有者确认）

| 层 | 测什么 | 设备 | 入口 |
| --- | --- | --- | --- |
| 单元测试 | app JVM 单测、拼音引擎单测、Paparazzi 键盘视觉回归 | 不需要 | `./gradlew` |
| 集成测试 | 键盘与跨屏输入全链路场景（含黄金图逐像素比对） | 模拟器或真机 | `tools/integration/integration_runner.py` |
| 引擎基准 | 拼音引擎算法耗时（JMH，宿主侧） | 不需要 | `./gradlew :pinyin:jmh` |
| 埋点性能回归 | 键帽反馈 / 候选刷新 / 远程上屏三段耗时达标与劣化 | 模拟器（仅逻辑回归）或真机（性能结论） | `tools/perf/` |

复测统一加 `--rerun-tasks`：不带时已跑过的任务 UP-TO-DATE 跳过，呈假绿。

### 单元测试（不需要设备）

**测什么**：app 模块 JVM 单测；`:pinyin` 拼音引擎单测（纯 JVM 模块，不在 `testDebugUnitTest` 内，需单独跑）；Paparazzi 键盘视觉回归 `KeyboardVisualRegressionTest`（纯 JVM 渲染，随单测一起跑）。

```bash
./gradlew --rerun-tasks testDebugUnitTest :pinyin:test
```

**预期产物**：

| 路径 | 内容 |
| --- | --- |
| `app/build/test-results/testDebugUnitTest/` | app 单测 JUnit XML（核对时间戳为本轮） |
| `pinyin/build/test-results/test/` | 拼音引擎单测 JUnit XML |
| `app/build/reports/tests/testDebugUnitTest/` | app 单测 HTML 报告 |
| `app/build/reports/paparazzi/debug/` | 键盘渲染快照产物 |

### 集成测试（模拟器或真机）

**测什么**：`InstrumentedScenarioSuite` 共 11 个场景——键盘显示 / 收起（黄金图逐像素）、组合与候选上屏、中英与大小写切换、符号键盘全半角、删除节奏、设置页 BACK 二次退出、隐藏态键码透传宿主、手机扫码远程配对全链路、检查更新（本地 mock 源）。黄金图按设备分目录：模拟器 `emulator-api34`、真机 `mitv4-api23`；首轮自动记录实机基线，次轮起真实比对。

#### 模拟器（一键全跑）

```bash
./gradlew assembleDebug assembleDebugAndroidTest                 # 前置：先产出 debug 与 androidTest 两包
python3 tools/integration/integration_runner.py --stage full --device emulator
```

自动编排：冷启模拟器 → 装两包 → 核对输入法 → 起本地 mock 服务 → 双轮 instrument（首轮记录黄金图后重建重装 androidTest 包）→ 出报告 → 关模拟器并复核端口。前提：本机无在线模拟器实例。全程约 25~40 分钟。

分步执行（排障或单跑一段，`--stage` 取值）：

| stage | 用途 |
| --- | --- |
| `boot` | 冷启模拟器并核验 |
| `install` | 装 debug + androidTest 两包 |
| `ime-check` | 切换并核对默认输入法 |
| `instrument` | 跑场景套件；`--scenarios s01_keyboard_show,s04_composition_candidates` 定向子集 |
| `phone` | 手机端模拟驱动（须与 instrument 并发跑） |
| `teardown` | 关模拟器并复核端口残留（报告在 full / rounds 编排内生成，无独立 stage） |

#### 真机（先确认，后执行）

```bash
# 场景映射与前置清单，只打印不触设备，可随时执行：
python3 tools/integration/integration_runner.py --stage mitv4-plan

# 前置核对 / 装机：须先经设备所有者逐次确认；脚本另有参数硬性门控（详见 tools/integration/README.md）：
python3 tools/integration/integration_runner.py --stage mitv4-preflight --device mitv4 --host <电视IP>:5555
python3 tools/integration/integration_runner.py --stage mitv4-install --device mitv4 --host <电视IP>:5555 --confirm-tap x,y
```

`--confirm-tap` 是安装许可弹窗确认钮坐标：先 `adb shell screencap` 目测再传；双钮两段式代点，上限 4 次不解除即停手。电视 IP 与无线链路注意事项以设备档案 [docs/devices/](docs/devices/) 为准（IP 随 DHCP 变动）。

#### 模拟器与真机参数对照

| 参数 | 模拟器 | 真机 |
| --- | --- | --- |
| `--device` | `emulator`（默认值） | `mitv4` |
| `--host` | 不传 | `<电视IP>:5555` 必传 |
| `--confirm-tap` | 不传 | `x,y` 装机必传 |
| 黄金图目录 | `assets/goldens/emulator-api34/` | `assets/goldens/mitv4-api23/` |
| 执行前提 | 本机无在线模拟器实例 | 设备所有者逐次确认 |

#### 预期产物

| 路径 | 内容 |
| --- | --- |
| `reports/integration/integration-report.md` | 场景 × PASS/FAIL 汇总报告（s10 一并记录实际手机模拟通道：chrome/chromium/brave 或 python3 WS 直连回落） |
| `reports/integration/pulled/round{1,2}/` | 每轮 results.tsv 与截图 |
| `reports/integration/logs/` | 模拟器 / mock 服务 / 手机驱动日志 |
| `app/src/androidTest/assets/goldens/<device-api>/` | 首轮记录的黄金图（入库资产，次轮起比对用） |

通过判据：报告全 PASS。首轮黄金图未落位时结果行记 `GOLDEN_RECORDED`（只记基线、不算通过）；一键全跑会在第二轮自动完成真实比对，单跑 `instrument` 时需再跑一轮。

### 性能基准与埋点回归

两条线。**引擎基准**在宿主机跑 JMH，不需要设备，秒级暴露拼音引擎算法劣化。**埋点回归**测 debug 构建内置的 `PerfTrace` 埋点（release 产物无埋点路径）三段跨度：键帽视觉反馈（预算 ≤100ms）、候选列表刷新（≤200ms）、远程收帧→上屏（≤300ms），另有手机端到端上屏（≤3000ms）。达标口径：每指标 ≥20 采样，中位数与 p95 均不超预算，且较基线中位数劣化 ≤20%。模拟器产物只验证判定机制，不作性能结论；性能结论只认真机。

#### 引擎基准（不需要设备）

```bash
./gradlew --rerun-tasks :pinyin:jmh
python3 tools/perf/jmh_to_baseline.py --results pinyin/build/reports/jmh/results.json \
  --out docs/perf/baseline-pinyin-jmh-host-$(uname -s | tr '[:upper:]' '[:lower:]')-$(uname -m).json
```

**预期产物**：`pinyin/build/reports/jmh/results.json`（JMH 原始结果）、`docs/perf/baseline-pinyin-jmh-host-<os>-<arch>.json`（引擎基线，环境指纹随文件记录）。

#### 判定脚本自测（不需要设备）

```bash
python3 tools/perf/perf_report.py --selftest   # synthetic 夹具，不触设备
```

**预期产物**：终端 PASS 输出（exit 0），无落盘文件。

#### 埋点回归 · 模拟器（仅逻辑回归，不产性能结论）

```bash
adb install -r app/build/outputs/apk/debug/sofatype-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/sofatype-debug-androidTest.apk
adb shell ime set com.sofatype.ime/.SofatypeImeService
# 1) 设备端断言（后台）与 2) WS 采样驱动并发：
adb shell am instrument -w -e class com.sofatype.ime.test.PerfBudgetTest \
  -e perfMinSamples 20 -e perfWsTimeoutMs 240000 \
  -e perfBaselinePath /data/local/tmp/perf/synthetic-baseline.json \
  com.sofatype.ime.test/androidx.test.runner.AndroidJUnitRunner &
adb push app/src/androidTest/assets/perf/synthetic-baseline.json /data/local/tmp/perf/synthetic-baseline.json
python3 tools/perf/perf_sample.py ws-driver --device emulator --commits 20 --rhythm 2.2
# 3) 取样与判定（synthetic 基线仅验证判定机制）：
python3 tools/perf/perf_sample.py collect --device emulator --out reports/perf/perf-samples.logcat
python3 tools/perf/perf_report.py --samples reports/perf/perf-samples.logcat \
  --baseline app/src/androidTest/assets/perf/synthetic-baseline.json \
  --device emulator --out reports/perf/perf-report.md
# 4) 收尾：恢复默认输入法、删 /data/local/tmp/perf/、关模拟器并复核端口
```

可选手机端到端：`python3 tools/perf/perf_sample.py phone-e2e --device emulator --out reports/perf/phone-e2e.json`（模拟器输出 `logicOnly=true`，仅逻辑核验；须与 instrument 并发）。完整步骤与收尾细节见 [tools/perf/README.md](tools/perf/README.md)。

#### 埋点回归 · 真机（性能结论唯一来源，先确认后执行）

与模拟器的差异只有三处：`--device` 传 `mitv4`；每条 `perf_sample.py` 命令必须加 `--i-have-owner-approval`（缺参脚本拒绝执行）；基线换真机基线 `docs/perf/baseline-mitv4.json`（push 到设备后经 `-e perfBaselinePath` 注入；首轮无基线时只判预算绝对值）。

```bash
python3 tools/perf/perf_sample.py ws-driver --device mitv4 --i-have-owner-approval
python3 tools/perf/perf_sample.py collect --device mitv4 --i-have-owner-approval --out reports/perf/perf-samples.logcat
python3 tools/perf/perf_report.py --samples reports/perf/perf-samples.logcat \
  --baseline docs/perf/baseline-mitv4.json --device mitv4 --out reports/perf/perf-report.md
```

#### 预期产物

| 路径 | 内容 |
| --- | --- |
| `reports/perf/perf-samples.logcat` | PerfTrace 原始采样（只含耗时与计数，无输入内容） |
| `reports/perf/perf-report.md` | 指标 × median/p95 × PASS/FAIL 判定报告 |
| `reports/perf/phone-e2e.json` | 手机端到端采样（可选场景） |
| `docs/perf/` | 基线入库目录（引擎基线 + 真机基线；基线重定须经项目所有者确认） |

## 参见

| 文档 | 内容 |
| --- | --- |
| [AGENTS.md](AGENTS.md) | 协作规范与红线（动手前必读；变更走 issue 制） |
| [docs/HANDOFF.md](docs/HANDOFF.md) | 关键决策 / 坑清单 / 当前状态 / 代码接入速查 |
| [docs/DICTS.md](docs/DICTS.md) | 词库档案（清单 / 体量 / 更新方式 / 构建选择） |
| [tools/integration/README.md](tools/integration/README.md) | 集成测试工具 man page（选项 / 阶段 / 退出状态 / 限制） |
| [tools/perf/README.md](tools/perf/README.md) | 性能回归工具 man page（子命令 / 判定口径 / 数据格式） |
| [tools/mock/github-releases/README.md](tools/mock/github-releases/README.md) | 检查更新 mock 源用法 |
| [docs/devices/](docs/devices/) | 设备档案（真机装机、地址、踩坑） |
| [docs/perf/README.md](docs/perf/README.md) | 性能基线库 schema 与判定口径 |
| [docs/issues/](docs/issues/) | 变更记录（issue 即需求；建号规则见其 README） |
| [CHANGELOG.md](CHANGELOG.md) | 发版记录 |
