# Handoff · 开发交接文档

给 agent 和人看的开发注意事项。记录每次版本更新的详细改动与踩过的坑。

细节出处：

- 简版差异：根目录 `CHANGELOG.md`。
- 实机参数与坑：`docs/devices/` 设备档案。
- 原则与红线：`AGENTS.md`。

本文档不重复上述内容。具体问题引用对应文档，不在本文展开。

## 维护约定（三处记录分工，2026-11-04 拍板）

1. **issue（`docs/issues/`）**：单次变更的唯一详细记录。每个代码 / 软件变更（功能、bug fix、构建脚本）对应一个 issue，与 TASKS 任务同名同编号；`open/` 进行中，户主验收拍板后挪入 `closed/`。纯文档调整不建 issue。规范与模板见 `docs/issues/README.md`。
2. **本文（HANDOFF）**：agent 每次独立完成一批活后追加一节记录：简短几句——干了啥、遇到什么坑、还有什么没干成；细节指向 issue，不重复展开。
3. **`CHANGELOG.md`**：发版时更新。一版一节，按该版本包含的 issue 汇总（新增 / 变更 / 修复）。
4. 实机新坑写入对应设备档案。同一事实只在设备档案维护，本文只写指向。
5. 需求 / 设计变更追加到 PRD 第 12 章。编号顺延，不重排。

## 当前状态（截至 2026-11-04）

- 2026-11-04：T1 验收关闭（户主拍板；issue 已挪 `docs/issues/closed/T1.md`，任务状态「真机已验证」）。户主裁决 wrapper 版本口径（决策 24）：以 nix package 为准，文档不规定 Gradle 版本。Gradle 工程位于仓库根：Kotlin 1.9.24 / AGP 8.2.2；minSdk 23、compileSdk 34、versionName 0.1.0(1)。构建：`nix develop` 内 `./gradlew assembleDebug`。真机安装实证：`com.sofatype.ime` 0.1.0(1) 已装小米电视4。
- PRD v1.1 与设计稿已定稿。开发期间不得改动（AGENTS.md 文档原则）。
- 2026-11-04：施工前任务拆解定稿。施工计划、任务清单与状态见 `docs/TASKS.md`；新增拍板决策 14~22，见 PRD 第 12 章。
- 2026-11-04：变更记录规范拍板。issue 与任务一一对应（`docs/issues/`，规范见 `docs/issues/README.md`）；HANDOFF 记会话、CHANGELOG 记发版；TASKS 任务编号改为全局顺序、从 1 开始（T1~T27）；issue 桩文件已预建于 `open/`，任务开工时填充正文。
- 2026-11-04：issue 模板补「目标」章节：目标描述本 issue 交付的功能与结果（结果导向），「改动」只记具体执行内容（执行导向），两者分开写（规范见 `docs/issues/README.md` 规则 6）。全部 27 个 issue 文件已按新模板补齐目标，T1 亦补（纯文档调整，不建 issue）。
- 2026-11-04：T2 曾按户主指示暂缓。2026-11-05 lead 派发并完成 T2 开发与模拟器验证（见下方版本记录）。2026-11-05 T2 验收关闭：模拟器 + 真机双验收通过，户主拍板归档（issue 挪 `docs/issues/closed/T2.md`，任务状态「已实现」）；验收后环境已清理——真机卸载 `com.sofatype.ime`、模拟器关闭、`~/.android` 重复 AVD 副本（2.9G）删除，细节见 issue T2「户主拍板」节。
- 工具链已拍板（2026-11-03）：语言 Kotlin；拼音引擎自研词组级 + 内置词库；局域网 HTTP/WS 服务自研、二维码用 ZXing core（纯 Java、无传递依赖）；工具链由根目录 `flake.nix` 声明，不在系统手工安装 SDK。
- `flake.nix` 已通过真实求值验证（2026-11-03）：环境构建成功，工具、TV 镜像、AVD 创建逐项核实，详见版本记录。

## 版本记录

### 阶段 0 · 文档与准备（2026-10-28 ~ 2026-11-03，无软件版本）

**改动**

- 2026-11-01：PRD v1.1 定稿。评审结论合入第 12 章（决策 1–12）。
- 2026-11-02：设计稿四组定稿。决策 13 拍板，兼容基线按验收实机调整。
- 2026-11-03：git init。创建 AGENTS.md，三次修订后定稿。

**踩过的坑**

