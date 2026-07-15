#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/private-cloud-lib.sh"

MODE="${1:-help}"
ISTIO_NAMESPACE="${ITUNDA_PRIVATE_CLOUD_ISTIO_NAMESPACE:-istio-system}"
ROLLOUTS_NAMESPACE="${ITUNDA_PRIVATE_CLOUD_ROLLOUTS_NAMESPACE:-argo-rollouts}"
KAFKA_REPLICATION_NAMESPACE="${ITUNDA_PRIVATE_CLOUD_KAFKA_REPLICATION_NAMESPACE:-kafka-replication}"
ISTIO_NODEPORT_HTTP="${ITUNDA_PRIVATE_CLOUD_ISTIO_HTTP_NODEPORT:-30082}"
PRIVATE_CLOUD_APP_REPLICAS="${ITUNDA_PRIVATE_CLOUD_APP_REPLICAS:-1}"
PRIVATE_CLOUD_ROLLOUT_PROFILE="${ITUNDA_PRIVATE_CLOUD_ROLLOUT_PROFILE:-auto}"
PRIVATE_CLOUD_AUTO_PROMOTE_ROLLOUTS="${ITUNDA_PRIVATE_CLOUD_AUTO_PROMOTE_ROLLOUTS:-0}"
PRIVATE_CLOUD_ENABLE_HPA="${ITUNDA_PRIVATE_CLOUD_ENABLE_HPA:-0}"
PRIVATE_CLOUD_ENSURE_TOPICS_ON_DEPLOY="${ITUNDA_PRIVATE_CLOUD_ENSURE_TOPICS_ON_DEPLOY:-0}"
PRIVATE_CLOUD_QUIESCE_PRIMARY_STATEFUL="${ITUNDA_PRIVATE_CLOUD_QUIESCE_PRIMARY_STATEFUL:-1}"
PRIVATE_CLOUD_PRESERVE_PRIMARY_HA_STATEFUL="${ITUNDA_PRIVATE_CLOUD_PRESERVE_PRIMARY_HA_STATEFUL:-1}"
ALLOW_WORKLOADS_ON_CONTROL_PLANE="${ITUNDA_PRIVATE_CLOUD_ALLOW_WORKLOADS_ON_CONTROL_PLANE:-0}"
METRICS_SERVER_MANIFEST_URL="${ITUNDA_PRIVATE_CLOUD_METRICS_SERVER_MANIFEST_URL:-https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml}"
METRICS_SERVER_PREFERRED_ADDRESS_TYPES="${ITUNDA_PRIVATE_CLOUD_METRICS_SERVER_ADDRESS_TYPES:-InternalIP,ExternalIP,Hostname}"
METRICS_SERVER_INSECURE_TLS="${ITUNDA_PRIVATE_CLOUD_METRICS_SERVER_INSECURE_TLS:-1}"
MM2_TOPICS_PATTERN="${ITUNDA_PRIVATE_CLOUD_MM2_TOPICS:-ledger\\..*|payment\\..*|transfer\\..*}"
MM2_TOPICS_EXCLUDE="${ITUNDA_PRIVATE_CLOUD_MM2_TOPICS_EXCLUDE:-mm2.*\\.internal,.*\\.replica,__.*}"
MM2_GROUPS_PATTERN="${ITUNDA_PRIVATE_CLOUD_MM2_GROUPS:-itunda-.*}"
MM2_GROUPS_EXCLUDE="${ITUNDA_PRIVATE_CLOUD_MM2_GROUPS_EXCLUDE:-console-consumer-.*,connect-.*,__.*}"
MM2_REFRESH_TOPICS_INTERVAL_SECONDS="${ITUNDA_PRIVATE_CLOUD_MM2_REFRESH_TOPICS_INTERVAL_SECONDS:-30}"
MM2_REFRESH_GROUPS_INTERVAL_SECONDS="${ITUNDA_PRIVATE_CLOUD_MM2_REFRESH_GROUPS_INTERVAL_SECONDS:-30}"
MM2_CHECKPOINT_INTERVAL_SECONDS="${ITUNDA_PRIVATE_CLOUD_MM2_CHECKPOINT_INTERVAL_SECONDS:-10}"
MM2_SYNC_GROUP_OFFSETS_INTERVAL_SECONDS="${ITUNDA_PRIVATE_CLOUD_MM2_SYNC_GROUP_OFFSETS_INTERVAL_SECONDS:-10}"
MM2_OFFSET_SYNCS_TOPIC_LOCATION="${ITUNDA_PRIVATE_CLOUD_MM2_OFFSET_SYNCS_TOPIC_LOCATION:-source}"
MM2_EPHEMERAL_STORAGE_REQUEST="${ITUNDA_PRIVATE_CLOUD_MM2_EPHEMERAL_STORAGE_REQUEST:-256Mi}"
MM2_EPHEMERAL_STORAGE_LIMIT="${ITUNDA_PRIVATE_CLOUD_MM2_EPHEMERAL_STORAGE_LIMIT:-1Gi}"

