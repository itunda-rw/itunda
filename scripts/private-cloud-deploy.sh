#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/private-cloud-lib.sh"

MODE="${1:-help}"

verify_runtime_topology() {
  if [[ "${ITUNDA_PRIVATE_CLOUD_ALLOW_UNSAFE_TOPOLOGY:-0}" == "1" ]]; then
    echo "Skipping topology verification because ITUNDA_PRIVATE_CLOUD_ALLOW_UNSAFE_TOPOLOGY=1"
    return 0
  fi

  bash "$ROOT_DIR/scripts/private-cloud-audit.sh" verify
}

patch_image_pull_policy() {
  local deployment="$1"
  local container="$2"

  cluster_kubectl "-n itunda patch deployment ${deployment} --type merge -p '{\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"${container}\",\"imagePullPolicy\":\"IfNotPresent\"}]}}}}'"
}

override_images_if_set() {
  [[ -n "${ITUNDA_BACKEND_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image deployment/backend backend=${ITUNDA_BACKEND_IMAGE}"
  [[ -n "${ITUNDA_API_GATEWAY_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image deployment/api-gateway api-gateway=${ITUNDA_API_GATEWAY_IMAGE}"
  [[ -n "${ITUNDA_LEDGER_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image deployment/ledger-service ledger-service=${ITUNDA_LEDGER_IMAGE}"
  [[ -n "${ITUNDA_PAYMENT_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image deployment/payment-service payment-service=${ITUNDA_PAYMENT_IMAGE}"
}

apply_image_pull_secret_if_set() {
  local deployment="$1"
  local patch

  if [[ -n "${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME:-}" ]]; then
    patch="{\"spec\":{\"template\":{\"spec\":{\"imagePullSecrets\":[{\"name\":\"${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME}\"}]}}}}"
  else
    patch='{"spec":{"template":{"spec":{"imagePullSecrets":null}}}}'
  fi

  cluster_kubectl "-n itunda patch deployment ${deployment} --type merge -p $(shell_quote "$patch")"
}

wait_for_rollouts() {
  local deployment
  local namespace

  for deployment in backend api-gateway ledger-service payment-service; do
    namespace="itunda"
    if ! cluster_kubectl "-n ${namespace} rollout status deployment/${deployment} --timeout=180s"; then
      echo
      echo "Rollout failed for ${namespace}/${deployment}. Current pods:"
      cluster_kubectl "-n ${namespace} get pods -o wide"
      echo
      cluster_kubectl "-n ${namespace} describe deployment/${deployment}"
      return 1
    fi
  done

  for deployment in prometheus grafana; do
    namespace="monitoring"
    if ! cluster_kubectl "-n ${namespace} rollout status deployment/${deployment} --timeout=180s"; then
      echo
      echo "Rollout failed for ${namespace}/${deployment}. Current pods:"
      cluster_kubectl "-n ${namespace} get pods -o wide"
      echo
      cluster_kubectl "-n ${namespace} describe deployment/${deployment}"
      return 1
    fi
  done
}

show_status() {
  local distro

  if ! cluster_is_active "$PRIMARY_NODE"; then
    echo "Kubernetes is not active on ${PRIMARY_NODE}. Run scripts/private-cloud-bootstrap.sh bootstrap first." >&2
    exit 1
  fi

  require_kubeadm_cluster
  distro="$(installed_cluster_distro)"
  echo "Cluster"
  echo "distro: ${distro}"
  echo
  echo "Nodes"
  cluster_kubectl "get nodes -o wide"
  echo
  echo "Workloads"
  cluster_kubectl "-n itunda get deploy,po,svc"
  echo
  echo "Monitoring"
  cluster_kubectl "-n monitoring get deploy,po,svc"
}

deploy_private_cloud() {
  local manifest
  local writer_node

  require_cmd multipass
  verify_runtime_topology
  ensure_runtime_env

  if ! cluster_is_active "$PRIMARY_NODE"; then
    echo "Kubernetes is not active on ${PRIMARY_NODE}. Run scripts/private-cloud-bootstrap.sh bootstrap first." >&2
    exit 1
  fi

  require_kubeadm_cluster
  bash "$ROOT_DIR/scripts/private-cloud-kafka-topics.sh" ensure
  if writer_node="$(private_cloud_db_writer_node 2>/dev/null)"; then
    if [[ -n "$(mysql_container_on_node "$writer_node" || true)" ]]; then
      ensure_private_cloud_mysql_databases "$writer_node"
    fi
  fi
  if ! cluster_kubectl "top nodes >/dev/null 2>&1"; then
    bash "$ROOT_DIR/scripts/private-cloud-platform.sh" install-metrics-server
  fi

  mount_repo_on_primary
  manifest="$(mktemp)"
  render_runtime_config_manifest "$manifest"
  multipass transfer "$manifest" "${PRIMARY_NODE}:/tmp/itunda-private-cloud-config.yaml"
  rm -f "$manifest"

  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/base/namespace.yaml"
  cluster_kubectl "apply -f /tmp/itunda-private-cloud-config.yaml"
  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/production"
  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/monitoring"
  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/private-cloud/nodeports.yaml"

  patch_image_pull_policy backend backend
  patch_image_pull_policy api-gateway api-gateway
  patch_image_pull_policy ledger-service ledger-service
  patch_image_pull_policy payment-service payment-service
  override_images_if_set
  apply_image_pull_secret_if_set backend
  apply_image_pull_secret_if_set api-gateway
  apply_image_pull_secret_if_set ledger-service
  apply_image_pull_secret_if_set payment-service

  wait_for_rollouts

  cat <<EOF
Private-cloud services exposed on:
- API gateway: http://$(server_ip):30080/health
- Backend actuator: http://$(server_ip):30081/actuator/health
- Prometheus: http://$(server_ip):30090
- Grafana: http://$(server_ip):30300
- Pull secret: ${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME:-none}
EOF
}

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-deploy.sh <command>

Commands:
  deploy   Apply namespaces, runtime config, app manifests, monitoring, and nodeports
  status   Show current cluster objects in the private cloud
EOF
}

case "$MODE" in
  deploy)
    deploy_private_cloud
    ;;
  status)
    show_status
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
