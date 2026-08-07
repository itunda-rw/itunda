import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

/// Real "my location" via Apple's own CLLocationManager -- same technique
/// DesignatedDriverScreenView.swift's own fetcher already establishes, a separate
/// private copy per this codebase's established per-file convention.
private final class BikeRentalLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
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

/// Real Kakao T 바이크 (Kakao T Bike, item 222) -- real PEER-TO-PEER bike/scooter rental
/// pool (any user self-registers a bike they own, no admin gate), billed by elapsed TIME
/// at rental end -- distinct from DesignatedDriverScreenView.swift/RideScreenView.swift,
/// which both know their fare up front. bank-mfe/Android already have this; this is the
/// first iOS client, mirroring its Rent-a-bike/My-bikes toggle exactly. Same honest v1
/// scope-down: one-tap "use my location" via CLLocationManager, no real map.
struct BikeRentalScreenView: View {
    var onBack: () -> Void = {}
    @State private var tab = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("Bike rental").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            Picker("", selection: $tab) {
                Text("Rent a bike").tag(0)
                Text("My bikes").tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)

            if tab == 0 {
                BikeRentContent()
            } else {
                BikeMineContent()
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct BikeRentContent: View {
    @StateObject private var locationFetcher = BikeRentalLocationFetcher()
    @State private var nearbyBikes: [BikeDto] = []
    @State private var activeRental: BikeRentalSessionDto?
    @State private var pastRentals: [BikeRentalSessionDto] = []
    @State private var busyBikeId: String?
    @State private var ending = false
    @State private var error: String?
    @State private var pollTask: Task<Void, Never>?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                if let rental = activeRental {
                    Text("Riding now").bold().foregroundColor(IDS.Colors.textPrimary)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Bike unlocked -- billed by elapsed time").bold().foregroundColor(IDS.Colors.textPrimary)
                        Button(action: { Task { await endRental(rental.id) } }) {
                            Text(ending ? "Ending…" : "End rental (park here)").bold().foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(ending)
                    }
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                } else {
                    Button(action: { locationFetcher.requestLocation() }) {
                        Text("Find nearby bikes").font(.subheadline).bold()
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    }
                    if let locError = locationFetcher.errorMessage {
                        Text(locError).font(.caption).foregroundColor(.red)
                    }
                    if locationFetcher.coordinate != nil && nearbyBikes.isEmpty {
                        EmptyStateView("No bikes available nearby.")
                    }
                    ForEach(nearbyBikes) { bike in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(bike.type == "ELECTRIC" ? "⚡ Electric bike" : "🚲 Regular bike").bold().foregroundColor(IDS.Colors.textPrimary)
                            Text(bike.type == "ELECTRIC" ? "150 RWF/minute" : "80 RWF/minute").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Button(action: { Task { await startRental(bike.id) } }) {
                                Text(busyBikeId == bike.id ? "…" : "Unlock").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busyBikeId == bike.id)
                        }
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }

                if !pastRentals.isEmpty {
                    Text("Past rides").bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(pastRentals) { session in BikeRentalSessionCard(session: session) }
                }
            }
            .padding(.horizontal)
        }
        .task {
            pollTask = Task {
                while !Task.isCancelled {
                    await loadHistory()
                    try? await Task.sleep(nanoseconds: 4_000_000_000)
                }
            }
        }
        .onDisappear { pollTask?.cancel() }
        .onChange(of: locationFetcher.coordinate?.latitude) { _ in
            Task { await loadNearby() }
        }
    }

    private func loadHistory() async {
        guard let all = try? await NetworkClient.shared.getMyBikeRentalHistory().rentals else { return }
        activeRental = all.first { $0.status == "ACTIVE" }
        pastRentals = all.filter { $0.status == "COMPLETED" }
    }

    private func loadNearby() async {
        guard let coordinate = locationFetcher.coordinate else { return }
        nearbyBikes = (try? await NetworkClient.shared.getNearbyBikes(latitude: coordinate.latitude, longitude: coordinate.longitude).bikes) ?? nearbyBikes
    }

    private func startRental(_ bikeId: String) async {
        guard let coordinate = locationFetcher.coordinate else { return }
        busyBikeId = bikeId
        error = nil
        do {
            _ = try await NetworkClient.shared.startBikeRental(bikeId: bikeId, startLatitude: coordinate.latitude, startLongitude: coordinate.longitude)
            await loadHistory()
        } catch {
            self.error = "Could not start this rental."
        }
        busyBikeId = nil
    }

    private func endRental(_ sessionId: String) async {
        guard let coordinate = locationFetcher.coordinate else {
            error = "Share your location to end this rental."
            return
        }
        ending = true
        error = nil
        do {
            _ = try await NetworkClient.shared.endBikeRental(sessionId: sessionId, endLatitude: coordinate.latitude, endLongitude: coordinate.longitude)
            await loadHistory()
        } catch {
            self.error = "Could not end this rental."
        }
        ending = false
    }
}