patch_workload_image() {
  local resource="$1"
  local container_index="${2:-0}"
  local image="$3"
  local patch

  patch="[{\"op\":\"replace\",\"path\":\"/spec/template/spec/containers/${container_index}/image\",\"value\":\"${image}\"}]"
  cluster_kubectl "-n itunda patch ${resource} --type json -p $(shell_quote "$patch")"
}

apply_image_pull_secret_if_set() {
  local resource="$1"
  local patch

  if [[ -n "${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME:-}" ]]; then
    patch="{\"spec\":{\"template\":{\"spec\":{\"imagePullSecrets\":[{\"name\":\"${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME}\"}]}}}}"
  else
    patch='{"spec":{"template":{"spec":{"imagePullSecrets":null}}}}'
  fi

  cluster_kubectl "-n itunda patch ${resource} --type merge -p $(shell_quote "$patch")"
}

override_rollout_images_if_set() {
  [[ -n "${ITUNDA_API_GATEWAY_IMAGE:-}" ]] && patch_workload_image "rollout api-gateway" 0 "${ITUNDA_API_GATEWAY_IMAGE}"
  [[ -n "${ITUNDA_BACKEND_IMAGE:-}" ]] && patch_workload_image "rollout backend" 0 "${ITUNDA_BACKEND_IMAGE}"
  [[ -n "${ITUNDA_LEDGER_IMAGE:-}" ]] && patch_workload_image "rollout ledger-service" 0 "${ITUNDA_LEDGER_IMAGE}"
  [[ -n "${ITUNDA_PAYMENT_IMAGE:-}" ]] && patch_workload_image "rollout payment-service" 0 "${ITUNDA_PAYMENT_IMAGE}"
}

require_cluster() {
  require_cmd multipass

  if ! cluster_is_active "$PRIMARY_NODE"; then
    echo "Kubernetes is not active on ${PRIMARY_NODE}. Run scripts/private-cloud-bootstrap.sh bootstrap first." >&2
    exit 1
  fi

  require_kubeadm_cluster
}

verify_runtime_topology() {
  if [[ "${ITUNDA_PRIVATE_CLOUD_ALLOW_UNSAFE_TOPOLOGY:-0}" == "1" ]]; then
    echo "Skipping topology verification because ITUNDA_PRIVATE_CLOUD_ALLOW_UNSAFE_TOPOLOGY=1"
    return 0
  fi

  bash "$ROOT_DIR/scripts/private-cloud-audit.sh" verify
}

ensure_runtime_databases() {
  local writer_node
  local mysql_container

  ensure_runtime_env

  if ! writer_node="$(private_cloud_db_writer_node 2>/dev/null)"; then
    echo "Skipping in-cluster MySQL bootstrap because DB_HOST=${DB_HOST} is external to the private-cloud nodes."
    return 0
  fi

  mysql_container="$(mysql_container_on_node "$writer_node" || true)"
  if [[ -z "$mysql_container" ]]; then
    echo "Skipping in-cluster MySQL bootstrap because ${writer_node} has no mysql-* container."
    return 0
  fi

  ensure_private_cloud_mysql_databases "$writer_node"
}

enforce_control_plane_scheduling() {
  if [[ "${ALLOW_WORKLOADS_ON_CONTROL_PLANE}" == "1" ]]; then
    cluster_kubectl "taint nodes ${PRIMARY_NODE} node-role.kubernetes.io/control-plane:NoSchedule- >/dev/null 2>&1 || true"
    return 0
  fi

  cluster_kubectl "taint nodes ${PRIMARY_NODE} node-role.kubernetes.io/control-plane=:NoSchedule --overwrite >/dev/null"
}

