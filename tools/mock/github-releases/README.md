# tools/mock/github-releases · 检查更新 mock 源（T41）

T21 评审建议③落地（户主 2026-11-08 拍板入库）：本地 HTTP 服务器模拟 GitHub Releases
（决策 17 mock 口径），供模拟器 / 真机批次联调「检查更新」三态，不依赖外网。
app 侧检查请求、解析、下载、安装链全是真实实现，本目录只提供数据与下载源。

## 文件

| 文件 | 用途 |
| --- | --- |
| `serve_mock.py` | HTTP 服务器；JSON 内 `__HOSTPORT__` 按请求 Host 头、`__VER_CUR__`/`__VER_NEWER__` 按启动参数 `--current-version`/`--newer-version` 动态改写 |
| `build-incremented-apk.gradle` | Gradle init 脚本：构建 versionCode 真递增的 mock APK（用法见下方「APK 资产」） |
| `releases-newer.json` | 发现新版态：tag `v__VER_NEWER__` 占位符（由 `--newer-version` 注入）+ 更新说明 + APK 资产 |
| `releases-up-to-date.json` | 已是最新态：tag `v__VER_CUR__` 占位符（= 当前版本，由 `--current-version` 注入） |

## 版本参数口径（占位符参数化，2026-11-19 户主拍板）

版本值一律由 `serve_mock.py` 启动参数传入，JSON 不硬编码（决策 17）：

- `--current-version` = 工程当前基线 versionName（现 0.2.0，随 `app/build.gradle.kts` bump 同步传新值）；
- `--newer-version` = 新版版本，必须数值真大于 current（服务启动时校验，传反即拒绝启动）；
- tag 派生规则 `v<版本>`；资产名派生规则 `sofatype-<版本>.apk`。

## APK 资产（不入库，用前放置）

服务器只 serve 本目录内的文件。APK 是构建产物不入 git，用前从构建输出拷入并按 JSON
里的资产名命名（`sofatype-<版本>.apk`）。

**versionCode 口径（T46/T47 修订，2026-11-09；参数化后改指 v0.3.0）**：
`releases-newer.json` 指向的递增包必须 **versionCode 真递增**（现口径 0.3.0(3)）——
两包同版本会构成「同版本复装回执 SUCCESS」假阳性（设备档案坑 15 修订，T27 Round 2 根因）。
基线包 = 工程当前版本（现 0.2.0(2)，T89 起，随 `app/build.gradle.kts` 走）。
**命令示例中的具体数值随工程基线递增时同步改写。**

递增包用本目录 `build-incremented-apk.gradle`（Gradle init 脚本，已入库）构建：
在 AGP 读取前经 afterEvaluate 改写 defaultConfig。注意 `-Pandroid.injected.version.code`
方式在 AGP 8.2.2 **实测无效**（clean 重建后清单版本仍 1，dev 与 review-qa 双向复现），勿用。

```bash
# 递增包（示例 0.3.0(3)，随 --newer-version 目标走；环境重定向四件套见仓库本地开发指引「构建与测试」节）
nix develop -c ./gradlew --no-daemon clean assembleDebug \
  -I tools/mock/github-releases/build-incremented-apk.gradle \
  -PmockVersionCode=3 -PmockVersionName=0.3.0
cp app/build/outputs/apk/debug/sofatype-debug.apk tools/mock/github-releases/sofatype-0.3.0.apk

# 基线包（= 工程当前版本）：不带 -I 与 -P 重新 clean 构建（注入非构建输入，不 clean 会残留递增包产物）
nix develop -c ./gradlew --no-daemon clean assembleDebug
cp app/build/outputs/apk/debug/sofatype-debug.apk tools/mock/github-releases/sofatype-0.2.0.apk

# 放置后用 SDK 工具实测取证清单版本（两包分别执行，versionCode 须为 3 / 2；示例值随基线同步）
aapt dump badging tools/mock/github-releases/sofatype-0.3.0.apk | grep package:
```

三态由 JSON 的 `tag_name` 决定（占位符经启动参数注入），但「发现新版 → 安装升级」链路必须靠递增包实证。

## 启动 / 停止

```bash
# 模拟器（默认监听 127.0.0.1:8000；模拟器经 10.0.2.2 访问宿主回环）
python3 tools/mock/github-releases/serve_mock.py --current-version 0.2.0 --newer-version 0.3.0

# 真机批次（电视访问 Mac 实际局域网 IP）
python3 tools/mock/github-releases/serve_mock.py --current-version 0.2.0 --newer-version 0.3.0 --bind 0.0.0.0
```

版本参数必传：`--current-version` = 工程当前基线 versionName、`--newer-version` 须真大于 current（启动时校验）；
对应 APK 资产须已按上节命令放置（资产名 `sofatype-<版本>.apk`）。
停止：Ctrl-C（或 `pkill -f serve_mock.py`）。走完即停，不常驻；
确认无残留：`lsof -nP -iTCP:<port> -sTCP:LISTEN`。

## app 侧指向 mock（仅 debug 构建，决策 21 口径）

装 debug 包、`am start` 设置页一次后，广播覆写接口地址
（`UpdateMockReceiver`，仅进程内存不持久化，release 无此组件）：

```bash
HOME=<模拟器adb钥匙目录> adb shell am broadcast \
  -n com.sofatype.ime/.update.UpdateMockReceiver \
  -a com.sofatype.ime.debug.action.SET_UPDATE_ENDPOINT \
  -e endpoint http://10.0.2.2:8000/releases-newer.json
```

adb home 走通用机制（`ANDROID_USER_HOME` / 调用方 `HOME`）：按需在调用环境声明，工具链不注入、不设专用变量。

- 模拟器：endpoint 用 `http://10.0.2.2:<port>/releases-newer.json`；
- 真机：endpoint 用 `http://<Mac 局域网 IP>:<port>/releases-newer.json`；
- 失败可重试态走查：endpoint 指向未监听端口（如 `http://10.0.2.2:8001/releases-newer.json`）。

## 真机批次备注（TASKS 6.3）

- 电视先在系统设置确认「未知来源 / 安装未知应用」授权；
- GitHub 国内网络不稳属预期，正好验「失败可重试」态；
- 走完停服务器并确认端口无残留（禁止事项 3）。
