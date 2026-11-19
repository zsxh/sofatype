import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
KT_GLOBS = ("app/src/**/*.kt", "pinyin/src/**/*.kt")
XML_GLOBS = ("app/src/**/*.xml",)
WORK_TAGS = ("TODO", "FIXME")


def is_work_tag(content: str) -> bool:
    text = content.lstrip()
    for tag in WORK_TAGS:
        if text.startswith(tag):
            rest = text[len(tag):]
            if not rest or not (rest[0].isalnum() or rest[0] == "_"):
                return True
    return False


def is_work_tag_block(content: str) -> bool:
    text = content.lstrip()
    while text.startswith("*"):
        text = text[1:].lstrip()
    return is_work_tag(text)


class KotlinStripper:
    def __init__(self, src: str):
        self.src = src
        self.chunks: list[str] = []
        self.length = 0
        self.protected: list[tuple[int, int]] = []

    def emit(self, text: str, protect: bool = False) -> None:
        if protect:
            start = self.length
            self.chunks.append(text)
            self.length += len(text)
            self.protected.append((start, self.length))
        else:
            self.chunks.append(text)
            self.length += len(text)

    def run(self) -> str:
        src = self.src
        n = len(src)
        i = 0
        while i < n:
            c = src[i]
            if c == "/" and src.startswith("//", i):
                end = src.find("\n", i)
                if end == -1:
                    end = n
                if is_work_tag(src[i + 2:end]):
                    self.emit(src[i:end])
                i = end
            elif c == "/" and src.startswith("/*", i):
                i = self.block(i)
            elif c == '"':
                if src.startswith('"""', i):
                    i = self.raw_string(i)
                else:
                    i = self.string(i)
            elif c == "'":
                i = self.char_literal(i)
            elif c == "`":
                i = self.backtick(i)
            else:
                self.emit(c)
                i += 1
        return "".join(self.chunks)

    def block(self, start: int) -> int:
        src = self.src
        n = len(src)
        depth = 1
        j = start + 2
        while j < n and depth > 0:
            if src.startswith("/*", j):
                depth += 1
                j += 2
            elif src.startswith("*/", j):
                depth -= 1
                j += 2
            else:
                j += 1
        if depth != 0:
            raise ValueError(f"unterminated block comment at offset {start}")
        if is_work_tag_block(src[start + 2:j - 2]):
            self.emit(src[start:j])
        return j

    def string(self, start: int) -> int:
        src = self.src
        n = len(src)
        i = start + 1
        segment = start
        while i < n:
            c = src[i]
            if c == "\\":
                i += 2
            elif c == '"':
                self.emit(src[segment:i + 1], protect=True)
                return i + 1
            elif c == "$" and i + 1 < n and src[i + 1] == "{":
                self.emit(src[segment:i + 2], protect=True)
                i = self.expr(i + 2)
                segment = i
            else:
                i += 1
        raise ValueError(f"unterminated string at offset {start}")

    def expr(self, start: int) -> int:
        src = self.src
        n = len(src)
        depth = 1
        i = start
        while i < n:
            c = src[i]
            if c == "/" and src.startswith("//", i):
                end = src.find("\n", i)
                if end == -1:
                    end = n
                if is_work_tag(src[i + 2:end]):
                    self.emit(src[i:end])
                i = end
            elif c == "/" and src.startswith("/*", i):
                i = self.block(i)
            elif c == '"':
                if src.startswith('"""', i):
                    i = self.raw_string(i)
                else:
                    i = self.string(i)
            elif c == "'":
                i = self.char_literal(i)
            elif c == "{":
                depth += 1
                self.emit(c)
                i += 1
            elif c == "}":
                depth -= 1
                self.emit(c)
                i += 1
                if depth == 0:
                    return i
            else:
                self.emit(c)
                i += 1
        raise ValueError("unterminated template expression")

    def raw_string(self, start: int) -> int:
        src = self.src
        n = len(src)
        i = start + 3
        while i < n:
            if src.startswith('"""', i):
                if i + 3 >= n or src[i + 3] != '"':
                    end = i + 3
                    self.emit(src[start:end], protect=True)
                    return end
            i += 1
        raise ValueError(f"unterminated raw string at offset {start}")

    def char_literal(self, start: int) -> int:
        src = self.src
        n = len(src)
        i = start + 1
        while i < n:
            if src[i] == "\\":
                i += 2
            elif src[i] == "'":
                self.emit(src[start:i + 1], protect=True)
                return i + 1
            else:
                i += 1
        raise ValueError(f"unterminated char literal at offset {start}")

    def backtick(self, start: int) -> int:
        end = self.src.find("`", start + 1)
        if end == -1:
            raise ValueError(f"unterminated backtick identifier at offset {start}")
        end += 1
        self.emit(self.src[start:end], protect=True)
        return end


