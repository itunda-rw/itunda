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

collect_outbox_metrics() {
  local container
  local root_password
  local table_exists
  local result
  local pending_events=0
  local oldest_pending_age_seconds=0

  container="$(docker_container_name 'mysql-')"
  if [[ -z "$container" ]]; then
    metric "itunda_outbox_metrics_available" 0 "$(node_labels)"
    metric "itunda_outbox_pending_events" 0 "$(node_labels)"
    metric "itunda_outbox_oldest_pending_age_seconds" 0 "$(node_labels)"
    return 0
  fi

  root_password="$(docker_env "$container" MYSQL_ROOT_PASSWORD)"
  table_exists="$(mysql_query "$container" "$root_password" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'itunda' AND table_name = 'outbox_events';" || true)"
  if [[ "$table_exists" != "1" ]]; then
    metric "itunda_outbox_metrics_available" 0 "$(node_labels)"
    metric "itunda_outbox_pending_events" 0 "$(node_labels)"
    metric "itunda_outbox_oldest_pending_age_seconds" 0 "$(node_labels)"
    return 0
  fi

  result="$(mysql_query "$container" "$root_password" "SELECT COUNT(*), COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), UTC_TIMESTAMP()), 0) FROM itunda.outbox_events WHERE processed_at IS NULL;" || true)"
  IFS=$'\t' read -r pending_events oldest_pending_age_seconds <<<"$result"
  [[ "$pending_events" =~ ^[0-9]+$ ]] || pending_events=0
  [[ "$oldest_pending_age_seconds" =~ ^[0-9]+$ ]] || oldest_pending_age_seconds=0

  metric "itunda_outbox_metrics_available" 1 "$(node_labels)"
  metric "itunda_outbox_pending_events" "$pending_events" "$(node_labels)"
  metric "itunda_outbox_oldest_pending_age_seconds" "$oldest_pending_age_seconds" "$(node_labels)"
}

collect_webhook_delivery_metrics() {
  local container
  local root_password
  local table_exists
  local result
  local pending_deliveries=0
  local exhausted_deliveries=0
  local overdue_deliveries=0
  local oldest_pending_age_seconds=0

  container="$(docker_container_name 'mysql-')"
  if [[ -z "$container" ]]; then
    metric "itunda_webhook_delivery_metrics_available" 0 "$(node_labels)"
    metric "itunda_webhook_deliveries_pending" 0 "$(node_labels)"
    metric "itunda_webhook_deliveries_exhausted" 0 "$(node_labels)"
    metric "itunda_webhook_deliveries_overdue" 0 "$(node_labels)"
    metric "itunda_webhook_deliveries_oldest_pending_age_seconds" 0 "$(node_labels)"
    return 0
  fi

  root_password="$(docker_env "$container" MYSQL_ROOT_PASSWORD)"
  table_exists="$(mysql_query "$container" "$root_password" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'itunda' AND table_name = 'webhook_deliveries';" || true)"
  if [[ "$table_exists" != "1" ]]; then
    metric "itunda_webhook_delivery_metrics_available" 0 "$(node_labels)"
    metric "itunda_webhook_deliveries_pending" 0 "$(node_labels)"
    metric "itunda_webhook_deliveries_exhausted" 0 "$(node_labels)"
    metric "itunda_webhook_deliveries_overdue" 0 "$(node_labels)"
    metric "itunda_webhook_deliveries_oldest_pending_age_seconds" 0 "$(node_labels)"
    return 0
  fi

  result="$(mysql_query "$container" "$root_password" "SELECT SUM(status = 'PENDING'), SUM(status = 'EXHAUSTED'), SUM(status = 'PENDING' AND next_attempt_at < UTC_TIMESTAMP()), COALESCE(TIMESTAMPDIFF(SECOND, MIN(CASE WHEN status = 'PENDING' THEN created_at END), UTC_TIMESTAMP()), 0) FROM itunda.webhook_deliveries;" || true)"
  IFS=$'\t' read -r pending_deliveries exhausted_deliveries overdue_deliveries oldest_pending_age_seconds <<<"$result"
  [[ "$pending_deliveries" =~ ^[0-9]+$ ]] || pending_deliveries=0
  [[ "$exhausted_deliveries" =~ ^[0-9]+$ ]] || exhausted_deliveries=0
  [[ "$overdue_deliveries" =~ ^[0-9]+$ ]] || overdue_deliveries=0
  [[ "$oldest_pending_age_seconds" =~ ^[0-9]+$ ]] || oldest_pending_age_seconds=0

  metric "itunda_webhook_delivery_metrics_available" 1 "$(node_labels)"
  metric "itunda_webhook_deliveries_pending" "$pending_deliveries" "$(node_labels)"
  metric "itunda_webhook_deliveries_exhausted" "$exhausted_deliveries" "$(node_labels)"
  metric "itunda_webhook_deliveries_overdue" "$overdue_deliveries" "$(node_labels)"
  metric "itunda_webhook_deliveries_oldest_pending_age_seconds" "$oldest_pending_age_seconds" "$(node_labels)"
}

