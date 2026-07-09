# itunda Payments

A real merchant payment gateway mirroring **Toss Payments'** actual REST API
contract — researched from `docs.tosspayments.com` (per "toss have thing
like passport, monitoring, internal tools, web tools like payments...
search about toss online let's implement the same. do not imagine"), not
guessed. This document cites what's real, what itunda's version deviates
from and why, and what's built and verified versus still outstanding — the
same discipline `saronite/README.md` follows.

## A note on `MERCHANT_SERVICES.md`

This repo already had a `MERCHANT_SERVICES.md` — a pre-existing, aspirational
spec for merchant/B2B features, written without checking a real payment
gateway's actual API. It's a useful example of exactly the failure mode this
document is trying to avoid: it specifies `Authorization: Bearer
MERCHANT_API_KEY` (Toss's real scheme is `Basic` + a base64-encoded secret
key, not a bearer token) and invented webhook event names like
`payment.received`/`payment.refunded` (Toss's real event is
`PAYMENT_STATUS_CHANGED`, with a `status` field inside the payload, not a
family of differently-named events). Nothing in that document was ever
implemented. itunda Payments (this document) is the real version, checked
against Toss's actual public docs.

## What Toss Payments actually does (sourced, not guessed)

- **Auth**: `Authorization: Basic base64(secretKey + ':')` — a
  server-to-server credential, deliberately separate from any end-user
  session. Key prefixes: `test_sk_`/`test_gsk_` (secret, sandbox),
  `live_sk_`/`live_gsk_` (secret, production), `test_ck_`/`test_gck_` and
  `live_ck_`/`live_gck_` (client, safe to expose to a browser). Test and
  live keys must not be mixed — doing so is a real, documented
  `INVALID_API_KEY` error.
  ([docs.tosspayments.com/reference/using-api/api-keys](https://docs.tosspayments.com/reference/using-api/api-keys))
- **Core endpoints** (`docs.tosspayments.com/en/api-guide`,
  `docs.tosspayments.com/reference`):
  - `POST /v1/payments/confirm` — `paymentKey`, `orderId` (6-64 chars),
    `amount` required. Finalizes a payment a customer already
    authenticated/authorized for.
  - `POST /v1/payments/{paymentKey}/cancel` — `cancelReason` required,
    `cancelAmount` optional (defaults to full remaining balance).
  - `GET /v1/payments/{paymentKey}` — retrieve.
  - `Idempotency-Key` header (unique, max 300 chars, valid 15 days) —
    real, documented requirement for POST idempotency.
- **The real `Payment` object's `status` enum**: `READY` (before
  authentication) → `IN_PROGRESS` (method verified, awaiting confirm) →
  `DONE` (approved) / `CANCELED` / `PARTIAL_CANCELED` / `ABORTED`
  (authorization failed) / `EXPIRED` (not confirmed within the validity
  window) / `WAITING_FOR_DEPOSIT` (virtual-account-only). Also real:
  `totalAmount`, `balanceAmount` ("취소할 수 있는 금액(잔고)" — the amount still
  cancelable), `cancels[]`, `receipt.url`, `checkout.url`.
- **Webhooks** (`docs.tosspayments.com/en/webhooks`): the real event is
  `PAYMENT_STATUS_CHANGED`, payload `{ eventType, createdAt, data: Payment
  }`. Toss retries a failing endpoint up to 7 times over exponentially
  increasing intervals (1 to 4096 minutes) and expects HTTP 200 back.

## What itunda Payments actually is

Same endpoint paths, same field names, same status enum, same auth scheme —
adapted to what itunda's real infrastructure can honestly back:

- **The only real payment method is an itunda wallet balance.** No card
  networks, no virtual accounts — so `WAITING_FOR_DEPOSIT` exists in the
  `PaymentStatus` type for fidelity to the real enum but is never actually
  reachable, and `method` is typed as `'WALLET' | null` rather than Toss's
  real `카드`/`가상계좌`/`간편결제`/etc.
- **A confirmed payment is a real, balanced ledger transaction** —
  `postLedgerTransaction()` debits the payer's actual wallet and credits a
  new `merchant_payments_suspense` ledger account (see
  `backend/src/services/ledger.ts`), the same double-entry system every
  other real money movement in this app already goes through. A canceled
  payment reverses it the same way. This was verified live, not just
  written: see "Verified" below.
- **Settlement reuses the existing `MerchantSettlement` model** (already
  displayed on the internal-tools System page) instead of inventing a
  parallel "merchant wallet" concept — a confirmed payment pushes a real
  settlement record with a 2.1% fee (matching the fee rate already modeled
  elsewhere in `system.controller.ts`'s rail data), and it actually shows up
  in that page's existing "Merchant Settlement" section.
- **No separate widget/SDK.** Real Toss Payments has a client-side widget
  that handles the customer's authentication/method-selection step. itunda
  has no such SDK yet, so `checkout.url` points at a real page in itunda's
  own web app (`src/pages/Checkout.tsx`, route `/pay/checkout/:paymentKey`)
  where the payer logs into their real itunda account and approves with
  their real wallet — that approval *is* the authorization step, via a new
  itunda-specific endpoint with no direct Toss equivalent:
  `POST /v1/payments/:paymentKey/authorize` (JWT-authenticated, the payer,
  not the merchant).

### Real endpoints

| Method | Path | Auth | Mirrors |
|---|---|---|---|
| `POST` | `/v1/payments` | Merchant (Basic) | itunda-specific: creates the checkout session Toss's widget would otherwise create client-side |
| `GET` | `/v1/payments/checkout/:paymentKey` | Public | itunda-specific: lets the checkout page load order details pre-login |
| `POST` | `/v1/payments/:paymentKey/authorize` | Payer (JWT) | itunda-specific: the payer's approval step |
| `POST` | `/v1/payments/confirm` | Merchant (Basic) | Real Toss endpoint, same fields |
| `POST` | `/v1/payments/:paymentKey/cancel` | Merchant (Basic) | Real Toss endpoint, same fields |
| `GET` | `/v1/payments/:paymentKey` | Merchant (Basic) | Real Toss endpoint |
| `GET` | `/v1/merchants/me` | Merchant (Basic) | Real Toss concept (Developer Center merchant profile) |
| `POST` | `/v1/merchants/webhook-url` | Merchant (Basic) | Real Toss concept (Developer Center webhook registration), API instead of a dashboard form |

`GET /v1/merchants/me` deliberately never returns a secret key back — not
even the caller's own. Toss's real dashboard shows both key pairs to a
human who's logged in via a session, a different auth model than a caller
who's only proven they hold one API key; echoing secrets back over an API
that already succeeded on a secret key is an unnecessary amplification
of what a leaked/misused key can extract.

## Built and verified in this pass

- Full lifecycle driven live against the running backend, not just written
  and assumed: `POST /v1/payments` (create) → payer login → `POST
  /v1/payments/:paymentKey/authorize` → `POST /v1/payments/confirm` →
  confirmed the payer's real wallet balance dropped by exactly the payment
  amount → `POST /v1/payments/:paymentKey/cancel` → confirmed the balance
  came back exactly.
- **A real bug caught by actually retrying, not just writing the code**:
  the first version checked `payment.status === 'DONE'` (→ 409) *before*
  checking for an idempotency-key replay, so a client that legitimately
  retried an already-succeeded `confirm` call (a dropped response, a
  timeout — the exact scenario `Idempotency-Key` exists for) got an
  `ALREADY_PROCESSED_PAYMENT` error instead of the original success replayed
  back. Fixed by moving the replay check before the status guards in both
  `confirmPayment` and `cancelPayment`; re-verified live: confirming twice
  with the same `Idempotency-Key` now returns the identical 200 both times
  and only ever debits the wallet once (checked the real balance after each
  call, not just the response body).
- Real webhook delivery verified against an actual local HTTP receiver (not
  assumed from the code): registered a `webhookUrl`, confirmed a payment,
  and captured the real `PAYMENT_STATUS_CHANGED` payload arriving with the
  full `Payment` object inside `data`.
- Auth boundaries verified live: an unknown/wrong secret key gets `401
  UNAUTHORIZED_KEY`; a regular end-user JWT bearer token gets rejected by
  the merchant-only endpoints (wrong scheme entirely, not just wrong role).
- Confirmed the new `merchant_payments_suspense` ledger account nets to
  exactly the sum of currently-confirmed-and-not-fully-canceled payments via
  `GET /system/ledger`'s integrity snapshot — not eyeballed, computed.
- Both backend (`tsc --noEmit`) and web frontend compile clean.

## Not done, and why

- **No merchant self-signup.** One demo merchant (`merchant_1`, "Kigali
  Coffee Co.") is seeded in `database.ts` with real randomly-generated key
  pairs (`crypto.randomBytes`), the same way the internal-tools staff
  account was seeded earlier this session. Building real merchant
  onboarding (business KYC, key rotation, a dashboard to view/regenerate
  keys) is a substantial feature in its own right, not a required part of
  proving the payment gateway itself works.
- **No merchant-facing dashboard UI.** `GET /v1/merchants/me` and the
  webhook-registration endpoint exist and are verified, but there's no web
  page consuming them yet — a merchant currently manages their integration
  via direct API calls (curl/Postman/their own backend), the same way a
  real Toss Payments integration starts before anyone opens the Developer
  Center dashboard.
- **Webhook retries are 3 attempts with a short linear backoff**, not
  Toss's real 7 attempts over up to 4096 minutes — that needs a persistent
  job queue to do honestly (multi-hour delays can't survive a process
  restart as an in-memory `setTimeout`), which is out of scope for a
  single Express process with no job store.
- **No card networks or virtual accounts** — see "What itunda Payments
  actually is" above. This isn't a phased-rollout gap so much as itunda
  having no real integration with any card acquirer or bank virtual-account
  system to build on top of; wallet-based payment is the honest full extent
  of what this platform can move today.

Sources: [Toss Payments API keys](https://docs.tosspayments.com/reference/using-api/api-keys),
[Payment APIs guide](https://docs.tosspayments.com/en/api-guide),
[Core API reference](https://docs.tosspayments.com/reference),
[Webhooks](https://docs.tosspayments.com/en/webhooks),
[Integrate payment widgets](https://docs.tosspayments.com/en/integration-widget).
