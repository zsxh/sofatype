# Handoff · 开发交接文档

给 agent 和人看的开发注意事项。核心价值：关键决策、踩过的坑、代码接入点。

细节出处（本文不重复）：

- 简版差异：根目录 `CHANGELOG.md`
- 实机参数与坑：`docs/devices/` 设备档案
- 原则与红线：`AGENTS.md`
- 任务状态与明细：`docs/TASKS_*.md`；决策全文：`docs/PRD_*.md` 第 12 章；环境与命令模板：`local/DEV-GUIDE.md`
- 各任务细节：`docs/issues/` 对应 issue（open / closed）

## 维护约定（三处记录分工）

1. **issue（`docs/issues/`）**：单次变更的唯一详细记录，与 TASKS 任务同名同编号；`open/` 进行中，户主验收拍板后挪入 `closed/`。纯文档调整不建 issue。规范见 `docs/issues/README.md`。
2. **本文（HANDOFF）**：只留关键决策、踩过的坑、当前状态与待办，细节指向 issue。**不记录工作过程**——干了什么事的过程叙事不进本文（归 issue 与 git 历史）；与 TASKS / issue 重复的内容用指针代替；每次更新顺手收缩，发现膨胀先整理再收笔。
3. **`CHANGELOG.md`**：发版时更新，一版一节，按该版本 issue 汇总。
4. 实机新坑写入对应设备档案；同一事实只在设备档案维护，本文只写指向。
5. 需求 / 设计变更追加到 PRD 第 12 章，编号顺延、不重排。**开发环境工具链拍板不入 PRD**，记录于本文决策速查与对应 issue。

## 当前快照（截至 2026-11-11）

- 工程基线：仓库根 Gradle 工程，Kotlin 1.9.24 / AGP 8.2.2；minSdk 23 / compileSdk 34 / versionName 0.1.0(1)。构建一律 `nix develop` 内 `./gradlew`，命令模板见 `local/DEV-GUIDE.md` 1.5。
- 工具链：纯 Kotlin、无第三方 SDK（例外：二维码 ZXing core）；拼音引擎自研 + 内置词库；跨屏 HTTP/WS 自研。一切工具由 `flake.nix` 声明（新工具先进 flake 再用），`flake.lock` 锁 nixpkgs-unstable、必须随批提交；devShell 含 Obscura 无头浏览器（H5 CDP 调试，见 T34）；支持 aarch64-darwin/linux 与 x86_64-linux，TV arm64 镜像 API 31 起；不装 NDK/CMake（坑见构建与环境 3/4）。
- 词库：AOSP 谷歌拼音词库（65 105 条，Apache-2.0），源文件入库 + sha256 门禁 + 构建零网络（决策 28→29）。
- 评审口径：双 agent 盲审（`dev` 实现 / `review-qa` 审查，沟通经 lead 中转），同一任务上限 3 轮，评审通过经户主验收拍板后归档；交接以消息经 lead 中转，仓库不落交接文件。**证据纪律（T33 起）：交接与验收记录写「已验证」必附对应证据；条件式分支未触发须如实标注，描述与证据失实按评审不通过处理。**
- 任务状态（逐任务以 TASKS 状态行为准，明细见对应 issue）：
  - T1~T53 已收口归档（T49/T50/T51/T52/T53 均真机复验 PASS 后归档，2026-11-11 户主拍板；T49 不再列发版已知问题）。
  - T52 已收口归档（2026-11-11）：修复口径=决策 41「符号键盘保留候选栏占位」；designs/20261110 符号板已按决策 41 回写（补空态占位带、键区顶与字母板一致）。
  - T53 已收口归档（2026-11-11）：修复口径=决策 42「候选栏占位全模式恒定」；真机复验四象限键区顶全 580 spread=0、英文两象限 510→580 修复签名实证；口径差异 2 条仅留 issue 挂账（明细见 closed/T53）。
  - T54 / T59 进行中（2026-11-11）：dev-review 闭环模拟器口径 PASS（T54 Round 2 设置页焦点接管＋pre-IME 分路；T59 Round 1 隐藏态键码七条口径落地），真机复验均经户主拍板留待真机批次；T59 改动 4 文件在工作区未提交（T54 已提交 `e9cb94e`）；明细见 open/T54、open/T59。
  - 首版终验已通过（2026-11-10，批次明细见 closed/T27「重启批次记录」）。
