# Toss-Inspired Platform Roadmap

> Updated 2026-10-11. This roadmap uses Toss's public repositories and documentation as engineering references, not as a claim that Toss's private systems are fully observable. Itunda keeps its own identity, Rwanda-first product decisions, and canonical architecture.

## Reference set

- [toss/granite](https://github.com/toss/granite) — Apache-2.0 React Native microservice framework and brownfield host integration.
- [toss/create-ait-app](https://github.com/toss/create-ait-app) — project scaffolding and repeatable developer setup.
- [apps-in-toss-community/sdk-example](https://github.com/apps-in-toss-community/sdk-example) — executable SDK reference app and browser mock/testing approach.
- [toss/es-toolkit](https://github.com/toss/es-toolkit) — typed, tested reusable package design.
- [Apps in Toss developer center](https://developers-apps-in-toss.toss.im/) — developer onboarding, design guidance, release process, and platform policies.
- [Toss Payments API docs](https://docs.tosspayments.com/en/api-guide) — explicit test/live credential separation and idempotency guidance.

Use upstream code only after reviewing its license, transitive dependencies, threat model, runtime fit, and maintenance status. Never copy Toss branding, proprietary assets, private APIs, or undocumented internal designs.

## Existing Itunda foundations to preserve

The repository already contains substantial work. Do not rebuild these just to imitate a reference:

- **services/backend/** is the canonical Kotlin/Spring Boot + MySQL backend.
- **services/microservices/** is a smaller payment/ledger MSA prototype and must remain explicitly distinguished from the canonical backend until a migration decision is implemented.
- **services/api-gateway/** provides rate limiting, request IDs, security headers, bounded upstream timeouts, and Prometheus metrics.
- **packages/saronite/** is Itunda's mini-app host/SDK; **android/** and **ios/** are native shells.
- **client/design-system/**, **design-system/**, **packages/design-tokens/**, and native IDS implementations are existing design-system surfaces. Consolidate ownership and generated outputs instead of adding a competing token system.
- **services/developer-docs/** and **services/blog/** exist; the developer docs are described in the root README as not yet deployed. Verify current deployment before changing that status.
- Product capabilities have mixed maturity. Use **docs/TOSS_PARITY_MATRIX.md** and **docs/ARCHITECTURE.md** as the status sources; a screen or endpoint is not production-ready merely because it exists.

## Priority 0 — trust, evidence, and a single source of truth

1. Maintain a capability inventory with one status per feature: real, demo, target, stub, superseded, or blocked.
2. For every money-moving capability, link the API contract, ledger invariants, idempotency behavior, ownership checks, provider mode (mock, sandbox, live), and a test proving the result.
3. Mark demo credentials, simulated providers, mock SDK bridges, and non-live payment rails conspicuously in developer tools and docs. Never allow a mock success response to look like a real settlement.
4. Require source-backed claims for statements about Toss. Public code and public engineering articles are evidence; private architecture assumptions are not.
5. Keep the canonical Itunda Indigo and canonical logo assets intact. Brand checksums and brand-integrity CI must not be weakened to accommodate design-token changes.

**Acceptance gate:** a new contributor can determine which implementation is canonical, which flows use simulated rails, and what remains blocked without reconciling conflicting documents manually.

## Priority 1 — super-app shell and mini-app lifecycle

Study Granite's host/mini-app separation, dynamic bundle model, brownfield integration, and small feature bundles. Compare it to **packages/saronite/**, **android/**, and **ios/** before adopting code.

- Write an ADR comparing the current Saronite architecture with Granite's public architecture and license. Explicitly decide whether to adopt Granite, selectively reuse compatible ideas, or keep the current implementation.
- Define a versioned mini-app manifest: app ID, version, entry point, required permissions, supported shell/SDK versions, integrity metadata, and rollback version.
- Make loading, cancellation, timeout, offline fallback, and crash isolation explicit lifecycle states.
- Define a signed artifact and compatibility policy before runtime remote bundle loading is treated as production-ready.
- Keep feature modules independently testable and prevent feature-to-feature imports that bypass stable contracts.

**Acceptance gate:** Android and iOS can load a sample mini-app, report lifecycle/compatibility failures clearly, and prove that a failed feature does not crash the host or expose another user's data.

## Priority 2 — Itunda SDK and developer CLI

Use create-ait-app as a reference for reproducible scaffolding and sdk-example as a reference for executable API examples.

- Publish a versioned Saronite SDK contract with semver, deprecation policy, typed errors, timeout rules, permission requirements, and platform capability detection.
- Build an **itunda create** CLI that generates a minimal mini-app, tests, manifest, sample screen, and CI workflow from maintained templates.
- Generate SDK reference documentation from the same type definitions used to build the SDK; do not hand-maintain a second, drifting API list.
- Maintain a sample app that exercises every public SDK API and can run against both a native bridge and a deterministic browser mock.
- Provide a compatibility matrix for SDK version × Android shell × iOS shell × mini-app runtime.

**Acceptance gate:** a new mini-app builds from a clean checkout using documented commands, and every public SDK method has a typed contract plus a passing native/mock test or a clearly documented platform limitation.

## Priority 3 — SDK sandbox and API explorer

Create an Itunda-owned sandbox inspired by the public Apps in Toss SDK example, not a clone of Toss's service.

- Add an interactive API catalog grouped by auth, navigation, device/environment, permissions, storage, location, camera/media, clipboard, haptics, payments, analytics, and notifications, showing only APIs actually implemented in Saronite.
- Support explicit mock and backend sandbox modes. A future live mode must require server-side authorization and must never be selected implicitly.
- Show request/response examples, schema validation, permission prompts, error codes, latency, and trace/request IDs.
- Make mock data deterministic and resettable; label every simulated result as simulated.
- Seed test users and test balances only in isolated non-production environments. Add rate limits and audit records for sandbox actions.
- Never expose signing secrets, production credentials, real identity documents, or payment-provider secrets to browser code.

**Acceptance gate:** a developer can try each supported SDK API in a browser without a production account, then reproduce the same contract in a native sample app.

## Priority 4 — API platform and integration reliability

- Treat OpenAPI (or an equivalent machine-readable contract) as the source for API docs, typed clients, request/response examples, and compatibility checks.
- Standardize error envelopes, validation errors, pagination, timestamps/time zones, RWF money representation, idempotency keys, and request/trace IDs.
- Add consumer-driven contract tests between web/native clients, gateway, and canonical backend.
- Add gateway circuit breaking and a documented retry policy. Retries for money movement must be safe under idempotency and must never imply that a timed-out payment failed before its final state is reconciled.
- Use an outbox and idempotent consumers for durable cross-service events. Define ownership of each data domain and prohibit cross-domain database writes.
- Keep mock/simulated provider connectors behind the same interface as real provider adapters, with capability flags and unmistakable environment labels.
- Add security tests for authorization boundaries, replay, duplicate requests, rate limits, and sensitive-data redaction.

**Acceptance gate:** generated clients match the API contract, breaking changes fail CI, duplicate payment requests do not duplicate ledger postings, and a provider timeout has a traceable, reconcilable state.

## Priority 5 — IDS design system and product UX

- Keep the Itunda visual identity and canonical assets. Benchmark Toss for information hierarchy, interaction clarity, responsive behavior, accessibility, and consistency—not for copying logos, proprietary assets, or exact branded screens.
- Establish one canonical token source and generate web, Android, and iOS token outputs. Avoid independent hex values and parallel definitions across mini-apps.
- Standardize semantic tokens for light/dark mode, typography, spacing, radii, elevation, focus, pressed/disabled/loading/error states, and reduced motion.
- Treat money movement, identity/KYC, payments, merchant collection, chat, marketplace, and account recovery as documented product patterns—not just collections of atomic components.
- Prefer calm surfaces, purposeful whitespace, low-noise boundaries, clear primary actions, and subtle translucency only where contrast and readability remain strong.
- Add visual regression snapshots for core flows and run accessibility checks at component and page level.

**Acceptance gate:** each core flow passes light/dark, mobile/desktop, keyboard/focus, reduced-motion, and contrast checks, with no product-specific token fork unless documented.

## Priority 6 — developer portal, sites, and internal tools

- Make developers.itunda.im the single starting point for API docs, SDK setup, sandbox, guides, examples, release notes, changelog, and support.
- Publish a getting-started path that reaches a working local sample before requiring cloud credentials.
- Build an internal platform console for service health, deployments, feature flags, SDK versions, mini-app artifacts, sandbox resets, provider health, reconciliation queues, and audit events. Access must be role-based and actions auditable.
- Keep consumer, business, developer, and engineering-blog sites distinct in purpose while sharing IDS tokens and navigation primitives.
- Verify host routing and deployed content for each domain from live HTTP checks; a successful build is not proof that the correct site is deployed to the correct host.
- Use staged deployments, smoke tests, rollback instructions, and a deployment status page for production changes.

**Acceptance gate:** every public host resolves to its intended application, docs examples are executable, and sensitive operational actions require authorization and leave an audit trail.

## Priority 7 — quality and delivery gates

Required CI checks should be incremental and evidence-producing:

- formatting, lint, type checks, dependency/license/security checks;
- unit and contract tests;
- backend integration tests with isolated databases and migrations;
- Android compile/test and iOS build/test where the runner supports them;
- SDK mock/native parity tests;
- accessibility and visual regression checks for critical flows;
- artifact provenance, dependency pinning, SBOM, and secret scanning;
- deployment smoke tests per hostname and a verified rollback path.

A green build only proves the checks that actually ran. Reports must list skipped tests, unavailable runners, mocked dependencies, and unresolved live-integration requirements.

## Execution order

1. Resolve canonical ownership and reconcile stale documentation.
2. Audit Saronite against Granite and publish the ADR.
3. Establish SDK contracts, CLI scaffolding, and executable examples.
4. Build the mock-first sandbox and machine-generated API docs.
5. Harden payment/API reliability and service boundaries.
6. Unify IDS tokens and critical product patterns.
7. Complete host routing, internal operations tooling, and production rollout gates.

## Out of scope

- Copying Toss trademarks, logos, proprietary UI assets, or private source code.
- Claiming full Toss parity before the feature and operational evidence exists.
- Enabling real payments, lending, investments, or insurance without the required Rwanda partner contracts, licensing, credentials, compliance controls, and reconciliation operations.
