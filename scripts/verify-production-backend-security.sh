#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT_DIR/infra/k8s/production/backend.yaml"

[[ -f "$MANIFEST" ]] || { echo "Missing production backend manifest: $MANIFEST" >&2; exit 1; }

grep -q 'name: SPRING_PROFILES_ACTIVE' "$MANIFEST" || { echo "Production backend must activate Spring profile 'production'." >&2; exit 1; }
grep -q 'value: "production"' "$MANIFEST" || { echo "SPRING_PROFILES_ACTIVE must be exactly production." >&2; exit 1; }
grep -q 'name: JWT_SECRET' "$MANIFEST" || { echo "Production backend must define JWT_SECRET." >&2; exit 1; }
grep -q 'name: itunda-jwt-secret' "$MANIFEST" || { echo "JWT_SECRET must come from the Kubernetes Secret itunda-jwt-secret." >&2; exit 1; }
grep -q 'name: ITUNDA_PROVIDER_SIMULATION_ENABLED' "$MANIFEST" || { echo "Production backend must explicitly disable provider simulation." >&2; exit 1; }
grep -q 'value: "false"' "$MANIFEST" || { echo "Production provider simulation must be explicitly false." >&2; exit 1; }

echo "Production backend security profile is fail-closed."