- 设备态：sofatype 已装（T53 包，lastUpdateTime 2026-11-11 15:12:40）＋默认 IME（2026-11-11 T52 真机复验后户主拍板保留现状，T53 复验装机后维持）；screenshots 处置由户主自理。
- 遗留观察（挂账，不影响功能）：
  - 行 4 键帽顶缘 +2px（容差内，坑清单词库与数据 2）。
  - H5 矮视口垂直溢出。
  - UPPER 态收起重弹后首键 OK 输出大写 A（FR-A13 P2 记忆边界）。
  - 换网段子分支与安卓 Chrome 走查户主拍板不测（issue T19）。

## 关键决策速查（全文以 PRD 第 12 章为准）

| 决策 | 一句话 |
| --- | --- |
| 23 | 纯 Kotlin 工程 APK 允许无 `lib/` 目录，ABI 核查视为通过 |
| 24 | Gradle/wrapper 版本以 nix package 为准，文档不写死版本号 |
| 26 | 已完成任务经真机验证可回溯标「真机已验证」 |
| 27 | 符号键盘键位微调排入全部 30 个全角符号、不含 `.com`/`@`、`designs/` 不改 |
| 28 | 词库换源 AOSP 谷歌拼音词库（CC-CEDICT 选型作废） |
| 29 | 词库源文件入库 + sha256 锁定 + 构建零网络（取代 28 的「源文件不入库」条款） |
| 30 | 候选栏无常亮高亮，高亮仅表达遥控器焦点态（FR-A7 首候选高亮条款废止） |
| 31 | TV 远程大字区自动折行（FR-B5 TV 端横滚取末尾废止），超出高度纵向滚动保持末行 + 光标 |
| 32 | 跨屏副卡整卡面切换（引导卡 / 配对卡；已连接形态见 35），等待态状态行落主卡（FR-B9 × 决策 4 冲突裁定） |
| 34 | hello.deviceName 会话内存驻留用于状态行展示（仅内存、停服即清、不持久化、不入日志） |
| 35 | 已连接副卡 = 二维码卡保留 + 网址行原位换绿点状态行「已连接 · 设备名」（部分推翻 32 的独立已连接卡）；token 滚动签发（逐 token 一次性不变），新设备配对自动顶替旧连接（replaced + 4002，FR-B11 提前）；已配对连接断开回等待扫码卡 |
| 36 | 检查更新走 UI 渠道（系统安装器），返回设置页按版本号变化判定安装结果；会话通道不作本机更新主链路 |
| 37 | T39 评估：默认输入法回退 = 平台强杀 × AOSP 6 force-stop 原生重置（真实主场景不回退）；挂账观察，不立项拆进程 / 自动写回 |
| 38 | 首版收口口径：所有 closed 任务均已通过真机验证或验收无需真机（T20/T24 不再追加真机义务） |
| 39 | FR-D4 安装确认去重：下载完成直转系统安装器，应用内确认框废止（修订 36 确认框口径）；失败可重试与 versionCode 判定链不变 |
| 40 | 设置页版本行只显示 v<versionName>，versionCode 不对用户展示（仅内部判定链用） |

## 坑清单（按主题合并；细节见对应 issue 与 DEV-GUIDE）

### 构建与环境

