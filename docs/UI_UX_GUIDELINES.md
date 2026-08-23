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

**Corollary, 2026-08-24 (direct user follow-up, same real Toss screenshots)**: the
page canvas itself should be white, not the grey `--itunda-grey-100` /
`Ids.colors.surfaceSoft` / `IDS.Colors.chipBackground` shade — every real Toss screen
referenced above, card-heavy or flat, sits on pure white; grey is reserved for
genuinely inset fills (search bars, chips, segmented-control tracks), never the page
itself. This was a real, repo-wide gap: itunda's `body`/`#root` (web),
`IdsLightSemanticColors.background` (Android), and `IDS.Colors.backgroundPrimary`
(iOS) all used the grey token as the page default. Fixed across all 3 platforms this
pass (commits `b7a7908a`, `a5571921`, `0cfdd908`) — this is now the standing default,
not an opt-in. One consequence worth knowing before touching a card-bearing screen:
with the page now white too, a white card has nothing left to separate it from its
background unless it adds its own hairline border (`var(--itunda-grey-200)` /
`Ids.colors.divider` / `IDS.Colors.divider`) — web's `.itunda-card`, Android's
`IdsCard`, and iOS's new `idsCardBorder(cornerRadius:)` extension all do this now, so
prefer those over a raw inline card box that would otherwise render invisibly.

**Second corollary, same day (direct user follow-up on the itunda Bank screen)**: a
flat *product-catalog* list (Save & Grow's items, a group-accounts list, a savings-
plans list — a short list of distinct features/entities a user picks from) separates
rows with whitespace alone, no divider per row — real Toss doesn't put a line under
every item. Reserve the divider for the boundary *between* whole sections (e.g.
between the standalone Auto-transfer row and the Save & Grow catalog below it), never
between individual rows inside one section/list. This is a different real convention
from a *transaction/statement ledger* (a dense, numbered list of past transactions) —
those keep a per-row divider deliberately (see iOS's `AccountLedgerDetailRow`/
`PayMoneyDetailScreen`, unchanged in this pass) since that's a real, separate Toss
pattern, not the same bug. Fixed the catalog-list case across all 3 platforms this
pass — Android's `ShellSection` (`40b3ead3`), bank-mfe's `accounts.map`/
`ikiminas.map`/two `plans.map` call sites (`593c62b6`), iOS's `HomeSectionCard`
(`bc62444a`).

## 11. itunda's brand identity: petal mark, indigo primary (2026-08-22)

itunda's app icon and primary brand color, after a full 14-shape x 16-color
exploration pass against a real ledger-screen/launcher-icon test harness (see
`itunda-identity.html`, [[project_itunda_brand_identity]]): the **petal** shape,
in **indigo** (`--itunda-indigo: #7472f4` light / `#7675f8` dark, replacing the old
`--itunda-blue`). Rendered as a unified two-facet 3D-gradient mark — one outer
silhouette split by a shared internal seam, not two independently-drawn shapes — on
a white canvas, matching Toss's real published App Store icon convention (fetched
and inspected directly, apps.apple.com id839333328) rather than a colored-square
background.

**New brand-adjacent colors are derived, never invented.** Toss's own real eng blog
(`toss.tech/article/tds-color-system-update`) documents how TDS generates a
consistent color family: move to OKLCH, hold a hue's lightness and chroma constant,
only rotate the hue angle, clamping chroma back into the sRGB gamut when a rotated
hue falls outside it. Indigo was derived this way directly from itunda's real
shipped blue anchor (not hand-picked) — this is the answer whenever a future
brand-adjacent color is needed ("what would itunda's X be"): rotate hue from a real
anchor, don't invent a hex.

**How to apply**: the mark and `--itunda-indigo`/`-active`/`-light` (web),
`Ids.colors.brand`/`textBrand`/`pressed` (Android/iOS), `colors.primaryIndigo`
(Saronite) are now the real, shipped brand tokens — reference them, don't
reintroduce a hardcoded blue literal. A wayfinding UI element that's brand-colored
on purpose (a map's "your route" line, "your location" dot) follows the same
rebrand; a color that's one option in an independent user-choice palette (bookmark
colors, category tags) does not — it isn't the brand pointer, just happens to share
a hue.

