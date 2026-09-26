# IDS Platform Parity

The IDS contract is the source of truth for Web, Android, and iOS primitive behavior.

## Verification levels

| Level | Meaning | Current audit |
|---|---|---|
| Mapped | Contract component resolves to a concrete implementation symbol on Web, Android, and iOS. | Automated |
| Contract-checked | Required states, content, accessibility, motion, and platform dimensions are present in the shared manifest. | Automated |
| Web a11y-checked | Key Web semantics required by the contract are represented in source (`aria-busy`, `aria-invalid`, tab/switch roles). | Automated |
| Build-verified | The corresponding platform project builds successfully from the current commit. | Not claimed |
| Visual-verified | Canonical states render correctly across themes, text scales, content stress, and reduced motion. | Not yet automated |

## Current canonical primitive mapping

| Primitive | Web | Android | iOS |
|---|---|---|---|
| Button | IDS web primitive | `IdsButton` | `IDSButton` |
| TextField | IDS web primitive | `IdsTextField` | `IDSTextField` |
| Select | IDS web primitive | `IdsSelect` | `IDSSelect` |
| Checkbox | IDS web primitive | `IdsCheckbox` | `IDSCheckbox` |
| Radio | IDS web primitive | `IdsRadioButton` | `IDSRadio` |
| Switch | IDS web primitive | `IdsSwitch` | `IDSSwitch` |
| Tabs | IDS web primitive | `IdsTabs` | `IDSTabs` |
| EmptyState | IDS web primitive | `IdsEmptyState` | `IDSEmptyState` |

This mapping is **source-level parity**. It does not claim that the three implementations have been rendered and pixel/behavior compared on real devices.


## Deterministic render matrix

The required execution matrix is defined in `design-system/qa/render-matrix.json`. It covers all eight canonical primitives, every deterministic QA scenario, Web/Android/iOS, light/dark themes, three text/display scale levels, normal/reduced motion, and representative compact/expanded viewports.

The matrix is a **verification contract**, not evidence. A scenario becomes verified only after real rendered evidence is captured with the current component, platform, scenario, theme, scale, motion, viewport, and contract signatures required by `design-system/qa/evidence-schema.json`.

## Release rule

A component must not be described as build-verified or visual-verified merely because it is mapped or contract-checked.

The audit currently proves source-level coverage. Platform builds and rendered visual states require separate verification. The canonical eight primitives now have concrete Web, Android, and iOS implementation surfaces, so the remaining 100% target is execution: build, render, exercise, capture, and compare the same scenario matrix on each platform.

## Canonical dimensions

Every promoted primitive should be reviewed for:

- default, pressed, focus, disabled, loading, error, and success states where applicable
- short and long localized content
- large text / dynamic type
- light and dark themes
- reduced motion
- keyboard and screen-reader behavior
- minimum 44px / 44dp / 44pt interaction targets
- Web / Android / iOS platform-specific implementation behavior
