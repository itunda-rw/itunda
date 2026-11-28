#!/usr/bin/env bash
set -euo pipefail

ROOT="packages/brand-assets/canonical/itunda-brand"
ICON="$ROOT/app-icons/itunda-app-icon.svg"
# Android launcher: white background + the brand-colour symbol on a transparent layer (like Toss), so the
# same layer is the adaptive foreground, the themed-icon monochrome layer and the Android 12 splash icon.
# Source is the canonical transparent splash icon (not edited); it is placed at 83% inside the 108dp
# canvas so the knot stays inside the 66dp adaptive-icon safe zone.
ADAPTIVE_FOREGROUND="$ROOT/splash/itunda-splash-icon.svg"
test -s "$ICON"
test -s "$ADAPTIVE_FOREGROUND"
command -v rsvg-convert >/dev/null

render() {
  local size="$1" output="$2" src="${3:-$ICON}"
  mkdir -p "$(dirname "$output")"
  rsvg-convert --format=png --width="$size" --height="$size" "$src" > "$output"
}

# Android launcher bitmaps use the canonical full-background app icon. Android applies its own mask.
for spec in "mdpi:48" "hdpi:72" "xhdpi:96" "xxhdpi:144" "xxxhdpi:192"; do
  density="${spec%%:*}"
  size="${spec##*:}"
  render "$size" "android/app/src/main/res/mipmap-$density/ic_launcher.png"
  render "$size" "android/app/src/main/res/mipmap-$density/ic_launcher_round.png"
done

# iOS asset-catalog filenames are part of Contents.json's contract.
for spec in "icon-20:20" "icon-29:29" "icon-40:40" "icon-58:58" "icon-60:60" "icon-76:76" "icon-80:80" "icon-87:87" "icon-120:120" "icon-152:152" "icon-167:167" "icon-180:180" "icon-1024:1024"; do
  name="${spec%%:*}"
  size="${spec##*:}"
  render "$size" "ios/App/Resources/Assets.xcassets/AppIcon.appiconset/$name.png"
done

mkdir -p android/app/src/main/res/drawable-nodpi
rsvg-convert --format=png --page-width=432 --page-height=432 --width=358 --height=358 --left=37 --top=37 "$ADAPTIVE_FOREGROUND" > android/app/src/main/res/drawable-nodpi/itunda_adaptive_foreground.png

echo "Native Android and iOS launcher raster assets regenerated from canonical Itunda v3 SVGs."
