# 小米电视4（`MiTV4-ANSM0`）

本项目 APK 的安装目标机。装 APK 前先通读「踩过的坑」。

## 设备概述

- **设备类型**：小米电视4 智能电视
- **型号**：`MiTV4-ANSM0`
- **品牌 / 设备代号**：Xiaomi / `xmen`
- **主控平台**：Amlogic `gxbaby`（S905 系列，`ro.hardware` = `amlogic`）
- **操作系统**：Android 6.0.1（SDK 23）

## 设备参数

### 硬件

| 项目 | 值 |
|---|---|
| CPU | 4 核；`/proc/cpuinfo` = `AArch64 Processor rev 4 (aarch64)` |
| 内存 | 约 1.6 GB（`MemTotal` 1663152 kB） |
| 无线 | 2.4 GHz Wi-Fi |
| 有线 | 背面通常有 RJ45 网口（未实测确认） |

### 系统 / ABI

| 项目 | 值 |
|---|---|
| `ro.product.cpu.abi` | `armeabi-v7a` |
| `ro.product.cpu.abilist` | `armeabi-v7a,armeabi` |
| `arm64-v8a` | **不支持**：内核是 AArch64，但 Android 用户空间只有 32 位 |

### 连接方式

| 方式 | 地址 / 说明 |
|---|---|
| 无线 ADB | `192.168.1.210:5555`（2026-10-28 实测地址；DHCP 可能变动，连不上时先向用户确认新 IP） |
| USB adb | 电视 USB 口连 Mac；连接后可 `adb tcpip 5555` 切回无线 |
| 有线 | RJ45 直连路由器 |

## 踩过的坑

### 坑 1：硬件 64 位、系统 32 位——装 APK 必须选 `armeabi-v7a`

最容易踩的坑：`/proc/cpuinfo` 显示 AArch64，容易被误导去下 arm64 包。实际 Android 用户空间只有 32 位，装纯 `arm64-v8a` 的包会直接失败：

```
INSTALL_FAILED_NO_MATCHING_ABIS
```

只能装 **`armeabi-v7a`** 的包（`universal`、`noarch` 也可以）。含原生库的包才受此限；纯 dex 的包（APK 无 `lib/` 目录）不受限，可装（拍板见 PRD 决策 23）。装前复核：

```bash
unzip -l <apk> | grep -oE 'lib/[a-z0-9_-]+/' | sort -u   # 无输出 = 纯 dex 包，可装；有输出时只允许 armeabi-v7a
adb shell getprop ro.product.cpu.abilist
```

### 坑 2：adb 注入英文字母被自带输入法（搜狗小米版）组合态吞掉（2026-11-05）

**现象**：`adb shell input text "HelloTV123"` 后 EditText 只落 `123`。搜狗输入法把注入的英文字母当作拼音组合态字符，组合串不上屏；按 BACK 收起键盘时组合串整体丢弃。数字直通上屏，追加正常。

**影响**：真机走查用 adb 注入文本时，含字母的样例（如 `Hello.com@`）不可靠，数字样例可靠。后续 T7 等英文输入任务的真机走查要么用应用内代码路径（如「填入示例文本」按钮），要么先处理输入法模式。

**适用范围**：预装搜狗小米版输入法为默认 IME 时。切换默认 IME 可规避，但需户主确认。

### 坑 3：切换默认输入法被系统自动夺回（2026-11-05）

**现象**：`ime set` 切到第三方 IME（sofatype）后，搜狗弹崩溃反馈弹窗（其进程被切走时异常），系统随后自动把 `default_input_method` 切回搜狗——表现为「ime set 已生效，弹出键盘却是搜狗」。

**处置**：重新 `ime set` 后立即走查；每次弹键盘前先 `settings get secure default_input_method` 核对。搜狗异常反馈弹窗用 BACK 关闭（不点发送）。根治（第三方 IME 稳态默认）待后续任务处理。

### 坑 4：电视空闲自动进屏保，走查指令全部落空（2026-11-05）

**现象**：走查间隔电视空闲后自动进 `com.mitv.screensaver`，前台被屏保压住：`input tap` 全落空、uiautomator dump 全是屏保节点。

**处置**：真机走查连贯执行；恢复操作前先 `dumpsys window` 核对前台，或先按遥控键唤醒退出屏保。

