// Source of truth: docs/PAYMENTS.md's "Pay with itunda" section and
// services/backend/merchant/PaymentsApiController.kt / MerchantService.kt. This is a
// direct port of already-accurate internal documentation to a real public-facing page —
// not new content. Keep these two in sync; if PaymentsApiController.kt changes, update
// docs/PAYMENTS.md first, then this file.
export const apiReferenceMarkdown = `
## Overview

"Pay with itunda" lets your own backend server accept real itunda payments — a customer
completes payment inside their itunda app, with **no itunda account login on your site at
any point**. It's a separate product from itunda's in-app consumer features: this is the
itunda equivalent of Toss Payments (docs.tosspayments.com), not Toss Pay.

Every key issued today is a sandbox key — itunda has no production/live tier yet. Nothing
here moves real regulated money; treat this exactly like any other payment gateway's test
mode while you integrate.

## Authentication

Every request is authenticated with a secret API key in the \`X-Api-Key\` header. Generate
one from your own itunda merchant account:

\`\`\`
POST /api/v1/merchant/api-key/generate
Authorization: Bearer <your itunda user JWT>
\`\`\`

The raw key is returned exactly once — only its hash is ever stored, so there's no way to
retrieve it again later. Reissuing a key keeps the old one valid for a real grace period, so
you can roll a new key across your own server fleet without a hard cutover.

## The real flow

1. **Create a payment.** Your backend calls \`POST /api/v1/pay/payments\` with your API key
   and a fresh \`Idempotency-Key\` header (required on every mutating call — safe to retry
   the exact same request on a timeout without double-charging anyone).

   \`\`\`
   POST /api/v1/pay/payments
   X-Api-Key: sk_test_...
   Idempotency-Key: <a fresh UUID per attempt>
   Content-Type: application/json

   {
     "amount": 5000,
     "description": "2 espresso",
     "orderId": "your-own-order-id",
     "successUrl": "https://yoursite.example/orders/123/success",
     "failUrl": "https://yoursite.example/orders/123/failed"
   }
   \`\`\`

   \`successUrl\`/\`failUrl\` must be absolute HTTPS URLs with no embedded credentials.
   You get back:

   \`\`\`
   { "success": true, "paymentKey": "pi_...", "checkoutUrl": "<your configured checkout host>/checkout/pi_...", "status": "PENDING", "expiresAt": "..." }
   \`\`\`

   The payment intent expires in 15 minutes if nobody pays it.

2. **Redirect the customer.** Send their browser to \`checkoutUrl\`. Since this moves real
   money into an itunda account balance (not a card network), they complete it the same way
   any other itunda payment works — scanning it with their itunda app.

3. **The hosted checkout page polls status itself** (\`GET /api/v1/pay/checkout/{paymentKey}\`,
   public, no API key — a browser should never hold your secret key) and redirects to your
   \`successUrl\`/\`failUrl\` once payment completes or the intent expires.

4. **Confirm on your own server before fulfilling the order.** A browser redirect alone is
   never proof of payment — always independently confirm:

   \`\`\`
   GET /api/v1/pay/payments/{paymentKey}
   X-Api-Key: sk_test_...
   \`\`\`

5. **Cancel or refund** — full or partial, repeatable up to the original amount:

   \`\`\`
   POST /api/v1/pay/payments/{paymentKey}/cancel
   X-Api-Key: sk_test_...
   Idempotency-Key: <a fresh UUID per attempt>
   Content-Type: application/json

   { "cancelReason": "Customer requested", "cancelAmount": 2000 }
   \`\`\`

   Omit \`cancelAmount\` for a full refund.

## Endpoints

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | \`/api/v1/pay/payments\` | \`X-Api-Key\` + \`Idempotency-Key\` | Create a payment, get a \`checkoutUrl\` |
| GET | \`/api/v1/pay/payments/{paymentKey}\` | \`X-Api-Key\` | Confirm payment status from your own server |
| POST | \`/api/v1/pay/payments/{paymentKey}/cancel\` | \`X-Api-Key\` + \`Idempotency-Key\` | Cancel or partially refund |
| GET | \`/api/v1/pay/checkout/{paymentKey}\` | none (public) | What the hosted checkout page itself calls |

\`PaymentIntentStatus\` is \`PENDING → COMPLETED | EXPIRED\` — there's no separate
\`CANCELLED\` state; a full refund is simply \`refundedAmount == amount\`.

## Rate limiting

Limits are per merchant, per minute: 30 for creating a payment or a cancel/refund, 120 for
reading one back. Every response — successful or not — carries:

| Header | Meaning |
| --- | --- |
| \`X-RateLimit-Limit\` | The limit for whichever endpoint you just called |
| \`X-RateLimit-Remaining\` | How many calls you have left in the current window |
| \`X-RateLimit-Reset\` | Seconds until the window resets |

Check these before you hit a 429, not just after. A real 429 also carries the standard
\`Retry-After\` header (the same seconds-until-reset value).

## Webhooks

Register a \`webhookUrl\` on your merchant account and itunda will POST you real, durable,
retry-backed delivery notifications instead of making you poll:

- \`PAYMENT_STATUS_CHANGED\` — fired when a payment completes
- \`CANCEL_STATUS_CHANGED\` — fired on a cancel/refund

Envelope: \`{ eventId, eventType, createdAt, data }\`. Your own \`orderId\` is included in
every payment webhook so you can correlate it back to your order record without a second
API call. Retries run up to 7 attempts (1, 4, 16, 64, 256, 1024, 4096 minutes apart — about
2.8 days total) if your endpoint doesn't return a 2xx.

**Always verify the signature.** Generate a webhook secret (\`POST
/api/v1/merchant/webhook-secret/generate\`, shown exactly once) and check the
\`X-Itunda-Signature\` header on every delivery: it's \`HMAC-SHA256(rawRequestBody, secret)\`,
computed once and resent unchanged on every retry. Recompute the same HMAC over the raw
bytes you received and compare with a constant-time comparison — that check is your
responsibility, the same as integrating any HMAC-signed webhook. Without a registered
secret, deliveries simply carry no signature header at all, so there's nothing stopping
someone else from forging a fake completion notification to your endpoint.

## Errors

Every error response is \`{ "success": false, "code": "...", "message": "..." }\` with a
matching HTTP status:

| Code | Status | Meaning |
| --- | --- | --- |
| \`API_KEY_REQUIRED\` | 401 | Missing \`X-Api-Key\` header |
| \`INVALID_API_KEY\` | 401 | Key doesn't match any merchant (or its grace period expired) |
| \`IDEMPOTENCY_KEY_REQUIRED\` | 400 | Missing \`Idempotency-Key\` on a POST that requires one |
| \`INVALID_CHECKOUT_REQUEST\` | 400 | Bad amount, description, or redirect URL |
| \`PAYMENT_NOT_FOUND\` | 404 | Unknown \`paymentKey\`, or it belongs to a different merchant |
| \`PAYMENT_NOT_REFUNDABLE\` | 409 | Trying to cancel something that isn't in a cancellable state |
| \`IDEMPOTENCY_KEY_CONFLICT\` | 409 | Same \`Idempotency-Key\` reused with a different request body |
| \`RATE_LIMITED\` | 429 | Too many requests — back off and retry |

## Questions

This is an early, actively-developed API. If something here doesn't match what you see in
practice, the internal reference (\`docs/PAYMENTS.md\` in the itunda repository) is the
canonical source — this page is generated from it and may occasionally lag by a commit or
two.
`;
