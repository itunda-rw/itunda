#!/usr/bin/env bash
set -euo pipefail

ROOT="packages/brand-assets/canonical/itunda-brand"
ICON="$ROOT/app-icons/itunda-app-icon.svg"
ADAPTIVE_FOREGROUND="$ROOT/app-icons/itunda-android-adaptive-foreground.svg"
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

render 432 "android/app/src/main/res/drawable/itunda_adaptive_foreground.png" "$ADAPTIVE_FOREGROUND"

echo "Native Android and iOS launcher raster assets regenerated from canonical Itunda v3 SVGs."
