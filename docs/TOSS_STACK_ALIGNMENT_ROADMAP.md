# Toss Stack Alignment Roadmap for Itunda

**Status:** proposal / implementation checklist. This document does not claim that proposed migrations are implemented, tested, or deployed.

## Goal

Adopt the strongest verifiable public Toss engineering patterns where they fit Itunda, while retaining Itunda's identity, Rwanda-first product requirements, native Android/iOS shell, and GCP deployment baseline. Do not claim to reproduce Toss's private architecture. Do not copy proprietary code, branding, or assets.

## Evidence rules

- Public source of truth: https://github.com/toss and individual repositories such as https://github.com/toss/granite and https://github.com/toss/frontend-fundamentals.
- Treat each Toss affiliate as a separate system; do not assume Toss Bank, Toss Securities, Toss Payments, and the super-app share one stack.
- Every Itunda stack claim must be labeled **implemented**, **tested**, **deployed**, or **planned** and link to source/CI/deployment evidence.
- No migration is complete until tests run on the latest commit and a rollback path exists.

## Target stack decisions

| Layer | Itunda direction | Decision |
|---|---|---|
| Web | TypeScript, React, existing web MFE boundaries, Yarn 4 Plug'n'Play | Preserve current workspace; improve shared packages, type safety, build boundaries and performance budgets before changing framework versions. |
| Web engineering | Toss Frontend Fundamentals principles: readability, predictability, cohesion, coupling, bundling, accessibility and debugging | Add review/checklist gates to existing CI; adopt the principles, not copied code. |
| Native host | Swift/iOS and Kotlin/Android native shells | Keep native host; validate lifecycle, navigation, authentication, deep links, release bundles and crash handling. |
| Mini-app runtime | Compare public Granite against current Saronite + existing React Native runtime | Run a compatibility and maintenance assessment first. Do not replace Saronite or adopt Granite wholesale without a proof-of-concept, license/dependency review, native build, rollback plan, and signed-delivery design. |
| Mini-app bundles | Independently buildable/versioned feature bundles with shared dependencies where technically safe | Measure compressed size, load latency, cache hit rate, failure fallback and version compatibility; never claim a 200 KB target unless measured. |
| Backend | Existing Kotlin/Spring services and explicit service boundaries | Keep service ownership clear; use schema migrations, OpenAPI/contract checks, authz tests, idempotency, timeouts, bounded retries and observable failure domains. |
| Data | GCP-managed data services as the current infrastructure baseline | Confirm actual deployed database per service; define ownership, migrations, backup/restore drills, encryption and retention. Avoid introducing a database just to match an assumed Toss stack. |
| Event streaming | Kafka only where asynchronous events and replay justify operational cost | Define event schemas, idempotent consumers, outbox/inbox where needed, lag alerts and replay procedures. Do not promise active-active multi-region without a tested design. |
| Cloud / delivery | GCP, Cloud Run and existing GCP-managed services | Keep GCP as source of truth. Do not migrate to AWS merely because Granite documents AWS deployment examples. Build provenance, staged rollout, smoke tests and rollback are mandatory. |
| Design system | IDS + Itunda Brand v3 across web, Android and iOS | Preserve canonical Itunda logos. Enforce semantic-token parity, contrast, accessibility, reduced motion, component behavior and visual regression. Do not use Toss branding or present private TDS source as public. |
| SDK / APIs | Saronite contracts and versioned backend APIs | Establish one canonical schema per operation, compatibility policy, capability permissions, generated or mechanically checked client types, and cross-platform contract tests. |
| Developer portal / sandbox | Itunda-owned portal, CLI, manifest validation and sandbox | Secure-by-default permissions; deny unknown capabilities; mock APIs explicitly; deterministic diagnostics; reproducible example apps; never enable partner code without end-to-end denial and signing tests. |
| Operations | SLOs, structured logs, distributed tracing, metrics, alerts and incident playbooks | Define user-journey SLOs and trace IDs across gateway/services. Set targets only after baseline measurements. Test restore, rollback and service degradation. |

