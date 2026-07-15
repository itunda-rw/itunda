#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/private-cloud-inventory.sh"

MODE="${1:-summary}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-cmdb.sh <command>

Commands:
  desired  Print the desired inventory source-of-truth JSON
  live     Print the live audited private-cloud JSON
  summary  Print a combined desired/live CMDB document
EOF
}

require_cmd jq

if [[ ! -f "$INVENTORY_PATH" ]]; then
  echo "Inventory file not found: $INVENTORY_PATH" >&2
  exit 1
fi

case "$MODE" in
  desired)
    cat "$INVENTORY_PATH"
    ;;
  live)
    bash "$ROOT_DIR/scripts/private-cloud-audit.sh" json
    ;;
  summary)
    desired_file="$(mktemp "${TMPDIR:-/tmp}/private-cloud-desired.XXXXXX")"
    live_file="$(mktemp "${TMPDIR:-/tmp}/private-cloud-live.XXXXXX")"
    cat "$INVENTORY_PATH" > "$desired_file"
    bash "$ROOT_DIR/scripts/private-cloud-audit.sh" json > "$live_file"
    jq -n \
      --arg generated_at "$(date '+%Y-%m-%d %H:%M:%S %Z')" \
      --slurpfile desired "$desired_file" \
      --slurpfile live "$live_file" \
      '{
        generatedAt: $generated_at,
        desired: $desired[0],
        live: $live[0],
        gapSummary: {
          desiredPlatform: $desired[0].platform.targetShape,
          liveNodeCount: ($live[0].nodes | length),
          desiredNodeCount: ($desired[0].platform.nodes | length),
          desiredWorkloadClusterCount: ($desired[0].platform.clusters | map(select(.role == "workload")) | length),
          liveKafkaClusterCount: $live[0].summary.kafka.clusterCount,
          liveMysqlWriterCount: $live[0].summary.mysql.writerCount
        }
      }'
    rm -f "$desired_file" "$live_file"
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
