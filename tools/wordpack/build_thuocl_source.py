import argparse
import os
import re
import sys

from pypinyin import lazy_pinyin

# T101 · THUOCL 上游域词表 -> 入库热词源 thuocl-open.txt（确定性派生）。
# 派生口径（T101）：各域归一解析 -> 纯汉字 1~6 字过滤 -> 跨域去重（同词取最大 DF）
# -> 按 DF 降序取前 N -> pypinyin 注音（不可注音跳过）-> hot 注音行（词 3000 0 拼音…）。
# 上游格式注意：UTF-8（diming 带 BOM、个别文件纯 CR 行尾）、词与 DF 间为 Tab 且两侧可能带空格。

HANZI_PATTERN = re.compile(r"[\u4e00-\u9fff]+")
SYLLABLE_PATTERN = re.compile(r"[a-z]+")
SOURCE_FREQ = "3000"

DEFAULT_LIMIT = 12000


def parse_upstream_file(path):
    raw = open(path, "rb").read().decode("utf-8")
    raw = raw.lstrip("﻿").replace("\r", "\n")
    rows = []
    for line in raw.split("\n"):
        columns = line.split()
        if len(columns) < 2:
            continue
        word, df = columns[0], columns[-1]
        try:
            rows.append((word, int(df)))
        except ValueError:
            print(f"[thuoclBuild] DF 列非法，跳过：{line[:40]!r}", file=sys.stderr)
    return rows


def annotate_word(word):
    syllables = lazy_pinyin(word)
    if len(syllables) != len(word):
        return None
    if any(not SYLLABLE_PATTERN.fullmatch(s) for s in syllables):
        return None
    return f"{word} {SOURCE_FREQ} 0 {' '.join(syllables)}"


def main():
    parser = argparse.ArgumentParser(description="THUOCL 上游派生入库热词源（确定性）")
    parser.add_argument("--upstream-dir", required=True, help="fetch_thuocl.py 的输出目录")
    parser.add_argument("--out", required=True, help="入库派生文件路径（thuocl-open.txt）")
    parser.add_argument("--limit", type=int, default=DEFAULT_LIMIT, help="DF 降序截断条数（默认 12000）")
    args = parser.parse_args()

    merged = {}
    for name in sorted(os.listdir(args.upstream_dir)):
        if not name.startswith("THUOCL_") or not name.endswith(".txt"):
            continue
        rows = parse_upstream_file(os.path.join(args.upstream_dir, name))
        print(f"[thuoclBuild] {name}：解析 {len(rows)} 行")
        for word, df in rows:
            if not HANZI_PATTERN.fullmatch(word) or not (1 <= len(word) <= 6):
                continue
            if df > merged.get(word, -1):
                merged[word] = df

    ranked = sorted(merged.items(), key=lambda item: (-item[1], item[0]))
    selected = ranked[: args.limit]
    print(
        f"[thuoclBuild] 纯汉字 1~6 字去重后 {len(merged)} 词，"
        f"DF 降序取前 {len(selected)}（limit={args.limit}）"
    )

    kept = []
    skipped_annotate = []
    for word, _df in selected:
        line = annotate_word(word)
        if line is None:
            skipped_annotate.append(word)
        else:
            kept.append(line)
    print(f"[thuoclBuild] 注音成功 {len(kept)}，跳过 {len(skipped_annotate)}")
    for word in skipped_annotate[:10]:
        print(f"[thuoclBuild] 注音跳过样例：{word}", file=sys.stderr)

    with open(args.out, "w", encoding="utf-8") as out:
        out.write("\n".join(kept) + ("\n" if kept else ""))
    print(f"[thuoclBuild] 输出：{args.out}（{len(kept)} 行）")


if __name__ == "__main__":
    main()
