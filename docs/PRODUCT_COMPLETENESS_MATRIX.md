# Itunda Product Completeness Matrix

**Status:** Baseline and acceptance contract; not a claim that every capability is implemented.
**Last reviewed:** 2026-10-13
**Purpose:** Make each Itunda product independently useful, production-ready, measurable, accessible, and consistent with the Itunda ecosystem. Use public Toss engineering projects as reference implementations for engineering patterns, not as a source of proprietary code or brand assets.

## Rules

1. Itunda identity is canonical. Use the Brand v3 assets and IDS token source; do not recreate or substitute official logos.
2. A product is not complete because its screen exists. Completion requires the end-to-end user journey, loading/empty/error/offline states, authorization, telemetry, accessibility, documentation, automated tests, release controls, and verified production behavior.
3. Mark each capability **Implemented**, **Tested**, **Deployed**, or **Planned** separately. Never infer one from another.
4. Every external integration needs an owner, contract, timeout/retry policy, observability, failure behavior, and a documented rollback.
5. Keep the current GCP deployment baseline, native Android/iOS shells, and Saronite/Granite integration unless repository evidence and measured outcomes justify a change.
6. Do not enable partner mini-app permissions, payment operations, or production writes based only on browser mocks. Native authorization and backend enforcement must be tested.
7. Prefer shared contracts and the monorepo while they reduce drift. Split a service or repository only when ownership, release independence, security, scale, or distribution justifies it.

## Product-level definition of done

Each product must satisfy all applicable gates:

- **Purpose and user journey:** a clear audience, one primary job, supported locales (Kinyarwanda first where user-facing), and a complete success path.
- **State coverage:** loading, empty, success, validation, permission denied, offline, timeout, server error, retry, and recovery states.
- **Trust and safety:** server-side authorization, least-privilege scopes, secret isolation, input validation, rate limiting, auditability for sensitive actions, and safe failure defaults.
- **API and data:** versioned contract, schema validation, consistent errors, pagination/idempotency where relevant, timeouts, compatibility policy, migration and rollback plan.
- **Design and accessibility:** IDS tokens/components, Brand v3, light/dark theme parity, responsive layout, keyboard/screen-reader support, contrast, large text, and reduced-motion support.
- **Quality:** unit, contract, integration, end-to-end, regression, and relevant visual/accessibility tests; flaky tests tracked rather than ignored.
- **Operations:** structured logs, metrics, traces where supported, dashboards, actionable alerts, ownership, SLOs, runbooks, backup/restore where applicable, and tested rollback.
- **Delivery:** reproducible build, dependency/license/security checks, environment separation, staged release, health verification, and documented release evidence.
- **Documentation:** user help, developer setup, API/SDK reference, troubleshooting, changelog, and known limitations.
- **Evidence:** commit SHA, exact CI runs, test results, environment and timestamp; no completion claim without current evidence.

## Product portfolio acceptance matrix

