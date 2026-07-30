#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/private-cloud-lib.sh"

MODE="${1:-help}"
IMAGE_TAG_DEFAULT="${ITUNDA_IMAGE_TAG:-$(date '+%Y-%m-%d')}"
REGISTRY_HOST_DEFAULT="${ITUNDA_REGISTRY_HOST:-harbor.platform.itunda.internal}"
PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT="${ITUNDA_PRIVATE_CLOUD_REGISTRY_HOST:-$(server_ip):32000}"
REGISTRY_PROJECT_DEFAULT="${ITUNDA_HARBOR_PROJECT:-itunda}"
PULL_SECRET_NAME_DEFAULT="${ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME:-itunda-registry}"

service_keys() {
  printf '%s\n' api-gateway backend ledger-service payment-service
}

ensure_remote_build_headroom() {
  local minimum_mb="${ITUNDA_PRIVATE_CLOUD_MIN_BUILD_AVAILABLE_MB:-1536}"
  local available_mb

  if [[ ! "$minimum_mb" =~ ^[0-9]+$ ]] || (( minimum_mb < 256 )); then
    echo "ITUNDA_PRIVATE_CLOUD_MIN_BUILD_AVAILABLE_MB must be an integer of at least 256." >&2
    exit 1
  fi

  available_mb="$(run_vm "$PRIMARY_NODE" "awk '/MemAvailable:/{print int(\$2 / 1024)}' /proc/meminfo" | tr -d '\r')"
  if [[ ! "$available_mb" =~ ^[0-9]+$ ]] || (( available_mb < minimum_mb )); then
    echo "Refusing private-cloud image build: ${PRIMARY_NODE} has ${available_mb:-unknown} MiB available memory; at least ${minimum_mb} MiB is required. Use normal CI or free capacity before building." >&2
    exit 1
  fi
}

selected_services() {
  local service

  if [[ -n "${ITUNDA_PRIVATE_CLOUD_SERVICES:-}" ]]; then
    for service in ${ITUNDA_PRIVATE_CLOUD_SERVICES}; do
      printf '%s\n' "$service"
    done
    return 0
  fi

  service_keys
}

service_context() {
  case "$1" in
    backend) printf '%s\n' "$ROOT_DIR/services/backend" ;;
    api-gateway) printf '%s\n' "$ROOT_DIR/services/api-gateway" ;;
    ledger-service|payment-service) printf '%s\n' "$ROOT_DIR/services/microservices" ;;
    *) echo "Unknown service: $1" >&2; exit 1 ;;
  esac
}

service_dockerfile() {
  case "$1" in
    backend) printf '%s\n' "$ROOT_DIR/services/backend/Dockerfile" ;;
    api-gateway) printf '%s\n' "$ROOT_DIR/services/api-gateway/Dockerfile" ;;
    ledger-service) printf '%s\n' "$ROOT_DIR/services/microservices/ledger-service/Dockerfile" ;;
    payment-service) printf '%s\n' "$ROOT_DIR/services/microservices/payment-service/Dockerfile" ;;
    *) echo "Unknown service: $1" >&2; exit 1 ;;
  esac
}

service_env_var() {
  case "$1" in
    backend) printf 'ITUNDA_BACKEND_IMAGE\n' ;;
    api-gateway) printf 'ITUNDA_API_GATEWAY_IMAGE\n' ;;
    ledger-service) printf 'ITUNDA_LEDGER_IMAGE\n' ;;
    payment-service) printf 'ITUNDA_PAYMENT_IMAGE\n' ;;
    *) echo "Unknown service: $1" >&2; exit 1 ;;
  esac
}

image_ref() {
  local service="$1"
  local registry_host="${2:-$REGISTRY_HOST_DEFAULT}"
  local image_tag="${3:-$IMAGE_TAG_DEFAULT}"

  printf '%s/%s/%s:%s\n' "$registry_host" "$REGISTRY_PROJECT_DEFAULT" "$service" "$image_tag"
}

