#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/private-cloud-lib.sh"

MODE="${1:-help}"
HARBOR_VALUES_FILE_DEFAULT="$ROOT_DIR/infra/openstack/cluster-api/pod0-platform/harbor-values.example.yaml"
HARBOR_REHEARSAL_VALUES_FILE_DEFAULT="$ROOT_DIR/infra/k8s/private-cloud/harbor-rehearsal-values.yaml"
HARBOR_NAMESPACE="${ITUNDA_HARBOR_NAMESPACE:-harbor-system}"
PULL_SECRET_NAME_DEFAULT="${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME:-itunda-registry}"
PULL_SECRET_NAMESPACE_DEFAULT="${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAMESPACE:-itunda}"
HARBOR_PROJECT_DEFAULT="${ITUNDA_HARBOR_PROJECT:-itunda}"
HARBOR_CHART_VERSION_DEFAULT="${ITUNDA_HARBOR_CHART_VERSION:-1.19.1}"
HELM_VERSION_DEFAULT="${ITUNDA_HELM_VERSION:-v3.21.1}"
HARBOR_NODEPORT_HTTP_DEFAULT="${ITUNDA_HARBOR_NODEPORT_HTTP:-30002}"
HARBOR_SECRET_KEY_DEFAULT="${ITUNDA_HARBOR_SECRET_KEY:-itundaharbor1234}"
HARBOR_ADMIN_PASSWORD_DEFAULT="${ITUNDA_HARBOR_ADMIN_PASSWORD:-Harbor12345!itunda}"
HARBOR_ALLOW_UNSUPPORTED_ARCH_DEFAULT="${ITUNDA_HARBOR_ALLOW_UNSUPPORTED_ARCH:-0}"
REGISTRY_REHEARSAL_MANIFEST_DEFAULT="$ROOT_DIR/infra/k8s/private-cloud/registry-rehearsal.yaml"
REGISTRY_NAMESPACE="${ITUNDA_REGISTRY_NAMESPACE:-registry-system}"
REGISTRY_NODEPORT_DEFAULT="${ITUNDA_PRIVATE_CLOUD_REGISTRY_NODEPORT:-32000}"
REGISTRY_IMAGE_DEFAULT="${ITUNDA_PRIVATE_CLOUD_REGISTRY_IMAGE:-registry:3}"

require_file() {
  local path="$1"

  if [[ ! -f "$path" ]]; then
    echo "File not found: $path" >&2
    exit 1
  fi
}

require_cluster() {
  require_cmd multipass

  if ! cluster_is_active "$PRIMARY_NODE"; then
    echo "Kubernetes is not active on ${PRIMARY_NODE}. Run scripts/private-cloud-bootstrap.sh bootstrap first." >&2
    exit 1
  fi

  require_kubeadm_cluster
}

harbor_rehearsal_url() {
  printf '%s\n' "${ITUNDA_HARBOR_URL:-http://$(server_ip):${HARBOR_NODEPORT_HTTP_DEFAULT}}"
}

registry_rehearsal_host() {
  printf '%s\n' "${ITUNDA_PRIVATE_CLOUD_REGISTRY_HOST:-$(server_ip):${REGISTRY_NODEPORT_DEFAULT}}"
}

registry_rehearsal_url() {
  printf 'http://%s\n' "$(registry_rehearsal_host)"
}

ensure_helm_on_primary() {
  local helm_version="${1:-$HELM_VERSION_DEFAULT}"

  run_vm "$PRIMARY_NODE" "
    set -euo pipefail
    version='${helm_version}'
    if command -v helm >/dev/null 2>&1; then
      current=\$(helm version --short 2>/dev/null || true)
      if printf '%s' \"\$current\" | grep -q \"${helm_version#v}\"; then
        printf '%s\n' \"\$current\" >/dev/null
      else
        current=''
      fi
    fi
    if [[ -z \"\${current:-}\" ]]; then
      arch=\$(uname -m)
      case \"\$arch\" in
        aarch64|arm64) arch=arm64 ;;
        x86_64|amd64) arch=amd64 ;;
        *)
          echo \"Unsupported architecture for Helm install: \$arch\" >&2
          exit 1
          ;;
      esac

      tmpdir=\$(mktemp -d)
      curl -fsSL -o \"\$tmpdir/helm.tgz\" \"https://get.helm.sh/helm-\${version}-linux-\${arch}.tar.gz\"
      tar -xzf \"\$tmpdir/helm.tgz\" -C \"\$tmpdir\"
      sudo install -m 0755 \"\$tmpdir/linux-\${arch}/helm\" /usr/local/bin/helm
      rm -rf \"\$tmpdir\"
    fi
  "
}

