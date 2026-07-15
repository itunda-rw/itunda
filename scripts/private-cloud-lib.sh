#!/usr/bin/env bash

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/private-cloud-inventory.sh"
NODES=($(inventory_nodes))
PRIMARY_NODE="${NODES[0]}"
REMOTE_REPO_PATH="${ITUNDA_PRIVATE_CLOUD_REMOTE_REPO_PATH:-/workspace/itunda}"
STAGED_REPO_PATH="${ITUNDA_PRIVATE_CLOUD_STAGED_REPO_PATH:-/tmp/itunda-staged-repo}"
DEPLOY_REPO_PATH="$REMOTE_REPO_PATH"
DEFAULT_KUBERNETES_DISTRO="${ITUNDA_PRIVATE_CLOUD_K8S_DISTRO:-kubeadm}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

shell_quote() {
  printf '%q' "$1"
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

  stdout_file="$(mktemp "${TMPDIR:-/tmp}/itunda-run-vm-stdout.XXXXXX")"
  stderr_file="$(mktemp "${TMPDIR:-/tmp}/itunda-run-vm-stderr.XXXXXX")"

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

container_name() {
  local node="$1"
  local prefix="$2"

  run_vm "$node" "sudo docker ps --format '{{.Names}}' | awk '/^${prefix}/{print; exit}'" 2>/dev/null || true
}

container_name_any_state() {
  local node="$1"
  local prefix="$2"

  run_vm "$node" "sudo docker ps -a --format '{{.Names}}' | awk '/^${prefix}/{print; exit}'" 2>/dev/null || true
}

container_state() {
  local node="$1"
  local container="$2"

  run_vm "$node" "sudo docker inspect -f '{{.State.Status}}' ${container}" 2>/dev/null | tr -d '\r' || true
}

ensure_container_running() {
  local node="$1"
  local container="$2"
  local state

  state="$(container_state "$node" "$container")"
  if [[ "$state" == "running" ]]; then
    return 0
  fi

  run_vm "$node" "sudo docker start ${container} >/dev/null"
}

container_env() {
  local node="$1"
  local container="$2"
  local key="$3"

  run_vm "$node" "sudo docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' ${container} | sed -n 's/^${key}=//p' | head -n 1" 2>/dev/null || true
}

legacy_cluster_service_name() {
  local node="$1"

  if [[ "$node" == "$PRIMARY_NODE" ]]; then
    printf 'k3s\n'
  else
    printf 'k3s-agent\n'
  fi
}

desired_cluster_distro() {
  printf '%s\n' "$DEFAULT_KUBERNETES_DISTRO"
}

require_kubeadm_cluster() {
  local distro

  distro="$(installed_cluster_distro)"
  if [[ "$distro" == "kubeadm" ]]; then
    return 0
  fi

  cat >&2 <<EOF
This workflow now targets kubeadm-based Kubernetes.

- detected cluster distro: ${distro}
- required distro: kubeadm

Run:
  bash scripts/private-cloud-bootstrap.sh migrate-kubeadm
EOF
  exit 1
}

installed_cluster_distro() {
  if run_vm "$PRIMARY_NODE" "test -f /etc/kubernetes/admin.conf && systemctl is-active kubelet >/dev/null 2>&1"; then
    printf 'kubeadm\n'
  elif run_vm "$PRIMARY_NODE" "systemctl is-active k3s >/dev/null 2>&1"; then
    printf 'k3s\n'
  else
    desired_cluster_distro
  fi
}

cluster_service_name() {
  local node="$1"
  local distro

  distro="$(installed_cluster_distro)"
  if [[ "$distro" == "kubeadm" ]]; then
    printf 'kubelet\n'
  else
    legacy_cluster_service_name "$node"
  fi
}

cluster_is_active() {
  local node="$1"
  local service
  local distro

  distro="$(installed_cluster_distro)"
  service="$(cluster_service_name "$node")"
  if [[ "$distro" == "kubeadm" ]]; then
    if [[ "$node" == "$PRIMARY_NODE" ]]; then
      [[ "$(run_vm "$node" "systemctl is-active ${service} 2>/dev/null || true" | tr -d '\r')" == "active" ]] && run_vm "$node" "test -f /etc/kubernetes/admin.conf"
    else
      [[ "$(run_vm "$node" "systemctl is-active ${service} 2>/dev/null || true" | tr -d '\r')" == "active" ]]
    fi
  else
    [[ "$(run_vm "$node" "systemctl is-active ${service} 2>/dev/null || true" | tr -d '\r')" == "active" ]]
  fi
}

cluster_kubeconfig_path() {
  local distro

  distro="$(installed_cluster_distro)"
  if [[ "$distro" == "kubeadm" ]]; then
    printf '/etc/kubernetes/admin.conf\n'
  else
    printf '/etc/rancher/k3s/k3s.yaml\n'
  fi
}

cluster_kubectl() {
  local distro

  distro="$(installed_cluster_distro)"
  if [[ "$distro" == "kubeadm" ]]; then
    run_vm "$PRIMARY_NODE" "sudo kubectl --kubeconfig $(cluster_kubeconfig_path) $*"
  else
    run_vm "$PRIMARY_NODE" "sudo k3s kubectl $*"
  fi
}

kubeconfig_cat() {
  local ip
  local path

  ip="$(server_ip)"
  path="$(cluster_kubeconfig_path)"
  run_vm "$PRIMARY_NODE" "sudo cat ${path}" | sed "s/127.0.0.1/${ip}/g"
}

containerd_socket_path() {
  printf 'unix:///run/containerd/containerd.sock\n'
}

normalize_arch() {
  case "$1" in
    aarch64|arm64)
      printf 'arm64\n'
      ;;
    x86_64|amd64)
      printf 'amd64\n'
      ;;
    *)
      printf '%s\n' "$1"
      ;;
  esac
}

