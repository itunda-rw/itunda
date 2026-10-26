#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/services/backend"
FORBIDDEN='itunda-dev-secret-do-not-use-in-production'

if grep -R -n -F "$FORBIDDEN" "$BACKEND_DIR" --include='application.yml' --include='JwtService.kt'; then
  echo "FAIL: backend runtime configuration contains the development JWT fallback" >&2
  exit 1
fi

if ! grep -Fq '@Value("\${itunda.jwt.secret}")' "$BACKEND_DIR/auth/src/main/kotlin/rw/itunda/auth/JwtService.kt"; then
  echo "FAIL: JwtService must require an explicitly configured secret" >&2
  exit 1
fi

if ! grep -Fq 'secret: ${JWT_SECRET}' "$BACKEND_DIR/app/src/main/resources/application.yml"; then
  echo "FAIL: canonical backend application.yml must require JWT_SECRET" >&2
  exit 1
fi

echo "JWT secret configuration checks passed: no backend application.yml or JwtService fallback found."
