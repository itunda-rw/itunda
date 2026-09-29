import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real fix (2026-08-26): split out of BenefitsShopAllScreens.swift once that file
// grew past its file-size-lint baseline. FlatRow/FlatSection/CollapsibleFlatSection/
// IconGridSection/IdsSearchBar moved on to Core/DesignSystem/Sources/Components/
// FlatRow.swift (2026-09-02, Menu Feature-module decomposition) -- FeatureMenu's
// EntireMenuScreen needs them, and a FeatureMenu-to-App import isn't possible. What
// remains here is used only by other App-only screens (Hood/Talk/Shop/Eats), not by
// any Feature module.

struct IdsPlainTopBar: View {
    let title: String
    var body: some View {
        HStack {
            Text(title)
                .font(IDS.scaledFont(size: 28, weight: .bold, relativeTo: .largeTitle))
                .foregroundColor(IDS.Colors.textPrimary)
            Spacer()
            Text("...")
                .font(IDS.scaledFont(size: 24, weight: .regular, relativeTo: .title1))
                .foregroundColor(IDS.Colors.textPrimary)
        }
    }
}

// SearchAndCategoryChips promoted to Core/DesignSystem/Sources/Components/
// SearchAndCategoryChips.swift (2026-09-06, Eats product-completeness pass) -- same
// real "FeatureX needs it, an App import isn't possible" reasoning FlatRow/
// IdsSearchBar's own move already established, now that Eats moved into its own
// Feature module.

struct SmallBlueButton: View {
    let label: String
    var body: some View {
        Text(label)
            .font(IDS.scaledFont(size: 14, weight: .bold, relativeTo: .footnote))
            .foregroundColor(IDS.Colors.brand)
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color(hex: 0x223554))
            .cornerRadius(12)
    }
}
