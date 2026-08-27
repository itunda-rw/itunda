import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation


struct MerchantDetailView: View {
    let merchant: ShoppingMerchantDto
    let products: [MerchantProductDto]?
    @Binding var cart: [String: CommerceCartLine]
    let onBack: () -> Void
    let onViewCart: () -> Void
    let onOpenProduct: (MerchantProductDto) -> Void
    var favoriteProductIds: Set<String> = []
    var favoritingProductId: String?
    var onToggleFavorite: (String) -> Void = { _ in }
    var following: Bool = false
    var followBusy: Bool = false
    var onToggleFollow: () -> Void = {}
    var onContactSeller: () -> Void = {}

    // Real Kakao Pay 정기결제/Toss 빌링키-style recurring billing plans this merchant
    // itself has published -- see MerchantBillingService's own doc comment. bank-mfe/
    // Android already have this; this is the first iOS client.
    @State private var billingPlans: [MerchantBillingPlanDto] = []
    @State private var mySubscriptions: [MerchantBillingSubscriptionDto] = []
    // Real 쿠팡파트너스 (Coupang Partners)-style affiliate link generation (item 229)
    // -- see AffiliateLinkDto's own doc comment. bank-mfe/Android already have this;
    // this is the first iOS client. Referral capture-at-checkout stays bank-mfe-only,
    // a named, honest v1 scope-down (no deep-link precedent exists on this app).
    @State private var sharingProductId: String?

    private func shareProduct(_ productId: String) {
        sharingProductId = productId
        Task {
            defer { sharingProductId = nil }
            do {
                let link = try await NetworkClient.shared.createAffiliateLink(productId: productId)
                let text = "Check this out on itunda! Use code \(link.link.code) — https://itunda.rw/shop?ref=\(link.link.code)"
                let activityVC = UIActivityViewController(activityItems: [text], applicationActivities: nil)
                if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                   let root = scene.windows.first?.rootViewController {
                    var top = root
                    while let presented = top.presentedViewController { top = presented }
                    top.present(activityVC, animated: true)
                }
            } catch {
                // Real, non-critical -- a share-link failure shouldn't block browsing.
            }
        }
    }

