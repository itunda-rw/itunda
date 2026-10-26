#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG="$ROOT_DIR/nginx.conf"
command -v docker >/dev/null || { echo "docker is required" >&2; exit 2; }
command -v curl >/dev/null || { echo "curl is required" >&2; exit 2; }

TMP_DIR="$(mktemp -d)"
CONTAINER_ID=""
cleanup() {
  if [[ -n "$CONTAINER_ID" ]]; then docker rm -f "$CONTAINER_ID" >/dev/null 2>&1 || true; fi
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

printf '<!doctype html><html><body>Itunda test shell</body></html>\n' > "$TMP_DIR/index.html"
mkdir -p "$TMP_DIR/.git" "$TMP_DIR/.well-known"
printf 'must-not-be-served\n' > "$TMP_DIR/.env"
printf '[core]\nrepositoryformatversion = 0\n' > "$TMP_DIR/.git/config"
printf 'public-well-known-test\n' > "$TMP_DIR/.well-known/security.txt"

CONTAINER_ID="$(docker run -d --rm -p 127.0.0.1::80 \
  -v "$CONFIG:/etc/nginx/conf.d/default.conf:ro" nginx:1.27-alpine)"
docker exec "$CONTAINER_ID" mkdir -p /usr/share/nginx/html/.git /usr/share/nginx/html/.well-known
docker cp "$TMP_DIR/index.html" "$CONTAINER_ID:/usr/share/nginx/html/index.html"
docker cp "$TMP_DIR/.env" "$CONTAINER_ID:/usr/share/nginx/html/.env"
docker cp "$TMP_DIR/.git/config" "$CONTAINER_ID:/usr/share/nginx/html/.git/config"
docker cp "$TMP_DIR/.well-known/security.txt" "$CONTAINER_ID:/usr/share/nginx/html/.well-known/security.txt"
PORT="$(docker port "$CONTAINER_ID" 80/tcp | sed 's/.*://')"
BASE="http://127.0.0.1:$PORT"

# Wait for Nginx to become ready.
for _ in $(seq 1 30); do
  if curl --silent --fail "$BASE/" >/dev/null; then break; fi
  sleep 1
done

assert_status() {
  local path="$1" expected="$2" actual
  actual="$(curl --silent --output "$TMP_DIR/response" --write-out '%{http_code}' "$BASE$path")"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL $path: expected HTTP $expected, got $actual" >&2
    cat "$TMP_DIR/response" >&2
    exit 1
  fi
  echo "PASS $path -> HTTP $actual"
}

assert_status "/healthz" 200
assert_status "/index.html" 200
assert_status "/" 200
assert_status "/account/settings" 200
assert_status "/.git/config" 404
assert_status "/.env" 404
assert_status "/.backup" 404
assert_status "/.well-known/security.txt" 200

if grep -q 'must-not-be-served' "$TMP_DIR/response"; then
  echo "Unexpected hidden-file content in response" >&2
  exit 1
fi

echo "Nginx hidden-file regression tests passed."
