# IDS Accessible Forms

Forms must make labels, input purpose, current value, validation, and recovery understandable without relying on color or visual position.

## Contract

- Every field has a visible label.
- Supporting, error, and success messages are associated with the field.
- Required status is programmatically exposed.
- Invalid state is exposed semantically.
- Error and success meaning is reinforced with text or another non-color cue.
- Long validation messages wrap naturally.
- Input type and IME/keyboard behavior remain native to each platform.
- Submission/loading states prevent duplicate actions without hiding the user's entered data.

## Recovery

Error messages should explain what happened and, when actionable, what the user can do next.