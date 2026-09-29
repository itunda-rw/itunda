# IDS parity tooling

Run:

```bash
node scripts/design-system-parity.mjs
```

The check validates semantic roles across Web, Android and iOS. It does not require pixel-identical platform code: Compose, SwiftUI and Web retain native geometry and accessibility behavior.

The legacy `packages/design-tokens/tokens.json` blue/grey/red primitives remain for compatibility and generation. They are not the current Itunda brand contract. Current semantic brand anchors are `#7472F4` (light) and `#7675F8` (dark).

Future CI should run this check before publishing standalone IDS platform packages.

## Component parity

Token parity is necessary but not sufficient. IDS now maintains a component contract in component-parity.json covering API variants, interaction states, accessibility, localization, dark mode, responsive/platform behavior, and recovery behavior.

Core components currently covered: Button, Text Field, List Row, Empty State, Error State, Skeleton, and Bottom CTA. Android and iOS may use Jetpack Compose/SwiftUI while Web uses CSS/HTML primitives; parity means the user-facing contract remains equivalent rather than forcing identical implementation code.
