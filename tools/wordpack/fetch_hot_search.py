import argparse
import json
import sys
import urllib.request

BOARD_URL = "https://top.baidu.com/board?tab=realtime"
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
)


def fetch_board_json(url):
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=60) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        html = response.read().decode(charset, errors="replace")
    marker = "<!--s-data:"
    start = html.find(marker)
    if start < 0:
        raise SystemExit("[fetchHotSearch] 页面未找到 s-data 数据块：" + url)
    start += len(marker)
    end = html.find("-->", start)
    if end < 0:
        raise SystemExit("[fetchHotSearch] s-data 数据块未闭合")
    return json.loads(html[start:end])


def main():
    parser = argparse.ArgumentParser(description="抓取百度热搜官方榜单页内嵌 s-data JSON 快照")
    parser.add_argument("--url", default=BOARD_URL)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    data = fetch_board_json(args.url)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
    cards = data.get("data", {}).get("cards", [])
    words = [
        item.get("word", "")
        for card in cards
        for item in card.get("content", [])
        if item.get("word")
    ]
    print(f"[fetchHotSearch] 榜单卡片：{len(cards)}，词条：{len(words)}")
    print(f"[fetchHotSearch] 快照：{args.out}")


if __name__ == "__main__":
    sys.exit(main())
