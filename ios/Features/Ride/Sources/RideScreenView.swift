import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

/// Real "my location" via Apple's own CLLocationManager, runtime-permission-gated --
/// same real technique HoodScreen.swift/MapScreenView.swift each already establish
/// their own copy of, reused here for the passenger's one-tap pickup location.
/// Internal (not private/fileprivate), not just file-scoped, since RideDriverContent
/// (moved to its own RideDriverContent.swift file, 2026-09-06 Rideshare
/// product-completeness pass) reuses this same copy for the driver's own
/// go-online location update.
final class RideLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
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
public struct RideScreenView: View {
    var onBack: () -> Void = {}
    var onReportIssue: (String) -> Void = { _ in }
    @State private var tab = 0

    public init(onBack: @escaping () -> Void = {}, onReportIssue: @escaping (String) -> Void = { _ in }) {
        self.onBack = onBack
        self.onReportIssue = onReportIssue
    }

    public var body: some View {
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
                RidePassengerContent(onReportIssue: onReportIssue)
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
// be reused directly: different target). Was internal (not private), reused directly
// by App/Sources/ShopProductDetail.swift, until Ride moved into FeatureRide
// (2026-09-06, Rideshare product-completeness pass) -- ShopProductDetail.swift got
// its own local duplicate at that point (same fix EatsRestaurantMenu.swift's own
// copy already got when Eats made the identical move); this copy no longer needs
// to stay internal, but is left as-is rather than narrowed to private for a
// same-pass file this large.
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
    var onReportIssue: (String) -> Void = { _ in }
    @StateObject private var locationFetcher = RideLocationFetcher()
    @State private var pickupAddress = ""
    @State private var dropoffAddress = ""
    @State private var dropoffLat = ""
    @State private var dropoffLng = ""
    @State private var scheduleHours = ""
    @State private var stops: [RideStopInput] = []
    @State private var myTrips: [RideTripDto] = []
    // Real pagination fix (2026-09-11, ported from bank-mfe's own fix and
    // Android's port -- see project_itunda_pagination_discard_sweep memory):
    // page 0 is polled every 4s for real-time active-trip tracking, so it
    // must always stay a live, page-0-only fetch. olderPastTrips is a
    // separate accumulator populated only by loadMorePastTrips, never
    // touched by the poll.
    @State private var olderPastTrips: [RideTripDto] = []
    @State private var pastTripsPage = 0
    @State private var pastTripsHasMore = false
    @State private var loadingMorePastTrips = false
    @State private var activeTripStops: [RideTripStopDto] = []
    @State private var requesting = false
    @State private var busyTripId: String?
    @State private var reviewedTripIds: Set<String> = []
    // Real optimistic-hide for TipDriverPrompt -- same pattern bank-mfe's own
    // tippedTripIds establishes.
    @State private var tippedTripIds: Set<String> = []
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
    private var pastTrips: [RideTripDto] {
        myTrips.filter { $0.status == "COMPLETED" || $0.status == "CANCELLED" } + olderPastTrips
    }

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
                            if active.status == "DRIVER_ASSIGNED" || active.status == "IN_PROGRESS" {
                                RideLiveDriverMap(
                                    tripId: active.id, fromLat: active.pickupLatitude, fromLng: active.pickupLongitude,
                                    toLat: active.dropoffLatitude, toLng: active.dropoffLongitude,
                                )
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
                        SavedPlacesQuickSelect(bookmarks: bookmarks) { bookmark in
                            dropoffAddress = bookmark.displayName
                            dropoffLat = String(bookmark.latitude)
                            dropoffLng = String(bookmark.longitude)
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

                PastRideTripsSection(
                    trips: pastTrips,
                    title: "Past rides",
                    hasMore: pastTripsHasMore,
                    loadingMore: loadingMorePastTrips,
                    onLoadMore: { Task { await loadMorePastTrips() } },
                    itemContent: { trip in
                        AnyView(
                            Group {
                                if trip.status == "COMPLETED", trip.driverId != nil, !reviewedTripIds.contains(trip.id) {
                                    RideReviewRow(busy: busyTripId == trip.id) { rating, comment in
                                        await submitReview(trip.id, rating, comment)
                                    }
                                }
                                if trip.status == "COMPLETED", trip.driverId != nil, trip.tipAmount == nil, !tippedTripIds.contains(trip.id) {
                                    TipDriverPrompt(tripId: trip.id, onTipped: { tippedTripIds.insert(trip.id) })
                                }
                                // Real "report an issue" hand-off (Support product-
                                // completeness pass, 2026-09-08) -- mirrors bank-mfe's
                                // RidePassengerView's own identical button, see
                                // SupportScreenView.swift's own doc comment.
                                if trip.status == "COMPLETED" {
                                    Button(action: { onReportIssue(trip.transactionId) }) {
                                        Text("Report an issue")
                                            .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                            .padding(.horizontal, 12).padding(.vertical, 8)
                                            .background(IDS.Colors.chipBackground).cornerRadius(8)
                                    }
                                }
                            }
                        )
                    }
                )
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
        guard let r = try? await NetworkClient.shared.getMyRideTrips(page: 0) else { return }
        myTrips = r.trips
        pastTripsHasMore = r.page + 1 < r.totalPages
    }

    private func loadMorePastTrips() async {
        let nextPage = pastTripsPage + 1
        loadingMorePastTrips = true
        defer { loadingMorePastTrips = false }
        guard let r = try? await NetworkClient.shared.getMyRideTrips(page: nextPage) else { return }
        olderPastTrips += r.trips.filter { $0.status == "COMPLETED" || $0.status == "CANCELLED" }
        pastTripsPage = nextPage
        pastTripsHasMore = r.page + 1 < r.totalPages
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
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not request a ride."
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
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not cancel this trip."
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

