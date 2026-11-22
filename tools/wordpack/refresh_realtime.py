import argparse

MIN_CHARS = 2
MAX_CHARS = 6
FREQ = 3000.0


def merge(existing_path, incoming_path, out_path):
    with open(existing_path, encoding="utf-8") as f:
        existing = [line.rstrip("\n") for line in f if line.strip()]
    with open(incoming_path, encoding="utf-8") as f:
        incoming = [line.rstrip("\n") for line in f if line.strip()]
    seen_words = {line.split(" ", 1)[0] for line in existing}
    appended = []
    for line in incoming:
        word = line.split(" ", 1)[0]
        if word in seen_words:
            continue
        seen_words.add(word)
        appended.append(line)
    merged = existing + appended
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("\n".join(merged) + ("\n" if merged else ""))
    print(f"[refreshRealtime] 原有：{len(existing)}，本轮新增：{len(appended)}，合并后：{len(merged)}")


def main():
    parser = argparse.ArgumentParser(description="实时榜注音行并入累积源文件（按词去重、追加式）")
    parser.add_argument("--existing", required=True)
    parser.add_argument("--incoming", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    merge(args.existing, args.incoming, args.out)


if __name__ == "__main__":
    main()
