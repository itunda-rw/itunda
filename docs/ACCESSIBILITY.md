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
| **textTertiary `#8b95a1` on background `#f2f4f6`** | **2.76:1** | **FAIL** | **FAIL** |
| textPrimary `#191f28` on card `#ffffff` | 16.56:1 | PASS | PASS |
| textSecondary `#4e5968` on card `#ffffff` | 7.11:1 | PASS | PASS |
| textTertiary `#8b95a1` on card `#ffffff` | 3.04:1 | FAIL | PASS (marginal) |
| brand blue `#3182f6` on white | 3.71:1 | FAIL | PASS |
| **success green `#04c065` on white** | **2.40:1** | **FAIL** | **FAIL** |
| danger red `#f04452` on white | 3.71:1 | FAIL | PASS |
| white text on brand blue button | 3.71:1 | FAIL | PASS |

### Dark theme

| Pair | Ratio | AA normal (4.5) | AA large/UI (3.0) |
|---|---|---|---|
| textPrimary `#ffffff` on background `#000000` | 21.00:1 | PASS | PASS |
| textSecondary `#989eaa` on background `#000000` | 7.81:1 | PASS | PASS |
| textTertiary `#575c66` on background `#000000` | 3.13:1 | FAIL | PASS |
| textPrimary `#ffffff` on surface `#17181d` | 17.72:1 | PASS | PASS |
| textSecondary `#989eaa` on surface `#17181d` | 6.59:1 | PASS | PASS |
| **textTertiary `#575c66` on surface `#17181d`** | **2.64:1** | **FAIL** | **FAIL** |
| brand blue `#4c8fff` on surface | 5.64:1 | PASS | PASS |
| success green `#20d394` on surface | 9.12:1 | PASS | PASS |
| danger red `#ff6b7a` on surface | 6.44:1 | PASS | PASS |

### Findings

1. **`textTertiary` fails AA-normal-text contrast in both themes**, and fails even the
   lenient large-text/UI threshold on the true-black dark background and on the
   grey-100 light background (2.76:1 / 2.64:1, both below 3.0:1). It only clears
   large-text/UI on a white card (3.04:1, and only marginally). `textTertiary` is
   currently used for de-emphasized captions/timestamps — real WCAG failure risk
   wherever it sits directly on `background` rather than a `card`/`surface`.
2. **`success` (green) on white fails contrast at every threshold in light mode**
   (2.40:1, below even 3.0:1). The dark-mode green (`#20d394`, chosen brighter for
   exactly this reason) passes everywhere. This is a real, previously undocumented
   light-mode-only defect — any light-mode "amount increased" / "payment received"
   text or icon rendered directly in green-on-white is likely under WCAG minimums.
3. **Brand blue, danger red, and white-on-blue-button all fail AA-normal-text (4.5:1)
   but pass AA-large/UI (3.0:1) in light mode.** This is fine for buttons and icons
   (UI-component threshold applies) but means these colors must not be used for
   small/normal-weight body text in light mode — only for button labels (which are
   typically bold/large enough) or icons/borders.
4. Not yet fixed: doing so requires either brightening `--toss-grey-500` (has a
   downstream ripple through every consumer of `TdsSemanticColors.textTertiary` /
   `IDS.Colors.textTertiary` / `--toss-grey-500` across Android/iOS/web, so it's a
   deliberate design-system decision, not a one-line patch) or restricting where
   `textTertiary`/light-mode `success` are allowed to render. Left open rather than
   patched blind.

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
  fixed, since it could not be confirmed without a real device/simulator run
  (no macOS/Xcode available in this environment, per `ARCHITECTURE.md` §3).

Not build-verified — no Docker/macOS/Xcode toolchain in this environment to run a real
`swift build` or simulator, same caveat as every other iOS change this session.

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
- `ContentView.swift` (the app's real `@main`-reachable entry point, still not
  wired to `CoreDesignSystem` — see `ARCHITECTURE.md` §3) had 8 of its own,
  separate raw `Font.system(size:weight:)` calls, since its Benefits/Shop/All tabs
  don't use the shared design system yet. Rather than adding a new
  `CoreDesignSystem` dependency edge to `Project.swift` just for this, added a
  small local `scaledFont` helper (same implementation, self-contained) and
  converted all 8.
- No call sites needed to change beyond the font declarations themselves — every
  existing `IDS.Typography.header`/`TdsTypography.title1`/etc. reference keeps
  working exactly as before, it just scales now.

Not build-verified — no Xcode/simulator in this environment to confirm the actual
runtime scaling behavior, same caveat as the rest of `ios/`. `UIFontMetrics` is
real, documented UIKit API (not invented), and every file compiles under
`swift -frontend -parse`, but the scaling itself is unverified here.

## 6. Not yet audited (open)

- **Focus order** (Compose semantics traversal order / SwiftUI focus order) — the
  one item in this whole audit that has no static-analysis path: there is no
  explicit focus-order manipulation anywhere in either codebase (grepped for it —
  none found), so the default traversal order applies, but confirming that order
  is actually correct requires a live TalkBack/VoiceOver run or Compose's
  `testTag`-based semantics tree inspection, neither available in this
  environment.

## Status

Corresponds to the `docs/TOSS_RWANDA_ALIGNMENT.md` gap-list item "Add accessibility
checks for touch targets, contrast, form labels, and focus" — contrast and
content-description/label checks are done (2 real color-contrast defects documented
above, left open pending a design-system-level fix; all content-description/label
bugs found were fixed), touch-target sizing is checked and the one real gap found
(Android's `TopIconButton`) is fixed, form labels are audited and both bugs found
are fixed, Dynamic Type/font scaling is audited and all 19 real bugs found (all on
iOS) are fixed. Only focus order remains open, genuinely blocked on live
device/simulator access this environment doesn't have.
