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
| POST | `/register` | none | `{phoneNumber, email?, firstName, lastName, password, referralCode?}` | `201` `AuthResponse` | Added 2026-07-17: `referralCode` is an existing user's own code, optional. Resolved to a real `referredByUserId` — see the Rewards section's `task_referral` |
| POST | `/login` | none | `{phoneNumber, password}` | `200` `AuthResponse` | |
| POST | `/refresh` | none (refresh token is the credential) | `{refreshToken}` | `200` `AuthResponse` | Rotates the refresh token; the old one is revoked immediately |
| POST | `/logout` | Bearer | `{refreshToken?}` (optional body) | `200` `{"success": true}` | Revokes the exact access token used to call this, and the refresh token too if provided |
| GET | `/profile` | Bearer | — | `200` `{"success": true, "user": PublicUser}` | |
| PUT | `/profile/photo` | Bearer | `{profilePhotoUrl}` | `200` `{"success": true, "user": PublicUser}` | Added 2026-07-17. A URL, not a binary upload — no file-storage layer exists in this backend |
| POST | `/profile/verify-email` | Bearer | — | `200` `{"success": true}` | Added 2026-07-17. Generates a real single-use, 30-minute token and delivers it via a real in-app `Notification` (`GET /api/v1/notifications`) — never in this endpoint's own response, since there is no SMTP relay in this backend to send a real email through |
| POST | `/profile/verify-email/confirm` | Bearer | `{token}` | `200` `{"success": true, "user": PublicUser}` | Added 2026-07-17. Real ownership + expiry + single-use check |

`AuthResponse`: `{success: true, message: string, user: PublicUser, accessToken: string,
refreshToken: string}`.

`PublicUser`: `{id, phoneNumber, email, firstName, lastName, kycVerified, creditScore,
createdAt, referralCode, profilePhotoUrl, emailVerified}`. **`kycVerified` is real as a field,
but nothing in this backend can ever set it `true` outside of demo seed data** — see the
Compliance row in `docs/TOSS_PARITY_MATRIX.md`; there is no verification endpoint anywhere in
this API. `referralCode` (added 2026-07-17) is lazily issued at registration — nullable for
accounts that predate this feature. `profilePhotoUrl`/`emailVerified` (added 2026-07-17) back
Rewards' `task_profile` — see that section below.

Errors: `409 PHONE_ALREADY_REGISTERED`, `401 INVALID_CREDENTIALS`, `404 USER_NOT_FOUND`,
`401 INVALID_REFRESH_TOKEN`, `429 RATE_LIMITED`, `400 REFERRAL_CODE_NOT_FOUND` (register only —
an invalid referral code fails loudly rather than silently registering with no attribution),
`400 NO_EMAIL_ON_FILE`, `409 EMAIL_ALREADY_VERIFIED`, `400 INVALID_VERIFICATION_TOKEN` (bogus,
expired, already-used, or belongs to a different user — profile endpoints only).

