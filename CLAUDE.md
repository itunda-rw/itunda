# itunda — repo instructions for AI agents

itunda is a Rwanda-market super-app modeled on real, sourced Toss/Kakao/Naver/Coupang/
Baemin/Uber/Karrot product and engineering practices. This is a multi-stack monorepo, not
a single framework — don't assume Node.js/npm/NestJS conventions apply repo-wide.

**Before writing any UI/UX or architecture code, read these three files in full:**

- [`docs/AI_AGENT_SELF_CHECK.md`](docs/AI_AGENT_SELF_CHECK.md) — **read this one first.**
  Real, measured "vibe coding" failure modes (security flaws in ~45% of AI-generated code
  samples, hallucinated packages, secrets leaked into client code, "looks correct" code
  that breaks under real load) with real instances itunda has already hit, and a direct
  self-check checklist to run on your OWN work before calling it done.
- [`docs/UI_UX_GUIDELINES.md`](docs/UI_UX_GUIDELINES.md) — sourced UI/UX rules (Toss,
  Kakao, Apple HIG, itunda's own copy voice), with a standing pre-ship checklist.
- [`docs/ARCHITECTURE_GUIDELINES.md`](docs/ARCHITECTURE_GUIDELINES.md) — sourced
  architecture/code-quality rules (Toss Frontend Fundamentals, Uber DOMA, Netflix, Spotify),
  with a standing pre-commit checklist.

All three are kept synchronized with what's actually shipped — if you make a decision that
should generalize, add it there in the same pass rather than leaving it for the next
agent to rediscover. If you find any of them contradicting real shipped code, fix
whichever one is wrong; never let them silently drift apart.

## Real stack, per workspace

- `services/backend` — the canonical backend: Kotlin + Spring Boot + Spring Data JPA +
  MySQL + Flyway (`ddl-auto: validate`, never `update`) + Spring Security JWT. Tests use
  Kotest + MockK. `:card-service`, `:insurance-service`, `:agents-service`,
  `:transit-service`, `:certificate-service`, `:bills-service`, `:vehicle-service`,
  `:partners-service`, `:identity-service`, `:overview-service`, `:knowledge-service`,
  `:notifications-service`, `:analytics-service`, and `:loans-service` (each its own
  Gradle module, own bootJar/Dockerfile/k8s Deployment) are the first fourteen products
  extracted out of `:app` into independently deployable services — same code, same
  shared MySQL schema, not a separate database. See `docs/ARCHITECTURE.md`'s "First
  independently-deployable product" row and its "Second" through "Fourteenth
  extraction" follow-ups (including the confirmed-zero-coupling candidate list, how the
  `:identity`/`:partners` shared-route-prefix collision was resolved by gateway
  registration order, a real `:app`-source-level coupling that ruled out `:calling`, how
  `:bills-service` wires real Kafka events, why any `hasRole("ADMIN")`-gated route needs
  a real admin JWT to verify removal, why a `:core`-shared implementation like
  `PushNotificationService` stays in `:core` even after the module that surfaces it via
  REST is extracted, and — the Fourteenth extraction's own real finding — why `:account`
  and `:savings` stay in `:app` for now despite looking superficially identical to
  `:loans`: real reverse Gradle coupling from `commerce`/`eats`/`gift`/`merchant`/`p2p`/
  `rideshare` (`AutoTopUpService`) and `p2p` (`RoundUpService`) respectively, a class of
  coupling only found by grepping every OTHER module's own `build.gradle.kts` for
  `project(":candidate")`, not just `:app`'s) before extracting another module the same
  way; the remaining candidate pool is genuinely thin now.
- `services/microservices/{payment-service,ledger-service,core-libs}` — a real, parallel,
  not-yet-reconciled hexagonal-architecture MSA prototype. Not superseded, not the default
  for new feature work.
- `services/micro-frontends/*` — Vite + React + TypeScript, Yarn workspaces + PnP
  (`yarn install` at root, `yarn workspace <name> run <script>` per package). `bank-mfe`
  is the real, deployed consumer super-app (not `host-app`'s module-federation shell,
  which is a mostly-unused 2-tab demo). Verification today: `tsc -b` + `vite build` +
  `oxlint` + `python3 scripts/accessibility-lint.py <file>` + a real browser click-through
  against the live deployed backend — there is no unit-test runner wired in yet.
- `android/` — Kotlin + Jetpack Compose, Gradle Feature-module scaffolding
  (`:features:<name>:{api,impl,testing}`), boundary-enforced by Konsist in CI — the
  scaffolding-vs-migration gap has closed a lot since the 2026-09-02 tab-decomposition
  push: `community`/`eats`/`jobs`/`marketplace`/`maps`/`payments`/`property`/`shop`/
  `talk`/`banking`/`credit`/`home`/`pay`/`menu`/`my`/`wealth` (confirmed 2026-09-03, real
  per-module `.kt` file counts) all have real code moved in — `banking`/`credit` (moved
  2026-09-02, `BankHubScreen`/`LoansScreen`/`CreditScoreScreen`/etc.) and `wealth`
  (moved 2026-09-03, `InvestScreen`/`StockDetailScreen`/`InvestPortfolio`) are no longer
  gaps, matching iOS's already-real `Features/Banking`/`Features/Credit` split. `bills`/
  `insurance` are correctly empty by design (Saronite/React-Native mini-apps, not
  native, same as iOS). `engagement`/`merchant` remain empty on BOTH platforms — a real
  product-scope gap (no mapped screen yet), not an architecture one; `assets` is also
  empty scaffolding but was never mapped to a specific tab, unlike the others.
  `:app:compileKotlin` or `:features:<name>:impl:compileDebugKotlin` to verify;
  `./gradlew :architecture-test:test` runs the Konsist boundary check (passes cleanly,
  but only meaningfully constrains the modules that actually have content).
- `ios/` — Swift + SwiftUI, `Core`/`Features/<Name>` module split, auto-discovered via
  `Project.swift`'s own `featureModules` list (every entry gets the same generic
  target structure + `CoreDesignSystem`/`CoreNetwork`/`CoreIdentity` wiring
  automatically — adding a Feature's real content is just dropping `.swift` files into
  its `Sources/` and removing the placeholder `Dummy.swift`, no per-module manifest
  edits needed). Confirmed 2026-09-07, real per-module file counts: `Banking`/`Credit`/
  `Maps`/`Payments`/`Home`/`Menu`/`My`/`Pay`/`Wealth`/`Assets`/`Certificate`/`Identity`/
  `Support`/`Eats`/`Ride`/`Shop` all have real, populated Feature modules (`Eats`
  extracted 2026-09-06, `Ride` and `Shop` extracted 2026-09-07, all out of
  `App/Sources`, each product-completeness pass's own real architecture-consistency
  fix — see each pass's own real blockers: `RouteMiniMap`/`SearchAndCategoryChips`/
  `SilentLocationFetcher` promoted to `Core/DesignSystem/Sources/Components` since
  still-App-only Hood/Shop screens also needed them; `TalkScreen.errorMessage`/
  `ShopBestSellerBadge`/`colorFromHex`/`eatsGoodPointOptions` given local per-file
  copies, matching `FeatureMaps`'s/`FeatureMy`'s own existing duplicate-small-utility
  convention; `ReorderButton` needed a real duplicate in Commerce's
  `ShopMerchantOrders.swift`, same as Android already has; `Ride`'s own
  `colorFromHex` reuse site (`ShopProductDetail.swift`) got the identical fix;
  `Shop`'s own extraction found a whole extra file only a real compiler error
  surfaced (`SimpleLiveRiderMiniMap.swift`, never promoted during the Eats pass,
  moved alongside its only real consumer `ShopMerchantOrders.swift`) and correctly
  left `ShopPay.swift` in `Features/Pay/Sources` despite its name — its real
  consumer `PayHomeExtras.swift` lives in that same module, and moving it would
  violate the Feature-isolation boundary; Android's own `:features:shop:impl`
  placement for the equivalent code is a real cross-platform naming inconsistency,
  not evidence of an iOS placement bug). `Bills`/`Insurance` are correctly empty
  (Saronite/React-Native mini-apps, not native). `Engagement`/`Merchant` remain
  empty on both platforms — a real product-scope gap (no mapped screen yet),
  matching Android exactly. `tuist generate && pod install` before `xcodebuild`.
