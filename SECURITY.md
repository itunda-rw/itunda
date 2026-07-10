# Itunda Security Review

This document reflects the actual current state of the codebase, checked directly
against the code rather than written aspirationally. It replaces an earlier version
of this file that described a mature enterprise security program (SOC 2, HSM-backed
key rotation, 24/7 monitoring, PCI-DSS compliance, etc.) with no relationship to what
is actually implemented — that mismatch is itself a real risk: a reviewer, auditor, or
new engineer reading the old document would reasonably believe protections exist that
do not. Every claim below was verified by reading the relevant code as of this review.

## Reporting Security Vulnerabilities

If you discover a security vulnerability in Itunda, please email security@itunda.rw
instead of using the issue tracker. Include: type of vulnerability, location, steps to
reproduce, potential impact, and a suggested fix if you have one.

## Current Security Posture — What's Real

- **Double-entry ledger with balance validation** (`backend/src/services/ledger.ts`): every money movement is a balanced set of debit/credit legs; an unbalanced set is rejected before any state changes, and a wallet debit that would overdraw is rejected before it's applied. Verified directly (see `docs/TOSS_PARITY_MATRIX.md`).
- **Idempotency keys on money-moving endpoints** (`backend/src/services/idempotency.ts`): a repeated request with the same key and body replays the original result; the same key with a different body is rejected (409). Survives a server restart.
- **Provider-layer failure handling** (`backend/src/services/providerConnectors.ts`): a simulated provider decline never touches the ledger — verified that wallet balance and ledger entry count are unchanged on a forced failure.
- **Password hashing on login/register** (`backend/src/controllers/auth.controller.ts`): passwords are hashed with bcrypt and verified with `bcrypt.compare`; login returns a real signed, expiring JWT (`jsonwebtoken`, `HS256`, 24h access / 7d refresh). This was fixed during this review — previously `login()` accepted *any* password for *any* phone number and always returned a static, non-expiring mock token (see "Fixed During This Review" below).
- **HTTP security headers** via `helmet()`, applied globally.
- **Input validation on amounts**: money-moving endpoints reject missing/non-positive amounts before doing anything.
- **JWT verification middleware is wired on every route** (`backend/src/middleware/auth.middleware.ts`, applied via `router.use(requireAuth)` in every route file, including `discover.routes.ts` which this review added it to). A missing, malformed, expired, or refresh-used-as-access token is rejected with 401 before reaching any controller. (This closes what an earlier version of this document — and this document itself, until this review — listed as "no middleware anywhere checks the JWT." That claim was stale; the middleware existed and was already mounted. What was still true, and is fixed below, is gap #2.)
- **Per-user authorization, not just authentication** (this review): every controller previously hardcoded `user_1` (or a specific wallet id like `wallet_1`/`wallet_3`) as "the current user" regardless of whose valid token made the request. A second real account — created via the real `POST /auth/register` — could authenticate successfully and then act on `user_1`'s wallets, loans, holdings, savings goals, and tickets, because nothing checked that the resource being touched actually belonged to the caller. Every controller now uses `req.userId` from the verified token, and every money-moving endpoint that accepts or resolves a wallet id (transfer quote/confirm, bill pay, airtime, loan apply/repay, stock buy, savings deposit, insurance enrollment) verifies ownership and returns 403 on mismatch, or resolves the caller's *own* wallet by type instead of a hardcoded id. Verified live end-to-end: registered a second account, confirmed it sees empty wallets/contacts/notifications (not `user_1`'s), confirmed it gets 403 attempting to quote a transfer from `wallet_1`, 403 confirming `user_1`'s own already-created quote, and 403 repaying `user_1`'s loan — while `user_1`'s own login → quote → confirm flow still completes normally with the correct balance change.

## Current Security Posture — What's Not Real Yet

These are not aspirational gaps in a roadmap — they are the actual current behavior of
the deployed code, checked directly:

1. **New accounts have no wallet of their own.** `POST /auth/register` creates a real user record, but nothing provisions a `MAIN`/`SAVINGS`/`INVESTMENT` wallet for them. After this review's ownership fixes, that means a second real user correctly *cannot* touch `user_1`'s money — but they also can't do anything money-related themselves (transfer, bill pay, loan apply, savings deposit, insurance enroll all now correctly 404 with "no wallet found for this account"). Onboarding/wallet-provisioning is real, scoped work this review did not do.
2. **Several account-level resources are still single-tenant demo data, not real per-user tables.** `rewardsSummary`, `interestJar`, and the insurance `myPolicies` list are each a single global object/array rather than keyed by user. This review added ownership gates where a `userId` field already existed (`rewardsSummary.userId`, `interestJar.userId`) so a second user gets a clean 403 instead of silently acting on `user_1`'s data, but `myPolicies` has no `userId` field at all yet — enrolling debits the caller's own wallet correctly, but the resulting policy isn't actually attributed to anyone. A real multi-user deployment needs these turned into real per-user tables, not just gated.
3. **No role separation on the operations endpoints.** `GET /system/*` (rails, reconciliation, fraud cases, compliance queue, incidents) requires a valid consumer JWT like everything else, but any authenticated consumer — not just ops/compliance staff — can view it. There is no `role` field on `User` and no admin-only middleware. This is a real gap for a production deployment, not addressed by this review.
4. **No rate limiting.** Login, registration, and every money-moving endpoint can be called as fast as the network allows. There is no brute-force protection on login. This remains true for *this* (Express) backend specifically. `services/backend/` has since added real Redis-backed rate limiting on login (5/minute) and registration (3/10 minutes), keyed by phone number — that work has not been ported back to Express, and doing so would need an equivalent counter store here (Redis, or a simple in-process limiter given this backend's smaller demo traffic).
5. **CORS is wide open.** `cors()` is called with no options, which allows any origin. Fine for local development, wrong for anything reachable from the internet.
6. **Data is not encrypted at rest.** State lives in in-memory JavaScript arrays, snapshotted in plaintext to `backend/.data/store.json` (see `backend/src/services/persistence.ts`). This includes wallet balances, transaction history, and National ID submissions. This is explicitly a demo-durability stepping stone, not a production data store (see the Non-Negotiable Gates section of `docs/TOSS_PARITY_MATRIX.md`).
7. **Error responses can leak internal detail.** The global error handler (`backend/src/index.ts`) returns `err.message` directly to the client. For an unexpected error this could expose internal implementation detail to a caller.
8. **`JWT_SECRET` has a hardcoded development fallback** (`backend/src/controllers/auth.controller.ts`) so local runs work without extra setup. If this code is ever deployed without a real `JWT_SECRET` environment variable set, every token is forgeable.
9. **KYC decision-making is entirely absent.** `POST /identity/submit` moves a document to `REVIEW` and opens a compliance queue item, but nothing — no vendor integration, no reviewer UI — ever moves it to `VERIFIED`. This is blocked on real regulatory/vendor access, not on code, but it means the KYC state in this system currently reflects "submitted," never a real decision.

## Fixed During This Review

- `login()` previously accepted any `phoneNumber`/`password` combination and always returned the same demo user with a static, non-expiring mock token (`MOCK_TOKEN`, a hardcoded JWT-shaped string with `exp: 9999999999`). It now verifies the password against a real bcrypt hash and issues a real signed, expiring token. The seed user's `passwordHash` was also fake (a repeating placeholder string, not a real bcrypt output) — replaced with a real hash.
- Registration previously did not persist the new user anywhere; it now actually adds the user to the store (and that store is included in the restart-persistence snapshot).
- **Broken authorization despite real authentication** (this review). `requireAuth` middleware was already correctly wired on nearly every route, and JWTs were real and verified — but no controller ever read the identity that verification produced. Every controller hardcoded `user_1`, `wallet_1`, or `wallet_3` as "the current user/wallet," so any second authenticated account (created via the real, working registration endpoint) could read or move `user_1`'s money and data. Fixed across `wallet`, `loan`, `stock`, `savings`, `contacts`, `identity`, `rewards`, `insurance`, `bills`, `support`, `notifications`, and `auth` controllers: every handler now uses `req.userId` from the verified token, money-moving endpoints resolve the caller's *own* wallet by type instead of a hardcoded id, and any client-supplied wallet/loan/ticket id is checked for ownership before use (403 on mismatch). Also added `requireAuth` to `discover.routes.ts`, the one route file that didn't have it. Transfer quotes now also carry the creating user's id and are re-checked at confirm time, closing a secondary "quote hijack" path (guessing or reusing someone else's `quoteId`). Verified live end-to-end with a real second registered account: empty wallets/contacts/notifications instead of `user_1`'s, 403 quoting from `wallet_1`, 403 confirming `user_1`'s quote, 403 repaying `user_1`'s loan — while `user_1`'s own transfer flow (quote → confirm) still completes correctly with the right balance change.

## Prioritized Remediation (Not Yet Done)

Ordered by what would cause the most damage if this were handling real money and real user data:

1. **Wallet provisioning at registration.** `POST /auth/register` should create a real `MAIN` wallet (and ideally `SAVINGS`/`INVESTMENT`) for the new user, so a second real account can actually use the product instead of correctly-but-uselessly 404ing on every money action.
2. **Turn `rewardsSummary`, `interestJar`, and `myPolicies` into real per-user tables** instead of single global objects with an ownership gate bolted on.
3. **Role-based access for `/system/*` operations endpoints.** Add a `role` field to `User` and an admin-only middleware; today any authenticated consumer can view the ops/compliance/fraud dashboards.
4. **Rate limiting**, especially on `/auth/login`.
5. **Restrict CORS** to known frontend origins before any non-local deployment.
6. **A real durable, encrypted data store** (Postgres, per the non-negotiable gates doc) instead of plaintext JSON on disk.
7. **Stop returning raw error messages** to clients; log detail server-side, return a generic message.
8. **Require `JWT_SECRET` to be set** (fail to start rather than silently falling back) outside of an explicit local-dev mode.
9. **Token revocation.** There is currently no way to invalidate an issued access/refresh token before it expires (e.g. on logout or suspected takeover). This remains true for *this* (Express) backend specifically. `services/backend/` (the parallel Kotlin/Spring backend, see `docs/TOSS_PARITY_MATRIX.md`) has since added real Redis-backed revocation with `POST /auth/logout` and refresh-token rotation on `POST /auth/refresh` — that work has not been ported back to Express, and doing so would need an equivalent revocation store here (e.g. Redis, or the same JSON-file tier the rest of this backend's state uses).

## Incident Response Runbooks

These are scoped to this system's actual architecture and actual failure modes, not
generic enterprise incident categories. Each assumes the on-call engineer has shell
access to the running backend process and its `backend/.data/store.json`.

### Runbook: Ledger integrity check fails (debits ≠ credits)

**Detection:** `postLedgerTransaction` throws `LedgerImbalanceError` before any state changes — an imbalance is rejected, not silently applied, so if you're seeing this it means a *caller* tried to post something wrong, not that the ledger is already corrupted.

1. Check the application logs for the `LedgerImbalanceError` message — it includes the computed debit/credit totals.
2. Identify which endpoint/controller constructed the unbalanced legs (the stack trace points at the call site).
3. Since the transaction was rejected before touching state, no reconciliation is needed — the wallet balances and ledger entries are exactly as they were before the failed call.
4. Fix the leg construction in the offending controller; add a regression check for that specific money flow.
5. If this happened in production-like data (not local dev), treat it as a P1 — it means a code path could have caused a real imbalance, and every recent deploy touching money flows should be audited.

### Runbook: A payment rail is degraded or down

**Detection:** `GET /system/reconciliation` shows a spike in `exceptionCount` for a rail, or `GET /system/rails` shows a rail's `status` as `degraded`/`offline`.

1. Check `GET /system/rails` for the affected rail's current `status`, `successRate`, and `avgLatencyMs`.
2. Check `GET /system/reconciliation` for that rail's batches — `expectedAmount` minus `matchedAmount` is exactly the value of failed attempts that need follow-up.
3. Every failed attempt is logged via `recordProviderAttempt` (`backend/src/services/reconciliation.ts`) with a timestamp and amount — pull the raw log for the affected rail/day to see individual failures. Note: since this is a simulated connector (`providerConnectors.ts`), a real rail issue in production would instead be diagnosed against the real provider's status page/API.
4. No customer funds are at risk from a rail failure by construction — `confirmTransferQuote`/`payBill`/`buyAirtime` all call the connector *before* posting to the ledger, so a decline never leaves a dangling ledger entry. Confirm this by checking that `exceptionCount` failures have no corresponding `COMPLETED` transaction.
5. If customers report money "stuck," check the transaction status in `GET /wallet/activity` — a genuinely failed attempt never creates a transaction record at all (by design), so a customer report of a failed-but-charged transfer would itself indicate a real bug in this invariant and should be escalated as P1.

### Runbook: Suspected duplicate charge / idempotency bypass

**Detection:** A customer reports being charged twice for what they believe was one action, or a support ticket references two transactions with near-identical amounts/timestamps.

1. Check whether both requests used the same `Idempotency-Key` header. If they used two *different* keys for what the client intended as one action, this is a client-side bug (the client should reuse the key on retry), not a backend defect — the backend did exactly what it was asked twice.
2. If the same key was used and two charges still resulted, check `backend/.data/store.json`'s persisted idempotency section (or query the in-memory store if the process hasn't restarted) for that key — confirm whether the stored record matches one of the two transactions. If it doesn't, that's a genuine bug in `getIdempotentReplay`/`storeIdempotentResult` and should be treated as P1.
3. Note idempotency keys currently live in the same JSON snapshot as everything else (`backend/src/services/idempotency.ts`) — there's no separate TTL enforcement beyond the 24h check in `getIdempotentReplay`, and no cross-instance sharing (a real multi-instance deployment needs Redis or a DB-backed store here, see Remediation #5).
4. If confirmed duplicate: file a support ticket of type `DISPUTE` referencing the erroneous transaction and use `POST /support/tickets/:id/refund` to reverse it — this replays the exact original ledger legs in reverse, so the customer's balance is restored to the exact pre-charge amount.

### Runbook: `backend/.data/store.json` is corrupted or lost

**Detection:** Backend fails to start, or starts but logs `itunda: failed to load persisted state, starting from seed data` when that wasn't expected.

1. `loadPersistedState()` (`backend/src/services/persistence.ts`) catches any parse/read error and falls back to seed data automatically — the process will still start, but **all real activity since the last successful write is lost** and the app is back to demo seed state.
2. Check whether a backup of `store.json` exists (there is currently no automated backup — this is a real gap, since the file is explicitly `.gitignore`'d and lives only on the single running host).
3. If no backup exists, the data is unrecoverable. This is exactly why the JSON snapshot is documented as a demo stepping stone, not a production data store — a real deployment needs point-in-time recovery, which requires an actual database (Remediation #5).
4. Going forward: this scenario is the concrete argument for prioritizing the Postgres migration before this system holds anything real.

### Runbook: Suspected unauthorized access / account takeover

**Detection:** A user reports activity they didn't perform, or unusual transaction patterns for a single wallet.

1. As of this review, requests are both authenticated (valid JWT required) and authorized (every controller checks that the resource being touched belongs to the caller's `req.userId`) — so a genuine report of unauthorized access is now a distinct, investigable signal rather than expected behavior. The main remaining exposure is token lifetime: there is no revocation, so a leaked/stolen token remains valid until its 24h (access) or 7d (refresh) expiry.
2. Force a password reset (invalidates future logins immediately) and audit `GET /wallet/activity` and `GET /support/tickets` for that user for the suspected window. Actual token revocation before expiry is not yet built (Remediation #9) — until it exists, a stolen token stays valid regardless of password reset.
3. File an `ACCOUNT_TAKEOVER` support ticket type (already modeled in `backend/src/controllers/support.controller.ts`) to track the investigation, even though the automated freeze/recovery flow referenced by that ticket type doesn't exist yet.

## What This Review Deliberately Does Not Claim

The previous version of this file listed AES-256 encryption, PCI-DSS/GDPR/SOC 2
compliance, a 24/7 SOC, quarterly penetration testing, HSM-backed key rotation, and
similar enterprise controls. None of that exists in this codebase today, and this
document does not restate those claims. When any of the above remediation items ship,
this file should be updated to describe what was actually built, not what is planned —
keeping this document accurate is itself part of the security posture.
