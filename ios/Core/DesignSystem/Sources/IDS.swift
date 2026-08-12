//
//  IDS.swift
//  Ported from mobile_clients/ios (2026-07-10) -- see docs/ARCHITECTURE.md §3.
//
//  Reconciled (2026-07-11): this token set and this module's Theme/IdsTheme.swift
//  (IdsPalette/IdsTypeScale) are two real, independently-built design systems that
//  both exist in this repo, ported as-is rather than silently merged under time
//  pressure -- but they were never actually competing at the same layer. IdsPalette
//  is the raw/primitive brand palette (Blue500, Gray900, ...) and correctly stays
//  static/non-reactive, exactly like Android's `IdsPalette` object. IDS.Colors is
//  the *semantic role* layer (textPrimary, background, divider, ...) -- the layer
//  that should actually change between light and dark, matching Android's
//  `Ids.colors` (`IdsSemanticColors`/`IdsLightSemanticColors`/`IdsDarkSemanticColors`
//  in core/designsystem/theme/IdsSemanticColors.kt). That's the real reconciliation:
//  not merging two files into one, but making the *semantic* layer theme-reactive
//  the way Android's already is, while leaving the primitive layer alone by design.
//
//  SwiftUI/UIKit can do this without Android's CompositionLocal-equivalent
//  plumbing: `Color(light:dark:)` below builds a `UIColor` with a dynamic
//  provider, so every existing call site (`IDS.Colors.textPrimary`, etc.)
//  automatically resolves to the right value for the current trait collection --
//  no call site anywhere in this file's consumers needed to change. Dark values
//  are Android's already-tuned `IdsDarkSemanticColors` values where the concept
//  maps 1:1 (verified against a real Toss dark-mode reference, see that file's own
//  comment); `iconTertiary`'s dark value has no Android counterpart to port, so
//  it's a reasonable extrapolation one step up from `divider`, noted inline.
//  `backgroundPrimary`'s light value also moved from `#F4F6F8` to Android's
//  `#F2F4F6` -- the 2-unit-per-channel drift the parity audit flagged.
//
//  NOT build-verified -- see Core/Risk/Sources/ZeroTrust.swift for why.
//

import SwiftUI
import UIKit

extension Color {
    /// A theme-reactive color: resolves to `dark` under `.dark` userInterfaceStyle,
    /// `light` otherwise. See this file's header for why IDS.Colors uses this and
    /// IdsPalette (the primitive layer) deliberately doesn't.
    init(light: UInt, dark: UInt) {
        self.init(uiColor: UIColor { traitCollection in
            traitCollection.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        })
    }
}

private extension UIColor {
    convenience init(hex: UInt, alpha: CGFloat = 1) {
        self.init(
            red: CGFloat((hex >> 16) & 0xff) / 255,
            green: CGFloat((hex >> 08) & 0xff) / 255,
            blue: CGFloat((hex >> 00) & 0xff) / 255,
            alpha: alpha
        )
    }
}

public struct IDS {
    // Fixed (2026-07-11): every font constant below Typography (and IdsTypeScale in
    // Theme/IdsTheme.swift, and every consumer that had its own inline
    // Font.system(size:weight:) call, e.g. FeatureBanking's BankView.swift) used to be
    // a plain Font.system(size:weight:) -- a fixed point size that does not grow or
    // shrink with the user's iOS Settings > Accessibility > Display & Text Size >
    // Larger Text setting (Dynamic Type), unlike semantic styles such as .title/.body.
    // This wraps UIFontMetrics.scaledFont(for:), Apple's documented pattern for "keep
    // this exact point size at the default content size category, but still scale
    // with Dynamic Type" -- the right fix when a design calls for a specific size that
    // doesn't map onto a built-in text style, rather than switching to .title/.body
    // and losing the design's actual type scale. Public (not private to Typography)
    // so every consumer with the same problem can share one implementation instead of
    // each defining its own copy. Not build-verified for the usual
    // no-Xcode-in-this-environment reason (see this file's own header) -- UIFontMetrics
    // is real UIKit API, not invented, but the runtime scaling behavior itself is
    // unverified here.
    // Real typeface fix (2026-08-13, direct user feedback: "we are still far away from
    // toss") -- see Android's identical Pretendard.kt for the full sourced account
    // (github.com/orioncactus/pretendard, SIL Open Font License 1.1, bundled as
    // App/Resources/Fonts/*.ttf and registered via UIAppFonts in Project.swift). Every
    // real call site below used to render UIFont.systemFont(ofSize:weight:) -- the plain
    // platform system font -- despite this whole app being built around Toss's own real
    // visual language everywhere else. Falls back to the real system font if the custom
    // font somehow isn't loaded (a defensive guard, not the normal path) so this can
    // never crash or silently render blank text.
    private static func pretendardFont(size: CGFloat, weight: UIFont.Weight) -> UIFont {
        let postscriptName: String
        switch weight {
        case .bold, .heavy, .black: postscriptName = "Pretendard-Bold"
        case .semibold: postscriptName = "Pretendard-SemiBold"
        case .medium: postscriptName = "Pretendard-Medium"
        default: postscriptName = "Pretendard-Regular"
        }
        return UIFont(name: postscriptName, size: size) ?? UIFont.systemFont(ofSize: size, weight: weight)
    }

