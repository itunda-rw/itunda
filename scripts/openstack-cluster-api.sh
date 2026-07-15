#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/private-cloud-inventory.sh"

MODE="${1:-help}"
CAPI_NAMESPACE="${ITUNDA_CAPO_NAMESPACE:-capo-itunda}"
CALICO_VERSION="${ITUNDA_CALICO_VERSION:-v3.32.1}"
CALICO_MANIFEST_URL="${ITUNDA_CALICO_MANIFEST_URL:-https://raw.githubusercontent.com/projectcalico/calico/${CALICO_VERSION}/manifests/calico.yaml}"
CAPO_CLOUD_CONF_SCRIPT_URL="${ITUNDA_CAPO_CLOUD_CONF_SCRIPT_URL:-https://raw.githubusercontent.com/kubernetes-sigs/cluster-api-provider-openstack/main/templates/create_cloud_conf.sh}"
OCCM_BASE_URL="${ITUNDA_OCCM_BASE_URL:-https://raw.githubusercontent.com/kubernetes/cloud-provider-openstack/master/manifests/controller-manager}"

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

workload_clusters() {
  require_cmd jq
  jq -r '.platform.clusters[] | select(.role == "workload") | .name' "$INVENTORY_PATH"
}

workload_live_zone() {
  local cluster_name="$1"

  require_cmd jq
  jq -r --arg cluster_name "$cluster_name" '.platform.clusters[] | select(.name == $cluster_name) | .liveZone' "$INVENTORY_PATH"
}

render_workload_kubeconfig() {
  local cluster_name="$1"
  local kubeconfig_path="$2"

  clusterctl get kubeconfig "$cluster_name" --namespace "$CAPI_NAMESPACE" > "$kubeconfig_path"
}

render_openstack_cloud_conf() {
  local clouds_yaml="$1"
  local cloud_name="$2"
  local output_path="$3"

  if command -v yq >/dev/null 2>&1; then
    curl -fsSL "$CAPO_CLOUD_CONF_SCRIPT_URL" | bash -s -- "$clouds_yaml" "$cloud_name" > "$output_path"
    return 0
  fi

  ruby -ryaml -e '
    clouds_path = ARGV[0]
    cloud_name = ARGV[1]
    password_fallback = ENV["OS_PASSWORD"]
    doc = YAML.load_file(clouds_path)
    cloud = (doc["clouds"] || {})[cloud_name]
    raise "cloud not found: #{cloud_name}" unless cloud

    auth = cloud["auth"] || {}
    auth_url = auth["auth_url"]
    raise "auth_url missing for #{cloud_name}" if auth_url.to_s.empty?

    username = auth["username"]
    password = auth["password"]
    password = password_fallback if password.to_s.empty?
    tenant_id = auth["project_id"]
    tenant_name = auth["project_name"]
    domain_name = auth["user_domain_name"]
    domain_name = auth["domain_name"] if domain_name.to_s.empty?
    domain_id = auth["user_domain_id"]
    domain_id = auth["domain_id"] if domain_id.to_s.empty?
    region = cloud["region_name"]
    cacert = cloud["cacert"]
    application_credential_name = auth["application_credential_name"]
    application_credential_id = auth["application_credential_id"]
    application_credential_secret = auth["application_credential_secret"]

    puts "[Global]"
    puts "auth-url=#{auth_url}"
    puts "username=\"#{username}\"" unless username.to_s.empty?
    puts "password=\"#{password}\"" unless password.to_s.empty?
    puts "tenant-id=\"#{tenant_id}\"" unless tenant_id.to_s.empty?
    puts "tenant-name=\"#{tenant_name}\"" unless tenant_name.to_s.empty?
    puts "domain-name=\"#{domain_name}\"" unless domain_name.to_s.empty?
    puts "domain-id=\"#{domain_id}\"" unless domain_id.to_s.empty?
    puts "ca-file=\"#{cacert}\"" unless cacert.to_s.empty?
    puts "region=\"#{region}\"" unless region.to_s.empty?
    puts "application-credential-name=\"#{application_credential_name}\"" unless application_credential_name.to_s.empty?
    puts "application-credential-id=\"#{application_credential_id}\"" unless application_credential_id.to_s.empty?
    puts "application-credential-secret=\"#{application_credential_secret}\"" unless application_credential_secret.to_s.empty?
  ' "$clouds_yaml" "$cloud_name" > "$output_path"
}

apply_calico() {
  local kubeconfig_path="$1"

  kubectl --kubeconfig "$kubeconfig_path" apply -f "$CALICO_MANIFEST_URL"
}

