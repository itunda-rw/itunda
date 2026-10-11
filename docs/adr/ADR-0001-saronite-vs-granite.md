# ADR-0001: Govern the existing Granite-based mini-app runtime

- **Status:** Accepted
- **Date:** 2026-10-11
- **Owners:** Itunda platform

## Context

Toss's public [Granite framework](https://github.com/toss/granite) is a reference for React Native mini-app runtime, brownfield native integration, bundle splitting, end-to-end tests, and artifact deployment. It is Apache-2.0 licensed in the public repository at the time of this review.

**Important repository correction:** Itunda has already moved beyond a purely hand-rolled Saronite approximation. The current repository's `docs/ARCHITECTURE.md` describes Granite adoption for all four first-party mini-apps, Android and iOS host integration, and the generated `BrickModule` / `GraniteBrownfield` dependencies. The root README and `packages/saronite/README.md` still contain older descriptions that can make the current implementation status appear contradictory. Do not start a second adoption project or replace working integration based on those stale statements.

Other evidence in the repository:
- `packages/saronite/mini-apps/partner-template/manifest.schema.json` defines manifest v1.
- `packages/saronite/mini-apps/partner-template/manifest.json` is the partner template.
- `tools/itunda-miniapp/bin/itunda-miniapp.mjs` provides scaffolding and manifest validation.
- `android/` and `ios/` contain the native hosts and Granite integration.
- The architecture documentation describes real first-party mini-apps for bills, wallet balance, rewards, and insurance.

These are repository/documentation findings. This ADR does not claim that a full clean build, live deployment, or all device tests were rerun as part of this review.

## Decision

**Keep the existing Granite-based runtime and Itunda-owned Saronite APIs/manifest/partner tooling. Do not introduce a competing mini-app runtime. The next priority is to verify runtime/package/documentation consistency, harden manifest validation, and close secure artifact delivery and rollout gaps.**

Granite is the runtime/framework; Saronite is Itunda's SDK and platform contract. They are not necessarily mutually exclusive alternatives.

## Immediate verified issue

The manifest schema uses `"additionalProperties": false` at the root but does not list the `$schema` property, while the actual partner template and CLI-generated manifests include `$schema`. A conforming JSON Schema validator would therefore reject the template's own `$schema` property.

This ADR accompanies a fix adding `$schema` as a URI string property in the schema. Follow-up validation should prove the checked-in template passes the schema and that invalid manifests fail CI.

## Required work

1. **Single source of truth:** reconcile `README.md`, `packages/saronite/README.md`, `docs/ARCHITECTURE.md`, and `docs/TOSS_PARITY_MATRIX.md`. Mark historical incidents and superseded architecture statements as historical; don't leave contradictory present-tense status claims.
2. **Manifest validation:** make the CLI validate all schema constraints, including extra properties, lengths, URL format, permissions, semantic version, and complete light/dark theme palettes. Prefer a standards-compliant JSON Schema validator over a second hand-maintained validator.
3. **Contract tests:** add automated tests for the valid template, missing required properties, unknown permission scopes, duplicate permissions, invalid HTTPS URLs, malformed versions, extra properties, and invalid brand tokens. Run them in CI.
4. **Secure artifact delivery:** require immutable versioned artifacts, checksums, supported shell/SDK compatibility metadata, signed release provenance, staged rollout, and rollback before treating remote bundle delivery as production-ready.
5. **Runtime compatibility:** pin and record the Granite/React/React Native/BrickModule versions together. Verify Android and iOS builds use supported codegen/autolinking paths, and report any manually maintained bridge glue explicitly.
6. **Lifecycle and isolation:** test launch parameters, deep links, foreground/background visibility, close/back behavior, cancellation, offline failure, incompatible bundles, and mini-app crash isolation.
7. **Performance evidence:** benchmark bundle size, cold start, memory, and host build impact. Treat upstream Granite's advertised bundle sizes as a goal/reference, not as a measured Itunda result.
8. **Provider-neutral delivery:** use Granite's public Pulumi/AWS infrastructure as a reference, but keep Itunda's artifact/deployment interface compatible with its actual hosting decision. Do not claim production CDN or rollback until live smoke tests prove it.

## Security boundaries

- Do not copy Toss trademarks, proprietary UI assets, private bridge APIs, or undocumented internal architecture.
- Public Granite does not imply that Toss's private payment/identity bridge APIs are included.
- Partner mini-apps must receive only explicitly granted capabilities; do not expose host credentials or unrestricted native APIs.
- Production bundle loading must fail closed on invalid signatures/checksums, incompatible versions, or revoked artifacts.
- Payment/identity APIs remain subject to Itunda's own authorization, audit, and Rwanda regulatory/provider requirements.

## Acceptance criteria

- The canonical docs agree on whether Granite is integrated and which runtime/version is used.
- The partner template validates against the canonical manifest schema.
- Automated tests cover both valid and invalid manifests and run in CI.
- Android and iOS host builds are reproducible from clean checkouts, with unsupported runner limitations reported honestly.
- Remote artifact rollout, integrity verification, staged release, and rollback are independently tested before production enablement.

## Sources

- [Granite public repository and license](https://github.com/toss/granite)
- [Itunda architecture](https://github.com/itunda-rw/itunda/blob/main/docs/ARCHITECTURE.md)
- [Itunda mini-app manifest schema](https://github.com/itunda-rw/itunda/blob/main/packages/saronite/mini-apps/partner-template/manifest.schema.json)
- [Itunda mini-app developer CLI](https://github.com/itunda-rw/itunda/blob/main/tools/itunda-miniapp/bin/itunda-miniapp.mjs)
