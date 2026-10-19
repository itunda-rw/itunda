#!/bin/bash
set -Eeuo pipefail
exec > >(tee -a /var/log/itunda-nominatim-startup.log | logger -t itunda-nominatim-startup -s 2>/dev/console) 2>&1

echo "Itunda Rwanda Nominatim bootstrap started at $(date -Is)"
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y docker.io curl ca-certificates openssl
systemctl enable --now docker

DISK=/dev/disk/by-id/google-itunda-nominatim-data
for i in $(seq 1 60); do
  [ -b "$DISK" ] && break
  sleep 2
done
if [ ! -b "$DISK" ]; then
  echo "ERROR: persistent disk not found: $DISK"
  exit 1
fi
if ! blkid "$DISK" >/dev/null 2>&1; then
  mkfs.ext4 -F "$DISK"
fi
mkdir -p /srv/itunda-nominatim
if ! mountpoint -q /srv/itunda-nominatim; then
  mount "$DISK" /srv/itunda-nominatim
fi
grep -q 'google-itunda-nominatim-data' /etc/fstab || echo "$DISK /srv/itunda-nominatim ext4 defaults,nofail 0 2" >> /etc/fstab
mkdir -p /srv/itunda-nominatim/postgres /srv/itunda-nominatim/flatnode

docker pull mediagis/nominatim:5.3
if docker container inspect itunda-nominatim >/dev/null 2>&1; then
  docker start itunda-nominatim || true
else
  # Bind-mounted host directories must be writable by the Postgres account in the image.
  POSTGRES_UID="$(docker run --rm --entrypoint /bin/sh mediagis/nominatim:5.3 -c 'id -u postgres')"
  POSTGRES_GID="$(docker run --rm --entrypoint /bin/sh mediagis/nominatim:5.3 -c 'id -g postgres')"
  chown -R "$POSTGRES_UID:$POSTGRES_GID" /srv/itunda-nominatim/postgres /srv/itunda-nominatim/flatnode

  NOMINATIM_PASSWORD="$(openssl rand -hex 24)"
  docker run -d --name itunda-nominatim --restart unless-stopped --shm-size=1g \
    -e PBF_URL=https://download.geofabrik.de/africa/rwanda-latest.osm.pbf \
    -e REPLICATION_URL=https://download.geofabrik.de/africa/rwanda-updates/ \
    -e IMPORT_WIKIPEDIA=false -e THREADS=4 -e NOMINATIM_PASSWORD="$NOMINATIM_PASSWORD" \
    -p 8080:8080 \
    -v /srv/itunda-nominatim/postgres:/var/lib/postgresql/16/main \
    -v /srv/itunda-nominatim/flatnode:/nominatim/flatnode \
    mediagis/nominatim:5.3
fi

echo "Nominatim container started; Rwanda data import may take several minutes."
docker ps --filter name=itunda-nominatim
# Forward container initialization/import logs to the serial console for diagnosability.
( docker logs -f --since=1m itunda-nominatim 2>&1 | logger -t itunda-nominatim-container -s 2>/dev/console ) &

# Smoke-check real Kigali search for up to 12 hours; never synthesize results.
(
  for i in $(seq 1 720); do
    if curl --connect-timeout 3 --max-time 10 -fsS 'http://127.0.0.1:8080/status' >/dev/null; then
      if curl --connect-timeout 5 --max-time 30 -fsS 'http://127.0.0.1:8080/search?q=Kigali&format=json&limit=3&countrycodes=rw' | grep -q 'display_name'; then
        echo "NOMINATIM_KIGALI_SEARCH_READY $(date -Is)" | logger -t itunda-nominatim-health -s 2>/dev/console
        exit 0
      fi
    fi
    if (( i % 5 == 0 )); then
      echo "Nominatim import/search not ready yet (probe $i/720) at $(date -Is)" | logger -t itunda-nominatim-health -s 2>/dev/console
    fi
    sleep 60
  done
  echo "ERROR: Nominatim did not return a real Kigali result within 12 hours" | logger -t itunda-nominatim-health -s 2>/dev/console
) >/var/log/itunda-nominatim-health.log 2>&1 &

echo "Itunda Rwanda Nominatim bootstrap finished at $(date -Is)"
