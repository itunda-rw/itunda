# itunda Payments

> **Rewritten 2026-08-30** (previously rewritten 2026-07-13). The 2026-07-13 version
> correctly described the state of that day — no `paymentKey` lifecycle, no cancel/refund,
> no merchant dashboard — but every one of those has since been built and shipped
> (2026-07-21 onward) without this document ever being updated. A developer reading the
> 2026-07-13 version today would have concluded itunda Pay has no external payment API at
> all, when it in fact has a real, working one. Found during a 2026-08-30 market-readiness
> audit specifically checking whether itunda's own dev-facing documentation matches its own
> real code — the same discipline this document's own 2026-07-13 rewrite already applied to
> the *previous*, hallucinated version, just not kept current since. This version documents
> what actually exists today, verified directly against `services/backend/merchant` and the
> 3 real clients that consume it.

## What real itunda Pay actually is today

Two real, separate ways money moves through a merchant, both backed by the same
`PaymentIntent`/ledger machinery underneath:

### 1. In-app QR / customer-code / card collection

The original, still-real flow for a merchant using itunda's own apps at a physical point of
sale — see `services/backend/merchant/MerchantController.kt`:

- `POST /api/v1/merchant/register` — any authenticated user registers as a merchant with a
  `businessName`. See "KYB" below for the current, more nuanced state of business
  verification.
- `POST /api/v1/merchant/qr/generate` — the merchant creates a payment intent (`amount`,
  `description`); a customer's QR scan resolves to it.
- `POST /api/v1/merchant/collect/{intentId}` — the paying customer collects against that
  intent. Idempotency-Key required, ownership-checked (a merchant can't pay themselves —
  `SELF_PAYMENT_NOT_ALLOWED`), real double-entry ledger legs:

  ```
  DEBIT  payer wallet             full amount
  CREDIT merchant wallet          amount - fee
  CREDIT fee_revenue              fee
  ```

  Fee rate is `feeRate = BigDecimal("0.015")` (1.5%) in `MerchantService.kt`, grounded in
  Toss Payments' own published merchant fee range (0.8%–1.8%).
- Also real: a customer-presented payment code (`POST /api/v1/merchant/pay/customer-code` +
  `charge-by-code`, the reverse direction — customer generates, merchant scans/types), a
  static/fixed merchant QR (`POST /{merchantId}/static-qr/pay`, Kakao Pay 정액 QR-style), a
  card-network charge path (`POST /card/charge`), and a real USSD-typeable numeric alias
  (`ussdCode`) generated alongside every intent so a feature-phone customer with no
  app/browser can complete a payment by phone (`UssdService.handleCompletePayment`).

### 2. "Pay with itunda" external checkout API (`/api/v1/pay/*`)

Real, added 2026-07-21 (`services/backend/merchant/PaymentsApiController.kt`) — the itunda
equivalent of real Toss Payments (docs.tosspayments.com): any external merchant's OWN
backend server integrates this directly, with **zero itunda user login involved anywhere in
the flow**, mirroring Toss Payments' actual documented API-key + hosted-checkout shape.
Genuinely different from Toss Pay's own in-app consumer feature above.

**Auth**: `X-Api-Key` header, a secret key generated once via
`POST /api/v1/merchant/api-key/generate` (returned exactly once, only its SHA-256 hash is
ever stored — see `MerchantService.generateApiKey`). Keys are prefixed `sk_test_` — itunda
Pay has no production/live key tier; every key is honestly a sandbox key, matching this
whole platform's own research/demo nature (see `docs/AI_AGENT_SELF_CHECK.md`). Reissuing a
key keeps the OLD one valid for a real grace period (`previousApiKeyExpiresAt`) so a
merchant's own server fleet can roll out a new key without a hard cutover.

**Real flow**:
1. The merchant's own backend calls `POST /api/v1/pay/payments` with `X-Api-Key` +
   `Idempotency-Key` headers and `{ amount, description, orderId?, successUrl?, failUrl? }`.
   Gets back `{ paymentKey, checkoutUrl, status, expiresAt }` (`pi_<uuid>`, expires in 15
   minutes). `successUrl`/`failUrl` must be absolute HTTPS URLs with no embedded
   credentials (SSRF/open-redirect hardening — `normalizeCheckoutRedirectUrl`).
2. The merchant's website redirects the customer's browser to `checkoutUrl` — a real
   itunda-hosted page (`services/micro-frontends/pay-checkout`, `itunda.pay.checkout-base-url`
   config). Since this moves real money into an itunda account balance, not a card network,
   the customer completes it the same way any other itunda QR/deep-link payment works:
   scanning it with their itunda app. No new payment mechanism was invented — only a new,
   non-interactive way for an external server to create/check the same real
   `PaymentIntent`/`collect()` machinery every in-app flow already uses.
