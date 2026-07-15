#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/private-cloud-lib.sh"

MODE="${1:-help}"
TARGET_NODE="${2:-}"

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-failover.sh <command> [node]

Commands:
  status          Print the live private-cloud audit
  promote <node>  Promote a healthy replica to the MySQL writer and re-home replication
EOF
}

shell_quote() {
  printf '%q' "$1"
}

mysql_container_for_node() {
  local node="$1"

  container_name "$node" 'mysql-'
}

mysql_root_password_for_node() {
  local node="$1"
  local container="$2"

  container_env "$node" "$container" MYSQL_ROOT_PASSWORD
}

mysql_query() {
  local node="$1"
  local container="$2"
  local password="$3"
  local query="$4"
  local pw_quoted
  local query_quoted

  pw_quoted="$(shell_quote "$password")"
  query_quoted="$(shell_quote "$query")"
  run_vm "$node" "sudo docker exec -e MYSQL_PWD=${pw_quoted} ${container} mysql -uroot -Nse ${query_quoted}" 2>/dev/null
}

mysql_exec() {
  local node="$1"
  local container="$2"
  local password="$3"
  local query="$4"
  local pw_quoted
  local query_quoted

  pw_quoted="$(shell_quote "$password")"
  query_quoted="$(shell_quote "$query")"
  run_vm "$node" "sudo docker exec -e MYSQL_PWD=${pw_quoted} ${container} mysql -uroot -e ${query_quoted}" 2>/dev/null
}

node_ip() {
  local node="$1"

  run_vm "$node" "hostname -I | awk '{print \$1}'" | tr -d '\r'
}

mysql_role() {
  local node="$1"
  local container="$2"
  local password="$3"
  local read_only
  local super_read_only
  local status
  local source_host
  local replica_io
  local replica_sql

  read_only="$(mysql_query "$node" "$container" "$password" 'SELECT @@read_only;' | tr -d '\r')"
  super_read_only="$(mysql_query "$node" "$container" "$password" 'SELECT @@super_read_only;' | tr -d '\r')"
  status="$(mysql_exec "$node" "$container" "$password" 'SHOW REPLICA STATUS\G' || true)"
  source_host="$(printf '%s\n' "$status" | awk -F': ' '/Source_Host:/{print $2; exit}')"
  replica_io="$(printf '%s\n' "$status" | awk -F': ' '/Replica_IO_Running:/{print $2; exit}')"
  replica_sql="$(printf '%s\n' "$status" | awk -F': ' '/Replica_SQL_Running:/{print $2; exit}')"

  if [[ -n "$source_host" ]]; then
    if [[ "$read_only" == "1" && "$super_read_only" == "1" && "$replica_io" == "Yes" && "$replica_sql" == "Yes" ]]; then
      printf 'replica\n'
    else
      printf 'unsafe-replica\n'
    fi
    return 0
  fi

  if [[ "$read_only" == "0" && "$super_read_only" == "0" ]]; then
    printf 'writer\n'
  else
    printf 'unknown\n'
  fi
}

mysql_replica_lag() {
  local node="$1"
  local container="$2"
  local password="$3"

  mysql_exec "$node" "$container" "$password" 'SHOW REPLICA STATUS\G' | awk -F': ' '/Seconds_Behind_Source:/{print $2; exit}' | tr -d '\r'
}

mysql_replication_user() {
  local node="$1"
  local container="$2"
  local password="$3"

  mysql_exec "$node" "$container" "$password" 'SHOW REPLICA STATUS\G' | awk -F': ' '/Source_User:/{print $2; exit}' | tr -d '\r'
}

mysql_replication_password() {
  local node="$1"
  local container="$2"
  local password="$3"

  mysql_query "$node" "$container" "$password" "SELECT User_password FROM mysql.slave_master_info LIMIT 1;" | tr -d '\r'
}

find_current_writer() {
  local node
  local container
  local password
  local role

  for node in "${NODES[@]}"; do
    container="$(mysql_container_for_node "$node")"
    [[ -z "$container" ]] && continue
    password="$(mysql_root_password_for_node "$node" "$container")"
    role="$(mysql_role "$node" "$container" "$password")"
    if [[ "$role" == "writer" ]]; then
      printf '%s\n' "$node"
      return 0
    fi
  done

  return 1
}

ensure_node_exists() {
  local node="$1"
  local known

  for known in "${NODES[@]}"; do
    if [[ "$known" == "$node" ]]; then
      return 0
    fi
  done

  echo "Unknown node: $node" >&2
  exit 1
}

