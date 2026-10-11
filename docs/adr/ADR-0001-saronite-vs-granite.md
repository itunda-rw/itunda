# ADR-0001: Saronite and Granite interoperability strategy

- **Status:** Proposed
- **Date:** 2026-10-11
- **Owners:** Itunda platform
- **Context:** Toss's public Granite framework is the reference for Itunda's mini-app platform. Itunda already has a Saronite SDK, native Android/iOS host integration, and real mini-apps. The decision must be based on repository evidence rather than the assumption that adopting an upstream framework automatically produces parity.

## Context and evidence

Public upstream reference: [toss/granite](https://github.com/toss/granite). Its README describes a React Native framework designed for microservice mini-apps and brownfield integration, small bundles through bundle splitting/ESBuild, routing/build tooling, end-to-end testing, and AWS/Pulumi bundle deployment. Granite is Apache-2.0 licensed according to its public repository at the time of this review.

Itunda repository evidence:

- `packages/saronite/README.md` documents Saronite as an Itunda-owned implementation of the brownfield host/mini-app pattern.
- Saronite has a `SaroniteHostBridge` boundary intended to keep SDK code independent of app internals.
- The documented native bridge includes shell operations plus Itunda-specific backend operations. The README/changelog describe real backend-connected mini-apps for wallet balance, bills, rewards, and insurance.
- Android and iOS have native shells. React Native mini-app runtime integration has surfaced meaningful host/build integration risks, including React Native Gradle plugin/Kotlin compatibility and iOS Fabric/codegen setup.
- The documented remaining platform gaps include production remote bundle/CDN hosting and a fully automated artifact deployment/rollback workflow.
- The root README calls Saronite a hand-rolled approximation rather than a framework built on Granite itself. That wording is not enough to decide adoption: the live integration surface and migration cost must be evaluated.

These statements summarize repository documentation. They do not assert that every item has been freshly re-run on a device in this ADR.

## Decision proposed

**Keep Saronite as Itunda's canonical SDK and native bridge for now; adopt Granite's compatible architectural patterns selectively. Do not replace Saronite wholesale or import Granite runtime packages into production until a measured proof-of-concept passes the gates below.**

This is not a permanent rejection of Granite. It is a staged adoption decision that protects the existing Android/iOS host integration while making the next decision evidence-based.

## What to adopt now

1. **Mini-app isolation:** enforce a stable manifest and SDK contract per mini-app instead of relying on directory naming conventions.
2. **Bundle discipline:** measure each mini-app's production bundle size and startup latency; split shared/runtime code from feature code where it measurably improves startup or update cost.
3. **Routing contract:** define typed deep-link routes, validated parameters, and explicit back-navigation behavior.
4. **Host lifecycle:** model launch, visible, backgrounded, closing, failed, and incompatible states explicitly; handle cancellation and timeouts.
5. **Artifact lifecycle:** add immutable versioned artifacts, checksums, compatibility metadata, staged rollout, and rollback records before remote bundles become a production dependency.
6. **Testing:** run the same mini-app contract tests against native-host and browser-mock adapters.
7. **Deployment:** build a provider-neutral artifact/deployment interface. Granite's Pulumi/AWS infrastructure is a useful reference, but Itunda should not couple itself to AWS when its chosen production environment may differ.

## What not to adopt blindly

- Do not copy Toss's private bridge APIs or imply that public Granite includes Toss's private payment/identity features.
- Do not add a second mini-app runtime just to claim Granite adoption.
- Do not replace Itunda's canonical native shells with a React Native whole-app rewrite.
- Do not make production mini-app execution depend on unsigned or unverified remote JavaScript.
- Do not copy Toss/TDS branding or proprietary UI assets into Itunda Design System.

## Proof-of-concept gates

Before changing the decision to direct Granite adoption, build one isolated, non-production vertical slice and record:

- [ ] Exact Granite version, license, dependency/license scan, supported React Native version, and security posture.
- [ ] A minimal mini-app running inside Itunda's existing Android host without replacing the native shell.
- [ ] Equivalent iOS integration with Fabric/codegen configured through supported integration paths.
- [ ] Typed host bridge for launch parameters, visibility, close/back behavior, and one harmless read-only Itunda API.
- [ ] Feature crash/failure does not crash the host or leak credentials/data.
- [ ] Reproducible build from a clean checkout with lockfiles and documented commands.
- [ ] Measured cold-start time, JS bundle size, memory impact, and native build impact against current Saronite.
- [ ] A written rollback path to the current Saronite implementation.
- [ ] No unsupported claim that a passing local build proves production CDN, signature verification, staged rollout, or rollback.

A comparison should include both a Granite-based spike and the current Saronite baseline. If the Granite spike wins on maintainability and operational reliability without destabilizing native integration, revisit the decision with measurements.

## Consequences

**Positive**
- Protects the working Itunda-specific mini-app integration while reducing architecture-by-imitation risk.
- Turns Granite adoption into a reversible, testable engineering decision.
- Prioritizes production gaps that matter regardless of runtime: artifact integrity, compatibility, observability, and rollback.

**Trade-offs**
- Saronite continues to carry some custom maintenance burden in the short term.
- Granite-specific tooling benefits are delayed until the proof-of-concept justifies adoption.
- The platform still needs a separate implementation plan for production bundle hosting and secure remote delivery.

## Follow-up implementation

1. Inventory current Saronite bridge methods, manifest format, navigation contract, and package boundaries.
2. Add a versioned mini-app manifest and compatibility validator.
3. Create the Granite proof-of-concept in an isolated branch/workspace; do not alter the production runtime during comparison.
4. Implement artifact checksum/metadata verification and deployment smoke tests independently of runtime choice.
5. Publish benchmark results and decide whether to adopt Granite, reuse selected packages/patterns, or retain Saronite.

## Sources

- [Granite public repository and license](https://github.com/toss/granite)
- [Itunda Saronite documentation](https://github.com/itunda-rw/itunda/blob/main/packages/saronite/README.md)
- [Itunda architecture](https://github.com/itunda-rw/itunda/blob/main/docs/ARCHITECTURE.md)
- [Itunda Toss parity matrix](https://github.com/itunda-rw/itunda/blob/main/docs/TOSS_PARITY_MATRIX.md)
