# Accessibility audit (2026-07-11)

Real, computed findings against itunda's actual design tokens and actual UI code —
not estimates. Contrast ratios below are computed with the exact WCAG 2.1 sRGB
relative-luminance formula against the live values in
`packages/design-tokens/tokens.css` (the single source of truth also consumed by
Android's `TdsSemanticColors.kt` and iOS's `IDS.swift`, per `ARCHITECTURE.md` §5).
`contentDescription`/`accessibilityLabel` findings are from a full grep-and-read
audit of every icon usage in `ItundaAppScreen.kt` (Android) and every `Image(systemName:)`
usage under `ios/` (iOS).

## 1. Color contrast (WCAG 2.1)

Method: `relative_luminance = 0.2126*R + 0.7152*G + 0.0722*B` (linearized sRGB per
channel), `ratio = (L_lighter + 0.05) / (L_darker + 0.05)`. AA thresholds: 4.5:1 for
normal text, 3.0:1 for large text (18pt+/14pt-bold+) and UI components/graphical
objects (WCAG 1.4.11).

### Light theme

| Pair | Ratio | AA normal (4.5) | AA large/UI (3.0) |
|---|---|---|---|
| textPrimary `#191f28` on background `#f2f4f6` | 15.02:1 | PASS | PASS |
| textSecondary `#4e5968` on background `#f2f4f6` | 6.45:1 | PASS | PASS |
| ~~textTertiary `#8b95a1` on background `#f2f4f6`~~ → `#636e7c` (fixed, item 241) | 4.70:1 | PASS | PASS |
| textPrimary `#191f28` on card `#ffffff` | 16.56:1 | PASS | PASS |
| textSecondary `#4e5968` on card `#ffffff` | 7.11:1 | PASS | PASS |
| ~~textTertiary `#8b95a1` on card `#ffffff`~~ → `#636e7c` (fixed, item 241) | 5.18:1 | PASS | PASS |
| brand blue `#3182f6` on white | 3.71:1 | FAIL | PASS |
| ~~success green `#04c065` on white~~ → `#05804a` on white (fixed, item 240) | 5.01:1 | PASS | PASS |
| danger red `#f04452` on white | 3.71:1 | FAIL | PASS |
| white text on brand blue button | 3.71:1 | FAIL | PASS |

### Dark theme

Note: the background/surface hex values below (`#000000`/`#17181d`) predate the
2026-07-21 real-dark-palette correction (see `packages/design-tokens/tokens.css`'s
own header) and no longer match the live tokens (now `#17171c`/`#202027`) --
re-verified for `textTertiary` only, as part of fixing it (item 241); the rest of
this table has not been re-audited against the current values.

| Pair | Ratio | AA normal (4.5) | AA large/UI (3.0) |
|---|---|---|---|
| textPrimary `#ffffff` on background `#000000` | 21.00:1 | PASS | PASS |
| textSecondary `#989eaa` on background `#000000` | 7.81:1 | PASS | PASS |
| ~~textTertiary `#575c66` on background~~ → `#848a96` on real background `#17171c` (fixed, item 241) | 5.15:1 | PASS | PASS |
| textPrimary `#ffffff` on surface `#17181d` | 17.72:1 | PASS | PASS |
| textSecondary `#989eaa` on surface `#17181d` | 6.59:1 | PASS | PASS |
| ~~textTertiary `#575c66` on surface~~ → `#848a96` on real card `#202027` (fixed, item 241) | 4.67:1 | PASS | PASS |
| brand blue `#4c8fff` on surface | 5.64:1 | PASS | PASS |
| success green `#20d394` on surface | 9.12:1 | PASS | PASS |
| danger red `#ff6b7a` on surface | 6.44:1 | PASS | PASS |

### Findings