### 坑 5：杀 IME 进程（同进程前台场景）→ 默认输入法被系统回退（2026-11-08，T12）

**现象**：`run-as com.sofatype.ime kill <pid>` 杀 IME 进程时，若前台 Activity 与 IME 同进程（设置页测试输入区场景），`default_input_method` 被系统回退为搜狗（2/2 复现），且前台任务回退到下层 app；前台为第三方 app 时纯杀 IME 进程不夺回（变量隔离实证）。进程死亡后系统可重新拉起 IME 服务（`Start proc … for service`，logcat 为证），但需重新 `ime set`（被夺回场景）。

**处置**：杀进程测试后每次弹键盘前按坑 3 口径核对 `default_input_method`；「防夺回」加固需户主拍板（详见 issue T12 坑 2）。

**补充（2026-11-08 午后）**：T12 终验待验空闲期再次发生回退（sofatype → 搜狗），当时无杀进程、无同进程前台 Activity 死亡伴随（前台 orchard，机制未查明）；同日累计 2 次空闲期回退。**机制评估归档（2026-11-09，T39 / PRD 决策 37）**：回退定性 = 平台 force-stop 类动作触发 AOSP 6 原生重置（当前 IME 包被 force-stop → 清空选中并重选系统默认输入法，源码实证）；本坑空闲期回退的机制假设 = 坑 9 屏保强杀同链——下次复现抓全窗 logcat 查 `Force stopping com.sofatype.ime` 指纹即可归并（取证纪律挂账，不立项实施，见 issue T39）。

### 坑 6：键盘收起后发 CENTER 唤起，CENTER 直落 IME 初始焦点键（2026-11-08，T12）

**现象**：键盘收起后注入 `input keyevent 23` 唤起，宿主 `showSoftInput` 重弹与注入竞争，CENTER 在重弹完成后到达 IME，落在初始焦点键 digit_1 上直出「1」（同轮 3 次复现，ImeKeyTrace 可证）。真机竞争窗口大于 120ms（HANDOFF「CENTER 收起延迟 120ms——时序常量真机待复核」的实锤）。

**处置**：唤起后先核对焦点 trace 再注入后续键；走查中避免「收起 → 立即 CENTER」的连贯操作，或唤起后检查输入区内容。

**修复去向（2026-11-08）**：T38 双抑制窗加固已修复——收起后显示抑制窗 veto 迟到 show、唤起首确认键吞一次即解除；新包真机实证（veto/吞键 trace，详见 `docs/issues/closed/T38.md`）。旧包（lastUpdateTime 2026-11-08 09:33 前）仍有此坑。

### 坑 7：uiautomator dump 退出阶段产生 FATAL EXCEPTION（2026-11-08，T12）

**现象**：Android 6 上 `uiautomator dump` 结束时，dump 工具进程自身（shell uid）抛 SecurityException FATAL（`unregisterUiTestAutomationService` 权限拒绝），进 logcat crash 缓冲。

**处置**：真机 logcat 全程核查 FATAL 时按 pid/uid 排除该工具噪音；其存在不代表应用崩溃。

### 坑 8：无线 adb 链路延迟剧烈波动，注入走查须串行确认（2026-11-08，T38）

**现象**：同一会话内 `adb shell` 往返在 0.4s ~ 40s+ 间波动；慢速期批量 `input keyevent` 尾部键延迟 30s+ 到达（导航偏航诱因），偶发 offline 断连（`adb connect` 即恢复）与 adb server 重启后钥匙混淆（unauthorized，按 DEV-GUIDE 1.4 kill-server 口径处置）；本地落盘的 logcat 文件滞后设备端缓冲 30s+。

**处置**：①注入走查用「单键一条命令 + 设备端 `logcat -d -s ImeKeyTrace | tail` 确认」模式，勿依赖本地文件做实时判定；②批量键发送后必须核对焦点再继续，偏航按 trace 实况纠偏；③`logcat -d` 全缓冲 FATAL 核查时区分系统进程/工具噪音与应用崩溃（坑 7）。

**补充（2026-11-09，T27）——注入病理窗口**：同日实测会话内出现：幻影 DEL×2、单 BACK 迟到 80s 二次送达、keycode 4 无中生有入 trace、事件沉入 T38 抑制浮层（MitvPhoneWindowManager「prepare dispatch key … Unhandled key」在案）。注入类走查的「直落/双写」类观测（trace 缺行、输入框多字符）须先排除病理窗口污染；重要结论换时段复测，指纹字段（如 firstRow=被命中键字母）可做自检。

