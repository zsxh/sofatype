# AGENTS.md · Agent 工作指引

本仓库是 **sofatype 输入法**：Android TV 输入法（IME）+ 跨屏输入（TV 出二维码、手机 H5 远程打字）+ TV 设置页。
当前状态：工程已创建，v1.2 施工进行中（进度见 `docs/TASKS_*.md` 与 `docs/HANDOFF.md` 当前快照）；PRD 与设计稿已评审定稿。任何 agent 在本仓库工作前，先读完本文件，再读完 `local/` 下全部文件——**动手之前必须先看 `local/`，这是硬性前置步骤**（详见文档原则）。

## 一、项目目录结构

```text
sofatype/
├── AGENTS.md               # Agent 工作指引（本文件）
├── CHANGELOG.md            # 简版发布记录：新增 / 变更 / 修复
├── docs/                   # 项目文档
│   ├── PRD_*.md            # 需求唯一来源（按需求批次建档，如 PRD_20261104.md）
│   ├── HANDOFF.md          # 开发交接：当前快照、关键决策速查、坑清单、代码接入点
│   ├── TASKS_*.md          # 施工任务拆解：任务、验收点、状态（按需求批次建档，如 TASKS_20261104.md）
│   ├── issues/             # 变更记录：一个任务一个 issue，open / closed 分目录
│   └── devices/            # 设备档案，一机一档
│       └── MiTV4-ANSM0.md  # 验收实机档案
├── local/                  # 本地文件，不提交（签名密钥、IP、开发指南）
│   └── DEV-GUIDE.md        # 本地开发指南：开发环境问题唯一记录处
└── designs/                # 设计稿定稿，只作视觉参考
    ├── tv-ime-crossscreen/ # TV 键盘 · 跨屏输入
    ├── phone-remote-input/ # 手机远程输入页
    ├── ime-settings/       # 输入法设置页
    └── color-schemes/      # 三端配色方案
```

- 创建位置（仓库根）与工具链（Kotlin + Nix flake，见 HANDOFF 当前快照与 PRD 第 12 章）已拍板；开工须用户明确指令，施工计划见 `docs/TASKS_*.md`。
- `local/`（本地文件：签名密钥、IP、本地开发指南）不提交。开发环境问题只在 `local/DEV-GUIDE.md` 记录；git 管理的其余文档不记录、不指向其内容，本条是唯一指向。

## 二、文档原则

- **当前处理批次**：PRD = `docs/PRD_20261104.md`；TASKS = `docs/TASKS_20261104.md`。引用 PRD / TASKS 时一律用 `docs/PRD_*.md` / `docs/TASKS_*.md` 通配符；新建需求批次时更新本行。
- **PRD（`docs/PRD_*.md`）是需求的唯一来源**：动手前先读当前处理批次；实现范围、兼容基线、技术约束一律以 PRD 为准，与 PRD 冲突时先向用户确认，不自行改需求。文件名批次后缀对应需求批次；新需求批次的 PRD / 任务清单另行建档，新 issue 未必记录于既有批次文件。
- **已拍板决策不重开**：[PRD 决策记录章节](docs/PRD_*.md) 的结论视为定论，不质疑、不绕开，不改设计稿定稿（[`designs/`](designs/)）；确有必要变更，先向用户提出。
- **设备档案（[`docs/devices/`](docs/devices/)）一机一档**：涉及实机部署、调试、传输前，先读目标设备的「踩过的坑」与「已知问题」；已实测失败的路径不要重走，已排除的原因不要重新验证。
- **动手之前必读 `local/`（本地文件，不提交）**：不限实机——构建、模拟器、adb、nix、环境配置、实机部署、调试、传输，任何操作动手前必须先通读 `local/` 下全部文件。其中 [`local/DEV-GUIDE.md`](local/DEV-GUIDE.md) 是开发环境问题的唯一记录处（adb 密钥目录、命令模板、就绪判据、链路坑）；不凭记忆拼命令，不原样执行他人转述的命令，先对照该文件核实。git 管理的其余文档不重复其内容。
- **设计稿（[`designs/`](designs/)）只作视觉与交互参考**：色值/规格以 [PRD 设计规范章节](docs/PRD_*.md) 为准。
- **[docs/](docs/) 下文档用 STE 规范输出**：按简化技术中文（ASD-STE100 原则）写——关键信息先行、一句一事、状态词固定；具体问题引用对应文档，不重复细节。可以直接用 `/ste` skill。

## 三、工具原则

- **任何环境操作（模拟器 / adb / nix / 构建工具链）动手前，先读 [`local/DEV-GUIDE.md`](local/DEV-GUIDE.md)**：钥匙目录、HOME 重定向、命令模板、就绪判据都在里面；没读就动手属于违规，造成的返工与授权浪费由操作者负责复盘。
- 本机已备常用开发调试工具（adb、python3、node/pnpm、java、git 等）；**动手前先验证工具是否存在及版本**，不要假设未验证的工具已安装。
- 工具链与构建方式等技术选型以 [PRD](docs/PRD_*.md) 和用户指示为准，未拍板前不要自行引入新依赖或新工具链。
- 实机连接信息以[设备档案](docs/devices/)为准，且可能随环境变动；连不上先向用户确认，不要盲试。

## 四、开发规范（原则）

