#!/usr/bin/env bash

set -euo pipefail

MODE="${1:-help}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/private-cloud-inventory.sh"
NODES=($(inventory_nodes))

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

run_vm() {
  local node="$1"
  shift
  local attempts="${ITUNDA_PRIVATE_CLOUD_VM_RETRY_ATTEMPTS:-4}"
  local delay_seconds="${ITUNDA_PRIVATE_CLOUD_VM_RETRY_DELAY_SECONDS:-3}"
  local attempt
  local status
  local stdout_file
  local stderr_file

  stdout_file="$(mktemp "${TMPDIR:-/tmp}/itunda-audit-run-vm-stdout.XXXXXX")"
  stderr_file="$(mktemp "${TMPDIR:-/tmp}/itunda-audit-run-vm-stderr.XXXXXX")"

  for ((attempt = 1; attempt <= attempts; attempt++)); do
    : >"$stdout_file"
    : >"$stderr_file"
    if multipass exec "$node" -- bash -lc "$*" </dev/null >"$stdout_file" 2>"$stderr_file"; then
      status=0
      cat "$stdout_file"
      cat "$stderr_file" >&2
      rm -f "$stdout_file" "$stderr_file"
      return 0
    else
      status=$?
    fi

    if ! grep -q "ssh connection failed" "$stderr_file" || [[ "$attempt" -ge "$attempts" ]]; then
      cat "$stdout_file"
      cat "$stderr_file" >&2
      rm -f "$stdout_file" "$stderr_file"
      return "$status"
    fi

    sleep "$delay_seconds"
  done

  rm -f "$stdout_file" "$stderr_file"
  return 1
}

kubeadm_cluster_active() {
  run_vm "${NODES[0]}" "test -f /etc/kubernetes/admin.conf && systemctl is-active kubelet >/dev/null 2>&1"
}

cluster_kubectl() {
  run_vm "${NODES[0]}" "sudo kubectl --kubeconfig /etc/kubernetes/admin.conf $*"
}

container_name() {
  local node="$1"
  local prefix="$2"

  run_vm "$node" "sudo docker ps --format '{{.Names}}' | awk '/^${prefix}/{print; exit}'" 2>/dev/null || true
}

container_env() {
  local node="$1"
  local container="$2"
  local key="$3"

  run_vm "$node" "sudo docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' ${container} | sed -n 's/^${key}=//p' | head -n 1" 2>/dev/null || true
}

