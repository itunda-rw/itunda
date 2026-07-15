#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/private-cloud-lib.sh"

MODE="${1:-help}"
PARTITIONS="${ITUNDA_KAFKA_PARTITIONS:-3}"
REPLICATION_FACTOR="${ITUNDA_KAFKA_REPLICATION_FACTOR:-1}"
TOPIC_CREATE_TIMEOUT="${ITUNDA_KAFKA_TOPIC_CREATE_TIMEOUT:-20}"
IFS=' ' read -r -a TOPICS <<< "${ITUNDA_KAFKA_TOPICS:-ledger.posted transfer.confirmed payment.provider_succeeded payment.provider_failed payment-events}"

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-kafka-topics.sh <command>

Commands:
  ensure   Create the required Itunda topics on every detected Kafka VM
  list     Print the current topic list for every detected Kafka VM
EOF
}

ensure_topics_on_node() {
  local node="$1"
  local container="$2"
  local topic
  local failures=0

  echo "$node (${container})"
  for topic in "${TOPICS[@]}"; do
    if run_vm "$node" "timeout ${TOPIC_CREATE_TIMEOUT} sudo docker exec ${container} /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists --topic ${topic} --partitions ${PARTITIONS} --replication-factor ${REPLICATION_FACTOR} >/dev/null 2>&1"; then
      echo "  ensured ${topic}"
    else
      echo "  failed ${topic}" >&2
      failures=$((failures + 1))
    fi
  done

  [[ "$failures" -eq 0 ]]
}

list_topics_on_node() {
  local node="$1"
  local container="$2"

  echo "$node (${container})"
  run_vm "$node" "sudo docker exec ${container} /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | sort"
}

for_each_kafka_node() {
  local callback="$1"
  local node
  local container

  for node in "${NODES[@]}"; do
    container="$(container_name "$node" 'kafka-')"
    [[ -z "$container" ]] && continue
    "$callback" "$node" "$container"
  done
}

case "$MODE" in
  ensure)
    for_each_kafka_node ensure_topics_on_node
    ;;
  list)
    for_each_kafka_node list_topics_on_node
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
