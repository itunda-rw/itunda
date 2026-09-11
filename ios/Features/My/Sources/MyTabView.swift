//
//  MyTabView.swift
//  Real Naver-style "My" personal hub (2026-07-22) -- at the user's direct request:
//  "My should be like Naver style My since we have shopping and eats and other products
//  where users need to easily get track of their orders, reservation, favorites." Every
//  number/row here is a real fetched count or preview, not decoration -- the same "no
//  fabricated numbers" discipline this app already follows elsewhere.
//
//  Trimmed down (2026-07-24) to ONLY this unique content -- its old "Quick links" and
//  "My account" sections are deleted, since both now fully duplicate rows already in
//  EntireMenuScreen's own catalog (now FeatureMenu). Mirrors Android's trimmed MyTab
//  exactly. This is now its own primary tab (ItundaTab.You, 2026-08-10, see
//  ContentView.swift's own doc comment) -- not reached via EntireMenuScreen's
//  profile icon anymore.
//
//  Moved here from App/Sources/BenefitsShopAllScreens.swift (2026-09-02, My
//  Feature-module decomposition, matching Android's own already-real
//  :features:my:impl) -- was originally rebuilt 2026-07-11 alongside
//  BenefitsScreen/DiscoverScreen/EntireMenuScreen (both retired or moved
//  separately; see FeatureMenu's own doc comment for that history).
//

import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork

public struct MyTabView: View {
    var onBack: () -> Void = {}
    var onSwitchToShop: () -> Void = {}
    var onSwitchToEats: () -> Void = {}
    var onSwitchToMarketplace: () -> Void = {}
    var onSwitchToJobs: () -> Void = {}
    var onSwitchToProperty: () -> Void = {}
    var onUpdatePin: (_ currentCredential: String, _ newPin: String) async -> String? = { _, _ in nil }

    @State private var shopOrders: [OrderDto] = []
    @State private var eatsOrders: [EatsOrderDto] = []
    @State private var favoriteListingsCount = 0
    @State private var favoriteJobPostsCount = 0
    @State private var favoritePropertyListingsCount = 0
    @State private var favoriteRestaurantsCount = 0
    @State private var myListingsCount = 0
    @State private var myJobPostsCount = 0
    @State private var myPropertyListingsCount = 0
    // Real 쿠팡파트너스 (Coupang Partners)-style affiliate earnings read-back (item 229)
    // -- link creation itself happens inline on the Shop product card's Share icon;
    // this is purely the read-back, mirroring bank-mfe's own AffiliateEarningsCard and
    // Android's own port.
    @State private var affiliateLinks: [AffiliateLinkDto] = []
    @State private var affiliateCommissions: [AffiliateCommissionDto] = []
    @State private var myScamReports: [ScamReportDto] = []
    @State private var myBookingReviews: [MerchantBookingReviewDto] = []
    // Real published third-party mini-app catalog (Partners product-completeness
    // pass, 2026-09-07) -- see PartnerMiniAppCatalogCard.swift's own doc comment.
    @State private var miniApps: [PartnerMiniAppDto] = []
    // Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix
    // -- see project_itunda_pagination_discard_sweep memory).
    @State private var miniAppsPage = 0
    @State private var miniAppsHasMore = false
    @State private var loadingMoreMiniApps = false

