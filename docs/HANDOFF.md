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
- 2026-11-04：施工前任务拆解定稿。施工计划、任务清单与状态见 `docs/TASKS_20261104.md`；新增拍板决策 14~22，见 PRD 第 12 章。
- 2026-11-04：变更记录规范拍板。issue 与任务一一对应（`docs/issues/`，规范见 `docs/issues/README.md`）；HANDOFF 记会话、CHANGELOG 记发版；TASKS 任务编号改为全局顺序、从 1 开始（T1~T27）；issue 桩文件已预建于 `open/`，任务开工时填充正文。
- 2026-11-04：issue 模板补「目标」章节：目标描述本 issue 交付的功能与结果（结果导向），「改动」只记具体执行内容（执行导向），两者分开写（规范见 `docs/issues/README.md` 规则 6）。全部 27 个 issue 文件已按新模板补齐目标，T1 亦补（纯文档调整，不建 issue）。
- 2026-11-04：T2 曾按户主指示暂缓。2026-11-05 lead 派发并完成 T2 开发与模拟器验证（见下方版本记录）。2026-11-05 T2 验收关闭：模拟器 + 真机双验收通过，户主拍板归档（issue 挪 `docs/issues/closed/T2.md`，任务状态「已实现」）；验收后环境已清理——真机卸载 `com.sofatype.ime`、模拟器关闭、`~/.android` 重复 AVD 副本（2.9G）删除，细节见 issue T2「户主拍板」节。
- 2026-11-05：T3 评审第 1 轮通过（code-reviewer 盲审，含独立复跑构建与单测），按 TASKS 6.2-7 口径自动归档：issue 挪 `docs/issues/closed/T3.md`，任务状态「已实现」；细节见版本记录 T3 节。
- 2026-11-05：T5 完成（双 agent 盲审 3 轮通过，自动归档：issue 挪 `docs/issues/closed/T5.md`，任务状态「已实现」）。同期拍板：T4 暂缓先派 T5（PRD 决策 25）、双 agent 盲审评审执行方式（TASKS 6.2-9）。细节见版本记录 T5 节。
- 2026-11-05：T28 完成（户主发现 T5 完成键与图标列错位 24px，回归修复；双 agent 盲审第 1 轮通过，自动归档：issue 挪 `docs/issues/closed/T28.md`，任务状态「已实现」）。根因与修复细节见版本记录 T28 节。
- 2026-11-06：T29 完成（户主发现 T8 符号键盘最后两列错位 54px，回归修复；review-qa 盲审第 1 轮 PASS 0 blocker，自动归档：issue 挪 `docs/issues/closed/T29.md`，任务状态「已实现」）。根因与修复细节见版本记录 T29 节。
- 2026-11-05：T4 完成（无线 adb 真机通道打通，键盘真机弹出；双 agent 真机验证 + 评审审计通过，户主拍板「真机已验证」归档；拍板后应用已卸载还原现场）。同批完成 T5/T28 真机补充验证（真机几何与模拟器/设计零偏移、色值全中、API 23 无渲染差异）。细节见版本记录 T4 节。
- 2026-11-05：户主拍板状态词口径修订（PRD 决策 26）：已完成任务经真机验证可回溯标「真机已验证」；同批回溯标注 T2/T3/T5/T28，TASKS 状态行与各 issue 真机记录已同步。
- 2026-11-05：户主拍板文档改名（批次后缀）：`docs/PRD.md` → `docs/PRD_20261104.md`、`docs/TASKS.md` → `docs/TASKS_20261104.md`，对应 2026-11-04 需求批次；新 issue / 新任务批次未必记录于此，另行建档。全仓库引用已同步（AGENTS / HANDOFF / CHANGELOG / issues 等），AGENTS.md 目录树已更新。
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

- 开工顺序：`AGENTS.md` → `docs/PRD_20261104.md` → `docs/devices/` 设备档案 → `designs/`。
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

### T3 · 最小可用 IME（2026-11-05，随未发布 0.1.0）

**干了啥**