shell_quote() {
  printf '%q' "$1"
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

current_time() {
  date '+%Y-%m-%d %H:%M:%S %Z'
}

mysql_backup_summary() {
  run_vm "${NODES[0]}" "
    timer=\$(sudo systemctl is-active itunda-mysql-backup.timer 2>/dev/null || true)
    latest=\$(find /home/ubuntu/itunda-backups/mysql -maxdepth 1 -type f -name 'itunda-mysql-*.sql.gz' -printf '%T@\\n' 2>/dev/null | sort -nr | head -n 1 | cut -d. -f1)
    if [[ \"\$latest\" =~ ^[0-9]+$ ]]; then
      echo \"timer=\${timer:-inactive} age_seconds=\$((\$(date +%s) - latest))\"
    else
      echo \"timer=\${timer:-inactive} age_seconds=absent\"
    fi
  " 2>/dev/null || echo "timer=unknown age_seconds=unknown"
}

webhook_delivery_summary() {
  local writer_index
  local container
  local root_password
  local table_exists
  local result
  local pending=0
  local exhausted=0
  local overdue=0

  if [[ "${#MYSQL_WRITER_INDEXES[@]}" -ne 1 ]]; then
    echo "available=unknown pending=unknown exhausted=unknown overdue=unknown"
    return 0
  fi

  writer_index="${MYSQL_WRITER_INDEXES[0]}"
  container="${MYSQL_CONTAINERS[$writer_index]}"
  root_password="$(container_env "${NODE_NAMES[$writer_index]}" "$container" MYSQL_ROOT_PASSWORD)"
  table_exists="$(mysql_query "${NODE_NAMES[$writer_index]}" "$container" "$root_password" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'itunda' AND table_name = 'webhook_deliveries';" || true)"
  if [[ "$table_exists" != "1" ]]; then
    echo "available=0 pending=0 exhausted=0 overdue=0"
    return 0
  fi

  result="$(mysql_query "${NODE_NAMES[$writer_index]}" "$container" "$root_password" "SELECT SUM(status = 'PENDING'), SUM(status = 'EXHAUSTED'), SUM(status = 'PENDING' AND next_attempt_at < UTC_TIMESTAMP()) FROM itunda.webhook_deliveries;" || true)"
  IFS=$'\t' read -r pending exhausted overdue <<<"$result"
  [[ "$pending" =~ ^[0-9]+$ ]] || pending=0
  [[ "$exhausted" =~ ^[0-9]+$ ]] || exhausted=0
  [[ "$overdue" =~ ^[0-9]+$ ]] || overdue=0
  echo "available=1 pending=${pending} exhausted=${exhausted} overdue=${overdue}"
}

fraud_review_summary() {
  local writer_index
  local container
  local root_password
  local table_exists
  local result
  local unreviewed=0
  local oldest_age_seconds=0

  if [[ "${#MYSQL_WRITER_INDEXES[@]}" -ne 1 ]]; then
    echo "available=unknown unreviewed=unknown oldest_age_seconds=unknown"
    return 0
  fi

  writer_index="${MYSQL_WRITER_INDEXES[0]}"
  container="${MYSQL_CONTAINERS[$writer_index]}"
  root_password="$(container_env "${NODE_NAMES[$writer_index]}" "$container" MYSQL_ROOT_PASSWORD)"
  table_exists="$(mysql_query "${NODE_NAMES[$writer_index]}" "$container" "$root_password" "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'itunda' AND table_name = 'fraud_flags';" || true)"
  if [[ "$table_exists" != "1" ]]; then
    echo "available=0 unreviewed=0 oldest_age_seconds=0"
    return 0
  fi

  result="$(mysql_query "${NODE_NAMES[$writer_index]}" "$container" "$root_password" "SELECT COUNT(*), COALESCE(TIMESTAMPDIFF(SECOND, MIN(created_at), UTC_TIMESTAMP()), 0) FROM itunda.fraud_flags WHERE reviewed = FALSE;" || true)"
  IFS=$'\t' read -r unreviewed oldest_age_seconds <<<"$result"
  [[ "$unreviewed" =~ ^[0-9]+$ ]] || unreviewed=0
  [[ "$oldest_age_seconds" =~ ^[0-9]+$ ]] || oldest_age_seconds=0
  echo "available=1 unreviewed=${unreviewed} oldest_age_seconds=${oldest_age_seconds}"
}

print_fraud_review_backlog_warning() {
  local summary="$1"
  local available
  local unreviewed
  local oldest_age_seconds
  local warn_unreviewed="${ITUNDA_PRIVATE_CLOUD_FRAUD_REVIEW_WARN_UNREVIEWED:-50}"
  local warn_oldest_age_seconds="${ITUNDA_PRIVATE_CLOUD_FRAUD_REVIEW_WARN_OLDEST_AGE_SECONDS:-86400}"

  available="$(printf '%s\n' "$summary" | sed -n 's/.*available=\([^ ]*\).*/\1/p')"
  unreviewed="$(printf '%s\n' "$summary" | sed -n 's/.*unreviewed=\([^ ]*\).*/\1/p')"
  oldest_age_seconds="$(printf '%s\n' "$summary" | sed -n 's/.*oldest_age_seconds=\([^ ]*\).*/\1/p')"
  [[ "$warn_unreviewed" =~ ^[0-9]+$ ]] || warn_unreviewed=50
  [[ "$warn_oldest_age_seconds" =~ ^[0-9]+$ ]] || warn_oldest_age_seconds=86400

  if [[ "$available" == "1" && "$unreviewed" =~ ^[0-9]+$ && "$oldest_age_seconds" =~ ^[0-9]+$ ]] && \
    { (( unreviewed >= warn_unreviewed )) || (( oldest_age_seconds >= warn_oldest_age_seconds )); }; then
    echo "- Warning: fraud review backlog needs an authorized reviewer (unreviewed=${unreviewed}, oldest_age_seconds=${oldest_age_seconds}; warning thresholds=${warn_unreviewed} flags or ${warn_oldest_age_seconds}s)."
  fi
}

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-audit.sh <command>

Commands:
  audit   Inspect the Multipass private cloud and print the real topology
  verify  Exit non-zero if the live topology is unsafe for app deployments
  env     Emit dotenv-compatible values for the current writable node
  json    Emit machine-readable JSON for CMDB/inventory tooling
EOF
}

require_cmd multipass

declare -a NODE_NAMES NODE_IPS NODE_LISTENERS
declare -a MYSQL_CONTAINERS MYSQL_ROLES MYSQL_SERVER_IDS MYSQL_READ_ONLY MYSQL_SUPER_READ_ONLY
declare -a MYSQL_SOURCE_HOSTS MYSQL_REPLICA_LAGS MYSQL_APP_DBS MYSQL_APP_USERS MYSQL_APP_PASSWORDS
declare -a MYSQL_REPLICA_IO_RUNNING MYSQL_REPLICA_SQL_RUNNING
declare -a REDIS_CONTAINERS REDIS_ROLES
declare -a KAFKA_CONTAINERS KAFKA_CLUSTER_IDS KAFKA_NODE_IDS KAFKA_VOTERS KAFKA_ADVERTISED
declare -a KAFKA_MM2_WORKERS
declare -a MYSQL_WRITER_INDEXES MYSQL_REPLICA_INDEXES MYSQL_REPLICA_WRITABLE_INDEXES
declare -a MYSQL_REPLICA_BROKEN_INDEXES MYSQL_REPLICA_BROKEN_WRITABLE_INDEXES
declare -a REDIS_MASTER_INDEXES REDIS_REPLICA_INDEXES KAFKA_BROKER_INDEXES

load_node_state() {
  local index="$1"
  local node="$2"
  local mysql_container
  local redis_container
  local kafka_container
  local mysql_root_password
  local mysql_status
  local read_only
  local super_read_only
  local server_id
  local source_host
  local replica_lag
  local replica_count
  local replica_io_running
  local replica_sql_running
  local kafka_mm2_worker

  NODE_NAMES[$index]="$node"
  NODE_IPS[$index]="$(run_vm "$node" "hostname -I | awk '{print \$1}'" 2>/dev/null | tr -d '\r')"
  NODE_LISTENERS[$index]="$(run_vm "$node" "sudo ss -ltnH | awk '{print \$4}' | sed 's/.*://' | sort -n | uniq | paste -sd, -" 2>/dev/null | tr -d '\r')"

  mysql_container="$(container_name "$node" 'mysql-')"
  MYSQL_CONTAINERS[$index]="$mysql_container"
  if [[ -n "$mysql_container" ]]; then
    mysql_root_password="$(container_env "$node" "$mysql_container" MYSQL_ROOT_PASSWORD)"
    MYSQL_APP_DBS[$index]="$(container_env "$node" "$mysql_container" MYSQL_DATABASE)"
    MYSQL_APP_USERS[$index]="$(container_env "$node" "$mysql_container" MYSQL_USER)"
    MYSQL_APP_PASSWORDS[$index]="$(container_env "$node" "$mysql_container" MYSQL_PASSWORD)"
    read_only="$(mysql_query "$node" "$mysql_container" "$mysql_root_password" 'SELECT @@read_only;' | tr -d '\r')"
    super_read_only="$(mysql_query "$node" "$mysql_container" "$mysql_root_password" 'SELECT @@super_read_only;' | tr -d '\r')"
    server_id="$(mysql_query "$node" "$mysql_container" "$mysql_root_password" 'SELECT @@server_id;' | tr -d '\r')"
    MYSQL_READ_ONLY[$index]="$read_only"
    MYSQL_SUPER_READ_ONLY[$index]="$super_read_only"
    MYSQL_SERVER_IDS[$index]="$server_id"
    mysql_status="$(mysql_exec "$node" "$mysql_container" "$mysql_root_password" 'SHOW REPLICA STATUS\G' 2>/dev/null || true)"
    source_host="$(printf '%s\n' "$mysql_status" | awk -F': ' '/Source_Host:/{print $2; exit}')"
    replica_lag="$(printf '%s\n' "$mysql_status" | awk -F': ' '/Seconds_Behind_Source:/{print $2; exit}')"
    replica_io_running="$(printf '%s\n' "$mysql_status" | awk -F': ' '/Replica_IO_Running:/{print $2; exit}')"
    replica_sql_running="$(printf '%s\n' "$mysql_status" | awk -F': ' '/Replica_SQL_Running:/{print $2; exit}')"
    MYSQL_REPLICA_IO_RUNNING[$index]="${replica_io_running:-}"
    MYSQL_REPLICA_SQL_RUNNING[$index]="${replica_sql_running:-}"

    if [[ -n "$source_host" ]]; then
      MYSQL_SOURCE_HOSTS[$index]="${source_host:-unknown}"
      MYSQL_REPLICA_LAGS[$index]="${replica_lag:-unknown}"
      if [[ "${replica_io_running:-}" == "Yes" && "${replica_sql_running:-}" == "Yes" ]]; then
        if [[ "$read_only" == "1" && "$super_read_only" == "1" ]]; then
          MYSQL_ROLES[$index]="replica"
        else
          MYSQL_ROLES[$index]="replica-writable"
        fi
      else
        if [[ "$read_only" == "1" && "$super_read_only" == "1" ]]; then
          MYSQL_ROLES[$index]="replica-broken"
        else
          MYSQL_ROLES[$index]="replica-broken-writable"
        fi
      fi
    elif [[ "$read_only" == "0" ]]; then
      MYSQL_ROLES[$index]="writer"
      replica_count="$(mysql_query "$node" "$mysql_container" "$mysql_root_password" 'SHOW REPLICAS;' 2>/dev/null | awk 'END { print NR + 0 }')"
      MYSQL_SOURCE_HOSTS[$index]="-"
      MYSQL_REPLICA_LAGS[$index]="replicas=${replica_count:-0}"
    else
      MYSQL_ROLES[$index]="read-only-standalone"
      MYSQL_SOURCE_HOSTS[$index]="-"
      MYSQL_REPLICA_LAGS[$index]="-"
    fi
  else
    MYSQL_APP_DBS[$index]=""
    MYSQL_APP_USERS[$index]=""
    MYSQL_APP_PASSWORDS[$index]=""
    MYSQL_ROLES[$index]="absent"
    MYSQL_SERVER_IDS[$index]=""
    MYSQL_READ_ONLY[$index]=""
    MYSQL_SUPER_READ_ONLY[$index]=""
    MYSQL_SOURCE_HOSTS[$index]=""
    MYSQL_REPLICA_LAGS[$index]=""
    MYSQL_REPLICA_IO_RUNNING[$index]=""
    MYSQL_REPLICA_SQL_RUNNING[$index]=""
  fi

  redis_container="$(container_name "$node" 'redis-')"
  REDIS_CONTAINERS[$index]="$redis_container"
  if [[ -n "$redis_container" ]]; then
    REDIS_ROLES[$index]="$(run_vm "$node" "sudo docker exec ${redis_container} redis-cli INFO replication | sed -n 's/^role://p' | head -n 1" 2>/dev/null | tr -d '\r')"
  else
    REDIS_ROLES[$index]="absent"
  fi

  kafka_container="$(container_name "$node" 'kafka-')"
  KAFKA_CONTAINERS[$index]="$kafka_container"
  if [[ -n "$kafka_container" ]]; then
    KAFKA_CLUSTER_IDS[$index]="$(container_env "$node" "$kafka_container" CLUSTER_ID)"
    KAFKA_NODE_IDS[$index]="$(container_env "$node" "$kafka_container" KAFKA_NODE_ID)"
    KAFKA_VOTERS[$index]="$(run_vm "$node" "sudo docker exec ${kafka_container} /opt/kafka/bin/kafka-metadata-quorum.sh --bootstrap-server localhost:9092 describe --status 2>/dev/null | sed -n 's/^CurrentVoters:[[:space:]]*//p' | head -n 1" 2>/dev/null | tr -d '\r')"
    KAFKA_ADVERTISED[$index]="$(container_env "$node" "$kafka_container" KAFKA_ADVERTISED_LISTENERS)"
    kafka_mm2_worker="$(run_vm "$node" "sudo docker ps --format '{{.Names}} {{.Image}}' | awk '/connect|mirror-maker|mm2/{print; exit}'" 2>/dev/null || true)"
    KAFKA_MM2_WORKERS[$index]="${kafka_mm2_worker:-none}"
  else
    KAFKA_CLUSTER_IDS[$index]=""
    KAFKA_NODE_IDS[$index]=""
    KAFKA_VOTERS[$index]=""
    KAFKA_ADVERTISED[$index]=""
    KAFKA_MM2_WORKERS[$index]="none"
  fi
}

classify_topology() {
  local i

  MYSQL_WRITER_INDEXES=()
  MYSQL_REPLICA_INDEXES=()
  MYSQL_REPLICA_WRITABLE_INDEXES=()
  MYSQL_REPLICA_BROKEN_INDEXES=()
  MYSQL_REPLICA_BROKEN_WRITABLE_INDEXES=()
  REDIS_MASTER_INDEXES=()
  REDIS_REPLICA_INDEXES=()
  KAFKA_BROKER_INDEXES=()

  for ((i = 0; i < ${#NODE_NAMES[@]}; i++)); do
    case "${MYSQL_ROLES[$i]}" in
      writer)
        MYSQL_WRITER_INDEXES+=("$i")
        ;;
      replica)
        MYSQL_REPLICA_INDEXES+=("$i")
        ;;
      replica-writable)
        MYSQL_REPLICA_WRITABLE_INDEXES+=("$i")
        ;;
      replica-broken)
        MYSQL_REPLICA_BROKEN_INDEXES+=("$i")
        ;;
      replica-broken-writable)
        MYSQL_REPLICA_BROKEN_WRITABLE_INDEXES+=("$i")
        ;;
    esac

    if [[ "${REDIS_ROLES[$i]}" == "master" ]]; then
      REDIS_MASTER_INDEXES+=("$i")
    elif [[ "${REDIS_ROLES[$i]}" == "slave" ]]; then
      REDIS_REPLICA_INDEXES+=("$i")
    fi

    if [[ -n "${KAFKA_CONTAINERS[$i]}" ]]; then
      KAFKA_BROKER_INDEXES+=("$i")
    fi
  done
}

