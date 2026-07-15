#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/private-cloud-lib.sh"

MODE="${1:-help}"
MM2_NAMESPACE="${ITUNDA_PRIVATE_CLOUD_KAFKA_REPLICATION_NAMESPACE:-kafka-replication}"
MM2_DEPLOYMENT="${ITUNDA_PRIVATE_CLOUD_MM2_DEPLOYMENT_NAME:-itunda-mirrormaker2}"
DRILL_TOPIC_PREFIX="${ITUNDA_PRIVATE_CLOUD_MM2_DRILL_TOPIC_PREFIX:-ledger.mm2-drill}"
DRILL_TIMEOUT_SECONDS="${ITUNDA_PRIVATE_CLOUD_MM2_DRILL_TIMEOUT_SECONDS:-90}"
DISCOVERY_TIMEOUT_SECONDS="${ITUNDA_PRIVATE_CLOUD_MM2_DISCOVERY_TIMEOUT_SECONDS:-45}"

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-kafka-drill.sh <command>

Commands:
  status                 Print the live kubeadm/MM2 drill prerequisites
  drill                  Run a bidirectional MM2 smoke drill on the current Kubernetes cluster
  drill-one-way <src> <dst>
                         Run a one-way drill such as dc-a -> dc-b
EOF
}

require_cluster() {
  require_cmd multipass

  if ! cluster_is_active "$PRIMARY_NODE"; then
    echo "Kubernetes is not active on ${PRIMARY_NODE}. Run scripts/private-cloud-bootstrap.sh bootstrap first." >&2
    exit 1
  fi

  require_kubeadm_cluster
}

mm2_deployment_ready() {
  local status_line
  local ready_column
  local ready_current
  local ready_desired
  local available_replicas

  status_line="$(cluster_kubectl "-n ${MM2_NAMESPACE} get deployment/${MM2_DEPLOYMENT} --no-headers" 2>/dev/null | tr -d '\r')"
  [[ -n "$status_line" ]] || return 1

  ready_column="$(printf '%s\n' "$status_line" | awk 'NR==1{print $2}')"
  available_replicas="$(printf '%s\n' "$status_line" | awk 'NR==1{print $4}')"
  ready_current="${ready_column%/*}"
  ready_desired="${ready_column#*/}"

  [[ "${ready_current:-0}" =~ ^[1-9][0-9]*$ ]] && [[ "$ready_current" == "$ready_desired" ]] && [[ "${available_replicas:-0}" =~ ^[1-9][0-9]*$ ]]
}

require_mm2_ready() {
  require_cluster
  wait_until 180 5 "MM2 deployment ${MM2_DEPLOYMENT} to become Ready" mm2_deployment_ready
}

run_with_timeout() {
  local seconds="$1"
  shift
  local cmd_pid
  local killer_pid
  local status

  "$@" &
  cmd_pid=$!
  (
    sleep "$seconds"
    kill -TERM "$cmd_pid" >/dev/null 2>&1 || true
  ) &
  killer_pid=$!

  if wait "$cmd_pid"; then
    status=0
  else
    status=$?
  fi

  kill -TERM "$killer_pid" >/dev/null 2>&1 || true
  wait "$killer_pid" >/dev/null 2>&1 || true
  return "$status"
}

wait_until() {
  local timeout_seconds="$1"
  local interval_seconds="$2"
  local description="$3"
  shift 3
  local deadline=$((SECONDS + timeout_seconds))

  while (( SECONDS < deadline )); do
    if "$@"; then
      return 0
    fi
    sleep "$interval_seconds"
  done

  echo "Timed out waiting for ${description}." >&2
  return 1
}

ensure_alias_supported() {
  local alias="$1"

  case "$alias" in
    dc-a|dc-b)
      return 0
      ;;
    *)
      echo "Unsupported cluster alias: ${alias}" >&2
      exit 1
      ;;
  esac
}