- manifest 注册 IME service（`BIND_INPUT_METHOD` 权限 + `android.view.InputMethod` intent-filter + `android.view.im` meta-data 引用 T2 预留的 `@xml/method`，系统输入法设置入口自此生效）；`SofatypeImeService` 占位键盘两键：「A」上屏 1 字符、「下屏」`requestHideSelf` 收起；`onEvaluateFullscreenMode` 恒 false，防 TV 横屏全屏提取模式盖住宿主界面。系统启用走 `ime enable` / `ime set`（TASKS 6.2.1 口径）。
- 占位键盘按键→动作纯逻辑最小抽取（`PlaceholderKeyboard.kt`，不依赖 Android 类），JUnit 单测 3/3 绿；`testImplementation("junit:junit:4.13.2")`，仅 test 作用域，无产品依赖。
- `./gradlew assembleDebug` + `testDebugUnitTest` 全绿（nix develop，缓存重定向进工作区）；模拟器（AVD sofatype-tv / API 34 冷启动）验收链路「唤起 → 输入 1 字符 → 收起」走查通过，「下屏」与系统返回键两条收起路径均验，收起不丢内容。证据见 `docs/issues/open/T3.md` 验收节与 `.shots-t3/`（gitignore）。走查后现场已清理：/data/local/tmp 本任务临时文件删除、模拟器按 PID 精确终止、adb server 停止，无守护进程残留。

**踩过的坑**

1. 同一条 `export` 语句内 `JAVA_TOOL_OPTIONS="-Duser.home=$HOME"` 展开先于赋值生效，user.home 指到旧 HOME → `validateSigningDebug` 写真实 `~/.android` 失败；拆两条 export（先 HOME 后 JAVA_TOOL_OPTIONS）后恢复。T1 坑 1 的补救命令须按此顺序，模板已补 `local/DEV-GUIDE.md` 1.5。
2. `uiautomator dump` 不含 IME 窗口（键位坐标用截图目测）；dump XML 属性顺序 text 在 resource-id/class 之前（细节见 issue T3 坑 2）。

**没干成 / 待办**

- 真机启用与走查属 T4 里程碑，未验证（模拟器 API 34 ≠ 验收机 API 23）。
- 占位键盘无真实布局与样式，T5 重做；届时删除 `ime_placeholder_height` / `ime_key_gap` 占位 Token 与 `PlaceholderKeyboard.kt`（真实键位模型替换）。

### T5 · 星夜青 Token + 键盘静态布局（2026-11-05，随未发布 0.1.0）

**干了啥**

- 键位模型 `KeyboardLayout.kt`（纯 Kotlin：画板① 4 行键位 + 跨两行完成键 + 动作语义 + 无障碍标注）+ 键盘悬浮层 `ime_keyboard.xml`（主副卡 + 底部柔影垫层，停靠底部 644px）+ drawable 8 个 + Token 补全；`SofatypeImeService` 改模型驱动生成键帽，仅「下屏」动作生效；删除 T3 占位物（`PlaceholderKeyboard.kt`、`ime_placeholder_height` / `ime_key_gap`、占位键字符串）。单测 12/12。
- 双 agent 盲审 3 轮闭环：R1 不通过（`.com` 键样式、柔影缺失）→ R2 不通过（副卡垫层复用主卡宽 Token，溢出罩暗主卡右半区）→ R3 通过（5 复测点精确命中、回归全绿）。评审方每轮独立复跑构建 / 单测 / 模拟器像素采样。细节见 `docs/issues/closed/T5.md`。

**踩过的坑**

1. 柔影垫层 Token 两坑：首版误按「px 再 ÷2」换算被卡体全遮 + `clipToPadding` 默认裁掉溢出晕带；副卡复用主卡宽 Token 致左溢 412px 罩暗主卡右半区（同一坑修了一半）。细节见 issue T5 坑 1/2。
2. IME 窗口边界吃掉柔影顶底带（系统约束非缺陷，dumpsys 双证）；采样验收的采样点须由布局几何推算生成并覆盖污染区，不能只采外侧带。细节见 issue T5 坑 2/3。

**没干成 / 待办**

- 真机（API 23 小米电视）柔影观感与完整遥控走查属 T12 里程碑，未验证。
- 键盘行为留待后续任务接线：焦点导航 T6、完整输入 T7、符号键盘 T8、拼音与候选 T9/T10；动作语义已在模型预留插点。

### T28 · 键盘列间隔对齐画板①修正（2026-11-05，随未发布 0.1.0）

**干了啥**

