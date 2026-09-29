import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

// Real fix (2026-08-26): split out of RideScreenView.swift once that file grew
// past its file-size-lint baseline. Components shared by/used from both
// RidePassengerContent and RideDriverContent (RideTripCard, driver rating), plus
// the trusted-contacts section / review row used only by the passenger flow --
// same split already done for Android's RideScreen.kt. Flipped from private to
// internal since their callers stay behind.

// Real Uber Safety "Trusted Contacts" (item 161, help.uber.com) -- a persistent, up to
// 5 contact list set up once, distinct from the per-share conversation pick the
// existing "Share trip" flow already offers. Last iOS client for RideController's
// trusted-contacts endpoints; mirrors bank-mfe's TrustedContactsSection/Android's own
// TrustedContactsSection exactly, adapted to this file's SwiftUI/Button conventions.
struct TrustedContactsSection: View {
    let contacts: [RideTrustedContactDto]?
    @Binding var phone: String
    @Binding var name: String
    let adding: Bool
    let onAdd: () -> Void
    let removingContactId: String?
    let onRemove: (String) -> Void
    let error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Trusted contacts").font(.headline).foregroundColor(IDS.Colors.textPrimary)
            Text("Add up to 5 people who can get your live trip status with one tap.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            if let contacts {
                ForEach(contacts, id: \.id) { contact in
                    HStack {
                        Text(contact.contactName).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Button(removingContactId == contact.id ? "…" : "Remove") { onRemove(contact.id) }
                            .font(.caption).foregroundColor(.red)
                            .disabled(removingContactId == contact.id)
                    }
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                }
                if contacts.count < 5 {
                    HStack(spacing: 8) {
                        IdsTextField("Phone number", text: $phone)
                        IdsTextField("Name", text: $name)
                    }
                    Button(action: onAdd) {
                        Text(adding ? "Adding…" : "Add trusted contact").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(phone.trimmingCharacters(in: .whitespaces).isEmpty ? Color(.tertiarySystemBackground) : IDS.Colors.brand)
                            .cornerRadius(10)
                    }
                    .disabled(adding || phone.trimmingCharacters(in: .whitespaces).isEmpty)
                } else {
                    Text("You've reached the limit of 5 trusted contacts.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
            } else {
                Text("Loading…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }
}

// Real Kakao T-style post-trip driver rating (item 213) -- see this file's own doc
// comment. Simple 5-star tap-to-rate row, matching bank-mfe's RideReviewPrompt shape.
struct RideReviewRow: View {
    let busy: Bool
    let onSubmit: (Int, String) async -> Void
    @State private var rating = 0
    @State private var comment = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Rate your driver").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            HStack(spacing: 4) {
                ForEach(1...5, id: \.self) { n in
                    Text("★")
                        .foregroundColor(n <= rating ? .yellow : IDS.Colors.textSecondary)
                        .onTapGesture { rating = n }
                }
            }
            if rating > 0 {
                IdsTextField("Comment (optional)", text: $comment)
                Button(action: { Task { await onSubmit(rating, comment) } }) {
                    Text(busy ? "Submitting…" : "Submit rating").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(busy)
            }
        }
    }
}

/// Real "meet your driver" rating + reviews during an active trip (item 233) -- found
/// via the uncalled-endpoint sweep, see NetworkClient.getRideDriverReviews's own doc
/// comment. bank-mfe/Android shipped this first (2026-08-05); this is the iOS port.
/// Honest v1: no driver name/vehicle field exists on the backend, so this shows the
/// driver's real rating + written reviews only, never a fabricated name.
struct DriverRatingSection: View {
    let driverId: String
    @State private var rating: RideDriverRatingResponse?
    @State private var reviews: [RideTripReviewDto]?
    @State private var expanded = false

    var body: some View {
        Group {
            if let rating, rating.count > 0 {
                VStack(alignment: .leading, spacing: 6) {
                    Button(action: {
                        expanded.toggle()
                        if expanded && reviews == nil {
                            Task {
                                reviews = (try? await NetworkClient.shared.getRideDriverReviews(driverId: driverId).reviews) ?? []
                            }
                        }
                    }) {
                        Text("★ \(String(format: "%.1f", rating.average ?? 0)) (\(rating.count) rating\(rating.count == 1 ? "" : "s")) \(expanded ? "▲" : "▼")")
                            .font(.caption).bold().foregroundColor(Color(red: 1, green: 0.76, blue: 0.03))
                    }
                    if expanded {
                        if let reviews {
                            if reviews.isEmpty {
                                Text("No written reviews yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            } else {
                                // Real fix (2026-08-24, flat-design sweep): dropped the
                                // per-row Card -- a history log of reviews, kept the
                                // per-row Divider convention (docs/DESIGN_REFERENCES.md
                                // §274).
                                ForEach(reviews, id: \.id) { review in
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(String(repeating: "⭐", count: review.rating)).font(.caption2).bold()
                                        if let comment = review.comment { Text(comment).font(.caption).foregroundColor(IDS.Colors.textPrimary) }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.vertical, 6)
                                    Divider().overlay(IDS.Colors.divider)
                                }
                            }
                        } else {
                            Text("Loading reviews…").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                    }
                }
            }
        }
        .task(id: driverId) {
            rating = try? await NetworkClient.shared.getRideDriverRating(driverId: driverId)
        }
    }
}

struct RideTripCard<Action: View>: View {
    let trip: RideTripDto
    var stops: [RideTripStopDto] = []
    let action: () -> Action

    init(trip: RideTripDto, stops: [RideTripStopDto] = [], @ViewBuilder action: @escaping () -> Action = { EmptyView() }) {
        self.trip = trip
        self.stops = stops
        self.action = action
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(trip.pickupAddress).bold().foregroundColor(IDS.Colors.textPrimary)
            ForEach(stops) { stop in
                Text("\(stop.arrivedAt != nil ? "✓" : "→") \(stop.address)")
                    .font(.caption).foregroundColor(stop.arrivedAt != nil ? IDS.Colors.textSecondary : IDS.Colors.textPrimary)
            }
            Text("→ \(trip.dropoffAddress)").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            if let scheduledFor = trip.scheduledFor {
                HStack(spacing: 3) {
                    ClockGlyph(size: 11)
                    Text("Scheduled for \(String(scheduledFor.prefix(16)).replacingOccurrences(of: "T", with: " "))")
                }
                .font(.caption).bold().foregroundColor(IDS.Colors.brand)
            }
            HStack {
                Text(rideTripStatusLabel(trip)).font(.caption).bold().foregroundColor(rideTripStatusColor(trip.status))
                Spacer()
                Text("\(formatMoney(trip.fare)) RWF · \(String(format: "%.1f", trip.distanceKm)) km")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            action()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }
}

// Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix and
// Android's port -- see project_itunda_pagination_discard_sweep memory),
// extracted from RideScreenView/RideDriverContent (RideScreenView crossed 500
// lines for the first time adding pagination state) into a shared section:
// the "past trips" list + "Load more" button, identical for both passenger
// and driver except for the per-trip extra content (review/tip/report vs
// none).
struct PastRideTripsSection: View {
    let trips: [RideTripDto]
    let title: String
    let hasMore: Bool
    let loadingMore: Bool
    let onLoadMore: () -> Void
    var itemContent: ((RideTripDto) -> AnyView)? = nil

    var body: some View {
        if !trips.isEmpty {
            Group {
                Text(title).bold().foregroundColor(IDS.Colors.textPrimary)
                ForEach(trips, id: \.id) { trip in
                    if let itemContent {
                        RideTripCard(trip: trip) { itemContent(trip) }
                    } else {
                        RideTripCard(trip: trip)
                    }
                }
                if hasMore {
                    Button(loadingMore ? "Loading…" : "Load more") { onLoadMore() }
                        .disabled(loadingMore)
                }
            }
        }
    }
}

// Extracted from RideScreenView (2026-09-11, same file-size-lint pass as
// PastRideTripsSection above -- still over 500 lines after that first
// extraction) -- the "Saved places" map-bookmark quick-select row, a
// genuinely self-contained block needing only the bookmark list and a
// selection callback.
struct SavedPlacesQuickSelect: View {
    let bookmarks: [MapBookmarkDto]
    let onSelect: (MapBookmarkDto) -> Void

    var body: some View {
        if !bookmarks.isEmpty {
            Text("Saved places").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(bookmarks) { bookmark in
                        Button(action: { onSelect(bookmark) }) {
                            HStack(spacing: 6) {
                                Circle().fill(colorFromHex(bookmark.color)).frame(width: 8, height: 8)
                                Text(bookmark.displayName).font(.caption).bold().lineLimit(1)
                            }
                            .foregroundColor(IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 10)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                        }
                    }
                }
            }
        }
    }
}

private func rideTripStatusLabel(_ trip: RideTripDto) -> String {
    if trip.status == "REQUESTED", trip.scheduledFor != nil { return "Scheduled" }
    switch trip.status {
    case "REQUESTED": return "Finding a driver…"
    case "DRIVER_ASSIGNED": return "Driver assigned"
    case "IN_PROGRESS": return "In progress"
    case "COMPLETED": return "Completed"
    case "CANCELLED": return "Cancelled"
    default: return trip.status
    }
}

private func rideTripStatusColor(_ status: String) -> Color {
    switch status {
    case "COMPLETED": return .green
    case "CANCELLED": return .red
    default: return IDS.Colors.brand
    }
}


// Real Uber post-trip tipping -- ported from bank-mfe/Android (2026-09-03), see
// RideTripDto.tipAmount's own doc comment. Same real device step-up pattern every
// other money-moving action in this app needs (a tip is a real account-to-account
// transfer, gated by DeviceVerificationFilter same as TransferFlow).
private let rideTipPresets = [500, 1000, 2000]

struct TipDriverPrompt: View {
    let tripId: String
    let onTipped: () -> Void

    @State private var amount: Int?
    @State private var customAmount = ""
    @State private var submitting = false
    @State private var error: String?
    @State private var needsDeviceVerification = false

    var body: some View {
        if needsDeviceVerification {
            DeviceStepUpHost(
                visible: true,
                onDismiss: { needsDeviceVerification = false },
                onVerified: { await submit(nil) }
            )
        } else {
            VStack(alignment: .leading, spacing: 6) {
                Text("Tip your driver").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                HStack(spacing: 6) {
                    ForEach(rideTipPresets, id: \.self) { preset in
                        let selected = amount == preset
                        Text(preset.formatted())
                            .font(.caption).bold().foregroundColor(selected ? .white : IDS.Colors.textSecondary)
                            .frame(maxWidth: .infinity).padding(.vertical, 8)
                            .background(selected ? IDS.Colors.brand : Color.clear).cornerRadius(8)
                            .onTapGesture { amount = preset; customAmount = ""; Task { await submit(preset) } }
                    }
                }
                HStack(spacing: 6) {
                    IdsTextField("Custom amount (RWF)", text: Binding(get: { customAmount }, set: { customAmount = $0; amount = nil }))
                        .keyboardType(.numberPad)
                    Button(action: { Task { await submit(nil) } }) {
                        Text(submitting ? "…" : "Tip").font(.caption).bold().foregroundColor(.white)
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .background((Int(customAmount) ?? 0) > 0 && !submitting ? IDS.Colors.brand : IDS.Colors.textTertiary).cornerRadius(8)
                    }
                    .disabled(submitting || (Int(customAmount) ?? 0) <= 0)
                }
                if let error { Text(error).font(.caption2).foregroundColor(.red) }
            }
            .padding(.top, 8)
        }
    }

    private func submit(_ overrideAmount: Int?) async {
        let finalAmount = overrideAmount ?? amount ?? Int(customAmount)
        guard let finalAmount, finalAmount > 0 else { return }
        needsDeviceVerification = false
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.tipRideDriver(tripId: tripId, amount: Double(finalAmount))
        } catch NetworkError.deviceNotVerified {
            needsDeviceVerification = true
            return
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
            return
        }
        onTipped()
    }
}
