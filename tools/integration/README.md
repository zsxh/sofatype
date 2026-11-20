# tools/integration · 集成测试工具

本目录两个宿主侧工具：`integration_runner.py`（集成测试编排器）与 `phone_driver.py`（手机端模拟驱动）。
工具为薄 wrapper：python3 标准库 + adb；模拟器进程与构建经 `nix develop -c` / `nix run` 子进程调用。

---

## INTEGRATION_RUNNER(1) · integration_runner.py

**名称** — 编排 `InstrumentedScenarioSuite` 集成测试并产出报告。

**概要**

```bash
python3 tools/integration/integration_runner.py [--stage STAGE] [--device {emulator|mitv4}]
      [--host IP:5555] [--confirm-tap X,Y] [--assume-ready] [--scenario ID] [--scenarios ID[,ID...]]
```

**描述**

- 在目标设备上执行 `app/src/androidTest` 的场景套件（11 个场景，见「场景」节），产出场景 × PASS/FAIL 报告。
- `--device emulator`（默认）：宿主全托管。`--stage full` 一条命令完成冷启、装机、双轮执行、报告与收尾。
- `--device mitv4`：仅装机类环节自动化（plan / preflight / install）；场景执行不纳入本工具，见「限制」节。
- adb home 走通用机制（`ANDROID_USER_HOME` / 调用方 `HOME`），工具不注入、不设专用变量；需要钥匙分向的环境用通用变量自行声明。见「环境变量」节。

**环境要求**

- 仓库根执行；adb 走系统 PATH；`boot` 阶段经 `nix run .#tv-emulator` 启动模拟器。
- 前置构建两包（命令见 [DEVELOP.md](../../DEVELOP.md)「本地构建」）：`sofatype-debug.apk` 与 `sofatype-debug-androidTest.apk`。
- `full` / `rounds` 要求本机无在线模拟器实例；活实例不可跨回合依赖，有则先 `teardown`。

**环境变量**

工具不发明专用变量、不注入环境；adb home 与构建缓行走各自的标准机制，按需在调用环境声明：

| 用途 | 标准变量（均可选） | 说明 |
| --- | --- | --- |
| adb home | `ANDROID_USER_HOME`（AVD 精确定位另可用 `ANDROID_AVD_HOME`） | adb 密钥与模拟器令牌所在；未声明时 adb 用 `HOME/.android` |
| Gradle 缓存 | `GRADLE_USER_HOME` | `nix develop` 构建链透传 |
| 缓存目录 | `XDG_CACHE_HOME` | `nix develop` 构建链透传 |

`boot` 阶段按上述通用变量解析 AVD 快照路径做真冷启；均未声明时跳过快照清理并在日志明示。

**选项**

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| `--stage` | `full` | 阶段名，见「阶段」节 |
| `--device` | `emulator` | `emulator` 或 `mitv4` |
| `--host` | 无 | 真机 adb 地址 `ip:5555`；mitv4 装机段必传 |
| `--confirm-tap` | 无 | 安装许可确认钮坐标 `x,y`；先 `screencap` 目测再传 |
| `--assume-ready` | 关 | `rounds` 跳过 boot / install / ime-check（当前实例已就绪时） |
| `--scenario` | 无 | 定向单场景，配合 `--stage instrument` |
| `--scenarios` | 无 | 逗号分隔多场景，配合 `--stage instrument` |

**阶段（--stage）**

| stage | 设备 | 行为 |
| --- | --- | --- |
| `full`（默认） | 模拟器 | boot → install → ime-check → 双轮编排 → 报告 → teardown，全程托管 |
| `boot` | 模拟器 | 删 default_boot 快照（保 userdata）→ 冷启 → 60 秒就绪判据 → `/proc/uptime` 秒级冷启核验 |
| `install` | 两者 | 装两包 + `am start` 解 stopped 态；mitv4 走 push + `pm install` + 许可弹窗代点 |
| `ime-check` | 两者 | `ime set` 本 IME + `default_input_method` 核对 |
| `instrument` | 两者 | `am instrument -w -e class InstrumentedScenarioSuite`；`--scenario` / `--scenarios` 定向子集 |
| `phone` | 两者 | 起 `phone_driver.py`（独立进程，须与 instrument 并发跑） |
| `rounds` | 模拟器 | 双轮编排：round A 记黄金图 → 落位重建 → round B 真实比对 → 报告 → teardown |
| `teardown` | 两者 | `adb emu kill`；复核 5554/5555 无 qemu、8000/9333 清场、`adb devices` 空；真机不动设备连接 |
| `mitv4-plan` | — | 只打印真机场景映射与前置清单，不触设备 |
| `mitv4-preflight` | — | 恒拒绝执行：逻辑已内置、待启用 |
| `mitv4-install` | 真机 | push + `pm install` + 许可弹窗 tap 代点 + 保活循环；缺 `--host` / `--confirm-tap` 直接拒绝 |

内部环节（无独立 stage 入口，仅 `full` / `rounds` 编排内部调用）：`pre`（起 serve_mock :8000）、`goldens`（首轮截图落位 androidTest assets 并重建 androidTest 包）、`report`（汇总 results.tsv 出报告）。

**场景**（`InstrumentedScenarioSuite`，11 项）

