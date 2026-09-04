# itunda Architecture & Code-Quality Guidelines

**Read this before adding a new file, module, service, or any non-trivial function.**
Prescriptive rules synthesized from real, published engineering practices at Toss, Kakao,
Spotify, Netflix, and Uber, chosen because each names a real, sourced failure mode this
repo has *actually hit* (cited inline) — not generic "clean code" platitudes. Every rule
traces to a real source. This is a companion to
[`docs/AI_AGENT_SELF_CHECK.md`](AI_AGENT_SELF_CHECK.md) (read that one first) and
[`docs/UI_UX_GUIDELINES.md`](UI_UX_GUIDELINES.md); for narrative history of what was built
and why, see `docs/DESIGN_REFERENCES.md`/`docs/ARCHITECTURE.md` — those are changelogs,
not guides.

## 1. Good code = easy to change (Toss Frontend Fundamentals)

Toss's real, open-sourced developer guide (`frontend-fundamentals.com`,
`github.com/toss/frontend-fundamentals`) defines good code by 4 criteria that trade off
against each other — don't chase one at the expense of the others:

- **Readability**: reduce context a reader must hold (separate non-concurrent code,
  abstract implementation details, one logic type per function), name complex conditions
  and magic numbers, read top-to-bottom (fewest viewpoint shifts, **no nested ternaries**).
- **Predictability**: a function/component's name should truthfully predict its behavior —
  unify return types across similar functions, never hide a surprising side effect behind
  an innocent name.
- **Cohesion**: code that changes together should live together (directly the argument
  for §2 below), name away magic numbers.
- **Coupling**: minimize blast radius — single responsibility, eliminate props drilling,
  and (the genuinely non-obvious one) **tolerate duplication over premature coupling**. A
  "DRY" fix that merges two superficially-similar-but-semantically-separate flows into one
  shared function is itself the anti-pattern, not an improvement.

**Enforced today**: `no-nested-ternary` is a real, live `oxlint` rule (`warn`, CI-safe) in
every workspace's `.oxlintrc.json` — 163 pre-existing violations in bank-mfe are a tracked
backlog, not yet fixed. Before proposing a NEW lint rule, verify it actually fires (`yarn
oxlint -D <rule> <test-file>`) and check its real noise level against this codebase first —
`no-magic-numbers` was deliberately NOT enabled repo-wide because it's extremely noisy
against thousands of legitimate CSS pixel/opacity literals; it needs per-file scoping to be
useful, not a blind repo-wide flag.

## 2. Code that changes together lives together (Uber DOMA + Toss cohesion)

