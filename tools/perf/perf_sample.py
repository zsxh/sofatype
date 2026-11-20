#!/usr/bin/env python3
"""性能回归 · 宿主侧采样编排（issue T92 口径 3/7/10：宿主只做采样编排与日志收集，预算断言在设备端 instrument 内完成）。

子命令：
  ws-driver   轮询设备 settings system 表 perf_remote_url（instrument p02 写入），python3 标准 WS
              直连配对后，按 >=2s 间隔连发指定轮数 commit（每轮产生一条 ws_commit PerfTrace 样本），
              结束写 marker：perf_ws_done=1、perf_ws_count=<ack 成功轮数>（失败也写，instrument 据此如实判 FAIL）。
  collect     adb logcat -d -s PerfTrace IntegrationScenario 落盘，作为 perf_report.py 输入。
  phone-e2e   手机段 E2E 测量（口径 3）：真机优先本地 headless 浏览器驱动 H5 页（页面打开 -> 页面 WS
              已连接 = 可输入，计时 phoneSegmentMs），摄像头扫码段不自动化；模拟器因 slirp 隧道与
              浏览器式页面加载不兼容（HANDOFF 坑 21），只做 python3 WS 直连等价通路逻辑核验，
              输出 logicOnly=true，不作为性能结论。

依赖：python3（标准库）+ adb；真机浏览器路径可选本机 chrome/chromium/brave（检测逻辑复用 tools/integration）。
真机（mitv4）目标一律需 --i-have-owner-approval 显式批准（口径：真机操作先询问户主），本脚本不含
装机/注入动作，仅经 adb 读写 settings 与 logcat。
退出码 0 = 采样闭环完成；非 0 = 失败（串行重试，不轰炸）。
"""
import argparse
import base64
import functools
import json
import os
import re
import socket
import statistics
import struct
import subprocess
import sys
import time
import urllib.request

from datetime import datetime

TOOLS_INTEGRATION = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "integration")
sys.path.insert(0, TOOLS_INTEGRATION)
from phone_driver import adb, adb_out, wait_page_ready, poll_page_connected  # noqa: E402  integration 既有通道（HOME 重定向封装）

_print = functools.partial(print, flush=True)


def log(*a, **kw):
    _print("[%s][perf]" % datetime.now().strftime("%H:%M:%S.%f")[:-3], *a, **kw)


MARKER_URL = "perf_remote_url"
MARKER_DONE = "perf_ws_done"
MARKER_COUNT = "perf_ws_count"
TUNNEL_HOST_PORT = 31000


def settings_get(device, key):
    return adb_out(device, "shell", "settings get system " + key)


def settings_put(device, key, value):
    adb_out(device, "shell", "settings put system %s %s" % (key, value))


def settings_delete(device, key):
    adb_out(device, "shell", "settings delete system " + key)


def redir_add(device, guest_port):
    adb_out(device, "emu", "redir", "add", "tcp:%d:%d" % (TUNNEL_HOST_PORT, guest_port))


def redir_del(device):
    try:
        adb_out(device, "emu", "redir", "del", "tcp:%d" % TUNNEL_HOST_PORT)
    except Exception as exc:
        log("redir del 失败（不阻断）：%s" % exc)


def adb_forward_add(device, guest_port):
    try:
        adb_out(device, "forward", "--remove", "tcp:%d" % TUNNEL_HOST_PORT)
    except Exception:
        pass
    adb_out(device, "forward", "tcp:%d:tcp:%d" % (TUNNEL_HOST_PORT, guest_port))


def adb_forward_del(device):
    try:
        adb_out(device, "forward", "--remove", "tcp:%d" % TUNNEL_HOST_PORT)
    except Exception as exc:
        log("forward remove 失败（不阻断）：%s" % exc)


def poll_remote_url(device, timeout_s):
    log("轮询 settings %s（最长 %ds）" % (MARKER_URL, timeout_s))
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        raw = settings_get(device, MARKER_URL)
        if raw.startswith("http://"):
            return raw
        time.sleep(2)
    raise RuntimeError("限时内未读到 %s marker（instrument p02 是否已起远程服务？）" % MARKER_URL)


def parse_url(url):
    port = int(url.split(":", 3)[2].split("/")[0])
    token = url.split("token=")[1].split("/")[0]
    host = url.split("http://")[1].split(":")[0]
    return host, port, token


