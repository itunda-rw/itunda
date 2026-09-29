# Itunda Design System — Source Reconciliation

IDS v2 is the unified documentation and semantic layer for the design implementations that already exist in the Itunda repository. This document records the existing sources so the extraction does not accidentally replace shipped platform work with a web-only abstraction.

## Existing sources audited

### Android

- `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/theme/IdsColors.kt`
  - primitive grey/blue/red scales
  - compatibility aliases
- `.../theme/IdsSemanticColors.kt`
  - light/dark semantic roles
  - brand, text, surfaces, dividers, status colors
- `.../theme/IdsLayout.kt`
  - screen/section/card/row spacing
  - shape radii
  - card elevation
  - minimum touch target
- `.../theme/IdsTypography.kt`
  - raw typography scale
  - title/body/button roles
  - large money amount
- `.../theme/IdsTheme.kt`
  - Material theme bridge
  - light/dark theme selection
- `android/core/designsystem/src/main/java/rw/itunda/core/designsystem/components/IdsButton.kt`
- `.../components/IdsListRow.kt`
- `.../sdui/SduiRenderer.kt`
  - server-driven component rendering

### iOS

- `ios/Core/DesignSystem/Sources/IDS.swift`
  - semantic colors
  - Dynamic Type support
  - motion
  - icons/layout utilities
- `ios/Core/DesignSystem/Sources/Theme/IdsTheme.swift`
  - primitive palette
  - typography scale
- `ios/Core/DesignSystem/Sources/Components/Components.swift`
  - `IdsButton`
  - `IdsTextField`
  - `IdsListRow`
  - `EmptyStateView`
  - `ErrorCardView`
  - `FixedBottomCTA`
  - press interaction style
  - additional shared components
- `ios/Core/SDUI/Sources/SduiModels.swift`
  - shared server-driven UI model

### Web

- `client/design-system/tokens.css`
  - IDS v2 semantic web tokens
- `client/design-system/tokens.json`
  - portable IDS v2 token source
- `client/design-system/components.css`
  - web component primitives
- `packages/design-tokens/tokens.json`
  - existing repository primitive color source used by the historical Android/iOS/web code-generation pipeline
- `packages/design-tokens/tokens.css`
  - existing cross-platform web token layer, including typography, motion and responsive dark-mode tokens

### Product-level designs

The design system also includes patterns already expressed in real product screens. These are not to be discarded during extraction:

- Android: `ItundaAppScreen.kt`, payment/transfer flows, identity, settings, maps, merchant/rider surfaces and SDUI renderer.
- iOS: `BankView.swift`, transfer/payment flows, Benefits/Shop/All surfaces, identity/security and shared components.
- Web: existing client/business product surfaces consuming the repository token layer.

## Reconciliation rules

1. **Existing shipped design work is source material.** IDS v2 documents and normalizes it; it does not replace it blindly.
2. **Primitive and semantic tokens remain separate.** Raw palettes can be retained for compatibility, while product code should consume semantic roles.
3. **Itunda brand identity is authoritative.** The current semantic brand anchor is Itunda indigo `#7472F4`.
4. **Platform implementations may differ where the platform requires it.** Compose, SwiftUI and Web should share intent and semantics without forcing identical layouts.
5. **Legacy Toss-named compatibility code is migration material, not the IDS public vocabulary.** New code should use `Ids*` / `--itunda-*` / IDS semantic roles.
6. **Product patterns are first-class design-system material.** Money movement, identity, payments, marketplace, chat, business and security flows must be documented alongside atomic components.
7. **No design claim is considered complete until its real implementation is mapped.** The catalog should point back to the implementation rather than describing an imaginary component.

## Target architecture

```
Existing Itunda implementations
        ↓
Primitive tokens
        ↓
Semantic tokens
        ↓
Component contracts
        ↓
Product patterns
        ↓
Web · Android · iOS
        ↓
Open-source Itunda Design System
```

## Current extraction status

- [x] Existing Android foundation layer audited
- [x] Existing iOS foundation layer audited
- [x] Existing Web foundation layer audited
- [x] Existing component implementations identified
- [x] Existing SDUI design-system bridge identified
- [x] Product-level design sources identified
- [x] IDS v2 component catalog added
- [x] IDS v2 product-pattern catalog added
- [ ] Complete one-to-one token migration of every product call site
- [ ] Extract platform implementations into the standalone open-source IDS package
- [ ] Add automated cross-platform token parity checks
- [ ] Add visual regression fixtures for Web/Android/iOS