    public static func scaledFont(size: CGFloat, weight: UIFont.Weight, relativeTo style: UIFont.TextStyle) -> Font {
        Font(UIFontMetrics(forTextStyle: style).scaledFont(for: pretendardFont(size: size, weight: weight)))
    }

    public struct Colors {
        // Dark values corrected 2026-07-21 (matches Android's
        // IdsSemanticColors.kt, same date): the previous true-black/navy set was
        // eyeballed from screenshots on 2026-07-10, which can't reliably tell true
        // black from a very dark grey. Toss's actual currently-published
        // `@toss/tds-colors@0.1.0` npm package ships the real adaptive dark values
        // directly (`--adaptiveBackground: #17171c`, `--adaptiveBackgroundLevel01:
        // #202027`, `--adaptiveBackgroundLevel02: #2c2c35`, `--adaptiveBlue500:
        // #3485fa`, `--adaptiveHairlineBorder: #3c3c47`) -- mapped by role (Level01/02
        // are named elevation steps above the base background, matching
        // backgroundSecondary/backgroundTertiary here). `pressed` and the tint colors
        // are left untouched since the real package doesn't expose which numbered
        // step its own semantic roles point to.
        public static let brand = Color(light: 0x3182F6, dark: 0x3485FA)
        public static let backgroundPrimary = Color(light: 0xF2F4F6, dark: 0x17171C)
        public static let backgroundSecondary = Color(light: 0xFFFFFF, dark: 0x202027)
        public static let backgroundTertiary = Color(light: 0xEDF2F7, dark: 0x2C2C35)
        public static let card = Color(light: 0xFFFFFF, dark: 0x202027)
        public static let raisedCard = Color(light: 0xFFFFFF, dark: 0x202027)
        public static let pressed = Color(light: 0xEAF2FF, dark: 0x1F3053)
        public static let divider = Color(light: 0xE5E8EB, dark: 0x3C3C47)
        public static let textPrimary = Color(light: 0x191F28, dark: 0xFFFFFF)
        public static let textSecondary = Color(light: 0x4E5968, dark: 0x989EAA)
        // Real WCAG AA contrast fix (item 241, docs/ACCESSIBILITY.md finding #1) --
        // light 0x8B95A1 measured 2.76:1 against backgroundPrimary and 3.04:1 against
        // a white card, both failing 4.5:1 AA-normal-text (this renders small caption/
        // timestamp text). Dark 0x575C66's audit numbers predate the 2026-07-21 true-
        // dark correction and were stale -- against this file's real dark values
        // (backgroundPrimary 0x17171C, card 0x202027) it actually measured 2.66:1 /
        // 2.41:1, even worse than documented. New values (light 0x636E7C: 4.70:1 /
        // 5.18:1; dark 0x848A96: 5.15:1 / 4.67:1) are the minimal step toward
        // textSecondary's own hue that clears 4.5:1 on both real backgrounds in each
        // theme. Matches the identical fix applied the same day to web's
        // --toss-grey-500 and Android's IdsSemanticColors.textTertiary. IdsPalette's
        // raw gray500 primitive (Theme/IdsTheme.swift, machine-generated from
        // tokens.json) is deliberately left untouched -- see MapScreenView.swift's
        // real, separate bug of using that primitive directly instead of this
        // theme-reactive semantic token, fixed alongside this.
        public static let textTertiary = Color(light: 0x636E7C, dark: 0x848A96)
        public static let textBrand = brand
        // Real WCAG AA contrast fix (item 240, docs/ACCESSIBILITY.md finding #2) --
        // iOS had no shared success/green token at all (only successTint, the pale
        // background wash below); real screens used SwiftUI's system `Color.green`
        // directly, which measures 2.22:1 against white -- worse than the light-mode
        // itunda green token this same fix corrects on Android/web (2.40:1), both
        // failing even the lenient 3.0:1 AA-large/UI threshold. Same values as
        // Android's IdsSemanticColors.success / web's --toss-green: light 0x05804A
        // (5.01:1 against white), dark 0x20D394 (already passes at 9+:1, matching
        // dark mode's own existing pattern of choosing a brighter shade for exactly
        // this reason).
        public static let success = Color(light: 0x05804A, dark: 0x20D394)
        public static let successTint = Color(light: 0xF5FAFF, dark: 0x10321F)
        public static let warningTint = Color(light: 0xFFF4D6, dark: 0x3A2E10)
        public static let dangerTint = Color(light: 0xFFECEB, dark: 0x3A1418)
        // Real cross-platform parity fix (2026-08-04) -- iOS had no plain danger TEXT
        // color token at all (every screen hardcoded `.foregroundColor(.red)`, system
        // red, not an IDS token); ported directly from Android's own
        // IdsSemanticColors.kt (light `danger = Color(0xFFF04452)`, dark
        // `danger = Color(0xFFFF6B7A)`), not guessed.
        public static let danger = Color(light: 0xF04452, dark: 0xFF6B7A)
        public static let iconPrimary = Color(light: 0x2C3643, dark: 0xE8EAED)
        public static let iconSecondary = Color(light: 0x6B7684, dark: 0x989EAA)
        // No Android IdsDarkSemanticColors counterpart to port -- extrapolated one
        // step up from `divider`'s dark value, not a direct reference-matched port.
        public static let iconTertiary = Color(light: 0xDDE3EA, dark: 0x3A3D45)
        // Matches Android's IdsSemanticColors.chip / .surfaceSoft -- one token covers
        // both roles here (chip pill backgrounds, soft icon-badge backgrounds). Added
        // 2026-07-11 for the Benefits/Shop/All tab rebuild -- IDS.Colors had no
        // chip/soft-surface role before this. Dark value corrected 2026-07-21 along
        // with backgroundTertiary/surfaceSoft (see IDS.Colors' own header comment).
        public static let chipBackground = Color(light: 0xF2F4F6, dark: 0x2C2C35)
        // Android's dark shadow is ~25% opacity black (0x40000000) vs. light's 8%.
        public static let shadow = Color(uiColor: UIColor { traitCollection in
            traitCollection.userInterfaceStyle == .dark
                ? UIColor.black.withAlphaComponent(0.25)
                : UIColor.black.withAlphaComponent(0.08)
        })

