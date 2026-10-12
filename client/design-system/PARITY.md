# IDS parity tooling

Run:

```bash
node scripts/design-system-parity.mjs
```

The check validates semantic roles across Web, Android and iOS. It does not require pixel-identical platform code: Compose, SwiftUI and Web retain native geometry and accessibility behavior.

The legacy `packages/design-tokens/tokens.json` blue/grey/red primitives remain for compatibility and generation. They are not the current Itunda brand contract. Current semantic brand anchors are `#7472F4` (light) and `#7675F8` (dark).

The `IDS semantic parity` GitHub Actions workflow runs this check and the component-source contract checker on relevant pull requests. A fresh passing run is still required for each new head before claiming the latest changes are verified.

## Component parity

Token parity is necessary but not sufficient. IDS now maintains a component contract in component-parity.json covering API variants, interaction states, accessibility, localization, dark mode, responsive/platform behavior, and recovery behavior.

Core components currently covered: Button, Text Field, List Row, Empty State, Error State, Skeleton, and Bottom CTA. Android and iOS may use Jetpack Compose/SwiftUI while Web uses CSS/HTML primitives; parity means the user-facing contract remains equivalent rather than forcing identical implementation code.


## Token-integrity contract

The semantic parity check also validates the legacy `--tds-*` compatibility layer:

- Every `var(--tds-...)` reference in `client/design-system/components.css` must have a declared token.
- Every alias referenced by a declared `--tds-*` token must resolve to a declared CSS custom property.
- Alias chains must not contain cycles, including cycles that pass through canonical `--itunda-*` variables.

This is static integrity validation. It does not prove that components render correctly or that interactions work on device.

## Release gates (current state)

| Gate | Status |
| --- | --- |
| Web / Android / iOS semantic color parity, light and dark | Automated check exists |
| WCAG AA normal-text contrast against declared semantic surfaces | Automated check exists |
| Component source mapping and contract completeness | Automated check exists |
| Legacy TDS-token references and alias integrity | Automated check exists; latest head needs fresh CI confirmation |
| Runtime interaction tests on Web / Android / iOS | Not yet complete |
| Screenshot baselines and visual-diff review | Not yet complete |
| Versioned IDS packages and compatibility policy | Not yet complete |

Do not equate a static source check with runtime, visual, or device validation. A release must report each gate separately.
