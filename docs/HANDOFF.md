# Handoff · 开发交接文档

给 agent 和人看的开发注意事项。核心价值：关键决策、踩过的坑、任务索引。

细节出处：

- 简版差异：根目录 `CHANGELOG.md`。
- 实机参数与坑：`docs/devices/` 设备档案。
- 原则与红线：`AGENTS.md`。
- 任务状态：`docs/TASKS_20261104.md`；需求决策全文：`docs/PRD_20261104.md` 第 12 章；环境与命令模板：`local/DEV-GUIDE.md`。

本文不重复上述内容；本文提及的各任务细节均已归档至 `docs/issues/closed/` 对应 issue。

## 维护约定（三处记录分工，2026-11-04 拍板）

1. **issue（`docs/issues/`）**：单次变更的唯一详细记录。每个代码 / 软件变更（功能、bug fix、构建脚本）对应一个 issue，与 TASKS 任务同名同编号；`open/` 进行中，户主验收拍板后挪入 `closed/`。纯文档调整不建 issue。规范与模板见 `docs/issues/README.md`。
2. **本文（HANDOFF）**：agent 每次独立完成一批活后追加一节记录：简短几句——干了啥、遇到什么坑、还有什么没干成；细节指向 issue，不重复展开。
3. **`CHANGELOG.md`**：发版时更新。一版一节，按该版本包含的 issue 汇总（新增 / 变更 / 修复）。
4. 实机新坑写入对应设备档案。同一事实只在设备档案维护，本文只写指向。
5. 需求 / 设计变更追加到 PRD 第 12 章。编号顺延，不重排。

## 当前快照（截至 2026-11-06）

- 工程基线：仓库根 Gradle 工程，Kotlin 1.9.24 / AGP 8.2.2；minSdk 23 / compileSdk 34 / versionName 0.1.0(1)。构建一律 `nix develop` 内 `./gradlew`，命令模板与沙箱重定向见 `local/DEV-GUIDE.md` 1.5。
- 工具链拍板（2026-11-03）：纯 Kotlin、无第三方 SDK；拼音引擎自研词组级 + 内置词库；跨屏 HTTP/WS 自研、二维码 ZXing core；一切工具由 `flake.nix` 声明，不手工装 SDK，新工具先进 flake 再用。
- 词库现行口径：AOSP 谷歌拼音词库（65 105 条，词 + 逐字拼音 + 内建频次，Apache-2.0）；源文件入库 + sha256 门禁 + 构建零网络。演变：决策 15（CC-CEDICT，作废）→ 28（换源）→ 29（源文件入库），全文见 PRD 第 12 章。
- 评审口径：双 agent 盲审（`dev` 实现 / `review-qa` 审查，沟通经 lead 中转），同一任务上限 3 轮，非里程碑自动归档（TASKS 6.2-7/9）；交接以消息经 lead 中转，仓库不落 handoff 文件（6.2-10）。
- 待办焦点：T11 更多候选展开（非里程碑，评审通过即自动归档）；真机验证统一走 T12 里程碑。

## 关键决策速查（本文涉及项，全文以 PRD 第 12 章为准）

| 决策 | 一句话 |
| --- | --- |
| 23 | 纯 Kotlin 工程 APK 允许无 `lib/` 目录，ABI 核查视为通过 |
| 24 | Gradle/wrapper 版本以 nix package 为准，文档不写死版本号 |
| 25 | 派单顺序调整：T4 暂缓先派 T5（历史口径） |
| 26 | 已完成任务经真机验证可回溯标「真机已验证」 |
| 27 | 符号键盘键位微调排入全部 30 个全角符号、不含 `.com`/`@`、`designs/` 不改 |
| 28 | 词库换源 AOSP 谷歌拼音词库（CC-CEDICT 选型作废） |
| 29 | 词库源文件入库 + sha256 锁定 + 构建零网络（取代 28 的「源文件不入库」条款） |

## 坑清单（按主题合并；细节与 issue 编号见各 closed issue 对应坑）

