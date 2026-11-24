# Handoff · 开发交接文档

给 agent 和人看的开发注意事项。核心价值：关键决策、踩过的坑、代码接入点。

细节出处（本文不重复）：

- 简版差异：根目录 `CHANGELOG.md`
- 实机参数与坑：`docs/devices/` 设备档案
- 原则与红线：`AGENTS.md`
- 任务制已废止（2026-11-19 起基线冻结、issue 制）：变更状态即 issue 状态（`docs/issues/`）；决策全文：1~78 见 `docs/PRD_20261104.md` 第 12 章（基线已冻结），其后拍板见对应 issue
- 各变更细节：`docs/issues/` 对应 issue（open / closed）

## 维护约定（三处记录分工）

1. **issue（`docs/issues/`）**：单次变更的唯一记录，issue 即需求；编号即全局变更编号。`open/` 进行中，户主验收拍板后挪入 `closed/`。纯文档调整不建 issue。规范见 `docs/issues/README.md`（2026-11-19 修订：TASKS 冻结、任务制废止）。
2. **本文（HANDOFF）**：只留关键决策、踩过的坑、当前状态与待办，细节指向 issue。**不记录工作过程**——干了什么事的过程叙事不进本文（归 issue 与 git 历史）；**已归档（closed）任务的结果不在本文复述**（2026-11-12 户主指令，含真机 PASS 与归档明细）；任务状态只以 bullet 列「已归档 / 进行中 / 未进行」，不写归档原因与过程（2026-11-15 户主指令）；与 issue 重复的内容用指针代替；每次更新顺手收缩，发现膨胀先整理再收笔。
3. **`CHANGELOG.md`**：发版时更新，一版一节，按该版本 issue 汇总。
4. 实机新坑写入对应设备档案；同一事实只在设备档案维护，本文只写指向。
5. 需求 / 设计变更与新拍板一律入对应 issue（issue 即需求），本文决策速查表按 issue 号登记一行。**基线 PRD（`docs/PRD_20261104.md`）已冻结，开发环境工具链拍板不入基线**，记录于本文决策速查与对应 issue。

## 当前快照（截至 2026-11-23）

