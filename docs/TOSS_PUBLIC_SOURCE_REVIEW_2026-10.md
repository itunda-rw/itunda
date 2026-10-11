# Toss Public Source Review and Itunda Improvement Backlog (2026-10)

## Purpose and boundaries

Use Toss's publicly inspectable code and developer materials as evidence for engineering patterns. Itunda must keep its own identity, canonical Brand v3 assets, Rwanda-first product requirements, and API contracts. This is a comparative engineering review, not a claim that Toss's private production backend is public. Do not copy Toss branding, proprietary assets, or private implementation.

**Evidence rule:** public repositories demonstrate only what is present in those repositories. They do not prove the design of Toss's entire production platform. For every Itunda change, record the current code path, tests, owner, and verification result; mark unimplemented capabilities as target, not real.

## Public sources inspected

| Source | What the public source supports | What it does not prove |
| --- | --- | --- |
| [Toss GitHub organization](https://github.com/toss) | Public engineering portfolio, including Granite, es-toolkit, overlay-kit, suspensive, react-simplikit, and frontend-fundamentals. | The full private architecture of the Toss super-app. |
| [toss/granite](https://github.com/toss/granite) | A public React Native framework for mini-app / microservice-style app composition; useful for studying host/runtime boundaries and brownfield integration. | That Itunda should replace its existing Saronite runtime or copy Granite wholesale. |
| [toss/slash](https://github.com/toss/slash) | A large collection of reusable TypeScript/React utilities and package organization patterns. The repository is archived; its own README directs users toward successor packages. | That Slash remains actively maintained or should be adopted as a dependency. |
| [toss/es-toolkit](https://github.com/toss/es-toolkit) | Small, typed, tree-shakable utility design and performance-focused implementation patterns. | That Itunda should add a utility dependency without a measured use case. |
| [toss/overlay-kit](https://github.com/toss/overlay-kit) | Declarative overlay composition as a focused reusable capability. | A complete design system or a substitute for Itunda's IDS. |
| [toss/suspensive](https://github.com/toss/suspensive) | Explicit Suspense and error-boundary primitives for async UI. | That all Itunda apps use the same rendering architecture. |
| [toss/frontend-fundamentals](https://github.com/toss/frontend-fundamentals) | Public frontend quality guidance and discussions. | Private internal standards not present in the public repository. |
| [Toss Payments browser SDK](https://github.com/tosspayments/browser-sdk) and [Android SDK](https://github.com/tosspayments/payment-sdk-android) / [iOS SDK](https://github.com/tosspayments/payment-sdk-ios) | Public payment-integration SDKs and samples from the separate Toss Payments organization. Useful for studying integration ergonomics, error surfaces, and platform-specific packaging. | The internal APIs of Toss super-app banking, or a reusable payment rail for Rwanda. |
| [create-ait-app](https://github.com/toss/create-ait-app) and [Apps-in-Toss harness](https://github.com/toss/apps-in-toss-harness) | Public scaffolding, mock SDK/devtools, and mini-app validation workflow patterns. | That Itunda can run Toss mini-app bundles or should adopt their console. |

## Engineering principles to transfer into Itunda

1. **Small, owned packages.** Publish or version focused SDK modules with explicit dependency boundaries instead of one oversized shared package.
2. **Host/runtime contract first.** Keep Saronite's host bridge, permissions, lifecycle, navigation, events, and error model versioned and testable. A mini-app must not gain capabilities simply because a native implementation happens to expose them.
3. **One interaction contract across platforms.** IDS tokens, component semantics, focus/pressed/disabled states, accessibility targets, loading/empty/error/retry behavior, and motion-reduction behavior must match across web, Android, iOS, and Saronite.
4. **Mocked development is explicit.** Provide a local sandbox with deterministic fixtures and a visible mock / staging / production environment indicator. Never let mock success look like a real payment or identity verification.
5. **Typed API boundaries.** Generate or verify clients from versioned OpenAPI contracts; standardize pagination, idempotency, error envelopes, request IDs, timeouts, retries, and deprecation rules.
6. **Recoverability is a feature.** SDKs and internal tools should expose actionable errors, correlation IDs, diagnostic bundles, and safe retry/recovery paths without leaking secrets or personal data.
7. **Evidence before release.** A green web check is not a native build; a successful build is not proof of runtime behavior; a deployment URL is not proof of source revision parity or production readiness.

## Prioritized Itunda backlog

Status here describes the work to verify next; it is not a claim that a feature is already implemented.

### P0 — Release safety and current source-of-truth

- [ ] Recover the currently stalled Android and iOS CI runs; capture the actual build/test logs and rerun on the current PR head.
- [ ] Keep PR #38 unmerged until Android APK assembly, Android unit tests, iOS simulator build, iOS Release build, packaged mini-app bundle verification, and iOS UI tests have explicit passing results.
- [ ] Reconcile the deployed GCP revision with the tested commit for each public, business, developer, and tech surface. Record service name, revision, image digest, commit SHA, health result, and rollback target.
- [ ] Keep the canonical logo assets immutable; validate the asset manifest and checksums in CI.
- [ ] Test a failed/expired session, offline request, duplicate submit, timeout, and retry path in the login/PIN flow before treating authentication as release-ready.

### P1 — SDK and mini-app platform

- [ ] Publish a versioned Saronite capability matrix: API name, permission, platform support, minimum host version, failure modes, and mock implementation.
- [ ] Add contract tests for host ↔ mini-app messaging, lifecycle, deep links, navigation/back behavior, storage boundaries, and permission denial.
- [ ] Add a deterministic sandbox that supports fixture reset, simulated network failures, delayed responses, and seeded identity/payment states.
- [ ] Require every mini-app manifest to declare permissions, API compatibility, entry point, assets, and rollback-safe version metadata.
- [ ] Build a compatibility suite that runs the same behavioral scenarios against Android, iOS, and web host adapters.

### P1 — API and service contracts

- [ ] Inventory every externally reachable API and map it to an owning service, authentication mechanism, data classification, idempotency policy, timeout, and SLO.
- [ ] Make OpenAPI the reviewable contract for public endpoints; generate clients and fail CI on unapproved breaking changes.
- [ ] Standardize a safe error envelope (code, user-safe message, requestId, retryability) while keeping internal stack traces and secrets server-side.
- [ ] Add consumer-driven contract tests between apps, gateway, backend, and service adapters.
- [ ] Separate demo/stub adapters from production providers at configuration and build boundaries; fail closed if a production environment resolves to a mock.

### P1 — Design system and product UX

- [ ] Keep Brand v3 Indigo #8F89FF and canonical logo assets as Itunda identity; transfer Toss's interaction restraint and hierarchy, not its brand.
- [ ] Extend parity checks beyond token values to button states, form validation, sheets/dialogs, navigation, empty/error/loading states, reduced motion, and accessibility.
- [ ] Add visual regression fixtures for light/dark themes and small/large text; compare web, Android, iOS, and Saronite implementations.
- [ ] Keep the developer design showcase at https://developers.itunda.im/design/ as a live IDS reference, but only call it current after checking its deployed revision and rendered tokens.
- [ ] Audit every product screen for the IDS sequence: value → context → decision → action → recovery.

### P2 — Developer portal, internal tools, and operations

- [ ] Create one discoverable developer path: quickstart, API reference, SDK installation, sample app, sandbox, troubleshooting, version policy, and release notes.
- [ ] Make API keys and test credentials environment-scoped, revocable, least-privilege, and never displayed again after creation.
- [ ] Build a release dashboard that joins commit SHA, CI checks, artifact digest/signature, GCP revision, smoke test, rollout percentage, and rollback status.
- [ ] Add operational views for payment reconciliation, provider health, idempotency conflicts, queue lag, failed webhooks, and audit events, each with RBAC and immutable audit logging.
- [ ] Redact tokens, PINs, national-ID values, payment credentials, and personal data from logs, support bundles, analytics, and screenshots.
- [ ] Document incident playbooks for provider outage, bad release, compromised credential, duplicate payment, data restore, and mini-app revocation.

## Acceptance standard

A backlog item is complete only when all of these are recorded:

- **Source:** changed paths and commit SHA.
- **Contract:** public behavior and compatibility impact.
- **Tests:** exact command/workflow and passing result.
- **Runtime:** relevant staging/device verification, not only compilation.
- **Operations:** metrics, alert, recovery or rollback path where applicable.
- **Evidence:** links to CI, artifact, deployed revision, or reproducible test.

## Current known gate

As of the last verified check for commit 147d319ef9fde18ca83d27759657e9bf1f0bc142, IDS semantic parity and the mini-app manifest contract passed, while Android PR Validation and iOS CI were still reported in progress. The workflow job-log endpoint returned BlobNotFound; native build success therefore remains **unverified**. Recheck the live workflow state before relying on this note.

## Source freshness and maintenance

This is a dated review, not a timeless inventory. Before adopting any dependency or API, re-check its repository's current maintenance status, license, security advisories, release cadence, and compatibility. Prefer learning from public implementation patterns over adding dependencies without a measured need.
