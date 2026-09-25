# IDS 3.0 Cross-Platform Contract

This document is the platform parity gate for the shared Itunda design system.

## Canonical semantic roles

| Role | Web | Android | iOS |
|---|---|---|---|
| Brand | `--itunda-brand` | `Ids.colors.brand` | `IDS.Colors.brand` |
| Primary text | `--itunda-text-primary` | `Ids.colors.textPrimary` | `IDS.Colors.textPrimary` |
| Secondary text | `--itunda-text-secondary` | `Ids.colors.textSecondary` | `IDS.Colors.textSecondary` |
| Tertiary text | `--itunda-text-tertiary` | `Ids.colors.textTertiary` | `IDS.Colors.textTertiary` |
| Surface | `--itunda-surface-default` | `Ids.colors.surface` | `IDS.Colors.backgroundSecondary` |
| Divider | `--itunda-border-default` | `Ids.colors.divider` | `IDS.Colors.divider` |
| Success | semantic success token | `Ids.colors.success` | `IDS.Colors.success` |
| Error | `--itunda-field-border-error` / semantic error | `Ids.colors.danger` | `IDS.Colors.danger` |

## Primitive parity

Every public primitive must document:

1. Anatomy
2. Variants and sizes
3. Default, pressed, focus, disabled, loading and validation states where applicable
4. 44px / 44dp / 44pt minimum interaction target
5. Large-text behavior
6. Long-content and localization behavior
7. Screen-reader / TalkBack / VoiceOver semantics
8. Reduced-motion behavior
9. Light/dark semantic mapping
10. Platform-specific rendering differences

## Empty state mapping

| Semantic role | Web | Android | iOS |
|---|---|---|---|
| Empty state | `EmptyState` | `IdsEmptyState` | `IdsEmptyState` |

The three implementations share the same semantic contract: meaningful title, optional supporting message, optional action, localization-safe wrapping, and accessible action semantics. Platform-native layout and interaction details may differ.

## Platform rule

The contract is shared; the implementation is native.

Web uses semantic CSS tokens and browser accessibility semantics. Android uses Compose-native semantics and platform interaction behavior. iOS uses SwiftUI/UIKit semantics and Dynamic Type. Visual differences are allowed only when required by platform conventions or accessibility.

## Promotion gate

A component is not considered IDS-complete until all three platform mappings are documented. A missing implementation is an explicit gap, not a silent fallback.

## Source of truth

Primitive values live in `packages/design-tokens/tokens.json`. Semantic roles are mapped per platform without changing their meaning. Product code should never create a second brand palette.