harbor_chart_app_version() {
  run_vm "$PRIMARY_NODE" "
    set -euo pipefail
    sudo helm repo add harbor https://helm.goharbor.io >/dev/null 2>&1 || true
    sudo helm repo update harbor >/dev/null
    sudo helm show chart harbor/harbor --version '${HARBOR_CHART_VERSION_DEFAULT}' | awk -F': ' '/^appVersion:/{print \$2; exit}'
  " | tr -d '\r'
}

verify_harbor_rehearsal_architecture() {
  local arch
  local app_version
  local image_tag
  local image_support

  if [[ "$HARBOR_ALLOW_UNSUPPORTED_ARCH_DEFAULT" == "1" ]]; then
    return 0
  fi

  arch="$(node_arch "$PRIMARY_NODE")"
  if [[ "$arch" == "amd64" ]]; then
    return 0
  fi

  app_version="$(harbor_chart_app_version)"
  if [[ -z "$app_version" ]]; then
    echo "Unable to resolve the Harbor app version for chart ${HARBOR_CHART_VERSION_DEFAULT}." >&2
    exit 1
  fi

  image_tag="v${app_version#v}"
  image_support="$(
    run_vm "$PRIMARY_NODE" "
      set -euo pipefail
      if sudo docker manifest inspect -v docker.io/goharbor/harbor-core:${image_tag} | grep -q '\"architecture\": \"${arch}\"'; then
        printf '1\n'
      else
        printf '0\n'
      fi
    " | tr -d '\r'
  )"

  if [[ "$image_support" == "1" ]]; then
    return 0
  fi

  cat >&2 <<EOF
Harbor rehearsal is blocked on this cluster architecture.

- node: ${PRIMARY_NODE}
- architecture: ${arch}
- Harbor chart: ${HARBOR_CHART_VERSION_DEFAULT}
- Harbor app version: ${app_version}
- checked image: docker.io/goharbor/harbor-core:${image_tag}

The pinned official Harbor image does not publish a ${arch} manifest, so the default install on
this kubeadm cluster would fail with exec format errors.

Use custom arm64-compatible Harbor images and a custom values file, or bypass this guard
deliberately with ITUNDA_HARBOR_ALLOW_UNSUPPORTED_ARCH=1.
EOF
  exit 1
}

login_registry() {
  local registry="$1"
  local username="$2"
  local password="$3"

  require_cmd docker
  printf '%s' "$password" | docker login "$registry" --username "$username" --password-stdin
}

ensure_harbor_project() {
  local harbor_url="$1"
  local username="$2"
  local password="$3"
  local project_name="${4:-$HARBOR_PROJECT_DEFAULT}"
  local response_file
  local status_code

  require_cmd curl
  require_cmd python3
  response_file="$(mktemp "${TMPDIR:-/tmp}/harbor-project.XXXXXX.json")"
  trap 'rm -f "$response_file"' RETURN

  status_code="$(
    curl -sS -u "${username}:${password}" \
      --get \
      --data-urlencode "name=${project_name}" \
      -o "$response_file" \
      -w '%{http_code}' \
      "${harbor_url%/}/api/v2.0/projects"
  )"

  if [[ "$status_code" != "200" ]]; then
    echo "Failed to query Harbor projects at ${harbor_url} (HTTP ${status_code})" >&2
    cat "$response_file" >&2 || true
    exit 1
  fi

  if python3 - "$response_file" "$project_name" <<'PY'
import json, sys
path, project_name = sys.argv[1], sys.argv[2]
with open(path, "r", encoding="utf-8") as f:
    items = json.load(f)
for item in items:
    if item.get("name") == project_name:
        raise SystemExit(0)
raise SystemExit(1)
PY
  then
    echo "Harbor project already exists: ${project_name}"
    rm -f "$response_file"
    trap - RETURN
    return 0
  fi

  status_code="$(
    curl -sS -u "${username}:${password}" \
      -H 'Content-Type: application/json' \
      -o "$response_file" \
      -w '%{http_code}' \
      -X POST \
      -d "{\"project_name\":\"${project_name}\",\"metadata\":{\"public\":\"false\",\"auto_scan\":\"true\"}}" \
      "${harbor_url%/}/api/v2.0/projects"
  )"

  if [[ "$status_code" == "201" || "$status_code" == "409" ]]; then
    echo "Harbor project ready: ${project_name}"
    rm -f "$response_file"
    trap - RETURN
    return 0
  fi

  echo "Failed to create Harbor project ${project_name} (HTTP ${status_code})" >&2
  cat "$response_file" >&2 || true
  exit 1
}

