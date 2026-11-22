#!/usr/bin/env python3
"""Install and exercise customization on an explicitly selected emulator, never a phone."""

import argparse
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

PACKAGE = "io.github.unknown27s.dsucontroller"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--apk", default="app/build/outputs/apk/debug/app-debug.apk")
    parser.add_argument("--output", default="/tmp/opencode/dsu-emulator")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("This script only runs on an emulator serial")
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=True)

    def adb(*command: str) -> bytes:
        return subprocess.check_output([args.adb, "-s", args.serial, *command], timeout=90)

    def nodes() -> list[ET.Element]:
        adb("shell", "uiautomator", "dump", "/sdcard/dsu-window.xml")
        return list(ET.fromstring(adb("shell", "cat", "/sdcard/dsu-window.xml")).iter("node"))

    def bounds(node: ET.Element) -> tuple[int, int, int, int]:
        values = tuple(map(int, re.findall(r"\d+", node.attrib["bounds"])))
        if len(values) != 4:
            raise AssertionError("invalid UI bounds")
        return values

    def tap_text(text: str, scroll: bool = False) -> None:
        for attempt in range(10 if scroll else 1):
            tree = nodes()
            matches = [node for node in tree if text in node.get("text", "").splitlines()]
            if matches:
                break
            if scroll:
                x1, y1, x2, y2 = bounds(tree[0])
                adb("shell", "input", "swipe", str((x1+x2)//2), str(int(y1+(y2-y1)*.80)),
                    str((x1+x2)//2), str(int(y1+(y2-y1)*.25)), "350")
        else:
            raise AssertionError(f"Missing UI text: {text}")
        x1, y1, x2, y2 = bounds(matches[0])
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))

    def screenshot(name: str) -> None:
        (output / name).write_bytes(adb("exec-out", "screencap", "-p"))

    adb("install", "-r", args.apk)
    # Deterministic fixture: this intentionally resets only this app on the selected emulator.
    adb("shell", "pm", "clear", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    nodes()
    screenshot("01-controller.png")
    tap_text("☰  Settings")
    screenshot("05-settings.png")
    tap_text("Violet", scroll=True)
    # Return to the controller, then reopen settings at the top of the list.
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    tap_text("☰  Settings")
    tap_text("Layout editor", scroll=True)
    canvas = next(node for node in nodes() if node.get("content-desc") == "Layout editor: drag a control to move it")
    x1, y1, x2, y2 = bounds(canvas)
    width, height = x2 - x1, y2 - y1
    adb("shell", "input", "swipe", str(int(x1 + width * .07)), str(int(y1 + height * .12)),
        str(int(x1 + width * .25)), str(int(y1 + height * .3)), "600")
    # Selecting L2 exposes its per-control size slider.
    sliders = [node for node in nodes() if node.get("class") == "android.widget.SeekBar"]
    if not sliders:
        raise AssertionError("Selected control size slider was not exposed")
    sx1, sy1, sx2, sy2 = bounds(sliders[0])
    adb("shell", "input", "tap", str(int(sx1 + (sx2 - sx1) * .8)), str((sy1 + sy2) // 2))
    tap_text("Hide")
    screenshot("02-custom-layout.png")
    tap_text("Save layout")
    assert not any(node.get("content-desc") == "Layout editor: drag a control to move it" for node in nodes())
    # Activity pause flushes pending SharedPreferences.apply writes before inspecting disk.
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    prefs = ET.fromstring(adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/controller.xml"))
    layout = json.loads(next(item.text for item in prefs if item.get("name") == "layout"))
    l2 = next(item for item in layout["controls"] if item["id"] == "L2")
    assert l2["x"] > .15 and l2["y"] > .2, l2
    assert l2["scale"] > 1.1 and not l2["visible"], l2
    assert next(item.get("value") for item in prefs if item.get("name") == "accent") == "2"
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    screenshot("03-reopened-layout.png")
    tap_text("☰  Settings")
    tap_text("Compact gamepad", scroll=True)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    assert any("COMPACT GAMEPAD" in node.get("text", "") for node in nodes())
    screenshot("06-compact-preset.png")
    tap_text("☰  Settings")
    tap_text("Custom layout", scroll=True)
    tap_text("Layout editor", scroll=True)
    screenshot("04-restored-editor.png")
    tap_text("Reset")
    tap_text("Cancel")
    tap_text("Discard")
    restored = ET.fromstring(adb("shell", "run-as", PACKAGE, "cat", "shared_prefs/controller.xml"))
    assert next(item.text for item in restored if item.get("name") == "layout") == next(item.text for item in prefs if item.get("name") == "layout")
    print("PASS: settings, color, move/resize/hide, process restart, preset switching and discard preserve custom layout")
    print("Evidence:", output)


if __name__ == "__main__":
    main()
