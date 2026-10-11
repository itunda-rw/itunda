# itunda logo assets (trefoil symbol)

Symbol only, unless the file is in `lockups/`. Brand colors: indigo `#7472F4`, deeper `#4B47C9`, light `#B0AEFF`, dark-mode `#9B98FF`.

## symbol/
| file | use |
|---|---|
| itunda-symbol.svg | main color symbol, light backgrounds, 32px and up |
| itunda-symbol-white.svg | on indigo or photo backgrounds |
| itunda-symbol-dark.svg | dark mode |
| itunda-symbol-mono.svg | one color (set the `color` property), stamps, receipts, emboss |
| itunda-symbol-small-mono.svg | thinner strands for 16-32px (favicon, tab bars, list icons) |
| itunda-symbol-animated.svg | web only: the knot draws itself in, honors reduced-motion |

## app-icons/
| file | use |
|---|---|
| itunda-app-icon.svg | iOS and store icon, 1024 square, no rounded corners (the OS rounds it) |
| itunda-ios-dark.svg / itunda-ios-tinted.svg | iOS 18 dark and tinted appearances |
| itunda-android-adaptive-background.svg + ...-foreground.svg | Android adaptive icon layers (108dp, foreground sits inside the 66dp safe zone) |
| itunda-android-monochrome.svg | Android themed-icon layer |
| itunda-android-notification.svg | white-only status bar icon (24dp) |
| itunda-favicon.svg | browser tab icon |

## splash/
itunda-splash-light.svg / itunda-splash-dark.svg: 1080x1920 logo-only screens.
itunda-splash-icon.svg / itunda-splash-icon-dark.svg: Android 12 splash icon (288dp, transparent background; set the splash background color to white / #090B0F).

## lockups/
`itunda-<product>-<light|dark>.svg` for bank, pay, eats, shopping, loans, insurance, transit, invest.

## Read before shipping
- Files marked "gaps" use an SVG `<mask>` to cut transparent gaps at the crossings. Renderers that skip masks will show solid blobs. react-native-svg and browsers support masks. Android VectorDrawable and Xcode asset catalogs may not, so export PNGs (or run the SVGs through your build tool) for those.
- Lockups use live `<text>` in a font stack starting with Pretendard. Outline the text in a design tool for print or marketing, and in the app render the symbol SVG plus native text instead.
- Below 24px use the small version, not the main symbol.
- Dark-mode halos are tuned for `#090B0F`.
- Not yet tested in Xcode or Android Studio, and not yet trademark-searched.
