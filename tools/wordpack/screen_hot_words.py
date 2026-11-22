import argparse
import json
import re

HANZI_PATTERN = re.compile(r"[\u4e00-\u9fff]+")
SEGMENT_SPLIT_PATTERN = re.compile(
    r"[\s\dA-Za-z\u3000-\u303f\uff00-\uffef\u2010-\u2027\u2E80-\u2EFF]+"
)
MIN_CHARS = 2
MAX_CHARS = 6
LIMIT = 30


def board_titles(data):
    titles = []
    for card in data.get("data", {}).get("cards", []):
        for item in card.get("content", []):
            word = item.get("word", "").strip()
            if not word or item.get("isTop"):
                continue
            titles.append(word)
    return titles


def screen(data):
    seen = set()
    words = []
    for title in board_titles(data):
        for segment in SEGMENT_SPLIT_PATTERN.split(title):
            if not (MIN_CHARS <= len(segment) <= MAX_CHARS):
                continue
            if not HANZI_PATTERN.fullmatch(segment):
                continue
            if segment in seen:
                continue
            seen.add(segment)
            words.append(segment)
            if len(words) >= LIMIT:
                break
        if len(words) >= LIMIT:
            break
    return words


def main():
    parser = argparse.ArgumentParser(description="从百度热搜快照自动筛词")
    parser.add_argument("--in", dest="input", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    with open(args.input, encoding="utf-8") as f:
        data = json.load(f)
    titles = board_titles(data)
    words = screen(data)
    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(words) + "\n")
    print(f"[screenHotWords] 榜单标题（跳过置顶运营位）：{len(titles)}")
    print(f"[screenHotWords] 筛出词（纯汉字、{MIN_CHARS}~{MAX_CHARS} 字、去重、前 {LIMIT}）：{len(words)}")
    print(f"[screenHotWords] 输出：{args.out}")


if __name__ == "__main__":
    main()