apply_occm() {
  local kubeconfig_path="$1"
  local cluster_name="$2"
  local cloud_conf_path="$3"

  kubectl --kubeconfig "$kubeconfig_path" -n kube-system create secret generic cloud-config \
    --from-file=cloud.conf="$cloud_conf_path" \
    --dry-run=client -o yaml | kubectl --kubeconfig "$kubeconfig_path" apply -f -

  kubectl --kubeconfig "$kubeconfig_path" apply -f "$OCCM_BASE_URL/cloud-controller-manager-roles.yaml"
  kubectl --kubeconfig "$kubeconfig_path" apply -f "$OCCM_BASE_URL/cloud-controller-manager-role-bindings.yaml"
  curl -fsSL "$OCCM_BASE_URL/openstack-cloud-controller-manager-ds.yaml" \
    | sed "s/value: kubernetes/value: ${cluster_name}/" \
    | kubectl --kubeconfig "$kubeconfig_path" apply -f -
}

bootstrap_workload_cluster() {
  local cluster_name="$1"
  local clouds_yaml="$2"
  local cloud_name="$3"
  local kubeconfig_path
  local cloud_conf_path

  require_cmd clusterctl
  require_cmd kubectl
  require_cmd curl
  require_cmd jq

  if [[ ! -f "$clouds_yaml" ]]; then
    echo "clouds.yaml not found: $clouds_yaml" >&2
    exit 1
  fi

  kubeconfig_path="$(mktemp "${TMPDIR:-/tmp}/${cluster_name}.XXXXXX.kubeconfig")"
  cloud_conf_path="$(mktemp "${TMPDIR:-/tmp}/${cluster_name}.XXXXXX.cloud.conf")"
  trap 'rm -f "$kubeconfig_path" "$cloud_conf_path"' EXIT INT TERM

  render_workload_kubeconfig "$cluster_name" "$kubeconfig_path"
  apply_calico "$kubeconfig_path"
  render_openstack_cloud_conf "$clouds_yaml" "$cloud_name" "$cloud_conf_path"
  apply_occm "$kubeconfig_path" "$cluster_name" "$cloud_conf_path"

  cat <<EOF
Bootstrapped ${cluster_name} with:
- Calico ${CALICO_VERSION}
- OpenStack cloud.conf Secret in kube-system
- OpenStack Cloud Controller Manager

Next:
1. Install a storage class for Harbor and stateful workloads, typically Cinder CSI.
2. Install shared Pod0 services such as Harbor and monitoring on itunda-mgmt.
3. Register the workload cluster in traffic steering before putting it into service.
EOF

  rm -f "$kubeconfig_path" "$cloud_conf_path"
  trap - EXIT INT TERM
}

bootstrap_all_workload_clusters() {
  local clouds_yaml="$1"
  local cloud_name="$2"
  local cluster_name

  while IFS= read -r cluster_name; do
    [[ -z "$cluster_name" ]] && continue
    echo "Bootstrapping ${cluster_name}"
    bootstrap_workload_cluster "$cluster_name" "$clouds_yaml" "$cloud_name"
    echo
  done < <(workload_clusters)
}

render_calico_configmap() {
  local output_dir="$1"
  local calico_manifest

  calico_manifest="$(mktemp "${TMPDIR:-/tmp}/itunda-calico.XXXXXX.yaml")"
  curl -fsSL "$CALICO_MANIFEST_URL" > "$calico_manifest"

  cat > "$output_dir/itunda-calico.configmap.yaml" <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: itunda-calico
  namespace: ${CAPI_NAMESPACE}
data:
  calico.yaml: |
$(sed 's/^/    /' "$calico_manifest")
EOF

  rm -f "$calico_manifest"
}