### 构建与环境

1. 沙箱构建四件套重定向：HOME / GRADLE_USER_HOME / XDG_CACHE_HOME / `JAVA_TOOL_OPTIONS=-Duser.home`；且 JAVA_TOOL_OPTIONS 必须单独一条 export、写在 HOME 之后——同一 export 语句内展开先于赋值生效（T1/T3，模板已固化 DEV-GUIDE 1.5）。
2. Gradle wrapper 官方域名超时：改腾讯镜像 + `distributionSha256Sum` 钉官方校验和；版本以 nix 包为准（决策 24）（T1）。
3. flake 三坑：SDK 许可经 nixpkgs config `android_sdk.accept_license` 声明（`composeAndroidPackages` 已不收该参数）；SDK 组件 unfree 需 `config.allowUnfree = true`；`nix run` 的 app 脚本 PATH 必须自带 `${sdk}/bin` 且脚本要 `set -euo pipefail`（否则失败还打印成功文案）。
4. 组合 SDK 的镜像目录是符号链接，`find -type d` 看不见，要用 `ls -la`；镜像路径带 `android-` 前缀（flake 批次）。
5. 自研二进制格式字段位宽按数据规模上限先算：SPD1 音节 id 用 uint8 时，416 个音节的 id ≥ 256 被 `writeByte` 静默截断（ting→dun），读写两端均不报错（T9）。
6. 同轮构建中 `processResources` 与 `convertDict` 无依赖边则执行顺序不定，测试可能复制旧产物；JavaExec 任务 classpath 用 `compileClasspath + classesDirs`，用 `runtimeClasspath` 会经 processResources 成环（T9）。
7. JUnit 4 `Assert.fail` 返回 void 非 Kotlin `Nothing`，`?: fail(...)` 的 elvis 推断失效；同包双 top-level `main` 裸调引用歧义——校验逻辑抽命名函数（T30）。
8. 词库转换必须确定性 + sha256 门禁：`verifyDict` 无输出声明每次必跑，防 up-to-date 跳过校验（T30）。
9. app 依赖 `:pinyin` 后两模块同图构建：`mergeDebugAssets` 需显式 `dependsOn(":pinyin:buildDict")`，否则隐式依赖校验失败（T10）；`pinyin/assets` 同时是 JVM resources 与 app assets 源目录，词库产物会双份入包（`assets/dict/` + 根级 `dict/`，各 1.5 MB），待后续任务 resources 排除（T10 评审非阻塞建议）。

### 模拟器走查

1. 模拟器只做逻辑回归：API 34 ≠ 验收机 API 23；不覆盖性能预算、微信内核、真实遥控器。
2. `uiautomator dump` 不含 IME 窗口，键位坐标用截图目测；dump XML 属性顺序 text 在 resource-id/class 之前（T3）。
3. 键帽 `focusableInTouchMode` 首击聚焦不点击：tap 走查丢键，用 `input keyevent` 或双击 tap（T7/T8）。
4. 注入通道偶发丢事件（keyevent/tap 均有）：`ImeKeyTrace` 无记录 = 注入层丢失，重启模拟器实例恢复（T6/T7/T8 多轮复现）。
5. CENTER 键收起延迟 120ms（`hideSelfAfterKeyDispatch`）防宿主 showSoftInput 覆盖，时序常量真机待复核；BACK 在宿主文本选择手柄激活时第一下被宿主消费（T6/T7）。
6. `adb force-stop` IME 包会把系统输入法打回 Gboard：恢复用 `ime set` + BACK + 重 tap 输入框，勿 force-stop（T29）。
7. 新增键盘布局必须同 `columnRhythmMatchesArtboard` 口径加列节奏 x 原点断言：格网列号断言拦不住视觉错位（T28/T29 教训）。
8. 未挂载的测量用 `TextView` 先设 `LayoutParams` 再 `setText`，否则 NPE 崩溃（系统自动重启 IME 恢复，不阻塞宿主）（T10）。
9. 候选行进入口径：仅行 1 键 UP 进候选行（栏区在行 1 上方）；行 2/3/4 先按格网到行 1 再进（T10）。

