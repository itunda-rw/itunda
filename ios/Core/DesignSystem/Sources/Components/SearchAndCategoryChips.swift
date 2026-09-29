import SwiftUI

/// Real shared browse-header view (2026-07-21) -- extracted from Eats' OrderFoodContent
/// (the only place this pattern previously existed) so Shop's merchant browse can reuse
/// the identical search+chips interaction instead of a second bespoke implementation.
/// Callers own their own debounce/state; this just renders the field + optional chip
/// row. Promoted from App/Sources/ExploreMenuSharedComponents.swift into
/// CoreDesignSystem (2026-09-06, Eats product-completeness pass) when Eats moved into
/// its own Feature module -- same real "FeatureX needs it, an App import isn't
/// possible" reasoning FlatRow/IdsSearchBar's own move already established.
public struct SearchAndCategoryChips: View {
    let searchText: String
    let onSearchChange: (String) -> Void
    let placeholder: String
    let categories: [String]
    let selectedCategory: String?
    let onSelectCategory: (String?) -> Void

    public init(
        searchText: String,
        onSearchChange: @escaping (String) -> Void,
        placeholder: String,
        categories: [String],
        selectedCategory: String?,
        onSelectCategory: @escaping (String?) -> Void
    ) {
        self.searchText = searchText
        self.onSearchChange = onSearchChange
        self.placeholder = placeholder
        self.categories = categories
        self.selectedCategory = selectedCategory
        self.onSelectCategory = onSelectCategory
    }

    public var body: some View {
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
