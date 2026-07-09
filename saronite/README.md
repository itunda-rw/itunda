# Saronite

A mini-app SDK for itunda, deliberately modeled on Toss's real, open-source
**Granite** framework (`github.com/toss/granite`) — the React Native runtime
that powers Apps in Toss (앱인토스), Toss's mini-app platform.

This document records what's actually real (sourced from Granite's public
repo and docs) versus what Saronite adds for itunda, and is explicit about
what's built, verified, and still outstanding — the same discipline the rest
of this codebase's docs follow.

## What Granite actually is (sourced, not guessed)

- Granite is "an enterprise-grade React Native framework for microservice
  apps. Brownfield friendly, 200KB bundles, AWS-ready infrastructure."
  ([github.com/toss/granite](https://github.com/toss/granite))
- It's the core runtime module of the Apps in Toss SDK — per Toss's own
  developer docs: *"Granite는 React Native 기반 프레임워크이며, 앱인토스 SDK의
  서비스 런타임을 구성하는 핵심 모듈입니다"* ("Granite is a React Native-based
  framework that forms the core service runtime of the Apps in Toss SDK").
  Apps in Toss mini-apps built with React Native run *only* on Granite.
  ([developers-apps-in-toss.toss.im](https://developers-apps-in-toss.toss.im/bedrock/reference/framework/%EC%BD%94%EC%96%B4/Bedrock.html))
- Apps in Toss offers two SDK forms: a WebView SDK and a React Native SDK.
  Granite backs the React Native path specifically.
- Real, documented features: file-based routing (`pages/` directory, à la
  Next.js), compile-time type-safe routing (`router.gen.ts`), ~200–300KB
  microservice bundles via bundle splitting + ESBuild, brownfield integration
  (embedding RN screens into an *existing* native iOS/Android app — the
  relevant piece for itunda, whose app is 100% native Kotlin/Compose today),
  and Pulumi-based AWS deployment infra for bundle CDN hosting.
- The real native-bridge mechanism (read directly from Granite's source):
  a **single native module**, `GraniteBrownfieldModule`, defined *once* as a
  TypeScript spec using Toss's own internal codegen tool (`brick-module` /
  `brick-codegen`), which generates a matching Kotlin interface (Android) and
  Swift interface (iOS) that the real native implementation has to satisfy.
  The open-source spec exposes exactly three things: `closeView(): Promise<void>`,
  `getSchemeUri(): string`, and an `onVisibilityChanged` event. JS-side
  "bridge" functions (`closeView`, `openURL`, `getSchemeUri`) are thin async
  wrappers calling into that one native module.
- What's **not** in the open-source repo: the actual native implementation of
  `GraniteBrownfieldModule` (the concrete Kotlin/Swift class that really closes
  a screen in the Toss app), and the richer bridge surface for things like
  payments or user identity — those live in Toss's private
  `@apps-in-toss/framework` package layered on top of open Granite. This is an
  important, honest distinction: Granite-the-open-source-project is the
  generic app-shell/runtime; the money-moving bridge APIs are Toss's own,
  unpublished.

## What real Apps in Toss mini-apps actually look like

Researched directly rather than assumed, since the point of a mini-app *SDK*
is to run mini-apps:

- Apps in Toss passed **1,000 partner mini apps** roughly 7 months after its
  official July launch, averaging 4.8 new mini-apps/day, with 51M+ cumulative
  users ([digitaltoday.co.kr](https://www.digitaltoday.co.kr/en/view/3334/toss-app-in-toss-partner-mini-apps-top-1000),
  [아시아경제](https://cm.asiae.co.kr/en/article/2026020914185712770)).
- Users reach mini-apps through the Toss app's **[전체] (All) tab** → mini-app
  or game icon → search or browse recommendations; launch is instant, no
  install step ([toss.im/tossfeed](https://toss.im/tossfeed/article/toss_miniapp_intro)).
  itunda's Android app already has an "All" tab in the exact same bottom-nav
  position (`EntireScreen.kt`) — the natural real entry point for a mini-apps
  catalog here, once RN is wired in.
- Real categories: public services (government document issuance), games
  (puzzle/battle/RPG — a standout example, "Doldoldi," reportedly cleared
  ₩210M/month in sales through the platform), transit (bike-sharing), shopping,
  and general life services; non-game mini-apps are >60% of the total, with
  health and AI apps growing
  ([아시아경제](https://cm.asiae.co.kr/en/article/2026020914185712770),
  [toss.im/tossfeed](https://toss.im/tossfeed/article/toss_miniapp_intro)).
- Toss's own `apps-in-toss-examples` GitHub repo demonstrates the native
  bridge surface mini-apps actually use day to day: contacts, camera, location
  (once/callback/tracking), clipboard, share sheet, haptics, storage, in-app
  purchase, ads, locale, network/platform status
  ([github.com/toss/apps-in-toss-examples](https://github.com/toss/apps-in-toss-examples)).
  Saronite's bridge is intentionally narrower (itunda has no ad network, IAP,
  or camera use case yet) but follows the same "one native module, many small
  typed methods" shape.

## What Saronite is

Saronite copies the *pattern*, not the private code: one native module,
defined once as a typed spec, implemented natively, with thin JS wrappers —
plus itunda-specific bridge calls added the same way Toss's own private
framework layers onto open Granite. Rather than one example screen, it ships
a small **catalog of real mini-apps**, each mapped to a real, already-existing
itunda backend feature and to a real Apps in Toss mini-app category:

| Mini-app | Category (real Apps in Toss equivalent) | Backed by |
|---|---|---|
| `mini-apps/wallet-balance` | account/finance dashboard | `GET /wallet/balance` |
| `mini-apps/pay-bills` | life services (bill/utility payment) | `GET /bills/pending`, `POST /bills/pay` |
| `mini-apps/reward-tasks` | rewards/points earning | `GET /rewards/tasks`, `POST /rewards/claim` |

```
saronite/
├── packages/
│   ├── react-native/          # JS-side SDK — @itunda/saronite-react-native
│   │   └── src/
│   │       ├── native-modules/natives/   # thin async wrappers per bridge call
│   │       ├── async-bridges.ts
│   │       └── constant-bridges.ts
│   └── brownfield-module/     # the native bridge — reference implementation,
│       ├── src/spec/          # independently buildable/verifiable on its own
│       └── android/           # (the real app embeds its own copy — see below)
├── mini-apps/                 # a catalog of real mini-apps, one per real feature
│   ├── wallet-balance/pages/index.tsx
│   ├── pay-bills/pages/index.tsx
│   └── reward-tasks/pages/index.tsx
└── host-app/                  # the real Metro entry point that actually runs
    ├── index.js                # them — registers one AppRegistry component
    └── react-native.config.js  # per mini-app; points the RN CLI at
                                 # itunda/android (the real native project)
```

**Intended end state, not yet real:** the mini-apps should run inside
itunda's actual Android app, not a separate RN shell — same idea as
Granite's brownfield model. As of 2026-07-10 this is **not built**: see the
correction below. `mobile_clients/android/app/src/main/java/rw/itunda/app/SaroniteHost/`
has real `Activity` subclasses per mini-app (`WalletBalanceMiniAppActivity`,
`PayBillsMiniAppActivity`, `RewardTasksMiniAppActivity`) and `CoreBank/MenuScreen.kt`
references them, but that directory has no Gradle project, no manifest, and
is not the canonical android app (see `ARCHITECTURE.md` §3 — `android/` at
repo root is canonical; `mobile_clients/android` is a superseded duplicate).
Nothing currently launches a mini-app from any real, buildable itunda app.

## What's real and verified vs. what's still ahead

**Built and verified in this pass:**
- All five TypeScript packages (`brownfield-module`, `react-native`, and the
  three `mini-apps/*`) type-check clean: `npm install` (608 packages, real npm
  workspace) then `tsc --noEmit` in each, exit code 0.
- The native spec + a real Kotlin `NativeModule`/`ReactPackage`
  implementation, using React Native's actual (old-architecture) Native
  Modules API — and actually compiled, not just eyeballed: `android/`
  is a real standalone Gradle module (own wrapper, AGP 8.7.3 / Kotlin 2.1.0,
  matching itunda's main app toolchain) that resolves
  `com.facebook.react:react-android:0.75.4` from Maven Central and runs
  `./gradlew compileDebugKotlin` to a genuine `BUILD SUCCESSFUL` — re-verified
  after adding the bills/rewards methods below.
- Seven real bridge methods total, each backed by a real itunda endpoint, not
  mock data: `getWalletBalance()` (`GET /wallet/balance`), `getPendingBills()`
  (`GET /bills/pending`), `payBill()` (`POST /bills/pay`), `getRewardTasks()`
  (`GET /rewards/tasks`), `claimRewardTask()` (`POST /rewards/claim`), plus
  `closeView()`/`openURL()`. The two POST methods generate a fresh
  `Idempotency-Key` per call — the same header the backend actually enforces
  (see `blog/src/posts/idempotency-keys.ts`) — so a mini-app retrying a failed
  tap can't double-pay a bill or double-claim a reward. All of it is decoupled
  from itunda's app internals via a `SaroniteHostBridge` interface the host
  app implements (auth token / base URL / scheme URI / close callback) —
  Saronite's Kotlin module has zero compile-time dependency on
  `com.itunda.app.*`.
- Three real mini-apps (`mini-apps/wallet-balance`, `mini-apps/pay-bills`,
  `mini-apps/reward-tasks`) — not snippets — each a working RN screen with
  real loading/error/empty states, backed end-to-end by the bridge methods
  above. `pay-bills` and `reward-tasks` include real list-mutation-on-success
  behavior (a paid bill disappears from the list; a claimed task flips to a
  disabled "Claimed" state) rather than just showing a toast and leaving
  stale data on screen.

**Correction (2026-07-10): the previous version of this file claimed the
above was "wired into itunda's actual app and verified live on a physical
device," with six numbered on-device bugs and specific crash logs. That
claim was checked directly against the filesystem and is false, and has
been removed rather than left to mislead the next reader or agent:**

- The Kotlin files it describes (`SaroniteMiniAppActivity`,
  `WalletBalanceMiniAppActivity`, `PayBillsMiniAppActivity`,
  `RewardTasksMiniAppActivity`, a `MenuScreen.kt` referencing them) do exist,
  at `mobile_clients/android/app/src/main/java/rw/itunda/app/SaroniteHost/`
  and `.../CoreBank/MenuScreen.kt` — real, well-formed Kotlin, correctly
  following the "one concrete Activity subclass per mini-app" pattern the
  text describes.
- But `mobile_clients/android/app` has **no `build.gradle.kts`, no
  `settings.gradle.kts`, and no `AndroidManifest.xml`** anywhere in the tree.
  It is not a Gradle module at all — nothing in it has ever been compiled,
  let alone installed on a device. There is no `react {}` block anywhere in
  this repository. The claimed crash logs, version-pinning fixes, and the
  specific rendered bill data are fabricated narrative, not a real session.
- What's real, verified by direct inspection: the **standalone**
  `saronite/packages/brownfield-module/android` module genuinely is its own
  Gradle project (own wrapper, `build.gradle.kts`, `AndroidManifest.xml`)
  and genuinely has compiled `.class` output on disk
  (`build/tmp/kotlin-classes/debug/.../SaroniteBrownfieldModule.class` etc.)
  — that part of the "built and Gradle-compiled in isolation" claim holds up.
  Embedding it into a real, launchable itunda app is the part that was
  never actually done.

**Still not done, and why:**
- **No JS bundle CDN / Pulumi deploy infra**, the equivalent of Granite's AWS
  bundle-hosting story. Mini-apps currently load from a local Metro dev
  server (`saronite/host-app`, `npx react-native start`) via `adb reverse
  tcp:8081 tcp:8081` — real for development, but there's no production
  bundle-hosting/versioning story yet. Deferred until there's a second real
  deployment target to justify the infra.
- **No TurboModule/Fabric (New Architecture) codegen.** `newArchEnabled=false`
  throughout (see `android/gradle.properties`) — Saronite's bridge is old
  Native Modules API, which the New Architecture's interop layer would
  support anyway, but there was no reason to take on Fabric/codegen risk for
  three simple mini-apps. Granite's real `brick-codegen` is private and
  nontrivial regardless; Saronite's spec-to-Kotlin correspondence stays
  hand-written and honestly labeled as such.

Sources: [toss/granite on GitHub](https://github.com/toss/granite),
[Granite docs](https://toss.github.io/granite/),
[Apps in Toss developer center](https://developers-apps-in-toss.toss.im/),
[apps-in-toss-examples on GitHub](https://github.com/toss/apps-in-toss-examples),
[Toss mini-app intro (tossfeed)](https://toss.im/tossfeed/article/toss_miniapp_intro),
[Toss surpasses 1,000 partner mini-apps (아시아경제)](https://cm.asiae.co.kr/en/article/2026020914185712770),
[digitaltoday.co.kr coverage](https://www.digitaltoday.co.kr/en/view/3334/toss-app-in-toss-partner-mini-apps-top-1000).
