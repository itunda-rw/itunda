import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation

// Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper here and at every
// other IDS.Colors.card site in this file (docs/UI_UX_GUIDELINES.md §10), matching
// Android's identical ShopOrders.kt conversion -- order-history log (this row) kept its
// per-row Divider (docs/DESIGN_REFERENCES.md §274); entity lists (favorites) below did
// not.
let commerceStatusLabel: [String: String] = [
    "PLACED": "Placed",
    "PACKED": "Packed",
    "SHIPPED": "Shipped",
    "DELIVERED": "Delivered",
    "CANCELLED": "Cancelled — refunded",
]

struct CommerceOrderRow<Action: View>: View {
    let order: OrderDto
    @ViewBuilder let action: () -> Action

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(commerceStatusLabel[order.status] ?? order.status).font(.subheadline).bold().foregroundColor(IDS.Colors.brand)
                    Text(order.deliveryAddress).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                Text("\(formatAmount(Int(order.totalAmount))) RWF").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            action()
        }
        .padding(.vertical, 10)
        Divider().overlay(IDS.Colors.divider)
    }
}

extension CommerceOrderRow where Action == EmptyView {
    init(order: OrderDto) {
        self.order = order
        self.action = { EmptyView() }
    }
}

// Real Shop product wishlist view (2026-07-24) -- iOS port of bank-mfe's
// ProductCatalogView wishlist tab, mirroring HoodScreen's own ListingWishlistView
// field-for-field. Closes docs/DESIGN_REFERENCES.md Section 5 recommendation #3.
struct ProductWishlistView: View {
    let onRemoved: () -> Void

