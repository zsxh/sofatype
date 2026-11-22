import argparse
import hashlib
import sys
import urllib.request
from http.cookiejar import CookieJar

DETAIL_URL = "https://pinyin.sogou.com/dict/detail/index/4"
DOWNLOAD_URL = (
    "https://pinyin.sogou.com/d/dict/download_cell.php"
    "?id=4&name=%E7%BD%91%E7%BB%9C%E6%B5%81%E8%A1%8C%E6%96%B0%E8%AF%8D&f=detail"
)
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0 Safari/537.36"
)
MIN_SCEL_BYTES = 0x1540 + 4
LENGTH_PREFIX = bytes([0x40, 0x15, 0x00, 0x00])
MAGIC_TAGS = (b"ECS\x01", b"DCS\x01")


def open_opener():
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(CookieJar()))


def fetch_scel(opener):
    referer = DETAIL_URL
    opener.open(urllib.request.Request(referer, headers={"User-Agent": USER_AGENT}), timeout=30).read()
    request = urllib.request.Request(
        DOWNLOAD_URL,
        headers={"User-Agent": USER_AGENT, "Referer": referer},
    )
    with opener.open(request, timeout=120) as response:
        return response.read()


def main():
    parser = argparse.ArgumentParser(description="抓取搜狗「网络流行新词」细胞词库（scel 原件，需先访问详情页取会话）")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    data = fetch_scel(open_opener())
    if len(data) < MIN_SCEL_BYTES or data[:4] != LENGTH_PREFIX or data[4:8] not in MAGIC_TAGS:
        head = data[:64]
        raise SystemExit(
            "[fetchSogouScel] 下载内容非 scel（ECS/DCS 头不符）："
            f"{len(data)} 字节，头 {head!r}。官方站点流程可能已变化，改走浏览器通道人工下载。"
        )
    with open(args.out, "wb") as f:
        f.write(data)
    print(f"[fetchSogouScel] 词条库原件：{args.out}（{len(data)} 字节）")
    print(f"[fetchSogouScel] sha256：{hashlib.sha256(data).hexdigest()}")


if __name__ == "__main__":
    sys.exit(main())
