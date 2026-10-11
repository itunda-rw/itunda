# Saronite Mini-app Release Security Gates

Status: **implemented, incomplete**. This document records source-level release risks and the evidence required before remote mini-app bundles can be enabled in production. It is not a claim that these gates already pass.

## Current evidence

- Android host: `android/app/src/main/java/rw/itunda/app/miniapps/ItundaApplication.kt`
- Android host package versions: `packages/saronite/host-app/package.json`
- Android release CI: `.github/workflows/ci-cd.yml`
- iOS release CI: `.github/workflows/ios-ci.yml`

The host package currently declares React Native `0.84.0` and Granite packages `1.0.36`. Keep these versions aligned with native Gradle dependencies, Brick codegen, and the iOS Pod graph; package.json alone does not prove that all native components are compatible.

## Blocking findings

1. **Remote JS integrity is not verified.** The Android CDN loader currently accepts a URL and executes the downloaded bundle through the React host. HTTPS protects transport but does not prove the artifact is the approved release. Require a release-manifest SHA-256 pin or a signed manifest verified with a pinned public key before selecting a downloaded bundle.
2. **The download is synchronous.** `downloadAndCache` calls OkHttp `execute()` from the lazy React-host initialization path. Verify the call is never made on the main thread; preferably move acquisition to a bounded background startup stage with a packaged-bundle fallback.
3. **The download lacks an explicit byte ceiling and verified temporary-file promotion.** Enforce a maximum bundle size while streaming, hash while writing to a temporary file, and only promote the file after successful verification. Preserve the last-known-good bundle until the new one passes verification.
4. **Bundle presence is not release integrity.** The Android CI gate checks that `assets/index.android.bundle` exists and is non-empty; iOS CI checks `main.jsbundle`. These are useful packaging regression checks, not proof of determinism, signature validity, or runtime startup.

## Required acceptance tests

- Reject HTTP URLs, URL credentials, invalid/missing digest pins, hash mismatches, empty bundles, oversized bodies, non-success responses, and HTTPS-to-HTTP redirects.
- Simulate network timeout, connection loss, cache-write failure, and corrupt partial downloads. The app must load its packaged/last-known-good bundle and must not replace it with an unverified file.
- Assert bundle download/verification runs off the main thread and has bounded total time.
- Build the same source twice in clean runners and compare the resulting bundle hashes; explain any expected nondeterminism.
- Verify the approved artifact digest against release metadata and test key rotation if signed manifests are adopted.
- Run Android and iOS host startup tests against both a valid remote bundle and a failed-download fallback.
- Only enable production CDN configuration after these tests, staged rollout, revocation, and rollback are documented and exercised.

## External reference

Toss's public Granite framework is useful as an architectural reference for a micro-app runtime, not as evidence that Itunda's own native bridge, bundle delivery, or operations are complete: https://github.com/toss/granite

## Current CI state

CI run status is tracked on PR #38. A successful dependency review, manifest contract, completeness inventory, or audit-script job does not substitute for the in-progress Android release and iOS release bundle gates. Record the final run URLs and outcomes here after completion.
