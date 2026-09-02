# Itunda

Itunda is a Rwanda-first financial super-app prototype modeled on the Toss product pattern: one trusted app for money movement, account aggregation, spending control, lending, investments, insurance, merchant tools, rewards, and the operating layer behind those services.

The goal is not to copy Toss visually only. The product target is Toss-like simplicity on top of Rwanda-native rails: MTN Mobile Money, Airtel Money, bank transfers, eKash-style interoperability, Irembo/RRA bill flows, RWF-first money formatting, National ID KYC, merchant QR, Face Pay concepts, settlement monitoring, fraud review, and reconciliation.

The canonical product, architecture, and UI/UX map is [docs/TOSS_RWANDA_ALIGNMENT.md](docs/TOSS_RWANDA_ALIGNMENT.md). The implementation checklist is [docs/TOSS_PARITY_MATRIX.md](docs/TOSS_PARITY_MATRIX.md). The source-backed product map is [docs/FACT_CHECKED_TOSS_RWANDA_MAP.md](docs/FACT_CHECKED_TOSS_RWANDA_MAP.md).
The current private-cloud gap analysis and Toss comparison is [docs/PRIVATE_CLOUD_BLUEPRINT.md](docs/PRIVATE_CLOUD_BLUEPRINT.md).
The operational private-cloud workflow is [docs/PRIVATE_CLOUD_OPERATIONS.md](docs/PRIVATE_CLOUD_OPERATIONS.md).

## Current Surfaces

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full real/demo/stub breakdown, sourced against
Toss's actual architecture in [docs/TOSS_ARCHITECTURE_FACTS.md](docs/TOSS_ARCHITECTURE_FACTS.md).
Top-level layout (restructured 2026-07-11 for naming clarity — see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) §5):

- `services/backend/`: the canonical backend — real Kotlin + Spring Boot + Spring Data JPA +
  MySQL + Flyway, covering every product vertical (auth/wallet/transfer/bills/loans/contacts/
  stocks/savings/insurance/eats/commerce+shop/marketplace+hood/rideshare/transit/certificate/
  agents/partners/identity/knowledge/notifications/discover/system and more), verified live
  against real MySQL. 13 of its modules (card/insurance/agents/transit/certificate/bills/
  vehicle/partners/identity/overview/knowledge/notifications/analytics) are also independently
  deployable as their own Spring Boot services — see `docs/DEPLOYMENT.md`.
- `services/microservices/`: a real, independently-deployable per-bounded-context MSA
  prototype (`payment-service`, `ledger-service`) — less feature coverage than `services/backend`
  but architecturally closer to Toss's actual documented MSA pattern. Not reconciled with
  `services/backend` yet.
- `services/micro-frontends/`: 7 real Vite/Module-Federation apps — `bank-mfe` (the real,
  deployed consumer super-app), `host-app` (a mostly-unused 2-tab demo shell), `kyc-mfe`,
  `ops-mfe` (the real internal admin/ops frontend), `merchant-mfe`, `maps-mfe`, `pay-checkout`.
- `services/api-gateway/`: a real Express reverse proxy — rate limiting (incl. a stricter
  money-movement limiter), a hand-rolled per-target circuit breaker, `X-Request-ID`
  propagation, security headers, Prometheus metrics, bounded upstream timeouts with fail-fast
  503/504 — not a stub.
- `services/blog/`: the `tech.itunda.rw` engineering blog, modeled on toss.tech.
- `packages/saronite/`: mini-app SDK modeled on Toss's real open-source `granite` — currently a
  hand-rolled approximation, not yet built on Granite itself.
- `android/`, `ios/`: native mobile shells with real bounded-context modules (design system,
  ledger, risk, identity, banking, payments) — both platforms verified building and running
  (Android: `./gradlew :app:compileDebugKotlin` / real on-device runs; iOS: full `ItundaApp`
  scheme `xcodebuild` against the Simulator, `BUILD SUCCEEDED`, a routine part of this repo's
  own verification habit — see `docs/ARCHITECTURE.md` §3 for the fuller per-Feature-module
  breakdown). Each platform's own `sdk/pay`/`SDK/Pay` module holds itunda's own (in-progress)
  payment SDK, `ItundaPayments`.
- `infra/`: Kubernetes manifests and other infrastructure config.

## Toss-Aligned Product System

The product is organized around the same core jobs Toss solves, adapted for Rwanda:

- Money movement: instant P2P, QR pay, mobile money, bank routing, request money, scheduled transfers, and bill payments.
- Financial control: all balances, transactions, spending, budgets, linked accounts, credit score, and financial health.
- Banking and savings: wallet, savings goals, fixed deposits, card management, and future licensed banking partnerships.
- Credit: personal loans, salary-backed loans, SME working capital, lender comparison, repayment, and alternative credit scoring.
- Investing: RSE stocks, bonds, funds, global stocks, portfolio tracking, and recurring investment plans.
- Protection: motor, health, travel, home, and life insurance marketplace patterns.
- Merchant ecosystem: merchant dashboard, QR collection, Face Pay terminal concept, POS, sales reports, batch settlement, and dispute handling.
- Rewards: cashback, benefits, points, walk-style rewards, referrals, and premium membership concepts.
- Operating layer: rail health, smart routing, fraud signals, compliance queue, reconciliation, settlement status, identity assurance, and incidents.

## Architecture Direction

Itunda should converge on a small set of production-grade bounded contexts instead of feature-by-feature sprawl:

- Identity and consent: phone auth, biometrics, National ID KYC, account-link consent, session risk.
- Ledger and wallet: double-entry ledger, balances, holds, reversals, audit trails, idempotency keys.
- Payments routing: MTN/Airtel/bank/eKash routing, retries, fallback selection, fees, provider health.
- Merchant acquiring: QR, terminals, settlement, disputes, refunds, receipts, reports.
- Credit and underwriting: loan marketplace, affordability, repayment behavior, risk model governance.
- Wealth and protection: investments, insurance policies, claims, suitability checks.
- Engagement: benefits, rewards, recommendations, notification orchestration.
- Operations: compliance, fraud, reconciliation, support tooling, observability.

## Reality Check

Full Toss parity cannot be achieved by UI screens alone. The remaining production work is mostly regulated integration work:

- Payment service provider licensing and bank/mobile-money partner contracts.
- Live provider credentials and certification environments.
- Custody, safeguarding, settlement, and reconciliation policies.
- AML/KYC processes, sanctions screening, risk rules, and audit retention.
- Securities and insurance brokerage permissions where applicable.
- Real incident response, monitoring, customer support, dispute, and refund operations.

The repository currently demonstrates the product shape and system contracts. Treat demo data as mocked unless a live provider integration is explicitly configured.

## Local Development

Install JS dependencies (root workspace covers `packages/shared-utils`,
`services/micro-frontends/*`, `services/api-gateway`; `packages/saronite` and `services/blog` are
separate npm workspaces, install those independently):

```bash
yarn install
```

Start the full local demo stack:

```bash
yarn audit:private-cloud
yarn env:private-cloud > .env
yarn dev:ecosystem
```

This assumes MySQL, Redis, and Kafka already exist in your private cloud or another
reachable environment and are configured through env vars (`.env` is loaded automatically
by `scripts/local-ecosystem.sh`).

That brings up locally:

- the canonical backend on `http://localhost:4001`
- the API gateway on `http://localhost:3000`
- the federated web shell on `http://localhost:5000`
- the two required remotes on `http://localhost:5001` and `http://localhost:5002`

If you want a full local-only fallback instead, use:

```bash
yarn dev:ecosystem:local
```

That starts `infra/docker-compose.yml` first and maps MySQL/Redis/Kafka to `3307`, `16379`,
and `9092`.

If you only want the web shell, `yarn dev` now starts `bank-mfe`, `kyc-mfe`, `ops-mfe`, and
`host-app` together instead of only the host shell. `merchant-mfe`/`maps-mfe`/`pay-checkout`
are 3 more real micro-frontends, run individually (`yarn workspace <name> run dev`).

Manual pieces, if you want them separately:

```bash
yarn dev:infra
yarn dev:backend
yarn dev:gateway
yarn dev
```

Build the full web surface:

```bash
yarn build
```

## Design Principles

- Put the user's money state first: balance, obligations, next best action.
- Keep flows short: one-minute credit check, one-screen transfer, clear confirmation.
- Prefer Rwanda-local terminology and rails over generic fintech language.
- Make operational state visible: route, risk, settlement, consent, and reconciliation.
- Use restrained Toss-like UI: quiet surfaces, clear hierarchy, rounded but compact rows, precise icons, and minimal decoration.
