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
  # card-service added 2026-09-01 -- the first independently-deployable itunda
  # product (see docs/ARCHITECTURE.md). Unlike ledger-service/payment-service, its
  # build context is services/backend itself (see service_context below), same as
  # backend -- it's still part of that Gradle multi-module build, just a
  # different bootJar target. `ITUNDA_PRIVATE_CLOUD_SERVICES=card-service` builds/
  # pushes ONLY this image, without touching the others.
  # insurance-service added 2026-09-01 -- the second independently-deployable
  # itunda product, same shape as card-service (services/backend build context,
  # different bootJar target).
  # agents-service added 2026-09-01 -- the third independently-deployable itunda
  # product, same shape as card-service/insurance-service.
  # transit-service added 2026-09-01 -- the fourth independently-deployable
  # itunda product, same shape as the three before it.
  # certificate-service added 2026-09-01 -- the fifth independently-deployable
  # itunda product, same shape as the four before it.
  # bills-service added 2026-09-01 -- the sixth independently-deployable
  # itunda product, same shape as the five before it.
  # vehicle-service added 2026-09-01 -- the seventh independently-deployable
  # itunda product, same shape as the six before it.
  # partners-service added 2026-09-01 -- the eighth independently-deployable
  # itunda product, same shape as the seven before it.
  # identity-service added 2026-09-01 -- the ninth independently-deployable
  # itunda product, same shape as the eight before it.
  # overview-service added 2026-09-01 -- the tenth independently-deployable
  # itunda product, same shape as the nine before it.
  # knowledge-service added 2026-09-01 -- the eleventh independently-deployable
  # itunda product, same shape as the ten before it.
  # notifications-service added 2026-09-01 -- the twelfth
  # independently-deployable itunda product, same shape as the eleven before
  # it.
  # analytics-service added 2026-09-02 -- the thirteenth
  # independently-deployable itunda product, same shape as the twelve before
  # it.
  printf '%s\n' api-gateway backend ledger-service payment-service card-service insurance-service agents-service transit-service certificate-service bills-service vehicle-service partners-service identity-service overview-service knowledge-service notifications-service analytics-service
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
    backend|card-service|insurance-service|agents-service|transit-service|certificate-service|bills-service|vehicle-service|partners-service|identity-service|overview-service|knowledge-service|notifications-service|analytics-service) printf '%s\n' "$ROOT_DIR/services/backend" ;;
    api-gateway) printf '%s\n' "$ROOT_DIR/services/api-gateway" ;;
    ledger-service|payment-service) printf '%s\n' "$ROOT_DIR/services/microservices" ;;
    *) echo "Unknown service: $1" >&2; exit 1 ;;
  esac
}

service_dockerfile() {
  case "$1" in
    backend) printf '%s\n' "$ROOT_DIR/services/backend/Dockerfile" ;;
    card-service) printf '%s\n' "$ROOT_DIR/services/backend/card-service/Dockerfile" ;;
    insurance-service) printf '%s\n' "$ROOT_DIR/services/backend/insurance-service/Dockerfile" ;;
    agents-service) printf '%s\n' "$ROOT_DIR/services/backend/agents-service/Dockerfile" ;;
    transit-service) printf '%s\n' "$ROOT_DIR/services/backend/transit-service/Dockerfile" ;;
    certificate-service) printf '%s\n' "$ROOT_DIR/services/backend/certificate-service/Dockerfile" ;;
    bills-service) printf '%s\n' "$ROOT_DIR/services/backend/bills-service/Dockerfile" ;;
    vehicle-service) printf '%s\n' "$ROOT_DIR/services/backend/vehicle-service/Dockerfile" ;;
    partners-service) printf '%s\n' "$ROOT_DIR/services/backend/partners-service/Dockerfile" ;;
    identity-service) printf '%s\n' "$ROOT_DIR/services/backend/identity-service/Dockerfile" ;;
    overview-service) printf '%s\n' "$ROOT_DIR/services/backend/overview-service/Dockerfile" ;;
    knowledge-service) printf '%s\n' "$ROOT_DIR/services/backend/knowledge-service/Dockerfile" ;;
    notifications-service) printf '%s\n' "$ROOT_DIR/services/backend/notifications-service/Dockerfile" ;;
    analytics-service) printf '%s\n' "$ROOT_DIR/services/backend/analytics-service/Dockerfile" ;;
    api-gateway) printf '%s\n' "$ROOT_DIR/services/api-gateway/Dockerfile" ;;
    ledger-service) printf '%s\n' "$ROOT_DIR/services/microservices/ledger-service/Dockerfile" ;;
    payment-service) printf '%s\n' "$ROOT_DIR/services/microservices/payment-service/Dockerfile" ;;
    *) echo "Unknown service: $1" >&2; exit 1 ;;
  esac
}

service_env_var() {
  case "$1" in
    backend) printf 'ITUNDA_BACKEND_IMAGE\n' ;;
    card-service) printf 'ITUNDA_CARD_SERVICE_IMAGE\n' ;;
    insurance-service) printf 'ITUNDA_INSURANCE_SERVICE_IMAGE\n' ;;
    agents-service) printf 'ITUNDA_AGENTS_SERVICE_IMAGE\n' ;;
    transit-service) printf 'ITUNDA_TRANSIT_SERVICE_IMAGE\n' ;;
    certificate-service) printf 'ITUNDA_CERTIFICATE_SERVICE_IMAGE\n' ;;
    bills-service) printf 'ITUNDA_BILLS_SERVICE_IMAGE\n' ;;
    vehicle-service) printf 'ITUNDA_VEHICLE_SERVICE_IMAGE\n' ;;
    partners-service) printf 'ITUNDA_PARTNERS_SERVICE_IMAGE\n' ;;
    identity-service) printf 'ITUNDA_IDENTITY_SERVICE_IMAGE\n' ;;
    overview-service) printf 'ITUNDA_OVERVIEW_SERVICE_IMAGE\n' ;;
    knowledge-service) printf 'ITUNDA_KNOWLEDGE_SERVICE_IMAGE\n' ;;
    notifications-service) printf 'ITUNDA_NOTIFICATIONS_SERVICE_IMAGE\n' ;;
    analytics-service) printf 'ITUNDA_ANALYTICS_SERVICE_IMAGE\n' ;;
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
    Print the exact docker build/push commands for the seventeen private-cloud images.

  plan-private-cloud [registry-host] [tag]
    Print the exact build/push commands against the arm64 rehearsal registry on the private cloud.

  build [registry-host] [tag]
    Build all seventeen service images for the target registry/project.

  push [registry-host] [tag]
    Push all seventeen service images to the target registry/project.

  build-push [registry-host] [tag]
    Build then push all seventeen service images.

  build-private-cloud [registry-host] [tag]
    Build all seventeen service images on the primary Multipass VM for the private-cloud registry.

  push-private-cloud [registry-host] [tag]
    Push all seventeen remote-built images from the primary Multipass VM to the private-cloud registry.

  build-push-private-cloud [registry-host] [tag]
    Stage the repo on the primary Multipass VM, build all seventeen images there, then push them.

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
