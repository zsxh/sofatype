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