format_nodes_from_indexes() {
  local indexes=("$@")
  local formatted=()
  local index

  for index in "${indexes[@]}"; do
    formatted+=("${NODE_NAMES[$index]} (${NODE_IPS[$index]})")
  done

  local IFS=', '
  printf '%s\n' "${formatted[*]}"
}

find_index_by_mysql_role() {
  local wanted="$1"
  local i

  for ((i = 0; i < ${#NODE_NAMES[@]}; i++)); do
    if [[ "${MYSQL_ROLES[$i]}" == "$wanted" ]]; then
      echo "$i"
      return 0
    fi
  done

  return 1
}

find_index_by_redis_role() {
  local wanted="$1"
  local i

  for ((i = 0; i < ${#NODE_NAMES[@]}; i++)); do
    if [[ "${REDIS_ROLES[$i]}" == "$wanted" ]]; then
      echo "$i"
      return 0
    fi
  done

  return 1
}

first_kafka_index() {
  local i

  for ((i = 0; i < ${#NODE_NAMES[@]}; i++)); do
    if [[ -n "${KAFKA_CONTAINERS[$i]}" ]]; then
      echo "$i"
      return 0
    fi
  done

  return 1
}

first_nonempty_value() {
  local field_name="$1"
  local i
  local value

  for ((i = 0; i < ${#NODE_NAMES[@]}; i++)); do
    eval "value=\${${field_name}[$i]}"
    if [[ -n "$value" ]]; then
      printf '%s\n' "$value"
      return 0
    fi
  done

  return 1
}

external_kafka_bootstrap() {
  local index="$1"
  local advertised
  local external

  advertised="${KAFKA_ADVERTISED[$index]}"
  external="$(printf '%s\n' "$advertised" | sed -n 's/.*EXTERNAL:\/\/\([^,]*\).*/\1/p')"
  if [[ -n "$external" ]]; then
    printf '%s\n' "$external"
  else
    printf '%s:9094\n' "${NODE_IPS[$index]}"
  fi
}

kafka_cluster_count() {
  local unique_clusters=""
  local count=0
  local i
  local cluster_id

  for ((i = 0; i < ${#NODE_NAMES[@]}; i++)); do
    cluster_id="${KAFKA_CLUSTER_IDS[$i]}"
    if [[ -n "$cluster_id" ]] && [[ ",$unique_clusters," != *",$cluster_id,"* ]]; then
      unique_clusters="${unique_clusters},${cluster_id}"
      count=$((count + 1))
    fi
  done

  printf '%s\n' "$count"
}

load_all_nodes() {
  local i=0
  local node

  for node in "${NODES[@]}"; do
    load_node_state "$i" "$node"
    i=$((i + 1))
  done
}

detect_kubernetes_mm2_workers() {
  local pod_rows
  local pod_row
  local pod_name
  local pod_node
  local i

  kubeadm_cluster_active || return 0
  pod_rows="$(cluster_kubectl "-n kafka-replication get pods -l app=itunda-mirrormaker2 --field-selector=status.phase=Running -o jsonpath='{range .items[*]}{.metadata.name}{\" \"}{.spec.nodeName}{\"\\n\"}{end}'" 2>/dev/null | tr -d '\r')"
  [[ -n "$pod_rows" ]] || return 0

  while IFS= read -r pod_row; do
    [[ -z "$pod_row" ]] && continue
    pod_name="${pod_row%% *}"
    pod_node="${pod_row#* }"
    for ((i = 0; i < ${#NODE_NAMES[@]}; i++)); do
      if [[ "${NODE_NAMES[$i]}" == "$pod_node" ]]; then
        KAFKA_MM2_WORKERS[$i]="k8s/itunda-mirrormaker2 pod=${pod_name}"
      fi
    done
  done <<< "$pod_rows"
}

print_audit() {
  local cluster_count
  local backup_summary
  local webhook_summary
  local fraud_summary
  local i

  cluster_count="$(kafka_cluster_count)"
  backup_summary="$(mysql_backup_summary)"
  webhook_summary="$(webhook_delivery_summary)"
  fraud_summary="$(fraud_review_summary)"

  echo "Itunda private-cloud audit ($(current_time))"
  echo
  for ((i = 0; i < ${#NODE_NAMES[@]}; i++)); do
    echo "${NODE_NAMES[$i]} (${NODE_IPS[$i]})"
    echo "  listeners: ${NODE_LISTENERS[$i]:-none}"
    echo "  mysql: ${MYSQL_CONTAINERS[$i]:-none} role=${MYSQL_ROLES[$i]:-absent} server_id=${MYSQL_SERVER_IDS[$i]:--} read_only=${MYSQL_READ_ONLY[$i]:--} super_read_only=${MYSQL_SUPER_READ_ONLY[$i]:--}"
    if [[ "${MYSQL_ROLES[$i]}" == replica* ]]; then
      echo "    source=${MYSQL_SOURCE_HOSTS[$i]:-unknown} lag=${MYSQL_REPLICA_LAGS[$i]:-unknown} io=${MYSQL_REPLICA_IO_RUNNING[$i]:-unknown} sql=${MYSQL_REPLICA_SQL_RUNNING[$i]:-unknown}"
    elif [[ "${MYSQL_ROLES[$i]}" == "writer" ]]; then
      echo "    ${MYSQL_REPLICA_LAGS[$i]:-replicas=0}"
    fi
    echo "  redis: ${REDIS_CONTAINERS[$i]:-none} role=${REDIS_ROLES[$i]:-absent}"
    echo "  kafka: ${KAFKA_CONTAINERS[$i]:-none} cluster_id=${KAFKA_CLUSTER_IDS[$i]:--} node_id=${KAFKA_NODE_IDS[$i]:--}"
    echo "    voters=${KAFKA_VOTERS[$i]:-unknown} external=${KAFKA_ADVERTISED[$i]:-unknown}"
    echo "    mm2_worker=${KAFKA_MM2_WORKERS[$i]:-none}"
    echo
  done
  echo "Summary"
  if [[ "${#MYSQL_WRITER_INDEXES[@]}" -eq 1 ]]; then
    i="${MYSQL_WRITER_INDEXES[0]}"
    echo "- MySQL writer: ${NODE_NAMES[$i]} (${NODE_IPS[$i]})."
  elif [[ "${#MYSQL_WRITER_INDEXES[@]}" -gt 1 ]]; then
    echo "- MySQL writer safety violation: multiple standalone writers detected: $(format_nodes_from_indexes "${MYSQL_WRITER_INDEXES[@]}")."
  else
    echo "- MySQL writer was not detected automatically."
  fi

  if [[ "${#MYSQL_REPLICA_INDEXES[@]}" -gt 0 ]]; then
    echo "- Healthy read-only MySQL replicas: $(format_nodes_from_indexes "${MYSQL_REPLICA_INDEXES[@]}")."
  else
    echo "- No healthy read-only MySQL replica node was detected."
  fi

  if [[ "${#MYSQL_REPLICA_WRITABLE_INDEXES[@]}" -gt 0 ]]; then
    echo "- Writable MySQL replicas detected: $(format_nodes_from_indexes "${MYSQL_REPLICA_WRITABLE_INDEXES[@]}"). Replication may be running, but failover safety is broken until read_only and super_read_only are restored."
  fi

  if [[ "${#MYSQL_REPLICA_BROKEN_INDEXES[@]}" -gt 0 ]]; then
    echo "- Broken read-only MySQL replicas detected: $(format_nodes_from_indexes "${MYSQL_REPLICA_BROKEN_INDEXES[@]}")."
  fi

  if [[ "${#MYSQL_REPLICA_BROKEN_WRITABLE_INDEXES[@]}" -gt 0 ]]; then
    echo "- Broken writable MySQL replicas detected: $(format_nodes_from_indexes "${MYSQL_REPLICA_BROKEN_WRITABLE_INDEXES[@]}")."
  fi

  if [[ "${#REDIS_MASTER_INDEXES[@]}" -eq 1 ]]; then
    i="${REDIS_MASTER_INDEXES[0]}"
    if [[ "${#REDIS_REPLICA_INDEXES[@]}" -gt 0 ]]; then
      echo "- Redis master on ${NODE_NAMES[$i]} (${NODE_IPS[$i]}) with a live replica on $(format_nodes_from_indexes "${REDIS_REPLICA_INDEXES[@]}")."
    else
      echo "- Redis is a singleton master on ${NODE_NAMES[$i]} (${NODE_IPS[$i]}), no replica detected."
    fi
  elif [[ "${#REDIS_MASTER_INDEXES[@]}" -gt 1 ]]; then
    echo "- Redis safety violation: multiple masters detected: $(format_nodes_from_indexes "${REDIS_MASTER_INDEXES[@]}")."
  else
    echo "- Redis master was not detected automatically."
  fi

  if [[ "${#KAFKA_BROKER_INDEXES[@]}" -gt 0 ]]; then
    if [[ "$cluster_count" -gt 1 ]]; then
      echo "- Kafka is two independent clusters, not one shared quorum; current broker clusters detected: $cluster_count."
    else
      echo "- Kafka cluster count could not be distinguished from the live data."
    fi
  else
    echo "- Kafka brokers were not detected automatically."
  fi

  if printf '%s\n' "${KAFKA_MM2_WORKERS[@]}" | grep -qv '^none$'; then
    echo "- An MM2 worker is live for the Kafka baseline (Docker-side or Kubernetes-hosted)."
  else
    echo "- No MM2 worker was detected; bidirectional Kafka mirroring is not proven by this topology alone."
  fi

  echo "- MySQL local backup: ${backup_summary}."
  echo "- Merchant webhook deliveries: ${webhook_summary}."
  echo "- Fraud review queue: ${fraud_summary}."
  print_fraud_review_backlog_warning "$fraud_summary"

  local redis_ha_phrase="Redis singleton"
  if [[ "${#REDIS_MASTER_INDEXES[@]}" -eq 1 && "${#REDIS_REPLICA_INDEXES[@]}" -gt 0 ]]; then
    redis_ha_phrase="Redis master+replica (no automated failover yet)"
  fi
  echo "- Verdict: this Multipass cloud is not yet a full Toss-style active-active platform. The stateless app layer is live on Kubernetes, but the current data plane is still MySQL single-writer, ${redis_ha_phrase}, and Kafka split into independent single-broker clusters with an OSS MM2 baseline."
}

verify_topology() {
  local issues=0
  local backup_summary
  local backup_timer
  local backup_age
  local max_backup_age_seconds="${ITUNDA_PRIVATE_CLOUD_MAX_BACKUP_AGE_SECONDS:-90000}"

  backup_summary="$(mysql_backup_summary)"
  backup_timer="$(printf '%s\n' "$backup_summary" | sed -n 's/.*timer=\([^ ]*\).*/\1/p')"
  backup_age="$(printf '%s\n' "$backup_summary" | sed -n 's/.*age_seconds=\([^ ]*\).*/\1/p')"

  if [[ "${#MYSQL_WRITER_INDEXES[@]}" -ne 1 ]]; then
    echo "Unsafe topology: expected exactly 1 standalone MySQL writer, found ${#MYSQL_WRITER_INDEXES[@]}." >&2
    if [[ "${#MYSQL_WRITER_INDEXES[@]}" -gt 0 ]]; then
      echo "Writers: $(format_nodes_from_indexes "${MYSQL_WRITER_INDEXES[@]}")." >&2
    fi
    issues=$((issues + 1))
  fi

  if [[ "${#MYSQL_REPLICA_INDEXES[@]}" -lt 1 ]]; then
    echo "Unsafe topology: no healthy read-only MySQL replica detected." >&2
    issues=$((issues + 1))
  fi

  if [[ "${#MYSQL_REPLICA_WRITABLE_INDEXES[@]}" -gt 0 ]]; then
    echo "Unsafe topology: writable MySQL replica(s) detected: $(format_nodes_from_indexes "${MYSQL_REPLICA_WRITABLE_INDEXES[@]}")." >&2
    issues=$((issues + 1))
  fi

  if [[ "${#MYSQL_REPLICA_BROKEN_INDEXES[@]}" -gt 0 ]]; then
    echo "Unsafe topology: broken MySQL replica(s) detected: $(format_nodes_from_indexes "${MYSQL_REPLICA_BROKEN_INDEXES[@]}")." >&2
    issues=$((issues + 1))
  fi

  if [[ "${#MYSQL_REPLICA_BROKEN_WRITABLE_INDEXES[@]}" -gt 0 ]]; then
    echo "Unsafe topology: broken writable MySQL replica(s) detected: $(format_nodes_from_indexes "${MYSQL_REPLICA_BROKEN_WRITABLE_INDEXES[@]}")." >&2
    issues=$((issues + 1))
  fi

  if [[ "${#REDIS_MASTER_INDEXES[@]}" -ne 1 ]]; then
    echo "Unsafe topology: expected exactly 1 Redis master, found ${#REDIS_MASTER_INDEXES[@]}." >&2
    issues=$((issues + 1))
  fi

  if [[ "${#KAFKA_BROKER_INDEXES[@]}" -lt 1 ]]; then
    echo "Unsafe topology: no Kafka brokers detected." >&2
    issues=$((issues + 1))
  fi

  if [[ "$backup_timer" != "active" ]]; then
    echo "Unsafe recovery baseline: MySQL backup timer is ${backup_timer:-unknown}." >&2
    issues=$((issues + 1))
  elif [[ ! "$backup_age" =~ ^[0-9]+$ ]] || (( backup_age > max_backup_age_seconds )); then
    echo "Unsafe recovery baseline: latest MySQL backup age is ${backup_age:-unknown}s (maximum ${max_backup_age_seconds}s)." >&2
    issues=$((issues + 1))
  fi

  if [[ "$issues" -eq 0 ]]; then
    echo "Topology verification passed."
    return 0
  fi

  return 1
}

print_env() {
  local allow_unsafe="${ITUNDA_PRIVATE_CLOUD_ALLOW_UNSAFE_TOPOLOGY:-0}"
  local writer_index
  local redis_master_index
  local kafka_index
  local db_name
  local db_user
  local db_password

  if [[ "${#MYSQL_WRITER_INDEXES[@]}" -ne 1 ]]; then
    echo "Refusing to generate env: expected exactly one standalone MySQL writer, found ${#MYSQL_WRITER_INDEXES[@]}." >&2
    exit 1
  fi

  if [[ "${#MYSQL_REPLICA_INDEXES[@]}" -lt 1 && "${allow_unsafe}" != "1" ]]; then
    echo "Refusing to generate env: no healthy read-only MySQL replica detected." >&2
    exit 1
  fi

  if [[ ("${#MYSQL_REPLICA_WRITABLE_INDEXES[@]}" -gt 0 || "${#MYSQL_REPLICA_BROKEN_INDEXES[@]}" -gt 0 || "${#MYSQL_REPLICA_BROKEN_WRITABLE_INDEXES[@]}" -gt 0) && "${allow_unsafe}" != "1" ]]; then
    echo "Refusing to generate env: MySQL replica topology is unsafe. Run 'bash scripts/private-cloud-audit.sh audit' for details." >&2
    exit 1
  fi

  if [[ "${#REDIS_MASTER_INDEXES[@]}" -ne 1 ]]; then
    echo "Refusing to generate env: expected exactly one Redis master, found ${#REDIS_MASTER_INDEXES[@]}." >&2
    exit 1
  fi

  if [[ "${#KAFKA_BROKER_INDEXES[@]}" -lt 1 ]]; then
    echo "Refusing to generate env: no Kafka brokers detected." >&2
    exit 1
  fi

  writer_index="${MYSQL_WRITER_INDEXES[0]}"
  redis_master_index="${REDIS_MASTER_INDEXES[0]}"
  if [[ -n "$writer_index" ]] && [[ -n "${KAFKA_CONTAINERS[$writer_index]}" ]]; then
    kafka_index="$writer_index"
  else
    kafka_index="$(first_kafka_index || true)"
  fi

  if [[ -z "$kafka_index" ]]; then
    echo "Could not find a Kafka broker node." >&2
    exit 1
  fi

  db_name="${MYSQL_APP_DBS[$writer_index]:-}"
  db_user="${MYSQL_APP_USERS[$writer_index]:-}"
  db_password="${MYSQL_APP_PASSWORDS[$writer_index]:-}"

  if [[ -z "$db_name" ]]; then
    db_name="$(first_nonempty_value MYSQL_APP_DBS || true)"
  fi
  if [[ -z "$db_user" ]]; then
    db_user="$(first_nonempty_value MYSQL_APP_USERS || true)"
  fi
  if [[ -z "$db_password" ]]; then
    db_password="$(first_nonempty_value MYSQL_APP_PASSWORDS || true)"
  fi

  db_name="${db_name:-itunda}"
  db_user="${db_user:-itunda}"
  db_password="${db_password:-itunda_password}"

  echo "# Generated by scripts/private-cloud-audit.sh on $(current_time)"
  echo "# Current audited writer: ${NODE_NAMES[$writer_index]} (${NODE_IPS[$writer_index]})"
  echo "# Current audited Redis master: ${NODE_NAMES[$redis_master_index]} (${NODE_IPS[$redis_master_index]})"
  if [[ "${allow_unsafe}" == "1" ]]; then
    echo "# Unsafe topology override enabled; env values may be emitted without a healthy MySQL replica."
  fi
  echo "# Kafka has an MM2 baseline when the replication deployment is healthy, but app clients"
  echo "# still target one broker explicitly and fail over manually."
  echo "DB_HOST=${NODE_IPS[$writer_index]}"
  echo "DB_PORT=3306"
  echo "DB_NAME=${db_name}"
  echo "DB_USER=${db_user}"
  echo "DB_PASSWORD=${db_password}"
  echo
  echo "REDIS_HOST=${NODE_IPS[$redis_master_index]}"
  echo "REDIS_PORT=6379"
  echo
  echo "KAFKA_BOOTSTRAP_SERVERS=$(external_kafka_bootstrap "$kafka_index")"
}

print_json() {
  local nodes_file
  local summary_file
  local writer_node=""
  local redis_master_node=""
  local writer_index
  local redis_master_index
  local kafka_index
  local backup_summary
  local webhook_summary
  local fraud_summary
  local backup_timer
  local backup_age
  local webhook_available
  local webhook_pending
  local webhook_exhausted
  local webhook_overdue
  local fraud_available
  local fraud_unreviewed
  local fraud_oldest_age
  local fraud_warn_unreviewed="${ITUNDA_PRIVATE_CLOUD_FRAUD_REVIEW_WARN_UNREVIEWED:-50}"
  local fraud_warn_oldest_age="${ITUNDA_PRIVATE_CLOUD_FRAUD_REVIEW_WARN_OLDEST_AGE_SECONDS:-86400}"
  local i

  require_cmd jq
  nodes_file="$(mktemp "${TMPDIR:-/tmp}/private-cloud-nodes.XXXXXX")"
  summary_file="$(mktemp "${TMPDIR:-/tmp}/private-cloud-summary.XXXXXX")"
  backup_summary="$(mysql_backup_summary)"
  webhook_summary="$(webhook_delivery_summary)"
  fraud_summary="$(fraud_review_summary)"
  backup_timer="$(printf '%s\n' "$backup_summary" | sed -n 's/.*timer=\([^ ]*\).*/\1/p')"
  backup_age="$(printf '%s\n' "$backup_summary" | sed -n 's/.*age_seconds=\([^ ]*\).*/\1/p')"
  webhook_available="$(printf '%s\n' "$webhook_summary" | sed -n 's/.*available=\([^ ]*\).*/\1/p')"
  webhook_pending="$(printf '%s\n' "$webhook_summary" | sed -n 's/.*pending=\([^ ]*\).*/\1/p')"
  webhook_exhausted="$(printf '%s\n' "$webhook_summary" | sed -n 's/.*exhausted=\([^ ]*\).*/\1/p')"
  webhook_overdue="$(printf '%s\n' "$webhook_summary" | sed -n 's/.*overdue=\([^ ]*\).*/\1/p')"
  fraud_available="$(printf '%s\n' "$fraud_summary" | sed -n 's/.*available=\([^ ]*\).*/\1/p')"
  fraud_unreviewed="$(printf '%s\n' "$fraud_summary" | sed -n 's/.*unreviewed=\([^ ]*\).*/\1/p')"
  fraud_oldest_age="$(printf '%s\n' "$fraud_summary" | sed -n 's/.*oldest_age_seconds=\([^ ]*\).*/\1/p')"
  [[ "$fraud_warn_unreviewed" =~ ^[0-9]+$ ]] || fraud_warn_unreviewed=50
  [[ "$fraud_warn_oldest_age" =~ ^[0-9]+$ ]] || fraud_warn_oldest_age=86400

  for ((i = 0; i < ${#NODE_NAMES[@]}; i++)); do
    jq -n \
      --arg name "${NODE_NAMES[$i]}" \
      --arg ip "${NODE_IPS[$i]}" \
      --arg listeners "${NODE_LISTENERS[$i]}" \
      --arg mysql_container "${MYSQL_CONTAINERS[$i]}" \
      --arg mysql_role "${MYSQL_ROLES[$i]}" \
      --arg mysql_server_id "${MYSQL_SERVER_IDS[$i]}" \
      --arg mysql_read_only "${MYSQL_READ_ONLY[$i]}" \
      --arg mysql_super_read_only "${MYSQL_SUPER_READ_ONLY[$i]}" \
      --arg mysql_source_host "${MYSQL_SOURCE_HOSTS[$i]}" \
      --arg mysql_replica_lag "${MYSQL_REPLICA_LAGS[$i]}" \
      --arg mysql_replica_io_running "${MYSQL_REPLICA_IO_RUNNING[$i]}" \
      --arg mysql_replica_sql_running "${MYSQL_REPLICA_SQL_RUNNING[$i]}" \
      --arg redis_container "${REDIS_CONTAINERS[$i]}" \
      --arg redis_role "${REDIS_ROLES[$i]}" \
      --arg kafka_container "${KAFKA_CONTAINERS[$i]}" \
      --arg kafka_cluster_id "${KAFKA_CLUSTER_IDS[$i]}" \
      --arg kafka_node_id "${KAFKA_NODE_IDS[$i]}" \
      --arg kafka_voters "${KAFKA_VOTERS[$i]}" \
      --arg kafka_advertised "${KAFKA_ADVERTISED[$i]}" \
      --arg kafka_external "$(external_kafka_bootstrap "$i")" \
      --arg kafka_mm2_worker "${KAFKA_MM2_WORKERS[$i]}" \
      '{
        name: $name,
        ip: $ip,
        listeners: ($listeners | select(length > 0) | split(",") // []),
        mysql: {
          container: $mysql_container,
          role: $mysql_role,
          serverId: $mysql_server_id,
          readOnly: $mysql_read_only,
          superReadOnly: $mysql_super_read_only,
          sourceHost: $mysql_source_host,
          replicaLag: $mysql_replica_lag,
          replicaIoRunning: $mysql_replica_io_running,
          replicaSqlRunning: $mysql_replica_sql_running
        },
        redis: {
          container: $redis_container,
          role: $redis_role
        },
        kafka: {
          container: $kafka_container,
          clusterId: $kafka_cluster_id,
          nodeId: $kafka_node_id,
          voters: $kafka_voters,
          advertisedListeners: $kafka_advertised,
          externalBootstrap: $kafka_external,
          mm2Worker: $kafka_mm2_worker
        }
      }' >> "$nodes_file"
  done

  if [[ "${#MYSQL_WRITER_INDEXES[@]}" -eq 1 ]]; then
    writer_index="${MYSQL_WRITER_INDEXES[0]}"
    writer_node="${NODE_NAMES[$writer_index]}"
  fi

  if [[ "${#REDIS_MASTER_INDEXES[@]}" -eq 1 ]]; then
    redis_master_index="${REDIS_MASTER_INDEXES[0]}"
    redis_master_node="${NODE_NAMES[$redis_master_index]}"
  fi

  if [[ "${#KAFKA_BROKER_INDEXES[@]}" -gt 0 ]]; then
    kafka_index="${KAFKA_BROKER_INDEXES[0]}"
  else
    kafka_index=""
  fi

  jq -n \
    --arg generated_at "$(current_time)" \
    --arg inventory_path "$INVENTORY_PATH" \
    --arg writer_node "$writer_node" \
    --arg redis_master_node "$redis_master_node" \
    --arg kafka_bootstrap "${kafka_index:+$(external_kafka_bootstrap "$kafka_index")}" \
    --arg backup_timer "$backup_timer" \
    --arg backup_age "$backup_age" \
    --arg webhook_available "$webhook_available" \
    --arg webhook_pending "$webhook_pending" \
    --arg webhook_exhausted "$webhook_exhausted" \
    --arg webhook_overdue "$webhook_overdue" \
    --arg fraud_available "$fraud_available" \
    --arg fraud_unreviewed "$fraud_unreviewed" \
    --arg fraud_oldest_age "$fraud_oldest_age" \
    --argjson fraud_warn_unreviewed "$fraud_warn_unreviewed" \
    --argjson fraud_warn_oldest_age "$fraud_warn_oldest_age" \
    --argjson mysql_writer_count "${#MYSQL_WRITER_INDEXES[@]}" \
    --argjson mysql_replica_count "${#MYSQL_REPLICA_INDEXES[@]}" \
    --argjson mysql_writable_replica_count "${#MYSQL_REPLICA_WRITABLE_INDEXES[@]}" \
    --argjson mysql_broken_replica_count "${#MYSQL_REPLICA_BROKEN_INDEXES[@]}" \
    --argjson mysql_broken_writable_replica_count "${#MYSQL_REPLICA_BROKEN_WRITABLE_INDEXES[@]}" \
    --argjson redis_master_count "${#REDIS_MASTER_INDEXES[@]}" \
    --argjson redis_replica_count "${#REDIS_REPLICA_INDEXES[@]}" \
    --argjson kafka_broker_count "${#KAFKA_BROKER_INDEXES[@]}" \
    --argjson kafka_cluster_count "$(kafka_cluster_count)" \
    --slurpfile nodes "$nodes_file" \
    'def number_or_null: tonumber? // null;
     def availability: if . == "1" then true elif . == "0" then false else null end;
     {
      generatedAt: $generated_at,
      inventoryPath: $inventory_path,
      nodes: $nodes,
      summary: {
        mysql: {
          writerNode: $writer_node,
          writerCount: $mysql_writer_count,
          healthyReplicaCount: $mysql_replica_count,
          writableReplicaCount: $mysql_writable_replica_count,
          brokenReplicaCount: $mysql_broken_replica_count,
          brokenWritableReplicaCount: $mysql_broken_writable_replica_count
        },
        redis: {
          masterNode: $redis_master_node,
          masterCount: $redis_master_count,
          replicaCount: $redis_replica_count
        },
        kafka: {
          bootstrap: $kafka_bootstrap,
          brokerCount: $kafka_broker_count,
          clusterCount: $kafka_cluster_count
        },
        recovery: {
          mysqlBackup: {
            timer: $backup_timer,
            ageSeconds: ($backup_age | number_or_null)
          }
        },
        operations: {
          webhookDeliveries: {
            available: ($webhook_available | availability),
            pending: ($webhook_pending | number_or_null),
            exhausted: ($webhook_exhausted | number_or_null),
            overdue: ($webhook_overdue | number_or_null)
          },
          fraudReview: {
            available: ($fraud_available | availability),
            unreviewed: ($fraud_unreviewed | number_or_null),
            oldestUnreviewedAgeSeconds: ($fraud_oldest_age | number_or_null)
          }
        }
      },
      warnings: [
        (if $fraud_available == "1" and (($fraud_unreviewed | number_or_null) as $unreviewed | ($fraud_oldest_age | number_or_null) as $oldestAge | $unreviewed != null and $oldestAge != null and ($unreviewed >= $fraud_warn_unreviewed or $oldestAge >= $fraud_warn_oldest_age))
         then {
           code: "FRAUD_REVIEW_BACKLOG",
           severity: "warning",
           message: "Fraud review backlog needs an authorized reviewer",
           unreviewed: ($fraud_unreviewed | number_or_null),
           oldestUnreviewedAgeSeconds: ($fraud_oldest_age | number_or_null),
           thresholds: {unreviewed: $fraud_warn_unreviewed, oldestAgeSeconds: $fraud_warn_oldest_age}
         }
         else empty
         end)
      ]
    }' > "$summary_file"

  cat "$summary_file"
  rm -f "$nodes_file" "$summary_file"
}

load_all_nodes
detect_kubernetes_mm2_workers
classify_topology

case "$MODE" in
  audit)
    print_audit
    ;;
  verify)
    verify_topology
    ;;
  env)
    print_env
    ;;
  json)
    print_json
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
