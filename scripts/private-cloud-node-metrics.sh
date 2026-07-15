#!/usr/bin/env bash

set -euo pipefail

TEXTFILE_DIR="${ITUNDA_PRIVATE_CLOUD_NODE_EXPORTER_TEXTFILE_DIR:-/var/lib/itunda-node-exporter/textfile_collector}"
OUTPUT_FILE="${TEXTFILE_DIR}/itunda_private_cloud.prom"
TMP_FILE=""
NODE_NAME="$(hostname -s)"

cleanup() {
  [[ -n "$TMP_FILE" ]] && rm -f "$TMP_FILE"
}

metric() {
  local name="$1"
  local value="$2"
  local labels="${3:-}"

  if [[ -n "$labels" ]]; then
    printf '%s{%s} %s\n' "$name" "$labels" "$value" >>"$TMP_FILE"
  else
    printf '%s %s\n' "$name" "$value" >>"$TMP_FILE"
  fi
}

node_labels() {
  printf 'node="%s"' "$NODE_NAME"
}

emit_role_info() {
  local metric_name="$1"
  local current_role="$2"
  shift 2
  local role
  local value

  for role in "$@"; do
    value=0
    [[ "$role" == "$current_role" ]] && value=1
    metric "$metric_name" "$value" "$(node_labels),role=\"${role}\""
  done
}

docker_container_name() {
  local prefix="$1"

  sudo docker ps --format '{{.Names}}' | awk "/^${prefix}/{print; exit}" 2>/dev/null || true
}

docker_env() {
  local container="$1"
  local key="$2"

  sudo docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$container" \
    | sed -n "s/^${key}=//p" \
    | head -n 1
}

mysql_query() {
  local container="$1"
  local password="$2"
  local query="$3"

  sudo docker exec -e MYSQL_PWD="$password" "$container" mysql -uroot -Nse "$query" 2>/dev/null
}

collect_mysql_metrics() {
  local container
  local root_password
  local read_only
  local super_read_only
  local server_id
  local role="absent"
  local lag=0
  local replica_status=""
  local source_host=""
  local io_running="No"
  local sql_running="No"

  container="$(docker_container_name 'mysql-')"
  if [[ -z "$container" ]]; then
    metric "itunda_mysql_up" 0 "$(node_labels)"
    metric "itunda_mysql_read_only" 0 "$(node_labels)"
    metric "itunda_mysql_super_read_only" 0 "$(node_labels)"
    metric "itunda_mysql_replica_io_running" 0 "$(node_labels)"
    metric "itunda_mysql_replica_sql_running" 0 "$(node_labels)"
    metric "itunda_mysql_replica_lag_seconds" 0 "$(node_labels)"
    emit_role_info "itunda_mysql_role_info" "$role" writer replica replica_writable replica_broken replica_broken_writable read_only_standalone absent
    return 0
  fi

  root_password="$(docker_env "$container" MYSQL_ROOT_PASSWORD)"
  if ! mysql_query "$container" "$root_password" 'SELECT 1;' >/dev/null; then
    metric "itunda_mysql_up" 0 "$(node_labels)"
    emit_role_info "itunda_mysql_role_info" "absent" writer replica replica_writable replica_broken replica_broken_writable read_only_standalone absent
    return 0
  fi

  metric "itunda_mysql_up" 1 "$(node_labels)"

  read_only="$(mysql_query "$container" "$root_password" 'SELECT @@read_only;' | tr -d '\r' || printf '0')"
  super_read_only="$(mysql_query "$container" "$root_password" 'SELECT @@super_read_only;' | tr -d '\r' || printf '0')"
  server_id="$(mysql_query "$container" "$root_password" 'SELECT @@server_id;' | tr -d '\r' || printf '0')"
  replica_status="$(sudo docker exec -e MYSQL_PWD="$root_password" "$container" mysql -uroot -e 'SHOW REPLICA STATUS\G' 2>/dev/null || true)"
  source_host="$(printf '%s\n' "$replica_status" | awk -F': ' '/Source_Host:/{print $2; exit}' | tr -d '\r')"
  lag="$(printf '%s\n' "$replica_status" | awk -F': ' '/Seconds_Behind_Source:/{print $2; exit}' | tr -d '\r')"
  io_running="$(printf '%s\n' "$replica_status" | awk -F': ' '/Replica_IO_Running:/{print $2; exit}' | tr -d '\r')"
  sql_running="$(printf '%s\n' "$replica_status" | awk -F': ' '/Replica_SQL_Running:/{print $2; exit}' | tr -d '\r')"

  [[ -z "$lag" || "$lag" == "NULL" ]] && lag=0
  [[ "$io_running" == "Yes" ]] && io_running=1 || io_running=0
  [[ "$sql_running" == "Yes" ]] && sql_running=1 || sql_running=0

  if [[ -n "$source_host" ]]; then
    if [[ "$io_running" == "1" && "$sql_running" == "1" ]]; then
      if [[ "$read_only" == "1" && "$super_read_only" == "1" ]]; then
        role="replica"
      else
        role="replica_writable"
      fi
    elif [[ "$read_only" == "1" && "$super_read_only" == "1" ]]; then
      role="replica_broken"
    else
      role="replica_broken_writable"
    fi
    metric "itunda_mysql_source_host_info" 1 "$(node_labels),source_host=\"${source_host}\""
  elif [[ "$read_only" == "0" ]]; then
    role="writer"
  else
    role="read_only_standalone"
  fi

  metric "itunda_mysql_read_only" "${read_only:-0}" "$(node_labels)"
  metric "itunda_mysql_super_read_only" "${super_read_only:-0}" "$(node_labels)"
  metric "itunda_mysql_server_id" "${server_id:-0}" "$(node_labels)"
  metric "itunda_mysql_replica_io_running" "$io_running" "$(node_labels)"
  metric "itunda_mysql_replica_sql_running" "$sql_running" "$(node_labels)"
  metric "itunda_mysql_replica_lag_seconds" "${lag:-0}" "$(node_labels)"
  emit_role_info "itunda_mysql_role_info" "$role" writer replica replica_writable replica_broken replica_broken_writable read_only_standalone absent
}