## Overview — `/api/v1/overview`

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Account aggregation
row. Aggregates every real itunda product into one net-worth figure, plus (also 2026-07-13)
real linked external bank/MoMo accounts — see the Accounts section below for the linking flow
itself.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, netWorth, accounts: [...], savings, loans, investments, insurance, linkedAccounts: [...]}` | `netWorth` = wallet balances + savings + investment cost basis − active loan outstanding. Insurance is excluded from `netWorth` (a paid premium is a sunk expense, not an asset) and reported separately as a coverage summary. `linkedAccounts` is excluded from `netWorth` too — no real external balance exists to add |

`accounts`: `[{id, type, name, balance, currency}]`. `savings`: `{totalSaved, goalCount}`.
`loans`: `{totalOutstanding, activeCount}` (active loans only — a fully `PAID` loan doesn't
count against outstanding). `investments`: `{totalCostBasis, holdingCount}` — cost basis
(`shares × avgPrice`), not live market value; the stock price catalog lives in the `:stocks`
module and no module in this backend depends on another feature module. `insurance`:
`{activePolicyCount, totalMonthlyPremium}`. `linkedAccounts`:
`[{id, provider, maskedAccountNumber, status}]` — only rows still in `LINKED` status; no
balance field, ever (see the Accounts section for why).

## Accounts — `/api/v1/accounts`

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Account aggregation
row. A real external bank/MoMo consent registry (`LinkedAccount`/`linked_accounts`), closing
what that row previously named as fully target. Verification is a real call through the same
`RailCatalog`/`ProviderConnector` simulation every other rail-calling flow in this backend
uses (transfers, bills, airtime) — linking "MTN MoMo" genuinely exercises the `mtn_momo` rail
profile's real latency/success-rate, including a real chance of `VERIFICATION_FAILED`, not an
unconditional success. **Deliberately never stores or surfaces a live external balance** —
this backend has no real provider access to fetch one from, and fabricating one would
misrepresent this as more integrated than it honestly is.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/link` | `{provider, externalAccountNumber}` | `{success, linkedAccount}` | `externalAccountNumber` must be ≥4 chars (`400 INVALID_REQUEST` otherwise); only the last 4 digits are ever stored, as `•••• 1234`. `linkedAccount.status` is `LINKED` on a successful provider verification or `VERIFICATION_FAILED` (with `failureReason`) on a real decline — both are saved, a decline is not an error response |
| GET | `/linked` | — | `{success, linkedAccounts: [...]}` | Caller's own full history, most recent first — includes `VERIFICATION_FAILED` and `UNLINKED` rows, unlike `GET /overview`'s filtered view |
| POST | `/link/{accountId}/unlink` | — | `{success, linkedAccount}` | Sets `status: UNLINKED`, `unlinkedAt`. `404 LINKED_ACCOUNT_NOT_FOUND` if the id doesn't exist, `403 LINKED_ACCOUNT_NOT_OWNED` if it belongs to a different user, `409 LINKED_ACCOUNT_ALREADY_UNLINKED` if already unlinked |

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
| POST | `/budgets` | `{category?, monthlyLimit}` | `{success, budget: {...}}` | Built and live-verified 2026-07-13. `category` null means an overall budget; otherwise must match a real `/spending` category name. Upserts the current real calendar month's budget |
| GET | `/budgets` | — | `{success, budgets: [...]}` | Each entry: `{category, monthlyLimit, spent, remaining, percentUsed, status: "UNDER"\|"NEAR"\|"OVER"}`, `spent` computed live against `/spending`'s real categorization. Crossing 80%/100% writes a real `BUDGET_NEAR`/`BUDGET_OVER` notification (see `## Notifications` above), once per threshold per month |
| POST | `/transfer/quote` | `{amount, recipient, fromWalletId?, description?}` | `{success, quote: {...}}` | Quote expires after 60 seconds |
| POST | `/transfer/confirm` | `{quoteId}` (+ `Idempotency-Key`) | `{success, message, transaction, newBalance}` | Requires the quote from `/transfer/quote`; posts through the double-entry ledger. Real per-rail routing as of 2026-07-13 — `recipient`'s phone prefix (078 → MTN, 072/073 → Airtel, per RURA's numbering plan) resolves the real rail instead of always falling through to `generic` |

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 WALLET_NOT_FOUND`, `403 WALLET_NOT_OWNED`,
`404 QUOTE_NOT_FOUND`, `409 QUOTE_EXPIRED`, `409 QUOTE_ALREADY_USED`,
`422 INSUFFICIENT_FUNDS`, `403 WALLET_FROZEN` (see `## Support` below), `502 PROVIDER_DECLINED`
(the simulated provider connector declined the rail), `400 INVALID_REQUEST`.

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
| POST | `/apply` | `{loanId, amount}` (+ `Idempotency-Key`) | `{success, message, loan}` | `loanId` here refers to an offer ID. `loan.creditScore` is the applicant's real score at approval time (see Credit Score section) |
| POST | `/repay` | `{loanId, amount}` (+ `Idempotency-Key`) | `{success, message, ...repayment fields}` | Repayment result fields are spread into the top level, not nested under a `repayment` key |

