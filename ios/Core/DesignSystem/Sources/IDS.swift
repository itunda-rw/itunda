//
//  IDS.swift
//  Ported from mobile_clients/ios (2026-07-10) -- see docs/ARCHITECTURE.md §3.
//
//  Reconciled (2026-07-11): this token set and this module's Theme/TdsTheme.swift
//  (TdsColors/TdsTypography) are two real, independently-built design systems that
//  both exist in this repo, ported as-is rather than silently merged under time
//  pressure -- but they were never actually competing at the same layer. TdsColors
//  is the raw/primitive brand palette (Blue500, Gray900, ...) and correctly stays
//  static/non-reactive, exactly like Android's `TdsColors` object. IDS.Colors is
//  the *semantic role* layer (textPrimary, background, divider, ...) -- the layer
//  that should actually change between light and dark, matching Android's
//  `Tds.colors` (`TdsSemanticColors`/`TdsLightSemanticColors`/`TdsDarkSemanticColors`
//  in core/designsystem/theme/TdsSemanticColors.kt). That's the real reconciliation:
//  not merging two files into one, but making the *semantic* layer theme-reactive
//  the way Android's already is, while leaving the primitive layer alone by design.
//
//  SwiftUI/UIKit can do this without Android's CompositionLocal-equivalent
//  plumbing: `Color(light:dark:)` below builds a `UIColor` with a dynamic
//  provider, so every existing call site (`IDS.Colors.textPrimary`, etc.)
//  automatically resolves to the right value for the current trait collection --
//  no call site anywhere in this file's consumers needed to change. Dark values
//  are Android's already-tuned `TdsDarkSemanticColors` values where the concept
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
    /// TdsColors (the primitive layer) deliberately doesn't.
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
    public struct Colors {
        public static let brand = Color(light: 0x3182F6, dark: 0x4C8FFF)
        public static let backgroundPrimary = Color(light: 0xF2F4F6, dark: 0x000000)
        public static let backgroundSecondary = Color(light: 0xFFFFFF, dark: 0x17181D)
        public static let backgroundTertiary = Color(light: 0xEDF2F7, dark: 0x23242B)
        public static let card = Color(light: 0xFFFFFF, dark: 0x17181D)
        public static let raisedCard = Color(light: 0xFFFFFF, dark: 0x17181D)
        public static let pressed = Color(light: 0xEAF2FF, dark: 0x1F3053)
        public static let divider = Color(light: 0xE5E8EB, dark: 0x2B2D35)
        public static let textPrimary = Color(light: 0x191F28, dark: 0xFFFFFF)
        public static let textSecondary = Color(light: 0x4E5968, dark: 0x989EAA)
        public static let textTertiary = Color(light: 0x8B95A1, dark: 0x575C66)
        public static let textBrand = brand
        public static let successTint = Color(light: 0xE8F3FF, dark: 0x10321F)
        public static let warningTint = Color(light: 0xFFF4D6, dark: 0x3A2E10)
        public static let dangerTint = Color(light: 0xFFECEB, dark: 0x3A1418)
        public static let iconPrimary = Color(light: 0x2C3643, dark: 0xE8EAED)
        public static let iconSecondary = Color(light: 0x6B7684, dark: 0x989EAA)
        // No Android TdsDarkSemanticColors counterpart to port -- extrapolated one
        // step up from `divider`'s dark value, not a direct reference-matched port.
        public static let iconTertiary = Color(light: 0xDDE3EA, dark: 0x3A3D45)
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

    public struct Typography {
        public static let header = Font.system(size: 30, weight: .bold)
        public static let title = Font.system(size: 22, weight: .bold)
        public static let sectionLabel = Font.system(size: 15, weight: .semibold)
        public static let bodyBold = Font.system(size: 17, weight: .bold)
        public static let bodyMedium = Font.system(size: 15, weight: .medium)
        public static let caption = Font.system(size: 13, weight: .medium)
        public static let largeAmount = Font.system(size: 34, weight: .bold)
        public static let metric = Font.system(size: 20, weight: .bold)
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
