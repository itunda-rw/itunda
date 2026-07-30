#!/usr/bin/env bash

set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/private-cloud-lib.sh"

BACKUP_DIR="${ITUNDA_PRIVATE_CLOUD_MYSQL_BACKUP_DIR:-/home/ubuntu/itunda-backups/mysql}"
RETENTION_DAYS="${ITUNDA_PRIVATE_CLOUD_MYSQL_BACKUP_RETENTION_DAYS:-7}"
VM_BACKUP_SCRIPT_PATH="${ITUNDA_PRIVATE_CLOUD_MYSQL_VM_BACKUP_SCRIPT_PATH:-$ROOT_DIR/scripts/private-cloud-mysql-backup-vm.sh}"

backup_node() {
  local node="$1"

  run_vm "$node" "
    set -euo pipefail
    backup_dir='${BACKUP_DIR}'
    retention_days='${RETENTION_DAYS}'
    install -d -m 0700 \"\$(dirname \"\$backup_dir\")\"
    install -d -m 0700 \"\$backup_dir\"
    mysql_container=\$(sudo docker ps --format '{{.Names}}' | awk '/^mysql-/{print; exit}')
    test -n \"\$mysql_container\"
    mysql_password=\$(sudo docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' \"\$mysql_container\" | sed -n 's/^MYSQL_ROOT_PASSWORD=//p' | head -n 1)
    timestamp=\$(date -u +%Y%m%dT%H%M%SZ)
    output=\"\$backup_dir/itunda-mysql-\$timestamp.sql.gz\"
    temporary=\"\$output.partial\"
    sudo docker exec -e MYSQL_PWD=\"\$mysql_password\" \"\$mysql_container\" \\
      mysqldump -uroot --single-transaction --routines --events --databases itunda itunda_ledger itunda_payment \\
      | gzip -c > \"\$temporary\"
    gzip -t \"\$temporary\"
    test \"\$(gzip -cd \"\$temporary\" | grep -c '^CREATE DATABASE')\" -ge 3
    chmod 0600 \"\$temporary\"
    mv \"\$temporary\" \"\$output\"
    find \"\$backup_dir\" -type f -name 'itunda-mysql-*.sql.gz' -mtime +\"\$retention_days\" -delete
    sha256sum \"\$output\"
  "
}

install_timer_node() {
  local node="$1"
  local tmpdir

  [[ -f "$VM_BACKUP_SCRIPT_PATH" ]] || { echo "File not found: $VM_BACKUP_SCRIPT_PATH" >&2; exit 1; }
  tmpdir="$(mktemp -d "${TMPDIR:-/tmp}/itunda-mysql-backup.XXXXXX")"
  cp "$VM_BACKUP_SCRIPT_PATH" "$tmpdir/itunda-mysql-backup.sh"
  cat >"$tmpdir/itunda-mysql-backup.service" <<'EOF'
[Unit]
Description=Create a verified local Itunda MySQL recovery backup
After=docker.service
Wants=docker.service

[Service]
Type=oneshot
User=ubuntu
ExecStart=/usr/local/bin/itunda-mysql-backup.sh
EOF
  cat >"$tmpdir/itunda-mysql-backup.timer" <<'EOF'
[Unit]
Description=Run the local Itunda MySQL backup daily

[Timer]
OnCalendar=*-*-* 02:30:00 UTC
Persistent=true
Unit=itunda-mysql-backup.service

[Install]
WantedBy=timers.target
EOF
  multipass transfer "$tmpdir/itunda-mysql-backup.sh" "$tmpdir/itunda-mysql-backup.service" "$tmpdir/itunda-mysql-backup.timer" "${node}:/tmp/"
  run_vm "$node" "
    set -euo pipefail
    sudo install -m 0755 /tmp/itunda-mysql-backup.sh /usr/local/bin/itunda-mysql-backup.sh
    sudo install -m 0644 /tmp/itunda-mysql-backup.service /etc/systemd/system/itunda-mysql-backup.service
    sudo install -m 0644 /tmp/itunda-mysql-backup.timer /etc/systemd/system/itunda-mysql-backup.timer
    sudo systemctl daemon-reload
    sudo systemctl enable --now itunda-mysql-backup.timer
    rm -f /tmp/itunda-mysql-backup.sh /tmp/itunda-mysql-backup.service /tmp/itunda-mysql-backup.timer
  "
  rm -rf "$tmpdir"
}

case "${1:-backup}" in
  backup)
    require_cmd multipass
    for node in "${NODES[@]}"; do backup_node "$node"; done
    ;;
  install-timer)
    require_cmd multipass
    for node in "${NODES[@]}"; do install_timer_node "$node"; done
    ;;
  *)
    echo "Usage: $0 [backup|install-timer]" >&2
    exit 1
    ;;
esac