3. The hosted checkout page (`GET /api/v1/pay/checkout/{paymentKey}`, deliberately public —
   no API key, since a browser never holds the merchant's secret) polls for status and
   redirects to `successUrl`/`failUrl` once `collect()` completes or the intent expires.
4. The merchant's own backend independently confirms via
   `GET /api/v1/pay/payments/{paymentKey}` (API-key authenticated, ownership-checked —
   real-404s for a paymentKey belonging to a different merchant) before fulfilling the
   order — "confirm, don't just trust the redirect," the same discipline every real payment
   gateway's docs recommend.
5. `POST /api/v1/pay/payments/{paymentKey}/cancel` — real cancel/refund, mirroring Toss
   Payments' actual cancel API (`cancelReason` required, `cancelAmount` optional — a full
   refund if omitted). Supports repeated partial cancels up to the original amount (tracked
   via `refundedAmount`, not a single all-or-nothing flag) — a real double-entry reversal of
   the original transaction's exact ledger legs, in the same proportion as the cancelled
   amount, never mutating the original historical entry.

`PaymentIntentStatus` is `PENDING → COMPLETED | EXPIRED` (no separate `CANCELLED` state — a
full refund is simply `refundedAmount == amount`).

### Webhooks

Real, persistent, retry-backed delivery (`WebhookDeliveryService`/`WebhookRetryScheduler`) —
a durable-outbox row per delivery, so a multi-hour retry window survives a process restart.
Fires `PAYMENT_STATUS_CHANGED` on completion and `CANCEL_STATUS_CHANGED` on a cancel/refund
(two genuinely separate event types, matching Toss Payments' own real convention, so a
receiver can dispatch on `eventType` alone). Envelope: `{ eventId, eventType, createdAt,
data }`. Retry schedule matches Toss's own documented scheme exactly: up to 7 attempts,
intervals 1, 4, 16, 64, 256, 1024, 4096 minutes (each 4× the last), a ~2.8-day window.
`orderId` is included in every payment webhook so a receiver can correlate back to its own
order record without a second API call.

**Real signature verification, added 2026-08-30** (`WebhookDeliveryService.sign`,
`X-Itunda-Signature` header) — closes a real, live security gap this document itself failed
to flag until this pass: the delivery mechanism above shipped 2026-07-13 with **no way for
a receiver to verify a webhook genuinely came from itunda**, meaning anyone who learned or
guessed a merchant's webhook URL could forge a fake `PAYMENT_STATUS_CHANGED` POST and trick
that merchant's server into fulfilling an order nobody paid for. Fixed the same way every
real payment gateway (Stripe, Toss Payments) solves this: a per-merchant signing secret
(`POST /api/v1/merchant/webhook-secret/generate`, `whsec_`-prefixed, shown exactly once —
wired into the merchant dashboard on all 3 platforms, see below), used to compute
`HMAC-SHA256(rawRequestBody, secret)` once at delivery-creation time. The receiver
recomputes the same HMAC over the raw bytes it received and compares it to the
`X-Itunda-Signature` header — a constant-time comparison is the receiver's own
responsibility, same as any HMAC-verification integration. The signature is computed ONCE
and resent unchanged on every retry (including a manual replay of an exhausted delivery),
so a merchant rotating their secret mid-retry-window can never invalidate an
already-queued delivery. A merchant who never generates a webhook secret gets no
`X-Itunda-Signature` header at all — fully backward-compatible with every existing receiver
that doesn't check for one, at the cost of that merchant having no forgery protection until
they do.

### Merchant dashboard (real, on all 3 platforms)

Contrary to what this document previously (incorrectly) claimed, a merchant does have a
real, working dashboard for all of this — self-signup, credential management, and delivery
observability, not a bare API call with nothing to manage it:

- **Self-signup**: `merchant-mfe`'s `RegisterScreen.tsx` (web), plus native "become a
  merchant" flows on Android/iOS.
- **API key + webhook secret generation, webhook URL config, delivery log + replay**:
  `merchant-mfe`'s `SettingsScreen.tsx` (`ApiIntegrationCard`), Android's
  `BusinessAccountScreen.kt` (`ApiIntegrationCard`), iOS's `BusinessAccountScreen.swift`
  (`ApiIntegrationCard`) — all three call the identical
  `generateApiKey`/`generateWebhookSecret`/`getWebhookDeliveries`/`replayWebhookDelivery`
  endpoints and render the same real delivery history (event type, status, attempt count,
  a "Replay" action for any `EXHAUSTED` delivery).
