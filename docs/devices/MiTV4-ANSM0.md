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

**补充（2026-11-08 午后）**：T12 终验待验空闲期再次发生回退（sofatype → 搜狗），当时无杀进程、无同进程前台 Activity 死亡伴随（前台 orchard，机制未查明）；同日累计 2 次空闲期回退。空闲期回退机制归 T39 同进程架构评估范围。

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

### 坑 8：搜狗输入法会话内 show 请求在、键盘窗口不渲染（2026-11-08，T12 对照定性）

**现象**：某次会话中搜狗（`com.sohu.inputmethod.sogou.tv/.SogouIME`）全程 `mInputShown=true`（请求态）但 IME 窗口 surface `shown=false`、SurfaceFlinger 无其合成层；BACK/OK/CENTER toggle 均不能使其建立可见基线，收起控件在不可见窗口内无可注入坐标。机制未查明，属搜狗内部行为。

**处置**：在该状态下做输入法对照实验会缺基线（T12 问题 1 对照因此不可测）；判定 IME 实际显示态不能只看 `mInputShown`，须并核窗口 surface / SurfaceFlinger 合成层。