    public init(
        onBack: @escaping () -> Void = {},
        onSwitchToShop: @escaping () -> Void = {},
        onSwitchToEats: @escaping () -> Void = {},
        onSwitchToMarketplace: @escaping () -> Void = {},
        onSwitchToJobs: @escaping () -> Void = {},
        onSwitchToProperty: @escaping () -> Void = {},
        onUpdatePin: @escaping (_ currentCredential: String, _ newPin: String) async -> String? = { _, _ in nil }
    ) {
        self.onBack = onBack
        self.onSwitchToShop = onSwitchToShop
        self.onSwitchToEats = onSwitchToEats
        self.onSwitchToMarketplace = onSwitchToMarketplace
        self.onSwitchToJobs = onSwitchToJobs
        self.onSwitchToProperty = onSwitchToProperty
        self.onUpdatePin = onUpdatePin
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.sectionSpacing) {
                HStack {
                    Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                    Spacer()
                    Text("My").font(IDS.scaledFont(size: 20, weight: .bold, relativeTo: .title2)).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Color.clear.frame(width: 20)
                }
                ProfilePhotoCard()
                // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
                // PinUpgradeCard.swift's own doc comment. Own file, not inline here,
                // matching this session's own file-size-lint discipline for this
                // already-large file.
                PinUpgradeCard(onUpdatePin: onUpdatePin)
                VerificationCard()
                if !affiliateLinks.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Partner earnings").font(IDS.scaledFont(size: 15, weight: .bold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
                        Text("Earn 3% on any purchase made through a product link you've shared.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        HStack {
                            Text("Links shared").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(affiliateLinks.count)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                        HStack {
                            Text("Total clicks").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(affiliateLinks.reduce(0) { $0 + $1.clickCount })").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                        HStack {
                            Text("Total earned").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Spacer()
                            Text("\(Int(affiliateCommissions.reduce(0) { $0 + $1.commissionAmount })) RWF").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        }
                    }
                    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper --
                    // matches "My orders" below (already flat) and the identical Android/
                    // web Partner-earnings conversion (docs/UI_UX_GUIDELINES.md §10).
                    .padding(.vertical, 10).frame(maxWidth: .infinity, alignment: .leading)
                }
                // Real order tracking -- Naver Pay/Shopping's own "My" tab leads with
                // recent orders across every product, not a settings list. Tapping
                // switches to that product's own tab where the full order-history view
                // already lives.
                if !shopOrders.isEmpty || !eatsOrders.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        Text("My orders").font(IDS.scaledFont(size: 19, weight: .bold, relativeTo: .title2)).foregroundColor(IDS.Colors.textPrimary)
                        ForEach(shopOrders.prefix(3)) { order in
                            orderRow(label: "Shop order", status: order.status, amount: order.totalAmount, action: onSwitchToShop)
                        }
                        ForEach(eatsOrders.prefix(3)) { order in
                            orderRow(label: "Eats order", status: order.status, amount: order.totalAmount, action: onSwitchToEats)
                        }
                    }
                }
                // Real favorites/wishlist tracking across every product with one --
                // counts are real (GET .../favorites on each module); tapping switches
                // directly to that real destination's own dedicated wishlist view.
                // Each of Marketplace/Jobs/Property is its own flat destination now
                // (2026-08-10, Hood's segmented Picker retired), so this is a real,
                // direct deep link, not "one more tap" into a shared sub-view.
                FlatSection(title: "My favorites", rows: [
                    FlatRow(title: "Marketplace wishlist", trailing: "\(favoriteListingsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToMarketplace),
                    FlatRow(title: "Jobs wishlist", trailing: "\(favoriteJobPostsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToJobs),
                    FlatRow(title: "Property wishlist", trailing: "\(favoritePropertyListingsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToProperty),
                    FlatRow(title: "Restaurant favorites", trailing: "\(favoriteRestaurantsCount)", glyph: { AnyView(WishlistHeart(favorited: true, size: 28)) }, action: onSwitchToEats),
                ])
                // Real "my own posts" tracking (Marketplace/Jobs/Property listings I
                // created) -- same Naver-style "track your own activity" pattern.
                FlatSection(title: "My listings", rows: [
                    FlatRow(title: "Marketplace", trailing: "\(myListingsCount)", glyph: { AnyView(PlaceGlyph(category: "MARKET", size: 28)) }, action: onSwitchToMarketplace),
                    FlatRow(title: "Jobs posted", trailing: "\(myJobPostsCount)", glyph: { AnyView(BriefcaseGlyph(size: 28)) }, action: onSwitchToJobs),
                    FlatRow(title: "Property listed", trailing: "\(myPropertyListingsCount)", glyph: { AnyView(TravelHouse(size: 28)) }, action: onSwitchToProperty),
                ])
                // Real "My scam reports" history -- see MyScamReportsSection.swift's own
                // doc comment for the full account of this parity gap.
                MyBookingReviewsSection(reviews: myBookingReviews)
                MyScamReportsSection(reports: myScamReports)
                if !miniApps.isEmpty {
                    PartnerMiniAppCatalogCard(
                        miniApps: miniApps,
                        hasMore: miniAppsHasMore,
                        loadingMore: loadingMoreMiniApps,
                        onLoadMore: { Task { await loadMoreMiniApps() } }
                    )
                }
                // "My account" (My assets/Get a loan/Credit score/etc) deliberately
                // dropped here (2026-07-24) -- every one of those rows already lives in
                // EntireMenuScreen's own "Financial services" section now that All is
                // the primary bottom tab; keeping a second copy here would just be stale
                // duplication. Mirrors Android's identical MyTab cleanup.
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            // Each fetch independent and best-effort -- one product's API hiccup must
            // never blank the rest of this real personal-activity summary.
            if let res = try? await NetworkClient.shared.getMyOrders() { shopOrders = res.orders }
            if let res = try? await NetworkClient.shared.getMyEatsOrders() { eatsOrders = res.orders }
            if let res = try? await NetworkClient.shared.getMyFavoriteListings() { favoriteListingsCount = res.favorites.count }
            if let res = try? await NetworkClient.shared.getMyFavoriteJobPosts() { favoriteJobPostsCount = res.favorites.count }
            if let res = try? await NetworkClient.shared.getMyFavoritePropertyListings() { favoritePropertyListingsCount = res.favorites.count }
            if let res = try? await NetworkClient.shared.getMyFavoriteRestaurants() { favoriteRestaurantsCount = res.favorites.count }
            // Real accuracy fix (2026-09-09, same pass as getMyListings's
            // pagination fix): this badge previously showed page 1's item
            // count (capped at 20), not the real total, for any user with
            // more than 20 real listings.
            if let res = try? await NetworkClient.shared.getMyListings() { myListingsCount = res.totalElements }
            // Real accuracy fix (2026-09-09, same pass as getMyJobPosts's
            // pagination fix): this badge previously showed page 1's item
            // count (capped at 20), not the real total, for any user with
            // more than 20 real job posts.
            if let res = try? await NetworkClient.shared.getMyJobPosts() { myJobPostsCount = res.totalElements }
            // Real accuracy fix (2026-09-09, same pass as
            // getMyPropertyListings's pagination fix): this badge previously
            // showed page 1's item count (capped at 20), not the real total,
            // for any user with more than 20 real property listings.
            if let res = try? await NetworkClient.shared.getMyPropertyListings() { myPropertyListingsCount = res.totalElements }
            if let res = try? await NetworkClient.shared.getMyAffiliateLinks() { affiliateLinks = res.links }
            if let res = try? await NetworkClient.shared.getMyAffiliateCommissions() { affiliateCommissions = res.commissions }
            if let res = try? await NetworkClient.shared.getMyScamReports() { myScamReports = res.reports }
            if let res = try? await NetworkClient.shared.getMyBookingReviews() { myBookingReviews = res.reviews }
            if let res = try? await NetworkClient.shared.getMiniAppCatalog(page: 0) {
                miniApps = res.miniApps
                miniAppsHasMore = res.page + 1 < res.totalPages
            }
        }
    }

    private func loadMoreMiniApps() async {
        let nextPage = miniAppsPage + 1
        loadingMoreMiniApps = true
        defer { loadingMoreMiniApps = false }
        guard let res = try? await NetworkClient.shared.getMiniAppCatalog(page: nextPage) else { return }
        miniApps += res.miniApps
        miniAppsPage = nextPage
        miniAppsHasMore = res.page + 1 < res.totalPages
    }

    @ViewBuilder
    private func orderRow(label: String, status: String, amount: Double, action: @escaping () -> Void) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(label).font(IDS.scaledFont(size: 15, weight: .semibold, relativeTo: .subheadline)).foregroundColor(IDS.Colors.textPrimary)
                Text(status).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            Spacer()
            Text("\(formatAmount(Int(amount))) RWF").foregroundColor(IDS.Colors.textPrimary)
        }
        .contentShape(Rectangle())
        .onTapGesture(perform: action)
        .padding(.vertical, 6)
    }
}

