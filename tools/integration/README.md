# tools/integration · 集成测试宿主 wrapper（T91）

宿主侧薄 wrapper：模拟器 instrument 全轮编排（冷启 → 装机 → ime set 核对 → am instrument →
收报告 → 显式收尾）+ 真机专有环节逻辑（保活循环、安装许可 tap 代点、场景映射）。
工具口径：python3（宿主 PATH）+ adb + flake 内 emulator / gradle（经 `nix develop -c` /
`nix run` 子进程调用）。真机执行一律先询问户主，户主同意后才跑（issue T91 口径 3）。

## s10 手机端通道（户主拍板：本地检测驱动）

- 检测本机 chrome / chromium / brave（macOS .app 与 PATH 双查，按此优先级，第一个能成功启动
  headless 实例的胜出）；命中则开 H5 页 + DOM 探针 + 页面自身 WS + H5 UI 全链
  （textarea 注入 → 上屏按钮 → 回执清空），WS 连接探测 15 秒短时限，超时快速降级。
- 未检测到浏览器、启动失败或页面 WS 超时 → 回退 python3 标准 WS 客户端直连（已证通路）。
- obscura 已从 s10 链路移出（其页面 WS 经 slirp 隧道存在稳定挂起假象，隔离诊断见 T91 报告）；
  obscura 仍留在 flake 供其他用途。
- **s10 场景浏览器为本地工具、非 flake 工具链**（户主拍板放开）；每轮报告如实记录实际通道、
  浏览器二进制路径与版本、是否发生降级。
- 清理纪律：headless 实例只杀自建标志进程（临时 user-data-dir 前缀 `integration-phone-browser-`），
  走查后无残留监听端口（9333 复核）、无残留页面。

## 模拟器一轮全跑（推荐入口）

```bash
cd <工作区>
python3 tools/integration/integration_runner.py --stage full --device emulator
```

- 前置：仓库已构建出 `app-debug.apk` 与 `app-debug-androidTest.apk`（构建命令见仓库本地开发指引
  「构建与测试」节）；无在线模拟器实例（活实例不可跨回合依赖）。
- 流程：删 default_boot 快照真冷启 → 60 秒就绪判据 → `/proc/uptime` 秒级核验 → 装两包 →
  `ime set` 核对 → 起 serve_mock（:8000）+ obscura（:9222，`--allow-private-network`）→
  Round A（黄金图缺失时自动记录实机基线）→ 黄金图落位 `app/src/androidTest/assets/goldens/emulator-api34/`
  → 重建重装 androidTest 包 → Round B（`Bitmap.sameAs` 真实比对）→ 报告落
  `reports/integration/integration-report.md` → `adb emu kill` + 端口复核。
- 预期时长：冷启 2~4 分钟 + 两轮各 5~12 分钟 + 构建 1~2 分钟 ≈ 25~40 分钟。
- 产物：`reports/integration/integration-report.md`、`reports/integration/pulled/round{1,2}/`（results.tsv + screenshots）、
  `reports/integration/logs/`（emulator / serve_mock / obscura / phone 驱动日志）。

## 分步执行

| 命令 | 用途 |
| --- | --- |
| `integration_runner.py --stage boot` | 冷启模拟器（含冷启核验） |
| `integration_runner.py --stage install` | 装 debug + androidTest 两包（装后 `am start` 一次解 stopped 态） |
| `integration_runner.py --stage ime-check` | `ime set` + default 核对 |
| `integration_runner.py --stage instrument` | `am instrument -w -e class …InstrumentedScenarioSuite` |
| `python3 tools/integration/phone_driver.py --device emulator` | 手机端模拟驱动（须与 instrument 并发） |
| `integration_runner.py --stage report` | 汇总 results.tsv 生成报告 |
| `integration_runner.py --stage teardown` | `adb emu kill` + 5554/5555 + 8000/9222 复核 |

## 真机（MiTV4）段——本轮未执行

- 场景映射与前置清单（只打印不触设备）：
  `python3 tools/integration/integration_runner.py --stage mitv4-plan`
- 真机前置核对 / 装机（push + `pm install` + 安装许可 tap 代点 + 保活循环）逻辑已内置：
  `--stage mitv4-preflight` / `--stage mitv4-install --host <ip:5555> --confirm-tap x,y`。
  **`mitv4-preflight` 与 `mitv4-install` 在获得户主逐次批准前被脚本硬性拒绝执行。**
- 代点纪律（设备档案坑 16/17）：先 `screencap` 目测弹窗确认钮，坐标经 `--confirm-tap` 传入，
  双钮两段式 tap×2，上限 4 次不解除即停手交户主，不轰炸重试；确认界面只吃 tap 不吃键码。
- 保活循环（设备档案坑 2）：装机等待期每约 6 分钟一发 DPAD，走查毕即清；instrument 运行期
  不注键码（防污染场景焦点），由 harness 侧 `svc power stayon true` 保屏。
- 黄金图目录：`mitv4-api23`（真机首轮自动走 GOLDEN_RECORDED 记录实机基线，次轮起真实比对）。
- 装机核验铁证：`dumpsys package com.sofatype.ime` 的 `pkgFlags` 含 `DEBUGGABLE`。