| Product / surface | Core user outcome | Completeness requirements | Primary evidence |
|---|---|---|---|
| Itunda consumer app (Android/iOS) | Reliably access Itunda services | Onboarding and identity, sign-in/PIN recovery, session expiry, home/navigation, settings, notifications, offline/error recovery, app lifecycle, accessible UI, crash diagnostics, release rollback | Native CI, device tests, auth/API E2E, store/release checks |
| Identity and MyData | Understand and control personal identity/data | Consent and purpose, verified identity state, profile edit, device/session management, revoke/export/delete where applicable, audit trail, privacy-safe logs, account recovery | Security tests, consent contract tests, native flow tests |
| Wallet and account | See accurate account information and complete permitted actions | Authoritative balance source, currency-safe aggregation, transaction states, idempotency, pending/failed/reversed flows, reconciliation and authorization | Backend contract tests, native bridge tests, sandbox and controlled E2E |
| Payments and transfers | Make a transaction safely and understand its status | Quote/fees before confirmation, recipient validation, step-up auth where required, duplicate prevention, pending/failed/success states, receipts, reconciliation, limits and audit trail | Idempotency tests, failure injection, reconciliation evidence, security review |
| Maps and location | Find places and navigate reliably in Rwanda | Kinyarwanda/English/French query handling as supported, alternate names, typo/zero-result behavior, geolocation consent, route geometry validation, network/provider outage handling, attribution and caching policy | Rwanda benchmark results, API contract tests, Android/iOS/web parity |
| Neighborhood marketplace | Discover and transact with nearby people/businesses | Listings lifecycle, search/filter/sort, location, seller profile, chat/contact, reporting/moderation, fraud controls, image upload limits, listing expiry, blocked/deleted states | Listing lifecycle E2E, moderation tests, performance and abuse tests |
| Communication and chat | Communicate reliably and safely | Delivery/read state if supported, retries, offline queue, attachment validation, block/report, notification preferences, privacy, pagination and retention policy | Multi-device E2E, failure/reconnect tests, abuse/security review |
| Mini-app platform / Saronite | Run first-party and approved partner experiences safely | Versioned manifest, CLI lifecycle, local mock host, permission/capability inspection, correlated request/event tracing, native transport parity, bundle validation, signing/integrity, registration/review, staged channels, rollback, kill switch | Manifest contract CI, Android/iOS bridge tests, partner-denial tests, release drill |
| Mini-app developer console | Register, configure, test, and release apps | App/project lifecycle, keys and scopes, environment separation, test scenarios, bundle validation, release history, review status, rollback, audit log, clear failure messages | Console E2E, API authorization tests, audit-log assertions |
| IDS design system | Deliver consistent accessible UI on every platform | Canonical tokens, component behavior contracts, semantic parity, contrast, keyboard/focus, dynamic type, dark mode, reduced motion, visual regression, documented usage and migration | IDS parity CI, component tests, accessibility/visual reports |
| ItundaFace | Provide consistent identity and reusable UI primitives | One canonical implementation/contract, localization, theme, accessible states, version compatibility, no duplicated identity rules | Cross-platform contract tests, consumer inventory |
| Consumer public site | Explain Itunda and convert visitors to the right next step | Accurate product information, fast/responsive pages, accessibility, SEO metadata, localization strategy, privacy/legal content, analytics consent and broken-link checks | GCP deploy health, Lighthouse/accessibility checks, smoke tests |
| Business site and business app | Help merchants onboard and manage their business | Merchant onboarding, verification status, catalog/orders where available, roles/permissions, error recovery, support paths, mobile usability, operational audit trail | Business journey E2E, permission tests, deployed smoke tests |
| Developers portal | Help developers build and ship mini-apps | Quick start, API/SDK reference, working examples, auth/permission docs, local setup, changelog, troubleshooting, compatibility matrix, status/incident links | Docs link checks, example builds, API reference validation |
| Tech blog / knowledge | Explain technical decisions and product updates | Accurate reviewed articles, accessible reading experience, search/navigation, canonical metadata, code samples that build, content freshness | Link/code checks, accessibility and site smoke tests |
| API gateway and public APIs | Offer predictable, secure integrations | OpenAPI/contracts, versioning, authentication/scopes, quotas, request IDs, consistent error envelope, timeout/retry semantics, pagination, idempotency, deprecation policy, security tests | OpenAPI diff checks, contract tests, gateway/security CI |
| SDKs (Saronite and external) | Make supported APIs safe and easy to consume | Typed public APIs, capability/permission mapping, consistent result/error shapes, compatibility matrix, examples, semantic versioning, deprecation policy, mock parity, native parity | SDK contract tests, package checks, example builds, Android/iOS integration |
| Sandbox and API workbench | Let developers test safely without production side effects | Deterministic fixtures, scenario switching, fake identities/accounts, request/event trace, permission denial, latency/error injection, resettable state, no production secrets/data | Sandbox isolation tests, deterministic replay tests, secret scan |
| Internal engineering tools | Make delivery, debugging, and operations observable | Engineering Control Center, same-commit CI/check status, API Workbench, Saronite SDK Lab, IDS Design Engineering Lab, knowledge search, release/reliability console, access controls and audit trail | Tool E2E, provider pagination/error tests, admin authorization tests |
| Backend services and data | Provide correct, resilient business capabilities | Explicit service ownership, bounded contexts, migrations, transaction/idempotency rules, queue/event retry and dead-letter policy where used, backup/restore, privacy retention, SLOs | Migration CI, service integration tests, load/failure tests, restore drill |
| Observability and operations | Detect and recover from failures quickly | Correlated request IDs, useful structured logs, metrics, traces, redaction, alert ownership, runbooks, incident communication, release health and rollback | Alert drills, dashboard review, incident simulation |
| Security and governance | Prevent unsafe changes reaching users | Dependency/license scanning, secret scanning, threat model for sensitive flows, access reviews, audit policy, data classification, environment separation, supply-chain checks | Security CI, access-control tests, documented review evidence |