Uber's real Domain-Oriented Microservice Architecture (`uber.com/blog/microservice-
architecture`) groups related services into **domains by logical function, never by org
chart**, with a strict layer hierarchy (infrastructure → business → product → presentation
→ edge) where **a layer only depends on the layer below it** — this is what keeps the
"blast radius" of a change predictable at 2,200+ services. Toss's own Frontend Fundamentals
names the frontend-scale version of the same idea: cohesion means files that must change
together live in the same directory.

itunda's own real, live violation of this, found and being actively fixed
(`docs/DESIGN_REFERENCES.md` §202): `BankDashboard.tsx` is 23,000+ lines holding every
product (Bank/Pay/Eats/Marketplace/Community/Messages/etc) in one file — by Uber's own
framing, this is a "networked monolith": no real module boundary means no real independent
blast radius, regardless of how the code is internally organized. Android's own
`:features:maps` module is the cautionary tale one level down: having a REAL module
boundary doesn't guarantee internal cohesion — `MapsScreen.kt` was still a 3,090-line,
40-state-variable monolith bundling 8 real, separable features (search, navigation,
bookmarks, nearby-browse, place-detail, bus-trips, ruler-tool, style/rendering) inside ONE
function, which had already hit a real JVM "Method too large" compile error. A full repo
sweep (2026-08-19) found this is not a two-file problem: `ShopScreen.kt` (3,629 lines),
`TalkScreen.kt` (3,595), `HoodScreen.swift` (4,058), `TalkScreen.swift` (3,368), and 84
more files across web/Android/iOS are already over 500 lines — the same shape of mistake,
repeated on every platform, every product.

**Real, automated enforcement, not just this prose**: `scripts/file-size-lint.py` (wired
into CI) freezes every currently-oversized file at its real, recorded line count in
`scripts/file-size-baseline.json` — a file already in the baseline that grows PAST its
recorded count fails CI, and any file NOT in the baseline that crosses 500 lines for the
first time fails CI too. Existing giants are grandfathered so this doesn't block on ~90
files needing rework in one shot, but they can't silently keep growing, and nothing new
can quietly become the next one. Decomposition (shrinking a baselined file) is always
free; growth requires a deliberate `--update-baseline` run with the reason stated in the
commit.

**How to apply**: before adding a new screen/feature, ask which existing file/module it
should live in by what it does, not by where it's convenient to paste it. When touching a
file that's already known to be oversized, prefer extracting the piece you're touching
into its own file/module over adding more to the pile — matching the safe, staged pattern
already used for `MapStyle.kt`/`MapUiComponents.kt` (move purely stateless code first,
zero behavior risk) before attempting anything that needs real state-holder redesign. If
`file-size-lint.py` fails on your change, that is the signal working as designed — extract,
don't bump the baseline reflexively.

## 3. One real component, never a local fork (Toss)

Toss's own real, named incident (`toss.tech/article/rethinking-design-system`): components
drift when every call site hand-rolls its own version instead of importing the shared one.
itunda hit this for real — 415+ raw `<button className="itunda-btn ...">` in
`BankDashboard.tsx` instead of the shared `IdsButton`. Same failure mode applies to any
shared logic, not just UI components: a second `submitJoinCode`-shaped function copy-pasted
with a typo is the code-level version of the same drift.

**How to apply**: before writing a new function/component, grep for whether one already
exists doing the same job. If two near-identical ones already exist and neither is clearly
canonical, that's real debt — don't add a third; consolidate or clearly document why they
must stay separate (per rule 1's "tolerate duplication" carve-out: only if merging them
would actually couple two unrelated concerns).

## 4. Operate what you build (Netflix)

Netflix's real "Full Cycle Developer" model (`netflixtechblog.com/full-cycle-developers-
at-netflix`): the team that builds a service also operates it — no separate ops team to
externalize the consequences of a bad design onto. Complemented by their real "Paved
Road" philosophy: centrally-supported tooling wins by being genuinely better to use, not
by being mandated.

**How to apply**: when you ship a feature, you own its live verification — this repo's own
established, working discipline (build → typecheck → lint → live browser click-through
against the real deployed backend, not just "it compiles"). When you find a systemic gap
(a missing accessibility check, a missing lint rule), build the paved road
(`scripts/accessibility-lint.py`, `.dependency-cruiser.cjs`, the `no-nested-ternary`
guardrail) rather than fixing the one instance and leaving the next agent to repeat the
same mistake blind.

## 5. One documented, honest path per discipline (Spotify)

Spotify's real "Golden Path" concept (`engineering.atspotify.com/.../golden-paths-to-
solve-fragmentation`): ONE opinionated, documented, supported route per kind of work,
explicitly to prevent "rumour-driven development" — engineers discovering the real way to
do something only through informal conversation, never written down. Their own stated
discipline: a Golden Path doc must reflect the ACTUAL practice; if the real path is too
long/awkward to document cleanly, the fix is simplifying the real path, not writing a
prettier lie.

itunda's own real violation of this, found in this same pass: `CONTRIBUTING.md` described
NestJS/TypeORM/`npm install` — none of which this repo actually uses (`services/backend`
is Kotlin+Spring Boot+MySQL; the JS workspaces use Yarn, not npm). A doc that doesn't match
reality is actively worse than no doc for an agent with no other context.

**How to apply**: this file and `UI_UX_GUIDELINES.md` ARE itunda's golden paths for
UI/UX and architecture — keep them synchronized with what's actually shipped. If you make
an architectural decision that should generalize (a new shared pattern, a new convention),
add it here in the same pass, don't leave it undocumented for the next agent to
rediscover independently.

**A real itunda-own instance of this, found 2026-09-04**: iOS has four separate
`NetworkClient.swift` copies (`Core/Network` for the main app, plus one each for
`MerchantApp`/`RiderApp`/`AgentApp`). Each app's shared low-level POST helper
(`authenticatedPost`/`sendRequest`) deliberately throws a message-less error for most
callers — widening it to carry the backend's real message would break every existing
`catch NetworkError.httpError(let statusCode)` pattern-match across that app's UI (up to
96 sites on the main app alone). The correct, established golden path when a SPECIFIC
endpoint's real backend message is worth surfacing: add one new, narrowly-named dedicated
function (`postEatsOrder`, `postSavingsGoal`, `postP2p`, etc.) that mirrors the shared
helper's body but throws the existing `NetworkError.httpErrorWithMessage(statusCode,
message)` case instead, switch just that endpoint to it, and update only that endpoint's
own UI catch site(s). Never widen the shared helper's arity itself — that's the "prettier
lie" version of this problem (looks like one fix, is actually ~96 silent behavior
changes). See `project_itunda_ios_error_message_gap` memory for the full account,
including which of the four NetworkClient copies still need incremental per-endpoint
conversions and which were fixed at the root (MerchantApp/RiderApp, both small and
centralized enough that one root-level fix was safe).

## 6. Module boundaries need real enforcement, not just intent (Toss silo model)

Toss's own real internal team structure — "loosely coupled, tightly aligned" silos
(researched and applied in [[project_itunda_multi_agent_isolation]]) — only works because
it's backed by real, automated enforcement, not good intentions: `.dependency-cruiser.cjs`
+ `yarn depcruise` in CI (JS/TS), Konsist (Android), and a standalone boundary-check script
(iOS) each verified to actually catch a real violation before being trusted (added a
deliberate cross-module import, confirmed it failed, reverted, confirmed clean).

**How to apply**: when you add a new module boundary (a new Feature module, a new
micro-frontend), add or extend the matching enforcement in the same pass — a boundary that
nothing checks will silently erode. When you add a new lint/architecture rule (like
`no-nested-ternary` this pass), always verify it actually fires with a throwaway test file
before trusting a clean run — "0 violations" can mean "the rule silently isn't running"
just as often as "there are really zero violations" (a real, previously-hit failure mode
with dependency-cruiser's own path-vs-glob resolution, see
[[project_itunda_multi_agent_isolation]]).

## 7. A name is a promise the code must keep (Toss Frontend Fundamentals)

The same real, open-sourced guide behind §1 has a dedicated real naming discipline under
its Readability and Predictability criteria — sourced directly from
`github.com/toss/frontend-fundamentals` (`condition-name.md`, `magic-number-readability.md`,
`http.md`, `use-user.md`), not invented. Four real rules:

- **Name complex conditions, don't leave them anonymous.** A multi-clause boolean buried
  inside a `.filter()`/`if` forces the reader to hold every sub-condition in their head at
  once. Extract to a named `const isSameCategory = ...` / `val isEligible = ...` /
  `let hasEnoughBalance = ...` before combining — but only when the logic is genuinely
  complex or reused; a one-line `arr.map(x => x * 2)` doesn't need a name for the mapping
  function. Applies equally to itunda's own real multi-clause `enabled = !busy && ...`
  guards on money-flow buttons (`RideScreen.kt`, `TransferFlow.kt`) — most are still short
  enough to stay inline, but the moment one grows a third clause, name it.
- **Name magic numbers.** Any numeric literal whose meaning isn't self-evident from
  immediate context (a delay in ms, a retry count, a threshold) becomes a named constant —
  `const ANIMATION_DELAY_MS = 300`, not a bare `300` passed to `delay()`. itunda already
  does this in most money-amount/threshold contexts (`MAX_SCORE`, `SEED_IDS`); the real
  gap is timing/retry constants scattered as bare literals across `catch`/retry blocks —
  audit those specifically, not amounts (already well-named).
- **A wrapper must not share a name with what it wraps if the behavior differs.**
  Toss's own real example: a service wrapped `http` around a library also called `http`,
  and `http.get()` silently added auth-token injection the library's own `get()` never
  did — a name that looks identical but behaves differently is a real, sourced bug class,
  not just a style nit. Rename to reveal what's actually different (`getWithAuth`, not
  `get`). Check this specifically wherever itunda wraps a platform/library primitive with
  its own logic added — `NetworkClient`'s various `*Api` properties, `IdsButton`/
  `IdsTextField` wrapping Material3's own `Button`/`TextField`, `pressScaleClickable`
  wrapping `Modifier.clickable` (this last one already follows the rule correctly — the
  name itself states what's different, scale feedback, not just "Clickable" again).
- **Unify names AND return shapes across a family of same-shaped functions.** Every
  `useXxx` data hook in a codebase should return the same shape (always the query object,
  never data-sometimes/query-object-other-times); every `checkIsXxxValid`-style validator
  should return the same `{ ok, reason }` shape. The name alone should let a reader predict
  both the CALLING convention and the RETURN shape without opening the function. Real,
  concrete itunda analogue: every `MoneyActionResult`-returning function (`sendTransfer`,
  `sendGift`, `depositToSavingsGoal`, …) already does this correctly (one shared sealed
  interface — `Success`/`Queued`/`Failure`/`DeviceNotVerified` — reused verbatim rather
  than each function inventing its own ad hoc success/error shape); use that as the
  reference pattern when adding a new money-moving function, not a new bespoke return type.

**How to apply**: when reviewing your own new code before committing, ask (1) does any
condition here need a name, (2) does any bare number here need a name, (3) if this wraps
something else, does its name honestly signal what's different, (4) if this is one of a
family of similar functions, does its name AND return shape match its siblings. A rename
that makes behavior more predictable is worth doing even mid-task, not deferred to a
separate cleanup pass — Toss's own guide frames this as inseparable from readability, not
optional polish.

## Standing checklist before adding new code

1. Does a shared version of this already exist? → apply rule 3.
2. Does this belong in the file/module I'm about to paste it into, by what it does? →
   apply rule 2.
3. Am I nesting a ternary, hiding a side effect behind an innocent name, or duplicating
   just to avoid a slightly-different sibling? → apply rule 1.
4. Have I actually verified this end-to-end (build, lint, live), not just "it compiles"? →
   apply rule 4.
5. Is this a new convention that should be written down here? → apply rule 5.
6. Did I add a new boundary/rule without also adding something that enforces it? → apply
   rule 6.
7. Does every name I just wrote truthfully predict its behavior, return shape, and how it
   differs from anything it wraps? → apply rule 7.