def ensure_tunnel(device, guest_port, budget_s=40):
    deadline = time.time() + budget_s
    attempt = 0
    while time.time() < deadline:
        attempt += 1
        try:
            with urllib.request.urlopen(
                    "http://127.0.0.1:%d/?t92probe=1" % TUNNEL_HOST_PORT, timeout=6) as resp:
                if "<" in resp.read().decode("utf-8", "replace"):
                    log("隧道探测通过（第 %d 次）" % attempt)
                    return
        except Exception as exc:
            log("隧道探测第 %d 次失败：%s" % (attempt, exc))
        time.sleep(2)
        try:
            redir_del(device)
            redir_add(device, guest_port)
        except Exception as exc:
            log("redir 重建失败：%s" % exc)
    raise RuntimeError("隧道 %d 秒内不可达（宿主 %d -> guest %d）" % (budget_s, TUNNEL_HOST_PORT, guest_port))


class WsClient(object):
    def __init__(self, host, port, handshake_timeout=10):
        self.sock = socket.create_connection((host, port), timeout=handshake_timeout)
        key = base64.b64encode(os.urandom(16)).decode()
        self.sock.sendall((
            "GET /ws HTTP/1.1\r\nHost: %s:%d\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            "Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\n\r\n" % (host, port, key)
        ).encode())
        self.sock.settimeout(handshake_timeout)
        buf = b""
        while b"\r\n\r\n" not in buf:
            chunk = self.sock.recv(4096)
            if not chunk:
                raise RuntimeError("握手阶段连接关闭")
            buf += chunk
        head = buf.partition(b"\r\n\r\n")[0].split(b"\r\n")[0].decode()
        if "101" not in head:
            raise RuntimeError("WS 握手响应异常：%s" % head)

    def send_obj(self, obj):
        raw = json.dumps(obj, ensure_ascii=False).encode()
        mask = os.urandom(4)
        header = bytearray([0x81])
        n = len(raw)
        if n < 126:
            header.append(0x80 | n)
        else:
            header.append(0x80 | 126)
            header += struct.pack(">H", n)
        header += mask
        self.sock.sendall(bytes(header) + bytes(b ^ mask[i % 4] for i, b in enumerate(raw)))

    def recv_obj(self, timeout_s):
        self.sock.settimeout(timeout_s)
        head = self.sock.recv(2)
        if len(head) < 2:
            raise EOFError("连接关闭")
        ln = head[1] & 0x7F
        if ln == 126:
            ln = struct.unpack(">H", self.sock.recv(2))[0]
        elif ln == 127:
            ln = struct.unpack(">Q", self.sock.recv(8))[0]
        data = b""
        while len(data) < ln:
            chunk = self.sock.recv(ln - len(data))
            if not chunk:
                raise EOFError("连接关闭")
            data += chunk
        return json.loads(data.decode())

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass


def ws_commit_round(host, port, token, commits, rhythm_s, text, ack_timeout_s=15):
    client = WsClient(host, port)
    acks = 0
    try:
        client.send_obj({"type": "hello", "token": token, "deviceName": "perf-driver",
                         "deviceType": "browser", "deviceKey": "perf-sample-driver"})
        state = client.recv_obj(ack_timeout_s)
        if not state.get("connected"):
            raise RuntimeError("配对被拒：%s" % state)
        log("已配对（clientCode=%s），开始 %d 轮 commit，间隔 %.1fs" % (state.get("clientCode"), commits, rhythm_s))
        for i in range(commits):
            t0 = time.time()
            client.send_obj({"type": "commit", "text": text})
            ack = client.recv_obj(ack_timeout_s)
            if not ack.get("ok"):
                raise RuntimeError("commit 回执 ok:false（第 %d 轮）" % (i + 1))
            acks += 1
            log("commit %d/%d ack ok（宿主往返 %.0fms，非性能结论）" % (i + 1, commits, (time.time() - t0) * 1000))
            time.sleep(rhythm_s)
    finally:
        client.close()
    return acks


def require_owner_approval(args):
    if args.device == "mitv4" and not args.i_have_owner_approval:
        raise SystemExit("真机目标需户主批准：加 --i-have-owner-approval（先询问户主，口径 T92/6）")