node_arch() {
  local node="$1"
  local arch

  arch="$(run_vm "$node" "uname -m" | tr -d '\r')"
  normalize_arch "$arch"
}

cluster_version_minor() {
  printf '%s\n' "${ITUNDA_PRIVATE_CLOUD_K8S_MINOR_VERSION:-v1.36}"
}

pod_network_cidr() {
  printf '%s\n' "${ITUNDA_PRIVATE_CLOUD_POD_CIDR:-10.244.0.0/16}"
}

service_network_cidr() {
  printf '%s\n' "${ITUNDA_PRIVATE_CLOUD_SERVICE_CIDR:-10.96.0.0/12}"
}

cluster_control_plane_port() {
  printf '%s\n' "${ITUNDA_PRIVATE_CLOUD_API_SERVER_PORT:-6443}"
}

node_ip() {
  local node="$1"

  run_vm "$node" "hostname -I | awk '{print \$1}'" | tr -d '\r'
}

api_server_endpoint() {
  printf '%s\n' "${ITUNDA_PRIVATE_CLOUD_API_SERVER_ENDPOINT:-$(node_ip "$PRIMARY_NODE")}"
}

cluster_join_endpoint() {
  printf '%s:%s\n' "$(api_server_endpoint)" "$(cluster_control_plane_port)"
}

flannel_manifest_url() {
  printf '%s\n' "${ITUNDA_PRIVATE_CLOUD_FLANNEL_MANIFEST_URL:-https://raw.githubusercontent.com/flannel-io/flannel/v0.28.7/Documentation/kube-flannel.yml}"
}