ready_worker_count() {
  cluster_kubectl "get nodes -l '!node-role.kubernetes.io/control-plane' --no-headers 2>/dev/null | awk '\$2 == \"Ready\" { count++ } END { print count + 0 }'"
}

resolve_rollout_profile() {
  local profile="${PRIVATE_CLOUD_ROLLOUT_PROFILE}"
  local worker_count

  case "$profile" in
    auto)
      worker_count="$(ready_worker_count)"
      if [[ "${PRIVATE_CLOUD_APP_REPLICAS}" -le 1 || "${worker_count}" -lt 2 ]]; then
        echo rolling
      else
        echo canary
      fi
      ;;
    canary|rolling)
      echo "$profile"
      ;;
    *)
      echo "Unsupported rollout profile: ${profile}" >&2
      exit 1
      ;;
  esac
}

set_rollout_strategy() {
  local rollout="$1"
  local profile="$2"
  local patch

  case "$profile" in
    rolling)
      patch='[{"op":"replace","path":"/spec/strategy/canary","value":{"maxSurge":0,"maxUnavailable":1}}]'
      ;;
    canary)
      return 0
      ;;
    *)
      echo "Unsupported rollout profile: ${profile}" >&2
      exit 1
      ;;
  esac

  cluster_kubectl "-n itunda patch rollout ${rollout} --type json -p $(shell_quote "$patch") >/dev/null"
}

configure_rollout_strategies() {
  local profile
  local rollout

  profile="$(resolve_rollout_profile)"

  for rollout in api-gateway backend ledger-service payment-service; do
    set_rollout_strategy "$rollout" "$profile"
  done

  echo "$profile"
}

apply_low_capacity_resource_profile() {
  local profile="$1"
  local patch

  [[ "$profile" == "rolling" ]] || return 0

  patch='[{"op":"replace","path":"/spec/template/spec/containers/0/resources","value":{"requests":{"cpu":"100m","memory":"128Mi"},"limits":{"cpu":"500m","memory":"512Mi"}}}]'
  cluster_kubectl "-n itunda patch rollout backend --type json -p $(shell_quote "$patch") >/dev/null"
}

delete_progressive_hpas() {
  cluster_kubectl "-n itunda delete hpa api-gateway backend ledger-service payment-service --ignore-not-found >/dev/null 2>&1 || true"
}

preserve_primary_ha_stateful_services() {
  local container

  [[ "${PRIVATE_CLOUD_PRESERVE_PRIMARY_HA_STATEFUL}" == "1" ]] || return 0

  # Keep the dc-a MySQL replica and dc-a Kafka broker alive. They are part of the honest
  # failover-domain baseline for this rehearsal, not disposable control-plane noise.
  for container in \
    "$(container_name_any_state "$PRIMARY_NODE" 'mysql-')" \
    "$(container_name_any_state "$PRIMARY_NODE" 'kafka-')"; do
    [[ -z "$container" ]] && continue
    ensure_container_running "$PRIMARY_NODE" "$container"
  done
}

quiesce_primary_stateful_services() {
  local primary_ip
  local redis_container

  [[ "${PRIVATE_CLOUD_QUIESCE_PRIMARY_STATEFUL}" == "1" ]] || return 0

  if ! ensure_runtime_env; then
    echo "Skipping primary-node stateful quiesce because the audited runtime endpoints are not currently resolvable." >&2
    return 0
  fi

  primary_ip="$(node_ip "$PRIMARY_NODE")"

  redis_container="$(container_name "$PRIMARY_NODE" 'redis-')"
  if [[ -n "$redis_container" && "${REDIS_HOST}" != "${primary_ip}" ]]; then
    run_vm "$PRIMARY_NODE" "sudo docker stop '${redis_container}' >/dev/null 2>&1 || true"
  fi
}

stabilize_control_plane() {
  require_cluster
  enforce_control_plane_scheduling

  if [[ "${PRIVATE_CLOUD_ENABLE_HPA}" != "1" ]]; then
    delete_progressive_hpas
  fi

  preserve_primary_ha_stateful_services
  quiesce_primary_stateful_services
}

