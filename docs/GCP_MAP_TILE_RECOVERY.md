# GCP map tile and glyph recovery

Status: blocked on locating the canonical Rwanda tile archive and Noto glyph assets. Do not point production clients at a guessed hostname or replace the Rwanda data with a demo tileset.

Tracking: [Issue #59](https://github.com/itunda-rw/itunda/issues/59)

## Confirmed failure mode

- Android's `TILES_BASE_URL` and `GLYPHS_BASE_URL` build defaults use `http://192.168.252.4:8090` and `:8091`.
- iOS hard-codes the same private-network tile address.
- The web maps MFE expects same-origin `/tiles/rwanda/{z}/{x}/{y}.mvt` and `/glyphs/{fontstack}/{range}.pbf`.
- GCP inventory checked on 2026-10-13 showed no dedicated map service and only the Cloud Build bucket. This does not prove the original archive is lost; its source location still needs to be identified.

## Target topology

Keep Itunda-owned map data and GCP as the hosting platform:

1. Store the canonical Rwanda `.pmtiles` archive in a dedicated GCS bucket in `asia-northeast3` (or the agreed data residency location). Preserve its source/license metadata and record the archive SHA-256.
2. Serve the archive through a tested PMTiles Z/X/Y endpoint that supports correct vector-tile MIME types and caching. The implementation may read GCS range requests or use a controlled read-only service; it must not load the entire archive into memory per request.
3. Serve the existing Noto glyph PBF assets under `/glyphs/{fontstack}/{range}.pbf` from a read-only HTTPS endpoint.
4. Route `/tiles/*` and `/glyphs/*` for the web origin through GCP URL-map path rules to those services, while preserving existing host routing and API routes. Verify precedence against every existing matcher before applying the URL-map change.
5. Configure Android and iOS to use the verified public HTTPS tile and glyph endpoints. Keep Gradle property overrides for staging/testing; remove every production fallback to `192.168.252.4`.
6. Add health/readiness checks, request metrics, bounded cache headers, access logging, and a cost/egress alert. Map tiles and glyphs must never share account/API response caching rules.

## Required asset discovery

Before deploying, locate and verify:
- the original Rwanda-only PMTiles archive built from the intended Geofabrik OSM extract;
- the existing Noto Regular/Bold glyph PBF directory;
- the source extract date, attribution/license metadata, archive size, and SHA-256.

Check the former private-cloud host/volume or its backup/export first. Do not regenerate a replacement dataset until the original's absence is confirmed and a fresh extract/build is explicitly approved.

## Release acceptance checklist

- [ ] Archive and glyph assets are located, licensed, and checksummed.
- [ ] A known in-Rwanda vector tile returns HTTP 200 with the expected vector-tile content type; an out-of-range tile returns the documented empty/not-found behavior.
- [ ] A known font stack/range returns HTTP 200 with PBF bytes.
- [ ] HTTPS, CORS (web only), byte/range behavior where applicable, cache headers, and gzip/brotli behavior are verified.
- [ ] Web map loads without console/network errors on `https://app.itunda.im`.
- [ ] Android and iOS contain no private LAN map URLs in production configuration.
- [ ] Map pan/zoom, place search, reverse geocoding, and directions are tested separately; tile delivery alone does not prove these services work.
- [ ] CI passes and a new Android APK is assembled; install and login/map smoke tests are recorded separately from build success.

## Safe deployment rule

Do not publish a Cloud Run service, change production URL maps, or build a release claiming map functionality until the canonical assets and the target endpoint are verified. A successful application build alone is not map-service validation.