collect_fraud_review_metrics() {
  local container
  local root_password
  local table_exists
  local result
  local pending_flags=0
  local oldest_pending_age_seconds=0
  local high_value_flags=0
  local velocity_flags=0
  local new_recipient_flags=0
  local high_value_confirmed=0
  local velocity_confirmed=0
  local new_recipient_confirmed=0
  local high_value_cleared=0
  local velocity_cleared=0
  local new_recipient_cleared=0
  local warn_unreviewed="${ITUNDA_PRIVATE_CLOUD_FRAUD_REVIEW_WARN_UNREVIEWED:-50}"
  local warn_oldest_age_seconds="${ITUNDA_PRIVATE_CLOUD_FRAUD_REVIEW_WARN_OLDEST_AGE_SECONDS:-86400}"
  local sla_breached=0

  [[ "$warn_unreviewed" =~ ^[0-9]+$ ]] || warn_unreviewed=50
  [[ "$warn_oldest_age_seconds" =~ ^[0-9]+$ ]] || warn_oldest_age_seconds=86400

  container="$(docker_container_name 'mysql-')"
  if [[ -z "$container" ]]; then
    metric "itunda_fraud_review_metrics_available" 0 "$(node_labels)"
    metric "itunda_fraud_flags_unreviewed" 0 "$(node_labels)"
    metric "itunda_fraud_flags_oldest_unreviewed_age_seconds" 0 "$(node_labels)"
    metric "itunda_fraud_review_sla_breached" 0 "$(node_labels)"
    metric "itunda_fraud_flags_unreviewed_by_rule" 0 "$(node_labels),rule=\"HIGH_VALUE\""
    metric "itunda_fraud_flags_unreviewed_by_rule" 0 "$(node_labels),rule=\"VELOCITY\""
    metric "itunda_fraud_flags_unreviewed_by_rule" 0 "$(node_labels),rule=\"NEW_RECIPIENT\""
    emit_fraud_review_decision_metrics 0 0 0 0 0 0
    return 0
  fi

  root_password="$(docker_env "$container" MYSQL_ROOT_PASSWORD)"
  table_exists="$(mysql_query "$container" "$root_password" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'itunda' AND table_name = 'fraud_flags';" || true)"
  if [[ "$table_exists" != "1" ]]; then
    metric "itunda_fraud_review_metrics_available" 0 "$(node_labels)"
    metric "itunda_fraud_flags_unreviewed" 0 "$(node_labels)"
    metric "itunda_fraud_flags_oldest_unreviewed_age_seconds" 0 "$(node_labels)"
    metric "itunda_fraud_review_sla_breached" 0 "$(node_labels)"
    metric "itunda_fraud_flags_unreviewed_by_rule" 0 "$(node_labels),rule=\"HIGH_VALUE\""
    metric "itunda_fraud_flags_unreviewed_by_rule" 0 "$(node_labels),rule=\"VELOCITY\""
    metric "itunda_fraud_flags_unreviewed_by_rule" 0 "$(node_labels),rule=\"NEW_RECIPIENT\""
    emit_fraud_review_decision_metrics 0 0 0 0 0 0
    return 0
  fi

  result="$(mysql_query "$container" "$root_password" "SELECT SUM(reviewed = FALSE), COALESCE(TIMESTAMPDIFF(SECOND, MIN(CASE WHEN reviewed = FALSE THEN created_at END), UTC_TIMESTAMP()), 0), SUM(reviewed = FALSE AND rule = 'HIGH_VALUE'), SUM(reviewed = FALSE AND rule = 'VELOCITY'), SUM(reviewed = FALSE AND rule = 'NEW_RECIPIENT'), SUM(reviewed = TRUE AND decision = 'CONFIRMED' AND rule = 'HIGH_VALUE'), SUM(reviewed = TRUE AND decision = 'CONFIRMED' AND rule = 'VELOCITY'), SUM(reviewed = TRUE AND decision = 'CONFIRMED' AND rule = 'NEW_RECIPIENT'), SUM(reviewed = TRUE AND decision = 'CLEARED' AND rule = 'HIGH_VALUE'), SUM(reviewed = TRUE AND decision = 'CLEARED' AND rule = 'VELOCITY'), SUM(reviewed = TRUE AND decision = 'CLEARED' AND rule = 'NEW_RECIPIENT') FROM itunda.fraud_flags;" || true)"
  IFS=$'\t' read -r pending_flags oldest_pending_age_seconds high_value_flags velocity_flags new_recipient_flags high_value_confirmed velocity_confirmed new_recipient_confirmed high_value_cleared velocity_cleared new_recipient_cleared <<<"$result"
  [[ "$pending_flags" =~ ^[0-9]+$ ]] || pending_flags=0
  [[ "$oldest_pending_age_seconds" =~ ^[0-9]+$ ]] || oldest_pending_age_seconds=0
  [[ "$high_value_flags" =~ ^[0-9]+$ ]] || high_value_flags=0
  [[ "$velocity_flags" =~ ^[0-9]+$ ]] || velocity_flags=0
  [[ "$new_recipient_flags" =~ ^[0-9]+$ ]] || new_recipient_flags=0
  [[ "$high_value_confirmed" =~ ^[0-9]+$ ]] || high_value_confirmed=0
  [[ "$velocity_confirmed" =~ ^[0-9]+$ ]] || velocity_confirmed=0
  [[ "$new_recipient_confirmed" =~ ^[0-9]+$ ]] || new_recipient_confirmed=0
  [[ "$high_value_cleared" =~ ^[0-9]+$ ]] || high_value_cleared=0
  [[ "$velocity_cleared" =~ ^[0-9]+$ ]] || velocity_cleared=0
  [[ "$new_recipient_cleared" =~ ^[0-9]+$ ]] || new_recipient_cleared=0
  if (( pending_flags >= warn_unreviewed || oldest_pending_age_seconds >= warn_oldest_age_seconds )); then
    sla_breached=1
  fi

  metric "itunda_fraud_review_metrics_available" 1 "$(node_labels)"
  metric "itunda_fraud_flags_unreviewed" "$pending_flags" "$(node_labels)"
  metric "itunda_fraud_flags_oldest_unreviewed_age_seconds" "$oldest_pending_age_seconds" "$(node_labels)"
  metric "itunda_fraud_review_sla_breached" "$sla_breached" "$(node_labels)"
  metric "itunda_fraud_flags_unreviewed_by_rule" "$high_value_flags" "$(node_labels),rule=\"HIGH_VALUE\""
  metric "itunda_fraud_flags_unreviewed_by_rule" "$velocity_flags" "$(node_labels),rule=\"VELOCITY\""
  metric "itunda_fraud_flags_unreviewed_by_rule" "$new_recipient_flags" "$(node_labels),rule=\"NEW_RECIPIENT\""
  emit_fraud_review_decision_metrics "$high_value_confirmed" "$velocity_confirmed" "$new_recipient_confirmed" "$high_value_cleared" "$velocity_cleared" "$new_recipient_cleared"
}

