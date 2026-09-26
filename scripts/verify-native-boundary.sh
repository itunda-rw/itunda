#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "\${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

fail=0

for path in "eas.json" ".eas" "expo.json" "app.config.js" "app.config.ts"; do
  if [ -e "$path" ]; then
    echo "::error::Native production boundary violation: $path exists."
    fail=1
  fi
done

for path in "packages/saronite/host-app/eas.json" "packages/saronite/host-app/.eas" "packages/saronite/host-app/app.config.js" "packages/saronite/host-app/app.config.ts"; do
  if [ -e "$path" ]; then
    echo "::error::Native production boundary violation: $path exists."
    fail=1
  fi
done

check_manifest() {
  local path="$1"
  if [ -f "$path" ] && grep -Eiq '"(expo|expo-[^"]+|eas-cli)"[[:space:]]*:' "$path"; then
    echo "::error::Expo/EAS dependency found in native production manifest: $path"
    fail=1
  fi
}

check_manifest "package.json"
check_manifest "packages/saronite/host-app/package.json"

if [ "$fail" -ne 0 ]; then
  exit 1
fi

echo "Native production boundary OK: Expo/Expo Go/EAS are not required by the native app architecture."