- `packages/design-tokens/tokens.css` — the single real source of truth for color/type
  tokens across every web workspace; matches Toss's own published TDS hex values exactly.

## Enforcement already wired into CI

`.woodpecker/*.yml` (real, open-source, self-hosted parallel path to
`.github/workflows/ci-cd.yml` — see `docs/CI_WOODPECKER_SETUP.md`) runs the same checks below;
keep both in sync when adding a new one.

- `.dependency-cruiser.cjs` (JS/TS): forbids one micro-frontend importing another's `src`
  directly.
- Konsist (Android) / a standalone boundary script (iOS): forbid cross-Feature-module
  `impl`-to-`impl` imports.
- `.oxlintrc.json` (root + per-workspace, since a workspace's own config does NOT inherit
  root's rules): `no-nested-ternary` is `warn` repo-wide.
- `scripts/accessibility-lint.py`: real accessibility checks on changed web files.
- `scripts/file-size-lint.py`: the "everything in one file" guardrail — freezes every
  currently-oversized file (`scripts/file-size-baseline.json`, 79 files across web/
  Android/iOS, `BankDashboard.tsx` and `MapsScreen.kt`/`HoodScreen.swift`/`ShopScreen.kt`
  among them) at its recorded line count; fails if a baselined file grows past it, or if
  any new file crosses 500 lines. Never bump the baseline just to make a red run green —
  extract instead.

When you add a new boundary or rule, verify it actually fires (deliberately trigger a
violation, confirm it's caught, then fix/revert) before trusting a clean run — "0
violations" can mean the rule silently isn't running.

## Docs that are history, not guides

`docs/DESIGN_REFERENCES.md` and `docs/ARCHITECTURE.md` are narrative changelogs of what
was built and why, valuable for context but not prescriptive — don't treat "this is what
we did in section 47" as "this is the rule going forward." The two guideline docs above
are the rules.