remote_path() {
  local path="$1"

  printf '%s/%s\n' "$DEPLOY_REPO_PATH" "${path#$ROOT_DIR/}"
}

build_service_image() {
  local service="$1"
  local registry_host="${2:-$REGISTRY_HOST_DEFAULT}"
  local image_tag="${3:-$IMAGE_TAG_DEFAULT}"
  local image

  image="$(image_ref "$service" "$registry_host" "$image_tag")"
  docker build -t "$image" -f "$(service_dockerfile "$service")" "$(service_context "$service")"
}

build_service_image_remote() {
  local service="$1"
  local registry_host="${2:-$PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT}"
  local image_tag="${3:-$IMAGE_TAG_DEFAULT}"
  local image

  ensure_remote_build_headroom
  image="$(image_ref "$service" "$registry_host" "$image_tag")"
  run_vm "$PRIMARY_NODE" "
    set -euo pipefail
    sudo docker build \
      -t '${image}' \
      -f '$(remote_path "$(service_dockerfile "$service")")' \
      '$(remote_path "$(service_context "$service")")'
  "
}

push_service_image() {
  local service="$1"
  local registry_host="${2:-$REGISTRY_HOST_DEFAULT}"
  local image_tag="${3:-$IMAGE_TAG_DEFAULT}"

  docker push "$(image_ref "$service" "$registry_host" "$image_tag")"
}

push_service_image_remote() {
  local service="$1"
  local registry_host="${2:-$PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT}"
  local image_tag="${3:-$IMAGE_TAG_DEFAULT}"

  run_vm "$PRIMARY_NODE" "
    set -euo pipefail
    sudo docker push '$(image_ref "$service" "$registry_host" "$image_tag")'
  "
}

cleanup_service_image_remote() {
  local service="$1"
  local registry_host="${2:-$PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT}"
  local image_tag="${3:-$IMAGE_TAG_DEFAULT}"

  run_vm "$PRIMARY_NODE" "
    set -euo pipefail
    sudo docker image rm '$(image_ref "$service" "$registry_host" "$image_tag")' >/dev/null 2>&1 || true
    sudo docker builder prune -af >/dev/null 2>&1 || true
  "
}

run_for_all_services() {
  local action="$1"
  local registry_host="${2:-$REGISTRY_HOST_DEFAULT}"
  local image_tag="${3:-$IMAGE_TAG_DEFAULT}"
  local service

  while IFS= read -r service; do
    [[ -z "$service" ]] && continue
    case "$action" in
      build) build_service_image "$service" "$registry_host" "$image_tag" ;;
      push) push_service_image "$service" "$registry_host" "$image_tag" ;;
      *)
        echo "Unknown action: $action" >&2
        exit 1
        ;;
    esac
  done < <(selected_services)
}

run_remote_for_all_services() {
  local action="$1"
  local registry_host="${2:-$PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT}"
  local image_tag="${3:-$IMAGE_TAG_DEFAULT}"
  local service

  require_cmd multipass
  if [[ "$action" == "build" ]]; then
    ensure_remote_build_headroom
  fi
  stage_repo_on_primary

  while IFS= read -r service; do
    [[ -z "$service" ]] && continue
    case "$action" in
      build) build_service_image_remote "$service" "$registry_host" "$image_tag" ;;
      push) push_service_image_remote "$service" "$registry_host" "$image_tag" ;;
      *)
        echo "Unknown action: $action" >&2
        exit 1
        ;;
    esac
  done < <(selected_services)
}

print_plan() {
  local registry_host="${1:-$REGISTRY_HOST_DEFAULT}"
  local image_tag="${2:-$IMAGE_TAG_DEFAULT}"
  local service

  echo "Private-cloud image plan"
  echo
  while IFS= read -r service; do
    [[ -z "$service" ]] && continue
    echo "- ${service}:"
    echo "  docker build -t $(image_ref "$service" "$registry_host" "$image_tag") -f $(service_dockerfile "$service") $(service_context "$service")"
    echo "  docker push $(image_ref "$service" "$registry_host" "$image_tag")"
  done < <(selected_services)
}