render_dockerconfigjson() {
  local registry="$1"
  local username="$2"
  local password="$3"
  local auth

  auth="$(printf '%s' "${username}:${password}" | base64 | tr -d '\n')"
  cat <<EOF
{"auths":{"${registry}":{"username":"${username}","password":"${password}","auth":"${auth}"}}}
EOF
}

render_pull_secret_yaml() {
  local registry="$1"
  local username="$2"
  local password="$3"
  local namespace="${4:-$PULL_SECRET_NAMESPACE_DEFAULT}"
  local secret_name="${5:-$PULL_SECRET_NAME_DEFAULT}"
  local dockerconfigjson
  local dockerconfigjson_b64

  dockerconfigjson="$(render_dockerconfigjson "$registry" "$username" "$password")"
  dockerconfigjson_b64="$(printf '%s' "$dockerconfigjson" | base64 | tr -d '\n')"

  cat <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${secret_name}
  namespace: ${namespace}
type: kubernetes.io/dockerconfigjson
data:
  .dockerconfigjson: ${dockerconfigjson_b64}
EOF
}

install_harbor() {
  local kubeconfig_path="$1"
  local values_file="${2:-$HARBOR_VALUES_FILE_DEFAULT}"

  require_cmd helm
  require_file "$kubeconfig_path"
  require_file "$values_file"

  helm repo add harbor https://helm.goharbor.io >/dev/null 2>&1 || true
  helm repo update harbor >/dev/null
  helm --kubeconfig "$kubeconfig_path" upgrade --install harbor harbor/harbor \
    --namespace "$HARBOR_NAMESPACE" \
    --create-namespace \
    -f "$values_file"
}

wait_for_deployment() {
  local namespace="$1"
  local deployment="$2"

  cluster_kubectl "-n ${namespace} rollout status deployment/${deployment} --timeout=600s"
}

wait_for_statefulset() {
  local namespace="$1"
  local statefulset="$2"

  cluster_kubectl "-n ${namespace} rollout status statefulset/${statefulset} --timeout=600s"
}

wait_for_registry_deployment() {
  cluster_kubectl "-n ${REGISTRY_NAMESPACE} rollout status deployment/itunda-registry --timeout=240s"
}

install_harbor_rehearsal() {
  local values_file="${1:-$HARBOR_REHEARSAL_VALUES_FILE_DEFAULT}"
  local external_url
  local remote_values_file

  require_cluster
  require_file "$values_file"
  mount_repo_on_primary
  ensure_helm_on_primary "$HELM_VERSION_DEFAULT"
  verify_harbor_rehearsal_architecture
  external_url="$(harbor_rehearsal_url)"
  remote_values_file="${DEPLOY_REPO_PATH}/${values_file#$ROOT_DIR/}"

  run_vm "$PRIMARY_NODE" "
    set -euo pipefail
    sudo env KUBECONFIG='$(cluster_kubeconfig_path)' helm repo add harbor https://helm.goharbor.io >/dev/null 2>&1 || true
    sudo env KUBECONFIG='$(cluster_kubeconfig_path)' helm repo update harbor >/dev/null
    sudo env KUBECONFIG='$(cluster_kubeconfig_path)' helm upgrade --install harbor harbor/harbor \
      --version '${HARBOR_CHART_VERSION_DEFAULT}' \
      --namespace '${HARBOR_NAMESPACE}' \
      --create-namespace \
      -f '${remote_values_file}' \
      --set externalURL='${external_url}' \
      --set harborAdminPassword='${HARBOR_ADMIN_PASSWORD_DEFAULT}' \
      --set secretKey='${HARBOR_SECRET_KEY_DEFAULT}'
  "

  wait_for_deployment "$HARBOR_NAMESPACE" harbor-core
  wait_for_deployment "$HARBOR_NAMESPACE" harbor-nginx
  wait_for_deployment "$HARBOR_NAMESPACE" harbor-portal
  wait_for_deployment "$HARBOR_NAMESPACE" harbor-jobservice
  wait_for_deployment "$HARBOR_NAMESPACE" harbor-registry
  wait_for_deployment "$HARBOR_NAMESPACE" harbor-exporter
  wait_for_statefulset "$HARBOR_NAMESPACE" harbor-database
  wait_for_statefulset "$HARBOR_NAMESPACE" harbor-redis
}

