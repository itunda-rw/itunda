import SwiftUI
import UIKit

/// Toss-Style Color System -- the primitive/raw brand palette (Blue500, Gray900, ...).
/// Deliberately static, not theme-reactive: see IDS.swift's header comment for the
/// 2026-07-11 reconciliation between this file and IDS.Colors. The *semantic* layer
/// that should change between light/dark is IDS.Colors, matching Android's split
/// between the static `TdsColors` object and the reactive `Tds.colors`.
public struct TdsColors {
    public static let blue500 = Color(hex: 0x3182F6)
    public static let blue600 = Color(hex: 0x1B64DA)
    public static let blue100 = Color(hex: 0xE8F3FF)
    
    public static let gray900 = Color(hex: 0x191F28) // Primary text
    public static let gray800 = Color(hex: 0x333D4B) // Secondary text
    public static let gray700 = Color(hex: 0x4E5968) // Tertiary text
    public static let gray600 = Color(hex: 0x6B7684) // Placeholder
    public static let gray500 = Color(hex: 0x8B95A1)
    public static let gray400 = Color(hex: 0xB0B8C1) // Disabled elements
    public static let gray300 = Color(hex: 0xD1D6DB) // Borders
    public static let gray200 = Color(hex: 0xE5E8EB) // Divider
    public static let gray100 = Color(hex: 0xF2F4F6) // Background (Cards)
    public static let gray50  = Color(hex: 0xF9FAFB) // Background (Screen)

    public static let red500  = Color(hex: 0xF04452) // Destructive/Error
    public static let green500 = Color(hex: 0x04C065) // Success
    public static let white   = Color(hex: 0xFFFFFF)
}

extension Color {
    init(hex: UInt, alpha: Double = 1) {
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
    public static let title1 = IDS.scaledFont(size: 24, weight: .bold, relativeTo: .title1)
    public static let title2 = IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2)
    public static let subtitle1 = IDS.scaledFont(size: 17, weight: .semibold, relativeTo: .subheadline)
    public static let body1 = IDS.scaledFont(size: 15, weight: .regular, relativeTo: .subheadline)
    public static let body2 = IDS.scaledFont(size: 13, weight: .regular, relativeTo: .caption1)
    public static let button = IDS.scaledFont(size: 16, weight: .semibold, relativeTo: .body)
}
