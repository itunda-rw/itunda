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

// Real fix (2026-08-26): split out of ShopScreen.swift once that file grew past its
// file-size-lint baseline. Both carousels are real, self-contained, mostly-
// presentational rails only shown on the unfiltered Shop landing state -- extracted
// with an onOpenMerchant closure instead of calling back into CommerceShopContent
// directly.

// Real "Deals" rail (2026-07-25) -- only shown on the unfiltered landing state, same
// "merchandising above the raw list, hidden once the user starts filtering"
// discipline a real Coupang/Naver home surface follows. Tapping a deal jumps
// straight to that real merchant via the same minimal-ShoppingMerchantDto shortcut
// the product search results use.
struct ShopDealsCarousel: View {
    let deals: [DealProductDto]
    let onOpenMerchant: (ShoppingMerchantDto) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                FlameGlyph(size: 17)
                Text("Deals")
            }
            .font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(deals) { d in
                        VStack(alignment: .leading, spacing: 6) {
                            ProductImageThumb(imageUrl: d.imageUrl, side: 96)
                            Text(d.name).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                            if let discountPercent = d.discountPercent, discountPercent > 0 {
                                Text("\(discountPercent)% off").font(.caption2).bold().foregroundColor(.red)
                            }
                            HStack(alignment: .lastTextBaseline, spacing: 4) {
                                Text("\(formatAmount(Int(d.price))) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                // Real strikethrough original price (2026-08-25, matches the
                                // Toss Shopping reference) -- same real originalPrice field the
                                // discount badge above already derives from.
                                if let originalPrice = d.originalPrice, originalPrice > d.price {
                                    Text("\(formatAmount(Int(originalPrice))) RWF").font(.caption2).foregroundColor(IDS.Colors.textTertiary).strikethrough()
                                }
                            }
                            // rating/reviewCount (2026-08-25) -- real batched ProductReview
                            // data, same real "no review yet -> no stars" honesty as this
                            // app's Android client.
                            if let rating = d.rating, let reviewCount = d.reviewCount, reviewCount > 0 {
                                HStack(spacing: 2) {
                                    Image(systemName: "star.fill").font(.system(size: 9)).foregroundColor(Color(red: 0.96, green: 0.65, blue: 0.14))
                                    Text(String(format: "%.1f (%d)", rating, reviewCount)).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                }
                            }
                            if d.isBestSeller { ShopBestSellerBadge() }
                            Text(d.stockQuantity.map { $0 == 0 ? "Out of stock" : "\($0) available" } ?? "Available")
                                .font(.caption2).foregroundColor(d.stockQuantity == 0 ? .red : IDS.Colors.textSecondary)
                        }
                        .frame(width: 120, alignment: .leading)
                        .padding(10)
                        .background(IDS.Colors.card)
                        .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
                        .onTapGesture {
                            onOpenMerchant(ShoppingMerchantDto(merchantId: d.merchantId, businessName: d.merchantName, category: nil, cashbackRate: "1%"))
                        }
                    }
                }
            }
        }
    }
}

// Real Coupang 타임특가 (Time Deal, item 226) -- see TimeDealDto's own doc comment.
// Tapping a deal jumps straight to that real merchant, same minimal-
// ShoppingMerchantDto shortcut the Deals rail above uses.
struct ShopTimeDealsCarousel: View {
    let timeDeals: [TimeDealViewDto]
    let onOpenMerchant: (ShoppingMerchantDto) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("⏰ Time Deals").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(timeDeals) { v in
                        VStack(alignment: .leading, spacing: 6) {
                            ProductImageThumb(imageUrl: v.productImageUrl, side: 96)
                            Text(v.productName).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                            Text("\(formatAmount(Int(v.deal.dealPrice))) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            // Real Coupang badge system (2026-08-05) -- see IdsBadge's own doc
                            // comment. Matches Android ShopScreen.kt's own identical StatusBadge
                            // treatment (this was plain Text on iOS until now).
                            // Real live HH:MM:SS countdown (2026-08-25, direct Toss Shopping
                            // reference screenshot) -- TimelineView ticks this to the second off
                            // the same real v.deal.endsAt, no manual Timer/@State plumbing needed.
                            TimelineView(.periodic(from: .now, by: 1)) { _ in
                                Text("⏰ \(formatTimeDealCountdownHms(v.deal.endsAt))").font(.caption2).bold().foregroundColor(IDS.Colors.danger)
                            }
                            IdsBadge("\(v.deal.remainingQuantity) left", tint: IDS.Colors.danger)
                        }
                        .frame(width: 120, alignment: .leading)
                        .padding(10)
                        .background(IDS.Colors.card)
                        .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
                        .onTapGesture {
                            onOpenMerchant(ShoppingMerchantDto(merchantId: v.deal.merchantId, businessName: v.businessName, category: nil, cashbackRate: "1%"))
                        }
                    }
                }
            }
        }
    }
}
