import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

// Extracted out of RideScreenView.swift (2026-09-06, Rideshare product-completeness
// pass) once that file crossed the file-size-lint 500-line guideline -- a real,
// cohesive driver-side content group, same precedent this codebase's other
// file-size-lint extractions already establish.
struct RideDriverContent: View {
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
    // Real Uber "Verify Your Ride" PIN -- what the driver has typed in for each real
    // active trip, keyed by trip id.
    @State private var startPinInputs: [String: String] = [:]
    // Real Uber "Destination Filter" + earnings report (uncalled-endpoint sweep
    // follow-up, item 247) -- both real, fully-built backend endpoints found with
    // zero client anywhere on any platform before bank-mfe's own 2026-08-21 port.
    // Manual lat/lng entry, matching this same screen's existing `dropoffLat`/
    // `dropoffLng` fields on `RidePassengerContent` exactly -- no real
    // geocoding-search component exists on iOS for rides either.
    @State private var destinationLat = ""
    @State private var destinationLng = ""
    @State private var destinationBusy = false
    @State private var earnings: [RideDailyEarnings]?
    // Real gap found live (2026-08-31, market-readiness audit) -- see backend
    // RideDriverService.kt's own doc comment. An honest, self-declared informational
    // text field, not a real license-verification gate this backend has no path to
    // check.
    @State private var licenseNumberInput = ""

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
                        Text("Earn a real fare for every trip you complete, paid straight to your account.")
                            .font(.footnote).foregroundColor(IDS.Colors.textSecondary).multilineTextAlignment(.center)
                        IdsTextField("Driver's license number", text: $licenseNumberInput)
                        Button(action: { Task { await register() } }) {
                            Text(registering ? "Registering…" : "Become a driver").bold().foregroundColor(.white)
                                .padding(.horizontal, 24).padding(.vertical, 14)
                                .background(licenseNumberInput.trimmingCharacters(in: .whitespaces).isEmpty ? IDS.Colors.divider : IDS.Colors.brand)
                                .cornerRadius(10)
                        }
                        .disabled(registering || licenseNumberInput.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    .frame(maxWidth: .infinity).padding(24).background(IDS.Colors.card).cornerRadius(12)
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
                    .padding(16).background(IDS.Colors.card).cornerRadius(12)

                    // Real Uber "Destination Filter" -- see RideDriverDto.destinationLatitude's
                    // own doc comment.
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Heading somewhere?").bold().foregroundColor(IDS.Colors.textPrimary)
                        if current.destinationLatitude != nil {
                            HStack {
                                Text("Only offered trips heading your way.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                Spacer()
                                Button(action: { Task { await clearDestination() } }) {
                                    Text(destinationBusy ? "…" : "Clear").bold().foregroundColor(IDS.Colors.textPrimary)
                                        .padding(.horizontal, 14).padding(.vertical, 8)
                                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                }
                                .disabled(destinationBusy)
                            }
                        } else {
                            Text("Set a destination and you'll only be offered trips heading that direction.")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            HStack(spacing: 8) {
                                TextField("Destination latitude", text: $destinationLat)
                                    .keyboardType(.decimalPad).padding(10).background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                TextField("Destination longitude", text: $destinationLng)
                                    .keyboardType(.decimalPad).padding(10).background(Color(.tertiarySystemBackground)).cornerRadius(8)
                            }
                            Button(action: { Task { await setDestination() } }) {
                                Text(destinationBusy ? "Setting…" : "Set destination").bold().foregroundColor(IDS.Colors.textPrimary)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Double(destinationLat) != nil && Double(destinationLng) != nil ? IDS.Colors.brand.opacity(0.5) : Color(.tertiarySystemBackground)).cornerRadius(10)
                            }
                            .disabled(destinationBusy || Double(destinationLat) == nil || Double(destinationLng) == nil)
                        }
                    }
                    .padding(16).background(IDS.Colors.card).cornerRadius(12)

