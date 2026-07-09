//
//  IDS.swift
//  Ported from mobile_clients/ios (2026-07-10) -- see ARCHITECTURE.md §3.
//
//  This token set and this module's existing Theme/Components/SduiRenderer
//  (TdsTheme etc.) are two real, independently-built design systems that both
//  exist in this repo; they have not been reconciled into one -- ported as-is
//  rather than silently merged under time pressure. Consolidating them is open
//  work (see ARCHITECTURE.md §5). Mirrors the Android port's identical decision
//  (core/designsystem/ids/IDS.kt).
//
//  NOT build-verified -- see Core/Risk/Sources/ZeroTrust.swift for why.
//

import SwiftUI

public struct IDS {
    public struct Colors {
        public static let brand = Color(red: 49/255, green: 130/255, blue: 246/255)
        public static let backgroundPrimary = Color(red: 244/255, green: 246/255, blue: 248/255)
        public static let backgroundSecondary = Color.white
        public static let backgroundTertiary = Color(red: 237/255, green: 242/255, blue: 247/255)
        public static let card = Color.white
        public static let raisedCard = Color.white
        public static let pressed = Color(red: 234/255, green: 242/255, blue: 255/255)
        public static let divider = Color(red: 229/255, green: 232/255, blue: 235/255)
        public static let textPrimary = Color(red: 25/255, green: 31/255, blue: 40/255)
        public static let textSecondary = Color(red: 78/255, green: 89/255, blue: 104/255)
        public static let textTertiary = Color(red: 139/255, green: 149/255, blue: 161/255)
        public static let textBrand = brand
        public static let successTint = Color(red: 232/255, green: 243/255, blue: 255/255)
        public static let warningTint = Color(red: 255/255, green: 244/255, blue: 214/255)
        public static let dangerTint = Color(red: 255/255, green: 236/255, blue: 235/255)
        public static let iconPrimary = Color(red: 44/255, green: 54/255, blue: 67/255)
        public static let iconSecondary = Color(red: 107/255, green: 118/255, blue: 132/255)
        public static let iconTertiary = Color(red: 221/255, green: 227/255, blue: 234/255)
        public static let shadow = Color.black.opacity(0.08)

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
