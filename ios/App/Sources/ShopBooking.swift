import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork
import CoreLocation


/// Real Kakao Pay 정기결제/Toss 빌링키-style subscribe/cancel -- subscribing charges the
/// first cycle immediately (real "인증 + 첫결제"), same as
/// MerchantBillingService.subscribe's own doc comment. One real active subscription per
/// plan; cancelling stops future charges but doesn't refund the current cycle already
/// paid for. bank-mfe/Android already have this; this is the first iOS client.
struct BillingPlanRow: View {
    let plan: MerchantBillingPlanDto
    let subscription: MerchantBillingSubscriptionDto?
    let onChanged: () -> Void

    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(plan.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("\(Int(plan.amount)) RWF every \(plan.intervalDays) days").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    if let description = plan.description, !description.isEmpty {
                        Text(description).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    }
                }
                Spacer()
                if let subscription {
                    Button(action: { Task { await cancel(subscription.id) } }) {
                        Text(busy ? "…" : "Cancel").bold().font(.caption)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                    }
                    .disabled(busy)
                } else {
                    Button(action: { Task { await subscribe() } }) {
                        Text(busy ? "…" : "Subscribe").bold().font(.caption).foregroundColor(.white)
                            .padding(.horizontal, 12).padding(.vertical, 8)
                            .background(IDS.Colors.brand).cornerRadius(8)
                    }
                    .disabled(busy)
                }
            }
            if let subscription {
                Text(subscription.status == "ACTIVE" ? "Next charge \(String(subscription.nextChargeAt.prefix(10)))" : "Cancelled")
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            }
            if let error {
                Text(error).font(.caption2).foregroundColor(.red)
            }
        }
        .padding(12).background(Color(.secondarySystemBackground)).cornerRadius(10)
    }

    private func subscribe() async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.subscribeToBillingPlan(plan.id)
            onChanged()
        } catch {
            self.error = "Could not subscribe to this plan."
        }
    }

    private func cancel(_ subscriptionId: String) async {
        busy = true
        error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.cancelBillingSubscription(subscriptionId)
            onChanged()
        } catch {
            self.error = "Could not cancel this subscription."
        }
    }
}

/// Real product-detail screen (2026-07-21), mirroring Android's
/// ProductDetailScreen in features/shop/impl/ShopScreen.kt: full-size image,
/// name, price row, rating badge, description, quantity stepper, and an
/// add/update-cart action -- reached by tapping a product card in
/// MerchantDetailView's grid (see that grid's own doc comment).
// Real Coupang 정기배송 (subscribe & save) -- see NetworkClient's ProductSubscriptionDto
// doc comment. A minimal delivery-address prompt via .alert rather than a full address
// form, matching bank-mfe's own compact-card scope (fixed qty=1, every 30d).
struct SubscribeAndSaveButton: View {
    let merchantId: String
    let productId: String

    @State private var showAlert = false
    @State private var address = ""
    @State private var busy = false
    @State private var done = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("✓ Subscribed -- delivered every 30 days").font(.caption).foregroundColor(IDS.Colors.brand)
        } else {
            VStack(alignment: .leading, spacing: 4) {
                Button(action: { showAlert = true }) {
                    Text("Subscribe & save (every 30 days)").font(.caption).bold().foregroundColor(IDS.Colors.brand)
                }
                if let error { Text(error).font(.caption2).foregroundColor(.red) }
            }
            .alert("Subscribe & save", isPresented: $showAlert) {
                TextField("Delivery address", text: $address)
                Button("Subscribe") { Task { await subscribe() } }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Delivered every 30 days. Cancel anytime.")
            }
        }
    }

    private func subscribe() async {
        guard !address.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a delivery address."
            return
        }
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.subscribeToProduct(merchantId: merchantId, productId: productId, quantity: 1, intervalDays: 30, deliveryAddress: address.trimmingCharacters(in: .whitespaces))
            done = true
        } catch {
            self.error = "Could not set up this subscription."
        }
    }
}

// Real local-business appointment booking (customer side) -- closes the "business
// profile + real booking" gap independently converged on by Naver Smart Place, Kakao
// Hair Shop, and Karrot's Business Profile research (docs/DESIGN_REFERENCES.md). Date
// picker is a plain next-14-days strip (no calendar widget); slots come straight from
// the real backend-computed availability (MerchantBookingService.getAvailableSlots),
// never client-guessed. merchant-mfe/Android already have this; this is the first
// iOS client.
struct BookingFlowView: View {
    let merchant: ShoppingMerchantDto
    let service: MerchantProductDto
    let onBack: () -> Void
    let onBooked: () -> Void

    @State private var selectedDate = Date()
    @State private var slots: [BookingSlotDto]?
    @State private var selectedSlot: BookingSlotDto?
    @State private var notes = ""
    @State private var submitting = false
    @State private var error: String?
    @State private var booked = false

    private var dateFormatter: DateFormatter {
        let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd"; return f
    }
    private var next14Days: [Date] {
        (0..<14).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: Calendar.current.startOfDay(for: Date())) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").font(IDS.scaledFont(size: 18, weight: .medium, relativeTo: .title3)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text("Book \(service.name)").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("\(service.name) · \(service.durationMinutes ?? 0) min · \(Int(service.price)) RWF")
                        .font(.footnote).foregroundColor(IDS.Colors.textSecondary)

                    MerchantBookingInfoSection(merchantId: merchant.merchantId)