- 工程基线：仓库根 Gradle 工程，Kotlin 1.9.24 / AGP 8.2.2；minSdk 23 / compileSdk 34 / versionName 0.2.0(2)（T89 起，首版 0.1.0(1)）。构建一律 `nix develop` 内 `./gradlew`。
- 工具链：纯 Kotlin、无第三方 SDK（例外：二维码 ZXing core、日志 Timber 决策 60；测试域第三方测试库经 T91 拍板放开，限 test / androidTest / 插件作用域不进 APK）；拼音引擎自研 + 内置词库；跨屏 HTTP/WS 自研。一切 CLI 工具由 `flake.nix` 声明（新工具先进 flake 再用），`flake.lock` 锁 nixpkgs-unstable、必须随批提交；H5 页调试用本机支持 CDP 的无头浏览器（chrome / chromium / brave / firefox 等）；支持 aarch64-darwin/linux 与 x86_64-linux，TV arm64 镜像 API 31 起；不装 NDK/CMake（坑见构建与环境 3/4）。
- 词库：AOSP 谷歌拼音词库（65 105 条，Apache-2.0），源文件入库 + sha256 门禁 + 构建零网络（决策 28→29）。热词库（T96 内置，T99 频次档位治理，T101 换开源源）：THUOCL 清华开放中文词库（MIT，上游 10 域 DF 前 12000 派生）+ 语监中心 / 咬文嚼字年度词表（事实性榜单，NOTICE 逐源登记），构建期转 SPD1 内置 `assets/dict/hotwords.bin` 并按词长写入档位频次（2~3 字 2500 / ≥4 字 300），运行时按包内频次原样并入（不再 ×10）；逐词库 gradle 开关默认全内置；搜狗 scel（限个人使用条款，公开仓库冲突）与百度两源已出库（git 历史留证）；词库档案与开关用法见 `docs/DICTS.md`。
- 评审口径：双 agent 盲审（`dev` 实现 / `review-qa` 审查，沟通经 lead 中转），同一 issue 上限 3 轮，评审通过经户主验收拍板后归档；交接以消息经 lead 中转，仓库不落交接文件。**证据纪律（T33 起）：交接与验收记录写「已验证」必附对应证据；条件式分支未触发须如实标注，描述与证据失实按评审不通过处理。**
- 变更状态（任务制已废止，以 `docs/issues/` 目录为准）：closed T1~T101 全部归档（T96~T99 真机已验证 2026-11-23；T100/T101 构建 + 单测 + GitHub 双流水线已验证 2026-11-24）；open 无。
- GitHub 通道：`.github/workflows/` 两 workflow——push/PR 到 main 跑 CI（runner 工具链 + Zulu 17，非 flake 通道）；push `v*` tag 直发 Release（门禁 tag=versionName、正文抽 CHANGELOG、unsigned 附件）；发版流程见 `DEVELOP.md`「GitHub CI 与发布」节。应用内「检查更新」默认直连 Releases Atom（T103，无 API 限额、代理可达），设置页「gh-proxy.com 加速」开关默认开，下载地址按 T100 资产命名构造。
- 遗留观察（挂账，不影响功能）：
  - tap 通道双击激活键盘切换存在焦点竞态（激活时点与布局重建窗口竞态，焦点被新布局同坐标键抢走 2 次复现；键码通道零偏差；2026-11-22 T97 review-qa 模拟器实测，与 T97 改动无关）：验收口径不受影响，是否立项评估待户主拍板。
  - 组合态活跃时切入符号键盘再返回，组合串终结为直出文本、候选栏消失（2026-11-18 T83 review-qa 模拟器实测；上层既有行为非 T83 引入，如需打磨另立 issue）。
  - flake tv-emulator 包装不透传参数：`-no-snapshot` 等实未生效，历史「冷启」多为快照恢复（判据：`/proc/uptime` 秒级 = 冷启、数百秒以上 = 快照恢复；明细见 closed/T77，2026-11-15 实测）；透传修复属 flake 改动，待户主拍板。
  - 平台夺回链（坑 5 / 9 同族：自家 Activity 切走沿 / 屏保沿触发平台 force-stop 本 IME 包 → 重选系统默认搜狗）为设备长期事实；应用侧治理 = 设置页检测横幅 + 一键选回恢复通道（T82 closed 2026-11-18，决策 66，明细见 `docs/issues/closed/T82.md`）。
  - 行 4 键帽顶缘 +2px（容差内，坑清单词库与数据 2）。
  - H5 矮视口垂直溢出。
  - 键盘 hide→重弹短间隔视图可能不重建，会话内语言 / 大小写 / 全半角状态延续（FR-A13 P2 记忆边界族，含 UPPER 态收起重弹后首键 OK 输出大写 A；T65 走查复核确认既有边界、非 T65 引入；T66 走查同族确认 keyboardMode 重显残留 SYMBOLS，非缺陷）。
  - WS 配对成功后已连接 UI 切换有秒级延迟（首帧约 3.5s 未切、约 6s 帧已切；renderRemoteUi 未被 T65 触碰，T65 走查发现，如需查证另立 issue）。
  - 换网段子分支与安卓 Chrome 走查户主拍板不测（issue T19）。
  - TestTypingActivity exported=true（沿 T2 走查先例，第三方可显式拉起调试页；2026-11-13 review-qa 建议挂账，当时依托 debug 门控、风险低——决策 65 / T79 起测试打字页 release 同样可达，暴露面随之放开；待走查通道收敛时拍板是否收紧，issue T63 审查记录 + closed/T79）。
  - 远程重显双 show 沿焦点竞争边界（2026-11-16 T79 review-qa 模拟器实测）：`am start` 同应用 Activity 触发双 `onStartInputView`（间隔约 1ms）时，第二沿按 `RemoteReentryFocusPolicy` 设计语义回归退出键焦点，决策 64「首发 OK 不误杀」护栏在该场景失效（拆会话一次、自动重建）；干净单沿场景（T78 口径）PASS；触发条件窄、T79 改动零触碰焦点链，非 T79 回归——如需修复另立 issue 评估。
  - 设置页 BACK finish 退出后 27ms 出现一次新实例即时重建（2026-11-14 T67 真机批次，此后语义全正确；疑似坑 8 家族注入病理 artifact，下次真机批次复测，明细见 closed/T67）。

