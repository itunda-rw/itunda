# itunda Payments

> **Rewritten 2026-07-13.** Every previous version of this document described a Toss
> Payments-mirroring REST gateway (`POST /v1/payments/confirm`, `paymentKey`/`orderId`
> lifecycle, `PAYMENT_STATUS_CHANGED` webhooks, a `Checkout.tsx` payer-approval page,
> `merchant_payments_suspense` ledger account, a 2.1% fee) built against the old Express/
> TypeScript backend (`backend/src/services/ledger.ts`, `database.ts`,
> `system.controller.ts`). **That Express backend no longer exists in this repo at all** —
> `ls backend` returns "No such file or directory" — and a repo-wide grep for
> `paymentKey`/`PAYMENT_STATUS_CHANGED`/`merchant_payments_suspense`/`v1/payments` across the
> real `services/backend` (Kotlin) returns nothing. None of the endpoints, ledger accounts, or
> pages this document used to describe as "built and verified" exist in the current codebase.
> This version documents what actually exists today: a real, but differently-shaped,
> QR-based merchant payment flow in `services/backend/merchant`.

## What real merchant payments actually is today

`services/backend/merchant` (`MerchantController.kt`, `MerchantService.kt`) — a QR-based
collection flow, not a Toss Payments-style hosted-checkout/confirm/cancel REST lifecycle. See
`docs/API_SPECIFICATION.md`'s Merchant section for the full endpoint reference; summarized
here:

1. `POST /api/v1/merchant/register` — any authenticated user registers as a merchant with a
   `businessName`. No KYB/business verification exists — this is a real gap, not a design
   choice (see `docs/TOSS_PARITY_MATRIX.md`'s Merchant row).
2. `POST /api/v1/merchant/qr/generate` — the merchant creates a payment intent (`amount`,
   `description`). This is what a customer's QR scan actually resolves to.
3. `POST /api/v1/merchant/collect/{intentId}` — the paying customer (a different authenticated
   user) collects against that intent. Idempotency-Key required, ownership-checked (a merchant
   can't pay themselves — `SELF_PAYMENT_NOT_ALLOWED`), real double-entry ledger legs:

   ```
   DEBIT  payer wallet             full amount
   CREDIT merchant wallet          amount - fee
   CREDIT fee_revenue              fee
   ```

   Fee rate is a real constant, `feeRate = BigDecimal("0.015")` (1.5%) in `MerchantService.kt`,
   grounded in Toss Payments' own published merchant fee range (0.8%–1.8% depending on
   payment method and merchant tier) — **not** the 2.1% this document previously claimed, and
   there is no `merchant_payments_suspense` account; the fee posts straight to `fee_revenue`
   and the net amount straight to the merchant's own wallet, with no intermediate suspense
   step.

There is no separate "payment" entity with its own lifecycle (`READY`/`IN_PROGRESS`/`DONE`/
`CANCELED`) — a payment intent is either uncollected or collected. There is no cancel/refund
endpoint for merchant collections specifically (general wallet-transfer refunds exist per
`docs/TOSS_PARITY_MATRIX.md`'s customer-support-workflow gate, but nothing merchant-specific).

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

## Gap to close, if itunda ever adopts the real Toss shape

If a future pass decides to converge itunda's merchant payments onto Toss's actual REST
contract (hosted checkout, `confirm`/`cancel` lifecycle, `PAYMENT_STATUS_CHANGED` webhooks)
rather than the current QR-collect model, that is a real, substantial rebuild — not a rename
of the existing endpoints. It would need: a `Payment` entity with the real status enum, a
payer-authorization step separate from merchant collection, a webhook delivery mechanism with
persistent retry (an in-memory retry loop can't survive a process restart — the previous,
now-deleted Express version's 3-attempt linear backoff was itself an acknowledged shortcut,
not a real implementation of Toss's 7-attempt/4096-minute scheme), and a real hosted-checkout
page. None of this is started in `services/backend`.

## Not done, and why

- **No card networks or virtual accounts.** The only real payment method is an itunda wallet
  balance — no acquirer or bank virtual-account integration exists to build on top of. This
  isn't a phased-rollout gap; it's the honest full extent of what this platform can move
  today, same as every other money-movement path in this repo (see
  `docs/TOSS_PARITY_MATRIX.md`'s Provider connector row).
- **No merchant self-signup or dashboard UI.** Registration is a bare API call
  (`POST /api/v1/merchant/register`) with no KYB check; there is no web page for a merchant to
  view transactions, rotate credentials, or configure anything.
- **No webhooks at all.** Not "webhooks with reduced retry count" (as the pre-2026-07-13
  version of this document claimed about the now-deleted Express implementation) — there is
  no webhook mechanism anywhere in `services/backend` today. See
  `docs/API_SPECIFICATION.md`'s "what does not exist" section.
- **No refund/cancel path specific to merchant collections.**

Sources for the Toss-facts section above: [Toss Payments API
keys](https://docs.tosspayments.com/reference/using-api/api-keys), [Payment APIs
guide](https://docs.tosspayments.com/en/api-guide), [Core API
reference](https://docs.tosspayments.com/reference),
[Webhooks](https://docs.tosspayments.com/en/webhooks), [Integrate payment
widgets](https://docs.tosspayments.com/en/integration-widget).
