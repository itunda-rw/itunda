#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="${ITUNDA_PRIVATE_CLOUD_DRILL_LOG_DIR:-$HOME/Library/Logs/itunda-private-cloud}"
LOG_FILE="$LOG_DIR/drills.log"

MODE="${1:-run}"

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-scheduled-drills.sh <command>

Commands:
  run   Audit + bidirectional Kafka MM2 drill. Both are read-only/non-mutating against the live
        MySQL/Redis roles, so this is safe to run unattended on a schedule (cron/launchd).

Not included here on purpose: the MySQL failover round-trip drill mutates the live writer role
twice. Run it by hand when you want to rehearse it:
  yarn private-cloud:failover promote <standby-node>
  yarn private-cloud:failover promote <original-writer-node>
EOF
}

log_section() {
  local title="$1"
  {
    echo
    echo "===== $(date -u '+%Y-%m-%dT%H:%M:%SZ') :: ${title} ====="
  } >>"$LOG_FILE"
}

run_audit() {
  log_section "audit"
  if ! bash "$ROOT_DIR/scripts/private-cloud-audit.sh" audit >>"$LOG_FILE" 2>&1; then
    echo "audit FAILED, see $LOG_FILE" >&2
    return 1
  fi
}

run_kafka_drill() {
  log_section "kafka-drill"
  if ! bash "$ROOT_DIR/scripts/private-cloud-kafka-drill.sh" drill >>"$LOG_FILE" 2>&1; then
    echo "kafka drill FAILED, see $LOG_FILE" >&2
    return 1
  fi
}

mkdir -p "$LOG_DIR"

case "$MODE" in
  run)
    run_audit
    run_kafka_drill
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac

echo "Drill run complete: $(date -u '+%Y-%m-%dT%H:%M:%SZ'). Log: $LOG_FILE"
