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

## USSD — `/api/v1/ussd`

**Added 2026-09-05, same slice.** Real USSD basic-banking access — a feature-phone
entry point alongside the app, for a real Africa's Talking-style USSD gateway to call
server-to-server. Confirmed by direct read of `UssdController.kt` (2 endpoints, 3
error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/session` (form-encoded, `text/plain` response) | form fields `sessionId`, `phoneNumber`, `text?` | plain-text `CON ...` (more menu) or `END ...` (session done) | Called by the gateway itself, not a logged-in itunda user — `permitAll`'d in `SecurityConfig`. Gated instead by an `X-Ussd-Gateway-Secret` header, compared with `MessageDigest.isEqual` (constant-time — a real fix, 2026-09-04, for a plain `!=` string-compare timing side-channel). Empty `gatewaySecret` (the dev/CI default) disables the check entirely; a real deployment sets `USSD_GATEWAY_SECRET` |
| POST | `/pin` | `{pin}` | `201 {success}` | Sets the caller's own USSD PIN, over the normal authenticated JWT gate (not the gateway-secret one above) |

Errors (all 3 real `@ExceptionHandler`s): `400 INVALID_PIN`,
`401 USSD_GATEWAY_UNAUTHORIZED`, `429 RATE_LIMITED`.

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
recipient-to-recipient — see that row for the full account). **Fully re-documented 2026-09-05**
(this section previously covered only 3 of the controller's 10 real endpoints and 8 of its 16
real error codes) — read directly from `P2pController.kt`.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/transfer-limit` | — | `{success, perTransferLimit, dailyLimit, remainingToday}` | Real Toss Bank "Transfer limit" row — surfaces the flat per-transfer/daily caps proactively instead of only reactively as a decline |
| POST | `/request` | `{amount, description}` | `201 {success, request: {...}}` | Creates a real `PENDING` request, 15-minute expiry (same convention as merchant QR) |
| GET | `/requests` | — | `{success, requests: [...]}` | Caller's own requests, most recent first |
| POST | `/pay/{requestId}` | — (+ `Idempotency-Key`) | `{success, message, transaction, newBalance}` | Direct `WALLET`→`WALLET` ledger pair, no fee |
| GET | `/recipient?identifier` | — | `{success, recipient: {...}}` | Real Toss/Kakao Bank-style recipient-name confirmation ("받는분 성함 확인") — resolves a phone/account number to a real display name BEFORE the sender confirms the send, catching a mistyped digit before money moves. Read-only, no `Idempotency-Key` |
| POST | `/send` | `{recipient, amount, description?, fromAccountId?}` (+ `Idempotency-Key`) | `{success, message, transaction, newBalance, fraudWarnings: [...]}` | Real direct push-transfer, no pre-existing request needed. `fraudWarnings` are real Toss "Fraud Suspicion Siren" (사기의심 사이렌) friendly strings (new recipient / high value / velocity) — purely informational, the transfer has already completed by the time they're returned |
| POST | `/send-to-family` | `{childUserId, amount, description?}` (+ `Idempotency-Key`) | `{success, message, transaction, newBalance}` | Real Naver Pay 가족 공유 자산 관리 (family shared asset management) — instant transfer to a linked family member, gated by the real `FamilySpendLimitExceededException` (guardian-set daily cap) |
| POST | `/send-delayed` | `{recipient, amount, description?}` (+ `Idempotency-Key`) | `201 {success, message, transfer: {...}}` | Real Korean 지연이체서비스 (Delayed Transfer Service) — an opt-in alternative to `/send` that holds the money for a real window instead of landing instantly, so a transfer made under phishing pressure (or a fat-fingered recipient) can still be cancelled |
| GET | `/delayed-transfers` | — | `{success, transfers: [...]}` | |
| POST | `/delayed-transfers/{transferId}/cancel` | — | `{success, message, transfer: {...}}` | Refunds the held amount back to the sender immediately. No `Idempotency-Key` — a retried cancel of an already-cancelled transfer just gets a real 409, never double-refunds |

Errors (all 16 real `@ExceptionHandler`s): `404 P2P_DELAYED_TRANSFER_NOT_FOUND`,
`409 P2P_DELAYED_TRANSFER_NOT_CANCELLABLE`, `404 P2P_REQUEST_NOT_FOUND`,
`409 P2P_REQUEST_NOT_PAYABLE` (already paid, or expired — a real request past its
`expiresAt` gets marked `EXPIRED` on the attempt, not silently allowed),
`400 SELF_PAYMENT_NOT_ALLOWED`, `404 ACCOUNT_NOT_FOUND`, `422 INSUFFICIENT_FUNDS`,
`403 ACCOUNT_FROZEN`, `403 FAMILY_SPEND_LIMIT_EXCEEDED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`, `429 RATE_LIMITED`,
`404 P2P_RECIPIENT_NOT_FOUND`, `400 INVALID_AMOUNT`, `422 P2P_TRANSFER_LIMIT_EXCEEDED`.
**Corrected 2026-09-04** — real code is `ACCOUNT_NOT_FOUND` (confirmed via
`P2pController.kt`'s own `@ExceptionHandler` list), `WALLET_NOT_FOUND` does not exist.

## Auto-Transfers — `/api/v1/p2p/auto-transfers`

**Added 2026-09-05.** Real Toss Bank 자동이체 (auto-transfer) equivalent — a recurring
transfer rule, executed later by a scheduler reusing `P2pService.sendDirect` unmodified,
not a separate payment rail.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{recipient, amount, frequency, dayOfWeek?, dayOfMonth?, description?}` (+ `Idempotency-Key`) | `201 {success, autoTransfer: {...}}` | `frequency` is `WEEKLY`/`MONTHLY`-shaped (`AutoTransferFrequency`). Idempotency-Key added after a real gap: a retry here doesn't double-charge immediately (money only moves later when the scheduler processes a due row), but would silently create a SECOND active recurring rule — a duplicate charge every period going forward, not just once |
| GET | `` (base path) | — | `{success, autoTransfers: [...]}` | |
| POST | `/{id}/pause` | — | `{success, autoTransfer: {...}}` | |
| POST | `/{id}/resume` | — | `{success, autoTransfer: {...}}` | |
| DELETE | `/{id}` | — | `{success, autoTransfer: {...}}` | |

Errors: `404 AUTO_TRANSFER_NOT_FOUND`, `400 INVALID_SCHEDULE`, `404 P2P_RECIPIENT_NOT_FOUND`,
`400 SELF_PAYMENT_NOT_ALLOWED`, `404 ACCOUNT_NOT_FOUND`, `400 INVALID_AMOUNT`,
`422 INSUFFICIENT_FUNDS`, `429 RATE_LIMITED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`.

## Scheduled Transfers — `/api/v1/p2p/scheduled-transfers`

**Added 2026-09-05.** Real Toss 예약송금 (scheduled/reserved ONE-TIME transfer) — distinct
from Auto-Transfers above (recurring); also executed later via `P2pService.sendDirect`.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{recipient, amount, scheduledDate, description?}` (+ `Idempotency-Key`) | `201 {success, scheduledTransfer: {...}}` | |
| GET | `` (base path) | — | `{success, scheduledTransfers: [...]}` | |
| POST | `/{id}/cancel` | — | `{success, scheduledTransfer: {...}}` | |

Errors: `404 SCHEDULED_TRANSFER_NOT_FOUND`, `409 SCHEDULED_TRANSFER_NOT_PENDING`,
`400 INVALID_SCHEDULED_DATE`, `404 P2P_RECIPIENT_NOT_FOUND`, `400 SELF_PAYMENT_NOT_ALLOWED`,
`404 ACCOUNT_NOT_FOUND`, `400 INVALID_AMOUNT`, `422 INSUFFICIENT_FUNDS`, `429 RATE_LIMITED`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`.

## Scam Reports — `/api/v1/p2p/scam-reports`

**Added 2026-09-05.** Real Toss 사기계좌 조회 (fraud-account lookup before transfer)-style
scam reporting — lets any user flag a phone/account identifier as a scam, and lets a SENDER
check that flag before transferring to it.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{identifier, reason}` | `201 {success, report: {...}}` | |
| GET | `/check?identifier` | — | `{success, result}` | Public — no auth-scoped filtering, checked before a transfer commits |
| GET | `/mine` | — | `{success, reports: [...]}` | Caller's own submitted reports |

Errors: `400 INVALID_SCAM_REPORT`, `409 SCAM_REPORT_ALREADY_EXISTS`, `429 RATE_LIMITED`.

## Account — `/api/v1/account`

**Corrected 2026-09-04** — this section previously said `/api/v1/wallet`, a prefix
that does not exist anywhere in the real backend (confirmed via
`grep -r '@RequestMapping("/api/v1/wallet'`, zero matches); the real controller is
`AccountController.kt`, mounted at `/api/v1/account`, and several of the error codes
below were also stale (`WALLET_NOT_FOUND`/`WALLET_FROZEN` don't exist — the real
codes are `ACCOUNT_NOT_FOUND`/`ACCOUNT_FROZEN`). Re-verified against the real
controller's current `@GetMapping`/`@PostMapping`/`@ExceptionHandler` list, not
re-derived from memory.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, accounts: [...]}` | |
| GET | `/{id}` | — | `{success, account: {...}}` | |
| GET | `/transactions` | — | `{success, transactions: [...]}` | Real transaction history, backs the transaction-history screen on both mobile platforms |
| GET | `/spending` | — | `{success, categories: [...], totalSpent}` | Categorizes by looking up each debit's real ledger counterpart, not the `transactions` table (only P2P transfers ever write a row there). `categories`: `[{name, amount}]`, largest first |
| POST | `/budgets` | `{category?, monthlyLimit}` | `{success, budget: {...}}` | `category` null means an overall budget; otherwise must match a real `/spending` category name. Upserts the current real calendar month's budget |
| GET | `/budgets` | — | `{success, budgets: [...]}` | Each entry: `{category, monthlyLimit, spent, remaining, percentUsed, status: "UNDER"\|"NEAR"\|"OVER"}`, `spent` computed live against `/spending`'s real categorization. Crossing 80%/100% writes a real `BUDGET_NEAR`/`BUDGET_OVER` notification (see `## Notifications` above), once per threshold per month |
| POST | `/transfer/quote` | `{amount, recipient, fromAccountId?, description?}` | `{success, quote: {...}}` | Quote expires after 60 seconds |
| POST | `/transfer/confirm` | `{quoteId}` (+ `Idempotency-Key`) | `{success, message, transaction, newBalance}` | Requires the quote from `/transfer/quote`; posts through the double-entry ledger. Real per-rail routing — `recipient`'s phone prefix (078 → MTN, 072/073 → Airtel, per RURA's numbering plan) resolves the real rail instead of always falling through to `generic`. **Real 30/hour per-user rate limit added 2026-09-04** (`RATE_LIMITED`), same baseline every other real money-moving endpoint in this backend uses |

Not yet documented here (real endpoints, confirmed present in the controller, not
written up in this pass — `/{id}/transactions`, `/transactions/timeline`,
`/spending/monthly-report`, `/business-expense-summary`, `/subscriptions`,
`/agent-withdrawal-authorizations` and its `/cancel` sibling): a real, disclosed gap,
not silently skipped — add these in a dedicated future pass rather than assuming this
section is now exhaustive.

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 ACCOUNT_NOT_FOUND`,
`404 QUOTE_NOT_FOUND`, `409 QUOTE_EXPIRED`, `409 QUOTE_ALREADY_USED`,
`422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`, `502 PROVIDER_DECLINED`
(the simulated provider connector declined the rail), `400 INVALID_REQUEST`,
`429 RATE_LIMITED`.

## Youth Account — `/api/v1/account/youth`

**Added 2026-09-05** (twenty-fourth documentation slice). Real KakaoBank mini-style
capped starter account, age-gated with a real daily/monthly/total-balance cap.
Confirmed by direct read of `YouthAccountController.kt` (2 endpoints, 11 error
codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/open` | — | `201 {success, account: {...}}` | |
| POST | `/deposit` | `{amount}` (+ `Idempotency-Key`) | `{success, ...}` | Real bug fixed 2026-08-05: previously had no `Idempotency-Key`, so a network-timeout retry could deposit the same money twice — the daily/monthly caps only caught this by accident, if the retried amount happened to push a running total over a threshold |

Errors (all 11 real `@ExceptionHandler`s): `404 ACCOUNT_NOT_FOUND`,
`400 INVALID_AMOUNT`, `422 YOUTH_ACCOUNT_BALANCE_CAP_EXCEEDED`,
`422 YOUTH_ACCOUNT_DAILY_LIMIT_EXCEEDED`, `422 YOUTH_ACCOUNT_MONTHLY_LIMIT_EXCEEDED`,
`422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`,
`422 YOUTH_ACCOUNT_BIRTH_DATE_REQUIRED`, `422 YOUTH_ACCOUNT_AGE_INELIGIBLE`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`.

## Foreign Currency — `/api/v1/account/foreign-currency`

**Added 2026-09-05.** Real 토스뱅크 외화통장 (foreign-currency account) equivalent —
see `ForeignCurrencyAccountService`'s own doc comment. Scoped to USD/EUR/GBP. A
conversion moves real money entirely between the caller's OWN RWF and foreign-
currency accounts at a real live mid-market rate plus a transparent 1.5% margin —
not a cross-border receiving/SWIFT rail.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/accounts` | `{currency}` (+ `Idempotency-Key`) | `201 {success, account: {...}}` | |
| GET | `/accounts` | — | `{success, accounts: [...]}` | |
| GET | `/rate?from&to` | — | `{success, from, to, rate}` | Real live mid-market rate, before itunda's own margin |
| POST | `/convert` | `{fromCurrency, toCurrency, amount}` (+ `Idempotency-Key`) | `201 {success, conversion: {...}}` | |
| GET | `/conversions` | — (paginated) | `{success, conversions: [...], ...page meta}` | |
| POST | `/rate-alert` | `{fromCurrency, toCurrency, targetRate, direction}` | `{success, alert: {...}}` | `direction` is `ABOVE`/`BELOW` |
| DELETE | `/rate-alert?fromCurrency&toCurrency` | — | `{success}` | |
| GET | `/rate-alerts` | — | `{success, alerts: [...]}` | |

Errors: `400 UNSUPPORTED_CURRENCY`, `409 FOREIGN_ACCOUNT_ALREADY_EXISTS`,
`404 FOREIGN_ACCOUNT_NOT_FOUND`, `400 INVALID_CONVERSION`,
`503 EXCHANGE_RATE_UNAVAILABLE`, `400 INVALID_RATE_ALERT`,
`404 RATE_ALERT_NOT_FOUND`, `404 ACCOUNT_NOT_FOUND`, `422 INSUFFICIENT_FUNDS`,
`403 ACCOUNT_FROZEN`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`.

## Debit Card — `/api/v1/card`

**Added 2026-09-05.** Real itunda debit-card issuance and management.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/issue` | `{design?}` (+ `Idempotency-Key`) | `201 {success, card: {...}, cardId}` | `design` optional, defaults to `DebitCardDesign.DEFAULT` |
| GET | `/my-card` | — | `{success, card: {...}}` | |
| GET | `/transactions?page&size` | — | `{success, transactions: [...], totalElements, totalPages}` | `size` capped to 100 |
| PUT | `/limits` | `{dailyLimit, monthlyLimit}` | `{success, card: {...}}` | |
| POST | `/freeze` | — | `{success, card: {...}}` | |
| POST | `/unfreeze` | — | `{success, card: {...}}` | |
| POST | `/report-lost` | — | `{success, card: {...}}` | |
| POST | `/close` | — | `{success, card: {...}}` | |
| POST | `/reissue` | — | `{success, card: {...}}` | |
| PUT | `/pin` | `{newPin, currentCredential}` | `{success, card: {...}}` | |
| POST | `/charge` | `{amount, merchantName}` (+ `Idempotency-Key`) | `201 {success, transaction: {...}, card: {...}}` | |

Errors: `409 CARD_ALREADY_ISSUED`, `404 CARD_NOT_FOUND`, `404 CARD_NO_ACCOUNT`,
`409 CARD_FROZEN`, `400 INVALID_CARD_LIMIT`, `400 INVALID_CARD_DESIGN`,
`409 CARD_LOST`, `409 CARD_CLOSED`, `409 CARD_NOT_ELIGIBLE_FOR_REISSUE`,
`400 INVALID_CARD_PIN`, `403 INCORRECT_CREDENTIAL`, `400 INVALID_AMOUNT`,
`409 CARD_DAILY_LIMIT_EXCEEDED`, `409 CARD_MONTHLY_LIMIT_EXCEEDED`,
`409 ACCOUNT_FROZEN`, `409 INSUFFICIENT_FUNDS`, `429 RATE_LIMITED`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`. **Real, worth-knowing inconsistency**:
`ACCOUNT_FROZEN`/`INSUFFICIENT_FUNDS` are both status 409 here specifically —
every other controller in this API gives them their own distinct statuses instead
(forbidden/unprocessable respectively — see the Merchant/Loans/VUP/etc. sections
above). Not yet unified.

## Bills — `/api/v1/bills`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/providers` | — | `{success, providers: [...]}` | |
| GET | `/pending` | — | `{success, bills: [...]}` | |
| POST | `/pay` | `{billId, amount, accountNumber?, provider?}` (+ `Idempotency-Key`) | `{success, message, transaction}` | **Real 30/hour per-user rate limit and `amount > 0` validation added 2026-09-04** |
| POST | `/airtime` | `{phoneNumber, amount, provider?}` (+ `Idempotency-Key`) | `{success, message, transaction}` | Same rate limit and validation as `/pay` |

