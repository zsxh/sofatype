#!/usr/bin/env python3
"""T41 · 检查更新 mock 源（T21 评审建议③入库）：本地 HTTP 服务器模拟 GitHub Releases。

serve 本目录文件：GitHub Releases 字段格式 JSON（tag_name / body / draft / prerelease /
assets[].browser_download_url）+ APK 资产。JSON 占位符按请求动态改写——
__HOSTPORT__ 按请求 Host 头改写（模拟器走 10.0.2.2:<port>、真机走 <Mac 局域网 IP>:<port>）；
__VER_CUR__ / __VER_NEWER__ 按启动参数 --current-version / --newer-version 改写
（tag 派生规则 v<版本>，版本值由参数传入不硬编码，工程基线 bump 后起服务传新参即可）。
同一份 JSON 通吃，无需改文件（决策 17：更新源可配置不硬编码）。

用法：
  python3 tools/mock/github-releases/serve_mock.py \
      --current-version 0.2.0 --newer-version 0.3.0            # 默认 127.0.0.1:8000，模拟器经 10.0.2.2 访问
  python3 tools/mock/github-releases/serve_mock.py \
      --current-version 0.2.0 --newer-version 0.3.0 --bind 0.0.0.0   # 真机批次：电视访问 Mac 局域网 IP
  可选 --port <P>（默认 8000）。两版本参数必传；newer 须数值真大于 current，
  对应递增包 versionCode 必须真递增（口径见同目录 README.md）。

APK 响应限速 ~0.35 MB/s，供下载进度 UI 走查截图；JSON 即时返回。走完 Ctrl-C（或
pkill -f serve_mock.py）即停，不常驻、不残留端口；目录内只有假数据与测试 APK，
无用户输入内容（隐私红线不涉）。APK 不入库，放置方法见同目录 README.md。
"""
import argparse
import os
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = os.path.dirname(os.path.abspath(__file__))
RATE_LIMIT_EXT = ".apk"
JSON_EXT = ".json"
ATOM_EXT = ".xml"
HOSTPORT_PLACEHOLDER = "__HOSTPORT__"
VER_CUR_PLACEHOLDER = "__VER_CUR__"
VER_NEWER_PLACEHOLDER = "__VER_NEWER__"
VERSION_RE = re.compile(r"^\d+(\.\d+)*$")
CHUNK = 16 * 1024
SLEEP_PER_CHUNK = CHUNK / (0.35 * 1024 * 1024)  # ~0.35 MB/s（进度 UI 走查可截窗口）

ARGS = None  # main() 里赋值，Handler 读


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def _hostport(self):
        # 请求 Host 头（ HttpURLConnection / curl 均带）；缺省回退本机回环。
        return self.headers.get("Host") or "127.0.0.1:%d" % ARGS.port

    def do_GET(self):
        path = self.path.split("?")[0].lstrip("/")
        target = os.path.normpath(os.path.join(ROOT, path))
        # 判定补路径分隔符：防同前缀兄弟目录（如 github-releases-evil/）借穿越路径混淆（T43，T41 评审 S2）
        if not target.startswith(ROOT + os.sep) or not os.path.isfile(target):
            self.send_error(404, "Not Found")
            return
        if target.endswith(JSON_EXT) or target.endswith(ATOM_EXT):
            with open(target, "r", encoding="utf-8") as f:
                body = f.read()
            body = body.replace(HOSTPORT_PLACEHOLDER, self._hostport())
            body = body.replace(VER_CUR_PLACEHOLDER, ARGS.current_version)
            body = body.replace(VER_NEWER_PLACEHOLDER, ARGS.newer_version)
            data = body.encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/atom+xml" if target.endswith(ATOM_EXT) else "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
        else:
            size = os.path.getsize(target)
            self.send_response(200)
            self.send_header("Content-Type", (
                "application/vnd.android.package-archive"
                if target.endswith(RATE_LIMIT_EXT) else "application/octet-stream"
            ))
            self.send_header("Content-Length", str(size))
            self.end_headers()
            with open(target, "rb") as f:
                while True:
                    chunk = f.read(CHUNK)
                    if not chunk:
                        break
                    self.wfile.write(chunk)
                    if target.endswith(RATE_LIMIT_EXT):
                        time.sleep(SLEEP_PER_CHUNK)
        sys.stderr.write("SERVED %s -> %s\n" % (path, self._hostport()))
        sys.stderr.flush()

    def log_message(self, fmt, *args):
        sys.stderr.write("MOCK " + fmt % args + "\n")
        sys.stderr.flush()


def _version_tuple(value):
    return tuple(int(part) for part in value.split("."))


def _check_versions(args):
    for name, value in (("--current-version", args.current_version),
                        ("--newer-version", args.newer_version)):
        if not VERSION_RE.match(value):
            sys.stderr.write("MOCK %s 非法：%r 须为 x.y.z 形态数字段\n" % (name, value))
            sys.exit(2)
    if _version_tuple(args.newer_version) <= _version_tuple(args.current_version):
        sys.stderr.write("MOCK --newer-version %s 须数值真大于 --current-version %s\n"
                         % (args.newer_version, args.current_version))
        sys.exit(2)


def main():
    global ARGS
    parser = argparse.ArgumentParser(description="GitHub Releases mock 源（检查更新联调）")
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--bind", default="127.0.0.1",
                        help="监听地址；模拟器走默认 127.0.0.1，真机批次用 0.0.0.0")
    parser.add_argument("--current-version", required=True,
                        help="当前基线版本（裸版本号，如 0.2.0）：__VER_CUR__ 改写值，tag 派生为 v<版本>")
    parser.add_argument("--newer-version", required=True,
                        help="新版版本（须真大于当前版本）：__VER_NEWER__ 改写值；对应递增包 versionCode 必须真递增")
    ARGS = parser.parse_args()
    _check_versions(ARGS)
    server = ThreadingHTTPServer((ARGS.bind, ARGS.port), Handler)
    sys.stderr.write("MOCK serving %s on %s:%d (cur=%s newer=%s)\n"
                     % (ROOT, ARGS.bind, ARGS.port, ARGS.current_version, ARGS.newer_version))
    sys.stderr.flush()
    server.serve_forever()


if __name__ == "__main__":
    main()
