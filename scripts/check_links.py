#!/usr/bin/env python3
"""链接与导航体检：仓库视角（--src）与在线书视角（--dist），区分大小写。

为什么需要它：
- macOS 文件系统不区分大小写——`HashTable` 写成 `Hashtable` 在本地"能打开"，到 GitHub 和线上就是 404；
- Markdown 行内链接的目标含裸空格时不会被渲染成链接，正则式体检会把它们当成合法链接放过；
- 在线书构建配置 ignoreDeadLinks 会把书内死链静默吞掉。

检查项（有问题退出码为 1）：
  死链    目标不存在
  大小写  目标仅大小写不同
  裸空格  链接目标含未编码空格，不会渲染成链接                  [仅 --src]
  未收录  正文没有被本轨任何 README.md 链接，读者从导航页点不到   [仅 --src]
  死图    <img src> 指向的站内文件不存在                        [仅 --dist]

用法（在仓库根执行）：
  python3 scripts/check_links.py --src              # 仓库源文：部署前门禁 / CI
  python3 scripts/check_links.py --dist book/dist   # 构建产物
"""
from __future__ import annotations

import argparse
import html
import os
import re
import sys
import unicodedata
from collections import defaultdict
from pathlib import Path
from urllib.parse import unquote

REPO = Path(__file__).resolve().parents[1]
TRACK = re.compile(r"^[A-G]-")
SKIP_DIRS = {"node_modules", ".git", ".mimosa", ".v2c"}
EXT_LINK = ("http://", "https://", "mailto:", "ftp://", "//")
ORDER = ("死链", "大小写", "裸空格", "未收录", "死图")


def nfc(s: str) -> str:
    return unicodedata.normalize("NFC", s)


def walk_names(base: Path, skip: set[str]) -> tuple[set[str], set[str]]:
    """精确大小写的路径集合：文件、目录（均为相对 base 的 POSIX 路径）。"""
    files: set[str] = set()
    dirs: set[str] = set()
    for dp, dn, fn in os.walk(base):
        dn[:] = [d for d in dn if d not in skip and not d.startswith(".")]
        for d in dn:
            dirs.add(nfc(os.path.relpath(os.path.join(dp, d), base)))
        for f in fn:
            files.add(nfc(os.path.relpath(os.path.join(dp, f), base)))
    return files, dirs


def md_links(line: str) -> list[tuple[str, bool]]:
    """一行内的 markdown 行内链接/图片目标：[(dest, 是否 <...> 形式)]。

    支持成对括号（文件名里的半角括号）、可选 title。
    """
    out = []
    for m in re.finditer(r"\]\(", line):
        i = m.end()
        n = len(line)
        if i < n and line[i] == "<":
            j = line.find(">", i)
            if j >= 0:
                out.append((line[i + 1 : j], True))
            continue
        depth = 0
        j = i
        while j < n:
            c = line[j]
            if c == "\\" and j + 1 < n:
                j += 2
                continue
            if c == "(":
                depth += 1
            elif c == ")":
                if depth == 0:
                    break
                depth -= 1
            j += 1
        if j < n and line[j] == ")":
            dest = re.sub(r'\s+"[^"]*"\s*$', "", line[i:j]).strip()
            if dest:
                out.append((dest, False))
    return out


def html_attrs(line: str, attr: str) -> list[str]:
    pat = re.compile(r"<[a-z]+\s[^>]*?\b" + attr + r'\s*=\s*(?:"([^"]*)"|\'([^\']*)\')', re.I)
    return [a or b for a, b in pat.findall(line)]


def strip_inline_code(line: str) -> str:
    out, tick = [], False
    for seg in line.split("`"):
        out.append("" if tick else seg)
        tick = not tick
    return "".join(out)


def resolve(src_rel: str, dest: str) -> str:
    path = unquote(dest.split("#")[0].split("?")[0])
    if path.startswith("/"):
        return os.path.normpath(path.lstrip("/"))
    return os.path.normpath(os.path.join(os.path.dirname(src_rel), path))


