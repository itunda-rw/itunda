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

## Run the full local ecosystem

```bash
yarn audit:private-cloud
yarn verify:private-cloud
yarn env:private-cloud > .env
yarn dev:ecosystem
```

This is the canonical local demo path. It starts:

- the canonical backend at `http://localhost:4001`
- the API gateway at `http://localhost:3000`
- the federated web shell at `http://localhost:5000`
- both required remotes at `http://localhost:5001` and `http://localhost:5002`

It assumes MySQL, Redis, and Kafka already exist in your private cloud or another
reachable environment and are configured through env vars. `scripts/local-ecosystem.sh`
automatically loads `.env` if present.

## Full local-only fallback

```bash
yarn dev:ecosystem:local
```

That starts `infra/docker-compose.yml` first and then boots the same backend/gateway/web
processes against the Docker-mapped ports (`3307` MySQL, `16379` Redis, `9092` Kafka).

## Bootstrap the Multipass private cloud

If you want the repo's Kubernetes app layer on the two Multipass nodes themselves:

```bash
yarn private-cloud:bootstrap
yarn private-cloud:topics
yarn private-cloud:deploy
```

The full operator flow is in [PRIVATE_CLOUD_OPERATIONS.md](PRIVATE_CLOUD_OPERATIONS.md).

## Run only the web shell

```bash
yarn dev
```

Unlike the old setup, `yarn dev` now starts all four required Vite apps together
(`bank-mfe`, `kyc-mfe`, `ops-mfe`, and `host-app` -- see `scripts/local-ecosystem.sh`'s own
`web-dev` mode). The host shell depends on the remotes, so starting only `host-app` was not a
complete local run. `services/micro-frontends/` also has 3 more real, independently-run
micro-frontends not part of this bundled command -- `merchant-mfe`, `maps-mfe`, and
`pay-checkout` -- run those individually with `yarn workspace <name> run dev` when working on
them specifically.

## Run the canonical backend

```bash
yarn dev:backend
```

By default the backend reads its upstream addresses from your env:

- MySQL: `DB_HOST` / `DB_PORT` from your env
- Redis: `REDIS_HOST` / `REDIS_PORT` from your env
- Kafka: `KAFKA_BOOTSTRAP_SERVERS` from your env

If you use the Docker fallback (`yarn dev:ecosystem:local`), those values are injected as:

- MySQL: `localhost:3307`
- Redis: `localhost:16379`
- Kafka: `localhost:9092`

If you want to start only the fallback infra yourself, run `yarn dev:infra` first.

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
│   ├── backend/               # canonical Kotlin/Spring Boot backend, plus 13 independently-
│   │                          #   deployable product services (card/insurance/agents/etc.,
│   │                          #   see docs/DEPLOYMENT.md)
│   ├── microservices/         # ledger-service, payment-service, core-libs
│   ├── api-gateway/           # thin Express reverse proxy
│   ├── micro-frontends/       # host-app, bank-mfe, kyc-mfe, ops-mfe, merchant-mfe,
│   │                          #   maps-mfe, pay-checkout (Vite Module Federation)
│   └── blog/                  # tech.itunda.rw engineering blog (own npm workspace)
├── infra/                    # docker-compose, k8s manifests
├── scripts/                  # one-off root-level scripts
└── docs/                     # every reference/architecture doc
```

## Development Checks

```bash
yarn lint
yarn lint:web
yarn tsc --noEmit
cd services/backend && ./gradlew build
cd services/microservices && ./gradlew build
cd android && ./gradlew :app:assembleDebug
```

## Production Reality

The repo is a product/system prototype. Real-money launch requires provider contracts, licensing, compliance operations, ledger hardening, reconciliation, support workflows, monitoring, and security review.
