#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLIST_SRC="$ROOT_DIR/infra/launchd/com.itunda.private-cloud-drills.plist"
PLIST_LABEL="com.itunda.private-cloud-drills"
PLIST_DEST="$HOME/Library/LaunchAgents/${PLIST_LABEL}.plist"

MODE="${1:-help}"

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-drill-schedule-install.sh <command>

Commands:
  install    Render the launchd plist with this repo's absolute path and load it
             (runs scripts/private-cloud-scheduled-drills.sh run every 6 hours).
  uninstall  Unload and remove the launchd job.
  status     Show whether the job is loaded.
EOF
}

case "$MODE" in
  install)
    sed "s#REPO_ROOT#${ROOT_DIR}#" "$PLIST_SRC" > "$PLIST_DEST"
    launchctl unload "$PLIST_DEST" >/dev/null 2>&1 || true
    launchctl load "$PLIST_DEST"
    echo "Installed and loaded ${PLIST_DEST}"
    echo "Logs: /tmp/itunda-private-cloud-drills.launchd.log"
    echo "Drill output: ${ITUNDA_PRIVATE_CLOUD_DRILL_LOG_DIR:-$HOME/Library/Logs/itunda-private-cloud}/drills.log"
    ;;
  uninstall)
    launchctl unload "$PLIST_DEST" >/dev/null 2>&1 || true
    rm -f "$PLIST_DEST"
    echo "Uninstalled ${PLIST_DEST}"
    ;;
  status)
    launchctl list | grep "$PLIST_LABEL" || echo "Not loaded."
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