install_istioctl_on_primary() {
  local version_env=""

  if run_vm "$PRIMARY_NODE" "command -v istioctl >/dev/null 2>&1"; then
    return 0
  fi

  if [[ -n "${ISTIO_VERSION:-}" ]]; then
    version_env="export ISTIO_VERSION='${ISTIO_VERSION}';"
  fi

  run_vm "$PRIMARY_NODE" "
    set -euo pipefail
    cd /tmp
    rm -rf istio-*
    export TARGET_ARCH=arm64
    ${version_env}
    curl -L https://istio.io/downloadIstio | sh -
    dir=\$(find /tmp -maxdepth 1 -type d -name 'istio-*' | sort | tail -n 1)
    sudo install -m 0755 \"\${dir}/bin/istioctl\" /usr/local/bin/istioctl
    rm -rf \"\${dir}\"
  "
}

ensure_rollouts_cli_on_primary() {
  local arch

  arch="$(node_arch "$PRIMARY_NODE")"
  run_vm "$PRIMARY_NODE" "
    set -euo pipefail
    if command -v kubectl-argo-rollouts >/dev/null 2>&1; then
      exit 0
    fi

    tmpdir=\$(mktemp -d)
    curl -fsSL -o \"\$tmpdir/kubectl-argo-rollouts\" \"https://github.com/argoproj/argo-rollouts/releases/latest/download/kubectl-argo-rollouts-linux-${arch}\"
    chmod +x \"\$tmpdir/kubectl-argo-rollouts\"
    sudo install -m 0755 \"\$tmpdir/kubectl-argo-rollouts\" /usr/local/bin/kubectl-argo-rollouts
    rm -rf \"\$tmpdir\"
  "
}

wait_for_deployment() {
  local namespace="$1"
  local deployment="$2"

  cluster_kubectl "-n ${namespace} rollout status deployment/${deployment} --timeout=240s"
}

wait_for_daemonset() {
  local namespace="$1"
  local daemonset="$2"

  cluster_kubectl "-n ${namespace} rollout status daemonset/${daemonset} --timeout=240s"
}

render_mirrormaker_manifest() {
  local manifest_path="$1"
  local dc_a_bootstrap
  local dc_b_bootstrap
  local replication_factor="${ITUNDA_PRIVATE_CLOUD_MM2_REPLICATION_FACTOR:-1}"
  local tasks_max="${ITUNDA_PRIVATE_CLOUD_MM2_TASKS_MAX:-4}"
  local heap_opts="${ITUNDA_PRIVATE_CLOUD_MM2_HEAP:--Xms256m -Xmx256m}"

  if [[ "${#NODES[@]}" -lt 2 ]]; then
    echo "Need at least 2 private-cloud nodes for the active-active Kafka scaffold." >&2
    exit 1
  fi

  dc_a_bootstrap="$(run_vm "${NODES[0]}" "hostname -I | awk '{print \$1}'" | tr -d '\r'):9094"
  dc_b_bootstrap="$(run_vm "${NODES[1]}" "hostname -I | awk '{print \$1}'" | tr -d '\r'):9094"

  cat >"$manifest_path" <<EOF
apiVersion: v1
kind: Namespace
metadata:
  name: ${KAFKA_REPLICATION_NAMESPACE}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: itunda-mirrormaker2
  namespace: ${KAFKA_REPLICATION_NAMESPACE}
data:
  connect-mirror-maker.properties: |
    clusters = dc-a, dc-b
    dc-a.bootstrap.servers = ${dc_a_bootstrap}
    dc-b.bootstrap.servers = ${dc_b_bootstrap}

    dc-a->dc-b.enabled = true
    dc-b->dc-a.enabled = true

    topics = ${MM2_TOPICS_PATTERN}
    topics.exclude = ${MM2_TOPICS_EXCLUDE}
    groups = ${MM2_GROUPS_PATTERN}
    groups.exclude = ${MM2_GROUPS_EXCLUDE}

    refresh.topics.enabled = true
    refresh.topics.interval.seconds = ${MM2_REFRESH_TOPICS_INTERVAL_SECONDS}
    refresh.groups.enabled = true
    refresh.groups.interval.seconds = ${MM2_REFRESH_GROUPS_INTERVAL_SECONDS}
    emit.heartbeats.enabled = true
    emit.offset-syncs.enabled = true
    sync.group.offsets.enabled = true
    sync.group.offsets.interval.seconds = ${MM2_SYNC_GROUP_OFFSETS_INTERVAL_SECONDS}
    emit.checkpoints.enabled = true
    emit.checkpoints.interval.seconds = ${MM2_CHECKPOINT_INTERVAL_SECONDS}
    offset-syncs.topic.location = ${MM2_OFFSET_SYNCS_TOPIC_LOCATION}

    tasks.max = ${tasks_max}
    replication.factor = ${replication_factor}
    config.storage.topic = itunda-mm2-configs
    offset.storage.topic = itunda-mm2-offsets
    status.storage.topic = itunda-mm2-status
    config.storage.replication.factor = ${replication_factor}
    offset.storage.replication.factor = ${replication_factor}
    status.storage.replication.factor = ${replication_factor}
    checkpoints.topic.replication.factor = ${replication_factor}
    heartbeats.topic.replication.factor = ${replication_factor}
    offset-syncs.topic.replication.factor = ${replication_factor}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: itunda-mirrormaker2
  namespace: ${KAFKA_REPLICATION_NAMESPACE}
spec:
  replicas: 1
  strategy:
    type: Recreate
  selector:
    matchLabels:
      app: itunda-mirrormaker2
  template:
    metadata:
      labels:
        app: itunda-mirrormaker2
    spec:
      containers:
      - name: mm2
        image: apache/kafka:4.2.0
        imagePullPolicy: IfNotPresent
        env:
        - name: KAFKA_HEAP_OPTS
          value: "${heap_opts}"
        - name: KAFKA_OPTS
          value: "-Dplugin.discovery=service_load"
        command: ["/bin/bash", "-lc"]
        args:
        - /opt/kafka/bin/connect-mirror-maker.sh /etc/kafka/mm2/connect-mirror-maker.properties
        ports:
        - containerPort: 8083
        resources:
          requests:
            cpu: "250m"
            memory: "256Mi"
            ephemeral-storage: "${MM2_EPHEMERAL_STORAGE_REQUEST}"
          limits:
            cpu: "1000m"
            memory: "768Mi"
            ephemeral-storage: "${MM2_EPHEMERAL_STORAGE_LIMIT}"
        volumeMounts:
        - name: mm2-config
          mountPath: /etc/kafka/mm2
      volumes:
      - name: mm2-config
        configMap:
          name: itunda-mirrormaker2
---
apiVersion: v1
kind: Service
metadata:
  name: itunda-mirrormaker2
  namespace: ${KAFKA_REPLICATION_NAMESPACE}
spec:
  selector:
    app: itunda-mirrormaker2
  ports:
  - name: rest
    port: 8083
    targetPort: 8083
  type: ClusterIP
EOF
}

