# API Specification

> **Rewritten 2026-07-13.** The previous version of this file was entirely fictional: a
> `http://localhost:3000/api/v1` base URL, endpoints like `/auth/register`,
> `/transactions/send`, `/loans/apply`, `/analytics/insights`, a `{"status": "success",
> "code": 200, "message": ..., "data": {}, "timestamp": ...}` response envelope, an
> `AUTH_001`-style error code table, and webhook events (`transaction.completed`,
> `loan.approved`) — none of it matched any real controller in `services/backend`. This
> version is generated directly from the real Kotlin/Spring controllers (one
> `@RestController` per domain module, all under `services/backend/<module>/src/main/kotlin/
> rw/itunda/<module>/`), read in full on 2026-07-13. Every endpoint, request shape, response
> shape, and error code below is copied from real code, not invented. See
> `docs/TOSS_PARITY_MATRIX.md` for which of these are load-bearing in a real device build vs.
> demo-only, and `docs/ARCHITECTURE.md` for the ledger/idempotency/provider-connector
> internals behind them.

## Base URL and conventions

- Base path: `/api/v1` (no version renegotiation — this is the only version that exists).
  Local dev default port is `4001` (`services/backend/app/src/main/resources/application.yml`);
  confirm against that file before assuming, it is not hardcoded here.