1. 沙箱构建四件套重定向（HOME / GRADLE_USER_HOME / XDG_CACHE_HOME / `JAVA_TOOL_OPTIONS=-Duser.home`）；JAVA_TOOL_OPTIONS 必须单独一条 export、写在 HOME 之后——同一 export 语句内展开先于赋值生效；模板固化 DEV-GUIDE 1.5（T1/T3）。
2. Gradle wrapper 官方域名超时：腾讯镜像 + `distributionSha256Sum` 钉官方校验和；版本以 nix 包为准（决策 24）（T1）。
3. flake 三坑：SDK 许可走 nixpkgs config `android_sdk.accept_license`；unfree 组件需 `allowUnfree = true`；`nix run` 的 app 脚本 PATH 自带 `${sdk}/bin` 且要 `set -euo pipefail`（否则失败还打印成功文案）。
4. 组合 SDK 镜像目录是符号链接，`find -type d` 看不见，用 `ls -la`；镜像路径带 `android-` 前缀。
5. 自研二进制字段位宽按数据规模上限先算：SPD1 音节 id 用 uint8，≥256 被 `writeByte` 静默截断（T9）。
6. `processResources` 与 `convertDict` 同轮无依赖边则顺序不定，测试可能复制旧产物；JavaExec classpath 用 `compileClasspath + classesDirs`（`runtimeClasspath` 成环）（T9）。
7. JUnit4 `Assert.fail` 返回 void 非 `Nothing`，`?: fail(...)` 的 elvis 推断失效；同包双 top-level `main` 裸调歧义——校验逻辑抽命名函数（T30）。
8. 词库转换确定性 + sha256 门禁：`verifyDict` 无输出声明每次必跑，防 up-to-date 跳过校验（T30）。
9. 两模块同图构建：`mergeDebugAssets` / `mergeReleaseAssets` 均须显式 `dependsOn(":pinyin:buildDict")`（T10/T32）；`pinyin/assets` 双源目录致词库产物双份入包，待 resources 排除（T10 遗留非阻塞建议）。
10. 分页模型区间是相对坐标系：`CandidateMoreRow.paginate` 返回区间勿直用作候选全局下标（T11）。
11. 单测取证防旧结果：`--rerun` 在本工具链实测静默失效（up-to-date 跳过、假绿），口径统一 `--rerun-tasks` 强制实跑，核对任务全 executed + test-results XML 时间戳（T32/T45）。
12. merge/package 类任务输入未变则 UP-TO-DATE：「实跑取证」须删 `app/build/intermediates/assets/<variant>/` 后复跑；release 产物名 `app-release-unsigned.apk`（T32）。
13. `testDebugUnitTest` 只覆盖 Android 模块（:app），纯 JVM 模块 `:pinyin` 单独 `:pinyin:test` 补跑（T15）。
14. debug 构建 StrictMode 查主线程网络 IO（death penalty）：WS 回发等任何 socket 写不得落主线程——帧写统一下沉专用线程（ConnectionWriter 模式），时序依赖用「同步等待写完」保住（T16，细节见 issue T16 坑 1）。
15. 新建 drawable 与消费点同轮核对引用：grep 资源名可检出「创建未挂载」资源，视觉走查对底/描边缺失类缺陷不敏感（T17，R1 评审抽实发现）。

### 模拟器走查

1. 模拟器只做逻辑回归：API 34 ≠ 验收机 API 23；不覆盖性能预算、微信内核、真实遥控器。
2. `uiautomator dump` 不含 IME 窗口，键位坐标截图目测；dump XML 属性顺序 text 在 resource-id/class 之前（T3）。
3. 键帽 `focusableInTouchMode` 首击聚焦不点击：用 `input keyevent` 或双击 tap（T7/T8）。
4. 注入通道偶发丢事件：`ImeKeyTrace` 无记录 = 注入层丢失，重启模拟器实例恢复（T6/T7/T8）。
5. 收起-唤起时序竞争已由 T38 双抑制窗加固：收起后显示抑制窗 veto 迟到 show 请求、唤起首确认键吞一次即解除（真机 veto/吞键 trace 实证；原「120ms 延迟防覆盖」口径作废，真机竞争窗口实测 >120ms）；BACK 在宿主文本选择手柄激活时首下被宿主消费（T6/T7）。
6. 勿 `force-stop` IME 包（会把默认输入法打回 Gboard）：恢复 `ime set` + BACK + 重 tap 输入框（T29）。
7. 新增键盘布局必须同 `columnRhythmMatchesArtboard` 口径加列节奏 x 原点断言：格网列号断言拦不住视觉错位（T28/T29）。
8. 未挂载的测量先设 `LayoutParams` 再 `setText`，否则 NPE（系统自动重启 IME 恢复，不阻塞宿主）（T10）。
9. 候选行进入口径：仅行 1 键 UP 直接进候选行；行 2/3/4 先按格网回行 1 再进（T10）。
10. 候选区焦点回归只能单点触发，收起路径不自行回归，统一由调用方判定（T11）。
11. 新装应用 stopped 态不收隐式广播（静默跳过）：先 `am start` 一次，再 `-n` 显式组件定向广播（T13）。
12. 服务监听条目在 `/proc/net/tcp6` 呈 IPv4 映射形态（`::ffff:<ipv4>`），纯 `/proc/net/tcp` 表无条目（T13）。
13. `adb emu redir` 并发连接约半数轮次单连接 reset（slirp 缺陷）：探针串行或模拟器内部直连（T13）。
14. debug 广播显式组件包段一律用 applicationId（`com.sofatype.ime`）：写成源集路径（`com.sofatype.ime.debug/...`）则 `am broadcast` 静默不派发（result=0 无报错），极易误判（T15）。
15. 冷启 `ime set` 后立即 tap 唤不起键盘（`mInputShown=false`）：tap 后核对 `dumpsys input_method` 再取证（T15）。
16. 无头浏览器残留报错页 tab 会在 redir 就绪后自动重载，抢先消费一次性 token：换 token 前 `/json/close` 清尽残留 tab；redir 端口随服务重启逐轮重加（T15）。
17. 走查脚本 HOME 重定向后 `$HOME` 不再指向真实用户目录：外部程序（无头浏览器等）路径一律绝对路径，否则 exit 127 静默失败（T33）。
18. 注入通道两坑：硬件键码 62 在组合态下是向组合串插空格（R-2 绑定空格键面，非键码）；键码 67 在 <1.5s 注入节奏下 IC 更新会丢，≥2s 节奏或每键穿插 dump 同步则 20/20 可靠——采样循环用候选提交复位（T36）。
19. 模拟器 tap 触摸通道收起键盘不生效（组合落定正常、requestHideSelf 未生效；键码通道同路径正常，既有问题非回归）：走查收起一律键码通道；隐藏态判定不单凭 `dumpsys` 的 `mInputShown`（宿主无输入会话时不可靠），以截图 + ImeKeyTrace 为准（T59 review-qa 复验发现，见 issue open/T59 走查发现）。

