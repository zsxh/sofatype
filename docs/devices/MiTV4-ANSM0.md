# 小米电视4（`MiTV4-ANSM0`）· 设备档案

本机为本项目 APK 的验收目标机。本文面向任何在这台机器上部署、调试、走查的 agent 或人，只记录**设备固有约束**与**当前有效的问题和应对方式**，不记录排查过程与历史时间线。

## 一、设备概述

- **机型**：小米电视4，型号 `MiTV4-ANSM0`（设备代号 `xmen`），Amlogic `gxbaby`（S905 系列）
- **系统**：Android 6.0.1（SDK 23）
- **内存**：约 1.6 GB；**无线**：2.4 GHz Wi-Fi；**有线**：背面 RJ45 网口
- **预装输入法**：搜狗小米版（`com.sohu.inputmethod.sogou.tv`）为机内默认输入法

## 二、连接方式

| 方式 | 说明 |
| --- | --- |
| 无线 adb | 端口 5555；IP 为 DHCP 动态分配，连不上先向设备主人确认当前地址 |
| USB adb | 电视 USB 口连电脑，连接后可 `adb tcpip 5555` 切回无线 |

## 三、固有约束（装机前必读）

1. **ABI**：内核 AArch64、Android 用户空间仅 32 位 → 只能装 `armeabi-v7a` / universal / 纯 dex 包，装 arm64 直接报 `INSTALL_FAILED_NO_MATCHING_ABIS`。装前复核：
   ```bash
   unzip -l <apk> | grep -oE 'lib/[a-z0-9_-]+/' | sort -u   # 无输出 = 纯 dex 包，可装
   adb shell getprop ro.product.cpu.abilist
   ```
2. **签名**：API 23 只验 v1 签名——unsigned release 包用 JDK `jarsigner` + debug keystore 签名后可直接 `pm install -r` 升级；装机核验铁证 = `dumpsys package <包名>` 的 `pkgFlags`（debug 包必含 `DEBUGGABLE`）。
3. **无 adb 关机通道**：`reboot -p` 实测等同重启；关机由设备主人遥控执行。
4. **shell 无法真实切换 Wi-Fi**：`svc wifi disable` 报 SecurityException、airplane 设置 MiTV 栈不响应——真实网络变化只能由人在电视设置手动操作。
5. **安装授权标准态**：安装授权判定读 global 位（UI 开关写 secure 位，两处不一致），`settings get global install_non_market_apps` 应为 `1`（长期保持置位）。

## 四、问题与应对（当前有效）

### 1. 默认输入法被夺回搜狗（核心问题）

**现象**：多条平台链对本应用执行包级 force-stop（指纹 `Force stopping com.sofatype.ime` + `MitvWindowManager: … has system window,so kill it`，发起方 system_server），随后 AOSP 6 对「当前 IME 的包被 force-stop」清空选中并重选系统默认输入法（= 搜狗），伴随搜狗崩溃反馈弹窗（BACK 关闭、不点发送）。

**已实证触发形态**：
- 本应用**自家 Activity 前台切走沿**：键盘窗口创建过后即成风险态（窗口对象收起后仍挂包名下，Activity 销毁与否无关）——平台检查「刚被切走的前台包」是否持系统窗口，自家包持 IME 窗口即被杀。
- **空闲屏保沿**：屏保启动时平台主动强杀持系统窗口的包（见四.2），同样触发夺回。
- **第三方宿主前台切走沿不触发**（第三方包名下无系统窗口，检查不命中）——第三方应用里打字后退出不落本坑。
- **instrument 测试边界沿**（T91 真机批次实证）：instrumented 套件跨场景的 `am start`（自家 Activity 前台切走 + 同进程键盘窗口对象仍在）→ 包级 force-stop（system_server）→ instrumentation 进程连带死亡（`am instrument` 报「Process crashed」）；随后 finished-inst 沿「has system window,so kill it」补刀。全量一轮跑不可达。

