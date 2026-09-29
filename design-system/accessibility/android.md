# IDS Android Accessibility

## Semantics

Use Compose semantics and native controls where possible. Expose the control's role, state, enabled/disabled status, selected state, and value when applicable.

## Touch

Interactive targets must be at least 44dp. Visual affordances may be smaller only when the touch target remains accessible.

## TalkBack

Provide concise content descriptions for controls that lack visible text. Do not announce decorative icons. Composite components should expose one coherent interaction model rather than leaking internal implementation details.

## Text and layout

Support font scaling and long localized content. Avoid fixed-height containers around text unless overflow behavior is explicitly defined.

## Motion

Respect platform animation/reduced-motion preferences and avoid essential information being conveyed only through animation.