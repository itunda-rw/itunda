# IDS Web Accessibility

## Semantics

Prefer native HTML elements: `button`, `a`, `input`, `select`, `textarea`, and semantic headings/landmarks. Add ARIA only when native semantics cannot express the behavior.

## Focus and keyboard

- Provide a visible focus indicator.
- Do not remove the browser focus outline without replacing it with an equally clear indicator.
- Follow the documented keyboard pattern for composite controls such as Tabs.
- Keep focus inside modal surfaces when required and restore it when they close.

## Forms

Associate labels with controls. Connect supporting, error, and success text programmatically. Validation must not depend on color alone.

## Content

Allow labels and messages to wrap. Do not rely on fixed heights for user-generated or localized text.

## Motion

Use `prefers-reduced-motion` to reduce or remove non-essential transitions.