    private var totalItems: Int { cart.values.reduce(0) { $0 + $1.quantity } }
    private func qty(_ productId: String) -> Int { cart["\(merchant.merchantId):\(productId)"]?.quantity ?? 0 }
    private func setQty(_ product: MerchantProductDto, _ quantity: Int) {
        let key = "\(merchant.merchantId):\(product.id)"
        if quantity <= 0 { cart.removeValue(forKey: key) }
        else if product.stockQuantity == nil || quantity <= product.stockQuantity! {
            cart[key] = CommerceCartLine(merchantId: merchant.merchantId, businessName: merchant.businessName, product: product, quantity: quantity)
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    IDS.Icons.back(size: 18, relativeTo: .title3).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text(merchant.businessName).font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                ShopMessageSellerButton(action: onContactSeller)
                // Real Naver Smart Store-style "알림받기" follow toggle -- first iOS
                // client for this feature (item 117, found via a content-grep sweep:
                // bank-mfe has it, Android/iOS didn't; Android ported the same day).
                Button(action: onToggleFollow) {
                    Text(following ? "Following" : "Follow")
                        .font(.caption).bold()
                        .foregroundColor(following ? IDS.Colors.textPrimary : .white)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(following ? IDS.Colors.chipBackground : IDS.Colors.brand)
                        .cornerRadius(10)
                }
                .disabled(followBusy)
            }
            .padding(.horizontal, 8)

            ScrollView {
                if !billingPlans.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Subscription plans").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        ForEach(billingPlans) { plan in
                            BillingPlanRow(
                                plan: plan,
                                subscription: mySubscriptions.first { $0.planId == plan.id && $0.status == "ACTIVE" },
                                onChanged: { Task { await loadBilling() } }
                            )
                        }
                    }
                    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper
                    // -- a lone conditional section.
                    .padding(.bottom, 10)
                }
                if let products {
                    if products.isEmpty {
                        EmptyStateView("This store hasn't added products yet — check back soon.")
                    } else {
                        // Real 2-column image-led grid (2026-07-21), replacing the
                        // previous single-column text-only row -- closes
                        // docs/DESIGN_REFERENCES.md Section 5 recommendation #5
                        // (Chloe Youn's Coupang case study: real cards are image-led).
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                            ForEach(products) { product in
                                VStack(alignment: .leading, spacing: 6) {
                                    // Real tap-through to the new product-detail screen
                                    // (2026-07-21) -- see ProductDetailView's own doc
                                    // comment. Only image/name/price is tappable so the
                                    // qty stepper below stays independently tappable.
                                    // Real Shop product wishlist heart (2026-07-24) -- a
                                    // sibling overlay button, not nested inside the
                                    // tap-through Button below (SwiftUI doesn't route
                                    // nested-Button taps reliably), same top-trailing
                                    // placement as Marketplace's ListingCard heart. Closes
                                    // docs/DESIGN_REFERENCES.md Section 5 recommendation #3.
                                    ZStack(alignment: .topTrailing) {
                                        Button(action: { onOpenProduct(product) }) {
                                            VStack(alignment: .leading, spacing: 6) {
                                                ProductImageThumb(imageUrl: product.imageUrl, side: 96)
                                                Text(product.name).font(IDS.Typography.bodyMedium).foregroundColor(IDS.Colors.textPrimary).lineLimit(2)
                                                ProductPriceRow(product: product)
                                            }
                                        }
                                        .buttonStyle(.plain)
                                        VStack(spacing: 6) {
                                            Button(action: { onToggleFavorite(product.id) }) {
                                                Image(systemName: favoriteProductIds.contains(product.id) ? "heart.fill" : "heart")
                                                    .foregroundColor(favoriteProductIds.contains(product.id) ? .red : .white)
                                                    .padding(4)
                                            }
                                            .accessibilityLabel(favoriteProductIds.contains(product.id) ? "Remove from favorites" : "Add to favorites")
                                            .disabled(favoritingProductId == product.id)
                                            Button(action: { shareProduct(product.id) }) {
                                                Image(systemName: "square.and.arrow.up")
                                                    .foregroundColor(.white)
                                                    .padding(4)
                                            }.accessibilityLabel("Share")
                                            .disabled(sharingProductId == product.id)
                                        }
                                    }
                                    HStack(spacing: 6) {
                                        ProductRatingBadge(productId: product.id)
                                        if product.isBestSeller { ShopBestSellerBadge() }
                                    }
                                    ShopDeliveryEtaPill(minutes: merchant.deliveryTimeMinutes)
                                    Text(product.stockQuantity.map { $0 == 0 ? "Out of stock" : "\($0) available" } ?? "Available")
                                        .font(.caption)
                                        .foregroundColor(product.stockQuantity == 0 ? .red : IDS.Colors.textSecondary)
                                    // Bookable-service products (a real durationMinutes
                                    // set) are filtered out before reaching this grid --
                                    // see MerchantDetailView's caller. Every product here
                                    // is a real cart-able good now.
                                    HStack(spacing: 10) {
                                        Spacer()
                                        qtyButton("minus") { setQty(product, qty(product.id) - 1) }
                                        Text("\(qty(product.id))").frame(width: 24).font(.subheadline).bold()
                                        qtyButton("plus") { setQty(product, qty(product.id) + 1) }
                                            .disabled(product.stockQuantity != nil && qty(product.id) >= product.stockQuantity!)
                                        Spacer()
                                    }
                                }
                                .padding(12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(IDS.Colors.card)
                                .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
                            }
                        }
                    }
                } else {
                    ProgressView().padding(.top, 20)
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 12)

            if totalItems > 0 {
                CartFab(totalItems: totalItems, onTap: onViewCart)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await loadBilling() }
    }

    private func loadBilling() async {
        billingPlans = (try? await NetworkClient.shared.getMerchantBillingPlans(merchant.merchantId))?.plans ?? []
        let subs = (try? await NetworkClient.shared.getMyBillingSubscriptions())?.subscriptions ?? []
        mySubscriptions = subs.filter { $0.merchantId == merchant.merchantId }
    }

    private func qtyButton(_ symbol: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(IDS.Colors.chipBackground)
                Image(systemName: symbol).font(.caption).foregroundColor(IDS.Colors.textPrimary)
            }
            .frame(width: 30, height: 30)
        }
        // Every real call site passes either "plus" or "minus" -- see this function's own
        // call sites -- so the label can be derived directly from the symbol instead of
        // threading a separate label param through every caller.
        .accessibilityLabel(symbol == "plus" ? "Increase quantity" : "Decrease quantity")
    }
}
