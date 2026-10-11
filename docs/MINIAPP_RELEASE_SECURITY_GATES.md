# Saronite Mini-app Release Security Gates

Status: **implemented, incomplete**. This document records source-level release risks and the evidence required before remote mini-app bundles can be enabled in production. It is not a claim that these gates already pass.

## Current evidence

- Android host: `android/app/src/main/java/rw/itunda/app/miniapps/ItundaApplication.kt`
- Android host package versions: `packages/saronite/host-app/package.json`
- Android release CI: `.github/workflows/ci-cd.yml`
- iOS release CI: `.github/workflows/ios-ci.yml`

The host package currently declares React Native `0.84.0` and Granite packages `1.0.36`. Keep these versions aligned with native Gradle dependencies, Brick codegen, and the iOS Pod graph; package.json alone does not prove that all native components are compatible.

## Blocking findings

1. **Remote JS integrity is partially implemented, not fully release-verified.** Android now requires the Gradle property `miniAppBundleSha256` alongside `miniAppBundleCdnUrl`; the release CDN path stays disabled when the pin is missing. The downloader checks the SHA-256, rejects non-HTTPS URLs and URL credentials, bounds the bundle to 50 MiB, and writes to a temporary file before replacing the cache. This is an integrity pin, not a signed-manifest/key-rotation system; verify the digest is delivered through a trusted release pipeline.
2. **Background acquisition is implemented; runtime verification remains.** The React-host lazy initializer now only selects a previously cached bundle after re-hashing it against the configured SHA-256 pin. A cold cache falls back to the packaged asset. CDN acquisition is scheduled on a dedicated daemon executor during application startup, so OkHttp `execute()` no longer runs in the React-host initialization path. Confirm this behavior with Android startup instrumentation and tests.
3. **Cache promotion and recovery still need tests.** The Android downloader enforces a 50 MiB streaming ceiling, hashes while writing a temporary file, and attempts to preserve the last-known-good cache until the new file is verified. Add tests for replacement failure and interrupted writes to prove recovery on the actual filesystem.
4. **Bundle presence is not release integrity.** The Android CI gate checks that `assets/index.android.bundle` exists and is non-empty; iOS CI checks `main.jsbundle`. These are useful packaging regression checks, not proof of determinism, signature validity, or runtime startup.

## Required acceptance tests

- Reject HTTP URLs, URL credentials, malformed URLs, and invalid digest pins. Pure policy unit tests now cover URL and digest validation in `android/app/src/test/java/rw/itunda/app/miniapps/MiniAppBundleSecurityPolicyTest.kt`; CI must confirm they run and pass.
- Automated downloader integration tests are still required for hash mismatches, empty/oversized bodies, non-success responses, HTTPS-to-HTTP redirects, interrupted writes, timeouts, cache replacement failure, and last-known-good recovery.
- Simulate network timeout, connection loss, cache-write failure, and corrupt partial downloads. The app must load its packaged/last-known-good bundle and must not replace it with an unverified file.
- Assert bundle acquisition runs off the main thread and has bounded total time. Background acquisition and packaged-bundle fallback are implemented; tests and startup instrumentation are still required.
- Build the same source twice in clean runners and compare the resulting bundle hashes; explain any expected nondeterminism.
- Verify the approved artifact digest against release metadata and test key rotation if signed manifests are adopted.
- Run Android and iOS host startup tests against both a valid remote bundle and a failed-download fallback.
- Only enable production CDN configuration after these tests, staged rollout, revocation, and rollback are documented and exercised.

## External reference

Toss's public Granite framework is useful as an architectural reference for a micro-app runtime, not as evidence that Itunda's own native bridge, bundle delivery, or operations are complete: https://github.com/toss/granite

## Current CI state

Snapshot checked against commit `11e78597c59b10195d5cc372466384b972f26b36` on 2026-10-11. This is a point-in-time CI snapshot, not a release certification.

- **Passed:** Cloudflare Pages Developers preview deployment; Cloudflare Pages Tech preview deployment; production-boundary check; dependency review; product-completeness inventory; mini-app manifest validation; shell validation; Actions analysis; Python analysis.
- **In progress:** Android PR validation; iOS CI; JavaScript/TypeScript analysis.
- **CodeQL:** completed successfully in the latest check snapshot.
- **Deployment scope:** Developers and Tech preview deployments succeeded for this branch. This does not prove production custom domains or GCP services are updated.
- **Still blocking mini-app release readiness:** downloader integration/recovery tests, deterministic bundle hash comparison, host startup tests for valid remote bundles and fallback paths, signed release metadata/key-rotation decision, and exercised staged rollout/revocation/rollback.

Check the live run results before making a release decision: [PR #38](https://github.com/itunda-rw/itunda/pull/38). A successful manifest check or bundle-presence check is not proof of end-to-end runtime readiness.