Real risk governance, built and live-verified 2026-07-13, gates `/apply`: a minimum credit
score of 400 to qualify for any loan, a higher bar of 600 for amounts over half the offer's
limit, and a cap of 2 concurrent active loans (checked before the score, cheapest check first).
Uses the exact same live computation as `GET /api/v1/credit-score` (moved into `:core` so both
modules share it), not a separate or cached copy.

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 LOAN_OFFER_NOT_FOUND`, `404 LOAN_NOT_FOUND`,
`404 WALLET_NOT_FOUND`, `403 LOAN_NOT_OWNED`, `409 LOAN_ALREADY_PAID`,
`422 INVALID_LOAN_AMOUNT`, `422 LOAN_APPLICATION_DECLINED`, `422 INSUFFICIENT_FUNDS`.

## Credit Score — `/api/v1/credit-score`

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Credit score row.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, score, factors: [...], computedAt}` | Computed live on every call from real data (KYC status, completed transaction count, loan repayment history, savings activity) — not a cached/static value, and not a real credit bureau pull |

`factors` entries: `{name, points, description}`. Score range 300–850. Writes the result back
onto `User.creditScore`, so `GET /api/v1/auth/profile` reflects the most recent computation.
Errors: `404 USER_NOT_FOUND`. The computation itself lives in `:core`'s `CreditScoreService`
(moved there 2026-07-13, was originally in the `creditscore` module alone) specifically so
`POST /api/v1/loans/apply`'s real risk governance can reuse the exact same score, not a
duplicated or cached copy — see the Loans section.

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
| POST | `/webhook-url` | `{webhookUrl}` | `{success, merchant: {...}}` | Built and live-verified 2026-07-13. Registers the URL `collect`/Commerce order updates/`/api/v1/pay/*` deliver events to |
| POST | `/webhook-secret/generate` | — | `{success, webhookSecret}` | Real, added 2026-08-30. `whsec_`-prefixed, shown exactly once, stored in plaintext (itunda re-signs every future delivery with it, unlike the one-way-hashed API key). Every webhook delivery to this merchant's `webhookUrl` carries an `X-Itunda-Signature: HMAC-SHA256(rawBody, secret)` header once generated — see `docs/PAYMENTS.md`'s Webhooks section for the full receiver-side verification contract |
| POST | `/qr/generate` | `{amount, description}` | `{success, paymentIntent: {...}}` | |
| POST | `/collect/{intentId}` | (+ `Idempotency-Key`) | `{success, ...}` | Ownership-checked, ledger-backed, real 1.5% fee split. On success, if the merchant has a `webhookUrl`, delivers a real `PAYMENT_STATUS_CHANGED` HTTP POST (Toss Payments' documented shape). Real persistent retry as of 2026-07-13 (7 attempts, 1/4/16/64/256/1024/4096-minute schedule, matching Toss's own documented retry policy exactly) — failure never blocks or rolls back the payment, see `docs/TOSS_PARITY_MATRIX.md`'s Merchant row |

Errors: `409 MERCHANT_ALREADY_REGISTERED`, `404 MERCHANT_NOT_FOUND`,
`404 WALLET_NOT_FOUND`, `404 PAYMENT_CODE_NOT_FOUND`, `409 PAYMENT_CODE_NOT_PAYABLE`,
`400 SELF_PAYMENT_NOT_ALLOWED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`422 INSUFFICIENT_FUNDS`. No POS/card processing or B2B payroll — see
`docs/TOSS_PARITY_MATRIX.md`'s Merchant row.

## Face Pay — `/api/v1/facepay`

Built and live-verified 2026-07-13, closing docs/TOSS_PARITY_MATRIX.md's Face Pay row —
see that row for the full live-verified lifecycle account. Reuses the exact same
`PaymentIntent`/ledger/fee/fraud flow as `POST /merchant/collect/{intentId}` (same
`pi_...` ids from `POST /merchant/qr/generate`), differing only in the authentication
factor: a real, on-device biometric enrollment (`NIDABiometricAuth`) instead of a QR
scan. **Stores zero biometric data** — enrollment is a real, revocable opt-in flag, not
a face template/image/hash.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/enroll` | — | `{success, enrollment: {...}}` | Idempotent — re-enrolling after a revoke reactivates the same row rather than creating a duplicate (`user_id` is unique) |
| POST | `/revoke` | — | `{success, enrollment: {...}}` | `404 FACEPAY_NOT_ENROLLED` if never enrolled |
| GET | `/status` | — | `{success, enrolled, enrollment}` | `enrollment` is `null` if never enrolled or currently revoked |
| POST | `/collect/{intentId}` | (+ `Idempotency-Key`) | `{success, ..., channel: "FACE_PAY"}` | Requires an active enrollment (`403 FACEPAY_NOT_ENROLLED` otherwise); same ownership/expiry/self-payment/fraud checks as `/merchant/collect/{intentId}`. `Transaction.channel` is now real and populated for the first time in this backend (`"FACE_PAY"` here, `"QR"` for merchant collection) |

