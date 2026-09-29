#!/usr/bin/env bash

set -euo pipefail

backup_dir="${ITUNDA_PRIVATE_CLOUD_MYSQL_BACKUP_DIR:-/home/ubuntu/itunda-backups/mysql}"
retention_days="${ITUNDA_PRIVATE_CLOUD_MYSQL_BACKUP_RETENTION_DAYS:-7}"

install -d -m 0700 "$(dirname "$backup_dir")"
install -d -m 0700 "$backup_dir"
mysql_container="$(sudo docker ps --format '{{.Names}}' | awk '/^mysql-/{print; exit}')"
test -n "$mysql_container"
mysql_password="$(sudo docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' "$mysql_container" | sed -n 's/^MYSQL_ROOT_PASSWORD=//p' | head -n 1)"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
output="$backup_dir/itunda-mysql-$timestamp.sql.gz"
temporary="$output.partial"

sudo docker exec -e MYSQL_PWD="$mysql_password" "$mysql_container" \
  mysqldump -uroot --single-transaction --routines --events --databases itunda itunda_ledger itunda_payment \
  | gzip -c >"$temporary"
gzip -t "$temporary"
test "$(gzip -cd "$temporary" | grep -c '^CREATE DATABASE')" -ge 3
chmod 0600 "$temporary"
mv "$temporary" "$output"
find "$backup_dir" -type f -name 'itunda-mysql-*.sql.gz' -mtime +"$retention_days" -delete
sha256sum "$output"