**应对**：
- 每次弹键盘前核对 `settings get secure default_input_method`；被夺回先 `ime set com.sofatype.ime/.SofatypeImeService` 恢复再取证；任何杀进程 / 夺回链实验后同样核对。
- 受控 `ime set` 切走不重启 IME 进程（pid 不变）——「服务重启、新 pid」仅发生于夺回链，勿把恢复动作误计为夺回证据。
- 搜狗可能出现「`mInputShown=true` 请求态但窗口不渲染」的内部态，IME 实际显示判定须并核窗口 surface（见四.6 显示态判据）。
- instrumented 批次按「单场景一跑」编排：每场景独立 `am instrument` 进程 + 轮间恢复纪律（BACK 清弹窗、`ime set` 核对、`logcat -c`）；收尾阶段 finished-inst 强杀 + 窗口击杀必现，属收尾噪音，场景结果以先期落盘的 tsv 为准。

### 2. 空闲自动清杀（走查头号现场杀手）

**现象**：
- 空闲 480s 自动进系统屏保（`com.mitv.screensaver`）：tap 全落空、前台被屏保压住；屏保启动沿同时强杀持系统窗口的包（四.1 触发形态之一），波及前台 Activity 与远程会话。
- `TvMgr-MemoryAutoClean` 周期性静默强杀后台 / 前台应用（SIGKILL、无应用侧日志、日志常被 chatty 吞掉）：前台 Activity 不豁免。**清杀窗可变**：后台化 ~14.6 秒至 4 分钟以上不等，**键盘窗口显示态闲置 ~1–4 分钟同样被杀**（含组合态活跃 51 秒样本；2026-11-18 T85 真机取证修正——原「后台化约 10 秒即杀」为早期单次观测口径）→ 对 IME 意味着「换 app 回弹键盘几乎必遇进程冷启」。

**应对**：
- 走查连贯执行；任何预计 >8 分钟的等待挂保活循环（每约 6 分钟注入一发 DPAD 重置屏保计时，走查毕即清）。
- 远程 WS 走查**进远程模式即挂保活**，不等出事再挂。
- 不对前台 Activity 存活做持久假设，关键节点前 30s 内核对前台（`dumpsys window mCurrentFocus`）。
- 判「进程不存在」不可只凭 `ps`（见四.5 交叉验证口径）。

### 3. 安装链特殊形态

**现象与应对**：
- 安装段长时间挂起 = 电视端弹**安装许可待确认**：shell uid 发起的安装弹「从其它设备安装」确认框——先 `screencap` 目测弹窗形态与焦点态，再远程 `input tap` 代点【确定】（确认界面**只吃 tap 不吃键码**；「双钮两段式：首 tap 移焦、再 tap 激活」与「双钮无初始焦点、首 tap 直激活」两型并存，认准确认钮勿误点取消）；代点无效再通知设备主人到电视前手动确认；等待期不盲试重试（挂起会话期间复装可能被拒）。
- 复装被拒 `INSTALL_FAILED_USER_RESTRICTED`：先排除挂起安装会话与前台安装窗，不盲试。
- 升级安装预期副作用：伴随 default 夺回（四.1，`ime set` 恢复）+ 杀设置页任务（升级确认后需 `am start` 恢复回设置页）。

### 4. 注入病理窗口

**现象**：偶发幻影键（未注入的键入 trace）、单键迟到二次送达、事件沉入系统浮层（`MitvPhoneWindowManager … Unhandled key` 在案）。

**应对**：「直落 / 双写 / trace 缺行」类观测先排除病理窗口污染；重要结论换时段复测；用指纹字段（如被命中键的字母）自检。

### 5. 走查工具假象

