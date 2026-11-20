#!/usr/bin/env python3
"""T91 · 手机端模拟驱动（本地 headless 浏览器优先 + python3 WS 回退，宿主侧）。

与 instrumented 场景 s10_remote_pairing 协作：
  1. 轮询设备侧 settings system 表 marker t91_remote_url（instrument 写入的本机可达配对地址）；
  2. 模拟器经 adb emu redir 把宿主 31000 转发到设备服务端口（真机为 LAN 直连，无隧道）；
  3. 浏览器分支仅真机路径启用：模拟器 slirp 隧道对浏览器式并发连接不兼容（页面资源并发加载
     即触发连接级 reset 并污染隧道，redir 重建不可恢复，实测证据见 T91 报告），模拟器跳过
     浏览器检测、直接走 python3 标准 WS 单连接串行直连；真机按户主拍板顺序检测本机
     chrome / chromium / brave（macOS .app 与 PATH 双查，含 headless 变体），第一个能成功
     启动 headless 实例的胜出：开 H5 页 + DOM 探针 + 页面自身 WS（H5 UI 全链，
     15 秒短时限，超时快速降级，不做长等待）；
  4. 浏览器未检出、启动失败或页面 WS 超时 → 回退 python3 标准 WS 客户端直连（配对/输入/commit）；
  5. 配对与上屏闭环完成后把上屏文本写入 settings system 表 marker t91_phone_committed，
     通道描述写入 t91_phone_channel，instrument 侧据此断言输入框内容，报告如实记录通道。

浏览器为本场景本地工具、非 flake 工具链（户主拍板放开）；obscura 已从 s10 链路移出。

用法：
  python3 tools/t91/t91_phone.py --device emulator --text "客厅远程T91" --timeout 240
依赖：python3（标准库）、adb；headless 浏览器（可选，缺失即走 python3 回退）。
退出码 0 = 配对与上屏闭环完成；非 0 = 失败（不轰炸重试）。
"""
import argparse
import base64
import json
import os
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import time
import urllib.request

import functools
from datetime import datetime

_print = functools.partial(print, flush=True)


def print(*a, **kw):
    _print("[%s]" % datetime.now().strftime("%H:%M:%S.%f")[:-3], *a, **kw)

ADB_HOME_EMU = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), ".emulator-adbhome")
BROWSER_PORT = 9333
PAGE_WS_TIMEOUT_S = 15


def adb(device, *args, timeout=30):
    env = os.environ.copy()
    if device == "emulator":
        env["HOME"] = ADB_HOME_EMU
    return subprocess.run(["adb", *args], env=env, capture_output=True, text=True, timeout=timeout)


def adb_out(device, *args, timeout=30):
    result = adb(device, *args, timeout=timeout)
    if result.returncode != 0:
        raise RuntimeError("adb %s 失败：%s%s" % (" ".join(args), result.stdout, result.stderr))
    return result.stdout.strip()


