#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-help}"

PACKAGE_MANAGER=()
CHILD_PIDS=()

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT_DIR/.env"
  set +a
fi

detect_package_manager() {
  if [[ "${npm_config_user_agent:-}" == *"yarn/"* ]] && [[ -n "${npm_execpath:-}" ]]; then
    if [[ "$npm_execpath" == *.js || "$npm_execpath" == *.cjs || "$npm_execpath" == *.mjs ]]; then
      PACKAGE_MANAGER=(node "$npm_execpath")
    else
      PACKAGE_MANAGER=("$npm_execpath")
    fi
    return
  fi

  if command -v yarn >/dev/null 2>&1; then
    PACKAGE_MANAGER=(yarn)
    return
  fi

  if command -v corepack >/dev/null 2>&1; then
    PACKAGE_MANAGER=(corepack yarn)
    return
  fi

  echo "Yarn 4 is required for the JS workspaces. Install it or run these commands via 'yarn <script>'." >&2
  exit 1
}

run_workspace() {
  local workspace="$1"
  local script_name="$2"

  detect_package_manager
  (cd "$ROOT_DIR" && "${PACKAGE_MANAGER[@]}" workspace "$workspace" "$script_name")
}

run_workspace_in_background() {
  local workspace="$1"
  local script_name="$2"

  run_workspace "$workspace" "$script_name" &
  CHILD_PIDS+=("$!")
}

terminate_children() {
  local pid
  for pid in "${CHILD_PIDS[@]:-}"; do
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
    fi
  done
}

wait_for_port() {
  local host="$1"
  local port="$2"
  local description="$3"
  local attempts="${4:-60}"
  local i

  for ((i = 1; i <= attempts; i++)); do
    if nc -z "$host" "$port" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  echo "Timed out waiting for $description on $host:$port" >&2
  return 1
}

start_infra() {
  (
    cd "$ROOT_DIR"
    docker compose -f infra/docker-compose.yml up -d \
      mysql \
      redis-node-1 \
      zookeeper \
      kafka-broker-1 \
      debezium
  )
}

start_backend() {
  local default_db_port="3306"
  local default_redis_port="6379"

  if [[ "${ITUNDA_LOCAL_INFRA:-0}" == "1" ]]; then
    default_db_port="3307"
    default_redis_port="16379"
  fi

  (
    cd "$ROOT_DIR/services/backend"
    DB_HOST="${DB_HOST:-localhost}" \
    DB_PORT="${DB_PORT:-$default_db_port}" \
    DB_NAME="${DB_NAME:-itunda}" \
    DB_USER="${DB_USER:-itunda}" \
    DB_PASSWORD="${DB_PASSWORD:-itunda_password}" \
    REDIS_HOST="${REDIS_HOST:-localhost}" \
    REDIS_PORT="${REDIS_PORT:-$default_redis_port}" \
    KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}" \
    ./gradlew :app:bootRun
  )
}

start_gateway() {
  (
    export BACKEND_URL="${BACKEND_URL:-http://localhost:4001}"
    export LEDGER_SERVICE_URL="${LEDGER_SERVICE_URL:-http://localhost:8082}"
    export PAYMENT_SERVICE_URL="${PAYMENT_SERVICE_URL:-http://localhost:8081}"
    run_workspace "@itunda/api-gateway" start
  )
}

start_web_dev() {
  run_workspace_in_background "bank-mfe" "dev"
  run_workspace_in_background "kyc-mfe" "dev"
  run_workspace_in_background "host-app" "dev"
  wait
}

build_web() {
  run_workspace "bank-mfe" "build"
  run_workspace "kyc-mfe" "build"
  run_workspace "host-app" "build"
}

lint_web() {
  run_workspace "bank-mfe" "lint"
  run_workspace "kyc-mfe" "lint"
  run_workspace "host-app" "lint"
}

start_all() {
  trap 'terminate_children' EXIT INT TERM

  start_backend &
  CHILD_PIDS+=("$!")
  wait_for_port localhost 4001 "canonical backend" 180

  start_gateway &
  CHILD_PIDS+=("$!")
  run_workspace_in_background "bank-mfe" "dev"
  run_workspace_in_background "kyc-mfe" "dev"
  run_workspace_in_background "host-app" "dev"
  wait
}

start_all_local() {
  ITUNDA_LOCAL_INFRA=1
  start_infra
  wait_for_port localhost 3307 "MySQL" 90
  wait_for_port localhost 16379 "Redis" 90

  start_all
}

print_help() {
  cat <<'EOF'
Usage: scripts/local-ecosystem.sh <command>

Commands:
  infra       Start local Docker dependencies (MySQL, Redis, Kafka, Debezium)
  backend     Run the canonical Kotlin backend against the configured infra
  gateway     Run the API gateway against the canonical backend
  web-dev     Run bank-mfe, kyc-mfe, and host-app together
  web-build   Build bank-mfe, kyc-mfe, and host-app
  web-lint    Lint bank-mfe, kyc-mfe, and host-app
  all         Start backend, gateway, and web apps against existing infra
  all-local   Start Docker infra first, then backend, gateway, and web apps
EOF
}

case "$MODE" in
  infra)
    start_infra
    ;;
  backend)
    start_backend
    ;;
  gateway)
    start_gateway
    ;;
  web-dev)
    trap 'terminate_children' EXIT INT TERM
    start_web_dev
    ;;
  web-build)
    build_web
    ;;
  web-lint)
    lint_web
    ;;
  all)
    start_all
    ;;
  all-local)
    start_all_local
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