- **严格按 [PRD](docs/PRD_*.md) 实现范围开发**：遵守优先级（P0/P1/P2）与「非目标」边界，不加戏、不提前实现后续版本功能。
- **视觉与交互严格对齐设计规范**：色值与规格 Token 化管理，不随意改值、不新增方案。
- **可靠性**：输入法服务崩溃不得阻塞宿主应用。
- **无障碍**：全部可交互元素提供无障碍标注，焦点与状态满足可读性要求。
- **性能预算**：以 [PRD 非功能需求章节](docs/PRD_*.md) 的指标为准，实现时逐条对照。
- **「已完成」的唯一标准是真机验证**：没有在真机上验证过的功能，不说「已完成」；汇报时明确区分「已实现」与「真机已验证」。
- **截图统一存放与清理**：凡需要截图验证的产物，统一放仓库根 `screenshots/` 目录（按任务建子目录，如 `screenshots/T6/`），不再另建 `.shots-*`、`.verify-tmp` 等散目录；该目录已 gitignore、不入库。**真机验证完成后，询问户主是否需要删除截图**，未经户主确认不自行清理。
- **工作留痕（三处分工，操作规范见 [`docs/issues/README.md`](docs/issues/README.md) 与 [`docs/HANDOFF.md`](docs/HANDOFF.md) 开头的「维护约定」）**：
  - **issue（[`docs/issues/`](docs/issues/)）**：单次变更的唯一详细记录。每个代码 / 软件变更（功能、bug fix、构建脚本）对应一个 issue，与 [`docs/TASKS_*.md`](docs/TASKS_*.md) 任务同名同编号；**新建 issue / 任务前先读 [`docs/issues/README.md`](docs/issues/README.md) 并按其规则从 [`docs/issues/NEXT-ID.md`](docs/issues/NEXT-ID.md) 取号、立即回写**；**处理任何 issue（开工实现、修复、验证等）之前必须先通知户主、询问是否处理，只有户主同意了才能进行，除非户主显式要求执行**（2026-11-10 拍板，T51 误派教训：需求拍板 ≠ 开工指令）；agent 通过模拟器 / 模拟过程验证后通知户主，户主验收拍板后由 `open/` 挪入 `closed/`。纯文档调整不建 issue。
  - **HANDOFF（[`docs/HANDOFF.md`](docs/HANDOFF.md)）**：只留四类内容——关键决策、踩过的坑、当前状态、待办；细节指向 issue，不重复展开。**不记录工作过程**：干了什么事的过程叙事一律不进本文（归 issue 与 git 历史）；与 [`docs/TASKS_*.md`](docs/TASKS_*.md) / issue 重复的内容用指针代替；每次更新顺手收缩，发现膨胀先整理再收笔。
  - **CHANGELOG（[`CHANGELOG.md`](CHANGELOG.md)）**：发版时更新，一版一节，按该版本包含的 issue 汇总（新增 / 变更 / 修复）。
- **文档同步**：实测结论与新踩的坑写入对应[设备档案](docs/devices/)（一机一档：概述 / 参数 / 踩过的坑 / 已知问题）；开发环境问题写入 `local/DEV-GUIDE.md`（不提交）；新拍板决策追加到 [PRD 决策记录章节](docs/PRD_*.md)，编号顺延、不重排。
- 提交信息与代码注释用中文。
- **提交信息遵循 [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/) 规范**：结构为 `<type>(<scope>): <描述>` + 可选 body + 可选 footer；type / scope / footer 关键词用规范固定英文词，描述与正文按上一条用中文。
  - **type（必填，小写英文）**：`feat` 新功能、`fix` 修 bug 必用；其余按需选 `build`、`chore`、`ci`、`docs`、`style`、`refactor`、`perf`、`test`、`revert`。
  - **scope（可选）**：括号内的名词，标明改动区域，如 `fix(ime):`。
  - **破坏性变更**：type/scope 后紧接 `!`（如 `feat(api)!:`），或 footer 写 `BREAKING CHANGE: <说明>`；footer 里 `BREAKING CHANGE` 必须全大写（`BREAKING-CHANGE` 同义）。
  - **描述**：紧跟 `: ` 之后，中文短句概括本次改动，不以句号结尾。
  - **body / footer**：各与上一段之间空一行；footer 用 git trailer 格式（token 内空格写 `-`，如 `Refs: #123`、`Reviewed-by: Z`）。
  - 示例：`feat(ime): 支持手机扫码远程输入`；`fix: 修复焦点丢失后按键无响应`；`docs: 更正 CHANGELOG 措辞`；`feat(settings)!: 重命名全部配色 Token`（另起 footer 写 `BREAKING CHANGE: ...`）。
  - 一个 commit 只对应一种 type；混了多种就拆成多个 commit。

## 五、禁止事项

1. 禁止安装或推荐 SofaType 以外的软件，禁止引入第三方 SDK、应用内连 adb shell 通道、接入不受我们管理的第三方服务；审 PR 时发现此类改动一律拦下，交用户决定。
2. 禁止上传或持久化用户输入内容（含日志与远程输入缓冲）——隐私红线，无例外；服务只监听限定网络接口。
3. 禁止在功能退出后残留监听端口、后台服务或临时文件。
4. 禁止卸载、停用任何系统应用（含 `com.mitv.tvhome`），除非用户明确要求且清楚后果。
5. 禁止覆盖已有配置：写 `enabled_accessibility_services` 只追加、不覆盖已有服务；在 zsh 里写 `${VAR}:...`，不要写 `$VAR:...`。
6. 禁止删除或改动户主的数据（上传的照片、选好的壁纸、电视里已有的他人 APK）；清理时只按自己创建时记下的 id 删，不按猜测批量删。
7. 禁止提交 `local/` 目录（签名密钥、IP 等敏感信息）。
8. 禁止自行执行 git 提交：只有户主审阅过变更并明确提出提交后，才可以提交。工作区可以随时改，commit 必须由户主发起。提交前先清理垃圾文件（临时截图、dump、临时产物等不入库文件），核对 `git status` 无垃圾残留后再提交。
