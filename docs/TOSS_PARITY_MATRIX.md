# Toss/Rwanda Parity Matrix

> **Rewritten 2026-07-13.** The previous version of this file described a since-deleted
> Express+TypeScript backend by file path (`backend/src/services/ledger.ts` and similar) and
> claimed the ledger "needs real Postgres" — false as of the real Kotlin backend, which has run
> on MySQL with Flyway migrations for some time. This version is grounded against the actual
> current codebase: `services/backend` (Kotlin/Spring/MySQL, the canonical backend),
> `services/microservices` (a real, smaller, unreconciled MSA prototype), the Android/iOS native
> shells, and `packages/saronite` (itunda's mini-app host, now genuinely built on real granite
> packages as of 2026-07-12 — see `docs/ARCHITECTURE.md`'s mini-app host row for the full,
> dated account, including the one specific upstream bug still blocking end-to-end use). Status
> values:
>
> - `real`: exists, runs, independently verified (build, test, or live on-device/against a real
>   backend — not just "the code looks right").
> - `demo`: present with mocked or local data, or wired but not yet independently verified live.
> - `target`: defined in the product model, no real implementation yet.
> - `blocked`: requires licensing, a provider contract, certification, or live credentials —
>   regulatory/business work, not something more code can resolve.

## Tech Stack Parity

`services/backend` is the canonical backend, on Toss's real documented stack: Kotlin + Spring
Boot + Spring Data JPA/Hibernate + **MySQL** (not Postgres — Flyway-managed schema,
`ddl-auto: validate`) + Spring Security (JWT), 13 domain modules (`auth`, `wallet`, `bills`,
`loans`, `stocks`, `savings`, `insurance`, `notifications`, `discover`, `contacts`, `system`,
`merchant`) plus `core`. `services/microservices` (`payment-service`, `ledger-service`) is a
real, separate, smaller-coverage MSA prototype closer to Toss's actual documented per-service
split (facts doc §1) — a deliberate, decided-and-acted-on architectural fork, not dead code; see
`docs/ARCHITECTURE.md` §1's "Reconciliation decided and acted on" note for the full reasoning
(the validated pattern, a transactional outbox, was ported into `services/backend`; the
codebases themselves were not merged).

## Product Area Matrix