class Report:
    def __init__(self) -> None:
        self.items: dict[str, list[tuple[str, str]]] = defaultdict(list)

    def add(self, kind: str, where: str, detail: str) -> None:
        self.items[kind].append((where, detail))

    def done(self, kinds: tuple[str, ...]) -> int:
        total = sum(len(self.items.get(k, [])) for k in kinds)
        if not total:
            print("体检通过：" + " / ".join(f"{k} 0" for k in kinds))
            return 0
        for kind in kinds:
            rows = self.items.get(kind)
            if not rows:
                continue
            print(f"\n【{kind}】{len(rows)} 处")
            for where, detail in rows:
                print(f"  {where}  {detail}")
        print(f"\n未通过：共 {total} 处")
        return 1


def check_src(rep: Report) -> None:
    files, dirs = walk_names(REPO, SKIP_DIRS | {"book"})
    lower_files = {f.lower(): f for f in files}
    lower_dirs = {d.lower(): d for d in dirs}
    articles: dict[str, set[str]] = defaultdict(set)
    nav_links: dict[str, set[str]] = defaultdict(set)  # 轨道 -> 本轨 README.md 链接到的路径

    for rel in sorted(f for f in files if f.endswith(".md") and not f.startswith("book/")):
        track = rel.split("/")[0]
        in_track = bool(TRACK.match(rel))
        is_nav = rel.endswith("/README.md")
        if in_track and not is_nav and not rel.lower().endswith("readme.md"):
            articles[track].add(rel)
        try:
            text = (REPO / rel).read_text(encoding="utf8")
        except UnicodeDecodeError:
            continue
        fence = False
        for no, raw in enumerate(text.split("\n"), 1):
            if re.match(r"^\s*(```|~~~)", raw):
                fence = not fence
                continue
            if fence:
                continue
            line = strip_inline_code(raw)
            dests = md_links(line) + [(d, True) for d in html_attrs(line, "href") + html_attrs(line, "src")]
            for dest, angle in dests:
                if dest.startswith(EXT_LINK):
                    continue
                where = f"{rel}:{no}"
                if not angle and " " in dest.split("#")[0]:
                    rep.add("裸空格", where, f"{dest}  ← 空格要写成 %20，否则不会渲染成链接")
                target = nfc(resolve(rel, dest))
                if target in files or target in dirs:
                    if in_track and is_nav:
                        nav_links[track].add(target)
                    continue
                real = lower_files.get(target.lower()) or lower_dirs.get(target.lower())
                if real:
                    rep.add("大小写", where, f"{dest}  ← 实际路径是 {real}")
                else:
                    rep.add("死链", where, dest)

    for track, arts in articles.items():
        for art in sorted(arts - nav_links[track]):
            rep.add("未收录", art, "← 没有被本轨任何 README.md 链接")


def check_dist(rep: Report, dist: Path) -> None:
    files, _ = walk_names(dist, set())
    for rel in sorted(f for f in files if f.endswith(".html")):
        text = (dist / rel).read_text(encoding="utf8", errors="ignore")
        base = rel[: -len("index.html")] if rel.endswith("index.html") else os.path.dirname(rel)
        for m in re.finditer(r'<([a-z]+)\s[^>]*?\b(href|src)\s*=\s*"([^"]*)"', text, re.I):
            tag, raw = m.group(1).lower(), html.unescape(m.group(3))
            if not raw or raw.startswith(EXT_LINK) or raw.startswith(("#", "data:", "javascript:", "tel:")):
                continue
            path = unquote(raw.split("#")[0].split("?")[0])
            if not path:
                continue
            url = path[1:] if path.startswith("/") else os.path.normpath(os.path.join(base, path))
            url = url.strip("/")
            # 根路径 "/" 即首页；cleanUrls=false 下 "/x" 可能对应 x.html，目录对应 x/index.html
            cands = ["index.html"] if not url else [url, url + ".html", url + "/index.html"]
            if not any(nfc(c) in files for c in cands):
                rep.add("死图" if tag == "img" else "死链", rel, raw)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--src", action="store_true", help="检查仓库源文")
    ap.add_argument("--dist", metavar="目录", help="检查构建产物")
    args = ap.parse_args()
    if not args.src and not args.dist:
        ap.error("需要 --src 或 --dist")
    rep = Report()
    kinds = ()
    if args.src:
        check_src(rep)
        kinds += ("死链", "大小写", "裸空格", "未收录")
    if args.dist:
        dist = Path(args.dist)
        if not dist.is_dir():
            print(f"构建产物目录不存在：{dist}（先执行构建）")
            return 2
        check_dist(rep, dist)
        kinds += ("死链", "死图")
    return rep.done(kinds)


if __name__ == "__main__":
    sys.exit(main())