emit_fraud_review_decision_metrics() {
  local high_value_confirmed="$1"
  local velocity_confirmed="$2"
  local new_recipient_confirmed="$3"
  local high_value_cleared="$4"
  local velocity_cleared="$5"
  local new_recipient_cleared="$6"

  metric "itunda_fraud_flags_reviewed_by_rule_and_decision" "$high_value_confirmed" "$(node_labels),rule=\"HIGH_VALUE\",decision=\"CONFIRMED\""
  metric "itunda_fraud_flags_reviewed_by_rule_and_decision" "$velocity_confirmed" "$(node_labels),rule=\"VELOCITY\",decision=\"CONFIRMED\""
  metric "itunda_fraud_flags_reviewed_by_rule_and_decision" "$new_recipient_confirmed" "$(node_labels),rule=\"NEW_RECIPIENT\",decision=\"CONFIRMED\""
  metric "itunda_fraud_flags_reviewed_by_rule_and_decision" "$high_value_cleared" "$(node_labels),rule=\"HIGH_VALUE\",decision=\"CLEARED\""
  metric "itunda_fraud_flags_reviewed_by_rule_and_decision" "$velocity_cleared" "$(node_labels),rule=\"VELOCITY\",decision=\"CLEARED\""
  metric "itunda_fraud_flags_reviewed_by_rule_and_decision" "$new_recipient_cleared" "$(node_labels),rule=\"NEW_RECIPIENT\",decision=\"CLEARED\""
}