## Workstreams and exit criteria

### P0 — Establish the actual baseline

- Inventory all workspaces, languages, frameworks, pinned versions, services, APIs, databases, workflows and deployed GCP revisions.
- Classify every capability as implemented, tested, deployed or planned.
- Identify stale/contradictory architecture documents and historical private-cloud scripts without deleting useful historical evidence.
- Exit: baseline report is reproducible from repository and GCP evidence; no unsupported production-readiness claims remain.

### P1 — CI and release reliability

- Make latest-head Android, iOS, web, backend, SDK, API gateway, IDS parity, dependency/security and product-completeness checks required where supported.
- Pin tools consistently (including Tuist) and use lockfiles.
- Generate a release manifest linking commit SHA, artifacts, tests and deployed revision.
- Exit: clean CI on latest commit, native builds succeed, release artifacts are verified, and rollback is rehearsed.

### P2 — API and SDK contract integrity

- Define canonical schemas and error taxonomy for account, wallet projection, Maps, authentication and mini-app bridge calls.
- Test backward compatibility, malformed payloads, timeouts, permission denial, retry behavior and duplicate writes.
- Keep account balance and wallet projection as distinct contracts; do not invent endpoints or combine currencies without FX conversion.
- Exit: backend, web, Android and iOS contract suites pass on the same API specification.

### P3 — Mini-app runtime decision

- Document current Saronite architecture and the existing React Native runtime integration.
- Compare Granite's public features, license, maintenance, platform requirements, bundle model, CLI/build flow, tests and AWS-specific assumptions.
- Build a minimal non-production proof-of-concept behind a feature flag; measure bundle size, startup time, native integration, security and upgrade costs.
- Exit: written go/no-go decision backed by measurements. No production runtime replacement until release bundling, signed delivery, staged rollout, revocation and rollback are proven.

### P4 — Toss-inspired frontend engineering

- Apply Frontend Fundamentals review principles to changed TypeScript/React code.
- Add focused workspace commands, typecheck/lint/test/build checks, dependency-boundary checks and bundle analysis.
- Adopt public Toss libraries only when they solve a real problem, pass license/dependency review, and are compatible with Itunda's runtime. Pin versions and add tests.
- Exit: measurable bundle/build improvements or documented maintainability gains, with no regressions.

### P5 — Product and operational quality

- Finish IDS parity and accessibility checks across platforms.
- Continue Rwanda Maps benchmarks: Kinyarwanda/English/French variants, typo tolerance, zero-result behavior, route validation, latency and network failure.
- Add tracing, SLO dashboards, alert ownership, database restore drills and incident runbooks.
- Exit: user-journey tests and operational drills pass; performance improvements are measured against a baseline.

## Current known gates from recent pull requests

- Maps search benchmark and alternate names: PR #53 — tests and benchmark execution are still required.
- Maps rendering and route reliability: PR #52 — latest-head native CI must confirm the Tuist toolchain correction.
- IDS semantic parity: PR #51 — latest-head Android/iOS/general CI and runtime checks must pass.
- Saronite wallet bridge contract: PR #49 — native builds, real mini-app verification and partner-denial tests remain required.

Do not merge these changes based solely on the existence of source code or tests.

## Public Toss references

- Granite: https://github.com/toss/granite
- Frontend Fundamentals: https://github.com/toss/frontend-fundamentals
- Toss organization and public repository list: https://github.com/toss
- es-toolkit: https://github.com/toss/es-toolkit
- overlay-kit: https://github.com/toss/overlay-kit
- suspensive: https://github.com/toss/suspensive
- Apps in Toss harness: https://github.com/toss/apps-in-toss-harness

## Explicit non-goals

- Do not claim to reproduce Toss's private architecture or exact internal stack.
- Do not copy Toss trademarks, visual identity, proprietary assets, or non-open-source code.
- Do not switch Itunda from GCP to AWS just to mirror Granite's sample infrastructure.
- Do not replace native Android/iOS shells or Saronite before measured compatibility work.
- Do not declare performance, reliability, compliance or production readiness without evidence.
