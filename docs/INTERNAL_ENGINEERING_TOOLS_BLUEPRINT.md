# Itunda Internal Engineering Tools Blueprint

Status: proposal; implementation must be verified against the repository and production environment.
Owner: Itunda engineering
Last updated: 2026-10-13

## Goal

Give Itunda a fast, safe, reproducible developer workflow inspired by publicly documented Toss engineering practices. This is a learning and architecture reference, not a claim that Itunda can reproduce Toss's private internal systems or that Toss uses every tool listed here.

## Public references reviewed

- Toss engineering article, “How Documents Find Developers at Toss” (2026-09-07): https://toss.tech/article/toss-frontend-ai-docs-eng
- Toss public GitHub organization and open-source projects: https://github.com/toss
- Toss open-source showcase site: https://github.com/toss/slash-site
- Apps in Toss community SDK mock/devtools (community project; repository says archived): https://github.com/apps-in-toss-community/devtools
- Apps in Toss SDK reference app: https://github.com/apps-in-toss-community/sdk-example
- Apps in Toss harness and MCP workflow: https://github.com/toss/apps-in-toss-harness

Important distinction: Toss's public repos and engineering articles are evidence for the specific public patterns above. They do not reveal Toss's complete private internal platform.

## Current repository facts to preserve

The repository README currently identifies:
- `services/micro-frontends/ops-mfe/` as the internal admin/operations frontend.
- `services/api-gateway/` as an Express gateway with request IDs, rate limits, circuit breaking, timeouts, security headers, and Prometheus metrics.
- `services/backend/` as the canonical Kotlin/Spring Boot backend, with a separate microservices prototype that is not yet reconciled with it.
- `packages/saronite/` as an in-progress mini-app SDK.
- `services/developer-docs/` as an API reference surface that is not yet deployed.
- `.github/workflows/ci-cd.yml` as the primary CI pipeline, with a separate Woodpecker path.

These are README statements, not a fresh runtime audit. Do not report deployment, CI, or production readiness as verified without current evidence.

## Proposed internal tool suite

### 1. Engineering Control Center

Build on the existing `ops-mfe` rather than creating a competing admin application.

Initial views:
- Repository/PR overview and required-check status.
- Build, test, deploy and rollback history with commit SHA and environment.
- Service health and recent error/latency summaries.
- Incident timeline, runbooks and accountable owner.
- Clear environment labels: local, sandbox, staging, production.

Every displayed state should include source, timestamp and freshness. Unknown or failed requests must render an explicit error state, never a false “healthy”, “empty” or “all clear” state.

### 2. API Workbench

Make the API contract the single source of truth:
- Store versioned OpenAPI specifications beside the service that owns them.
- Validate specs in CI; detect breaking changes against the base branch.
- Generate client types/SDKs and reference docs from the same contract.
- Provide sandbox fixtures and mock servers for deterministic frontend development.
- Add contract tests for gateway-to-service and SDK-to-API compatibility.
- Use stable error envelopes, request IDs, idempotency for money movement, pagination rules and explicit timeouts.

Do not generate duplicate hand-maintained API references that drift from the contract. Do not use production customer data in mocks or test fixtures.

### 3. Saronite SDK Lab

Create a browser-based local harness inspired by the public Apps in Toss mock/devtools pattern:
- Mock authentication, navigation, storage, permissions, location, notifications and analytics behind explicit interfaces.
- Add deterministic fixtures, latency/error injection and resettable local state.
- Show SDK calls, parameters, results and errors in a development-only panel.
- Make mock mode visually unmistakable; block real payments and production credentials in mock mode.
- Test both mocked browser execution and real native integration separately.
- Publish SDK compatibility matrix and migration notes per version.

The public community devtools repository is archived, so use it as a design reference, not as an unqualified new production dependency.

### 4. IDS Design Engineering Lab

Use Itunda's canonical brand assets and IDS tokens as the authority:
- Token schema and semantic-token validation.
- Verify canonical logo assets by manifest/hash; never recreate or silently replace supplied logos.
- Component gallery with states: loading, empty, error, disabled, success and accessibility.
- Contrast, dynamic text sizing, reduced-motion and keyboard/focus checks.
- Screenshot and semantic parity checks across web, Android and iOS.
- Flag hard-coded colors and spacing in governed components.
- Keep brand identity Itunda-owned; learn from Toss's consistency practices without copying Toss assets.

