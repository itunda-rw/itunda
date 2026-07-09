# Itunda System Architecture

This document describes itunda's technical architecture and how it maps to Toss's *actual*,
sourced architecture. Every "Toss does X" claim here must trace to
[docs/TOSS_ARCHITECTURE_FACTS.md](docs/TOSS_ARCHITECTURE_FACTS.md). For the product/bounded-context
model see [docs/TOSS_RWANDA_ALIGNMENT.md](docs/TOSS_RWANDA_ALIGNMENT.md); for implementation
status see [docs/TOSS_PARITY_MATRIX.md](docs/TOSS_PARITY_MATRIX.md).

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
| Core ledger + money-moving backend | `services/spring-backend` | **real** | Kotlin + Spring Boot + Spring Data JPA + MySQL + Spring Security JWT. ~4,300 LOC across auth/wallet/transfer/bills/loans/contacts/stocks/savings/insurance/notifications/discover/system, verified live against real MySQL with per-user ownership checks and a transactional idempotency store. This is the one piece that is genuinely on Toss's real stack (Kotlin, Spring, MySQL) — see `docs/TOSS_PARITY_MATRIX.md` for the verified detail. **This is the canonical backend.** |
| Per-bounded-context MSA prototype | `services/spring-microservices/{payment-service,ledger-service,spring-core-libs}` | **real, different shape** | **Correction (2026-07-10):** an earlier pass of this doc called these "superseded" based on a shallow file count. Wrong — checked properly, this is genuine hexagonal-architecture Kotlin (domain/application/infrastructure layers), a real double-entry `TransferService`, an outbox pattern for Kafka event publishing, and a mocked RNP (Rwanda National Digital Payment System) gateway. Each service (`payment-service`, `ledger-service`) has its own Gradle root and is independently deployable — this is actually **closer** to Toss Bank's real documented MSA split (facts doc §1: independently deployable per-bounded-context services) than `spring-backend`'s single consolidated app is. Not superseded; a parallel, not-yet-reconciled architectural direction. |
| API gateway | `services/api-gateway` | **stub** | Express + http-proxy-middleware, essentially just `index.js`. Not a real gateway yet — no rate limiting, no auth, no routing table beyond a proxy. |
| Micro-frontends | `services/micro-frontends/{host-app,bank-mfe,kyc-mfe}` | **demo** | Real Vite+React scaffolds (~1,500 LOC) — `bank-mfe`/`kyc-mfe` already depend on the real `@toss/use-funnel`, a genuine alignment point. But this is a *web* micro-frontend split, which is not the architecture Toss is actually known for (§3 below is). Keep only if the goal is a web admin/BFF surface distinct from the consumer super-app. |
| Earlier Express demo API | referenced in older docs as `backend/` | **gone** | Does not exist in the tree. Superseded by `spring-backend`. |