## 关键决策速查（决策 1~78 全文见基线 PRD `docs/PRD_20261104.md` 第 12 章，已冻结；新条目键 = issue 号，详情入对应 issue）

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
| 55 | 候选翻页落点分向：下翻落当页首、上翻落当页尾（修订决策 53 落点条款） |
| 56 | 字母/符号两键盘最右列（col12）全部按键按[右]直达「远程输入」（修订 FR-A11 右缘表缘不动口径） |
| 57 | 设置页 BACK 二次退出：首击只弹 Toast 提示、2 秒内再击退出、超窗重计 |
| 59 | 远程输入上屏保活：上屏成功不停服可连续上屏（修订 FR-B6/R-4）；新增已连接无活动 10 分钟自动退出（走 FR-B7 通知链） |
| 60 | 调试日志：设置页「日志」子项（开关默认关 / 1-10MB 档位超限轮转 / 查看页浏览·刷新·清空）；Timber 双树（户主指令例外），ImeKeyTrace 门控放宽「debug 或 开关开启」 |
| 62 | 远程模式 BACK 分连接态：已连接只收起键盘保会话（切输入框零扫码），未连接维持 FR-B7 完整退出；显式退出收窄「退出远程」按钮（修订 FR-B7 / 决策 59 退出通道） |
| 63 | 远程服务端口持久复用：首次随机、持久化、使用期复用（修订决策 16 端口随机）——服务 URL origin 跨退出重开稳定，决策 61 保号链真实路径生效 |
| T89 | APK 版本升至 0.2.0(2)：按决策 19 随发布批次递增，工程不再锚定 0.1.0(1)；mock 源 v0.2.0 锚定口径随之失效，刷新另立 issue |
| T90 | 检查更新 mock 源占位符参数化：版本值由 serve_mock.py 启动参数注入（JSON 零硬编码，决策 17 同构）；递增包口径随基线改指 0.3.0(3)，基线包 = 工程当前版本 |
| T91 | 测试体系拆两 issue 立项：集成测试 T91、性能回归 T92；真机操作一律先询问户主；测试域第三方测试库放开（test / androidTest / 插件作用域，不进 APK）——instrumented 层（UiAutomator）+ 黄金图逐像素对比（按设备分目录）+ Paparazzi 视觉回归（2026-11-20 户主拍板转正）；依赖可重现机制（版本目录 + 依赖锁 + SHA-256）2026-11-20 户主拍板整套废止，依赖声明内联坐标；s10 手机端模拟浏览器可换、本地检测优先（chrome > chromium > brave），模拟器 slirp 隧道与浏览器式页面加载不兼容（实测毒化）走 python3 WS 直连，浏览器路径保留真机 |
| T92 | 性能回归 = 方案 A「应用内 debug 门控 PerfTrace 埋点 + 设备侧计时」+ JMH 引擎地板（宿主侧）；判定 = 预算绝对值 + 较基线中位数劣化 ≤20%，断言在设备端 instrument 内；基线入库与重定须拍板；模拟器数据不作性能结论；依赖声明内联坐标（同 T91 废止口径） |
| T93 | 测试工具去 T 编号自描述化命名（`tools/integration/`、`tools/perf/`、Kotlin 类与参数键 / settings marker 同步）；集成 / 性能报告迁仓库根 `reports/`（gitignore 不入库），`screenshots/` 语义不变；根 README 收敛产品页、开发内容迁 `DEVELOP.md`；测试工具 adb home / 构建缓存全走标准环境变量（`ANDROID_USER_HOME` / `ANDROID_AVD_HOME` / `GRADLE_USER_HOME` / `XDG_CACHE_HOME`）纯透传零注入，不发明专用变量；构建产物名带 `sofatype-` 前缀；obscura 出全仓与 flake，H5 调试改通用 CDP 无头浏览器（phone_driver 自动检测 chrome / chromium / brave）（2026-11-20 多轮户主拍板） |
| T94 | 词包插件机制（词库外热词/新词）：词包 = SPD1 数据包 + manifest，设置页勾选、手动更新（自动更新不做）；分发 URL 构建期固定 GitHub Release——GitHub（Release + Actions）第三方服务例外入册（ZXing/Timber 先例同款）；下载匿名 GET 不带设备标识与输入内容，输入处理仍全本地；候选装载仍仅由组合态触发（决策 42 不变）。**2026-11-22 户主拍板 DROP，被 T96 替代后关闭** |
| T95 | 词包流水线自动化：scel（自带拼音免注音）/ 百度热搜端点自动筛词（pypinyin 注音、查主词库去重；2026-11-22 拍板废止人工筛词）→ SPD1 + manifest → GitHub Release 发布（workflow 现仅手动触发，定时周期待拍板）；scel 版权条款限个人使用，户主拍板私人家用接受、产物不对外分发；转换确定性（重跑字节稳定）。**发布链路部分 2026-11-22 随 T96 废止；转换器资产留作 T96 复用评估** |
| T96 | 热词内置离线化（2026-11-22 拍板）：DROP T94/T95 词包下载与 GitHub 发布路线；搜狗「网络流行新词」周更库 + 百度热搜词构建期内置 APK（不含天文词），随 app 更新，无设置挑选、用户无感知、零外部词库地址依赖；词源入库 + sha256 门禁 + 构建零网络沿决策 28/29。同日追加拍板：①热词目标为历年累积库（app 年更）；②新建 `docs/DICTS.md` 词库档案；③网络公开长尾热词资源一并整合、逐源评估取舍；④gradle 构建期逐词库开关，默认全内置。合并形态评估结论：主词库去重词 64 406 逼近 SPD1 词 id uint16 上限（65 535），热词必须走独立第二数据包（运行时 DictHotMerge 加权 ×10 并入，沿 T94 实测系数）。**2026-11-22 Round 1 实施并 review-qa 审查 PASS（模拟器已验证），户主验收归档；2026-11-23 真机已验证（含批次修复 putIfAbsent 崩溃）** |
| T97 | 键盘交互三口径（2026-11-22 拍板）：①组合串非字母段退格逐段删净（新增分支：光标前为空且光标后有余文时删光标后 1 字符；组合态仍逐字母删）；②行 4 ←/→ 维持光标移动语义、键面/行为/朗读三者一致，不改键位；③`[123_!]` ↔ `[返回]` 布局切换焦点双向互指，切换保留组合串上屏（内容不丢）。**2026-11-22 Round 1 实施并 review-qa 审查 PASS（模拟器已验证），户主验收归档；2026-11-23 真机已验证** |
| T98 | 键面字号定稿（2026-11-22 拍板 A 方案，仅放大不加粗）：字母/数字/符号键 30、功能键 24（中英 / 123_! / 全半 标签 20 拉齐边距）、空格 24、完成 24、键内图标 30；完成键格位 left:1282/宽 96、符号板空格键 left:1176 按分词列栅格对齐。设计稿已全量回写 `designs/20261114`（键盘板 1/2/3/4/10/11/12/13，对比板 A/B/C 已删）。**2026-11-23 Round 1 实施并 review-qa 审查 PASS（模拟器已验证），户主验收归档；2026-11-23 真机三态抽查已验证** |
| T99 | 词库质量治理（2026-11-23 拍板）：简拼命中顺序目标 = 常用词 → 短热词（2~3 字）→ 长尾词（4 字以上）；百度实时热搜榜（baidu-realtime）停止并入（新闻标题片段非词、时效差）；热词不再「原始频次 ×10」，按词长分档重写绝对频次档位。实证：热词 ×10 后中位频次 42 720 压过主词库 99%+ 词条、简拼 top20 热词占 86%、4~6 字长尾频次反而高于短热词（探针 `reports/dict-quality-probe/`，不入库）。**2026-11-23 Round 1 实施并 review-qa 审查 PASS（模拟器已验证 + 真机已验证），户主验收归档** |
| T100 | GitHub CI/CD（2026-11-24 拍板）：仓库推 GitHub 公开仓库；CI 走方案 B（ubuntu runner 预装工具链 + Zulu 17，CI 不装 nix，flake 不动），任务面 assembleDebug + 双模块单测 + assembleRelease，不含视觉回归与模拟器测试；Release = push `v*` tag 触发，门禁 tag=versionName，正文抽 CHANGELOG 版本节，unsigned 直发（无草稿），版本仍人工维护（决策 19 不修订）；本地 act 模拟实跑证实不可达（amd64 模拟层挂载卷 syscall 缺口 / aapt2 仅 x86_64-linux）后二次拍板废止，workflow 无本地模拟分支，验证依赖真实推送；GitHub（Release + Actions）第三方服务例外重新入册（T94 先例同款）。**2026-11-24 CI 与 Release 双流水线 GitHub 侧已验证（CI 7m40s 绿；v0.2.1 直发 Release 4m29s 绿），户主授权发版后归档** |
| T101 | 热词源合规化（2026-11-24 拍板，公开仓库前置）：删搜狗 scel（官方条款限个人使用，公开再分发冲突）与百度沸点 / 实时榜（无单独许可灰源），热词开放源换 THUOCL 清华开放中文词库（MIT，许可原件已核验）——上游 10 域 DF 前 12000 派生 `thuocl-open.txt` 入库（确定性派生，重跑字节一致）；语监 / 咬文嚼字事实性榜单保留；运行时零改动；选型对照 Rime 生态（开放源 + 逐源登记许可，商用词库数据不随发行物分发）；NOTICE 补热词源登记欠账。**2026-11-24 构建 + 单测 + 模拟器已验证，户主预授权归档；jieba/腾讯加源评估后户主拍板不做（KB 级约束）** |

