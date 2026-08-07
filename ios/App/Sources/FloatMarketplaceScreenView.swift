import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

/// Real "my location" via Apple's own CLLocationManager -- same technique
/// DesignatedDriverScreenView.swift's own fetcher already establishes, a separate
/// private copy per this codebase's established per-file convention.
private final class FloatMarketplaceLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
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

/// Real Rwanda-native peer-to-peer agent float rebalancing marketplace -- sourced
/// beyond this session's usual Toss/Kakao/Naver/Coupang reference ecosystems. Running
/// out of e-float or physical cash is a documented top-2 operational challenge for
/// mobile money agents across Africa; the real existing rebalancing path is traveling
/// to a central point, often impossible on a weekend. See the backend's
/// FloatMarketplaceService.kt doc comment for the full account. bank-mfe/Android
/// already have this; this is the first iOS client.
struct FloatMarketplaceScreenView: View {
    var onBack: () -> Void = {}

    @StateObject private var locationFetcher = FloatMarketplaceLocationFetcher()
    @State private var nearby: [NearbyFloatListingDto] = []
    @State private var myListings: [FloatListingDto] = []
    @State private var myRequests: [FloatTransferRequestDto] = []
    @State private var incomingRequests: [FloatTransferRequestDto] = []
    @State private var locating = false
    @State private var busy = false
    @State private var error: String?
    @State private var message: String?
    @State private var listAmount = ""
    @State private var requestAmounts: [String: String] = [:]

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left").foregroundColor(IDS.Colors.textPrimary) }.accessibilityLabel("Back")
                Spacer()
                Text("Float marketplace").font(.headline).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Color.clear.frame(width: 20)
            }
            .padding()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if let message { Text(message).font(.caption).foregroundColor(IDS.Colors.brand) }

                    postListingCard
                    nearbyCard
                    myListingsCard
                    incomingRequestsCard
                    myRequestsCard
                }
                .padding(IDS.Layout.screenHorizontal)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await loadMine() }
        .onReceive(locationFetcher.$coordinate) { coordinate in
            guard let coordinate else { return }
            Task { await findNearby(latitude: coordinate.latitude, longitude: coordinate.longitude) }
        }
        .onReceive(locationFetcher.$errorMessage) { message in
            guard let message else { return }
            locating = false
            error = message
        }
    }

    private var postListingCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Offer surplus float").bold()
            IdsTextField("Amount to offer (RWF)", text: $listAmount, keyboardType: .decimalPad)
            Button(action: { Task { await postListing() } }) {
                Text(busy ? "Working…" : "Post listing").bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(busy)
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private var nearbyCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Nearby agents with float to spare").bold()
            Button(action: { locating = true; error = nil; locationFetcher.requestLocation() }) {
                Text(locating ? "Finding…" : "Find nearby listings").bold()
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(Color(.secondarySystemBackground)).cornerRadius(10)
            }
            .disabled(locating)
            if nearby.isEmpty {
                EmptyStateView("No nearby listings loaded yet.")
            }
            ForEach(nearby) { n in
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("\(n.agentDisplayName) · \(String(format: "%.1f", n.distanceKm)) km").font(.footnote)
                        Spacer()
                        Text("\(Int(n.remainingAmount)) RWF available").font(.footnote).bold()
                    }
                    HStack {
                        IdsTextField("Amount to request", text: Binding(
                            get: { requestAmounts[n.listing.id] ?? "" },
                            set: { requestAmounts[n.listing.id] = $0 }
                        ), keyboardType: .decimalPad)
                        Button("Request") { Task { await requestFloat(listingId: n.listing.id, remainingAmount: n.remainingAmount) } }
                            .disabled(busy)
                    }
                }
                .padding(.vertical, 6)
                Divider()
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private var myListingsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("My listings").bold()
            if myListings.isEmpty {
                EmptyStateView("No float listings yet — post one to let nearby agents claim your spare cash.")
            }
            ForEach(myListings) { l in
                HStack {
                    Text("\(Int(l.amount)) RWF offered · \(Int(l.claimedAmount)) claimed · \(l.status)").font(.footnote)
                    Spacer()
                    if l.status == "OPEN" {
                        Button("Cancel") { Task { await cancelListing(l.id) } }.disabled(busy)
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private var incomingRequestsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Requests against my listings").bold()
            if incomingRequests.isEmpty {
                EmptyStateView("No requests yet — they'll show up here once another agent claims from your listing.")
            }
            ForEach(incomingRequests) { r in
                HStack {
                    Text("\(Int(r.amount)) RWF · \(r.status)").font(.footnote)
                    Spacer()
                    if r.status == "REQUESTED" {
                        Button("Accept") { Task { await acceptRequest(r.id) } }.disabled(busy)
                        Button("Decline") { Task { await declineRequest(r.id) } }.disabled(busy)
                    }
                }
                .padding(.vertical, 4)
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private var myRequestsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("My requests").bold()
            if myRequests.isEmpty {
                EmptyStateView("No requests yet — claim from a nearby listing above and it'll show up here.")
            }
            ForEach(myRequests) { r in
                HStack {
                    Text("\(Int(r.amount)) RWF").font(.footnote)
                    Spacer()
                    Text(r.status).font(.footnote).bold()
                }
                .padding(.vertical, 4)
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
    }

    private func loadMine() async {
        myListings = (try? await NetworkClient.shared.getMyFloatListings())?.listings ?? []
        myRequests = (try? await NetworkClient.shared.getMyFloatRequests())?.requests ?? []
        incomingRequests = (try? await NetworkClient.shared.getIncomingFloatRequests())?.requests ?? []
    }

    private func findNearby(latitude: Double, longitude: Double) async {
        do {
            nearby = try await NetworkClient.shared.getNearbyFloatListings(latitude: latitude, longitude: longitude).listings
        } catch {
            self.error = "Could not load nearby float listings."
        }
        locating = false
    }

    private func postListing() async {
        guard let amount = Double(listAmount), amount > 0 else {
            error = "Enter a real positive amount of float to offer."
            return
        }
        busy = true; error = nil; message = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.postFloatListing(amount: amount)
            message = "Listing posted — other nearby agents can now request this float."
            listAmount = ""
            await loadMine()
        } catch {
            self.error = "Could not post this listing."
        }
    }

    private func cancelListing(_ listingId: String) async {
        busy = true; error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.cancelFloatListing(listingId: listingId)
            await loadMine()
        } catch {
            self.error = "Could not cancel this listing."
        }
    }

    private func requestFloat(listingId: String, remainingAmount: Double) async {
        guard let amount = Double(requestAmounts[listingId] ?? ""), amount > 0, amount <= remainingAmount else {
            error = "Enter a real amount up to the \(Int(remainingAmount)) RWF still available on this listing."
            return
        }
        busy = true; error = nil; message = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.requestFloat(listingId: listingId, amount: amount)
            message = "Request sent — the listing owner will accept or decline it."
            requestAmounts[listingId] = ""
            await loadMine()
        } catch {
            self.error = "Could not send this request."
        }
    }

    private func acceptRequest(_ requestId: String) async {
        busy = true; error = nil; message = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.acceptFloatRequest(requestId: requestId)
            message = "Float transferred to the requesting agent."
            await loadMine()
        } catch {
            self.error = "Could not accept this request."
        }
    }

    private func declineRequest(_ requestId: String) async {
        busy = true; error = nil
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.declineFloatRequest(requestId: requestId)
            await loadMine()
        } catch {
            self.error = "Could not decline this request."
        }
    }
}
