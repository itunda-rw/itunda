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
                Button(action: onBack) { IDS.Icons.back(size: 17, color: IDS.Colors.textPrimary, relativeTo: .body) }.accessibilityLabel("Back")
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

// Real Uber/Kakao T-style saved-places quick-select (2026-08-23) -- see
// RidePassengerContent's own `bookmarks` doc comment. Small hex parser rather than a
// new app-wide Color extension for one feature -- same real precedent Features/Maps/
// Sources/MapScreenView.swift's own private colorFromHex already established (can't
// be reused directly: different target, App doesn't depend on FeatureMaps). Internal
// (not private/fileprivate), not just file-scoped, since EatsRestaurantMenu.swift
// reuses this same copy for the identical fix on the Eats delivery-address field --
// both live in the App target, so widening this one avoids a third duplicate.
func colorFromHex(_ hex: String) -> Color {
    var sanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
    if sanitized.hasPrefix("#") { sanitized.removeFirst() }
    guard sanitized.count == 6, let value = UInt64(sanitized, radix: 16) else {
        return Color(red: 0.961, green: 0.651, blue: 0.137) // the default star-yellow, same fallback MapScreenView.swift's own copy uses
    }
    return Color(
        red: Double((value >> 16) & 0xFF) / 255,
        green: Double((value >> 8) & 0xFF) / 255,
        blue: Double(value & 0xFF) / 255
    )
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
    // Real Uber "Verify Your Ride" PIN -- fetched once a driver is assigned so the
    // passenger can read it aloud before pickup.
    @State private var activeTripPin: String?
    // Real Uber Safety "Trusted Contacts" (help.uber.com) -- last remaining client
    // platform for this feature (item 161; bank-mfe/Android already have it). Mirrors
    // bank-mfe's TrustedContactsSection/Android's TrustedContactsSection exactly,
    // adapted to this file's own SwiftUI conventions.
    @State private var trustedContacts: [RideTrustedContactDto]?
    @State private var addContactPhone = ""
    @State private var addContactName = ""
    @State private var addingContact = false
    @State private var removingContactId: String?
    @State private var contactError: String?
    @State private var sendingStatus = false
    @State private var sendStatusResult: String?
    // Real Uber/Kakao T-style saved-places quick-select (2026-08-23) -- itunda
    // already has a real, backend-synced "map bookmarks" feature (the Maps tab's own
    // star/save, folders/colors and all -- MapsService.addBookmark/getMyBookmarks),
    // never surfaced anywhere in ride booking despite being exactly the real "Home"/
    // "Work" shortcut every real ride-hailing app shows before you type anything.
    // Especially valuable here: this screen has no autocomplete at all -- a rider
    // currently has to know and type the exact GPS coordinates by hand.
    @State private var bookmarks: [MapBookmarkDto] = []

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
                            if let pin = activeTripPin, active.status == "DRIVER_ASSIGNED" {
                                VStack(spacing: 2) {
                                    Text("Tell your driver this PIN before you get in").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    Text(pin).font(IDS.scaledFont(size: 28, weight: .bold, relativeTo: .title1)).foregroundColor(IDS.Colors.brand).tracking(4)
                                }
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(IDS.Colors.brand.opacity(0.1)).cornerRadius(10)
                            }
                            if let driverId = active.driverId {
                                DriverRatingSection(driverId: driverId)
                            }
                            if let trustedContacts, !trustedContacts.isEmpty {
                                Button(action: { Task { await sendStatusToTrustedContacts(active.id) } }) {
                                    Text(sendingStatus ? "Sending…" : "Send status to trusted contacts")
                                        .bold().foregroundColor(IDS.Colors.textPrimary)
                                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                                        .background(IDS.Colors.card).cornerRadius(10).idsCardBorder(cornerRadius: 10)
                                }
                                .disabled(sendingStatus)
                                if let sendStatusResult {
                                    Text(sendStatusResult).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                }
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
                        if !bookmarks.isEmpty {
                            Text("Saved places").font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 8) {
                                    ForEach(bookmarks) { bookmark in
                                        Button(action: {
                                            dropoffAddress = bookmark.displayName
                                            dropoffLat = String(bookmark.latitude)
                                            dropoffLng = String(bookmark.longitude)
                                        }) {
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
                            .padding(10).background(IDS.Colors.card).cornerRadius(10)
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
                    .padding(16).background(IDS.Colors.card).cornerRadius(12)
                    .onChange(of: locationFetcher.coordinate?.latitude) { _ in
                        if locationFetcher.coordinate != nil { pickupAddress = "Current location" }
                    }
                    if let locError = locationFetcher.errorMessage {
                        Text(locError).font(.caption).foregroundColor(.red)
                    }
                }

                TrustedContactsSection(
                    contacts: trustedContacts,
                    phone: $addContactPhone, name: $addContactName,
                    adding: addingContact, onAdd: { Task { await addTrustedContact() } },
                    removingContactId: removingContactId, onRemove: { id in Task { await removeTrustedContact(id) } },
                    error: contactError
                )

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
        .task { await loadTrustedContacts() }
        .task { await loadBookmarks() }
        .onDisappear { pollTask?.cancel() }
        .onChange(of: activeTrip?.id) { _ in
            Task { await loadActiveTripStops(); await loadActiveTripPin() }
        }
        .onChange(of: activeTrip?.status) { _ in
            Task { await loadActiveTripPin() }
        }
    }

    private func loadTrips() async {
        myTrips = (try? await NetworkClient.shared.getMyRideTrips().trips) ?? myTrips
    }

    private func loadActiveTripStops() async {
        guard let id = activeTrip?.id else { activeTripStops = []; return }
        activeTripStops = (try? await NetworkClient.shared.getRideTripStops(tripId: id).stops) ?? []
    }

    private func loadActiveTripPin() async {
        guard let id = activeTrip?.id, activeTrip?.status != "REQUESTED" else { activeTripPin = nil; return }
        activeTripPin = (try? await NetworkClient.shared.getRideTripPin(id: id).pin) ?? nil
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

    private func loadTrustedContacts() async {
        trustedContacts = (try? await NetworkClient.shared.getRideTrustedContacts().contacts) ?? trustedContacts
    }

    private func loadBookmarks() async {
        bookmarks = (try? await NetworkClient.shared.getMyMapBookmarks().bookmarks) ?? bookmarks
    }

    private func addTrustedContact() async {
        addingContact = true
        contactError = nil
        do {
            _ = try await NetworkClient.shared.addRideTrustedContact(phoneNumber: addContactPhone.trimmingCharacters(in: .whitespaces), name: addContactName.trimmingCharacters(in: .whitespaces))
            addContactPhone = ""
            addContactName = ""
            await loadTrustedContacts()
        } catch let NetworkError.httpErrorWithCode(_, code, _) where code == "TRUSTED_CONTACT_ALREADY_ADDED" {
            addContactPhone = ""
            addContactName = ""
            await loadTrustedContacts()
        } catch let NetworkError.httpErrorWithCode(_, code, message) where code == "TOO_MANY_TRUSTED_CONTACTS" {
            contactError = message ?? "You can have at most 5 trusted contacts."
        } catch {
            contactError = "Could not add this trusted contact. Make sure they have an itunda account."
        }
        addingContact = false
    }

    private func removeTrustedContact(_ contactId: String) async {
        removingContactId = contactId
        do {
            _ = try await NetworkClient.shared.removeRideTrustedContact(contactId: contactId)
            await loadTrustedContacts()
        } catch {
            contactError = "Could not remove this trusted contact."
        }
        removingContactId = nil
    }

    private func sendStatusToTrustedContacts(_ tripId: String) async {
        sendingStatus = true
        sendStatusResult = nil
        do {
            let sentCount = try await NetworkClient.shared.sendStatusToRideTrustedContacts(tripId: tripId).sentCount
            sendStatusResult = "Sent to \(sentCount) contact\(sentCount == 1 ? "" : "s")."
        } catch {
            sendStatusResult = "Could not send your status right now."
        }
        sendingStatus = false
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

