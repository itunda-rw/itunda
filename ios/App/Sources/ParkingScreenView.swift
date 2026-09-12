import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

/// Real "my location" via Apple's own CLLocationManager -- same technique
/// BikeRentalScreenView.swift's own fetcher already establishes, a separate private
/// copy per this codebase's established per-file convention.
private final class ParkingLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
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

/// Real Kakao T 주차 (Kakao T Parking, item 223) -- real PEER-TO-PEER parking-spot
/// rental pool (any user self-lists a spot they own/control, no admin gate), billed by
/// elapsed HOURS at checkout -- distinct from RideScreenView.swift/
/// DesignatedDriverScreenView.swift, which both know their fare up front, but the same
/// "settle at session end" shape BikeRentalScreenView.swift already establishes (just
/// hourly, not per-minute). bank-mfe/Android already have this; this is the first iOS
/// client, mirroring its Find-a-spot/My-spots toggle exactly. Same honest v1
/// scope-down: one-tap "use my location" via CLLocationManager, no real map.
struct ParkingScreenView: View {
    var onBack: () -> Void = {}
    @State private var tab = 0

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
                Spacer()
                Text("Parking").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            Picker("", selection: $tab) {
                Text("Find a spot").tag(0)
                Text("My spots").tag(1)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal)

            if tab == 0 {
                ParkingFindContent()
            } else {
                ParkingMineContent()
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct ParkingFindContent: View {
    @StateObject private var locationFetcher = ParkingLocationFetcher()
    @State private var nearbySpots: [ParkingSpotDto] = []
    @State private var activeSession: ParkingSessionDto?
    @State private var pastSessions: [ParkingSessionDto] = []
    // Real pagination-discard fix (2026-09-13, porting web/Android's own fix -- see
    // project_itunda_pagination_discard_sweep memory) -- getMyParkingHistory
    // silently capped this list at the first 20 sessions. Page 0 is polled every
    // 4s for real-time active-session accuracy, so it must stay a live, page-0-only
    // fetch; olderSessions is a separate accumulator populated only by loadMore.
    @State private var olderSessions: [ParkingSessionDto] = []
    @State private var sessionsPage = 0
    @State private var sessionsHasMore = false
    @State private var loadingMoreSessions = false
    @State private var busySpotId: String?
    @State private var ending = false
    @State private var error: String?
    @State private var pollTask: Task<Void, Never>?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                if let session = activeSession {
                    Text("Parked now").bold().foregroundColor(IDS.Colors.textPrimary)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Checked in -- billed by elapsed hours").bold().foregroundColor(IDS.Colors.textPrimary)
                        Button(action: { Task { await endSession(session.id) } }) {
                            Text(ending ? "Checking out…" : "Check out (end session)").bold().foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(ending)
                    }
                    .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                } else {
                    Button(action: { locationFetcher.requestLocation() }) {
                        Text("Find nearby spots").font(.subheadline).bold()
                            .frame(maxWidth: .infinity).padding(.vertical, 14)
                            .background(Color(.tertiarySystemBackground)).cornerRadius(10)
                    }
                    if let locError = locationFetcher.errorMessage {
                        Text(locError).font(.caption).foregroundColor(.red)
                    }
                    if locationFetcher.coordinate != nil && nearbySpots.isEmpty {
                        EmptyStateView("No parking spots available nearby.")
                    }
                    ForEach(nearbySpots) { spot in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(spot.address).bold().foregroundColor(IDS.Colors.textPrimary)
                            Text("\(formatMoney(spot.hourlyRate)) RWF / hour").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Button(action: { Task { await startSession(spot.id) } }) {
                                Text(busySpotId == spot.id ? "…" : "Check in").bold().foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                                    .background(IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busySpotId == spot.id)
                        }
                        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
                    }
                }

                let allPastSessions = pastSessions + olderSessions
                if !allPastSessions.isEmpty {
                    Text("Past sessions").bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(allPastSessions) { session in ParkingSessionCard(session: session) }
                    if sessionsHasMore {
                        Button(loadingMoreSessions ? "Loading…" : "Load more") {
                            Task { await loadMoreSessions() }
                        }
                        .disabled(loadingMoreSessions)
                    }
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
        guard let res = try? await NetworkClient.shared.getMyParkingHistory(page: 0) else { return }
        activeSession = res.sessions.first { $0.status == "ACTIVE" }
        pastSessions = res.sessions.filter { $0.status == "COMPLETED" }
        sessionsHasMore = res.page + 1 < res.totalPages
    }

    private func loadMoreSessions() async {
        let nextPage = sessionsPage + 1
        loadingMoreSessions = true
        defer { loadingMoreSessions = false }
        guard let res = try? await NetworkClient.shared.getMyParkingHistory(page: nextPage) else { return }
        olderSessions += res.sessions.filter { $0.status == "COMPLETED" }
        sessionsPage = nextPage
        sessionsHasMore = res.page + 1 < res.totalPages
    }

    private func loadNearby() async {
        guard let coordinate = locationFetcher.coordinate else { return }
        nearbySpots = (try? await NetworkClient.shared.getNearbyParkingSpots(latitude: coordinate.latitude, longitude: coordinate.longitude).spots) ?? nearbySpots
    }

    private func startSession(_ spotId: String) async {
        busySpotId = spotId
        error = nil
        do {
            _ = try await NetworkClient.shared.startParkingSession(spotId: spotId)
            await loadHistory()
        } catch {
            self.error = "Could not check in to this spot."
        }
        busySpotId = nil
    }

    private func endSession(_ sessionId: String) async {
        ending = true
        error = nil
        do {
            _ = try await NetworkClient.shared.endParkingSession(sessionId: sessionId)
            await loadHistory()
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            await loadHistory()
        } catch {
            self.error = "Could not end this session."
        }
        ending = false
    }
}

private struct ParkingMineContent: View {
    @StateObject private var locationFetcher = ParkingLocationFetcher()
    @State private var mySpots: [ParkingSpotDto] = []
    @State private var loaded = false
    @State private var address = ""
    @State private var hourlyRate = ""
    @State private var registering = false
    @State private var busySpotId: String?
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if let error {
                    Text(error).font(.footnote).foregroundColor(.red)
                }
                VStack(alignment: .leading, spacing: 8) {
                    Text("List a spot you own").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                    Text("Any itunda user can list a driveway or private lot space into the shared rental pool.")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    IdsTextField("Address", text: $address)
                    IdsTextField("Hourly rate (RWF)", text: $hourlyRate, keyboardType: .decimalPad)
                    Button(action: { Task { await register() } }) {
                        Text(registering ? "Registering…" : "List at my current location").bold().foregroundColor(.white)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(IDS.Colors.brand).cornerRadius(10)
                    }
                    .disabled(registering)
                }
                .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)

                if !loaded {
                    ProgressView().frame(maxWidth: .infinity).padding(20)
                } else if mySpots.isEmpty {
                    Text("You haven't listed any spots yet.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                } else {
                    Text("Your spots").bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(mySpots) { spot in
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text(spot.address).bold().foregroundColor(IDS.Colors.textPrimary)
                                Spacer()
                                Button(action: { Task { await toggleAvailable(spot) } }) {
                                    Text(spot.available ? "Available" : "Unavailable").font(.caption).bold()
                                        .foregroundColor(spot.available ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 14).padding(.vertical, 10)
                                        .background(spot.available ? IDS.Colors.success : Color(.tertiarySystemBackground)).cornerRadius(10)
                                }
                                .disabled(busySpotId == spot.id)
                            }
                            Text("\(formatMoney(spot.hourlyRate)) RWF / hour").font(.caption).foregroundColor(IDS.Colors.textSecondary)
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
        mySpots = (try? await NetworkClient.shared.getMyParkingSpots().spots) ?? mySpots
        loaded = true
    }

    private func register() async {
        guard let rate = Double(hourlyRate), rate > 0, !address.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a real address and hourly rate."
            return
        }
        registering = true
        error = nil
        locationFetcher.requestLocation()
        // Real device geolocation is asynchronous -- poll briefly for the callback to
        // land, same honest bound BikeRentalScreenView.swift's own registration flow
        // accepts rather than a real indefinite wait.
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
            _ = try await NetworkClient.shared.registerParkingSpot(address: address.trimmingCharacters(in: .whitespaces), latitude: coordinate.latitude, longitude: coordinate.longitude, hourlyRate: rate)
            address = ""
            hourlyRate = ""
            await load()
        } catch {
            self.error = "Could not list this spot."
        }
        registering = false
    }

    private func toggleAvailable(_ spot: ParkingSpotDto) async {
        busySpotId = spot.id
        do {
            _ = try await NetworkClient.shared.setParkingSpotAvailability(spotId: spot.id, available: !spot.available)
            await load()
        } catch {
            self.error = "Could not update this spot."
        }
        busySpotId = nil
    }
}

private struct ParkingSessionCard: View {
    let session: ParkingSessionDto

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\(session.durationMinutes ?? 0) min parked").bold().foregroundColor(IDS.Colors.textPrimary)
            if let fare = session.totalFare {
                Text("\(formatMoney(fare)) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16).background(Color(.secondarySystemBackground)).cornerRadius(12)
    }
}

