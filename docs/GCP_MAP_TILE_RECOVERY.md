# GCP map tile and glyph recovery

Status: blocked on locating the canonical Rwanda tile archive and Noto glyph assets. Do not point production clients at a guessed hostname or replace the Rwanda data with a demo tileset.

Tracking: [Issue #59](https://github.com/itunda-rw/itunda/issues/59)

## Confirmed failure mode

- Android's `TILES_BASE_URL` and `GLYPHS_BASE_URL` build defaults use `http://192.168.252.4:8090` and `:8091`.
- iOS hard-codes the same private-network tile address and glyph service.
- The web maps MFE expects same-origin `/tiles/rwanda/{z}/{x}/{y}.mvt` and `/glyphs/{fontstack}/{range}.pbf`.
- GCP inventory checked on 2026-10-13 showed no dedicated map service and only the Cloud Build bucket. This does not prove the original archive is lost; its source location still needs to be identified.
- Repository history says the original vector tiles were built using Planetiler from a Geofabrik Rwanda OpenStreetMap extract. The Noto Sans Regular/Bold glyph PBFs came from the standard `openmaptiles/fonts` source.
- Historical docs say the former private-cloud deployment was reduced to a single node, `itunda-dc-a` at `192.168.252.4`; `itunda-dc-b` was decommissioned. The LAN address is not a usable public-device endpoint. Current GCP inventory does not establish whether the old node or an export/backup is still recoverable.

## Recovery decision tree

1. **Recover original first.** Search the surviving private-cloud node's data directories, container volumes, service configuration, and any VM/host backups or exports for the exact Rwanda `.pmtiles` archive and glyph PBF directory. If access is unavailable, record that as unverified rather than claiming the files are gone.
2. **Verify recovered artifacts.** Record file names, sizes, SHA-256 checksums, source extract date, Planetiler build configuration/version if available, and OpenStreetMap/Geofabrik attribution and license metadata. Inspect archive metadata and confirm expected vector layers before publishing.
3. **Only if original recovery is confirmed impossible, approve a reproducible rebuild.** Use a dated Geofabrik Rwanda OSM extract and a documented, pinned Planetiler pipeline; regenerate the required Noto glyph PBFs from the documented OpenMapTiles fonts source. Record that this is a new dataset/build, its source date, checksums, configuration, and license notices. Do not silently replace the original dataset.
4. Keep the canonical build artifacts and provenance in Itunda-controlled GCP storage; do not commit large binary archives into Git.

## Target topology

Keep Itunda-owned map data and GCP as the hosting platform:

1. Store the verified Rwanda `.pmtiles` archive in a dedicated GCS bucket in `asia-northeast3` (or the agreed data residency location). Preserve source/license metadata and record the archive SHA-256.
2. Serve the archive through a tested PMTiles Z/X/Y endpoint that supports correct vector-tile MIME types and caching. It may read GCS range requests or use a controlled read-only service; it must not load the entire archive into memory per request.
3. Serve the Noto glyph PBF assets under `/glyphs/{fontstack}/{range}.pbf` from a read-only HTTPS endpoint.
4. Route `/tiles/*` and `/glyphs/*` for the web origin through GCP URL-map path rules to those services, preserving existing host routing and API routes. Verify precedence against every existing matcher before applying the URL-map change.
5. Configure Android and iOS to use verified public HTTPS tile and glyph endpoints. Keep Gradle property overrides for staging/testing; remove every production fallback to `192.168.252.4`.
6. Add health/readiness checks, request metrics, bounded cache headers, access logging, and a cost/egress alert. Map tiles and glyphs must never share account/API response caching rules.

## Release acceptance checklist

- [ ] Original assets recovered, or explicit approval and documented evidence for a reproducible rebuild.
- [ ] Assets are licensed, source-dated, inspected, and checksummed.
- [ ] A known in-Rwanda vector tile returns HTTP 200 with the expected vector-tile content type; an out-of-range tile returns documented empty/not-found behavior.
- [ ] A known font stack/range returns HTTP 200 with PBF bytes.
- [ ] HTTPS, CORS (web only), byte/range behavior where applicable, cache headers, and compression behavior are verified.
- [ ] Web map loads without console/network errors on `https://app.itunda.im`.
- [ ] Android and iOS contain no private LAN map URLs in production configuration.
- [ ] Map pan/zoom, place search, reverse geocoding, and directions are tested separately; tile delivery alone does not prove these services work.
- [ ] CI passes and a new Android APK is assembled; install and login/map smoke tests are recorded separately from build success.

## Safe deployment rule

Do not publish a Cloud Run service, change production URL maps, or build a release claiming map functionality until canonical assets and the target endpoint are verified. A successful application build alone is not map-service validation.
