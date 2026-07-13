import SwiftUI
import UIKit

/// Toss-Style Color System -- the primitive/raw brand palette (Blue500, Gray900, ...).
/// Deliberately static, not theme-reactive: see IDS.swift's header comment for the
/// 2026-07-11 reconciliation between this file and IDS.Colors. The *semantic* layer
/// that should change between light/dark is IDS.Colors, matching Android's split
/// between the static `TdsColors` object and the reactive `Tds.colors`.
public struct TdsColors {
    // GENERATED:BEGIN -- do not hand-edit; regenerate with packages/design-tokens/generate-tokens.js from tokens.json. gray900 is primary text, gray700 secondary, gray500 tertiary/placeholder by established convention (see IDS.Colors for the theme-reactive equivalents).
    public static let gray50 = Color(hex: 0xF9FAFB)
    public static let gray100 = Color(hex: 0xF2F4F6)
    public static let gray200 = Color(hex: 0xE5E8EB)
    public static let gray300 = Color(hex: 0xD1D6DB)
    public static let gray400 = Color(hex: 0xB0B8C1)
    public static let gray500 = Color(hex: 0x8B95A1)
    public static let gray600 = Color(hex: 0x6B7684)
    public static let gray700 = Color(hex: 0x4E5968)
    public static let gray800 = Color(hex: 0x333D4B)
    public static let gray900 = Color(hex: 0x191F28)

    public static let blue50 = Color(hex: 0xE8F3FF)
    public static let blue100 = Color(hex: 0xC9E2FF)
    public static let blue200 = Color(hex: 0x90C2FF)
    public static let blue300 = Color(hex: 0x64A8FF)
    public static let blue400 = Color(hex: 0x4593FC)
    public static let blue500 = Color(hex: 0x3182F6)
    public static let blue600 = Color(hex: 0x2272EB)
    public static let blue700 = Color(hex: 0x1B64DA)
    public static let blue800 = Color(hex: 0x1957C2)
    public static let blue900 = Color(hex: 0x194AA6)

    public static let red50 = Color(hex: 0xFFEEEE)
    public static let red100 = Color(hex: 0xFFD4D6)
    public static let red200 = Color(hex: 0xFEAFB4)
    public static let red300 = Color(hex: 0xFB8890)
    public static let red400 = Color(hex: 0xF66570)
    public static let red500 = Color(hex: 0xF04452)
    public static let red600 = Color(hex: 0xE42939)
    public static let red700 = Color(hex: 0xD22030)
    public static let red800 = Color(hex: 0xBC1B2A)
    public static let red900 = Color(hex: 0xA51926)

    public static let green500 = Color(hex: 0x04C065)
    public static let white = Color(hex: 0xFFFFFF)
    // GENERATED:END

    // Fixed product-icon accent colors (Benefits/Shop/All tab icon badges) -- real
    // Toss brand/product colors, not semantic theme colors, so like the rest of this
    // struct they intentionally stay constant across light/dark. Ported exact-value
    // from android/app/.../ItundaAppScreen.kt's AccentBlue/Teal/Purple/Orange/Red/
    // Pink/Gray (2026-07-11, for the Benefits/Shop/All tab rebuild).
    public static let accentBlue = Color(hex: 0x3182F6)
    public static let accentTeal = Color(hex: 0x14AE85)
    public static let accentPurple = Color(hex: 0x7C5CFC)
    public static let accentOrange = Color(hex: 0xF2A93B)
    public static let accentRed = Color(hex: 0xFF5B5B)
    public static let accentPink = Color(hex: 0xEC5F8C)
    public static let accentGray = Color(hex: 0x6B7684)
}

extension Color {
    // Made public 2026-07-11 (was internal, so unusable outside this module) for the
    // Benefits/Shop/All tab rebuild -- those screens need a few one-off campaign
    // colors (a promo banner purple, a pill pink) that mirror Android's own choice
    // to use local Color(0xFF...) literals for those specific spots rather than
    // adding them to the reusable token set, since they're one-off screen dressing,
    // not colors any other screen reuses.
    public init(hex: UInt, alpha: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 08) & 0xff) / 255,
            blue: Double((hex >> 00) & 0xff) / 255,
            opacity: alpha
        )
    }
}

/// Toss-Style Typography (SwiftUI ViewModifiers)
///
/// Fixed (2026-07-11): every entry here used to be a plain Font.system(size:weight:
/// design:) -- a fixed point size that does not scale with iOS's Dynamic Type
/// accessibility setting. Reuses `IDS.scaledFont` (same module, `Core/DesignSystem/
/// Sources/IDS.swift`) rather than a second copy of the same helper -- see its doc
/// comment for the full reasoning. No call site needed to change.
public struct TdsTypography {
    // Real TDS typography scale (2026-07-13), sourced by directly fetching Toss's own
    // official docs (tossmini-docs.toss.im/tds-mobile/foundation/typography) --
    // ported 1:1 from Android's TdsTypography.kt Typography1-7 addition, see that
    // file's own doc comment for the full reasoning (kept alongside, not replacing,
    // the existing title1/subtitle1/etc. semantic scale below). Line height isn't
    // expressible via IDS.scaledFont's UIFontMetrics-based API the way Android's
    // TextStyle.lineHeight is -- SwiftUI derives line spacing from the Dynamic-Type
    // text style itself, so only the real font sizes are ported directly; each is
    // paired with the closest matching real Apple text style for its role.
    public static let typography1 = IDS.scaledFont(size: 30, weight: .bold, relativeTo: .largeTitle)
    public static let typography2 = IDS.scaledFont(size: 26, weight: .bold, relativeTo: .title1)
    public static let typography3 = IDS.scaledFont(size: 22, weight: .bold, relativeTo: .title2)
    public static let typography4 = IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title3)
    public static let typography5 = IDS.scaledFont(size: 17, weight: .regular, relativeTo: .body)
    public static let typography6 = IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline)
    public static let typography7 = IDS.scaledFont(size: 13, weight: .regular, relativeTo: .caption1)

    public static let title1 = IDS.scaledFont(size: 24, weight: .bold, relativeTo: .title1)
    public static let title2 = IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2)
    public static let subtitle1 = IDS.scaledFont(size: 17, weight: .semibold, relativeTo: .subheadline)
    public static let body1 = IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline)
    public static let body2 = IDS.scaledFont(size: 13, weight: .regular, relativeTo: .caption1)
    public static let button = IDS.scaledFont(size: 16, weight: .semibold, relativeTo: .body)
}
