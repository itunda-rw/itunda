# IDS Keyboard Interaction

Keyboard behavior is part of the component API for Web and any hardware-keyboard experience supported by a platform.

## Rules

- Tab moves through interactive controls in a logical order.
- Focus is always visible.
- Enter/Space activate buttons according to native platform behavior.
- Arrow keys operate composite widgets such as Tabs according to their documented pattern.
- Home/End are supported where the component contract defines them.
- Escape closes dismissible overlays and returns focus to the invoking control.
- Never trap focus in a non-modal component.

## Composite widgets

Each composite component must document its keyboard model alongside its visual states.