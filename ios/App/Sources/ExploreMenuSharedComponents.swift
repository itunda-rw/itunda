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

// Real shared browse-header view (2026-07-21) -- extracted from Eats'
// OrderFoodContent (the only place this pattern previously existed) so Shop's
// merchant browse can reuse the identical search+chips interaction instead of a
// second bespoke implementation. Callers own their own debounce/state; this just
// renders the field + optional chip row.
struct SearchAndCategoryChips: View {
    let searchText: String
    let onSearchChange: (String) -> Void
    let placeholder: String
    let categories: [String]
    let selectedCategory: String?
    let onSelectCategory: (String?) -> Void

    var body: some View {
        VStack(spacing: IDS.Layout.cardGap) {
            TextField(placeholder, text: Binding(get: { searchText }, set: onSearchChange))
                .padding(12)
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)

            if !categories.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(([nil] as [String?]) + categories.map { Optional($0) }, id: \.self) { c in
                            Button(action: { onSelectCategory(c) }) {
                                Text(c ?? "All")
                                    .font(.caption).bold()
                                    .foregroundColor(selectedCategory == c ? .white : IDS.Colors.textSecondary)
                                    .padding(.horizontal, 14).padding(.vertical, 6)
                                    .background(selectedCategory == c ? IDS.Colors.brand : IDS.Colors.chipBackground)
                                    .cornerRadius(16)
                            }
                        }
                    }
                }
            }
        }
    }
}

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