| ID | 内容 |
| --- | --- |
| `s01_keyboard_show` | 键盘显示（黄金图 `s01_keyboard_show`） |
| `s02_hide_keycode_back` | 键码通道（BACK）收起 |
| `s03_tap_hide_whitelisted` | tap 通道收起（模拟器白名单；真机预期真 PASS） |
| `s04_composition_candidates` | 网格导航 nihao → 候选行 → 上屏（黄金图 `s04_keyboard_candidates`） |
| `s05_language_case_switch` | 中英与大小写切换：EN+UPPER 直落 A / LOWER 直落 a |
| `s06_symbols_fullhalf_golden` | 符号键盘与全半角（黄金图 `s06_keyboard_symbols`，模拟器滞留帧白名单） |
| `s07_delete_rhythm` | 删除节奏：单删 3 次 + OK 长按连删 |
| `s08_back_exit_gate` | 设置页 BACK 二次退出（2 秒窗） |
| `s09_hidden_key_passthrough` | 隐藏态键码透传宿主 |
| `s10_remote_pairing` | 远程配对（phone_driver 模拟手机端配合） |
| `s11_update_check_mock` | 检查更新（serve_mock 桩：发现新版 v0.3.0 + 已是最新） |

**输出文件**

| 路径 | 内容 |
| --- | --- |
| `reports/integration/integration-report.md` | 场景 × PASS/FAIL 汇总报告；s10 一并记录实际手机模拟通道 |
| `reports/integration/pulled/round{1,2}/` | 每轮 `results.tsv` + 截图 |
| `reports/integration/logs/` | 模拟器 / serve_mock / phone 驱动日志 |
| `app/src/androidTest/assets/goldens/emulator-api34/` | 首轮落位的黄金图（入库资产，次轮起比对用） |

**退出状态**

- `0`：编排完成。不代表全场景 PASS；场景级结果以报告为准。
- `1`：阶段失败（RuntimeError：APK 缺失、就绪超时、非冷启、装机失败、端口残留等）。
- `2`：用法错误（argparse）。

**限制**

- 真机场景执行（`am instrument`）不在阶段内：真机全量一轮受 instrumentation 击杀链限制不可达（[docs/devices/](../../docs/devices/) 设备档案），按「单场景一跑」手动编排；黄金图真机目录 `mitv4-api23`，首轮记 `GOLDEN_RECORDED` 基线。
- `mitv4-install` 做纯 dex 校验：检出 `lib/` 即中止。
- 许可弹窗代点上限 4 次，不解除即停手；确认界面只吃 tap 不吃键码。
- `boot` 仅支持模拟器；已有模拟器在线时拒绝执行（先 `teardown`）。

**示例**

```bash
# 模拟器一键全跑（约 25~40 分钟；先构建两包）
./gradlew assembleDebug assembleDebugAndroidTest
python3 tools/integration/integration_runner.py --stage full --device emulator

# 定向重跑两个场景（当前实例已就绪时）
python3 tools/integration/integration_runner.py --stage instrument \
  --scenarios s01_keyboard_show,s04_composition_candidates

# 只看真机计划（不触设备，可随时执行）
python3 tools/integration/integration_runner.py --stage mitv4-plan
```

---

## PHONE_DRIVER(1) · phone_driver.py

**名称** — 手机 H5 端模拟驱动：配对 + 远程打字全链。

**概要**

```bash
python3 tools/integration/phone_driver.py [--device {emulator|mitv4}]
      [--marker-prefix STR] [--text STR] [--timeout SEC]
```

**描述**

- 轮询设备 `settings system <prefix>_remote_url`（IME 远程卡写入）取配对 URL，注入文本并上屏，核验回执 `<prefix>_phone_committed` / `<prefix>_phone_channel`。
- 通道选择：真机按 chrome > chromium > brave 检测（macOS `.app` 与 PATH 双查），命中走 headless 页面全链；未命中或页面 WS 15 秒超时降级 python3 WS 直连。模拟器固定 python3 WS 直连（slirp 隧道对浏览器式并发连接毒化，实测不可恢复）。
- adb home 同走通用机制（见「环境变量」节）；真机路径沿用调用方 adb 环境。
- 须与 instrument 的 s10 场景并发运行：先起本进程，再起 instrument。

**选项**

| 选项 | 默认 | 说明 |
| --- | --- | --- |
| `--device` | `emulator` | 目标设备 |
| `--marker-prefix` | `integration` | settings 键前缀（须与 harness 写入侧一致） |
| `--text` | 客厅远程集成验证 | 注入文本 |
| `--timeout` | `240` | 等待配对 URL 的秒数上限 |

**退出状态**

- `0`：完成，上屏回执核验通过。
- `2`：限时内未读到 remote_url marker。
- 其他非 0：通道失败。

**清理纪律**：headless 实例只杀自建进程（临时 profile 前缀 `integration-phone-browser-`）；收尾后 9333 无监听、无残留页面。每轮报告如实记录实际通道、浏览器路径与版本、是否降级。

---

## 参见

- [DEVELOP.md](../../DEVELOP.md)「测试」节：模拟器 / 真机完整操作指引与参数对照
- [docs/devices/MiTV4-ANSM0.md](../../docs/devices/MiTV4-ANSM0.md)：真机纪律与已踩坑
- [tools/mock/github-releases/](../mock/github-releases/README.md)：serve_mock 更新源桩
- [docs/issues/closed/T91.md](../../docs/issues/closed/T91.md)：工具来源与拍板记录