install_mesh() {
  require_cluster
  mount_repo_on_primary
  install_istioctl_on_primary

  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/base/namespace.yaml"
  run_vm "$PRIMARY_NODE" "sudo env KUBECONFIG=$(cluster_kubeconfig_path) istioctl install -f ${DEPLOY_REPO_PATH}/infra/k8s/private-cloud/istio-operator.yaml -y"
  cluster_kubectl "label namespace itunda istio-injection=enabled --overwrite"
  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/private-cloud/istio-nodeports.yaml"

  wait_for_deployment "$ISTIO_NAMESPACE" istiod
  wait_for_deployment "$ISTIO_NAMESPACE" istio-ingressgateway

  cat <<EOF
Istio installed.
- Ingress NodePort: http://$(server_ip):${ISTIO_NODEPORT_HTTP}
- Example check: curl -H 'Host: api.itunda.internal' http://$(server_ip):${ISTIO_NODEPORT_HTTP}/health
EOF
}

install_rollouts() {
  require_cluster

  if ! cluster_kubectl "get namespace ${ROLLOUTS_NAMESPACE} >/dev/null 2>&1"; then
    cluster_kubectl "create namespace ${ROLLOUTS_NAMESPACE}"
  fi
  cluster_kubectl "apply -n ${ROLLOUTS_NAMESPACE} -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml"
  wait_for_deployment "$ROLLOUTS_NAMESPACE" argo-rollouts
}

render_metrics_server_patch() {
  local insecure_arg=""

  if [[ "${METRICS_SERVER_INSECURE_TLS}" == "1" ]]; then
    insecure_arg=',"--kubelet-insecure-tls"'
  fi

  cat <<EOF
[{"op":"replace","path":"/spec/template/spec/containers/0/args","value":["--cert-dir=/tmp","--secure-port=10250","--kubelet-preferred-address-types=${METRICS_SERVER_PREFERRED_ADDRESS_TYPES}","--kubelet-use-node-status-port","--metric-resolution=15s"${insecure_arg}]}]
EOF
}

