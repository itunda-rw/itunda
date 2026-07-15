#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/private-cloud-lib.sh"

MODE="${1:-help}"
DESIRED_DISTRO="$(desired_cluster_distro)"
KUBERNETES_MINOR_VERSION="$(cluster_version_minor)"
KUBERNETES_STABLE_CHANNEL="stable-${KUBERNETES_MINOR_VERSION#v}"
POD_CIDR="$(pod_network_cidr)"
SERVICE_CIDR="$(service_network_cidr)"
CRI_SOCKET="$(containerd_socket_path)"
FLANNEL_URL="$(flannel_manifest_url)"
ALLOW_WORKLOADS_ON_CONTROL_PLANE="${ITUNDA_PRIVATE_CLOUD_ALLOW_WORKLOADS_ON_CONTROL_PLANE:-0}"

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-bootstrap.sh <command>

Commands:
  bootstrap         Install kubeadm Kubernetes on the private cloud
  migrate-kubeadm   Replace the live k3s cluster with kubeadm Kubernetes
  reset             Remove the current Kubernetes distro from the private cloud
  status            Show service state and, if available, cluster nodes
  kubeconfig        Print a host-usable kubeconfig for the primary node
EOF
}

service_active_on_node() {
  local node="$1"
  local service="$2"

  [[ "$(run_vm "$node" "systemctl is-active ${service} 2>/dev/null || true" | tr -d '\r')" == "active" ]]
}

prepare_kernel_prereqs() {
  local node="$1"

  run_vm "$node" "
    set -euo pipefail
    cat <<'EOF' | sudo tee /etc/modules-load.d/itunda-k8s.conf >/dev/null
overlay
br_netfilter
EOF
    sudo modprobe overlay
    sudo modprobe br_netfilter
    cat <<'EOF' | sudo tee /etc/sysctl.d/99-itunda-k8s.conf >/dev/null
net.ipv4.ip_forward = 1
net.bridge.bridge-nf-call-iptables = 1
net.bridge.bridge-nf-call-ip6tables = 1
EOF
    sudo sysctl --system >/dev/null
    sudo swapoff -a || true
  "
}

configure_containerd_for_kubernetes() {
  local node="$1"

  run_vm "$node" "
    set -euo pipefail
    sudo mkdir -p /etc/containerd
    if [[ -f /etc/containerd/config.toml && ! -f /etc/containerd/config.toml.pre-kubeadm ]]; then
      sudo cp /etc/containerd/config.toml /etc/containerd/config.toml.pre-kubeadm
    fi
    containerd config default | sudo tee /etc/containerd/config.toml >/dev/null
    sudo sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
    sudo systemctl restart containerd
  "
}

install_kubernetes_packages() {
  local node="$1"

  run_vm "$node" "
    set -euo pipefail
    sudo mkdir -p -m 755 /etc/apt/keyrings
    sudo apt-get update
    sudo apt-get install -y apt-transport-https ca-certificates curl gpg
    curl -fsSL https://pkgs.k8s.io/core:/stable:/${KUBERNETES_MINOR_VERSION}/deb/Release.key | sudo gpg --dearmor --yes -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
    echo 'deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/${KUBERNETES_MINOR_VERSION}/deb/ /' | sudo tee /etc/apt/sources.list.d/kubernetes.list >/dev/null
    sudo apt-get update
    sudo apt-get install -y kubelet kubeadm kubectl
    sudo apt-mark hold kubelet kubeadm kubectl
    sudo systemctl enable --now kubelet
  "
}

prepare_kubeadm_node() {
  local node="$1"

  prepare_kernel_prereqs "$node"
  configure_containerd_for_kubernetes "$node"
  install_kubernetes_packages "$node"
}