Errors: `403 FACEPAY_NOT_ENROLLED`, `404 MERCHANT_NOT_FOUND`, `404 WALLET_NOT_FOUND`,
`404 PAYMENT_CODE_NOT_FOUND`, `409 PAYMENT_CODE_NOT_PAYABLE`,
`400 SELF_PAYMENT_NOT_ALLOWED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`422 INSUFFICIENT_FUNDS`, `403 WALLET_FROZEN`.

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
this already existed). Real per-task activity verification (not honor-system) added
2026-07-16/17 for all 5 of the 5 catalog tasks — see that same row for the full account of each.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/tasks` | — | `{success, tasks: [...], rewardsTotal}` | Static 5-task catalog merged with the caller's own claim status and a real per-task `eligible` boolean |
| POST | `/claim` | `{taskId}` (+ `Idempotency-Key`) | `{success, message, rewardAmount, newBalance}` | `newBalance` is the caller's new cumulative claimed-rewards total, not their overall wallet balance. Real ledger transaction posts the reward straight into the caller's MAIN wallet |
| GET | `/referral` | — | `{success, referralCode, referredCount, completedReferralCount}` | Added 2026-07-17. The caller's own referral code (nullable — accounts created before this feature have none until they register again isn't applicable; see `AuthService`) plus real progress toward `task_referral` |

`RewardTask` shape (as returned in `tasks`): `{id, title, subtitle, rewardAmount, claimed,
claimedAt, eligible}`.

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 REWARD_TASK_NOT_FOUND`,
`403 REWARD_TASK_NOT_ELIGIBLE` (real activity check, before ever touching the wallet or ledger),
`409 REWARD_TASK_ALREADY_CLAIMED` (a real DB-unique-constraint guard, not just an
application-level check — a race between two concurrent claims for the same task can't both
succeed), `404 WALLET_NOT_FOUND`, `404 USER_NOT_FOUND`. All 5 catalog tasks
(`task_first_transfer`, `task_first_bill`, `task_savings_goal`, `task_referral`,
`task_profile`) are real-activity-verified as of 2026-07-17 — see the Auth section above for
the `profile/photo` and `profile/verify-email` endpoints `task_profile` checks.

## Notifications — `/api/v1/notifications`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, notifications: [...], unreadCount}` | Real bug fixed 2026-07-13: previously read `Authentication.name` as the userId, which silently resolved to the stringified `CurrentUser` object rather than the real id, so this always returned zero results regardless of how many notifications actually existed for the caller — fixed to `@AuthenticationPrincipal CurrentUser`, matching every other controller |
| POST | `/{id}/read` | — | `{success}` | `id` may be `"all"`. Real ownership check — a notification only flips to read if it belongs to the caller |

As of 2026-07-13, real budget-threshold alerts (`BUDGET_NEAR`/`BUDGET_OVER`, see `## Wallet`
below) are the only thing in this backend that actually writes to this table outside of demo
seed data.

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

## Support — `/api/v1/support`