### 坑 8：搜狗输入法会话内 show 请求在、键盘窗口不渲染（2026-11-08，T12 对照定性）

**现象**：某次会话中搜狗（`com.sohu.inputmethod.sogou.tv/.SogouIME`）全程 `mInputShown=true`（请求态）但 IME 窗口 surface `shown=false`、SurfaceFlinger 无其合成层；BACK/OK/CENTER toggle 均不能使其建立可见基线，收起控件在不可见窗口内无可注入坐标。机制未查明，属搜狗内部行为。

**处置**：在该状态下做输入法对照实验会缺基线（T12 问题 1 对照因此不可测）；判定 IME 实际显示态不能只看 `mInputShown`，须并核窗口 surface / SurfaceFlinger 合成层。

### 坑 9：屏保启动时平台强杀持有 IME 窗口的进程，连带 default_input_method 被夺回（2026-11-08，T19）

**现象**：空闲 480s（`checkScreenSaverTimeoutLocked come 480000`，18:03:46 实证）屏保启动时，平台 Force stopping 本应用并杀进程（logcat：`it's time to start screen saver` → `Force stopping com.sofatype.ime` → `MitvWindowManager: com.sofatype.ime has system window,so kill it`）。进程被杀后 `default_input_method` 被系统夺回搜狗（坑 5 同族后果，诱因为平台行为而非测试动作），远程会话随进程死亡断开。

**处置**：走查期间防 >8 分钟空闲，注入任意键（如 DPAD）可重置屏保计时（PowerManagerService userActivity 日志可证）；每次弹键盘前按坑 3 口径核对 `default_input_method`；恢复按坑 3/5。

**补充（2026-11-09，T27）**：屏保强杀同样波及**前台设置页 Activity**（设置页消失后系统 HOME 兜底可落到第三方应用，实测 tvbox 回前台，非该应用自启拉前台——logcat 无其 START 痕迹）；长等待期用保活循环（每 6 分钟注入 DPAD 重置计时，走查毕即清，T27 实测有效）。本轮坑 9 两次复现（480s 整点触发各一次），诱因均为走查等待期未注入。

### 坑 10：TvMgr-MemoryAutoClean 静默强杀后台/前台应用，无应用侧日志（2026-11-08，T19）

**现象**：`TvMgr-MemoryAut`（pid 3371）周期性 Force stopping 第三方应用（18:22:52 实证连发 `Force stopping com.newtv.cboxtv ... from pid 3371` 等，自身 126 行被 chatty 吞掉）；本应用后台化（HOME 回桌面）约 10 秒后被清杀，前台静默死亡一次（~18:19，疑似同源）。SIGKILL 无应用侧异常，ActivityManager 痕迹常被 chatty 汇总吞掉。

**处置**：配对/远程会话期间保持应用前台，不要 HOME/切后台；判「进程不存在」不可只凭 `ps`（坑 12），用 `dumpsys activity processes` 交叉验证；进程死后恢复按坑 3（ime set）+ 重新进远程模式。

**补充（2026-11-09，T27）**：前台 Activity 并不豁免——sofatype 设置页前台亦被清杀一次（11:36:09，pid 11205，自杀型 killProcess 路径，直接诱因强嫌疑 TvMgr-MemoryAutoClean（pid 3387，死亡后 0.1s 有其 expire 痕迹，自身日志被 chatty 吞）；crash 栈未捕获）。走查脚本对前台 Activity 存活不可做持久假设，节点前 30s 内核对前台。

### 坑 11：shell 无法真实切换 Wi-Fi——svc 无权限、airplane 广播被 MiTV 栈忽略（2026-11-08，T19）

**现象**：三通道全堵：① `svc wifi disable` → `SecurityException: WifiService: Neither user 2000 nor current process has android.permission.CHANGE_WIFI_STATE`（logcat 18:41:41.918），Wi-Fi 不下线；② Android 6 `svc` 无 airplane 子命令（仅 power）；③ `settings put global airplane_mode_on 1` + `am broadcast AIRPLANE_MODE_CHANGED` 设置位生效（读回 1）但 MiTV Wi-Fi 栈不响应，wlan0 不掉线（广播 result=0，18:43:41 实证）。