node_for_alias() {
  local alias="$1"

  ensure_alias_supported "$alias"
  case "$alias" in
    dc-a)
      printf '%s\n' "${NODES[0]}"
      ;;
    dc-b)
      if [[ "${#NODES[@]}" -lt 2 ]]; then
        echo "Need at least two private-cloud nodes for MM2 drills." >&2
        exit 1
      fi
      printf '%s\n' "${NODES[1]}"
      ;;
  esac
}

kafka_container_for_node() {
  local node="$1"
  local container

  container="$(container_name "$node" 'kafka-')"
  if [[ -z "$container" ]]; then
    echo "Kafka container not found on ${node}." >&2
    exit 1
  fi

  printf '%s\n' "$container"
}

run_kafka_shell() {
  local node="$1"
  local container="$2"
  shift 2

  run_vm "$node" "sudo docker exec ${container} bash -lc $(shell_quote "$*")"
}

kafka_topic_exists() {
  local node="$1"
  local container="$2"
  local topic="$3"
  local topic_quoted

  topic_quoted="$(shell_quote "$topic")"
  run_kafka_shell "$node" "$container" "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep -Fx ${topic_quoted} >/dev/null"
}

kafka_group_exists() {
  local node="$1"
  local container="$2"
  local group="$3"
  local group_quoted

  group_quoted="$(shell_quote "$group")"
  run_kafka_shell "$node" "$container" "/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list | grep -Fx ${group_quoted} >/dev/null"
}

create_drill_topic() {
  local node="$1"
  local container="$2"
  local topic="$3"
  local topic_quoted

  topic_quoted="$(shell_quote "$topic")"
  run_kafka_shell "$node" "$container" "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic ${topic_quoted} --partitions 1 --replication-factor 1 >/dev/null"
}

produce_message() {
  local node="$1"
  local container="$2"
  local topic="$3"
  local message="$4"
  local topic_quoted
  local message_quoted

  topic_quoted="$(shell_quote "$topic")"
  message_quoted="$(shell_quote "$message")"
  run_kafka_shell "$node" "$container" "printf '%s\n' ${message_quoted} | /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic ${topic_quoted} >/dev/null"
}

consume_topic_contains_message() {
  local node="$1"
  local container="$2"
  local topic="$3"
  local message="$4"
  local topic_quoted
  local message_quoted

  topic_quoted="$(shell_quote "$topic")"
  message_quoted="$(shell_quote "$message")"
  run_with_timeout 20 run_kafka_shell "$node" "$container" "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic ${topic_quoted} --from-beginning --max-messages 1 --timeout-ms 5000 | grep -Fx ${message_quoted} >/dev/null"
}

commit_source_group() {
  local node="$1"
  local container="$2"
  local topic="$3"
  local group="$4"
  local topic_quoted
  local group_quoted

  topic_quoted="$(shell_quote "$topic")"
  group_quoted="$(shell_quote "$group")"
  run_with_timeout 20 run_kafka_shell "$node" "$container" "/opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic ${topic_quoted} --group ${group_quoted} --from-beginning --max-messages 1 --timeout-ms 5000 >/dev/null"
}

list_sync_topics() {
  local node="$1"
  local container="$2"

  run_kafka_shell "$node" "$container" "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep -E 'heartbeats|checkpoints\\.internal|offset-sync' || true"
}

mm2_log_excerpt() {
  cluster_kubectl "-n ${MM2_NAMESPACE} logs deployment/${MM2_DEPLOYMENT} --tail=120" | grep -E 'Mirror|connector|topic-partitions|leader|ERROR|WARN' || true
}

show_status() {
  local alias
  local node
  local container

  require_mm2_ready

  echo "Cluster distro: $(installed_cluster_distro)"
  cluster_kubectl "-n ${MM2_NAMESPACE} get deployment/${MM2_DEPLOYMENT} pod -l app=${MM2_DEPLOYMENT} -o wide"
  echo

  for alias in dc-a dc-b; do
    node="$(node_for_alias "$alias")"
    container="$(kafka_container_for_node "$node")"
    echo "${alias} (${node})"
    echo "  container: ${container}"
    echo "  mm2 sync topics:"
    list_sync_topics "$node" "$container" | sed 's/^/    /'
    echo "  mm2 drill topics:"
    run_kafka_shell "$node" "$container" "/opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep -E '^${DRILL_TOPIC_PREFIX//./\\.}' || true" | sed 's/^/    /'
    echo
  done
}

