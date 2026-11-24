import argparse
import sys
import urllib.request

# T101 · THUOCL（清华大学开放中文词库）上游抓取。
# 上游：https://github.com/thunlp/THUOCL（MIT，Copyright 2018 THUNLP）。
# 参与域与派生口径见 build_thuocl_source.py；gradle 构建期零网络，抓取只发生在本机刷新时。

# 参与派生的域（诗词为句类名句，超出 IME 词条形态，排除）。
DOMAINS = [
    "IT",             # IT 类词汇
    "caijing",        # 财经
    "chengyu",        # 成语
    "diming",         # 地名
    "lishimingren",   # 历史名人
    "medical",        # 医学
    "food",           # 饮食
    "law",            # 法律
    "car",            # 汽车
    "animal",         # 动物
]

RAW_URL = "https://raw.githubusercontent.com/thunlp/THUOCL/master/data/THUOCL_{domain}.txt"


def fetch(domain, out_path):
    url = RAW_URL.format(domain=domain)
    request = urllib.request.Request(url, headers={"User-Agent": "sofatype-wordpack/1"})
    with urllib.request.urlopen(request) as response, open(out_path, "wb") as out:
        out.write(response.read())
    print(f"[thuoclFetch] {domain}：{url} -> {out_path}")


def main():
    parser = argparse.ArgumentParser(description="抓取 THUOCL 上游域词表（本机刷新用，构建期零网络）")
    parser.add_argument("--out-dir", required=True, help="上游原件输出目录（不入库）")
    args = parser.parse_args()
    import os

    os.makedirs(args.out_dir, exist_ok=True)
    failed = []
    for domain in DOMAINS:
        out_path = os.path.join(args.out_dir, f"THUOCL_{domain}.txt")
        try:
            fetch(domain, out_path)
        except Exception as exc:  # noqa: BLE001
            failed.append(domain)
            print(f"[thuoclFetch] {domain} 抓取失败：{exc}", file=sys.stderr)
    if failed:
        print(f"[thuoclFetch] 失败域：{failed}", file=sys.stderr)
        sys.exit(1)
    print(f"[thuoclFetch] 全部 {len(DOMAINS)} 域抓取完成：{args.out_dir}")


if __name__ == "__main__":
    main()
