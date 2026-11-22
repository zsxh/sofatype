#!/usr/bin/env bash
# T96 · 热词源年更刷新（本机脚本行为，允许网络；gradle 构建期零网络，见 pinyin/build.gradle.kts）
# 用法：nix develop -c bash tools/wordpack/refresh_hotwords.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC_DIR="$REPO_ROOT/pinyin/dictsrc/hotwords"
TMP="$REPO_ROOT/tools/wordpack/dist"
cd "$REPO_ROOT"
mkdir -p "$TMP"

SNAPSHOT="$TMP/baidu-board-$(date +%Y%m%d).json"

echo "== 1. 搜狗「网络流行新词」scel 整包重抓 =="
python3 tools/wordpack/fetch_sogou_scel.py --out "$SRC_DIR/sogou-liuxingxinci.scel"

echo "== 2. 百度实时榜抓取 → 筛词 → 注音 → 并入累积源 =="
python3 tools/wordpack/fetch_hot_search.py --out "$SNAPSHOT"
python3 tools/wordpack/screen_hot_words.py --in "$SNAPSHOT" --out "$TMP/hot-words-screened.txt"
python3 tools/wordpack/hot_search_annotate.py --in "$TMP/hot-words-screened.txt" --out "$TMP/hot-words-annotated.txt"
python3 tools/wordpack/refresh_realtime.py \
    --existing "$SRC_DIR/baidu-realtime-days.txt" \
    --incoming "$TMP/hot-words-annotated.txt" \
    --out "$SRC_DIR/baidu-realtime-days.txt"

echo "== 3. 重算热词源 sha256 锁定（顺序固定，随变更整体入库）=="
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

echo "== 4. 门禁复验 =="
./gradlew --no-daemon :pinyin:verifyHotDict

echo "== 5. 发版前人工步骤 =="
echo "  - 百度沸点/搜索风云榜、咬文嚼字、语监中心当年榜单：人工整理为注音行，并入对应 *_years.txt"
echo "  - 核对 git diff（词源 + sources.sha256）后随发版批次提交"
