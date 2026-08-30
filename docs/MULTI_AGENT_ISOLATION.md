# Multi-Agent Isolation: the Silo Model

Last updated: 2026-07-23 (backend module count and iOS silo status corrected 2026-08-31 —
see inline notes below; found stale during a doc-staleness sweep after the same pattern
hit `docs/PAYMENTS.md` and `docs/API_SPECIFICATION.md` the same week)

Multiple Claude Code agents work on this repo in parallel, each in its own git
worktree (`git log` shows the real pattern: `worktree-agent-*` branches merging
into `agent/itunda-agent-network`). This doc formalizes how that stays safe as the
number of concurrent agents grows: every agent should be able to fully own a
feature slice and ship without waiting on, or colliding with, another agent's
slice.

## The Toss silo analogy

Toss organizes its product teams as **silos**: *"느슨하게 결합되고, 한편으로는
단단히 정렬된"* — **loosely coupled, tightly aligned**. Each silo decides its own
goals, experiment schedule, and budget without needing sign-off from other silos,
but every silo stays aligned to shared standards (design system, API contracts,
release process) that let the whole company move as one.

This repo's own `android/settings.gradle.kts` already cites the same lineage —
its Microfeatures module graph (`:core:*` / `:features:<name>:{api,impl,testing}`)
is modeled on Toss's real published architecture (`toss.tech/article/slash23-iOS`).
**Correction, 2026-08-29**: that source is Toss's real, named **iOS** "Microfeatures"
architecture (Tuist + Stencil, 5-way Feature/Interface/Testing/Tests/Example split) —
no primary Toss source describes an Android-specific equivalent, so Android's own
module graph here is a cross-platform extrapolation of the iOS pattern, not a
directly Toss-Android-sourced one (see `docs/TOSS_ARCHITECTURE_FACTS.md` §8). Worth
noting: Toss's real iOS version also has a per-feature **Example** mini-app for a
~5x-faster build/design-review loop — not yet confirmed present in itunda's own
`ios/Features/<Name>` split.
This doc extends that same silo boundary to the whole repo and to how agents work,
not just how Android code is organized.

**Loosely coupled** = what an agent owns outright and never needs to coordinate
on: its own feature's `impl`, its own micro-frontend's `src/`, its own backend
module's internals.

**Tightly aligned** = shared surface that every silo depends on. Touching it is
allowed, but the PR/commit description must call it out explicitly, because
another in-flight agent may be relying on its current shape.

## Silo registry

### Android (`android/features/<name>/{api,impl,testing}`)

Contract boundary: only `*.api` packages are safe to import from outside the
feature. `*.impl` is private to the feature.

| Silo | Status (2026-07-23) |
|---|---|
| payments | real (3 impl files) |
| marketplace | real |
| community, eats, jobs, maps, property, shop, talk | real (1+ impl file each) |
| assets, banking, bills, credit, engagement, insurance, merchant, wealth | empty shell — module exists, no impl yet |

Shared (`android/core/{designsystem,network,testing,identity,consent,ledger,risk}`)
is tightly-aligned — every feature module depends on these.

Standalone apps `:app`, `:riderapp`, `:merchantapp`, `:agentapp`, `:sdk:pay` are
each their own silo; `:app`'s `SuperAppTabs.kt` is a temporary tightly-aligned
exception — most real screens still live there pending decomposition into feature
modules, so two agents editing it concurrently is a known conflict risk.

### iOS (`ios/Project.swift`, Tuist)

Mirrors the Android `Feature<Name>Interface / Feature<Name>` split, and as of
2026-07-23 this is a real, CI-verified build (`ios-build` job on a `macos-15`
runner: `tuist generate` + `pod install` + `xcodebuild ... ItundaApp`) — not
just a paper structure. `CoreNetwork` now holds the real `NetworkClient`/
`KeychainTokenStore` (promoted from `App`, mirroring Android's own
`:core:network` relocation), so a Feature module can call the backend
directly.

