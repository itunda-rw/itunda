# IDS iOS Accessibility

## Semantics

Use SwiftUI accessibility modifiers and native controls. Expose an accessible label, value, hint, and traits when they add meaning.

## VoiceOver

Group composite controls when the child elements should be perceived as one interaction. Hide decorative elements from the accessibility tree.

## Dynamic Type

All text-bearing components must tolerate Dynamic Type. Prefer flexible layouts and wrapping over clipping or fixed text heights.

## Touch

Interactive targets must be at least 44pt.

## Motion

Respect Reduce Motion and remove non-essential movement while preserving state changes and task completion feedback.