run_one_way_drill() {
  local run_id="$1"
  local source_alias="$2"
  local target_alias="$3"
  local source_node
  local target_node
  local source_container
  local target_container
  local topic
  local remote_topic
  local group
  local message
  local sync_topics

  ensure_alias_supported "$source_alias"
  ensure_alias_supported "$target_alias"
  if [[ "$source_alias" == "$target_alias" ]]; then
    echo "Source and target aliases must differ." >&2
    return 1
  fi

  source_node="$(node_for_alias "$source_alias")"
  target_node="$(node_for_alias "$target_alias")"
  source_container="$(kafka_container_for_node "$source_node")"
  target_container="$(kafka_container_for_node "$target_node")"

  topic="${DRILL_TOPIC_PREFIX}.${source_alias}.${run_id}"
  remote_topic="${source_alias}.${topic}"
  group="itunda-mm2-drill-${source_alias}-${run_id}"
  message="mm2-drill|run=${run_id}|source=${source_alias}|target=${target_alias}"

  echo "Drill ${source_alias} -> ${target_alias}"
  echo "  topic: ${topic}"
  echo "  remote topic: ${remote_topic}"
  echo "  group: ${group}"

  create_drill_topic "$source_node" "$source_container" "$topic"
  produce_message "$source_node" "$source_container" "$topic" "$message"
  commit_source_group "$source_node" "$source_container" "$topic" "$group"

  wait_until "$DISCOVERY_TIMEOUT_SECONDS" 5 "remote topic ${remote_topic} on ${target_alias}" \
    kafka_topic_exists "$target_node" "$target_container" "$remote_topic"

  wait_until "$DRILL_TIMEOUT_SECONDS" 5 "replicated payload on ${remote_topic}" \
    consume_topic_contains_message "$target_node" "$target_container" "$remote_topic" "$message"

  wait_until "$DRILL_TIMEOUT_SECONDS" 5 "translated consumer group ${group} on ${target_alias}" \
    kafka_group_exists "$target_node" "$target_container" "$group"

  sync_topics="$(list_sync_topics "$target_node" "$target_container" | tr '\n' ' ' | sed 's/[[:space:]]\+$//')"
  if [[ -z "$sync_topics" ]]; then
    echo "No MM2 sync topics were visible on ${target_alias} after the drill." >&2
    return 1
  fi

  echo "  synced topics: ${sync_topics}"
  echo "  result: PASS"
  return 0
}

run_bidirectional_drill() {
  local run_id
  local failures=0

  require_mm2_ready
  run_id="$(date -u +%Y%m%d%H%M%S)"

  if ! run_one_way_drill "$run_id" dc-a dc-b; then
    failures=$((failures + 1))
  fi
  echo
  if ! run_one_way_drill "$run_id" dc-b dc-a; then
    failures=$((failures + 1))
  fi

  if [[ "$failures" -gt 0 ]]; then
    echo
    echo "MM2 diagnostics"
    cluster_kubectl "-n ${MM2_NAMESPACE} get deployment/${MM2_DEPLOYMENT} pod -l app=${MM2_DEPLOYMENT} -o wide" || true
    mm2_log_excerpt || true
    return 1
  fi

  echo
  echo "Bidirectional MM2 drill passed."
}

case "$MODE" in
  status)
    show_status
    ;;
  drill)
    run_bidirectional_drill
    ;;
  drill-one-way)
    require_mm2_ready
    if [[ "$#" -ne 3 ]]; then
      print_help
      exit 1
    fi
    run_one_way_drill "$(date -u +%Y%m%d%H%M%S)" "$2" "$3"
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