1. ABI 约束：见设备档案「踩过的坑」。不要重走已排除的路径。
2. 同一事实只在一处维护。
3. 给 agent 的指引只写原则。具体值查 PRD 与设备档案。
4. 写 `enabled_accessibility_services` 只追加、不覆盖。zsh 里写 `${VAR}:...`。见 AGENTS.md 禁止事项 5。
5. 未在真机验证的功能不说「已完成」。见 AGENTS.md 开发规范。

**给下一个 agent**

- 开工顺序：`AGENTS.md` → `docs/PRD.md` → `docs/devices/` 设备档案 → `designs/`。
- 实机部署、传输、系统设置写入前，先读设备档案。
- 工程位置与构建工具链未拍板。动手前先向用户确认。结论记入本文下一节版本记录。

### 开发环境配置 · Nix flake（2026-11-03，随未发布 0.1.0）

**改动**

- 新增根目录 `flake.nix`：JDK 17（darwin 上解析为 Zulu 17 预编译二进制）+ Android SDK（platform android-34、build-tools 34.0.0、cmdline-tools、platform-tools、emulator）+ TV 模拟器镜像 `system-images;android-34;android-tv;arm64-v8a`。
- 两个 app 入口：`nix run .#tv-avd` 创建 AVD（tv_1080p 形态），`nix run .#tv-emulator` 启动。遥控器对应：方向键=十字键、回车=OK、退格=返回。
- 支持宿主机：`aarch64-darwin` / `aarch64-linux` / `x86_64-linux`。Nix 按当前机器架构自动选择对应输出；列表外机器明确报错。
- 镜像 ABI 跟随宿主机：aarch64 → `arm64-v8a`（原生）；`x86_64-linux` → x86（android-tv 系列没有 x86_64 镜像）。
- Linux 上模拟器需要 `/dev/kvm`，使用者需在 kvm 组。
- 工具链最小集全部在 flake 内声明：JDK 17、Android SDK、git、gradle；不依赖宿主机已装的任何开发工具，保证跨机器可复现（用户拍板）。开发中需要新工具，先加进 flake 再使用。
- 不装 NDK / CMake / Sources：项目依赖均为纯 Java/Kotlin（含 ZXing core）。
- 许可证声明在 nixpkgs config：`android_sdk.accept_license = true` + `allowUnfree = true`（原因见坑 A / 坑 B）。
- `flake.lock` 已生成，锁定 nixpkgs `nixos-26.05`（commit `7dcdd873`）。

**原因**

- 用户拍板：先不手工安装 SDK，改用 Nix flake 声明工具链。声明式、可复现、不污染系统。
- compileSdk 34 / build-tools 34.0.0 / TV 镜像 API 34 的组合已逐项核对 androidenv repo.json（nixos-26.05 分支），包均存在；镜像用标准 `android-sdk-license`，无需预览许可。

**验证结果（2026-11-03，真实求值）**

- `nix develop -c true` 构建成功，exit 0。首次下载约 2 GB，耗时约 10 分钟。
- 环境内逐一核实：JDK Zulu 17.0.19、adb 37.0.0、emulator 36.5.11、gradle 8.14.4、build-tools 34.0.0、platform android-34；TV 镜像完整（kernel / ramdisk / build.prop 齐全，符号链接正确）。
- `nix run .#tv-avd` 成功创建 AVD `sofatype-tv`（tv_1080p / Android 14 / android-tv arm64-v8a）。
- 未验证：模拟器 GUI 实际启动（避免无人值守开窗口）。由户主手动 `nix run .#tv-emulator` 验证。

**踩过的坑 / 注意**

1. 坑 A：nixos-26.05 的 `composeAndroidPackages` 不再收 `licenseAccepted` 参数（上移到 androidenv 层，经 nixpkgs config 的 `android_sdk.accept_license` 声明）。传了直接报 `unexpected argument`。
2. 坑 B：SDK 组件（cmdline-tools / emulator / 系统镜像）在 nixpkgs 标记 unfree，需 `config.allowUnfree = true`，否则求值报 `Refusing to evaluate`。
3. 坑 C：组合 SDK 的统一可执行入口在包根 `${sdk}/bin`。mkShell 自动加该路径，但 `nix run` 的 app 脚本不会——PATH 必须自己带 `${sdk}/bin`；脚本要 `set -euo pipefail`，否则失败还打印成功文案（首次 `tv-avd` 就这样静默失败过）。
4. 坑 D：排查镜像注意两点——组合 SDK 里镜像路径是 `system-images/android-34/…`（带 `android-` 前缀）；镜像目录是符号链接，`find -type d` 看不见，要用 `ls -la`。
5. TV arm64 镜像从 API 31 才有；模拟器是 API 34（Android 14），与验收机 API 23 系统行为不同。模拟器只做逻辑回归，API 23 差异与最终验收靠真机。
6. 模拟器不覆盖：性能预算、微信内核兼容、真实遥控器。真机部署按设备档案执行。

