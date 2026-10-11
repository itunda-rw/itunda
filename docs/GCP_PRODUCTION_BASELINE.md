# Itunda GCP production baseline

**Audited:** 2026-10-11  
**GCP project:** `itunda-org`  
**Region:** `asia-northeast3` (Seoul)  
**Purpose:** current infrastructure evidence for the Toss-vs-Itunda architecture audit.

This document supersedes the private-cloud blueprint as the description of Itunda's current infrastructure target. The private-cloud document is retained as historical migration/rehearsal evidence only. This is a read-only audit: no GCP resources were changed.

## Evidence standard

- **Verified live** means a read-only GCP command returned the resource and current state on the audit date.
- **Configured** means the Cloud Run revision contains connection settings; it does not prove a successful end-to-end request to that dependency.
- **Not verified** means the audit did not establish the behavior. Do not treat it as implemented.
- A resource being `READY` or `ACTIVE` is not evidence of high availability, secure transport, tested restore, or application-level health.

## Current GCP inventory

| Component | Live evidence | What this proves |
|---|---|---|
| Cloud Run API | `itunda-api`, Ready; revision `itunda-api-00023-xr2`; 100% traffic on latest ready revision | A deployed API service exists and Cloud Run reports it Ready |
| Cloud Run web app | `itunda-web`, Ready; revision `itunda-web-00119-7tz` | A deployed web app service exists |
| Consumer site | `itunda-public-site`, Ready | Public site is deployed |
| Business app/site | `itunda-business-app` and `itunda-business-site`, Ready | Both business surfaces are deployed separately |
| Developer site | `itunda-developers-site`, Ready | Developer site is deployed |
| Tech blog | `itunda-tech-blog`, Ready | Tech blog is deployed |
| Artifact Registry | Cloud Run images reference `asia-northeast3-docker.pkg.dev/itunda-org/itunda/...` | Images are stored in Artifact Registry |
| Cloud SQL | `itunda-mysql`, MySQL 8.0, state `RUNNABLE`, private IP, `db-f1-micro`, zonal | A small, single-zone MySQL instance exists |
| Memorystore for Redis | `itunda-redis`, Redis 7.2, state `READY`, direct peering | A managed Redis instance exists |
| Managed Kafka | `itunda-kafka`, state `ACTIVE`, capacity 3 vCPU / 3 GiB | A managed Kafka cluster exists |
| Secret Manager | `itunda-db-password` and `itunda-jwt-secret` exist | Secret resources exist; revision references secret versions |
| Pub/Sub | No topics returned by the audit command | No Pub/Sub topics were found in this project at audit time; Kafka is the configured event-bus path |

The API revision's environment includes `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `REDIS_HOST`, `REDIS_PORT`, and `KAFKA_BOOTSTRAP_SERVERS`, plus Secret Manager references for the database password and JWT secret. This is evidence of wiring configuration, **not** a successful DB/Redis/Kafka integration test.

## Critical findings — resolve before calling this production-hardened

### P0 — Database backup and recovery

The live Cloud SQL configuration reports:

- automated backups: **disabled**
- availability type: **ZONAL**
- machine tier: `db-f1-micro`
- disk: 10 GB

This is the highest-priority verified resilience gap for a money-moving application. Configure and validate automated backups and point-in-time recovery appropriate to the chosen Cloud SQL edition/configuration, define retention/RPO/RTO, and perform a restore drill into a separate instance. Decide whether regional high availability is required; do not infer it from a running instance.

### P0 — Transport security to data services

- Cloud SQL reports `requireSsl=false` and `sslMode=ALLOW_UNENCRYPTED_AND_ENCRYPTED`.
- Memorystore reports `transitEncryptionMode=DISABLED`.

These settings do not enforce encrypted transport. Verify client support and rollout safety, then enforce TLS for Cloud SQL and enable in-transit encryption for Redis where supported. Test certificate/CA handling and reconnect behavior before rollout. Do not rotate credentials or flip enforcement blindly.

