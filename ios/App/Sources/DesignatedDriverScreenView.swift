import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

/// Real "my location" via Apple's own CLLocationManager -- same technique
/// RideScreenView.swift's own RideLocationFetcher already establishes, a separate
/// private copy per this codebase's established per-file convention.
private final class DesignatedDriverLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
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

/// Real Kakao T 대리운전 (designated driver, item 221) -- a professional driver comes
/// to the customer's location and drives the CUSTOMER'S OWN CAR home for them, distinct
/// from RideScreenView.swift's ride-hailing (driver uses their own vehicle). bank-mfe/
/// Android already have this; this is the first iOS client, mirroring RideScreenView's
/// Get-a-ride/Drive toggle exactly. Same honest v1 scope-down: manual dropoff address/
/// lat/lng entry, one-tap "use my location" for pickup via CLLocationManager.
struct DesignatedDriverScreenView: View {
    var onBack: () -> Void = {}
    @State private var tab = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Designated driver").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            Picker("", selection: $tab) {
                Text("Get a driver").tag(0)
                Text("Drive").tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)

            if tab == 0 {
                DesignatedDriverRequestContent()
            } else {
                DesignatedDriverDriveContent()
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct DesignatedDriverRequestContent: View {
    @StateObject private var locationFetcher = DesignatedDriverLocationFetcher()
    @State private var pickupAddress = ""
    @State private var dropoffAddress = ""
    @State private var dropoffLat = ""
    @State private var dropoffLng = ""
    @State private var vehicleMake = ""
    @State private var vehicleModel = ""
    @State private var vehiclePlate = ""
    @State private var myTrips: [DesignatedDriverTripDto] = []
    @State private var requesting = false
    @State private var busyTripId: String?
    @State private var error: String?
    @State private var pollTask: Task<Void, Never>?

    private var activeTrip: DesignatedDriverTripDto? {
        myTrips.first { $0.status == "REQUESTED" || $0.status == "ACCEPTED" || $0.status == "DRIVING" }
    }
    private var pastTrips: [DesignatedDriverTripDto] { myTrips.filter { $0.status == "COMPLETED" || $0.status == "CANCELLED" } }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                if let active = activeTrip {
                    Text("Your driver").bold().foregroundColor(IDS.Colors.textPrimary)
                    DesignatedDriverTripCard(trip: active) {
                        if active.status == "REQUESTED" {
                            Button(action: { Task { await cancelTrip(active.id) } }) {
                                Text(busyTripId == active.id ? "Cancelling…" : "Cancel").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(Color.red).cornerRadius(10)
                            }
                            .disabled(busyTripId == active.id)
                        }
                    }
                } else {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Get a designated driver").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                        Text("A real professional driver comes to you and drives YOUR OWN CAR home.")
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        HStack(spacing: 8) {
                            IdsTextField("Pickup", text: $pickupAddress)
                            Button(action: { locationFetcher.requestLocation() }) {
                                Text("Use my location").font(.caption).bold()
                                    .padding(.horizontal, 12).padding(.vertical, 14)
                                    .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                            }
                        }
                        IdsTextField("Drop-off address", text: $dropoffAddress)
                        HStack(spacing: 8) {
                            IdsTextField("Drop-off latitude", text: $dropoffLat, keyboardType: .decimalPad)
                            IdsTextField("Drop-off longitude", text: $dropoffLng, keyboardType: .decimalPad)
                        }
                        IdsTextField("Car make (e.g. Toyota)", text: $vehicleMake)
                        IdsTextField("Car model (e.g. RAV4)", text: $vehicleModel)
                        IdsTextField("License plate", text: $vehiclePlate)
                        Button(action: { Task { await requestTrip() } }) {
                            Text(requesting ? "Requesting…" : "Request a driver")
                                .bold().foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 14)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(requesting || locationFetcher.coordinate == nil || dropoffAddress.isEmpty || vehicleMake.isEmpty || vehicleModel.isEmpty || vehiclePlate.isEmpty)
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
                    Text("Past trips").bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(pastTrips, id: \.id) { trip in DesignatedDriverTripCard(trip: trip) }
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
    }

    private func loadTrips() async {
        myTrips = (try? await NetworkClient.shared.getMyDesignatedDriverTrips().trips) ?? myTrips
    }

    private func requestTrip() async {
        guard let coordinate = locationFetcher.coordinate,
              let dLat = Double(dropoffLat), let dLng = Double(dropoffLng), !dropoffAddress.isEmpty,
              !vehicleMake.isEmpty, !vehicleModel.isEmpty, !vehiclePlate.isEmpty else { return }
        requesting = true
        error = nil
        do {
            _ = try await NetworkClient.shared.requestDesignatedDriverTrip(
                pickupAddress: pickupAddress, pickupLatitude: coordinate.latitude, pickupLongitude: coordinate.longitude,
                dropoffAddress: dropoffAddress, dropoffLatitude: dLat, dropoffLongitude: dLng,
                vehicleMake: vehicleMake, vehicleModel: vehicleModel, vehiclePlate: vehiclePlate
            )
            dropoffAddress = ""
            dropoffLat = ""
            dropoffLng = ""
            vehicleMake = ""
            vehicleModel = ""
            vehiclePlate = ""
            await loadTrips()
        } catch {
            self.error = "Could not request a designated driver."
        }
        requesting = false
    }

    private func cancelTrip(_ id: String) async {
        busyTripId = id
        do {
            _ = try await NetworkClient.shared.cancelDesignatedDriverTrip(id: id)
            await loadTrips()
        } catch {
            self.error = "Could not cancel this trip."
        }
        busyTripId = nil
    }
}

private struct DesignatedDriverDriveContent: View {
    @StateObject private var locationFetcher = DesignatedDriverLocationFetcher()
    @State private var driver: DesignatedDriverDto?
    @State private var licenseNumber = ""
    @State private var loaded = false
    @State private var registering = false
    @State private var availableTrips: [DesignatedDriverTripDto] = []
    @State private var myDriverTrips: [DesignatedDriverTripDto] = []
    @State private var busyTripId: String?
    @State private var error: String?
    @State private var pollTask: Task<Void, Never>?

    private var activeDriverTrips: [DesignatedDriverTripDto] { myDriverTrips.filter { $0.status == "ACCEPTED" || $0.status == "DRIVING" } }
    private var pastDriverTrips: [DesignatedDriverTripDto] { myDriverTrips.filter { $0.status == "COMPLETED" || $0.status == "CANCELLED" } }

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
                        Text("Become a designated driver").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                        Text("Any itunda user can register. License number is self-declared, not verified against a real registry.")
                            .font(.footnote).foregroundColor(IDS.Colors.textSecondary).multilineTextAlignment(.center)
                        IdsTextField("License number", text: $licenseNumber)
                        Button(action: { Task { await register() } }) {
                            Text(registering ? "Registering…" : "Register").bold().foregroundColor(.white)
                                .padding(.horizontal, 24).padding(.vertical, 14)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(registering || licenseNumber.isEmpty)
                    }
                    .frame(maxWidth: .infinity).padding(24).background(Color(.secondarySystemBackground)).cornerRadius(12)
                } else if let current = driver {
                    HStack {
                        Text(current.available ? "You're online" : "You're offline").bold().foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Button(action: { Task { await toggleAvailable() } }) {
                            Text(current.available ? "Go offline" : "Go online").bold().foregroundColor(.white)
                                .padding(.horizontal, 16).padding(.vertical, 10)
                                .background(current.available ? Color.red : IDS.Colors.brand).cornerRadius(10)
                        }
                    }
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                    if !activeDriverTrips.isEmpty {
                        Text("Active").bold().foregroundColor(IDS.Colors.textPrimary)
                        ForEach(activeDriverTrips, id: \.id) { trip in
                            DesignatedDriverTripCard(trip: trip) {
                                if trip.status == "ACCEPTED" {
                                    Button(action: { Task { await act(trip.id) { try await NetworkClient.shared.startDesignatedDriverTrip(id: $0).trip } } }) {
                                        Text(busyTripId == trip.id ? "…" : "Start driving").bold().foregroundColor(.white)
                                            .frame(maxWidth: .infinity).padding(.vertical, 12).background(IDS.Colors.brand).cornerRadius(10)
                                    }
                                    .disabled(busyTripId == trip.id)
                                } else if trip.status == "DRIVING" {
                                    Button(action: { Task { await act(trip.id) { try await NetworkClient.shared.completeDesignatedDriverTrip(id: $0).trip } } }) {
                                        Text(busyTripId == trip.id ? "…" : "Complete").bold().foregroundColor(.white)
                                            .frame(maxWidth: .infinity).padding(.vertical, 12).background(IDS.Colors.brand).cornerRadius(10)
                                    }
                                    .disabled(busyTripId == trip.id)
                                }
                            }
                        }
                    }

                    if !availableTrips.isEmpty {
                        Text("Nearby requests").bold().foregroundColor(IDS.Colors.textPrimary)
                        ForEach(availableTrips, id: \.id) { trip in
                            DesignatedDriverTripCard(trip: trip) {
                                Button(action: { Task { await act(trip.id) { try await NetworkClient.shared.acceptDesignatedDriverTrip(id: $0).trip } } }) {
                                    Text(busyTripId == trip.id ? "…" : "Accept").bold().foregroundColor(.white)
                                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                                        .background(IDS.Colors.brand).cornerRadius(10)
                                }
                                .disabled(busyTripId == trip.id)
                            }
                        }
                    }

                    if !pastDriverTrips.isEmpty {
                        Text("Completed").bold().foregroundColor(IDS.Colors.textPrimary)
                        ForEach(pastDriverTrips, id: \.id) { trip in DesignatedDriverTripCard(trip: trip) }
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
                    driver = try? await NetworkClient.shared.updateDesignatedDriverLocation(latitude: coordinate.latitude, longitude: coordinate.longitude).driver
                }
            }
        }
    }

    private func loadDriver() async {
        do {
            driver = try await NetworkClient.shared.getMyDesignatedDriverProfile().driver
        } catch NetworkError.httpError(let statusCode) where statusCode == 404 {
            driver = nil
        } catch {
            // Leave driver state as-is; next poll may recover.
        }
        loaded = true
    }

    private func loadTrips() async {
        availableTrips = (try? await NetworkClient.shared.getAvailableDesignatedDriverTrips().trips) ?? availableTrips
        myDriverTrips = (try? await NetworkClient.shared.getMyDesignatedDriverDriverTrips().trips) ?? myDriverTrips
    }

    private func register() async {
        registering = true
        do {
            driver = try await NetworkClient.shared.registerAsDesignatedDriver(licenseNumber: licenseNumber).driver
            licenseNumber = ""
        } catch NetworkError.httpError(let statusCode) where statusCode == 409 {
            // DESIGNATED_DRIVER_ALREADY_REGISTERED in practice (matches Android's
            // identical DesignatedDriverScreen.kt fix, 2026-08-15) -- a fresh
            // install/reinstall has no local memory of a prior registration, but the
            // account genuinely IS already registered. Resolve forward: load the
            // existing profile instead of a dead-end error.
            await loadDriver()
            licenseNumber = ""
        } catch {
            self.error = "Could not register as a designated driver."
        }
        registering = false
    }

    private func toggleAvailable() async {
        guard let current = driver else { return }
        do {
            let updated = try await NetworkClient.shared.setDesignatedDriverAvailability(available: !current.available).driver
            driver = updated
            if updated?.available == true { locationFetcher.requestLocation() }
        } catch {
            self.error = "Could not update your availability."
        }
    }

    private func act(_ tripId: String, _ action: (String) async throws -> DesignatedDriverTripDto) async {
        busyTripId = tripId
        do {
            _ = try await action(tripId)
            await loadTrips()
        } catch {
            self.error = "Could not update this trip."
        }
        busyTripId = nil
    }
}