## Shared platform contracts

### API envelope

All APIs should document their own versioned schema. Where compatible with existing clients, use a consistent error shape with:
- stable machine-readable `code`
- safe human-readable `message`
- request/correlation ID
- optional field-level validation details
- explicit retryability only when known

Do not leak stack traces, secrets, provider payloads, or personal data in errors. Do not change established response envelopes without a compatibility review and migration plan.

### SDK and capability contract

Every public SDK method must map to a documented protocol operation, capability, permission, result type, error type, and platform support status. Unknown methods, unknown scopes, missing runtime trust, invalid manifests, and bridge failures must fail closed. Browser mocks must share the contract but must never be treated as proof of native security.

### Design contract

IDS is the source of truth for tokens and component semantics. Product-specific UI may compose IDS components but must not silently fork semantic colors, spacing, typography, accessibility behavior, or interaction states. Brand color is an identity/accent token; semantic success/error/warning meaning must remain understandable and accessible, with icon/text/state cues rather than color alone.

### Release contract

A release is complete only after:
1. CI passes on the exact candidate SHA.
2. Required platform and contract checks are green.
3. Security-sensitive changes receive review.
4. Deployment is observed healthy in the target environment.
5. Smoke tests cover critical user journeys.
6. Rollback/kill-switch instructions are available and, for high-risk systems, exercised.
7. Evidence and known limitations are recorded.

## Delivery order

### P0 — Trust and release correctness
1. Resolve current required CI failures/pending checks on existing PRs; never merge based on stale or missing checks.
2. Verify sign-in/PIN recovery and device-management flows end-to-end.
3. Finish Saronite Android/iOS permission enforcement, wallet bridge contract, and explicit partner-denial E2E before partner bundles are enabled.
4. Verify API gateway authentication, rate limits, error contracts, and no-secret leakage.
5. Verify Brand v3 / IDS cross-platform parity and accessibility gates.

### P1 — Product journey completeness
1. Create a source-backed product inventory mapping every shipped app/screen/API/service/site to an owner and critical user journey.
2. Add missing failure, loading, empty, offline, retry, and recovery states to the highest-traffic journeys.
3. Build contract/E2E coverage across Android, iOS, web, backend, and Saronite.
4. Collect actual Rwanda Maps benchmark results before claiming search-quality parity.

### P2 — Developer experience
1. Complete the Saronite local mock host and DevTools: scenario switching, permissions, correlated request/event tracing, and failure injection.
2. Complete the developer console lifecycle: registration, validation, review, release channels, rollout and rollback.
3. Add API Workbench, IDS Design Engineering Lab, and a release/reliability console with role-based access and auditability.
4. Ensure examples are executable and tested against the current SDK.

### P3 — Performance and maturity
1. Establish per-product SLOs and measure cold start, API latency, crash-free sessions, task completion, and failure recovery.
2. Benchmark before adopting tooling or changing stack components.
3. Add progressive rollout, automated rollback triggers where safe, disaster recovery drills, and dependency lifecycle policies.
4. Reassess service boundaries only with observed ownership, scaling, or reliability evidence.

## Public Toss references

- https://github.com/toss
- https://github.com/toss/granite
- https://github.com/toss/create-ait-app
- https://github.com/toss/apps-in-toss-harness
- https://github.com/apps-in-toss-community/sdk-example
- https://github.com/apps-in-toss-ax

Use these as public references for patterns and workflows. Verify each repository's license before reusing code; prefer independently implementing the pattern and retaining Itunda's own design, security model, and product requirements.

## Current evidence to attach before marking complete

For every row, record:
- repository path(s) and owning team/person
- state: Planned / Implemented / Tested / Deployed
- current commit SHA and CI run URLs
- unit/contract/E2E/device test evidence
- production environment and last smoke-test timestamp
- known gaps, severity, next action, and rollback plan

This matrix is a working acceptance contract. It must be updated from repository and production evidence; it is not a statement that every listed capability currently exists.
