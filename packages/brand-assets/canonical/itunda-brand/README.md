# itunda brand assets v3 (one color, #8F89FF)

This updates the earlier itunda-brand package. The logo is now a single flat color, `#8F89FF`, in light mode, dark mode, app icon and splash. There are no deep shades and no dark halos. The crossings are transparent gaps, so one file works on any background.

## Colors (tokens/)
| token | value | notes |
|---|---|---|
| brand | #8F89FF | logo, buttons, icon background. Same in both modes |
| on-brand text | #121216 | text on brand fills: 6.4:1. White on brand is only 2.9:1, avoid it |
| brand-pressed | #7D77F7 | pressed state, UI only |
| brand-text | #6960F9 on light, #8F89FF on dark | link text that passes 4.5:1 |
| tint | #F1F0FF light, #25234A dark | selected rows, chips |
| dark bg / surface / elevated | #121216 / #1C1C22 / #25252D | neutral surfaces |

Contrast to know: brand on dark 6.4:1 (good), brand on white 2.9:1 (just under the 3:1 guideline for graphics). If you need to pass 3:1 on white, replace #8F89FF with #8D86FC (3.0:1, visually almost identical): search and replace in the SVGs and tokens.

## Files
- symbol/: itunda-symbol (one file for light and dark), -white, -mono (set `color`), -small-mono (use below 32px), -animated (web)
- app-icons/: app-icon (iOS/store, square), ios-dark, ios-tinted, android adaptive background + foreground, android monochrome, android notification, favicon
- splash/: splash-light, splash-dark (1080x1920), splash-icon (Android 12, transparent; set splash background to #FFFFFF or #121216)
- lockups/: bank, pay, eats, shopping, loans, insurance, transit, invest, each light and dark
- tokens/: itunda-colors.css and .json

## Changes from the earlier package
- Removed: itunda-symbol-dark.svg and itunda-splash-icon-dark.svg (the symbol is now the same in both modes).
- All colors changed to #8F89FF; gradients and shadows removed.

## Read before shipping
- Gaps use an SVG `<mask>`. Browsers and react-native-svg support it; Android VectorDrawable and Xcode asset catalogs may not, so export PNGs for those.
- Lockups use live text in a Pretendard font stack; outline for print.
- Not yet checked in Xcode or Android Studio. Trademark search still pending.