collect_redis_metrics() {
  local container
  local info
  local role="absent"
  local connected_slaves=0

  container="$(docker_container_name 'redis-')"
  if [[ -z "$container" ]]; then
    metric "itunda_redis_up" 0 "$(node_labels)"
    metric "itunda_redis_connected_slaves" 0 "$(node_labels)"
    emit_role_info "itunda_redis_role_info" "$role" master slave absent
    return 0
  fi

  info="$(sudo docker exec "$container" redis-cli INFO replication 2>/dev/null || true)"
  if [[ -z "$info" ]]; then
    metric "itunda_redis_up" 0 "$(node_labels)"
    metric "itunda_redis_connected_slaves" 0 "$(node_labels)"
    emit_role_info "itunda_redis_role_info" "$role" master slave absent
    return 0
  fi

  role="$(printf '%s\n' "$info" | sed -n 's/^role://p' | head -n 1 | tr -d '\r')"
  connected_slaves="$(printf '%s\n' "$info" | sed -n 's/^connected_slaves://p' | head -n 1 | tr -d '\r')"
  [[ -z "$connected_slaves" ]] && connected_slaves=0

  metric "itunda_redis_up" 1 "$(node_labels)"
  metric "itunda_redis_connected_slaves" "$connected_slaves" "$(node_labels)"
  emit_role_info "itunda_redis_role_info" "${role:-absent}" master slave absent
}

collect_kafka_metrics() {
  local container
  local cluster_id=""
  local node_id=0
  local quorum_status=""
  local leader_id=0
  local leader_epoch=0
  local max_follower_lag=0
  local voters=""
  local voter_count=0

  container="$(docker_container_name 'kafka-')"
  if [[ -z "$container" ]]; then
    metric "itunda_kafka_up" 0 "$(node_labels)"
    metric "itunda_kafka_quorum_voters_count" 0 "$(node_labels)"
    metric "itunda_kafka_leader_id" 0 "$(node_labels)"
    metric "itunda_kafka_leader_epoch" 0 "$(node_labels)"
    metric "itunda_kafka_max_follower_lag" 0 "$(node_labels)"
    return 0
  fi

  cluster_id="$(docker_env "$container" CLUSTER_ID)"
  node_id="$(docker_env "$container" KAFKA_NODE_ID)"
  quorum_status="$(sudo docker exec "$container" /opt/kafka/bin/kafka-metadata-quorum.sh --bootstrap-server localhost:9092 describe --status 2>/dev/null || true)"

  if [[ -z "$quorum_status" ]]; then
    metric "itunda_kafka_up" 0 "$(node_labels)"
    metric "itunda_kafka_quorum_voters_count" 0 "$(node_labels)"
    metric "itunda_kafka_leader_id" 0 "$(node_labels)"
    metric "itunda_kafka_leader_epoch" 0 "$(node_labels)"
    metric "itunda_kafka_max_follower_lag" 0 "$(node_labels)"
    return 0
  fi

  leader_id="$(printf '%s\n' "$quorum_status" | awk -F':' '/^LeaderId:/{gsub(/ /, "", $2); print $2; exit}' | tr -d '\r')"
  leader_epoch="$(printf '%s\n' "$quorum_status" | awk -F':' '/^LeaderEpoch:/{gsub(/ /, "", $2); print $2; exit}' | tr -d '\r')"
  max_follower_lag="$(printf '%s\n' "$quorum_status" | awk -F':' '/^MaxFollowerLag:/{gsub(/ /, "", $2); print $2; exit}' | tr -d '\r')"
  voters="$(printf '%s\n' "$quorum_status" | awk -F':' '/^CurrentVoters:/{print $2; exit}' | tr -d '\r')"
  voter_count="$(printf '%s' "$voters" | tr -d '[][:space:]' | awk -F',' '{ if ($1 == "") print 0; else print NF }')"

  metric "itunda_kafka_up" 1 "$(node_labels)"
  metric "itunda_kafka_quorum_voters_count" "${voter_count:-0}" "$(node_labels)"
  metric "itunda_kafka_leader_id" "${leader_id:-0}" "$(node_labels)"
  metric "itunda_kafka_leader_epoch" "${leader_epoch:-0}" "$(node_labels)"
  metric "itunda_kafka_max_follower_lag" "${max_follower_lag:-0}" "$(node_labels)"
  metric "itunda_kafka_cluster_info" 1 "$(node_labels),cluster_id=\"${cluster_id:-unknown}\",kafka_node_id=\"${node_id:-0}\""
}

main() {
  trap cleanup EXIT

  install -d -m 0755 "$TEXTFILE_DIR"
  TMP_FILE="$(mktemp "${TEXTFILE_DIR}/itunda-private-cloud.XXXXXX.prom")"
  metric "itunda_private_cloud_node_audit_timestamp_seconds" "$(date +%s)" "$(node_labels)"
  collect_mysql_metrics
  collect_redis_metrics
  collect_kafka_metrics
  chmod 0644 "$TMP_FILE"
  mv "$TMP_FILE" "$OUTPUT_FILE"
}

main "$@"
