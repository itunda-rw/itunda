import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real gap found 2026-08-30 (project_itunda_money_formatting_sweep's own standing
// convention -- comma thousands-separator for every whole-number RWF amount --
// never reached this file). Same per-file shape TransactionHistoryScreen.swift
// already established.
private func formatAmount(_ value: Int) -> String {
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: value)) ?? "0"
}

// Real Coupang Eats-style quick-filter sort chip (itunda Eats redesign, 2026-08-28) --
// extends the existing real RestaurantSortMode system, see
// ShoppingMerchantBrowseService.browse's own doc comment. Toggleable: tapping an
// already-active chip clears the sort back to the default order, matching Android's
// own EatsSortChip toggle behavior.
struct EatsSortChip: View {
    let label: String
    let active: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.caption).bold()
                .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(active ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }
}

// Real "recommended for you" / "popular now" dish rail (itunda Eats redesign,
// 2026-08-28) -- see EatsDishRecommendationService.getDishes' own doc comment.
// sortBy == "popular" ranks by real order count (neighborhood-wide, not a fabricated
// radius popularity); nil ranks by real personal order history (true only when the
// buyer has actually ordered from that restaurant before). web/Android already have
// this; this is the first iOS client. Renders nothing while empty -- no skeleton, since
// this rail sits below the always-present restaurant list.
struct EatsDishRail: View {
    let title: String
    let category: String?
    let sortBy: String?
    let onOpen: (EatsDishDto) -> Void

    @State private var dishes: [EatsDishDto] = []

    var body: some View {
        Group {
            if !dishes.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text(title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(dishes) { dish in
                                Button(action: { onOpen(dish) }) {
                                    VStack(alignment: .leading, spacing: 6) {
                                        RestaurantPhotoThumb(imageUrl: dish.imageUrl, side: 100)
                                        Text(dish.name).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                                        Text(dish.merchantName).font(.caption2).foregroundColor(IDS.Colors.textSecondary).lineLimit(1)
                                        Text("\(formatAmount(Int(dish.price))) RWF").font(.caption2).bold().foregroundColor(IDS.Colors.textPrimary)
                                    }
                                    .frame(width: 110, alignment: .leading)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
        }
        .task(id: category) {
            dishes = (try? await NetworkClient.shared.getEatsDishes(category: category, sortBy: sortBy).dishes) ?? []
        }
    }
}

// Real 당근(Karrot) 반경 타기팅-style nearby ads rail for Eats -- reuses the exact
// endpoint/pattern ShopScreen.swift's own rail already established (same
// NearbyMerchantAdDto), only the merchant tap-through constructs a restaurant-shaped
// stub. bank-mfe/Android already wire this into their own Eats surface; this is the
// first iOS client.
struct EatsNearbyAdsRail: View {
    let onOpen: (ShoppingMerchantDto) -> Void

    @State private var ads: [NearbyMerchantAdDto] = []
    @StateObject private var locationFetcher = SilentLocationFetcher()

    var body: some View {
        Group {
            if !ads.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("📍 Near you").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(ads) { a in
                                Button(action: { onOpen(ShoppingMerchantDto(merchantId: a.ad.merchantId, businessName: a.businessName, category: nil, cashbackRate: "1%")) }) {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(a.ad.title).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                        Text(a.businessName).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                        Text(String(format: "%.1f km away", a.distanceKm)).font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                                    }
                                    .padding(10).frame(width: 150, alignment: .leading)
                                    .background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
            }
        }
        .onAppear { locationFetcher.requestLocation() }
        .onChange(of: locationFetcher.coordinate?.latitude) { _ in
            guard let coordinate = locationFetcher.coordinate else { return }
            Task { ads = (try? await NetworkClient.shared.getNearbyMerchantAds(latitude: coordinate.latitude, longitude: coordinate.longitude))?.ads ?? [] }
        }
    }
}