def merge_intervals(intervals):
    if not intervals:
        return []
    ordered = sorted(intervals)
    merged = [list(ordered[0])]
    for start, end in ordered[1:]:
        if start <= merged[-1][1]:
            merged[-1][1] = max(merged[-1][1], end)
        else:
            merged.append([start, end])
    return merged


def line_offsets(lines):
    offsets = []
    pos = 0
    for line in lines:
        offsets.append(pos)
        pos += len(line) + 1
    return offsets


def intersects(merged, start, end):
    for a, b in merged:
        if a < end and b > start:
            return True
    return False


def kotlin_plan_factory(text, protected):
    lines = text.split("\n")
    merged = merge_intervals(protected)
    offsets = line_offsets(lines)

    def plan(line_no, line):
        start = offsets[line_no]
        end = start + len(line)
        if line.strip() == "":
            return "keep" if intersects(merged, start, end) else "blank"
        tail = len(line) - len(line.rstrip())
        if not intersects(merged, end - tail, end):
            return "rstrip"
        return "keep"

    return plan


def tidy_lines(lines, plan) -> str:
    out = []
    blank_run = 0
    for line_no, line in enumerate(lines):
        action = plan(line_no, line)
        if action == "keep":
            out.append(line)
            blank_run = 0
        elif action == "blank":
            blank_run += 1
            if blank_run == 1 and out:
                out.append("")
        else:
            blank_run = 0
            out.append(line.rstrip())
    while out and not out[0]:
        out.pop(0)
    while out and not out[-1]:
        out.pop()
    if not out:
        return ""
    return "\n".join(out) + "\n"


def clean_kotlin(src: str) -> str:
    stripper = KotlinStripper(src)
    text = stripper.run()
    return tidy_lines(text.split("\n"), kotlin_plan_factory(text, stripper.protected))


def clean_xml(src: str) -> str:
    out = []
    i = 0
    n = len(src)
    while i < n:
        if src.startswith("<!--", i):
            end = src.find("-->", i + 4)
            if end == -1:
                raise ValueError("unterminated XML comment")
            if out and out[-1] not in " \t\n" and end + 3 < n and src[end + 3] not in " \t\n":
                out.append(" ")
            i = end + 3
        else:
            out.append(src[i])
            i += 1
    text = "".join(out)

    def plan(line_no, line):
        stripped = line.strip()
        if stripped == "":
            return "blank"
        if stripped.count('"') % 2 == 0 and stripped.count("'") % 2 == 0:
            return "rstrip"
        return "keep"

    return tidy_lines(text.split("\n"), plan)


def guard_non_empty(path: Path, original: str, cleaned: str) -> bool:
    if cleaned == "" and original.strip() != "":
        print(f"SKIP suspicious empty result: {path.relative_to(REPO_ROOT)}", file=sys.stderr)
        return False
    return True


def main() -> int:
    check = "--check" in sys.argv
    kt_files = sorted({p for pattern in KT_GLOBS for p in REPO_ROOT.glob(pattern)})
    xml_files = sorted({p for pattern in XML_GLOBS for p in REPO_ROOT.glob(pattern)})
    changed = 0
    failures = []
    for path in kt_files + xml_files:
        original = path.read_text(encoding="utf-8")
        if path.suffix == ".kt":
            cleaned = clean_kotlin(original)
        else:
            cleaned = clean_xml(original)
        if not guard_non_empty(path, original, cleaned):
            failures.append(path)
            continue
        if path.suffix == ".kt":
            verifier = KotlinStripper(cleaned)
            residual = verifier.run()
        else:
            residual = clean_xml(cleaned)
        if residual != cleaned:
            failures.append(path)
            print(f"UNSTABLE result: {path.relative_to(REPO_ROOT)}", file=sys.stderr)
            continue
        if cleaned != original:
            changed += 1
            rel = path.relative_to(REPO_ROOT)
            if check:
                print(f"would clean: {rel}")
            else:
                path.write_text(cleaned, encoding="utf-8")
                print(f"cleaned: {rel}")
    print(f"files changed: {changed}, scope: {len(kt_files)} kt + {len(xml_files)} xml")
    if failures:
        print(f"failures: {len(failures)}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