Built and live-verified 2026-07-13, closing a false "real" claim
`docs/TOSS_PARITY_MATRIX.md`'s Non-Negotiable Gates section previously made about this exact
workflow with nothing actually built — see that section's Customer support entry for the full
account, and `### Support review` above for the ADMIN-gated queue/resolve endpoints.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/tickets` | `{transactionId, category: "GENERAL" \| "PAYMENT_DISPUTE" \| "ACCOUNT_TAKEOVER", description}` | `{success, ticket}` | Ownership-checked — the transaction must involve the caller as sender or recipient. `ACCOUNT_TAKEOVER` real-freezes the caller's own wallet from that transaction (`Wallet.isActive = false`, genuinely enforced in `LedgerService`, not cosmetic) until a reviewer resolves the ticket |
| GET | `/tickets` | — | `{success, tickets: [...]}` | Caller's own tickets, newest first |

Real itunda-defined SLA (not a sourced Toss number — Toss doesn't publish one): 4 hours for
`ACCOUNT_TAKEOVER`, 48 hours for `PAYMENT_DISPUTE`, 72 hours for `GENERAL`. Errors:
`404 TRANSACTION_NOT_FOUND`, `403 TRANSACTION_NOT_OWNED`.

## Offline actions — `/api/v1/actions`

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Offline row for the full
account, including what's still missing on the mobile client side.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/types` | — | `{success, types: [...]}` | Every registered `BatchActionHandler`'s action type, e.g. `["BILL_PAY", "BUY_AIRTIME", "SAVINGS_DEPOSIT"]` |
| POST | `/batch` | `{actions: [{clientActionId, type, idempotencyKey, body}]}` | `{success, results: [...]}` | Always `200` at the batch level — each action carries its own `status`/`body`, matching exactly what that action's own single-action endpoint would have returned |

Each action's `body` is dispatched to the real domain service for its `type`, through the same
`IdempotencyService.replayOrExecute` path the equivalent single-action endpoint already uses —
resubmitting a batch with the same `idempotencyKey`s replays already-completed actions rather
than double-executing them (live-verified: two identical batch submissions with a fixed key
returned the same transaction id both times). One action failing never fails the rest of the
batch — live-verified a real 3-action batch that returned a real `200` (airtime purchase), a real
`400` (`UNKNOWN_ACTION_TYPE`), and a real `404` (`GOAL_NOT_FOUND`) together in one response.
Deliberately has no `WALLET_TRANSFER` action type — `WalletService`'s quote/confirm split has a
real 60-second quote expiry that a queued-while-offline confirm can't honor safely.

## System — `/api/v1/system` (ADMIN role only)

Requires the `ADMIN` role JWT claim — a `USER`-role token gets a real `403`, not `401`. There
is no self-service role-promotion flow; `ADMIN` only exists via seed data today.

| Method | Path | Notes |
|---|---|---|
| GET | `/dashboard` | **Stub** — returns hardcoded zero values (`todayVolume: 0`, `activeConsents: 0`), not real aggregated data. Controller's own comment: "Simple mock endpoints ... to complete the migration" |
| GET | `/capabilities` | **Stub** — always returns empty `capabilities`/`summary` |
| GET | `/parity` | **Stub** — always returns empty `parity`/`gates` |
| GET | `/rails` | **Real, fixed 2026-07-13** — see below, no longer a stub |

`/dashboard`, `/capabilities`, `/parity` remain wired to entirely fake data — don't mistake "the
endpoint exists and is protected" for "the endpoint returns anything real." Building those out is
open work, not a bug.

### Rail health — `/api/v1/system/rails` (ADMIN role only)

Fixed 2026-07-13 (previously always returned `{rails: []}`) — see
`docs/TOSS_PARITY_MATRIX.md`'s Operations/Provider health row for the full account.

| Method | Path | Success | Notes |
|---|---|---|---|
| GET | `/` | `{success, rails: [...]}` | Real, measured per-rail health |