### 真机走查（前置检查；细节以设备档案为准）

1. 弹键盘前核对 `default_input_method`：`ime set` 后搜狗崩溃反馈会把默认输入法切回搜狗（T4）。
2. 电视空闲自动进屏保，tap 全落空：走查须连贯执行（T4）。
3. 根 Activity BACK 直接退应用；DPAD 唤起后 BACK 只收 IME（T4）。
4. 几何偏移定性前先排除自身工具口径因素（T4 评审纪律：dev 报的「+1 系统性偏移」被证伪为扫描口径差）。

### 词库与数据

1. AOSP 源词库每行第 3 列标志字段语义无官方文档确认，转换忽略（不影响词/频次/拼音三要素）（T9）。
2. 行 4 键帽顶缘 +2px 偏移（容差内、机制未查明）：真机与模拟器跨 API 数据已在 T4 采集，T28 遗留观察项。

## 任务档案（一句话结论；细节见对应 closed issue）

- 阶段 0（2026-10-28~11-03）：PRD v1.1 定稿、设计稿四组定稿、AGENTS.md 定稿、git init。
- Nix flake（2026-11-03，无 issue 档案，坑见上文清单）：JDK 17（Zulu）+ Android SDK + TV 镜像 API 34 + `nix run .#tv-avd/.#tv-emulator`；真实求值验证通过（环境内 JDK/adb/gradle/build-tools/镜像逐项核实）；`flake.lock` 锁 nixos-26.05、必须随批提交；支持 aarch64-darwin / aarch64-linux / x86_64-linux，镜像 ABI 跟随宿主（TV arm64 镜像 API 31 起才有）；不装 NDK/CMake。
- T1 工程骨架（2026-11-04，真机已验证）/ T2 设置页壳（debug 测试输入区 + 全集 Token 化，真机已验证）/ T3 最小 IME（service 注册 + `onEvaluateFullscreenMode` 恒 false + 收起不丢内容，真机已验证）/ T5 星夜青键盘布局（盲审 3 轮闭环，真机已验证）/ T4 真机无线 adb 通道（真机已验证）/ T28 列间隔 66px 口径修复（真机已验证）/ T6 焦点导航基座 / T7 英文完整输入 / T8 符号键盘（决策 27）/ T29 符号键盘列对齐回归修复 / T9 拼音引擎（决策 28）/ T30 词库入库零网络（决策 29）/ T10 中文模式接入 + 候选词栏：全部归档于 `docs/issues/closed/`，状态标注以 TASKS 为准。

## 给下一个 agent（T10 接入相关）

- 键位事件处理全部在 `SofatypeImeService.onKeyDown`：方向键 → FocusNavigator，OK/ENTER → performClick；新增键面行为改 `execute(action)` 分支（T6/T7）。
- 布局切换入口：`SofatypeImeService.switchKeyboardMode` / `rebuildKeys`；新布局须提供 rows + overlayCells（跨行键格位）+ initialCell，12 列 × 4 行与既有布局同构（数字列 col1-3、功能区 col11/12）（T8）。
- 大小写变换入口在模型层：`displayLabel` / `outputValue` / `shiftContentDescription`（T7）。
- IME 服务 `onEvaluateFullscreenMode` 恒 false（防 TV 横屏全屏提取盖住宿主），勿动（T3）。
- 拼音引擎接口见 `:pinyin` 模块与 issue T9；接入时顺带处理评审非阻塞建议（Segmenter 超长防御、多音双桶用例、VerifyDict 常量抽取等，清单见各 issue 评审段）。
- 开工顺序：`AGENTS.md` → `docs/PRD_20261104.md` → `docs/TASKS_20261104.md` → `docs/devices/` → `local/DEV-GUIDE.md` → `designs/`。
