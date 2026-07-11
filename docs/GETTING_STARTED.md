# Itunda Platform - Getting Started

Use this guide for the current repository state. The full Toss/Rwanda target is documented in [docs/TOSS_RWANDA_ALIGNMENT.md](TOSS_RWANDA_ALIGNMENT.md). For the full real/demo/stub breakdown of every surface below, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Prerequisites

- Node.js 20+ and Yarn (the repo root is a Yarn PnP workspace)
- JDK 21 for `services/backend` and `services/microservices`; JDK 17 for `android/`
- Xcode + a Tuist version matching `ios/Project.swift`'s manifest API (see the `## iOS`
  section below — this repo's `Project.swift` needs Tuist 3.x specifically, not the
  latest 4.x)

## Install

The root workspace covers `packages/shared-utils`, `services/micro-frontends/*`, and
`services/api-gateway`. `packages/saronite` and `services/blog` are separate npm workspaces
(their own `package-lock.json`) — install those independently if you're working on them.

```bash
yarn install
```

## Run the web app

```bash
yarn dev
```

This runs `services/micro-frontends/host-app`.

## Run the canonical backend

```bash
cd services/backend
./gradlew :app:bootRun
```

Needs a real MySQL + Redis (see `services/backend/README.md` for the one-line `docker run`
setup). There is no demo/mock-data fallback here — this is the real Kotlin/Spring backend.

## Build the web app

```bash
yarn build
```

## Android

Build debug APK:

```bash
cd android
./gradlew :app:assembleDebug
```

Install on a connected emulator or device:

```bash
cd android
./gradlew :app:installDebug
```

Launch with adb after install:

```bash
adb shell monkey -p rw.itunda.app 1
```

If the package id changes, inspect `android/app/build.gradle.kts` and use that `applicationId`.

The Android app is native-first: `MainActivity` renders the Compose navigation graph directly.

Run the real instrumented UI tests (`androidTest/.../FocusOrderTest.kt` — checks
accessibility focus order against the live semantics tree, the same one TalkBack
reads) against a connected emulator or device:

```bash
cd android
./gradlew :app:connectedDebugAndroidTest
```

If Gradle's test orchestrator (UTP) fails at APK install with a transient
`Broken pipe`/`INSTALL_FAILED` error, restart adb (`adb kill-server && adb
start-server`) and retry, or install + run directly instead:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class rw.itunda.app.ui.FocusOrderTest \
  rw.itunda.app.test/androidx.test.runner.AndroidJUnitRunner
```

## iOS

Needs full Xcode (not just Command Line Tools) and Tuist 3.x — this repo's
`ios/Project.swift` uses the `platform: .iOS`/`Target(name:platform:product:...)`
manifest API, which Tuist 4.0+ replaced with `destinations:` as a breaking change, so
the latest Tuist will fail with confusing `ProjectDescription` type errors against
this file. If `xcode-select -p` reports the Command Line Tools instead of a full
Xcode install, check `/Applications/Xcode.app` before assuming Xcode isn't
available — point at it for just your shell session, no `sudo`/system-wide
`xcode-select -s` needed:

```bash
export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
```

Install a compatible Tuist via [mise](https://mise.jdx.dev/) (any 3.x release; 3.42.3
is confirmed working):

```bash
mise install tuist@3.42.3
```

Generate the real Xcode workspace and build against a simulator:

```bash
cd ios
mise exec tuist@3.42.3 -- tuist generate --no-open
xcodebuild -workspace Itunda.xcworkspace -scheme ItundaApp \
  -destination 'platform=iOS Simulator,name=iPhone 14' build
```

`Itunda.xcodeproj`/`Itunda.xcworkspace`/`Derived/` are Tuist output, gitignored —
regenerate with the command above rather than expecting them to already exist.
See `ARCHITECTURE.md` §3's "MAJOR CORRECTION" note for the full account of getting
this working, including two real Swift compiler bugs it caught that
`swift -frontend -parse` alone couldn't (a `#Preview` macro plugin issue and a
15-child `@ViewBuilder` exceeding Swift 5.8's 10-child limit).

Run the real UI tests (`App/UITests/FocusOrderTests.swift` — checks accessibility
focus order against the live accessibility tree, the same one VoiceOver reads):

```bash
xcodebuild -workspace Itunda.xcworkspace -scheme ItundaApp \
  -destination 'platform=iOS Simulator,name=iPhone 14' test
```

## Current Project Structure

```text
itunda/
├── android/, ios/           # native mobile shells (see ARCHITECTURE.md §3)
├── packages/
│   ├── shared-utils/         # shared JS/TS utils (real es-toolkit dependency)
│   └── saronite/              # mini-app host + native bridge (own npm workspace)
├── services/
│   ├── backend/               # canonical Kotlin/Spring Boot backend
│   ├── microservices/         # ledger-service, payment-service, core-libs
│   ├── api-gateway/           # thin Express reverse proxy
│   ├── micro-frontends/       # host-app, bank-mfe, kyc-mfe (Vite Module Federation)
│   └── blog/                  # tech.itunda.rw engineering blog (own npm workspace)
├── infra/                    # docker-compose, k8s manifests
├── scripts/                  # one-off root-level scripts
└── docs/                     # every reference/architecture doc
```

## Development Checks

```bash
yarn lint
yarn tsc --noEmit
cd services/backend && ./gradlew build
cd services/microservices && ./gradlew build
cd android && ./gradlew :app:assembleDebug
```

## Production Reality

The repo is a product/system prototype. Real-money launch requires provider contracts, licensing, compliance operations, ledger hardening, reconciliation, support workflows, monitoring, and security review.
