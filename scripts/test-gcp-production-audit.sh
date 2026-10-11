#!/usr/bin/env bash
# Offline smoke tests for gcp-production-audit.sh. No credentials or cloud access needed.
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/bin"
cat >"$tmp/bin/gcloud" <<'MOCK'
#!/usr/bin/env bash
set -Eeuo pipefail
joined="$*"
case "$joined" in
  "sql instances describe "*)
    backup="${MOCK_BACKUP_ENABLED:-true}"
    pitr="${MOCK_PITR_ENABLED:-true}"
    pitr_field="${MOCK_PITR_FIELD:-pointInTimeRecoveryEnabled}"
    cat <<JSON
{"state":"RUNNABLE","settings":{"tier":"db-f1-micro","availabilityType":"REGIONAL","backupConfiguration":{"enabled":$backup,"$pitr_field":$pitr},"ipConfiguration":{"requireSsl":true,"sslMode":"ENCRYPTED_ONLY"}},"ipAddresses":[{"type":"PRIVATE","ipAddress":"10.0.0.2"}]}
JSON
    ;;
  "redis instances describe "*)
    cat <<'JSON'
{"state":"READY","transitEncryptionMode":"SERVER_AUTHENTICATION","authEnabled":true}
JSON
    ;;
  "managed-kafka clusters describe "*)
    cat <<'JSON'
{"state":"ACTIVE"}
JSON
    ;;
  "run services describe "*)
    cat <<'JSON'
{"metadata":{"annotations":{"run.googleapis.com/ingress":"internal"}},"status":{"conditions":[{"type":"Ready","status":"True"}]},"spec":{"template":{"spec":{"containers":[{"image":"example.invalid/itunda-api:test","env":[{"name":"DB_HOST"},{"name":"DB_NAME"},{"name":"REDIS_HOST"},{"name":"KAFKA_BOOTSTRAP_SERVERS"}]}]}}}}
JSON
    ;;
  *)
    echo "Unexpected mocked gcloud command: $joined" >&2
    exit 2
    ;;
esac
MOCK
chmod +x "$tmp/bin/gcloud"
export PATH="$tmp/bin:$PATH"
command -v jq >/dev/null || { echo "jq is required for this test" >&2; exit 2; }

good_output="$(bash "$ROOT/scripts/gcp-production-audit.sh")"
grep -q 'RESULT: REVIEW' <<<"$good_output" || {
  printf 'Expected healthy fixture to return REVIEW because connectivity/Kafka checks are intentionally warnings. Output:\n%s\n' "$good_output" >&2
  exit 1
}

set +e
bad_output="$(MOCK_BACKUP_ENABLED=false bash "$ROOT/scripts/gcp-production-audit.sh" 2>&1)"
bad_status=$?
set -e
if [[ "$bad_status" -eq 0 ]] || ! grep -q 'Cloud SQL automated backups DISABLED' <<<"$bad_output"; then
  printf 'Expected disabled backups to fail the audit. Exit=%s output:\n%s\n' "$bad_status" "$bad_output" >&2
  exit 1
fi


legacy_pitr_output="$(MOCK_PITR_FIELD=binaryLogEnabled bash "$ROOT/scripts/gcp-production-audit.sh")"
grep -q 'PASS  Cloud SQL point-in-time recovery enabled' <<<"$legacy_pitr_output" || {
  printf 'Expected legacy binaryLogEnabled PITR field to be recognized. Output:\n%s\n' "$legacy_pitr_output" >&2
  exit 1
}

set +e
no_pitr_output="$(MOCK_PITR_ENABLED=false bash "$ROOT/scripts/gcp-production-audit.sh" 2>&1)"
no_pitr_status=$?
set -e
if [[ "$no_pitr_status" -eq 0 ]] || ! grep -q 'Cloud SQL PITR not enabled' <<<"$no_pitr_output"; then
  printf 'Expected disabled PITR to fail the audit. Exit=%s output:\n%s\n' "$no_pitr_status" "$no_pitr_output" >&2
  exit 1
fi

echo "PASS: healthy mocked resources produce REVIEW with explicit warnings"
echo "PASS: current pointInTimeRecoveryEnabled and legacy binaryLogEnabled fields are recognized"
echo "PASS: disabled Cloud SQL PITR produces a failing exit code"
echo "PASS: disabled Cloud SQL backups produce a failing exit code"
