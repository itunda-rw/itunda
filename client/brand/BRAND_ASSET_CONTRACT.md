# Itunda Brand Asset Contract

Source of truth: `itunda-brand.zip` supplied by the Itunda brand package.

Package SHA-256: `922d93772ad57b005339cba7e34309f26999c186b4c86513d07e7a47b5486546`

The exact file hashes are pinned in `BRAND_ASSET_SHA256_MANIFEST.md`.

Do not redraw, recolor, optimize, replace, or regenerate these assets.

## Canonical directories

- `symbol/` — primary Itunda symbol variants
- `app-icons/` — iOS, Android, browser, and notification assets
- `splash/` — light/dark launch assets
- `lockups/` — product lockups for consumer mini-products

## Symbol rules

- `itunda-symbol.svg` — primary symbol, 32px+
- `itunda-symbol-white.svg` — indigo/photo backgrounds
- `itunda-symbol-dark.svg` — dark mode
- `itunda-symbol-mono.svg` — one-color contexts
- `itunda-symbol-small-mono.svg` — below 32px; favicon/tab/list contexts
- `itunda-symbol-animated.svg` — web only; respects reduced motion

## App assets

- `itunda-app-icon.svg` — iOS/store source
- `itunda-ios-dark.svg` — iOS dark appearance
- `itunda-ios-tinted.svg` — iOS tinted appearance
- `itunda-android-adaptive-background.svg` + `itunda-android-adaptive-foreground.svg` — Android adaptive icon
- `itunda-android-monochrome.svg` — Android themed icon
- `itunda-android-notification.svg` — Android notification/status icon
- `itunda-favicon.svg` — browser favicon

## Splash assets

- `itunda-splash-light.svg`
- `itunda-splash-dark.svg`
- `itunda-splash-icon.svg`
- `itunda-splash-icon-dark.svg`

## Brand color

Primary Itunda Indigo: `#7472F4`

Supporting canonical colors:
- Deeper Indigo: `#4B47C9`
- Light: `#B0AEFF`
- Dark-mode: `#9B98FF`

## Product application

The canonical brand source is shared by:
Itunda, Itunda Business, Itunda Developers, Itunda Tech, Itunda Designs, ItundaFace, and Saronite.

Product-specific wordmarks/lockups must remain product-specific. The core Itunda symbol remains canonical.

## Rendering constraints

Assets using SVG masks must preserve mask support. For Android VectorDrawable/Xcode asset catalogs, use build-time PNG exports rather than altering the source SVG.

Below 24px, use the supplied small symbol variant.

Lockups use live text; native app surfaces should render the supplied symbol plus native product text where appropriate.
