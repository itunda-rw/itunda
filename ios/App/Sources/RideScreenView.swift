import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

/// Real "my location" via Apple's own CLLocationManager, runtime-permission-gated --
/// same real technique HoodScreen.swift/MapScreenView.swift each already establish
/// their own copy of, reused here for the passenger's one-tap pickup location.
private final class RideLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    @Published var errorMessage: String?
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
    }

    func requestLocation() {
        errorMessage = nil
        let status = manager.authorizationStatus
        if status == .notDetermined {
            manager.requestWhenInUseAuthorization()
        } else if status == .denied || status == .restricted {
            errorMessage = "Location permission was denied."
        } else {
            manager.requestLocation()
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        if manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways {
            manager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        coordinate = locations.last?.coordinate
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        errorMessage = "Could not access your real location right now."
    }
}

// Real Kakao T-style ride-hailing (rw.itunda.rideshare, real since 2026-07-26) -- last
// remaining client platform for this feature (item 110; bank-mfe always had it,
// Android ported it the same day as item 109). Honest v1 scoping: manual dropoff
// address/lat/lng entry, no autocomplete search integration this pass -- pickup uses a
// one-tap "use my location" via CLLocationManager instead.
//
// **Real Kakao T 예약 호출 (scheduled ride booking, item 212)/multi-stop (item 214)/
// driver rating (item 213) added 2026-07-31** -- first iOS client for these three,
// backend/bank-mfe/Android real since the same day. Same honest platform-specific
// scope-down Android's own port just established: no date/time-picker precedent exists
// anywhere in this app either, so scheduling here is "N hours from now" instead of an
// exact real calendar date/time -- the real backend contract (`scheduledFor` as an ISO
// instant) is identical either way.
struct RideScreenView: View {
    var onBack: () -> Void = {}
    @State private var tab = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("Rides").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            Picker("", selection: $tab) {
                Text("Get a ride").tag(0)
                Text("Drive").tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)

            if tab == 0 {
                RidePassengerContent()
            } else {
                RideDriverContent()
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct RideStopInput: Identifiable {
    let id = UUID()
    var address = ""
    var lat = ""
    var lng = ""
}

private struct RidePassengerContent: View {
    @StateObject private var locationFetcher = RideLocationFetcher()
    @State private var pickupAddress = ""
    @State private var dropoffAddress = ""
    @State private var dropoffLat = ""
    @State private var dropoffLng = ""
    @State private var scheduleHours = ""
    @State private var stops: [RideStopInput] = []
    @State private var myTrips: [RideTripDto] = []
    @State private var activeTripStops: [RideTripStopDto] = []
    @State private var requesting = false
    @State private var busyTripId: String?
    @State private var reviewedTripIds: Set<String> = []
    @State private var error: String?
    @State private var pollTask: Task<Void, Never>?

    private var activeTrip: RideTripDto? {
        myTrips.first { $0.status == "REQUESTED" || $0.status == "DRIVER_ASSIGNED" || $0.status == "IN_PROGRESS" }
    }
    private var pastTrips: [RideTripDto] { myTrips.filter { $0.status == "COMPLETED" || $0.status == "CANCELLED" } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                if let active = activeTrip {
                    Text("Your ride").bold().foregroundColor(IDS.Colors.textPrimary)
                    RideTripCard(trip: active, stops: activeTripStops) {
                        VStack(alignment: .leading, spacing: 8) {
                            if let driverId = active.driverId {
                                DriverRatingSection(driverId: driverId)
                            }
                            if active.status != "IN_PROGRESS" {
                                Button(action: { Task { await cancelTrip(active.id) } }) {
                                    Text(busyTripId == active.id ? "Cancelling…" : "Cancel ride").bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                                        .background(Color.red).cornerRadius(10)
                                }
                                .disabled(busyTripId == active.id)
                            }
                        }
                    }
                } else {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Request a ride").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                        HStack(spacing: 8) {
                            IdsTextField("Pickup", text: $pickupAddress)
                            Button(action: { locationFetcher.requestLocation() }) {
                                Text("Use my location").font(.caption).bold()
                                    .padding(.horizontal, 12).padding(.vertical, 14)
                                    .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                            }
                        }
                        IdsTextField("Dropoff address", text: $dropoffAddress)
                        HStack(spacing: 8) {
                            IdsTextField("Dropoff latitude", text: $dropoffLat, keyboardType: .decimalPad)
                            IdsTextField("Dropoff longitude", text: $dropoffLng, keyboardType: .decimalPad)
                        }
                        ForEach($stops) { $stop in
                            VStack(alignment: .leading, spacing: 6) {
                                HStack {
                                    Text("Stop").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                                    Spacer()
                                    Button("Remove") { stops.removeAll { $0.id == stop.id } }
                                        .font(.caption).foregroundColor(.red)
                                }
                                IdsTextField("Address", text: $stop.address)
                                HStack(spacing: 8) {
                                    IdsTextField("Latitude", text: $stop.lat, keyboardType: .decimalPad)
                                    IdsTextField("Longitude", text: $stop.lng, keyboardType: .decimalPad)
                                }
                            }
                            .padding(10).background(Color(.secondarySystemBackground)).cornerRadius(10)
                        }
                        if stops.count < 3 {
                            Button("+ Add a stop") { stops.append(RideStopInput()) }
                                .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                        }
                        IdsTextField("Schedule for later (hours from now, optional)", text: $scheduleHours, keyboardType: .decimalPad)
                        Button(action: { Task { await requestRide() } }) {
                            Text(requesting ? "Requesting…" : (Double(scheduleHours) != nil ? "Schedule ride" : "Request ride"))
                                .bold().foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 14)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(requesting || locationFetcher.coordinate == nil || dropoffAddress.isEmpty)
                    }
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    .onChange(of: locationFetcher.coordinate?.latitude) { _ in
                        if locationFetcher.coordinate != nil { pickupAddress = "Current location" }
                    }
                    if let locError = locationFetcher.errorMessage {
                        Text(locError).font(.caption).foregroundColor(.red)
                    }
                }

                if !pastTrips.isEmpty {
                    Text("Past rides").bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(pastTrips, id: \.id) { trip in
                        RideTripCard(trip: trip) {
                            if trip.status == "COMPLETED", trip.driverId != nil, !reviewedTripIds.contains(trip.id) {
                                RideReviewRow(busy: busyTripId == trip.id) { rating, comment in
                                    await submitReview(trip.id, rating, comment)
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal)
        }
        .task {
            pollTask = Task {
                while !Task.isCancelled {
                    await loadTrips()
                    try? await Task.sleep(nanoseconds: 4_000_000_000)
                }
            }
        }
        .onDisappear { pollTask?.cancel() }
        .onChange(of: activeTrip?.id) { _ in
            Task { await loadActiveTripStops() }
        }
    }

    private func loadTrips() async {
        myTrips = (try? await NetworkClient.shared.getMyRideTrips().trips) ?? myTrips
    }

    private func loadActiveTripStops() async {
        guard let id = activeTrip?.id else { activeTripStops = []; return }
        activeTripStops = (try? await NetworkClient.shared.getRideTripStops(tripId: id).stops) ?? []
    }

    private func requestRide() async {
        guard let coordinate = locationFetcher.coordinate,
              let dLat = Double(dropoffLat), let dLng = Double(dropoffLng), !dropoffAddress.isEmpty else { return }
        requesting = true
        error = nil
        let resolvedStops = stops.compactMap { stop -> RideStopRequestDto? in
            guard !stop.address.isEmpty, let lat = Double(stop.lat), let lng = Double(stop.lng) else { return nil }
            return RideStopRequestDto(address: stop.address, latitude: lat, longitude: lng)
        }
        let scheduledFor: String? = Double(scheduleHours).map { hours in
            ISO8601DateFormatter().string(from: Date().addingTimeInterval(hours * 3600))
        }
        do {
            _ = try await NetworkClient.shared.requestRideTrip(
                pickupAddress: pickupAddress, pickupLatitude: coordinate.latitude, pickupLongitude: coordinate.longitude,
                dropoffAddress: dropoffAddress, dropoffLatitude: dLat, dropoffLongitude: dLng,
                scheduledFor: scheduledFor, stops: resolvedStops.isEmpty ? nil : resolvedStops
            )
            dropoffAddress = ""
            dropoffLat = ""
            dropoffLng = ""
            scheduleHours = ""
            stops = []
            await loadTrips()
        } catch {
            self.error = "Could not request a ride."
        }
        requesting = false
    }

    private func cancelTrip(_ id: String) async {
        busyTripId = id
        do {
            _ = try await NetworkClient.shared.cancelRideTrip(id: id)
            await loadTrips()
        } catch {
            self.error = "Could not cancel this trip."
        }
        busyTripId = nil
    }

    private func submitReview(_ tripId: String, _ rating: Int, _ comment: String) async {
        busyTripId = tripId
        do {
            _ = try await NetworkClient.shared.submitRideReview(tripId: tripId, rating: rating, comment: comment.isEmpty ? nil : comment)
            reviewedTripIds.insert(tripId)
        } catch NetworkError.httpError(let statusCode) where statusCode == 409 {
            reviewedTripIds.insert(tripId)
        } catch {
            self.error = "Could not submit your rating."
        }
        busyTripId = nil
    }
}

// Real Kakao T-style post-trip driver rating (item 213) -- see this file's own doc
// comment. Simple 5-star tap-to-rate row, matching bank-mfe's RideReviewPrompt shape.
private struct RideReviewRow: View {
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

private struct RideDriverContent: View {
    @StateObject private var locationFetcher = RideLocationFetcher()
    @State private var driver: RideDriverDto?
    @State private var driverRating: RideDriverRatingResponse?
    @State private var loaded = false
    @State private var registering = false
    @State private var availableTrips: [RideTripDto] = []
    @State private var myDriverTrips: [RideTripDto] = []
    @State private var activeTripStops: [String: [RideTripStopDto]] = [:]
    @State private var busyTripId: String?
    @State private var error: String?
    @State private var pollTask: Task<Void, Never>?

    private var activeDriverTrips: [RideTripDto] { myDriverTrips.filter { $0.status == "DRIVER_ASSIGNED" || $0.status == "IN_PROGRESS" } }
    private var pastDriverTrips: [RideTripDto] { myDriverTrips.filter { $0.status == "COMPLETED" || $0.status == "CANCELLED" } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                if !loaded {
                    ProgressView().frame(maxWidth: .infinity).padding(40)
                } else if driver == nil {
                    VStack(spacing: 12) {
                        Text("Drive with Itunda").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                        Text("Earn a real fare for every trip you complete, paid straight to your wallet.")
                            .font(.footnote).foregroundColor(IDS.Colors.textSecondary).multilineTextAlignment(.center)
                        Button(action: { Task { await register() } }) {
                            Text(registering ? "Registering…" : "Become a driver").bold().foregroundColor(.white)
                                .padding(.horizontal, 24).padding(.vertical, 14)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(registering)
                    }
                    .frame(maxWidth: .infinity).padding(24).background(Color(.secondarySystemBackground)).cornerRadius(12)
                } else if let current = driver {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(current.available ? "You're online" : "You're offline").bold().foregroundColor(IDS.Colors.textPrimary)
                            Text(current.available ? "Visible for new trip requests" : "Go online to see trip requests")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            if let rating = driverRating, rating.count > 0 {
                                Text("★ \(String(format: "%.1f", rating.average ?? 0)) (\(rating.count) rating\(rating.count == 1 ? "" : "s"))")
                                    .font(.caption).bold().foregroundColor(.yellow)
                            }
                        }
                        Spacer()
                        Button(action: { Task { await toggleAvailable() } }) {
                            Text(current.available ? "Go offline" : "Go online").bold().foregroundColor(.white)
                                .padding(.horizontal, 16).padding(.vertical, 10)
                                .background(current.available ? Color.red : IDS.Colors.brand).cornerRadius(10)
                        }
                    }
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                    if !activeDriverTrips.isEmpty {
                        Text("Active trips").bold().foregroundColor(IDS.Colors.textPrimary)
                        ForEach(activeDriverTrips, id: \.id) { trip in
                            let tripStops = activeTripStops[trip.id] ?? []
                            let nextStop = tripStops.first { $0.arrivedAt == nil }
                            RideTripCard(trip: trip, stops: tripStops) {
                                VStack(spacing: 8) {
                                    if trip.status == "IN_PROGRESS", let nextStop {
                                        Button(action: { Task { await arriveAtStop(trip.id) } }) {
                                            Text(busyTripId == trip.id ? "…" : "Arrived at \(nextStop.address)").bold()
                                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                                .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                                        }
                                        .disabled(busyTripId == trip.id)
                                    }
                                    if trip.status == "DRIVER_ASSIGNED" {
                                        Button(action: { Task { await act(trip.id) { try await NetworkClient.shared.startRideTrip(id: $0).trip } } }) {
                                            Text(busyTripId == trip.id ? "…" : "Start trip").bold().foregroundColor(.white)
                                                .frame(maxWidth: .infinity).padding(.vertical, 12).background(IDS.Colors.brand).cornerRadius(10)
                                        }
                                        .disabled(busyTripId == trip.id)
                                    } else if trip.status == "IN_PROGRESS" {
                                        Button(action: { Task { await act(trip.id) { try await NetworkClient.shared.completeRideTrip(id: $0).trip } } }) {
                                            Text(busyTripId == trip.id ? "…" : "Complete trip").bold().foregroundColor(.white)
                                                .frame(maxWidth: .infinity).padding(.vertical, 12).background(IDS.Colors.brand).cornerRadius(10)
                                        }
                                        .disabled(busyTripId == trip.id)
                                    }
                                }
                            }
                        }
                    }

                    if !availableTrips.isEmpty {
                        Text("Available trips").bold().foregroundColor(IDS.Colors.textPrimary)
                        ForEach(availableTrips, id: \.id) { trip in
                            RideTripCard(trip: trip) {
                                HStack(spacing: 8) {
                                    Button(action: { Task { await act(trip.id) { try await NetworkClient.shared.declineRideTrip(id: $0).trip } } }) {
                                        Text("Decline").bold()
                                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                                            .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                                    }
                                    .disabled(busyTripId == trip.id)
                                    Button(action: { Task { await act(trip.id) { try await NetworkClient.shared.acceptRideTrip(id: $0).trip } } }) {
                                        Text(busyTripId == trip.id ? "…" : "Accept").bold().foregroundColor(.white)
                                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                                            .background(IDS.Colors.brand).cornerRadius(10)
                                    }
                                    .disabled(busyTripId == trip.id)
                                }
                            }
                        }
                    }

                    if !pastDriverTrips.isEmpty {
                        Text("Past trips").bold().foregroundColor(IDS.Colors.textPrimary)
                        ForEach(pastDriverTrips, id: \.id) { trip in RideTripCard(trip: trip) }
                    }
                }
            }
            .padding(.horizontal)
        }
        .task {
            await loadDriver()
            pollTask = Task {
                while !Task.isCancelled {
                    if driver != nil { await loadTrips() }
                    try? await Task.sleep(nanoseconds: 4_000_000_000)
                }
            }
        }
        .onDisappear { pollTask?.cancel() }
        .onChange(of: locationFetcher.coordinate?.latitude) { _ in
            if let coordinate = locationFetcher.coordinate {
                Task {
                    driver = try? await NetworkClient.shared.updateRideDriverLocation(latitude: coordinate.latitude, longitude: coordinate.longitude).driver
                }
            }
        }
    }

    private func loadDriver() async {
        do {
            let d = try await NetworkClient.shared.getMyRideDriverProfile().driver
            driver = d
            driverRating = try? await NetworkClient.shared.getRideDriverRating(driverId: d.id)
        } catch NetworkError.httpError(let statusCode) where statusCode == 404 {
            driver = nil
        } catch {
            // Leave driver state as-is; next poll may recover.
        }
        loaded = true
    }

    private func loadTrips() async {
        availableTrips = (try? await NetworkClient.shared.getAvailableRideTrips().trips) ?? availableTrips
        myDriverTrips = (try? await NetworkClient.shared.getMyRideDriverTrips().trips) ?? myDriverTrips
        for trip in activeDriverTrips {
            if let stops = try? await NetworkClient.shared.getRideTripStops(tripId: trip.id).stops, !stops.isEmpty {
                activeTripStops[trip.id] = stops
            }
        }
    }

    private func register() async {
        registering = true
        do {
            driver = try await NetworkClient.shared.registerAsRideDriver().driver
        } catch {
            self.error = "Could not register as a driver."
        }
        registering = false
    }

    private func toggleAvailable() async {
        guard let current = driver else { return }
        do {
            let updated = try await NetworkClient.shared.setRideDriverAvailability(available: !current.available).driver
            driver = updated
            if updated.available { locationFetcher.requestLocation() }
        } catch {
            self.error = "Could not update your availability."
        }
    }

    private func act(_ tripId: String, _ action: (String) async throws -> RideTripDto) async {
        busyTripId = tripId
        do {
            _ = try await action(tripId)
            await loadTrips()
        } catch {
            self.error = "Could not update this trip."
        }
        busyTripId = nil
    }

    private func arriveAtStop(_ tripId: String) async {
        busyTripId = tripId
        do {
            _ = try await NetworkClient.shared.arriveAtRideStop(tripId: tripId)
            if let stops = try? await NetworkClient.shared.getRideTripStops(tripId: tripId).stops {
                activeTripStops[tripId] = stops
            }
        } catch {
            self.error = "Could not mark this stop arrived."
        }
        busyTripId = nil
    }
}

/// Real "meet your driver" rating + reviews during an active trip (item 233) -- found
/// via the uncalled-endpoint sweep, see NetworkClient.getRideDriverReviews's own doc
/// comment. bank-mfe/Android shipped this first (2026-08-05); this is the iOS port.
/// Honest v1: no driver name/vehicle field exists on the backend, so this shows the
/// driver's real rating + written reviews only, never a fabricated name.
private struct DriverRatingSection: View {
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
                                ForEach(reviews, id: \.id) { review in
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(String(repeating: "⭐", count: review.rating)).font(.caption2).bold()
                                        if let comment = review.comment { Text(comment).font(.caption).foregroundColor(IDS.Colors.textPrimary) }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(8)
                                    .background(IDS.Colors.card)
                                    .cornerRadius(8)
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

private struct RideTripCard<Action: View>: View {
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
                Text("🕒 Scheduled for \(String(scheduledFor.prefix(16)).replacingOccurrences(of: "T", with: " "))")
                    .font(.caption).bold().foregroundColor(IDS.Colors.brand)
            }
            HStack {
                Text(rideTripStatusLabel(trip)).font(.caption).bold().foregroundColor(rideTripStatusColor(trip.status))
                Spacer()
                Text("\(formatMoneyRide(trip.fare)) RWF · \(String(format: "%.1f", trip.distanceKm)) km")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            action()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
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

private func formatMoneyRide(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