**给下一个 agent**

- flake 已验证可用，M0 开工直接 `nix develop` 进入环境。
- `flake.lock` 必须随本批变更一起提交，保证跨机器可复现。
- 构建入口：shell 内用 `gradle wrapper` 生成包装器（gradle 包只为此存在），之后固定 AGP 版本于 `build.gradle.kts`，日常只用 `./gradlew`。（2026-11-04 决策 24 修订：wrapper 版本以 nix package 为准，文档不写死 Gradle 版本号；重生成在 flake 环境内跑 `gradle wrapper`，不带指定版本参数。）

### T1 · 工程骨架（2026-11-04，随未发布 0.1.0）

**干了啥**

- 仓库根创建 Gradle 工程：Kotlin 1.9.24 + AGP 8.2.2（wrapper 版本以 nix package 为准，决策 24）；minSdk 23 / compileSdk 34 / 0.1.0(1)；app 模块最小 manifest（无 Activity / Service，T2/T3 补入）。
- `./gradlew assembleDebug` 出包；模拟器（AVD sofatype-tv，API 34）`adb install` 成功。细节与验收证据见 `docs/issues/open/T1.md`。

**踩过的坑**

1. 官方 Gradle 发行版域名（services.gradle.org / downloads.gradle.org）网络超时：wrapper `distributionUrl` 改腾讯镜像，`distributionSha256Sum` 钉 nixpkgs fetchurl 权威校验和（与镜像下载字节比对一致）；wrapper 版本以 nix package 为准（决策 24）。细节见 issue T1 坑 3/6。
2. 沙箱（agent 环境）下 `~/.android`、`~/.gradle`、`~/.cache/nix` 不可写：构建与模拟器需把 HOME / JAVA_TOOL_OPTIONS(-Duser.home) / GRADLE_USER_HOME / ANDROID_USER_HOME / XDG_CACHE_HOME 重定向进工作区（已 gitignore）。非沙箱终端不受影响，细节见 issue T1 坑 1/2/4。

**没干成 / 待办**

- 真机安装通道（T4）未动。真机部署按设备档案执行（坑 1 armeabi-v7a）。
- 验收第 2 条「APK 含 `lib/armeabi-v7a/`」口径已按 PRD 决策 23 拍板（2026-11-04）：纯 Kotlin/Java 工程 APK 允许无 `lib/` 目录，ABI 核查视为通过；TASKS 与设备档案已回写。

### T2 · 最小设置页壳子（2026-11-05，随未发布 0.1.0）

**干了啥**

- 设置页 Activity + 面包屑「sofatype 输入法 ｜ 设置」；debug 独有测试输入区（输入框 + 「填入示例文本」「全选」，`BuildConfig.DEBUG` 门控，工程开启 `buildConfig` feature）；`res/xml/method.xml` 声明 IME 设置入口（service 与 meta-data 留 T3，lead 拍板方案 A）。色值/规格全部 Token 化：`res/values/colors.xml`（PRD 6.1 全集）、`dimens.xml`（1080p 基准 px ÷ 2 = dp/sp）。无第三方依赖，仍只有 Kotlin stdlib。
- `./gradlew assembleDebug` + `assembleRelease` 均出包；模拟器（AVD sofatype-tv / API 34）三项验收走查通过：adb 启动、输入框聚焦/输入/选中、release 无测试区。证据与截图说明见 `docs/issues/closed/T2.md` 验收节；2026-11-05 模拟器 + 真机验收均通过，户主拍板归档（见当前状态）。

**踩过的坑**

1. release 未配签名，unsigned APK 装不上模拟器：本地 apksigner + debug keystore 测试签名后安装；仅验证手段，仓库不加签名配置。
2. `selectAll()` 置中的选中区间，高亮只在输入框持焦时渲染；「全选」点击后焦点在按钮，走查需先把焦点移回输入框（DPAD 上移）才见高亮。

**没干成 / 待办**

- 系统输入法设置的入口未生效也未走查：依赖 T3 声明 IME service 时引用 `@xml/method`；「从系统入口进入」属 T20 走查范围。
- 品牌区、版本信息卡、底部操作提示属 T20，未实现。
- 模拟器已关闭（2026-11-05 验收后，进程精确匹配终止，无残留）；后续关闭仍按 T1 坑 5 用 /nix/store 路径精确匹配，勿 pkill 宽匹配。
