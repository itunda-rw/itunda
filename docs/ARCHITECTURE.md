# Itunda System Architecture

This document describes itunda's technical architecture and how it maps to Toss's *actual*,
sourced architecture. Every "Toss does X" claim here must trace to
[docs/TOSS_ARCHITECTURE_FACTS.md](TOSS_ARCHITECTURE_FACTS.md). For the product/bounded-context
model see [docs/TOSS_RWANDA_ALIGNMENT.md](TOSS_RWANDA_ALIGNMENT.md); for implementation
status see [docs/TOSS_PARITY_MATRIX.md](TOSS_PARITY_MATRIX.md).

Status labels below follow the repo convention: `real` (exists and runs), `demo` (exists,
mocked/local data), `target` (designed, not built), `stub` (scaffold with little/no logic),
`superseded` (duplicate, not canonical).

## 0. The one architectural idea to copy first

Toss is not one backend wearing one app's face. "Toss," Toss Bank, Toss Securities, and Toss
Payments are separately regulated systems with **different backends and different tech
stacks** (Toss Bank: Kotlin/MySQL/Kubernetes; Toss Securities: legacy C ledger + Java edge),
unified by **one frontend mechanism**: a native host app that dynamically loads independent
React Native mini-app bundles per feature (§3 of the facts doc, productized as
[toss/granite](https://github.com/toss/granite)).

That reframes what "100% Toss-like" should mean for itunda: **not** one monolithic set of 12
generically-named microservices sharing one repo, but (a) a small number of real bounded-context
backends that can evolve independently, unified by (b) one real super-app shell that loads
feature bundles independently. Itunda currently has neither cleanly — it has fragments of both,
scattered across duplicate trees. This document's job is to say which fragment is the real one.

## 1. Backend architecture

### What Toss actually does (sourced, see facts doc §1-2)

- Toss Bank: monolith → MSA, Kotlin/Spring-style services, **MySQL**, all channel/business
  services containerized on **Kubernetes**, independent per-service CI/CD deploy and scaling,
  **active-active dual-datacenter** (not primary/DR).
- Toss Securities: does **not** rewrite its system of record reflexively — keeps a legacy C
  ledger core, puts a Java/Kubernetes edge in front of it, bridges the two with **Kafka**.

### Itunda's current state

| Layer | Directory | Status | Notes |
|---|---|---|---|
| Core ledger + money-moving backend | `services/backend` | **real** | Kotlin + Spring Boot + Spring Data JPA + MySQL + Spring Security JWT. ~4,300 LOC across auth/wallet/transfer/bills/loans/contacts/stocks/savings/insurance/notifications/discover/system, verified live against real MySQL with per-user ownership checks and a transactional idempotency store. This is the one piece that is genuinely on Toss's real stack (Kotlin, Spring, MySQL) — see `docs/TOSS_PARITY_MATRIX.md` for the verified detail. **This is the canonical backend.** **Fixed (2026-07-11):** `wallet` had a second, reachable `POST /api/v1/transfers` (`TransferController`/`TransferService`) that called a *different*, mocked `LedgerService` (`getAccount()` fabricated a random account, `.save()` calls were commented out) and returned `"SUCCESS"` while persisting nothing — a live fake-success endpoint, not just a stub. The same mocked `LedgerService` was also wired into `InsuranceService.enrollInPlan()`, so insurance premium debits were silently never happening either, despite the parity matrix describing that flow as ledger-backed. `InsuranceService` now posts through the real `rw.itunda.core.ledger.LedgerService` (same pattern as `SavingsService`); the fake `TransferController`/`TransferService` and the mocked `ledger.service`/`ledger.domain` packages were deleted outright rather than fixed, since no client anywhere called `/api/v1/transfers` and the real transfer flow (`WalletController`'s quote/confirm) already existed and was correct. Verified: `:app:compileKotlin` and `:core:test` both pass after the deletion. **Flyway added (2026-07-11):** `ddl-auto: update` (Hibernate inferring the schema, the documented gap this file used to flag repeatedly) is now `ddl-auto: validate` alongside a real `V1__init_schema.sql` in `app/src/main/resources/db/migration/` — one table per `@Entity` in `core/.../domain/` and `core/.../idempotency/`, transcribed column-for-column (13 tables, indexes on every column a `findByX` repository method queries against). Seed data stays in `SeedDataRunner.kt`, not the migration, since it already seeds idempotently on every boot. Verified: `./gradlew build` passes and the migration file is bundled into the runnable jar at `BOOT-INF/classes/db/migration/`. Not runtime-verified against a live MySQL instance (no Docker daemon in this environment) — if Hibernate's schema validation disagrees with any column here, that would surface loudly at startup rather than silently, consistent with this repo's discipline elsewhere. **Merchant module added (2026-07-11):** `rw.itunda.merchant` — the backend previously had zero merchant/business code anywhere despite `docs/MERCHANT_SERVICES.md` documenting a full (self-admittedly aspirational) spec for it. Built a real, right-sized subset instead of that spec's invented POS/card/B2B/webhook surface: registration reusing the owner's existing `MAIN` wallet as settlement wallet (no new `WalletType` needed, since `AuthService.register` already provisions one per user), fixed-amount QR-style payment-intent generation, and collection that debits the payer, credits the merchant's wallet minus a fee, and credits `fee_revenue` — same `LedgerService`/idempotency/ownership-check pattern as every other money-moving flow, 1.5% fee rate grounded in Toss Payments' real published 0.8%–1.8% range (`docs/TOSS_ARCHITECTURE_FACTS.md`), not the old spec's own invented number. New `V2__merchant.sql` migration (`merchants`, `payment_intents`). Verified: `./gradlew build` passes with the new `:merchant` module compiling and linking into `:app`, migration confirmed bundled in the runnable jar. Not runtime-verified against a live database for the same Docker-daemon reason as the rest of this row. **Test coverage added (2026-07-11):** `MerchantServiceTest.kt`, same Kotest/MockK convention as `LedgerServiceTest.kt` (the only other test file that existed in this backend before this) — `LedgerService` itself is mocked, not exercised, since its balance math is already covered there; this file is merchant-specific: fee-split math (1.5% to `fee_revenue`, remainder to the merchant, verified to still balance against the payer's full debit), ownership checks (self-payment rejected), expiry (marks `EXPIRED` and blocks payment, doesn't just silently fail), and double-collection prevention (an already-`COMPLETED` intent can't be paid again, and critically, no ledger call happens in that path — verified via `mockk` call-count assertions, not just the thrown exception). 10 tests, all passing (`./gradlew :merchant:test`, confirmed via the JUnit XML report, not just an absence of Gradle failure output). **Provider connector added (2026-07-11):** bills/airtime previously always succeeded instantly against a flat rail with no provider simulation at all — explicitly flagged open in `docs/TOSS_RWANDA_ALIGNMENT.md`'s gap list. Added `rw.itunda.core.provider.ProviderConnector` (a real interface, not a stub) with `SimulatedProviderConnector`: per-rail `RailProfile` (latency + success rate, "designed for demo realism" honestly labeled as such, not claimed as sourced from a real provider SLA itunda has no access to), called via `attempt()` *before* `LedgerService.postLedgerTransaction` so a decline never touches a wallet balance — same ordering discipline `TOSS_PARITY_MATRIX.md` describes for the now-removed Express `providerConnectors.ts` this rebuilds the design of, not a literal port (that file no longer exists in this repo). `BillsController` got a new `ProviderDeclinedException` → 502 handler. Added `BillsServiceTest.kt` (bills had zero test coverage before this) verifying specifically that a provider decline never reaches the ledger. 4 tests, all passing. |
| Per-bounded-context MSA prototype | `services/microservices/{payment-service,ledger-service,core-libs}` | **real, different shape** | **Correction (2026-07-10):** an earlier pass of this doc called these "superseded" based on a shallow file count. Wrong — checked properly, this is genuine hexagonal-architecture Kotlin (domain/application/infrastructure layers), a real double-entry `TransferService`, an outbox pattern for Kafka event publishing, and an RNP (Rwanda National Digital Payment System) gateway port. **`RnpPaymentGateway` fixed (2026-07-11):** it previously always returned `true` after a fake 200ms sleep, unconditionally claiming every mobile-money transfer succeeded — the same fake-success pattern already found and fixed in `services/backend` this session. It now throws `RnpGatewayNotConfiguredException` instead of lying, since no real MTN MoMo/Airtel Money/RNP endpoint exists anywhere (that needs real provider credentials and certification, not a code change — see `docs/TOSS_PARITY_MATRIX.md`'s "Non-Negotiable Gates Before Real Money"). `ConfirmPaymentService`'s domain logic already correctly handled a real `false` return (marks the payment `ABORTED`) — that path was simply never reachable because the fake adapter never returned it. Each service (`payment-service`, `ledger-service`) has its own Gradle root and is independently deployable — this is actually **closer** to Toss Bank's real documented MSA split (facts doc §1: independently deployable per-bounded-context services) than `services/backend`'s single consolidated app is. Not superseded; a parallel, not-yet-reconciled architectural direction. |
| API gateway | `services/api-gateway` | **stub** | Express + http-proxy-middleware, essentially just `index.js`. Not a real gateway yet — no rate limiting, no auth. **Routing fixed (2026-07-11):** it previously only proxied `/api/v1/payments` and `/api/v1/ledger` to the two microservices, with no route at all to `services/backend` (port 4001) — the canonical backend holding most of the actual API surface. Added a catch-all `/api/v1` route to `services/backend`, registered after the two specific routes so they still take precedence. While fixing this, found `ledger-service` had no `application.yml` at all (would have defaulted to Spring Boot's port 8080, not the 8082 the gateway assumed, and had no datasource despite real JPA entities) and `payment-service` was missing a datasource too. Both fixed — separate databases (`itunda_ledger`, `itunda_payment` via a new `infra/mysql-init/` script) because both services map an `OutboxEvent` entity to a table named `outbox_events` with different columns, which would have collided under one shared schema. Also found and fixed a pre-existing, unrelated Gradle bug: `ledger-db` and `payment-db` (library-only submodules, consumed via `runtimeOnly` at the API entry points) had the Spring Boot Gradle plugin applied without a main class, so `./gradlew build` failed on their `bootJar` task — disabled `bootJar` in favor of the plain `jar` task for both. Verified: `./gradlew build` now passes clean for both `services/backend` and `services/microservices` (it did not before, for the microservices workspace). Not runtime-verified end-to-end (no Docker daemon in this environment to actually start MySQL/Kafka and confirm the services boot and the gateway proxies correctly) — config-level and compile-level verified only. |
| Micro-frontends | `services/micro-frontends/{host-app,bank-mfe,kyc-mfe}` | **demo, now actually federated** | Real Vite+React scaffolds (~1,500 LOC) — `bank-mfe`/`kyc-mfe` already depend on the real `@toss/use-funnel`, a genuine alignment point. But this is a *web* micro-frontend split, which is not the architecture Toss is actually known for (§3 below is). Keep only if the goal is a web admin/BFF surface distinct from the consumer super-app. **Fixed (2026-07-11):** `host-app` declared `kyc_mfe` as a federation remote and never rendered it, and never referenced `bank_mfe` as a remote at all — it rendered an unrelated Toss Payments checkout demo instead (see next row). Now `App.tsx` actually lazy-loads both `bank_mfe/BankDashboard` and `kyc_mfe/KycDashboard` behind a two-tab shell. Fixing this surfaced real, pre-existing bugs that had never been caught because these packages could never actually build before: `kyc-mfe` imported `@originjs/vite-plugin-federation` without declaring it as a dependency at all; `bank-mfe`'s `BankDashboard.tsx` imported `framer-motion`/`lucide-react` without declaring either; `framer-motion` needed an explicit `@emotion/is-prop-valid` dependency to resolve under Yarn PnP's strict resolution; and `@toss/use-funnel@1.4.2` (its own source marks the whole API `@deprecated`, superseded by a separate `@use-funnel` package) unconditionally imports `next/router.js` and `react-query` even when unused — added `react-query` for real, and aliased `next/router.js` to a small local stub (`kyc-mfe/src/shims/next-router.ts`) rather than pulling in all of Next.js for an import that's never actually exercised. Verified: all three apps build clean (`yarn build`), and all three dev servers start and serve their `remoteEntry.js` federation endpoints with real 200 responses. Not verified with a real browser DOM render — no Playwright/headless browser available in this environment. |
| Earlier Express demo API | referenced in older docs as `backend/` | **gone** | Does not exist in the tree. Superseded by `services/backend`. |

**Decision this implies:** `services/backend` is the backend with the most product-surface
coverage today and stays the default for new feature work, but `services/microservices`
should **not** be deleted or treated as dead — it's the shape Toss's real MSA split actually
looks like (facts doc §1), just with far less feature coverage. Reconciling the two (eventually
splitting `services/backend`'s bounded contexts out into independently-deployable services matching
`ledger-service`/`payment-service`'s pattern) is real future work, not a cleanup task. Kafka is
not yet wired between any of these — that's the concrete gap between "itunda has Spring apps"
and "itunda has the MSA event backbone Toss actually runs" (`transfer.confirmed` /
`payment.provider_succeeded` / `ledger.posted` events listed in `docs/TOSS_RWANDA_ALIGNMENT.md`'s
event model are designed but not emitted anywhere yet — `core-libs`' `KafkaConfig.kt` is
the only real Kafka wiring that exists, and only `payment-service` uses it).

## 2. Frontend / super-app architecture

### What Toss actually does (sourced, see facts doc §3)

Native host app + independently-built, independently-deployed **React Native mini-app
bundles**, split into one **shared bundle** (RN core + common code) and many **service
bundles** (one per feature), **loaded dynamically at runtime** rather than shipped up front.
Open-sourced as `toss/granite`. Third-party version of the same mechanism is **Apps-in-Toss**.

### Itunda's current state

| Layer | Directory | Status | Notes |
|---|---|---|---|
| Apps-in-Itunda mini-app host | `android/app/src/main/java/rw/itunda/app/miniapps/` + `packages/saronite/` | **real, verified live on-device (2026-07-10)** | Not the full Granite mechanism (no dynamic bundle loading over CDN, no shared/service-bundle split, no autolinking/codegen — a deliberately manual, minimal brownfield integration) but genuinely real and running: a real `ReactApplication` host, a real native bridge module, and one concrete `Activity` per mini-app, wired into the canonical `android/app`, launched from a real "Mini apps" section in `ItundaAppScreen.kt`'s All tab. Verified by actually tapping through the real UI on a real emulator: `./gradlew :app:assembleDebug` → install → Home → All → Pay bills → Metro's live "Bundling 88.1%..." → real RN screen rendering "Couldn't load bills: No active itunda session" (a correct failure, since itunda has no login flow yet — see `packages/saronite/README.md` for the full account, including four real bugs found and fixed getting here: a Kotlin 1.9→2.1 project-wide upgrade forced by react-android's stdlib metadata, a missing native library requiring an RN 0.72.17 downgrade, an RN-version API difference, and a component-name mismatch between the Activities and `index.js`). |
| Consumer web app | `services/micro-frontends/host-app` (root `package.json` is now a pure workspace root) | **demo** | Root previously had leftover `dev`/`build`/`vite` scripts and app deps from a deleted app with no `src/`; fixed 2026-07-10 by making root a pure Yarn workspace root that delegates to `host-app`. **Fixed (2026-07-11):** `host-app` previously rendered Toss Payments' real SDK/widget (`@tosspayments/payment-widget-sdk`, a real public Toss test key) with UI copy claiming "Secure payments via MTN MoMo, Airtel Money, and Bank Transfer" — a widget that can only actually process Toss's own Korean payment methods, not Rwandan rails. Removed entirely; see the Micro-frontends row above for what replaced it. |
| Design system | none | **target** | TDS itself isn't open-source, but its components are documented (facts doc §3) and should be the literal reference for itunda's design tokens, not an invented "Itunda Design System (aligned with Toss Design System)" placeholder with no actual token file behind it. `ios/Core/DesignSystem` and `android/core/designsystem` exist as directories but were not verified to contain a real token set. |

**Decision this implies:** the mini-app host mechanism itself is now real and proven end-to-end
on Android. What's still missing to call this "Granite-equivalent" rather than "a working
brownfield integration": dynamic bundle loading from a CDN instead of a local Metro server,
a shared-bundle/service-bundle split, and RN autolinking. The iOS side has no equivalent yet.
`packages/saronite/README.md` §5's dependency choices (RN 0.72.17, old architecture, manual
Maven deps) are locked in by what was actually verified working — changing them means
re-verifying on-device, not just updating a version number.

## 3. Mobile native shells

**Resolved (2026-07-10):** `mobile_clients/android` and `mobile_clients/ios` were duplicate
native-shell trees with real orphaned source and no build system (see git history for the full
"two incomplete halves" analysis that preceded this). Both have been consolidated into the
canonical `android/`/`ios/` trees and deleted. Current state:

| Module | Content | Verification |
|---|---|---|
| `android/core/risk` | `RootDetection.kt` | **Real, compiles.** `./gradlew :app:assembleDebug` succeeded; the resulting APK was installed and launched on a real emulator (avd `andros`), confirmed alive via `adb` (no `AndroidRuntime`/`FATAL` in logcat) with a real screenshot. `MainActivity` calls this before rendering. |
| `android/core/identity` | `NIDABiometricAuth.kt` (+ `androidx.biometric` dep) | Same build/run verification as above. Local biometric gate only — no NIDA server-side call, honestly labeled in the file. **Fixed and wired (2026-07-11):** the original version built a `BiometricPrompt.PromptInfo` and never called `.authenticate()` on it, unconditionally reporting success without ever showing the system prompt — a fake-success bug, not just an unwired one. Now actually calls `BiometricPrompt.authenticate()`; `MainActivity` changed from `ComponentActivity` to `FragmentActivity` since `BiometricPrompt`'s constructor requires it. Added a generic `authenticateForTransaction(reason:)` alongside the NID-specific `authenticateUser`, wired into `ItundaAppScreen.kt`'s transfer confirm step as a biometric gate before the sheet closes. Verified: `:app:assembleDebug` builds a full debug APK successfully. |
| `android/features/banking/impl` | `BankScreen.kt`, `MySpendingScreen.kt` | Same verification. Ported with three real compile-error fixes (ambiguous lambda type, invalid `Modifier.padding()` args, a `TransactionItem` composable that was called but never defined in the original) — proof the source had genuinely never compiled before. **Deleted (061cff6, same day):** these screens were unreachable from real navigation and superseded by `ItundaAppScreen.kt`'s Home tab, built directly against real Toss reference screenshots. The module went back to empty, but `android/app/build.gradle.kts` still depended on it — **cleaned up (2026-07-11):** removed the dangling `project(":features:banking:impl")` dependency rather than re-adding screens that would duplicate what `ItundaAppScreen.kt` already covers more completely (iOS's still-real, still-unreconciled `BankView.swift` covers similar ground — see §2 — but porting it back would recreate the exact duplication this session eliminated elsewhere in the backend/SDKs/shared-utils). The module stays declared as a placeholder for a genuinely distinct future feature, like the other empty feature modules. |
| `android/features/payments/impl` | `RecipientScreen.kt` (alongside the pre-existing `TransferQuoteScreen.kt`) | Same verification. |
| `android/core/designsystem/ids` | `IDS.kt` | Ported as a second, separate token set alongside the existing `Tds*` tokens rather than silently merged — reconciling the two remains open (see §5). |
| `ios/Core/Risk`, `ios/Core/Identity`, `ios/Features/Banking`, `ios/Core/DesignSystem`, `ios/SDK/Pay` | `ZeroTrust.swift`, `NIDABiometricAuth.swift`, `BankView.swift`, `IDS.swift`, `PaymentWidget.swift`/`AgreementWidget.swift`/`PaymentMethodWidget.swift` | **Ported, not build-verified.** This sandbox's Tuist install can't run (`libswiftSynchronization.dylib` missing — Tuist was built for a newer macOS than this environment has) and only Xcode Command Line Tools are selected, not full Xcode, so `tuist generate`/`xcodebuild` could not be exercised. Files were hand-audited for correct `public` access-control and per-module `import` statements (Swift multi-framework visibility), but treat as unverified until someone runs this on a real Mac with Xcode. **`ZeroTrust` (2026-07-11):** now wired into `ItundaApp.swift`'s app entry point, same gate-before-any-UI pattern as Android's `MainActivity` — previously the code existed but had zero call sites. `Project.swift`'s `ItundaApp` target dependencies updated to include `CoreRisk` so the import resolves. **`NIDABiometricAuth` (2026-07-11):** unlike Android's version, this one already correctly called `LAContext.evaluatePolicy` — it just had zero call sites. Added a generic `authenticateForTransaction(reason:)` (mirroring Android's) and wired it into `TransferScreen.swift`'s "Confirm & Send" button. Still not build-verified for the same Tuist-toolchain reason as the rest of this row. |
| `android/sdk/pay`, `ios/SDK/Pay` | `ItundaPayments.kt`/`ItundaPayments.swift` (+ iOS's `PaymentWidget`/`AgreementWidget`/`PaymentMethodWidget`) | **Deleted (2026-07-11):** `mobile_clients/itunda-pay-sdk` was a vendored, unmodified clone of the real `tosspayments/payment-sdk-android` (own `.git` history of genuine Toss Payments commits) — removed entirely rather than kept as "reference," since a Korea-market payment SDK can't process Rwandan rails (MTN MoMo, Airtel Money, local bank transfer) and keeping it around only invited confusion about which SDK is itunda's own. itunda's own SDK entry points were renamed `ItundaPay` → `ItundaPayments` for consistency. |

`MainTabScreen.kt`/`MenuScreen.kt`/`BankActivity.kt` (Android) and `MainTabView.swift`/
`BankViewController.swift`/`MenuView.swift`/`SaroniteViewController.swift` (iOS) were
deliberately **not** ported — they're alternate app-shell/entry-point implementations that would
compete with the canonical `MainActivity`/`ItundaAppScreen` and `ContentView`, not extend them.
The old `TransferScreen.kt` (Android) was dropped as superseded by the newer, already-real
`TransferQuoteScreen.kt`.

These native shells hold the trust-critical native surface (bank, transfer, identity/biometrics,
ledger) permanently — not migrated to RN — while non-core features load as Granite-style mini-app
bundles per §2. That split mirrors how Toss itself only exposes the *generic* app-shell mechanism
as open-source Granite while keeping money/identity bridge APIs private and native (facts doc
§3) — core trust surface stays native, extensibility surface goes through the mini-app runtime.

## 4. Infrastructure

- `infrastructure/` renamed to `infra/` (2026-07-10), matching the top-level naming Toss's real
  `toss/granite` repo uses. `infra/kubernetes/` was deleted — it was manifests for the fictional
  12-microservice/MongoDB/Elasticsearch/Kibana stack this document already flags as unsourced
  (§1, facts doc §5); `infra/k8s/` is now the only k8s directory.
- **Fixed (2026-07-11):** until this point, `infra/k8s/production/api-gateway.yaml` was the
  *only* deployment manifest that existed — it deployed the 24-line Node.js proxy, while
  `services/backend` (the canonical Kotlin backend, most of the real product surface) had no
  k8s manifest at all. Added `backend.yaml`, `ledger-service.yaml`, `payment-service.yaml`
  alongside it, and updated `api-gateway.yaml` with the in-cluster Service DNS names for all
  three (`http://backend:4001`, `http://ledger-service:8082`, `http://payment-service:8081`)
  — `services/api-gateway/index.js` was hardcoding `localhost` for these targets, which
  doesn't resolve inside a Kubernetes pod; now env-configurable, defaulting to `localhost`
  for local dev. Writing the health-check probes for these manifests surfaced a real gap:
  neither `services/backend` nor either microservice had Spring Boot Actuator at all, so a
  `/actuator/health` liveness probe would have crash-looped every pod against a path that
  didn't exist. Added `spring-boot-starter-actuator` to all three and permitted
  `/actuator/health` in `services/backend`'s `SecurityConfig` (it already had a `/health`
  permitAll rule pointing at an endpoint that was never actually implemented — the same
  "declared but not real" pattern this whole document tracks elsewhere). Secrets
  (`itunda-db-credentials`, `itunda-jwt-secret`) and ConfigMaps (`itunda-db-config`,
  `itunda-microservices-db-config`, `itunda-redis-config`, `itunda-kafka-config`) are
  referenced but deliberately not created by these manifests — committing real credentials
  to a YAML file in git would repeat exactly the mistake `SECURITY.md` exists to catch
  elsewhere; they need out-of-band provisioning, documented in each manifest's header
  comment. Verified: all three services' Gradle builds pass with Actuator added, and every
  manifest is valid YAML (checked with both Ruby's YAML parser and `kubectl` client-side
  parsing). Not verified against a live cluster — no Docker daemon running in this
  environment, so neither a real cluster nor even `kind` could come up to test an actual
  `kubectl apply`.
- Kubernetes itself is a real point of alignment (Toss Bank channel services run on K8s) —
  keep it. Kafka is not yet present and is the real gap (§1).
- Active-active dual-datacenter, 1,000+ topic Kafka mirroring, and sub-200ms real-time
  ledger writes (facts doc §1-2) are **not targets for itunda's current stage** — they are
  what Toss does at real-money, real-scale, regulated-bank operation. Naming them in a doc as
  something itunda "has" or will build next would repeat the same fabrication problem this
  document exists to fix. They belong in this facts doc as context, not on itunda's near-term
  roadmap.
- No claim of PCI-DSS Level 1, SOC 2, or similar should appear anywhere in itunda's docs
  without an actual audit behind it (facts doc §5). `SECURITY.md` was already rewritten once to
  remove exactly this kind of fiction — `docs/IMPLEMENTATION_GUIDE.md` still has it and needs the
  same treatment.

## 5. Repo layout (2026-07-10 restructure; naming pass 2026-07-11)

The top level was reorganized to match Toss's real monorepo convention (`toss/granite`,
`toss/es-toolkit`: a `packages/` + `services/` split, Yarn PnP at root, `docs/`, `infra/`).
A follow-up pass on 2026-07-11 fixed naming/placement left over from that restructure: the
`spring-` prefix on both backend directories hid which one was canonical, `itunda-pay-sdk`
turned out to be someone else's SDK rather than itunda's, two packages both claimed to be
"the" shared-utils package, and 13 markdown files sat loose at repo root.

```
itunda/
├── packages/              # shared libraries
│   ├── shared-utils/       (depends on the real es-toolkit; now has real content)
│   └── saronite/           (own npm workspace -- see §2)
├── services/               # deployable apps/backends
│   ├── backend/            (canonical backend, §1)
│   ├── microservices/      (payment-service, ledger-service, core-libs -- §1)
│   ├── api-gateway/
│   ├── micro-frontends/    (host-app, bank-mfe, kyc-mfe)
│   └── blog/                (own npm workspace, tech.itunda.rw)
├── android/, ios/          # native mobile shells (§3), each with its own sdk/pay
│                           # holding itunda's own ItundaPayments SDK
├── infra/                  # was infrastructure/, kubernetes/ dupe removed (§4)
├── scripts/                # one-off root-level scripts (setup_toss.py, test_outbox.sh)
└── docs/                   # every reference doc; root keeps only README/CHANGELOG/
                            # CONTRIBUTING/SECURITY (GitHub-recognized files)
```

`mobile_clients/android`, `mobile_clients/ios`, `web-prototype/`, `node_workspace/`,
`spring_workspace/` (old name), and the stale untracked `dist/` are gone — either fully
consolidated into the tree above (§3) or deleted as pure scaffolding with no unique content
(`web-prototype/` was an unmodified `npm create vite` template, never touched).
`mobile_clients/itunda-pay-sdk/` is also gone (2026-07-11): it was a vendored, unmodified
clone of `tosspayments/payment-sdk-android` — a Korea-market SDK that can't process Rwandan
payment rails, so it was deleted outright rather than kept as reference (§1 mobile SDK row
above). `itunda-utils` was merged into `shared-utils` — both were dead code, but only one
package should claim to be "the" shared-utils home.

## 6. Remaining architecture backlog

In priority order, each item closes a specific gap identified above:

1. Evolve the now-working mini-app host toward Granite's actual mechanism: dynamic bundle
   loading from a CDN (Metro dev server only, currently), a shared-bundle/service-bundle split,
   and RN autolinking — plus wire up a real login flow so `getAuthToken()` can return something
   other than null and the mini-apps can show real data, not just a correct auth error. Port the
   same brownfield integration to iOS (nothing exists there yet).
2. Introduce Kafka as the actual event backbone for the event model already designed in
   `docs/TOSS_RWANDA_ALIGNMENT.md` (`transfer.confirmed`, `payment.provider_succeeded`,
   `ledger.posted`, etc.) — currently those events are documented but not emitted anywhere.
   `services/microservices/core-libs`' `KafkaConfig.kt` is the only real Kafka
   wiring in the repo today.
3. Reconcile `services/backend` (most feature coverage, monolith-shaped) with
   `services/microservices` (less coverage, real per-service MSA shape) — decide whether
   to split backend's bounded contexts out to match, or fold the microservices' patterns
   (outbox, hexagonal layering) into backend instead.
4. Build-verify the `ios/` port on a real Mac with full Xcode (this sandbox's Tuist can't run —
   see §3) and get iOS to the same "compiles and runs on-device" bar Android is now at.
5. **Done on Android (2026-07-10) and iOS (2026-07-11):** `core/designsystem`'s two token sets
   (`Tds*` and `ids/IDS`) are reconciled — `IDS.Colors` (a second, light-only-hardcoded color
   system with zero dark-mode values, the actual root cause of `BankScreen`/`MySpendingScreen`/
   `RecipientScreen` having no working dark mode) is gone; every color reference across
   `ItundaAppScreen.kt`, `BankScreen.kt`, `MySpendingScreen.kt`, `RecipientScreen.kt`,
   `TransferScreen.kt`, `TdsButton`, and `TdsListRow` now goes through one real,
   `CompositionLocal`-backed `Tds.colors` (`TdsSemanticColors.kt`), verified live in both light
   and dark on the real emulator (`adb shell cmd uimode night yes/no`) across all 5 real tabs
   (Home/Benefits/Shop/Pay/All). Dark palette now matches the actual Toss app (true-black
   background, not the old unexplained navy `#191F28`) rather than a guess.
   **iOS (2026-07-11):** fixed differently, not identically — SwiftUI/UIKit can build a
   theme-reactive `Color` directly from a dynamic `UIColor` provider, so `ios/Core/DesignSystem/
   Sources/IDS.swift`'s `IDS.Colors` (the semantic-role layer, same job as Android's `Tds.colors`)
   now resolves every value via a new `Color(light:dark:)` init with real dark values ported from
   Android's already-tuned `TdsDarkSemanticColors` where the concept maps 1:1 — with zero call-site
   changes needed anywhere that already used `IDS.Colors.textPrimary` etc., unlike Android's
   CompositionLocal-threading approach. `TdsColors`/`TdsTypography` (`Theme/TdsTheme.swift`)
   correctly stay static/non-reactive, matching Android's `TdsColors` object — that's the
   primitive/raw-palette layer, not the semantic layer, and was never actually competing with
   `IDS.Colors` at the same job the way the original "two unreconciled token sets" framing implied.
   Not build-verified for the same Tuist-toolchain reason as the rest of `ios/` (§3) — passes
   `swiftc -parse` only.
   **New finding from this pass:** `BankScreen`, `MySpendingScreen`, `RecipientScreen`, and
   `TransferQuoteScreen` all compile and are now correctly themed, but **none of them are
   reachable from any real navigation** — `grep` for their call sites in `:app` returns nothing.
   They're real, tested-for-theming code with no path a user could ever hit. Wiring at least one
   in (e.g. the Home tab's "Spent in July" row is the natural entry point to
   `MySpendingScreen`) is the next concrete step to make this design-system work actually visible
   in the app, not just correct in principle.
6. Replace the placeholder "Itunda Design System (aligned with Toss Design System)" with an
   actual token set derived from the publicly documented TDS components (facts doc §3) —
   `Tds.colors`' light values already independently converged with real TDS hex values twice
   (`TdsColors` and the old `IDS.Colors`); this item is about the rest of the token surface
   (spacing scale, elevation, component shapes) matching the real documented TDS, not just colors.
7. Consolidate `infrastructure/k8s` and `infrastructure/kubernetes` into one directory.
