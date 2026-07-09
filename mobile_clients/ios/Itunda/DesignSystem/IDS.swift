//
//  IDS.swift
//  Itunda
//
//  Itunda Design System (IDS)
//  Fact-checked: Replicates Toss's "Toss Design System" (TDS) for iOS (SwiftUI/UIKit).
//

import SwiftUI

struct IDS {
    struct Colors {
        static let brand = Color(red: 49/255, green: 130/255, blue: 246/255)
        static let backgroundPrimary = Color(red: 244/255, green: 246/255, blue: 248/255)
        static let backgroundSecondary = Color.white
        static let backgroundTertiary = Color(red: 237/255, green: 242/255, blue: 247/255)
        static let card = Color.white
        static let raisedCard = Color.white
        static let pressed = Color(red: 234/255, green: 242/255, blue: 255/255)
        static let divider = Color(red: 229/255, green: 232/255, blue: 235/255)
        static let textPrimary = Color(red: 25/255, green: 31/255, blue: 40/255)
        static let textSecondary = Color(red: 78/255, green: 89/255, blue: 104/255)
        static let textTertiary = Color(red: 139/255, green: 149/255, blue: 161/255)
        static let textBrand = brand
        static let successTint = Color(red: 232/255, green: 243/255, blue: 255/255)
        static let warningTint = Color(red: 255/255, green: 244/255, blue: 214/255)
        static let dangerTint = Color(red: 255/255, green: 236/255, blue: 235/255)
        static let iconPrimary = Color(red: 44/255, green: 54/255, blue: 67/255)
        static let iconSecondary = Color(red: 107/255, green: 118/255, blue: 132/255)
        static let iconTertiary = Color(red: 221/255, green: 227/255, blue: 234/255)
        static let shadow = Color.black.opacity(0.08)

        // Backward-compatible aliases for older mobile screens.
        static let primaryBlue = brand
        static let background = backgroundPrimary
        static let positiveBackground = successTint
    }
    
    struct Typography {
        static let header = Font.system(size: 30, weight: .bold)
        static let title = Font.system(size: 22, weight: .bold)
        static let sectionLabel = Font.system(size: 15, weight: .semibold)
        static let bodyBold = Font.system(size: 17, weight: .bold)
        static let bodyMedium = Font.system(size: 15, weight: .medium)
        static let caption = Font.system(size: 13, weight: .medium)
        static let largeAmount = Font.system(size: 34, weight: .bold)
        static let metric = Font.system(size: 20, weight: .bold)
    }
    
    struct Layout {
        static let screenHorizontal: CGFloat = 20
        static let screenTop: CGFloat = 18
        static let sectionSpacing: CGFloat = 24
        static let cardPadding: CGFloat = 24
        static let cardGap: CGFloat = 16
        static let rowGap: CGFloat = 14
        static let inlineGap: CGFloat = 12
        static let tightGap: CGFloat = 8
        static let cardCornerRadius: CGFloat = 28
        static let sectionCornerRadius: CGFloat = 26
        static let buttonCornerRadius: CGFloat = 18
        static let pillCornerRadius: CGFloat = 999
        static let iconCornerRadius: CGFloat = 18
        static let tabBarCornerRadius: CGFloat = 28
        static let topBarActionSize: CGFloat = 44
        static let quickActionIconSize: CGFloat = 48
        static let rowIconSize: CGFloat = 44
        static let tabBarIconSize: CGFloat = 26
        static let floatingTabShadowRadius: CGFloat = 14

        // Backward-compatible alias for older mobile screens.
        static let standardPadding: CGFloat = cardPadding
    }
}
