#!/usr/bin/env bash

if [[ -z "${ROOT_DIR:-}" ]]; then
  ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fi

INVENTORY_PATH="${ITUNDA_PRIVATE_CLOUD_INVENTORY_PATH:-$ROOT_DIR/infra/openstack/cmdb/private-cloud-inventory.json}"

inventory_default_nodes() {
  printf '%s\n' itunda-dc-a itunda-dc-b
}

inventory_nodes() {
  if [[ -n "${ITUNDA_PRIVATE_CLOUD_NODES:-}" ]]; then
    for node in ${ITUNDA_PRIVATE_CLOUD_NODES}; do
      printf '%s\n' "$node"
    done
    return 0
  fi

  if command -v jq >/dev/null 2>&1 && [[ -f "$INVENTORY_PATH" ]]; then
    jq -r '.platform.nodes[] | select(.status != "disabled") | .name' "$INVENTORY_PATH"
    return 0
  fi

  inventory_default_nodes
}