private struct DesignatedDriverTripCard<Action: View>: View {
    let trip: DesignatedDriverTripDto
    let action: () -> Action

    init(trip: DesignatedDriverTripDto, @ViewBuilder action: @escaping () -> Action = { EmptyView() }) {
        self.trip = trip
        self.action = action
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(trip.pickupAddress).bold().foregroundColor(IDS.Colors.textPrimary)
            Text("→ \(trip.dropoffAddress)").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            Text("\(trip.vehicleMake) \(trip.vehicleModel) · \(trip.vehiclePlate)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            HStack {
                Text(designatedDriverStatusLabel(trip.status)).font(.caption).bold().foregroundColor(designatedDriverStatusColor(trip.status))
                Spacer()
                Text("\(formatMoneyDesignatedDriver(trip.fare)) RWF · \(String(format: "%.1f", trip.distanceKm)) km")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            action()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }
}

private func designatedDriverStatusLabel(_ status: String) -> String {
    switch status {
    case "REQUESTED": return "Finding a driver…"
    case "ACCEPTED": return "Driver on the way"
    case "DRIVING": return "Driving you home"
    case "COMPLETED": return "Completed"
    case "CANCELLED": return "Cancelled"
    default: return status
    }
}

private func designatedDriverStatusColor(_ status: String) -> Color {
    switch status {
    case "COMPLETED": return .green
    case "CANCELLED": return .red
    default: return IDS.Colors.brand
    }
}

private func formatMoneyDesignatedDriver(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
