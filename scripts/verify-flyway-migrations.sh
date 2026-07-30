#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATION_DIR="$ROOT_DIR/services/backend/app/src/main/resources/db/migration"
seen_versions_file="$(mktemp "${TMPDIR:-/tmp}/itunda-flyway-versions.XXXXXX")"
trap 'rm -f "$seen_versions_file"' EXIT
count=0

for migration in "$MIGRATION_DIR"/V*__*.sql; do
  [[ -e "$migration" ]] || continue
  filename="${migration##*/}"
  if [[ ! "$filename" =~ ^V([1-9][0-9]*)__[A-Za-z0-9_]+\.sql$ ]]; then
    echo "Invalid Flyway migration filename: ${filename}" >&2
    exit 1
  fi

  version="${BASH_REMATCH[1]}"
  previous="$(awk -F '\t' -v version="$version" '$1 == version { print $2; exit }' "$seen_versions_file")"
  if [[ -n "$previous" ]]; then
    echo "Duplicate Flyway migration version V${version}: ${previous} and ${filename}" >&2
    exit 1
  fi
  printf '%s\t%s\n' "$version" "$filename" >>"$seen_versions_file"
  count=$((count + 1))
done

if (( count == 0 )); then
  echo "No Flyway migrations found in ${MIGRATION_DIR}" >&2
  exit 1
fi

echo "Validated ${count} unique Flyway migrations."
