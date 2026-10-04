# Itunda Brand

Canonical visual identity package for Itunda. The prepared source package uses the Itunda droplet/leaf symbol system and is designed to work consistently across product UI, websites, native apps, and partner experiences.

## Identity

- Primary brand: `#7472F4` — Itunda Indigo
- Deeper brand: `#4B47C9`
- Light highlight: `#B0AEFF`
- Dark-mode brand: `#9B98FF`
- Dark surface: `#090B0F`
- Primary text: `#191F28`
- Light surface: `#FFFFFF` / `#F7F8FA`

## Symbol

The symbol is the primary Itunda mark. Use the symbol alone where recognition matters more than the wordmark.

- `symbol/itunda-symbol.svg` — primary color symbol
- `symbol/itunda-symbol-dark.svg` — dark-mode symbol
- `symbol/itunda-symbol-white.svg` — white symbol for indigo/photo surfaces
- `symbol/itunda-symbol-mono.svg` — single-color applications
- `symbol/itunda-symbol-small-mono.svg` — small-size mark
- `symbol/itunda-symbol-animated.svg` — web animation; honor reduced motion

Do not redraw, stretch, rotate, or add independent decorative effects to the symbol.

## Product lockups

Product lockups are available in light/dark variants for:

- Bank
- Pay
- Eats
- Shopping
- Loans
- Insurance
- Transit
- Invest

For native product UI, prefer the symbol plus native text rather than embedding live SVG text into the app.

## App icons

The package includes iOS and Android adaptive/tinted/monochrome/notification variants plus a favicon. OS-level containers should provide their own platform rounding; the source app icon remains square.

## Splash

Light and dark splash compositions are provided, together with platform splash-icon variants. The dark splash uses `#090B0F`.

## Usage rules

1. Keep Itunda Indigo as the identity anchor.
2. Light and dark themes must remain first-class and visually equivalent.
3. Keep the visual language flat and calm; avoid unnecessary gradients, glass effects, 3D treatment, or decorative chrome in product UI.
4. Prefer whitespace, hierarchy, and clear actions over borders and divider-heavy layouts.
5. Use the small symbol below 24px.
6. Preserve the supplied symbol geometry and safe area.
7. SVG masks may need platform-specific raster/vector export for Android VectorDrawable and Xcode asset catalogs.
8. The supplied lockups use live text; marketing/print exports should outline text, while app surfaces should use native typography.

## IDS relationship

This package is the source asset layer. IDS remains the semantic design contract: components, spacing, typography, motion, accessibility, and platform-specific implementation rules consume the brand identity rather than redefining it.

The canonical semantic brand anchor remains `#7472F4`.
