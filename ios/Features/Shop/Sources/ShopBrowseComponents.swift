import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation

struct StorePhotoThumb: View {
    let imageUrl: String?
    var side: CGFloat = 44

    var body: some View {
        Group {
            if let imageUrl, let url = URL(string: imageUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: side, height: side)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .background(IDS.Colors.chipBackground)
    }

    private var placeholder: some View {
        ZStack {
            IDS.Colors.chipBackground
            Image(systemName: "storefront.fill").foregroundColor(IDS.Colors.brand)
        }
    }
}

struct CartFab: View {
    let totalItems: Int
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack {
                Image(systemName: "cart.fill")
                Text("View cart (\(totalItems) item\(totalItems == 1 ? "" : "s"))")
            }
            .font(IDS.Typography.bodyBold)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(IDS.Colors.brand)
            .cornerRadius(16)
        }
        .padding(IDS.Layout.screenHorizontal)
    }
}

// Real post-delivery product reviews (2026-07-20), mirroring EatsScreen's own
// RestaurantRatingBadge/ReviewOrderCard pattern -- see ProductReviewService's own doc
// comment for the full backend account. One real review per real delivered line item.
// ShopStarRatingRow is duplicated here rather than shared, matching EatsScreen.swift's own
// `private` (file-scoped) declaration -- each screen file in this codebase is self-contained.
// Real product-image thumbnail (2026-07-21) -- imageUrl is a merchant-supplied external
// URL (see backend MerchantProduct.kt's own doc comment: no upload/storage layer exists
// in this backend, so this is a real "bring your own URL" v1, not a fake pipeline).
// AsyncImage (native SwiftUI, no third-party dependency) handles the nil/broken-URL case
// itself via its placeholder closure -- same fallback icon for "no image set" and "image
// failed to load," both real, valid states.
struct ProductImageThumb: View {
    let imageUrl: String?
    var side: CGFloat = 44

    var body: some View {
        Group {
            if let imageUrl, let url = URL(string: imageUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: side, height: side)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .background(IDS.Colors.chipBackground)
    }

    private var placeholder: some View {
        ZStack {
            IDS.Colors.chipBackground
            Image(systemName: "bag").foregroundColor(IDS.Colors.brand).font(IDS.scaledFont(size: side / 2.5, weight: .regular, relativeTo: .title3))
        }
    }
}

// Real discount-price display (2026-07-21) -- Baymard Institute's own placement
// research (docs/DESIGN_REFERENCES.md Section 5): the discount % must sit immediately
// next to the struck-through original price. discountPercent is always server-computed
// (see backend doc comment), never trusted from the client -- purely a rendering of
// numbers the server already validated.
struct ProductPriceRow: View {
    let product: MerchantProductDto

    // Real bulk/wholesale pricing -- closes the gap named in Baemin's own real
    // 배민상회 B2B supplies marketplace research. Shows the best (highest-quantity)
    // real tier as a hint; the actual price used at checkout is always resolved
    // server-side from the real ordered quantity, never trusted from this display.
    // Android already has this; this is the first iOS client.
    private var bestTier: PriceTierDto? { product.priceTiers?.max { $0.minQuantity < $1.minQuantity } }