## 坑清单（按主题合并；细节见对应 issue）

### 构建与环境

1. 沙箱构建四件套重定向（HOME / GRADLE_USER_HOME / XDG_CACHE_HOME / `JAVA_TOOL_OPTIONS=-Duser.home`）；JAVA_TOOL_OPTIONS 必须单独一条 export、写在 HOME 之后——同一 export 语句内展开先于赋值生效（T1/T3）。
2. Gradle wrapper 官方域名超时：腾讯镜像 + `distributionSha256Sum` 钉官方校验和；版本以 nix 包为准（决策 24）（T1）。
3. flake 三坑：SDK 许可走 nixpkgs config `android_sdk.accept_license`；unfree 组件需 `allowUnfree = true`；`nix run` 的 app 脚本 PATH 自带 `${sdk}/bin` 且要 `set -euo pipefail`（否则失败还打印成功文案）。
4. 组合 SDK 镜像目录是符号链接，`find -type d` 看不见，用 `ls -la`；镜像路径带 `android-` 前缀。
5. 自研二进制字段位宽按数据规模上限先算：SPD1 音节 id 用 uint8，≥256 被 `writeByte` 静默截断（T9）。
6. `processResources` 与 `convertDict` 同轮无依赖边则顺序不定，测试可能复制旧产物；JavaExec classpath 用 `compileClasspath + classesDirs`（`runtimeClasspath` 成环）（T9）。
7. JUnit4 `Assert.fail` 返回 void 非 `Nothing`，`?: fail(...)` 的 elvis 推断失效；同包双 top-level `main` 裸调歧义——校验逻辑抽命名函数（T30）。
8. 词库转换确定性 + sha256 门禁：`verifyDict` 无输出声明每次必跑，防 up-to-date 跳过校验（T30）。
9. 两模块同图构建：`mergeDebugAssets` / `mergeReleaseAssets` 均须显式 `dependsOn(":pinyin:buildDict")`（T10/T32）；`pinyin/assets` 双源目录致词库产物双份入包，待 resources 排除（T10 遗留非阻塞建议）。
10. 分页模型区间坐标系（T11）：`CandidateMoreRow.paginate` 区间为相对坐标系的旧坑已随 T66 重构消除——paginate 与「更多⌄」体系整体删除，`CandidatePager.kt` 页区间即候选全局下标；新代码勿再引入相对/全局两套坐标系。
11. 单测取证防旧结果：`--rerun` 在本工具链实测静默失效（up-to-date 跳过、假绿），口径统一 `--rerun-tasks` 强制实跑，核对任务全 executed + test-results XML 时间戳（T32/T45）。
12. merge/package 类任务输入未变则 UP-TO-DATE：「实跑取证」须删 `app/build/intermediates/assets/<variant>/` 后复跑；release 产物名 `sofatype-release-unsigned.apk`（无 signingConfig；T32 口径，产物名前缀随 archivesName 改为 sofatype-）。
13. `testDebugUnitTest` 只覆盖 Android 模块（:app），纯 JVM 模块 `:pinyin` 单独 `:pinyin:test` 补跑（T15）。
14. debug 构建 StrictMode 查主线程网络 IO（death penalty）：WS 回发等任何 socket 写不得落主线程——帧写统一下沉专用线程（ConnectionWriter 模式），时序依赖用「同步等待写完」保住（T16，细节见 issue T16 坑 1）。
15. 新建 drawable 与消费点同轮核对引用：grep 资源名可检出「创建未挂载」资源，视觉走查对底/描边缺失类缺陷不敏感（T17，R1 评审抽实发现）。
16. API 24+ 集合方法（`putIfAbsent` / `compute*` 等）在 minSdk 23 真机运行时必抛 NoSuchMethodError，模拟器 API 34 与 JVM 单测均不可暴露：新代码禁用，热词合并链路已中招已修（T96 真机批次实证）。

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
19. 模拟器 tap 触摸通道收起键盘不生效（组合落定正常、requestHideSelf 未生效；键码通道同路径正常，既有问题非回归）：走查收起一律键码通道；隐藏态判定不单凭 `dumpsys` 的 `mInputShown`（宿主无输入会话时不可靠），以截图 + ImeKeyTrace 为准（T59 review-qa 复验发现，见 closed/T59 走查发现）。
20. 模拟器窗口帧可能滞留旧键面（渲染层，逻辑层无恙）：T64「行步进后全/半切换」场景实证——视图树/状态/输出全部正确（诊断 trace + 焦点探针 + 输出值均新键位），窗口帧滞留旧页键面，`invalidate()` 亦不恢复，offset=0 场景与步进本身的 rebuild 不复现；真机已定性（2026-11-13 MiTV4 复验：同路径双向切换零滞留——API 34 模拟器渲染管线 gfxstream 缺陷，非应用缺陷，模拟器口径规避即可，T64 坑 1）。截图判读键面字符时，先用 ImeKeyTrace 焦点探针核对逻辑焦点键位，勿单凭截图断言状态错乱（T64 Round 1）。
21. 模拟器 slirp 隧道与浏览器式页面加载不兼容：浏览器页面单次主文档导航即触发连接级毒化且不可恢复（redir 同端口重建、换端口、关页均救不回，python3 WS 随后同样被 reset；T91 隔离实测，浏览器隔离下页面 WS 本身 2 秒直连正常）。测试用 H5 驱动在模拟器一律走 python3 WS 直连等价通路，浏览器优先路径保留真机 LAN 直连（T91，通道与降级态每轮入报告）。

