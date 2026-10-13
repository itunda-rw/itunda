# Itunda Pay — Toss Pay-inspired product roadmap

**Purpose:** make Itunda Pay feel as effortless as Toss Pay while keeping Itunda's own identity, Rwanda-first needs, and honest payment capabilities. This is a product and engineering acceptance plan, not a claim that every target is already shipped.

## Product boundary: two different products

- **Itunda Pay (consumer):** the Pay tab, wallet balance, pay-in-store, QR/customer-code, send/request money, bills, rewards, payment history and safety controls.
- **Pay Itunda (merchant platform):** merchant registration, API keys, hosted checkout, payment-intent API, cancellations/refunds, webhooks and merchant operations.
- Keep consumer and merchant experiences separate. Share the underlying payment intent, ledger, risk and event infrastructure where appropriate; do not merge their navigation or permissions.

Toss Pay is a consumer payment experience; Toss Payments is a merchant payment gateway. They are useful related references, but they are not interchangeable. See the official [Toss Pay overview](https://toss.im/en-us/business/tosspay), [Toss Payments API guide](https://docs.tosspayments.com/en/api-guide), [widget integration](https://docs.tosspayments.com/en/integration-widget), and [public SDK/sample repositories](https://github.com/tosspayments).

## Non-negotiable principles

1. **One obvious next action.** Prioritize scan/pay, send, and payment history by context. Secondary features should not compete with the primary task.
2. **Short checkout.** Remember the user's selected eligible funding source, show the final total before authorization, and remove repeated data entry where the user has explicitly consented.
3. **Trust before conversion.** Show merchant identity, amount, currency, fees, and funding source before confirmation. Require a deliberate confirmation for money movement.
4. **Real status, never optimistic success.** UI success must follow a committed backend/ledger result. Pending, failed, expired, reversed, and refunded states must be distinct.
5. **Retry safely.** Every money-moving request needs server-side idempotency; concurrent requests must not double-debit. Retries with the same key and same body replay the original result; key reuse with a different body is rejected.
6. **Accessible by default.** Large tap targets, screen-reader labels, dynamic type, reduced motion, clear error recovery, and full light/dark theme support.
7. **Itunda-native identity.** Use Itunda's approved logo and current IDS tokens; do not copy Toss branding, proprietary assets, or exact screen artwork.
8. **No fake rails.** Do not label a wallet-only flow as card, bank, MTN MoMo, or Airtel Money unless the corresponding licensed/provider integration is actually configured and end-to-end verified.

## Current capability baseline (verify against code before changing)

- The consumer Pay surface and native Android/iOS Pay experiences already expose real wallet-backed payment and transfer components.
- Merchant QR/customer-code collection uses the existing payment-intent and ledger machinery.
- Pay Itunda has a sandbox-only external checkout API, merchant API keys, cancellation/refund support, signed webhooks, rate limits, and a merchant dashboard.
- The documented payment method available today is an Itunda wallet balance. Card acquiring, virtual accounts, and production/live merchant credentials are not available merely because API shapes resemble a gateway.
- The repo has existing payment, ledger, idempotency, webhook, merchant, and consumer UI code. Extend these canonical paths; do not create a second wallet or parallel ledger.

Primary source of truth: [PAYMENTS.md](./PAYMENTS.md), [TOSS_PARITY_MATRIX.md](./TOSS_PARITY_MATRIX.md), and the backend modules under `services/backend/merchant`, `services/backend/core`, and `services/backend/p2p`.

## Delivery plan and acceptance criteria

### P0 — Correctness and payment safety

- [ ] Audit every consumer Pay action on web, Android, and iOS for a real destination, loading state, disabled state, recoverable error, and duplicate-tap handling.
- [ ] Add or confirm integration tests for two simultaneous collect/confirm requests against the same payment intent: exactly one ledger debit and one successful terminal transition.
- [ ] Verify same idempotency key + same payload replays the original response; same key + changed payload returns a conflict; fresh-key retries cannot spend the same single-use quote/intent twice.
- [ ] Confirm refund/cancel paths reverse the exact original ledger legs and enforce cumulative partial-refund limits under concurrency.
- [ ] Verify the webhook outbox survives restart, signatures cover the exact raw body, retries are observable, and replay cannot change the original event or signature.
- [ ] Ensure the checkout success redirect is never treated as proof of payment; merchant fulfillment must verify payment status server-to-server.
- [ ] Add monitoring for payment failures, duplicate/conflict responses, webhook exhaustion, latency, and ledger imbalance. Alert on ledger imbalance.

**Release gate:** automated tests plus a real test-environment end-to-end run prove balances and ledger entries, not just HTTP response bodies. No production-money claims without independent provider and compliance readiness.

### P1 — Simplify the consumer Pay experience

- [ ] Put the most-used task first for the current context: scan/pay in-store, send money, or payment history. Avoid a wall of equally weighted feature tiles.
- [ ] Make the wallet balance and available funding source clear; show the amount and total debit in the confirmation step.
- [ ] Keep QR scan and customer-code entry as one coherent Pay entry point. Avoid duplicate tiles that open the same capability.
- [ ] Present recent payments with merchant/recipient, time, amount, and a clear status; make a receipt/detail view reachable in one tap.
- [ ] Make failed/pending payments explain what happened and what to do next; do not ask users to retry blindly if status is uncertain.
- [ ] Make refunds and reversals visible in the original transaction timeline, linked to the original payment.
- [ ] Preserve Rwanda-first RWF formatting and localization; avoid showing unlinked external-bank balances as if they were live data.

**Acceptance:** moderated task tests show users can find the correct Pay action, identify the funding source and total, and understand success/failure without coaching. Measure task completion, time-to-pay, abandonment, and accidental duplicate attempts before claiming improvement.

### P2 — Faster repeat payments, only where supported

- [ ] Offer recent merchants/recipients as suggestions, with clear identity and an easy way to correct a mistaken selection.
- [ ] Add a favorite funding source and an optional default only when the account is genuinely eligible; always show it before final confirmation.
- [ ] Introduce saved merchant checkout details or one-tap repeat only with explicit consent, revocation, step-up authentication rules, and a clear limit.
- [ ] Consider rewards/discounts only when eligibility and final price are verified by the backend; disclose conditions before payment.

### P3 — Merchant and developer quality

- [ ] Publish a small, versioned Itunda Pay SDK with typed request/response models, safe error handling, webhook-signature verification, and idempotency helpers.
- [ ] Keep sandbox and live credentials structurally separate. Live mode must remain unavailable until provider contracts, compliance, settlement/reconciliation, secrets, risk controls, and production operations are approved.
- [ ] Provide a self-serve sandbox, replayable test events, sample apps, API explorer, clear error catalog, and copy-paste examples that never expose merchant secrets in client code.
- [ ] Add merchant payment search, status filters, cancellation/refund history, webhook delivery state, and safe replay controls.
- [ ] Version API changes and document deprecations; contract-test SDKs against the real server API.

### P4 — Physical and external payment rails

- [ ] Evaluate real card acquiring, bank rails, MTN MoMo, and Airtel Money as separate provider integrations.
- [ ] For each rail, require a provider contract, sandbox credentials, callback/signature verification, idempotent reconciliation, failure/timeout handling, settlement reports, refund semantics, and end-to-end certification.
- [ ] Use explicit capability flags so unsupported methods never appear as selectable payment options.
- [ ] Do not launch FacePay/biometrics as a payment authorization shortcut without consent, threat modelling, fallback, liveness/anti-spoofing review, and the necessary provider/regulatory approvals.

## Cross-platform release checklist

For each feature, record evidence separately for web, Android, iOS, backend, and merchant tools:

- [ ] Build passes on the relevant platform.
- [ ] Unit and integration tests pass.
- [ ] Accessibility and light/dark checks pass.
- [ ] API contract and authorization tests pass.
- [ ] Concurrent/retry tests verify exactly-once money movement.
- [ ] Test-environment end-to-end balance and ledger deltas are checked.
- [ ] Telemetry, failure recovery, and rollback path are documented.
- [ ] Documentation matches the deployed revision.
- [ ] Any external payment rail is explicitly marked **blocked** until provider and compliance prerequisites are met.

## Definition of “Toss Pay-level quality”

It means equally disciplined simplicity, clear payment confirmation, fast repeat use, transparent status, robust recovery, security, accessibility, observability, and developer ergonomics. It does **not** mean copying Toss's identity or claiming parity with rails Itunda has not integrated. Each item should be closed with a linked commit, test evidence, and deployment verification rather than a checklist edit alone.