                    Text("Choose a date").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(next14Days, id: \.self) { date in
                                let selected = Calendar.current.isDate(date, inSameDayAs: selectedDate)
                                VStack {
                                    Text(date.formatted(.dateTime.weekday(.abbreviated))).font(.caption2)
                                    Text(date.formatted(.dateTime.day())).font(.subheadline).bold()
                                }
                                .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(selected ? IDS.Colors.brand : IDS.Colors.card)
                                .cornerRadius(10)
                                .idsCardBorder(cornerRadius: 10)
                                .onTapGesture { selectedDate = date; selectedSlot = nil }
                            }
                        }
                    }

                    Text("Choose a time").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    if let slots {
                        if slots.isEmpty {
                            EmptyStateView("No open times on this date — try another day.")
                        } else {
                            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                                ForEach(slots, id: \.startTime) { slot in
                                    let selected = slot == selectedSlot
                                    Text(String(slot.startTime.prefix(5)))
                                        .font(.subheadline).bold()
                                        .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                                        .background(selected ? IDS.Colors.brand : IDS.Colors.card)
                                        .cornerRadius(8)
                                        .idsCardBorder(cornerRadius: 8)
                                        .onTapGesture { selectedSlot = slot }
                                }
                            }
                        }
                    } else {
                        ProgressView()
                    }

                    TextField("Notes (optional)", text: $notes)
                        .padding(12).background(IDS.Colors.card).cornerRadius(10).idsCardBorder(cornerRadius: 10)

                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }

                    Button(action: { Task { await book() } }) {
                        Text(submitting ? "Requesting…" : "Request booking")
                            .font(IDS.Typography.bodyBold).foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 16)
                            .background(submitting || selectedSlot == nil ? IDS.Colors.textTertiary : IDS.Colors.brand)
                            .cornerRadius(16)
                    }
                    .disabled(submitting || selectedSlot == nil)
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 12)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await loadSlots() }
        .onChange(of: selectedDate) { _ in Task { await loadSlots() } }
        .alert("Booking requested", isPresented: $booked) {
            Button("Done", action: onBooked)
        } message: {
            Text("\(service.name) on \(dateFormatter.string(from: selectedDate)) at \(selectedSlot.map { String($0.startTime.prefix(5)) } ?? "") -- \(merchant.businessName) will confirm shortly.")
        }
    }

    private func loadSlots() async {
        selectedSlot = nil
        slots = nil
        do {
            slots = try await NetworkClient.shared.getBookingSlots(merchantId: merchant.merchantId, serviceId: service.id, date: dateFormatter.string(from: selectedDate)).slots
        } catch {
            self.error = "Couldn't load available times."
            slots = []
        }
    }

    private func book() async {
        guard let slot = selectedSlot else { return }
        submitting = true
        error = nil
        do {
            let res = try await NetworkClient.shared.createBooking(CreateBookingRequest(
                merchantId: merchant.merchantId, serviceId: service.id,
                date: dateFormatter.string(from: selectedDate), startTime: slot.startTime,
                notes: notes.trimmingCharacters(in: .whitespaces).isEmpty ? nil : notes.trimmingCharacters(in: .whitespaces)
            ))
            if res.success { booked = true }
        } catch {
            self.error = "Could not request this booking."
        }
        submitting = false
    }
}

/// Real pre-booking browsing (item 231) -- found via a defined-but-uncalled-endpoint
/// sweep, see NetworkClient.getMerchantReviews/getCouponsForCustomer's own doc
/// comments. bank-mfe/Android shipped this first (2026-08-05); this is the iOS port.
struct MerchantBookingInfoSection: View {
    let merchantId: String
    @State private var reviews: [MerchantBookingReviewDto]?
    @State private var rating: MerchantBookingRatingDto?
    @State private var coupons: [MerchantCouponPreviewDto]?

    var body: some View {
        Group {
            if !(coupons ?? []).isEmpty || !(reviews ?? []).isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    if let coupons, !coupons.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Coupons for you").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            ForEach(coupons, id: \.id) { c in
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(c.title).font(.subheadline).bold()
                                        if let d = c.description { Text(d).font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                                    }
                                    Spacer()
                                    Text(c.discountType == "PERCENT" ? "\(Int(c.discountValue))% off" : "\(Int(c.discountValue)) RWF off")
                                        .font(.subheadline).bold().foregroundColor(IDS.Colors.brand)
                                }
                                .padding(12)
                                .background(IDS.Colors.brand.opacity(0.08))
                                .cornerRadius(10)
                            }
                        }
                    }
                    if let reviews, !reviews.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Reviews" + (rating?.average.map { " · ⭐ \(String(format: "%.1f", $0)) (\(rating?.count ?? 0))" } ?? ""))
                                .font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            // Real fix (2026-08-24, flat-design sweep): dropped the per-row
                            // Card -- history log of reviews, kept the per-row Divider
                            // convention (docs/DESIGN_REFERENCES.md §274).
                            ForEach(reviews.prefix(3), id: \.id) { r in
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(String(repeating: "⭐", count: r.rating)) · \(r.serviceName)").font(.caption).bold()
                                    if let c = r.comment { Text(c).font(.caption).foregroundColor(IDS.Colors.textSecondary) }
                                    if let reply = r.ownerReply { Text("↳ \(reply)").font(.caption2).foregroundColor(IDS.Colors.textTertiary) }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, 10)
                                Divider().overlay(IDS.Colors.divider)
                            }
                        }
                    }
                }
            }
        }
        .task(id: merchantId) {
            do {
                let res = try await NetworkClient.shared.getMerchantReviews(merchantId: merchantId)
                reviews = res.reviews
                rating = res.rating
            } catch {
                reviews = []
            }
            do {
                coupons = try await NetworkClient.shared.getCouponsForCustomer(merchantId: merchantId).coupons
            } catch {
                coupons = []
            }
        }
    }
}

