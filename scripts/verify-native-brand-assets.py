#!/usr/bin/env python3
"""Verify generated native Itunda launcher assets without third-party packages."""
import json
import struct
import subprocess
from pathlib import Path
from tempfile import NamedTemporaryFile

ROOT = Path(__file__).resolve().parents[1]
failures = []
BRAND_ROOT = ROOT / "packages/brand-assets/canonical/itunda-brand"
APP_ICON_SVG = BRAND_ROOT / "app-icons/itunda-app-icon.svg"
ADAPTIVE_FOREGROUND_SVG = BRAND_ROOT / "app-icons/itunda-android-adaptive-foreground.svg"

def rendered_png(svg_path, size):
    result = subprocess.run(
        ["rsvg-convert", "--format=png", f"--width={size}", f"--height={size}", str(svg_path)],
        check=True, capture_output=True,
    )
    return result.stdout

def check_render_matches(path, svg_path, size):
    try:
        expected = rendered_png(svg_path, size)
        check(path.read_bytes() == expected, f"{path}: pixels differ from canonical {svg_path}")
    except (OSError, subprocess.CalledProcessError) as exc:
        check(False, f"could not render canonical {svg_path}: {exc}")

def check(condition, message):
    if not condition:
        failures.append(message)

def png_size(path):
    data = path.read_bytes()
    check(data[:8] == b"\x89PNG\r\n\x1a\n", f"{path}: invalid PNG signature")
    if data[:8] != b"\x89PNG\r\n\x1a\n" or len(data) < 24:
        return (0, 0)
    check(data[12:16] == b"IHDR", f"{path}: missing PNG IHDR")
    return struct.unpack(">II", data[16:24])

for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    for name in ["ic_launcher.png", "ic_launcher_round.png"]:
        path = ROOT / f"android/app/src/main/res/mipmap-{density}/{name}"
        check(path.is_file(), f"missing {path}")
        if path.is_file():
            check(png_size(path) == (size, size), f"{path}: expected {size}x{size}, got {png_size(path)}")\n        check_render_matches(path, APP_ICON_SVG, size)\n            check_render_matches(path, APP_ICON_SVG, size)

adaptive = ROOT / "android/app/src/main/res/drawable/itunda_adaptive_foreground.png"
check(adaptive.is_file(), f"missing {adaptive}")
if adaptive.is_file():
    check(png_size(adaptive) == (108, 108), f"{adaptive}: expected 108x108, got {png_size(adaptive)}")\n    check_render_matches(adaptive, ADAPTIVE_FOREGROUND_SVG, 108)

icon_dir = ROOT / "ios/App/Resources/Assets.xcassets/AppIcon.appiconset"
catalog = json.loads((icon_dir / "Contents.json").read_text())
expected = {
    "icon-20.png": 20, "icon-29.png": 29, "icon-40.png": 40,
    "icon-58.png": 58, "icon-60.png": 60, "icon-76.png": 76,
    "icon-80.png": 80, "icon-87.png": 87, "icon-120.png": 120,
    "icon-152.png": 152, "icon-167.png": 167, "icon-180.png": 180,
    "icon-1024.png": 1024,
}
referenced = {item["filename"] for item in catalog.get("images", [])}
for filename, size in expected.items():
    path = icon_dir / filename
    check(filename in referenced, f"{filename}: not referenced by AppIcon Contents.json")
    check(path.is_file(), f"missing {path}")
    if path.is_file():
        check(png_size(path) == (size, size), f"{path}: expected {size}x{size}, got {png_size(path)}")

foreground_xml = (ROOT / "android/app/src/main/res/drawable/ic_launcher_foreground.xml").read_text()
background_xml = (ROOT / "android/app/src/main/res/drawable/ic_launcher_background.xml").read_text()
check("@drawable/itunda_adaptive_foreground" in foreground_xml, "Android adaptive foreground does not reference generated canonical raster")
check("#8F89FF" in background_xml, "Android adaptive background does not use v3 brand color")

if failures:
    print("\n".join("FAIL: " + item for item in failures))
    raise SystemExit(1)
print("Native Itunda v3 launcher assets verified: every Android/iOS raster is pixel-identical to the canonical v3 SVG render.")
