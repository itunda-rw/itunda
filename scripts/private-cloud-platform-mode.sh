#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/private-cloud-lib.sh"

MODE="${1:-status}"

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-platform-mode.sh <command>

This repo's two Multipass VMs (8GB host RAM total) cannot run the full platform stack
continuously and stay stable -- see docs/PRIVATE_CLOUD_BLUEPRINT.md's "Known hardware ceiling"
section. This script switches between two postures:

  full   Everything running: Istio, Argo Rollouts controller, Prometheus/Grafana, the registry.
         Use this only while actively rehearsing one of those specific capabilities (a canary
         rollout, a dashboard check, a fresh image push), then switch back to lean.

  lean   Only the always-on core: the kubeadm control plane, MySQL, Redis, Kafka + MM2 mirroring,
         and the four app services (api-gateway, backend, ledger-service, payment-service). This
         is the actual Toss-pattern thing being rehearsed -- two independent sites with real
         cross-site data replication -- kept running continuously. Everything else is scaled to
         zero to free RAM/CPU headroom. This is the default posture.

Commands:
  lean     Scale platform extras (Istio, Argo Rollouts, Prometheus/Grafana, registry) to 0.
  full     Scale platform extras back to their normal replica counts.
  status   Show current replica counts for the toggled deployments.
EOF
}

# deployment namespace:name:full-replicas
TOGGLE_TARGETS=(
  "istio-system:istiod:1"
  "istio-system:istio-ingressgateway:1"
  "argo-rollouts:argo-rollouts:1"
  "monitoring:prometheus:1"
  "monitoring:grafana:1"
  "registry-system:itunda-registry:1"
)

scale_target() {
  local spec="$1"
  local replicas="$2"
  local namespace
  local name

  namespace="$(cut -d: -f1 <<<"$spec")"
  name="$(cut -d: -f2 <<<"$spec")"

  if ! cluster_kubectl "get deployment ${name} -n ${namespace} >/dev/null 2>&1"; then
    echo "  skip ${namespace}/${name} (not installed)"
    return 0
  fi

  cluster_kubectl "scale deployment ${name} -n ${namespace} --replicas=${replicas}" >/dev/null
  echo "  ${namespace}/${name} -> ${replicas}"
}

run_lean() {
  echo "Scaling platform extras to 0 (lean mode). Core data plane and app services are untouched."
  for spec in "${TOGGLE_TARGETS[@]}"; do
    scale_target "$spec" 0
  done
}

run_full() {
  echo "Scaling platform extras back up (full mode)."
  for spec in "${TOGGLE_TARGETS[@]}"; do
    local_replicas="$(cut -d: -f3 <<<"$spec")"
    scale_target "$spec" "$local_replicas"
  done
}

run_status() {
  echo "Platform extras (toggled by lean/full):"
  for spec in "${TOGGLE_TARGETS[@]}"; do
    local namespace
    local name

    namespace="$(cut -d: -f1 <<<"$spec")"
    name="$(cut -d: -f2 <<<"$spec")"

    if ! cluster_kubectl "get deployment ${name} -n ${namespace} >/dev/null 2>&1"; then
      echo "  ${namespace}/${name}: not installed"
      continue
    fi

    cluster_kubectl "get deployment ${name} -n ${namespace} -o jsonpath='{.metadata.namespace}/{.metadata.name} desired={.spec.replicas} available={.status.availableReplicas}{\"\\n\"}'"
  done

  echo
  echo "Always-on core (never toggled by this script):"
  echo "  kube-system (control plane), mysql-a/mysql-b, redis-a/redis-b, kafka-a/kafka-b,"
  echo "  kafka-replication/itunda-mirrormaker2, itunda/{api-gateway,backend,ledger-service,payment-service}"
}

case "$MODE" in
  lean)
    run_lean
    ;;
  full)
    run_full
    ;;
  status)
    run_status
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
