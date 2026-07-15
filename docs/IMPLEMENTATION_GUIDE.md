# Itunda Implementation Guide

## Toss-like Fintech System for Rwanda — Prototype Stage

### System Overview
Itunda is a prototype fintech super-app for Rwanda, aiming for Toss's product and architecture
pattern. It is **not** production-ready and has no real money, PSP licensing, or audited
compliance certification. See [ARCHITECTURE.md](ARCHITECTURE.md) for what's real vs. stub vs.
target, and [docs/TOSS_PARITY_MATRIX.md](TOSS_PARITY_MATRIX.md) for the implementation
checklist. The only backend on Toss's actual real-world stack today is
`services/backend`; everything else is scaffolding at varying depth.

## 1. Architecture

This section previously listed a fictional 12-microservice / Istio / Elasticsearch / Kibana
stack with no basis in the actual repo — removed 2026-07-10. See
[ARCHITECTURE.md](ARCHITECTURE.md) for the real, sourced architecture and
[docs/TOSS_ARCHITECTURE_FACTS.md](TOSS_ARCHITECTURE_FACTS.md) for what Toss itself actually
runs. Summary: `services/backend` (canonical, most feature coverage) and
`services/microservices` (a real, less-complete per-bounded-context MSA prototype closer
to Toss's actual documented architecture) coexist and are not yet reconciled. Frontend surfaces
are `android/`/`ios/` native shells (real bounded-context modules, partly ported/verified) and
`services/micro-frontends` (web, demo depth). Kafka exists in exactly one place
(`services/microservices/core-libs`' `KafkaConfig.kt`) and is not yet a real event
backbone across services.

## 2. Getting Started (Development)

### Prerequisites
```bash
# Required tools
- JDK 17 or higher
- Node.js 20+ and yarn 4 (root workspace) — packages/saronite and services/blog are each
  their own separately-managed npm workspace, not part of the root yarn workspace
  (see package.json's own comment)
- Docker & Docker Compose
- MySQL 8.0 (infra/docker-compose.yml pins 8.0.39)
- Redis 7 (infra/docker-compose.yml pins the redis:7-alpine image)
- Tuist (for iOS)
```

### Quick Start
```bash
cd /Users/me/rwanda

yarn install
yarn dev:ecosystem

# Access
Web:    http://localhost:5000
Gateway: http://localhost:3000
Backend: http://localhost:4001/api/v1/...
```

`yarn dev:ecosystem` is the current canonical local run path. It assumes MySQL, Redis,
and Kafka already exist in your private cloud or another reachable environment and starts:

- the canonical backend on `:4001`
- the API gateway on `:3000`
- the federated host app on `:5000`
- the `kyc-mfe` and `bank-mfe` remotes on `:5001` and `:5002`

If you only want the web shell, `yarn dev` now starts all three Vite apps together. The
Android/iOS apps and the `packages/saronite` mini-app host are still separate build targets.

For a full local-only fallback, use `yarn dev:ecosystem:local`. That starts
`infra/docker-compose.yml` first and injects the Docker-mapped ports (`3307`, `16379`,
`9092`) into the backend process automatically.

## 3. Key Features

This section previously marked every feature area with a blanket "✓", implying things like
cryptocurrency trading, AI-powered credit scoring, real-time price feeds, claims processing,
PCI-DSS compliance, and MFA all exist — none of them do. Removed 2026-07-13 rather than
maintained as a second, drifting copy of feature status: **the single source of truth for
per-feature status is [docs/TOSS_PARITY_MATRIX.md](TOSS_PARITY_MATRIX.md)**, which uses
`real`/`demo`/`target`/`blocked` labels and is kept current against the actual controllers.
At a glance, as of 2026-07-13: wallet transfer, bills, loans, stocks, savings, and insurance
enrollment are `real` (ledger-backed, idempotent, test-covered) in `services/backend`; account
aggregation, spending analytics, credit scoring, and KYC/AML are `demo` or `target`; anything
involving cryptocurrency, real-time market data, claims processing, or biometric/PCI-DSS
compliance certification does not exist in this codebase at all.

## 4. API Documentation

Full, real endpoint-by-endpoint reference: **[API_SPECIFICATION.md](API_SPECIFICATION.md)**,
generated directly from every `@RestController` in `services/backend` (rewritten 2026-07-13;
the version of this section that used to live here — `/users/profile`, `/transactions/transfer`,
`/accounts`, `/investments/*` — matched none of the real controllers and has been removed
rather than kept as a second, drifting copy). There is no Swagger/OpenAPI UI in this repo
(no `springdoc`/`swagger` dependency anywhere in `services/backend`'s Gradle build) — the
markdown file is the only API reference that exists.

## 5. Database Schema

### Core Tables (MySQL)
- `users` (authentication, profiles)
- `wallets` (user wallets for RWF, investments)
- `ledger_accounts` (chart of accounts, e.g., fee_revenue, loan_payable)
- `ledger_entries` (double-entry accounting records)
- `transactions` (transaction boundary records)
- `loans` (loan products and applications)
- `holdings` (investment portfolio)
- `savings_goals` (savings tracking)
- `interest_jar` (accrued interest)
- `insurance_policies` (insurance enrollment)
- `idempotency_keys` (distributed replay protection)

## 6. Deployment

### Development
```bash
yarn dev:ecosystem
```

### Staging / Production
`infra/k8s/production/` has one real manifest (`api-gateway.yaml`) plus a `monitoring/`
manifest — no staging manifests exist yet, and nothing here has been applied to a real cluster.
Treat this as a starting point, not a working deploy pipeline:
```bash
kubectl apply -f infra/k8s/production/
# Auto-scaling and multi-replica numbers below are aspirational, not configured anywhere yet
```

## 7. Performance Targets

These are aspirational targets for a future production system, not measurements of the current
prototype — nothing in this repo has been load-tested. Toss Bank's own real-world benchmark
(sourced, see `docs/TOSS_ARCHITECTURE_FACTS.md` §1) is active-active dual-datacenter with
independent per-service scaling; that is the scale reference to aim at eventually, not a claim
to make about itunda today.

- API Response Time: < 100ms (p95) — target
- Transaction Processing: < 2 seconds — target
- Database Query: < 50ms — target
- Cache Hit Ratio: > 85% — target (no cache layer exists yet)
- System Uptime, concurrent-user scale — not applicable pre-launch; do not state a number
  without a real production deployment to measure

## 8. Security & Compliance

No compliance certification (PCI-DSS, SOC 2, or otherwise) exists for this repository — those
require an actual third-party audit, not a claim in a markdown file. See
[SECURITY.md](../SECURITY.md) for the real, verified security posture and open gaps, and
[docs/TOSS_ARCHITECTURE_FACTS.md](TOSS_ARCHITECTURE_FACTS.md) §5 for why unearned
compliance claims are explicitly disallowed in this repo's docs.

### KYC/AML — target, not built
- Document verification: not implemented.
- Sanctions list checking: not implemented.
- Beneficial ownership verification: not implemented.
- No submission endpoint exists either. Corrected 2026-07-13: this section previously claimed
  `POST /identity/submit` was real and moved a credential to a `REVIEW` state. A repo-wide grep
  found no such endpoint anywhere in `services/backend` — `User.kycVerified` is a plain boolean,
  `false` by default, only ever set `true` by demo seed data (see `docs/TOSS_PARITY_MATRIX.md`'s
  Compliance row). Both submission and decision-making remain to be built.

### Monitoring — target, not built
Real-time transaction monitoring, fraud detection, anomaly alerts, and compliance reporting
are all designed in `docs/TOSS_RWANDA_ALIGNMENT.md`'s operations bounded context but not
implemented.

## 9. Testing

Corrected 2026-07-13: none of the `npm run test*`/`security:scan` scripts below used to be
here ever existed — the root `package.json` only declares `dev`, `build`, and `lint` (see §2).
Real test commands, per stack:

```bash
# Backend (Kotest, real, run this repeatedly during backend work)
cd services/backend
./gradlew test

# Android instrumented tests (real device/emulator required)
cd android
./gradlew connectedAndroidTest

# Saronite mini-app host (type-check only, no test runner configured yet)
cd packages/saronite/host-app
npx tsc --noEmit
```

No E2E suite, load-testing tooling, or automated security scanning exists in this repo yet —
`SECURITY.md` documents real, manually-found-and-fixed vulnerabilities, not an automated scan.

## 10. Monitoring & Observability — target, not built

Corrected 2026-07-13: this section previously listed Prometheus, Grafana, Elasticsearch, and
Kibana as if they were running services with real URLs. `infra/docker-compose.yml` defines
`mysql`, `redis-node-1`, `zookeeper`, `kafka-broker-1`, and `debezium` — no monitoring/logging
stack. `infra/k8s/production/monitoring/` has one manifest that has never been applied to a
real cluster (see §6). None of this exists yet; treat it as a real gap, not a running system.

## 11. Support & Documentation

- **API Docs**: http://localhost:3000/api/docs
- **Architecture**: See ARCHITECTURE.md
- **API Spec**: See API_SPECIFICATION.md
- **Contributing**: See CONTRIBUTING.md
- **Security**: See SECURITY.md
- **Testing**: See TESTING.md

## 12. Roadmap

See [ARCHITECTURE.md §5](ARCHITECTURE.md#5-immediate-architecture-backlog) for the actual
current priority order and [docs/TOSS_PARITY_MATRIX.md](TOSS_PARITY_MATRIX.md) for
per-feature status. Nothing below is "complete" in a production sense — demo/mocked coverage
exists for most product areas on `services/backend`; native and web clients
range from real-but-duplicated to broken to stub (see ARCHITECTURE.md §1-3).

### Done (demo-grade, verified live against real MySQL)
- Core ledger, wallet, transfer quote/confirm, idempotency (services/backend)
- Bills, loans, stocks, savings, insurance enrollment, notifications, discover (services/backend)

### Not done
- Kafka event backbone (designed, not wired)
- Real super-app mini-app loading mechanism (`saronite/` is a placeholder, see ARCHITECTURE.md §2)
- Working root web app (currently broken, see ARCHITECTURE.md §2)
- Canonical native mobile apps (currently duplicated, see ARCHITECTURE.md §3)
- Load testing, security hardening beyond `SECURITY.md`'s fixes, production deployment

## 13. Quick Commands

```bash
# Backend Development
cd services/backend
./gradlew :app:bootRun          # Start Spring Boot API gateway & core
./gradlew test                  # Run Kotest unit tests

# Frontend Development

npm run dev                     # Start React app

# iOS Development
cd ios
tuist generate                  # Generate Xcode project with Microfeatures
open Itunda.xcworkspace

# Android Development
cd android
./gradlew installDebug          # Build and install on connected device/emulator

# Database
docker-compose up -d mysql redis  # Start required infrastructure
```

## 14. Troubleshooting

### Service Won't Start
```bash
docker-compose logs <service>
npm run health:check
```

### Database Connection Issues
`services/backend` uses **MySQL**, not Postgres/MongoDB (those were part of the
fictional stack removed from §1 above):
```bash
# Check if MySQL/Redis are running
docker ps | grep mysql
docker ps | grep redis

# Restart (real service names, per infra/docker-compose.yml — "redis", not "redis-node-1",
# was a real bug in this doc until 2026-07-13)
docker-compose -f infra/docker-compose.yml restart mysql redis-node-1
```

### High API Latency
No metrics endpoint or auto-scaling exists to check (see §10 — corrected 2026-07-13, this
subsection previously pointed at a non-existent Prometheus URL and `api-gateway` deployment,
which is a stub with no replicas running anywhere, per §2). Today, diagnosing latency means
reading application logs directly (`./gradlew :app:bootRun`'s stdout) or attaching a profiler —
there is no dashboard.

## 15. Contact & Support

No real support email, hosted documentation site, or status page exists yet — this is a local
prototype repository, not a deployed service. Fill this in once those actually exist.

---

**Last Updated**: 2026-07-13
**Status**: Prototype — see ARCHITECTURE.md for what's real vs. stub vs. target
