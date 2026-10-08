# Itunda Platform Parity Blueprint — 2026

Status: active implementation blueprint
Branch: `agent/toss-parity-2026`

## Goal

Itunda should provide a **Toss-class product and developer platform**, adapted to Rwanda and using the Itunda identity.

"100% like Toss" means parity of the **publicly observable product/platform model**, not copying proprietary Toss code, private infrastructure, trademarks, assets, or undocumented internal implementation.

The target is one coherent platform:

1. Consumer super-app
2. Business/merchant platform
3. Developer platform
4. Mini-app runtime and SDK
5. Sandbox and test tooling
6. Design system
7. API platform
8. Documentation and technical blog
9. Internal operations tooling
10. Release, analytics, observability and experimentation infrastructure

## 1. Product architecture

### Consumer

The consumer app is the primary shell. It should make the next useful action obvious and keep financial state understandable.

Core domains:

- Identity and phone authentication
- Home / overview
- Wallet and ledger
- P2P transfers
- Mobile-money and bank rails
- QR payments
- Bills and airtime
- Merchant payments
- Contacts
- Requests / split bills / gifts
- Savings
- Credit
- Investments
- Insurance
- Rewards
- Marketplace / neighborhood
- Messaging
- Maps / mobility
- Certificates / public services
- Notifications
- Search and discovery
- Support

Each domain must have an explicit ownership boundary and an API contract. New UI must not create a second source of truth.

### Business

`business.itunda.im` is the business product and `business-app.itunda.im` is the application surface.

Business capabilities:

- Merchant onboarding and verification
- Business profile
- QR collection
- Payment intents
- Transactions
- Refunds
- Settlement
- Payouts
- Staff and roles
- Receipts
- Customers
- Products/catalog
- Orders
- Reports
- Disputes
- Notifications
- API credentials
- Webhooks
- Developer integrations

### Developer platform

`developers.itunda.im` should be the entry point for:

- API reference
- SDKs
- Authentication
- Webhooks
- Errors
- Idempotency
- Versioning
- Changelog
- Guides
- Quickstarts
- Mini-app development
- Sandbox
- CLI
- Console
- Release management
- Security and compliance requirements

The developer experience should follow the public pattern visible in Toss Payments and Apps in Toss: documentation is part of the product, not a separate afterthought.

## 2. Backend architecture

### Canonical rule

The system is organized around **bounded contexts**, not a giant collection of HTTP endpoints.

Required foundational domains:

- identity
- account
- ledger
- payment
- provider-routing
- merchant
- settlement
- risk
- compliance
- notification
- support
- analytics
- developer
- mini-app platform

Money movement must use:

- double-entry ledger
- idempotency
- explicit transaction state machines
- immutable audit events
- provider attempt records
- reconciliation
- settlement records
- request IDs
- distributed tracing
- deterministic error contracts

### Service boundary rule

A service owns its data and contract. Cross-domain writes are forbidden except through explicitly defined commands/events.

Use synchronous calls only where the user-facing transaction requires an immediate result.

Use Kafka/event infrastructure for:

- transaction events
- settlement
- notifications
- analytics
- reconciliation
- risk signals
- audit propagation
- asynchronous provider callbacks

### Reliability model

Every critical request should have:

- request ID
- idempotency key where applicable
- bounded timeout
- retry policy
- circuit-breaker policy
- structured error
- trace context
- audit event

The target is graceful degradation, not a false promise that every dependency is always available.

## 3. Mobile/super-app architecture

The public Toss architecture provides a strong reference: a native host can embed independently developed React Native feature bundles, with brownfield integration and dynamically loaded mini-apps. Toss has open-sourced Granite under Apache-2.0.

Itunda should therefore converge on:

- Native Android host
- Native iOS host
- IDS shared design primitives
- Saronite as the Itunda mini-app platform
- independently buildable feature bundles
- shared runtime bundle
- dynamic bundle loading
- bundle integrity metadata
- CDN delivery
- rollback
- staged release
- runtime capability/permission model

Do not rewrite the entire native host as a web application.

Do not make every feature a permanently linked native module when it can safely be an independently released mini-app.

## 4. Saronite SDK

Saronite should become a real platform contract rather than a collection of helpers.

SDK domains:

