# IDS parity tooling

Run:

```bash
node scripts/design-system-parity.mjs
```

The check validates semantic roles across Web, Android and iOS. It does not require pixel-identical platform code: Compose, SwiftUI and Web retain native geometry and accessibility behavior.

The legacy `packages/design-tokens/tokens.json` blue/grey/red primitives remain for compatibility and generation. They are not the current Itunda brand contract. Current semantic brand anchors are `#7472F4` (light) and `#7675F8` (dark).

Future CI should run this check before publishing standalone IDS platform packages.
