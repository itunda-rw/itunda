# Itunda Security Review

> **Rewritten 2026-09-03.** The previous version of this file (itself an honest, detailed
> rewrite of an even older aspirational version) reviewed a plain Node/Express prototype
> backend at the repo root (`backend/src/services/ledger.ts`, `backend/src/controllers/
> auth.controller.ts`, `backend/.data/store.json`, etc.) — that backend no longer exists
> anywhere in this repo. The canonical backend today is `services/backend` (Kotlin + Spring
> Boot + Spring Data JPA + MySQL + Flyway + Spring Security JWT, per root `CLAUDE.md`), which
> the old document only ever mentioned as "the parallel Kotlin backend." This version reviews
> the real, current backend directly, verified against the actual code as of this rewrite —
> the same discipline the old version established, applied to the system that's actually
> running. When any claim below stops matching the code, fix this file, not just the code
> that drifted away from it — that mismatch is itself a real risk (a reviewer or new engineer
> reading a stale doc will reasonably believe protections exist that don't).

## Reporting Security Vulnerabilities

If you discover a security vulnerability in Itunda, please email security@itunda.rw instead
of using the issue tracker. Include: type of vulnerability, location, steps to reproduce,
potential impact, and a suggested fix if you have one.

## Current Security Posture — What's Real

- **Double-entry ledger with pessimistic row-locking** (`core/ledger/LedgerService.kt`):
  every money movement is a balanced set of debit/credit legs, rejected before any state
  change if unbalanced. All 144+ balance-mutating call sites go through one choke point
  (`postLedgerTransaction`), which locks every account in stable sorted-id order (deadlock
  avoidance) via `@Lock(PESSIMISTIC_WRITE)`-backed `findByIdForUpdate`, then additionally calls
  `entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE)` — the non-obvious part,
  added after a real, live-reproduced 3-way-concurrent-transfer lost-update bug (fixed
  2026-08-09): Hibernate's identity map returns a stale already-managed instance even after
  the DB row lock is genuinely held, and a bare unlocked `.refresh()` still isn't enough
  (MySQL's REPEATABLE READ snapshot still shows the old value) — only a *locking* refresh
  forces the true latest committed row.
- **Idempotency keys on money-moving endpoints** (`core/idempotency/IdempotencyService.kt`,
  DB-backed, survives a restart): a repeated request with the same key and body replays the
  original result; the same key with a different body is rejected.
- **Real password auth + JWT** (`auth/JwtService.kt`, `security/SecurityConfig.kt`,
  `security/JwtAuthenticationFilter.kt`): passwords are hashed and verified server-side; login
  issues a real signed, expiring JWT (HS256, 24h access / 7d refresh), and the signing key
  must be at least 256 bits — a too-short secret throws at startup rather than silently
  weakening the key. Every token carries a `jti` (unique id) and a `role` claim.
- **Real token revocation** (`auth/TokenBlocklistService.kt`, Redis-backed): unlike a plain
  stateless-JWT scheme, a token can genuinely be invalidated before its natural expiry (e.g.
  on logout or a suspected takeover) by blocklisting its `jti` — this is a real capability the
  Express-era predecessor of this backend never had.
- **Per-resource authorization, not just authentication** — closed as a bug class via a
  dedicated 7-pass IDOR audit (see project history): every controller checks that a
  client-supplied resource id (wallet, loan, ticket, listing, etc.) actually belongs to the
  authenticated caller before acting on it, returning 403 (not silently succeeding or leaking
  a 404-vs-403 timing/existence signal) on mismatch.
- **Role-based access on admin/ops endpoints**: 33 files use `@PreAuthorize("hasRole('ADMIN')")`
  gating `/api/v1/system/**` operational surfaces (reconciliation, fraud-case review, compliance
  queues, moderation). A real, closed bug class exists here too: a sweep found 7 "process due
  X system-wide" batch endpoints living OUTSIDE `/system/**` (insurance renewals, savings goal
  maturity, student-loan grace-end, postpaid-credit reminders, certificate renewal, gift-voucher
  expiry, merchant-coupon expiry) that had been missed by the blanket `/system/**` gate and were
  callable by any authenticated user — all 7 fixed with the same `@PreAuthorize` annotation.
- **Rate limiting** (`auth/RateLimiter.kt`): 122+ real `.checkLimit(` call sites across roughly
  80 services (login/register/device-verify/USSD-PIN, plus savings/loans/p2p/messaging/
  merchant/rideshare/marketplace and more) — broader than the class's own doc comment
  describes (that comment is itself stale, understating real coverage; not yet fixed as of
  this rewrite, a small follow-up).
- **CORS scoped to an explicit allow-list** (`security/SecurityConfig.kt`), not wide-open —
  configurable via `itunda.cors.allowed-origins`, defaulting to the known local dev ports.
- **Webhook delivery is HMAC-signed** (`merchant/WebhookDeliveryService.kt`): outbound webhooks
  (payment/order-status events to an integrating merchant's own server) carry an
  `X-Itunda-Signature` header (HMAC-SHA256 over the raw payload, a per-merchant secret) — added
  after an audit found merchant webhook delivery had shipped with no signature verification at
  all, meaning anyone who learned/guessed a merchant's webhook URL could forge a fake payment
  event.
- **Android hardware-backed device keys**: the device-verification key gating money-moving
  step-up now explicitly requests StrongBox where the hardware supports it (falling back to
  TEE where it doesn't), fixed after an audit found the code's own header comment claiming
  "StrongBox-backed" was aspirational, not actually wired (`setIsStrongBoxBacked` was never
  called). iOS's equivalent uses Secure Enclave uniformly (no TEE/StrongBox-style split exists
  on iOS).
- **Real-time fraud-flag surfacing** (`FraudRuleEngine`, wired into `P2pService.sendDirect`):
  HIGH_VALUE/VELOCITY/NEW_RECIPIENT heuristics are computed on every P2P Quick Transfer send and
  now surfaced to the sender as an informational warning (never a silent block), not just
  logged to an admin-only review queue as before. Deliberately scoped to `sendDirect` only
  (matching the real Toss "Fraud Suspicion Siren" reference this was sourced from, which is
  itself scoped to simple/Quick Transfer, not every money-moving flow) — the ~14 other
  `FraudRuleEngine` callers (Commerce/Eats/Bills/Marketplace/Payroll/etc.) still only log to the
  admin queue, a real, disclosed, not-yet-closed scope boundary, not an oversight.

## Current Security Posture — What's Not Real Yet

1. **`JWT_SECRET` has a hardcoded development fallback**
   (`itunda-dev-secret-do-not-use-in-production`, referenced in every service's
   `application.yml` and `JwtService.kt`'s own `@Value` default). If any of these services is
   ever deployed without a real `JWT_SECRET` environment variable set, every token is
   forgeable. This is the same class of gap the old Express-backend review flagged, carried
   forward because the underlying risk (a fallback secret existing at all) is structurally the
   same even though the implementation is entirely different.
2. **No automated security scanning or penetration testing.** Every fix referenced above (IDOR
   audit, concurrency audit, StrongBox, webhook signing, fraud-flag surfacing) was found via
   manual, code-reading audits, not a scanner or pentest — there is no SAST/DAST/dependency-
   vulnerability-scanning tool wired into this repo anywhere.
3. **No data-at-rest encryption beyond whatever MySQL/the hosting environment provide by
   default.** No field-level or application-level encryption exists for sensitive columns
   (National ID numbers, KYC document references, etc.) — confirmed via a direct grep for any
   AES/encryption primitive in `core`, none found.
4. **KYC decision-making is entirely human-review, with no automated fraud/document scoring.**
   `IdentityService.submit()` routes every submission to mandatory human review
   (`PENDING` -> `decide()`); there's no ML/automated accept-reject step at all. This was
   checked deliberately against a real sourced Toss AI-ID-verification finding and correctly
   NOT ported — the problem that finding solves (an automated gate wrongly bouncing valid
   users) doesn't exist here since nothing is auto-rejected in the first place.
5. **CI-enforced verification of any of the above is not running.** GitHub Actions has been
   billing-blocked and failing in seconds on every run since at least 2026-08-15 (see
   `docs/ARCHITECTURE.md`) — every check this document references (Kotest suites, Konsist/
   dependency-cruiser boundaries, accessibility/file-size lints) is currently local-run-only,
   not pipeline-enforced. A clean local run is not the same guarantee as a clean CI run gating
   merges.
6. **Fraud-flag surfacing is scoped to one flow** (see above) — the other ~14
   `FraudRuleEngine` callers are a real, disclosed, not-yet-closed follow-up if a stronger Toss
   source is ever found scoping the reference feature beyond Quick Transfer.

## Incident Response Runbooks

The old (Express-backend) version of this file had detailed runbooks for ledger-imbalance,
rail-degradation, duplicate-charge, and store-corruption scenarios, each citing specific
Express file paths and endpoints (`backend/.data/store.json`, `GET /system/reconciliation`,
`recordProviderAttempt`, etc.) that don't exist in this backend. Rather than fabricate
Kotlin-backend-equivalent runbooks without directly verifying each one's real detection
signal/recovery steps against the current code, this rewrite deliberately omits them --
writing accurate runbooks for `LedgerImbalanceException`, the reconciliation system
(`SystemController.getPaymentRails`), and idempotency-conflict handling
(`IdempotencyConflictException`/`IdempotencyInProgressException`) is a real, disclosed
follow-up, not a silently-dropped section.

## What This Review Deliberately Does Not Claim

No PCI-DSS/SOC 2/GDPR compliance certification, no HSM-backed key management, no 24/7 SOC, and
no quarterly penetration-testing program exist for this codebase — none of that is claimed
here or anywhere else in this repo's docs. This is a research/demo fintech prototype (see root
`CLAUDE.md` and `docs/IMPLEMENTATION_GUIDE.md`), not a licensed, audited financial institution.
When a genuinely new security capability ships, update this file to describe what was actually
built, not what's planned — keeping this document accurate is itself part of the security
posture, which is exactly the discipline that made this rewrite necessary in the first place.

---

**Last updated**: 2026-09-03