- 户主走查发现 T5 完成键与上方图标列错位 24px、空格→完成 36px。像素审计定位根因：画板① 列间隔实宽 66px（flex gap 12px 在 42px spacer 两侧各一道），实现只算单侧 54px，后续列累计左移（字母 −12、功能/图标列 −24）；完成键绝对定位本身精确。修复：`SofatypeImeService` spacer 视图补 `marginEnd`（一处改动，四列回位）；新增列节奏单测 `columnRhythmMatchesArtboard`（13/13）锁画板①口径。双 agent 盲审第 1 轮通过（含评审方独立复算盒模型 + 逐列像素核查）。细节见 `docs/issues/closed/T28.md`。

**踩过的坑**

1. flex gap 双侧口径坑：CSS flex `gap` 对 spacer 两侧各生效，列间隔 = 12+42+12 = 66px，不是 42+12 = 54px（T5 坑 1 同类）。口径已写进 Token 注释与单测防再犯，细节见 issue T28 坑 1。
2. 遗留：行 4 键帽顶缘 +2px 偏移（894 vs 892，T28 引入、容差内、机制待查）——**T8 重构键盘布局前必须先查明**，细节见 issue T28 坑 3。

**没干成 / 待办**

- 实现层 marginEnd 间距合成无 JUnit 覆盖（不引第三方测试库前提下），回归靠模拟器像素走查兜底；T8 复用 Cell 结构时保留走查口径（评审观察项）。
- 真机统一走 T12 里程碑。

### T4 · 真机安装通道（2026-11-05，随未发布 0.1.0）

**干了啥**

- 无线 adb 通道打通：真机钥匙（`HOME=.MiTV4-adbhome`）连 192.168.1.210:5555，小包验通道秒回 → `adb install -r` 装 813KB debug 包 38.2s（5 分钟硬限内）；纯 dex 复核（设备档案坑 1）通过；`ime enable`/`ime set` 启用；键盘两路真机弹出（am start 自动弹出 + DPAD 唤起）。
- 同批完成 T5/T28 真机走查：评审审计实测真机几何与设计/模拟器**零偏移**、五项色值全中、API 23 无渲染差异；行 4 +2px 真机复现（T28 坑 3 的 T8 待查项获跨 API 数据）。双 agent 真机验证 + 评审审计通过，户主拍板「真机已验证」；拍板后应用卸载、电视还原（默认输入法搜狗未动）。

**踩过的坑**

1. IME 夺回：`ime set` 后搜狗崩溃反馈，系统自动把默认输入法切回搜狗——弹键盘前须核对 `default_input_method`（issue T4 坑 1，已同步设备档案）。
2. 屏保抢占：电视空闲自动进 `com.mitv.screensaver`，tap 全落空——真机走查须连贯执行（issue T4 坑 4，已同步设备档案）。
3. 根 Activity BACK 时序：根页 BACK 直接退应用；DPAD 唤起后 BACK 只收 IME（issue T4 坑 2）。
4. dev 报的「真机 +1 系统性偏移」被评审证伪（扫描口径差）——报几何定性前先排除自身工具因素，此纪律已向 dev 固化（issue T4 验收节修正记录）。

**给下一个 agent**

- 遥控器焦点可见性属 T6：当前基线无焦点态描边，真机按遥控「看起来没反应」不是故障（焦点实测在移动）。
- T8 前必须先查明行 4 +2px 机制（T28 坑 3）。
- 真机走查前置检查：唤醒防屏保、核对默认 IME、BACK 时序。

### T6 · 遥控器焦点导航基座（2026-11-06，随未发布 0.1.0）

**干了啥**

- 新增 `FocusNavigator.kt`（纯 Kotlin 焦点导航：画板① 12 列 × 4 行格网，左右同行/上下同列/表缘不动/完成键顶行锚）+ `FocusNavigatorTest.kt` 11 项（网格全覆盖、行列性质、表缘不动、完成键路径、47 键全可达）；`SofatypeImeService` 接线：初始焦点、DPAD 导航、OK 触发、BACK 收起、完成键收起；`bg_key`/`bg_key_done` 补焦点态（key_hover + border_accent / accent_wash_strong，零新增色值）。
- 单测 24/24 绿（FocusNavigatorTest 11 + KeyboardLayoutTest 13）；模拟器（AVD sofatype-tv / API 34）遥控器键走查通过：初始焦点、行列移动、OK 触发（下屏/完成收起、mInputShown=false 佐证）、收起不丢内容（示例文本两路径收起后保留）。证据见 `docs/issues/open/T6.md` 验收节与 `screenshots/T6/`。

**踩过的坑**

