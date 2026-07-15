#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/private-cloud-lib.sh"

MODE="${1:-help}"
NODE_METRICS_SCRIPT_PATH="${ITUNDA_PRIVATE_CLOUD_NODE_METRICS_SCRIPT_PATH:-$ROOT_DIR/scripts/private-cloud-node-metrics.sh}"
VM_TEXTFILE_DIR="${ITUNDA_PRIVATE_CLOUD_NODE_EXPORTER_TEXTFILE_DIR:-/var/lib/itunda-node-exporter/textfile_collector}"
VM_SCRIPT_PATH="${ITUNDA_PRIVATE_CLOUD_VM_NODE_METRICS_SCRIPT_PATH:-/usr/local/bin/itunda-private-cloud-node-metrics.sh}"
SYSTEMD_SERVICE_NAME="itunda-private-cloud-node-metrics.service"
SYSTEMD_TIMER_NAME="itunda-private-cloud-node-metrics.timer"

require_file() {
  local path="$1"

  if [[ ! -f "$path" ]]; then
    echo "File not found: $path" >&2
    exit 1
  fi
}

render_systemd_service() {
  cat <<EOF
[Unit]
Description=Generate Itunda private-cloud Prometheus textfile metrics
After=docker.service
Wants=docker.service

[Service]
Type=oneshot
ExecStart=${VM_SCRIPT_PATH}
EOF
}

render_systemd_timer() {
  cat <<EOF
[Unit]
Description=Refresh Itunda private-cloud Prometheus textfile metrics every 30 seconds

[Timer]
OnBootSec=30s
OnUnitActiveSec=30s
Unit=${SYSTEMD_SERVICE_NAME}

[Install]
WantedBy=timers.target
EOF
}

install_vm_metrics_on_node() {
  local node="$1"
  local tmpdir

  require_file "$NODE_METRICS_SCRIPT_PATH"
  tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/itunda-observability.XXXXXX")"
  cp "$NODE_METRICS_SCRIPT_PATH" "$tmpdir/itunda-private-cloud-node-metrics.sh"
  render_systemd_service > "$tmpdir/${SYSTEMD_SERVICE_NAME}"
  render_systemd_timer > "$tmpdir/${SYSTEMD_TIMER_NAME}"

  multipass transfer "$tmpdir/itunda-private-cloud-node-metrics.sh" "${node}:/tmp/itunda-private-cloud-node-metrics.sh"
  multipass transfer "$tmpdir/${SYSTEMD_SERVICE_NAME}" "${node}:/tmp/${SYSTEMD_SERVICE_NAME}"
  multipass transfer "$tmpdir/${SYSTEMD_TIMER_NAME}" "${node}:/tmp/${SYSTEMD_TIMER_NAME}"

  run_vm "$node" "
    set -euo pipefail
    sudo install -d -m 0755 '${VM_TEXTFILE_DIR}'
    sudo install -d -m 0755 '$(dirname "$VM_SCRIPT_PATH")'
    sudo install -m 0755 /tmp/itunda-private-cloud-node-metrics.sh '${VM_SCRIPT_PATH}'
    sudo install -m 0644 /tmp/${SYSTEMD_SERVICE_NAME} /etc/systemd/system/${SYSTEMD_SERVICE_NAME}
    sudo install -m 0644 /tmp/${SYSTEMD_TIMER_NAME} /etc/systemd/system/${SYSTEMD_TIMER_NAME}
    sudo systemctl daemon-reload
    sudo systemctl enable --now ${SYSTEMD_TIMER_NAME}
    sudo systemctl start ${SYSTEMD_SERVICE_NAME}
    rm -f /tmp/itunda-private-cloud-node-metrics.sh /tmp/${SYSTEMD_SERVICE_NAME} /tmp/${SYSTEMD_TIMER_NAME}
  "

  rm -rf "$tmpdir"
}

run_vm_metrics_on_node() {
  local node="$1"

  run_vm "$node" "sudo ${VM_SCRIPT_PATH}"
}

show_node_status() {
  local node="$1"

  echo "== ${node} =="
  run_vm "$node" "
    set -euo pipefail
    echo \"timer: \$(sudo systemctl is-active ${SYSTEMD_TIMER_NAME} 2>/dev/null || true)\"
    echo \"service result: \$(sudo systemctl show -p Result --value ${SYSTEMD_SERVICE_NAME} 2>/dev/null || true)\"
    echo \"metrics file: ${VM_TEXTFILE_DIR}/itunda_private_cloud.prom\"
    sudo sed -n '1,120p' ${VM_TEXTFILE_DIR}/itunda_private_cloud.prom 2>/dev/null || true
  "
  echo
}

install_vm_metrics() {
  local node

  require_cmd multipass
  for node in "${NODES[@]}"; do
    install_vm_metrics_on_node "$node"
  done
}

run_vm_metrics() {
  local node

  require_cmd multipass
  for node in "${NODES[@]}"; do
    run_vm_metrics_on_node "$node"
  done
}

status() {
  local node

  require_cmd multipass
  for node in "${NODES[@]}"; do
    show_node_status "$node"
  done
}

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-observability.sh <command>

Commands:
  install-vm-metrics   Install the VM-side Prometheus textfile collector service on each node
  run-vm-metrics       Run one immediate metrics collection pass on each node
  status               Show the VM-side collector status and current metrics output
EOF
}

case "$MODE" in
  install-vm-metrics)
    install_vm_metrics
    ;;
  run-vm-metrics)
    run_vm_metrics
    ;;
  status)
    status
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