1. **FIXED (item 241, 2026-08-07).** `textTertiary` failed AA-normal-text contrast in
   both themes, and even the lenient large-text/UI threshold on the light-mode
   `background` and (re-verified against real current dark tokens) the dark-mode
   `background`/`card` (2.76:1 light-bg, 3.04:1 light-card, 2.66:1 dark-bg, 2.41:1
   dark-card). Since this token renders small caption/timestamp text at 493 real web
   call sites alone, it needed 4.5:1 everywhere it appears, not just the 3.0:1
   UI-component threshold. Light `#8b95a1` → `#636e7c` (4.70:1 / 5.18:1); dark
   `#575c66` → `#848a96` (5.15:1 / 4.67:1) — both the minimal step toward
   `textSecondary`'s own hue that clears 4.5:1 on both real backgrounds in each
   theme. Applied to `packages/design-tokens/tokens.css` `--toss-grey-500` (the
   real single source of truth, 493 consumers, all confirmed text/icon colors, none
   structural — verified via repo-wide grep before touching it) and its two mirrors
   (Android `IdsSemanticColors.kt`, iOS `IDS.swift`). The raw `Gray500`/`gray500`
   primitives used for non-text roles elsewhere were deliberately left untouched —
   iOS's is machine-generated from `tokens.json` and marked do-not-hand-edit. Found
   and fixed one real, separate bug along the way: `MapScreenView.swift` used that
   raw non-theme-reactive `IdsPalette.gray500` primitive directly for caption text
   (3 call sites) instead of the theme-reactive semantic token — swapped to
   `IDS.Colors.textTertiary`, which also fixes its dark-mode adaptivity, not just
   its contrast.
2. **FIXED (item 240, 2026-08-07).** `success` (green) on white failed contrast at
   every threshold in light mode (`#04c065`, 2.40:1, below even 3.0:1). Darkened to
   `#05804a` (5.01:1, comfortably clears AA-normal-text 4.5:1), same hue family, same
   fix already used for dark mode's own `#20d394`. Applied to the real single source
   of truth (`packages/design-tokens/tokens.css` `--toss-green`) and its two mirrors
   (Android `IdsSemanticColors.kt` `IdsLightSemanticColors.success`, iOS `IDS.swift`
   `IDS.Colors.success` — the latter previously didn't exist at all; real screens used
   SwiftUI's raw system `Color.green` directly, `#34c759`, which measured even worse
   at 2.22:1). iOS call sites swapped from `Color.green` to `IDS.Colors.success`:
   `BikeRentalScreenView.swift`, `ParkingScreenView.swift`, `InvestScreenView.swift`,
   `TalkScreen.swift`. Two more real duplicate hardcoded `#04C065` text colors found
   via repo-wide grep and fixed the same way: `packages/saronite/mini-apps/
   reward-tasks/pages/index.tsx` (`rewardAmount`) and `.../insurance_mini_app/pages/
   index.tsx` (`policyStatus`) — both real text-on-white-card usages, same failure
   mode. Dark mode's `#20d394` (9.12:1) was already passing and left unchanged.
3. **Brand blue, danger red, and white-on-blue-button all fail AA-normal-text (4.5:1)
   but pass AA-large/UI (3.0:1) in light mode.** This is fine for buttons and icons
   (UI-component threshold applies) but means these colors must not be used for
   small/normal-weight body text in light mode — only for button labels (which are
   typically bold/large enough) or icons/borders.