### 5. Developer Knowledge Assistant

Follow the documented Toss principle of making relevant knowledge find developers:
- Search docs, OpenAPI, runbooks, architecture decisions, code ownership and troubleshooting guides.
- Add repository-aware answers with links to the exact source file, line or document section.
- Index only authorized sources and show when content was last synchronized.
- Surface relevant guidance in PR templates, CI failures and local developer commands.
- Treat retrieved documents and code as untrusted content; never let indexed text override security policy or secrets handling.

Start with searchable Markdown and generated API docs before adding an AI layer. Correctness and source attribution come first.

### 6. Release and Reliability Console

- Require build, unit, integration, API contract, security and platform-specific checks appropriate to each changed area.
- Deploy immutable artifacts identified by commit SHA.
- Separate sandbox/staging/production credentials and permissions.
- Add staged rollout, health-based promotion and a documented rollback path.
- Track latency percentiles, error rate, saturation, dependency health and payment-flow outcomes.
- Audit sensitive admin actions; require explicit authorization for production mutations.
- Keep secret values out of logs, browser bundles, screenshots and CI output.

## Suggested architecture

- Existing `ops-mfe`: operator UI and developer control-center views.
- Existing API gateway: authenticated, least-privilege operations endpoints; do not expose raw infrastructure/admin APIs to browsers.
- Backend-owned APIs: deployment metadata, CI summaries and service-health aggregations with bounded timeouts.
- OpenAPI + generated SDK/docs: contract-first API lifecycle.
- GitHub Actions and existing Woodpecker path: consolidate overlapping checks deliberately; avoid two conflicting sources of release truth.
- GCP: keep production workloads and secrets within the existing GCP ownership model; use workload identity/short-lived credentials where available.
- Postgres/MySQL and telemetry storage: choose per existing workload and measured needs; do not add infrastructure solely to imitate another company.

## Delivery order

### Phase 0 — Inventory and evidence
1. Map current ops-mfe routes, gateway endpoints, backend ownership and deployment workflows.
2. Record which checks run on pull requests and which run before production.
3. Label each capability as implemented, partial, stubbed or unverified.
4. Identify duplicate deployment paths and establish one authoritative release status.

### Phase 1 — CI trust
1. Standardize required status checks and branch protection.
2. Emit a machine-readable verification summary with commit SHA, environment, check name, result and timestamp.
3. Add API schema validation and breaking-change detection.
4. Make failed/stale checks block promotion rather than appear green.

### Phase 2 — Local development speed
1. Add deterministic Saronite SDK mocks and an isolated devtools panel.
2. Add fixtures and contract tests.
3. Provide one documented command for bootstrapping the supported local stack.
4. Measure clean setup time and test feedback time before and after changes.

### Phase 3 — IDS quality gates
1. Validate canonical asset manifest and tokens in CI.
2. Add component states and cross-platform accessibility checks.
3. Add screenshot/semantic parity tests for critical flows.

### Phase 4 — Operations and knowledge
1. Add deployment history, service health and runbook links to ops-mfe.
2. Add freshness, source attribution and explicit unknown/error states.
3. Add searchable docs and PR/CI context-aware guidance.

## Acceptance criteria

- Every build/deployment status links to a commit SHA and timestamp.
- A failed or stale required check cannot be interpreted as success.
- SDK mocks can run without a production app or production credentials.
- Payment SDK calls in mock mode cannot move real money.
- API docs and generated SDKs are reproducible from versioned contracts.
- Design-token and canonical-logo checks run in CI.
- Operations actions are authenticated, authorized and audited.
- Critical flows have owner-defined tests and rollback instructions.
- Any parity claim is evidence-backed and scoped; no claim of “100% Toss parity” without a measurable, verified checklist.

## First implementation task

Audit `ops-mfe`, `.github/workflows/ci-cd.yml`, `.woodpecker/`, `services/api-gateway/`, `packages/saronite/` and `services/developer-docs/`. Produce a file-backed gap report before changing runtime behavior. Then implement the smallest safe slice: a reliable CI/deployment status contract with explicit freshness and failure states.