**Decision this implies:** `services/spring-backend` is the backend with the most product-surface
coverage today and stays the default for new feature work, but `services/spring-microservices`
should **not** be deleted or treated as dead — it's the shape Toss's real MSA split actually
looks like (facts doc §1), just with far less feature coverage. Reconciling the two (eventually
splitting `spring-backend`'s bounded contexts out into independently-deployable services matching
`ledger-service`/`payment-service`'s pattern) is real future work, not a cleanup task. Kafka is
not yet wired between any of these — that's the concrete gap between "itunda has Spring apps"
and "itunda has the MSA event backbone Toss actually runs" (`transfer.confirmed` /
`payment.provider_succeeded` / `ledger.posted` events listed in `docs/TOSS_RWANDA_ALIGNMENT.md`'s
event model are designed but not emitted anywhere yet — `spring-core-libs`' `KafkaConfig.kt` is
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
| Consumer web app | `services/micro-frontends/host-app` (root `package.json` is now a pure workspace root) | **demo** | Root previously had leftover `dev`/`build`/`vite` scripts and app deps from a deleted app with no `src/`; fixed 2026-07-10 by making root a pure Yarn workspace root that delegates to `host-app`. `host-app` itself already depends on the real `@tosspayments/payment-widget-sdk`. |
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
| `android/core/identity` | `NIDABiometricAuth.kt` (+ `androidx.biometric` dep) | Same build/run verification as above. Local biometric gate only — no NIDA server-side call, honestly labeled in the file. |
| `android/features/banking/impl` | `BankScreen.kt`, `MySpendingScreen.kt` | Same verification. Ported with three real compile-error fixes (ambiguous lambda type, invalid `Modifier.padding()` args, a `TransactionItem` composable that was called but never defined in the original) — proof the source had genuinely never compiled before. |
| `android/features/payments/impl` | `RecipientScreen.kt` (alongside the pre-existing `TransferQuoteScreen.kt`) | Same verification. |
| `android/core/designsystem/ids` | `IDS.kt` | Ported as a second, separate token set alongside the existing `Tds*` tokens rather than silently merged — reconciling the two remains open (see §5). |
| `ios/Core/Risk`, `ios/Core/Identity`, `ios/Features/Banking`, `ios/Core/DesignSystem`, `ios/SDK/Pay` | `ZeroTrust.swift`, `NIDABiometricAuth.swift`, `BankView.swift`, `IDS.swift`, `PaymentWidget.swift`/`AgreementWidget.swift`/`PaymentMethodWidget.swift` | **Ported, not build-verified.** This sandbox's Tuist install can't run (`libswiftSynchronization.dylib` missing — Tuist was built for a newer macOS than this environment has) and only Xcode Command Line Tools are selected, not full Xcode, so `tuist generate`/`xcodebuild` could not be exercised. Files were hand-audited for correct `public` access-control and per-module `import` statements (Swift multi-framework visibility), but treat as unverified until someone runs this on a real Mac with Xcode. |
| `mobile_clients/itunda-pay-sdk` | vendored `tosspayments/payment-sdk-android` clone | Untouched — real third-party reference, not itunda code, its own `.git` history is genuine Toss Payments commits. Still worth moving out of `mobile_clients/` eventually so the name stops implying it's itunda's own client code. |

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
  (§1, facts doc §5); `infra/k8s/` (which references the real `api-gateway` service) is now the
  only k8s directory.
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
  remove exactly this kind of fiction — `IMPLEMENTATION_GUIDE.md` still has it and needs the
  same treatment.

## 5. Repo layout (2026-07-10 restructure)

The top level was reorganized to match Toss's real monorepo convention (`toss/granite`,
`toss/es-toolkit`: a `packages/` + `services/` split, Yarn PnP at root, `docs/`, `infra/`):

```
itunda/
├── packages/              # shared libraries
│   ├── shared-utils/       (depends on the real es-toolkit)
│   ├── itunda-utils/
│   └── saronite/           (own npm workspace -- see §2)
├── services/               # deployable apps/backends
│   ├── spring-backend/     (canonical backend, §1)
│   ├── spring-microservices/  (payment-service, ledger-service, spring-core-libs -- §1)
│   ├── api-gateway/
│   ├── micro-frontends/    (host-app, bank-mfe, kyc-mfe)
│   └── blog/                (own npm workspace, tech.itunda.rw)
├── android/, ios/          # native mobile shells (§3)
├── mobile_clients/itunda-pay-sdk/  # vendored real Toss Payments SDK, reference only
├── infra/                  # was infrastructure/, kubernetes/ dupe removed (§4)
└── docs/
```

`mobile_clients/android`, `mobile_clients/ios`, `web-prototype/`, `node_workspace/`,
`spring_workspace/` (old name), and the stale untracked `dist/` are gone — either fully
consolidated into the tree above (§3) or deleted as pure scaffolding with no unique content
(`web-prototype/` was an unmodified `npm create vite` template, never touched).

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
   `services/spring-microservices/spring-core-libs`' `KafkaConfig.kt` is the only real Kafka
   wiring in the repo today.
3. Reconcile `services/spring-backend` (most feature coverage, monolith-shaped) with
   `services/spring-microservices` (less coverage, real per-service MSA shape) — decide whether
   to split spring-backend's bounded contexts out to match, or fold the microservices' patterns
   (outbox, hexagonal layering) into spring-backend instead.
4. Build-verify the `ios/` port on a real Mac with full Xcode (this sandbox's Tuist can't run —
   see §3) and get iOS to the same "compiles and runs on-device" bar Android is now at.
5. **Done on Android (2026-07-10), still open on iOS:** `core/designsystem`'s two token sets
   (`Tds*` and `ids/IDS`) are reconciled — `IDS.Colors` (a second, light-only-hardcoded color
   system with zero dark-mode values, the actual root cause of `BankScreen`/`MySpendingScreen`/
   `RecipientScreen` having no working dark mode) is gone; every color reference across
   `ItundaAppScreen.kt`, `BankScreen.kt`, `MySpendingScreen.kt`, `RecipientScreen.kt`,
   `TransferScreen.kt`, `TdsButton`, and `TdsListRow` now goes through one real,
   `CompositionLocal`-backed `Tds.colors` (`TdsSemanticColors.kt`), verified live in both light
   and dark on the real emulator (`adb shell cmd uimode night yes/no`) across all 5 real tabs
   (Home/Benefits/Shop/Pay/All). Dark palette now matches the actual Toss app (true-black
   background, not the old unexplained navy `#191F28`) rather than a guess. iOS's `IDS.swift`
   has the identical bug (light-only, ported unverified — see §3) and needs the same fix once
   iOS can be build-verified.
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
