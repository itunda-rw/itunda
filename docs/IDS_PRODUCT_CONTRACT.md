# Itunda Design System Product Contract

## Rule

Every IDS refinement is a product-wide refinement. A change is not considered complete when it only appears in the design showcase.

## Propagation order

1. Canonical tokens and semantic roles in `packages/design-tokens/`.
2. Android `android/core/designsystem` and Android product surfaces.
3. iOS `ios/Core/DesignSystem` and iOS product surfaces.
4. Shared native/React Native surfaces under `packages/saronite/`.
5. Consumer web, Business, Developers, KYC, Ops, and other web products.
6. Design showcase and documentation.

## Product behavior contract

Every product should inherit the same principles:

- One core message and one obvious next step.
- Context before controls.
- Clear primary/secondary/tertiary action hierarchy.
- Complete component states: default, pressed, focused, disabled, loading, success, error, empty, and recovery where applicable.
- Semantic tokens instead of product-local color literals.
- Native platform behavior on Android and iOS; responsive web behavior on web.
- Accessibility is part of the component contract: screen readers, focus order, large text, contrast, touch targets, reduced motion, and localization expansion.
- Errors explain the problem and expose the next useful recovery action.
- Itunda Indigo remains the brand identity; reference products inform principles and craft, not copied assets or proprietary implementation.

## Current propagation pass

The September 2026 pass strengthens the shared React Native semantic color contract and migrates the Saronite mini-app surfaces and web product shells to semantic IDS roles. Future IDS work should continue from this contract rather than introducing another product-local visual vocabulary.
