import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real local-business appointment booking (customer side) -- closes the "business
/// profile + real booking" gap independently converged on by Naver Smart Place, Kakao
/// Hair Shop, and Karrot's Business Profile research (docs/DESIGN_REFERENCES.md).
///
/// Moved here from App/Sources/ShopBooking.swift + ShopMerchantDetail.swift (2026-08-25,
/// direct user feedback: "booking... that's features that supposed to be in itunda
/// place not in itunda shopping") -- the original doc comment already named the real
/// sourcing (Naver Smart Place/Karrot Business Profile, both real *local-place*
/// products, never a nationwide online catalog), so a real-time appointment at a
/// physical location belongs in itunda Place, not Shop's "completely online" catalog.
/// The backend (MerchantBookingController/MerchantBookingService, real, already
/// vertical-neutral) needed no change -- only this client-side UI ownership moved.
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
                    IDS.Icons.back(size: 18, relativeTo: .title3).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text("Book \(service.name)").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    Text("\(service.name) · \(service.durationMinutes ?? 0) min · \(formatAmount(Int(service.price))) RWF")
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

// Thin full-screen gate -- keeps MapScreenView.swift's own call site to a couple of
// lines, same "extract instead of grow a baselined file" discipline
// docs/ARCHITECTURE_GUIDELINES.md §2 requires (mirrors Android's identical
// MerchantBookingGate in MapsBooking.kt).
struct MerchantBookingGate: View {
    let merchant: ShoppingMerchantDto?
    let service: MerchantProductDto?
    let onDismiss: () -> Void

    var body: some View {
        if let merchant, let service {
            BookingFlowView(merchant: merchant, service: service, onBack: onDismiss, onBooked: onDismiss)
        }
    }
}

// Real "Book" entry point for a selected place's real bookable services (a real
// durationMinutes set on a MerchantProductDto) -- the itunda Place-side counterpart to
// what used to be Shop's MerchantDetailView "Book" button. Self-sufficient: fetches its
// own data off the merchant id, same pattern MerchantBookingInfoSection below uses.
struct MerchantBookableServicesSection: View {
    let merchantId: String
    let onBook: (MerchantProductDto) -> Void

    @State private var services: [MerchantProductDto]?

    var body: some View {
        Group {
            if let services, !services.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Book an appointment").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    ForEach(services) { service in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(service.name).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                Text("\(service.durationMinutes ?? 0) min · \(formatAmount(Int(service.price))) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                            Spacer()
                            Button(action: { onBook(service) }) {
                                Text("Book").font(.caption).bold().foregroundColor(.white)
                                    .padding(.horizontal, 14).padding(.vertical, 8)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                        }
                        .padding(12).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
                    }
                }
            }
        }
        .task(id: merchantId) {
            do {
                let products = try await NetworkClient.shared.getMerchantProducts(merchantId: merchantId).products
                services = products.filter { $0.active && $0.durationMinutes != nil }
            } catch {
                services = []
            }
        }
    }
}

/// Real pre-booking browsing (item 231) -- see NetworkClient.getMerchantReviews/
/// getCouponsForCustomer's own doc comments. bank-mfe/Android shipped this first.
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
                                    Text(c.discountType == "PERCENT" ? "\(Int(c.discountValue))% off" : "\(formatAmount(Int(c.discountValue))) RWF off")
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

let BOOKING_STATUS_LABEL: [String: String] = [
    "REQUESTED": "Requested", "CONFIRMED": "Confirmed", "DECLINED": "Declined",
    "CANCELLED": "Cancelled", "COMPLETED": "Completed",
]

/// Real customer-side view of merchant bookings requested via BookingFlowView. Cancel
/// is the only customer action here (confirm/decline/complete are owner-side, already
/// real on merchant-mfe/Android's own merchant apps).
struct MyBookingsView: View {
    @State private var bookings: [MerchantBookingDto]?
    @State private var error: String?
    @State private var cancellingId: String?

    var body: some View {
        Group {
            if let bookings, !bookings.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    Text("Bookings").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    ForEach(bookings) { b in
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text(b.serviceName).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                Spacer()
                                Text(BOOKING_STATUS_LABEL[b.status] ?? b.status).font(.caption).bold().foregroundColor(IDS.Colors.brand)
                            }
                            Text("\(b.bookingDate) at \(String(b.startTime.prefix(5)))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            if b.status == "REQUESTED" || b.status == "CONFIRMED" {
                                Button(action: { Task { await cancel(b.id) } }) {
                                    Text(cancellingId == b.id ? "Cancelling…" : "Cancel booking")
                                        .font(.caption).bold().foregroundColor(.white)
                                        .padding(.horizontal, 14).padding(.vertical, 8)
                                        .background(.red).cornerRadius(10)
                                }
                                .disabled(cancellingId == b.id)
                            }
                            if b.status == "COMPLETED" {
                                BookingReviewButton(bookingId: b.id)
                            }
                        }
                        .padding(.vertical, 10).frame(maxWidth: .infinity, alignment: .leading)
                        Divider().overlay(IDS.Colors.divider)
                    }
                }
                .padding(.top, 16)
            }
        }
        .task { await load() }
    }

    private func load() async {
        bookings = (try? await NetworkClient.shared.getMyBookings())?.bookings ?? []
    }

    private func cancel(_ bookingId: String) async {
        cancellingId = bookingId
        error = nil
        do {
            _ = try await NetworkClient.shared.cancelBooking(bookingId)
            await load()
        } catch {
            self.error = "Couldn't cancel this booking."
        }
        cancellingId = nil
    }
}

/// Real customer-side post-appointment review -- see NetworkClient.swift's own doc
/// comment on submitBookingReview. Mirrors ProductReviewRow's exact shape (star rating
/// + optional comment, a real 409 BOOKING_ALREADY_REVIEWED is treated as already-done).
struct BookingReviewButton: View {
    let bookingId: String

    @State private var open = false
    @State private var done = false
    @State private var rating = 0
    @State private var comment = ""
    @State private var submitting = false
    @State private var error: String?

    var body: some View {
        if done {
            Text("Thanks for your review!").font(.caption).foregroundColor(IDS.Colors.textSecondary)
        } else if !open {
            Button(action: { open = true }) {
                Text("Rate this visit").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
        } else {
            VStack(alignment: .leading, spacing: 8) {
                StarRatingRow(value: rating) { rating = $0 }
                TextField("How was it? (optional)", text: $comment)
                    .padding(10).background(IDS.Colors.chipBackground).cornerRadius(10)
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                HStack(spacing: 10) {
                    Button(action: { open = false }) {
                        Text("Cancel").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.chipBackground).cornerRadius(12)
                    }
                    Button(action: { Task { await submit() } }) {
                        Text(submitting ? "Submitting…" : "Submit review").font(.subheadline).bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(submitting ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(submitting)
                }
            }
        }
    }

    private func submit() async {
        guard rating > 0 else {
            error = "Pick a star rating."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.submitBookingReview(
                bookingId, rating: rating, comment: comment.trimmingCharacters(in: .whitespaces).isEmpty ? nil : comment
            )
            done = true
        } catch let NetworkError.httpError(statusCode) {
            if statusCode == 409 {
                done = true
            } else {
                error = "Something went wrong. Please try again."
            }
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}
