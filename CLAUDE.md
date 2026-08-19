# itunda — repo instructions for AI agents

itunda is a Rwanda-market super-app modeled on real, sourced Toss/Kakao/Naver/Coupang/
Baemin/Uber/Karrot product and engineering practices. This is a multi-stack monorepo, not
a single framework — don't assume Node.js/npm/NestJS conventions apply repo-wide.

**Before writing any UI/UX or architecture code, read these two files in full:**

- [`docs/UI_UX_GUIDELINES.md`](docs/UI_UX_GUIDELINES.md) — sourced UI/UX rules (Toss,
  Kakao, Apple HIG, itunda's own copy voice), with a standing pre-ship checklist.
- [`docs/ARCHITECTURE_GUIDELINES.md`](docs/ARCHITECTURE_GUIDELINES.md) — sourced
  architecture/code-quality rules (Toss Frontend Fundamentals, Uber DOMA, Netflix, Spotify),
  with a standing pre-commit checklist.

Both are kept synchronized with what's actually shipped — if you make a decision that
should generalize, add it there in the same pass rather than leaving it for the next
agent to rediscover. If you find either doc contradicting real shipped code, fix
whichever one is wrong; never let them silently drift apart.

## Real stack, per workspace

- `services/backend` — the canonical backend: Kotlin + Spring Boot + Spring Data JPA +
  MySQL + Flyway (`ddl-auto: validate`, never `update`) + Spring Security JWT. Tests use
  Kotest + MockK.
- `services/microservices/{payment-service,ledger-service,core-libs}` — a real, parallel,
  not-yet-reconciled hexagonal-architecture MSA prototype. Not superseded, not the default
  for new feature work.
- `services/micro-frontends/*` — Vite + React + TypeScript, Yarn workspaces + PnP
  (`yarn install` at root, `yarn workspace <name> run <script>` per package). `bank-mfe`
  is the real, deployed consumer super-app (not `host-app`'s module-federation shell,
  which is a mostly-unused 2-tab demo). Verification today: `tsc -b` + `vite build` +
  `oxlint` + `python3 scripts/accessibility-lint.py <file>` + a real browser click-through
  against the live deployed backend — there is no unit-test runner wired in yet.
- `android/` — Kotlin + Jetpack Compose, real Gradle Feature-module isolation
  (`:features:<name>:{api,impl,testing}`, enforced by Konsist in CI). `:app:compileKotlin`
  or `:features:<name>:impl:compileDebugKotlin` to verify.
- `ios/` — Swift + SwiftUI, `Core`/`Features/<Name>` module split (in progress, not every
  feature moved yet), plus real React Native "Saronite" mini-apps for some features
  (Bills/Insurance). `tuist generate && pod install` before `xcodebuild`.
- `packages/design-tokens/tokens.css` — the single real source of truth for color/type
  tokens across every web workspace; matches Toss's own published TDS hex values exactly.

## Enforcement already wired into CI

- `.dependency-cruiser.cjs` (JS/TS): forbids one micro-frontend importing another's `src`
  directly.
- Konsist (Android) / a standalone boundary script (iOS): forbid cross-Feature-module
  `impl`-to-`impl` imports.
- `.oxlintrc.json` (root + per-workspace, since a workspace's own config does NOT inherit
  root's rules): `no-nested-ternary` is `warn` repo-wide.
- `scripts/accessibility-lint.py`: real accessibility checks on changed web files.

When you add a new boundary or rule, verify it actually fires (deliberately trigger a
violation, confirm it's caught, then fix/revert) before trusting a clean run — "0
violations" can mean the rule silently isn't running.

## Docs that are history, not guides

`docs/DESIGN_REFERENCES.md` and `docs/ARCHITECTURE.md` are narrative changelogs of what
was built and why, valuable for context but not prescriptive — don't treat "this is what
we did in section 47" as "this is the rule going forward." The two guideline docs above
are the rules.