Errors: `404 BILL_PROVIDER_NOT_FOUND`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`404 ACCOUNT_NOT_FOUND`, `422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`,
`502 PROVIDER_DECLINED`, `400 INVALID_REQUEST`, `429 RATE_LIMITED`. **Corrected
2026-09-04** — the real code is `ACCOUNT_NOT_FOUND`, not `WALLET_NOT_FOUND` (which
doesn't exist); `BILL_PROVIDER_NOT_FOUND`/`ACCOUNT_FROZEN`/`INVALID_REQUEST`/
`RATE_LIMITED` were missing entirely.

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
`404 ACCOUNT_NOT_FOUND`, `403 ACCOUNT_FROZEN`, `409 LOAN_ALREADY_PAID`,
`422 INVALID_LOAN_AMOUNT`, `422 LOAN_APPLICATION_DECLINED`, `422 INSUFFICIENT_FUNDS`.
**Corrected 2026-09-04** — real code is `ACCOUNT_NOT_FOUND`, not `WALLET_NOT_FOUND`
(doesn't exist); `LOAN_NOT_OWNED` also doesn't exist — `LoansService.kt` throws the
same `LoanNotFoundException` (→ `LOAN_NOT_FOUND`) whether the loan is missing or
owned by someone else, the same IDOR-safe folding used elsewhere in this API;
`ACCOUNT_FROZEN` was missing from this list entirely. **Still incomplete** —
`LoansController.kt` also has `/lenders`, `/refinance`, `/overdraft/open`,
`/overdraft/draw`, `/overdraft/repay`, `/postpaid-credit/apply`,
`/postpaid-credit/spend`, `/postpaid-credit/repay`,
`/postpaid-credit/process-payment-reminders` — found while documenting the 5 loan-
family sibling controllers below, not yet added to this table, a real, separate,
still-open gap on this same base controller.

## VUP Micro-loan — `/api/v1/loans/vup`

**Added 2026-09-05.** Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial
Services means-tested micro-loan, targeted at lower Ubudehe-category households — see
`VupLoanService`'s own doc comment for the full sourced account.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/apply` | `{declaredUbudeheCategory, purpose, amount}` (+ `Idempotency-Key`) | `201 {success, loan: {...}}` | `purpose` is one of `FARMING`/`LIVESTOCK`/`BUSINESS`. `declaredUbudeheCategory` is self-declared, not verified against a real government registry |
| POST | `/{loanId}/disburse` | — (+ `Idempotency-Key`) | `{success, loan: {...}}` | |
| POST | `/{loanId}/repay` | `{amount}` (+ `Idempotency-Key`) | `{success, loan: {...}}` | |
| GET | `/my` | — | `{success, loans: [...]}` | |
| GET | `/eligibility` | — | `{success, hasActiveLoan, canApply, minUbudeheCategory, maxUbudeheCategory, interestRate, maxAmount}` | |
| GET | `/{loanId}` | — | `{success, loan: {...}}` | |

Errors: `400 INELIGIBLE_UBUDEHE_CATEGORY`, `400 INVALID_VUP_LOAN_AMOUNT`,
`400 INVALID_REPAY_AMOUNT`, `409 VUP_LOAN_ALREADY_ACTIVE`, `404 VUP_LOAN_NOT_FOUND`,
`404 ACCOUNT_NOT_FOUND`, `409 VUP_LOAN_NOT_REQUESTED`, `409 VUP_LOAN_NOT_REPAYABLE`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`.

## Student Loan — `/api/v1/loans/student`

**Added 2026-09-05.** Real Rwanda BRD (Development Bank of Rwanda) higher-education
student loan — see `StudentLoanService`'s own doc comment for the full sourced account.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/apply` | `{level, declaredAnnualHouseholdIncome, amount, expectedGraduationDate}` (+ `Idempotency-Key`) | `201 {success, loan: {...}}` | `level` is `UNDERGRADUATE`/`POSTGRADUATE` |
| POST | `/{loanId}/disburse` | — (+ `Idempotency-Key`) | `{success, loan: {...}}` | |
| POST | `/{loanId}/declare-graduated` | — (+ `Idempotency-Key`) | `{success, loan: {...}}` | Starts the real 6-month grace period, moves status to `IN_GRACE_PERIOD` |
| POST | `/{loanId}/repay` | `{amount}` (+ `Idempotency-Key`) | `{success, loan: {...}}` | |
| GET | `/my` | — | `{success, loans: [...]}` | |
| GET | `/{loanId}` | — | `{success, loan: {...}}` | |
| GET | `/{loanId}/suggested-payment` | — | `{success, loanId, outstandingBalance, suggestedMonthlyPayment, note}` | A suggestion only — itunda has no payroll/RRA-integration path to enforce the real 8%-of-income deduction BRD itself uses |
| POST | `/process-grace-end-reminders` | — | `{success, processed}` | **ADMIN role only** — manually fires the grace-period-ending-soon reminder job for every user's due loans system-wide |

Errors: `400 INVALID_GRADUATION_DATE`, `400 INVALID_STUDENT_LOAN_AMOUNT`,
`409 STUDENT_LOAN_ALREADY_ACTIVE`, `404 STUDENT_LOAN_NOT_FOUND`,
`404 ACCOUNT_NOT_FOUND`, `409 STUDENT_LOAN_NOT_REQUESTED`,
`409 STUDENT_LOAN_NOT_DISBURSED`, `409 STUDENT_LOAN_NOT_REPAYABLE`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`.

## Vendor Cash Advance — `/api/v1/vendor-advance`

**Added 2026-09-05.** Real Isoko ("market" in Kinyarwanda) vendor cash advance — a
merchant-facing product, not a consumer loan — see `VendorCashAdvanceService`'s own doc
comment for the full sourced account.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/offer?merchantId` | — | `{success, ...offer fields}` | |
| POST | `/apply` | `{merchantId}` (+ `Idempotency-Key`) | `201 {success, advance: {...}}` | |
| POST | `/{advanceId}/disburse` | — (+ `Idempotency-Key`) | `{success, advance: {...}}` | |
| GET | `/me?merchantId` | — | `{success, advance: {...} \| null}` | |
| GET | `/{advanceId}` | — | `{success, advance: {...}}` | |
| GET | `/{advanceId}/collection-history` | — | `{success, ...}` | Real automatic per-sale collection against this advance — see `VendorCashAdvanceCollectionScheduler` |
| POST | `/{advanceId}/repay-early` | `{amount}` (+ `Idempotency-Key`) | `{success, advance: {...}}` | |

Errors: `404 VENDOR_CASH_ADVANCE_NOT_FOUND`, `404 ACCOUNT_NOT_FOUND`,
`409 VENDOR_CASH_ADVANCE_NOT_REQUESTED`, `409 VENDOR_CASH_ADVANCE_NOT_REPAYABLE`,
`409 VENDOR_CASH_ADVANCE_ALREADY_ACTIVE`, `400 VENDOR_CASH_ADVANCE_NOT_ELIGIBLE`,
`400 INVALID_REPAY_AMOUNT`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`.

## Cooperatives (Harvest Advance) — `/api/v1/cooperatives`

**Added 2026-09-05.** Real Rwanda coffee-cooperative harvest-advance / input financing
— see `CooperativeService`'s own doc comment for the full sourced account.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{name, cropType, registrationNumber?}` | `201 {success, cooperative: {...}}` | No `@AuthenticationPrincipal` on this endpoint — registering a cooperative itself isn't user-scoped, only joining one is |
| POST | `/{cooperativeId}/join` | — | `201 {success, membership: {...}}` | |
| GET | `/my-memberships` | — | `{success, memberships: [...]}` | |
| GET | `/{cooperativeId}/overview` | — | `{success, ...}` | Real `403`-via-`NotMemberException` gate — a non-member can't see it |
| POST | `/advances` | `{membershipId, principalAmount, purpose, expectedHarvestDate}` (+ `Idempotency-Key`) | `201 {success, advance: {...}}` | |
| POST | `/advances/{advanceId}/disburse` | — (+ `Idempotency-Key`) | `{success, advance: {...}}` | |
| POST | `/advances/{advanceId}/repay` | `{amount}` (+ `Idempotency-Key`) | `{success, advance: {...}}` | Repayment `amount` must exactly equal the advance's own `principalAmount` — no free-form partial repayment (a real bug caught and fixed before this feature shipped: a token repayment used to silently forgive the rest of the debt) |
| GET | `/advances/my-advances` | — | `{success, advances: [...]}` | |

Errors: `404 COOPERATIVE_NOT_FOUND`, `400 INVALID_COOPERATIVE_NAME`,
`409 ALREADY_MEMBER`, `404 NOT_MEMBER`, `404 ACCOUNT_NOT_FOUND`,
`400 INVALID_AMOUNT`, `404 HARVEST_ADVANCE_NOT_FOUND`, `409 INVALID_ADVANCE_STATUS`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`.

## Moto-Taxi Ownership — `/api/v1/moto-ownership`

**Added 2026-09-05.** Real Rwanda moto-taxi ownership savings-to-loan plan (save toward
a down payment, then convert the remainder into a real loan) — see
`MotoOwnershipService`'s own doc comment for the full sourced account. Lives in the
`rideshare` module, not `loans`, despite the loan-family shape.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/plans` | `{bikePrice, dailyContribution}` (+ `Idempotency-Key`) | `201 {success, plan: {...}}` | |
| POST | `/plans/{planId}/contribute` | `{amount}` (+ `Idempotency-Key`) | `{success, plan: {...}}` | |
| POST | `/plans/{planId}/cancel` | — (+ `Idempotency-Key`) | `{success, plan: {...}}` | |
| POST | `/plans/{planId}/convert-to-loan` | — (+ `Idempotency-Key`) | `{success, plan: {...}}` | Only once the real down-payment target has been met — see `DOWN_PAYMENT_NOT_MET` below |
| POST | `/plans/{planId}/repay` | `{amount}` (+ `Idempotency-Key`) | `{success, plan: {...}}` | |
| GET | `/plans/me` | — | `{success, plans: [...]}` | |
| GET | `/plans/{planId}` | — | `{success, plan: {...}}` | |

Errors: `404 MOTO_OWNERSHIP_PLAN_NOT_FOUND`, `404 ACCOUNT_NOT_FOUND`,
`400 INVALID_BIKE_PRICE`, `400 INVALID_DAILY_CONTRIBUTION`, `400 INVALID_AMOUNT`,
`409 MOTO_OWNERSHIP_PLAN_ALREADY_ACTIVE`, `409 MOTO_OWNERSHIP_PLAN_NOT_SAVING`,
`409 MOTO_OWNERSHIP_PLAN_NOT_CANCELLABLE`, `409 MOTO_OWNERSHIP_PLAN_NOT_REPAYABLE`,
`409 DOWN_PAYMENT_NOT_MET`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`, `429 RATE_LIMITED`.

## Rides — `/api/v1/rides`

**Added 2026-09-05** (tenth documentation slice — itunda's core Kakao T-style
ride-hailing product, the single highest-value gap this standing follow-up has found:
previously undocumented, hidden from every prior remaining-controller count by a
prefix-matching bug in the count script itself, see `project_itunda_market_readiness`
memory for the full account). Confirmed by direct read of `RideController.kt` (26
endpoints, 33 `@ExceptionHandler`s, 32 unique codes — `InvalidRideDriverLocationException`
and `InvalidRideLocationException` both map to `INVALID_LOCATION`).

### Drivers

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/drivers/register` | `{licenseNumber}` | `201 {success, driver: {...}}` | |
| GET | `/drivers/me` | — | `{success, driver: {...}}` | |
| POST | `/drivers/availability` | `{available}` | `{success, driver: {...}}` | |
| POST | `/drivers/location` | `{latitude, longitude}` | `{success, driver: {...}}` | |
| POST | `/drivers/destination` | `{latitude, longitude}` | `{success, driver: {...}}` | Real Uber "Destination Filter" — biases dispatch toward trips heading the driver's own way |
| POST | `/drivers/destination/clear` | — | `{success, driver: {...}}` | |
| GET | `/drivers/{driverId}/reviews` | — | `{success, reviews: [...], ...pageMeta}` | |
| GET | `/drivers/{driverId}/rating` | — | `{success, average, count}` | |

### Trip lifecycle

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/trips/estimate?pickupLatitude&pickupLongitude&dropoffLatitude&dropoffLongitude` | — | `{success, estimatedFare}` | Real Uber "Upfront Fare" preview — pure computation, no side effects, no `Idempotency-Key`. Doesn't account for stops; the actual charged fare on `requestTrip` always uses full stop data regardless |
| POST | `/trips` | `{pickupAddress, pickupLatitude, pickupLongitude, dropoffAddress, dropoffLatitude, dropoffLongitude, scheduledFor?, stops?}` (+ `Idempotency-Key`) | `201 {success, trip: {...}, stops: [...]}` | `scheduledFor` (null = ASAP) is real Kakao T 예약 호출 (scheduled ride booking); `stops` (empty = direct) is real Kakao T-style multi-stop |
| GET | `/trips/available` | — | `{success, trips: [...]}` | Driver-side, nearest/matching offers |
| GET | `/trips/my-trips` | — | `{success, trips: [...], ...pageMeta}` | Passenger's own trips |
| GET | `/trips/my-driver-trips` | — | `{success, trips: [...], ...pageMeta}` | Driver's own trips |
| GET | `/trips/my-earnings?from&to` | — | `{success, from, to, days: [...]}` | Real Uber Driver app-style earnings report. Defaults to the last 7 days when `from`/`to` are omitted |
| POST | `/trips/{tripId}/accept` | — | `{success, trip: {...}}` | Driver accepts an offered trip |
| POST | `/trips/{tripId}/decline` | — | `{success, trip: {...}}` | |
| POST | `/trips/{tripId}/start` | `{pin}` | `{success, trip: {...}}` | Real Uber "Verify Your Ride" PIN — driver enters the PIN the passenger tells them verbally at pickup |
| GET | `/trips/{tripId}/pin` | — | `{success, pin}` | Passenger-only — the driver never sees this through any other endpoint |
| POST | `/trips/{tripId}/share` | `{conversationId}` | `201 {success, message: {...}}` | Real Uber "Share Trip Status" — posts a live-status message into an existing chat conversation |
| POST | `/trips/{tripId}/send-status` | — | `{success, sentCount}` | Real Uber "Send Status" — fans the trip's live status out to every one of the caller's Trusted Contacts (see the Ride Trusted Contacts section below) in one tap |
| GET | `/trips/{tripId}/stops` | — | `{success, stops: [...]}` | Real Kakao T-style multi-stop rides |
| POST | `/trips/{tripId}/stops/arrive` | — | `{success, stop: {...}}` | Marks the next unvisited stop arrived |
| POST | `/trips/{tripId}/complete` | — | `{success, trip: {...}}` | |
| POST | `/trips/{tripId}/tip` | `{amount}` (+ `Idempotency-Key`) | `{success, trip: {...}}` | Real Uber post-trip tipping — a real account-to-account transfer, never safe to silently retry |
| POST | `/trips/{tripId}/cancel` | — | `{success, trip: {...}}` | |
| POST | `/trips/{tripId}/review` | `{rating, comment?}` | `201 {success, review: {...}}` | Real Kakao T-style post-trip driver rating |

### Errors (complete — all 33 `@ExceptionHandler`s in `RideController.kt`, 32 unique codes)

`409 RIDE_DRIVER_ALREADY_REGISTERED`, `404 ACCOUNT_NOT_FOUND`,
`404 RIDE_DRIVER_NOT_REGISTERED`, `400 INVALID_LOCATION` (shared by the driver-location
and trip-location validators), `400 INVALID_LICENSE_NUMBER`,
`429 DESTINATION_FILTER_LIMIT_EXCEEDED`, `400 SELF_TRIP_NOT_ALLOWED`,
`400 INVALID_SCHEDULED_TIME`, `400 INVALID_RATING`, `409 RIDE_TRIP_NOT_YET_COMPLETED`,
`409 RIDE_TRIP_ALREADY_REVIEWED`, `400 TOO_MANY_STOPS`, `409 NO_REMAINING_STOPS`,
`404 RIDE_TRIP_NOT_FOUND`, `404 CONVERSATION_NOT_FOUND`,
`409 RIDE_TRIP_ALREADY_CLAIMED`, `409 RIDE_DRIVER_ALREADY_ON_TRIP`,
`409 RIDE_DRIVER_NOT_AVAILABLE`, `409 INVALID_RIDE_STATUS_TRANSITION`,
`400 INCORRECT_RIDE_PIN`, `409 NO_ACTIVE_OFFER`, `400 INVALID_EARNINGS_RANGE`,
`409 RIDE_TRIP_NOT_COMPLETED`, `409 RIDE_TRIP_ALREADY_TIPPED`,
`400 RIDE_TRIP_TIP_WINDOW_EXPIRED`, `400 INVALID_TIP_AMOUNT`,
`400 INVALID_DATE_FORMAT` (`my-earnings` `from`/`to` not `YYYY-MM-DD`),
`422 INSUFFICIENT_FUNDS`, `429 RATE_LIMITED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`.

## Bike Share — `/api/v1/bikeshare`

**Added 2026-09-05** (fourteenth documentation slice — the rest of itunda's Kakao T
micro-mobility family, sibling products to Rides above). Real Kakao T 바이크 (Kakao T
Bike) — a peer-registered bike (not itunda-owned fleet), rented by the trip.
Confirmed by direct read of `BikeRentalController.kt` (9 endpoints, 9 error codes,
all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/bikes` | `{type, latitude, longitude}` | `201 {success, bike: {...}}` | |
| GET | `/bikes/mine` | — | `{success, bikes: [...]}` | |
| POST | `/bikes/{bikeId}/availability` | `{available}` | `{success, bike: {...}}` | |
| POST | `/bikes/{bikeId}/location` | `{latitude, longitude}` | `{success, bike: {...}}` | |
| GET | `/bikes/nearby?latitude&longitude&radiusKm` | — | `{success, bikes: [...]}` | `radiusKm` defaults to 5.0 |
| POST | `/rentals` | `{bikeId, startLatitude, startLongitude}` | `201 {success, rental: {...}}` | |
| POST | `/rentals/{sessionId}/end` | `{endLatitude, endLongitude}` | `{success, rental: {...}}` | |
| POST | `/rentals/process-abandoned` | — | `{success, processedCount, rentals: [...]}` | ADMIN only — manual trigger for the abandoned-rental scheduler; force-settles real other users' money |
| GET | `/rentals/my-history` | — | `{success, rentals: [...], ...pageMeta}` | |

Errors (all 9 real `@ExceptionHandler`s): `404 BIKE_NOT_FOUND`,
`409 BIKE_NOT_AVAILABLE`, `400 SELF_RENTAL_NOT_ALLOWED`, `404 ACCOUNT_NOT_FOUND`,
`400 INVALID_LOCATION`, `404 BIKE_RENTAL_NOT_FOUND`, `409 BIKE_RENTAL_ALREADY_ENDED`,
`422 INSUFFICIENT_FUNDS`, `429 RATE_LIMITED`.

## Bus — `/api/v1/bus`

**Added 2026-09-05, same slice.** Real Kakao T 시외버스 (intercity bus booking) — a
driver posts a trip with a fixed seat count, riders book seats. Confirmed by direct
read of `BusController.kt` (7 endpoints, 12 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/trips` | `{origin, destination, departureTime, totalSeats, farePerSeat}` | `201 {success, trip: {...}}` | |
| GET | `/trips/mine` | — | `{success, trips: [...]}` | Driver's own posted trips |
| GET | `/trips/{tripId}/bookings` | — | `{success, bookings: [...]}` | Driver-only |
| GET | `/trips/search?origin&destination` | — | `{success, trips: [...]}` | Both params optional |
| POST | `/bookings` | `{tripId, seatCount}` (+ `Idempotency-Key`) | `201 {success, booking: {...}}` | Real bug fixed 2026-08-02: this had no `Idempotency-Key` despite creating a real booking row and decrementing a real `@Version`-guarded seat count on every call — `@Version` alone only protects against two DIFFERENT concurrent requests, not one rider's own sequential retry, which could have booked the same trip twice |
| POST | `/bookings/{bookingId}/cancel` | — | `{success, booking: {...}}` | |
| GET | `/bookings/my-history` | — | `{success, bookings: [...], ...pageMeta}` | |

Errors (all 12 real `@ExceptionHandler`s): `404 BUS_TRIP_NOT_FOUND`,
`400 INVALID_BUS_TRIP`, `404 ACCOUNT_NOT_FOUND`, `409 INSUFFICIENT_SEATS`,
`404 BUS_BOOKING_NOT_FOUND`, `409 BUS_BOOKING_ALREADY_CANCELLED`,
`409 BUS_TRIP_ALREADY_DEPARTED`, `422 INSUFFICIENT_FUNDS`, `429 RATE_LIMITED`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`.

## Parking — `/api/v1/parking`

**Added 2026-09-05, same slice.** Real Kakao T 주차 (Kakao T Parking) — a
peer-registered spot, rented by the session. Confirmed by direct read of
`ParkingController.kt` (8 endpoints, 9 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/spots` | `{address, latitude, longitude, hourlyRate}` | `201 {success, spot: {...}}` | |
| GET | `/spots/mine` | — | `{success, spots: [...]}` | |
| POST | `/spots/{spotId}/availability` | `{available}` | `{success, spot: {...}}` | |
| GET | `/spots/nearby?latitude&longitude&radiusKm` | — | `{success, spots: [...]}` | `radiusKm` defaults to 5.0 |
| POST | `/sessions` | `{spotId}` | `201 {success, session: {...}}` | |
| POST | `/sessions/{sessionId}/end` | — | `{success, session: {...}}` | |
| POST | `/sessions/process-abandoned` | — | `{success, processedCount, sessions: [...]}` | ADMIN only — manual trigger for the abandoned-session scheduler; force-settles real other users' money |
| GET | `/sessions/my-history` | — | `{success, sessions: [...], ...pageMeta}` | |

Errors (all 9 real `@ExceptionHandler`s): `404 PARKING_SPOT_NOT_FOUND`,
`409 PARKING_SPOT_NOT_AVAILABLE`, `400 SELF_RENTAL_NOT_ALLOWED`,
`404 ACCOUNT_NOT_FOUND`, `400 INVALID_LOCATION`, `404 PARKING_SESSION_NOT_FOUND`,
`409 PARKING_SESSION_ALREADY_ENDED`, `422 INSUFFICIENT_FUNDS`, `429 RATE_LIMITED`.

## Moto Fare — `/api/v1/moto-fare`

**Added 2026-09-05, same slice.** Real "tap to pay your moto-taxi fare" — a moto
driver collects a fare by scanning the same real customer-payment-code any itunda
user's "My payment code" screen already shows (the identical code
`POST /api/v1/merchant/pay/customer-code` generates — no separate moto-specific code
type). Confirmed by direct read of `MotoFareController.kt` (3 endpoints, 11 error
codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/trips?page&size` | — | `{success, trips: [...], totalElements, totalPages}` | Rider's own trips. `size` capped at 100 |
| GET | `/earnings?page&size` | — | `{success, trips: [...], totalElements, totalPages}` | Driver's own collected fares |
| POST | `/collect` | `{code, fare}` (+ `Idempotency-Key`) | `201 {success, collected: {...}}` | Called by the driver after scanning the rider's code |

Errors (all 11 real `@ExceptionHandler`s): `404 MOTO_FARE_CODE_NOT_FOUND`,
`409 MOTO_FARE_CODE_NOT_PAYABLE`, `400 MOTO_FARE_SELF_COLLECTION`,
`400 INVALID_MOTO_FARE`, `404 MOTO_FARE_NO_ACCOUNT`, `409 ACCOUNT_FROZEN` (a real,
minor inconsistency — every other controller on this page maps this same code to
`403`, not `409`), `409 INSUFFICIENT_FUNDS` (same inconsistency — elsewhere on this
page this is `422`), `429 RATE_LIMITED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`.

## Transit — `/api/v1/transit`

**Added 2026-09-05, same slice.** Real Kigali GTFS-transit fare card — top up a
balance, tap to pay a fare (or have an agent collect it via the rider's own
customer-payment code, the same real primitive `POST /api/v1/moto-fare/collect`
reuses). Confirmed by direct read of `TransitController.kt` (5 endpoints, 14 error
codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/balance` | — | `{success, balance}` | |
| GET | `/trips?page&size` | — | `{success, trips: [...], totalElements, totalPages}` | `size` capped at 100 |
| POST | `/topup` | `{amount}` (+ `Idempotency-Key`) | `{success, balance}` | |
| POST | `/tap` | `{operator, fare}` (+ `Idempotency-Key`) | `201 {success, trip: {...}, balance}` | |
| POST | `/tap-by-code` | `{code, operator, fare}` (+ `Idempotency-Key`) | `201 {success, collected: {...}}` | Real "agent collects a fare via a rider's presented code" flow — `code` is the same `CustomerPaymentCode` any user's "My payment code" screen shows |

Errors (all 14 real `@ExceptionHandler`s): `404 TRANSIT_CODE_NOT_FOUND`,
`409 TRANSIT_CODE_NOT_PAYABLE`, `400 TRANSIT_SELF_COLLECTION`,
`404 TRANSIT_NO_ACCOUNT`, `400 INVALID_AMOUNT`, `400 INVALID_TRANSIT_OPERATOR`,
`400 INVALID_TRANSIT_FARE`, `409 TRANSIT_INSUFFICIENT_BALANCE`,
`409 ACCOUNT_FROZEN` (same real inconsistency as Moto Fare above — every other
controller on this page maps this code to `403`), `409 INSUFFICIENT_FUNDS` (same
inconsistency — elsewhere on this page this is `422`), `429 RATE_LIMITED`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`.

## Designated Driver — `/api/v1/designated-driver`

**Added 2026-09-05.** Real Kakao T 대리운전 (designated driver) — a driver comes to
where the customer's OWN car is parked and drives them + their car home, distinct
from `/api/v1/rides` (a driver's own vehicle). `/api/v1/rides` itself remains
undocumented on this page — a real, separate gap, not fixed this pass.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/drivers/register` | `{licenseNumber}` (+ `Idempotency-Key`) | `201 {success, driver: {...}}` | |
| GET | `/drivers/me` | — | `{success, driver: {...}}` | |
| POST | `/drivers/availability` | `{available}` | `{success, driver: {...}}` | |
| POST | `/drivers/location` | `{latitude, longitude}` | `{success, driver: {...}}` | |
| POST | `/trips` | `{pickupAddress, pickupLatitude, pickupLongitude, dropoffAddress, dropoffLatitude, dropoffLongitude, vehicleMake, vehicleModel, vehiclePlate}` (+ `Idempotency-Key`) | `201 {success, trip: {...}}` | Idempotency-Key required since 2026-08-02 — this creates a brand-new trip row with a real fare hold on every call and has no "customer already has an active trip" guard, so an unprotected retry would hold the fare twice |
| GET | `/trips/available` | — | `{success, trips: [...]}` | |
| GET | `/trips/my-trips` | — (paginated) | `{success, trips: [...], ...page meta}` | As the customer |
| GET | `/trips/my-driver-trips` | — (paginated) | `{success, trips: [...], ...page meta}` | As the driver |
| POST | `/trips/{tripId}/accept` | — | `{success, trip: {...}}` | |
| POST | `/trips/{tripId}/start-driving` | — | `{success, trip: {...}}` | |
| POST | `/trips/{tripId}/complete` | — | `{success, trip: {...}}` | |
| POST | `/trips/{tripId}/cancel` | — | `{success, trip: {...}}` | |

Errors: `409 DESIGNATED_DRIVER_ALREADY_REGISTERED`, `404 ACCOUNT_NOT_FOUND`,
`404 DESIGNATED_DRIVER_NOT_REGISTERED`, `400 INVALID_LOCATION`,
`400 SELF_TRIP_NOT_ALLOWED`, `404 DESIGNATED_DRIVER_TRIP_NOT_FOUND`,
`409 DESIGNATED_DRIVER_TRIP_ALREADY_CLAIMED`,
`409 INVALID_DESIGNATED_DRIVER_STATUS_TRANSITION`, `422 INSUFFICIENT_FUNDS`,
`429 RATE_LIMITED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`.

## Ride Trusted Contacts — `/api/v1/rides/trusted-contacts`

**Added 2026-09-05, same slice.** Real Uber Safety "Trusted Contacts" — a persistent
contact list set up once, distinct from the main `RideController`'s own per-trip
`POST /trips/{tripId}/send-status` (see the Rides section above). Extracted into its
own controller once `RideController.kt` first crossed the file-size-lint guideline.
Confirmed by direct read of `RideTrustedContactController.kt` (3 endpoints, 5 error
codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` (base path) | — | `{success, contacts: [...]}` | |
| POST | `` (base path) | `{phoneNumber, name}` | `201 {success, contact: {...}}` | Capped at a small fixed maximum (see `TOO_MANY_TRUSTED_CONTACTS` below) |
| DELETE | `/{contactId}` | — | `{success}` | |

Errors (all 5 real `@ExceptionHandler`s): `404 TRUSTED_CONTACT_NOT_FOUND`,
`404 TRUSTED_CONTACT_RECIPIENT_NOT_FOUND`, `400 CANNOT_ADD_SELF_AS_TRUSTED_CONTACT`,
`409 TRUSTED_CONTACT_ALREADY_ADDED`, `409 TOO_MANY_TRUSTED_CONTACTS`.

## Marketplace — `/api/v1/marketplace`

**Added 2026-09-05** (eleventh documentation slice). Real 당근마켓-style used-goods
marketplace — listings (including 당근카/Karrot Vehicles lease-takeover fields),
당근 price-offer negotiation posted inline into the buyer-seller chat, 끌어올리기
(free self-bump), seller-paid sponsored boost, "pay via itunda" escrow, and
post-transaction reviews. The parent controller for the already-documented Vehicle
Inspections and Keyword Alerts sub-resources below. Confirmed by direct read of
`MarketplaceController.kt` (31 endpoints, 32 `@ExceptionHandler`s, 28 unique codes —
`LISTING_NOT_FOUND` is shared by 4 exceptions, `ACCOUNT_NOT_FOUND` by 2).

### Listings

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/listings` | `{title, description, price, category, latitude?, longitude?, meetingPlace?, photoUrl?, vehicleMileageKm?, vehicleInsuranceClaimCount?, vehicleIsLeaseTakeover?, leaseTotalAcquisitionCost?, leaseRemainingMonths?, leaseTotalMonths?, leaseMonthlyPayment?, leaseSubsidyAmount?, leaseReturnFee?}` | `201 {success, listing: {...}}` | The `lease*`/`vehicle*` fields are real 당근카 (Karrot Vehicles) lease-takeover data, all optional. Triggers real Keyword Alert matching (see the Keyword Alerts section below) |
| GET | `/categories` | — | `{success, categories: [...]}` | |
| GET | `/listings?category` | — | `{success, listings: [...], trustScores, ...pageMeta}` | Every real browse call is JWT-authenticated (no guest-browse path here, unlike some other browse endpoints in this backend), so hidden listings (see `/hide` below) are already excluded |
| GET | `/listings/nearby?latitude&longitude&radiusKm` | — | `{success, listings: [...], trustScores, ...pageMeta}` | `radiusKm` defaults to 5.0 |
| GET | `/listings/my-neighborhood?category` | — | `{success, listings: [...], trustScores, likedByMe, ...pageMeta}` | |
| GET | `/listings/search?q` | — | `{success, listings: [...], trustScores, ...pageMeta}` | |
| GET | `/listings/{listingId}` | — | `{success, listing: {...}, sellerTrustScore}` | |
| GET | `/my-listings` | — | `{success, listings: [...], trustScores, likedByMe, ...pageMeta}` | |
| GET | `/my-purchases` | — | `{success, listings: [...], trustScores, likedByMe, ...pageMeta}` | |
| DELETE | `/listings/{listingId}` | — | `{success, listing: {...}}` | |
| POST | `/listings/{listingId}/like` | — | `{success, liked}` | Idempotent toggle |
| POST | `/listings/{listingId}/hide` | — | `201 {success, hide: {...}}` | Real Karrot "이 글 숨기기" (hide this post) — removes it from the caller's own browse results only |
| DELETE | `/listings/{listingId}/hide` | — | `{success}` | |
| POST | `/listings/{listingId}/bump` | — | `{success, listing: {...}}` | Real 당근마켓 끌어올리기 (bump to top of feed) — free, self-serve, no `Idempotency-Key` |
| PATCH | `/listings/{listingId}/price` | `{price}` | `{success, listing: {...}}` | Real 가격 수정 (price edit) — also triggers a real Karrot 가격 하락 알림 (price-drop notification) to interested buyers when the price drops |
| POST | `/listings/{listingId}/mark-sold` | `{buyerPhoneNumber?}` | `{success, listing: {...}}` | |

### Boost & escrow (real money movement)

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/listings/{listingId}/boost` | `{days}` (+ `Idempotency-Key`) | `{success, listing: {...}}` | Seller-paid sponsored placement — the first money-moving endpoint in this controller, so the first to require `Idempotency-Key` |
| GET | `/boost-tiers` | — | `{success, tiers: {...}}` | |
| POST | `/listings/{listingId}/pay-escrow` | `{deliveryAddress?}` (+ `Idempotency-Key`) | `201 {success, escrow: {...}}` | Real "pay via itunda" escrow. `deliveryAddress` is optional — omit it for the original in-person handoff flow |
| POST | `/listings/{listingId}/confirm-receipt` | — (+ `Idempotency-Key`) | `{success, escrow: {...}}` | Releases escrowed funds to the seller |
| POST | `/listings/{listingId}/dispute-escrow` | `{reason}` | `{success, escrow: {...}}` | Not `Idempotency-Key`-protected — opens a dispute rather than moving money itself |
| GET | `/listings/{listingId}/escrow` | — | `{success, escrow: {...}}` | |

### Favorites, contact, offers, reviews

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/listings/{listingId}/favorite` | — | `201 {success, favorite: {...}}` | |
| DELETE | `/listings/{listingId}/favorite` | — | `{success}` | |
| GET | `/listings/favorites` | — | `{success, favorites: [...], ...pageMeta}` | |
| POST | `/listings/{listingId}/contact-seller` | — | `{success, conversation: {...}}` | |
| POST | `/listings/{listingId}/offers` | `{amount}` | `201 {success, offer: {...}}` | Real 당근-style price-offer negotiation — posted as a real message in the buyer-seller conversation `contact-seller` establishes |
| POST | `/offers/{offerId}/respond` | `{action, counterAmount?}` | `{success, offer: {...}}` | `action` is accept/reject/counter |
| GET | `/conversations/{conversationId}/offers` | — | `{success, offers: [...]}` | Per-thread offer history, so a client can render offer bubbles inline in the conversation it already fetches from Messaging |
| POST | `/listings/{listingId}/review` | `{goodPoints?, uncomfortablePoints?}` | `201 {success, review: {...}}` | Shared `HoodReviewService` — asymmetric public/private visibility, same as Jobs' own reviews |
| GET | `/listings/{listingId}/review` | — | `{success, reviews: [...]}` | |

Reporting a listing moved to the unified `POST /api/v1/hood/reports`
(`HoodReportController`, not yet documented on this page) — this controller
deliberately has no separate report endpoint of its own, same retirement `Community`'s
own section above documents for the identical reason.

### Errors (complete — all 32 `@ExceptionHandler`s in `MarketplaceController.kt`, 28 unique codes)

`404 OFFER_NOT_FOUND`, `400 INVALID_OFFER_AMOUNT`, `409 OFFER_ALREADY_RESOLVED`,
`400 OWN_OFFER`, `404 LISTING_NOT_FOUND` (shared by 4 exceptions — the listing itself
not existing, a stale favorite/hide pointer, and a review-lookup miss),
`400 INVALID_LISTING`, `400 INVALID_LISTING_PRICE`, `409 LISTING_NOT_ACTIVE`,
`400 OWN_LISTING`, `429 BUMP_COOLDOWN`, `429 RATE_LIMITED`, `400 INVALID_COORDINATES`,
`400 NEIGHBORHOOD_NOT_SET`, `404 BUYER_NOT_FOUND`,
`409 REVIEW_TRANSACTION_NOT_COMPLETED`, `400 REVIEW_NO_COUNTERPARTY`,
`404 REVIEW_NOT_PARTY` (IDOR fix, 2026-08-30 — was `403`, same fix class as Jobs'
identical review-ownership check), `409 REVIEW_ALREADY_SUBMITTED`,
`400 INVALID_BOOST_DURATION`, `404 ACCOUNT_NOT_FOUND` (seller or buyer, shared),
`404 ESCROW_NOT_FOUND`, `409 INVALID_ESCROW_STATUS`, `400 INVALID_DISPUTE_REASON`,
`422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`.

## Uploads — `/api/v1` (`UploadController`)

**Added 2026-09-05, same slice.** Real, minimal photo upload — every "photo"/"image"
field elsewhere in this backend (`Merchant.photoUrl`, `MerchantProduct.imageUrl`,
`User.profilePhotoUrl`, Marketplace listing photos) was previously a paste-your-own-
externally-hosted-URL string with no actual storage layer; this closes that gap with
real local-disk storage on the single cluster node, served back out under this same
`/api/v1` path. Deliberately not S3/MinIO/a CDN — a plain validated-and-bounded local
directory is the honest, lean choice for a single-node deployment. Confirmed by
direct read of `UploadController.kt` (1 endpoint, 2 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/uploads` | multipart form field `file` | `201 {success, url}` | Max 5MB; JPEG/PNG/WebP only; rate-limited to 20 uploads/hour per user. `url` is a real `/api/v1/uploads/{filename}` path any other field can be set to |

Errors (both real `@ExceptionHandler`s): `400 INVALID_UPLOAD` (empty file, over the
size limit, or an unsupported content type), `429 RATE_LIMITED`. **Real gap found and
fixed 2026-09-05** (this documentation pass): the rate-limit check above already
threw `RateLimitExceededException` on the 21st upload/hour, but this controller had
never registered a handler for it — an unhandled exception (a generic 500) instead
of this same clean 429 every other rate-limited endpoint in this backend returns.

## Keyword Alerts — `/api/v1/marketplace/keyword-alerts`

**Added 2026-09-05, same slice.** Real 당근마켓-style Keyword Alert — save a keyword,
get notified when a new listing matches it (triggered from `MarketplaceController
.createListing` above), with a real do-not-disturb quiet-hours window. Confirmed by
direct read of `KeywordAlertController.kt` (5 endpoints, 4 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{keyword}` | `201 {success, alert: {...}}` | |
| GET | `` (base path) | — | `{success, alerts: [...], ...pageMeta}` | |
| DELETE | `/{alertId}` | — | `{success}` | |
| POST | `/quiet-hours` | `{startTime, endTime, enabled?}` | `{success, quietHours: {...}}` | |
| GET | `/quiet-hours` | — | `{success, quietHours: {...}}` | |

Errors (all 4 real `@ExceptionHandler`s): `400 INVALID_QUIET_HOURS`,
`400 INVALID_KEYWORD`, `409 KEYWORD_ALERT_CAP_REACHED`, `404 KEYWORD_ALERT_NOT_FOUND`.

## Property Listings — `/api/v1/realestate`

**Added 2026-09-05** (twenty-second documentation slice). Real 당근부동산-style
property board (rent/sale) — browse, price-offer negotiation, ownership
verification, a real Toss Bank 우리집 시세 (home valuation) estimate computed live
from comparable listings, and asymmetric post-transaction reviews. Structurally
mirrors `MarketplaceController` closely (same trust-score enrichment, same
price-offer shape). Confirmed by direct read of `PropertyListingController.kt` (23
endpoints, 20 `@ExceptionHandler`s, 18 unique codes —
`PROPERTY_LISTING_NOT_FOUND` is shared by 3 exceptions).

### Listings

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/property-types` | — | `{success, propertyTypes: [...]}` | |
| POST | `/listings` | `{listingType, propertyType, title, description, price, bedrooms?, sizeSqm?, latitude?, longitude?}` | `201 {success, listing: {...}}` | `listingType` is rent or sale |
| GET | `/listings?listingType&propertyType` | — | `{success, listings: [...], trustScores, ...pageMeta}` | |
| GET | `/listings/nearby?latitude&longitude&radiusKm` | — | `{success, listings: [...], trustScores, ...pageMeta}` | `radiusKm` defaults to 5.0 |
| GET | `/listings/my-neighborhood` | — | `{success, listings: [...], trustScores, ...pageMeta}` | |
| GET | `/listings/search?q` | — | `{success, listings: [...], trustScores, ...pageMeta}` | |
| GET | `/my-listings` | — | `{success, listings: [...], trustScores, ...pageMeta}` | |
| GET | `/my-acquired-listings` | — | `{success, listings: [...], trustScores, ...pageMeta}` | Real "Places I got" history |
| GET | `/valuation?latitude&longitude&propertyType&listingType&sizeSqm&radiusKm` | — | `{success, estimate: {...}}` | Real Toss Bank 우리집 시세 — read-only, computed fresh from real comparable listings on every call, never persisted. `radiusKm` defaults to 5.0 |
| GET | `/listings/{propertyListingId}` | — | `{success, listing: {...}, listerTrustScore}` | |
| POST | `/listings/{propertyListingId}/mark-taken` | `{counterpartyPhoneNumber?}` | `{success, listing: {...}}` | |
| POST | `/listings/{propertyListingId}/price` | `{price}` | `{success, listing: {...}}` | Real Karrot-style price-drop notification |
| POST | `/listings/{propertyListingId}/verify-ownership` | `{documentUrl}` | `201 {success, submission: {...}}` | `documentUrl` should already be a real `/api/v1/uploads/{name}` URL; review happens via the ADMIN-only property-verification queue, not documented on this page |
| DELETE | `/listings/{propertyListingId}` | — | `{success, listing: {...}}` | |

### Favorites, contact, offers, reviews

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/listings/{propertyListingId}/favorite` | — | `201 {success, favorite: {...}}` | |
| DELETE | `/listings/{propertyListingId}/favorite` | — | `{success}` | |
| GET | `/listings/favorites` | — | `{success, favorites: [...], ...pageMeta}` | |
| POST | `/listings/{propertyListingId}/contact-lister` | — | `{success, conversation: {...}}` | |
| POST | `/listings/{propertyListingId}/offers` | `{amount}` | `201 {success, offer: {...}}` | Real 당근-style price-offer negotiation |
| POST | `/offers/{offerId}/respond` | `{action, counterAmount?}` | `{success, offer: {...}}` | |
| GET | `/conversations/{conversationId}/offers` | — | `{success, offers: [...]}` | |
| POST | `/listings/{propertyListingId}/review` | `{goodPoints?, uncomfortablePoints?}` | `201 {success, review: {...}}` | Shared `HoodReviewService` — asymmetric public/private visibility, same as Jobs/Marketplace |
| GET | `/listings/{propertyListingId}/review` | — | `{success, reviews: [...]}` | |

### Errors (complete — all 20 `@ExceptionHandler`s in `PropertyListingController.kt`, 18 unique codes)

`404 OFFER_NOT_FOUND`, `400 INVALID_OFFER_AMOUNT`, `409 OFFER_ALREADY_RESOLVED`,
`400 OWN_OFFER`, `404 PROPERTY_LISTING_NOT_FOUND` (shared by 3 exceptions — the
listing itself not existing, a stale favorite pointer, and a review-lookup miss),
`400 INVALID_PROPERTY_LISTING`, `409 PROPERTY_LISTING_NOT_AVAILABLE`,
`400 OWN_PROPERTY_LISTING`, `400 INVALID_COORDINATES`, `429 RATE_LIMITED`,
`400 NEIGHBORHOOD_NOT_SET`, `404 COUNTERPARTY_NOT_FOUND`,
`409 REVIEW_TRANSACTION_NOT_COMPLETED`, `400 REVIEW_NO_COUNTERPARTY`,
`404 REVIEW_NOT_PARTY` (IDOR fix, 2026-08-30 — same fix class as Jobs/Marketplace's
identical review-ownership check), `409 REVIEW_ALREADY_SUBMITTED`,
`409 OWNERSHIP_VERIFICATION_ALREADY_PENDING`, `422 INSUFFICIENT_COMPARABLES`
(the valuation estimate needs a minimum number of real comparable listings nearby).

## Vehicle Inspections — `/api/v1/marketplace/inspections`

**Added 2026-09-05.** Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection
accompaniment) — a buyer books an independent mechanic to inspect a car listed on
`/api/v1/marketplace` (itself still undocumented on this page — a real, separate gap)
before purchase.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/mechanics/register` | `{businessName}` (+ `Idempotency-Key`) | `201 {success, mechanic: {...}}` | |
| GET | `/mechanics/me` | — | `{success, mechanic: {...}}` | |
| GET | `/mechanics` | — | `{success, mechanics: [...]}` | Public — every available mechanic |
| POST | `/mechanics/availability` | `{available}` | `{success, mechanic: {...}}` | |
| POST | `` (base path) | `{listingId, mechanicId, fee, scheduledFor}` (+ `Idempotency-Key`) | `201 {success, booking: {...}}` | |
| GET | `/my-bookings` | — | `{success, bookings: [...]}` | As the buyer |
| GET | `/my-mechanic-bookings` | — | `{success, bookings: [...]}` | As the mechanic |
| POST | `/{bookingId}/accept` | — | `{success, booking: {...}}` | |
| POST | `/{bookingId}/complete` | `{findings?}` | `{success, booking: {...}}` | |
| POST | `/{bookingId}/cancel` | — | `{success, booking: {...}}` | |

Errors: `409 MECHANIC_ALREADY_REGISTERED`, `404 MECHANIC_NOT_REGISTERED`,
`404 ACCOUNT_NOT_FOUND` (both the mechanic's and the buyer's own account map to this
same code), `400 INVALID_AMOUNT`, `404 LISTING_NOT_FOUND`,
`400 SELF_INSPECTION_NOT_ALLOWED`, `404 INSPECTION_BOOKING_NOT_FOUND`,
`409 INVALID_INSPECTION_STATUS_TRANSITION`, `422 INSUFFICIENT_FUNDS`,
`403 ACCOUNT_FROZEN`, `429 RATE_LIMITED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`.

## Vehicles — `/api/v1/vehicles`

**Added 2026-09-05, same slice.** Real Toss 내 차 시세 (my car's market value)-style
vehicle value estimator — register a car you own, get a computed depreciation-based
valuation. Distinct from Vehicle Inspections above (a real mechanic booking a
physical check) and Marketplace's own 당근카 lease-takeover listing fields — this
controller is a personal-finance estimator, not a marketplace or inspection feature.
Confirmed by direct read of `VehicleController.kt` (5 endpoints, 3 error codes, all
unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{make, model, modelYear, purchasePrice, purchaseDate, mileageKm}` | `201 {success, vehicle: {...}}` | |
| GET | `` (base path) | — | `{success, vehicles: [...]}` | |
| GET | `/{id}/valuation` | — | `{success, valuation: {...}}` | |
| POST | `/{id}/mileage` | `{mileageKm}` | `{success, vehicle: {...}}` | |
| DELETE | `/{id}` | — | `{success}` | |

Errors (all 3 real `@ExceptionHandler`s): `404 VEHICLE_NOT_FOUND`,
`400 INVALID_VEHICLE`, `429 RATE_LIMITED`.

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

## Trust Score — `/api/v1/trust-score`

**Added 2026-09-05** (sixteenth documentation slice). Real Karrot-Score-style 0-1000
numeric trust badge (starting at 30, not a manner-temperature metaphor) shown
alongside sellers/posters across Marketplace/Jobs/Community. Mirrors Credit Score
above exactly: computed live on every call, not cached, so the account-tenure factor
stays honestly current between events. Confirmed by direct read of
`TrustScoreController.kt` (1 endpoint, 1 error code).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` (base path) | — | `{success, score, factors, computedAt}` | |

Errors: `404 USER_NOT_FOUND`.

## Analytics — `/api/v1/analytics`

**Added 2026-09-05, same slice.** Real, deliberately minimal product-analytics
endpoint — a small, closed vocabulary of known event names (adding one is a one-line
code change, not a migration), not a general-purpose event pipe. Confirmed by direct
read of `AnalyticsController.kt` (2 endpoints, 1 error code).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/events` | `{eventName, platform, metadata?}` | `{success}` | `eventName` must be one of a small known set (`home_view`, `coop_rail_tap`, `discover_banner_impression` as of this writing); `metadata` is truncated to 500 characters server-side |
| GET | `/summary?days` | — | `{success, windowDays, totalActiveUsers, events: {...}, coopRailReturnRate: {...}}` | ADMIN only — gated by `SecurityConfig`'s own path matcher on this exact route, not a `@PreAuthorize` annotation on the method itself, unlike most other ADMIN-only endpoints on this page. `days` defaults to 30. `coopRailReturnRate` answers "of users who tapped the coop-savings rail, what fraction had any activity 24h+ later" |

Errors: `400 UNKNOWN_EVENT`.

## Savings — `/api/v1/savings`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/goals` | — | `{success, goals: [...]}` | |
| POST | `/goals` | `{name, targetAmount, monthlyContribution?, targetDate?, category?}` | `{success, goal: {...}}` | |
| POST | `/deposit` | `{goalId, amount, fromAccountId?}` (+ `Idempotency-Key`) | `{success, ...}` | |
| GET | `/interest-jar` | — | `{success, ...}` | |
| POST | `/interest-jar/claim` | (+ `Idempotency-Key`) | `{success, ...}` | |

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 GOAL_NOT_FOUND`, `404 ACCOUNT_NOT_FOUND`,
`404 INTEREST_JAR_NOT_FOUND`, `403 ACCOUNT_FROZEN`, `409 NO_INTEREST_AVAILABLE`,
`422 INSUFFICIENT_FUNDS`. **Corrected 2026-09-04** — real code is `ACCOUNT_NOT_FOUND`
(`WALLET_NOT_FOUND` doesn't exist), request field is `fromAccountId` not
`fromWalletId`, and `WALLET_NOT_OWNED` never actually gets returned —
`SavingsService.kt`'s own code comment explains this is deliberate: an
ownership mismatch folds into the same `ACCOUNT_NOT_FOUND` 404 an IDOR-safe
lookup already returns, not a distinguishable 403, so a caller can't use the
difference to enumerate accounts that exist but aren't theirs; `ACCOUNT_FROZEN`
(a real, separate handler) was missing from this list entirely. Recurring
auto-save is built and live-verified 2026-07-13 —
`AutoSaveScheduler` runs on a real 30-day business cadence (30-second poll for demo speed) and
charges `monthlyContribution` from the goal owner's MAIN wallet, skipping gracefully on
insufficient funds. There is no API endpoint for this — it's a background job, not a route; see
`docs/TOSS_PARITY_MATRIX.md`'s Savings row for the full account, including a real scheduler
thread-starvation bug found and fixed alongside it.

## Ikimina — `/api/v1/ikiminas`

**Added 2026-09-05** (seventh documentation slice). Real ikimina — Rwanda's own
rotating savings & credit association, genuinely distinct from every Toss/Kakao/
Naver/Coupang-sourced feature in this backend, not a localization of a foreign
product. An organizer creates a group, invites members by phone, starts a cycle, and
each round every member contributes; the round's full pot pays out automatically to
that round's recipient the moment the last member contributes. Confirmed by direct
read of `IkiminaController.kt` (7 endpoints, 15 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{name, contributionAmount, cycleFrequencyDays, memberCap}` | `201 {success, ikimina: {...}}` | |
| GET | `` (base path) | — | `{success, ikiminas: [...]}` | Caller's own ikiminas (organizer or member) |
| GET | `/{id}` | — | `{success, ikimina: {...}, balance, members: [...], currentRoundContributions}` | |
| POST | `/{id}/members` | `{phoneNumber}` | `201 {success, member: {...}}` | Organizer-only invite |
| POST | `/{id}/start` | — | `{success, ikimina: {...}}` | Organizer-only — begins the first contribution round |
| POST | `/{id}/contribute` | — (+ `Idempotency-Key`) | `{success, ikimina: {...}, payout}` | `payout` is `null` on every contribution except the one that completes the round — that one auto-triggers the payout in the same call |
| POST | `/{id}/payout` | — (+ `Idempotency-Key`) | `{success, ikimina: {...}, recipientUserId, amount}` | Manual fallback trigger for the same auto-payout `contribute` above already does when it completes a round |

### Errors (complete — all 15 `@ExceptionHandler`s in `IkiminaController.kt`)

`404 IKIMINA_NOT_FOUND` (also covers "not a member"/"not the organizer" — see below),
`404 RECIPIENT_NOT_FOUND`, `409 ALREADY_MEMBER`, `409 IKIMINA_FULL`,
`404 ACCOUNT_NOT_FOUND`, `409 IKIMINA_NOT_FORMING`, `409 IKIMINA_NOT_ACTIVE`,
`400 TOO_FEW_MEMBERS`, `409 ALREADY_CONTRIBUTED`, `422 CONTRIBUTIONS_INCOMPLETE`,
`429 RATE_LIMITED`, `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `400 INVALID_REQUEST` (bare `IllegalArgumentException`
fallback).

**Real IDOR fix, 2026-09-04** (noted in the controller's own comment): a stranger
probing `ikiminaId` values could distinguish "exists, you're not a member/organizer"
from "doesn't exist" purely from the response body, even though both already returned
404 — `IkiminaNotMemberException`/`IkiminaNotOrganizerException` and their dedicated
handlers were removed entirely, and every membership/organizer check in
`IkiminaService.kt` now throws the same `IkiminaNotFoundException` instead.

## Group Accounts — `/api/v1/group-accounts`

**Added 2026-09-05** (fifteenth documentation slice — the rest of the Savings
product family). A shared account with a real KakaoBank 회비 (dues) management
layer — organizer invites members, everyone can deposit/withdraw, the organizer can
set a recurring dues amount and remind unpaid members. Confirmed by direct read of
`GroupAccountController.kt` (9 endpoints, 13 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{name}` | `201 {success, groupAccount: {...}}` | |
| GET | `` (base path) | — | `{success, groupAccounts: [...]}` | |
| GET | `/{id}` | — | `{success, groupAccount: {...}, balance, members: [...]}` | |
| POST | `/{id}/members` | `{phoneNumber}` (+ `Idempotency-Key`) | `201 {success, member: {...}}` | Real gap fixed 2026-09-05 — a lost response after a successful invite would resubmit and hit `ALREADY_MEMBER` on retry; `deposit`/`withdraw` were already protected, this was the outlier |
| POST | `/{id}/deposit` | `{amount}` (+ `Idempotency-Key`) | `{success, groupAccount: {...}, balance, members: [...]}` | |
| POST | `/{id}/withdraw` | `{amount}` (+ `Idempotency-Key`) | `{success, groupAccount: {...}, balance, members: [...]}` | |
| PUT | `/{id}/dues` | `{amount}` | `{success, groupAccount: {...}}` | Real KakaoBank 회비 (dues) management. `amount: null` clears the recurring dues requirement |
| GET | `/{id}/dues` | — | `{success, dues: {...}}` | |
| POST | `/{id}/dues/remind` | — | `{success, remindedCount}` | |

Errors (all 13 real `@ExceptionHandler`s): `404 GROUP_ACCOUNT_NOT_FOUND` (also covers
"not a member/owner" — same IDOR fix class as Ikimina/Community/Jobs above),
`400 INVALID_GROUP_ACCOUNT_NAME`, `404 RECIPIENT_NOT_FOUND`, `409 ALREADY_MEMBER`,
`409 GROUP_ACCOUNT_FULL`, `404 ACCOUNT_NOT_FOUND`, `422 INSUFFICIENT_FUNDS`,
`403 ACCOUNT_FROZEN`, `429 RATE_LIMITED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`400 INVALID_REQUEST` (bare `IllegalArgumentException` fallback).

## Grow31 Savings — `/api/v1/grow31-savings`

**Added 2026-09-05, same slice.** A 31-day fixed daily-deposit plan with a streak
bonus for completing every day — cancelling early forfeits the streak bonus but still
pays out principal and base-rate interest. Confirmed by direct read of
`Grow31SavingsController.kt` (8 endpoints, 14 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/plans` | `{name, dailyAmount}` (+ `Idempotency-Key`) | `201 {success, plan: {...}}` | |
| GET | `/plans` | — | `{success, plans: [...]}` | |
| GET | `/plans/{id}` | — | `{success, plan: {...}, accountBalance, deposits: [...]}` | |
| GET | `/plans/{id}/transactions` | — | `{success, transactions: [...]}` | |
| POST | `/plans/{id}/deposit-today` | — (+ `Idempotency-Key`) | `{success, plan: {...}, accountBalance, deposits: [...]}` | |
| POST | `/plans/{id}/cancel` | — (+ `Idempotency-Key`) | `{success, message, plan: {...}, accountBalance, deposits: [...]}` | Streak bonus forfeited; principal and base-rate interest still paid out |
| POST | `/plans/{id}/withdraw` | — (+ `Idempotency-Key`) | `{success, message, plan: {...}, accountBalance, deposits: [...]}` | Matured plans only |
| POST | `/process-due` | — | `{success, processed}` | ADMIN only — demo/ops convenience exposing the real `@Scheduled` maturity sweep, so a real 31-day maturity can be verified without waiting real wall-clock days |

Errors (all 14 real `@ExceptionHandler`s): `404 GROW31_PLAN_NOT_FOUND`,
`400 INVALID_AMOUNT`, `400 INVALID_NAME`, `409 GROW31_PLAN_NOT_ACTIVE`,
`409 GROW31_PLAN_NOT_MATURED`, `409 GROW31_PLAN_ALREADY_WITHDRAWN`,
`409 ALREADY_DEPOSITED_TODAY`, `404 ACCOUNT_NOT_FOUND`, `422 INSUFFICIENT_FUNDS`,
`403 ACCOUNT_FROZEN`, `429 RATE_LIMITED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`.

## Round-Up — `/api/v1/savings/round-up`

**Added 2026-09-05, same slice.** Real round-up auto-saving settings — the actual
round-up itself fires from inside `P2pService.sendDirect`'s own real
`Idempotency-Key`-protected transfer, so this settings endpoint doesn't need one of
its own. Confirmed by direct read of `RoundUpController.kt` (2 endpoints, 5 error
codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` (base path) | — | `{success, settings: {...}}` | |
| POST | `` (base path) | `{enabled, roundToNearest, targetGoalId?, targetStockId?}` | `{success, settings: {...}}` | Exactly one of `targetGoalId`/`targetStockId` when `enabled` — round-up spare change into either a Savings goal or a stock purchase |

Errors (all 5 real `@ExceptionHandler`s): `400 INVALID_ROUND_UP_INCREMENT`,
`400 ROUND_UP_TARGET_REQUIRED`, `400 ROUND_UP_SINGLE_TARGET_REQUIRED`,
`404 GOAL_NOT_FOUND`, `404 STOCK_NOT_FOUND`.

## SACCO — `/api/v1/sacco`

**Added 2026-09-05, same slice.** Real Umurenge SACCO-style shares & dividends —
Rwanda's real 416-sector government-backed cooperative savings model, genuinely
distinct from every Toss/Kakao/Naver/Coupang-sourced feature in this backend and from
Ikimina (a rotating-pot ROSCA, a different real Rwandan savings model entirely).
Confirmed by direct read of `SaccoController.kt` (4 endpoints, 9 error codes, all
unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/shares/buy` | `{amount}` (+ `Idempotency-Key`) | `{success, shareholding, currentValue}` | |
| POST | `/shares/redeem` | `{amount}` (+ `Idempotency-Key`) | `{success, shareholding, currentValue}` | |
| GET | `/shares/me` | — | `{success, shareholding, currentValue}` | Both `null` if the caller has never bought shares |
| GET | `/dividends/me` | — | `{success, payouts: [...]}` | |

Errors (all 9 real `@ExceptionHandler`s): `404 ACCOUNT_NOT_FOUND`,
`404 SACCO_NO_SHAREHOLDING`, `422 SACCO_INSUFFICIENT_SHARES`,
`422 SACCO_NO_SHARES_OUTSTANDING`, `429 RATE_LIMITED`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `400 INVALID_REQUEST` (bare
`IllegalArgumentException` fallback).

## Upfront-Interest Deposits — `/api/v1/upfront-deposits`

**Added 2026-09-05, same slice.** Real Toss Bank 먼저 이자받는 정기예금 (interest-
paid-upfront term deposit) equivalent — the full interest amount pays out to the
caller's main account immediately at open, with principal locked for 12 months.
Confirmed by direct read of `UpfrontInterestDepositController.kt` (5 endpoints, 11
error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{principal}` (+ `Idempotency-Key`) | `201 {success, deposit: {...}, message}` | |
| GET | `` (base path) | — | `{success, deposits: [...]}` | |
| GET | `/{id}/transactions` | — | `{success, transactions: [...]}` | |
| POST | `/{id}/withdraw` | — (+ `Idempotency-Key`) | `{success, deposit: {...}, message}` | Matured deposits only |
| POST | `/process-due` | — | `{success, processed}` | ADMIN only — demo/ops convenience exposing the real `@Scheduled` maturity sweep |

Errors (all 11 real `@ExceptionHandler`s): `404 UPFRONT_DEPOSIT_NOT_FOUND`,
`400 INVALID_AMOUNT`, `409 UPFRONT_DEPOSIT_NOT_MATURED`,
`409 UPFRONT_DEPOSIT_ALREADY_WITHDRAWN`, `404 ACCOUNT_NOT_FOUND`,
`422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`, `429 RATE_LIMITED`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`.

## Weekly Savings — `/api/v1/weekly-savings`

**Added 2026-09-05, same slice.** A 26-week savings plan with a rising per-week
deposit amount (`escalationRate` above `baseWeeklyAmount`) and the same streak-bonus-
on-completion/forfeit-on-early-cancel shape as Grow31 above. Confirmed by direct read
of `WeeklySavingsController.kt` (7 endpoints, 14 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/plans` | `{name, baseWeeklyAmount, escalationRate}` (+ `Idempotency-Key`) | `201 {success, plan: {...}}` | |
| GET | `/plans` | — | `{success, plans: [...]}` | |
| GET | `/plans/{id}` | — | `{success, plan: {...}, accountBalance, installments: [...]}` | |
| GET | `/plans/{id}/transactions` | — | `{success, transactions: [...]}` | |
| POST | `/plans/{id}/cancel` | — (+ `Idempotency-Key`) | `{success, message, plan: {...}, accountBalance, installments: [...]}` | Streak bonus forfeited; principal and base-rate interest still paid out |
| POST | `/plans/{id}/withdraw` | — (+ `Idempotency-Key`) | `{success, message, plan: {...}, accountBalance, installments: [...]}` | Matured plans only |
| POST | `/process-due` | — | `{success, processed}` | ADMIN only — demo/ops convenience exposing the real `@Scheduled` weekly-installment sweep, network-wide (not per-user-scoped) |

Errors (all 14 real `@ExceptionHandler`s): `404 WEEKLY_PLAN_NOT_FOUND`,
`400 INVALID_ESCALATION_RATE`, `400 INVALID_AMOUNT`, `400 INVALID_NAME`,
`409 WEEKLY_PLAN_NOT_ACTIVE`, `409 WEEKLY_PLAN_NOT_MATURED`,
`409 WEEKLY_PLAN_ALREADY_WITHDRAWN`, `404 ACCOUNT_NOT_FOUND`,
`422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`, `429 RATE_LIMITED`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`.

## Stocks — `/api/v1/stocks`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, stocks: [...]}` | |
| GET | `/portfolio` | — | `{success, portfolio: [...]}` | |
| POST | `/buy` | `{stockId, shares}` (+ `Idempotency-Key`) | `{success, ...}` | Weighted-average-cost basis recomputed on each buy. **Real 30/hour per-user rate limit and `shares > 0` validation added 2026-09-04** |
| POST | `/sell` | `{stockId, shares}` (+ `Idempotency-Key`) | `{success, ...}` | Same rate limit and validation as `/buy` |

Errors: `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `404 STOCK_NOT_FOUND`, `404 ACCOUNT_NOT_FOUND`,
`400 INVALID_AMOUNT`, `422 INSUFFICIENT_SHARES`, `422 INSUFFICIENT_FUNDS`,
`403 ACCOUNT_FROZEN`, `429 RATE_LIMITED`. **Corrected 2026-09-04** — the real code is
`ACCOUNT_NOT_FOUND`, not `WALLET_NOT_FOUND` (which doesn't exist), and `INVALID_AMOUNT`/
`ACCOUNT_FROZEN`/`RATE_LIMITED` were missing entirely. RSE (Rwanda Stock Exchange)
brokerage/custody integration is blocked (regulatory), not built — stock prices and
trades here are simulated.

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
`409 POLICY_NOT_ACTIVE`, `404 ACCOUNT_NOT_FOUND`, `403 ACCOUNT_FROZEN`,
`422 INSUFFICIENT_FUNDS`, `400 INVALID_REQUEST`, `400 INVALID_CLAIM`,
`429 RATE_LIMITED`. **Corrected 2026-09-04** — real code is `ACCOUNT_NOT_FOUND`,
not `WALLET_NOT_FOUND` (doesn't exist); `ACCOUNT_FROZEN`/`INVALID_CLAIM`/
`RATE_LIMITED` were missing entirely. (This controller used to throw the
inconsistent `RATE_LIMIT_EXCEEDED` for the same `RateLimitExceededException`
other controllers throw `RATE_LIMITED` for — standardized on `RATE_LIMITED`
repo-wide 2026-09-04, see `docs/TOSS_PARITY_MATRIX.md`'s Overview row.) Real insurer quote/bind
adapters are not built — this is itunda's own claims workflow, not a live
connection to an actual insurer.

`InsuranceController.kt` also has 5 real, undocumented premium-fund routes not
listed above: `POST /policies/{policyId}/premium-fund` (create), `POST
/premium-funds/{fundId}/contribute`, `POST /premium-funds/{fundId}/cancel`, `GET
/premium-funds`, and the ADMIN-only `POST /policies/process-renewal-reminders` —
with their own `PREMIUM_FUND_NOT_FOUND`/`PREMIUM_FUND_ALREADY_EXISTS`/
`PREMIUM_FUND_NOT_ACTIVE`/`INVALID_PREMIUM_FUND_AMOUNT` error codes.

### Insurance claims review — `/api/v1/system/insurance-claims` (ADMIN role only)

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/queue` | — | `{success, queue: [...]}` | All `SUBMITTED` claims, oldest first |
| POST | `/{claimId}/decide` | `{approve, reason?}` | `{success, claim}` | On `approve: true`, really pays out from a new `insurance_claims_expense` ledger account straight into the claimant's wallet — confirmed live, balance moved by the exact claim amount |

Errors (complete — all 5 real `@ExceptionHandler`s in `InsuranceClaimsAdminController.kt`):
`404 CLAIM_NOT_FOUND`, `409 CLAIM_NOT_PENDING` (already decided), `404 ACCOUNT_NOT_FOUND`,
`422 INSUFFICIENT_FUNDS`, `400 INVALID_DECISION_REASON` (added 2026-09-05 — a real
handler this section had never listed). **Corrected 2026-09-04** — real code is
`ACCOUNT_NOT_FOUND`, not `WALLET_NOT_FOUND` (doesn't exist; confirmed via
`InsuranceClaimsAdminController.kt`).

## Crop Weather-Index Insurance — `/api/v1/insurance/crop-index`

**Added 2026-09-05** (twenty-third documentation slice). Real Rwanda NAIS-style
parametric crop weather-index insurance — a genuinely different product shape from
`InsuranceController` above (claims-based): here, a season's real rainfall index is
published once by an admin, and every enrolled policy for that district/season
either automatically pays out or doesn't, based on the published index — no
individual claim filing at all. Confirmed by direct read of
`WeatherIndexInsuranceController.kt` (7 endpoints, 12 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/catalog` | — | `{success, catalog: {...}}` | |
| POST | `/policies` | `{cropType, district, season, insuredAmount}` (+ `Idempotency-Key`) | `201 {success, policy: {...}}` | |
| GET | `/policies` | — | `{success, policies: [...]}` | |
| GET | `/policies/{id}` | — | `{success, policy: {...}}` | |
| POST | `/policies/{id}/cancel` | — (+ `Idempotency-Key`) | `{success, policy: {...}}` | |
| POST | `/districts/{district}/seasons/{season}/index` | `{rainfallIndexPercent, droughtThresholdPercent}` | `201 {success, index: {...}}` | ADMIN only. Deliberately NOT `Idempotency-Key`-protected — a one-time, admin-transcribed real fact, guarded instead by a real DB-unique constraint on `(district, season)`, not a retryable client action |
| GET | `/districts/{district}/seasons/{season}/index` | — | `{success, index}` | Public — `index: null` (not an error) before one has been published yet |

Errors (all 12 real `@ExceptionHandler`s): `404 WEATHER_INDEX_POLICY_NOT_FOUND`,
`409 WEATHER_INDEX_POLICY_NOT_CANCELLABLE`,
`409 SEASON_RAINFALL_INDEX_ALREADY_PUBLISHED`, `400 INVALID_WEATHER_INDEX_ENROLLMENT`,
`404 ACCOUNT_NOT_FOUND`, `422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `429 RATE_LIMITED`, `400 INVALID_REQUEST` (bare
`IllegalArgumentException` fallback).

## Merchant — `/api/v1/merchant`

**Fully re-documented 2026-09-05** (closing the "25+ more `@ExceptionHandler`s exist
beyond what's listed here" gap this section's own 2026-09-04 note flagged) — every real
`@GetMapping`/`@PostMapping` and every real `@ExceptionHandler` in
`MerchantController.kt` is now listed below (32 endpoints, 38 error codes), confirmed
by direct read of the controller source, not inferred from the pattern of the core-flow
subset previously documented.

**Real coverage gap found and closed 2026-09-05**: `/api/v1/merchant` is shared, verbatim,
by 8 OTHER `@RestController` classes beyond `MerchantController.kt` itself
(`MerchantAdController`/`MerchantCouponController`/`MerchantFollowController`/
`MerchantDiscoveryController`/`MerchantUpdateController`/`MerchantBillingController`/
`MerchantBookingController`/`MerchantBookingReviewController`, all documented in their
own sections below) — this section's own path-based header made every one of them
look "already documented" to a naive prefix-matching audit, the same false-negative
class already found once for `RideController`/`RideTrustedContactController` above,
just in the opposite direction (siblings sharing one exact path, not a parent/
child prefix pair).

### Registration & profile

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/register` | `{businessName}` (+ `Idempotency-Key`) | `{success, merchant: {...}}` | No KYB/business verification — accepts any authenticated user |
| GET | `/me` | — | `{success, merchant: {...}}` | |
| POST | `/fee-waiver/apply` | — | `{success, merchant: {...}}` | Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support) |
| POST | `/api-key/generate` | — | `{success, apiKey}` | Real "Pay with itunda" external checkout key — shown exactly once, only its hash is stored afterward |
| POST | `/location` | `{latitude, longitude}` | `{success, merchant: {...}}` | |
| POST | `/category` | `{category}` | `{success, merchant: {...}}` | |
| POST | `/cashback-rate` | `{rate}` | `{success, merchant: {...}}` | Real Naver Pay-style boosted-cashback opt-in |
| POST | `/eats-membership-participation` | `{participates}` | `{success, merchant: {...}}` | Real Baemin Club-style participating-restaurant opt-in |
| POST | `/scheduled-orders-participation` | `{accepts}` | `{success, merchant: {...}}` | Real 배달의민족 예약주문 (scheduled ordering) opt-in |
| POST | `/accepting-orders` | `{accepting}` | `{success, merchant: {...}}` | Real Baemin CEO app 영업일시중지 (temporarily pause business) |
| POST | `/closed-weekdays` | `{weekdays}` | `{success, merchant: {...}}` | Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day schedule) |
| POST | `/photo` | `{photoUrl}` | `{success, merchant: {...}}` | Restaurant-card photo |
| POST | `/photos` | `{photoUrls}` | `{success, merchant: {...}}` | Real photo gallery (Maps redesign, 2026-08-28) |
| POST | `/min-order` | `{minOrderAmount}` | `{success, merchant: {...}}` | |
| POST | `/phone` | `{phoneNumber}` | `{success, merchant: {...}}` | |
| POST | `/hours` | `{openingHours}` | `{success, merchant: {...}}` | |
| POST | `/prep-time` | `{avgPrepTimeMinutes}` | `{success, merchant: {...}}` | Real per-merchant kitchen-prep time |
| POST | `/pickup-discount` | `{pickupDiscountPercent}` | `{success, merchant: {...}}` | Real Baemin 포장할인 (pickup discount) |

### Payments & collection

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/qr/generate` | `{amount, description}` | `{success, paymentIntent: {...}}` | |
| GET | `/intent/{intentId}` | — | `{success, ...}` | Real read-only preview before `collect` — lets a payer see the merchant/amount/their own coupon eligibility first |
| POST | `/collect/{intentId}` | `{couponId?, pointsToRedeem?}` (+ `Idempotency-Key`) | `{success, ...}` | Ownership-checked, ledger-backed, real 1.5% fee split. On success, if the merchant has a `webhookUrl`, delivers a real `PAYMENT_STATUS_CHANGED` HTTP POST. Real persistent retry as of 2026-07-13 (7 attempts, 1/4/16/64/256/1024/4096-minute schedule) — failure never blocks or rolls back the payment. `couponId`/`pointsToRedeem` are where the coupon/loyalty-points error codes below actually fire from — there's no separate "redeem coupon" endpoint |
| POST | `/{merchantId}/static-qr/pay` | `{amount, description}` (+ `Idempotency-Key`) | `{success, ...}` | Real Kakao Pay 정액 QR (static/fixed merchant QR) — public `merchantId` lookup, any registered merchant already accepts dynamic QR, this isn't a new authorization surface |
| GET | `/{merchantId}/loyalty-balance` | — | `{success, pointBalance}` | Real Toss Place-style 자동 적립 balance check, lets a customer see their point balance at this store before redeeming at checkout |
| POST | `/pay/customer-code` | `{accountId?}` | `{success, code, expiresAt, accountId}` | Real customer-presented payment code — called by the PAYING customer, any logged-in user, not merchant-role-specific |
| POST | `/pay/charge-by-code` | `{code, amount}` (+ `Idempotency-Key`) | `{success, ...}` | Called by the MERCHANT after scanning the customer's code |
| POST | `/card/charge` | `{amount, description, cardNumber, expiryMonth, expiryYear, cvc}` (+ `Idempotency-Key`) | `{success, ...}` | Real demo card-processing endpoint — not real card-network integration |

### Webhooks

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/webhook-url` | `{webhookUrl}` | `{success, merchant: {...}}` | Registers the URL `collect`/Commerce order updates/`/api/v1/pay/*` deliver events to |
| POST | `/webhook-secret/generate` | — | `{success, webhookSecret}` | `whsec_`-prefixed, shown exactly once, stored in plaintext (re-signs every future delivery with it, unlike the one-way-hashed API key). Every delivery afterward carries `X-Itunda-Signature: HMAC-SHA256(rawBody, secret)` — see `docs/PAYMENTS.md`'s Webhooks section |
| GET | `/webhook-deliveries` | — | `{success, deliveries: [...]}` | |
| POST | `/webhook-deliveries/{deliveryId}/replay` | — | `202 {success, delivery: {...}}` | Manually replay an exhausted-retry delivery. Returns `400 {success:false, error:"WEBHOOK_URL_NOT_CONFIGURED"}` or `409 {success:false, error:"WEBHOOK_DELIVERY_NOT_REPLAYABLE"}` as plain bodies, NOT the shared `ApiError` shape every other error on this page uses — a real, minor inconsistency, not yet unified |

### Reports

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/reports?from&to` | — | `{success, from, to, days: [...]}` | Defaults to the last 7 days when `from`/`to` are omitted |
| GET | `/reports/top-products?from&to` | — | `{success, ...}` | Real Coupang WING-style top-selling-products report |

### Errors (complete — all 38 `@ExceptionHandler`s in `MerchantController.kt`)

`400 INVALID_DATE_FORMAT` (reports `from`/`to` not `YYYY-MM-DD`), `400 INVALID_REPORT_RANGE`,
`409 MERCHANT_ALREADY_REGISTERED`, `400 INVALID_BUSINESS_NAME`, `404 MERCHANT_NOT_FOUND`,
`404 ACCOUNT_NOT_FOUND`, `404 PAYMENT_CODE_NOT_FOUND`, `409 PAYMENT_CODE_NOT_PAYABLE`,
`404 CUSTOMER_PAYMENT_CODE_NOT_FOUND`, `409 CUSTOMER_PAYMENT_CODE_NOT_PAYABLE`,
`422 ACCOUNT_NOT_PAYMENT_ELIGIBLE`, `400 SELF_PAYMENT_NOT_ALLOWED`, `422 CARD_DECLINED`,
`400 INVALID_WEBHOOK_URL`, `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`,
`429 RATE_LIMITED`, `400 INVALID_COUPON`, `400 INSUFFICIENT_LOYALTY_POINTS`,
`404 COUPON_NOT_FOUND`, `403 COUPON_NOT_ELIGIBLE`, `409 COUPON_ALREADY_REDEEMED`,
`400 INVALID_COORDINATES`, `400 INVALID_CATEGORY`, `400 INVALID_CLOSED_WEEKDAYS`,
`400 INVALID_CASHBACK_RATE`, `400 INVALID_PHOTO_URL`, `409 MERCHANT_ALREADY_WAIVED`,
`422 MERCHANT_NOT_ELIGIBLE_FOR_FEE_WAIVER`, `400 INVALID_AMOUNT` (static-QR pay),
`400 INVALID_MIN_ORDER_AMOUNT`, `400 INVALID_PHONE_NUMBER`, `400 INVALID_OPENING_HOURS`,
`400 INVALID_AVG_PREP_TIME`, `400 INVALID_PICKUP_DISCOUNT`.

**Corrected 2026-09-04** — real code is `ACCOUNT_NOT_FOUND`, not `WALLET_NOT_FOUND`
(doesn't exist). No POS/card processing (beyond the demo `card/charge` above) — see
`docs/TOSS_PARITY_MATRIX.md`'s Merchant row. B2B payroll lives at a separate
`PayrollController`, documented immediately below.

## Merchant Ads — `/api/v1/merchant` (`MerchantAdController`)

**Added 2026-09-05** (seventeenth documentation slice — the first of the 5 Merchant
sibling controllers named above). Real radius-targeted local business ads — creating
or extending an ad is a real payment (`fee_revenue`), so it's `Idempotency-Key`-gated.
Confirmed by direct read of `MerchantAdController.kt` (3 endpoints, 10 error codes,
all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/ads` | `{title, description?, radiusMeters, days}` (+ `Idempotency-Key`) | `201 {success, ad: {...}}` | Creates or extends the caller's one ad |
| GET | `/ads/me` | — | `{success, ad: {...}}` | |
| GET | `/ads/nearby?latitude&longitude` | — | `{success, ads: [...]}` | |

Errors (all 10 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`,
`400 MERCHANT_LOCATION_REQUIRED`, `400 INVALID_AD_TITLE`, `400 INVALID_AD_RADIUS`,
`400 INVALID_AD_DURATION`, `400 INVALID_COORDINATE`, `404 ACCOUNT_NOT_FOUND`,
`422 INSUFFICIENT_FUNDS`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`.

## Merchant Coupons — `/api/v1/merchant` (`MerchantCouponController`)

**Added 2026-09-05, same slice.** Real merchant coupons with 단골 (regulars-only)
loyalty gating, plus a customer-facing "Coupon box" browse and Store-points loyalty
balances. Creation/management isn't money-moving itself (no `Idempotency-Key`) —
actual redemption happens inside `MerchantController.collect`, which already requires
one. Confirmed by direct read of `MerchantCouponController.kt` (8 endpoints, 3 error
codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/coupons/process-expiry-reminders` | — | `{success, processed}` | ADMIN only — manual trigger for the expiry-reminder scheduler, fires system-wide |
| POST | `/coupons` | `{title, description?, discountType, discountValue, regularsOnly?, expiresAt?}` | `201 {success, coupon: {...}}` | |
| GET | `/coupons` | — | `{success, coupons: [...]}` | Merchant's own issued coupons |
| POST | `/coupons/{couponId}/deactivate` | — | `{success, coupon: {...}}` | |
| GET | `/{merchantId}/coupons` | — | `{success, coupons: [...]}` | Customer-facing — coupons available to the caller at this specific merchant |
| GET | `/coupons/browse` | — | `{success, coupons: [...]}` | Real "Coupon box" browse across all merchants |
| GET | `/coupons/my-redemptions` | — | `{success, redemptions: [...]}` | |
| GET | `/loyalty/my-balances` | — | `{success, balances: [...], total}` | Real Membership-screen "Store points" row |

Errors (all 3 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`,
`400 INVALID_COUPON`, `404 COUPON_NOT_FOUND`.

## Merchant Follow — `/api/v1/merchant` (`MerchantFollowController`)

**Added 2026-09-05, same slice.** Real Naver Smart Store-style 알림받기 (follow a
store) — followers can be broadcast a message from the merchant. Confirmed by direct
read of `MerchantFollowController.kt` (5 endpoints, 3 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/{merchantId}/follow` | — | `201 {success, follow: {...}}` | |
| DELETE | `/{merchantId}/follow` | — | `{success}` | |
| GET | `/follows` | — | `{success, follows: [...], ...pageMeta}` | Caller's own followed merchants |
| GET | `/followers/count` | — | `{success, count}` | Merchant's own follower count |
| POST | `/followers/broadcast` | `{title, body}` | `{success, recipientCount}` | Merchant-only |

Errors (all 3 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`,
`400 INVALID_BROADCAST`, `429 RATE_LIMITED`.

## Merchant Discovery — `/api/v1/merchant` (`MerchantDiscoveryController`)

**Added 2026-09-05, same slice.** Customer-facing "stores near me" — mirrors
`AgentDiscoveryController` exactly. Confirmed by direct read of
`MerchantDiscoveryController.kt` (1 endpoint, 1 error code).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/nearby?latitude&longitude&radiusKm` | — | `{success, merchants: [...]}` | `radiusKm` defaults to 5 |

Errors: `400 INVALID_MERCHANT_SEARCH` (bare `IllegalArgumentException` fallback).

## Merchant Updates — `/api/v1/merchant` (`MerchantUpdateController`)

**Added 2026-09-05, same slice.** Real business news/updates feed a merchant posts to
their own storefront. Confirmed by direct read of `MerchantUpdateController.kt` (3
endpoints, 4 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/updates` | `{label, title, body, periodStart?, periodEnd?}` | `201 {success, update: {...}}` | |
| GET | `/{merchantId}/updates` | — | `{success, updates: [...]}` | |
| POST | `/updates/{updateId}/like` | — | `{success, liked}` | Idempotent toggle |

Errors (all 4 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`,
`404 MERCHANT_UPDATE_NOT_FOUND`, `400 INVALID_MERCHANT_UPDATE`, `429 RATE_LIMITED`.

## Merchant Billing — `/api/v1/merchant` (`MerchantBillingController`)

**Added 2026-09-05, later same day** (the last 3 of the 8 Merchant sibling
controllers, closing this gap completely). Real Kakao Pay 정기결제/Toss Payments
billing-key-style recurring merchant billing — a merchant defines a plan, a customer
subscribes, and the recurring charge itself happens elsewhere (this controller only
manages plans/subscriptions, not the charge-execution scheduler). Confirmed by direct
read of `MerchantBillingController.kt` (7 endpoints, 11 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/billing-plans` | `{name, description?, amount, intervalDays}` (+ `Idempotency-Key`) | `201 {success, plan: {...}}` | |
| GET | `/billing-plans` | — | `{success, plans: [...]}` | Merchant's own plans |
| POST | `/billing-plans/{planId}/deactivate` | — | `{success, plan: {...}}` | |
| GET | `/{merchantId}/billing-plans` | — | `{success, plans: [...]}` | Customer-facing |
| POST | `/billing-plans/{planId}/subscribe` | — (+ `Idempotency-Key`) | `201 {success, subscription: {...}}` | |
| GET | `/billing-subscriptions/my` | — | `{success, subscriptions: [...]}` | Customer's own subscriptions |
| POST | `/billing-subscriptions/{subscriptionId}/cancel` | — | `{success, subscription: {...}}` | |

Errors (all 11 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`,
`400 INVALID_BILLING_PLAN`, `404 BILLING_PLAN_NOT_FOUND`,
`404 BILLING_SUBSCRIPTION_NOT_FOUND`, `400 SELF_SUBSCRIPTION_NOT_ALLOWED`,
`422 INSUFFICIENT_FUNDS`, `404 ACCOUNT_NOT_FOUND`, `429 RATE_LIMITED`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`.

## Merchant Booking — `/api/v1/merchant` (`MerchantBookingController`)

**Added 2026-09-05, same batch.** Real local-business appointment booking (hair
salons, clinics, etc.) with a real Kakao Hair Shop-style prepay-to-book deposit for
any service the merchant marks `requiresPrepay` (see `MerchantProductController`,
not yet documented). Confirmed by direct read of `MerchantBookingController.kt` (12
endpoints, 13 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/booking/availability` | `{windows: [{dayOfWeek, startTime, endTime}]}` | `{success, windows: [...]}` | Merchant sets their own recurring weekly availability — full replace |
| GET | `/booking/availability` | — | `{success, windows: [...]}` | |
| GET | `/{merchantId}/booking-availability` | — | `{success, windows: [...]}` | Customer-facing |
| GET | `/{merchantId}/booking-slots?serviceId&date` | — | `{success, slots: [...]}` | |
| POST | `/bookings` | `{merchantId, serviceId, date, startTime, notes?}` (+ `Idempotency-Key`) | `201 {success, booking: {...}}` | Real fix, 2026-08-02: this became money-moving the moment prepay-to-book shipped (conditionally holds a real deposit), so it needed the `Idempotency-Key` it was originally missing |
| GET | `/bookings/my-bookings` | — | `{success, bookings: [...], ...pageMeta}` | Customer's own bookings |
| GET | `/bookings/merchant-bookings` | — | `{success, bookings: [...], ...pageMeta}` | Merchant's incoming bookings |
| POST | `/bookings/{bookingId}/respond` | `{confirm}` | `{success, booking: {...}}` | Merchant-only |
| POST | `/bookings/{bookingId}/complete` | — | `{success, booking: {...}}` | |
| POST | `/bookings/{bookingId}/cancel` | — | `{success, booking: {...}}` | |
| GET | `/bookings/{bookingId}/deposit` | — | `{success, deposit: {...}}` | Only meaningful for `requiresPrepay` services |
| POST | `/bookings/process-no-shows` | — | `{success, processedCount, bookings: [...]}` | ADMIN only — manual trigger for the no-show sweep, network-wide |

Errors (all 13 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`,
`400 INVALID_AVAILABILITY_WINDOW`, `400 SERVICE_NOT_BOOKABLE`,
`400 INVALID_BOOKING_SLOT`, `409 SLOT_NO_LONGER_AVAILABLE`, `404 SERVICE_NOT_FOUND`,
`404 BOOKING_NOT_FOUND`, `409 INVALID_BOOKING_STATUS_TRANSITION`,
`422 INSUFFICIENT_FUNDS` (real fix, 2026-07-25 — a customer without enough balance
for a prepay deposit previously got a raw unhandled 500, not this real error, since
this controller had never registered a handler for the same exception
`MerchantController.collect` already handles), `404 ACCOUNT_NOT_FOUND`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`.

## Merchant Booking Reviews — `/api/v1/merchant` (`MerchantBookingReviewController`)

**Added 2026-09-05, same batch — the last of the 8 Merchant siblings.** Real post-
appointment reviews plus owner-side reply, the review counterpart to Merchant
Booking above. Not money-moving, no `Idempotency-Key`. Confirmed by direct read of
`MerchantBookingReviewController.kt` (4 endpoints, 6 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/bookings/{bookingId}/review` | `{rating, comment?}` | `201 {success, review: {...}}` | Completed bookings only |
| GET | `/{merchantId}/reviews` | — | `{success, reviews: [...], rating, ...pageMeta}` | |
| GET | `/reviews/my-reviews` | — | `{success, reviews: [...], ...pageMeta}` | |
| POST | `/reviews/{reviewId}/reply` | `{reply}` | `{success, review: {...}}` | Merchant owner's reply |

Errors (all 6 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`,
`400 INVALID_RATING`, `409 BOOKING_NOT_COMPLETED`, `409 BOOKING_ALREADY_REVIEWED`,
`404 REVIEW_NOT_FOUND`, `400 INVALID_REVIEW_REPLY`.

**All 8 controllers sharing `/api/v1/merchant` are now fully documented.** See the
note atop the main Merchant section above for the full account of how this gap was
found (the same false-negative pattern class, in reverse, as the earlier
RideController find) and confirmed complete via a full-backend re-audit for other
same-path sibling groups (none found).

## Merchant Products — `/api/v1/merchant/products`

**Added 2026-09-05** (nineteenth documentation slice). The register-software half of
"Toss Place" — a merchant's own catalog management. Not money-moving, no
`Idempotency-Key` — checkout goes through `MerchantController`'s existing
`/qr/generate`/`/card/charge`, unmodified. Confirmed by direct read of
`MerchantProductController.kt` (13 endpoints, 12 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{name, price, imageUrl?, originalPrice?, description?, durationMinutes?, requiresPrepay?, stockQuantity?}` | `201 {success, product: {...}}` | `requiresPrepay` (real Kakao Hair Shop-style prepay-to-book) is only meaningful with `durationMinutes` set |
| GET | `` (base path) | — | `{success, products: [...]}` | Merchant's own catalog |
| PUT | `/{productId}` | same shape as create | `{success, product: {...}}` | Full replace |
| PATCH | `/{productId}/stock` | `{stockQuantity?}` | `{success, product: {...}}` | Deliberately narrow — a cashier restocking a shelf must not accidentally re-submit or erase pricing/description/booking/discount settings |
| PATCH | `/{productId}/surplus-deal` | `{expiresAt?, stockQuantity?}` | `{success, product: {...}}` | Real 마감할인 (closing/surplus discount) toggle — `expiresAt: null` clears it |
| PATCH | `/{productId}/sold-out` | `{soldOut}` | `{success, product: {...}}` | Real Baemin CEO app/DoorDash-style "86" temporarily-sold-out toggle |
| GET | `/{productId}/analytics` | — | `{success, viewCount, orderCount}` | Real Coupang WING 상품분석 (product analytics) |
| DELETE | `/{productId}` | — | `{success, product: {...}}` | |
| POST | `/{productId}/option-groups` | `{name, choices, required?, multiSelect?}` | `201 {success, optionGroup: {...}}` | |
| GET | `/{productId}/option-groups` | — | `{success, optionGroups: [...]}` | Public — no ownership gate, also folded directly into Shopping's own menu-browse payload |
| DELETE | `/{productId}/option-groups/{groupId}` | — | `{success}` | |
| POST | `/{productId}/price-tiers` | `{tiers: [{minQuantity, unitPrice}]}` | `{success, tiers: [...]}` | Real bulk/wholesale pricing |
| GET | `/{productId}/price-tiers` | — | `{success, tiers: [...]}` | Public — no ownership gate |

Errors (all 12 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`,
`400 INVALID_PRODUCT_PRICE`, `400 INVALID_PRODUCT_IMAGE_URL`,
`400 INVALID_PRODUCT_DISCOUNT` (server-computed from price/originalPrice, never
trusted from the client, so this only ever fires from those two fields), `400
INVALID_PRODUCT_DURATION`, `400 INVALID_STOCK_QUANTITY`, `400 INVALID_SURPLUS_DEAL`,
`400 INVALID_PRICE_TIER`, `404 MERCHANT_PRODUCT_NOT_FOUND`,
`400 INVALID_MENU_OPTION_GROUP`, `404 MENU_OPTION_GROUP_NOT_FOUND`,
`429 RATE_LIMITED`.

## Merchant Profile Views — `/api/v1/merchant/profile-views`

**Added 2026-09-05, same slice.** Real 비즈프로필 (Karrot Business Profile)
visitor-count dashboard. Confirmed by direct read of
`MerchantProfileViewController.kt` (1 endpoint, 1 error code).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/trend?days` | — | `{success, trend: [...]}` | `days` defaults to 7 |

Errors: `404 MERCHANT_NOT_FOUND`.

## Merchant Business Account — `/api/v1/merchant/business-account`

**Added 2026-09-05** (twenty-fifth documentation slice). Real 토스뱅크 개인사업자
(business banking for sole proprietors)-style separate business ledger — the same
person on both sides of a "move" action, but real ledger-level separation between
personal and business money. Confirmed by direct read of
`MerchantBusinessAccountController.kt` (5 endpoints, 10 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | — (+ `Idempotency-Key`) | `201 {success, account: {...}}` | Real gap fixed 2026-09-05 — a lost response after a successful open would previously resubmit and hit `BUSINESS_ACCOUNT_ALREADY_EXISTS` on retry; the two `move-*` endpoints below were already protected |
| GET | `` (base path) | — | `{success, account: {...}}` | |
| GET | `/transactions` | — | `{success, transactions: [...]}` | |
| POST | `/move-to-business` | `{amount}` (+ `Idempotency-Key`) | `{success, account: {...}}` | |
| POST | `/move-to-personal` | `{amount}` (+ `Idempotency-Key`) | `{success, account: {...}}` | |

Errors (all 10 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`,
`409 BUSINESS_ACCOUNT_ALREADY_EXISTS`, `404 BUSINESS_ACCOUNT_NOT_FOUND`,
`400 INVALID_MOVE_AMOUNT`, `404 ACCOUNT_NOT_FOUND`, `422 INSUFFICIENT_FUNDS`,
`403 ACCOUNT_FROZEN`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`.

## Pay API — `/api/v1/pay`

**Added 2026-09-05, same slice.** Real "Pay with itunda" external checkout API — the
itunda equivalent of Toss Payments (a genuinely different product from Toss Pay's
own in-app consumer feature): any external merchant's own backend server integrates
directly with a real API key, zero itunda user login involved anywhere in the flow.
Deliberately `permitAll` at the Spring Security layer — API-key resolution happens
inside this controller, not the JWT filter chain, since neither a merchant's server
nor a paying customer's browser holds an itunda user JWT. Confirmed by direct read
of `PaymentsApiController.kt` (4 endpoints, 10 `@ExceptionHandler`s, 11 total codes
— one handler branches into 2 different codes depending on which header is missing).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/payments` | `{amount, description, orderId?, successUrl?, failUrl?}` header `X-Api-Key` (+ `Idempotency-Key`) | `201 {success, paymentKey, checkoutUrl, status, expiresAt}` | Rate-limited to 30/minute per merchant. Creates a payable intent; no money moves yet |
| GET | `/payments/{paymentKey}` | — header `X-Api-Key` | `{success, paymentKey, orderId, amount, status, completedTransactionId}` | Rate-limited to 120/minute per merchant. The merchant's own backend calls this to independently confirm payment before fulfilling an order — a customer's browser redirect alone is never trusted as proof of payment |
| POST | `/payments/{paymentKey}/cancel` | `{cancelReason, cancelAmount?}` header `X-Api-Key` (+ `Idempotency-Key`) | `{success, ...}` | Rate-limited to 30/minute per merchant. Real cancel/refund — reverses real ledger legs, unlike `createPayment` above |
| GET | `/checkout/{paymentKey}` | — | `{success, paymentKey, merchantName, amount, description, status, successUrl, failUrl}` | Public, no API key — the customer's own browser calls this (a real itunda-hosted checkout page), not the merchant's server |

Errors (all 10 real `@ExceptionHandler`s, 11 total codes): `401 INVALID_API_KEY`,
`400 INVALID_CHECKOUT_REQUEST`, `404 PAYMENT_NOT_FOUND`, `404 MERCHANT_NOT_FOUND`,
`400 IDEMPOTENCY_KEY_REQUIRED` / `401 API_KEY_REQUIRED` (one handler, branches on
which of the two required headers is actually missing — this controller needs both
`X-Api-Key` and, on every POST, `Idempotency-Key`, so a single hardcoded message
would be wrong for the other case), `409 PAYMENT_NOT_REFUNDABLE`,
`400 INVALID_CANCEL_REQUEST`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `429 RATE_LIMITED`.

## Merchant Payroll — `/api/v1/merchant/payroll`

**Added 2026-09-05** (was named as a real, still-open documentation gap in the Merchant
section above — added here rather than folded into that table, since it's genuinely a
separate real controller with its own error surface). Needs no external
payroll/banking credentials, unlike the rest of `docs/TOSS_PARITY_MATRIX.md`'s blocked
Merchant-row gaps, since every payment is just an internal itunda-account-to-itunda-
account ledger transfer — see `PayrollService`'s own doc comment.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/employees` | `{phoneNumber, salaryAmount}` | `{success, employee: {...}}` | Adds an existing itunda user (by phone number) to the merchant's payroll roster. Not money-moving — no `Idempotency-Key` |
| GET | `/employees` | — | `{success, employees: [...]}` | |
| DELETE | `/employees/{employeeId}` | — | `{success, employee: {...}}` | |
| POST | `/run` | — (+ `Idempotency-Key`) | `{success, ...}` | Pays every roster employee their `salaryAmount` in one run — real money movement, so `Idempotency-Key` is required, same convention as `/collect`/`/card/charge` above |
| GET | `/runs` | — | `{success, runs: [...]}` | |
| GET | `/runs/{runId}/payslips` | — | `{success, payslips: [...]}` | |

Errors (all 15 real `@ExceptionHandler`s in `PayrollController.kt`): `404 MERCHANT_NOT_FOUND`,
`404 ACCOUNT_NOT_FOUND` (the merchant's own account), `404 EMPLOYEE_PHONE_NOT_FOUND`,
`409 EMPLOYEE_ALREADY_ON_ROSTER`, `400 EMPLOYEE_IS_OWNER` (can't add yourself),
`422 EMPLOYEE_NO_ACCOUNT` (the phone number resolves to a real user with no itunda
account), `400 INVALID_SALARY_AMOUNT`, `422 EMPTY_PAYROLL_ROSTER` (running payroll with
no employees), `404 PAYROLL_ROSTER_ENTRY_NOT_FOUND`, `404 PAYROLL_RUN_NOT_FOUND`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `422 INSUFFICIENT_FUNDS` (the merchant's own account
can't cover the full run), `403 ACCOUNT_FROZEN`.

## Shopping — `/api/v1/shopping`

**Added 2026-09-05** (twelfth documentation slice). The real "browse partner
merchants" half of Coupang/Toss-style shopping — itunda's own registered Merchant
directory doubles as the shopping catalog (no external merchant-partnership network
exists to draw a separate one from). Restaurant browsing for Eats also reuses these
same endpoints (see the Eats section above) — there is no duplicate catalog surface.
Confirmed by direct read of `ShoppingController.kt` (10 endpoints, 3 error codes, all
unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/products/{productId}` | — | `{success, product: {...}}` | Real Coupang WING 상품분석 (product analytics) view trigger |
| GET | `/products/{productId}/frequently-ordered-with` | — | `{success, products: [...]}` | Real co-occurrence cross-sell — honestly empty when no pair clears the minimum real order-count bar, never padded |
| GET | `/merchants?category&businessType&q&buyerLat&buyerLng&sortBy` | — | `{success, merchants: [...], ...pageMeta}` | Also backs Eats' own restaurant browse (pass `businessType=RESTAURANT`). `sortBy` includes a real "fastest delivery" option |
| GET | `/merchants/categories?businessType` | — | `{success, categories: [...]}` | Derived from real merchant data, not a hardcoded list |
| GET | `/merchants/{merchantId}/products` | — | `{success, merchant: {...}, products: [...]}` | Public, read-only per-merchant catalog. Each product enriched with `optionGroups`/`priceTiers`/`isBestSeller`/`soldOut` |
| GET | `/products/search?q&businessType` | — | `{success, products: [...], ...pageMeta}` | Real relevance-ranked full-text search (MySQL BOOLEAN MODE, AND-of-terms, prefix matching), falling back to plain `LIKE` for queries under 3 characters (FULLTEXT's structural minimum token size) |
| GET | `/products/deals` | — | `{success, products: [...], ...pageMeta}` | Real discount-ranked rail |
| GET | `/products/surplus-deals` | — | `{success, products: [...], ...pageMeta}` | Real 마감할인 (closing/surplus discount) rail — genuinely time-boxed, still-in-stock items only, soonest-to-expire first. Each product carries `surplusExpiresAt` for a real countdown |
| GET | `/membership-day` | — | `{success, isMembershipDay, multiplier}` | Real Naver Pay 멤버십 데이 (Membership Day) cashback-boost status — single source of truth so no client banner can drift from what actually gets applied server-side |
| POST | `/merchants/{merchantId}/contact-seller` | — | `{success, conversation: {...}}` | |

Errors (all 3 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`, `400 OWN_MERCHANT`,
`404 MERCHANT_PRODUCT_NOT_FOUND`.

## Orders — `/api/v1/orders`

**Added 2026-09-05, same slice.** Real Coupang-style checkout — the money-moving
counterpart to Shopping's browse endpoints above. Two real fulfillment paths: merchant
self-declared delivery, or itunda's own rider fleet (the same `Rider` entity Eats
riders use — one registration, either delivery type). Confirmed by direct read of
`OrderController.kt` (27 endpoints, 41 `@ExceptionHandler`s, 39 unique codes —
`ORDER_NOT_FOUND` and `PRODUCT_NOT_FOUND` are each shared by 2 exceptions).

### Checkout & fulfillment

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{merchantId, items, deliveryAddress, referralCode?}` (+ `Idempotency-Key`) | `201 {success, order: {...}, items: [...]}` | `referralCode` is a real 쿠팡파트너스 (Coupang Partners)-style affiliate link — optional, and a missing/unknown/self-referral code falls through to a normal order with no commission paid, never an error |
| GET | `/my-orders` | — | `{success, orders: [...], ...pageMeta}` | Buyer's own orders |
| GET | `/merchant-orders` | — | `{success, orders: [...], ...pageMeta}` | Merchant's incoming orders |
| GET | `/{orderId}` | — | `{success, order: {...}, items: [...]}` | |
| POST | `/{orderId}/status` | `{status}` | `{success, order: {...}}` | Merchant-side fulfillment status transition |
| POST | `/{orderId}/cancel` | — | `{success, order: {...}}` | Buyer or seller, `PLACED` orders only — refunds automatically |
| GET | `/available-deliveries` | — | `{success, orders: [...], ...pageMeta}` | Rider-side, same shared `Rider` account as Eats |
| POST | `/{orderId}/claim-delivery` | — | `{success, order: {...}}` | |
| POST | `/{orderId}/complete-delivery` | — | `{success, order: {...}}` | |
| GET | `/my-deliveries` | — | `{success, orders: [...], ...pageMeta}` | Rider's own delivery history |
| GET | `/{orderId}/rider-location` | — | `{success, available, location}` | `available: false` (not an error) is the honest response before a rider has shared a location yet — mirrors `EatsController`'s identical endpoint |

### Returns & exchanges

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/{orderId}/return` | `{type, reasonCode, reasonNote?}` | `201 {success, returnRequest: {...}}` | Real post-delivery Return & Exchange — genuinely distinct from `cancel` above (which only applies to `PLACED`, pre-delivery orders) |
| GET | `/returns/my-requests` | — | `{success, returnRequests: [...], ...pageMeta}` | Buyer's own return requests |
| GET | `/returns/merchant-queue` | — | `{success, returnRequests: [...], ...pageMeta}` | Merchant's incoming return requests |
| POST | `/returns/{returnRequestId}/decide` | `{approve}` | `{success, returnRequest: {...}}` | Merchant-only |

### Reviews, inquiries, favorites

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/items/{orderItemId}/review` | `{rating, comment?}` | `201 {success, review: {...}}` | Buyer-only, ownership-checked — mirrors `EatsReviewService`'s already-proven shape |
| POST | `/reviews/{reviewId}/reply` | `{reply}` | `{success, review: {...}}` | Merchant owner's reply |
| GET | `/products/{productId}/reviews` | — | `{success, reviews: [...], ...pageMeta}` | Public |
| GET | `/products/{productId}/rating` | — | `{success, average, count}` | |
| POST | `/reviews/{reviewId}/helpful` | — | `{success, helpful}` | Real Coupang/Naver-style "helpful" idempotent toggle |
| POST | `/products/{productId}/inquiries` | `{question}` | `201 {success, inquiry: {...}}` | Real Coupang-style pre-purchase product Q&A — needs no real purchase, unlike the review endpoints above |
| GET | `/products/{productId}/inquiries` | — | `{success, inquiries: [...], ...pageMeta}` | Public |
| GET | `/inquiries/my-questions` | — | `{success, inquiries: [...], ...pageMeta}` | |
| POST | `/inquiries/{inquiryId}/answer` | `{answer}` | `{success, inquiry: {...}}` | Merchant-only |
| POST | `/products/{productId}/favorite` | — | `201 {success, favorite: {...}}` | |
| DELETE | `/products/{productId}/favorite` | — | `{success}` | |
| GET | `/products/favorites` | — | `{success, favorites: [...], ...pageMeta}` | |

### Errors (complete — all 41 `@ExceptionHandler`s in `OrderController.kt`, 39 unique codes)

`404 PRODUCT_NOT_FOUND` (shared by the favorite-lookup and order-item-product-lookup
exceptions), `404 ORDER_ITEM_NOT_FOUND`, `409 PRODUCT_NOT_YET_DELIVERED`,
`409 PRODUCT_ALREADY_REVIEWED`, `400 INVALID_RATING`, `404 REVIEW_NOT_FOUND`,
`400 INVALID_REVIEW_REPLY`, `400 INVALID_INQUIRY`, `404 INQUIRY_NOT_FOUND`,
`400 INVALID_ANSWER`, `404 MERCHANT_NOT_FOUND`, `404 MERCHANT_ACCOUNT_NOT_FOUND`,
`404 ACCOUNT_NOT_FOUND`, `400 EMPTY_ORDER`, `400 MERCHANT_NOT_ACCEPTING_ORDERS`,
`422 MIN_ORDER_AMOUNT_NOT_MET`, `400 INVALID_DELIVERY_ADDRESS`,
`400 INVALID_QUANTITY`, `409 INSUFFICIENT_PRODUCT_STOCK`, `409 PRODUCT_SOLD_OUT`,
`409 SURPLUS_DEAL_EXPIRED`, `400 SELF_ORDER_NOT_ALLOWED`, `404 ORDER_NOT_FOUND`
(shared by the order-lookup and return-order-lookup exceptions),
`409 INVALID_ORDER_STATUS_TRANSITION`, `404 RIDER_NOT_REGISTERED`,
`409 RIDER_NOT_AVAILABLE`, `409 RIDER_ALREADY_ON_DELIVERY`,
`409 DELIVERY_ALREADY_CLAIMED`, `409 ORDER_NOT_DELIVERED`,
`422 RETURN_WINDOW_EXPIRED`, `409 RETURN_ALREADY_REQUESTED`,
`400 INVALID_RETURN_REASON`, `404 RETURN_REQUEST_NOT_FOUND`,
`409 RETURN_REQUEST_ALREADY_DECIDED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`.

## Affiliate — `/api/v1/affiliate`

**Added 2026-09-05, same slice.** Real 쿠팡파트너스 (Coupang Partners)-style
affiliate link program — the real backing for `POST /api/v1/orders`'s own
`referralCode` field above. Normal itunda-user JWT gate, except `resolveLink`: a
shared link must resolve for anyone who clicks it, not just the referrer, so that one
read is public. Confirmed by direct read of `AffiliateController.kt` (4 endpoints, 3
error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/links` | `{productId}` | `201 {success, link: {...}}` | |
| GET | `/links/my-links` | — | `{success, links: [...]}` | |
| GET | `/commissions/my-commissions` | — | `{success, commissions: [...]}` | |
| POST | `/links/{code}/resolve` | — | `{success, link: {...}}` | Public — no auth required |

Errors (all 3 real `@ExceptionHandler`s): `404 PRODUCT_NOT_FOUND`,
`404 AFFILIATE_LINK_NOT_FOUND`, `429 RATE_LIMITED`.

## Product Subscriptions — `/api/v1/product-subscriptions`

**Added 2026-09-05, same slice.** Real Coupang 정기배송 (subscribe & save)-style
recurring product delivery. Confirmed by direct read of
`ProductSubscriptionController.kt` (7 endpoints, 15 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{merchantId, productId, quantity, intervalDays, deliveryAddress}` (+ `Idempotency-Key`) | `201 {success, subscription: {...}}` | |
| GET | `` (base path) | — | `{success, subscriptions: [...]}` | |
| POST | `/{id}/pause` | — | `{success, subscription: {...}}` | |
| POST | `/{id}/resume` | — | `{success, subscription: {...}}` | |
| POST | `/{id}/cancel` | — | `{success, subscription: {...}}` | |
| POST | `/{id}/skip-next` | — | `{success, subscription: {...}}` | Real Coupang 정기배송 "건너뛰기" (skip next delivery) |
| POST | `/{id}/update` | `{quantity?, intervalDays?}` | `{success, subscription: {...}}` | Real Coupang 정기배송 수량/주기 변경 (change quantity/interval) |

Errors (all 15 real `@ExceptionHandler`s, one shared across 2 exception classes):
`404 PRODUCT_SUBSCRIPTION_NOT_FOUND`, `404 PRODUCT_NOT_FOUND`,
`400 INVALID_PRODUCT_SUBSCRIPTION`, `404 MERCHANT_NOT_FOUND`,
`400 SELF_ORDER_NOT_ALLOWED`, `400 EMPTY_ORDER`, `400 INVALID_DELIVERY_ADDRESS`,
`400 INVALID_QUANTITY`, `422 MIN_ORDER_AMOUNT_NOT_MET`,
`404 ACCOUNT_NOT_FOUND` (one handler covers both `BuyerNoAccountException` and
`MerchantNoAccountException`), `422 INSUFFICIENT_FUNDS`, `429 RATE_LIMITED`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`.

## Time Deals — `/api/v1/time-deals`

**Added 2026-09-05, same slice.** Real Coupang 타임특가 (Time Deal) — a merchant runs
a time-boxed discount on one of their own products; a real Toss Shopping-style banner
carousel reuses this same active-deal data rather than a separate fabricated CMS.
Confirmed by direct read of `TimeDealController.kt` (6 endpoints, 4 error codes, all
unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{productId, dealPrice, totalQuantity, startsAt, endsAt}` | `201 {success, deal: {...}}` | Not `Idempotency-Key`-protected |
| GET | `` (base path) | — | `{success, deals: [...], ...pageMeta}` | |
| GET | `/banners` | — | `{success, banners: [...]}` | |
| GET | `/mine` | — | `{success, deals: [...], ...pageMeta}` | Merchant's own deals |
| GET | `/{dealId}` | — | `{success, deal: {...}}` | |
| POST | `/{dealId}/end` | — | `{success, deal: {...}}` | |

Errors (all 4 real `@ExceptionHandler`s): `404 MERCHANT_NOT_FOUND`,
`404 PRODUCT_NOT_FOUND`, `404 TIME_DEAL_NOT_FOUND`, `400 INVALID_TIME_DEAL`.

## Eats — `/api/v1/eats`

**Added 2026-09-05** (part of the standing "~66 remaining undocumented controllers"
follow-up). Real Baemin/Coupang Eats-style food ordering + delivery. Restaurant
browsing/menus deliberately reuse the existing `GET /api/v1/shopping/merchants` and
`GET /api/v1/shopping/merchants/{id}/products` endpoints (a restaurant IS a `Merchant`,
a menu item IS a `MerchantProduct`) — there is no separate catalog-browsing endpoint
here. Every endpoint below is confirmed by direct read of `EatsController.kt` (40
endpoints, 57 error codes).

### Browsing, memberships, riders

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/dishes?category&maxBudget&sortBy` | — | `{success, dishes: [...], ...pageMeta}` | Real Coupang Eats-style dish grid across all restaurants, not scoped to one merchant |
| POST | `/platform-membership/subscribe` | `{days}` (+ `Idempotency-Key`) | `{success, membership: {...}}` | Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver, platform-wide — distinct from the per-restaurant membership below |
| GET | `/platform-membership/me` | — | `{success, membership: {...}}` | |
| POST | `/membership/subscribe` | `{days}` (+ `Idempotency-Key`) | `{success, membership: {...}}` | Real Baemin Club (배민클럽)-style free-delivery membership, per restaurant |
| GET | `/membership/me` | — | `{success, membership: {...}}` | |
| POST | `/membership/process-expiry-reminders` | — | `{success, processed}` | ADMIN only — manual trigger for the expiry-reminder scheduler, fires system-wide for every user's expiring memberships |
| POST | `/riders/register` | — | `201 {success, rider: {...}}` | |
| GET | `/riders/me` | — | `{success, rider: {...}}` | |
| POST | `/riders/availability` | `{available}` | `{success, rider: {...}}` | |
| POST | `/riders/location` | `{latitude, longitude}` | `{success, rider: {...}}` | Backs real nearest-first ranking on `/orders/available` |
| GET | `/riders/{riderId}/rating` | — | `{success, average, count}` | |

### Orders

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/orders` | `{restaurantId, items, deliveryAddress?, deliveryLatitude?, deliveryLongitude?, deliveryNotes?, fulfillmentType?, scheduledFor?}` (+ `Idempotency-Key`) | `201 {success, order: {...}, items: [...]}` | `fulfillmentType` defaults to `DELIVERY`; a `PICKUP` order needs no delivery address. `scheduledFor` (null = ASAP) is a real 배달의민족 예약주문 (scheduled ordering) time |
| GET | `/geocode/search?q` | — | `{success, suggestions: [...]}` | Real address-search autocomplete backed by itunda's self-hosted Nominatim |
| GET | `/orders/my-orders` | — | `{success, orders: [...], ...pageMeta}` | Buyer's own orders. Each order is enriched with `riderName`/`estimatedArrivalMinutes`, resolved at the controller layer |
| GET | `/orders/restaurant-orders` | — | `{success, orders: [...], ...pageMeta}` | Restaurant owner's incoming orders |
| GET | `/orders/rider-deliveries` | — | `{success, orders: [...], ...pageMeta}` | Rider's own delivery history |
| GET | `/orders/available` | — | `{success, orders: [...], ...pageMeta}` | Deliveries an available rider can claim, nearest-first |
| GET | `/orders/{orderId}` | — | `{success, order: {...}, items: [...]}` | |
| GET | `/orders/{orderId}/rider-location` | — | `{success, available, location}` | `available: false` (not an error) is the honest response before a rider has been assigned or shared a location yet |
| POST | `/orders/{orderId}/contact-restaurant` | — | `{success, conversation: {...}}` | Real Uber Eats-style "Live Order Chat" — opens/reuses a real messaging conversation with the restaurant owner |
| POST | `/orders/{orderId}/status` | `{status, deliveryPhotoUrl?}` | `{success, order: {...}}` | Restaurant-side status transition |
| POST | `/orders/{orderId}/items/{itemId}/unavailable` | — | `{success, order: {...}}` | Real DoorDash/Uber Eats-style "Item Unavailable" flow |
| POST | `/orders/{orderId}/complete-pickup` | — | `{success, order: {...}}` | Real Baemin-style 포장주문 (Pickup) terminal transition |
| POST | `/orders/{orderId}/cancel` | — | `{success, order: {...}}` | Buyer or restaurant, `PLACED` orders only — refunds automatically |
| POST | `/orders/{orderId}/claim` | — | `{success, order: {...}}` | Rider claims an available delivery |
| POST | `/orders/{orderId}/decline` | — | `{success, order: {...}}` | Only the rider currently holding the exclusive offer can decline it |
| POST | `/orders/{orderId}/rider-status` | `{status, deliveryPhotoUrl?}` | `{success, order: {...}}` | Rider-side status transition; `deliveryPhotoUrl` is a real 안심배달 (safe/contactless delivery) proof photo, meaningful only when `status` is `DELIVERED` |
| POST | `/orders/process-abandoned-deliveries` | — | `{success, processedCount, orders: [...]}` | ADMIN only — manual trigger for the abandoned-delivery scheduler; force-cancels and refunds |
| POST | `/orders/{orderId}/tip` | `{amount}` (+ `Idempotency-Key`) | `{success, order: {...}}` | Real post-delivery tip, Idempotency-Key required since 2026-09-03 (a legitimate retry must not double-tip) |

### Reviews & favorites

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/orders/{orderId}/review` | `{restaurantRating, restaurantComment?, riderRating?, riderComment?, photoUrl?, goodPoints?}` | `201 {success, review: {...}}` | `riderRating` is nullable — a `PICKUP` order has no rider to rate |
| POST | `/reviews/{reviewId}/reply` | `{reply}` | `{success, review: {...}}` | Restaurant owner's reply to a review |
| GET | `/restaurants/{restaurantId}/reviews` | — | `{success, reviews: [...], ...pageMeta}` | |
| POST | `/reviews/{reviewId}/helpful` | — | `{success, helpful}` | Real Baemin/Coupang-style "도움돼요" (helpful) idempotent toggle |
| POST | `/reviews/{reviewId}/report` | `{reason, details?}` | `201 {success, report: {...}}` | Real 배달의민족 리뷰 신고하기 (report a review) |
| GET | `/restaurants/{restaurantId}/rating` | — | `{success, average, count}` | |
| GET | `/restaurants/{restaurantId}/good-points` | — | `{success, counts, goodPointOptions}` | `goodPointOptions` is the real, fixed vocab clients render the review pill-picker from — never invented client-side |
| POST | `/restaurants/{restaurantId}/favorite` | — | `201 {success, favorite: {...}}` | Idempotent — adding an already-favorited restaurant just returns the existing row |
| DELETE | `/restaurants/{restaurantId}/favorite` | — | `{success}` | Idempotent — removing a non-favorited restaurant is not an error |
| GET | `/favorites` | — | `{success, favorites: [...], ...pageMeta}` | |
| POST | `/favorites/share` | `{conversationId}` | `201 {success, message: {...}}` | Real Baemin-style 찜 리스트 공유하기 (share favorites list) into an existing chat |

### Errors (complete — all 57 `@ExceptionHandler`s in `EatsController.kt`)

`400 NO_FAVORITES_TO_SHARE`, `404 CONVERSATION_NOT_FOUND`, `404 RESTAURANT_NOT_FOUND`,
`404 RESTAURANT_ACCOUNT_NOT_FOUND`, `404 ACCOUNT_NOT_FOUND` (buyer, membership, and
platform-membership account-not-found all share this one code), `400
INVALID_MEMBERSHIP_DURATION` (both membership and platform-membership durations share
this code), `400 EMPTY_ORDER`, `400 INVALID_DELIVERY_ADDRESS`, `400
INVALID_COORDINATES`, `400 INVALID_DELIVERY_NOTES`, `400 INVALID_QUANTITY`, `404
MENU_ITEM_NOT_FOUND`, `409 MENU_ITEM_SOLD_OUT`, `409 SURPLUS_DEAL_EXPIRED`, `422
MISSING_REQUIRED_MENU_OPTION`, `400 INVALID_MENU_OPTION_SELECTION`, `400
SELF_ORDER_NOT_ALLOWED`, `400 RESTAURANT_NOT_ACCEPTING_ORDERS`, `404 ORDER_NOT_FOUND`,
`409 ORDER_NOT_DELIVERED`, `409 ORDER_ALREADY_TIPPED`, `400 ORDER_TIP_WINDOW_EXPIRED`,
`400 INVALID_TIP_AMOUNT`, `400 ORDER_NO_RIDER`, `400 CANNOT_MESSAGE_OWN_RESTAURANT`,
`409 ORDER_NOT_YET_DELIVERED`, `409 ORDER_ALREADY_REVIEWED`, `400 INVALID_RATING`,
`404 REVIEW_NOT_FOUND`, `400 INVALID_REVIEW_REPLY`, `400 OWN_REVIEW_REPORT`, `409
REVIEW_ALREADY_REPORTED`, `422 SCHEDULED_ORDERS_NOT_SUPPORTED`, `400
INVALID_SCHEDULED_ORDER_TIME`, `422 MIN_ORDER_AMOUNT_NOT_MET`, `422
INVALID_ORDER_CHARGE`, `409 INVALID_ORDER_STATUS_TRANSITION`, `404
ORDER_ITEM_NOT_FOUND`, `409 ORDER_ITEM_ALREADY_UNAVAILABLE`, `422 CANNOT_EMPTY_ORDER`,
`409 RIDER_ALREADY_REGISTERED`, `404 RIDER_NOT_REGISTERED`, `404
RIDER_ACCOUNT_NOT_FOUND`, `400 INVALID_RIDER_LOCATION`, `409 RIDER_NOT_AVAILABLE`,
`409 DELIVERY_ALREADY_CLAIMED`, `409 RIDER_ALREADY_ON_DELIVERY`, `409
NO_ACTIVE_OFFER`, `409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`, `429
RATE_LIMITED`.

## Eats Dine-In — `/api/v1/eats/dine-in`

**Added 2026-09-05.** Real 배민오더-style table/QR in-store ordering — restaurant
browsing/menus reuse the same Shopping catalog endpoints Eats itself does. A separate
domain from delivery/pickup Eats orders above (its own `DineInOrder` entity, its own
`DineInOrderStatus` lifecycle), not a variant of it. Confirmed by direct read of
`DineInOrderController.kt` (6 endpoints, 20 error codes).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/orders` | `{restaurantId, tableNumber, items, notes?}` (+ `Idempotency-Key`) | `201 {success, order: {...}, items: [...]}` | |
| GET | `/orders/my-orders` | — | `{success, orders: [...], ...pageMeta}` | |
| GET | `/orders/restaurant-orders` | — | `{success, orders: [...], ...pageMeta}` | |
| GET | `/orders/{orderId}` | — | `{success, order: {...}, items: [...]}` | |
| POST | `/orders/{orderId}/status` | `{status}` | `{success, order: {...}}` | |
| POST | `/orders/{orderId}/cancel` | — | `{success, order: {...}}` | |

Errors (all 20 real `@ExceptionHandler`s in `DineInOrderController.kt`):
`404 RESTAURANT_NOT_FOUND`, `404 RESTAURANT_ACCOUNT_NOT_FOUND`, `404 ACCOUNT_NOT_FOUND`,
`400 EMPTY_ORDER`, `400 RESTAURANT_NOT_ACCEPTING_ORDERS`, `400 INVALID_TABLE_NUMBER`,
`400 INVALID_QUANTITY`, `404 MENU_ITEM_NOT_FOUND`, `409 MENU_ITEM_SOLD_OUT`,
`409 SURPLUS_DEAL_EXPIRED`, `422 MISSING_REQUIRED_MENU_OPTION`,
`400 INVALID_MENU_OPTION_SELECTION`, `400 SELF_ORDER_NOT_ALLOWED`,
`404 ORDER_NOT_FOUND`, `409 INVALID_ORDER_STATUS_TRANSITION`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`.

## Eats Group Orders — `/api/v1/eats/group-orders`

**Added 2026-09-05.** Real 배달의민족 함께주문 (Baemin "Together Order") — a
pre-checkout shared-cart layer in front of the existing, unchanged
`POST /api/v1/eats/orders`; `finalize` below is the only endpoint that actually places
a real order and moves money. Confirmed by direct read of `GroupEatsOrderController.kt`
(6 endpoints, 22 error codes).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{restaurantId, deliveryAddress?, deliveryLatitude?, deliveryLongitude?, fulfillmentType?}` | `201 {success, groupOrder: {...}}` | Creates the group order and returns a real join code |
| POST | `/join` | `{joinCode}` | `{success, groupOrder: {...}}` | |
| GET | `/{groupOrderId}` | — | `{success, groupOrder: {...}, grandTotal, participants: [...]}` | Each participant's own subtotal and item lines |
| POST | `/{groupOrderId}/items` | `{items}` | `{success, groupOrder: {...}, grandTotal, participants: [...]}` | Sets (replaces) the calling participant's own item list; returns the same shape as `GET /{groupOrderId}` |
| POST | `/{groupOrderId}/finalize` | — (+ `Idempotency-Key`) | `201 {success, order: {...}, items: [...]}` | Host-only. Places one real combined order via the unchanged `EatsOrderService.placeOrder`, then posts real split-bill requests to every participant — money-moving, so `Idempotency-Key` is required |
| POST | `/{groupOrderId}/cancel` | — | `{success, groupOrder: {...}}` | Host-only |

Errors (all 22 real `@ExceptionHandler`s in `GroupEatsOrderController.kt`):
`404 GROUP_ORDER_NOT_FOUND`, `409 GROUP_ORDER_NOT_OPEN`, `403 NOT_GROUP_ORDER_HOST`,
`400 GROUP_ORDER_EMPTY`, `404 INVALID_JOIN_CODE`, `404 RESTAURANT_NOT_FOUND`,
`404 RESTAURANT_ACCOUNT_NOT_FOUND`, `404 ACCOUNT_NOT_FOUND`,
`400 INVALID_DELIVERY_ADDRESS`, `400 INVALID_COORDINATES`, `400 INVALID_QUANTITY`,
`404 MENU_ITEM_NOT_FOUND`, `422 MIN_ORDER_AMOUNT_NOT_MET`, `400 SELF_ORDER_NOT_ALLOWED`,
`422 MISSING_REQUIRED_MENU_OPTION`, `400 INVALID_MENU_OPTION_SELECTION`,
`400 EMPTY_ORDER`, `400 INSUFFICIENT_FUNDS` (note: `400`, not the `422` every other
Eats/DineIn controller above uses for this same code — a real, minor inconsistency,
not yet unified), `403 ACCOUNT_FROZEN`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `429 RATE_LIMITED`.

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

Errors: `403 FACEPAY_NOT_ENROLLED`, `404 MERCHANT_NOT_FOUND`, `404 ACCOUNT_NOT_FOUND`,
`404 PAYMENT_CODE_NOT_FOUND`, `409 PAYMENT_CODE_NOT_PAYABLE`,
`400 SELF_PAYMENT_NOT_ALLOWED`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`422 INSUFFICIENT_FUNDS`, `403 ACCOUNT_FROZEN`. **Corrected 2026-09-04** — real
codes are `ACCOUNT_NOT_FOUND`/`ACCOUNT_FROZEN`, not `WALLET_NOT_FOUND`/
`WALLET_FROZEN` (neither exists; confirmed via `FacePayController.kt`'s own
`@ExceptionHandler` list).

## Identity — `/api/v1/identity`

Built and live-verified 2026-07-13 — see `docs/TOSS_PARITY_MATRIX.md`'s Compliance row for the
full account of what this closes and what's still blocked (real NIDA/vendor verification).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/submit` | `{documentType, documentNumber, documentReference}` | `201` `{success, submission}` | `documentReference` is a demo-mode stand-in for an uploaded ID scan — no file-storage layer exists. Rejects a second submission while one is already `PENDING` |
| GET | `/status` | — | `{success, submissions: [...]}` | Caller's own submission history, most recent first |

Errors: `409 KYC_SUBMISSION_ALREADY_PENDING`.

## Certificate — `/api/v1/certificate`

**Added 2026-09-05, same slice.** Real Korean-style electronic-certificate (공동인증서)
issuance — a real, itunda-issued key pair the private half of which is shown exactly
once and never persisted server-side; the public half backs signature verification
that any third party (with or without an itunda account) can check. Confirmed by
direct read of `CertificateController.kt` (6 endpoints, 5 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/issue` | — | `201 {success, certificate: {...}, privateKey}` | `privateKey` is returned exactly once — this backend never persists it and can never show it again |
| GET | `/me` | — | `{success, certificate: {...}}` | |
| POST | `/revoke` | — | `{success, certificate: {...}}` | |
| GET | `/status/{serialNumber}` | — | `{success, certificate: {...}}` | Public — no auth required. Real CRL/OCSP-style status check |
| POST | `/verify` | `{serialNumber, payload, signature}` | `{success, signatureValid, certificateStatus, userId, serialNumber}` | Public — no auth required. Real asymmetric-signature verification never needs a secret credential, and a third party checking a document someone else signed has no itunda account of their own — both this and `/status` are explicitly `permitAll`'d, a real bug fix (both were initially left behind the default JWT gate, 401ing the exact callers they're meant to serve) |
| POST | `/process-renewal-reminders` | — | `{success, processed}` | ADMIN only — manual trigger for the renewal-reminder scheduler, fires system-wide |

Errors (all 5 real `@ExceptionHandler`s): `404 USER_NOT_FOUND`,
`403 KYC_REQUIRED`, `404 NO_ACTIVE_CERTIFICATE`, `404 CERTIFICATE_NOT_FOUND`,
`429 RATE_LIMITED`.

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
succeed), `404 ACCOUNT_NOT_FOUND`, `404 USER_NOT_FOUND`, `400 INVALID_STEP_COUNT`
(a real handler in `RewardsController.kt` not covered by the 5-task catalog note
below — a step-tracking task exists beyond what's documented here). **Corrected
2026-09-04** — real code is `ACCOUNT_NOT_FOUND`, not `WALLET_NOT_FOUND` (doesn't
exist). All 5 catalog tasks
(`task_first_transfer`, `task_first_bill`, `task_savings_goal`, `task_referral`,
`task_profile`) are real-activity-verified as of 2026-07-17 — see the Auth section above for
the `profile/photo` and `profile/verify-email` endpoints `task_profile` checks.

## Shopping Points — `/api/v1/shopping/points`

**Added 2026-09-05, same slice.** Real Toss Shopping 포인트 및 쿠폰받기 (get points
and coupons) daily-mission row, plus a stated-odds spin reward. Naturally idempotent
— each mission only ever credits once, checked against a real stored flag — so no
`Idempotency-Key`, same convention `RewardsController.reportSteps` already uses.
Confirmed by direct read of `ShoppingMissionController.kt` (2 endpoints, 2 real
`ApiError`-shaped codes).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` (base path) | — | `{success, missions: [...], spinOutcomes: [...]}` | `spinOutcomes` states the real odds up front (item 248 discipline), not just discovered after a spin |
| POST | `/missions/{type}/complete` | — | `{success, type, amountEarned, newAccountBalance}` | A real, deliberately-noted inconsistency: an unrecognized `type` returns `404 {"success": false, "error": "Unknown mission type"}`, NOT the shared `ApiError{code, message}` shape every other error on this page uses |

Errors (both real `@ExceptionHandler`s): `409 MISSION_ALREADY_COMPLETED`,
`404 ACCOUNT_NOT_FOUND`.

## Notifications — `/api/v1/notifications`

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` | — | `{success, notifications: [...], unreadCount}` | Real bug fixed 2026-07-13: previously read `Authentication.name` as the userId, which silently resolved to the stringified `CurrentUser` object rather than the real id, so this always returned zero results regardless of how many notifications actually existed for the caller — fixed to `@AuthenticationPrincipal CurrentUser`, matching every other controller |
| POST | `/{id}/read` | — | `{success}` | `id` may be `"all"`. Real ownership check — a notification only flips to read if it belongs to the caller |

As of 2026-07-13, real budget-threshold alerts (`BUDGET_NEAR`/`BUDGET_OVER`, see `## Wallet`
below) are the only thing in this backend that actually writes to this table outside of demo
seed data.

## Device Tokens — `/api/v1/notifications/device-tokens`

**Added 2026-09-05, same slice.** Real push-notification device-token registration.
Confirmed by direct read of `DeviceTokenController.kt` (2 endpoints, no
`@ExceptionHandler`s of its own).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{platform, token}` | `201 {success, deviceToken: {...}}` | Not `Idempotency-Key`-protected — a real client-generated `token` is already its own natural idempotency key (a unique DB constraint on `token` makes a repeat call a real no-op upsert, never a duplicate row). Re-registering an existing token under a different caller overwrites `userId` — a deliberate device-handoff behavior, not a bug |
| DELETE | `/{token}` | — | `{success}` | Silently no-ops if the token doesn't exist or belongs to a different user — never leaks which |

No documented error codes — every real failure mode here degrades to a silent no-op by
design rather than a thrown exception.

## Maps — `/api/v1/maps`

**Added 2026-09-05** (twenty-first documentation slice — itunda's self-hosted maps
stack: search, directions, place details, bookmarks with Naver Map-style public
folders, and Kakao Map-style live "Friend Location" sharing). Confirmed by direct
read of `MapsController.kt` (26 endpoints, 18 `@ExceptionHandler`s, 17 unique codes —
`InvalidLiveLocationCoordinateException` and `InvalidMapsCoordinateException` both
map to `INVALID_COORDINATES`).

### Search, directions, places

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/places/{merchantId}` | — | `{success, place: {...}}` | Real consolidated place-detail endpoint |
| GET | `/search?q` | — | `{success, results: [...]}` | |
| GET | `/weather` | — | `{success, weather}` | Real Kigali weather chip; `weather: null` (not an error) when genuinely unavailable |
| GET | `/reverse?lat&lng` | — | `{success, placeName}` | |
| GET | `/directions?fromLat&fromLng&toLat&toLng&mode` | — | `{success, route: {...}}` | `mode` defaults to `DRIVING`; an unrecognized value real-400s via Spring's own enum-conversion failure, not a silent fallback |
| GET | `/directions/transit?fromLat&fromLng&toLat&toLng` | — | `{success, journeys: [...]}` | Real Kigali GTFS-based transit journeys; an empty list (never an error) means no direct transit option was found |
| POST | `/directions/itinerary` | `{waypoints: [{latitude, longitude}], mode?}` | `{success, route: {...}}` | A deliberately bounded 2–7 stop itinerary, POST rather than encoding an ordered array into query params |
| GET | `/directions/alternatives?fromLat&fromLng&toLat&toLng&mode` | — | `{success, routes: [...]}` | Separate from `/directions` since that endpoint's single-`route` response shape is already depended on unchanged by every existing caller |
| GET | `/categories` | — | `{success, categories: [...]}` | |
| GET | `/nearby?category&lat&lng&radiusKm` | — | `{success, places: [...]}` | `radiusKm` defaults to 2.0 |
| GET | `/around-me?lat&lng&radiusKm` | — | `{success, places: [...]}` | Real "Smart Around"-style default state — 2 of Naver Map's real 5 sections, an honest scope, not a fabricated 5-for-5 |
| GET | `/trending?days&limit` | — | `{success, places: [...]}` | `days` defaults to 7, `limit` to 10 |

### Bookmarks

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/bookmarks` | `{displayName, latitude, longitude, folderName?, color?}` | `{success, bookmark: {...}}` | |
| PATCH | `/bookmarks?lat&lng` | `{folderName, color}` | `{success, bookmark: {...}}` | Real "move to folder" — keyed by `(lat, lng)` query params, the same real key `DELETE /bookmarks` already uses |
| DELETE | `/bookmarks?lat&lng` | — | `{success}` | |
| GET | `/bookmarks` | — | `{success, bookmarks: [...]}` | |
| PATCH | `/bookmarks/folder-visibility` | `{folderName, isPublic}` | `{success, updatedCount}` | Real Naver Map-style public/private folder |
| GET | `/shared/{userId}/{folderName}` | — | `{success, bookmarks: [...]}` | Deliberately unauthenticated — `permitAll`'d under this one specific sub-path so the rest of `/api/v1/maps/**` stays gated |
| POST | `/shared/{userId}/{folderName}/subscribe` | — | `{success, copiedCount}` | Real Kakao Map-style 구독 (subscribe) — authenticated, since unlike viewing a share link this writes real bookmark rows into the caller's own account |

### Live location sharing

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/location-share` | `{recipientPhoneNumber, durationHours?}` | `{success, share: {...}}` | Real Kakao Map 친구위치 (Friend Location). `durationHours` defaults to 1 |
| POST | `/location-share/{id}/update-location` | `{latitude, longitude}` | `{success, updatedShareCount}` | `id` is accepted for shape symmetry, but one push actually fans out to every one of the caller's active shares at once, not just this one |
| POST | `/location-share/{id}/extend` | `{additionalHours?}` | `{success, share: {...}}` | `additionalHours` defaults to 1 |
| POST | `/location-share/{id}/stop` | — | `{success}` | |
| GET | `/location-share/mine` | — | `{success, shares: [...]}` | Caller's own active shares as sharer |
| GET | `/location-share/shared-with-me` | — | `{success, shares: [...]}` | |
| GET | `/location-share/{id}` | — | `{success, share: {...}}` | Recipient-side poll — periodically refreshed by the client, not a persistent push channel |

### Errors (complete — all 18 `@ExceptionHandler`s in `MapsController.kt`, 17 unique codes)

`404 LOCATION_SHARE_NOT_FOUND`, `400 SELF_LOCATION_SHARE_NOT_ALLOWED`,
`404 LOCATION_SHARE_RECIPIENT_NOT_FOUND`, `400 TOO_MANY_ACTIVE_LOCATION_SHARES`,
`400 INVALID_LOCATION_SHARE_DURATION`, `410 LOCATION_SHARE_ENDED`,
`400 INVALID_COORDINATES` (shared by the live-location and general-maps coordinate
validators), `404 MERCHANT_NOT_FOUND`, `400 INVALID_ITINERARY`,
`400 INVALID_CATEGORY`, `400 INVALID_BOOKMARK_NAME`, `400 INVALID_BOOKMARK_FOLDER`,
`400 INVALID_BOOKMARK_COLOR`, `404 BOOKMARK_NOT_FOUND`, `404 ROUTE_NOT_FOUND`,
`400 INVALID_TRAVEL_MODE` (a real `MethodArgumentTypeMismatchException` — an
unrecognized `mode` value fails Spring's own enum conversion before the controller
method body runs, mapped to this backend's own consistent `ApiError` shape rather
than Spring's default generic error body), `429 RATE_LIMITED`.

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

## Messaging (1:1 chat) — `/api/v1/messages`

**Added 2026-09-05** (eighth documentation slice — the largest single gap closed so
far: itunda Talk's core 1:1 chat, previously entirely undocumented despite being the
foundation the Gifts/Gift-Vouchers/Emoticons sections above already reference by
`conversationId`). Real KakaoTalk-parity messaging — threads, reactions, pinning,
forwarding, search, presence, and per-conversation quiet/archive/pin-to-top/favorite
preferences. Confirmed by direct read of `MessagingController.kt` (25 endpoints, 18
`@ExceptionHandler`s, 15 unique codes — `EMPTY_MESSAGE`/`MESSAGE_NOT_FOUND`/
`MESSAGE_TOO_LONG` are each mapped by 2 handlers, once for this controller's own
1:1-message exceptions and once for the group-message exceptions that can surface here
when forwarding INTO a group).

### Conversations

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/conversations` | `{phoneNumber?, otherUserId?}` | `{success, conversation: {...}}` | Exactly one of the two must be set; `phoneNumber` is the real human-friendly entry point |
| GET | `/conversations?archived` | — | `{success, conversations: [...], ...pageMeta}` | |
| GET | `/contacts` | — | `{success, contacts: [...]}` | Talk-specific contact list, distinct from `/api/v1/contacts` above |
| GET | `/contacts/birthdays-today` | — | `{success, contacts: [...]}` | Real KakaoTalk "오늘의 생일" (Today's Birthday) |
| POST | `/conversations/{conversationId}/block` | — | `{success}` | |
| DELETE | `/conversations/{conversationId}/block` | — | `{success}` | |
| POST | `/conversations/{conversationId}/quiet` | `{quiet}` | `{success, quiet}` | |
| GET | `/conversations/{conversationId}/quiet` | — | `{success, quiet}` | |
| POST | `/conversations/{conversationId}/archive` | `{archived}` | `{success, archived}` | Real recoverable archive |
| GET | `/conversations/{conversationId}/archive` | — | `{success, archived}` | |
| POST | `/conversations/{conversationId}/pin-to-top` | `{pinned}` | `{success, pinned}` | Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) — distinct from pinning a MESSAGE below |
| GET | `/conversations/{conversationId}/pin-to-top` | — | `{success, pinned}` | |
| POST | `/conversations/{conversationId}/favorite` | `{favorite}` | `{success, favorite}` | |
| GET | `/conversations/{conversationId}/favorite` | — | `{success, favorite}` | |
| GET | `/presence?userIds` | — | `{success, presence}` | Real online/offline presence for any set of user ids, not just conversation partners |

### Messages

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/conversations/{conversationId}/messages` | — | `{success, messages: [...], ...pageMeta}` | Each message enriched with `reactions`, `replyCount`, `imageUrl`, `emoticonId`, `forwardedFromMessageId`, `forwardedFromType` — a deleted message's `body` is replaced with `"This message was deleted"` server-side |
| GET | `/conversations/{conversationId}/messages/{messageId}/thread` | — | `{success, messages: [...]}` | Root message first, then every direct reply oldest-first |
| GET | `/conversations/{conversationId}/messages/search?query` | — | `{success, messages: [...], ...pageMeta}` | |
| POST | `/conversations/{conversationId}/messages` | `{body, replyToMessageId?, imageUrl?}` | `201 {success, message: {...}}` | |
| DELETE | `/conversations/{conversationId}/messages/{messageId}` | — | `{success}` | |
| POST | `/messages/{messageId}/reactions` | `{emoji}` | `{success, reactions}` | Real toggle — tapping an already-active reaction removes it, not a separate add/remove pair |
| POST | `/messages/{messageId}/forward` | `{destinationType: "DIRECT" \| "GROUP", destinationId}` | `201 {success, message: {...}, destinationType}` | |
| POST | `/conversations/{conversationId}/pin/{messageId}` | — | `{success}` | Pins a MESSAGE inside the room |
| DELETE | `/conversations/{conversationId}/pin` | — | `{success}` | |
| GET | `/conversations/{conversationId}/pin` | — | `{success, message}` | |

### Errors (complete — all 18 `@ExceptionHandler`s in `MessagingController.kt`, 15 unique codes)

`404 RECIPIENT_NOT_FOUND`, `400 RECIPIENT_REQUIRED`, `400 SELF_CONVERSATION_NOT_ALLOWED`,
`404 CONVERSATION_NOT_FOUND`, `400 EMPTY_MESSAGE`, `400 MESSAGE_TOO_LONG`,
`400 INVALID_MESSAGE_IMAGE`, `429 RATE_LIMITED`, `404 MESSAGE_NOT_FOUND`,
`400 INVALID_REACTION`, `403 CONVERSATION_BLOCKED`, `400 INVALID_MESSAGE_SEARCH`,
`403 MESSAGE_DELETE_FORBIDDEN`, `400 INVALID_FORWARD_DESTINATION`,
`404 GROUP_NOT_FOUND` (only surfaces when forwarding a 1:1 message INTO a group and the
target group doesn't exist — the 3 codes above it are similarly shared with
`GroupMessagingController`'s own exceptions for the same reason).

## Group Messaging — `/api/v1/messages/groups`

**Added 2026-09-05, same slice.** Real KakaoTalk-parity group chat — named groups (by
user id or phone number), KakaoTalk 오픈채팅-style open groups joined by code,
announcements, polls, and the same reaction/pin/thread/forward feature set 1:1
messaging above has. Confirmed by direct read of `GroupMessagingController.kt` (23
endpoints, 26 `@ExceptionHandler`s, 23 unique codes — same 3-code sharing pattern with
`MessagingController` as above, in reverse: `EMPTY_MESSAGE`/`MESSAGE_NOT_FOUND`/
`MESSAGE_TOO_LONG` each have a group-specific handler AND a 1:1-message handler for
when a GROUP message is forwarded INTO a 1:1 conversation).

### Groups

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{name, memberUserIds?, memberPhoneNumbers?}` | `201 {success, group: {...}}` | `memberPhoneNumbers` is the real human-friendly entry point; takes precedence over `memberUserIds` if both are given |
| POST | `/open` | `{name}` | `201 {success, group: {...}}` | Real KakaoTalk 오픈채팅-style open group — anyone with the join code can join |
| POST | `/join` | `{joinCode}` | `{success, group: {...}}` | |
| GET | `` (base path) | — | `{success, groups: [...], ...pageMeta}` | Caller's own groups |
| GET | `/{groupId}/members` | — | `{success, members: [...]}` | Real resolved display names, not just raw user ids |
| POST | `/{groupId}/members` | `{userId}` | `{success, group: {...}}` | |
| DELETE | `/{groupId}/members/me` | — | `{success}` | Leave the group |
| POST | `/{groupId}/photo` | `{photoUrl}` | `{success, group: {...}}` | |
| POST | `/{groupId}/description` | `{description}` | `{success, group: {...}}` | |

### Messages, announcements, polls

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/{groupId}/messages` | — | `{success, messages: [...], ...pageMeta}` | Each message additionally enriched with `unreadCount` (real Kakao-style per-message read-receipt countdown) and `mentionedUserIds`, beyond the same fields 1:1 messages carry |
| GET | `/{groupId}/messages/{messageId}/thread` | — | `{success, messages: [...]}` | |
| POST | `/{groupId}/messages` | `{body, replyToMessageId?, imageUrl?}` | `201 {success, message: {...}}` | |
| DELETE | `/{groupId}/messages/{messageId}` | — | `{success}` | |
| POST | `/messages/{groupMessageId}/reactions` | `{emoji}` | `{success, reactions}` | |
| POST | `/messages/{messageId}/forward` | `{destinationType: "DIRECT" \| "GROUP", destinationId}` | `201 {success, message: {...}, destinationType}` | |
| POST | `/{groupId}/pin/{messageId}` | — | `{success}` | |
| DELETE | `/{groupId}/pin` | — | `{success}` | |
| GET | `/{groupId}/pin` | — | `{success, message}` | |
| POST | `/{groupId}/announcement` | `{body}` | `201 {success, announcement: {...}}` | One active announcement at a time — posting a new one replaces the old |
| GET | `/{groupId}/announcement` | — | `{success, announcement}` | |
| POST | `/{groupId}/polls` | `{question, options, allowMultiple?, closesAt?}` | `201 {success, poll: {...}}` | |
| GET | `/{groupId}/polls` | — | `{success, polls: [...]}` | |
| POST | `/{groupId}/polls/{pollId}/vote` | `{optionId}` | `{success, poll: {...}}` | |

### Errors (complete — all 26 `@ExceptionHandler`s in `GroupMessagingController.kt`, 23 unique codes)

`404 GROUP_NOT_FOUND`, `400 GROUP_NAME_REQUIRED`, `400 GROUP_NAME_TOO_LONG`,
`400 GROUP_PHOTO_URL_TOO_LONG`, `400 GROUP_DESCRIPTION_TOO_LONG`,
`400 GROUP_NEEDS_MORE_MEMBERS`, `404 MEMBER_NOT_FOUND`, `409 ALREADY_MEMBER`,
`404 INVALID_GROUP_JOIN_CODE`, `400 EMPTY_MESSAGE`, `400 MESSAGE_TOO_LONG`,
`400 INVALID_MESSAGE_IMAGE`, `429 RATE_LIMITED`, `404 MESSAGE_NOT_FOUND`,
`403 MESSAGE_DELETE_FORBIDDEN`, `400 INVALID_REACTION`,
`400 INVALID_FORWARD_DESTINATION`, `404 CONVERSATION_NOT_FOUND`,
`403 CONVERSATION_BLOCKED` (the last 4 shared with `MessagingController`'s own
exceptions — same reasoning as above, in reverse), `400 INVALID_ANNOUNCEMENT`,
`400 INVALID_POLL`, `404 POLL_NOT_FOUND`, `404 POLL_OPTION_NOT_FOUND`.

Reused as the underlying implementation of both `POST /api/v1/split-bills/direct/
{otherUserId}` (via `getOrCreateDirectSplitGroup`) and `POST /api/v1/community/posts/
{postId}/finalize-group-buy` — see the Split Bills and Community sections above.

## AI Chat — `/api/v1/talk/ai-chat`

**Added 2026-09-05, same slice.** Real AI chatbot channel inside Talk, a third
conversation type alongside 1:1 and group chat above. Confirmed by direct read of
`AiChatController.kt` (2 endpoints, 1 real `ApiError`-shaped code).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/messages` | `{text}` | `201 {success, message: {...}, reply: {...}}` | |
| GET | `/messages` | — | `{success, messages: [...], ...pageMeta}` | |

Errors: `429 RATE_LIMITED`, plus a real, deliberately-noted inconsistency — a busy/
overloaded AI backend returns `429 {"success": false, "reason": "busy"}`, NOT the
shared `ApiError{code, message}` shape every other error on this page uses. A client
checking for a `code` field on this specific 429 will find none.

## Service Channel — `/api/v1/talk/service-channel`

**Added 2026-09-05, same slice.** Real itunda service channel — a read-only system
message thread inside Talk (marking a bubble read reuses the existing
`POST /api/v1/notifications/{id}/read` endpoint unchanged, not a new one).
Confirmed by direct read of `ServiceChannelController.kt` (1 endpoint, no
`@ExceptionHandler`s of its own).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` (base path) | — | `{success, bubbles: [...], ...pageMeta}` | |

## Calls — `/api/v1/calls`

**Added 2026-09-05, same slice.** Real 1:1 voice/video calling inside Talk, WebRTC-
based (ephemeral TURN credentials, never a static long-lived secret shipped to a
client). Confirmed by direct read of `CallController.kt` (5 endpoints, 5
`@ExceptionHandler`s, 4 unique codes — `CallNotFoundException` and
`CallNotParticipantException` both map to `CALL_NOT_FOUND`, the same IDOR-safe
"don't let a non-participant distinguish exists-but-not-mine from doesn't-exist"
pattern used elsewhere on this page).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{conversationId, callType}` | `201 {success, call: {...}}` | |
| POST | `/{callId}/answer` | — | `{success, call: {...}}` | |
| POST | `/{callId}/end` | `{reason}` | `{success, call: {...}}` | |
| GET | `/history` | — | `{success, calls: [...], ...pageMeta}` | |
| GET | `/turn-credentials` | — | `{success, credentials: {...}}` | Real ephemeral TURN credentials for WebRTC NAT traversal |

Errors (all 5 real `@ExceptionHandler`s, 4 unique codes): `404 CALL_NOT_FOUND`
(shared by 2 exceptions), `409 CALL_ALREADY_ENDED`, `404 CONVERSATION_NOT_FOUND`,
`429 RATE_LIMITED`.

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

## Community — `/api/v1/community`

**Added 2026-09-05** (fifth documentation slice — part of the standing "remaining
undocumented controllers" follow-up). Real 당근마켓 동네생활 (Karrot "neighborhood life")
community board: posts, 당근모임-style meetups with recurring sessions and check-in,
and 같이사요 (group-buy) finalization into a real `SplitBill`. Confirmed by direct read
of `CommunityController.kt` (22 endpoints, 21 error codes).

### Posts

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/categories` | — | `{success, categories: [...]}` | |
| GET | `/topics` | — | `{success, topics: [...]}` | Real 동네생활 topic-chip filter row |
| POST | `/posts` | `{category, title, body, latitude?, longitude?, eventDate?, capacity?, topic?}` | `201 {success, post: {...}}` | `eventDate`/`capacity` are ignored unless `category == "meetup"` |
| GET | `/meetups/upcoming` | — | `{success, posts: [...], joinedCounts, ...pageMeta}` | Real 당근모임-style "upcoming meetups" browse across all neighborhoods |
| GET | `/posts?category&topic` | — | `{success, posts: [...], joinedCounts, ...pageMeta}` | |
| GET | `/posts/nearby?latitude&longitude&radiusKm` | — | `{success, posts: [...], joinedCounts, ...pageMeta}` | `radiusKm` defaults to 5.0 |
| GET | `/posts/my-neighborhood?category` | — | `{success, posts: [...], joinedCounts, ...pageMeta}` | Real hyperlocal "my neighborhood" browse |
| GET | `/posts/search?q` | — | `{success, posts: [...], joinedCounts, ...pageMeta}` | Real relevance-ranked search |
| GET | `/my-posts` | — | `{success, posts: [...], joinedCounts, ...pageMeta}` | |
| GET | `/posts/{postId}` | — | `{success, post: {...}, authorName, likedByMe}` | |
| DELETE | `/posts/{postId}` | — | `{success, post: {...}}` | |
| GET | `/posts/{postId}/comments` | — | `{success, comments: [...], ...pageMeta}` | Each comment enriched with `authorName` |
| POST | `/posts/{postId}/comments` | `{body}` | `201 {success, comment: {...}}` | |
| POST | `/notification-preference` | `{enabled}` | `{success, commentNotificationsEnabled}` | Real Karrot 동네생활 "새 댓글 알림 끄기" (turn off new-comment notifications) |
| GET | `/notification-preference` | — | `{success, commentNotificationsEnabled}` | |
| POST | `/posts/{postId}/like` | — | `{success, liked}` | Idempotent toggle |

Reporting a post moved to the unified `POST /api/v1/hood/reports` (`HoodReportController`,
not yet documented on this page) — this section deliberately has no separate report
endpoint of its own, per the controller's own doc comment on why the old path was retired.

### Meetups & group-buy

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/posts/{postId}/join` | — | `{success, groupId}` | Real 같이해요 (join-together) explicit 참여하기 tap |
| POST | `/posts/{postId}/sessions` | `{dates}` | `201 {success, sessions: [...]}` | Real 당근모임 recurring schedule — organizer only |
| GET | `/posts/{postId}/sessions` | — | `{success, sessions: [...]}` | |
| POST | `/sessions/{sessionId}/check-in` | — | `201 {success, attendance: {...}}` | |
| GET | `/sessions/{sessionId}/attendance` | — | `{success, attendance: [...]}` | Membership-checked — a non-member gets `404 SESSION_NOT_FOUND`, not `403`, so a stranger can't use the status code to distinguish "exists, you're not in it" from "doesn't exist" |
| POST | `/posts/{postId}/finalize-group-buy` | `{totalAmount, description}` (+ `Idempotency-Key`) | `201 {success, splitBill: {...}, participants: [...]}` | Real 당근마켓 같이사요 (Karrot "Let's Buy Together") — creates a real `SplitBill` via the same underlying service `SplitBillController.createSplitBill` uses; `Idempotency-Key` required so a retried request can't create a second real split bill for the same purchase |

### Errors (complete — all 21 `@ExceptionHandler`s in `CommunityController.kt`)

`400 COMMUNITY_MEETUP_JOIN_INVALID`, `404 COMMUNITY_POST_NOT_FOUND`,
`400 INVALID_COMMUNITY_POST`, `400 INVALID_COMMUNITY_COMMENT`,
`400 INVALID_COORDINATES`, `429 RATE_LIMITED`, `400 NEIGHBORHOOD_NOT_SET`,
`400 INVALID_MEETUP`, `409 MEETUP_FULL`, `400 INVALID_MEETUP_SCHEDULE`,
`404 MEETUP_SESSION_NOT_FOUND`, `409 ALREADY_CHECKED_IN`, `404 SESSION_NOT_FOUND`
(the membership-gate IDOR fix noted above), `400 INVALID_GROUP_BUY_FINALIZE`,
`400 INVALID_AMOUNT` and `400 DESCRIPTION_REQUIRED` (both surfaced from the shared
`SplitBillService.createSplitBill` this endpoint calls into), `409
IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `400 NEEDS_PARTICIPANTS`,
`400 PARTICIPANT_NOT_GROUP_MEMBER` (the latter two also from the shared split-bill
service).

## Neighborhood Reviews — `/api/v1/community/neighborhoods`

**Added 2026-09-05, same slice.** Real 살아본 후기 (Karrot "lived here" reviews) — a
separate, small controller, not folded into `CommunityController` above. Confirmed by
direct read of `NeighborhoodReviewController.kt` (2 endpoints, 3 error codes).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/{neighborhood}/reviews` | — | `{success, reviews: [...]}` | Public — no auth required |
| POST | `/{neighborhood}/reviews` | `{residencyYears?, body}` | `201 {success, review: {...}}` | One review per user per neighborhood |

Errors (all 3 real `@ExceptionHandler`s): `400 INVALID_NEIGHBORHOOD_REVIEW`,
`409 NEIGHBORHOOD_REVIEW_ALREADY_SUBMITTED`, `429 RATE_LIMITED`.

## Jobs — `/api/v1/jobs`

**Added 2026-09-05, same slice.** Real 당근알바-style local job board — structured
applications, post-transaction reviews (shared `HoodReviewService`, asymmetric
public/private visibility), and a real job-post wishlist. Confirmed by direct read of
`JobPostController.kt` (21 endpoints, 18 `@ExceptionHandler`s, 16 unique codes —
`JOB_POST_NOT_FOUND` is shared by 3 distinct exceptions: the post itself not existing,
a stale favorite pointing at a removed post, and a review-lookup miss).

### Posts

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/categories` | — | `{success, categories: [...]}` | |
| POST | `/posts` | `{category, title, description, payType, payAmount, latitude?, longitude?}` | `201 {success, post: {...}}` | |
| GET | `/posts?category` | — | `{success, posts: [...], trustScores, ...pageMeta}` | `trustScores` is a real Karrot-Score-style trust badge, batch-resolved per poster in one query |
| GET | `/posts/nearby?latitude&longitude&radiusKm` | — | `{success, posts: [...], trustScores, ...pageMeta}` | `radiusKm` defaults to 5.0 |
| GET | `/posts/my-neighborhood?category` | — | `{success, posts: [...], trustScores, ...pageMeta}` | |
| GET | `/posts/search?q` | — | `{success, posts: [...], trustScores, ...pageMeta}` | |
| GET | `/my-posts` | — | `{success, posts: [...], trustScores, ...pageMeta}` | |
| GET | `/my-worked-posts` | — | `{success, posts: [...], trustScores, ...pageMeta}` | Real "Jobs I did" history |
| GET | `/posts/{jobPostId}` | — | `{success, post: {...}, posterTrustScore}` | Public — no auth required |
| POST | `/posts/{jobPostId}/mark-filled` | `{workerPhoneNumber?}` | `{success, post: {...}}` | Notifies everyone who favorited this post that it's closed |
| DELETE | `/posts/{jobPostId}` | — | `{success, post: {...}}` | Same closure notification as `mark-filled` |
| POST | `/posts/{jobPostId}/contact-poster` | — | `{success, conversation: {...}}` | |

### Applications

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/posts/{jobPostId}/apply` | `{message}` | `201 {success, application: {...}}` | Real structured application, replacing an informal "just message the poster" flow |
| GET | `/posts/{jobPostId}/applications` | — | `{success, applications: [...], ...pageMeta}` | Poster-only |
| GET | `/my-applications` | — | `{success, applications: [...], ...pageMeta}` | Applicant's own applications |
| POST | `/applications/{applicationId}/respond` | `{accept}` | `{success, application: {...}, conversation: {...}}` | Accepting opens a real conversation (returned inline); declining does not |

### Reviews & favorites

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/posts/{jobPostId}/review` | `{goodPoints?, uncomfortablePoints?}` | `201 {success, review: {...}}` | Shared `HoodReviewService` — asymmetric public/private visibility, same as other Hood-transaction reviews |
| GET | `/posts/{jobPostId}/review` | — | `{success, reviews: [...]}` | |
| POST | `/posts/{jobPostId}/favorite` | — | `201 {success, favorite: {...}}` | |
| DELETE | `/posts/{jobPostId}/favorite` | — | `{success}` | |
| GET | `/posts/favorites` | — | `{success, favorites: [...], ...pageMeta}` | |

### Errors (complete — all 18 `@ExceptionHandler`s in `JobPostController.kt`)

`404 JOB_POST_NOT_FOUND` (shared by 3 exceptions — see note above), `400
INVALID_JOB_POST`, `409 JOB_POST_NOT_OPEN`, `400 OWN_JOB_POST`, `400
INVALID_COORDINATES`, `429 RATE_LIMITED`, `400 NEIGHBORHOOD_NOT_SET`, `404
WORKER_NOT_FOUND`, `409 REVIEW_TRANSACTION_NOT_COMPLETED`, `400
REVIEW_NO_COUNTERPARTY`, `404 REVIEW_NOT_PARTY` (IDOR fix, 2026-08-30 — was `403`,
which let a stranger with a real `transactionId` distinguish "exists, you weren't a
party" from "doesn't exist" by status code alone), `409 REVIEW_ALREADY_SUBMITTED`,
`400 INVALID_JOB_APPLICATION`, `409 JOB_APPLICATION_ALREADY_PENDING`, `404
JOB_APPLICATION_NOT_FOUND`, `409 JOB_APPLICATION_NOT_PENDING`.

## Résumé — `/api/v1/jobs/resume`

**Added 2026-09-05, same slice.** Real 이력서 (Karrot 당근알바-style résumé) builder —
a separate controller from job posts above, one résumé per user. Confirmed by direct
read of `ResumeController.kt` (9 endpoints, 2 error codes).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/strengths` | — | `{success, strengths: [...]}` | The fixed vocab the client's strengths picker renders from |
| GET | `` (base path) | — | `{success, resume, experiences: [...], educations: [...], certifications: [...], completionPercent}` | |
| PUT | `` (base path) | `{selfIntro?, strengths?, additionalInfo?}` | `{success, resume: {...}}` | Full-replace on the profile fields only — experience/education/certification entries are managed by their own endpoints below |
| POST | `/experience` | `{company, role, period, description?}` | `201 {success, experience: {...}}` | |
| DELETE | `/experience/{experienceId}` | — | `{success}` | |
| POST | `/education` | `{school, degree?, major?}` | `201 {success, education: {...}}` | |
| DELETE | `/education/{educationId}` | — | `{success}` | |
| POST | `/certification` | `{name, issuedDate?}` | `201 {success, certification: {...}}` | |
| DELETE | `/certification/{certificationId}` | — | `{success}` | |

Errors (both real `@ExceptionHandler`s): `400 INVALID_RESUME`, `404 RESUME_ENTRY_NOT_FOUND`.

## Knowledge — `/api/v1/knowledge`

**Added 2026-09-05** (ninth documentation slice). Real Naver 지식iN (Knowledge iN)-style
open-topic community Q&A — post a question, others answer, the asker adopts one
answer which counts toward the answerer's reputation. Confirmed by direct read of
`KnowledgeController.kt` (10 endpoints, 7 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/categories` | — | `{success, categories: [...]}` | |
| POST | `/questions` | `{category, title, body}` | `201 {success, question: {...}}` | |
| GET | `/questions?category` | — | `{success, questions: [...], ...pageMeta}` | |
| GET | `/questions/my-questions` | — | `{success, questions: [...], ...pageMeta}` | |
| GET | `/answers/my-answers` | — | `{success, answers: [...], ...pageMeta}` | |
| GET | `/reputation/me` | — | `{success, adoptedAnswerCount}` | |
| GET | `/questions/{questionId}` | — | `{success, question: {...}}` | |
| GET | `/questions/{questionId}/answers` | — | `{success, answers: [...]}` | |
| POST | `/questions/{questionId}/answers` | `{body}` | `201 {success, answer: {...}}` | |
| POST | `/questions/{questionId}/answers/{answerId}/adopt` | — | `{success, answer: {...}}` | Question-asker-only. One adopted answer per question |

Errors (all 7 real `@ExceptionHandler`s): `400 INVALID_KNOWLEDGE_QUESTION`,
`400 INVALID_KNOWLEDGE_ANSWER`, `404 KNOWLEDGE_QUESTION_NOT_FOUND`,
`404 KNOWLEDGE_ANSWER_NOT_FOUND`, `400 KNOWLEDGE_ANSWER_NOT_FOR_QUESTION`,
`409 KNOWLEDGE_QUESTION_ALREADY_HAS_ADOPTED_ANSWER`, `429 RATE_LIMITED`.

## Family Link — `/api/v1/family`

**Added 2026-09-05** (sixth documentation slice). Real Toss 유스 (Toss Youth)-style
guardian-child account link — a guardian invites a child by phone number, the child
accepts, and the guardian gets a real per-day spend-limit control over the child's
account. Confirmed by direct read of `FamilyLinkController.kt` (8 endpoints, 9 error
codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/invite` | `{childPhoneNumber}` | `201 {success, link: {...}}` | |
| GET | `/invites` | — | `{success, invites: [...]}` | Invites the caller has received AS a child |
| POST | `/invites/{id}/respond` | `{accept}` | `{success, link: {...}}` | |
| GET | `/children` | — | `{success, children: [...]}` | Guardian's own linked children |
| GET | `/guardians` | — | `{success, guardians: [...]}` | Child's own linked guardians |
| GET | `/children/{childUserId}/overview` | — | `{success, overview: {...}}` | Guardian-only, ownership-checked |
| POST | `/children/{childUserId}/spend-limit` | `{dailySpendLimit}` | `{success, link: {...}}` | Not money-moving itself — no `Idempotency-Key`. `dailySpendLimit: null` removes the limit |
| POST | `/links/{id}/revoke` | — | `{success, link: {...}}` | Either party can revoke |

Errors (all 9 real `@ExceptionHandler`s): `404 FAMILY_LINK_NOT_FOUND`,
`404 FAMILY_LINK_ACCOUNT_NOT_FOUND`, `400 SELF_LINK_NOT_ALLOWED`,
`409 FAMILY_LINK_ALREADY_EXISTS`, `409 FAMILY_LINK_NOT_PENDING`,
`409 FAMILY_LINK_NOT_ACTIVE`, `403 FAMILY_LINK_UNAUTHORIZED`,
`400 INVALID_SPEND_LIMIT`, `429 RATE_LIMITED`.

## Gifts — `/api/v1/gifts`

**Added 2026-09-05, same slice.** Real KakaoTalk-style 선물하기 money gift — send a
real ledger transfer as a "gift" a recipient must actively claim (unclaimed gifts
expire and refund automatically; see `GiftService`'s own doc comment). Confirmed by
direct read of `GiftController.kt` (5 endpoints, 13 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{recipientPhoneNumber, amount, note?, theme?}` (+ `Idempotency-Key`) | `201 {success, gift: {...}}` | |
| POST | `/conversations/{conversationId}` | `{amount, note?, theme?}` (+ `Idempotency-Key`) | `201 {success, gift: {...}}` | Real chat-embedded gift — recipient resolved as "whichever participant isn't me", no phone number re-entry |
| GET | `/{id}` | — | `{success, gift: {...}}` | |
| GET | `/conversations/{conversationId}` | — | `{success, gifts: [...]}` | |
| POST | `/{id}/claim` | — (+ `Idempotency-Key`) | `{success, gift: {...}}` | |

Errors (all 13 real `@ExceptionHandler`s): `404 GIFT_NOT_FOUND`,
`409 GIFT_ALREADY_RESOLVED`, `409 GIFT_EXPIRED`, `403 NOT_GIFT_RECIPIENT`,
`400 SELF_GIFT_NOT_ALLOWED`, `404 ACCOUNT_NOT_FOUND`, `404 GIFT_RECIPIENT_NOT_FOUND`,
`400 INVALID_AMOUNT`, `422 INSUFFICIENT_FUNDS`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`429 RATE_LIMITED`.

## Gift Vouchers — `/api/v1/gift-vouchers`

**Added 2026-09-05, same slice.** Real KakaoTalk-style 선물하기 기프티콘 (mobile gift
voucher) — buy a specific merchant product (or a cash amount) as a redeemable voucher
for another user; redemption is merchant-authenticated, not recipient self-serve.
Confirmed by direct read of `GiftVoucherController.kt` (6 endpoints, 17 error codes,
all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/process-expiry-reminders` | — | `{success, processed}` | ADMIN only — manual trigger for the expiry-reminder scheduler, fires system-wide |
| POST | `` (base path) | `{recipientPhoneNumber, merchantId, merchantProductId?, amount?}` (+ `Idempotency-Key`) | `201 {success, voucher: {...}}` | |
| GET | `/{id}` | — | `{success, voucher: {...}}` | |
| GET | `/conversations/{conversationId}` | — | `{success, vouchers: [...]}` | |
| POST | `/{id}/extend` | — | `{success, voucher: {...}}` | Not money-moving, no `Idempotency-Key` — a legitimate retry after this call already succeeded hits `GIFT_VOUCHER_ALREADY_EXTENDED` and shows a scary-looking error even though the extension already happened; a known, deliberately deferred gap (see `feedback_toss_error_handling.md`'s AlreadyX audit) |
| POST | `/{id}/redeem` | — (+ `Idempotency-Key`) | `{success, voucher: {...}}` | Merchant-authenticated, not the recipient |

Errors (all 17 real `@ExceptionHandler`s): `404 GIFT_VOUCHER_NOT_FOUND`,
`409 GIFT_VOUCHER_NOT_ACTIVE`, `409 GIFT_VOUCHER_EXPIRED`, `400 SELF_GIFT_NOT_ALLOWED`,
`404 ACCOUNT_NOT_FOUND`, `404 GIFT_VOUCHER_RECIPIENT_NOT_FOUND`, `400 INVALID_AMOUNT`,
`404 MERCHANT_NOT_FOUND`, `404 PRODUCT_NOT_FOUND`, `409 PRODUCT_OUT_OF_STOCK`,
`422 GIFT_VOUCHER_NOT_EXTENDABLE`, `409 GIFT_VOUCHER_ALREADY_EXTENDED`,
`422 INSUFFICIENT_FUNDS`, `409 IDEMPOTENCY_KEY_CONFLICT`,
`409 IDEMPOTENT_REQUEST_PROCESSING`, `400 IDEMPOTENCY_KEY_REQUIRED`,
`429 RATE_LIMITED`.

## Emoticons — `/api/v1/emoticons`

**Added 2026-09-05, same slice.** Real KakaoTalk Emoticon Store — buy or gift a pack,
then send an owned emoticon into a 1:1 or group chat. Confirmed by direct read of
`EmoticonController.kt` (7 endpoints, 10 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/packs` | — | `{success, packs: [...]}` | Public catalog — no auth required |
| GET | `/packs/{packId}` | — | `{success, emoticons: [...]}` | |
| GET | `/packs/owned` | — | `{success, packs: [...]}` | |
| POST | `/packs/{packId}/purchase` | — | `201 {success, ownedPack: {...}}` | Not money-moving-idempotent-protected — a genuine gap flagged separately (`EMOTICON_PACK_ALREADY_OWNED` already resolves forward on the reachable clients, see `feedback_toss_error_handling.md`) |
| POST | `/packs/{packId}/gift` | `{recipientPhoneNumber}` | `201 {success, giftedPack: {...}}` | Recipient identified by phone number, same convention `P2pController.send` uses |
| POST | `/conversations/{conversationId}/send` | `{emoticonId}` | `201 {success, message: {...}}` | |
| POST | `/groups/{groupId}/send` | `{emoticonId}` | `201 {success, message: {...}}` | |

Errors (all 10 real `@ExceptionHandler`s): `404 EMOTICON_PACK_NOT_FOUND`,
`404 EMOTICON_NOT_FOUND`, `409 EMOTICON_PACK_ALREADY_OWNED`,
`403 EMOTICON_PACK_NOT_OWNED`, `404 ACCOUNT_NOT_FOUND`,
`404 GIFT_RECIPIENT_NOT_FOUND`, `400 GIFT_TO_SELF`, `422 INSUFFICIENT_FUNDS`,
`403 ACCOUNT_FROZEN`, `429 RATE_LIMITED`.

## Split Bills — `/api/v1/split-bills`

**Added 2026-09-05, same slice.** Real KakaoPay-style 정산하기 (settlement/split-bill),
chat-embedded in an existing group conversation or a fixed 1:1 direct split (which
resolves or creates a hidden 2-person group behind the scenes, then runs the identical
logic the named-group path uses). Includes a real KakaoPay 사다리타기 (ladder-game)
random-split mode. Confirmed by direct read of `SplitBillController.kt` (8 endpoints,
19 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/conversations/{groupConversationId}` | `{totalAmount, description, participantUserIds, mode?, ladderVarianceLevel?}` (+ `Idempotency-Key`) | `201 {success, splitBill: {...}, participants: [...]}` | `mode` defaults to `EVEN`; `ladderVarianceLevel` only matters for the ladder-game mode |
| POST | `/direct/{otherUserId}` | `{totalAmount, description, mode?, ladderVarianceLevel?}` (+ `Idempotency-Key`) | `201 {success, splitBill: {...}, participants: [...]}` | No `participantUserIds` — the other person is fixed by the path variable |
| GET | `/direct/{otherUserId}` | — | `{success, splitBills: [...]}` | Read-only counterpart to the POST above — never creates a hidden group; empty list if the two people have never split a bill |
| GET | `/{id}` | — | `{success, splitBill: {...}, participants: [...]}` | |
| GET | `/conversations/{groupConversationId}` | — | `{success, splitBills: [...]}` | |
| POST | `/{id}/receipt` | `{imageUrl}` | `{success, splitBill: {...}}` | Not money-moving, no `Idempotency-Key` |
| POST | `/{id}/next-round` | — | `{success, splitBill: {...}}` | Real up-to-5 settlement-round escalation. Not money-moving, no `Idempotency-Key` |
| POST | `/{id}/pay` | — (+ `Idempotency-Key`) | `{success, participant: {...}}` | Pays the caller's own share |

### Errors (complete — all 19 `@ExceptionHandler`s in `SplitBillController.kt`)

`400 GROUP_NEEDS_MORE_MEMBERS`, `404 MEMBER_NOT_FOUND` (both surfaced from the shared
`GroupMessagingService.getOrCreateDirectSplitGroup` the direct-split path calls into),
`404 SPLIT_BILL_NOT_FOUND`, `400 INVALID_AMOUNT`, `400 DESCRIPTION_REQUIRED`,
`400 SPLIT_BILL_NEEDS_PARTICIPANTS`, `400 INVALID_LADDER_VARIANCE_LEVEL`,
`400 INVALID_RECEIPT_URL`, `400 PARTICIPANT_NOT_GROUP_MEMBER`,
`409 SPLIT_BILL_SHARE_ALREADY_PAID`, `404 ACCOUNT_NOT_FOUND`,
`409 SPLIT_BILL_ALREADY_SETTLED`, `409 SPLIT_BILL_NO_PENDING_PARTICIPANTS`,
`409 SPLIT_BILL_MAX_ROUNDS_REACHED`, `422 INSUFFICIENT_FUNDS`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 IDEMPOTENCY_KEY_REQUIRED`, `429 RATE_LIMITED`.

Reused as the underlying implementation of `POST /api/v1/community/posts/{postId}
/finalize-group-buy` (see the Community section above) — that endpoint's own
`INVALID_AMOUNT`/`DESCRIPTION_REQUIRED`/`SPLIT_BILL_NEEDS_PARTICIPANTS`/
`PARTICIPANT_NOT_GROUP_MEMBER` handlers exist because this same service can throw them.

## Partners — `/api/v1/partners`

**Added 2026-09-05** (twentieth documentation slice — itunda's third-party developer
platform: register, get an API key, submit a mini-app for review). Mapped outside
`/api/v1/system/**` since a partner authenticates with its own real API key (checked
inside `PartnerService.resolvePartner` for every endpoint but `/register`), not an
itunda-user JWT — `permitAll` at the Spring Security layer. Confirmed by direct read
of `PartnerController.kt` (4 endpoints, 9 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/register` | `{companyName, contactEmail}` | `201 {success, partner: {...}, apiKey}` | `apiKey` is shown exactly once |
| POST | `/mini-apps` | `{name, description, iconUrl?, bundleUrl, permissions}` header `X-Api-Key` | `201 {success, miniApp: {...}}` | |
| GET | `/mini-apps` | — header `X-Api-Key` | `{success, miniApps: [...]}` | Partner's own submitted mini-apps |
| GET | `/permissions` | — | `{success, permissions: [...]}` | The real, fixed allowed-permission vocab a submission's own `permissions` must be drawn from |

Errors (all 9 real `@ExceptionHandler`s): `409 PARTNER_EMAIL_ALREADY_REGISTERED`,
`400 INVALID_EMAIL`, `401 INVALID_API_KEY`, `403 PARTNER_SUSPENDED`,
`400 INVALID_PERMISSION_SCOPE`, `400 INVALID_MINI_APP_SUBMISSION`,
`401 API_KEY_REQUIRED`, `429 RATE_LIMITED`, `400 INVALID_DECISION_REASON` (surfaces
here even though the decide action itself lives on Partner Admin below, since both
share this controller's exception-handling scope).

## Partner Identity — `/api/v1/partners/identity`

**Added 2026-09-05, same slice.** The partner-facing side of "verify/sign in with
itunda" — a partner creates a verification request, hands the user a real
`itunda://verify/{requestId}` deep link, and polls for the result. Same
`X-Api-Key`-authenticated, `permitAll`-at-Spring-Security shape as Partners above.
Confirmed by direct read of `PartnerIdentityController.kt` (3 endpoints, 5 error
codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/requests` | — header `X-Api-Key` | `201 {success, requestId, verifyUrl, expiresAt}` | |
| GET | `/requests/{requestId}` | — header `X-Api-Key` | `{success, status}` or `{success, status, identity: {...}, signature}` | `identity`/`signature` are only ever present once a real user has approved — never returned for PENDING/DECLINED/EXPIRED |
| GET | `/public-key` | — | `{success, algorithm: "Ed25519", publicKey}` | Lets a partner verify `signature` above independently |

Errors (all 5 real `@ExceptionHandler`s): `404 VERIFICATION_REQUEST_NOT_FOUND`,
`401 INVALID_API_KEY`, `403 PARTNER_SUSPENDED`, `401 API_KEY_REQUIRED`,
`429 RATE_LIMITED`.

## Partner Review — `/api/v1/system/partners` (ADMIN role only)

**Added 2026-09-05, same slice.** The ADMIN-only review queue for partner mini-app
submissions — mapped under `/api/v1/system/partners` specifically so it inherits
`SecurityConfig`'s existing `hasRole("ADMIN")` rule on the system path prefix, same
convention `ComplianceController`'s own KYC/KYB queue already establishes. Confirmed
by direct read of `PartnerAdminController.kt` (2 endpoints, 2 error codes, all
unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/queue` | — | `{success, queue: [...], ...pageMeta}` | Pending mini-app submissions |
| POST | `/{miniAppId}/decide` | `{approve, reason?}` | `{success, miniApp: {...}}` | |

Errors: `404 PARTNER_MINI_APP_NOT_FOUND`, `409 PARTNER_MINI_APP_NOT_PENDING`.

## Mini-App Catalog — `/api/v1/mini-apps`

**Added 2026-09-05, same slice.** The real "app store" surface every itunda client
fetches to know which approved third-party mini-apps are available — normal
itunda-user JWT gate (no ADMIN role needed), distinct from both `/api/v1/partners/**`
(partner-authenticated) and `/api/v1/system/partners/**` (ADMIN-only review) above.
Confirmed by direct read of `MiniAppCatalogController.kt` (1 endpoint, no
`@ExceptionHandler`s of its own).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/catalog` | — | `{success, miniApps: [...], ...pageMeta}` | |

## Identity Verification — `/api/v1/identity/verification`

**Added 2026-09-05, same slice.** The real, informed-consent user-facing side of
"verify/sign in with itunda" — reached via the `itunda://verify/{requestId}` deep
link Partner Identity above hands to a partner. Normal itunda-user JWT gate.
Confirmed by direct read of `IdentityVerificationController.kt` (3 endpoints, 3
error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/{requestId}` | — | `{success, partnerName, status, expiresAt, requestedFields: [...]}` | `requestedFields` is a fixed v1 list (name, phone, ID-verification status, date of birth) — every request asks for the same full scope, not yet per-partner configurable |
| POST | `/{requestId}/approve` | — | `{success}` | |
| POST | `/{requestId}/decline` | — | `{success}` | |

Errors (all 3 real `@ExceptionHandler`s): `404 VERIFICATION_REQUEST_NOT_FOUND`,
`409 VERIFICATION_REQUEST_NOT_PENDING`, `404 USER_NOT_FOUND`.

## Agent Discovery — `/api/v1/agents`

**Added 2026-09-05, same slice** (the itunda cash-agent network — a real Kenyan
M-Pesa-agent-style cash-in/cash-out network, physical agents who convert cash to/from
wallet balance). Customer-facing "find a cash point near me". Confirmed by direct
read of `AgentDiscoveryController.kt` (1 endpoint, 1 error code).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/nearby?latitude&longitude&radiusKm` | — | `{success, agents: [...]}` | `radiusKm` defaults to 5 |

Errors: `400 INVALID_AGENT_SEARCH` (bare `IllegalArgumentException` fallback).

## Agent Operator — `/api/v1/agent`

**Added 2026-09-05, same slice.** Store-facing API for a real agent's assigned
operator — the operator's own JWT determines which agent they act for; no endpoint
accepts a caller-supplied agent id. Confirmed by direct read of
`AgentOperatorController.kt` (7 endpoints, 12 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/me` | — | `{success, operator: {...}}` | |
| POST | `/location` | `{latitude, longitude}` | `{success, agent: {...}}` | Real gap fixed 2026-08-16 — the customer-facing "nearby agents" feature already depended on this data, but no real agent had any way to actually report it |
| GET | `/till` | — | `{success, till: {...}}` | |
| GET | `/activity?limit` | — | `{success, activity: [...]}` | `limit` defaults to 30 |
| POST | `/cash-ins` | `{accountNumber, amount, receiptNumber}` (+ `Idempotency-Key`) | `{success, ...}` | A customer deposits cash with the agent, who credits the customer's wallet |
| POST | `/cash-outs` | `{accountNumber, amount, receiptNumber, authorizationCode}` (+ `Idempotency-Key`) | `{success, ...}` | A customer withdraws cash from the agent, who debits the customer's wallet. `authorizationCode` is the same real code `POST /api/v1/account/agent-withdrawal-authorizations` generates (see the Account section above) |
| POST | `/till-reconciliations` | `{countedCash}` (+ `Idempotency-Key`) | `201 {success, reconciliation: {...}}` | Real gap fixed 2026-09-05 — the highest-severity item in this backend's own Toss-error-handling audit: a lost response after a successful till count previously resubmitted here and hit `TILL_COUNT_ALREADY_SUBMITTED` on retry, showing the operator a confusing conflict for a count that had actually already succeeded |

Errors (all 12 real `@ExceptionHandler`s): `403 AGENT_OPERATOR_NOT_AUTHORIZED`,
`404 AGENT_NOT_FOUND`, `409 AGENT_SUSPENDED`, `409 CASH_RECEIPT_ALREADY_USED`,
`422 AGENT_DAILY_LIMIT_EXCEEDED`, `422 AGENT_INSUFFICIENT_CASH`,
`422 WITHDRAWAL_AUTHORIZATION_INVALID`, `409 TILL_COUNT_ALREADY_SUBMITTED`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 INVALID_AGENT_TRANSACTION` (bare `IllegalArgumentException` fallback),
`400 IDEMPOTENCY_KEY_REQUIRED`.

## Agent Administration — `/api/v1/system/agents` (ADMIN role only)

**Added 2026-09-05, same slice.** ADMIN-operated agent-network management —
registration, status, operator assignment, float funding, and till reconciliation,
until the separate agent-staff authentication flow this controller's own doc comment
names is introduced. Confirmed by direct read of `AgentAdminController.kt` (13
endpoints, 10 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` (base path) | — | `{success, agents: [...]}` | |
| POST | `` (base path) | `{displayName, dailyCashInLimit, dailyCashOutLimit}` | `201 {success, agent: {...}}` | |
| POST | `/{agentId}/status` | `{status}` | `{success, agent: {...}}` | |
| GET | `/{agentId}/operators` | — | `{success, operators: [...]}` | |
| POST | `/{agentId}/operators` | `{userId}` | `201 {success, operator: {...}}` | |
| POST | `/{agentId}/location` | `{latitude, longitude}` | `{success, agent: {...}}` | |
| POST | `/{agentId}/operators/{userId}/status` | `{isActive}` | `{success, operator: {...}}` | |
| POST | `/{agentId}/float` | `{amount, reference}` (+ `Idempotency-Key`) | `{success, ...}` | Real money movement — funds the agent's own till |
| GET | `/till-reconciliations/pending` | — | `{success, reconciliations: [...]}` | |
| GET | `/till-reconciliations?from&to` | — | `{success, report: {...}}` | |
| POST | `/till-reconciliations/{id}/resolve` | `{note}` | `{success, reconciliation: {...}}` | |
| POST | `/{agentId}/cash-ins` | `{accountNumber, amount, receiptNumber}` (+ `Idempotency-Key`) | `{success, ...}` | Admin-initiated equivalent of Agent Operator's own `/cash-ins` |
| POST | `/{agentId}/cash-outs` | `{accountNumber, amount, receiptNumber, authorizationCode}` (+ `Idempotency-Key`) | `{success, ...}` | |

Errors (all 10 real `@ExceptionHandler`s): `404 AGENT_NOT_FOUND`,
`409 AGENT_SUSPENDED`, `409 CASH_RECEIPT_ALREADY_USED`,
`422 AGENT_DAILY_LIMIT_EXCEEDED`, `422 AGENT_INSUFFICIENT_CASH`,
`409 AGENT_OPERATOR_ALREADY_ASSIGNED`, `404 TILL_RECONCILIATION_NOT_FOUND`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 INVALID_CASH_IN` (bare `IllegalArgumentException` fallback).

## Float Marketplace — `/api/v1/float-marketplace`

**Added 2026-09-05, same slice.** Real peer-to-peer agent float rebalancing — an
agent short on cash (or long on wallet float) posts a listing, another agent fills
it, real money moves between their two tills. Every endpoint requires a normal
operator JWT and derives the caller's own agent identity server-side — no endpoint
accepts a caller-supplied agent id, so there's no ownership parameter to spoof.
Confirmed by direct read of `FloatMarketplaceController.kt` (9 endpoints, 12 error
codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/listings` | `{amount}` | `201 {success, listing: {...}}` | |
| GET | `/listings/nearby?latitude&longitude&radiusKm` | — | `{success, listings: [...]}` | `radiusKm` defaults to 20 |
| GET | `/listings/mine` | — | `{success, listings: [...]}` | |
| POST | `/listings/{listingId}/cancel` | — | `{success, listing: {...}}` | |
| POST | `/listings/{listingId}/requests` | `{amount}` | `201 {success, request: {...}}` | |
| GET | `/requests/mine` | — | `{success, requests: [...]}` | |
| GET | `/requests/incoming` | — | `{success, requests: [...]}` | |
| POST | `/requests/{requestId}/accept` | — (+ `Idempotency-Key`) | `{success, request: {...}}` | Real money movement between the two agents' tills |
| POST | `/requests/{requestId}/decline` | — | `{success, request: {...}}` | |

Errors (all 12 real `@ExceptionHandler`s): `403 AGENT_OPERATOR_NOT_AUTHORIZED`,
`409 AGENT_SUSPENDED`, `404 FLOAT_LISTING_NOT_FOUND`,
`404 FLOAT_TRANSFER_REQUEST_NOT_FOUND`, `409 FLOAT_LISTING_NOT_OPEN`,
`422 FLOAT_LISTING_INSUFFICIENT_REMAINING`, `409 FLOAT_TRANSFER_REQUEST_NOT_PENDING`,
`400 FLOAT_SELF_TRANSFER`, `422 FLOAT_LISTING_INSUFFICIENT_CASH`,
`409 IDEMPOTENCY_KEY_CONFLICT`, `409 IDEMPOTENT_REQUEST_PROCESSING`,
`400 INVALID_FLOAT_MARKETPLACE_REQUEST` (bare `IllegalArgumentException` fallback).

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
| GET | `/queue` | — | `{success, queue: [...], summary, activePolicy, ...pageMeta}` | All unreviewed flags, oldest first. `summary`/`activePolicy` added since this section was first written — real, not yet individually documented here |
| POST | `/{flagId}/decide` | `{decision: "CLEARED" \| "CONFIRMED", reviewNote?}` | `{success, flag}` | Blocks re-deciding an already-reviewed flag. `reviewNote` added since this section was first written |

Flags are raised by `FraudRuleEngine` (lives in `:core`, called from P2P, wallet transfer, and
merchant collection as of 2026-07-13 — every real money-moving flow in this backend except
bills/loans/stocks/savings/insurance, which don't move money between two itunda users) and
never block a transaction — review-only by design. Rules: `HIGH_VALUE` (≥100,000 RWF),
`VELOCITY` (3+ outgoing transactions in 5 minutes), `NEW_RECIPIENT` (first-ever payment to that
recipient). Errors (complete — all 3 real `@ExceptionHandler`s in `FraudController.kt`,
`FRAUD_REVIEW_NOTE_INVALID` added 2026-09-05, a real handler this section had never listed):
`404 FRAUD_FLAG_NOT_FOUND`, `409 FRAUD_FLAG_ALREADY_REVIEWED`, `400 FRAUD_REVIEW_NOTE_INVALID`.

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

### Hood report review — `/api/v1/system/hood-reports` (ADMIN role only)

**Added 2026-09-05** (twenty-sixth documentation slice — the final push through the
remaining ADMIN-only moderation queues). A second, distinct `@RestController` class
(`HoodReportAdminController`) living in the same file as the user-facing Hood Report
endpoint below — a real gap this session's own controller-counting method had never
caught, since it assumed one `@RestController` per file. Confirmed by direct read of
`HoodReportController.kt`'s second class (3 endpoints, 2 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `` (base path) | — | `{success, reports: [...], ...pageMeta}` | |
| POST | `/{id}/resolve` | — | `{success, report: {...}}` | |
| POST | `/{id}/remove-target` | — | `{success, report: {...}}` | Removes the reported post/comment/etc. itself |

Errors: `404 HOOD_REPORT_NOT_FOUND`, `404 HOOD_REPORT_TARGET_NOT_FOUND`.

### Marketplace escrow disputes — `/api/v1/system/marketplace-escrow` (ADMIN role only)

**Added 2026-09-05, same slice.** Resolves a disputed Marketplace escrow (see the
Marketplace section above) by releasing funds to the seller or refunding the buyer.
Confirmed by direct read of `MarketplaceEscrowAdminController.kt` (2 endpoints, 2
error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/disputes` | — | `{success, disputes: [...]}` | |
| POST | `/{escrowId}/resolve` | `{release}` | `{success, escrow: {...}}` | `release: true` pays the seller; `false` refunds the buyer |

Errors: `404 ESCROW_NOT_FOUND`, `409 INVALID_ESCROW_STATUS`.

### Merchant moderation — `/api/v1/system/merchants` (ADMIN role only)

**Added 2026-09-05, same slice.** Real merchant-directory moderation — before this
existed, there was no way anywhere in the app to take a merchant out of public
browse once created. Confirmed by direct read of
`MerchantModerationAdminController.kt` (3 endpoints, 1 error code).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/uncategorized?` | — | `{success, merchants: [...], ...pageMeta}` | Real moderation queue — "`ACTIVE` with no category" is the real signal a merchant needs review |
| POST | `/{merchantId}/suspend` | — | `{success, status}` | |
| POST | `/{merchantId}/reactivate` | — | `{success, status}` | |

Errors: `404 MERCHANT_NOT_FOUND`.

### Property ownership verification — `/api/v1/system/property-verification` (ADMIN role only)

**Added 2026-09-05, same slice.** Reviews the ownership-verification document a
lister submits via `POST /api/v1/realestate/listings/{propertyListingId}
/verify-ownership` (see Property Listings above). Confirmed by direct read of
`PropertyOwnershipAdminController.kt` (2 endpoints, 3 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| GET | `/queue` | — | `{success, queue: [...], ...pageMeta}` | |
| POST | `/{submissionId}/decide` | `{approve, reason?}` | `{success, submission: {...}}` | |

Errors (all 3 real `@ExceptionHandler`s): `404 PROPERTY_OWNERSHIP_SUBMISSION_NOT_FOUND`,
`409 PROPERTY_OWNERSHIP_SUBMISSION_NOT_PENDING`, `400 INVALID_DECISION_REASON`.

### Transit GTFS import — `/api/v1/system/transit` (ADMIN role only)

**Added 2026-09-05, same slice.** A real, one-time (re-runnable) fetch-and-replace
import of the real Kigali GTFS feed backing Maps' own transit directions — not a
live per-request call. Confirmed by direct read of `TransitGtfsAdminController.kt`
(1 endpoint, 1 error code).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/import-gtfs` | — | `{success, imported}` | |

Errors: `502 TRANSIT_GTFS_FETCH_FAILED`.

### AI summary generation (Eats & Hood) — `/api/v1/system/ai-summaries`, `/api/v1/system/hood-ai-summaries` (ADMIN role only)

**Added 2026-09-05, same slice.** Two near-identical manual-trigger endpoints
(`AiSummaryAdminController`, `HoodAiSummaryAdminController`) for their respective
`AiSummaryService`/`HoodAiSummaryService` batch jobs, alongside each service's own
automatic daily scheduler — useful for a first, deliberately-supervised run right
after the real llama-server instance is deployed. Neither controller has an
`@ExceptionHandler` of its own.

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `/api/v1/system/ai-summaries/generate?limit` | — | `{success, generated}` | Eats restaurant summaries. `limit` defaults to 20 |
| POST | `/api/v1/system/hood-ai-summaries/generate?limit` | — | `{success, generated}` | Community/Hood post summaries. `limit` defaults to 20 |

## Chat Reports — `/api/v1/chat/reports`

**Added 2026-09-05, same slice.** Real 1:1-message reporting inside Talk. Confirmed
by direct read of `ChatReportController.kt` (1 endpoint, 2 error codes, all unique —
one handler method covers 2 distinct exception types, both mapping to the same
not-found code).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{messageId, reason}` | `201 {success, report: {...}}` | |

Errors: `404 CHAT_MESSAGE_NOT_FOUND` (covers both a nonexistent message and a
message the caller isn't allowed to report), `409 CHAT_REPORT_ALREADY_OPEN`.

## Hood Reports — `/api/v1/hood/reports`

**Added 2026-09-05, same slice.** The unified, cross-vertical reporting endpoint
Community/Marketplace/Jobs listings all retired their own separate report endpoints
in favor of (see those sections above). Reviewed via the ADMIN-only queue documented
above. Confirmed by direct read of `HoodReportController.kt`'s first class (1
endpoint, 2 error codes, all unique).

| Method | Path | Body | Success | Notes |
|---|---|---|---|---|
| POST | `` (base path) | `{targetType, targetId, reason}` | `201 {success, report: {...}}` | `targetType` is which vertical the report targets (a post, listing, job, etc.) |

Errors: `409 HOOD_REPORT_ALREADY_OPEN`, `404 HOOD_REPORT_TARGET_NOT_FOUND`.

## What does not exist (previously implied real, or plausible-sounding, but absent)

Grepped for directly, confirmed absent as of 2026-07-13:

- **Any `/users/*` route** (invented in a previous version of this document). Profile
  lives at `GET /api/v1/auth/profile`; there is no spending-analytics endpoint at all
  yet (`docs/TOSS_PARITY_MATRIX.md`'s Spending row: `demo`, categorization is still
  target). **Corrected 2026-09-05** — `/api/v1/analytics/*` was also claimed absent
  here, but a real, minimal `AnalyticsController` does exist (see the Analytics
  section above) — it's a small, closed-vocabulary product-event recorder plus an
  ADMIN-only usage summary, not the user-facing spending-analytics feature this bullet
  originally meant to describe as missing. The spending-analytics gap itself is still
  real; only this bullet's blanket "no `/analytics/*` route at all" claim was wrong.
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
