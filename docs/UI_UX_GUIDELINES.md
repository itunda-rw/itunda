# itunda UI/UX Guidelines

**Read this before building or changing any user-facing screen.** These are prescriptive
rules synthesized from real, published guidelines of trusted product ecosystems, chosen
because each names a genuine, sourced failure mode and a genuine, sourced fix — not
because it sounds impressive. Every rule below traces to a real source; do not add a rule
here that doesn't. If you find a real conflict between a rule here and what's actually
shipped, fix the code or fix this doc — never let them silently drift apart.

This is a companion to [`docs/AI_AGENT_SELF_CHECK.md`](AI_AGENT_SELF_CHECK.md) (read that
one first — the self-check for whether you're actually applying these rules or just
producing code that looks like it does), [`docs/ARCHITECTURE_GUIDELINES.md`](ARCHITECTURE_GUIDELINES.md)
(code/architecture rules), and [`docs/COPY_VOICE.md`](COPY_VOICE.md) (itunda's own copy
register, already itself an example of rule 6 below). For narrative "what was built and
why" history, see `docs/DESIGN_REFERENCES.md` — that's a changelog, not a guide; this file
is the guide.

## 1. One screen, one job (Toss)

Toss's own real Product Principles (`toss.im/tossfeed/article/tossproductprinciples`):
**One Thing** — every page delivers one core message, refined to its essentials. Toss's
own real Product Owner account of *why* (`toss.im/tossfeed/article/importance-simplicity`)
goes further: a stricter version than "one message" — every real Toss screen has **exactly
one button** the user needs to press to move forward. Toss genuinely tested putting
multiple services on the home screen and reversed course when it didn't help.

**How to apply**: before adding a second primary action to a screen, ask whether it's
really the same job. `FullScreenFlow` (`bank-mfe/src/BankDashboard.tsx`) is itunda's own
real, shipped mechanism for this — a multi-step flow gets ONE full-screen page per step
with ONE pinned `bottomCTA`, not a compound form as one busy page. Applied to `TransferFlow`
and all four savings-creation flows already; use it for any new multi-field flow.

## 2. Explain why, don't just demand (Toss)

Toss's real Product Principles name **Explain Why** ("never assume what's obvious to us is
obvious to users") as a distinct principle from Clear Action. A required step with no
stated reason reads as arbitrary friction even when it's genuinely necessary.

**How to apply**: any screen that asks for something costly (a document upload, a
permission grant, a password re-entry) states the real reason inline, grounded in real
itunda data where possible — not an invented claim. Example already shipped:
`IdentityView` now shows the real, live Credit Score point value
(`fetchCreditScoreSuggestions`) a KYC approval awards, instead of opening straight into a
form with no context.

## 3. Match the mechanism to the physical situation, not the product's mood (Toss, corrected live by the user)

The most concrete lesson from this project's own history: a QR code only works between two
people physically in front of each other. Don't reach for "use a QR code" as a default
simplicity fix — triage by real situation:

- **In-person / co-located** (paying a merchant you're standing in front of): camera scan
  is correct. See `QrScanCamera` in `BankDashboard.tsx`.
- **Remote** (inviting a friend who isn't in the room): a tap-to-join **share link**
  (`navigator.share`, Kakao's own real invite-link pattern) is correct; QR is a secondary
  "if they're standing next to you" option, and a typed/read-aloud code is the last resort.
- **Never** make typing or reading out a code the *only* path. See
  [[feedback_no_manual_codes_ux]] (session memory) for the full account and the 4 real
  bank-mfe fixes this produced.

## 4. Clarity, deference, depth, consistency (Apple HIG)

Apple's Human Interface Guidelines' real, foundational framework, still the reference every
platform-native design system (including Toss's own TDS) ultimately measures against:

- **Clarity**: text legible at every size, icons precise, no ambiguous controls.
- **Deference**: the interface never competes with the user's own content — chrome
  recedes, content leads.
- **Depth**: layering/motion communicate real hierarchy, not decoration for its own sake
  (this is the same real constraint Toss's own interaction-decision framework enforces —
  see rule 7).
- **Consistency**: a control behaves the same way everywhere it appears. This is the
  platform-native argument for itunda staying visually close to each OS's own idioms on
  Android/iOS rather than forcing one cross-platform look — itunda's own Android/iOS
  design systems already follow this (Compose Material-adjacent on Android, native
  SwiftUI conventions on iOS), matching TDS itself doing the same per-platform adaptation.

**How to apply**: when a screen feels cluttered, ask which of the 4 is actually violated
before reaching for a generic "simplify" pass — usually it's Deference (too much chrome
around the real content) or Clarity (an ambiguous icon/label), not raw information density.

## 5. Motion only if it earns its cost (Toss)

Toss's own real, named early misconception (`toss.tech/article/interaction`): "thinking of
interaction as just a tool to make things prettier." Their real decision tree: ship if it
demonstrably moves a metric; maybe ship if there's no metric but real team consensus;
discard if there's no metric AND real engineering cost. Real discarded examples exist in
their own writeup (a tab sidebar animation, an ID-scan screen, a card-issuance screen).

**How to apply**: before adding an animation, state which real, measurable thing it's
supposed to improve (task completion time, a genuinely confusing state transition made
legible) — "it'll feel nicer" is not sufficient justification by Toss's own real bar.
itunda's own shipped example: `useCountUp` on the Bank balance display is justified because
an instant balance jump after a transfer is genuinely disorienting, not because animation
is inherently good.

## 6. Say what's missing *and* what fixes it (itunda's own, Baemin-influenced)

See `docs/COPY_VOICE.md` in full — itunda's own real copy-voice guide, itself modeled on
Baemin's real, named voice (배민다움) as proof that copy register is a legitimate, separate
design lever. The compressed rule: every empty/failure state names the real object and the
real fix, attributes the cause honestly (yours to fix vs. someone else's gap), never a bare
"No X yet."

## 7. Everyday language, not internal vocabulary (Toss + Kakao)

Toss's real Product Principles: **Casual Concept** — replace technical jargon with
everyday language "anyone can grasp instantly." A raw backend enum (`NATIONAL_ID`,
`PENDING`) or an internal domain term leaking into UI copy is the same failure whether
it's a full sentence or a single label.

**How to apply**: any place a backend `status`/`type` string is rendered directly needs a
real humanized-label lookup next to it, not the raw value. Fixed example: `IdentityView`'s
`IDENTITY_STATUS_LABELS`/`IDENTITY_DOCUMENT_LABELS` maps. Grep for `{.*\.status}` and
`{.*\.type}` rendered directly in JSX as a real audit starting point — not yet swept
repo-wide.

## 8. Design tokens are the only source of truth for color/type/theme (Kakao)

Kakao Style's own real 2024 design-system rebuild
(`devblog.kakaostyle.com/ko/2024-12-13-1-rebuilding-frontend-design-system`) — moved from
runtime CSS-in-JS to CSS custom properties specifically so theme (dark/light) switching is
browser-native via a `data-theme` attribute, not a React re-render, and so no component can
silently drift from the shared token contract. itunda already has the equivalent real
mechanism: `packages/design-tokens/tokens.css` (confirmed to already match Toss's own
published TDS hex values exactly — see [[project_itunda_product_feel]]).

**How to apply**: never hardcode a hex color or a raw pixel font-size in new UI code — use
`var(--itunda-*)`. `kyc-mfe`'s own hardcoded-hex-to-token conversion (real, already done,
see `docs/ARCHITECTURE.md`) is the fix pattern if you find a screen that still hasn't
adopted tokens.

## 9. One real component, never a local fork (Toss)

Toss's own real, named incident (`toss.tech/article/rethinking-design-system`,
"디자인 시스템 다시 생각해보기"): components drift when every screen hand-rolls its own
version instead of importing the shared one. itunda hit this exact failure for real:
`grep`-confirmed 415+ raw `<button className="itunda-btn ...">` occurrences in
`BankDashboard.tsx` alone instead of the real shared `IdsButton` component, each one a
place accessibility/disabled-state/`type="button"` behavior could silently diverge.

**How to apply**: before writing `<button className="itunda-btn ...">` or any other raw
element that duplicates an existing shared component's job, use the shared component
(`IdsButton`, `Badge`, `EmptyState`, etc.) instead. The 415+ existing raw buttons are a
real, tracked, NOT-yet-fixed backlog — don't add to it, and prefer converting one when
you're already touching that screen for another reason.

## 10. Flat over card-heavy (Toss, direct 2026-08-21 user correction)

Real Toss Pay Money detail screen (user-provided screenshots, 2026-08-21): balance,
Send/Add money buttons, and the transaction statement all sit directly on the page
background — no `itunda-card` wrapper anywhere on that screen. Sections are separated
by a thin rule or a bottom border on each row, not by boxing content in cards. Direct
user instruction from the same screenshot: "that's how I want our all designs to be
(flat and beautiful) like that." itunda's own screens lean heavily on `itunda-card`
(the real Toss reference itself is far more selective about it — cards for genuinely
separable, tappable *modules* like a swipeable account carousel, not for every section
of a single linear screen).

**How to apply**: on a new screen, default to flat sections (page background + a rule/
border between them) unless the content is a genuinely separate, self-contained module
(a carousel, a distinct product entry point). Don't wrap a screen's balance headline,
primary action buttons, or a list in `itunda-card` reflexively. This is a direction for
new/touched screens, not a retroactive sweep — itunda's existing card-heavy screens
(most of `BankDashboard.tsx` and its Android/iOS equivalents) haven't been converted;
convert one when you're already touching that screen for another reason, same
discipline as rule 9's `IdsButton` backlog.

## Standing checklist before shipping a new screen

1. Does it do more than one job? → apply rule 1.
2. Does it ask for something without saying why? → apply rule 2.
3. Does it ask the user to type/read a code? → apply rule 3's triage.
4. Is chrome competing with content, or is a control ambiguous? → apply rule 4.
5. Is there an animation with no stated metric it improves? → apply rule 5.
6. Is there a bare "no X yet" or unhumanized backend value? → apply rules 6-7.
7. Is there a hardcoded hex/px instead of a token, or a raw `<button>`/duplicated
   component instead of the shared one? → apply rules 8-9.
8. Is a linear screen's content boxed in `itunda-card` out of habit rather than because
   it's a genuinely separate module? → apply rule 10.