                    // Real Uber Driver-style earnings report -- see NetworkClient's own
                    // getMyRideEarnings doc comment. Hidden entirely for a fresh driver
                    // with zero completed trips rather than showing an empty/zero state.
                    if let weekEarnings = earnings, !weekEarnings.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            Text("This week").bold().foregroundColor(IDS.Colors.textPrimary)
                            HStack {
                                VStack(alignment: .leading) {
                                    Text("Trips").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    Text("\(weekEarnings.reduce(0) { $0 + $1.tripCount })").bold().font(.title3).foregroundColor(IDS.Colors.textPrimary)
                                }
                                Spacer()
                                VStack(alignment: .leading) {
                                    Text("Gross fare").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    Text("\(Int(weekEarnings.reduce(0) { $0 + $1.grossFare })) RWF").bold().font(.title3).foregroundColor(IDS.Colors.textPrimary)
                                }
                                Spacer()
                                VStack(alignment: .leading) {
                                    Text("Net earnings").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    Text("\(Int(weekEarnings.reduce(0) { $0 + $1.netEarnings })) RWF").bold().font(.title3).foregroundColor(.green)
                                }
                            }
                        }
                        .padding(16).background(IDS.Colors.card).cornerRadius(12)
                    }

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
                                        let pin = startPinInputs[trip.id] ?? ""
                                        TextField("Ask passenger for their 4-digit PIN", text: Binding(
                                            get: { startPinInputs[trip.id] ?? "" },
                                            set: { startPinInputs[trip.id] = String($0.filter(\.isNumber).prefix(4)) }
                                        ))
                                        .keyboardType(.numberPad)
                                        .multilineTextAlignment(.center)
                                        .padding(10)
                                        .background(Color(.tertiarySystemBackground)).cornerRadius(8)
                                        Button(action: { Task { await act(trip.id) { try await NetworkClient.shared.startRideTrip(id: $0, pin: pin).trip } } }) {
                                            Text(busyTripId == trip.id ? "…" : "Start trip").bold().foregroundColor(.white)
                                                .frame(maxWidth: .infinity).padding(.vertical, 12).background(pin.count == 4 ? IDS.Colors.brand : Color.gray).cornerRadius(10)
                                        }
                                        .disabled(busyTripId == trip.id || pin.count != 4)
                                        // Real driver-side cancel-after-acceptance (Rideshare product-
                                        // completeness pass, 2026-09-06) -- see
                                        // RideTripService.driverCancelTrip's own doc comment.
                                        Button(action: { Task { await act(trip.id) { try await NetworkClient.shared.driverCancelRideTrip(id: $0).trip } } }) {
                                            Text(busyTripId == trip.id ? "…" : "Cancel trip").bold().foregroundColor(.white)
                                                .frame(maxWidth: .infinity).padding(.vertical, 12).background(Color.red).cornerRadius(10)
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
            await loadEarnings()
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
            driver = try await NetworkClient.shared.registerAsRideDriver(licenseNumber: licenseNumberInput).driver
        } catch NetworkError.httpErrorWithMessage(let statusCode, _) where statusCode == 409 {
            // Real gap found live (Toss-style error-handling audit, 2026-08-30): same
            // register-once shape as Eats' own RIDER_ALREADY_REGISTERED, apparently
            // missed when that one was fixed -- a double-tap or a second device
            // registering first isn't really a failure. 409 is unambiguous for this
            // specific call (the only other real error, ACCOUNT_NOT_FOUND, is 404).
            await loadDriver()
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

    private func loadEarnings() async {
        earnings = try? await NetworkClient.shared.getMyRideEarnings().days
    }

    private func setDestination() async {
        guard let lat = Double(destinationLat), let lng = Double(destinationLng) else { return }
        destinationBusy = true
        do {
            driver = try await NetworkClient.shared.setRideDriverDestination(latitude: lat, longitude: lng).driver
        } catch {
            self.error = "Could not set your destination."
        }
        destinationBusy = false
    }

    private func clearDestination() async {
        destinationBusy = true
        do {
            driver = try await NetworkClient.shared.clearRideDriverDestination().driver
            destinationLat = ""
            destinationLng = ""
        } catch {
            self.error = "Could not clear your destination."
        }
        destinationBusy = false
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
