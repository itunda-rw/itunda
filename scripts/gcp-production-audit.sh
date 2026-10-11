#!/usr/bin/env bash
# Read-only GCP production posture audit for Itunda.
# Requires: gcloud, jq, permission to describe the named resources.
# Does not change cloud resources or print secret values.
set -Eeuo pipefail

PROJECT="${PROJECT:-itunda-org}"
REGION="${REGION:-asia-northeast3}"
SQL_INSTANCE="${SQL_INSTANCE:-itunda-mysql}"
REDIS_INSTANCE="${REDIS_INSTANCE:-itunda-redis}"
KAFKA_CLUSTER="${KAFKA_CLUSTER:-itunda-kafka}"
API_SERVICE="${API_SERVICE:-itunda-api}"

if ! command -v gcloud >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
  echo "ERROR: gcloud and jq are required." >&2
  exit 2
fi

pass() { printf 'PASS  %s\n' "$*"; }
warn() { printf 'WARN  %s\n' "$*"; }
fail() { printf 'FAIL  %s\n' "$*"; }
errors=0
warnings=0

fetch_json() {
  local label="$1"; shift
  local output
  if ! output="$("$@" 2>&1)"; then
    fail "$label could not be read: $output" >&2
    errors=$((errors + 1))
    return 1
  fi
  printf '%s' "$output"
}

echo "Itunda GCP production posture audit (read-only)"
echo "Project: $PROJECT | Region: $REGION"
echo

if sql="$(fetch_json "Cloud SQL $SQL_INSTANCE" gcloud sql instances describe "$SQL_INSTANCE" --project="$PROJECT" --format=json)"; then
  state="$(jq -r '.state // "UNKNOWN"' <<<"$sql")"
  tier="$(jq -r '.settings.tier // "UNKNOWN"' <<<"$sql")"
  availability="$(jq -r '.settings.availabilityType // "UNKNOWN"' <<<"$sql")"
  backup_enabled="$(jq -r '.settings.backupConfiguration.enabled // false' <<<"$sql")"
  pitr_enabled="$(jq -r 'if .settings.backupConfiguration.pointInTimeRecoveryEnabled != null then .settings.backupConfiguration.pointInTimeRecoveryEnabled else (.settings.backupConfiguration.binaryLogEnabled // false) end' <<<"$sql")"
  require_ssl="$(jq -r '.settings.ipConfiguration.requireSsl // false' <<<"$sql")"
  ssl_mode="$(jq -r '.settings.ipConfiguration.sslMode // "UNKNOWN"' <<<"$sql")"
  private_ip="$(jq -r '[.ipAddresses[]? | select(.type == "PRIVATE")] | length > 0' <<<"$sql")"
  if [[ "$state" == "RUNNABLE" ]]; then pass "Cloud SQL state RUNNABLE"; else fail "Cloud SQL state is $state"; errors=$((errors + 1)); fi
  if [[ "$private_ip" == "true" ]]; then pass "Cloud SQL has private IP"; else warn "Cloud SQL has no private IP"; warnings=$((warnings + 1)); fi
  if [[ "$backup_enabled" == "true" ]]; then pass "Cloud SQL automated backups enabled"; else fail "Cloud SQL automated backups DISABLED"; errors=$((errors + 1)); fi
  if [[ "$pitr_enabled" == "true" ]]; then pass "Cloud SQL point-in-time recovery enabled"; else fail "Cloud SQL PITR not enabled"; errors=$((errors + 1)); fi
  if [[ "$require_ssl" == "true" || "$ssl_mode" == "ENCRYPTED_ONLY" ]]; then pass "Cloud SQL enforces encrypted connections"; else warn "Cloud SQL does not enforce encrypted connections (requireSsl=$require_ssl, sslMode=$ssl_mode)"; warnings=$((warnings + 1)); fi
  echo "INFO  Cloud SQL tier=$tier availability=$availability (capacity/HA suitability requires workload review)"
else
  errors=$((errors + 1))
fi

if redis="$(fetch_json "Memorystore $REDIS_INSTANCE" gcloud redis instances describe "$REDIS_INSTANCE" --region="$REGION" --project="$PROJECT" --format=json)"; then
  state="$(jq -r '.state // "UNKNOWN"' <<<"$redis")"
  transit="$(jq -r '.transitEncryptionMode // "UNKNOWN"' <<<"$redis")"
  auth="$(jq -r '.authEnabled // false' <<<"$redis")"
  if [[ "$state" == "READY" ]]; then pass "Memorystore state READY"; else fail "Memorystore state is $state"; errors=$((errors + 1)); fi
  if [[ "$transit" == "SERVER_AUTHENTICATION" ]]; then pass "Memorystore TLS in-transit encryption enabled"; else warn "Memorystore in-transit encryption is $transit"; warnings=$((warnings + 1)); fi
  if [[ "$auth" == "true" ]]; then pass "Memorystore AUTH enabled"; else warn "Memorystore AUTH is disabled"; warnings=$((warnings + 1)); fi
else
  errors=$((errors + 1))
fi

if kafka="$(fetch_json "Managed Kafka $KAFKA_CLUSTER" gcloud managed-kafka clusters describe "$KAFKA_CLUSTER" --location="$REGION" --project="$PROJECT" --format=json)"; then
  state="$(jq -r '.state // "UNKNOWN"' <<<"$kafka")"
  if [[ "$state" == "ACTIVE" ]]; then pass "Managed Kafka state ACTIVE"; else fail "Managed Kafka state is $state"; errors=$((errors + 1)); fi
  warn "Kafka replication factor, ACL/TLS/SASL, topic retention, consumer lag, and produce/consume tests require separate verification"
  warnings=$((warnings + 1))
else
  errors=$((errors + 1))
fi

if api="$(fetch_json "Cloud Run $API_SERVICE" gcloud run services describe "$API_SERVICE" --region="$REGION" --project="$PROJECT" --format=json)"; then
  ready="$(jq -r '[.status.conditions[]? | select(.type == "Ready") | .status][0] // "UNKNOWN"' <<<"$api")"
  ingress="$(jq -r '.metadata.annotations["run.googleapis.com/ingress"] // "UNKNOWN"' <<<"$api")"
  image="$(jq -r '.spec.template.spec.containers[0].image // "UNKNOWN"' <<<"$api")"
  env_names="$(jq -r '[.spec.template.spec.containers[0].env[]?.name] | join(",")' <<<"$api")"
  if [[ "$ready" == "True" ]]; then pass "Cloud Run API Ready"; else fail "Cloud Run API Ready status is $ready"; errors=$((errors + 1)); fi
  echo "INFO  Cloud Run ingress=$ingress image=$image"
  for dependency in DB_HOST DB_NAME REDIS_HOST KAFKA_BOOTSTRAP_SERVERS; do
    if [[ ",$env_names," == *",$dependency,"* ]]; then pass "API has $dependency configuration"; else warn "API has no $dependency environment entry"; warnings=$((warnings + 1)); fi
  done
  warn "Environment entries prove configuration only, not successful dependency connectivity or protected-route authorization"
  warnings=$((warnings + 1))
else
  errors=$((errors + 1))
fi

echo
echo "Summary: $errors failure(s), $warnings warning(s)"
if (( errors > 0 )); then
  echo "RESULT: FAIL — address failed data-protection/availability checks before treating the environment as production-hardened."
  exit 1
fi
if (( warnings > 0 )); then
  echo "RESULT: REVIEW — no hard failure detected, but warnings remain."
  exit 0
fi
echo "RESULT: PASS"
