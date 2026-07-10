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
- Node.js 20+ and npm (for frontend only)
- Docker & Docker Compose
- MySQL 8.0+ (Enterprise equivalent)
- Redis 7+
- Tuist (for iOS)
```

### Quick Start
```bash
cd /Users/me/rwanda/itunda

# Start infrastructure (MySQL, Redis)
docker-compose up -d

# Start the Spring Boot Backend (Toss Architecture)
cd services/backend
DB_HOST=localhost DB_PORT=3306 DB_NAME=itunda DB_USER=itunda DB_PASSWORD=itunda ./gradlew :app:bootRun

# In a new terminal, start the frontend workspace
cd /Users/me/rwanda/itunda
yarn install
yarn dev

# Access
Web:    http://localhost:5173
```

No admin portal or API gateway currently runs at a fixed port — `services/api-gateway` is a
stub (see ARCHITECTURE.md §1), not a running service to point a URL at.

## 3. Key Features

### ✓ Payments & Transfers
- P2P money transfers (instant)
- Mobile money integration (MTN, Airtel)
- Bank account linking
- QR code payments
- Bill payments

### ✓ Banking
- Multiple account types
- Multi-currency support
- Statement generation
- Transaction history
- Balance tracking

### ✓ Credit Products
- Instant loan applications
- AI-powered credit scoring
- Quick approvals (< 5 minutes)
- Multiple loan types
- Flexible repayment schedules

### ✓ Investment Platform
- Stock trading
- Cryptocurrency trading
- Mutual funds/ETFs
- Real-time price feeds
- Portfolio tracking

### ✓ Insurance
- Multiple insurance products
- Quick claims processing
- Digital policy issuance
- Beneficiary management

### ✓ Savings
- Savings goals
- Fixed deposits
- Interest calculation
- Auto-save features

### ✓ Analytics
- Spending insights
- Income tracking
- AI recommendations
- Reports & exports

### ✓ Security
- Biometric authentication
- Multi-factor authentication
- End-to-end encryption
- PCI-DSS compliance
- KYC/AML verification

## 4. API Documentation

### Authentication
```bash
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
```

### Users
```bash
GET  /api/v1/users/profile
PUT  /api/v1/users/profile
POST /api/v1/users/kyc/upload
GET  /api/v1/users/kyc/status
```

### Transactions
```bash
POST /api/v1/transactions/transfer
GET  /api/v1/transactions/history
GET  /api/v1/transactions/{id}
POST /api/v1/transactions/{id}/receipt
```

### Accounts
```bash
GET  /api/v1/accounts
GET  /api/v1/accounts/{id}
POST /api/v1/accounts
GET  /api/v1/accounts/{id}/balance
```

### Loans
```bash
POST /api/v1/loans/apply
GET  /api/v1/loans
GET  /api/v1/loans/{id}
POST /api/v1/loans/{id}/repay
```

### Investments
```bash
POST /api/v1/investments/buy
POST /api/v1/investments/sell
GET  /api/v1/investments/portfolio
GET  /api/v1/investments/prices
```

Full API docs: http://localhost:3000/api/docs (Swagger UI)

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
docker-compose -f infra/docker-compose.yml up -d
yarn dev
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
- `POST /identity/submit` moves a credential to `REVIEW` and opens a real compliance-queue
  item (see `docs/TOSS_PARITY_MATRIX.md`), but there is no decision-maker (no NIDA/vendor
  integration, no reviewer UI) to move it to `VERIFIED`.

### Monitoring — target, not built
Real-time transaction monitoring, fraud detection, anomaly alerts, and compliance reporting
are all designed in `docs/TOSS_RWANDA_ALIGNMENT.md`'s operations bounded context but not
implemented.

## 9. Testing

```bash
# Unit tests
npm run test

# Integration tests
npm run test:integration

# E2E tests
npm run test:e2e

# Load testing (1M concurrent users)
npm run test:load

# Security scanning
npm run security:scan
```

## 10. Monitoring & Observability

### Metrics
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001
- Custom dashboards for each service

### Logs
- Elasticsearch: http://localhost:9200
- Kibana: http://localhost:5601

### Alerts
- Critical issues → SMS/Email/Slack
- Performance degradation → Auto-scaling
- Security events → Immediate notification

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

# Restart
docker-compose -f infra/docker-compose.yml restart mysql redis
```

### High API Latency
```bash
# Check metrics
curl http://localhost:9090/api/v1/query?query=http_request_duration_seconds
# Scale up services
kubectl scale deployment api-gateway --replicas=5
```

## 15. Contact & Support

No real support email, hosted documentation site, or status page exists yet — this is a local
prototype repository, not a deployed service. Fill this in once those actually exist.

---

**Last Updated**: 2026-07-10
**Status**: Prototype — see ARCHITECTURE.md for what's real vs. stub vs. target