private struct BikeMineContent: View {
    @StateObject private var locationFetcher = BikeRentalLocationFetcher()
    @State private var myBikes: [BikeDto] = []
    @State private var loaded = false
    @State private var bikeType = "ELECTRIC"
    @State private var registering = false
    @State private var busyBikeId: String?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text("Register a bike you own").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                    Text("Any itunda user can list a bike or scooter into the shared rental pool.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    Picker("", selection: $bikeType) {
                        Text("⚡ Electric").tag("ELECTRIC")
                        Text("🚲 Regular").tag("REGULAR")
                    }
                    .pickerStyle(.segmented)
                    Button(action: { Task { await register() } }) {
                        Text(registering ? "Registering…" : "Register at my current location").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(registering)
                }
                .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                if !loaded {
                    ProgressView().frame(maxWidth: .infinity).padding(20)
                } else if myBikes.isEmpty {
                    Text("You haven't registered any bikes yet.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                } else {
                    Text("Your bikes").bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(myBikes) { bike in
                        HStack {
                            Text(bike.type == "ELECTRIC" ? "⚡ Electric bike" : "🚲 Regular bike").bold().foregroundColor(IDS.Colors.textPrimary)
                            Spacer()
                            Button(action: { Task { await toggleAvailable(bike) } }) {
                                Text(bike.available ? "Available" : "Unavailable").font(.caption).bold()
                                    .foregroundColor(bike.available ? .white : IDS.Colors.textPrimary)
                                    .padding(.horizontal, 14).padding(.vertical, 10)
                                    .background(bike.available ? IDS.Colors.success : Color(.tertiarySystemBackground)).cornerRadius(10)
                            }
                            .disabled(busyBikeId == bike.id)
                        }
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }
            }
            .padding(.horizontal)
        }
        .task { await load() }
    }

    private func load() async {
        myBikes = (try? await NetworkClient.shared.getMyBikes().bikes) ?? myBikes
        loaded = true
    }

    private func register() async {
        registering = true
        error = nil
        locationFetcher.requestLocation()
        // Real device geolocation is asynchronous -- poll briefly for the callback to
        // land, same honest bound RideScreenView.swift's own location flows accept
        // rather than a real indefinite wait.
        for _ in 0..<20 {
            if locationFetcher.coordinate != nil { break }
            try? await Task.sleep(nanoseconds: 250_000_000)
        }
        guard let coordinate = locationFetcher.coordinate else {
            error = "Could not access your real location right now."
            registering = false
            return
        }
        do {
            _ = try await NetworkClient.shared.registerBike(type: bikeType, latitude: coordinate.latitude, longitude: coordinate.longitude)
            await load()
        } catch {
            self.error = "Could not register this bike."
        }
        registering = false
    }

    private func toggleAvailable(_ bike: BikeDto) async {
        busyBikeId = bike.id
        do {
            _ = try await NetworkClient.shared.setBikeAvailability(bikeId: bike.id, available: !bike.available)
            await load()
        } catch {
            self.error = "Could not update this bike."
        }
        busyBikeId = nil
    }
}

private struct BikeRentalSessionCard: View {
    let session: BikeRentalSessionDto

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(session.durationMinutes ?? 0) min ride").bold().foregroundColor(IDS.Colors.textPrimary)
            if let fare = session.totalFare {
                Text("\(formatMoneyBike(fare)) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }
}

private func formatMoneyBike(_ value: Double) -> String {
    let rounded = (value * 100).rounded() / 100
    return rounded == rounded.rounded(.down) ? String(Int64(rounded)) : String(format: "%.2f", rounded)
}