### 真机走查（前置检查；细节以设备档案为准）

1. 弹键盘前核对 `default_input_method`：`ime set` 后搜狗崩溃反馈会切回搜狗（T4）。
2. 电视空闲自动进屏保，tap 全落空：走查须连贯执行（T4）。
3. 根 Activity BACK 直接退应用；DPAD 唤起后 BACK 只收 IME（T4）。
4. 几何偏移定性前先排除自身工具口径因素（T4 评审纪律）。
5. 平台安装授权判定读 global 位（UI 开关写 secure，两处不一致，户主拍板 global=1 长期保持）；注入病理窗口（幻影键/迟到副本/事件沉浮层）会污染直落/双写类走查观测——细节见设备档案坑 15 与坑 8 补充（T27）。
6. 系统安装器包确认界面只吃 tap 不吃键码（遥控/adb 键码全死），双钮两段式交互（首 tap 移焦、再 tap 激活；初始带焦点钮首 tap 即激活），小米形态含「未经过安全审核」红色警告——安装器走查按户主意图 tap 代操作并报备（T46/T47/T51 真机段，设备档案坑 16）。
7. `adb install` 传输完成后电视端弹安装许可：agent 先远程 `input tap` 代点「确定 / 继续安装」（键码无效、双钮两段式，设备档案坑 16/17），代点无效再通知户主手动确认；安装段时长取决于确认速度，等待期不盲试重试（T50 复验 + 2026-11-11 户主修订流程，设备档案坑 17）。
8. `/proc` 端口表读数偶发假阴性（监听在报 0 条）：同参数重读即恢复，「无监听」类结论必须双读 + 双表（tcp6/tcp）交叉验证；设备占端口通道 = `/system/xbin/busybox` nc 且占用与走查序列须同一条 adb shell 会话内执行（2026-11-16 T74/T75 真机批次，设备档案坑 12 补充）。