Each entry: `railId`, `displayName`, `totalAttempts`, `successCount`, `failureCount`,
`successRate`, `avgLatencyMs`, `status` (`"HEALTHY"` or `"INCIDENT"` — cross-referenced against
`IncidentDetector`'s open incidents). A rail with zero real attempts since the process started
doesn't appear in the list at all — no fabricated zero-row. Built from `ProviderHealthTracker`
(`:core`), which observes every real attempt through `SimulatedProviderConnector` — these are
*measured* numbers, not a replay of the static per-rail simulation config, and will diverge from
it over a small sample.

### Reconciliation — `/api/v1/system/reconciliation` (ADMIN role only)

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Operations/Reconciliation
row for the full account, including why this replaced a previously false claim.

| Method | Path | Query | Success | Notes |
|---|---|---|---|---|
| GET | `/` | `date` (optional, `YYYY-MM-DD`, defaults to today) | `{success, date, rails: [...]}` | Real per-rail totals for that day, from a real persisted attempt log |

Each `rails` entry: `railId`, `displayName`, `totalAttempts`, `successCount`, `failureCount`,
`successRate`, `avgLatencyMs` — the persisted, by-day counterpart to `/api/v1/system/rails`'s
in-memory live view (that one resets on restart; this one doesn't — live-verified with a real
process restart). Errors: `400 INVALID_DATE_FORMAT` for a malformed `date`. Still honestly
one-sided: reconciles itunda's own attempt log against itself — there's no real external
settlement file to diff it against.

### Fraud review — `/api/v1/system/fraud` (ADMIN role only)

Unlike the stubs above, this one is genuinely real end to end. Built and live-verified
2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Operations/Fraud row.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/queue` | — | `{success, queue: [...]}` | All unreviewed flags, oldest first |
| POST | `/{flagId}/decide` | `{decision: "CLEARED" \| "CONFIRMED"}` | `{success, flag}` | Blocks re-deciding an already-reviewed flag |

Flags are raised by `FraudRuleEngine` (lives in `:core`, called from P2P, wallet transfer, and
merchant collection as of 2026-07-13 — every real money-moving flow in this backend except
bills/loans/stocks/savings/insurance, which don't move money between two itunda users) and
never block a transaction — review-only by design. Rules: `HIGH_VALUE` (≥100,000 RWF),
`VELOCITY` (3+ outgoing transactions in 5 minutes), `NEW_RECIPIENT` (first-ever payment to that
recipient). Errors: `404 FRAUD_FLAG_NOT_FOUND`, `409 FRAUD_FLAG_ALREADY_REVIEWED`.

### Support review — `/api/v1/system/support` (ADMIN role only)

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Non-Negotiable Gates
"Customer support workflow" entry for the full account. See `## Support` below for the
user-facing ticket-creation/listing endpoints this queue reviews.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/queue` | — | `{success, queue: [...]}` | Open tickets, overdue-first then soonest-due-first |
| POST | `/{ticketId}/resolve` | `{resolution: "REFUNDED" \| "REJECTED", notes?}` | `{success, ticket}` | `REFUNDED` posts a real reversing ledger transaction (every original leg flipped, same accounts and amounts); either decision unfreezes a wallet the ticket froze. Blocks re-resolving |

Errors: `404 SUPPORT_TICKET_NOT_FOUND`, `409 SUPPORT_TICKET_ALREADY_RESOLVED`,
`422 REFUND_SOURCE_TRANSACTION_MISSING`.

### Incidents — `/api/v1/system/incidents` (ADMIN role only)

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Operations/Incidents
row for the full account, including a real `@Transactional` propagation bug found and fixed
during live verification.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/` | — | `{success, incidents: [...]}` | All incidents, newest-opened first |
| POST | `/{incidentId}/resolve` | — | `{success, incident}` | Sets `RESOLVED`, records reviewer + timestamp |

`IncidentDetector` (lives in `:core`) is hooked into `SimulatedProviderConnector.attempt` — the
single choke point every rail-calling flow (`WalletService.confirmTransfer`,
`BillsService.payBill`/`buyAirtime`) passes through. 2+ real provider declines from the same rail
within a rolling 5-minute window auto-opens one `Incident` per rail (a second open incident for
an already-`OPEN` rail is suppressed, not duplicated). Errors: `404 INCIDENT_NOT_FOUND`,
`409 INCIDENT_ALREADY_RESOLVED`. Live-verified end to end: ~180 real attempts against an
unmatched-provider rail produced 2 real declines and a real persisted `OPEN` incident; a
non-admin token real-403s on resolve, the admin resolve real-200s, resolving again real-409s,
resolving an unknown id real-404s.

## What does not exist (previously implied real, or plausible-sounding, but absent)

Grepped for directly, confirmed absent as of 2026-07-13:

- **Any `/users/*` or `/analytics/*` route** (both were invented in the previous version of
  this document). Profile lives at `GET /api/v1/auth/profile`; there is no spending-analytics
  endpoint at all yet (`docs/TOSS_PARITY_MATRIX.md`'s Spending row: `demo`, categorization is
  still target).
- ~~Webhooks for anything other than merchant collection.~~ **Stale as of 2026-08-30** — real
  as of 2026-07-13 was `POST /api/v1/merchant/webhook-url` + `PAYMENT_STATUS_CHANGED`/
  `CANCEL_STATUS_CHANGED` delivery on collection/cancel (Merchant section). Since then, the
  external "Pay with itunda" checkout API (`/api/v1/pay/*`, see `docs/PAYMENTS.md`) reuses
  the identical mechanism, and Commerce order lifecycle (`OrderService.placeOrder`/
  `updateOrderStatus`/`claimDelivery`/`completeDelivery`/`cancelOrder`) now fires a real
  `ORDER_STATUS_CHANGED` event on the same `webhookUrl` — one shared
  `WebhookDeliveryService`, not a second bespoke mechanism. Also new 2026-08-30: real
  `X-Itunda-Signature` HMAC-SHA256 verification (`POST /api/v1/merchant/webhook-secret/
  generate`) on every event type, closing the "any attacker who learns a webhook URL can
  forge a delivery" gap this document itself never flagged as a risk until now.

## What Changed Since the Last Version of This Document

**2026-07-17, later the same day:** added `PUT /api/v1/auth/profile/photo`,
`POST /api/v1/auth/profile/verify-email`, and `POST /api/v1/auth/profile/verify-email/confirm`
— closes `task_profile`, the last of Rewards' 5 catalog tasks to become real-activity-verified.
See the Auth and Rewards sections above.

**2026-07-17:** added `GET /api/v1/rewards/referral` (real referral code + progress) and a
`referralCode` field to `POST /api/v1/auth/register`'s request and `PublicUser` — see the Auth
and Rewards sections above. This doc otherwise wasn't kept current through several passes
between 2026-07-13 and 2026-07-17 (merchant reports, webhook retry, all-four-mini-app iOS work,
etc. shipped without a matching entry here) — `docs/TOSS_PARITY_MATRIX.md` is the source of
truth for what's real as of any given date; treat gaps in this changelog as this doc's own
staleness, not as those features not existing.

**2026-07-13, latest of all:** added `/api/v1/accounts/link`, `/api/v1/accounts/linked`,
`/api/v1/accounts/link/{accountId}/unlink` (real, live-verified external bank/MoMo consent
registry) and extended `GET /api/v1/overview` with a `linkedAccounts` field — see the Accounts
and Overview sections above.

**2026-07-13, truly latest:** added `/api/v1/system/reconciliation` (real, live-verified,
persisted per-rail-per-day report) — see the Reconciliation section above.

**2026-07-13, latest:** added `/api/v1/actions/batch` and `/api/v1/actions/types` (real,
live-verified offline-queue-replay endpoints) — see the Offline actions section above.

**2026-07-13, even later still:** `/api/v1/system/rails` fixed from an always-empty stub to a
real, measured per-rail health endpoint — see the Rail health section above.

**2026-07-13, later still:** added `/api/v1/system/incidents` (real, ADMIN-gated auto-detection
and resolve flow) — see the Incidents section above.

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

**Same day, an eleventh time:** the Merchant section's `webhook-url` endpoint and webhook
delivery note were added — verified with an actual HTTP listener process on a separate host,
not a mocked call: the real `PAYMENT_STATUS_CHANGED` payload arrived over the network with a
logged 200 response. Also verified a payment still completes when the registered endpoint is
unreachable.

**Same day, a twelfth time:** the Loans section's real risk-governance note was added, and
`CreditScoreService` moved from the `creditscore` module into `:core` so `/loans/apply` could
reuse its exact computation. Live-verified with three real accounts at different real score
tiers, each declining for the specific documented reason (below minimum, below the high-amount
tier, or the concurrent-loan cap), not a generic rejection.