- auth
- identity
- navigation
- environment
- permissions
- storage
- location
- camera
- contacts
- clipboard
- haptics
- payments
- notifications
- analytics
- partner/accessory controls
- events
- deep links
- sharing
- network state

Every SDK method needs:

- TypeScript types
- Android bridge contract
- iOS bridge contract
- web/mock implementation
- permission behavior
- error model
- versioning policy
- example
- automated test
- compatibility status

Public SDK packages should expose only stable contracts.

## 5. Mini-app lifecycle

A mini-app must move through:

`create -> develop -> local mock -> sandbox -> validate -> upload -> compile -> test device -> staged release -> production -> rollback`

The Itunda CLI should provide this workflow.

Target commands:

```text
itunda auth login
itunda app create
itunda app dev
itunda app validate
itunda app build
itunda app upload
itunda app test
itunda app release
itunda app rollback
itunda app logs
```

The existing `tools/itunda-miniapp` should evolve into this CLI instead of becoming a second developer workflow.

## 6. Sandbox

The sandbox is a first-class environment.

It must provide deterministic:

- test users
- wallets
- balances
- merchants
- payment methods
- provider responses
- webhook delivery
- failures
- time manipulation
- idempotency scenarios
- authentication states
- permissions
- notifications

Developers must be able to simulate:

- success
- insufficient funds
- provider timeout
- provider decline
- duplicate request
- expired payment
- webhook retry
- settlement delay
- refund
- partial failure

Sandbox data must never reach production.

## 7. API platform

Public APIs use a versioned contract:

`https://api.itunda.im/v1/...`

Every resource should define:

- authentication
- scopes
- request schema
- response schema
- error schema
- idempotency
- pagination
- rate limits
- webhook events
- examples
- SDK examples

Recommended resource families:

```text
/v1/customers
/v1/accounts
/v1/wallets
/v1/payments
/v1/payment-intents
/v1/transfers
/v1/merchants
/v1/orders
/v1/refunds
/v1/payouts
/v1/settlements
/v1/webhooks
/v1/identity
/v1/kyc
/v1/notifications
/v1/mini-apps
/v1/releases
```

Use OpenAPI as the source of truth.

Generate SDK types and reference documentation from the contract where practical.

## 8. Itunda Pay

The payment platform should be a standalone product surface inside the larger Itunda ecosystem.

Client/server separation:

- client SDK creates or starts payment
- server verifies authoritative amount and state
- server confirms/finalizes payment
- webhooks communicate asynchronous state

Never trust the client for final amount, account ownership, settlement status or payment completion.

This follows the same important contract exposed by Toss Payments' public API documentation: client-side initiation is separate from server-side confirmation, and idempotency is part of the payment contract.

## 9. Design system — IDS

IDS is the product language, not merely a component package.

Layers:

1. Primitive tokens
2. Semantic tokens
3. Component tokens
4. Components
5. Patterns
6. Product templates
7. Accessibility rules
8. UX writing rules
9. Motion rules
10. Platform implementations

Required shared surfaces:

- Android
- iOS
- Web
- Mini-app web
- Business
- Developer console

Rules:

- Itunda Indigo remains the canonical Itunda brand color.
- Existing canonical Itunda brand assets remain untouched and are reused.
- Do not import Toss assets or branding.
- Use Toss as a UX/engineering reference, not as an asset source.
- Prefer hierarchy and whitespace over borders.
- Avoid unnecessary cards, dividers and visual noise.
- Light and dark themes are first-class.
- Accessibility scaling is first-class.
- Reduced-motion behavior is first-class.

## 10. Web properties

The domain architecture is intentionally product-separated:

| Host | Purpose |
|---|---|
| `itunda.im` | Main company/product home |
| `app.itunda.im` | Consumer web application |
| `business.itunda.im` | Business product marketing/entry |
| `business-app.itunda.im` | Business application |
| `developers.itunda.im` | Developer platform |
| `tech-blog.itunda.im` | Engineering/technology blog |

Host routing must be explicit.

A host must never silently render another product's application.

Every site should have:

- independent navigation
- product-specific metadata
- product-specific analytics
- canonical URLs
- sitemap
- robots policy
- error pages
- loading/splash behavior
- light/dark support where appropriate
- shared IDS foundations

## 11. Internal tools

Internal systems are products.

Required surfaces:

### Operations Console

