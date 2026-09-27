#!/usr/bin/env bash
set -euo pipefail

# Native IDS verification entrypoint.
# This intentionally uses Limrun for the actual native build/install boundary;
# GitHub Actions is only an optional orchestration layer.

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

bash ./scripts/verify-native-boundary.sh

if ! command -v lim >/dev/null 2>&1; then
  echo "ERROR: Limrun CLI is required."
  echo "Install with: npm install --global lim"
  exit 1
fi

if [[ -z "${LIM_API_KEY:-}" ]]; then
  echo "ERROR: LIM_API_KEY is required."
  exit 1
fi

echo "== Android: native Gradle build =="
lim gradle build . --project-path android --upload itunda-native-android.apk

echo "== Android: install on native emulator =="
lim android create --install-asset=itunda-native-android.apk

echo "== iOS: native Tuist/Xcode build =="
# The Xcode sandbox is remote; the local machine does not need Xcode.
# The generated Xcode project is included explicitly because Tuist artifacts
# are intentionally gitignored in this repository.
if command -v mise >/dev/null 2>&1; then
  (cd ios && mise install tuist@3.42.3 && mise exec tuist@3.42.3 -- tuist generate --no-open)
elif command -v tuist >/dev/null 2>&1; then
  echo "WARNING: using the installed Tuist; this repository's verified iOS generation line is 3.42.3."
  (cd ios && tuist generate --no-open)
else
  echo "INFO: Tuist 3.42.3 is required for native iOS project generation."
  echo "      Install mise or Tuist 3.42.3, then rerun this script."
  exit 2
fi

lim ios create --attach --no-open
lim xcode build . --scheme ItundaRiderApp --configuration Debug --include '^ios/.*\\.xcodeproj/'
lim xcode logs

echo
echo "Native IDS artifact build/install completed."
echo "Next gate: drive both installed native apps and capture accessibility/state evidence."
