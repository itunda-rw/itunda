# Itunda GCP production baseline

**Audited:** 2026-10-11  
**GCP project:** `itunda-org`  
**Region:** `asia-northeast3` (Seoul)  
**Purpose:** current infrastructure evidence for the Toss-vs-Itunda architecture audit.

This document supersedes the private-cloud blueprint as the description of Itunda's current infrastructure target. The private-cloud document is retained as historical migration/rehearsal evidence only. This document combines read-only inspection with a recorded, narrowly scoped live change: automated Cloud SQL backups were enabled on 2026-10-11. No other live GCP changes are recorded here.

## Evidence standard

- **Verified live** means a read-only GCP command returned the resource and current state on the audit date.
- **Configured** means the Cloud Run revision contains connection settings; it does not prove a successful end-to-end request to that dependency.
- **Not verified** means the audit did not establish the behavior. Do not treat it as implemented.
- A resource being `READY` or `ACTIVE` is not evidence of high availability, secure transport, tested restore, or application-level health.

## Current GCP inventory

| Component | Live evidence | What this proves |
|---|---|---|
| Cloud Run API | `itunda-api`, Ready; revision `itunda-api-00023-xr2`; 100% traffic on latest ready revision; image tag `c85d7fcc4177aafa667215677c661af456e76d3e` | A deployed API service exists and Cloud Run reports it Ready; the tag resolves to source commit `c85d7fcc` |
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

The deployed API image tag maps to source commit [`c85d7fcc`](https://github.com/itunda-rw/itunda/commit/c85d7fcc4177aafa667215677c661af456e76d3e). Comparing it with the audited PR head `e85d2391a1ec4c56d9f803274ba74f713e6d5744` showed the PR head is 98 commits ahead of that deployed source commit (at the time of this audit). Therefore, the current PR's security review and other branch-level changes must not be represented as already deployed. Plan a tested deployment after CI passes and release checks are satisfied.

## Critical findings — resolve before calling this production-hardened

### P0 — Database backup and recovery

The live Cloud SQL configuration was rechecked on 2026-10-11 after the backup change:

- automated backups: **enabled** (verified by a successful Cloud SQL UPDATE operation and a fresh instance describe)
- backup schedule: daily at `13:00 UTC` (22:00 Korea Standard Time)
- retained backups: `7`
- point-in-time recovery (PITR): **not enabled/verified**; transaction-log retention alone does not prove PITR is active
- availability type: **ZONAL**
- machine tier: `db-f1-micro`
- disk: 10 GB
- backups list immediately after enabling returned an empty list; no completed backup or restore has been verified yet

The initial backup gap is closed at the configuration level, but recoverability is not yet proven. Wait for the first successful scheduled backup, confirm it appears in `gcloud sql backups list`, then restore it to an isolated instance and run application consistency checks. Define RPO/RTO and decide whether PITR and regional high availability are required. PITR was deliberately not enabled in this pass because it needs a separate impact/restart and log-storage review.

### P0 — Transport security to data services

- Cloud SQL reports `requireSsl=false` and `sslMode=ALLOW_UNENCRYPTED_AND_ENCRYPTED`.
- Memorystore reports `transitEncryptionMode=DISABLED`, persistence mode `DISABLED`, and `BASIC` tier with one node.

These settings do not enforce encrypted transport. Verify client support and rollout safety, then enforce TLS for Cloud SQL and enable in-transit encryption for Redis where supported. Test certificate/CA handling and reconnect behavior before rollout. Do not rotate credentials or flip enforcement blindly.

### P1 — Redis dependency vs target architecture

The deployed API revision explicitly configures Redis. That differs from the previously stated Itunda target of avoiding Redis. This is not automatically wrong, but it must be an explicit architecture decision: identify each Redis use, classify whether it is cache/session/rate-limit/coordination or correctness-critical, document failure behavior, and either approve it or remove it safely. Do not remove the live instance based on a target preference alone.

### P1 — Kafka resilience and event correctness

The managed Kafka cluster is `ACTIVE` and the API has a bootstrap-server setting. The describe output exposes a standard bootstrap address on port `9092` and an mTLS bootstrap address on port `9192`, but the audit did not establish which address the application uses or verify broker count, replication factor, minimum in-sync replicas, topic retention, ACLs, client authentication policy, consumer lag alerting, cross-region recovery, or end-to-end message delivery. Verify those settings and run a producer → topic → consumer integration test plus a broker/consumer restart drill. Do not describe this as Toss-style active-active: no second independent cluster, mirroring, or offset synchronization was verified.

### P1 — Public API exposure and identity boundaries

Cloud Run reports API ingress as `all`, and `gcloud run services get-iam-policy` returned `roles/run.invoker` bound to `allUsers`. This confirms public invocation is allowed at the Cloud Run IAM layer; it does **not** prove that application routes are unauthenticated. Verify authentication on every protected route, rate limits, service-to-service identity, CORS behavior, abuse controls, and whether direct `.run.app` access should be blocked or intentionally supported. If public invocation is not the intended design, plan an ingress/IAM change only after confirming the intended public API and testing custom-domain, gateway, and health-check paths. CORS is not an authentication control.

#### Repository-level authentication review (not yet tied to the deployed image)

I inspected the current PR branch's `services/backend/security/.../SecurityConfig.kt` and `JwtAuthenticationFilter.kt`. The code defines a JWT-by-default policy: most routes require a verified access JWT, while explicit public exceptions include health/actuator paths, selected auth bootstrap endpoints, `/ws/**` (with a separate JWT handshake interceptor), partner routes (API-key checked inside the service), public pay/checkout routes (with merchant API-key checks on protected payment operations), public certificate verification/status, GET uploads, GET public shared-map folders, and a USSD webhook path. Admin/system routes have explicit role checks. The JWT filter rejects invalid/refresh/revoked access tokens when it processes a bearer header.

This is **source-code evidence for the PR branch only**. The live Cloud Run image tag was mapped to source commit `c85d7fcc`, which is 98 commits behind the audited PR head, so the PR-branch policy is not yet evidence of deployed behavior. Before closing this finding, deploy a reviewed commit through CI/CD, map the resulting image digest/revision to that exact source commit, run unauthenticated/invalid/expired/wrong-owner tests against the deployed API for each protected route family, and separately verify the partner API-key, merchant-key, WebSocket handshake, and USSD webhook controls. Keep Cloud Run public invocation only if it is the intended public API boundary; IAM `allUsers` is not itself a route-authentication bug when application-level auth is correctly enforced.

### P1 — Capacity and operational readiness

The API revision is configured with 1 vCPU / 1 GiB and max scale 20; the database is `db-f1-micro`. Treat these as current configuration, not proven capacity. Run load and soak tests using realistic Rwanda mobile-network latency and payment/ledger workloads. Establish SLOs, dashboards, alerting, error budgets, and tested rollback before raising traffic.

## Required next verification gates

1. **Backup/restore:** automated daily backup is now configured (13:00 UTC, 7 retained); verify the first completed backup, enable PITR only after reviewing its restart/storage impact, then restore into an isolated instance and measure recovery time and data loss.
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

## Repeatable audit

Run the read-only posture check from the repository root:

```bash
bash scripts/gcp-production-audit.sh
```

The script checks Cloud SQL backups/PITR/TLS, Memorystore TLS/AUTH, Managed Kafka resource state, Cloud Run readiness, and whether the API revision declares its dependency settings. It does not mutate resources, read secret payloads, or replace end-to-end integration tests. It returns a failing exit code when required resources are unreadable or database backup/PITR checks fail; other hardening gaps are reported as warnings for review.
