# mock/github-releases · 检查更新 mock 源（T41）

T21 评审建议③落地（户主 2026-11-08 拍板入库）：本地 HTTP 服务器模拟 GitHub Releases
（决策 17 mock 口径），供模拟器 / 真机批次联调「检查更新」三态，不依赖外网。
app 侧检查请求、解析、下载、安装链全是真实实现，本目录只提供数据与下载源。

## 文件

| 文件 | 用途 |
| --- | --- |
| `serve_mock.py` | HTTP 服务器；JSON 内 `__HOSTPORT__` 占位符按请求 Host 头动态改写 |
| `releases-newer.json` | 发现新版态：tag v0.2.0 + 更新说明 + APK 资产 |
| `releases-up-to-date.json` | 已是最新态：tag v0.1.0（= 当前版本） |

## APK 资产（不入库，用前放置）

服务器只 serve 本目录内的文件。APK 是构建产物不入 git，用前从构建输出拷入并按 JSON
里的资产名命名：

```bash
cp app/build/outputs/apk/debug/app-debug.apk mock/github-releases/sofatype-0.2.0.apk
cp app/build/outputs/apk/debug/app-debug.apk mock/github-releases/sofatype-0.1.0.apk
```

同一 debug 包即可：三态由 JSON 的 `tag_name` 决定，不靠 APK 版本号。

## 启动 / 停止

```bash
# 模拟器（默认监听 127.0.0.1:8000；模拟器经 10.0.2.2 访问宿主回环）
python3 mock/github-releases/serve_mock.py

# 真机批次（电视访问 Mac 局域网 IP，见 local/DEV-GUIDE 1.2：192.168.1.136）
python3 mock/github-releases/serve_mock.py --bind 0.0.0.0
```

停止：Ctrl-C（或 `pkill -f serve_mock.py`）。走完即停，不常驻；
确认无残留：`lsof -nP -iTCP:<port> -sTCP:LISTEN`。

## app 侧指向 mock（仅 debug 构建，决策 21 口径）

装 debug 包、`am start` 设置页一次后，广播覆写接口地址
（`UpdateMockReceiver`，仅进程内存不持久化，release 无此组件）：

```bash
HOME=<工作区>/.emulator-adbhome adb shell am broadcast \
  -n com.sofatype.ime/.update.UpdateMockReceiver \
  -a com.sofatype.ime.debug.action.SET_UPDATE_ENDPOINT \
  -e endpoint http://10.0.2.2:8000/releases-newer.json
```

- 模拟器：endpoint 用 `http://10.0.2.2:<port>/releases-newer.json`；
- 真机：endpoint 用 `http://<Mac 局域网 IP>:<port>/releases-newer.json`；
- 失败可重试态走查：endpoint 指向未监听端口（如 `http://10.0.2.2:8001/releases-newer.json`）。

## 真机批次备注（TASKS 6.3）

- 电视先在系统设置确认「未知来源 / 安装未知应用」授权；
- GitHub 国内网络不稳属预期，正好验「失败可重试」态；
- 走完停服务器并确认端口无残留（禁止事项 3）。
