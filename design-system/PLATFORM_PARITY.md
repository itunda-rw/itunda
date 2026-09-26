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

## Release rule

A component must not be described as build-verified or visual-verified merely because it is mapped or contract-checked.

The audit currently proves source-level coverage. Platform builds and rendered visual states require separate verification.

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
