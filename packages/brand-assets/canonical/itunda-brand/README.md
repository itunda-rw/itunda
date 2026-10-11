# Itunda Brand v3 assets

This directory is the canonical home for Itunda brand assets. Brand v3 uses one flat brand color in light and dark modes: `#8F89FF`. Keep the Itunda identity and use these source assets unchanged; do not redraw or substitute the symbol.

## Color tokens

| Token | Value | Use |
|---|---|---|
| Brand | `#8F89FF` | Logo, brand surfaces and primary actions |
| Brand pressed | `#7D77F7` | Pressed UI state |
| On-brand text | `#121216` | Text on brand fills; avoid white text on this color |
| Brand text on light | `#6960F9` | Links and text on light surfaces |
| Brand text on dark | `#8F89FF` | Links and text on dark surfaces |
| Light tint | `#F1F0FF` | Selected controls and subtle brand surfaces |
| Dark tint | `#25234A` | Selected controls and subtle brand surfaces |
| Dark background / surface / elevated | `#121216` / `#1C1C22` / `#25252D` | Neutral dark theme |

Use neutral text and surface tokens for readability; do not apply the brand color to every element. Primary and pressed brand fills remain the same across light and dark modes.

## symbol/

- `itunda-symbol.svg`: main flat-color symbol for light or dark backgrounds.
- `itunda-symbol-white.svg`: white symbol for brand or dark surfaces.
- `itunda-symbol-mono.svg`: monochrome usage where the color can be controlled.
- `itunda-symbol-small-mono.svg`: small symbol for compact navigation and icons.
- `itunda-symbol-animated.svg`: web-only animation; respect reduced-motion preferences.
- Older `*-dark.svg` variants are retained for compatibility during migration; new integrations should use the Brand v3 symbol unless a specific platform appearance requires otherwise.

## app-icons/

Includes the store/app icon, favicon, iOS dark/tinted variants, Android adaptive background and foreground, Android monochrome icon, and notification symbol. Keep platform-safe zones and platform-specific icon requirements in mind.

## splash/

Includes light and dark splash screens and Android 12 splash-icon variants. Pair the transparent splash icon with the correct background color.

## lockups/

Product lockups are available for bank, pay, eats, shopping, loans, insurance, transit, and invest in light and dark variants. In application UI, prefer the symbol plus native text where it improves accessibility and localization.

## Rendering and release checks

- Symbol crossings use SVG masks. Browsers and react-native-svg support masks, but Android VectorDrawable and Xcode asset catalogs may not; export raster assets through a deterministic build step for native app icons rather than assuming SVG is supported.
- Lockups contain live text. For native UI, render the symbol plus native localized text rather than relying on embedded SVG text.
- Use the small symbol below 24px where appropriate.
- Before release, verify the exact source asset, rendered appearance, safe zone, and light/dark behavior on real Android and iOS builds. Canonical SVG presence alone does not mean platform icons have been regenerated.
- Trademark search remains a separate release task.