1. 转发键事件（DPAD/OK）不经过 IME view 树，键帽/根视图 OnKeyListener 均收不到，必须在 `IMS.onKeyDown` 处理；BACK 早期实测有效是 policy 层路径，不能当「事件到 view」的证据（issue T6 坑 2/3）。
2. CENTER 键广播双触发：IME 同步收起被宿主 EditText 的 showSoftInput 覆盖，「下屏/完成」收起延迟 120ms（`hideSelfAfterKeyDispatch`），时序常量真机需复核（issue T6 坑 1）。
3. BACK 在宿主文本选择手柄激活时第一下被宿主消费（退出选择），第二下才收起——平台标准行为。

**没干成 / 待办**

- 真机走查（API 23 小米电视）属 T12 里程碑，未验证；`HIDE_DELAY_MS` 时序常量真机复核待 T12。
- 文本输入（字母键动作）、符号键盘、拼音候选分别属 T7/T8/T9/T10；OK 已能把焦点键的 click 派发到位，T7 接 `execute` 的 Text 动作即可。

**给下一个 agent**

- 键位事件处理全部在 `SofatypeImeService.onKeyDown`：方向键 → FocusNavigator；OK/ENTER → performClick；新增键面行为改 `execute(action)` 分支。
- 模拟器走查注意：`input tap` 唤起后键盘内的方向键走查不受 touch mode 影响（事件经 service 层）；焦点态肉眼核对走截图（uiautomator dump 不含 IME 窗口，既有坑）。

### T7 · 英文模式完整输入（2026-11-06，随未发布 0.1.0）

**干了啥**

- 模型层：`CaseState`、`displayLabel`/`outputValue`（大写态键面与输出变换）、`shiftContentDescription`（FR-A3 原文态提示）、`letterKeyIds`；service 层：`execute(key)` 接线 FR-A9 全部动作（字母/数字/`.com`/`@` 直出、空格、删除、光标移动）+ 大小写切换与键面/态提示刷新（上档激活视觉复用强调键 drawable 与既有 Token）；`ImeKeyTrace` 诊断日志（T6 评审遗留跟进，只记键码/焦点 key id）。单测 28/28 绿（KeyboardLayoutTest 17 + FocusNavigatorTest 11）。
- 模拟器走查：大小写两态切换（截图）、大写态输出 H、空格/删除互证、光标定位插入、`.com`/`@` 直出、完成键收起文本保留；完整样例 "Hello.com@" 出现在输入框文本中（`docs/issues/open/T7.md` 验收节）。

**踩过的坑**

1. `focusableInTouchMode` 键帽首击聚焦不点击（View `focusTaken` 语义）：tap 走查大面积「丢键」（3 tap→2 字符）；走查用 `input keyevent`（导航+OK 23）替代 tap（issue T7 坑 1）。
2. 模拟器注入通道偶发丢事件（keyevent/tap 均有）：logcat 证明事件未到达 IME 即丢失，重启模拟器实例恢复——T6「偶发焦点卡死」同源现象，`ImeKeyTrace` 焦点轨迹日志是定位手段（issue T7 坑 2）。
3. CENTER 宿主竞态（T6 坑 1）本轮复现一次：OK 的 commitText 疑似被宿主同拍处理吞掉（issue T7 坑 3）。

**没干成 / 待办**

- 真机走查属 T12 里程碑，未验证。
- 符号键盘（T8）、拼音/候选（T9/T10）未动；中/英切换键、123_! 键仍静默（T8/T10 接线）。

**给下一个 agent**

- 大小写变换入口全部在模型（`displayLabel`/`outputValue`/`shiftContentDescription`），T8 符号键盘复用键面生成时字母区已随 `applyCaseState()` 刷新。
- 模拟器走查优先 `input keyevent` 通道；tap 需双击（首击聚焦）；事件丢失先查 logcat `ImeKeyTrace`（无记录 = 注入层丢），必要时重启模拟器实例。

### T8 · 符号键盘（2026-11-06，随未发布 0.1.0）

**干了啥**

