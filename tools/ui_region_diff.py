#!/usr/bin/env python3
"""Locate and explain the pixel differences between two UI captures.

A single MAE per screen says a page differs but never says how, and reading a
screenshot needs eyes. This does the reading instead: it cuts the difference
map into connected regions, then labels each region with the text the two
accessibility trees put there, so a difference can be reported as "this row
says A on the left and B on the right" rather than "10015 pixels differ".

Usage:
    python3 tools/ui_region_diff.py artifacts/ui-parity-v27
    python3 tools/ui_region_diff.py artifacts/ui-parity-v27 --only theme
    python3 tools/ui_region_diff.py artifacts/ui-parity-v27 --crop-dir /tmp/regions
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from collections import deque
from pathlib import Path

import numpy as np
from PIL import Image

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")

# Differences the migration intends, so they are named rather than counted.
EXPLAINED = (
    ("手机控制", "Phone Control removed by scope"),
    ("无障碍", "Phone Control removed by scope"),
    ("/data/user/0", "different data directory (legacy)"),
    ("/data/data", "different data directory (current)"),
    ("如 GPT-4o", "legacy input hint; the port renders none"),
    ("如 Qwen2.5", "legacy input hint; the port renders none"),
    ("https://api.example.com", "legacy input hint; the port renders none"),
    ("sk-", "legacy input hint; the port renders none"),
    ("如 128K", "legacy input hint; the port renders none"),
)

# The legacy keeps the chat screen's nodes in the tree behind a full-screen
# route; their pixels are absent, so they are not differences.
STALE = {
    "菜单", "LineCode", "上下文占用 0%", "权限", "新建对话", "更多操作",
    "从这里，开始。", "进入 设置 → 模型管理 → 添加模型，保存后再发送消息。",
    "添加模型", "请先到设置 → 模型管理配置模型", "选择图片发送", "发送消息",
}


def elements(path: Path) -> list[tuple[str, int, int, int, int]]:
    out: list[tuple[str, int, int, int, int]] = []
    if not path.is_file():
        return out
    for node in ET.parse(path).getroot().iter("node"):
        value = (node.get("text", "") or node.get("content-desc", "")).strip()
        match = BOUNDS.fullmatch(node.get("bounds", ""))
        if not value or not match:
            continue
        x0, y0, x1, y1 = (int(g) for g in match.groups())
        out.append((value, x0, y0, x1, y1))
    return out


def regions(mask: np.ndarray, minimum: int) -> list[tuple[int, int, int, int, int]]:
    """Connected components of `mask`, largest first."""
    height, width = mask.shape
    seen = np.zeros_like(mask, dtype=bool)
    found: list[tuple[int, int, int, int, int]] = []
    for y in range(height):
        for x in range(width):
            if not mask[y, x] or seen[y, x]:
                continue
            queue = deque([(x, y)])
            seen[y, x] = True
            x0 = x1 = x
            y0 = y1 = y
            count = 0
            while queue:
                cx, cy = queue.popleft()
                count += 1
                x0, x1 = min(x0, cx), max(x1, cx)
                y0, y1 = min(y0, cy), max(y1, cy)
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1),
                               (cx, cy - 1)):
                    if 0 <= nx < width and 0 <= ny < height and mask[ny, nx] \
                            and not seen[ny, nx]:
                        seen[ny, nx] = True
                        queue.append((nx, ny))
            if count >= minimum:
                found.append((count, x0, y0, x1, y1))
    found.sort(reverse=True)
    return found


def overlapping(items, box, pad: int = 24):
    _, x0, y0, x1, y1 = box
    return [
        value for value, ax0, ay0, ax1, ay1 in items
        if ax0 < x1 + pad and ax1 > x0 - pad and ay0 < y1 + pad and ay1 > y0 - pad
    ]


def explain(value: str) -> str | None:
    for needle, reason in EXPLAINED:
        if needle in value:
            return reason
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--only", default=None)
    parser.add_argument("--threshold", type=int, default=60)
    parser.add_argument("--minimum", type=int, default=250,
                        help="changed pixels before a region is reported")
    parser.add_argument("--top", type=int, default=6)
    parser.add_argument("--crop-dir", type=Path, default=None,
                        help="write a side-by-side crop per reported region")
    args = parser.parse_args()

    baseline_dir = args.directory / "baseline"
    candidate_dir = args.directory / "candidate"
    names = sorted(p.stem for p in baseline_dir.glob("*.png"))
    if args.only:
        names = [n for n in names if args.only in n]
    if args.crop_dir:
        args.crop_dir.mkdir(parents=True, exist_ok=True)

    for name in names:
        left_img = Image.open(baseline_dir / f"{name}.png").convert("RGB")
        right_img = Image.open(candidate_dir / f"{name}.png").convert("RGB")
        a = np.asarray(left_img).astype(int)
        b = np.asarray(right_img).astype(int)
        diff = np.abs(a - b).sum(axis=2)
        mask = diff > args.threshold
        if mask.sum() == 0:
            continue

        left_text = elements(baseline_dir / f"{name}.xml")
        right_text = elements(candidate_dir / f"{name}.xml")
        found = regions(mask, args.minimum)
        if not found:
            continue

        print(f"=== {name}  差异像素 {int(mask.sum())}  "
              f"({mask.sum() / mask.size * 100:.2f}%)  区域 {len(found)}")
        for rank, (count, x0, y0, x1, y1) in enumerate(found[:args.top]):
            box = (count, x0, y0, x1, y1)
            lvals = overlapping(left_text, box)
            rvals = overlapping(right_text, box)
            only_l = [v for v in lvals if v not in rvals and v not in STALE]
            only_r = [v for v in rvals if v not in lvals and v not in STALE]
            notes = {explain(v) for v in only_l + only_r}
            notes.discard(None)
            print(f"  [{rank}] {count:6d}px  x{x0}-{x1} y{y0}-{y1}")
            if only_l and not only_r:
                print(f"        仅基线有此文字: {only_l[:3]}")
            elif only_r and not only_l:
                print(f"        仅候选有此文字: {only_r[:3]}")
            elif only_l or only_r:
                print(f"        左侧: {only_l[:2]}")
                print(f"        右侧: {only_r[:2]}")
            elif notes:
                print(f"        已说明: {'; '.join(sorted(notes))}")
            elif lvals:
                print(f"        两侧文字相同，仅位置/字形不同: {lvals[:3]}")
            if args.crop_dir:
                pad = 16
                cx0, cy0 = max(0, x0 - pad), max(0, y0 - pad)
                cx1 = min(left_img.width, x1 + pad)
                cy1 = min(left_img.height, y1 + pad)
                lcrop = left_img.crop((cx0, cy0, cx1, cy1))
                rcrop = right_img.crop((cx0, cy0, cx1, cy1))
                strip = Image.new("RGB",
                                  (lcrop.width * 2 + 8, lcrop.height),
                                  (255, 0, 0))
                strip.paste(lcrop, (0, 0))
                strip.paste(rcrop, (lcrop.width + 8, 0))
                strip.save(args.crop_dir / f"{name}-{rank}.png")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
