# IDS Accessibility Fundamentals

Accessibility is part of the Itunda component contract, not a final QA pass. Every primitive must preserve semantic meaning, operability, readable content, and understandable state across Web, Android, and iOS.

## Baseline

- Use native platform semantics before custom ARIA/roles.
- Every interactive control has an accessible name and a visible focus/pressed state where the platform supports it.
- Never communicate meaning by color alone.
- Keep interaction targets at least 44px / 44dp / 44pt.
- Support localization, long labels, large text, and dynamic content without clipping.
- Disabled, loading, error, success, and selected states must remain programmatically understandable.
- Respect reduced-motion settings.
- Test both light and dark themes.

## Component checklist

1. Anatomy and reading order are explicit.
2. Keyboard or assistive navigation is defined.
3. Screen-reader name, role, state, and value are defined.
4. Long content and large text are verified.
5. Motion has a reduced-motion behavior.
6. Validation and status are not color-only.
7. Web, Android, and iOS mappings are documented.

## Release gate

A component is not complete until accessibility behavior is documented and implemented on every supported platform.