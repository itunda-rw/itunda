# Itunda Business & Partner Platform

The public Itunda business surface maps to three repository-backed integration paths.

## Pay Itunda

The merchant backend exposes an external payment API under `/api/v1/pay/*`.

Current real flow:
1. A merchant backend uses an `X-Api-Key` and `Idempotency-Key`.
2. `POST /api/v1/pay/payments` creates a PaymentIntent and returns a payment key plus hosted checkout URL.
3. The customer completes payment through the Itunda-hosted checkout surface.
4. The merchant backend confirms the payment server-to-server.
5. Persistent webhooks report payment and cancellation status.

The repository currently issues `sk_test_` keys only. This is therefore an integration/sandbox surface, not a claim that Itunda has a production acquiring network.

## Apps in Itunda

The partner platform is implemented under `services/backend/partners` and `packages/saronite`.

Partner flow:
- `POST /api/v1/partners/register` creates a partner and returns its API key once.
- `POST /api/v1/partners/mini-apps` submits a mini-app manifest.
- A human reviewer can approve or reject the submission.
- Approved apps are published through the mini-app catalog.
- Android already has a `PartnerMiniAppLoader` that downloads an approved bundle, caches it by app ID and launches it through the shared runtime.

Current third-party permission allow-list:
- `account:read`
- `transactions:read`
- `profile:read`

Money-moving partner permissions are intentionally not exposed yet.

## Merchant ecosystem

Itunda also has a real merchant surface:
- merchant registration
- QR payment intent creation and collection
- customer-code payment
- card charge path
- merchant dashboard
- API key and webhook-secret management
- settlement/business-account flows

These are separate from the partner mini-app registry and from the external Pay Itunda checkout API, but they share the platform's payment and ledger foundations.

## Product architecture

The business website should make the three paths obvious:

**Pay Itunda** → integrate payments.

**Apps in Itunda** → build a mini-app and reach users inside the Itunda host.

**Merchant ecosystem** → operate a business and accept payments.

This separation follows the repository's actual implementation instead of presenting every roadmap item as a finished capability.
