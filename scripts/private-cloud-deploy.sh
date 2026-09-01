#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/private-cloud-lib.sh"

MODE="${1:-help}"

app_workload_kind() {
  local name="$1"

  if cluster_kubectl "-n itunda get rollout ${name} --ignore-not-found -o name" 2>/dev/null | grep -q .; then
    printf '%s\n' "rollout"
  else
    printf '%s\n' "deployment"
  fi
}

app_workload_ref() {
  local name="$1"

  printf '%s/%s\n' "$(app_workload_kind "$name")" "$name"
}

cluster_argo_rollouts() {
  local args="$*"

  # kubectl plugins require the plugin name before --kubeconfig, unlike ordinary
  # kubectl commands wrapped by cluster_kubectl().
  run_vm "$PRIMARY_NODE" "sudo kubectl argo rollouts ${args} --kubeconfig /etc/kubernetes/admin.conf"
}

verify_runtime_topology() {
  if [[ "${ITUNDA_PRIVATE_CLOUD_ALLOW_UNSAFE_TOPOLOGY:-0}" == "1" ]]; then
    echo "Skipping topology verification because ITUNDA_PRIVATE_CLOUD_ALLOW_UNSAFE_TOPOLOGY=1"
    return 0
  fi

  bash "$ROOT_DIR/scripts/private-cloud-audit.sh" verify
}

patch_image_pull_policy() {
  local workload="$1"
  local container="$2"

  cluster_kubectl "-n itunda patch ${workload} --type merge -p '{\"spec\":{\"template\":{\"spec\":{\"containers\":[{\"name\":\"${container}\",\"imagePullPolicy\":\"IfNotPresent\"}]}}}}'"
}

override_images_if_set() {
  [[ -n "${ITUNDA_BACKEND_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image $(app_workload_ref backend) backend=${ITUNDA_BACKEND_IMAGE}"
  # card-service added 2026-09-01 -- the first independently-deployable itunda
  # product (see docs/ARCHITECTURE.md). Setting only this image never touches
  # backend's own Deployment/pods.
  [[ -n "${ITUNDA_CARD_SERVICE_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image $(app_workload_ref card-service) card-service=${ITUNDA_CARD_SERVICE_IMAGE}"
  # insurance-service added 2026-09-01 -- the second independently-deployable
  # itunda product (see docs/ARCHITECTURE.md). Setting only this image never
  # touches backend's or card-service's own Deployment/pods.
  [[ -n "${ITUNDA_INSURANCE_SERVICE_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image $(app_workload_ref insurance-service) insurance-service=${ITUNDA_INSURANCE_SERVICE_IMAGE}"
  # agents-service added 2026-09-01 -- the third independently-deployable itunda
  # product (see docs/ARCHITECTURE.md).
  [[ -n "${ITUNDA_AGENTS_SERVICE_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image $(app_workload_ref agents-service) agents-service=${ITUNDA_AGENTS_SERVICE_IMAGE}"
  [[ -n "${ITUNDA_API_GATEWAY_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image $(app_workload_ref api-gateway) api-gateway=${ITUNDA_API_GATEWAY_IMAGE}"
  [[ -n "${ITUNDA_LEDGER_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image $(app_workload_ref ledger-service) ledger-service=${ITUNDA_LEDGER_IMAGE}"
  [[ -n "${ITUNDA_PAYMENT_IMAGE:-}" ]] && cluster_kubectl "-n itunda set image $(app_workload_ref payment-service) payment-service=${ITUNDA_PAYMENT_IMAGE}"
}

apply_image_pull_secret_if_set() {
  local workload="$1"
  local patch

  if [[ -n "${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME:-}" ]]; then
    patch="{\"spec\":{\"template\":{\"spec\":{\"imagePullSecrets\":[{\"name\":\"${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME}\"}]}}}}"
  else
    patch='{"spec":{"template":{"spec":{"imagePullSecrets":null}}}}'
  fi

  cluster_kubectl "-n itunda patch ${workload} --type merge -p $(shell_quote "$patch")"
}

wait_for_rollouts() {
  local workload
  local app
  local namespace

  for app in backend card-service insurance-service agents-service api-gateway ledger-service payment-service; do
    namespace="itunda"
    workload="$(app_workload_ref "$app")"
    if [[ "$workload" == rollout/* ]]; then
      cluster_argo_rollouts "status ${app} -n ${namespace} --timeout=180s" || {
        echo
        echo "Rollout failed for ${namespace}/${app}. Current pods:"
        cluster_kubectl "-n ${namespace} get pods -o wide"
        echo
        cluster_kubectl "-n ${namespace} describe ${workload}"
        return 1
      }
      continue
    fi
    if ! cluster_kubectl "-n ${namespace} rollout status ${workload} --timeout=180s"; then
      echo
      echo "Rollout failed for ${namespace}/${app}. Current pods:"
      cluster_kubectl "-n ${namespace} get pods -o wide"
      echo
      cluster_kubectl "-n ${namespace} describe ${workload}"
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
  cluster_kubectl "-n itunda get rollout,deploy,po,svc"
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
  if cluster_kubectl "-n itunda get rollout api-gateway --ignore-not-found -o name" 2>/dev/null | grep -q .; then
    echo "Refusing legacy deployment manifest apply: this cluster uses Argo Rollouts. Run scripts/private-cloud-platform.sh deploy-progressive instead." >&2
    exit 1
  fi
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

  patch_image_pull_policy "$(app_workload_ref backend)" backend
  patch_image_pull_policy "$(app_workload_ref card-service)" card-service
  patch_image_pull_policy "$(app_workload_ref insurance-service)" insurance-service
  patch_image_pull_policy "$(app_workload_ref agents-service)" agents-service
  patch_image_pull_policy "$(app_workload_ref api-gateway)" api-gateway
  patch_image_pull_policy "$(app_workload_ref ledger-service)" ledger-service
  patch_image_pull_policy "$(app_workload_ref payment-service)" payment-service
  override_images_if_set
  apply_image_pull_secret_if_set "$(app_workload_ref backend)"
  apply_image_pull_secret_if_set "$(app_workload_ref card-service)"
  apply_image_pull_secret_if_set "$(app_workload_ref insurance-service)"
  apply_image_pull_secret_if_set "$(app_workload_ref agents-service)"
  apply_image_pull_secret_if_set "$(app_workload_ref api-gateway)"
  apply_image_pull_secret_if_set "$(app_workload_ref ledger-service)"
  apply_image_pull_secret_if_set "$(app_workload_ref payment-service)"

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
