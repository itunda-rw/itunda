# Itunda Platform - Getting Started

Use this guide for the current repository state. The full Toss/Rwanda target is documented in [docs/TOSS_RWANDA_ALIGNMENT.md](TOSS_RWANDA_ALIGNMENT.md). For the full real/demo/stub breakdown of every surface below, see [ARCHITECTURE.md](ARCHITECTURE.md).

## Prerequisites

- Node.js 20+ and Yarn (the repo root is a Yarn PnP workspace)
- JDK 21 for `services/backend` and `services/microservices`; JDK 17 for `android/`
- Xcode + Tuist for `ios/` work, if needed (not build-verified in most environments — see `ARCHITECTURE.md` §3)

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