render_workload_addon_bundle() {
  local cluster_name="$1"
  local clouds_yaml="$2"
  local cloud_name="$3"
  local output_dir="$4"
  local live_zone
  local cloud_conf_path
  local roles_path
  local bindings_path
  local daemonset_path
  local combined_path

  live_zone="$(workload_live_zone "$cluster_name")"
  if [[ -z "$live_zone" || "$live_zone" == "null" ]]; then
    echo "Could not determine live zone for cluster: $cluster_name" >&2
    exit 1
  fi

  cloud_conf_path="$(mktemp "${TMPDIR:-/tmp}/${cluster_name}.XXXXXX.cloud.conf")"
  roles_path="$(mktemp "${TMPDIR:-/tmp}/${cluster_name}.XXXXXX.roles.yaml")"
  bindings_path="$(mktemp "${TMPDIR:-/tmp}/${cluster_name}.XXXXXX.bindings.yaml")"
  daemonset_path="$(mktemp "${TMPDIR:-/tmp}/${cluster_name}.XXXXXX.daemonset.yaml")"
  combined_path="$(mktemp "${TMPDIR:-/tmp}/${cluster_name}.XXXXXX.combined.yaml")"

  render_openstack_cloud_conf "$clouds_yaml" "$cloud_name" "$cloud_conf_path"
  curl -fsSL "$OCCM_BASE_URL/cloud-controller-manager-roles.yaml" > "$roles_path"
  curl -fsSL "$OCCM_BASE_URL/cloud-controller-manager-role-bindings.yaml" > "$bindings_path"
  curl -fsSL "$OCCM_BASE_URL/openstack-cloud-controller-manager-ds.yaml" \
    | sed "s/value: kubernetes/value: ${cluster_name}/" > "$daemonset_path"
  cat "$roles_path" "$bindings_path" "$daemonset_path" > "$combined_path"

  cat > "$output_dir/${cluster_name}-cloud-provider-openstack.configmap.yaml" <<EOF
apiVersion: v1
kind: ConfigMap
metadata:
  name: ${cluster_name}-cloud-provider-openstack
  namespace: ${CAPI_NAMESPACE}
data:
  cloud-provider-openstack.yaml: |
$(sed 's/^/    /' "$combined_path")
EOF

  cat > "$output_dir/${cluster_name}-cloud-config.secret.yaml" <<EOF
apiVersion: v1
kind: Secret
metadata:
  name: ${cluster_name}-cloud-config
  namespace: ${CAPI_NAMESPACE}
type: addons.cluster.x-k8s.io/resource-set
stringData:
  cloud-config-secret.yaml: |
    apiVersion: v1
    kind: Secret
    metadata:
      name: cloud-config
      namespace: kube-system
    type: Opaque
    stringData:
      cloud.conf: |
$(sed 's/^/        /' "$cloud_conf_path")
EOF

  cat > "$output_dir/${cluster_name}-clusterresourceset.yaml" <<EOF
apiVersion: addons.cluster.x-k8s.io/v1beta2
kind: ClusterResourceSet
metadata:
  name: ${cluster_name}-openstack-workload
  namespace: ${CAPI_NAMESPACE}
spec:
  strategy: Reconcile
  clusterSelector:
    matchLabels:
      itunda.io/provider: openstack
      itunda.io/live-zone: ${live_zone}
  resources:
  - kind: ConfigMap
    name: itunda-calico
  - kind: Secret
    name: ${cluster_name}-cloud-config
  - kind: ConfigMap
    name: ${cluster_name}-cloud-provider-openstack
EOF

  rm -f "$cloud_conf_path" "$roles_path" "$bindings_path" "$daemonset_path" "$combined_path"
}

render_workload_addons() {
  local clouds_yaml="$1"
  local cloud_name="$2"
  local output_dir="$3"
  local cluster_name

  require_cmd curl
  require_cmd jq

  [[ -f "$clouds_yaml" ]] || {
    echo "clouds.yaml not found: $clouds_yaml" >&2
    exit 1
  }

  mkdir -p "$output_dir"
  render_calico_configmap "$output_dir"
  while IFS= read -r cluster_name; do
    [[ -z "$cluster_name" ]] && continue
    render_workload_addon_bundle "$cluster_name" "$clouds_yaml" "$cloud_name" "$output_dir"
  done < <(workload_clusters)

  cat <<EOF
Rendered management-cluster addon manifests in:
${output_dir}

These files implement the official ClusterResourceSet pattern:
- one shared Calico ConfigMap
- one OpenStack cloud-config Secret per workload cluster
- one OCCM ConfigMap per workload cluster
- one ClusterResourceSet per workload cluster

Apply them to the management cluster after the workload Cluster objects exist:
kubectl apply -f ${output_dir}
EOF
}