    @State private var favorites: [FavoriteProductDto]?
    @State private var error: String?
    @State private var removingId: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 10)
            } else if favorites == nil {
                SkeletonBlock(height: 120)
            } else if favorites!.isEmpty {
                EmptyStateView("No saved products yet -- tap ♡ on any product to save it here.")
                    .foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(favorites!) { f in
                    HStack(spacing: 12) {
                        ProductImageThumb(imageUrl: f.imageUrl, side: 48)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(f.name).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            Text("\(f.businessName) · \(formatAmount(Int(f.price))) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            // Real Naver Shopping price-drop alert (item 227) -- see
                            // FavoriteProductDto's own doc comment.
                            if f.priceDropped {
                                HStack(spacing: 3) {
                                    PriceDropGlyph(size: 11)
                                    Text("Price dropped")
                                }
                                .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                            }
                        }
                        Spacer()
                        Button(action: { Task { await remove(f.productId) } }) {
                            Text(removingId == f.productId ? "Removing…" : "Remove")
                                .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(IDS.Colors.chipBackground).cornerRadius(10)
                        }
                        .disabled(removingId == f.productId)
                    }
                    .padding(.vertical, 10)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyFavoriteProducts()
            favorites = res.favorites
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func remove(_ productId: String) async {
        removingId = productId
        defer { removingId = nil }
        do {
            _ = try await NetworkClient.shared.removeProductFavorite(productId)
            favorites = favorites?.filter { $0.productId != productId }
            onRemoved()
        } catch {
            self.error = "Couldn't remove this item. Check your connection and try again."
        }
    }
}

// Real Coupang 정기배송 (subscribe & save)-style recurring product delivery -- see
// NetworkClient's ProductSubscriptionDto doc comment. bank-mfe already had this;
// this is the first iOS client, mirroring bank-mfe's MyProductSubscriptionsCard.
struct MyProductSubscriptionsView: View {
    @State private var subscriptions: [ProductSubscriptionDto]?
    @State private var error: String?
    @State private var busyId: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 10)
            } else if subscriptions == nil {
                SkeletonBlock(height: 120)
            } else if subscriptions!.isEmpty {
                EmptyStateView("No recurring deliveries yet -- subscribe from any product's detail page.")
                    .foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(subscriptions!) { s in
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Qty \(s.quantity) · every \(s.intervalDays)d").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        if s.status == "CANCELLED", let cancelledAt = s.cancelledAt {
                            Text("Cancelled \(String(cancelledAt.prefix(10)))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        } else {
                            Text("\(s.status) · \(s.deliveryCount) delivered").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        if let reason = s.lastFailureReason, s.status == "ACTIVE" {
                            Text("Last delivery failed: \(reason)").font(.caption).foregroundColor(.red)
                        }
                        if s.status != "CANCELLED" {
                            HStack(spacing: 8) {
                                if s.status == "ACTIVE" {
                                    Button(action: { Task { await skipNext(s.id) } }) {
                                        Text(busyId == s.id ? "…" : "Skip next")
                                            .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                            .padding(.horizontal, 12).padding(.vertical, 8)
                                            .background(IDS.Colors.chipBackground).cornerRadius(10)
                                    }
                                    .disabled(busyId == s.id)
                                }
                                Button(action: { Task { await toggle(s) } }) {
                                    Text(busyId == s.id ? "…" : (s.status == "ACTIVE" ? "Pause" : "Resume"))
                                        .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                        .padding(.horizontal, 12).padding(.vertical, 8)
                                        .background(IDS.Colors.chipBackground).cornerRadius(10)
                                }
                                .disabled(busyId == s.id)
                                Button(action: { Task { await cancel(s.id) } }) {
                                    Text("Cancel")
                                        .font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                                        .padding(.horizontal, 12).padding(.vertical, 8)
                                        .background(IDS.Colors.chipBackground).cornerRadius(10)
                                }
                                .disabled(busyId == s.id)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 10)
                    Divider().overlay(IDS.Colors.divider)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyProductSubscriptions()
            subscriptions = res.subscriptions
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func toggle(_ s: ProductSubscriptionDto) async {
        busyId = s.id
        defer { busyId = nil }
        do {
            if s.status == "ACTIVE" { _ = try await NetworkClient.shared.pauseProductSubscription(s.id) }
            else { _ = try await NetworkClient.shared.resumeProductSubscription(s.id) }
            await load()
        } catch {
            self.error = "Could not update this subscription."
        }
    }

    private func skipNext(_ id: String) async {
        busyId = id
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.skipNextProductSubscriptionDelivery(id)
            await load()
        } catch {
            self.error = "Could not skip this delivery."
        }
    }

    private func cancel(_ id: String) async {
        busyId = id
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.cancelProductSubscription(id)
            await load()
        } catch {
            self.error = "Could not cancel this subscription."
        }
    }
}

// Real "my questions across every product I've ever asked about" -- see
// NetworkClient's getMyProductInquiries doc comment. ProductInquirySection (on a
// single product's detail page) already lets a buyer ask/view that one product's
// Q&A; this is the first place a buyer can see every question they've ever asked,
// across every product, in one list. Read-only from here -- answering is the
// merchant app's job. Real gap found live (uncalled-endpoint sweep, 2026-09-03):
// this endpoint existed with zero caller on iOS or bank-mfe -- only Android had
// this wired, since 2026-08-04 (mirrors that platform's own ShopOrders.kt
// MyProductInquiriesView exactly).
struct MyProductInquiriesView: View {
    @State private var inquiries: [ProductInquiryDto]?
    @State private var error: String?
    // Real pagination-discard fix (2026-09-13, porting web's own fix -- see
    // project_itunda_pagination_discard_sweep memory) -- getMyProductInquiries
    // silently capped this list at the first 20 questions.
    @State private var inquiriesPage = 0
    @State private var inquiriesHasMore = false
    @State private var loadingMoreInquiries = false

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 10)
            } else if inquiries == nil {
                SkeletonBlock(height: 120)
            } else if inquiries!.isEmpty {
                EmptyStateView("No questions asked yet -- ask one from any product's detail page.")
                    .foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(inquiries!) { q in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(q.question).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        if let answer = q.answer, !answer.isEmpty {
                            Text("Answered: \(answer)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        } else {
                            Text("Waiting for an answer…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 10)
                    Divider().overlay(IDS.Colors.divider)
                }
                if inquiriesHasMore {
                    Button(loadingMoreInquiries ? "Loading…" : "Load more") {
                        Task { await loadMoreInquiries() }
                    }
                    .disabled(loadingMoreInquiries)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyProductInquiries()
            inquiries = res.inquiries
            inquiriesPage = res.page
            inquiriesHasMore = res.page + 1 < res.totalPages
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadMoreInquiries() async {
        let nextPage = inquiriesPage + 1
        loadingMoreInquiries = true
        defer { loadingMoreInquiries = false }
        guard let res = try? await NetworkClient.shared.getMyProductInquiries(page: nextPage) else { return }
        inquiries = (inquiries ?? []) + res.inquiries
        inquiriesPage = nextPage
        inquiriesHasMore = res.page + 1 < res.totalPages
    }
}

