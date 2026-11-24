#!/usr/bin/env bash
# T96 · 热词源年更刷新（本机脚本行为，允许网络；gradle 构建期零网络，见 pinyin/build.gradle.kts）
# T101 修订：搜狗 scel 与百度两源出库，开放源换为 THUOCL（MIT，清华 THUNLP）；
# 语监中心 / 咬文嚼字榜单仍为人工转录。
# 用法：nix develop -c bash tools/wordpack/refresh_hotwords.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC_DIR="$REPO_ROOT/pinyin/dictsrc/hotwords"
TMP="$REPO_ROOT/tools/wordpack/dist"
cd "$REPO_ROOT"
mkdir -p "$TMP/thuocl"

echo "== 1. THUOCL 上游域词表重取 =="
python3 tools/wordpack/fetch_thuocl.py --out-dir "$TMP/thuocl"

echo "== 2. 派生入库热词源 thuocl-open.txt（确定性：DF 降序 top12000 + pypinyin 注音）=="
python3 tools/wordpack/build_thuocl_source.py \
  --upstream-dir "$TMP/thuocl" \
  --out "$SRC_DIR/thuocl-open.txt"

echo "== 3. 重算热词源 sha256 锁定（顺序固定，随变更整体入库）=="
python3 - "$SRC_DIR" <<'PYEOF'
import hashlib, os, sys
src = sys.argv[1]
order = [
    "thuocl-open.txt",
    "nlc-netlang-years.txt",
    "yaowenjiaozi-years.txt",
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
echo "  - 语监中心、咬文嚼字当年榜单：人工整理为注音行，并入对应 *_years.txt"
echo "  - 核对 git diff（词源 + sources.sha256）后随发版批次提交"
