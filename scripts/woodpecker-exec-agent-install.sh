#!/usr/bin/env bash
# Installs/manages the macOS "local"-backend Woodpecker agent (handles ios-build
# only -- see infra/ci/docker-compose.yml's doc comment). Mirrors
# scripts/private-cloud-drill-schedule-install.sh's own install/uninstall/status
# pattern for launchd jobs in this repo.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLIST_SRC="$ROOT_DIR/infra/launchd/com.itunda.woodpecker-exec-agent.plist"
PLIST_LABEL="com.itunda.woodpecker-exec-agent"
PLIST_DEST="$HOME/Library/LaunchAgents/${PLIST_LABEL}.plist"
INSTALL_DIR="$HOME/Library/Application Support/itunda-woodpecker-agent"
BINARY="$INSTALL_DIR/woodpecker-agent"
WOODPECKER_VERSION="v3.18.0"

MODE="${1:-help}"

print_help() {
  cat <<'EOF'
Usage: scripts/woodpecker-exec-agent-install.sh <command>

Commands:
  install    Download the woodpecker-agent binary (if missing), render the launchd
             plist with this repo's absolute path, and load it as a persistent
             LaunchAgent (auto-restarts on crash/logout via KeepAlive).
  uninstall  Unload and remove the launchd job. Does not delete the downloaded
             binary or infra/ci/woodpecker-exec-agent.env.
  status     Show whether the job is loaded.

Prerequisite: infra/ci/woodpecker-exec-agent.env must exist first (copy
infra/ci/woodpecker-exec-agent.env.example and fill in real values -- the same
WOODPECKER_AGENT_SECRET as infra/ci/.env, and the real public WOODPECKER_SERVER
host:port).
EOF
}

detect_arch() {
  case "$(uname -m)" in
    arm64) echo "arm64" ;;
    x86_64) echo "amd64" ;;
    *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;;
  esac
}

case "$MODE" in
  install)
    if [ ! -f "$ROOT_DIR/infra/ci/woodpecker-exec-agent.env" ]; then
      echo "Missing infra/ci/woodpecker-exec-agent.env -- copy the .example file and fill in real values first." >&2
      exit 1
    fi
    if [ ! -x "$BINARY" ]; then
      arch="$(detect_arch)"
      mkdir -p "$INSTALL_DIR"
      url="https://github.com/woodpecker-ci/woodpecker/releases/download/${WOODPECKER_VERSION}/woodpecker-agent_darwin_${arch}.tar.gz"
      echo "Downloading $url"
      curl -fL "$url" -o "$INSTALL_DIR/agent.tar.gz"
      tar -xzf "$INSTALL_DIR/agent.tar.gz" -C "$INSTALL_DIR"
      rm -f "$INSTALL_DIR/agent.tar.gz"
      chmod +x "$BINARY"
    fi
    sed "s#REPO_ROOT#${ROOT_DIR}#" "$PLIST_SRC" > "$PLIST_DEST"
    launchctl unload "$PLIST_DEST" >/dev/null 2>&1 || true
    launchctl load "$PLIST_DEST"
    echo "Installed and loaded ${PLIST_DEST}"
    echo "Logs: /tmp/itunda-woodpecker-exec-agent.launchd.log"
    ;;
  uninstall)
    launchctl unload "$PLIST_DEST" >/dev/null 2>&1 || true
    rm -f "$PLIST_DEST"
    echo "Uninstalled ${PLIST_DEST} (binary and env file left in place)"
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