harbor_status() {
  require_cluster
  cluster_kubectl "-n ${HARBOR_NAMESPACE} get deploy,sts,po,svc"
}

ping_harbor() {
  local url="${1:-$(harbor_rehearsal_url)}"

  require_cmd curl
  curl -fsS "${url%/}/api/v2.0/ping"
}

install_registry_rehearsal() {
  local manifest_path="${1:-$REGISTRY_REHEARSAL_MANIFEST_DEFAULT}"

  require_cluster
  require_file "$manifest_path"
  mount_repo_on_primary

  cluster_kubectl "apply -f ${DEPLOY_REPO_PATH}/${manifest_path#$ROOT_DIR/}"
  wait_for_registry_deployment
}

registry_rehearsal_status() {
  require_cluster
  cluster_kubectl "-n ${REGISTRY_NAMESPACE} get deploy,po,svc"
}

ping_registry_rehearsal() {
  local url="${1:-$(registry_rehearsal_url)}"

  require_cmd curl
  curl -fsS "${url%/}/v2/"
}

smoke_registry_rehearsal() {
  local registry_host="${1:-$(registry_rehearsal_host)}"
  local smoke_tag="${2:-$(date '+%Y%m%d%H%M%S')}"
  local smoke_namespace="registry-smoke"
  local smoke_pod="registry-smoke-worker"
  local smoke_image="${registry_host}/itunda/registry-smoke:${smoke_tag}"
  local manifest_path

  require_cluster

  run_vm "$PRIMARY_NODE" "
    set -euo pipefail
    sudo docker pull busybox:1.36 >/dev/null
    sudo docker tag busybox:1.36 '${smoke_image}'
    sudo docker push '${smoke_image}'
  "

  manifest_path="$(mktemp "${TMPDIR:-/tmp}/itunda-registry-smoke.XXXXXX.yaml")"
  cat >"$manifest_path" <<EOF
apiVersion: v1
kind: Namespace
metadata:
  name: ${smoke_namespace}
---
apiVersion: v1
kind: Pod
metadata:
  name: ${smoke_pod}
  namespace: ${smoke_namespace}
spec:
  nodeSelector:
    kubernetes.io/hostname: ${NODES[1]:-$PRIMARY_NODE}
  restartPolicy: Never
  containers:
  - name: smoke
    image: ${smoke_image}
    command: ["sh", "-lc", "echo pulled-from-private-registry && sleep 5"]
EOF
  multipass transfer "$manifest_path" "${PRIMARY_NODE}:/tmp/itunda-registry-smoke.yaml"
  rm -f "$manifest_path"

  cluster_kubectl "apply -f /tmp/itunda-registry-smoke.yaml"
  cluster_kubectl "-n ${smoke_namespace} wait --for=condition=Ready pod/${smoke_pod} --timeout=180s"
  cluster_kubectl "-n ${smoke_namespace} logs ${smoke_pod}"
  cluster_kubectl "-n ${smoke_namespace} get pod ${smoke_pod} -o wide"
}

configure_containerd_registry() {
  local node="$1"
  local registry_host="$2"
  local registry_host_alt="${registry_host/:/_}_"
  local configure_script

  read -r -d '' configure_script <<'PY' || true
import pathlib
import re

path = pathlib.Path('/etc/containerd/config.toml')
text = path.read_text(encoding='utf-8')
section = r"(\[plugins\.'io\.containerd\.cri\.v1\.images'\.registry\]\s*\n)(\s*config_path\s*=\s*).*"
updated, count = re.subn(section, r"\1\2'/etc/containerd/certs.d'", text, count=1)
if count != 1:
    raise SystemExit('Could not find the active containerd v3 registry config_path')
path.write_text(updated, encoding='utf-8')
PY

  run_vm "$node" "
    set -euo pipefail
    sudo mkdir -p '/etc/containerd/certs.d/${registry_host}' '/etc/containerd/certs.d/${registry_host_alt}'
    # containerd does not automatically load /etc/containerd/conf.d/*.toml on this
    # kubeadm image. Updating a drop-in there looked correct but was inert: CRI kept
    # treating the HTTP registry as HTTPS and pods failed with ImagePullBackOff. Patch
    # the active v3 configuration in place and keep one real config_path (not a
    # colon-separated pseudo-path, which containerd interprets literally).
    sudo python3 -c $(shell_quote "$configure_script")
    cat <<'EOF' | sudo tee '/etc/containerd/certs.d/${registry_host}/hosts.toml' >/dev/null
server = 'http://${registry_host}'

[host.'http://${registry_host}']
  capabilities = ['pull', 'resolve', 'push']
  skip_verify = true
EOF
    sudo cp '/etc/containerd/certs.d/${registry_host}/hosts.toml' '/etc/containerd/certs.d/${registry_host_alt}/hosts.toml'
    sudo systemctl restart containerd
  "
}