- **Transactions**: each platform's own merchant order/collection history views (outside
  this document's scope — see `docs/API_SPECIFICATION.md`'s Merchant section).

### KYB (business verification) — a real, separate, opt-in flow

`POST /api/v1/merchant/register` itself still has **no KYB gate** — any authenticated user
can register as a merchant with just a `businessName`, unchanged from before. But a real
KYB verification flow does now exist as a *separate*, opt-in submission
(`DemoIdentityService`/`IdentityService`, `documentType = "BUSINESS_TIN"`): a merchant
submits Rwanda's real, publicly documented 9-digit RRA Taxpayer Identification Number, a
structural pre-check runs automatically, and a human reviewer's approval flips
`Merchant.kybVerified` — see `docs/TOSS_PARITY_MATRIX.md`'s Compliance row for the full
account. It just doesn't block registration itself; a merchant can accept payments
immediately and get KYB-verified afterward, or never.

## What real Toss Payments does (sourced reference, not a description of itunda)

Kept for reference since it's accurate, sourced research and a real target shape to converge
toward — but every claim below is about **Toss Payments**, not itunda. Do not read this
section as describing anything built in this repo.

- **Auth**: `Authorization: Basic base64(secretKey + ':')` — a server-to-server credential.
  Key prefixes: `test_sk_`/`test_gsk_` (secret, sandbox), `live_sk_`/`live_gsk_` (secret,
  production), `test_ck_`/`test_gck_` and `live_ck_`/`live_gck_` (client-safe). Mixing test and
  live keys is a documented `INVALID_API_KEY` error.
  ([docs.tosspayments.com/reference/using-api/api-keys](https://docs.tosspayments.com/reference/using-api/api-keys))
- **Core endpoints** (`docs.tosspayments.com/en/api-guide`, `docs.tosspayments.com/reference`):
  `POST /v1/payments/confirm` (`paymentKey`, `orderId` 6-64 chars, `amount` required),
  `POST /v1/payments/{paymentKey}/cancel` (`cancelReason` required, `cancelAmount` optional),
  `GET /v1/payments/{paymentKey}`. `Idempotency-Key` header, unique, max 300 chars, valid 15
  days.
- **`Payment.status` enum**: `READY` → `IN_PROGRESS` → `DONE` / `CANCELED` /
  `PARTIAL_CANCELED` / `ABORTED` / `EXPIRED` / `WAITING_FOR_DEPOSIT` (virtual-account only).
  Also real: `totalAmount`, `balanceAmount`, `cancels[]`, `receipt.url`, `checkout.url`.
- **Webhooks** (`docs.tosspayments.com/en/webhooks`): real event is `PAYMENT_STATUS_CHANGED`,
  payload `{eventType, createdAt, data: Payment}`. Toss retries a failing endpoint up to 7
  times over exponentially increasing intervals (1 to 4096 minutes), expects HTTP 200 back.

## Not done, and why

- **No card networks or virtual accounts.** The only real payment method is an itunda wallet
  balance — no acquirer or bank virtual-account integration exists to build on top of. This
  isn't a phased-rollout gap; it's the honest full extent of what this platform can move
  today, same as every other money-movement path in this repo (see
  `docs/TOSS_PARITY_MATRIX.md`'s Provider connector row).
- **No KYB gate at registration.** See "KYB" above — a real verification flow exists, it
  just doesn't block anyone from registering and accepting payments before completing it.
- **No production/live API key tier.** Every key is `sk_test_`-prefixed; there is no
  `live_sk_`-equivalent distinction, honestly matching this platform's own research/demo
  status rather than fabricating a "production mode" that would imply real regulatory
  standing this platform doesn't have.
- **Receiver-side signature verification is the merchant's own responsibility.** itunda
  computes and sends `X-Itunda-Signature`; there's no itunda-provided client SDK/library
  that does the receiver-side HMAC comparison for a merchant automatically (unlike, say,
  Stripe's official SDKs) — a merchant's own server has to implement that one comparison
  itself, per this document's own instructions above.

Sources for the Toss-facts section above: [Toss Payments API
keys](https://docs.tosspayments.com/reference/using-api/api-keys), [Payment APIs
guide](https://docs.tosspayments.com/en/api-guide), [Core API
reference](https://docs.tosspayments.com/reference),
[Webhooks](https://docs.tosspayments.com/en/webhooks), [Integrate payment
widgets](https://docs.tosspayments.com/en/integration-widget).