collect_mysql_backup_metrics() {
  local backup_dir="${ITUNDA_PRIVATE_CLOUD_MYSQL_BACKUP_DIR:-/home/ubuntu/itunda-backups/mysql}"
  local latest_epoch
  local latest_file
  local latest_bytes=0
  local now
  local age_seconds=0

  latest_epoch="$(find "$backup_dir" -maxdepth 1 -type f -name 'itunda-mysql-*.sql.gz' -printf '%T@\n' 2>/dev/null | sort -nr | head -n 1 | cut -d. -f1 || true)"
  if [[ ! "$latest_epoch" =~ ^[0-9]+$ ]]; then
    metric "itunda_mysql_backup_available" 0 "$(node_labels)"
    metric "itunda_mysql_backup_age_seconds" 0 "$(node_labels)"
    metric "itunda_mysql_backup_latest_bytes" 0 "$(node_labels)"
    return 0
  fi

  latest_file="$(find "$backup_dir" -maxdepth 1 -type f -name 'itunda-mysql-*.sql.gz' -printf '%T@ %p\n' 2>/dev/null | sort -nr | head -n 1 | cut -d' ' -f2-)"
  latest_bytes="$(stat -c '%s' "$latest_file" 2>/dev/null || printf '0')"
  [[ "$latest_bytes" =~ ^[0-9]+$ ]] || latest_bytes=0
  now="$(date +%s)"
  age_seconds=$((now - latest_epoch))
  (( age_seconds < 0 )) && age_seconds=0
  metric "itunda_mysql_backup_available" 1 "$(node_labels)"
  metric "itunda_mysql_backup_age_seconds" "$age_seconds" "$(node_labels)"
  metric "itunda_mysql_backup_latest_bytes" "$latest_bytes" "$(node_labels)"
}

collect_kubernetes_workload_metrics() {
  local app
  local output
  local ready_nodes
  local ready_pods=0
  local restarts=0
  local metric_labels

  if ! sudo test -r /etc/kubernetes/admin.conf || ! command -v kubectl >/dev/null 2>&1; then
    metric "itunda_kubernetes_workload_metrics_available" 0 "$(node_labels)"
    return 0
  fi

  metric "itunda_kubernetes_workload_metrics_available" 1 "$(node_labels)"
  ready_nodes="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl get nodes --no-headers -o custom-columns='READY:.status.conditions[?(@.type=="Ready")].status' 2>/dev/null | awk '$1 == "True" { count++ } END { print count + 0 }')"
  metric "itunda_kubernetes_ready_nodes" "$ready_nodes" "$(node_labels)"
  for app in api-gateway backend ledger-service payment-service; do
    output="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl get pods -n itunda -l "app=${app}" --no-headers -o custom-columns='READY:.status.containerStatuses[0].ready,RESTARTS:.status.containerStatuses[0].restartCount' 2>/dev/null || true)"
    ready_pods="$(printf '%s\n' "$output" | awk '$1 == "true" { count++ } END { print count + 0 }')"
    restarts="$(printf '%s\n' "$output" | awk '$2 ~ /^[0-9]+$/ { sum += $2 } END { print sum + 0 }')"
    metric_labels="$(node_labels),namespace=\"itunda\",app=\"${app}\""
    metric "itunda_kubernetes_app_ready_pods" "$ready_pods" "$metric_labels"
    metric "itunda_kubernetes_app_container_restarts" "$restarts" "$metric_labels"
  done
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

collect_kafka_consumer_group_metrics() {
  local container
  local groups_output
  local group
  local description
  local lag
  local group_count=0

  container="$(docker_container_name 'kafka-')"
  if [[ -z "$container" ]]; then
    metric "itunda_kafka_consumer_group_metrics_available" 0 "$(node_labels)"
    metric "itunda_kafka_consumer_groups_count" 0 "$(node_labels)"
    return 0
  fi

  groups_output="$(sudo docker exec "$container" /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list 2>/dev/null || true)"
  metric "itunda_kafka_consumer_group_metrics_available" 1 "$(node_labels)"
  while IFS= read -r group; do
    [[ -z "$group" ]] && continue
    group_count=$((group_count + 1))
    description="$(sudo docker exec "$container" /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group "$group" 2>/dev/null || true)"
    lag="$(printf '%s\n' "$description" | awk 'NR > 1 && $6 ~ /^[0-9]+$/ { sum += $6 } END { print sum + 0 }')"
    metric "itunda_kafka_consumer_group_lag" "$lag" "$(node_labels),group=\"${group}\""
  done <<<"$groups_output"
  metric "itunda_kafka_consumer_groups_count" "$group_count" "$(node_labels)"
}

main() {
  trap cleanup EXIT

  install -d -m 0755 "$TEXTFILE_DIR"
  TMP_FILE="$(mktemp "${TEXTFILE_DIR}/itunda-private-cloud.XXXXXX.prom")"
  metric "itunda_private_cloud_node_audit_timestamp_seconds" "$(date +%s)" "$(node_labels)"
  collect_mysql_metrics
  collect_outbox_metrics
  collect_webhook_delivery_metrics
  collect_fraud_review_metrics
  collect_mysql_backup_metrics
  collect_kubernetes_workload_metrics
  collect_redis_metrics
  collect_kafka_metrics
  collect_kafka_consumer_group_metrics
  chmod 0644 "$TMP_FILE"
  mv "$TMP_FILE" "$OUTPUT_FILE"
}

main "$@"
