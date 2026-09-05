#!/usr/bin/env python3
"""Run identical ADB scenarios against two APKs and produce pixel/function diffs."""

from __future__ import annotations

import argparse
import dataclasses
import json
import math
import re
import shutil
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any, Iterable

from PIL import Image, ImageChops, ImageDraw


@dataclasses.dataclass(frozen=True)
class Config:
    adb: str
    serial: str
    baseline_package: str
    candidate_package: str
    activity: str
    output: Path
    baseline_apk: Path
    candidate_apk: Path
    scenario_file: Path


class TestFailure(RuntimeError):
    pass


def run(command: Iterable[str], *, binary: bool = False, check: bool = True) -> bytes | str:
    completed = subprocess.run(
        list(command),
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and completed.returncode != 0:
        raise TestFailure(
            f"command failed ({completed.returncode}): {' '.join(command)}\n"
            f"{completed.stderr.decode('utf-8', errors='replace')}"
        )
    return completed.stdout if binary else completed.stdout.decode("utf-8", errors="replace")


def adb(config: Config, *arguments: str, binary: bool = False, check: bool = True) -> bytes | str:
    return run([config.adb, "-s", config.serial, *arguments], binary=binary, check=check)


def wait_for_device(config: Config) -> None:
    adb(config, "wait-for-device")
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        booted = str(adb(config, "shell", "getprop", "sys.boot_completed", check=False)).strip()
        if booted == "1":
            return
        time.sleep(1)
    raise TestFailure("device did not finish booting within 180 seconds")


def stabilize_device(config: Config) -> None:
    for namespace, key, value in (
        ("global", "window_animation_scale", "0"),
        ("global", "transition_animation_scale", "0"),
        ("global", "animator_duration_scale", "0"),
        ("system", "font_scale", "1.0"),
    ):
        adb(config, "shell", "settings", "put", namespace, key, value)
    adb(config, "shell", "wm", "size", "1080x2400")
    adb(config, "shell", "wm", "density", "420")


def install_apk(config: Config, apk: Path) -> None:
    output = str(adb(config, "install", "-r", "-d", "-t", str(apk)))
    if "Success" not in output:
        raise TestFailure(f"APK installation failed: {output}")


def launch(config: Config, package: str) -> None:
    component = f"{package}/{config.activity}"
    adb(config, "shell", "am", "force-stop", package)
    adb(config, "shell", "am", "start", "-W", "-n", component)


def dump_ui(config: Config) -> tuple[str, str]:
    adb(config, "shell", "uiautomator", "dump", "/sdcard/linecode-window.xml", check=False)
    xml = str(adb(config, "shell", "cat", "/sdcard/linecode-window.xml", check=False))
    activity = str(
        adb(config, "shell", "dumpsys", "activity", "activities", check=False)
    )
    return xml, activity


def screencap(config: Config, destination: Path) -> None:
    destination.write_bytes(adb(config, "exec-out", "screencap", "-p", binary=True))  # type: ignore[arg-type]
    with Image.open(destination) as image:
        image.verify()


def all_visible_text(xml: str) -> str:
    if not xml.strip():
        return ""
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return xml
    values: list[str] = []
    for node in root.iter():
        for name in ("text", "content-desc"):
            value = node.attrib.get(name, "").strip()
            if value:
                values.append(value)
    return "\n".join(values)


def find_text_center(xml: str, value: str, contains: bool) -> tuple[int, int] | None:
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return None
    for node in root.iter():
        labels = (node.attrib.get("text", ""), node.attrib.get("content-desc", ""))
        if not any(value in label if contains else value == label for label in labels):
            continue
        match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
        if match:
            left, top, right, bottom = (int(part) for part in match.groups())
            return (left + right) // 2, (top + bottom) // 2
    return None


def execute_action(config: Config, action: dict[str, Any], label: str) -> list[str]:
    failures: list[str] = []
    targets = action.get("targets")
    if targets is not None and label not in targets:
        return failures
    kind = action["type"]
    if kind == "sleep":
        time.sleep(float(action.get("seconds", 1)))
    elif kind == "tap":
        adb(config, "shell", "input", "tap", str(action["x"]), str(action["y"]))
    elif kind == "swipe":
        adb(
            config,
            "shell",
            "input",
            "swipe",
            str(action["x1"]),
            str(action["y1"]),
            str(action["x2"]),
            str(action["y2"]),
            str(action.get("duration_ms", 300)),
        )
    elif kind == "keyevent":
        adb(config, "shell", "input", "keyevent", str(action["key"]))
    elif kind == "text":
        value = str(action["value"]).replace(" ", "%s")
        adb(config, "shell", "input", "text", value)
    elif kind == "tap_text":
        xml, _ = dump_ui(config)
        value = str(action["value"])
        center = find_text_center(xml, value, bool(action.get("contains")))
        if center is None:
            if not action.get("optional", False):
                failures.append(f"tap_text could not find {value!r}")
        else:
            adb(config, "shell", "input", "tap", str(center[0]), str(center[1]))
    elif kind in {"expect_text", "expect_no_text", "expect_activity"}:
        xml, activities = dump_ui(config)
        haystack = activities if kind == "expect_activity" else all_visible_text(xml)
        expected = str(action["value"])
        found = expected in haystack
        if kind == "expect_no_text":
            found = not found
        if not found:
            failures.append(f"{kind} failed for {expected!r}")
    else:
        raise TestFailure(f"unknown action type: {kind}")
    return failures


def run_suite(
    config: Config,
    label: str,
    apk: Path,
    package: str,
    suite: dict[str, Any],
) -> dict[str, Any]:
    install_apk(config, apk)
    adb(config, "shell", "pm", "clear", package)
    locale = str(suite.get("locale", "zh-CN"))
    adb(
        config,
        "shell",
        "cmd",
        "locale",
        "set-app-locales",
        package,
        "--user",
        "0",
        "--locales",
        locale,
    )
    label_dir = config.output / label
    label_dir.mkdir(parents=True, exist_ok=True)
    results: dict[str, Any] = {}

    launch(config, package)
    for action in suite.get("setup", []):
        execute_action(config, action, label)

    for scenario in suite["scenarios"]:
        name = str(scenario["name"])
        if scenario.get("relaunch", True):
            launch(config, package)
            time.sleep(float(suite.get("launch_settle_seconds", 0.8)))
        failures: list[str] = []
        for action in scenario.get("actions", []):
            failures.extend(execute_action(config, action, label))
        time.sleep(float(scenario.get("settle_seconds", suite.get("settle_seconds", 1.0))))
        screenshot = label_dir / f"{name}.png"
        screencap(config, screenshot)
        xml, activities = dump_ui(config)
        (label_dir / f"{name}.xml").write_text(xml, encoding="utf-8")
        (label_dir / f"{name}.activities.txt").write_text(activities, encoding="utf-8")
        results[name] = {"functional_failures": failures, "screenshot": str(screenshot)}
    return results


def mask_rectangles(image: Image.Image, masks: list[list[int]]) -> Image.Image:
    result = image.copy()
    draw = ImageDraw.Draw(result)
    for left, top, right, bottom in masks:
        draw.rectangle((left, top, right, bottom), fill=(0, 0, 0, 255))
    return result


def compare_images(
    baseline_path: Path,
    candidate_path: Path,
    diff_path: Path,
    masks: list[list[int]],
) -> dict[str, Any]:
    with Image.open(baseline_path) as baseline_source, Image.open(candidate_path) as candidate_source:
        baseline = baseline_source.convert("RGBA")
        candidate = candidate_source.convert("RGBA")
    if baseline.size != candidate.size:
        return {
            "passed": False,
            "reason": "dimension_mismatch",
            "baseline_size": baseline.size,
            "candidate_size": candidate.size,
        }
    baseline = mask_rectangles(baseline, masks)
    candidate = mask_rectangles(candidate, masks)
    difference = ImageChops.difference(baseline, candidate).convert("RGB")
    difference.save(diff_path)
    histogram = difference.histogram()
    channel_pixels = baseline.width * baseline.height * 3
    absolute_sum = sum((index % 256) * count for index, count in enumerate(histogram))
    squared_sum = sum(((index % 256) ** 2) * count for index, count in enumerate(histogram))
    bbox = difference.getbbox()
    different_pixels = sum(
        1 for pixel in difference.get_flattened_data() if pixel != (0, 0, 0)
    )
    return {
        "passed": bbox is None,
        "size": baseline.size,
        "mean_absolute_error": absolute_sum / channel_pixels,
        "root_mean_square_error": math.sqrt(squared_sum / channel_pixels),
        "different_pixels": different_pixels,
        "different_pixel_ratio": different_pixels / (baseline.width * baseline.height),
        "difference_bounds": bbox,
        "diff": str(diff_path),
    }


def parse_args() -> Config:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--baseline-apk", required=True, type=Path)
    parser.add_argument("--candidate-apk", required=True, type=Path)
    parser.add_argument("--scenarios", required=True, type=Path)
    parser.add_argument("--output", default=Path("artifacts/ui-parity"), type=Path)
    parser.add_argument("--baseline-package", default="cn.lineai")
    parser.add_argument("--candidate-package", default="cn.lineai")
    parser.add_argument("--activity", default="cn.lineai.MainActivity")
    parser.add_argument("--adb", default=shutil.which("adb") or "adb")
    args = parser.parse_args()
    return Config(
        adb=args.adb,
        serial=args.serial,
        baseline_package=args.baseline_package,
        candidate_package=args.candidate_package,
        activity=args.activity,
        output=args.output.resolve(),
        baseline_apk=args.baseline_apk.resolve(),
        candidate_apk=args.candidate_apk.resolve(),
        scenario_file=args.scenarios.resolve(),
    )


def main() -> int:
    config = parse_args()
    suite = json.loads(config.scenario_file.read_text(encoding="utf-8"))
    config.output.mkdir(parents=True, exist_ok=True)
    wait_for_device(config)
    stabilize_device(config)

    baseline = run_suite(
        config, "baseline", config.baseline_apk, config.baseline_package, suite
    )
    candidate = run_suite(
        config, "candidate", config.candidate_apk, config.candidate_package, suite
    )
    masks = suite.get("pixel_masks", [])
    comparisons: dict[str, Any] = {}
    for scenario in suite["scenarios"]:
        name = str(scenario["name"])
        comparisons[name] = compare_images(
            config.output / "baseline" / f"{name}.png",
            config.output / "candidate" / f"{name}.png",
            config.output / f"{name}.diff.png",
            masks + scenario.get("pixel_masks", []),
        )

    report = {"baseline": baseline, "candidate": candidate, "pixel_comparisons": comparisons}
    report_path = config.output / "report.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(report_path)
    functional_failures = sum(
        len(result["functional_failures"])
        for group in (baseline, candidate)
        for result in group.values()
    )
    pixel_failures = sum(not comparison["passed"] for comparison in comparisons.values())
    print(f"functional failures: {functional_failures}; non-identical screenshots: {pixel_failures}")
    return 1 if functional_failures or pixel_failures else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except TestFailure as error:
        print(error, file=sys.stderr)
        raise SystemExit(2)
