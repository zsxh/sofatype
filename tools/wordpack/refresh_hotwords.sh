#!/usr/bin/env bash
# T96 · 热词源年更刷新（本机脚本行为，允许网络；gradle 构建期零网络，见 pinyin/build.gradle.kts）
# T99 修订：baidu-realtime（百度实时热搜榜）停止并入，刷新链不再抓取实时榜；
# 抓取脚本（fetch_hot_search / screen_hot_words / hot_search_annotate / refresh_realtime）与
# 源文件 baidu-realtime-days.txt 保留供复评，不参与词库构建。
# 用法：nix develop -c bash tools/wordpack/refresh_hotwords.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC_DIR="$REPO_ROOT/pinyin/dictsrc/hotwords"
TMP="$REPO_ROOT/tools/wordpack/dist"
cd "$REPO_ROOT"
mkdir -p "$TMP"

echo "== 1. 搜狗「网络流行新词」scel 整包重抓 =="
python3 tools/wordpack/fetch_sogou_scel.py --out "$SRC_DIR/sogou-liuxingxinci.scel"

echo "== 2. 重算热词源 sha256 锁定（顺序固定，随变更整体入库）=="
python3 - "$SRC_DIR" <<'PYEOF'
import hashlib, os, sys
src = sys.argv[1]
order = [
    "sogou-liuxingxinci.scel",
    "baidu-boiling-years.txt",
    "nlc-netlang-years.txt",
    "yaowenjiaozi-years.txt",
    "baidu-realtime-days.txt",
]
lines = []
for name in order:
    data = open(os.path.join(src, name), "rb").read()
    lines.append(f"{hashlib.sha256(data).hexdigest()}  {name}")
open(os.path.join(src, "sources.sha256"), "w", encoding="utf-8").write("\n".join(lines) + "\n")
print("\n".join(lines))
PYEOF

echo "== 3. 门禁复验 =="
./gradlew --no-daemon :pinyin:verifyHotDict

echo "== 4. 发版前人工步骤 =="
echo "  - 百度沸点/搜索风云榜、咬文嚼字、语监中心当年榜单：人工整理为注音行，并入对应 *_years.txt"
echo "  - 核对 git diff（词源 + sources.sha256）后随发版批次提交"
