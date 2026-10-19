# Toss Benchmark Research and Itunda Platform Roadmap

**Status:** Proposal for incremental, evidence-based implementation  
**Owner:** Itunda platform  
**Benchmark rule:** Reuse public engineering lessons and documented patterns; do not copy Toss branding, proprietary assets, private source code, or product behavior that is not publicly documented.

## Why this exists

Itunda is building a Rwanda-first consumer and business platform. The objective is not literal duplication of Toss. It is to reach a comparable level of clarity, reliability, developer experience, operational discipline, and cross-platform consistency while preserving Itunda's own identity and market needs.

This roadmap complements the existing [Itunda Design System](../itunda-design-system.md) and IDS v2 component completion contract in `client/design-system/README.md`. It does not replace the canonical brand assets, Indigo `#7472F4`, or the existing architecture decisions.

## Public Toss engineering references reviewed

These are public repositories and documentation, not a claim that Toss's private product source is available:

- [Toss GitHub organization](https://github.com/toss) — public engineering projects include Granite, frontend-fundamentals, slash, and es-toolkit.
- [Toss Frontend Fundamentals](https://github.com/toss/frontend-fundamentals) — public guidance on code quality, bundling, accessibility, and debugging.
- [Toss Granite](https://github.com/toss/granite) — public React Native framework described as supporting microservice apps and brownfield adoption. Study its documented modularity and integration approach; do not assume its internals are a drop-in fit for Itunda.
- [Toss UI](https://github.com/toss/toss-ui) — public UI package repository; inspect its license and current maintenance state before borrowing any implementation.
- [Toss Payments Browser SDK](https://github.com/tosspayments/browser-sdk) — a public TypeScript monorepo example for SDK packaging and release discipline.
- [Toss Payments API reference](https://docs.tosspayments.com/reference) and [developer portal](https://developers.tosspayments.com/) — examples of structured API references, runnable API testing, sandbox onboarding, integration examples, and release guidance.

Public repositories have their own licenses and scopes. Any reuse must be license-compatible, attribution-compliant, and isolated from Itunda's proprietary or independently authored code. The Payments repositories describe payment products, not the full Toss consumer-app backend.

## Principles

1. **Itunda identity stays canonical.** Use the official Itunda brand ZIP and existing logo assets unchanged. Use IDS tokens and Indigo `#7472F4`; never substitute Toss assets.
2. **Audit before adding.** Find and improve the existing source of truth instead of creating duplicate packages, token systems, APIs, workflows, or deployment paths.
3. **Contract before implementation.** Define request/response schemas, errors, auth, idempotency, pagination, versioning, and ownership before clients independently implement a feature.
4. **One user outcome at a time.** Prioritize the highest-friction end-to-end journeys and represent only capabilities that actually exist.
5. **Secure by default.** Separate public, user, business, and operator access. Keep secrets server-side; use least privilege, audit trails, rate limits, and explicit authorization.
6. **Measure the experience.** Track startup success, API latency/error rate, task completion, accessibility, and deployment health. Establish baselines before setting targets.
7. **Ship in small verified changes.** Prefer focused pull requests with automated checks and rollback paths over a broad rewrite.

## Workstreams and acceptance criteria

### 1. Product experience and IDS

- Audit the actual consumer app on Android, iOS, and web for onboarding/PIN recovery, home, money movement, identity verification, marketplace, chat, offline handling, and account/security.
- Map each journey to the existing IDS v2 patterns and shared semantic tokens.
- Standardize loading, empty, error, retry, success, disabled, offline, and permission-denied states.
- Verify keyboard navigation, visible focus, screen-reader labels, contrast, large text, reduced motion, and Kinyarwanda/English long-copy behavior.
- Remove competing local tokens and one-off components only after references and migrations are verified.

**Done when:** the same journey has consistent behavior and meaning on supported platforms, with automated checks and documented exceptions.

### 2. Architecture and service ownership

- Produce a repository-backed service inventory: owner, responsibility, API/event contracts, datastore, secrets, dependencies, health checks, SLOs, and deployment target.
- Preserve the modular monorepo unless measured team/release boundaries justify splitting it.
- Define explicit boundaries between identity, profile/MyData, money/payment integrations, marketplace, chat, business, notifications, and platform infrastructure.
- Use REST for synchronous resource operations and events for durable asynchronous workflows where appropriate. Define retries, dead-letter handling, idempotency, and schema evolution.
- Document database ownership and migration/rollback practices. Do not introduce Redis or another platform component without a demonstrated requirement and an approved architecture decision.

**Done when:** every production service has an owner, contract, health signal, data ownership statement, and tested deploy/rollback procedure.

### 3. API platform

- Publish an OpenAPI contract for each supported public/internal API, with examples and machine validation in CI.
- Standardize error envelopes, correlation/request IDs, pagination, validation errors, idempotency for mutation endpoints, and deprecation policy.
- Generate or validate typed clients from contracts where appropriate; do not hand-maintain conflicting copies.
- Provide webhook signing, delivery retries, replay protection, event versioning, and a visible delivery log where webhooks are exposed.
- Separate public API documentation from internal operational documentation and never publish secrets or private endpoints.

**Done when:** contract checks prevent unintended breaking changes and every documented endpoint has success and error examples.

### 4. Saronite SDK and integration experience

- Inventory existing SDK source and supported platforms before adding new packages.
- Define a small stable core: configuration, authentication handoff, networking, typed errors, logging/redaction, cancellation, retries, and environment selection.
- Keep platform-specific APIs idiomatic for Kotlin/Android, Swift/iOS, and TypeScript/web.
- Publish semantic versions, compatibility policy, changelog, migration guide, sample apps, and reproducible release artifacts.
- Avoid embedding server secrets or treating an SDK as a security boundary.

**Done when:** a clean sample app can integrate the supported flow from documented steps and pass a maintained compatibility test matrix.

### 5. Developer portal and sandbox

- Create one developer journey: create project → choose sandbox → obtain test credentials → run sample → inspect request/response → test failure paths → read production checklist.
- Sandbox data and credentials must be isolated from production; clearly label simulated capabilities and prohibit real transfers in test mode.
- Add API explorer, copyable examples, SDK installation snippets, test identities, seeded fixtures, request IDs, and deterministic error scenarios.
- Add rate limits, credential rotation/revocation, abuse monitoring, and an explicit path to production access.
- Make documentation version-aware and test examples in CI where feasible.

**Done when:** a new developer can complete a safe, end-to-end test integration without manual database edits or access to production secrets.

### 6. Websites and domain boundaries

Keep the intended products distinct:
- `itunda.im` — consumer-facing product story and entry point.
- `app.itunda.im` — consumer app.
- `business.itunda.im` — business information and conversion.
- `business-app.itunda.im` — business product.
- `developers.itunda.im` — developer documentation and sandbox entry.
- `tech-blog.itunda.im` — engineering articles.
- `api.itunda.im` — API endpoint only, not a consumer website.

Use host-aware routing only when it deterministically maps each hostname to the intended application. Add deployment smoke tests that request every hostname and verify expected title, canonical URL, asset paths, route behavior, and response status. Avoid deploying all hostnames to one generic shell as a substitute for separate product experiences.

**Done when:** automated post-deploy checks verify each hostname and a failed check blocks promotion or triggers the documented rollback.

### 7. Internal operations and reliability

- Provide role-based operator tools for service health, deployments, incidents, support cases, webhook delivery, feature flags, and audit events.
- Separate read-only observability from privileged mutations; require confirmation and audit records for high-impact actions.
- Standardize structured logs, traces, metrics, correlation IDs, dashboards, alert ownership, and incident runbooks.
- Use feature flags and staged rollout for risky changes; define rollback and data-repair steps before release.
- Avoid exposing personal data in logs and dashboards; redact tokens, PINs, phone numbers, and other sensitive fields.

**Done when:** an operator can diagnose a failed journey using a correlation ID and follow a documented, access-controlled remediation path.

## Suggested implementation order

1. **Baseline and critical-path audit:** inspect current source, deployments, DNS/host routing, CI, and the customer journey. Record reproducible failures instead of guessing.
2. **Customer journey reliability:** fix launch/login/PIN and connection-error handling, then verify recovery and offline states on real devices.
3. **IDS parity:** enforce token/component parity and implement the highest-impact shared product patterns.
4. **API contracts and service inventory:** publish authoritative contracts and ownership boundaries.
5. **Developer portal and sandbox:** deliver one complete, testable sample flow before broadening the API catalog.
6. **Operations and release safety:** post-deploy smoke tests, observability, rollback, and operator access controls.
7. **Expand product features:** ship only after security, localization, support, and operational readiness are defined.

## CI and release gates

- Design token parity across web, Android, and iOS.
- Lint, type checks, unit tests, contract validation, and dependency/license checks.
- Accessibility checks for key routes and components.
- Secret scanning and authorization tests for sensitive endpoints.
- Build verification for each supported platform affected by the change.
- Host-specific post-deploy smoke tests for every configured production hostname.
- Release notes, migration notes, and a rollback procedure for externally visible changes.

## Decision log

- Itunda remains Itunda; the benchmark is engineering quality, not brand imitation.
- The canonical Itunda logo ZIP and approved assets remain the only logo source of truth.
- IDS remains the cross-platform design foundation.
- Preserve the existing modular monorepo and current infrastructure choices unless repository evidence or measurements justify a change.
- Do not claim a service, API, sandbox, deployment, or product feature is complete until source and runtime checks verify it.

## Research links

- https://github.com/toss
- https://github.com/toss/frontend-fundamentals
- https://github.com/toss/granite
- https://github.com/toss/toss-ui
- https://github.com/tosspayments/browser-sdk
- https://github.com/tosspayments/payment-sdk-android
- https://github.com/tosspayments/payment-sdk-ios
- https://docs.tosspayments.com/reference
- https://developers.tosspayments.com/
