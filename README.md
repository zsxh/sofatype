# SofaType 输入法

Android TV 输入法：电视端键盘 + 跨屏输入（电视出二维码、手机 H5 远程打字）+ 设置页。

## 功能

- **TV 键盘**：拼音（词组级候选）/ 英文 / 符号三模式，遥控器焦点导航。
- **跨屏输入**：电视端起局域网 HTTP/WS 服务并展示二维码，手机扫码打开 H5 页远程打字，实时上屏电视焦点输入框。
- **设置页**：输入法设置，含测试打字页与调试日志（release 同样可用）。

技术要点：纯 Kotlin，无第三方 SDK（仅二维码用 ZXing core）；自研拼音引擎 + 内置 AOSP 谷歌拼音词库（Apache-2.0）；H5 纯原生 HTML/CSS/JS、打包进 APK 本地下发，不依赖任何外部服务；输入内容不出电视，服务仅监听局域网接口。

| 项 | 值 |
| --- | --- |
| 语言 | Kotlin |
| minSdk / compileSdk | 23 / 34 |
| 兼容基线 | Android TV 6.0+（验收机小米电视4） |
| 当前状态 | v1.2 及后续变更全部归档（T1~T92）；变更走 issue 制，见 [docs/issues/](docs/issues/) 与 [docs/HANDOFF.md](docs/HANDOFF.md) |

## 开发

构建、本地开发与测试（Nix 工具链、TV 模拟器、H5 调试、真机安装、单元 / 集成 / 性能回归测试）见 [DEVELOP.md](DEVELOP.md)。

## 文档

| 文档 | 内容 |
| --- | --- |
| [DEVELOP.md](DEVELOP.md) | 开发指南：本地构建 / 工具链 / 模拟器 / H5 调试 / 真机安装 / 测试 |
| [`docs/PRD_20261104.md`](docs/PRD_20261104.md) | v1.2 需求基线快照（冻结只读，含拍板决策 1~78） |
| [`docs/TASKS_20261104.md`](docs/TASKS_20261104.md) | v1.2 施工拆解历史快照（冻结只读） |
| [docs/HANDOFF.md](docs/HANDOFF.md) | 开发交接：关键决策 / 坑清单 / 当前状态 |
| [docs/issues/](docs/issues/) | 变更记录（一变更一档，issue 即需求） |
| [docs/devices/](docs/devices/) | 验收设备档案 |
| [designs/](designs/) | 设计稿定稿（视觉参考） |

AI agent 协作规范见 [AGENTS.md](AGENTS.md)。