**处置**：真实网络变化场景只能由户主在电视设置 UI 手动开关 Wi-Fi；adb UI 注入自行开关不可行——Wi-Fi 断则无线 adb 断，re-enable 半程无链路可注入（注入式开关只能完成 OFF 半程）。T19 的 O-6 真机触发因此免除（户主拍板模拟器口径）。

### 坑 12：无线链路劣化期工具输出假象——ps 空表、grep 原生崩溃（2026-11-08，T19）

**现象**：链路慢速期，设备端 `ps -A` 可回仅表头的空列表（进程实际存活）；toybox `grep` 自身可原生崩溃产生 tombstone（18:07:42 `/system/bin/grep` F/DEBUG 栈）。据此误判「进程死亡」「无匹配」会造成假结论（T19 实际踩中：误判应用进程死亡，实际存活）。

**处置**：「进程不存在/无输出」类结论必须换通道交叉验证（`dumpsys activity processes`）；计数/匹配类操作拉回本地处理（`adb shell ps -A` 重定向到本地文件再 grep）；logcat 全缓冲 FATAL 核查时排除 grep/svc/uiautomator 工具自身崩溃（坑 7 同族扩展）。

### 坑 13：uiautomator dump 坐标系是窗口缩放系，直接用于 input tap 会偏移（2026-11-08，T19）

**现象**：本机 `uiautomator dump` 的 bounds 处于应用窗口坐标空间（根节点高度 1001px），与 `input tap` 的屏幕像素空间（1080px）比例 ≈0.927；把 dump 坐标直接搬去 tap 会整体偏上约 7%（实测点击 EditText 无响应，换 screencap 目测像素坐标立即命中）。

**处置**：`input tap` 坐标一律以 `screencap` 截图目测像素为准（DEV-GUIDE「走查通道备注」的设备侧实锤与量化）；dump 仅用于读控件结构/文本，不用于产坐标。

### 坑 14：候选栏显隐驱动键盘区整体位移，同坐标两态命中不同键（2026-11-09，T27）

**现象**：中文模式候选栏区域常驻（含空组合，键盘整体处于低位）；英文/符号态候选栏隐藏，键盘上移约 72px。同一屏幕坐标在两态命中不同键（实测 (1180,730)：低位=「中/英」，高位=`123_!`）。两态切换瞬间存在渲染中间态（A2 截图「数字列消失」，自愈，非缺陷）。

**处置**：`input tap` 坐标以「当前态最新一次 screencap 目测」为准；候选栏显隐、中/英切换、进出符号键盘后必须重新截图目测再点（坑 13 的扩展：不只 dump 缩放系，还有布局两态位移）。

### 坑 15：应用发起安装的授权判定读 global 位，UI「未知来源」开关写 secure 位（2026-11-09，T27）

**现象**：应用 PackageInstaller 会话 commit 被平台拒 `INSTALL_FAILED_USER_RESTRICTED`，当时 `secure install_non_market_apps=1` 且设置 UI「安装未知来源应用」常开（户主核对一直开着）；`settings put global install_non_market_apps 1` 补写后，mock 会话提交返回回执 SUCCESS（14:07:30 落地）——平台判定源=global 位，UI 开关写 secure 位，两处不一致。**修订（2026-11-09 户主核对证伪）**：mock 包清单版本同为 0.1.0、versionCode 未递增，上句 SUCCESS 是同版本复装，不是升级；Round 2/3 走查全程设置页版本恒 `v0.1.0 (1)`（10 份 dump 为证），原「静默直装成功」表述作废。versionCode 真递增的会话提交在本机未验证过。另实测：系统安装窗/会话活跃期间，`adb install -r` 复装亦被拒 `USER_RESTRICTED`（195s 失败回执，机制未查）——复装失败先排除会话残留与前台安装窗，不盲试。

**处置**：户主拍板（2026-11-09）：`global install_non_market_apps=1` 长期保持置位，为该机安装授权标准态。涉及真机安装链验证时，先核对 `settings get global install_non_market_apps`。同日拍板：检查更新链路改走 UI 渠道（系统安装器），会话通道不再作本机更新主链路——PRD 决策 36，落地任务 T47。