BROWSER_CANDIDATES = [
    ("chrome", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"),
    ("chrome", "google-chrome"),
    ("chrome", "google-chrome-stable"),
    ("chrome", "chrome-headless-shell"),
    ("chromium", "/Applications/Chromium.app/Contents/MacOS/Chromium"),
    ("chromium", "chromium"),
    ("chromium", "chromium-browser"),
    ("brave", "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser"),
    ("brave", "brave"),
    ("brave", "brave-browser"),
]


def detect_browser():
    for name, binary in BROWSER_CANDIDATES:
        path = binary if os.path.isabs(binary) else shutil.which(binary)
        if not path or not os.path.isfile(path):
            continue
        try:
            out = subprocess.run([path, "--version"], capture_output=True, text=True, timeout=15)
            version = (out.stdout or out.stderr).strip().splitlines()[0] if (out.stdout or out.stderr).strip() else ""
            if out.returncode == 0 and version:
                return name, path, version
        except Exception as exc:
            print("[phone] 候选 %s（%s）探测失败：%s" % (name, path, exc))
    return None


class BrowserInstance(object):
    def __init__(self, name, binary, version):
        self.name = name
        self.binary = binary
        self.version = version
        self.user_data_dir = tempfile.mkdtemp(prefix="t91-phone-browser-")
        self.port = BROWSER_PORT
        self.proc = None

    def start(self):
        self.proc = subprocess.Popen(
            [self.binary, "--headless=new", "--no-sandbox", "--use-mock-keychain",
             "--user-data-dir=" + self.user_data_dir,
             "--remote-debugging-port=%d" % self.port, "about:blank"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            stdin=subprocess.DEVNULL, start_new_session=True)
        deadline = time.time() + 20
        while time.time() < deadline:
            try:
                urllib.request.urlopen("http://127.0.0.1:%d/json/version" % self.port, timeout=3).read()
                return True
            except Exception:
                time.sleep(1)
        return False

    def open_page(self, url):
        endpoint = "http://127.0.0.1:%d/json/new?%s" % (self.port, url)
        req = urllib.request.Request(endpoint, method="PUT")
        data = json.loads(urllib.request.urlopen(req, timeout=15).read().decode())
        return CdpEval(data["webSocketDebuggerUrl"], timeout=60), data.get("id")

    def close_page(self, target_id):
        try:
            session = CdpEval("ws://127.0.0.1:%d/devtools/browser" % self.port)
            session.call("Target.closeTarget", {"targetId": target_id})
        except Exception as exc:
            print("[phone] Target.closeTarget 失败（不阻断）：%s" % exc)

    def stop(self):
        if self.proc:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=10)
            except Exception:
                subprocess.run(["pkill", "-f", "user-data-dir=" + self.user_data_dir], capture_output=True)
        shutil.rmtree(self.user_data_dir, ignore_errors=True)
        try:
            s = socket.create_connection(("127.0.0.1", self.port), timeout=2)
            s.close()
            print("[phone][WARN] 浏览器调试端口 %d 仍可连（残留复核不通过）" % self.port)
        except OSError:
            pass


class CdpEval(object):
    def __init__(self, ws_url, timeout=60):
        self.sock = self._connect(ws_url)
        self.sock.settimeout(timeout)
        self.next_id = 0

    @staticmethod
    def _connect(ws_url, timeout=15):
        assert ws_url.startswith("ws://")
        rest = ws_url[5:]
        hostport, path = rest.split("/", 1)
        path = "/" + path
        host, port = hostport.split(":")
        sock = socket.create_connection((host, int(port)), timeout=timeout)
        key = base64.b64encode(os.urandom(16)).decode()
        handshake = (
            "GET %s HTTP/1.1\r\nHost: %s\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
            "Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\n\r\n" % (path, hostport, key)
        )
        sock.sendall(handshake.encode())
        reader = _LineReader(sock)
        status = reader.readline().strip()
        parts = status.split(" ", 2)
        if len(parts) < 2 or "101" not in parts[1]:
            raise RuntimeError("CDP 握手失败：%s" % status)
        while True:
            line = reader.readline()
            if line in ("\r\n", "\n", ""):
                break
        sock.settimeout(timeout)
        return sock

    def eval_js(self, expression, timeout=30, flat_session=None):
        result = self.call(
            "Runtime.evaluate",
            {"expression": expression, "returnByValue": True},
            session=flat_session,
            timeout=timeout,
        )
        return result.get("result", {}).get("value")

    def call(self, method, params=None, session=None, timeout=30):
        self.next_id += 1
        msg_id = self.next_id
        payload = {"id": msg_id, "method": method, "params": params or {}}
        if session:
            payload["sessionId"] = session
        self._send_text(json.dumps(payload))
        deadline = time.time() + timeout
        while time.time() < deadline:
            frame = self._recv_frame()
            if frame is None:
                raise RuntimeError("CDP 连接关闭")
            data = json.loads(frame.decode("utf-8"))
            if data.get("id") != msg_id:
                continue
            if "error" in data:
                raise RuntimeError("CDP error：%s" % data["error"])
            return data.get("result", {})
        raise RuntimeError("CDP call 超时：%s" % method)

    def close(self):
        try:
            self.sock.close()
        except OSError:
            pass

    def _send_text(self, text):
        raw = text.encode("utf-8")
        header = bytearray([0x81])
        mask = os.urandom(4)
        n = len(raw)
        if n < 126:
            header.append(0x80 | n)
        elif n < 65536:
            header.append(0x80 | 126)
            header += struct.pack(">H", n)
        else:
            header.append(0x80 | 127)
            header += struct.pack(">Q", n)
        header += mask
        masked = bytes(b ^ mask[i % 4] for i, b in enumerate(raw))
        self.sock.sendall(bytes(header) + masked)

    def _recv_frame(self):
        head = self._recv_exact(2)
        if head is None:
            return None
        opcode = head[0] & 0x0F
        length = head[1] & 0x7F
        if length == 126:
            length = struct.unpack(">H", self._recv_exact(2))[0]
        elif length == 127:
            length = struct.unpack(">Q", self._recv_exact(8))[0]
        payload = self._recv_exact(length) if length else b""
        if opcode == 0x8:
            return None
        if opcode == 0x9:
            self.sock.sendall(b"\x8a\x80" + os.urandom(4))
            return self._recv_frame()
        return payload

    def _recv_exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                return None if not buf else buf
            buf += chunk
        return buf


class _LineReader(object):
    def __init__(self, sock):
        self.sock = sock
        self.buffer = b""

    def readline(self):
        while b"\n" not in self.buffer:
            chunk = self.sock.recv(4096)
            if not chunk:
                return self.buffer.decode("utf-8", "replace")
            self.buffer += chunk
        line, self.buffer = self.buffer.split(b"\n", 1)
        return (line + b"\n").decode("utf-8", "replace")


def wait_page_ready(session, timeout_s=120):
    deadline = time.time() + timeout_s
    last = None
    while time.time() < deadline:
        try:
            present = session.eval_js(
                "document.getElementById('status-text') !== null", timeout=5)
            if present is True:
                return True
            last = present
        except Exception as exc:
            last = str(exc)
        time.sleep(1)
    raise RuntimeError("页面执行上下文 %d 秒未就绪（status-text 未见，最后状态=%r）" % (timeout_s, last))


def poll_page_connected(session, window_s):
    """短窗轮询页面 WS 状态。返回 (已连接, 状态文本或原因)。"""
    js_status = "document.getElementById('status-text') === null ? null : document.getElementById('status-text').textContent"
    t0 = time.time()
    while time.time() - t0 < window_s:
        cur = session.eval_js(js_status, timeout=10)
        if cur:
            if "已连接" in cur:
                return True, cur
            if "配对失败" in cur or "重新扫码" in cur:
                return False, "rejected:%s" % cur
            if "连接失败" in cur:
                return False, "closed:%s" % cur
        time.sleep(1)
    cur = session.eval_js(js_status, timeout=10)
    return False, "timeout:%r" % cur


def submit_via_page(session, text):
    """H5 UI 全链提交：textarea 注入 → 上屏按钮 → 回执清空确认。"""
    inject = ("var ta=document.getElementById('ghost-input');"
              "ta.value=%s;"
              "ta.dispatchEvent(new Event('input'));"
              "'ok'" % json.dumps(text, ensure_ascii=False))
    assert session.eval_js(inject, timeout=10) == "ok"
    print("[phone] 已注入文本并触发 input")
    time.sleep(2)
    session.eval_js("document.getElementById('btn-commit').click()", timeout=10)
    print("[phone] 已点击上屏")
    deadline = time.time() + 30
    while time.time() < deadline:
        value = session.eval_js("document.getElementById('ghost-input').value", timeout=10)
        if value == "":
            hint = session.eval_js("document.getElementById('limit-hint').textContent", timeout=10) or ""
            print("[phone] 上屏回执确认（textarea 已清空，hint=%r）" % hint)
            return True
        time.sleep(1)
    raise RuntimeError("上屏回执未确认（textarea 未清空）")


def pair_via_browser(browser, url, text):
    """headless 浏览器开 H5 页 + 页面自身 WS（就绪后 15 秒短时限）+ H5 UI 全链。

    返回 (session, target_id, 通道描述)；页面短窗未连时返回未连接标记，由调用方
    决定 python3 尝试与页面迟到兜底（页面 WS 经 slirp 可能迟到配对成功并消费 token）。
    """
    time.sleep(8)
    session, target_id = browser.open_page(url)
    wait_page_ready(session)
    page_t0 = time.time()
    connected, detail = poll_page_connected(session, PAGE_WS_TIMEOUT_S)
    if connected:
        print("[phone] 页面自身 WS 直连成功（%s，耗时 %.0fs）" % (detail, time.time() - page_t0))
        submit_via_page(session, text)
        return session, target_id, "%s（%s）页面自身 WS + H5 UI 全链" % (browser.name, browser.version)
    return session, target_id, "not_connected:%s" % detail


class TokenRotated(Exception):
    pass


def pair_via_python(token, text, ws_port, device="emulator", tunnel=False, retries=5,
                    url_setting=None, url_getter=None, host="127.0.0.1"):
    for attempt in range(1, retries + 1):
        sock = None
        try:
            sock = socket.create_connection((host, ws_port), timeout=10)
            key = base64.b64encode(os.urandom(16)).decode()
            sock.sendall((
                "GET /ws HTTP/1.1\r\nHost: %s:%d\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                "Sec-WebSocket-Key: %s\r\nSec-WebSocket-Version: 13\r\n\r\n" % (host, ws_port, key)
            ).encode())
            sock.settimeout(10)
            buf = b""
            while b"\r\n\r\n" not in buf:
                chunk = sock.recv(4096)
                if not chunk:
                    raise RuntimeError("握手阶段连接关闭")
                buf += chunk
            head = buf.partition(b"\r\n\r\n")[0].split(b"\r\n")[0].decode()
            if "101" not in head:
                raise RuntimeError("WS 握手响应异常：%s" % head)

            mask = os.urandom(4)

            def send_text(obj):
                raw = json.dumps(obj, ensure_ascii=False).encode()
                header = bytearray([0x81])
                n = len(raw)
                if n < 126:
                    header.append(0x80 | n)
                else:
                    header.append(0x80 | 126)
                    header += struct.pack(">H", n)
                header += mask
                sock.sendall(bytes(header) + bytes(b ^ mask[i % 4] for i, b in enumerate(raw)))

            def recv_text(timeout=8):
                sock.settimeout(timeout)
                head2 = sock.recv(2)
                if len(head2) < 2:
                    raise EOFError("closed")
                ln = head2[1] & 0x7F
                if ln == 126:
                    ln = struct.unpack(">H", sock.recv(2))[0]
                data = b""
                while len(data) < ln:
                    data += sock.recv(ln - len(data))
                return data.decode()

            send_text({"type": "hello", "token": token, "deviceName": "browser",
                       "deviceType": "browser", "deviceKey": "t91-emulator-phone"})
            state = json.loads(recv_text())
            if not state.get("connected"):
                fresh_full = url_getter().strip() if url_getter else ""
                fresh = fresh_full.split("token=")[-1].split("/")[0] if "token=" in fresh_full else ""
                if fresh and fresh != token:
                    raise TokenRotated("token 已滚动（旧=%s 新=%s）" % (token, fresh))
                raise RuntimeError("配对被拒：%s" % state)
            print("[phone] python3 WS 已配对：%s" % state.get("clientCode"))
            send_text({"type": "input", "text": text})
            time.sleep(1)
            send_text({"type": "commit", "text": text})
            ack = json.loads(recv_text())
            if not ack.get("ok"):
                raise RuntimeError("commit 回执 ok:false")
            print("[phone] python3 WS commit 回执 ok:true")
            sock.close()
            return "python3 标准 WS 客户端直连（浏览器回退）"
        except TokenRotated as exc:
            print("[phone] python3 WS 第 %d 次失败：%s → 取滚动新 token 重试" % (attempt, exc))
            if sock:
                try:
                    sock.close()
                except OSError:
                    pass
            token = url_getter().split("token=")[-1].split("/")[0]
            time.sleep(2)
        except Exception as exc:
            print("[phone] python3 WS 第 %d 次失败：%s" % (attempt, exc))
            if sock:
                try:
                    sock.close()
                except OSError:
                    pass
            if tunnel and attempt % 2 == 0:
                try:
                    adb_out(device, "emu", "redir", "del", "tcp:31000")
                    adb_out(device, "emu", "redir", "add", "tcp:31000:%d" % ws_port)
                    print("[phone] redir 已重建（slirp 并发 reset 缓解，串行重试）")
                except Exception as redir_exc:
                    print("[phone] redir 重建失败：%s" % redir_exc)
            time.sleep(3)
    raise RuntimeError("python3 WS 配对在 %d 次重试后未成功" % retries)


def ensure_tunnel_ready(page_url, budget_s=40):
    """HTTP 探测确认隧道通（slirp 单连接可能 reset），失败重建 redir 再试，串行不轰炸。"""
    deadline = time.time() + budget_s
    attempt = 0
    while time.time() < deadline:
        attempt += 1
        try:
            with urllib.request.urlopen(page_url, timeout=6) as response:
                body = response.read().decode("utf-8", "replace")
            if "<" in body:
                print("[phone] 隧道探测通过（第 %d 次，%d 字节）" % (attempt, len(body)))
                return
            print("[phone] 探测返回非 HTML（%d 字节）" % len(body))
        except Exception as exc:
            print("[phone] 隧道探测第 %d 次失败：%s" % (attempt, exc))
        time.sleep(2)
        try:
            adb_out("emulator", "emu", "redir", "del", "tcp:31000")
            adb_out("emulator", "emu", "redir", "add", "tcp:31000:53206")
        except Exception as redir_exc:
            print("[phone] redir 重建失败：%s" % redir_exc)
    raise RuntimeError("隧道 %d 秒内不可达（31000）" % budget_s)


def main():
    parser = argparse.ArgumentParser(description="T91 手机端模拟驱动（本地 headless 浏览器优先 + python3 WS 回退）")
    parser.add_argument("--device", default="emulator", choices=["emulator", "mitv4"])
    parser.add_argument("--marker-prefix", default="t91")
    parser.add_argument("--text", default="客厅远程T91")
    parser.add_argument("--timeout", type=int, default=240)
    args = parser.parse_args()

    marker_url = "settings:" + args.marker_prefix + "_remote_url"
    url_setting = args.marker_prefix + "_remote_url"
    commit_setting = args.marker_prefix + "_phone_committed"
    channel_setting = args.marker_prefix + "_phone_channel"

    print("[phone] 轮询 settings %s（最长 %ds）" % (url_setting, args.timeout))
    deadline = time.time() + args.timeout
    raw_url = ""
    while time.time() < deadline:
        result = adb(args.device, "shell", "settings get system " + url_setting)
        raw_url = result.stdout.strip()
        if raw_url.startswith("http://"):
            break
        time.sleep(2)
    if not raw_url.startswith("http://"):
        print("[phone][FAIL] 未读到 remote_url marker")
        return 2
    time.sleep(3)
    again = adb(args.device, "shell", "settings get system " + url_setting).stdout.strip()
    if again.startswith("http://") and again != raw_url:
        print("[phone] marker 覆盖窗口检测：以新值为准（%s → %s）" % (raw_url, again))
        raw_url = again
    print("[phone] 配对地址：%s" % raw_url)

    port = raw_url.split("127.0.0.1:")[1].split("/")[0] if "127.0.0.1:" in raw_url else \
        raw_url.split(":", 3)[2].split("/")[0]
    token = raw_url.split("token=")[1].split("/")[0]
    ws_host = "127.0.0.1" if args.device == "emulator" else raw_url.split("http://")[1].split(":")[0]

    tunnel = False
    if args.device == "emulator":
        host_port = 31000
        adb_out(args.device, "emu", "redir", "add", "tcp:%d:%s" % (host_port, port))
        tunnel = True
        print("[phone] emu redir 已加：宿主 %d -> guest %s" % (host_port, port))
        ws_port = host_port
        page_url = "http://127.0.0.1:%d/?token=%s" % (host_port, token)
    else:
        ws_port = int(port)
        page_url = raw_url

    channel = None
    browser = None
    session = None
    target_id = None
    use_browser = args.device != "emulator"
    if not use_browser:
        print("[phone] 模拟器：slirp 隧道对浏览器式并发连接不兼容（实测页面资源并发加载即触发连接级 reset 并污染隧道，"
              "redir 重建不可恢复）→ 跳过浏览器，python3 标准 WS 严格单连接串行直连（已证通路）")
        ensure_tunnel_ready(page_url, budget_s=30)
    if use_browser:
        browser_info = detect_browser()
        if not browser_info:
            print("[phone] 本机未检测到 chrome/chromium/brave → 直接 python3 WS")
        else:
            name, binary, version = browser_info
            print("[phone] 本地浏览器命中：%s（%s，%s）" % (name, binary, version))
            browser = BrowserInstance(name, binary, version)
            if not browser.start():
                print("[phone] 浏览器实例启动失败 → 降级 python3 WS")
                browser.stop()
                browser = None
    else:
        print("[phone] 模拟器场景跳过浏览器检测（slirp 隧道不兼容，见上）→ 直接 python3 WS")

    if browser is not None:
        try:
            session, target_id, result = pair_via_browser(browser, page_url, args.text)
            if not result.startswith("not_connected:"):
                channel = result
            else:
                print("[phone] 页面 WS %d 秒短窗未连（%s）→ python3 尝试 + 页面迟到兜底" % (
                    PAGE_WS_TIMEOUT_S, result))
                for attempt in (1, 2):
                    try:
                        channel = pair_via_python(token, args.text, ws_port,
                                                  device=args.device, tunnel=tunnel,
                                                  host=ws_host) + "；降级=是"
                        break
                    except Exception as exc:
                        print("[phone] python3 WS 尝试 %d 失败：%s" % (attempt, exc))
                    connected, detail = poll_page_connected(session, 10)
                    if connected:
                        print("[phone] 页面 WS 迟到连上（%s）→ 回到 H5 UI 提交" % detail)
                        submit_via_page(session, args.text)
                        channel = "%s（%s）页面自身 WS（迟到连上）+ H5 UI 全链" % (browser.name, browser.version)
                        break
        except Exception as exc:
            print("[phone] 浏览器链路失败 → 降级 python3 WS：%s" % exc)
        finally:
            if session is not None and target_id is not None:
                browser.close_page(target_id)
            if session is not None:
                session.close()
            browser.stop()
            session = None
            target_id = None
            browser = None

    if channel is None:
        if browser is not None:
            print("[phone] 浏览器路径未成 → 关浏览器 → python3 最终尝试（token 可能仍有效）")
        channel = pair_via_python(token, args.text, ws_port,
                                  device=args.device, tunnel=tunnel,
                                  url_setting=url_setting,
                                  url_getter=lambda: adb(args.device, "shell",
                                      "settings get system " + url_setting).stdout.strip(),
                                  host=ws_host) + "；降级=是"

    adb_out(args.device, "shell", "settings put system " + commit_setting + " " + args.text)
    channel_desc = channel
    adb_out(args.device, "shell", "settings put system " + channel_setting + " " + channel_desc.replace(" ", "_"))
    print("[phone] marker 已写：%s = %s；通道：%s" % (commit_setting, args.text, channel_desc))

    if session is not None and browser is not None:
        browser.close_page(target_id)
        session.close()
    if browser is not None:
        browser.stop()
    if tunnel:
        try:
            adb_out(args.device, "emu", "redir", "del", "tcp:31000")
        except Exception as exc:
            print("[phone] redir del 失败（收尾阶段模拟器即将销毁，不阻断）：%s" % exc)
    return 0


if __name__ == "__main__":
    sys.exit(main())