print_image_env() {
  local registry_host="${1:-$REGISTRY_HOST_DEFAULT}"
  local image_tag="${2:-$IMAGE_TAG_DEFAULT}"
  local pull_secret_name="$PULL_SECRET_NAME_DEFAULT"
  local service

  if [[ $# -ge 3 ]]; then
    pull_secret_name="$3"
  fi

  while IFS= read -r service; do
    [[ -z "$service" ]] && continue
    printf 'export %s=%q\n' "$(service_env_var "$service")" "$(image_ref "$service" "$registry_host" "$image_tag")"
  done < <(selected_services)
  printf 'export ITUNDA_PRIVATE_CLOUD_PULL_SECRET_NAME=%q\n' "$pull_secret_name"
}

build_push_private_cloud() {
  local registry_host="${1:-$PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT}"
  local image_tag="${2:-$IMAGE_TAG_DEFAULT}"
  local service

  require_cmd multipass
  ensure_remote_build_headroom
  stage_repo_on_primary

  while IFS= read -r service; do
    [[ -z "$service" ]] && continue
    build_service_image_remote "$service" "$registry_host" "$image_tag"
    push_service_image_remote "$service" "$registry_host" "$image_tag"
    cleanup_service_image_remote "$service" "$registry_host" "$image_tag"
  done < <(selected_services)
}

print_help() {
  cat <<'EOF'
Usage: scripts/private-cloud-images.sh <command> [args]

Commands:
  plan [registry-host] [tag]
    Print the exact docker build/push commands for the four private-cloud images.

  plan-private-cloud [registry-host] [tag]
    Print the exact build/push commands against the arm64 rehearsal registry on the private cloud.

  build [registry-host] [tag]
    Build all four service images for the target registry/project.

  push [registry-host] [tag]
    Push all four service images to the target registry/project.

  build-push [registry-host] [tag]
    Build then push all four service images.

  build-private-cloud [registry-host] [tag]
    Build all four service images on the primary Multipass VM for the private-cloud registry.

  push-private-cloud [registry-host] [tag]
    Push all four remote-built images from the primary Multipass VM to the private-cloud registry.

  build-push-private-cloud [registry-host] [tag]
    Stage the repo on the primary Multipass VM, build all four images there, then push them.

  print-env [registry-host] [tag]
    Print ITUNDA_*_IMAGE env vars and the pull-secret name for deploy/progressive commands.

  print-env-private-cloud [registry-host] [tag]
    Print ITUNDA_*_IMAGE env vars for the arm64 rehearsal registry on the private cloud.
EOF
}

case "$MODE" in
  plan)
    print_plan "${2:-$REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}"
    ;;
  plan-private-cloud)
    print_plan "${2:-$PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}"
    ;;
  build)
    run_for_all_services build "${2:-$REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}"
    ;;
  push)
    run_for_all_services push "${2:-$REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}"
    ;;
  build-push)
    run_for_all_services build "${2:-$REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}"
    run_for_all_services push "${2:-$REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}"
    ;;
  build-private-cloud)
    run_remote_for_all_services build "${2:-$PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}"
    ;;
  push-private-cloud)
    run_remote_for_all_services push "${2:-$PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}"
    ;;
  build-push-private-cloud)
    build_push_private_cloud "${2:-$PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}"
    ;;
  print-env)
    print_image_env "${2:-$REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}" "$PULL_SECRET_NAME_DEFAULT"
    ;;
  print-env-private-cloud)
    print_image_env "${2:-$PRIVATE_CLOUD_REGISTRY_HOST_DEFAULT}" "${3:-$IMAGE_TAG_DEFAULT}" ""
    ;;
  help|-h|--help)
    print_help
    ;;
  *)
    print_help
    exit 1
    ;;
esac