    var body: some View {
        VStack(alignment: .leading, spacing: 1) {
            if let originalPrice = product.originalPrice, let discountPercent = product.discountPercent, discountPercent > 0 {
                HStack(spacing: 4) {
                    Text("\(discountPercent)%").font(.subheadline).bold().foregroundColor(.red)
                    Text("\(formatAmount(Int(product.price))) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                }
                Text("\(formatAmount(Int(originalPrice))) RWF").font(.caption2).foregroundColor(IDS.Colors.textSecondary).strikethrough()
            } else {
                Text("\(formatAmount(Int(product.price))) RWF").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            }
            if let bestTier {
                Text("Buy \(bestTier.minQuantity)+ for \(formatAmount(Int(bestTier.unitPrice))) RWF each")
                    .font(.caption2).bold().foregroundColor(.green)
            }
        }
    }
}


// Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row --
// ported from bank-mfe/Android (2026-09-03), see backend ShoppingMissionService's own
// doc comment. Every icon here is a real, backend-tracked once-per-day (or once-ever,
// for the welcome bonus) claim that credits real RWF straight into the real account --
// no fabricated points currency.
struct ShoppingPointsRow: View {
    @State private var missions: [ShoppingMissionDto] = []
    @State private var spinOutcomes: [SpinOutcomeDto] = []
    @State private var busyType: String?
    @State private var feedback: String?

    private func icon(for type: String) -> String {
        switch type {
        case "CHECK_IN": return "arrow.triangle.2.circlepath"
        case "SCROLL": return "hand.draw"
        case "SPIN": return "star.fill"
        case "CAT_FEED": return "pawprint.fill"
        default: return "doc.text"
        }
    }

    var body: some View {
        Group {
            if !missions.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Get points and coupons").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 18) {
                            ForEach(missions, id: \.type) { m in
                                let done = m.type == "WELCOME_BONUS" ? m.claimedEver : m.completedToday
                                Button(action: { Task { await complete(m.type) } }) {
                                    VStack(spacing: 4) {
                                        ZStack {
                                            RoundedRectangle(cornerRadius: 14)
                                                .fill(done ? IDS.Colors.chipBackground : IDS.Colors.brand.opacity(0.15))
                                                .frame(width: 52, height: 52)
                                            if busyType == m.type {
                                                Text("…").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                                            } else {
                                                Image(systemName: icon(for: m.type)).foregroundColor(done ? IDS.Colors.textTertiary : IDS.Colors.brand)
                                            }
                                        }
                                        Text(m.label)
                                            .font(.caption2).foregroundColor(done ? IDS.Colors.textTertiary : IDS.Colors.textPrimary)
                                            .lineLimit(1)
                                        if !done {
                                            // Real, stated odds for SPIN -- shows the real
                                            // min-max range up front rather than a hidden
                                            // mechanic.
                                            if m.type == "SPIN", let minAmount = spinOutcomes.map(\.amount).min(), let maxAmount = spinOutcomes.map(\.amount).max() {
                                                Text("+\(formatAmount(Int(minAmount)))~\(formatAmount(Int(maxAmount)))").font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                                            } else {
                                                Text("+\(formatAmount(Int(m.rewardAmount)))").font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                                            }
                                        }
                                    }
                                    .frame(width: 64)
                                }
                                .disabled(done || busyType != nil)
                            }
                        }
                    }
                    if let feedback {
                        Text(feedback).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        if let res = try? await NetworkClient.shared.getShoppingMissions() {
            missions = res.missions
            spinOutcomes = res.spinOutcomes
        }
    }

    private func complete(_ type: String) async {
        busyType = type
        defer { busyType = nil }
        do {
            let res = try await NetworkClient.shared.completeShoppingMission(type: type)
            feedback = "+\(formatAmount(Int(res.amountEarned))) RWF"
            await load()
        } catch {
            feedback = "Could not complete this mission."
        }
    }
}

// Extracted out of ShopScreen.swift's own browseBody (2026-09-07, Shop/Commerce
// product-completeness pass) once that file crossed the file-size-lint 500-line
// guideline once the Feature-module extraction landed. Same real "self-contained,
// mostly-presentational rail with an onOpenMerchant closure" convention
// ShopDealsCarousels.swift's own ShopDealsCarousel/ShopTimeDealsCarousel already
// established.

// Real "recently viewed products" rail (2026-08-23) -- see
// ShopRecentlyViewedStore.swift's own doc comment. Only shown on the unfiltered
// landing state.
struct ShopRecentlyViewedRail: View {
    let products: [RecentlyViewedProduct]
    let onOpenMerchant: (ShoppingMerchantDto) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("🕒 Recently viewed").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(products) { rv in
                        Button(action: { onOpenMerchant(ShoppingMerchantDto(merchantId: rv.merchantId, businessName: rv.businessName, category: nil, cashbackRate: "1%")) }) {
                            VStack(alignment: .leading, spacing: 6) {
                                ProductImageThumb(imageUrl: rv.imageUrl, side: 96)
                                Text(rv.name).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                                if let discountPercent = rv.discountPercent, discountPercent > 0 {
                                    Text("\(discountPercent)% off").font(.caption2).bold().foregroundColor(.red)
                                }
                                Text("\(formatAmount(Int(rv.price))) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

// Real 당근(Karrot) 반경 타기팅-style nearby ads rail -- see lib/shopping.ts's own
// NearbyMerchantAd doc comment. Only shown on the unfiltered landing state.
struct ShopNearbyAdsRail: View {
    let ads: [NearbyMerchantAdDto]
    let onOpenMerchant: (ShoppingMerchantDto) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("📍 Near you").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(ads) { a in
                        Button(action: { onOpenMerchant(ShoppingMerchantDto(merchantId: a.ad.merchantId, businessName: a.businessName, category: nil, cashbackRate: "1%")) }) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(a.ad.title).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                Text(a.businessName).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                if let description = a.ad.description, !description.isEmpty {
                                    Text(description).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                }
                                Text(String(format: "%.1f km away", a.distanceKm)).font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                            }
                            .padding(10).frame(width: 160, alignment: .leading)
                            .background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }
}

// Real cross-merchant product search (item 191) -- see NetworkClient.searchProducts's
// own doc comment. Owns its own search invocation (searchShopProducts, in
// ShopAsyncHandlers.swift) rather than taking an onSearch closure, since the
// search/result/loading state triplet is only ever read/written together.
struct ShopProductSearchSection: View {
    @Binding var query: String
    @Binding var results: [ProductSearchResultDto]?
    @Binding var searching: Bool
    let onOpenMerchant: (ShoppingMerchantDto) -> Void

    var body: some View {
        Group {
            HStack(spacing: 8) {
                TextField("Search products across every merchant", text: $query)
                    .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                Button(action: { Task { await search() } }) {
                    Text(searching ? "…" : "Search").bold().foregroundColor(.white)
                        .padding(.horizontal, 16).padding(.vertical, 14)
                        .background(searching || query.trimmingCharacters(in: .whitespaces).isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand)
                        .cornerRadius(10)
                }
                .disabled(searching || query.trimmingCharacters(in: .whitespaces).isEmpty)
                if results != nil {
                    Button(action: { results = nil; query = "" }) {
                        Text("Clear").bold().foregroundColor(IDS.Colors.textPrimary)
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .background(IDS.Colors.textTertiary).cornerRadius(10)
                    }
                }
            }

            if let results {
                if results.isEmpty {
                    Text("No products matched \"\(query)\".").foregroundColor(IDS.Colors.textSecondary)
                } else {
                    ForEach(results) { r in
                        Button(action: { onOpenMerchant(ShoppingMerchantDto(merchantId: r.merchantId, businessName: r.merchantName, category: nil, cashbackRate: "1%")) }) {
                            HStack(spacing: 12) {
                                ProductImageThumb(imageUrl: r.imageUrl, side: 48)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(r.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                    Text(r.merchantName).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    Text(r.stockQuantity.map { $0 == 0 ? "Out of stock" : "\($0) available" } ?? "Available").font(.caption).foregroundColor(r.stockQuantity == 0 ? .red : IDS.Colors.textSecondary)
                                    if r.isBestSeller { ShopBestSellerBadge() }
                                }
                                Spacer()
                                Text("\(formatAmount(Int(r.price))) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            }
                            .padding(.vertical, 10)
                        }
                    }
                }
            }
        }
    }

    private func search() async {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        searching = true
        results = await searchShopProducts(q)
        searching = false
    }
}