set_read_only_state() {
  local node="$1"
  local container="$2"
  local password="$3"
  local mode="$4"
  local query

  if [[ "$mode" == "on" ]]; then
    query="SET SESSION sql_log_bin=0; SET GLOBAL read_only = ON; SET GLOBAL super_read_only = ON; SET PERSIST_ONLY read_only = ON; SET PERSIST_ONLY super_read_only = ON;"
  else
    query="SET SESSION sql_log_bin=0; SET GLOBAL super_read_only = OFF; SET GLOBAL read_only = OFF; SET PERSIST_ONLY super_read_only = OFF; SET PERSIST_ONLY read_only = OFF;"
  fi

  mysql_exec "$node" "$container" "$password" "$query"
}

wait_for_replica_health() {
  local node="$1"
  local container="$2"
  local password="$3"
  local attempts=20
  local i
  local io_running
  local sql_running
  local lag

  for ((i = 1; i <= attempts; i++)); do
    io_running="$(mysql_exec "$node" "$container" "$password" 'SHOW REPLICA STATUS\G' | awk -F': ' '/Replica_IO_Running:/{print $2; exit}' | tr -d '\r')"
    sql_running="$(mysql_exec "$node" "$container" "$password" 'SHOW REPLICA STATUS\G' | awk -F': ' '/Replica_SQL_Running:/{print $2; exit}' | tr -d '\r')"
    lag="$(mysql_replica_lag "$node" "$container" "$password")"
    if [[ "$io_running" == "Yes" && "$sql_running" == "Yes" && ( "$lag" == "0" || "$lag" == "NULL" ) ]]; then
      return 0
    fi
    sleep 2
  done

  echo "Replica on ${node} did not become healthy in time." >&2
  return 1
}

promote_node() {
  local target_node="$1"
  local current_writer
  local target_container
  local target_password
  local target_role
  local target_lag
  local target_ip
  local repl_user
  local repl_password
  local old_writer_container
  local old_writer_password

  ensure_node_exists "$target_node"
  current_writer="$(find_current_writer || true)"
  if [[ -z "$current_writer" ]]; then
    echo "Could not determine the current MySQL writer." >&2
    exit 1
  fi

  if [[ "$current_writer" == "$target_node" ]]; then
    echo "${target_node} is already the MySQL writer."
    return 0
  fi

  target_container="$(mysql_container_for_node "$target_node")"
  old_writer_container="$(mysql_container_for_node "$current_writer")"
  if [[ -z "$target_container" || -z "$old_writer_container" ]]; then
    echo "MySQL container missing on one of the private-cloud nodes." >&2
    exit 1
  fi

  target_password="$(mysql_root_password_for_node "$target_node" "$target_container")"
  old_writer_password="$(mysql_root_password_for_node "$current_writer" "$old_writer_container")"
  target_role="$(mysql_role "$target_node" "$target_container" "$target_password")"
  target_lag="$(mysql_replica_lag "$target_node" "$target_container" "$target_password")"

  if [[ "$target_role" != "replica" ]]; then
    echo "Refusing promotion: ${target_node} is not a healthy read-only replica (role=${target_role})." >&2
    exit 1
  fi

  if [[ "$target_lag" != "0" ]]; then
    echo "Refusing promotion: ${target_node} replica lag is ${target_lag}, not 0." >&2
    exit 1
  fi

  repl_user="$(mysql_replication_user "$target_node" "$target_container" "$target_password")"
  repl_password="$(mysql_replication_password "$target_node" "$target_container" "$target_password")"
  target_ip="$(node_ip "$target_node")"

  if [[ -z "$repl_user" || -z "$repl_password" || -z "$target_ip" ]]; then
    echo "Could not resolve replication parameters for ${target_node}." >&2
    exit 1
  fi

  echo "Promoting ${target_node} and demoting ${current_writer}."

  set_read_only_state "$current_writer" "$old_writer_container" "$old_writer_password" on

  mysql_exec "$target_node" "$target_container" "$target_password" "STOP REPLICA; RESET REPLICA ALL;"
  set_read_only_state "$target_node" "$target_container" "$target_password" off

  mysql_exec "$current_writer" "$old_writer_container" "$old_writer_password" "STOP REPLICA; RESET REPLICA ALL;"
  mysql_exec "$current_writer" "$old_writer_container" "$old_writer_password" "CHANGE REPLICATION SOURCE TO SOURCE_HOST='${target_ip}', SOURCE_USER='${repl_user}', SOURCE_PASSWORD='${repl_password}', SOURCE_PORT=3306, SOURCE_AUTO_POSITION=1, GET_SOURCE_PUBLIC_KEY=1; START REPLICA;"

  wait_for_replica_health "$current_writer" "$old_writer_container" "$old_writer_password"
  bash "$ROOT_DIR/scripts/private-cloud-audit.sh" verify
  bash "$ROOT_DIR/scripts/private-cloud-audit.sh" audit
}

case "$MODE" in
  status)
    bash "$ROOT_DIR/scripts/private-cloud-audit.sh" audit
    ;;
  promote)
    if [[ -z "$TARGET_NODE" ]]; then
      echo "promote requires a target node name." >&2
      exit 1
    fi
    promote_node "$TARGET_NODE"
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