print_plan() {
  cat <<EOF
OpenStack / OKS bootstrap plan

1. Bootstrap a temporary management cluster and run:
   clusterctl init --infrastructure openstack
2. Apply:
   - infra/openstack/cluster-api/openstack-cloud-config.secret.example.yaml
   - infra/openstack/cluster-api/itunda-mgmt-cluster.yaml
3. Move Cluster API management into itunda-mgmt.
4. Apply:
   - infra/openstack/cluster-api/itunda-live3-cluster.yaml
   - infra/openstack/cluster-api/itunda-live4-cluster.yaml
5. Render and apply workload add-ons from the management cluster:
   bash scripts/openstack-cluster-api.sh render-workload-addons <path/to/clouds.yaml> <cloud-name> <output-dir>
   kubectl apply -f <output-dir>
6. On the management cluster, install Pod0 shared services:
   - Harbor using infra/openstack/cluster-api/pod0-platform/harbor-values.example.yaml
   - Mimir using infra/openstack/cluster-api/pod0-platform/mimir-values.example.yaml
   - Zabbix using infra/openstack/cluster-api/pod0-platform/zabbix-values.example.yaml
   - the Pod0 monitoring overlay at infra/openstack/cluster-api/pod0-platform/monitoring
   - run: bash scripts/openstack-cluster-api.sh pod0-plan

This follows the published Toss Payments pattern:
- Pod0 management
- Pod1 / Pod2 independent OpenStack workload pods
- Cluster API + CAPO + OCCM
- Harbor as the private registry layer
EOF
}

print_pod0_plan() {
  cat <<'EOF'
Pod0 management-cluster plan

1. Fetch the management-cluster kubeconfig:
   clusterctl get kubeconfig itunda-mgmt --namespace capo-itunda > /tmp/itunda-mgmt.kubeconfig

2. Install Harbor on Pod0:
   helm repo add harbor https://helm.goharbor.io
   helm --kubeconfig /tmp/itunda-mgmt.kubeconfig upgrade --install harbor harbor/harbor \
     --namespace harbor-system \
     --create-namespace \
     -f infra/openstack/cluster-api/pod0-platform/harbor-values.example.yaml

3. Install Grafana Mimir on Pod0:
   helm repo add grafana https://grafana.github.io/helm-charts
   helm --kubeconfig /tmp/itunda-mgmt.kubeconfig upgrade --install mimir grafana/mimir-distributed \
     --namespace monitoring \
     --create-namespace \
     -f infra/openstack/cluster-api/pod0-platform/mimir-values.example.yaml

4. Apply the shared Pod0 monitoring overlay:
   kubectl --kubeconfig /tmp/itunda-mgmt.kubeconfig apply -k \
     infra/openstack/cluster-api/pod0-platform/monitoring

5. Install Zabbix on Pod0:
   helm repo add zabbix-community https://zabbix-community.github.io/helm-zabbix
   helm --kubeconfig /tmp/itunda-mgmt.kubeconfig upgrade --install zabbix zabbix-community/zabbix \
     --dependency-update \
     --namespace monitoring \
     --create-namespace \
     -f infra/openstack/cluster-api/pod0-platform/zabbix-values.example.yaml

6. After Pod0 is live:
   - point workload image distribution at Harbor
   - remote-write Prometheus into Mimir
   - use Zabbix for management-plane node and service coverage
EOF
}

print_help() {
  cat <<'EOF'
Usage: scripts/openstack-cluster-api.sh <command> [args]

Commands:
  plan
    Print the Cluster API / OpenStack bootstrap sequence.

  pod0-plan
    Print the management-cluster Harbor, Mimir, monitoring, and Zabbix install sequence.

  render-workload-addons <path/to/clouds.yaml> <cloud-name> <output-dir>
    Generate ClusterResourceSet, cloud-config Secret, OCCM ConfigMap, and shared Calico
    ConfigMap manifests for all workload clusters in the CMDB inventory.

  bootstrap-workload <cluster-name> <path/to/clouds.yaml> <cloud-name>
    Fetch the workload kubeconfig via clusterctl, install Calico, generate cloud.conf with
    CAPO's helper script, and install the OpenStack Cloud Controller Manager.

  bootstrap-workloads <path/to/clouds.yaml> <cloud-name>
    Run bootstrap-workload for every workload cluster in the CMDB inventory.
EOF
}

case "$MODE" in
  plan)
    print_plan
    ;;
  pod0-plan)
    print_pod0_plan
    ;;
  render-workload-addons)
    [[ $# -eq 4 ]] || {
      print_help
      exit 1
    }
    render_workload_addons "$2" "$3" "$4"
    ;;
  bootstrap-workload)
    [[ $# -eq 4 ]] || {
      print_help
      exit 1
    }
    bootstrap_workload_cluster "$2" "$3" "$4"
    ;;
  bootstrap-workloads)
    [[ $# -eq 3 ]] || {
      print_help
      exit 1
    }
    bootstrap_all_workload_clusters "$2" "$3"
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