**Stale as of 2026-08-31** — the paragraph below described the state as of
2026-07-23 ("most feature targets are still `Dummy.swift`"); a month-plus of
decomposition work (tracked in memory as `project_itunda_feature_isolation`)
substantially changed this. Current real state, checked directly via
`find ios/Features/*/Sources -iname "*.swift"`: `ios/Features/` now has 13
module directories (not 9), and 8 of them have real, non-`Dummy.swift`
content — Assets, Banking, Certificate, Credit, Identity, Maps, Payments,
Support. Only Bills, Engagement, Insurance, Merchant, and Wealth remain
`Dummy.swift`-only, and per that same memory thread, Bills/Insurance are
correctly empty by design (Saronite React Native mini-apps, not native gaps
— trace `BenefitsShopAllScreens.swift`, the iOS equivalent of
`SuperAppTabs.kt`, before assuming there's native code to move), while
Merchant/Wealth/Engagement are empty because they have no mapped screen on
either platform yet and need a product decision, not architecture work, to
proceed. Re-verify this list directly rather than trusting either date if
it's been a while since 2026-08-31 either.

### Backend (`services/backend/<module>`)

**Stale count corrected 2026-08-31** (was "33 Gradle modules" as of 2026-07-23; actual
count checked directly via `find services/backend -maxdepth 1 -type d`): 44 Gradle
modules. Also stale: `:wallet` was renamed to `:account` 2026-08-21 (`5bc9d1ea`, "real
Toss Bank/Toss Pay separation") — the example list below is illustrative, not
exhaustive, so check `ls services/backend` directly rather than trusting either list
(`:auth`, `:account`, `:bills`, `:loans`, `:marketplace`, `:eats`, …) in a mostly-clean
star topology — nearly everything depends only on `:core`.
A bounded set also depend on `:auth` and/or `:messaging`: community, certificate,
insurance, eats, gift, jobs, marketplace, maps, messaging, merchant, realestate,
partners, splitbill, p2p, overview, savings. `:core` is tightly-aligned.

`services/microservices` (`ledger-service`, `payment-service`, `core-libs`) is
genuinely independently deployable — each split into `-domain/-db/-api`.

### Micro-frontends (`services/micro-frontends/<name>`)

Separate yarn workspaces: `bank-mfe`, `kyc-mfe`, `merchant-mfe`, `ops-mfe`,
`pay-checkout`, `host-app`. Each owns its own `src/`; none should import another
MFE's internals directly (enforced — see below).

### Shared packages (`packages/<name>`)

`shared-utils`, `design-tokens`, `itunda-pay-widget` are consumed by every
micro-frontend and `services/api-gateway`. This is the **highest-risk
shared-conflict surface** in the repo — no per-package ownership or versioning
exists, so two agents changing the same shared package in the same window is the
most likely place for silent breakage. Always flag changes here in the PR
description. (`packages/saronite` is its own independently-managed workspace, out
of scope for this registry.)

## Git workflow rules

1. One agent = one worktree = one branch = one silo at a time. Don't claim two
   feature silos in the same worktree unless they're trivially related.
2. Before starting, check whether another agent already has the same silo or a
   tightly-aligned file (`SuperAppTabs.kt`, anything in `packages/*`, `:core:*`)
   in flight, to avoid two agents racing the same file.
3. Branches merge into `agent/itunda-agent-network` (the integration branch for
   concurrent agent work), which periodically merges into `main`.
4. When a change touches tightly-aligned surface, say so plainly in the commit/PR
   description — that's the signal other agents (or the human) need to notice
   before rebasing on top of it.

## Enforcement

Boundaries above are backed by real CI checks, not just convention:

- **JS/TS**: `dependency-cruiser` (`.dependency-cruiser.cjs`, `yarn depcruise`)
  forbids one micro-frontend importing another directly and forbids deep imports
  into another package's internals. Runs in CI (`lint-and-typecheck` job).
- **Android**: `:architecture-test` (Konsist) asserts no feature's `impl` package
  imports another feature's `impl` package — only `*.api` is cross-feature-
  importable. Runs in CI (`android-build` job).
- **iOS**: `scripts/ios-silo-boundary-check.py` (pure text scan, no Xcode/Tuist
  needed) asserts no `Features/<Name>/Sources/**` file imports another
  feature's `Feature<Name>` module directly — only `Feature<Name>Interface` is
  cross-feature-importable. Runs in CI (`lint-and-typecheck` job, since it
  needs no macOS runner).

## Out of scope (tracked, not forgotten)

- Decomposing the remaining empty Android feature shells, the still-mostly-
  `Dummy.swift` iOS feature targets, or `SuperAppTabs.kt` itself — this doc
  only adds the guardrails so that decomposition, done by any agent, in
  parallel, doesn't collide.
