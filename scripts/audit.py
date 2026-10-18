#!/usr/bin/env python3
"""内容治理盘点脚本：扫描指定目录，输出每篇 md 的治理特征数据（表格）。

特征列说明（打标的客观依据）：
- 字数：正文字符数（去空白），<1000 视为轻量篇
- 来源：含"内容来源/整理自/原作者"标注 → 转载系；"生产/反哺/复盘/2026" → 反哺系
- 反哺：文末含"2026-xx 新增（…生产…"式回填标记
- 链接：库内互链数（改名联动影响面）
- 死链风险：引用了哪些同库文件（改名时的同步清单）

用法：python3 scripts/audit.py <目录>   （在 MUYI 仓库根执行）
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def audit_one(md: Path) -> dict:
    text = md.read_text(encoding="utf8")
    chars = len(re.sub(r"\s", "", text))
    has_source = bool(re.search(r"内容来源|整理自|版权归原作者|来源清单", text))
    has_repay = bool(re.search(r"生产实践反哺|生产代码|star-site|voice-utils|2026-\d{2}\s*新增", text))
    # 库内互链（相对 .md 链接）
    internal = []
    for m in re.finditer(r"\]\(([^)#\s]+\.md)\)", text):
        target = (md.parent / m.group(1)).resolve()
        if target.exists() and ROOT in target.parents:
            internal.append(str(target.relative_to(ROOT)))
    # 被（谁引用我）——扫描全库成本高，治理时按需 grep，此处不扫
    return {
        "file": str(md.relative_to(ROOT)),
        "chars": chars,
        "source": "转载系" if has_source else "",
        "repay": "反哺" if has_repay else "",
        "links_out": len(internal),
        "first_line": (text.split("\n", 1)[0] or "")[:50],
    }


def main() -> None:
    target = ROOT / sys.argv[1]
    rows = []
    files = sorted(target.rglob("*.md")) if target.is_dir() else [target]
    for md in files:
        if "node_modules" in str(md) or "/." in str(md):
            continue
        rows.append(audit_one(md))
    # 表格输出（markdown 台账底稿）
    print("| 文件 | 字数 | 特征 | 互链 | 首行 |")
    print("|---|---|---|---|---|")
    for r in sorted(rows, key=lambda x: -x["chars"]):
        feat = "/".join(filter(None, [r["source"], r["repay"]])) or "普通"
        print(f"| {r['file']} | {r['chars']} | {feat} | {r['links_out']} | {r['first_line']} |")
    print(f"\n共 {len(rows)} 篇，总字数 {sum(r['chars'] for r in rows)}")


if __name__ == "__main__":
    main()