### P1 — Redis dependency vs target architecture

The deployed API revision explicitly configures Redis. That differs from the previously stated Itunda target of avoiding Redis. This is not automatically wrong, but it must be an explicit architecture decision: identify each Redis use, classify whether it is cache/session/rate-limit/coordination or correctness-critical, document failure behavior, and either approve it or remove it safely. Do not remove the live instance based on a target preference alone.

### P1 — Kafka resilience and event correctness

The managed Kafka cluster is `ACTIVE` and the API has a bootstrap-server setting. The audit did not establish broker count, replication factor, minimum in-sync replicas, topic retention, ACLs, TLS/SASL enforcement, consumer lag alerting, cross-region recovery, or end-to-end message delivery. Verify those settings and run a producer → topic → consumer integration test plus a broker/consumer restart drill. Do not describe this as Toss-style active-active: no second independent cluster, mirroring, or offset synchronization was verified.

### P1 — Public API exposure and identity boundaries

Cloud Run reports API ingress as `all`. That means the service is reachable through its Cloud Run endpoint; it does not prove the application is unauthenticated. Verify the intended public ingress model, authentication on every protected route, rate limits, service-to-service identity, CORS behavior, abuse controls, and whether direct `.run.app` access should be blocked or intentionally supported. CORS is not an authentication control.

### P1 — Capacity and operational readiness

The API revision is configured with 1 vCPU / 1 GiB and max scale 20; the database is `db-f1-micro`. Treat these as current configuration, not proven capacity. Run load and soak tests using realistic Rwanda mobile-network latency and payment/ledger workloads. Establish SLOs, dashboards, alerting, error budgets, and tested rollback before raising traffic.

## Required next verification gates

1. **Backup/restore:** automated backup enabled; a restore into an isolated instance passes application consistency checks; recovery time and data loss measured.
2. **Security:** SQL TLS enforced; Redis in-transit encryption enabled or documented exception; Kafka transport/authentication and topic ACLs verified; secrets are never logged.
3. **Runtime connectivity:** automated tests from the deployed API identity prove DB transactions, Redis failure handling, and Kafka produce/consume; tests fail loudly when a dependency is unavailable.
4. **Ledger correctness:** concurrent transfer/idempotency tests against the actual Cloud SQL engine; transaction rollback and outbox delivery tested under process restarts.
5. **Ingress and identity:** every protected API route tested for missing, invalid, expired, and wrong-owner credentials; direct endpoint and custom-domain behavior documented.
6. **Operations:** latency/error dashboards, database connections/storage, Redis memory, Kafka consumer lag, Cloud Run cold starts/scaling, paging thresholds, and rollback runbook verified.
7. **Regional resilience:** explicit RTO/RPO and decision on regional Cloud SQL HA, backups, cross-region recovery, and regional dependency failure.
8. **Toss comparison:** compare bounded-context ownership, independent deployment, event guarantees, mini-app runtime and artifact lifecycle, and operational evidence—not just service count or cloud brand.

## Commands used for this audit

Read-only inventory commands included:

- `gcloud run services list --project=itunda-org --region=asia-northeast3 --format=json`
- `gcloud sql instances list --project=itunda-org --format=json`
- `gcloud sql instances describe itunda-mysql --project=itunda-org --format=json`
- `gcloud redis instances list --project=itunda-org --region=asia-northeast3 --format=json`
- `gcloud redis instances describe itunda-redis --region=asia-northeast3 --project=itunda-org --format=json`
- `gcloud managed-kafka clusters list --project=itunda-org --location=asia-northeast3 --format=json`
- `gcloud managed-kafka clusters describe itunda-kafka --location=asia-northeast3 --project=itunda-org --format=json`
- `gcloud secrets list --project=itunda-org`
- `gcloud pubsub topics list --project=itunda-org`

This baseline is a dated snapshot, not a substitute for automated drift detection. Re-run it after infrastructure changes.
