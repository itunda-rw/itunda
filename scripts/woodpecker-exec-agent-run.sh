#!/usr/bin/env bash
# Launched by infra/launchd/com.itunda.woodpecker-exec-agent.plist. Not meant to be
# run directly except for manual testing -- use
# scripts/woodpecker-exec-agent-install.sh to install/start/stop the real service.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/infra/ci/woodpecker-exec-agent.env"
BINARY="$HOME/Library/Application Support/itunda-woodpecker-agent/woodpecker-agent"

if [ ! -f "$ENV_FILE" ]; then
  echo "Missing $ENV_FILE -- copy infra/ci/woodpecker-exec-agent.env.example and fill in real values first." >&2
  exit 1
fi
if [ ! -x "$BINARY" ]; then
  echo "Missing $BINARY -- run scripts/woodpecker-exec-agent-install.sh install first." >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
source "$ENV_FILE"
set +a

exec "$BINARY" agent
