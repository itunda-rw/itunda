import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
/// rw.itunda.marketplace.VehicleInspectionService's own doc comment. A buyer books and
/// 100%-prepays a real mechanic to inspect a real Marketplace used-car listing before
/// purchase; a mechanic can register, browse incoming bookings, and deliver findings.
/// bank-mfe/Android already have this; this is the first iOS client, mirroring bank-mfe's
/// BUYER/MECHANIC toggle and Android's VehicleInspectionScreen.kt exactly. Same honest
/// platform scope-down as RideScreenView's own scheduled-ride booking: "N hours from now"
/// instead of a real calendar date/time picker.
struct VehicleInspectionScreenView: View {
    var onBack: () -> Void = {}
    @State private var tab = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Vehicle inspection").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            Picker("", selection: $tab) {
                Text("Get a car inspected").tag(0)
                Text("Mechanic").tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)

            if tab == 0 {
                InspectionBuyerContent()
            } else {
                InspectionMechanicContent()
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct InspectionBuyerContent: View {
    @State private var mechanics: [VehicleInspectionMechanicDto] = []
    @State private var myBookings: [VehicleInspectionBookingDto] = []
    @State private var listingId = ""
    @State private var selectedMechanicId = ""
    @State private var fee = ""
    @State private var scheduleHours = ""
    @State private var requesting = false
    @State private var busyBookingId: String?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Book an inspection").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                    Text("Pay a local mechanic to inspect a used car before you buy it — held until they deliver their findings.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    IdsTextField("Listing ID", text: $listingId)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Mechanic").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        ForEach(mechanics, id: \.id) { m in
                            let selected = m.id == selectedMechanicId
                            HStack {
                                Text(m.businessName).font(.subheadline).fontWeight(selected ? .bold : .regular)
                                Spacer()
                            }
                            .padding(10)
                            .background(selected ? IDS.Colors.brand.opacity(0.12) : Color.clear)
                            .cornerRadius(8)
                            .onTapGesture { selectedMechanicId = m.id }
                        }
                        if mechanics.isEmpty {
                            EmptyStateView("No mechanics available right now.")
                        }
                    }
                    IdsTextField("Inspection fee (RWF)", text: $fee, keyboardType: .decimalPad)
                    IdsTextField("Hours from now", text: $scheduleHours, keyboardType: .decimalPad)
                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }
                    Button(action: { Task { await requestInspection() } }) {
                        Text(requesting ? "Booking…" : "Book & pay").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(requesting)
                }
                .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                if myBookings.isEmpty {
                    EmptyStateView("No inspections booked yet — book one to get a real used car checked before you buy.")
                } else {
                    ForEach(myBookings, id: \.id) { b in
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Listing \(b.listingId)").bold().font(.subheadline)
                            Text("\(formatMoneyInspection(b.fee)) RWF · \(b.status)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            if let findings = b.findings {
                                Text(findings).font(.subheadline)
                            }
                            if b.status == "REQUESTED" || b.status == "ACCEPTED" {
                                Button(action: { Task { await cancel(b.id) } }) {
                                    Text(busyBookingId == b.id ? "Cancelling…" : "Cancel").bold().foregroundColor(.white)
                                        .padding(.horizontal, 14).padding(.vertical, 8)
                                        .background(Color.red).cornerRadius(8)
                                }
                                .disabled(busyBookingId == b.id)
                            }
                        }
                        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }
            }
            .padding(.horizontal)
        }
        .task { await load() }
    }

    private func load() async {
        do {
            mechanics = try await NetworkClient.shared.getAvailableInspectionMechanics().mechanics
            myBookings = try await NetworkClient.shared.getMyInspectionBookings().bookings
        } catch {
            self.error = "Couldn't load inspections."
        }
    }

    private func requestInspection() async {
        guard let numericFee = Double(fee), numericFee > 0, let hours = Double(scheduleHours), hours > 0, !listingId.isEmpty, !selectedMechanicId.isEmpty else {
            error = "Fill in the listing id, a mechanic, a valid fee, and hours from now."
            return
        }
        requesting = true
        error = nil
        defer { requesting = false }
        do {
            let scheduledFor = ISO8601DateFormatter().string(from: Date().addingTimeInterval(hours * 3600))
            _ = try await NetworkClient.shared.requestVehicleInspection(listingId: listingId, mechanicId: selectedMechanicId, fee: numericFee, scheduledFor: scheduledFor)
            listingId = ""
            selectedMechanicId = ""
            fee = ""
            scheduleHours = ""
            await load()
        } catch {
            self.error = "Couldn't request this inspection."
        }
    }

    private func cancel(_ bookingId: String) async {
        busyBookingId = bookingId
        defer { busyBookingId = nil }
        do {
            _ = try await NetworkClient.shared.cancelVehicleInspection(bookingId: bookingId)
            await load()
        } catch {
            self.error = "Couldn't cancel this booking."
        }
    }
}

private struct InspectionMechanicContent: View {
    @State private var profile: VehicleInspectionMechanicDto?
    @State private var loaded = false
    @State private var businessName = ""
    @State private var registering = false
    @State private var bookings: [VehicleInspectionBookingDto] = []
    @State private var findingsByBooking: [String: String] = [:]
    @State private var busyBookingId: String?
    @State private var error: String?

