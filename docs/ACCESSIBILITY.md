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
- `AgreementWidget.swift` / `PaymentMethodWidget.swift` — icons inside `Button`s
  that also contain `Text()` (terms checkbox, payment method rows). Not a hard
  violation (each button has a text label), but not deeply verified for
  SwiftUI's per-child accessibility-element merging — flagged as a follow-up, not
  fixed. Could now actually be checked with the real simulator `ARCHITECTURE.md`
  §3's major-correction note describes (VoiceOver can run in the iOS Simulator),
  but wasn't re-visited in that pass — left open, no longer for lack of a
  toolchain, just not yet done.

**Build-verification status corrected (2026-07-11):** this was written when "no
macOS/Xcode toolchain in this environment" was believed true. It wasn't —
`ARCHITECTURE.md` §3's major-correction note has the full account. A real
`xcodebuild ... BUILD SUCCEEDED` now exists covering every Swift file this
document's fixes touched (`ItundaAppScreen.kt`'s Android side was already
build-verified throughout). The `AgreementWidget`/`PaymentMethodWidget` item above
is the one real accessibility question this document leaves open that the
now-available simulator could resolve but hasn't yet.

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

## Status

Corresponds to the `docs/TOSS_RWANDA_ALIGNMENT.md` gap-list item "Add accessibility
checks for touch targets, contrast, form labels, and focus" — contrast and
content-description/label checks are done (2 real color-contrast defects documented
above, left open pending a design-system-level fix; all content-description/label
bugs found were fixed), touch-target sizing is checked and the one real gap found
(Android's `TopIconButton`) is fixed, form labels are audited and both bugs found
are fixed, Dynamic Type/font scaling is audited and all 19 real bugs found (all on
iOS) are fixed, focus order is now real, live-verified on both platforms across
all 5 tabs (XCUITest on iOS — 6/6 passing; `androidx.compose.ui.test` on
Android — 6/6 passing). Every item in the original gap list now has real,
non-speculative, full-coverage verification behind it on both platforms.
