import argparse
import re
import sys

from pypinyin import lazy_pinyin

DEFAULT_FREQ = 3000.0
SYLLABLE_PATTERN = re.compile(r"[a-z]+")


def annotate_word(word):
    syllables = lazy_pinyin(word)
    if len(syllables) != len(word):
        return None
    if any(not SYLLABLE_PATTERN.fullmatch(s) for s in syllables):
        return None
    return f"{word} {DEFAULT_FREQ} 0 {' '.join(syllables)}"


def main():
    parser = argparse.ArgumentParser(description="筛出词经 pypinyin 注音为词包源文本行")
    parser.add_argument("--in", dest="input", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    with open(args.input, encoding="utf-8") as f:
        words = [line.strip() for line in f if line.strip()]
    kept = []
    skipped = []
    for word in words:
        line = annotate_word(word)
        if line is None:
            skipped.append(word)
        else:
            kept.append(line)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(kept) + ("\n" if kept else ""))
    print(f"[hotSearchAnnotate] 输入词：{len(words)}，注音成功：{len(kept)}，跳过：{len(skipped)}")
    for word in skipped:
        print(f"[hotSearchAnnotate] 跳过（拼音不可用）：{word}", file=sys.stderr)
    print(f"[hotSearchAnnotate] 输出：{args.out}")


if __name__ == "__main__":
    sys.exit(main())
