import SwiftUI
import CoreDesignSystem

private enum MerchantTab { case orders, catalog, register, reports, business, dineIn, reviews, coupons, followers, payroll, ads, booking, billing, cashAdvance, visitors, updates, photos, vouchers }

struct MerchantHomeScreen: View {
    let merchant: MerchantDto
    let onLogout: () -> Void

    @State private var tab: MerchantTab = .orders

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading) {
                    Text("Itunda Merchant").font(.title2).bold()
                    HStack(spacing: 6) {
                        Text(merchant.businessName).font(.caption).foregroundColor(.secondary)
                        // Real Coupang badge system (2026-08-05) -- see IdsBadge's own
                        // doc comment. A real, named benefit/verification tier next to
                        // the business name, not buried in a settings screen -- this
                        // field existed on the DTO but was never rendered anywhere in
                        // the iOS merchant app at all before now.
                        if merchant.kybVerified {
                            IdsBadge("✓ Verified", tint: IDS.Colors.brand)
                        }
                    }
                }
                Spacer()
                Button(action: { MerchantKeychainTokenStore.shared.clearSession(); onLogout() }) {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                }.accessibilityLabel("Log out")
            }
            .padding(16)

            Picker("", selection: $tab) {
                // Wrapped in Group (2026-08-01): past 10 items, this project's
                // ViewBuilder overload set needs the split -- see
                // BenefitsShopAllScreens.swift (purely a child-count workaround).
                Group {
                    Text("Orders").tag(MerchantTab.orders)
                    Text("Catalog").tag(MerchantTab.catalog)
                    Text("Register").tag(MerchantTab.register)
                    Text("Reports").tag(MerchantTab.reports)
                    Text("Business").tag(MerchantTab.business)
                    Text("Dine-in").tag(MerchantTab.dineIn)
                    Text("Reviews").tag(MerchantTab.reviews)
                    Text("Coupons").tag(MerchantTab.coupons)
                    Text("Followers").tag(MerchantTab.followers)
                    Text("Payroll").tag(MerchantTab.payroll)
                }
                Text("Ads").tag(MerchantTab.ads)
                Text("Booking").tag(MerchantTab.booking)
                Text("Billing").tag(MerchantTab.billing)
                Text("Cash advance").tag(MerchantTab.cashAdvance)
                Text("Visitors").tag(MerchantTab.visitors)
                // Real gaps found live (uncalled-endpoint sweep, 2026-08-29/30) -- see
                // UpdatesTab.swift/PhotosTab.swift's own doc comments.
                Text("Updates").tag(MerchantTab.updates)
                Text("Photos").tag(MerchantTab.photos)
                Text("Vouchers").tag(MerchantTab.vouchers)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)

            switch tab {
            case .orders: OrdersTab()
            case .catalog: CatalogTab()
            case .register: PosTab()
            case .reports: ReportsTab()
            case .business: BusinessAccountTab()
            case .dineIn: DineInTab(restaurantId: merchant.id)
            case .reviews: ReviewsTab(restaurantId: merchant.id)
            case .coupons: CouponsTab()
            case .followers: FollowersTab()
            case .payroll: PayrollTab()
            case .ads: AdsTab()
            case .booking: BookingTab()
            case .billing: BillingTab()
            case .cashAdvance: VendorCashAdvanceTab(merchantId: merchant.id)
            case .visitors: VisitorAnalyticsTab()
            case .updates: UpdatesTab(merchantId: merchant.id)
            case .photos: PhotosTab()
            case .vouchers: VoucherRedeemTab()
            }
        }
    }
}

/// Real incoming Eats orders (restaurant side) -- previously only ever exposed in
/// the consumer app's own bank-mfe (a real structural gap: a merchant had no way to
/// manage incoming orders except through their buyer account). Restaurant-driven
/// statuses only: PLACED -> ACCEPTED -> PREPARING -> READY_FOR_PICKUP.
private struct OrdersTab: View {
    @State private var orders: [EatsOrderDto]?
    @State private var error: String?