4. Both open items above (findings #1 and #2) are now fixed as of 2026-08-07 — see
   items 240/241. No open color-contrast findings remain in this section.

## 2. Icon-only interactive elements without an accessible name

### Android (`ItundaAppScreen.kt`)

Audited every `contentDescription = null` occurrence (18 total). Triage rule: `null`
is correct when the icon sits beside its own visible `Text()` in the same
row/column (the text already gives assistive tech the accessible name — a
non-null description would just be redundant); `null` is a real bug when the icon
is the *only* content of an interactive element.

Found and fixed 3 genuine bugs (7 icon usages), all icon-only buttons with no
adjacent text:
- `TopIconButton` composable — signature changed from `(icon: ImageVector)` to
  `(icon: ImageVector, contentDescription: String)` so a new call site can't
  silently reintroduce a hardcoded `null`. Call sites: QR scan, Notifications,
  Settings.
- `ShopTopBar()` — Person and ShoppingCart icons (`"Profile"`, `"Cart"`).
- `PayTopBar()` — QrCodeScanner and Public icons (`"Scan QR code"`, `"Language"`).

Remaining 12 `null`s confirmed decorative (paired with visible `Text()` in the same
row) — left as `null`, which is correct per Android's own accessibility guidance
(avoids double-announcing "icon, icon, label" to TalkBack).

Verified: `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`.

### iOS (`ios/**/*.swift`)

Audited every `Image(systemName:)` usage (4 files). Found and fixed 1 genuine bug:
- `BankView.swift`'s `TopBarActionButton` — an icon-only `Button` (bell, person)
  with no `.accessibilityLabel` at all; SwiftUI does not derive an accessible name
  from an SF Symbol's name, so VoiceOver would have announced these as bare
  "Button". Fixed by adding an `accessibilityLabel` parameter
  (`"Notifications"` / `"Profile"`) and applying `.accessibilityLabel(...)` to the
  `Button`.

Other `Image(systemName:)` usages checked and left alone:
- `ContentView.swift`'s tab items (`house.fill`/`diamond.fill`/etc.) each sit next
  to a `Text()` label inside the same tab item — the system TabView already exposes
  the combined label to VoiceOver.
- `ContentView.swift`'s `HeaderTitle`'s `bell.fill` is not wrapped in a `Button` —
  it's a static, non-interactive decorative image next to a heading, not an
  accessibility violation.
- **FIXED (item 242, 2026-08-07).** `AgreementWidget.swift` / `PaymentMethodWidget.swift`
  — icons inside `Button`s that also contain `Text()` (terms checkbox, payment
  method rows). SwiftUI's `Button` already merges its subviews into one spoken
  accessibility element by default, so this was never a hard "unlabeled control"
  violation — but the checkbox/selection-indicator icons (`checkmark.square.fill`/
  `square`, `checkmark.circle.fill`/`circle`) had no explicit accessibility
  treatment, so their raw SF Symbol names would get folded into the combined
  spoken label as redundant noise alongside the real text, and selected/checked
  state was conveyed only by which icon shape was showing rather than through the
  standard `.isSelected` accessibility trait. Fixed by marking the purely
  decorative icons `.accessibilityHidden(true)` and adding
  `.accessibilityAddTraits(.isSelected)` to each `Button` when checked/selected.
  **Caveat, stated plainly:** this environment has `xcodebuild`/`xcrun` present but
  `xcrun simctl list devices` shows zero configured simulator devices, so this was
  resolved via documented Apple accessibility-API behavior (merge-by-default
  `Button`, `accessibilityHidden`, `.isSelected` trait are all real, standard
  SwiftUI APIs, not invented), not a live VoiceOver run — genuinely still open if
  "verified live" is the bar, closed if "correct per documented platform
  behavior" is.

**Build-verification status corrected (2026-07-11):** this was written when "no
macOS/Xcode toolchain in this environment" was believed true. It wasn't —
`ARCHITECTURE.md` §3's major-correction note has the full account. A real
`xcodebuild ... BUILD SUCCEEDED` now exists covering every Swift file this
document's fixes touched (`ItundaAppScreen.kt`'s Android side was already
build-verified throughout). As of 2026-08-07 this environment's `xcrun simctl
list devices` shows zero configured simulator devices, so a live VoiceOver/
XCUITest run isn't currently possible here — the `AgreementWidget`/
`PaymentMethodWidget` item above was resolved via documented platform behavior
instead (see its own note for exactly what that does and doesn't cover).

**Re-audited against current Android code (item 242, 2026-08-07):** this section's
original sweep only covered the 2026-07-11 `ItundaAppScreen.kt`, which has since
been split across `:app` and 8 feature modules with a much larger surface. A fresh
repo-wide grep of every `contentDescription = null` (43 occurrences, 15 files)
found 42 correctly decorative (each paired with adjacent visible `Text()` in the
same clickable container/row, matching this section's own established triage
rule) and one genuine bug: `SuperAppTabs.kt`'s Hood-tab neighborhood-switcher
location-pin icon had its own independent `.clickable`, separate from the
neighborhood-name `Text()`'s own separate `.clickable` right next to it — making
the icon a distinct, unlabeled clickable accessibility node (TalkBack would
announce a bare "Button") and leaving a real tap dead-zone between icon and text.
Fixed by wrapping icon+text in one shared inner `Row` with a single `.clickable`,
scoped narrowly to just those two (the outer `Row` also holds the unrelated
Search/Notifications/Menu icons as later siblings, so the fix couldn't just move
onto the whole outer row).

## 3. Touch target size

Platform minimums: Android/Material Design recommends 48dp; Apple HIG requires
44pt; WCAG 2.1's own baseline (2.5.5, AAA) is 44×44 CSS px.

- **iOS**: `IDS.Layout.topBarActionSize = 44` (points) — meets Apple's 44pt HIG
  minimum exactly. Pass.
- **Android**: `TopIconButton`'s tap area — **fixed (2026-07-11):** bumped from
  `.size(44.dp)` (met the WCAG 44px baseline but sat below Material's own 48dp
  recommendation) to `.size(48.dp)`. Checked all 3 call sites' surrounding `Row`
  layout first (`ShopTopBar`'s `weight(1f)` search box, `AllTopBar`'s
  `SpaceBetween`) — each absorbs the extra 4dp per button without overflow risk, so
  this was safe to change directly rather than leaving as an open item. Verified:
  `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`.
- **Android — `ShopTopBar()` / `PayTopBar()`**: the `Icon(...)` composables fixed in
  §2 (Profile, Cart, Scan QR, Language) are **not currently wrapped in a
  `clickable` modifier or a sized `Box`** the way `TopIconButton` is — they have no
  `onClick` at all yet (this screen is UI-only in these spots; see
  `ARCHITECTURE.md` for what's wired vs. not). Flagging now so that whoever wires
  real navigation to these icons does it inside a `≥48dp` clickable target
  (matching `TopIconButton`'s pattern) rather than leaving the bare ~20dp icon
  itself as the tap target.

## 4. Form labels (2026-07-11)

Grep-audited every text-input composable across `android/` (`grep -rl
"TextField\|EditText"`) — there is exactly one form field in the entire app:
`RecipientEntryScreen`'s account-number `BasicTextField` in
`android/features/payments/impl/.../TransferFlow.kt`. Every other screen is
read-only (lists, cards, amounts) or uses the custom `NumericKeypad` composable
(digit buttons, not a text field).

Found and fixed 2 real bugs:
- The account-number `BasicTextField` had no accessible label at all.
  Unlike a View-based `TextInputLayout`, `BasicTextField` doesn't
  auto-associate the visible `"Enter account number"` `Text()` sitting above
  it — Compose doesn't merge separate sibling composables into one
  accessible node unless told to — so TalkBack announced a bare, unlabeled
  edit field. Fixed with `Modifier.semantics { contentDescription = "Account
  number, up to 16 digits" }`.
- `NumericKeypad`'s digit keys (`0`–`9`, `00`) are fine as-is — their visible
  text already is their accessible name. The `DEL` key uses only a `"⌫"`
  glyph as its visible content, which isn't a meaningful accessible name on
  its own — fixed by adding `contentDescription = "Delete"` to that key only.

Verified: `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`.

iOS has no equivalent audit yet — `AgreementWidget.swift`/
`PaymentMethodWidget.swift` (§2) are the closest thing to form inputs and were
already checked there; no `TextField`/`SecureField` usage exists elsewhere in
`ios/` today.

## 5. Dynamic Type / font scaling (2026-07-11)

Unlike focus order (needs a live TalkBack/VoiceOver run), this is fully checkable
statically — grepped every font-size declaration on both platforms.

**Android:** every `fontSize = N.sp` usage in `ItundaAppScreen.kt` and
`TransferFlow.kt` (the two files with real text) already uses `.sp`, the scalable
unit Compose uses to auto-scale with the system font-size accessibility setting —
zero uses of `.dp` for text size (a real bug on Android, since `.dp` never scales),
and no `LocalDensity`/`fontScale` override anywhere that would disable scaling.
Android was already correct; nothing to fix.

**iOS:** genuinely broken, and fixed. `IDS.Typography` (`Core/DesignSystem/Sources/
IDS.swift`) and `TdsTypography` (`.../Theme/TdsTheme.swift`) — the app's only two
typography token sets — used `Font.system(size:weight:)` throughout, a fixed point
size that does not respond to iOS Settings → Accessibility → Display & Text Size →
Larger Text at all, unlike semantic styles (`.title`, `.body`). A further sweep
found 5 more raw `Font.system(size:weight:)` calls directly in `BankView.swift`
(on `Image(systemName:)` SF Symbols, not just `Text()`), bypassing the token
system entirely. **14 typography constants + 5 inline icon fonts, 19 total, all
fixed:**
- Added `IDS.scaledFont(size:weight:relativeTo:)`, wrapping
  `UIFontMetrics(forTextStyle:).scaledFont(for:)` — Apple's documented pattern for
  "keep this exact point size at the default content size category, but still
  scale with Dynamic Type," the right fix when a design calls for a specific size
  that doesn't map onto a built-in text style.
- `IDS.Typography` and `TdsTypography` both now build every constant through this
  one shared helper (`TdsTypography` reuses `IDS.scaledFont` directly — same
  module, no duplicate implementation).
- `BankView.swift`'s 5 inline icon fonts converted to `IDS.scaledFont` calls too
  (it already imports `CoreDesignSystem`).
- `ContentView.swift` (the app's real `@main`-reachable entry point) had 8 of its
  own, separate raw `Font.system(size:weight:)` calls. At the time this was fixed,
  `ContentView.swift` wasn't wired to `CoreDesignSystem` yet, so a small local
  `scaledFont` helper (same implementation, self-contained) was added instead of a
  new `Project.swift` dependency edge just for this. **Superseded, same day:** the
  Benefits/Shop/All tab rebuild (`ARCHITECTURE.md` §3) added that
  `CoreDesignSystem` dependency anyway for unrelated reasons — `ContentView.swift`'s
  local `scaledFont` copy is now redundant with `IDS.scaledFont` but harmless,
  left as-is rather than churned for a pure dedup.
- No call sites needed to change beyond the font declarations themselves — every
  existing `IDS.Typography.header`/`TdsTypography.title1`/etc. reference keeps
  working exactly as before, it just scales now.

**Runtime-verified, not just build-verified (2026-07-11):** written when "no
Xcode/simulator in this environment" was believed true — it wasn't
(`ARCHITECTURE.md` §3's major-correction note). Went further than just
confirming the build: `xcrun simctl ui <device> content_size
accessibility-extra-large` followed by a full app relaunch (the running app
doesn't pick up a new content-size category until relaunched — confirmed by
screenshotting *before* relaunch too, where nothing had changed) then a fresh
screenshot shows the Home tab's text and icons visibly, substantially larger —
"Good morning" now wraps to two lines, the balance figure runs off-screen, the
notification/profile icon circles grew — a real, live, before/after comparison,
not an inference from reading the code. `IDS.scaledFont` genuinely works at
runtime.

## 6. Focus order (both platforms: real, live-verified)

**iOS — real XCUITest, 2026-07-11.** This was written as "the one item in this
whole audit that has no static-analysis path... requires a live TalkBack/
VoiceOver run... neither available in this environment." That stopped being
true once this session found the real Xcode/simulator toolchain
(`ARCHITECTURE.md` §3's "MAJOR CORRECTION" note) — XCUITest reads the exact
same accessibility tree VoiceOver does, so a real UI test against it is a real
live check, not a proxy for one. New `App/UITests/FocusOrderTests.swift`
(`ItundaAppUITests` target, added to `Project.swift`):
- `testTabBarFocusOrderMatchesVisualLeftToRightOrder` — asserts the tab bar's
  accessibility-tree order is exactly `["Home", "Benefits", "Shop", "Pay",
  "All"]` (Android's taxonomy, in order) *and* that each button's
  accessibility position is left-to-right on screen, so a VoiceOver swipe-right
  traversal can't silently diverge from what's visible.
- `testHomeTabTopBarIconsFocusOrderMatchesVisualOrder` — asserts the
  Notifications (bell) button's accessibility position sits left of Profile,
  matching `HomeTopBar`'s real `HStack` order (both were the icon-only buttons
  fixed for missing labels earlier this session).
- `testBenefitsTabRowsFocusOrderMatchesVisualTopToBottomOrder`,
  `testShopTabTopBarIconsFocusOrderMatchesVisualOrder`,
  `testPayTabMerchantRowsFocusOrderMatchesVisualTopToBottomOrder`,
  `testAllTabTopBarFocusOrderMatchesVisualOrder` — added same day, extending
  coverage from Home-only to all 5 tabs (each taps the real tab bar first, the
  same path a VoiceOver user takes, not a navigation shortcut). Found one real
  thing worth noting along the way, not a bug: `ShopTopBar`'s Profile/Cart and
  `TdsAllTopBar`'s Settings icon are bare `Image()`s with an
  `accessibilityLabel`, not wrapped in `Button` — the first version of these
  tests queried `app.buttons[...]` and genuinely failed to find them; querying
  `app.images[...]` (the correct element type, confirmed by XCUITest itself,
  not assumed) passed. Matches the already-documented finding that these
  specific icons have no clickable wrapper/real navigation yet.
- Run via `xcodebuild -workspace Itunda.xcworkspace -scheme ItundaApp
  -destination 'platform=iOS Simulator,name=iPhone 14' test` →
  **`TEST SUCCEEDED`**, 6/6 tests passed, 0 failed. Full 5-tab coverage.

**Android — also real, live-verified, same pass.** A real "andros" AVD emulator
(API 36 / Android 16) was already available in this environment
(`$ANDROID_HOME/emulator/emulator -avd andros`). New `androidTest` source set
(this repo's first — `android/app/src/androidTest/java/rw/itunda/app/ui/
FocusOrderTest.kt`) using `androidx.compose.ui.test`, which reads the same
semantics tree TalkBack does:
- `tabBarFocusOrderMatchesVisualLeftToRightOrder` — asserts `TossBottomBar`'s
  five tab labels are in left-to-right `x`-position order.
- `homeTopBarIconsFocusOrderMatchesVisualOrder` — asserts "Scan QR code" sits
  left of "Notifications", matching `HomeTopBar`'s real `Row` order.
- `benefitsTabRowsFocusOrderMatchesVisualTopToBottomOrder`,
  `shopTabTopBarIconsFocusOrderMatchesVisualOrder`,
  `payTabTopBarIconsFocusOrderMatchesVisualOrder`,
  `allTabTopBarFocusOrderMatchesVisualOrder` — same extension to all 5 tabs as
  iOS, each tapping the real tab bar first. Unlike iOS, no element-type
  surprise here — Android's `Icon(contentDescription = ...)` composables are
  directly queryable via `onNodeWithContentDescription` regardless of whether
  they're wrapped in `.clickable`, so all 6 passed on the first real run once
  the two environment bugs below were fixed.

Getting this running found and fixed two real, independent environment bugs
along the way, neither hypothetical:
1. `android/app/build.gradle.kts` never set `testInstrumentationRunner` —
   `pm list instrumentation` on the real device confirmed it had silently
   defaulted to the ancient, pre-AndroidX `android.test.InstrumentationTestRunner`,
   which only understands legacy JUnit3 `TestCase` subclasses. Every `@Test`-
   annotated class in this repo (Android or not) would have silently reported
   "No tests found" rather than running — invisible until this session's first
   `androidTest` source set actually tried to run one. Fixed by setting
   `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`.
2. `espresso-core:3.5.1`'s `InputManagerEventInjectionStrategy` reflectively
   calls `android.hardware.input.InputManager.getInstance()`, a hidden API
   removed/renamed on this emulator's API 36 platform — a live
   `NoSuchMethodException`, not a guess. Bumped to `espresso-core:3.7.0`, which
   targets newer platforms correctly.

`adb shell am instrument -w -e class rw.itunda.app.ui.FocusOrderTest
rw.itunda.app.test/androidx.test.runner.AndroidJUnitRunner` → **`OK (6 tests)`**,
run live against the real emulator. Full 5-tab coverage, matching iOS.

Both platforms now have complete, real, live focus-order verification across
every tab — the pattern (tap the real tab bar, assert accessibility-tree
position matches visual position) is proven and directly reusable for any
future screen.

## 7. Web micro-frontends (item 244, 2026-08-07 — first audit, previously zero coverage)

Every section above (1-6) covered only Android and iOS. `services/micro-frontends/`
(bank-mfe, merchant-mfe, ops-mfe, kyc-mfe, pay-checkout, host-app) — real, live,
user-facing web surfaces — had never been accessibility-audited at all until this
pass. Two sweeps, both real bugs found and fixed, not a clean-bill-of-health report:

**Form labels.** `<label>` without `htmlFor`/`id` isn't automatically a bug — the
dominant, correct pattern across this codebase (`LoginPage.tsx`, `RegisterPage.tsx`,
most `merchant-mfe/screens/*.tsx`) wraps the `<input>` as the label's own descendant,
which needs no `htmlFor` at all. Found one real exception: `PlaceSearchInput`
(`bank-mfe/BankDashboard.tsx`, used 5× for ride/rental pickup+dropoff fields) had its
`<label>` as a plain *sibling* of `<input>`, with no association of either kind —
neither click-to-focus nor a screen reader's field name worked. Fixed with
`useId()` wiring `htmlFor`/`id`. Found one related, different-class bug in the same
pass: a bill-split "Ladder game" toggle was a bare `<label onClick=...>` with no
associated control at all — not in the tab order, not activatable via Enter/Space.
Changed to a real `<button type="button">`. A follow-up check of `ops-mfe`'s own
`<label>`-without-`htmlFor` hits confirmed those match the same correct wrapping
pattern — no bug. `kyc-mfe`, `pay-checkout`, and `host-app` have no `<label>` at
all; checking why surfaced a third, more consequential real bug: `kyc-mfe`'s real
KYC identity-document submission form (`KycDashboard.tsx`) had its document-number
and document-reference fields relying entirely on `placeholder` text for their
name — not a substitute for a real label (WCAG 3.3.2), and it disappears the
moment anyone starts typing, for every user, not just screen-reader users. This is
itunda's real identity-verification submission flow, not a low-stakes form. Fixed
with `aria-label` (this minimalist single-column wizard has no room shown in its
CSS for a persistent visible caption per field, so `aria-label` is the fix that
doesn't require a visual redesign).

**Icon-only interactive elements without an accessible name** — the same category
as Android/iOS §2 above, applied to web for the first time. Repo-wide sweep of
every `<button>` (707 across `services/micro-frontends/*/src/`, only 37
pre-existing `aria-label` uses) found 10 real violations, all fixed with a
descriptive `aria-label` (using real per-item data for the label wherever
available, e.g. `` `Decrease quantity of ${line.product.name}` ``, rather than a
generic string): `BankDashboard.tsx`'s emoticon-store sticker picker, both 1:1/
group chat Send buttons, both cancel-reply "×" buttons, both modal-close "×"
buttons; `MapView.tsx`'s bookmark-folder color-swatch picker (also missing
`type="button"` — a real bug risk inside a form, and now carries `aria-pressed`
for the selected swatch); `merchant-mfe/PosScreen.tsx`'s cart quantity +/-
buttons. `ops-mfe`, `kyc-mfe`, `pay-checkout`, and `host-app` had no genuine
icon-only violations in this pass.

**Touch target size** — WCAG 2.5.8 Target Size (Minimum), a 2.2-era AA-level
criterion (not 2.5.5's AAA-only 44×44) requiring 24×24 CSS pixels unless an
exception applies. First pass fixed 6 violations among the icon-only buttons
already in hand from the label sweep. A full follow-up sweep of all 707 buttons
across every micro-frontend found 16 more, all icon-only with zero/near-zero
padding: 14 in `BankDashboard.tsx` — 11 of them one exact copy-pasted style
object (`{ display: 'flex', color: 'var(--toss-grey-700)' }`) reused across every
screen's "Back" navigation button plus a group-chat header's 3 icons (fixed in
one pass since the style string was byte-identical everywhere), plus the
stock-detail back/watchlist-star toggle, main dashboard sign-out, and the chat
emoji-reaction-picker trigger; 2 more in `merchant-mfe/PosScreen.tsx`'s
product-option-group and price-tier row editors. All bumped to a real ≥24×24px
clickable area via padding. Deliberately left alone: the two chat "Cancel reply"
"×" buttons, which sit inline within a flowing text sentence ("Replying to: ...
×") — WCAG 2.5.8's own "inline" exception covers targets constrained by
surrounding text's line-height, and forcing padding there would have broken the
sentence's visual flow to satisfy a rule that doesn't apply. `ops-mfe`,
`kyc-mfe`, `pay-checkout`, and `host-app` confirmed to have zero touch-target
violations — this category is now a genuine full sweep, not a partial one.

**Keyboard-inoperable click targets** — the same bug class as the "Ladder game"
toggle above (a plain `<label onClick=...>`/`<div onClick=...>` with no
`role="button"`/`tabIndex`, unreachable via keyboard or screen reader), swept
across every micro-frontend. Found 3 more real instances, all in
`BankDashboard.tsx`: the notification-list row (mark as read), the
stock-watchlist row (open stock detail), and a community post card (open post —
which also has a *different*, already-keyboard-accessible nested "Remove"
button with its own `stopPropagation`, so only the card's own primary action was
unreachable). All 3 fixed with `role="button"`, `tabIndex={0}`, and an
`onKeyDown` handling Enter/Space (native click doesn't fire from keyboard Enter
on a `<div>` even once it's focusable). Modal backdrops' click-outside-to-close
and `stopPropagation` guards were correctly left alone — neither is a
user-facing action needing its own keyboard affordance.

Verified: `bank-mfe`, `merchant-mfe`, and `ops-mfe` all `tsc -b` + `vite build`
clean on every change. **Not yet audited on web:** color contrast (the
token-level fixes in §1 above apply automatically since every micro-frontend
imports the same `packages/design-tokens/tokens.css`, but no independent
web-specific contrast pass has been run), Dynamic Type/OS text-zoom equivalent,
and focus *order* specifically (this pass fixed keyboard *reachability* for
previously-unreachable elements, a different, narrower claim than verifying the
resulting tab sequence actually matches visual layout the way §6's real
XCUITest/Espresso tests did for Android/iOS) — these three categories remain
genuinely open on web, not verified-clean.

## Status

Corresponds to the `docs/TOSS_RWANDA_ALIGNMENT.md` gap-list item "Add accessibility
checks for touch targets, contrast, form labels, and focus" — contrast and
content-description/label checks are done on Android/iOS (the 2 color-contrast
defects documented in §1 are now fixed, item 240/241; all content-description/label
bugs found were fixed), touch-target sizing is checked and the one real gap found
(Android's `TopIconButton`) is fixed, form labels are audited and both bugs found
are fixed, Dynamic Type/font scaling is audited and all 19 real bugs found (all on
iOS) are fixed, focus order is now real, live-verified on both platforms across
all 5 tabs (XCUITest on iOS — 6/6 passing; `androidx.compose.ui.test` on
Android — 6/6 passing). Every item in the original gap list now has real,
non-speculative, full-coverage verification behind it on Android and iOS. Web
micro-frontends got their first-ever pass in §7 (form labels + icon-only buttons,
10+2 real bugs found and fixed) — contrast/touch-target/Dynamic-Type/focus-order
on web remain open, unaudited categories, not verified-clean ones.