- 新增 `SymbolsLayout.kt`（4 行 12 列格网与字母键盘同构；全角 30/半角 28 符号集逐字符对齐 PRD FR-A6；空格/完成键跨行 3/4 col11/12、返回键行 2 col11，决策 3/27）；`FocusNavigator` 泛化（overlayCells/initialCell 参数化，字母键盘默认行为不变）；`KeyboardLayout` 增 `ToggleWidth`/`BackToLetters` 动作与 `EmptyCell`；service 布局状态机（字母 ↔ 符号、全/半，`rebuildKeys` 重绑行/浮层/导航器）+ `ImeKeyTrace` debug 门控（T7 评审遗留跟进项）。单测 45/45 绿（新增 SymbolsLayoutTest 17 项：符号集逐字符比对、决策 3/27 断言、格网满覆盖、导航性质、全键可达）。
- 模拟器走查：进入符号键盘（123_! → 全角态布局截图）、全角样例 ＃？直出、全/半切换（半角布局截图，logcat 佐证）、半角直出、返回字母键盘（logcat 佐证）、BACK 收起文本保留。证据见 `docs/issues/open/T8.md` 验收节与 `screenshots/T8/`。
- 开工前拍板：PRD FR-A6 全角 30 符号 vs 画板 28 格真冲突，户主拍板方案 D（决策 27，PRD 第 12 章已记）：键位微调排入 30 符号、符号键盘不含 .com/@、designs/ 不改。

**踩过的坑**

1. 画板③行 3 col11 的 .com 键与空格 ABS 键（col11 跨行 3/4）重叠——设计稿自身缺陷；实现行 3 col11 为空格 ABS 格位（issue T8 坑 1）。
2. PRD 全角 30 符号 vs 画板 28 格真冲突——户主拍板决策 27 解决（issue T8「原因」节）。
3. 模拟器注入通道丢事件（T7 坑 2 延续）本轮复现：tap 双击全/半的事件未达键帽而落宿主致键盘收起；ImeKeyTrace 无到达记录 = 注入层丢失，重启实例恢复（issue T8 坑 3）。

**没干成 / 待办**

- 真机走查属 T12 里程碑，未验证。
- 拼音/候选（T9/T10）未动；符号键盘内中/英切换不存在（FR-A5 决策：无中/英键）。

**给下一个 agent**

- 布局切换入口：`SofatypeImeService.switchKeyboardMode`/`rebuildKeys`；新增布局须提供 rows + overlayCells（跨行键格位）+ initialCell，格网 12 列 × 4 行与既有布局同构（数字列 col1-3、功能区 col11/12）。
- `Cell.EmptyCell`（占格无键）用于布局缺键位；`input tap` 走查需双击（focusableInTouchMode 首击聚焦，T7 坑 1）。

### T29 · 符号键盘最后两列对齐修复（2026-11-06，随未发布 0.1.0）

**干了啥**

- 户主走查发现 T8 回归：符号键盘行 1/2 最后两列较浮层键左移 54px。根因 = `SymbolsLayout.rows()` 漏抄画板③行 1/2 符号区后的 `kspacer`（T28 坑 1 防的正是这类漏抄，但 T8 无列节奏单测兜底故未拦住）。修复：行 1/2/3 各补一道 `Cell.Spacer`（行 3 与画板结构对齐，几何无影响），全/半、返回回位功能列 1028、下屏、删除回位图标列 1120，与浮层空格/完成及字母键盘逐位对齐（模拟器像素核查偏差 0，修复前实测 1082/1174 → 修复后 1136/1228）。
- 补测试锁定：SymbolsLayoutTest 新增 `functionColumnOriginsMatchLettersKeyboardAndOverlays`（功能列/图标列 x 原点 + 与字母键盘行 1/2 列原点逐位一致断言），17→18 项；全量 46/46 绿。review-qa 盲审第 1 轮 PASS（0 blocker / 0 major / 0 minor，独立复跑构建单测 + 独立像素核查），按 TASKS 6.2-7 非里程碑口径自动归档。细节见 `docs/issues/closed/T29.md`。

**踩过的坑**

1. 新键盘布局缺「列节奏单测」时，格网列号断言（positionsOf col==11）拦不住视觉错位——错误 x 原点会被 FocusNavigator 首次顺序编号成正确列号。凡新增布局，必须同 `columnRhythmMatchesArtboard` 口径加 x 原点断言。
2. `adb force-stop` IME 包会把系统输入法打回 Gboard（本轮实测）：走查中断后恢复键盘用 `ime set` 重设 + BACK 收起 + 重 tap 输入框，不要 force-stop。

**没干成 / 待办**

- 真机走查属 T12 里程碑，未验证。
- git status 存在一处非本任务 staged 改动（`.agents/skills/dev/SKILL.md`），户主提交前自行决定取舍。