- `ps -A` 可偶发回仅表头空表；toybox `grep` 可原生崩溃产生 tombstone → 「进程不存在 / 无匹配」必须换通道交叉验证（`dumpsys activity processes`）；计数 / 匹配操作拉回本地文件处理。
- `/proc/net/tcp6` 端口表读数偶发假阴性（监听中报 0 条）→ 「无监听 / 残留」结论必须同参数双读 + 双表（tcp6/tcp）交叉验证，单次 0 条不定论。
- `pidof <包名>` 返回全量 pid 不过滤 → 进程定位用 `ps | grep` 或 logcat 行内 pid。
- `dumpsys cpuinfo` 可能保留已死进程的陈旧条目（T92 批次实测：显示已不存在 pid 的 1.9% CPU，现进程 0%）→ 非激活零 CPU 类结论以 `top -n 1` 现行进程读数为准，cpuinfo 陈旧条目须并核 pid 存活后排除。
- `uiautomator dump` 退出段自身抛 SecurityException FATAL（shell uid 工具噪音）→ 全窗 FATAL 核查按 pid/uid 排除，不代表应用崩溃。
- dump 坐标系是应用窗口缩放系（与屏幕像素空间比 ≈0.927），且候选栏显隐 / 中英切换 / 进出符号键盘会引起键盘区整体位移 → `input tap` 坐标一律以**当前态最新 screencap 目测像素**为准，两态切换后必须重新截图目测再点；dump 只用于读控件结构 / 文本，不产坐标。
- 设备端 `logcat -f <文件>` 后台落盘可失效（命令在跑、文件不落盘）→ 长窗取证改用 `logcat -d` 全缓冲导出（先 `logcat -G` 扩容防 chatty 吞没），等效取证。
- `UiDevice.takeScreenshot`（uiautomator）该机恒返回失败（SurfaceFlinger 拒绝 app 域截图），失败沿可断 UiAutomation 连锁终止整轮 instrumentation（「Process crashed」）；shell `screencap` 正常 → 测试框架截图取证改走 `screencap` 落盘 + 二进制回读通道（T91 Harness 已内置），勿再用 UiDevice 截图。

### 6. 输入法会话与操作边界

- **tap 激活分型**：设置页主卡按钮一击即激活；设置页副卡（随键盘窗口控件）首击仅聚焦、第二次 tap 同坐标才激活；第三方应用控件同族（首击常仅聚焦，第三方输入框可能自带应用内置键盘、需二次 tap 才调起系统 IME——内置键盘态下系统 IME 判据全部无效）。遥控通道激活优先：键码 OK 走焦点控件 performClick，单键即激活。
- **副卡随键盘窗口渲染**，键盘收起即消失（Activity 树无副卡节点）→ 涉及副卡的操作（进远程模式、看二维码）须在键盘显示态执行。
- 新进程 `am start` 设置页可能自动弹键盘盖住下方区域（非必现）→ 先 BACK 收起再操作页面下方区域。
- 「收起后键码取证」（遥控宿主响应类）必须在收起后**未 tap 过任何按钮**的干净态做；tap 通道用过的状态只做显示态键盘项。
- IME 显示 / 隐藏态判定不单凭 `mInputShown`（宿主无输入会话时读数不可靠），以截图 + ImeKeyTrace（`onWindowShown` 有无）为准。
- 强杀链可能把前台设置页整个清掉（见四.2），系统 HOME 兜底可落到第三方应用——前台判定以 dumpsys 实测为准，不假设回到桌面。

### 7. 文本注入限制

默认 IME 为搜狗时，`adb shell input text` 注入含英文字母的文本会被搜狗当拼音组合态吞掉（数字直通可靠）→ 含字母样例不可靠，改用应用内通道（如「填入示例文本」按钮）或先处理输入法模式。

### 8. 外部存储 app 域文件创建被拒（EACCES）

**现象**：app 对自身 `Android/data/<pkg>/` 可建子目录，但文件创建报 EACCES（sdcardfs 权限执行差异；manifest 未声明存储权限属产品口径）；`run-as` 身份同路径可写，仅 app 域被拒。

**应对**：
- 应用 / 测试框架落盘证据用内部存储（`filesDir`），不依赖 `getExternalFilesDir`（T91 Harness 已按「外部可写探测 → 内部回退」实现）。
- 内部存储产物拉回：`adb exec-out run-as <pkg> cat <path>` 逐文件拉取并核对字节数后才可用；二进制内容走 `adb shell` 文本通道会流损坏（tar 实证），大文件不硬扛。

### 9. 机端实存版本与记录口径可能漂移

**现象**：装机记录为「已卸载」时，实测 `dumpsys package` 可能为旧版本已装且为当时 default（T91 批次实测 0.1.0(1)，firstInstallTime=lastUpdateTime=2026-11-19 11:48:57）。

**应对**：装机类操作动手前一律 `dumpsys package <pkg>` 实测装机态（versionName / lastUpdateTime / pkgFlags），以实测为准，不按既有记录臆断；版本差异按升级链 `pm install -r` 处置并如实留痕。
