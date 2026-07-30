#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFESTS=(
  "$ROOT_DIR/infra/k8s/production/api-gateway.yaml"
  "$ROOT_DIR/infra/k8s/progressive-delivery/api-gateway-rollout.yaml"
  "$ROOT_DIR/infra/k8s/staging/api-gateway.yaml"
)
DOCKERFILE="$ROOT_DIR/services/api-gateway/Dockerfile"

[[ -f "$DOCKERFILE" ]] || { echo "Missing API gateway Dockerfile: $DOCKERFILE" >&2; exit 1; }
if ! grep -Eq '^USER[[:space:]]+itunda[[:space:]]*$' "$DOCKERFILE"; then
  echo "API gateway Dockerfile must run as the non-root itunda user." >&2
  exit 1
fi

ruby -ryaml -e '
  required = %w[runAsNonRoot seccompProfile]
  ARGV.each do |path|
    documents = YAML.load_stream(File.read(path)).compact
    workload = documents.find { |doc| %w[Deployment Rollout].include?(doc["kind"]) && doc.dig("metadata", "name") == "api-gateway" }
    abort("#{path}: missing api-gateway workload") unless workload
    pod = workload.dig("spec", "template", "spec") || {}
    pod_security = pod["securityContext"] || {}
    abort("#{path}: runAsNonRoot must be true") unless pod_security["runAsNonRoot"] == true
    abort("#{path}: seccompProfile.type must be RuntimeDefault") unless pod_security.dig("seccompProfile", "type") == "RuntimeDefault"
    abort("#{path}: automountServiceAccountToken must be false") unless pod["automountServiceAccountToken"] == false

    container = (pod["containers"] || []).find { |item| item["name"] == "api-gateway" }
    abort("#{path}: missing api-gateway container") unless container
    security = container["securityContext"] || {}
    abort("#{path}: allowPrivilegeEscalation must be false") unless security["allowPrivilegeEscalation"] == false
    abort("#{path}: readOnlyRootFilesystem must be true") unless security["readOnlyRootFilesystem"] == true
    abort("#{path}: capabilities.drop must include ALL") unless (security.dig("capabilities", "drop") || []).include?("ALL")
    abort("#{path}: /tmp must mount the tmp volume") unless (container["volumeMounts"] || []).any? { |mount| mount["name"] == "tmp" && mount["mountPath"] == "/tmp" }
    abort("#{path}: tmp must be an emptyDir volume") unless (pod["volumes"] || []).any? { |volume| volume["name"] == "tmp" && volume.key?("emptyDir") }
  end
  puts "API gateway runtime security controls are present in every deployment manifest."
' "${MANIFESTS[@]}"