        public static let primaryBlue = brand
        public static let background = backgroundPrimary
        public static let positiveBackground = successTint
    }

    // Fixed (2026-07-11): every entry here used to be a plain Font.system(size:weight:)
    // with no Dynamic Type scaling -- see IDS.scaledFont's own doc comment above for
    // the full reasoning. No call site needed to change -- every existing
    // `IDS.Typography.header` etc. reference keeps working, it just scales now.
    public struct Typography {
        // Explicitly qualified with `IDS.` -- a nested type's static members can't
        // reliably rely on unqualified lookup to reach the enclosing type's members.
        public static let header = IDS.scaledFont(size: 30, weight: .bold, relativeTo: .largeTitle)
        public static let title = IDS.scaledFont(size: 22, weight: .bold, relativeTo: .title1)
        public static let sectionLabel = IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline)
        public static let bodyBold = IDS.scaledFont(size: 17, weight: .bold, relativeTo: .body)
        public static let bodyMedium = IDS.scaledFont(size: 15, weight: .medium, relativeTo: .subheadline)
        public static let caption = IDS.scaledFont(size: 13, weight: .medium, relativeTo: .caption1)
        public static let largeAmount = IDS.scaledFont(size: 34, weight: .bold, relativeTo: .largeTitle)
        public static let metric = IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2)
    }

    public struct Layout {
        public static let screenHorizontal: CGFloat = 20
        public static let screenTop: CGFloat = 18
        public static let sectionSpacing: CGFloat = 24
        public static let cardPadding: CGFloat = 24
        public static let cardGap: CGFloat = 16
        public static let rowGap: CGFloat = 14
        public static let inlineGap: CGFloat = 12
        public static let tightGap: CGFloat = 8
        public static let cardCornerRadius: CGFloat = 28
        public static let sectionCornerRadius: CGFloat = 26
        public static let buttonCornerRadius: CGFloat = 18
        public static let pillCornerRadius: CGFloat = 999
        public static let iconCornerRadius: CGFloat = 18
        public static let tabBarCornerRadius: CGFloat = 28
        public static let topBarActionSize: CGFloat = 44
        public static let quickActionIconSize: CGFloat = 48
        public static let rowIconSize: CGFloat = 44
        public static let tabBarIconSize: CGFloat = 26
        public static let floatingTabShadowRadius: CGFloat = 14

        public static let standardPadding: CGFloat = cardPadding
    }
}