def ws_driver(args):
    require_owner_approval(args)
    device = args.device
    url = poll_remote_url(device, args.timeout)
    time.sleep(3)
    again = settings_get(device, MARKER_URL)
    if again.startswith("http://") and again != url:
        log("marker 覆盖窗口检测：以新值为准")
        url = again
    host, guest_port, token = parse_url(url)
    tunnel = device == "emulator"
    ws_host = "127.0.0.1" if tunnel else host
    ws_port = guest_port
    if tunnel:
        redir_add(device, guest_port)
        ws_port = TUNNEL_HOST_PORT
        log("emu redir 已加：宿主 %d -> guest %d" % (TUNNEL_HOST_PORT, guest_port))
        ensure_tunnel(device, guest_port)
    acks = 0
    failure = None
    for attempt in range(1, args.retries + 1):
        try:
            acks = ws_commit_round(ws_host, ws_port, token, args.commits, args.rhythm, args.text)
            failure = None
            break
        except Exception as exc:
            failure = exc
            log("ws 采样第 %d 次失败：%s" % (attempt, exc))
            if tunnel and attempt % 2 == 0:
                redir_del(device)
                redir_add(device, guest_port)
                log("redir 已重建（slirp 单连接 reset 缓解，串行重试）")
            time.sleep(3)
    settings_put(device, MARKER_DONE, "1")
    settings_put(device, MARKER_COUNT, str(acks))
    log("marker 已写：%s=1 %s=%d" % (MARKER_DONE, MARKER_COUNT, acks))
    if tunnel:
        redir_del(device)
    if failure is not None:
        raise failure
    return 0


def collect(args):
    require_owner_approval(args)
    out = adb_out(args.device, "shell", "logcat -d -s PerfTrace IntegrationScenario")
    with open(args.out, "w") as fh:
        fh.write(out)
        if not out.endswith("\n"):
            fh.write("\n")
    lines = out.splitlines()
    perf = sum(1 for l in lines if "PerfTrace:" in l)
    log("logcat 已落盘 %s（PerfTrace 行 %d，总行 %d）" % (args.out, perf, len(lines)))
    return 0


def detect_browser_local():
    sys.path.insert(0, TOOLS_INTEGRATION)
    from phone_driver import detect_browser
    return detect_browser()


