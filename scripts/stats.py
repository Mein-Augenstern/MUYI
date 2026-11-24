#!/usr/bin/env python3
"""全库统计：根 README 与在线书首页所有数字的唯一来源。

口径（docs/治理/协作与统计口径约定.md）：
  篇数      轨道目录下的正文 md（不含 README.md / readme.md 这类导航页与配图说明）
  追问链    正文含「追问链」小节的篇数
  自测题    自测清单.md 的表格数据行数（以 "| 数字" 开头的行）
  配图      git 跟踪的图片文件数（png/jpg/jpeg/gif/webp，不含 book/）
  目录导读  轨道目录下名为 README.md 的导航页数

用法（在仓库根执行）：
  python3 scripts/stats.py                   打印，供人工核对
  python3 scripts/stats.py --json 输出路径   写 JSON（在线书构建时由 prepare-src 调用）
  python3 scripts/stats.py --check           校验 README 里的数字，不一致则退出码 1
"""
from __future__ import annotations

import argparse
import datetime
import json
import os
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
TRACK = re.compile(r"^[A-G]-")
IMG = re.compile(r"\.(png|jpe?g|gif|webp)$", re.I)


def tracks() -> list[Path]:
    return sorted((p for p in REPO.iterdir() if p.is_dir() and TRACK.match(p.name)), key=lambda p: p.name)


def is_article(p: Path) -> bool:
    return p.suffix == ".md" and p.name.lower() != "readme.md"


def collect() -> dict:
    per_track: dict[str, int] = {}
    zhuiwen = 0
    readmes = 0
    for t in tracks():
        arts = [p for p in t.rglob("*.md") if is_article(p) and not any(x.startswith(".") for x in p.parts)]
        per_track[t.name[0]] = len(arts)
        zhuiwen += sum(1 for p in arts if "追问链" in p.read_text(encoding="utf8", errors="ignore"))
        # 大小写必须精确：小写 readme.md 是配图说明，不是导读页。不能用 rglob("README.md")——
        # macOS 上它不区分大小写，会把小写的一并数进来，本机与 CI 的数字就对不上。
        # 跳过隐藏目录（.mimosa 之类只存在于本机）。
        for _, dirnames, filenames in os.walk(t):
            dirnames[:] = [d for d in dirnames if not d.startswith(".")]
            readmes += filenames.count("README.md")
    quiz = len(re.findall(r"^\| \d+", (REPO / "自测清单.md").read_text(encoding="utf8"), re.M))
    return {
        "articles": sum(per_track.values()),
        "tracks": len(per_track),
        "quiz": quiz,
        "zhuiwen": zhuiwen,
        "images": count_images(),
        "readmes": readmes,
        "per_track": per_track,
        "generated": datetime.date.today().isoformat(),
    }


def count_images() -> int:
    try:
        out = subprocess.run(
            ["git", "-c", "core.quotepath=false", "ls-files"], cwd=REPO, capture_output=True, text=True, check=True
        ).stdout
        return sum(1 for l in out.splitlines() if IMG.search(l) and not l.startswith("book/"))
    except (OSError, subprocess.CalledProcessError):
        return sum(1 for p in REPO.rglob("*") if p.is_file() and IMG.search(p.name) and "book" not in p.parts)


def check(stats: dict) -> int:
    """README 里出现的每个数字都必须等于实算值。"""
    bad: list[str] = []
    readme = (REPO / "README.md").read_text(encoding="utf8")
    probes = [
        ("篇数", r"`(\d+) 篇实文`"),
        ("篇数", r"（\d+ 轨道 · (\d+) 篇）"),
        ("自测题", r"`(\d+) 题自测闭环`"),
        ("自测题", r"全库按 \[(\d+) 题自测清单\]"),
        ("自测题", r"自测清单\.md\) —— (\d+) 题"),
        ("配图", r"`(\d+) 张配图"),
        ("配图", r"\*\*(\d+) 张配图"),
    ]
    shown = {"篇数": stats["articles"], "自测题": stats["quiz"], "配图": stats["images"]}
    for label, pat in probes:
        for value in re.findall(pat, readme):
            if int(value) != shown[label]:
                bad.append(f"README.md：{label}写的是 {value}，实算 {shown[label]}")
    table = {m.group(1): int(m.group(2)) for m in re.finditer(r"^\| \[([A-G])-[^|\]]+\]\([^)]*\) \| (\d+) 篇 \|", readme, re.M)}
    for letter, n in stats["per_track"].items():
        if letter not in table:
            bad.append(f"README.md：轨道表缺少 {letter} 轨")
        elif table[letter] != n:
            bad.append(f"README.md：{letter} 轨写的是 {table[letter]} 篇，实算 {n}")
    for t in tracks():
        nav = t / "README.md"
        if not nav.exists():
            continue
        for value in re.findall(r"实况 \*\*(\d+) 篇", nav.read_text(encoding="utf8")):
            if int(value) != stats["per_track"][t.name[0]]:
                bad.append(f"{t.name}/README.md：写的是 {value} 篇，实算 {stats['per_track'][t.name[0]]}")
    if bad:
        print("README 数字与实算不一致：")
        for b in bad:
            print("  " + b)
        print("修正方法：按下面“实算”一栏同步 README，再重新执行 --check。")
        return 1
    print("README 数字与实算一致。")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--json", metavar="路径", help="写出 JSON")
    ap.add_argument("--check", action="store_true", help="校验 README 里的数字")
    args = ap.parse_args()
    stats = collect()
    if args.json:
        Path(args.json).write_text(json.dumps(stats, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
        print(f"统计已写出：{args.json}")
    if not args.json or args.check:
        print(f"实算：{stats['articles']} 篇 · {stats['tracks']} 轨道 · {stats['quiz']} 题 · "
              f"{stats['zhuiwen']} 篇追问链 · {stats['readmes']} 个目录导读 · {stats['images']} 张配图")
        print("各轨：" + "  ".join(f"{k} {v}" for k, v in stats["per_track"].items()))
    if args.check:
        return check(stats)
    return 0


if __name__ == "__main__":
    sys.exit(main())