- Auth: `Authorization: Bearer <accessToken>` header, required on every route except
  `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/refresh`,
  `/health`, and `/actuator/health` (see `SecurityConfig.kt`). A missing/invalid token gets
  `401`. A valid token lacking the required role (currently only `/api/v1/system/**`, which
  requires the `ADMIN` role) gets `403`, not `401` — this distinction was a real, previously
  live bug (see `SecurityConfig.kt`'s own comments) and is now fixed and tested.
- Response envelope: **there is no single global envelope.** Every controller hand-builds its
  own `Map<String, Any>` body, but they all follow the same convention: a top-level
  `"success": true` boolean plus one or more named keys holding the actual payload (e.g.
  `{"success": true, "wallet": {...}}`, not a generic `"data"` wrapper). The exact key name is
  documented per-endpoint below because it varies by resource (`"wallets"`, `"bills"`,
  `"loan"`, `"quote"`, etc.) — there is no single field name to rely on generically.
- Error shape: **uniform**, unlike the success envelope —
  `{"code": "SCREAMING_SNAKE_CASE", "message": "human-readable reason"}`
  (`rw.itunda.core.web.ApiError`), deliberately matching Toss Payments' own published error
  shape (`docs.tosspayments.com/reference/error-codes`) rather than a made-up convention.
  Every domain-specific error code actually used is listed per-module below — there is no
  central error registry, each controller declares its own `@ExceptionHandler`s.
- Idempotency: every money-moving `POST` (transfer confirm, bill pay, airtime, loan
  apply/repay, stock buy/sell, savings deposit/claim, insurance enrollment, merchant
  collection) requires an `Idempotency-Key` header. Missing it is a real `400
  IDEMPOTENCY_KEY_REQUIRED`. A replayed key with the same request body returns the original
  cached result (same status code, same body) rather than re-executing. A reused key with a
  **different** body is a real `409 IDEMPOTENCY_KEY_CONFLICT`. A key currently mid-flight (a
  concurrent duplicate request) is a real `409 IDEMPOTENT_REQUEST_PROCESSING`. Backed by a
  durable MySQL table, not an in-memory map — survives a process restart.
- Amounts are `BigDecimal` (JSON numbers, RWF, no minor-unit/cents convention — Toss's own API
  uses whole KRW, and RWF has no minor unit in practice either).

## Auth — `/api/v1/auth`

| Method | Path | Auth | Body | Success | Notes |
|---|---|---|---|---|---|
| POST | `/register` | none | `{phoneNumber, email?, firstName, lastName, password}` | `201` `AuthResponse` | |
| POST | `/login` | none | `{phoneNumber, password}` | `200` `AuthResponse` | |
| POST | `/refresh` | none (refresh token is the credential) | `{refreshToken}` | `200` `AuthResponse` | Rotates the refresh token; the old one is revoked immediately |
| POST | `/logout` | Bearer | `{refreshToken?}` (optional body) | `200` `{"success": true}` | Revokes the exact access token used to call this, and the refresh token too if provided |
| GET | `/profile` | Bearer | — | `200` `{"success": true, "user": PublicUser}` | |

`AuthResponse`: `{success: true, message: string, user: PublicUser, accessToken: string,
refreshToken: string}`.

`PublicUser`: `{id, phoneNumber, email, firstName, lastName, kycVerified, creditScore,
createdAt}`. **`kycVerified` is real as a field, but nothing in this backend can ever set it
`true` outside of demo seed data** — see the Compliance row in `docs/TOSS_PARITY_MATRIX.md`;
there is no verification endpoint anywhere in this API.

Errors: `409 PHONE_ALREADY_REGISTERED`, `401 INVALID_CREDENTIALS`, `404 USER_NOT_FOUND`,
`401 INVALID_REFRESH_TOKEN`, `429 RATE_LIMITED`.

## Overview — `/api/v1/overview`

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Account aggregation
row. Aggregates every real itunda product into one net-worth figure; does not touch external
bank/MoMo linking, which remains fully target.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, netWorth, accounts: [...], savings, loans, investments, insurance}` | `netWorth` = wallet balances + savings + investment cost basis − active loan outstanding. Insurance is excluded from `netWorth` (a paid premium is a sunk expense, not an asset) and reported separately as a coverage summary |

`accounts`: `[{id, type, name, balance, currency}]`. `savings`: `{totalSaved, goalCount}`.
`loans`: `{totalOutstanding, activeCount}` (active loans only — a fully `PAID` loan doesn't
count against outstanding). `investments`: `{totalCostBasis, holdingCount}` — cost basis
(`shares × avgPrice`), not live market value; the stock price catalog lives in the `:stocks`
module and no module in this backend depends on another feature module. `insurance`:
`{activePolicyCount, totalMonthlyPremium}`.

## P2P — `/api/v1/p2p`

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s QR Pay row. The first
real wallet-to-wallet money movement in this backend where both sides are known itunda users
(the existing `wallet/transfer/*` flow always routes through the simulated external rail, even
recipient-to-recipient — see that row for the full account).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/request` | `{amount, description}` | `201` `{success, request}` | Creates a real `PENDING` request, 15-minute expiry (same convention as merchant QR) |
| GET | `/requests` | — | `{success, requests: [...]}` | Caller's own requests, most recent first |
| POST | `/pay/{requestId}` | — (+ `Idempotency-Key`) | `{success, message, transaction, newBalance}` | Direct `WALLET`→`WALLET` ledger pair, no fee. `newBalance` is the payer's own wallet balance after payment |

Errors: `404 P2P_REQUEST_NOT_FOUND`, `409 P2P_REQUEST_NOT_PAYABLE` (already paid, or expired —
a real request past its `expiresAt` gets marked `EXPIRED` on the attempt, not silently allowed),
`400 SELF_PAYMENT_NOT_ALLOWED`, `404 WALLET_NOT_FOUND`, `422 INSUFFICIENT_FUNDS`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`.

## Wallet — `/api/v1/wallet`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, wallets: [...]}` | |
| GET | `/{id}` | — | `{success, wallet: {...}}` | |
| GET | `/transactions` | — | `{success, transactions: [...]}` | Real transaction history, backs the transaction-history screen on both mobile platforms |
| GET | `/spending` | — | `{success, categories: [...], totalSpent}` | Built and live-verified 2026-07-13. Categorizes by looking up each wallet debit's real ledger counterpart, not the `transactions` table (only P2P transfers ever write a row there). `categories`: `[{name, amount}]`, largest first |
| POST | `/transfer/quote` | `{amount, recipient, fromWalletId?, description?}` | `{success, quote: {...}}` | Quote expires after 60 seconds |
| POST | `/transfer/confirm` | `{quoteId}` (+ `Idempotency-Key`) | `{success, message, transaction, newBalance}` | Requires the quote from `/transfer/quote`; posts through the double-entry ledger |

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 WALLET_NOT_FOUND`, `403 WALLET_NOT_OWNED`,
`404 QUOTE_NOT_FOUND`, `409 QUOTE_EXPIRED`, `409 QUOTE_ALREADY_USED`,
`422 INSUFFICIENT_FUNDS`, `502 PROVIDER_DECLINED` (the simulated provider connector declined
the rail), `400 INVALID_REQUEST`.

## Bills — `/api/v1/bills`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/providers` | — | `{success, providers: [...]}` | |
| GET | `/pending` | — | `{success, bills: [...]}` | |
| POST | `/pay` | `{billId, amount, accountNumber?, provider?}` (+ `Idempotency-Key`) | `{success, message, transaction}` | |
| POST | `/airtime` | `{phoneNumber, amount, provider?}` (+ `Idempotency-Key`) | `{success, message, transaction}` | |

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 WALLET_NOT_FOUND`, `422 INSUFFICIENT_FUNDS`,
`502 PROVIDER_DECLINED`.

No real biller integration exists behind this (REG/WASAC/Irembo/RRA credentials) — see
`docs/TOSS_PARITY_MATRIX.md`'s Bills row; this is a real simulated provider connector, not a
live utility payment.

## Loans — `/api/v1/loans`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/offers` | — | `{success, offers: [...]}` | |
| GET | `/my-loans` | — | `{success, loans: [...]}` | |
| POST | `/apply` | `{loanId, amount}` (+ `Idempotency-Key`) | `{success, message, loan}` | `loanId` here refers to an offer ID |
| POST | `/repay` | `{loanId, amount}` (+ `Idempotency-Key`) | `{success, message, ...repayment fields}` | Repayment result fields are spread into the top level, not nested under a `repayment` key |

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 LOAN_OFFER_NOT_FOUND`, `404 LOAN_NOT_FOUND`,
`404 WALLET_NOT_FOUND`, `403 LOAN_NOT_OWNED`, `409 LOAN_ALREADY_PAID`,
`422 INVALID_LOAN_AMOUNT`, `422 INSUFFICIENT_FUNDS`.

## Credit Score — `/api/v1/credit-score`

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Credit score row.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, score, factors: [...], computedAt}` | Computed live on every call from real data (KYC status, completed transaction count, loan repayment history, savings activity) — not a cached/static value, and not a real credit bureau pull |

`factors` entries: `{name, points, description}`. Score range 300–850. Writes the result back
onto `User.creditScore`, so `GET /api/v1/auth/profile` reflects the most recent computation.
Errors: `404 USER_NOT_FOUND`.

## Savings — `/api/v1/savings`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/goals` | — | `{success, goals: [...]}` | |
| POST | `/goals` | `{name, targetAmount, monthlyContribution?, targetDate?, category?}` | `{success, goal: {...}}` | |
| POST | `/deposit` | `{goalId, amount, fromWalletId?}` (+ `Idempotency-Key`) | `{success, ...}` | |
| GET | `/interest-jar` | — | `{success, ...}` | |
| POST | `/interest-jar/claim` | (+ `Idempotency-Key`) | `{success, ...}` | |

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 GOAL_NOT_FOUND`, `404 WALLET_NOT_FOUND`,
`404 INTEREST_JAR_NOT_FOUND`, `403 WALLET_NOT_OWNED`, `409 NO_INTEREST_AVAILABLE`,
`422 INSUFFICIENT_FUNDS`. Recurring auto-save is built and live-verified 2026-07-13 —
`AutoSaveScheduler` runs on a real 30-day business cadence (30-second poll for demo speed) and
charges `monthlyContribution` from the goal owner's MAIN wallet, skipping gracefully on
insufficient funds. There is no API endpoint for this — it's a background job, not a route; see
`docs/TOSS_PARITY_MATRIX.md`'s Savings row for the full account, including a real scheduler
thread-starvation bug found and fixed alongside it.

## Stocks — `/api/v1/stocks`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, stocks: [...]}` | |
| GET | `/portfolio` | — | `{success, portfolio: [...]}` | |
| POST | `/buy` | `{stockId, shares}` (+ `Idempotency-Key`) | `{success, ...}` | Weighted-average-cost basis recomputed on each buy |
| POST | `/sell` | `{stockId, shares}` (+ `Idempotency-Key`) | `{success, ...}` | |

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 STOCK_NOT_FOUND`, `404 WALLET_NOT_FOUND`,
`422 INSUFFICIENT_SHARES`, `422 INSUFFICIENT_FUNDS`. RSE (Rwanda Stock Exchange) brokerage/
custody integration is blocked (regulatory), not built — stock prices and trades here are
simulated.

## Insurance — `/api/v1/insurance`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/plans` | — | `{success, plans: [...]}` | |
| GET | `/my-policies` | — | `{success, policies: [...]}` | |
| POST | `/enroll` | `{planId}` (+ `Idempotency-Key`) | `{success, ...}` | |
| POST | `/claims` | `{policyId, description, amount}` | `201` `{success, claim}` | Built and live-verified 2026-07-13. No `Idempotency-Key` — filing isn't money-moving, only the ADMIN decide step is. Real `404` if the policy isn't yours, real `409` if it's not `active` |
| GET | `/claims` | — | `{success, claims: [...]}` | Caller's own claims, most recent first |

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 PLAN_NOT_FOUND`, `404 POLICY_NOT_FOUND`,
`409 POLICY_NOT_ACTIVE`, `404 WALLET_NOT_FOUND`, `422 INSUFFICIENT_FUNDS`,
`400 INVALID_REQUEST`. Real insurer quote/bind adapters are not built — this is itunda's own
claims workflow, not a live connection to an actual insurer.

### Insurance claims review — `/api/v1/system/insurance-claims` (ADMIN role only)

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/queue` | — | `{success, queue: [...]}` | All `SUBMITTED` claims, oldest first |
| POST | `/{claimId}/decide` | `{approve, reason?}` | `{success, claim}` | On `approve: true`, really pays out from a new `insurance_claims_expense` ledger account straight into the claimant's wallet — confirmed live, balance moved by the exact claim amount |

Errors: `404 CLAIM_NOT_FOUND`, `409 CLAIM_NOT_PENDING` (already decided), `404 WALLET_NOT_FOUND`,
`422 INSUFFICIENT_FUNDS`.

## Merchant — `/api/v1/merchant`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/register` | `{businessName}` | `{success, merchant: {...}}` | No KYB/business verification — accepts any authenticated user |
| GET | `/me` | — | `{success, merchant: {...}}` | |
| POST | `/qr/generate` | `{amount, description}` | `{success, paymentIntent: {...}}` | |
| POST | `/collect/{intentId}` | (+ `Idempotency-Key`) | `{success, ...}` | Ownership-checked, ledger-backed, real 1.5% fee split |

Errors: `409 MERCHANT_ALREADY_REGISTERED`, `404 MERCHANT_NOT_FOUND`,
`404 WALLET_NOT_FOUND`, `404 PAYMENT_CODE_NOT_FOUND`, `409 PAYMENT_CODE_NOT_PAYABLE`,
`400 SELF_PAYMENT_NOT_ALLOWED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`422 INSUFFICIENT_FUNDS`. No POS/card processing, B2B payroll, or webhooks — see
`docs/TOSS_PARITY_MATRIX.md`'s Merchant row.

## Identity — `/api/v1/identity`

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Compliance row for the
full account of what this closes and what's still blocked (real NIDA/vendor verification).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/submit` | `{documentType, documentNumber, documentReference}` | `201` `{success, submission}` | `documentReference` is a demo-mode stand-in for an uploaded ID scan — no file-storage layer exists. Rejects a second submission while one is already `PENDING` |
| GET | `/status` | — | `{success, submissions: [...]}` | Caller's own submission history, most recent first |

Errors: `409 KYC_SUBMISSION_ALREADY_PENDING`.

## Compliance — `/api/v1/system/compliance` (ADMIN role only)

Mapped under the `system` path prefix specifically so it inherits the existing
`hasRole("ADMIN")` gate — a `USER`-role token gets a real `403` here too.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/queue` | — | `{success, queue: [...]}` | All `PENDING` submissions, oldest first |
| POST | `/{submissionId}/decide` | `{approve, reason?}` | `{success, submission}` | On `approve: true`, really sets `User.kycVerified = true` — confirmed live by re-fetching the user's own `GET /api/v1/auth/profile` afterward, not just asserted from the code |

Errors: `404 KYC_SUBMISSION_NOT_FOUND`, `409 KYC_SUBMISSION_NOT_PENDING` (already decided —
re-deciding is blocked, not silently overwritten), `404 USER_NOT_FOUND`.

## Rewards — `/api/v1/rewards`

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Rewards row for the
full account, including what this corrects (a previous version of that row falsely claimed
this already existed).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/tasks` | — | `{success, tasks: [...], rewardsTotal}` | Static 5-task catalog merged with the caller's own claim status |
| POST | `/claim` | `{taskId}` (+ `Idempotency-Key`) | `{success, message, rewardAmount, newBalance}` | `newBalance` is the caller's new cumulative claimed-rewards total, not their overall wallet balance. Real ledger transaction posts the reward straight into the caller's MAIN wallet |

`RewardTask` shape (as returned in `tasks`): `{id, title, subtitle, rewardAmount, claimed,
claimedAt}`.

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 REWARD_TASK_NOT_FOUND`,
`409 REWARD_TASK_ALREADY_CLAIMED` (a real DB-unique-constraint guard, not just an
application-level check — a race between two concurrent claims for the same task can't both
succeed), `404 WALLET_NOT_FOUND`. The task catalog is static — claiming "Make your first
transfer" is honor-system today, not verified against the caller's actual transaction history.

## Notifications — `/api/v1/notifications`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, notifications: [...]}` | |
| POST | `/{id}/read` | — | `{success, ...}` | |

## Discover — `/api/v1/discover`

| Method | Path | Notes |
|---|---|---|
| GET | `` | All discover items |
| GET | `/{category}` | Filtered by category |

No auth required on either route (no `@AuthenticationPrincipal` in the controller).

## Contacts — `/api/v1/contacts`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, contacts: [...]}` (exact shape — controller returns `ResponseEntity<Any>`, check `ContactsService` directly before building strict client-side types against it) | |
| POST | `` | `{name, bank?, phoneNumber}` | `201`/`200` (verify against controller before relying on the exact status code — not re-confirmed this pass) | |

Errors: `400 INVALID_REQUEST`.

## System — `/api/v1/system` (ADMIN role only)

Requires the `ADMIN` role JWT claim — a `USER`-role token gets a real `403`, not `401`. There
is no self-service role-promotion flow; `ADMIN` only exists via seed data today.

| Method | Path | Notes |
|---|---|---|
| GET | `/dashboard` | **Stub** — returns hardcoded zero values (`todayVolume: 0`, `activeConsents: 0`), not real aggregated data. Controller's own comment: "Simple mock endpoints ... to complete the migration" |
| GET | `/capabilities` | **Stub** — always returns empty `capabilities`/`summary` |
| GET | `/parity` | **Stub** — always returns empty `parity`/`gates` |
| GET | `/rails` | **Stub** — always returns empty `rails` |

Unlike every other module in this file, `SystemController` is real infrastructure (real route,
real RBAC gate) wired to entirely fake data — don't mistake "the endpoint exists and is
protected" for "the endpoint returns anything real." Building this out is open work, not a bug.

### Fraud review — `/api/v1/system/fraud` (ADMIN role only)

Unlike the stubs above, this one is genuinely real end to end. Built and live-verified
2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Operations/Fraud row.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/queue` | — | `{success, queue: [...]}` | All unreviewed flags, oldest first |
| POST | `/{flagId}/decide` | `{decision: "CLEARED" \| "CONFIRMED"}` | `{success, flag}` | Blocks re-deciding an already-reviewed flag |

Flags are raised by `FraudRuleEngine` (lives in `:core`, currently only called from the P2P
payment flow) and never block a transaction — review-only by design. Rules: `HIGH_VALUE`
(≥100,000 RWF), `VELOCITY` (3+ outgoing transactions in 5 minutes), `NEW_RECIPIENT` (first-ever
payment to that recipient). Errors: `404 FRAUD_FLAG_NOT_FOUND`,
`409 FRAUD_FLAG_ALREADY_REVIEWED`.

## What does not exist (previously implied real, or plausible-sounding, but absent)

Grepped for directly, confirmed absent as of 2026-07-13:

- **Any `/users/*` or `/analytics/*` route** (both were invented in the previous version of
  this document). Profile lives at `GET /api/v1/auth/profile`; there is no spending-analytics
  endpoint at all yet (`docs/TOSS_PARITY_MATRIX.md`'s Spending row: `demo`, categorization is
  still target).
- **Webhooks.** No outbound webhook mechanism exists in this API for any event.

## What Changed Since the Last Version of This Document

The previous version described a generic, fictional REST API (`localhost:3000`, a
`status`/`code`/`data`/`timestamp` envelope, endpoints like `/transactions/send` and
`/loans/apply` with a different request shape than the real `/loans/apply`, an `AUTH_001`-style
error code table that doesn't match any real `ApiError.code` value, and fabricated webhook
events). None of it was grounded in `services/backend`. This version was generated by reading
every real `@RestController` in the backend directly on 2026-07-13; anything not explicitly
listed above does not exist yet. See `docs/IMPLEMENTATION_GUIDE.md` and `docs/PAYMENTS.md` for
two other docs with known, not-yet-fixed staleness as of this pass.

**Later the same day:** the Identity and Compliance sections were added — a real `identity`
module, built same-day and live-verified against a running backend (see
`docs/TOSS_PARITY_MATRIX.md`'s Compliance row for the full account). This document's own "what
does not exist" list above used to include KYC/identity submission; it doesn't anymore.

**Same day, again:** the Rewards section was added — a real `rewards` module, also built and
live-verified same-day (claim-once guard confirmed via a real 409 on re-claim, reward amount
confirmed to actually post to the caller's real wallet balance). This document's own "what
does not exist" list used to include `/rewards/*` entirely; it doesn't anymore.

**Same day, a third time:** the Credit Score section was added — `creditScore` used to be a
static, seed-data-only field with no endpoint at all; `GET /api/v1/credit-score` now computes
it live from real account data and was verified to produce the exact expected value (300 base
+ 100 for a real KYC-verified test account, matching the documented factor weights).

**Same day, a fourth time:** `GET /api/v1/wallet/spending` was added — categorization used to
be `target` entirely. Live testing caught a real bug in the first version (bills, airtime, and
transfers all share the `rail_suspense` ledger account, so they merged into one bucket); fixed
using each debit's own memo and re-verified live before this document was updated.

**Same day, a fifth time:** the Overview section was added — `GET /api/v1/overview` aggregates
every real itunda product (wallets, savings, loans, investments, insurance) into one net-worth
figure for the first time; previously only wallets were ever aggregated. Live-verified against
a real account; also surfaced a separate real gap (no `SAVINGS`-type wallet auto-provisioning
at registration, so `POST /api/v1/savings/goals` currently 404s for every new user) — noted in
`docs/TOSS_PARITY_MATRIX.md`'s Account aggregation row, since fixed (see below).

**Same day, a sixth time:** the savings-wallet gap above was fixed — registration now
provisions both `MAIN` and `SAVINGS` wallets, live-verified with a brand-new account
(`POST /api/v1/savings/goals` now returns a real `201`, not the `404` every prior real user hit).

**Same day, a seventh time:** the P2P section was added — the first real wallet-to-wallet
transfer in this backend. Live-verified with two real accounts: a requester starting at 0 RWF
ended at exactly the requested amount after being paid, and saw the transaction in their own
`GET /wallet/transactions` for the first time (the existing transfer flow's hardcoded
`recipientId: "external"` never allowed that).

**Same day, an eighth time:** the System section's Fraud review subsection was added — no
fraud-rule engine existed at all before. A real live bug was caught and fixed mid-build
(evaluating fraud rules after saving the current transaction let it match itself, permanently
masking `NEW_RECIPIENT`); re-verified live after the fix with a genuinely new recipient.

**Same day, a ninth time:** the Savings section's recurring-auto-save note was corrected from
"not implemented" to real. A real, separate infrastructure bug was found and fixed alongside
it — Spring Boot's single-threaded default scheduler pool, shared across every `@Scheduled`
bean in the app, meant the new job ran once at boot and never again until
`spring.task.scheduling.pool.size` was raised in `application.yml`.

**Same day, a tenth time:** the Insurance section's claims endpoints and the new Insurance
claims review admin section were added. Live-verified end to end against a real policy: filed
a claim, saw it in the ADMIN queue, approved it, and confirmed the claimant's wallet balance
moved by the exact claim amount, not just the response body.
