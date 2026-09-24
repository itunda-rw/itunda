# Itunda Design System (IDS)

Itunda Design System is the shared visual foundation extracted from the Itunda monorepo so it can be reused across independent projects.

## Included

- Cross-platform design tokens
- Light and dark semantic colors
- Typography scale
- Spacing, shape, elevation and motion primitives
- Web CSS tokens
- Android Jetpack Compose theme and reusable components
- SwiftUI theme and reusable components
- Pretendard web font assets and license notice

## Structure

```
design-system/
  tokens/
    tokens.json
    tokens.css
    fonts/
  android/
    theme/
    components/
  ios/
    IDS.swift
    Theme/
    Components/
```

## Design language

IDS is intentionally its own design system. It uses a calm, high-contrast interface language: strong hierarchy, compact spacing, rounded surfaces, restrained decoration, accessible contrast, responsive typography, and shared motion behavior.

Some values and interaction research in the original Itunda implementation were informed by publicly documented industry design-system material. Itunda is not affiliated with or endorsed by Toss or any other referenced company.

## License

Itunda-authored source is released under the MIT License.

Pretendard font files remain subject to the SIL Open Font License 1.1; see `tokens/PRETENDARD_LICENSE.txt`.

## Status

This branch is the extraction stage from `itunda-rw/itunda`. The next packaging step is to publish the same extracted source as a standalone public repository, with Android and Swift Package distributions added without changing the design language.
