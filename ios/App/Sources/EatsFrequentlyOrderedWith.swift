import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real "frequently ordered together" cross-sell (itunda Eats redesign, 2026-08-28,
// direct user reference: real Coupang Eats "다른 고객은 함께 주문했어요" rail). See
// backend OrderItemRepository.getFrequentlyOrderedWith's own doc comment: a real,
// derived co-occurrence signal, never a fabricated pairing -- renders nothing at all
// when the real list comes back empty. web/Android already have this; this is the
// first iOS client.
struct EatsFrequentlyOrderedWith: View {
    let productId: String
    let onAdd: (FrequentlyOrderedWithItemDto) -> Void

    @State private var items: [FrequentlyOrderedWithItemDto] = []

    var body: some View {
        Group {
            if !items.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Frequently ordered together").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(items) { item in
                                VStack(alignment: .leading, spacing: 6) {
                                    RestaurantPhotoThumb(imageUrl: item.imageUrl, side: 80)
                                    Text(item.name).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                                    Text("\(Int(item.price)) RWF").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    Button(action: { onAdd(item) }) {
                                        Text(item.stockQuantity == 0 ? "Out of stock" : "+ Add")
                                            .font(.caption2).bold()
                                            .foregroundColor(item.stockQuantity == 0 ? IDS.Colors.textTertiary : IDS.Colors.brand)
                                    }
                                    .disabled(item.stockQuantity == 0)
                                }
                                .frame(width: 100)
                                .padding(10)
                                .background(IDS.Colors.card)
                                .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
                            }
                        }
                    }
                }
                .padding(.top, 8)
            }
        }
        .task(id: productId) {
            items = (try? await NetworkClient.shared.getFrequentlyOrderedWith(productId).products) ?? []
        }
    }
}
