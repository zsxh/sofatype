#!/usr/bin/env python3
"""集成测试 · 宿主薄 wrapper（python3 + adb；模拟器全轮编排 + 真机专有环节逻辑）。

阶段（--stage，可单步可 full）：
  boot          冷启模拟器（删 default_boot 快照保 userdata → nix run .#tv-emulator → 60 秒就绪判据
                → /proc/uptime 秒级冷启核验）
  install       装 app-debug + androidTest 两包（emulator 直装；mitv4 走 push + pm install + 安装许可代点）
  ime-check     ime set 本 IME + default_input_method 核对
  instrument    am instrument 跑 InstrumentedScenarioSuite（--scenario / --scenarios 定向子集）
  phone         手机端模拟驱动（调 phone_driver.py；与 instrument 并发）
  rounds        双轮编排：round A 记黄金图 → goldens 落位重建 → round B 真实比对 → 报告 → teardown
                （--assume-ready 跳过 boot/install/ime-check）
  teardown      显式收尾：adb emu kill + 端口复核 + serve_mock/浏览器残留清场
  mitv4-plan    真机场景映射与前置清单（只打印，不触设备）
  mitv4-preflight   恒拒绝执行：逻辑已内置、待启用
  mitv4-install     真机装机逻辑（push + pm install + 安装许可 tap 代点 + 保活循环）；
                缺 --host / --confirm-tap 直接拒绝，执行前须户主逐次批准
  full          emulator：boot→install→ime-check→rounds（assume_ready）全程托管

编排内部环节（无独立 stage 入口，仅 rounds / full 内调用）：
  pre（起 serve_mock :8000）、goldens（黄金图落位 androidTest assets 并重建 androidTest 包）、
  report（拉 results.tsv 生成场景 × PASS/FAIL 报告落 reports/integration/）

环境前提：仓库根执行；python3 走系统 PATH；nix / flake 内工具由本脚本以
`nix develop -c` / `nix run` 子进程方式调用；adb 走系统 PATH，adb home 走通用机制
（ANDROID_USER_HOME / 调用方 HOME；工具不注入 HOME、不设专用变量）。
预期时长（emulator full，双轮）：冷启 2~4 分钟 + 两轮 instrument 各 5~12 分钟 + 构建 1~2 分钟。
真机执行一律先询问户主，户主同意后才跑（issue T91 口径 3）。
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import threading
import time

WORKSPACE = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SHOTS = os.path.join(WORKSPACE, "reports", "integration")
TOOLS_INTEGRATION = os.path.join(WORKSPACE, "tools", "integration")
APK_DEBUG = os.path.join(WORKSPACE, "app", "build", "outputs", "apk", "debug", "sofatype-debug.apk")
APK_ANDROIDTEST = os.path.join(WORKSPACE, "app", "build", "outputs", "apk", "androidTest", "debug", "sofatype-debug-androidTest.apk")
TEST_RUNNER = "com.sofatype.ime.test/androidx.test.runner.AndroidJUnitRunner"
TEST_CLASS = "com.sofatype.ime.test.InstrumentedScenarioSuite"
GOLDEN_ASSET_DIR = os.path.join(WORKSPACE, "app", "src", "androidTest", "assets", "goldens", "emulator-api34")
MITV4_GOLDEN_DIR_NAME = "mitv4-api23"
PULLED = os.path.join(SHOTS, "pulled")

SCENARIO_MAP = {
    "s01_keyboard_show": "键盘显隐：显示（黄金图 s01_keyboard_show）",
    "s02_hide_keycode_back": "键盘显隐：键码通道收起",
    "s03_tap_hide_whitelisted": "键盘显隐：tap 通道收起（模拟器白名单；真机预期真 PASS）",
    "s04_composition_candidates": "组合态/候选：网格导航 nihao→候选行→上屏（黄金图 s04_keyboard_candidates）",
    "s05_language_case_switch": "中英与大小写切换：EN+UPPER 直落 A / LOWER 直落 a",
    "s06_symbols_fullhalf_golden": "符号键盘与全半角（黄金图 s06_keyboard_symbols，模拟器滞留帧白名单）",
    "s07_delete_rhythm": "删除节奏：单删 3 次 + OK 长按连删",
    "s08_back_exit_gate": "设置页 BACK 二次退出（2 秒窗）",
    "s09_hidden_key_passthrough": "隐藏态键码透传宿主",
    "s10_remote_pairing": "远程配对（phone_driver 模拟手机端，host 配合）",
    "s11_update_check_mock": "检查更新（serve_mock 桩：发现新版 v0.3.0 + 已是最新）",
}

MITV4_PREFLIGHT_ITEMS = [
    ("default_input_method", "settings get secure default_input_method", "com.sofatype.ime/.SofatypeImeService"),
    ("install_non_market_apps(global)", "settings get global install_non_market_apps", "1"),
    ("cpu abilist（纯 dex 可装）", "getprop ro.product.cpu.abilist", "armeabi-v7a 段存在即可（装前 unzip -l 复核无 lib/）"),
    ("已装版本", "dumpsys package com.sofatype.ime | grep versionName", "0.2.0"),
]


def log(msg):
    print("[runner %s] %s" % (time.strftime("%H:%M:%S"), msg), flush=True)


def avd_snapshots_dir():
    base = os.environ.get("ANDROID_AVD_HOME")
    if not base:
        user_home = os.environ.get("ANDROID_USER_HOME")
        if user_home:
            base = os.path.join(user_home, "avd")
        elif os.environ.get("HOME"):
            base = os.path.join(os.environ["HOME"], ".android", "avd")
    if not base:
        return None
    return os.path.join(base, "sofatype-tv.avd", "snapshots", "default_boot")


def run(cmd, timeout=300, env_extra=None, check=True):
    env = os.environ.copy()
    if env_extra:
        env.update(env_extra)
    proc = subprocess.run(cmd, cwd=WORKSPACE, env=env, capture_output=True, text=True, timeout=timeout)
    if check and proc.returncode != 0:
        raise RuntimeError("命令失败(%d)：%s\nstdout:%s\nstderr:%s" % (
            proc.returncode, " ".join(cmd), proc.stdout[-2000:], proc.stderr[-2000:]))
    return proc


def adb(device, *args, timeout=120, check=True):
    return run(["adb", *args], timeout=timeout, check=check)


def adb_out(device, *args, timeout=120):
    proc = adb(device, *args, timeout=timeout)
    return proc.stdout.strip()


def nix_dev(*cmd, timeout=1800):
    return run(["nix", "develop", "-c", *cmd], timeout=timeout)


def wait_port_free(port, timeout=10):
    deadline = time.time() + timeout
    while time.time() < deadline:
        proc = subprocess.run(["lsof", "-nP", "-iTCP:%d" % port, "-sTCP:LISTEN"],
                              capture_output=True, text=True)
        if proc.returncode != 0 or not proc.stdout.strip():
            return True
        time.sleep(1)
    return False


def wait_port_listen(port, timeout=60):
    deadline = time.time() + timeout
    while time.time() < deadline:
        proc = subprocess.run(["lsof", "-nP", "-iTCP:%d" % port, "-sTCP:LISTEN"],
                              capture_output=True, text=True)
        if proc.returncode == 0 and proc.stdout.strip():
            return True
        time.sleep(1)
    return False


def spawn_bg(cmd, log_path, env_extra=None):
    os.makedirs(os.path.dirname(log_path), exist_ok=True)
    env = os.environ.copy()
    if env_extra:
        env.update(env_extra)
    handle = open(log_path, "ab")
    proc = subprocess.Popen(cmd, cwd=WORKSPACE, stdout=handle, stderr=handle, env=env,
                            stdin=subprocess.DEVNULL, start_new_session=True)
    log("后台进程已起 pid=%d log=%s：%s" % (proc.pid, log_path, " ".join(cmd)))
    return proc


def device_serial(device):
    return "emulator-5554" if device == "emulator" else None


def ensure_no_emulator():
    proc = run(["adb", "devices"], check=False)
    if "emulator-" in proc.stdout:
        raise RuntimeError("已有模拟器实例在线，不可跨回合依赖；先执行 teardown 收尾再冷启。stdout=%s" % proc.stdout)


def stage_boot(device):
    if device != "emulator":
        raise RuntimeError("boot 仅支持模拟器")
    ensure_no_emulator()
    snapshots = avd_snapshots_dir()
    if snapshots and os.path.isdir(snapshots):
        shutil.rmtree(snapshots)
        log("已删 default_boot 快照（保留 userdata）→ 本次为真冷启")
    elif not snapshots:
        log("AVD 目录未定（未声明 ANDROID_AVD_HOME / ANDROID_USER_HOME / HOME），跳过快照清理")
    else:
        log("无 default_boot 快照，冷启成立")
    boot_log = os.path.join(SHOTS, "logs", "emulator-boot.log")
    spawn_bg(["nix", "run", ".#tv-emulator"], boot_log)
    serial = device_serial(device)
    deadline = time.time() + 60
    ready = False
    saw_unauthorized = False
    while time.time() < deadline:
        proc = run(["adb", "devices"], check=False)
        line = [l for l in proc.stdout.splitlines() if l.startswith(serial)]
        if line and line[0].endswith("device"):
            ready = True
            break
        if line and line[0].endswith("unauthorized"):
            saw_unauthorized = True
        time.sleep(2)
    if not ready:
        if saw_unauthorized:
            raise RuntimeError(
                "60 秒内模拟器未就绪且持续 unauthorized：核对调用环境声明的 adb home（ANDROID_USER_HOME / HOME）"
                "钥匙是否与 AVD 已信任钥匙一致，看 %s" % boot_log)
        raise RuntimeError("60 秒内模拟器未就绪（adb devices 无 device），看 %s" % boot_log)
    log("模拟器就绪（device）")
    uptime = adb_out(device, "shell", "cat", "/proc/uptime")
    seconds = float(uptime.split()[0])
    log("/proc/uptime = %s（%.0f 秒）" % (uptime, seconds))
    if seconds > 300:
        raise RuntimeError("非冷启（uptime %.0f 秒）＝快照恢复；处置见仓库本地开发指引「模拟器」节" % seconds)
    time.sleep(5)


def stage_install(device, host=None, confirm_tap=None):
    if device == "emulator":
        for apk in (APK_DEBUG, APK_ANDROIDTEST):
            if not os.path.isfile(apk):
                raise RuntimeError("APK 缺失：%s（先构建）" % apk)
        log(adb_out(device, "install", "-r", "-g", APK_DEBUG))
        log(adb_out(device, "install", "-r", APK_ANDROIDTEST))
    else:
        assert host, "真机装机需 --host ip:5555"
        assert confirm_tap, "真机装机需 --confirm-tap x,y（先 screencap 目测弹窗确认钮，勿盲点）"
        run(["unzip", "-l", APK_DEBUG], check=True)
        listing = subprocess.run(["unzip", "-l", APK_DEBUG], capture_output=True, text=True).stdout
        if re.search(r"lib/[a-z0-9_-]+/", listing):
            raise RuntimeError("真机只收纯 dex 包：检出 lib/ 目录，中止（设备档案坑：ABI）")
        adb_out(device, "connect", host)
        adb_out(device, "-s", host, "push", APK_DEBUG, "/data/local/tmp/sofatype-integration.apk")
        keepalive = KeepAlive(device, host)
        keepalive.start()
        try:
            install_proc = subprocess.Popen(
                ["adb", "-s", host, "shell", "pm", "install", "-r", "/data/local/tmp/sofatype-integration.apk"],
                env=os.environ.copy(),
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            confirm_install_dialog(device, host, confirm_tap, install_proc)
            out, err = install_proc.communicate(timeout=600)
            log("pm install: %s %s" % (out.strip(), err.strip()))
            if "Success" not in out:
                raise RuntimeError("真机安装未成功：%s%s（不盲试重试，先核弹窗与挂起会话）" % (out, err))
        finally:
            keepalive.stop()
        adb_out(device, "-s", host, "shell", "rm", "-f", "/data/local/tmp/sofatype-integration.apk")
    adb(device, "shell", "am", "start", "-n", "com.sofatype.ime/.SettingsActivity")
    time.sleep(2)
    adb(device, "shell", "input", "keyevent", "4")
    time.sleep(1)
    log("装机后已 am start 一次（解除 stopped 态广播静默）")


def confirm_install_dialog(device, host, confirm_tap, install_proc):
    x, y = (float(v) for v in confirm_tap.split(","))
    taps = 0
    while install_proc.poll() is None and taps < 4:
        time.sleep(8)
        if install_proc.poll() is not None:
            break
        focus_dump = adb_out(device, "-s", host, "shell", "dumpsys", "window")
        match_focus = re.search(r"mCurrentFocus=(\S+)", focus_dump)
        focus = match_focus.group(1) if match_focus else ""
        if "installer" in focus or "PackageInstaller" in focus or "security" in focus.lower():
            taps += 1
            log("检测到安装窗（%s），tap 代点第 %d 次：%s" % (focus.strip(), taps, confirm_tap))
            adb(device, "-s", host, "shell", "input", "tap", str(int(x)), str(int(y)))
            time.sleep(2)
            adb(device, "-s", host, "shell", "input", "tap", str(int(x)), str(int(y)))
    if install_proc.poll() is None:
        raise RuntimeError("安装段长时间挂起且代点未解除——通知设备主人到电视前手动确认，不轰炸重试")


class KeepAlive(object):
    """真机走查保活循环：每约 6 分钟一发 DPAD，防 >8 分钟空闲进屏保强杀（仓库本地开发指引「走查操作纪律」节）。"""

    def __init__(self, device, host, interval=360):
        self.device = device
        self.host = host
        self.interval = interval
        self.stop_flag = threading.Event()
        self.thread = None

    def start(self):
        self.thread = threading.Thread(target=self._loop, daemon=True)
        self.thread.start()
        log("保活循环已挂（每 %d 秒一发 DPAD）" % self.interval)

    def stop(self):
        self.stop_flag.set()
        if self.thread:
            self.thread.join(timeout=5)
        log("保活循环已清")

    def _loop(self):
        while not self.stop_flag.wait(self.interval):
            try:
                adb(self.device, "-s", self.host, "shell", "input", "keyevent", "20", timeout=30)
                log("保活 DPAD 已注入")
            except Exception as exc:
                log("保活注入失败（不重试轰炸）：%s" % exc)
                return


def stage_ime_check(device, host=None):
    args = ["-s", host] if host else []
    adb(device, *args, "shell", "ime", "set", "com.sofatype.ime/.SofatypeImeService")
    time.sleep(1)
    default = adb_out(device, *args, "shell", "settings", "get", "secure", "default_input_method")
    if default != "com.sofatype.ime/.SofatypeImeService":
        raise RuntimeError("默认输入法未就位：%s" % default)
    log("ime set 核对通过：%s" % default)


class SideServices(object):
    def __init__(self):
        self.mock = None

    def start(self):
        subprocess.run(["pkill", "-f", "serve_mock.py"], capture_output=True)
        subprocess.run(["pkill", "-f", "integration-phone-browser-"], capture_output=True)
        if not wait_port_free(8000, 10):
            raise RuntimeError("8000 端口未清场，先人工核查残留进程")
        self.mock = spawn_bg(
            ["python3", "tools/mock/github-releases/serve_mock.py",
             "--current-version", "0.2.0", "--newer-version", "0.3.0"],
            os.path.join(SHOTS, "logs", "serve_mock.log"))
        if not wait_port_listen(8000, 30):
            raise RuntimeError("serve_mock 未监听 8000")
        log("serve_mock（:8000）就绪；s10 浏览器通道由 phone 驱动按本地检测结果选择")

    def stop(self):
        subprocess.run(["pkill", "-f", "serve_mock.py"], capture_output=True)
        subprocess.run(["pkill", "-f", "integration-phone-browser-"], capture_output=True)
        ok = wait_port_free(8000, 15) and wait_port_free(9333, 10)
        log("side services 清场%s（8000 无监听复核；9333 无浏览器调试端口）" % ("通过" if ok else "未通过（需人工复核）"))


def pull_device_output(device, tag):
    os.makedirs(PULLED, exist_ok=True)
    target = os.path.join(PULLED, tag)
    if os.path.isdir(target):
        shutil.rmtree(target)
    adb(device, "pull", "/storage/emulated/0/Android/data/com.sofatype.ime/files/integration", target, timeout=300)
    log("已拉取设备产物 → %s" % target)
    return target


def read_results(pulled_dir):
    results = []
    path = os.path.join(pulled_dir, "results.tsv")
    if os.path.isfile(path):
        with open(path, encoding="utf-8") as handle:
            for line in handle:
                parts = line.rstrip("\n").split("\t")
                if len(parts) >= 3:
                    results.append((parts[0], parts[1], parts[2]))
    return results


def stage_instrument(device, tag, timeout=1500, scenario=None, scenarios=None):
    adb(device, "shell", "logcat", "-c")
    target = TEST_CLASS
    filter_list = None
    if scenario:
        filter_list = scenario
    elif scenarios:
        filter_list = scenarios.replace(" ", "")
    log("am instrument 开始（tag=%s target=%s）" % (tag, filter_list or target))
    args = ["am", "instrument", "-w", "-e", "class", target]
    if filter_list:
        args += ["-e", "integrationScenarios", filter_list]
    args += [TEST_RUNNER]
    proc = adb(device, "shell", *args, timeout=timeout, check=False)
    tail = "\n".join(proc.stdout.strip().splitlines()[-12:])
    log("am instrument 结束 code=%d\n%s" % (proc.returncode, tail))
    return proc.returncode, proc.stdout


def stage_goldens(pulled_dir_a):
    screens = os.path.join(pulled_dir_a, "screens")
    if not os.path.isdir(screens):
        raise RuntimeError("round A 未产出 screenshots：%s" % screens)
    os.makedirs(GOLDEN_ASSET_DIR, exist_ok=True)
    moved = []
    missing = []
    for name in ("s01_keyboard_show.png", "s04_keyboard_candidates.png",
                 "s06_keyboard_symbols.png", "s11_settings_page.png"):
        src = os.path.join(screens, name)
        if not os.path.isfile(src):
            missing.append(name)
            continue
        shutil.copy2(src, os.path.join(GOLDEN_ASSET_DIR, name))
        moved.append(name)
    if missing:
        log("黄金图源缺失（不阻断，缺：%s）" % ", ".join(missing))
    if not moved:
        raise RuntimeError("round A 无可落位黄金图")
    log("黄金图落位 androidTest assets：%s" % ", ".join(sorted(moved)))
    nix_dev("./gradlew", "--no-daemon", "assembleDebug", "assembleDebugAndroidTest")
    adb("emulator", "install", "-r", APK_ANDROIDTEST)
    log("androidTest 包已重建重装（黄金图比对口径生效）")


def read_channel(device, tag):
    out = ""
    try:
        out = adb_out(device, "shell", "settings get system integration_phone_channel")
    except Exception:
        pass
    return out if out and out != "null" else "未记录"


def build_report(rows_by_round, duration_note, out_path, channels=None):
    lines = [
        "# 集成测试 · instrumented 场景 × PASS/FAIL 报告",
        "",
        "| 场景 | 说明 | %s |" % " | ".join("Round %s" % n for n in sorted(rows_by_round)),
        "|---|---|%s" % ("---|" * len(rows_by_round)),
    ]
    statuses = {n: {r[0]: (r[1], r[2]) for r in rows} for n, rows in rows_by_round.items()}
    ids = []
    for n in sorted(rows_by_round):
        for (sid, _, _) in rows_by_round[n]:
            if sid not in ids:
                ids.append(sid)
    counts = {}
    for sid in sorted(SCENARIO_MAP):
        cells = []
        for n in sorted(rows_by_round):
            status, detail = statuses.get(n, {}).get(sid, ("—", ""))
            cells.append("`%s` %s" % (status, detail))
            key = status.split("[")[0]
            counts[key] = counts.get(key, 0) + 1
        lines.append("| %s | %s | %s |" % (sid, SCENARIO_MAP[sid], " | ".join(cells)))
    lines += ["", "## 计数（含两轮累计）", ""]
    for key, count in sorted(counts.items()):
        lines.append("- %s：%d" % (key, count))
    if channels:
        lines += ["", "## s10 手机端通道（如实记录）", ""]
        for tag in sorted(channels):
            lines.append("- %s：%s" % (tag, channels[tag]))
    lines += ["", "## 环境与证据", "", "- 时长口径：%s" % duration_note,
              "- 原始结果与截图：%s" % PULLED,
              "- 黄金图：%s" % GOLDEN_ASSET_DIR]
    with open(out_path, "w", encoding="utf-8") as handle:
        handle.write("\n".join(lines) + "\n")
    log("报告已落：%s" % out_path)


def stage_report_collect():
    rows_by_round = {}
    for tag in sorted(os.listdir(PULLED)) if os.path.isdir(PULLED) else []:
        rows = read_results(os.path.join(PULLED, tag))
        if rows:
            rows_by_round[tag] = rows
    return rows_by_round


def stage_report(device, rounds, duration_note):
    rows_by_round = {}
    for tag in sorted(os.listdir(PULLED)) if os.path.isdir(PULLED) else []:
        rows = read_results(os.path.join(PULLED, tag))
        if rows:
            rows_by_round[tag] = rows
    if not rows_by_round:
        raise RuntimeError("无任何 results.tsv 可构建报告")
    build_report(rows_by_round, duration_note, os.path.join(SHOTS, "integration-report.md"))


def stage_teardown(device):
    log("显式收尾开始")
    if device == "emulator":
        proc = run(["adb", "devices"], check=False)
        if "emulator-" in proc.stdout:
            run(["adb", "shell", "svc", "power", "stayon", "false"],
                check=False)
        subprocess.run(["pkill", "-f", "integration-phone-browser-"], capture_output=True)
        run(["adb", "emu", "kill"], check=False)
        time.sleep(5)
        for port in (5554, 5555):
            if not wait_port_free(port, 30):
                raise RuntimeError("端口 %d 仍有 qemu 监听，收尾未通过" % port)
        proc = run(["adb", "devices"], check=False)
        if "emulator-" in proc.stdout:
            raise RuntimeError("adb devices 非空，收尾未通过：%s" % proc.stdout)
        log("模拟器收尾通过：5554/5555 无监听且 devices 无实例")
    else:
        proc = adb(device, "devices", check=False)
        log("真机收尾不动设备连接（户主资产），devices=%s" % proc.stdout.strip().replace("\n", " ; "))


def stage_mitv4_plan():
    lines = ["集成测试 · MiTV4 真机场景映射（本轮仅打印；执行须户主逐次批准）", ""]
    for sid, desc in sorted(SCENARIO_MAP.items()):
        lines.append("- %s：%s" % (sid, desc))
    lines += ["", "真机前置核对项："]
    for name, cmd, expect in MITV4_PREFLIGHT_ITEMS:
        lines.append("- %s（%s，期望：%s）" % (name, cmd, expect))
    lines += [
        "",
        "真机专有环节（wrapper 内置逻辑，未执行）：",
        "- 保活循环：每约 6 分钟一发 DPAD（KeepAlive），装机等待期挂、走查毕即清；",
        "  instrument 运行期依赖 harness 侧 svc power stayon true，不注键码防污染场景。",
        "- 安装许可代点：screencap 目测确认钮 → --confirm-tap x,y → 双钮两段式（tap×2），",
        "  上限 4 次不解除即停手交户主（不轰炸重试）；确认界面只吃 tap 不吃键码。",
        "- 场景差异映射：黄金图目录 mitv4-api23（首轮自动走 GOLDEN_RECORDED 记录实机基线）；",
        "  tap 收起在真机预期真 PASS（白名单只在模拟器触发）。",
    ]
    print("\n".join(lines))


def stage_mitv4_preflight(host):
    raise RuntimeError("真机前置核对涉及真机操作：本轮按 issue 口径不执行；逻辑已内置（mitv4-install / KeepAlive / confirm_install_dialog），执行前须户主逐次批准")


def ensure_side_services(services):
    mock_up = wait_port_listen(8000, 3)
    if not mock_up:
        log("side services 健康检查未过（mock=%s）→ 重启" % mock_up)
        services.stop()
        services.start()


def stage_rounds(assume_ready):
    started = time.time()
    if not assume_ready:
        ensure_no_emulator()
        stage_boot("emulator")
        stage_install("emulator")
        stage_ime_check("emulator")
    services = SideServices()
    services.start()
    notes = []
    channels = {}
    try:
        for round_no in (1, 2):
            tag = "round%d" % round_no
            ensure_side_services(services)
            adb("emulator", "shell", "settings", "delete", "system", "integration_remote_url")
            adb("emulator", "shell", "settings", "delete", "system", "integration_phone_committed")
            adb("emulator", "shell", "settings", "delete", "system", "integration_phone_channel")
            adb("emulator", "shell", "rm", "-rf",
                "/storage/emulated/0/Android/data/com.sofatype.ime/files/integration")
            log("marker 与设备侧 integration 产物已清（%s）" % tag)
            phone = spawn_bg(
                ["python3", os.path.join(TOOLS_INTEGRATION, "phone_driver.py"),
                 "--device", "emulator", "--text", "客厅远程集成验证", "--timeout", "420"],
                os.path.join(SHOTS, "logs", "phone-%s.log" % tag))
            code, _ = stage_instrument("emulator", tag)
            if phone.poll() is None:
                phone.wait(timeout=300)
            log("phone 驱动退出码：%s" % phone.poll())
            pulled = pull_device_output("emulator", tag)
            channels[tag] = read_channel("emulator", tag)
            rows = read_results(pulled)
            notes.append("round%d：instrument code=%d，场景 %d 项" % (round_no, code, len(rows)))
            assets_missing = any(
                not os.path.isfile(os.path.join(GOLDEN_ASSET_DIR, name))
                for name in ("s01_keyboard_show.png", "s04_keyboard_candidates.png",
                             "s06_keyboard_symbols.png", "s11_settings_page.png")
            )
            if round_no == 1 and (any(r[1] == "GOLDEN_RECORDED" for r in rows) or assets_missing):
                stage_goldens(pulled)
    finally:
        services.stop()
    duration = "emulator 双轮 %.1f 分钟" % ((time.time() - started) / 60)
    build_report(rows_by_round=stage_report_collect(), duration_note=duration,
                 out_path=os.path.join(SHOTS, "integration-report.md"), channels=channels)
    log("报告已落：%s" % os.path.join(SHOTS, "integration-report.md"))
    stage_teardown("emulator")
    log("rounds 完成：%s" % "; ".join(notes))


def stage_full(args):
    ensure_no_emulator()
    stage_boot("emulator")
    stage_install("emulator")
    stage_ime_check("emulator")
    stage_rounds(assume_ready=True)


def main():
    parser = argparse.ArgumentParser(description="集成测试宿主 wrapper")
    parser.add_argument("--stage", default="full")
    parser.add_argument("--device", default="emulator", choices=["emulator", "mitv4"])
    parser.add_argument("--host", help="真机 adb 地址 ip:5555（mitv4 段用）")
    parser.add_argument("--confirm-tap", help="安装许可确认钮坐标 x,y（screencap 目测后传入）")
    parser.add_argument("--assume-ready", action="store_true",
                        help="rounds 阶段跳过 boot/install/ime-check（当前实例已就绪时使用）")
    parser.add_argument("--scenario", help="定向单场景（如 s10_remote_pairing），配合 --stage instrument")
    parser.add_argument("--scenarios", help="逗号分隔多场景定向（如 s01_keyboard_show,s04_composition_candidates），配合 --stage instrument")
    args = parser.parse_args()

    os.makedirs(os.path.join(SHOTS, "logs"), exist_ok=True)
    stage = args.stage
    if stage == "boot":
        stage_boot(args.device)
    elif stage == "install":
        stage_install(args.device, args.host, args.confirm_tap)
    elif stage == "ime-check":
        stage_ime_check(args.device, args.host)
    elif stage == "instrument":
        stage_instrument(args.device, "manual", scenario=args.scenario, scenarios=args.scenarios)
    elif stage == "phone":
        subprocess.run(["python3", os.path.join(TOOLS_INTEGRATION, "phone_driver.py"),
                        "--device", args.device], check=True)
    elif stage == "mitv4-plan":
        stage_mitv4_plan()
    elif stage == "mitv4-preflight":
        stage_mitv4_preflight(args.host)
    elif stage == "mitv4-install":
        stage_install("mitv4", args.host, args.confirm_tap)
    elif stage == "teardown":
        stage_teardown(args.device)
    elif stage == "rounds":
        stage_rounds(assume_ready=args.assume_ready)
    elif stage == "full":
        stage_full(args)
    else:
        parser.error("未知 stage：%s" % stage)


if __name__ == "__main__":
    main()
