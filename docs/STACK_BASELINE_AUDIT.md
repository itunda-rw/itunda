# Itunda Stack Baseline Audit

**Audit date:** 2026-10-13  
**Scope:** repository source and workflow evidence retrieved for this audit. This is not a live GCP infrastructure audit and does not prove production deployment state.

## Verified from repository source

| Area | Evidence found | Assessment |
|---|---|---|
| Root JS workspace | Root `package.json` declares `yarn@4.17.0`, Yarn workspaces, and workspaces for shared utilities, design tokens, web design system, pay widget, micro-frontends, and API gateway. | Existing monorepo foundation; preserve it and enforce workspace boundaries. |
| Workspace exclusions | Root package comments explicitly exclude `packages/saronite` and `services/blog` because each uses its own npm lockfile; Kotlin Gradle backend/microservices are outside the JS workspace. | Multiple package-management domains require explicit, reproducible install/test instructions. |
| Frontend quality tools | Root scripts include Oxlint, TypeScript, dependency-cruiser, IDS audit and monorepo audit. | Prefer tightening existing checks before adding duplicate tooling. |
| Backend | `docs/TOSS_PARITY_MATRIX.md` identifies `services/backend` as Kotlin/Spring Boot, Spring Data JPA/Hibernate, MySQL, Flyway migrations and Spring Security/JWT. | Keep MySQL as the documented canonical backend store unless a measured service-specific case supports change. |
| Service topology | The parity matrix distinguishes the broader Kotlin backend, a smaller microservices prototype, and an API gateway with request IDs, rate limits, security headers, metrics and bounded upstream timeouts. | Resolve ownership and routing boundaries; avoid two services silently becoming competing sources of truth. |
| Mini-app host | The parity matrix documents `packages/saronite` and notes that it already uses real Granite packages in parts of the implementation. | This is not a greenfield decision: inventory existing Granite dependency versions and exact call sites before proposing a replacement. |
| Native / design system | Recent PRs #51 and #52 record Android/iOS work and a Tuist 4.205.0 alignment; PR #49 records Saronite wallet bridge contract alignment. | Treat latest-head CI and real runtime tests as gates; previous-head passes are insufficient. |
| Maps | PR #53 documents alternate names and a Rwanda search-quality benchmark seed; benchmark execution and current-head CI are not yet established in that PR description. | Report fixture coverage separately from measured relevance. |
| Legacy infrastructure scripts | Root `package.json` exposes numerous `private-cloud:*` scripts. Project direction separately states GCP is the active baseline. | Do not execute these scripts as part of a migration without checking their effects; classify as historical/rehearsal tooling or active tooling based on references and CI usage. |

## Toss references checked

- [Toss Granite](https://github.com/toss/granite): public React Native framework for brownfield host integration, split mini-app bundles, ESBuild-oriented build flow, end-to-end testing and AWS-oriented infrastructure examples. The README's 200 KB is a stated framework capability/goal, not an Itunda benchmark result.
- [Toss Frontend Fundamentals](https://github.com/toss/frontend-fundamentals): public guidance for code quality, bundling, accessibility and debugging.
- [Toss public repositories](https://github.com/toss): source for checking available public projects and licenses.

## Immediate next actions

1. Generate a machine-readable inventory from actual manifests and Gradle files: package names, versions, workspace boundaries, lockfiles, native toolchain pins and CI workflow ownership.
2. Map service-to-database ownership and public API routing from source and GCP deployment evidence.
3. Inspect Saronite's installed Granite packages and bridge implementation before any runtime decision.
4. Fetch workflow runs for the latest commits of PRs #49, #51, #52 and #53; fix the latest-head failures and distinguish queued/running from passed.
5. Establish baseline measurements before changing technologies: web bundle sizes, mini-app bundle sizes, app startup, API p50/p95/p99 latency, crash rate and Maps search relevance.

## Guardrails

- A source file is evidence of implementation, not proof of successful runtime behavior.
- A passing older commit does not prove the latest commit passes.
- A workflow run does not prove a change is deployed.
- A GCP service declaration does not prove healthy end-to-end production behavior.
- Never claim exact parity with Toss's private stack. Use only public, sourced patterns and keep Itunda's brand and Rwanda-specific requirements.