## 12. A product name is an ecosystem-wide promise, not a per-screen label (Google, Samsung,
Apple, Kakao, Toss)

Direct user correction (2026-08-23): "itunda account" (bare, no qualifier) was being used
for itunda's real MAIN financial-account row — but "[Brand] Account" already means something
specific and different across every major platform a user has already learned from: it's
**exclusively the SSO/identity layer**, never a specific money-holding product. Confirmed
directly, not assumed — Google Account, Samsung Account, and Apple Account (renamed from
Apple ID in 2024) are each their platform's one foundational login identity, existing
*alongside* rather than *as* any specific banking/payment product. Kakao Account (카카오계정)
is the identical pattern: the shared login across KakaoTalk/KakaoBank/KakaoPay, confirmed
distinct from KakaoBank's own real deposit accounts and KakaoPay's own separate e-wallet.
Toss goes further and doesn't even brand its identity layer at all — login is just phone
number + name, no "Toss Account" product name exists; "account" in Toss's own real
vocabulary is reserved exclusively for actual banking terms (토스뱅크 계좌, "Toss Bank
account"), and the stored-value e-money balance gets its own distinct, non-generic name
(토스머니, "Toss Money") rather than "Toss account" or "Toss wallet."

**A second, real, live collision found the same pass, worse than the first**: itunda's
KakaoBank-Mini-style capped teen banking product ("Mini account," ages 7-18) and Saronite's
real embedded partner mini-program framework ("Mini apps") were BOTH shipped, user-facing
labels in the same "All"/Explore-tab area on iOS (`BenefitsShopAllScreens.swift`) — and
Android had an actual grid tile labeled bare **"Mini"** (using an Apps icon) that opened the
banking product, not mini-apps. Checking KakaoBank's own real branding confirmed the fix
shape directly: their product is never referred to as bare "미니"/"Mini" — always the full
"카카오뱅크 미니" (KakaoBank Mini), the parent-product prefix travels with the qualifier every
time. itunda's own follow-up correction (direct user feedback): an abstract borrowed label
like "Mini" needs its own subtitle to explain itself (itunda's real existing copy already
carries `"Capped starter account, ages 7-18"` next to it) — a plain, descriptive name
wouldn't need that crutch at all, more in line with rule 7's own "Casual Concept" principle
than mirroring a borrowed brand term. Renamed to **"Youth account"** — a real, common banking
term that covers the full 7-18 range (unlike "Teen," which usually implies 13-19) without
needing an explanatory subtitle to be understood on sight.

**The standing rule, not just this one fix**: two real fixes, one general practice.

1. **A bare "[itunda] Account" is retired everywhere it names a specific financial product
   row or screen.** Always qualify it — "itunda Bank account" for MAIN, "itunda Pay" for PAY
   (already itunda's own established Bank/Pay-separation naming). Bare "itunda account"
   survives only in its real identity/signup sense ("has an itunda account" = "is signed up
   with itunda") — the same restrained usage Google/Kakao/Samsung/Apple make of their own
   bare "[Brand] Account" phrase, where it IS the identity term and is never reused for a
   sub-product.
2. **Before shipping any new user-facing product/feature name, grep the whole live codebase
   (all platforms) for the exact same bare qualifier word already in use elsewhere.** A bare
   generic word ("Mini," "Account," "Home," "Pay") is a real collision risk the instant two
   unrelated features both reach for it independently — the fix is always the same shape:
   attach the specific product/vertical prefix, never let a bare qualifier carry meaning
   alone. Don't discover this after shipping, the way both cases above were found.

**How to apply**: before naming a new screen, product, or section, check this rule first. If
the candidate name could plausibly collide with something that already exists (shares a bare
noun with unrelated meaning, or reuses "Account" for something that isn't the identity
layer), rename before shipping rather than after a live collision is found. This applies
ecosystem-wide — a name has to stay unambiguous across web, Android, and iOS at once, not
just within the one screen it was written for.

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
9. Does this new name collide with an existing one anywhere in the ecosystem, or misuse
   "[itunda] Account" for something that isn't the identity layer? → apply rule 12.
