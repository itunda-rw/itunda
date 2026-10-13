# Itunda Maps: Rwanda Nominatim on GCP

This is the deployment source of truth for Itunda's self-hosted, Rwanda-only place search and geocoding backend. It uses OpenStreetMap data from [Geofabrik's Rwanda extract](https://download.geofabrik.de/africa/rwanda.html) and the [mediagis Nominatim container](https://github.com/mediagis/nominatim-docker).

## Live resources

- GCP project: `itunda-org`
- Region / zone: `asia-northeast3` / `asia-northeast3-a`
- Compute Engine VM: `itunda-nominatim-rw` (`e2-standard-4`)
- Persistent disk: `itunda-nominatim-data` (150 GB `pd-balanced`, not auto-deleted with the VM)
- Reserved internal IP: `10.178.0.6` (`itunda-nominatim-ip`)
- Private service URL: `http://10.178.0.6:8080`
- Cloud Run API configuration: `ITUNDA_NOMINATIM_BASE_URL=http://10.178.0.6:8080`

The API's Spring property `itunda.nominatim.base-url` is populated by Spring Boot's relaxed environment-variable binding from `ITUNDA_NOMINATIM_BASE_URL`.

## Network boundary

Nominatim is not a public HTTP API. Firewall rules restrict TCP/8080 to the Cloud Run direct-VPC-egress subnet (`10.178.0.0/20`) and explicitly deny public TCP/8080. Public SSH is denied; SSH is permitted only from Google IAP's `35.235.240.0/20` range. The VM currently has an external address for outbound image/data downloads, but its application port and SSH port are not publicly reachable.

Do not add a public ingress rule, load balancer, or public DNS record for this internal endpoint. Keep Cloud Run VPC egress set to the `default` network / `default` subnet in `asia-northeast3`.

## Persistent storage and import

The container stores PostgreSQL data at `/var/lib/postgresql/16/main` and the flatnode file at `/nominatim/flatnode`; both map to the persistent disk under `/srv/itunda-nominatim`. The startup script formats the disk only when it has no filesystem, mounts it, and generates the database password on the VM rather than committing it to Git.

The first boot downloads the Rwanda PBF and imports it. This can take time; the API should not be considered fully validated until the health probe returns a real Kigali result. The startup script forwards container logs to the serial console and checks `/search?q=Kigali&format=json&limit=3&countrycodes=rw`. No synthetic place data is used.

## Verify

From a host inside the `asia-northeast3` VPC, test the real search endpoint:

```sh
curl --fail --show-error --max-time 30 \
  'http://10.178.0.6:8080/search?q=Kigali&format=json&limit=3&countrycodes=rw'
```

The response should be a JSON array containing real `display_name`, `lat`, and `lon` fields. Also test a real nearby category through the authenticated API:

```text
GET /api/v1/maps/around-me?lat=-1.9441&lng=30.0619&radiusKm=3
GET /api/v1/maps/nearby?category=RESTAURANTS&lat=-1.9441&lng=30.0619&radiusKm=3
```

Use a valid user access token for the API requests. An empty list during import or when OSM has no matching tagged features is an honest empty result, not proof of a successful search. Verify multiple categories (restaurants, cafes, pharmacies, banks, hospitals) before declaring Maps place discovery release-ready.

## Cost and operations

The VM, persistent disk, and public IPv4 address incur ongoing GCP charges while provisioned. Monitor usage and cost. Do not delete the persistent disk when replacing the VM. Plan periodic OSM replication updates and backups before treating the service as production-complete; this initial setup establishes the live search dependency, not a complete HA/backup program.

OpenStreetMap data attribution and ODbL obligations must be respected in user-facing Maps surfaces.
