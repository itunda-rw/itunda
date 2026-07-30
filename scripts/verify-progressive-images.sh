#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROLLOUT_DIR="$ROOT_DIR/infra/k8s/progressive-delivery"
SERVICES=(api-gateway backend ledger-service payment-service)

for service in "${SERVICES[@]}"; do
  manifest="$ROLLOUT_DIR/${service}-rollout.yaml"
  [[ -f "$manifest" ]] || { echo "Missing rollout manifest: $manifest" >&2; exit 1; }

  image_lines="$(sed -n 's/^[[:space:]]*image:[[:space:]]*//p' "$manifest")"
  image_count="$(printf '%s\n' "$image_lines" | sed '/^$/d' | wc -l | tr -d ' ')"
  if [[ "$image_count" != "1" ]]; then
    echo "${service}: expected exactly one workload image, found ${image_count}" >&2
    exit 1
  fi

  image="$(printf '%s\n' "$image_lines" | sed '/^$/d')"
  if [[ ! "$image" =~ ^[^[:space:]@]+@sha256:[a-f0-9]{64}$ ]]; then
    echo "${service}: progressive rollout image must be content-addressed, got: ${image}" >&2
    exit 1
  fi
done

echo "Progressive rollout images are pinned to SHA-256 content digests."
