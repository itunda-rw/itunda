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
- **Android**: `TopIconButton`'s tap area is `.size(44.dp)` — meets the WCAG 44px
  baseline but is **below** Material Design's own 48dp recommendation. Not changed
  in this pass (a size bump is a visual, not just accessibility, change to a
  component used across every top bar — left as an open, scoped item rather than
  changed unreviewed).
- **Android — `ShopTopBar()` / `PayTopBar()`**: the `Icon(...)` composables fixed in
  §2 (Profile, Cart, Scan QR, Language) are **not currently wrapped in a
  `clickable` modifier or a sized `Box`** the way `TopIconButton` is — they have no
  `onClick` at all yet (this screen is UI-only in these spots; see
  `ARCHITECTURE.md` for what's wired vs. not). Flagging now so that whoever wires
  real navigation to these icons does it inside a `≥48dp` clickable target
  (matching `TopIconButton`'s pattern) rather than leaving the bare ~20dp icon
  itself as the tap target.

## 4. Not yet audited (open)

- **Focus order** (Compose semantics traversal order / SwiftUI focus order) — not
  started this pass. Requires either a live TalkBack/VoiceOver run or Compose's
  `testTag`-based semantics tree inspection, neither available in this
  environment.
- **Form labels** — bill-pay/transfer/registration form fields (`TextField`s
  across the app) not yet audited for `label`/`placeholder`-only fields lacking a
  real accessible label.
- **Dynamic Type / font scaling** — not checked on either platform.

## Status

Corresponds to the `docs/TOSS_RWANDA_ALIGNMENT.md` gap-list item "Add accessibility
checks for touch targets, contrast, form labels, and focus" — contrast and
content-description/label checks are done (with 2 real defects documented above,
left open pending a design-system-level fix), touch-target sizing is checked and
one real gap flagged, form labels and focus order remain open.