wait_for_metrics_api() {
  local attempts=60
  local i

  for ((i = 1; i <= attempts; i++)); do
    if cluster_kubectl "top nodes >/dev/null 2>&1"; then
      return 0
    fi
    sleep 5
  done

  echo "Metrics API did not become ready." >&2
  cluster_kubectl "-n kube-system get deploy,po metrics-server" || true
  cluster_kubectl "-n kube-system logs deploy/metrics-server --tail=80" || true
  return 1
}

install_metrics_server() {
  local patch

  require_cluster

  cluster_kubectl "apply -f ${METRICS_SERVER_MANIFEST_URL}"
  patch="$(render_metrics_server_patch)"
  cluster_kubectl "-n kube-system patch deployment metrics-server --type json -p $(shell_quote "$patch")"
  wait_for_deployment kube-system metrics-server
  wait_for_metrics_api
}

install_observability() {
  require_cluster
  mount_repo_on_primary

  bash "$ROOT_DIR/scripts/private-cloud-observability.sh" install-vm-metrics
  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/monitoring"
  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/private-cloud/observability-nodeports.yaml"
  cluster_kubectl "-n monitoring rollout restart deployment/prometheus deployment/grafana >/dev/null"
  wait_for_deployment monitoring prometheus
  wait_for_deployment monitoring grafana
  wait_for_daemonset monitoring node-exporter
}

scale_private_cloud_rollouts() {
  local rollout

  for rollout in api-gateway backend ledger-service payment-service; do
    cluster_kubectl "-n itunda patch rollout.argoproj.io/${rollout} --type merge -p '{\"spec\":{\"replicas\":${PRIVATE_CLOUD_APP_REPLICAS}}}' >/dev/null"
    apply_image_pull_secret_if_set "rollout ${rollout}"
  done
}

promote_rollouts() {
  local promote_flag="${1:---full}"
  local rollout

  require_cluster
  ensure_rollouts_cli_on_primary

  for rollout in api-gateway backend ledger-service payment-service; do
    run_vm "$PRIMARY_NODE" "
      set -euo pipefail
      sudo env KUBECONFIG='$(cluster_kubeconfig_path)' kubectl-argo-rollouts promote '${rollout}' -n itunda ${promote_flag}
    "
  done
}

deploy_progressive() {
  local manifest
  local rollout_profile

  require_cluster
  verify_runtime_topology
  ensure_runtime_env
  ensure_runtime_databases
  stabilize_control_plane
  mount_repo_on_primary

  if ! cluster_kubectl "get crd rollouts.argoproj.io >/dev/null 2>&1"; then
    echo "Argo Rollouts CRD is missing. Run scripts/private-cloud-platform.sh install-rollouts first." >&2
    exit 1
  fi

  if ! cluster_kubectl "get deployment istiod -n ${ISTIO_NAMESPACE} >/dev/null 2>&1"; then
    echo "Istio is not installed. Run scripts/private-cloud-platform.sh install-mesh first." >&2
    exit 1
  fi

  if [[ "${PRIVATE_CLOUD_ENSURE_TOPICS_ON_DEPLOY}" == "1" ]]; then
    bash "$ROOT_DIR/scripts/private-cloud-kafka-topics.sh" ensure
  fi

  if ! cluster_kubectl "top nodes >/dev/null 2>&1"; then
    install_metrics_server
  fi

  manifest="$(mktemp)"
  render_runtime_config_manifest "$manifest"
  multipass transfer "$manifest" "${PRIMARY_NODE}:/tmp/itunda-progressive-runtime-config.yaml"
  rm -f "$manifest"

  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/base/namespace.yaml"
  cluster_kubectl "apply -f /tmp/itunda-progressive-runtime-config.yaml"
  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/production/policies.yaml"
  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/production/pdbs.yaml"
  cluster_kubectl "-n itunda delete deployment api-gateway backend ledger-service payment-service --ignore-not-found"
  cluster_kubectl "apply -k ${DEPLOY_REPO_PATH}/infra/k8s/progressive-delivery"
  if [[ "${PRIVATE_CLOUD_ENABLE_HPA}" != "1" ]]; then
    delete_progressive_hpas
  fi
  rollout_profile="$(configure_rollout_strategies)"
  apply_low_capacity_resource_profile "$rollout_profile"
  override_rollout_images_if_set
  scale_private_cloud_rollouts
  if [[ "${PRIVATE_CLOUD_AUTO_PROMOTE_ROLLOUTS}" == "1" ]]; then
    promote_rollouts --full
  fi
  if cluster_kubectl "get namespace monitoring >/dev/null 2>&1"; then
    cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/monitoring"
    cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/private-cloud/observability-nodeports.yaml"
  else
    install_observability
  fi
  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/infra/k8s/private-cloud/istio-nodeports.yaml"

  cat <<EOF
Progressive-delivery resources applied.
- Ingress through Istio: curl -H 'Host: api.itunda.internal' http://$(server_ip):${ISTIO_NODEPORT_HTTP}/health
- Private-cloud rollout replicas: ${PRIVATE_CLOUD_APP_REPLICAS}
- Effective rollout profile: ${rollout_profile}
- Pull secret: ${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME:-none}
- Rollouts: kubectl --kubeconfig <kubeconfig> -n itunda get rollout
EOF
}