    var body: some View {
        Group {
            if let error {
                Text(error).foregroundColor(.red).padding(16)
            }
            if let orders {
                let active = orders.filter { ["PLACED", "ACCEPTED", "PREPARING", "READY_FOR_PICKUP"].contains($0.status) }
                if active.isEmpty {
                    Spacer()
                    Text("No open orders right now.").foregroundColor(.secondary)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 10) {
                            ForEach(active) { order in
                                OrderCard(order: order, onAdvanced: { Task { await refresh() } })
                            }
                        }
                        .padding(16)
                    }
                }
            } else {
                Spacer()
                ProgressView()
                Spacer()
            }
        }
        .task {
            await refresh()
            while true {
                try? await Task.sleep(nanoseconds: 8_000_000_000)
                await refresh()
            }
        }
    }

    private func refresh() async {
        do {
            orders = try await MerchantNetworkClient.shared.getRestaurantOrders().orders
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct OrderCard: View {
    let order: EatsOrderDto
    let onAdvanced: () -> Void

    @State private var busy = false
    @State private var error: String?

    private func nextAction(for status: String) -> (String, String)? {
        switch status {
        case "PLACED": return ("ACCEPTED", "Accept order")
        case "ACCEPTED": return ("PREPARING", "Start preparing")
        case "PREPARING": return ("READY_FOR_PICKUP", "Mark ready for pickup")
        default: return nil
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                MerchantStatusBadge(status: order.status)
                Spacer()
                Text("\(formattedRWF(order.totalAmount)) RWF").bold()
            }
            Text(order.deliveryAddress).font(.footnote)
            if let notes = order.deliveryNotes, !notes.isEmpty {
                Text("Note: \(notes)").font(.footnote)
            }
            if let error {
                Text(error).foregroundColor(.red).font(.caption)
            }
            // Real Baemin-style 포장주문 (Pickup) terminal edge (item 208) -- a PICKUP
            // order at READY_FOR_PICKUP has no `nextAction` (there's no rider to hand
            // off to), so it previously just sat here forever with no action anywhere
            // to close it out.
            if order.fulfillmentType == "PICKUP" && order.status == "READY_FOR_PICKUP" {
                Button(action: {
                    busy = true
                    Task {
                        do {
                            _ = try await MerchantNetworkClient.shared.completePickupOrder(order.id)
                            onAdvanced()
                        } catch let NetworkError.httpErrorWithMessage(_, message) {
                            self.error = message ?? "Couldn't update this order. Try again."
                        } catch {
                            self.error = "Couldn't update this order. Try again."
                        }
                        busy = false
                    }
                }) {
                    Text("Mark picked up").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(busy)
            } else if let (nextStatus, label) = nextAction(for: order.status) {
                Button(action: {
                    busy = true
                    Task {
                        do {
                            _ = try await MerchantNetworkClient.shared.advanceRestaurantOrderStatus(order.id, status: nextStatus)
                            onAdvanced()
                        } catch let NetworkError.httpErrorWithMessage(_, message) {
                            self.error = message ?? "Couldn't update this order. Try again."
                        } catch {
                            self.error = "Couldn't update this order. Try again."
                        }
                        busy = false
                    }
                }) {
                    Text(label).bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(busy)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}

/// Real written-review list + owner-reply management (item 184/185/186/188/189) -- see
/// EatsReviewService.replyToRestaurantReview's own doc comment. bank-mfe (item 184) and
/// Android (item 185) already have this; this is the first iOS client for the
/// owner-reply side. Its own dedicated tab, unlike OrdersTab (which only ever shows
/// ACTIVE orders -- reviews only exist once an order is DELIVERED). Also folds in
/// Commerce product reviews (item 189, mirroring merchant-mfe's item 187 / Android's
/// item 188): no aggregate "all my products' reviews" backend endpoint exists, so this
/// fans out one real per-product review fetch across the merchant's own catalog.
private struct ReviewsTab: View {
    let restaurantId: String

    @State private var restaurantReviews: [EatsReviewDto]?
    @State private var productReviews: [(productName: String, review: ProductReviewDto)]?
    @State private var error: String?

    var body: some View {
        Group {
            if let error {
                Text(error).foregroundColor(.red).padding(16)
            }
            if let restaurantReviews, let productReviews {
                if restaurantReviews.isEmpty && productReviews.isEmpty {
                    Spacer()
                    // Real copy-voice fix (item 244, round 7): honest about whose gap
                    // this is -- reviews only appear once customers leave them after a
                    // booking or purchase.
                    Text("No reviews yet — reviews will show up here once customers leave them after a booking or purchase.").foregroundColor(.secondary)
                    Spacer()
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 10) {
                            if !restaurantReviews.isEmpty {
                                Text("Restaurant reviews").font(.headline)
                                ForEach(restaurantReviews) { review in
                                    ReviewReplyCard(review: review, onReplied: { Task { await refreshRestaurant() } })
                                }
                            }
                            if !productReviews.isEmpty {
                                Text("Product reviews").font(.headline)
                                ForEach(productReviews, id: \.review.id) { entry in
                                    ProductReviewReplyCard(productName: entry.productName, review: entry.review, onReplied: { Task { await refreshProducts() } })
                                }
                            }
                        }
                        .padding(16)
                    }
                }
            } else {
                Spacer()
                ProgressView()
                Spacer()
            }
        }
        .task {
            await refreshRestaurant()
            await refreshProducts()
        }
    }

    private func refreshRestaurant() async {
        do {
            restaurantReviews = try await MerchantNetworkClient.shared.getRestaurantReviews(restaurantId).reviews
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func refreshProducts() async {
        do {
            let products = try await MerchantNetworkClient.shared.getProductCatalog().products
            var combined: [(productName: String, review: ProductReviewDto)] = []
            for product in products {
                if let reviews = try? await MerchantNetworkClient.shared.getProductReviews(product.id).reviews {
                    combined.append(contentsOf: reviews.map { (product.name, $0) })
                }
            }
            productReviews = combined.sorted { $0.review.createdAt > $1.review.createdAt }
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct ReviewReplyCard: View {
    let review: EatsReviewDto
    let onReplied: () -> Void

    @State private var replying = false
    @State private var reply = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(String(repeating: "★", count: review.restaurantRating) + String(repeating: "☆", count: 5 - review.restaurantRating))
                .foregroundColor(.yellow)
            if let comment = review.restaurantComment, !comment.isEmpty {
                Text(comment).font(.subheadline)
            }
            if let ownerReply = review.ownerReply, !ownerReply.isEmpty {
                Text("Your reply: \(ownerReply)").font(.footnote).foregroundColor(.secondary)
            } else if replying {
                HStack {
                    IdsTextField("Write a reply…", text: $reply)
                    Button(action: { Task { await submit() } }) {
                        Text(submitting ? "…" : "Reply").bold().foregroundColor(.white)
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(submitting || reply.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            } else {
                Button(action: { replying = true }) {
                    Text("Reply").bold().foregroundColor(IDS.Colors.brand)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                }
            }
            if let error {
                Text(error).foregroundColor(.red).font(.caption)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }

    private func submit() async {
        submitting = true
        error = nil
        do {
            _ = try await MerchantNetworkClient.shared.replyToRestaurantReview(review.id, reply: reply.trimmingCharacters(in: .whitespaces))
            replying = false
            onReplied()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not submit your reply."
        } catch {
            self.error = "Could not submit your reply."
        }
        submitting = false
    }
}

private struct ProductReviewReplyCard: View {
    let productName: String
    let review: ProductReviewDto
    let onReplied: () -> Void

    @State private var replying = false
    @State private var reply = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(productName).bold()
            Text(String(repeating: "★", count: review.rating) + String(repeating: "☆", count: 5 - review.rating))
                .foregroundColor(.yellow)
            if let comment = review.comment, !comment.isEmpty {
                Text(comment).font(.subheadline)
            }
            if let ownerReply = review.ownerReply, !ownerReply.isEmpty {
                Text("Your reply: \(ownerReply)").font(.footnote).foregroundColor(.secondary)
            } else if replying {
                HStack {
                    IdsTextField("Write a reply…", text: $reply)
                    Button(action: { Task { await submit() } }) {
                        Text(submitting ? "…" : "Reply").bold().foregroundColor(.white)
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(submitting || reply.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            } else {
                Button(action: { replying = true }) {
                    Text("Reply").bold().foregroundColor(IDS.Colors.brand)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                }
            }
            if let error {
                Text(error).foregroundColor(.red).font(.caption)
            }
        }
        .padding(16)
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }

    private func submit() async {
        submitting = true
        error = nil
        do {
            _ = try await MerchantNetworkClient.shared.replyToProductReview(review.id, reply: reply.trimmingCharacters(in: .whitespaces))
            replying = false
            onReplied()
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not submit your reply."
        } catch {
            self.error = "Could not submit your reply."
        }
        submitting = false
    }
}

// FollowersTab moved to its own file, FollowersTab.swift, 2026-08-30 -- see that
// file's own doc comment for why.

struct MerchantStatusBadge: View {
    let status: String

    private var label: String {
        switch status {
        case "PLACED": return "New order"
        case "ACCEPTED": return "Accepted"
        case "PREPARING": return "Preparing"
        case "READY_FOR_PICKUP": return "Ready for pickup"
        case "RIDER_ASSIGNED": return "Rider on the way"
        case "PICKED_UP": return "Out for delivery"
        case "DELIVERED": return "Delivered"
        case "CANCELLED": return "Cancelled"
        default: return status
        }
    }

    var body: some View {
        Text(label)
            .font(.caption2).bold()
            .padding(.horizontal, 8).padding(.vertical, 2)
            .background(IDS.Colors.brand.opacity(0.15))
            .cornerRadius(8)
    }
}

func formattedRWF(_ amount: Double) -> String {
    let formatter = NumberFormatter()
    formatter.locale = Locale(identifier: "en_US_POSIX")
    formatter.numberStyle = .decimal
    formatter.maximumFractionDigits = 0
    formatter.groupingSeparator = ","
    return formatter.string(from: NSNumber(value: amount)) ?? "\(Int(amount))"
}
