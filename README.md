# Itunda

Itunda is a Rwanda-first financial super-app prototype modeled on the Toss product pattern: one trusted app for money movement, account aggregation, spending control, lending, investments, insurance, merchant tools, rewards, and the operating layer behind those services.

The goal is not to copy Toss visually only. The product target is Toss-like simplicity on top of Rwanda-native rails: MTN Mobile Money, Airtel Money, bank transfers, eKash-style interoperability, Irembo/RRA bill flows, RWF-first money formatting, National ID KYC, merchant QR, Face Pay concepts, settlement monitoring, fraud review, and reconciliation.

The canonical product, architecture, and UI/UX map is [docs/TOSS_RWANDA_ALIGNMENT.md](/Users/me/rwanda/itunda/docs/TOSS_RWANDA_ALIGNMENT.md). The implementation checklist is [docs/TOSS_PARITY_MATRIX.md](/Users/me/rwanda/itunda/docs/TOSS_PARITY_MATRIX.md). The source-backed product map is [docs/FACT_CHECKED_TOSS_RWANDA_MAP.md](/Users/me/rwanda/itunda/docs/FACT_CHECKED_TOSS_RWANDA_MAP.md).

## Current Surfaces

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full real/demo/stub breakdown, sourced against
Toss's actual architecture in [docs/TOSS_ARCHITECTURE_FACTS.md](docs/TOSS_ARCHITECTURE_FACTS.md).
Top-level layout (restructured 2026-07-10 to match Toss's own repo convention):

- `services/spring-backend/`: the canonical backend — real Kotlin + Spring Boot + MySQL, covering
  auth/wallet/transfer/bills/loans/contacts/stocks/savings/insurance/notifications/discover/system,
  verified live against real MySQL.
- `services/spring-microservices/`: a real, independently-deployable per-bounded-context MSA
  prototype (`payment-service`, `ledger-service`) — less feature coverage than `spring-backend`
  but architecturally closer to Toss's actual documented MSA pattern. Not reconciled with
  `spring-backend` yet.
- `services/micro-frontends/`, `services/api-gateway/`: web/BFF surfaces, demo-to-stub depth.
- `services/blog/`: the `tech.itunda.rw` engineering blog, modeled on toss.tech.
- `packages/saronite/`: mini-app SDK modeled on Toss's real open-source `granite` — currently a
  hand-rolled approximation, not yet built on Granite itself.
- `android/`, `ios/`: native mobile shells with real bounded-context modules (design system,
  ledger, risk, identity, banking, payments) — Android verified compiling and running on-device;
  iOS ported but not build-verified in this environment (see ARCHITECTURE.md §3).
- `mobile_clients/itunda-pay-sdk/`: a vendored clone of the real `tosspayments/payment-sdk-android`, kept as reference only.
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

Install JS dependencies (root workspace covers `packages/shared-utils`, `packages/itunda-utils`,
`services/micro-frontends/*`, `services/api-gateway`; `packages/saronite` and `services/blog` are
separate npm workspaces, install those independently):

```bash
yarn install
```

Run the web app:

```bash
yarn dev
```

Run the canonical backend:

```bash
cd services/spring-backend
./gradlew :app:bootRun
```

Build the web app:

```bash
yarn build
```

## Design Principles

- Put the user's money state first: balance, obligations, next best action.
- Keep flows short: one-minute credit check, one-screen transfer, clear confirmation.
- Prefer Rwanda-local terminology and rails over generic fintech language.
- Make operational state visible: route, risk, settlement, consent, and reconciliation.
- Use restrained Toss-like UI: quiet surfaces, clear hierarchy, rounded but compact rows, precise icons, and minimal decoration.
