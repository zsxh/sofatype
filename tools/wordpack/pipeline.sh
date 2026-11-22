#!/usr/bin/env bash
set -euo pipefail

VERSION_ARG="${1:?用法：pipeline.sh <版本号YYYYMMDD.N> <输出目录> [词包名称]}"
OUT_DIR="${2:?缺少输出目录参数}"
PACK_NAME="${3:-热词包}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FIXTURES="$REPO_ROOT/tools/wordpack/fixtures"
MAIN_DICT_SRC="$REPO_ROOT/pinyin/dictsrc/rawdict_utf16_65105_freq.txt"
cd "$REPO_ROOT"
case "$OUT_DIR" in
    /*) ;;
    *) OUT_DIR="$REPO_ROOT/$OUT_DIR" ;;
esac

mkdir -p "$OUT_DIR"
SNAPSHOT="${HOT_SNAPSHOT:-$OUT_DIR/baidu-hot-search-snapshot.json}"
if [ -z "${HOT_SNAPSHOT:-}" ]; then
    python3 tools/wordpack/fetch_hot_search.py --out "$SNAPSHOT"
fi
python3 tools/wordpack/screen_hot_words.py --in "$SNAPSHOT" --out "$OUT_DIR/hot-words-screened.txt"
python3 tools/wordpack/hot_search_annotate.py --in "$OUT_DIR/hot-words-screened.txt" --out "$OUT_DIR/hot-words-annotated.txt"
./gradlew --no-daemon :pinyin:wordPackBuild -PtoolArgs="$OUT_DIR/wordpack.bin,$OUT_DIR/manifest.json,$VERSION_ARG,$PACK_NAME,$MAIN_DICT_SRC,scel:$FIXTURES/sogou-wangluoliuxingxinci.scel,scel:$FIXTURES/sogou-tianwencihui.scel,hot:$OUT_DIR/hot-words-annotated.txt"

if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$OUT_DIR/wordpack.bin" "$OUT_DIR/manifest.json"
else
    shasum -a 256 "$OUT_DIR/wordpack.bin" "$OUT_DIR/manifest.json"
fi
