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
struct ShopStarRatingRow: View {
    let value: Int
    let onChange: (Int) -> Void

    var body: some View {
        HStack(spacing: 4) {
            ForEach(1...5, id: \.self) { n in
                Button(action: { onChange(n) }) {
                    IDS.Icons.star(size: 24, color: n <= value ? .yellow : IDS.Colors.textTertiary)
                }
                .accessibilityLabel("Rate \(n) star\(n == 1 ? "" : "s")")
            }
        }
    }
}

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
                    Text("\(Int(product.price)) RWF").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                }
                Text("\(Int(originalPrice)) RWF").font(.caption2).foregroundColor(IDS.Colors.textSecondary).strikethrough()
            } else {
                Text("\(Int(product.price)) RWF").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            }
            if let bestTier {
                Text("Buy \(bestTier.minQuantity)+ for \(Int(bestTier.unitPrice)) RWF each")
                    .font(.caption2).bold().foregroundColor(.green)
            }
        }
    }
}