### 词库与数据

1. AOSP 源词库每行第 3 列标志字段语义无官方文档确认，转换忽略（不影响词/频次/拼音三要素）（T9）。
2. 行 4 键帽顶缘 +2px 偏移（容差内、机制未查明）：真机与模拟器跨 API 数据已在 T4 采集，遗留观察项（T28）。

## 给下一个 agent · 代码接入速查

- 键位事件处理全部在 `SofatypeImeService.dispatchKeyDown` / `onKeyUp`：方向键 → FocusNavigator，OK/回车显示态 → performClick + T38 两窗；隐藏态确认键 = `confirmWakeSessionActive()` 门控（会话活跃才唤起，T86 分层兜底；无会话透传宿主 DOWN/UP 对称，T88 · 决策 69）；BACK/方向/打字键码隐藏态透传宿主（T59）。新增键面行为改 `execute(action)` 分支（T6/T7）。
- 布局切换入口：`SofatypeImeService.switchKeyboardMode` / `rebuildKeys`；新布局须提供 rows + overlayCells（跨行键格位）+ initialCell，12 列 × 4 行与既有布局同构（数字列 col1-3、功能区 col11/12）（T8）。
- 大小写变换入口在模型层：`displayLabel` / `outputValue` / `shiftContentDescription`（T7）；键面两态渲染（英文小写/大写键面）同在 `KeyboardLayout` 模型层（T66）。
- 候选栏翻页模型在 `CandidatePager.kt`：页区间 = 候选全局下标，箭头装饰不进焦点链（T66；「更多⌄」展开体系已删除，FR-A15 废止）。
- IME 服务 `onEvaluateFullscreenMode` 恒 false（防 TV 横屏全屏提取盖住宿主），勿动（T3）。
- 拼音引擎接口见 `:pinyin` 模块（issue T9）；跨屏服务接口见 `remote/` 包（issue T13/T14/T15）。
- 集成测试接入点：instrument 场景集在 `app/src/androidTest/java/com/sofatype/ime/test/`（harness `IntegrationTestHarness.kt` + 场景套件 `InstrumentedScenarioSuite.kt`，黄金图按设备目录 `assets/goldens/<device-api>/`），宿主 wrapper `tools/integration/`（README 含分步纪律与 `--scenario` 定向通道）；视觉回归 `KeyboardVisualRegressionTest`（Paparazzi，已转正）（T91）。
- 开工顺序：`AGENTS.md` → `docs/issues/README.md`（规范与模板）→ 基线 `docs/PRD_20261104.md`（按需查阅，先核对第 12 章修订链）→ `docs/devices/` → `designs/`。
| T102 | 检查更新接真实 GitHub Releases latest（2026-11-24 拍板）：默认 endpoint 填入 api.github.com latest、解析器顶层兼容单对象形状、debug 注入通道扩展。**随 T103 架构调整被取代关闭（API latest 依赖移除），产出的数组兼容、单测与注入通道扩展保留** |
| T103 | 检查更新切 Atom + 构造下载 + 代理前缀（2026-11-24 拍板，面向 GitHub 加速代理场景）：检查源默认 releases.atom（首个 entry = 最新发布，无 API 限额，gh 代理实测可达）；下载地址按 T100 资产命名口径构造并可叠加代理前缀；设置页「gh-proxy.com 加速」复选框默认开（SharedPreferences 持久化）；debug 注入扩展 EXTRA_ATOM、serve_mock 支持 .xml Atom 形状（仅供检查/发现新版/UI 呈现段走查）；api.github.com 依赖移除（T102 取代关闭）。**验证：单测 + 链路集成 + 模拟器 mock Atom 全链（NEWER 态 UI 与失败态 UX 截图留证）；真实源真网直连验证沿下一真机批次（本机代理出口受 GitHub 网页侧限制）** |