render_kubeadm_init_config() {
  local node="$1"
  local node_ip_value
  local advertise_address

  node_ip_value="$(node_ip "$node")"
  advertise_address="$(api_server_endpoint)"

  cat <<EOF
apiVersion: kubeadm.k8s.io/v1beta4
kind: InitConfiguration
localAPIEndpoint:
  advertiseAddress: ${advertise_address}
  bindPort: $(cluster_control_plane_port)
nodeRegistration:
  name: ${node}
  criSocket: ${CRI_SOCKET}
  kubeletExtraArgs:
  - name: node-ip
    value: "${node_ip_value}"
---
apiVersion: kubeadm.k8s.io/v1beta4
kind: ClusterConfiguration
kubernetesVersion: ${KUBERNETES_STABLE_CHANNEL}
controlPlaneEndpoint: "$(cluster_join_endpoint)"
networking:
  podSubnet: "${POD_CIDR}"
  serviceSubnet: "${SERVICE_CIDR}"
---
apiVersion: kubelet.config.k8s.io/v1beta1
kind: KubeletConfiguration
cgroupDriver: systemd
EOF
}

initialize_control_plane() {
  local config_file

  if cluster_is_active "$PRIMARY_NODE"; then
    echo "${PRIMARY_NODE}: Kubernetes control plane already active"
    return 0
  fi

  config_file="$(mktemp "${TMPDIR:-/tmp}/itunda-kubeadm-init.XXXXXX.yaml")"
  render_kubeadm_init_config "$PRIMARY_NODE" > "$config_file"
  multipass transfer "$config_file" "${PRIMARY_NODE}:/tmp/itunda-kubeadm-init.yaml"
  rm -f "$config_file"

  run_vm "$PRIMARY_NODE" "
    set -euo pipefail
    sudo kubeadm config images pull --config /tmp/itunda-kubeadm-init.yaml
    sudo kubeadm init --config /tmp/itunda-kubeadm-init.yaml
    mkdir -p \$HOME/.kube
    sudo cp /etc/kubernetes/admin.conf \$HOME/.kube/config
    sudo chown \$(id -u):\$(id -g) \$HOME/.kube/config
  "
}

install_flannel() {
  cluster_kubectl "apply -f ${FLANNEL_URL}"
}

wait_for_cluster() {
  local attempts=60
  local i

  for ((i = 1; i <= attempts; i++)); do
    if cluster_kubectl "get nodes >/dev/null 2>&1"; then
      return 0
    fi
    sleep 5
  done

  echo "Timed out waiting for the Kubernetes API on ${PRIMARY_NODE}" >&2
  exit 1
}

join_worker_nodes() {
  local join_command
  local node

  join_command="$(run_vm "$PRIMARY_NODE" "sudo kubeadm token create --print-join-command" | tr -d '\r')"
  for node in "${NODES[@]:1}"; do
    if service_active_on_node "$node" kubelet && run_vm "$node" "test -f /etc/kubernetes/kubelet.conf"; then
      echo "${node}: kubelet already joined"
      continue
    fi

    run_vm "$node" "
      set -euo pipefail
      sudo ${join_command} --cri-socket ${CRI_SOCKET}
    "
  done
}

wait_for_node_readiness() {
  local attempts=60
  local i
  local ready_count

  for ((i = 1; i <= attempts; i++)); do
    ready_count="$(cluster_kubectl "get nodes --no-headers 2>/dev/null | awk '\$2==\"Ready\"{count++} END{print count+0}'" | tr -d '\r')"
    if [[ "$ready_count" -ge "${#NODES[@]}" ]]; then
      return 0
    fi
    sleep 5
  done

  echo "Timed out waiting for all Kubernetes nodes to become Ready." >&2
  cluster_kubectl "get nodes -o wide" || true
  exit 1
}

label_nodes() {
  local zone
  local node

  for node in "${NODES[@]}"; do
    zone="${node##*-}"
    cluster_kubectl "label node ${node} itunda.rw/private-cloud=true topology.kubernetes.io/zone=${zone} itunda.rw/datacenter=${zone} --overwrite >/dev/null"
  done
}

