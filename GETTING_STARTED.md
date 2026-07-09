# Itunda Platform - Getting Started

Use this guide for the current repository state. The full Toss/Rwanda target is documented in [docs/TOSS_RWANDA_ALIGNMENT.md](docs/TOSS_RWANDA_ALIGNMENT.md).

## Prerequisites

- Node.js 18+
- npm
- Java/Android SDK for Android work
- Xcode and XcodeGen for iOS work, if needed

## Install

```bash
npm install
cd backend && npm install
```

## Run Web App

```bash
npm run dev
```

The web app runs at `http://localhost:5173`.

## Run Demo API

```bash
cd backend
npm run dev
```

The web client points at `http://localhost:4000/api/v1`.

If the backend is not running, the app intentionally falls back to demo data for core preview screens.

## Build Web App

```bash
npm run build
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
adb shell monkey -p com.itunda.app 1
```

If the package id changes, inspect `android/app/build.gradle.kts` and use that `applicationId`.

The Android app is native-first. `MainActivity` renders the Compose navigation graph directly; the bundled web assets are not the Android runtime UI.

## Current Project Structure

```text
itunda/
├── src/                    # React web app
├── backend/                # Express demo API
├── ios/                    # SwiftUI iOS app shell
├── android/                # Android app scaffold
├── microservices/          # Service prototypes, currently loan-focused
├── spring-backend/         # Spring backend scaffold
├── k8s/                    # Deployment/monitoring manifests
├── docs/                   # Product and architecture alignment docs
└── dist/                   # Generated web build output
```

## Demo Credentials

The auth screen includes demo mode. For backend login, use the demo data configured in `backend/src/services/database.ts`.

## Development Checks

```bash
npm run build
cd backend && npm run build
cd android && ./gradlew :app:assembleDebug
```

## Production Reality

The repo is a product/system prototype. Real-money launch requires provider contracts, licensing, compliance operations, ledger hardening, reconciliation, support workflows, monitoring, and security review.