deploy_kafka_replication() {
  local manifest

  require_cluster
  verify_runtime_topology

  manifest="$(mktemp)"
  render_mirrormaker_manifest "$manifest"
  multipass transfer "$manifest" "${PRIMARY_NODE}:/tmp/itunda-mirrormaker2.yaml"
  rm -f "$manifest"

  cluster_kubectl "apply -f /tmp/itunda-mirrormaker2.yaml"
  wait_for_deployment "$KAFKA_REPLICATION_NAMESPACE" itunda-mirrormaker2
}

show_status() {
  require_cluster

  echo "Istio"
  if cluster_kubectl "get namespace ${ISTIO_NAMESPACE} >/dev/null 2>&1"; then
    cluster_kubectl "-n ${ISTIO_NAMESPACE} get deploy,po,svc"
  else
    echo "not installed"
  fi

  echo
  echo "Argo Rollouts"
  if cluster_kubectl "get namespace ${ROLLOUTS_NAMESPACE} >/dev/null 2>&1"; then
    cluster_kubectl "-n ${ROLLOUTS_NAMESPACE} get deploy,po"
  else
    echo "not installed"
  fi

  echo
  echo "Observability"
  if cluster_kubectl "get namespace monitoring >/dev/null 2>&1"; then
    cluster_kubectl "-n monitoring get deploy,daemonset,po,svc"
  else
    echo "not installed"
  fi

  echo
  echo "Kafka Replication"
  if cluster_kubectl "get namespace ${KAFKA_REPLICATION_NAMESPACE} >/dev/null 2>&1"; then
    cluster_kubectl "-n ${KAFKA_REPLICATION_NAMESPACE} get deploy,po,svc,configmap"
  else
    echo "not installed"
  fi

  echo
  echo "Application Rollouts"
  if cluster_kubectl "get crd rollouts.argoproj.io >/dev/null 2>&1"; then
    cluster_kubectl "-n itunda get rollout,svc,virtualservice 2>/dev/null || true"
  else
    echo "rollout CRD not installed"
  fi
}

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-platform.sh <command>

Commands:
  install-mesh         Install low-overhead Istio on the private-cloud Kubernetes cluster
  install-rollouts     Install Argo Rollouts controller
  install-metrics-server Install Metrics Server for HPA/cpu metrics on kubeadm Kubernetes
  install-observability Install Prometheus, Grafana, node exporters, and VM-side service metrics
  install              Install Istio, Argo Rollouts, Metrics Server, and the observability baseline
  deploy-progressive   Apply progressive-delivery app manifests
  deploy-kafka         Apply the live MirrorMaker 2 baseline against the audited Kafka brokers
  stabilize-control-plane Reduce control-plane pressure for low-memory kubeadm rehearsal
  promote-rollouts     Promote the private-cloud app rollouts, defaulting to --full
  status               Show platform components and rollout objects
EOF
}

case "$MODE" in
  install-mesh)
    install_mesh
    ;;
  install-rollouts)
    install_rollouts
    ;;
  install-metrics-server)
    install_metrics_server
    ;;
  install-observability)
    install_observability
    ;;
  install)
    install_mesh
    install_rollouts
    install_metrics_server
    install_observability
    ;;
  deploy-progressive)
    deploy_progressive
    ;;
  deploy-kafka)
    deploy_kafka_replication
    ;;
  stabilize-control-plane)
    stabilize_control_plane
    ;;
  promote-rollouts)
    promote_rollouts "${2:---full}"
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