configure_control_plane_scheduling() {
  if [[ "${ALLOW_WORKLOADS_ON_CONTROL_PLANE}" == "1" ]]; then
    cluster_kubectl "taint nodes ${PRIMARY_NODE} node-role.kubernetes.io/control-plane- >/dev/null 2>&1 || true"
    return 0
  fi

  cluster_kubectl "taint nodes ${PRIMARY_NODE} node-role.kubernetes.io/control-plane=:NoSchedule --overwrite >/dev/null 2>&1 || true"
}

bootstrap_kubeadm_cluster() {
  local node

  require_cmd multipass
  require_cmd curl

  if [[ "$(installed_cluster_distro)" == "k3s" ]] && cluster_is_active "$PRIMARY_NODE"; then
    echo "k3s is still installed. Run scripts/private-cloud-bootstrap.sh migrate-kubeadm to replace it." >&2
    exit 1
  fi

  for node in "${NODES[@]}"; do
    prepare_kubeadm_node "$node"
  done

  initialize_control_plane
  wait_for_cluster
  install_flannel
  join_worker_nodes
  wait_for_node_readiness
  label_nodes
  configure_control_plane_scheduling
  show_status
}

reset_k3s_node() {
  local node="$1"

  if [[ "$node" == "$PRIMARY_NODE" ]]; then
    run_vm "$node" "if [[ -x /usr/local/bin/k3s-uninstall.sh ]]; then sudo /usr/local/bin/k3s-uninstall.sh; fi"
  else
    run_vm "$node" "if [[ -x /usr/local/bin/k3s-agent-uninstall.sh ]]; then sudo /usr/local/bin/k3s-agent-uninstall.sh; elif [[ -x /usr/local/bin/k3s-uninstall.sh ]]; then sudo /usr/local/bin/k3s-uninstall.sh; fi"
  fi
}

reset_kubeadm_node() {
  local node="$1"

  run_vm "$node" "
    if command -v kubeadm >/dev/null 2>&1; then
      sudo kubeadm reset -f || true
    fi
    sudo systemctl stop kubelet >/dev/null 2>&1 || true
    sudo rm -rf /etc/cni/net.d /var/lib/cni /var/lib/kubelet /etc/kubernetes /var/lib/etcd
    sudo ip link delete cni0 >/dev/null 2>&1 || true
    sudo ip link delete flannel.1 >/dev/null 2>&1 || true
  "
}

reset_cluster() {
  local current
  local node

  current="$(installed_cluster_distro)"
  for node in "${NODES[@]}"; do
    if [[ "$current" == "k3s" ]]; then
      reset_k3s_node "$node"
    fi
    reset_kubeadm_node "$node"
  done
}

migrate_to_kubeadm() {
  echo "Replacing the live ${PRIMARY_NODE} private-cloud control plane with kubeadm Kubernetes."
  reset_cluster
  bootstrap_kubeadm_cluster
}

show_status() {
  local node
  local service
  local distro

  distro="$(installed_cluster_distro)"
  echo "Cluster distro: ${distro}"
  for node in "${NODES[@]}"; do
    service="$(cluster_service_name "$node")"
    echo "$node ${service}: $(run_vm "$node" "systemctl is-active ${service} 2>/dev/null || true" | tr -d '\r')"
  done

  if cluster_is_active "$PRIMARY_NODE"; then
    echo
    cluster_kubectl "get nodes -o wide"
  fi
}

print_kubeconfig() {
  if ! cluster_is_active "$PRIMARY_NODE"; then
    echo "Kubernetes is not active on ${PRIMARY_NODE}. Run bootstrap first." >&2
    exit 1
  fi

  kubeconfig_cat
}

case "$MODE" in
  bootstrap)
    if [[ "$DESIRED_DISTRO" == "kubeadm" ]]; then
      bootstrap_kubeadm_cluster
    else
      echo "Unsupported configured distro: ${DESIRED_DISTRO}" >&2
      exit 1
    fi
    ;;
  migrate-kubeadm)
    migrate_to_kubeadm
    ;;
  reset)
    reset_cluster
    ;;
  status)
    show_status
    ;;
  kubeconfig)
    print_kubeconfig
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