configure_docker_insecure_registry() {
  local node="$1"
  local registry_host="$2"

  run_vm "$node" "
    set -euo pipefail
    sudo mkdir -p /etc/docker
    python3 - '${registry_host}' <<'PY' | sudo tee /tmp/itunda-daemon.json >/dev/null
import json
import pathlib
import sys

path = pathlib.Path('/etc/docker/daemon.json')
registry = sys.argv[1]
data = {}
if path.exists():
    with path.open('r', encoding='utf-8') as f:
        data = json.load(f)

items = data.get('insecure-registries', [])
if registry not in items:
    items.append(registry)
data['insecure-registries'] = items
print(json.dumps(data, indent=2, sort_keys=True))
PY
    sudo mv /tmp/itunda-daemon.json /etc/docker/daemon.json
    sudo systemctl restart docker
  "
}

configure_registry_rehearsal_runtime() {
  local registry_host="${1:-$(registry_rehearsal_host)}"
  local node

  require_cluster

  for node in "${NODES[@]}"; do
    configure_containerd_registry "$node" "$registry_host"
    configure_docker_insecure_registry "$node" "$registry_host"
  done
}

apply_pull_secret() {
  local kubeconfig_path="$1"
  local registry="$2"
  local username="$3"
  local password="$4"
  local namespace="${5:-$PULL_SECRET_NAMESPACE_DEFAULT}"
  local secret_name="${6:-$PULL_SECRET_NAME_DEFAULT}"

  require_cmd kubectl
  require_file "$kubeconfig_path"

  kubectl --kubeconfig "$kubeconfig_path" create namespace "$namespace" --dry-run=client -o yaml | kubectl --kubeconfig "$kubeconfig_path" apply -f - >/dev/null
  render_pull_secret_yaml "$registry" "$username" "$password" "$namespace" "$secret_name" | kubectl --kubeconfig "$kubeconfig_path" apply -f -
}

apply_private_cloud_pull_secret() {
  local registry="$1"
  local username="$2"
  local password="$3"
  local namespace="${4:-$PULL_SECRET_NAMESPACE_DEFAULT}"
  local secret_name="${5:-$PULL_SECRET_NAME_DEFAULT}"
  local kubeconfig_path

  require_cmd kubectl

  kubeconfig_path="$(mktemp "${TMPDIR:-/tmp}/itunda-private-cloud.XXXXXX.kubeconfig")"
  trap 'rm -f "$kubeconfig_path"' EXIT INT TERM
  kubeconfig_cat > "$kubeconfig_path"
  apply_pull_secret "$kubeconfig_path" "$registry" "$username" "$password" "$namespace" "$secret_name"
  rm -f "$kubeconfig_path"
  trap - EXIT INT TERM
}

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-registry.sh <command> [args]