- payments
- transfers
- settlements
- provider health
- incidents
- reconciliation
- customer support
- risk review

### Risk Console

- fraud signals
- suspicious activity
- velocity
- account/device risk
- manual review
- decision history

### Developer Console

- applications
- API keys
- OAuth/client credentials
- webhooks
- events
- logs
- sandbox
- releases
- team access

### Mini-app Console

- app registration
- metadata
- permissions
- bundle versions
- integrity hashes
- staged rollout
- release status
- rollback
- analytics

### Design/Content Console

- design-token releases
- component documentation
- UX copy
- localization
- experiments

## 12. Observability

Every product surface should converge on one observability model:

- logs
- metrics
- traces
- request IDs
- release IDs
- service version
- user-safe error IDs

Money movement additionally requires:

- ledger audit
- provider attempt
- reconciliation state
- settlement state

A customer-facing error should expose a safe support reference, never internal stack traces.

## 13. Release engineering

Every release should have:

- immutable artifact
- SHA-256
- manifest
- release ID
- build provenance
- automated tests
- security checks
- staged rollout
- rollback target

Mini-app bundles and native releases should share the same release-integrity vocabulary.

## 14. Security baseline

Mandatory:

- no plaintext production secrets
- short-lived access tokens
- refresh rotation
- device/session risk
- least-privilege scopes
- API key rotation
- webhook signing
- replay protection
- idempotency
- audit logs
- rate limiting
- abuse controls
- dependency scanning
- secret scanning
- SBOM/provenance where available

## 15. Product quality gates

A feature is not "real" merely because a screen exists.

Use these statuses:

- `real`: independently verified implementation
- `demo`: implemented but uses mocked/local data
- `target`: designed but not implemented
- `blocked`: requires external licensing/provider/certification
- `deprecated`: intentionally replaced
- `experimental`: behind an explicit flag

Every parity item must name:

- owner
- source of truth
- implementation location
- test
- verification method
- remaining dependency

## 16. Public-source benchmarking rules

Use public Toss material as engineering evidence:

- official Toss technical articles
- official Toss developer documentation
- official Toss GitHub repositories
- official public npm packages
- Apps in Toss public examples/documentation

Do not treat:

- random clones
- screenshots
- reverse-engineered private code
- unofficial "Toss source code"
- speculative architecture posts

as authoritative facts.

When a Toss capability is not publicly documented, mark the Itunda decision as an engineering choice rather than claiming "Toss does this."

## 17. Implementation order

### Phase A — Platform foundation

1. IDS token/component parity
2. canonical API/OpenAPI contracts
3. error + idempotency standards
4. Saronite SDK contract
5. mini-app CLI
6. sandbox runtime
7. developer console foundations

### Phase B — Super-app runtime

1. native host
2. shared runtime
3. dynamic bundles
4. bundle integrity
5. release service
6. CDN
7. rollback
8. analytics

### Phase C — Product depth

1. money movement
2. merchant
3. bills
4. savings
5. credit
6. rewards
7. marketplace
8. messaging
9. public services
10. additional Rwanda-native services

### Phase D — Operating platform

1. operations console
2. risk console
3. reconciliation
4. settlement
5. support
6. incident management
7. experimentation
8. release analytics

### Phase E — Ecosystem

1. public SDKs
2. developer onboarding
3. partner APIs
4. webhooks
5. mini-app marketplace/discovery
6. developer analytics
7. certification/review workflow

## 18. Definition of done for "Toss-class Itunda"

Itunda reaches the target when:

- consumer, business and developer surfaces are independently addressable
- the native host can load independently released feature bundles
- Saronite provides a stable capability SDK
- developers can build against a deterministic sandbox
- APIs are versioned and OpenAPI-described
- payment flows have server-authoritative confirmation
- webhooks are signed, replay-safe and observable
- releases are immutable and rollbackable
- approved partner bundles are verified by SHA-256 and size before runtime loading
- mini-app release history is immutable and supports staged activation and rollback targets
- IDS is shared across all product surfaces
- internal tools cover support, risk, reconciliation and releases
- product events have a common analytics contract
- architecture boundaries are enforced automatically
- every "real" feature has an executable verification path
- Rwanda-specific rails and regulatory requirements are treated as first-class constraints

This is the target architecture for Itunda. It is intentionally Toss-class without becoming a Toss clone.