def phone_e2e(args):
    device = args.device
    require_owner_approval(args)
    url = poll_remote_url(device, args.timeout)
    host, guest_port, token = parse_url(url)
    tunnel = device == "emulator"
    record = {"device": device, "sampledAt": datetime.now().isoformat(timespec="seconds")}
    page_url = url
    ws_port = guest_port
    ws_host = host
    if tunnel:
        redir_del(device)
        redir_add(device, guest_port)
        ws_port = TUNNEL_HOST_PORT
        try:
            ensure_tunnel(device, guest_port)
            record["tunnel"] = "emu redir %d->%d" % (TUNNEL_HOST_PORT, guest_port)
        except Exception as exc:
            log("emu redir 不可达（%s），改走 adb forward 等价通路重试" % exc)
            redir_del(device)
            adb_forward_add(device, guest_port)
            ensure_tunnel(device, guest_port)
            record["tunnel"] = "adb forward %d->%d（emu redir 不可达后回退）" % (TUNNEL_HOST_PORT, guest_port)
        page_url = "http://127.0.0.1:%d/?token=%s" % (TUNNEL_HOST_PORT, token)
        ws_host = "127.0.0.1"

    use_browser = device != "emulator"
    browser = None
    session = None
    target_id = None
    try:
        if use_browser:
            info = detect_browser_local()
            if not info:
                raise RuntimeError("本机未检出 chrome/chromium/brave，真机浏览器路径不可用（手机段 E2E 需浏览器通道）")
            name, binary, version = info
            log("本地浏览器命中：%s（%s）" % (name, version))
            browser = __import__("phone_driver").BrowserInstance(name, binary, version)
            if not browser.start():
                browser.stop()
                raise RuntimeError("浏览器实例启动失败")
            time.sleep(8)
            t0 = time.time()
            session, target_id = browser.open_page(page_url)
            wait_page_ready(session)
            connected = False
            deadline = time.time() + 30
            while time.time() < deadline:
                connected, detail = poll_page_connected(session, 10)
                if connected:
                    break
            if not connected:
                raise RuntimeError("页面 WS %d 秒内未连（%s）" % (30, detail))
            t1 = time.time()
            record.update({
                "channel": "%s（%s）页面自身 WS + H5 UI 全链" % (name, version),
                "logicOnly": False,
                "phoneSegmentMs": round((t1 - t0) * 1000, 1),
                "note": "页面打开 -> 页面 WS 已连接（可输入）",
            })
            submit_via_page(session, args.text)
            log("手机段 E2E 采样完成：phoneSegmentMs=%.0f" % record["phoneSegmentMs"])
        else:
            t0 = time.time()
            client = WsClient(ws_host, ws_port)
            client.send_obj({"type": "hello", "token": token, "deviceName": "perf-driver",
                             "deviceType": "browser", "deviceKey": "perf-sample-driver"})
            state = client.recv_obj(15)
            t1 = time.time()
            if not state.get("connected"):
                client.close()
                raise RuntimeError("配对被拒：%s" % state)
            client.send_obj({"type": "commit", "text": args.text})
            ack = client.recv_obj(15)
            t2 = time.time()
            client.close()
            if not ack.get("ok"):
                raise RuntimeError("commit 回执 ok:false")
            record.update({
                "channel": "python3 标准 WS 直连等价通路（模拟器 slirp 与浏览器式页面加载不兼容，HANDOFF 坑 21）",
                "logicOnly": True,
                "connectPairMs": round((t1 - t0) * 1000, 1),
                "firstCommitAckMs": round((t2 - t1) * 1000, 1),
                "note": "模拟器逻辑核验，非浏览器测量，不作为性能结论；浏览器优先路径保留真机（T91 既有口径）",
            })
            log("模拟器 WS 直连逻辑核验完成：connectPairMs=%.0f（logicOnly）" % record["connectPairMs"])
    finally:
        if session is not None and target_id is not None and browser is not None:
            browser.close_page(target_id)
            session.close()
        if browser is not None:
            browser.stop()
        if tunnel:
            redir_del(device)
            adb_forward_del(device)

    with open(args.out, "w") as fh:
        json.dump(record, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    log("phone-e2e 记录已落盘：%s" % args.out)
    return 0


def phone_e2e_cycles(args):
    device = args.device
    require_owner_approval(args)
    if device == "emulator":
        raise SystemExit("--cycles 仅支持真机浏览器路径（模拟器 slirp 与浏览器式页面加载不兼容，HANDOFF 坑 21）")
    info = detect_browser_local()
    if not info:
        raise SystemExit("本机未检出 chrome/chromium/brave，真机浏览器路径不可用（手机段 E2E 需浏览器通道）")
    name, binary, version = info
    log("本地浏览器命中：%s（%s）" % (name, version))
    browser = __import__("phone_driver").BrowserInstance(name, binary, version)
    if not browser.start():
        browser.stop()
        raise SystemExit("浏览器实例启动失败")
    samples = []
    session = None
    target_id = None
    qr_captured = False
    try:
        time.sleep(8)
        for cycle in range(1, args.cycles + 1):
            adb_out(device, "shell",
                    "am broadcast -n com.sofatype.ime/.remote.RemoteServerDebugReceiver "
                    "-a com.sofatype.ime.debug.action.STOP_REMOTE_SERVER")
            time.sleep(2)
            adb_out(device, "shell",
                    "am broadcast -n com.sofatype.ime/.remote.RemoteServerDebugReceiver "
                    "-a com.sofatype.ime.debug.action.START_REMOTE_SERVER")
            time.sleep(4)
            logtext = adb_out(device, "shell", "logcat -d -s RemoteInputServer")
            urls = re.findall(r"配对地址：(http://[^\s（]+)", logtext)
            if not urls:
                log("第 %d 轮未读到配对地址，跳过" % cycle)
                samples.append({"cycle": cycle, "ok": False, "detail": "no_pairing_url"})
                continue
            url = urls[-1]
            host, guest_port, token = parse_url(url)
            t0 = time.time()
            session, target_id = browser.open_page(url)
            wait_page_ready(session)
            connected = False
            detail = ""
            deadline = time.time() + 30
            while time.time() < deadline:
                connected, detail = poll_page_connected(session, 10)
                if connected:
                    break
            t1 = time.time()
            if connected:
                ms = round((t1 - t0) * 1000, 1)
                samples.append({"cycle": cycle, "ok": True, "phoneSegmentMs": ms})
                log("第 %d 轮 phoneSegmentMs=%.0f" % (cycle, ms))
            else:
                samples.append({"cycle": cycle, "ok": False, "detail": detail})
                log("第 %d 轮未连（%s）" % (cycle, detail))
            if session is not None and target_id is not None:
                browser.close_page(target_id)
                session.close()
                session = None
                target_id = None
            if not qr_captured:
                try:
                    adb_out(device, "shell", "screencap -p /data/local/tmp/perfb/e2e-qr.png", timeout=60)
                    qr_local = args.out.rsplit("/", 1)[0] + "/e2e-qr-tv.png"
                    adb_out(device, "pull", "/data/local/tmp/perfb/e2e-qr.png", qr_local, timeout=180)
                    adb_out(device, "shell", "rm -f /data/local/tmp/perfb/e2e-qr.png")
                    qr_captured = True
                except Exception as exc:
                    log("二维码截图留档失败（不阻断测量）：%s" % exc)
            time.sleep(2)
    finally:
        adb_out(device, "shell",
                "am broadcast -n com.sofatype.ime/.remote.RemoteServerDebugReceiver "
                "-a com.sofatype.ime.debug.action.STOP_REMOTE_SERVER")
        if session is not None and target_id is not None and browser is not None:
            browser.close_page(target_id)
            session.close()
        if browser is not None:
            browser.stop()

    ok_values = [s["phoneSegmentMs"] for s in samples if s.get("ok")]
    record = {
        "device": device,
        "sampledAt": datetime.now().isoformat(timespec="seconds"),
        "channel": "%s（%s）页面自身 WS，页面打开 -> 页面 WS 已连接（可输入）" % (name, version),
        "logicOnly": False,
        "cycles": args.cycles,
        "okSamples": len(ok_values),
        "phoneSegmentMs": round(statistics.median(ok_values), 1) if ok_values else None,
        "samples": samples,
        "note": "每轮使用电视端服务签发的配对 URL（与电视二维码渲染同源 pairingUrlText，截图 e2e-qr-tv.png 留人工核验）；"
                "摄像头扫码段不自动化（口径 3）；首轮含浏览器冷启动伪影时由中位数消解",
    }
    with open(args.out, "w") as fh:
        json.dump(record, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    log("phone-e2e 记录已落盘：%s（ok %d/%d，median=%s）" % (
        args.out, len(ok_values), args.cycles, record["phoneSegmentMs"]))
    return 0


def main():
    parser = argparse.ArgumentParser(description="T92 宿主侧采样编排")
    sub = parser.add_subparsers(dest="mode", required=True)

    p_ws = sub.add_parser("ws-driver", help="WS commit 采样驱动（与 instrument p02 并发）")
    p_ws.add_argument("--device", default="emulator", choices=["emulator", "mitv4"])
    p_ws.add_argument("--commits", type=int, default=20)
    p_ws.add_argument("--rhythm", type=float, default=2.2)
    p_ws.add_argument("--text", default="性能采样")
    p_ws.add_argument("--timeout", type=int, default=240)
    p_ws.add_argument("--retries", type=int, default=5)
    p_ws.add_argument("--i-have-owner-approval", action="store_true")

    p_co = sub.add_parser("collect", help="logcat 取证落盘")
    p_co.add_argument("--device", default="emulator", choices=["emulator", "mitv4"])
    p_co.add_argument("--out", required=True)
    p_co.add_argument("--i-have-owner-approval", action="store_true")

    p_e2e = sub.add_parser("phone-e2e", help="手机段 E2E 测量（真机浏览器优先，模拟器仅逻辑核验）")
    p_e2e.add_argument("--device", default="emulator", choices=["emulator", "mitv4"])
    p_e2e.add_argument("--text", default="性能采样")
    p_e2e.add_argument("--timeout", type=int, default=240)
    p_e2e.add_argument("--out", required=True)
    p_e2e.add_argument("--cycles", type=int, default=1)
    p_e2e.add_argument("--i-have-owner-approval", action="store_true")

    args = parser.parse_args()
    if args.mode == "ws-driver":
        return ws_driver(args)
    if args.mode == "collect":
        return collect(args)
    if args.mode == "phone-e2e":
        if getattr(args, "cycles", 1) > 1:
            return phone_e2e_cycles(args)
        return phone_e2e(args)
    return 2


if __name__ == "__main__":
    sys.exit(main())
