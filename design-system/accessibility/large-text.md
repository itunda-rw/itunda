# IDS Large Text

Large text is a supported product state, not an edge case.

## Requirements

- Text may wrap to multiple lines.
- Controls may grow vertically when content requires it.
- Icons and supporting layout may adapt without clipping text.
- Avoid fixed heights around text-heavy content.
- Test long Korean, English, and localized strings.
- Verify disabled, error, success, selected, and loading states at larger text sizes.

## Component gate

Every component guide must state how it behaves when text becomes larger than its default scale.