### 真机走查（前置检查；细节以设备档案为准）

1. 弹键盘前核对 `default_input_method`：`ime set` 后搜狗崩溃反馈会切回搜狗（T4）。
2. 电视空闲自动进屏保，tap 全落空：走查须连贯执行（T4）。
3. 根 Activity BACK 直接退应用；DPAD 唤起后 BACK 只收 IME（T4）。
4. 几何偏移定性前先排除自身工具口径因素（T4 评审纪律）。
5. 平台安装授权判定读 global 位（UI 开关写 secure，两处不一致，户主拍板 global=1 长期保持）；注入病理窗口（幻影键/迟到副本/事件沉浮层）会污染直落/双写类走查观测——细节见设备档案坑 15 与坑 8 补充（T27）。
6. 系统安装器包确认界面只吃 tap 不吃键码（遥控/adb 键码全死），双钮两段式交互（首 tap 移焦、再 tap 激活；初始带焦点钮首 tap 即激活），小米形态含「未经过安全审核」红色警告——安装器走查按户主意图 tap 代操作并报备（T46/T47/T51 真机段，设备档案坑 16）。
7. `adb install` 传输完成后电视端弹安装许可：agent 先远程 `input tap` 代点「确定 / 继续安装」（键码无效、双钮两段式，设备档案坑 16/17），代点无效再通知户主手动确认；安装段时长取决于确认速度，等待期不盲试重试（T50 复验 + 2026-11-11 户主修订流程，设备档案坑 17）。

### 词库与数据

1. AOSP 源词库每行第 3 列标志字段语义无官方文档确认，转换忽略（不影响词/频次/拼音三要素）（T9）。
2. 行 4 键帽顶缘 +2px 偏移（容差内、机制未查明）：真机与模拟器跨 API 数据已在 T4 采集，遗留观察项（T28）。

## 给下一个 agent · 代码接入速查

- 键位事件处理全部在 `SofatypeImeService.onKeyDown`：方向键 → FocusNavigator，OK/ENTER → performClick；新增键面行为改 `execute(action)` 分支（T6/T7）。
- 布局切换入口：`SofatypeImeService.switchKeyboardMode` / `rebuildKeys`；新布局须提供 rows + overlayCells（跨行键格位）+ initialCell，12 列 × 4 行与既有布局同构（数字列 col1-3、功能区 col11/12）（T8）。
- 大小写变换入口在模型层：`displayLabel` / `outputValue` / `shiftContentDescription`（T7）。
- IME 服务 `onEvaluateFullscreenMode` 恒 false（防 TV 横屏全屏提取盖住宿主），勿动（T3）。
- 拼音引擎接口见 `:pinyin` 模块（issue T9）；跨屏服务接口见 `remote/` 包（issue T13/T14/T15）。
- 开工顺序：`AGENTS.md` → `docs/PRD_*.md` → `docs/TASKS_*.md` → `docs/devices/` → `local/DEV-GUIDE.md` → `designs/`。