    var body: some View {
        Group {
            if !loaded {
                VStack { Spacer(); ProgressView(); Spacer() }
            } else if let profile {
                registeredView(profile)
            } else {
                registerView
            }
        }
        .task { await load() }
    }

    private var registerView: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Become an inspection mechanic").font(.headline).foregroundColor(IDS.Colors.textPrimary)
            Text("Get booked and paid to inspect used cars for real buyers before they purchase.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            IdsTextField("Business name", text: $businessName)
            if let error {
                Text(error).font(.footnote).foregroundColor(.red)
            }
            Button(action: { Task { await register() } }) {
                Text(registering ? "Registering…" : "Register").bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(registering)
            Spacer()
        }
        .padding(16)
    }

    private func registeredView(_ profile: VehicleInspectionMechanicDto) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(profile.businessName).bold()
                        Text(profile.available ? "Visible for new bookings" : "Not accepting bookings")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Spacer()
                    Button(action: { Task { await toggleAvailable() } }) {
                        Text(profile.available ? "Go unavailable" : "Go available").bold().foregroundColor(.white)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .background(profile.available ? Color.red : IDS.Colors.brand).cornerRadius(8)
                    }
                }
                .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                if bookings.isEmpty {
                    EmptyStateView("No bookings yet — they'll show up here once a buyer books an inspection.")
                } else {
                    ForEach(bookings, id: \.id) { b in
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Listing \(b.listingId)").bold().font(.subheadline)
                            Text("\(formatMoneyInspection(b.fee)) RWF · \(b.status)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            if b.status == "REQUESTED" {
                                Button(action: { Task { await accept(b.id) } }) {
                                    Text(busyBookingId == b.id ? "Accepting…" : "Accept").bold().foregroundColor(.white)
                                        .padding(.horizontal, 14).padding(.vertical, 8)
                                        .background(IDS.Colors.brand).cornerRadius(8)
                                }
                                .disabled(busyBookingId == b.id)
                            }
                            if b.status == "ACCEPTED" {
                                IdsTextField("Inspection findings", text: Binding(
                                    get: { findingsByBooking[b.id] ?? "" },
                                    set: { findingsByBooking[b.id] = $0 }
                                ))
                                Button(action: { Task { await complete(b.id) } }) {
                                    Text(busyBookingId == b.id ? "Completing…" : "Mark complete").bold().foregroundColor(.white)
                                        .padding(.horizontal, 14).padding(.vertical, 8)
                                        .background(IDS.Colors.brand).cornerRadius(8)
                                }
                                .disabled(busyBookingId == b.id)
                            }
                        }
                        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }
            }
            .padding(.horizontal)
        }
    }

    private func load() async {
        do {
            let mechanic = try await NetworkClient.shared.getMyInspectionMechanicProfile().mechanic
            profile = mechanic
            if mechanic != nil {
                bookings = (try? await NetworkClient.shared.getMyInspectionMechanicBookings().bookings) ?? []
            }
        } catch {
            self.error = "Couldn't load your mechanic profile."
        }
        loaded = true
    }

    private func register() async {
        guard !businessName.isEmpty else { return }
        registering = true
        error = nil
        defer { registering = false }
        do {
            profile = try await NetworkClient.shared.registerAsInspectionMechanic(businessName: businessName).mechanic
            await load()
        } catch NetworkError.httpError(let statusCode) where statusCode == 409 {
            // MECHANIC_ALREADY_REGISTERED in practice (matches Android's identical
            // VehicleInspectionScreen.kt fix, 2026-08-15) -- resolve forward: load the
            // existing profile instead of a dead-end error.
            await load()
        } catch {
            self.error = "Couldn't register as a mechanic."
        }
    }

    private func toggleAvailable() async {
        guard let current = profile else { return }
        do {
            profile = try await NetworkClient.shared.setInspectionMechanicAvailability(available: !current.available).mechanic
        } catch {
            // Real, non-critical -- an availability toggle failure isn't worth a hard error.
        }
    }

    private func accept(_ bookingId: String) async {
        busyBookingId = bookingId
        defer { busyBookingId = nil }
        do {
            _ = try await NetworkClient.shared.acceptVehicleInspection(bookingId: bookingId)
            await load()
        } catch {
            self.error = "Couldn't accept this booking."
        }
    }

    private func complete(_ bookingId: String) async {
        busyBookingId = bookingId
        defer { busyBookingId = nil }
        do {
            _ = try await NetworkClient.shared.completeVehicleInspection(bookingId: bookingId, findings: findingsByBooking[bookingId])
            await load()
        } catch {
            self.error = "Couldn't complete this booking."
        }
    }
}

private func formatMoneyInspection(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    let formatter = NumberFormatter()
    formatter.numberStyle = .decimal
    formatter.groupingSeparator = ","
    formatter.usesGroupingSeparator = true
    if rounded == rounded.rounded(.down) {
        formatter.maximumFractionDigits = 0
        return formatter.string(from: NSNumber(value: rounded)) ?? String(Int64(rounded))
    }
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.string(from: NSNumber(value: rounded)) ?? String(format: "%.2f", rounded)
}