Commands:
  install-harbor <management-kubeconfig> [values-file]
    Install or upgrade Harbor on the management cluster.

  install-harbor-rehearsal [values-file]
    Install a low-memory Harbor rehearsal on the current private-cloud kubeadm cluster.
    On arm64, this fails fast unless custom Harbor images are provided.

  install-registry-rehearsal [manifest-path]
    Install the arm64-safe OCI registry rehearsal on the current private-cloud kubeadm cluster.

  configure-registry-rehearsal-runtime [registry-host]
    Configure containerd and Docker on the private-cloud nodes to trust the rehearsal registry.

  status-registry-rehearsal
    Show the registry rehearsal workloads and service.

  ping-registry-rehearsal [registry-url]
    Query the rehearsal registry v2 endpoint.

  smoke-registry-rehearsal [registry-host] [tag]
    Push a smoke image into the rehearsal registry and verify a worker node can pull it.

  status
    Show Harbor workloads and services on the current private-cloud Kubernetes cluster.

  ping [harbor-url]
    Query Harbor's v2 API ping endpoint.

  login <registry> <username> <password>
    Log Docker into the target registry.

  ensure-project <harbor-url> <username> <password> [project-name]
    Ensure the Harbor project exists using Harbor's documented v2 API.

  render-pull-secret <registry> <username> <password> [namespace] [secret-name]
    Print a docker-registry Secret manifest.

  apply-pull-secret <kubeconfig> <registry> <username> <password> [namespace] [secret-name]
    Apply the registry pull secret to a target cluster.

  apply-private-cloud-pull-secret <registry> <username> <password> [namespace] [secret-name]
    Apply the registry pull secret to the current Multipass private-cloud cluster.
EOF
}

case "$MODE" in
  install-harbor)
    [[ $# -ge 2 && $# -le 3 ]] || {
      print_help
      exit 1
    }
    install_harbor "$2" "${3:-$HARBOR_VALUES_FILE_DEFAULT}"
    ;;
  install-harbor-rehearsal)
    [[ $# -ge 1 && $# -le 2 ]] || {
      print_help
      exit 1
    }
    install_harbor_rehearsal "${2:-$HARBOR_REHEARSAL_VALUES_FILE_DEFAULT}"
    ;;
  install-registry-rehearsal)
    [[ $# -ge 1 && $# -le 2 ]] || {
      print_help
      exit 1
    }
    install_registry_rehearsal "${2:-$REGISTRY_REHEARSAL_MANIFEST_DEFAULT}"
    ;;
  configure-registry-rehearsal-runtime)
    [[ $# -ge 1 && $# -le 2 ]] || {
      print_help
      exit 1
    }
    configure_registry_rehearsal_runtime "${2:-$(registry_rehearsal_host)}"
    ;;
  status-registry-rehearsal)
    [[ $# -eq 1 ]] || {
      print_help
      exit 1
    }
    registry_rehearsal_status
    ;;
  ping-registry-rehearsal)
    [[ $# -ge 1 && $# -le 2 ]] || {
      print_help
      exit 1
    }
    ping_registry_rehearsal "${2:-$(registry_rehearsal_url)}"
    ;;
  smoke-registry-rehearsal)
    [[ $# -ge 1 && $# -le 3 ]] || {
      print_help
      exit 1
    }
    smoke_registry_rehearsal "${2:-$(registry_rehearsal_host)}" "${3:-$(date '+%Y%m%d%H%M%S')}"
    ;;
  status)
    [[ $# -eq 1 ]] || {
      print_help
      exit 1
    }
    harbor_status
    ;;
  ping)
    [[ $# -ge 1 && $# -le 2 ]] || {
      print_help
      exit 1
    }
    ping_harbor "${2:-$(harbor_rehearsal_url)}"
    ;;
  login)
    [[ $# -eq 4 ]] || {
      print_help
      exit 1
    }
    login_registry "$2" "$3" "$4"
    ;;
  ensure-project)
    [[ $# -ge 4 && $# -le 5 ]] || {
      print_help
      exit 1
    }
    ensure_harbor_project "$2" "$3" "$4" "${5:-$HARBOR_PROJECT_DEFAULT}"
    ;;
  render-pull-secret)
    [[ $# -ge 4 && $# -le 6 ]] || {
      print_help
      exit 1
    }
    render_pull_secret_yaml "$2" "$3" "$4" "${5:-$PULL_SECRET_NAMESPACE_DEFAULT}" "${6:-$PULL_SECRET_NAME_DEFAULT}"
    ;;
  apply-pull-secret)
    [[ $# -ge 5 && $# -le 7 ]] || {
      print_help
      exit 1
    }
    apply_pull_secret "$2" "$3" "$4" "$5" "${6:-$PULL_SECRET_NAMESPACE_DEFAULT}" "${7:-$PULL_SECRET_NAME_DEFAULT}"
    ;;
  apply-private-cloud-pull-secret)
    [[ $# -ge 4 && $# -le 6 ]] || {
      print_help
      exit 1
    }
    apply_private_cloud_pull_secret "$2" "$3" "$4" "${5:-$PULL_SECRET_NAMESPACE_DEFAULT}" "${6:-$PULL_SECRET_NAME_DEFAULT}"
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