ensure_runtime_env() {
  local line

  if [[ -n "${DB_HOST:-}" && -n "${REDIS_HOST:-}" && -n "${KAFKA_BOOTSTRAP_SERVERS:-}" ]]; then
    return 0
  fi

  while IFS= read -r line; do
    [[ -z "$line" || "$line" == \#* ]] && continue
    export "$line"
  done < <(bash "$ROOT_DIR/scripts/private-cloud-audit.sh" env)
}

render_runtime_config_manifest() {
  local manifest_path="$1"

  cat >"$manifest_path" <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: itunda-db-credentials
  namespace: itunda
type: Opaque
stringData:
  username: ${DB_USER}
  password: ${DB_PASSWORD}
---
apiVersion: v1
kind: Secret
metadata:
  name: itunda-jwt-secret
  namespace: itunda
type: Opaque
stringData:
  secret: ${JWT_SECRET:-itunda-dev-secret-do-not-use-in-production}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: itunda-db-config
  namespace: itunda
data:
  host: "${DB_HOST}"
  port: "${DB_PORT:-3306}"
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: itunda-microservices-db-config
  namespace: itunda
data:
  host: "${DB_HOST}"
  port: "${DB_PORT:-3306}"
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: itunda-redis-config
  namespace: itunda
data:
  host: "${REDIS_HOST}"
  port: "${REDIS_PORT:-6379}"
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: itunda-kafka-config
  namespace: itunda
data:
  bootstrap-servers: "${KAFKA_BOOTSTRAP_SERVERS}"
EOF
}

node_by_ip() {
  local wanted_ip="$1"
  local node

  for node in "${NODES[@]}"; do
    if [[ "$(node_ip "$node")" == "$wanted_ip" ]]; then
      printf '%s\n' "$node"
      return 0
    fi
  done

  return 1
}

mysql_container_on_node() {
  local node="$1"

  container_name "$node" 'mysql-'
}

mysql_root_password_on_node() {
  local node="$1"
  local container

  container="$(mysql_container_on_node "$node")"
  [[ -n "$container" ]] || return 1
  container_env "$node" "$container" MYSQL_ROOT_PASSWORD
}

mysql_exec_on_node() {
  local node="$1"
  local query="$2"
  local container
  local password
  local quoted_password
  local quoted_query

  container="$(mysql_container_on_node "$node")"
  password="$(mysql_root_password_on_node "$node")"
  [[ -n "$container" && -n "$password" ]] || return 1

  quoted_password="$(shell_quote "$password")"
  quoted_query="$(shell_quote "$query")"
  run_vm "$node" "sudo docker exec -e MYSQL_PWD=${quoted_password} ${container} mysql -uroot -e ${quoted_query}"
}

private_cloud_db_writer_node() {
  ensure_runtime_env
  node_by_ip "$DB_HOST"
}

ensure_private_cloud_mysql_databases() {
  local writer_node="$1"
  local sql

  sql="$(<"$ROOT_DIR/infra/mysql-init/01-init-databases.sql")"
  mysql_exec_on_node "$writer_node" "$sql"
}

mount_repo_on_primary() {
  local archive

  archive="$(mktemp "${TMPDIR:-/tmp}/itunda-infra.XXXXXX.tgz")"
  COPYFILE_DISABLE=1 COPY_EXTENDED_ATTRIBUTES_DISABLE=1 tar --no-mac-metadata --no-xattrs -C "$ROOT_DIR" -czf "$archive" infra
  multipass transfer "$archive" "${PRIMARY_NODE}:/tmp/itunda-infra.tgz"
  rm -f "$archive"

  run_vm "$PRIMARY_NODE" "sudo rm -rf ${STAGED_REPO_PATH} && sudo mkdir -p ${STAGED_REPO_PATH} && sudo tar -xzf /tmp/itunda-infra.tgz -C ${STAGED_REPO_PATH}"
  DEPLOY_REPO_PATH="$STAGED_REPO_PATH"
}

stage_repo_on_primary() {
  local archive

  archive="$(mktemp "${TMPDIR:-/tmp}/itunda-repo.XXXXXX.tgz")"
  COPYFILE_DISABLE=1 COPY_EXTENDED_ATTRIBUTES_DISABLE=1 tar --no-mac-metadata --no-xattrs \
    --exclude='.git' \
    --exclude='.yarn/cache' \
    --exclude='.gradle' \
    --exclude='node_modules' \
    --exclude='build' \
    --exclude='dist' \
    --exclude='target' \
    --exclude='.next' \
    --exclude='coverage' \
    -C "$ROOT_DIR" \
    -czf "$archive" \
    .
  multipass transfer "$archive" "${PRIMARY_NODE}:/tmp/itunda-repo.tgz"
  rm -f "$archive"

  run_vm "$PRIMARY_NODE" "sudo rm -rf ${STAGED_REPO_PATH} && sudo mkdir -p ${STAGED_REPO_PATH} && sudo tar -xzf /tmp/itunda-repo.tgz -C ${STAGED_REPO_PATH}"
  DEPLOY_REPO_PATH="$STAGED_REPO_PATH"
}

server_ip() {
  node_ip "$PRIMARY_NODE"
}
