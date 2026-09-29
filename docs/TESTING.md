# Itunda Testing Guide

> **Rewritten 2026-09-03.** The previous version of this file was generic NestJS/Jest
> boilerplate (`@nestjs/testing`, `npm run test:cov`, `npm run db:seed:test`, `npm run
> test:a11y`) that matched nothing in this repo -- there is no NestJS anywhere in this codebase,
> no Jest config, and no `test`/`test:cov`/`test:a11y`/`db:seed:test` script in the root
> `package.json` or any workspace. This version describes the real test tooling that actually
> exists per stack, matching the same "describe what's real, not what a generic template
> assumes" discipline `docs/DEPLOYMENT.md`/`docs/IMPLEMENTATION_GUIDE.md` already apply. See
> `docs/ARCHITECTURE.md` for what's real/demo/target across the codebase.

## Backend (`services/backend`) -- real, Kotest

The canonical backend has real, substantial Kotest + MockK test coverage per module.

```bash
cd services/backend
./gradlew test              # every module's Kotest suite
./gradlew :p2p:test         # one module only, e.g. p2p
```

Tests live alongside each Gradle module (`services/backend/<module>/src/test/kotlin/...`),
using `Given`/`When`/`Then`-style Kotest specs and MockK for mocking repositories/services.
No integration-test or E2E layer exists against a real database -- these are all real unit
tests against mocked dependencies. Coverage varies genuinely by module; there is no enforced
coverage percentage threshold configured anywhere in this repo (Jacoco/Kover are not wired in)
-- don't cite a specific coverage number as a requirement, since none is enforced.

## `services/microservices` -- real, same Kotest convention

```bash
cd services/microservices
./gradlew test
```

Same Kotest/MockK shape as the canonical backend, scoped to `ledger-service`/`payment-service`/
`core-libs`.

## Android (`android/`) -- real, two distinct layers

```bash
cd android
./gradlew test                              # JVM unit tests (ViewModel/logic-level)
./gradlew :app:connectedDebugAndroidTest     # real instrumented UI tests, needs a
                                              # connected emulator or device
./gradlew :architecture-test:test           # Konsist module-boundary check (forbids
                                              # cross-Feature-module impl-to-impl imports)
```

The one real instrumented UI test today is `androidTest/.../FocusOrderTest.kt` -- checks
accessibility focus order against the live semantics tree, the same tree TalkBack reads. See
`docs/GETTING_STARTED.md`'s Android section for the adb-recovery steps if the test orchestrator
(UTP) fails at APK install.

## iOS (`ios/`) -- real, one UI test target

```bash
cd ios
tuist generate --no-open && pod install
xcodebuild -workspace Itunda.xcworkspace -scheme ItundaApp \
  -destination 'platform=iOS Simulator,name=<your simulator>' test
```

The real UI test is `App/UITests/FocusOrderTests.swift` -- checks accessibility focus order
against the live accessibility tree, the same tree VoiceOver reads. `pod install` must be
re-run after every `tuist generate`, not just the first time (see
`docs/GETTING_STARTED.md`'s iOS section).

## Web (`services/micro-frontends/*`) -- no unit-test runner wired in yet

There is genuinely no Jest/Vitest/Testing-Library configuration in any micro-frontend today.
Verification for web changes is:

```bash
cd services/micro-frontends/<workspace>       # e.g. bank-mfe
yarn tsc -b                                    # type-check
yarn vite build                                # production build succeeds
```

Plus, from the repo root:

```bash
yarn workspace <workspace> run lint            # oxlint
python3 scripts/file-size-lint.py              # file-size guideline (frozen baseline + 500-line cap on new files)
python3 scripts/accessibility-lint.py <changed files>   # real accessibility checks on changed files
```

And a real manual click-through against the live deployed backend (or a local
`yarn dev:ecosystem` run) for feature correctness -- type-checking and a successful build verify
code correctness, not feature correctness. Adding a real unit-test runner to the web workspaces
is a legitimate future improvement, not something this repo has today.

## `packages/saronite` -- type-check only

```bash
cd packages/saronite/host-app
npx tsc --noEmit
```

No test runner configured here either.

## What does NOT exist

No E2E suite, no load-testing tooling, and no automated security-scanning pipeline exist in
this repo. `SECURITY.md` documents real, manually-found-and-fixed vulnerabilities, not an
automated scan. No CI currently runs any of the commands above automatically --
`docs/ARCHITECTURE.md`/root `CLAUDE.md` track GitHub Actions as billing-blocked since at least
2026-08-15, so "CI-verified" claims anywhere in this repo mean locally-run, not
pipeline-enforced.

---

**Last updated**: 2026-09-03
