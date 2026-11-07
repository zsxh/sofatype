# SofaType 输入法

Android TV 输入法：电视端键盘 + 跨屏输入（电视出二维码、手机 H5 远程打字）+ 设置页。

## 功能

- **TV 键盘**：拼音（词组级候选）/ 英文 / 符号三模式，遥控器焦点导航。
- **跨屏输入**：电视端起局域网 HTTP/WS 服务并展示二维码，手机扫码打开 H5 页远程打字，实时上屏电视焦点输入框。
- **设置页**：输入法设置；测试输入区仅 debug 构建显示。

技术要点：纯 Kotlin，无第三方 SDK（仅二维码用 ZXing core）；自研拼音引擎 + 内置 AOSP 谷歌拼音词库（Apache-2.0）；H5 纯原生 HTML/CSS/JS、打包进 APK 本地下发，不依赖任何外部服务；输入内容不出电视，服务仅监听局域网接口。

| 项 | 值 |
| --- | --- |
| 语言 | Kotlin |
| minSdk / compileSdk | 23 / 34 |
| 兼容基线 | Android TV 6.0+（验收机小米电视4） |
| 当前状态 | v1.2 施工中，进度见 `docs/TASKS_*.md` |

## 本地构建

前置：[Nix](https://nix.dev)（启用 flakes）。宿主支持 macOS（Apple Silicon）与 Linux（arm64 / x86_64）。首次进入会自动下载 Android SDK 组件，需网络。

```bash
nix develop      # 开发 shell：JDK 17 / Android SDK / adb / gradle / Obscura 全就绪
./gradlew assembleDebug                                         # Debug APK
./gradlew assembleDebug testDebugUnitTest assembleRelease       # 全量：单测 + debug + 未签名 release
```

产物在 `app/build/outputs/apk/{debug,release}/`（release 为未签名口径）。

## 本地开发

- **工具链以 `flake.nix` 为准**：一律在 `nix develop` 内操作，勿混用系统 PATH 的 JDK / gradle / adb。
- **TV 模拟器**：

  ```bash
  nix run .#tv-avd        # 创建 AVD（首次执行一次）
  nix run .#tv-emulator   # 启动模拟器
  ```

  遥控器对应：方向键 = 十字键，回车 = OK，退格 = 返回。

- **单元测试**：`./gradlew testDebugUnitTest :pinyin:test`（拼音引擎是纯 JVM 模块，需单独跑）。
- **H5 页调试**：devShell 内置 Obscura 无头浏览器，提供 CDP 端点：

  ```bash
  obscura serve --port 9222 --allow-private-network   # 调本机 / 局域网页面必须带 --allow-private-network
  ```

  Puppeteer 用 `puppeteer.connect({ browserWSEndpoint: 'ws://127.0.0.1:9222' })`，Playwright 用 `chromium.connectOverCDP`。调试完退出 serve，不留监听端口。注意 nixpkgs 锁的是 0.2.2 版：截图中文为方块，文字断言走 `Runtime.evaluate`。

- **真机安装**：见 [docs/devices/](docs/devices/) 设备档案（一机一档，含已踩坑）。

## 文档

| 文档 | 内容 |
| --- | --- |
| `docs/PRD_*.md` | 需求唯一来源（含拍板决策记录） |
| `docs/TASKS_*.md` | 施工任务拆解与进度 |
| [docs/HANDOFF.md](docs/HANDOFF.md) | 开发交接：关键决策 / 坑清单 / 当前状态 |
| [docs/issues/](docs/issues/) | 变更记录（一任务一档） |
| [docs/devices/](docs/devices/) | 验收设备档案 |
| [designs/](designs/) | 设计稿定稿（视觉参考） |

AI agent 协作规范见 [AGENTS.md](AGENTS.md)。
