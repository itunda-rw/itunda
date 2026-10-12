# IDS v2 — Platform Source Map

IDS v2 includes the real design implementations already used by Itunda. The web token layer is **not** a replacement for Android or iOS; it is the shared semantic contract that those implementations map to.

## Canonical platform implementations

| Platform | Foundation | Components | Runtime patterns |
|---|---|---|---|
| Web | `client/design-system/tokens.css`, `tokens.json` | `client/design-system/components.css` | Client + Business surfaces |
| Android | `android/core/designsystem/.../theme/IdsColors.kt`, `IdsSemanticColors.kt`, `IdsLayout.kt`, `IdsTypography.kt`, `IdsTheme.kt` | `android/core/designsystem/.../components/` | Consumer, payments, identity, settings, maps, merchant, SDUI |
| iOS | `ios/Core/DesignSystem/Sources/IDS.swift`, `Theme/IdsTheme.swift` | `ios/Core/DesignSystem/Sources/Components/Components.swift` | Consumer, banking, payments, identity, security, Shop/Benefits/All |
| SDUI | Android `SduiRenderer.kt` + iOS `SduiModels.swift` | Shared server-driven component contracts | Server-driven product composition |
| React Native | `packages/saronite/packages/ids-react-native/` | IDS React Native primitives/tokens | Saronite mini apps |

## What is already shared

### Color semantics
The mobile implementations now use the same semantic intent as IDS v2:

- `brand` → Itunda indigo
- `background`
- `surface`
- `surfaceSoft`
- `textPrimary`
- `textSecondary`
- `textTertiary`
- `divider`
- `success`
- `warning`
- `danger`
- icon roles
- pressed/disabled states

The current Itunda brand semantic anchor is **#7472F4** in light mode and **#7675F8** in dark mode. The older numbered blue primitive scale remains in the repository for compatibility and code generation; it is not the current product brand.

## Typography

Android and iOS already contain production typography layers, including:

- heading/title/body roles
- button typography
- large money/metric typography
- Dynamic Type support on iOS
- platform-native text rendering

IDS v2 Web typography should preserve the same hierarchy rather than forcing identical platform metrics.

## Motion

The mobile systems already contain shared motion primitives:

- standard/ease curves
- exponential and back easing
- spring presets
- press-scale interaction
- reduced-motion compatibility at the product layer

These should map to the IDS v2 Web motion tokens instead of creating a second motion vocabulary.

## Component inventory

Existing real components include:

- Button
- Icon Button
- List Row
- Text Field / form controls
- Fixed Bottom CTA
- Empty State
- Error State
- Skeleton / loading
- Star Rating
- SDUI renderer/model
- press interaction styles
- cards and flat sections
- keyboard-docked CTA
- navigation/tab patterns

The component catalog must point to these implementations. A component is not considered “implemented” merely because a documentation page exists.

## Product-level source material

IDS v2 also incorporates the actual product screens that established the current visual language:

- Android: `ItundaAppScreen.kt`, transfer/payment flows, identity, settings, maps and merchant/rider surfaces.
- iOS: `BankView.swift`, transfer/payment flows, Benefits/Shop/All, identity/security and shared components.
- Web: client/business product surfaces using the repository design-token layer.
- Saronite/React Native: mini-app design primitives and token consumers.

## Reconciliation policy

1. Preserve working platform implementations.
2. Normalize semantics before normalizing geometry.
3. Keep primitive tokens separate from semantic roles.
4. Keep platform-native accessibility and interaction behavior.
5. Keep Itunda's indigo/petal identity; system-quality is the benchmark, not another company's visual identity.
6. Promote repeated product patterns into shared components instead of duplicating screen-local implementations.
7. Add visual regression fixtures before declaring Web/Android/iOS parity complete.

## Quality gates and next parity work

### Automated checks and evidence status

- [x] Semantic token parity for Web, Android and iOS, including light/dark mappings and contrast checks
- [x] Component source coverage and required accessibility/state contract fields
- [x] Static regression guards for the Android and iOS loading-button contracts
- [x] Component CSS token references must resolve to declared `--tds-*` tokens
- [x] Component-token aliases must resolve to declared IDS tokens without cycles
- [ ] Fresh CI run confirming the latest token-integrity guards
- [ ] Runtime component behavior tests on each platform
- [ ] Cross-platform screenshot baselines and visual-diff review
- [ ] Public package release/version compatibility policy

A source-symbol or CSS-token check is a regression guard, not proof of runtime behavior. Do not mark the new token-integrity checks as CI-verified until a fresh workflow run passes. Keep this distinction explicit in PRs and release notes.

### TDS-inspired developer experience (Itunda-native)

Public references reviewed:
- Official Toss `apps-in-toss-harness` documents project scaffolding, browser-based mock SDK/devtools, opt-in debugging, device testing and console/release workflows.
- Official Toss `create-ait-app` demonstrates a reusable project generator and repeatable build/deploy commands.
- The separate `apps-in-toss-community/devtools` repository identifies itself as archived/unmaintained; use it as historical implementation research only, not as a production dependency.

These public repositories describe public tooling, not Toss's complete private production architecture. Itunda should implement equivalent capabilities against its own IDS and Saronite contracts, without depending on Toss packages, branding, or proprietary services:

1. **IDS catalog** — executable examples for every component variant/state, theme and localization.
2. **Local mini-app host** — mock phone/session/permission/navigation APIs with deterministic fixtures and inspectable events.
3. **Diagnostics panel** — component/token inspection, accessibility warnings, API errors and event traces.
4. **Test-on-device bridge** — a clearly separated build/install/debug workflow that never silently publishes or releases.
5. **Release gate** — token parity, unit/behavior tests, visual diffs, API compatibility and migration notes before publishing packages.

## Extraction status

- [x] Android foundation inventory
- [x] iOS foundation inventory
- [x] Web foundation inventory
- [x] React Native/Saronite source identified
- [x] SDUI bridge identified
- [x] Component implementations mapped
- [x] Product-level sources mapped
- [x] IDS v2 web component catalog
- [x] IDS v2 product-pattern catalog
- [ ] Standalone open-source platform packages
- [x] Automated token parity check
- [ ] Cross-platform visual regression suite