| Area | Toss-like capability | Rwanda adaptation | Current status | Next implementation gate |
|---|---|---|---|---|
| Home | Money state first | RWF balance, linked wallet/bank preview | demo | Real account-linking/consent model (today: itunda's own wallets only) |
| Transfer | Simple P2P | Phone/contact/account transfer | real | Quote/confirm split, idempotency, provider-decline handling all real (`WalletController`); real per-rail routing for P2P still doesn't exist (`RailCatalog.resolve` falls through to generic) |
| Transfer | Free/fast routing | MTN, Airtel, bank switch fallback | demo | `ProviderConnector`/`SimulatedProviderConnector` is real simulation (latency/success-rate per rail) but not a real MTN/Airtel/bank integration — blocked on provider credentials |
| QR Pay | Merchant/person QR | Itunda QR and merchant scan | real | Merchant QR generate/collect is real and ledger-backed (`rw.itunda.merchant`); person-to-person QR not built |
| Face Pay | Biometric merchant pay | Face Pay terminal concept | target | Local biometric gates exist on both mobile platforms (`NIDABiometricAuth`) but are device-local only, not a merchant-terminal payment flow; blocked on biometric-payment consent/fraud policy and a real terminal concept |
| Bills | Utility/public bills | REG, WASAC, Irembo, RRA | real | Real `GET /bills/pending`/`POST /bills/pay` with provider-connector simulation and idempotency; no real biller integration (REG/WASAC/RRA credentials) — blocked |
| Account aggregation | All assets in one place | Bank, MoMo, savings, investments | demo | Only itunda's own wallets/products aggregate today; a real consent registry + external account linking is still target |
| Spending | Transaction insight | Categorized RWF spending | demo | `GET /wallet/transactions` is real; categorization/budgeting is still target |
| Savings | Goal/fixed savings | Salary and merchant auto-save | real | Ledger-backed (`savings_goal_payable`, `interest_expense`), real deposit/claim-interest endpoints, real test coverage; recurring auto-save scheduling still target |
| Credit | Loan marketplace | Personal, salary-backed, SME | real | Ledger-backed (`loan_payable`), real apply/repay with ownership checks and overpayment clamping, real test coverage; affordability/risk-model governance and multi-lender marketplace still target |
| Credit score | Free credit check | Alternative data with consent | demo | Present but not independently re-verified this pass — treat as unconfirmed until checked directly |
| Invest | Securities | RSE stocks, bonds, global where permitted | real | Ledger-backed (`securities_suspense`), real buy/sell with weighted-average-cost recompute, real test coverage; brokerage/custody integration blocked |
| Insurance | Marketplace | Motor, health, life, travel | real | Ledger-backed enrollment (`insurance_premium_revenue`), real test coverage; claims filing and real insurer quote/bind adapters still target — `docs/MERCHANT_SERVICES.md`-style honesty applies here too, check `docs/PAYMENTS.md`/insurance docs directly before citing specifics |
| Merchant | Business tools | QR, POS, reports, settlements | real (registration + QR collection) | `POST /api/v1/merchant/register`, `/qr/generate`, `/collect/{intentId}` are real, idempotent, ownership-checked, ledger-backed with a real fee split (1.5%, grounded in Toss Payments' published 0.8–1.8% range). POS, card processing, B2B payroll, webhooks not built — KYB/business verification blocked (same regulatory gate as identity verification) |
| Rewards | Benefits/cashback | Cashback, points, referrals | real (backend) / real (mini-app bridge, itunda's own) | Ledger-backed (`rewards_expense`) with claim-once guard on the main app's own API. The Saronite mini-app bridge's `getRewardTasks`/`claimRewardTask` methods call `GET /rewards/tasks`/`POST /rewards/claim`, which **do not exist** in `services/backend` at all (confirmed via `grep`) — this is a real, separate gap: the reward-tasks *mini-app* has no working backend regardless of bridge correctness |
| Operations | Provider health | Rail success/latency/fallback | demo | `ProviderConnector` simulates per-rail health; no live monitoring/alerting |
| Operations | Fraud/review | High-value QR, velocity, new recipient | target | No fraud-rule engine exists yet |
| Operations | Reconciliation | Provider settlement files | real (one-sided) | `reconciliation.ts`-equivalent logic now computed from real provider-attempt logs (`services/backend`), aggregated by rail/day; still reconciles itunda's own attempt log against itself — there is no real external settlement file to diff against, so this is honest but not yet two-sided |
| Operations | Incidents | Rail degradation and user impact | target | No incident-response tooling in-product; `SECURITY.md` has real runbooks for this system's actual failure modes, which is a process document, not product surface |
| Compliance | KYC/AML | National ID, KYB, AML/CFT queue | demo (submission) / blocked (decision) | `POST /identity/submit` is real — moves a credential to `REVIEW`, opens a real compliance-queue item. The actual decision-maker (NIDA/vendor API, or a human reviewer UI to resolve `REVIEW` → `VERIFIED`/`EXPIRED`) doesn't exist — blocked on regulatory/vendor access, not a code gap |
| Ledger | Trust core | Double-entry wallet and settlement ledger | real | MySQL, Flyway-migrated schema, row-level pessimistic locking in stable order (deadlock avoidance), real transactional outbox with a real relay polling and publishing to Kafka. Real, not a stepping stone — the "needs Postgres" framing in the old version of this doc was never true of this backend |
| Mobile | Same IA | Home, Benefits, Transfer, Stocks, All | real | Both Android and iOS build and run (Android on-device, iOS on simulator, verified this session's predecessors), 5-tab taxonomy aligned across platforms, real login/session flow with real token storage on both |
| Mini-app host | Granite-style super-app mechanism | Itunda mini-apps (bills, wallet, rewards) | real (native infra) / demo (one mini-app, JS-blocked) | See `docs/ARCHITECTURE.md`'s mini-app host row for the full, dated account — real granite packages, real RN Gradle Plugin, RN 0.84.0, real New Architecture, real native `brick-module` bridge all independently verified on-device (2026-07-12). One specific, diagnosed upstream JS bug in vendored `@granite-js/brownfield-module` currently blocks the real bridge from working end-to-end for any mini-app; `pay-bills` reverted to itunda's own working bridge in the meantime |
| Offline | Low-connectivity use | Demo/fallback and pending actions | target | No offline queue/conflict handling |

## Non-Negotiable Gates Before Real Money

Status of the gates that would need to be true before this system could move real money, kept
separate from the product-area matrix above because these are cross-cutting and higher-stakes
than any single feature row.

- **Double-entry ledger — real.** MySQL with Flyway-managed schema (`V1`–`V3+` migrations),
  row-level locking in stable sorted order, `@Transactional` boundaries. All money-moving
  endpoints post through it. Verified surviving a real process restart in a prior session; not
  yet load-tested or verified under real concurrent-write contention at scale.
- **Transfer quote/confirm separation — real.** `POST /api/v1/wallet/transfer/quote` +
  `POST /api/v1/wallet/transfer/confirm`, real 60-second quote expiry, ownership checks on both
  steps.
- **Idempotency keys — real, durable.** MySQL-backed idempotency service, required via the
  `Idempotency-Key` header on every money-moving endpoint (transfer confirm, bill pay, airtime,
  loan apply/repay, stock buy/sell, savings deposit/claim, insurance enrollment, merchant
  collection). Verified: a replayed key returns the cached result rather than double-processing,
  and a reused key with a different body correctly 409s.
- **Provider connector abstraction — real, simulated.** `ProviderConnector`/
  `SimulatedProviderConnector` sits in front of transfers, bills, and airtime with per-rail
  latency/success-rate profiles and one retry for degraded rails; a decline never reaches the
  ledger (verified via test assertions that no ledger call happens on decline, not just that an
  exception is thrown). This is a real simulation of the pattern, not a real MTN/Airtel/bank
  integration — those need actual provider credentials and certification, which is blocked, not
  a code gap.
- **KYC/KYB and AML/CFT workflow — half real, half blocked.** Submission is real and durable
  (see the Compliance row above). The decision-making half — an actual NIDA/vendor API or a
  human reviewer UI — is blocked on regulatory/vendor access.
- **Customer support workflow — real.** Real ticket creation/listing tied to a specific
  transaction, and a real refund action that reverses the exact original ledger legs (same
  accounts, flipped direction, including the fee) rather than a synthetic adjustment. Missing:
  account-takeover-specific flow, an ops-side ticket queue UI, and a formal SLA/escalation
  policy.
- **Daily settlement and reconciliation — real, one-sided.** See the Reconciliation row above —
  computed from itunda's own real provider-attempt log, not yet diffed against an external
  settlement file (there isn't one to diff against without a real provider relationship).
- **Security review and incident response — real.** `SECURITY.md` documents real, previously
  fixed vulnerabilities (auth accepting any password; controllers not using the authenticated
  caller's identity, allowing cross-account access) with real runbooks for this system's actual
  failure modes. `/system/*` ops endpoints now have a real, if narrow, RBAC check (`hasRole
  ("ADMIN")`) — added 2026-07-12, gates only the system/compliance module, not a broad
  permission system across every endpoint.

## What Changed Since the Last Version of This Document

For anyone who read the pre-2026-07-13 version of this file: the entire "three unreconciled
backend directions" section describing a JSON-file-persisted Express backend, a mostly-empty
NestJS scaffold, and a spring-backend with zero Kotlin files is **obsolete** — none of that
exists anymore. The Express backend was fully ported to `services/backend` and then deleted; the
"three directions" resolved to `services/backend` (canonical) and `services/microservices` (a
real, smaller, deliberately-kept-separate MSA prototype). If any other document in this repo
still describes `backend/src/...` paths, treat it as stale — see `docs/ARCHITECTURE.md`'s own
doc-staleness notes for which ones were already caught and fixed as of this pass
(`docs/DEPLOYMENT.md`, `docs/TOSS_FEATURE_SPECIFICATION.md`) and which are still open
(`docs/API_SPECIFICATION.md`, `docs/IMPLEMENTATION_GUIDE.md`, `docs/PAYMENTS.md`).
