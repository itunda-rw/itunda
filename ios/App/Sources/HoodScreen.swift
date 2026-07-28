import SwiftUI
import CoreLocation
import CoreDesignSystem
import CoreNetwork

/// Real device-location fetch, shared by NewListingForm's "share my location" toggle and
/// ListingCard's "directions to this seller" -- same runtime-permission-gated
/// CLLocationManager technique MapScreenView.swift's own LocationFetcher already
/// established.
private final class HoodLocationFetcher: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    @Published var errorMessage: String?
    private let manager = CLLocationManager()
    var onLocation: ((CLLocationCoordinate2D) -> Void)?

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
        guard let coordinate = locations.last?.coordinate else { return }
        self.coordinate = coordinate
        onLocation?(coordinate)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        errorMessage = "Could not access your real location right now."
    }
}

/// Freshness is a practical trust signal in a local marketplace: it distinguishes a
/// current offer from a stale listing without inventing any reputation data.
private func hoodRelativeTime(_ isoTimestamp: String) -> String {
    let standard = ISO8601DateFormatter()
    standard.formatOptions = [.withInternetDateTime]
    let fractional = ISO8601DateFormatter()
    fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    guard let date = fractional.date(from: isoTimestamp) ?? standard.date(from: isoTimestamp) else { return "" }
    let seconds = max(0, Date().timeIntervalSince(date))
    switch seconds {
    case ..<60: return "Just now"
    case ..<3_600: return "\(Int(seconds / 60))m ago"
    case ..<86_400: return "\(Int(seconds / 3_600))h ago"
    case ..<604_800: return "\(Int(seconds / 86_400))d ago"
    default: return "\(Int(seconds / 604_800))w ago"
    }
}

// Real 당근모임-style structured event date display (2026-07-25 backend/Android;
// ported to iOS 2026-07-28) -- falls back to the raw ISO string on any parse failure,
// never a fabricated date.
private func formatMeetupDate(_ iso: String) -> String {
    guard let date = ISO8601DateFormatter().date(from: iso) else { return iso }
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!
    let components = calendar.dateComponents([.year, .month, .day, .hour, .minute], from: date)
    guard let year = components.year, let month = components.month, let day = components.day,
          let hour = components.hour, let minute = components.minute else { return iso }
    return String(format: "%04d-%02d-%02d %02d:%02d", year, month, day, hour, minute)
}

/// The selected distance is sent to the existing nearby endpoints; it is not a
/// cosmetic filter. Small preset choices keep the control usable on a phone.
private struct HoodRadiusControl: View {
    @Binding var radiusKm: Double
    let onChanged: () -> Void
    var body: some View {
        HStack(spacing: 6) {
            ForEach([1.0, 3.0, 5.0, 10.0], id: \.self) { radius in
                let selected = radiusKm == radius
                Button("\(Int(radius)) km") { radiusKm = radius; onChanged() }
                    .font(.caption).bold().foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                    .padding(.horizontal, 12).padding(.vertical, 7)
                    .background(selected ? IDS.Colors.brand : IDS.Colors.chipBackground).cornerRadius(999)
            }
            Spacer()
        }
    }
}

/// Real hyperlocal neighborhood setup (2026-07-20) -- shared across every Hood-mode
/// content view (Marketplace/Community/Jobs/Property), mirroring bank-mfe's
/// NeighborhoodSetupPrompt and Android's own composable of the same name exactly.
/// Reuses HoodLocationFetcher, the same real CLLocationManager wrapper
/// NewListingForm's own "share my location" already established -- one location
/// permission flow, not a second one invented for this.
private struct NeighborhoodSetupPrompt: View {
    let onDone: (String) -> Void

    @State private var busy = false
    @State private var error: String?
    @StateObject private var locationFetcher = HoodLocationFetcher()

    var body: some View {
        VStack(spacing: 8) {
            Text("Set your neighborhood").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            Text("Share your real location once to see what's happening near you.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary).multilineTextAlignment(.center)
            Button(action: { if !busy { locationFetcher.requestLocation() } }) {
                Text(busy ? "Finding your neighborhood…" : "📍 Share my location")
                    .font(.subheadline).bold().foregroundColor(.white)
                    .padding(.horizontal, 20).padding(.vertical, 12)
                    .background(IDS.Colors.brand).cornerRadius(12)
            }
            .disabled(busy)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(28)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                busy = true
                Task {
                    do {
                        let res = try await NetworkClient.shared.setNeighborhood(latitude: coordinate.latitude, longitude: coordinate.longitude)
                        busy = false
                        if let neighborhood = res.user.neighborhood { onDone(neighborhood) }
                    } catch let NetworkError.httpError(statusCode) {
                        busy = false
                        error = TalkScreen.errorMessage(statusCode)
                    } catch {
                        busy = false
                        self.error = "Couldn't reach itunda. Check your connection and try again."
                    }
                }
            }
        }
        .onChange(of: locationFetcher.errorMessage) { newValue in
            if let newValue { error = newValue }
        }
    }
}

/// Card-shaped loading placeholders keep every Hood feed legible while its real
/// neighborhood data is loading, instead of showing a disconnected spinner.
private struct HoodFeedSkeleton: View {
    var body: some View {
        VStack(spacing: IDS.Layout.cardGap) {
            ForEach(0..<3, id: \.self) { _ in
                VStack(alignment: .leading, spacing: 10) {
                    RoundedRectangle(cornerRadius: 5).fill(IDS.Colors.chipBackground).frame(width: 92, height: 12)
                    RoundedRectangle(cornerRadius: 6).fill(IDS.Colors.chipBackground).frame(maxWidth: .infinity).frame(height: 18)
                    RoundedRectangle(cornerRadius: 5).fill(IDS.Colors.chipBackground).frame(maxWidth: .infinity).frame(height: 12)
                    RoundedRectangle(cornerRadius: 5).fill(IDS.Colors.chipBackground).frame(width: 160, height: 12)
                }
                .padding(18)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)
            }
        }
        .accessibilityLabel("Loading Hood content")
    }
}

/// Real 당근마켓 (Danggeun/Karrot Market)-style neighborhood marketplace (2026-07-18) --
/// iOS mirror of Android's HoodTab (SuperAppTabs.kt). See NetworkClient.swift's
/// Marketplace extension and rw.itunda.marketplace.MarketplaceService's own doc
/// comment for the full backend account, including the honest "no real location data"
/// scope this screen inherits unchanged.
// Real 당근-style neighborhood-services hub (2026-07-19) -- Marketplace, Community
// (동네생활), Jobs (당근알바), and Property (당근부동산) all fold into this one screen
// via a segmented Picker, matching the exact "no free bottom-nav slot, fold into an
// existing tab" pattern ShopScreen's own Shop/Eats toggle already established.
struct HoodScreen: View {
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    private enum HoodMode { case marketplace, community, jobs, property }
    @State private var mode: HoodMode = .marketplace
    @State private var neighborhoodName: String?
    @State private var neighborhoodVerificationCount = 0

    var body: some View {
        VStack(spacing: 0) {
            Picker("", selection: $mode) {
                Text("Market").tag(HoodMode.marketplace)
                Text("Life").tag(HoodMode.community)
                Text("Jobs").tag(HoodMode.jobs)
                Text("Home").tag(HoodMode.property)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)

            HStack(spacing: 6) {
                Text("📍")
                Text(neighborhoodName.map {
                    neighborhoodVerificationCount > 0
                        ? "Near \($0) · confirmed \(neighborhoodVerificationCount)×"
                        : "Near \($0)"
                } ?? "Choose your neighborhood in any Hood service")
                    .font(IDS.Typography.caption)
                    .foregroundColor(IDS.Colors.textSecondary)
                Spacer()
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.vertical, 10)

            switch mode {
            case .marketplace:
                MarketplaceContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            case .community:
                CommunityContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            case .jobs:
                JobsContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            case .property:
                PropertyContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            if let profile = try? await NetworkClient.shared.getProfile() {
                neighborhoodName = profile.user.neighborhood
                neighborhoodVerificationCount = profile.user.neighborhoodVerificationCount ?? 0
            }
        }
    }
}

private struct MarketplaceContent: View {
    /// Real "message seller" hand-off to TalkScreen -- see TalkScreen.swift's own
    /// doc comment on `pendingConversationId` for the full mechanism.
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    // wishlist added 2026-07-21, porting bank-mfe's Marketplace wishlist (shipped
    // earlier the same day) to iOS -- see favoriteIds state and ListingWishlistView
    // below for the full account.
    // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6, see backend ListingRepository's own doc comment.
    private enum HoodView { case browse, nearby, neighborhood, mine, purchases, wishlist }

    @State private var view: HoodView = .browse
    @State private var listings: [ListingDto]?
    // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment.
    @State private var trustScores: [String: Int] = [:]
    @State private var error: String?
    @State private var showNewListing = false
    @State private var neighborhoodName: String?
    @State private var neighborhoodChecked = false
    @StateObject private var locationFetcher = HoodLocationFetcher()
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    // Real Marketplace listing wishlist (2026-07-21) -- porting bank-mfe's wishlist
    // (backend + web UI shipped earlier the same day) to iOS. Favorite state is
    // lifted here, same as bank-mfe's own MarketplaceView, so the heart on every
    // ListingCard in Browse/Neighborhood/My-listings stays correct after a toggle
    // from any of them, not just the dedicated Wishlist tab.
    @State private var favoriteIds: Set<String> = []
    @State private var favoritingId: String?

    @State private var favoriteNotice: String?

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                // No IdsPlainTopBar("Hood") here (2026-07-24) -- the bottom nav and
                // this screen's own top-level Market/Life/Jobs/Home picker already
                // establish where the user is; Hood's other three sub-screens
                // (Community/Jobs/Property) never had this extra title, confirming
                // it was a real inconsistency, matching the same fix already made
                // on Android's MarketplaceScreen.kt.
                Picker("", selection: $view) {
                    Text("Browse").tag(HoodView.browse)
                    Text("Near me").tag(HoodView.nearby)
                    Text("Neighborhood").tag(HoodView.neighborhood)
                    Text("My listings").tag(HoodView.mine)
                    Text("Purchases").tag(HoodView.purchases)
                    Text("♡ Wishlist").tag(HoodView.wishlist)
                }
                .pickerStyle(.segmented)

                if let favoriteNotice {
                    Text(favoriteNotice)
                        .font(.caption).foregroundColor(IDS.Colors.brand)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }

                if view == .mine {
                    if showNewListing {
                        NewListingForm(onCreated: {
                            showNewListing = false
                            Task { await load() }
                        }, onCancel: { showNewListing = false })
                    } else {
                        Button(action: { showNewListing = true }) {
                            Text("+ List an item")
                                .font(IDS.Typography.bodyBold)
                                .foregroundColor(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 14)
                                .background(IDS.Colors.brand)
                                .cornerRadius(14)
                        }
                    }
                }

                if view == .neighborhood && neighborhoodChecked && neighborhoodName == nil {
                    NeighborhoodSetupPrompt(onDone: { _ in Task { await load() } })
                }
                if view == .neighborhood, let neighborhoodName {
                    Text("Your neighborhood: \(neighborhoodName)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                if view == .wishlist {
                    ListingWishlistView(onRemoved: { Task { await loadFavoriteIds() } })
                } else if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(IDS.Colors.card)
                    .cornerRadius(IDS.Layout.cardCornerRadius)
                } else if listings == nil {
                    HoodFeedSkeleton()
                } else if listings!.isEmpty && (view != .neighborhood || neighborhoodName != nil) {
                    Text(
                        view == .browse ? "No listings yet."
                            : view == .nearby ? "No listings near you yet."
                            : view == .neighborhood ? "No listings in your neighborhood yet."
                            : view == .purchases ? "No purchases recorded yet."
                            : "You haven't listed anything yet."
                    )
                    .foregroundColor(IDS.Colors.textSecondary)
                } else if !listings!.isEmpty {
                    ForEach(listings!) { listing in
                        ListingCard(
                            listing: listing,
                            isMine: view == .mine || listing.sellerId == currentUserId,
                            onChanged: { Task { await load() } },
                            onMessageSeller: { id in Task { await messageSeller(id) } },
                            onMakeOffer: { id, amount in Task { await makeOffer(id, amount) } },
                            favorited: favoriteIds.contains(listing.id),
                            favoriteBusy: favoritingId == listing.id,
                            onToggleFavorite: { Task { await toggleFavorite(listing.id) } },
                            sellerTrustScore: trustScores[listing.sellerId]
                        )
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            locationFetcher.onLocation = { coordinate in Task { await loadNearby(coordinate) } }
            await load()
        }
        .task { await loadFavoriteIds() }
        .onChange(of: view) { _ in Task { await load() } }
        .onChange(of: locationFetcher.errorMessage) { message in
            guard view == .nearby, let message else { return }
            error = message + " You can still use Browse or Neighborhood."
            listings = []
        }
    }

    private func load() async {
        listings = nil
        if view == .wishlist {
            // ListingWishlistView below owns its own fetch (it needs title/price/
            // category straight from the favorites endpoint, not the ListingDto
            // shape) -- nothing to load into `listings` here.
            return
        }
        if view == .nearby {
            locationFetcher.requestLocation()
            return
        }
        if view == .neighborhood {
            neighborhoodChecked = false
            do {
                let profile = try await NetworkClient.shared.getProfile()
                let res = try await NetworkClient.shared.getListingsMyNeighborhood()
                neighborhoodName = profile.user.neighborhood
                listings = res.listings
                trustScores = res.trustScores ?? [:]
                error = nil
            } catch let NetworkError.httpError(statusCode) where statusCode == 400 {
                neighborhoodName = nil
                listings = []
                error = nil
            } catch {
                self.error = "Couldn't reach itunda. Check your connection and try again."
            }
            neighborhoodChecked = true
            return
        }
        do {
            let res: ListingsResponse
            switch view {
            case .browse: res = try await NetworkClient.shared.browseListings()
            case .purchases: res = try await NetworkClient.shared.getMyPurchases()
            default: res = try await NetworkClient.shared.getMyListings()
            }
            listings = res.listings
            trustScores = res.trustScores ?? [:]
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadNearby(_ coordinate: CLLocationCoordinate2D) async {
        do {
            let res = try await NetworkClient.shared.getNearbyListings(lat: coordinate.latitude, lng: coordinate.longitude)
            listings = res.listings
            trustScores = res.trustScores ?? [:]
            error = nil
        } catch {
            self.error = "Couldn't load nearby listings. Check your connection and try again."
            listings = []
        }
    }

    // Real Marketplace listing wishlist (2026-07-21) -- best-effort: a failure here
    // just means hearts render as empty, the rest of the tab still works.
    private func loadFavoriteIds() async {
        do {
            let res = try await NetworkClient.shared.getMyFavoriteListings()
            favoriteIds = Set(res.favorites.map { $0.listingId })
        } catch {
            // Best-effort, see doc comment above.
        }
    }

    private func toggleFavorite(_ listingId: String) async {
        favoritingId = listingId
        defer { favoritingId = nil }
        do {
            if favoriteIds.contains(listingId) {
                _ = try await NetworkClient.shared.removeListingFavorite(listingId)
                favoriteIds.remove(listingId)
                favoriteNotice = "Removed from your wishlist."
            } else {
                _ = try await NetworkClient.shared.addListingFavorite(listingId)
                favoriteIds.insert(listingId)
                favoriteNotice = "Saved to your wishlist."
            }
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func messageSeller(_ listingId: String) async {
        do {
            let res = try await NetworkClient.shared.contactSeller(listingId: listingId)
            pendingConversationId = res.conversation.id
            onSwitchToTalk()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func makeOffer(_ listingId: String, _ amount: Double) async {
        do {
            let res = try await NetworkClient.shared.makeOffer(listingId: listingId, amount: amount)
            pendingConversationId = res.offer.conversationId
            onSwitchToTalk()
        } catch {
            self.error = "Couldn't send this offer. Check your connection and try again."
        }
    }
}

private struct NewListingForm: View {
    let onCreated: () -> Void
    let onCancel: () -> Void

    @State private var title = ""
    @State private var description = ""
    @State private var price = ""
    @State private var category = ""
    @State private var meetingPlace = ""
    @State private var error: String?
    @State private var submitting = false

    // Real optional seller location (2026-07-19) -- powers real proximity search and
    // "Directions to this seller"; a listing without it simply doesn't appear in either,
    // an honest opt-in, never assumed.
    @State private var shareLocation = false
    @State private var myLocation: CLLocationCoordinate2D?
    @StateObject private var locationFetcher = HoodLocationFetcher()

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("List an item").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            TextField("What are you selling?", text: $title).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            TextField("Description", text: $description).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            HStack {
                TextField("Price (RWF)", text: $price).keyboardType(.numberPad).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
                TextField("Category", text: $category).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            }
            TextField("Suggested meeting place (optional)", text: $meetingPlace)
                .padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            Text("Use a public landmark, not a home address.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            Button(action: {
                if shareLocation { shareLocation = false } else { locationFetcher.requestLocation() }
            }) {
                Text(
                    shareLocation
                        ? "📍 Real location shared -- buyers can see distance & get directions"
                        : "📍 Share my real location (optional)"
                )
                .font(.caption)
                .foregroundColor(shareLocation ? IDS.Colors.brand : IDS.Colors.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            HStack {
                Button("Cancel", action: onCancel).frame(maxWidth: .infinity)
                Button(action: { Task { await submit() } }) {
                    Text(submitting ? "Listing…" : "List it")
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(IDS.Colors.brand)
                        .cornerRadius(14)
                }
                .disabled(submitting)
            }
        }
        .padding(20)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                shareLocation = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { newValue in
            if let newValue { error = newValue }
        }
    }

    private func submit() async {
        guard let priceValue = Double(price), priceValue > 0, !title.isEmpty, !description.isEmpty, !category.isEmpty else {
            error = "Fill in every field with a real price."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            let loc = shareLocation ? myLocation : nil
            _ = try await NetworkClient.shared.createListing(
                title: title, description: description, price: priceValue, category: category,
                latitude: loc?.latitude, longitude: loc?.longitude,
                meetingPlace: meetingPlace.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : meetingPlace.trimmingCharacters(in: .whitespacesAndNewlines),
            )
            onCreated()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

// Real Karrot-Score-style numeric trust/reputation badge (2026-07-24) -- backend
// (User.trustScore, TrustScoreService) and the trustScores map on every Hood browse
// endpoint have existed since 2026-07-21, but no client rendered it anywhere -- closes
// docs/DESIGN_REFERENCES.md Section 4 recommendation #1. Deliberately a plain 0-1000
// number, never a manner-temperature/Celsius metaphor (see backend User.kt's own doc
// comment on why that's specifically wrong for a non-Korean market). Shared by
// ListingCard/JobPostCard/PropertyListingCard, all in this file.
private struct TrustBadge: View {
    let score: Int

    var body: some View {
        Text("Trust \(score)")
            .font(.caption2).fontWeight(.semibold)
            .foregroundColor(IDS.Colors.textSecondary)
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(IDS.Colors.chipBackground)
            .cornerRadius(6)
    }
}

// Real post-transaction review preset checklist labels (2026-07-24) -- ids must match
// backend HoodReviewService.GOOD_POINTS/UNCOMFORTABLE_POINTS exactly.
private let hoodGoodPointLabels: [(String, String)] = [
    ("RESPONSIVE", "Quick to respond"), ("AS_DESCRIBED", "As described"), ("ON_TIME", "On time"),
    ("FRIENDLY", "Friendly"), ("FAIR_PRICE", "Fair price"),
]
private let hoodUncomfortablePointLabels: [(String, String)] = [
    ("LATE", "Was late"), ("NOT_AS_DESCRIBED", "Not as described"), ("UNRESPONSIVE", "Hard to reach"),
    ("RUDE", "Rude"), ("PRICE_ISSUE", "Price disagreement"),
]

// Real post-transaction review with Karrot's own asymmetric public/private visibility
// (2026-07-24) -- closes docs/DESIGN_REFERENCES.md Section 4 recommendation #2. A
// preset checklist, not free text, matching Karrot's own real review UX: "good points"
// are shown publicly (feed into the trust score), "uncomfortable points" stay private
// between the two real parties to the transaction. Shared by Marketplace/Jobs/Property.
private struct HoodReviewForm: View {
    @Binding var selectedGoodPoints: Set<String>
    @Binding var selectedUncomfortablePoints: Set<String>
    let submitting: Bool
    let onCancel: () -> Void
    let onSubmit: () async -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("What went well? (shown publicly)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(hoodGoodPointLabels, id: \.0) { id, label in
                        let selected = selectedGoodPoints.contains(id)
                        Text(label)
                            .font(.caption).bold()
                            .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(selected ? IDS.Colors.brand : IDS.Colors.chipBackground)
                            .cornerRadius(999)
                            .onTapGesture {
                                if selected { selectedGoodPoints.remove(id) } else { selectedGoodPoints.insert(id) }
                            }
                    }
                }
            }
            Text("Anything uncomfortable? (private -- only you two see this)").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(hoodUncomfortablePointLabels, id: \.0) { id, label in
                        let selected = selectedUncomfortablePoints.contains(id)
                        Text(label)
                            .font(.caption).bold()
                            .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(selected ? .red : IDS.Colors.chipBackground)
                            .cornerRadius(999)
                            .onTapGesture {
                                if selected { selectedUncomfortablePoints.remove(id) } else { selectedUncomfortablePoints.insert(id) }
                            }
                    }
                }
            }
            HStack(spacing: 10) {
                Button("Cancel", action: onCancel)
                    .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    .padding(.horizontal, 16).padding(.vertical, 10)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
                Button(action: { Task { await onSubmit() } }) {
                    Text(submitting ? "Submitting…" : "Submit review")
                        .font(.subheadline).bold().foregroundColor(.white)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(IDS.Colors.brand).cornerRadius(12)
                }
                .disabled(submitting)
            }
        }
    }
}

private struct ListingCard: View {
    let listing: ListingDto
    let isMine: Bool
    let onChanged: () -> Void
    let onMessageSeller: (String) -> Void
    let onMakeOffer: (String, Double) -> Void
    // Real Marketplace listing wishlist (2026-07-21) -- state is lifted to
    // MarketplaceContent (mirroring the already-real lifted-favoriteIds pattern used
    // for Eats favorite restaurants) so the heart stays correct across Browse/
    // Neighborhood/My-listings without a per-card refetch.
    var favorited: Bool = false
    var favoriteBusy: Bool = false
    var onToggleFavorite: () -> Void = {}
    var sellerTrustScore: Int?

    @State private var busy = false
    @State private var error: String?
    @State private var offering = false
    @State private var offerAmount = ""
    @State private var showingSafetyChecklist = false
    @State private var showingReportOptions = false

    // Real optional buyer identification at mark-sold time (2026-07-24) -- see backend
    // MarketplaceService.markSold's own doc comment. Confirm with a phone number or
    // Skip, either way the sale completes.
    @State private var markingSold = false
    @State private var buyerPhone = ""

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @State private var showReviewSheet = false
    @State private var selectedGoodPoints: Set<String> = []
    @State private var selectedUncomfortablePoints: Set<String> = []
    @State private var submittingReview = false
    @State private var reviewSubmitted = false

    // Real "directions to this seller" (2026-07-19, item 8 on the Maps "100%" roadmap) --
    // reuses itunda's own self-hosted OSRM directions, same RouteMiniMap component Eats
    // orders use.
    @State private var myLocation: CLLocationCoordinate2D?
    @State private var showRoute = false
    @StateObject private var locationFetcher = HoodLocationFetcher()

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    HStack {
                        Text(listing.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        if listing.status == "SOLD" {
                            Text("SOLD")
                                .font(.caption2).bold()
                                .foregroundColor(IDS.Colors.textSecondary)
                                .padding(.horizontal, 8).padding(.vertical, 2)
                                .background(IDS.Colors.chipBackground)
                                .cornerRadius(8)
                        }
                    }
                    Text("\(listing.category) · \(hoodRelativeTime(listing.createdAt))").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                if !isMine {
                    Button(action: onToggleFavorite) {
                        Image(systemName: favorited ? "heart.fill" : "heart")
                            .foregroundColor(favorited ? .red : IDS.Colors.textSecondary)
                    }
                    .disabled(favoriteBusy)
                    .padding(.trailing, 6)
                }
                Text("\(Int(listing.price)) RWF").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
            // comment. Only shown for someone else's listing.
            if !isMine, let sellerTrustScore {
                TrustBadge(score: sellerTrustScore)
            }
            Text(listing.description).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            if let meetingPlace = listing.meetingPlace {
                Text("Suggested hand-off: \(meetingPlace)")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            Button(action: { showingSafetyChecklist.toggle() }) {
                Text(showingSafetyChecklist ? "Hide safety tips" : "Before you pay: safety tips")
                    .font(IDS.Typography.caption).foregroundColor(IDS.Colors.brand)
            }
            if showingSafetyChecklist {
                VStack(alignment: .leading, spacing: 5) {
                    Text("Before you meet or pay").font(IDS.Typography.sectionLabel).foregroundColor(IDS.Colors.textPrimary)
                    Text("• Meet in a safe public place\n• Inspect the item before payment\n• Keep the price and handover in Itunda chat\n• Never share a PIN or send money for an unseen item")
                        .font(IDS.Typography.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            if offering {
                HStack(spacing: 10) {
                    TextField("Your offer (RWF)", text: $offerAmount)
                        .keyboardType(.numberPad)
                        .padding(10)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(10)
                    Button(action: {
                        guard let amount = Double(offerAmount) else { return }
                        offering = false
                        offerAmount = ""
                        onMakeOffer(listing.id, amount)
                    }) {
                        Text("Send").font(.subheadline).bold().foregroundColor(.white)
                            .padding(.horizontal, 16).padding(.vertical, 10)
                            .background(IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(Double(offerAmount) == nil)
                }
            }
            // Real optional "who bought this?" prompt (2026-07-24) -- see backend
            // MarketplaceService.markSold's own doc comment.
            if markingSold {
                TextField("Buyer's phone (optional)", text: $buyerPhone)
                    .keyboardType(.phonePad)
                    .padding(10)
                    .background(IDS.Colors.chipBackground)
                    .cornerRadius(10)
                HStack(spacing: 10) {
                    actionButton("Skip", filled: false) { await markSold(buyerPhoneNumber: nil) }
                    actionButton("Confirm", filled: true) { await markSold(buyerPhoneNumber: buyerPhone.trimmingCharacters(in: .whitespaces)) }
                }
            }
            // Real post-transaction review, preset checklist with asymmetric public/
            // private visibility (2026-07-24) -- see backend HoodReviewService's own
            // doc comment. Only offered once a real buyer was recorded at mark-sold
            // time; no pre-check for "already reviewed" (a real, honest v1 -- a second
            // attempt just surfaces the backend's own REVIEW_ALREADY_SUBMITTED error).
            if isMine, listing.status == "SOLD", listing.buyerId != nil, !reviewSubmitted {
                if showReviewSheet {
                    HoodReviewForm(
                        selectedGoodPoints: $selectedGoodPoints,
                        selectedUncomfortablePoints: $selectedUncomfortablePoints,
                        submitting: submittingReview,
                        onCancel: { showReviewSheet = false },
                        onSubmit: { await submitReview() }
                    )
                } else {
                    actionButton("Rate this buyer", filled: true) { showReviewSheet = true }
                }
            }
            HStack(spacing: 10) {
                if isMine {
                    if listing.status == "ACTIVE" && !markingSold {
                        actionButton("Mark sold", filled: false) { markingSold = true }
                    }
                    if listing.status != "REMOVED" {
                        actionButton("Remove", filled: false) { await remove() }
                    }
                } else if listing.status == "ACTIVE" && !offering {
                    actionButton(busy ? "Starting…" : "Message seller", filled: false) {
                        onMessageSeller(listing.id)
                    }
                    actionButton("Make an offer", filled: true) { offering = true }
                    actionButton("Report", filled: false) { showingReportOptions = true }
                }
            }
            if !isMine, listing.status == "ACTIVE", let toLat = listing.latitude, let toLng = listing.longitude {
                Button(action: {
                    if showRoute { showRoute = false } else if myLocation != nil { showRoute = true } else { locationFetcher.requestLocation() }
                }) {
                    Text(showRoute ? "Hide directions" : "🚗 Directions to this seller")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(IDS.Colors.chipBackground).cornerRadius(12)
                }
                if showRoute, let myLocation {
                    RouteMiniMap(fromLat: myLocation.latitude, fromLng: myLocation.longitude, toLat: toLat, toLng: toLng, fromLabel: "You", toLabel: listing.title)
                }
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .confirmationDialog("Report this listing", isPresented: $showingReportOptions, titleVisibility: .visible) {
            Button("Item is unavailable or misleading") { Task { await report("The item appears unavailable or misleading") } }
            Button("Unsafe payment or contact request") { Task { await report("The seller made an unsafe payment or contact request") } }
            Button("Prohibited or suspicious item") { Task { await report("The item appears prohibited or suspicious") } }
        } message: { Text("Reports go to Itunda’s review queue.") }
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                showRoute = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { newValue in
            if let newValue { error = newValue }
        }
    }

    private func actionButton(_ label: String, filled: Bool, action: @escaping () async -> Void) -> some View {
        Button(action: { Task { await action() } }) {
            Text(label)
                .font(.subheadline).bold()
                .foregroundColor(filled ? .white : IDS.Colors.textPrimary)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(filled ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .cornerRadius(12)
        }
        .disabled(busy)
    }

    private func markSold(buyerPhoneNumber: String?) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.markListingSold(listing.id, buyerPhoneNumber: buyerPhoneNumber?.isEmpty == true ? nil : buyerPhoneNumber)
            markingSold = false
            onChanged()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    private func submitReview() async {
        submittingReview = true
        defer { submittingReview = false }
        do {
            _ = try await NetworkClient.shared.submitListingReview(
                listing.id, goodPoints: Array(selectedGoodPoints), uncomfortablePoints: Array(selectedUncomfortablePoints),
            )
            reviewSubmitted = true
            showReviewSheet = false
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func remove() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.removeListing(listing.id)
            onChanged()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func report(_ reason: String) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.reportHoodContent(targetType: "MARKETPLACE_LISTING", targetId: listing.id, reason: reason)
            error = "Thanks. Your report was sent for review."
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            error = "You already reported this listing."
        } catch {
            self.error = "Couldn't send the report. Check your connection and try again."
        }
    }
}

// Real Marketplace listing wishlist view (2026-07-21) -- iOS port of bank-mfe's
// ListingWishlistView, same day. Lists every real favorited listing (title/price/
// category straight from the favorites endpoint); a favorited-then-deleted listing's
// "no longer available" fallback is the backend's own responsibility
// (ListingFavoriteService.kt already resolves that server-side).
private struct ListingWishlistView: View {
    let onRemoved: () -> Void

    @State private var favorites: [FavoriteListingDto]?
    @State private var error: String?
    @State private var removingId: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)
            } else if favorites == nil {
                HoodFeedSkeleton()
            } else if favorites!.isEmpty {
                Text("No saved listings yet -- tap ♡ on any listing to save it here.")
                    .foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(favorites!) { f in
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(f.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            Text("\(f.category) · \(Int(f.price)) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        Spacer()
                        Button(action: { Task { await remove(f.listingId) } }) {
                            Text(removingId == f.listingId ? "Removing…" : "Remove")
                                .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(IDS.Colors.chipBackground).cornerRadius(10)
                        }
                        .disabled(removingId == f.listingId)
                    }
                    .padding(16)
                    .background(IDS.Colors.card)
                    .cornerRadius(IDS.Layout.cardCornerRadius)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyFavoriteListings()
            favorites = res.favorites
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func remove(_ listingId: String) async {
        removingId = listingId
        defer { removingId = nil }
        do {
            _ = try await NetworkClient.shared.removeListingFavorite(listingId)
            favorites = favorites?.filter { $0.listingId != listingId }
            onRemoved()
        } catch {
            self.error = "Couldn't remove this item. Check your connection and try again."
        }
    }
}

// ============================== COMMUNITY (동네생활) ==============================

private struct CommunityContent: View {
    private enum CommunityView { case browse, nearby, neighborhood, mine }

    /// Real "join meetup" hand-off to Talk (2026-07-24) -- same
    /// pendingConversationId/onSwitchToTalk pair Marketplace/Jobs/Property already
    /// share, extended (see TalkScreen.swift's own doc comment) to also accept a real
    /// GroupConversation id, not just a 1:1 conversation id.
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    @State private var view: CommunityView = .browse
    @State private var categories: [CommunityCategoryDto] = []
    @State private var activeCategory: String?
    @State private var posts: [CommunityPostDto]?
    // Real 같이해요 (join-together) group join counts (2026-07-24) -- postId -> real
    // member count of that meetup's group chat, closing docs/DESIGN_REFERENCES.md
    // Section 4 recommendation #4.
    @State private var joinedCounts: [String: Int] = [:]
    @State private var joiningPostId: String?
    @State private var error: String?
    @State private var showNewPost = false
    @State private var openPostId: String?
    @State private var neighborhoodName: String?
    @State private var neighborhoodChecked = false
    @StateObject private var locationFetcher = HoodLocationFetcher()
    private let currentUserId = KeychainTokenStore.shared.getUserId()
    @State private var favoriteIds: Set<String> = []
    @State private var favoritingId: String?

    var body: some View {
        if let openPostId {
            CommunityPostDetailView(postId: openPostId, onBack: { self.openPostId = nil; Task { await load() } })
        } else {
            ScrollView {
                VStack(spacing: IDS.Layout.cardGap) {
                    Picker("", selection: $view) {
                        Text("Feed").tag(CommunityView.browse)
                        Text("Near me").tag(CommunityView.nearby)
                        Text("Neighborhood").tag(CommunityView.neighborhood)
                        Text("My posts").tag(CommunityView.mine)
                    }
                    .pickerStyle(.segmented)

                    if (view == .browse || view == .neighborhood) && !categories.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(categories) { c in
                                    let active = activeCategory == c.id
                                    Text(c.label)
                                        .font(.caption).bold()
                                        .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 12).padding(.vertical, 6)
                                        .background(active ? IDS.Colors.brand : Color.clear)
                                        .overlay(RoundedRectangle(cornerRadius: 999).stroke(active ? IDS.Colors.brand : IDS.Colors.textSecondary.opacity(0.3), lineWidth: 1))
                                        .cornerRadius(999)
                                        .onTapGesture { activeCategory = active ? nil : c.id }
                                }
                            }
                        }
                    }

                    if showNewPost {
                        NewCommunityPostForm(categories: categories, onCreated: { showNewPost = false; Task { await load() } }, onCancel: { showNewPost = false })
                    } else {
                        if view == .mine {
                            Button(action: { showNewPost = true }) {
                                Text("+ Write a post")
                                    .font(IDS.Typography.bodyBold).foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                                    .background(IDS.Colors.brand).cornerRadius(14)
                            }
                        } else {
                            Button(action: { showNewPost = true }) {
                                Text("Ask your neighbors")
                                    .font(IDS.Typography.bodyBold).foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                                    .background(IDS.Colors.brand).cornerRadius(14)
                            }
                        }
                    }

                    if view == .neighborhood && neighborhoodChecked && neighborhoodName == nil {
                        NeighborhoodSetupPrompt(onDone: { _ in Task { await load() } })
                    }
                    if view == .neighborhood, let neighborhoodName {
                        Text("Your neighborhood: \(neighborhoodName)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    if let error {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(error).foregroundColor(.red).font(.subheadline)
                            Button("Retry") { Task { await load() } }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    } else if posts == nil {
                        HoodFeedSkeleton()
                    } else if posts!.isEmpty && (view != .neighborhood || neighborhoodName != nil) {
                        Text(
                            view == .browse ? "No posts yet."
                                : view == .nearby ? "No posts near you yet."
                                : view == .neighborhood ? "No posts in your neighborhood yet."
                                : "You haven't posted anything yet."
                        ).foregroundColor(IDS.Colors.textSecondary)
                    } else if !posts!.isEmpty {
                        // Real 같이해요 (join-together) pinned mid-feed slot
                        // (2026-07-24) -- Karrot's real board gives meetup posts a
                        // dedicated slot instead of mixing them purely chronologically
                        // (docs/DESIGN_REFERENCES.md Section 4 recommendation #4). "My
                        // posts" stays plain chronological.
                        let meetups = view != .mine ? posts!.filter { $0.category == "meetup" } : []
                        let regular = view != .mine ? posts!.filter { $0.category != "meetup" } : posts!
                        if !meetups.isEmpty {
                            Text("🎉 Meetups").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            ForEach(meetups) { post in
                                CommunityPostCard(
                                    post: post,
                                    categoryLabel: categories.first(where: { $0.id == post.category })?.label ?? post.category,
                                    isMine: post.authorId == currentUserId,
                                    onOpen: { openPostId = post.id },
                                    onRemoved: { Task { await load() } },
                                    joinedCount: joinedCounts[post.id] ?? 0,
                                    joining: joiningPostId == post.id,
                                    onJoin: { Task { await joinMeetup(post.id) } }
                                )
                            }
                        }
                        ForEach(regular) { post in
                            CommunityPostCard(
                                post: post,
                                categoryLabel: categories.first(where: { $0.id == post.category })?.label ?? post.category,
                                isMine: view == .mine || post.authorId == currentUserId,
                                onOpen: { openPostId = post.id },
                                onRemoved: { Task { await load() } },
                                joinedCount: joinedCounts[post.id] ?? 0,
                                joining: joiningPostId == post.id,
                                onJoin: { Task { await joinMeetup(post.id) } }
                            )
                        }
                    }
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, IDS.Layout.screenTop)
                .padding(.bottom, IDS.Layout.sectionSpacing)
            }
            .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
            .task {
                if categories.isEmpty {
                    categories = (try? await NetworkClient.shared.getCommunityCategories().categories) ?? []
                }
                locationFetcher.onLocation = { coordinate in Task { await loadNearby(coordinate) } }
                await load()
            }
            .onChange(of: view) { _ in Task { await load() } }
            .onChange(of: activeCategory) { _ in Task { await load() } }
            .onChange(of: locationFetcher.errorMessage) { message in
                guard view == .nearby, let message else { return }
                error = message + " You can still use Feed or Neighborhood."
                posts = []
            }
        }
    }

    private func load() async {
        posts = nil
        if view == .nearby {
            locationFetcher.requestLocation()
            return
        }
        if view == .neighborhood {
            neighborhoodChecked = false
            do {
                let profile = try await NetworkClient.shared.getProfile()
                let res = try await NetworkClient.shared.getCommunityPostsMyNeighborhood(category: activeCategory)
                neighborhoodName = profile.user.neighborhood
                posts = res.posts
                joinedCounts = res.joinedCounts ?? [:]
                error = nil
            } catch let NetworkError.httpError(statusCode) where statusCode == 400 {
                neighborhoodName = nil
                posts = []
                error = nil
            } catch {
                self.error = "Couldn't reach itunda. Check your connection and try again."
            }
            neighborhoodChecked = true
            return
        }
        do {
            let res = view == .browse ? try await NetworkClient.shared.browseCommunityPosts(category: activeCategory) : try await NetworkClient.shared.getMyCommunityPosts()
            posts = res.posts
            joinedCounts = res.joinedCounts ?? [:]
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadNearby(_ coordinate: CLLocationCoordinate2D) async {
        do {
            let res = try await NetworkClient.shared.getNearbyCommunityPosts(lat: coordinate.latitude, lng: coordinate.longitude)
            posts = res.posts
            joinedCounts = res.joinedCounts ?? [:]
            error = nil
        } catch {
            self.error = "Couldn't load nearby posts. Check your connection and try again."
            posts = []
        }
    }

    // Real 같이해요 (join-together) explicit 참여하기 tap (2026-07-24) -- closes
    // docs/DESIGN_REFERENCES.md Section 4 recommendation #4. Reuses the exact same
    // pendingConversationId/onSwitchToTalk hand-off Marketplace/Jobs/Property already
    // share -- TalkScreen.swift's own tryOpenPending() was extended to also check
    // `groups`, so a real GroupConversation id works here too.
    private func joinMeetup(_ postId: String) async {
        joiningPostId = postId
        defer { joiningPostId = nil }
        do {
            let res = try await NetworkClient.shared.joinCommunityMeetup(postId)
            joinedCounts[postId, default: 0] += 1
            pendingConversationId = res.groupId
            onSwitchToTalk()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct NewCommunityPostForm: View {
    let categories: [CommunityCategoryDto]
    let onCreated: () -> Void
    let onCancel: () -> Void

    @State private var category: String
    @State private var title = ""
    @State private var postBody = ""
    @State private var error: String?
    @State private var submitting = false
    @State private var shareLocation = false
    @State private var myLocation: CLLocationCoordinate2D?
    @StateObject private var locationFetcher = HoodLocationFetcher()
    // Real 당근모임-style structured event date + capacity (2026-07-25 backend/Android;
    // ported to iOS 2026-07-28) -- see backend CommunityService.createPost's own doc
    // comment for why eventDate is required for this one category.
    @State private var eventDateText = ""
    @State private var eventTimeText = ""
    @State private var capacityText = ""

    init(categories: [CommunityCategoryDto], onCreated: @escaping () -> Void, onCancel: @escaping () -> Void) {
        self.categories = categories
        self.onCreated = onCreated
        self.onCancel = onCancel
        _category = State(initialValue: categories.first?.id ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Write a post").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(categories) { c in
                        let selected = category == c.id
                        Text(c.label)
                            .font(.caption).bold()
                            .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(selected ? IDS.Colors.brand : IDS.Colors.chipBackground)
                            .cornerRadius(999)
                            .onTapGesture { category = c.id }
                    }
                }
            }
            TextField("Title", text: $title).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            TextField("What's going on in the neighborhood?", text: $postBody).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            if category == "meetup" {
                HStack(spacing: 8) {
                    TextField("Date (YYYY-MM-DD)", text: $eventDateText).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
                    TextField("Time (HH:mm)", text: $eventTimeText).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
                }
                TextField("Max people (optional -- blank means unlimited)", text: $capacityText)
                    .keyboardType(.numberPad)
                    .padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            }
            Button(action: {
                if shareLocation { shareLocation = false } else { locationFetcher.requestLocation() }
            }) {
                Text(shareLocation ? "📍 Location shared with nearby neighbors" : "📍 Share location for nearby neighbors (optional)")
                    .font(.caption).foregroundColor(shareLocation ? IDS.Colors.brand : IDS.Colors.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 14).padding(.vertical, 12)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            HStack {
                Button("Cancel", action: onCancel).frame(maxWidth: .infinity)
                Button(action: { Task { await submit() } }) {
                    Text(submitting ? "Posting…" : "Post")
                        .foregroundColor(.white).frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(14)
                }
                .disabled(submitting)
            }
        }
        .padding(20)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                shareLocation = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { message in
            if let message { error = message }
        }
    }

    private func submit() async {
        guard !title.isEmpty, !postBody.isEmpty, !category.isEmpty else {
            error = "Fill in every field."
            return
        }
        var eventDateIso: String?
        var capacity: Int?
        if category == "meetup" {
            guard !eventDateText.isEmpty, !eventTimeText.isEmpty else {
                error = "A meetup needs a real date and time."
                return
            }
            let candidate = "\(eventDateText.trimmingCharacters(in: .whitespaces))T\(eventTimeText.trimmingCharacters(in: .whitespaces)):00Z"
            guard ISO8601DateFormatter().date(from: candidate) != nil else {
                error = "Enter a real date (YYYY-MM-DD) and time (HH:mm)."
                return
            }
            eventDateIso = candidate
            let trimmedCapacity = capacityText.trimmingCharacters(in: .whitespaces)
            if !trimmedCapacity.isEmpty {
                guard let parsedCapacity = Int(trimmedCapacity) else {
                    error = "Max people must be a whole number."
                    return
                }
                capacity = parsedCapacity
            }
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            let location = shareLocation ? myLocation : nil
            _ = try await NetworkClient.shared.createCommunityPost(
                category: category, title: title, body: postBody, latitude: location?.latitude, longitude: location?.longitude,
                eventDate: eventDateIso, capacity: capacity
            )
            onCreated()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct CommunityPostCard: View {
    let post: CommunityPostDto
    let categoryLabel: String
    let isMine: Bool
    let onOpen: () -> Void
    let onRemoved: () -> Void
    var joinedCount: Int = 0
    var joining: Bool = false
    var onJoin: () -> Void = {}

    @State private var busy = false
    @State private var error: String?
    @State private var showingReportOptions = false

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("\(categoryLabel) · \(hoodRelativeTime(post.createdAt))").font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                Spacer()
                if isMine {
                    Button(action: { Task { await remove() } }) {
                        Text("Remove").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(IDS.Colors.chipBackground).cornerRadius(10)
                    }
                    .disabled(busy)
                } else {
                    Button("Report") { showingReportOptions = true }
                        .font(.caption).bold().foregroundColor(IDS.Colors.textSecondary)
                }
            }
            Text(post.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            Text(post.body).font(.subheadline).foregroundColor(IDS.Colors.textSecondary).lineLimit(2)
            Text("❤️ \(post.likeCount) · 💬 \(post.commentCount)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            if !isMine && post.category == "question" {
                Button("Answer this question", action: onOpen)
                    .font(.caption).bold().foregroundColor(IDS.Colors.brand)
            } else if post.category == "meetup" {
                // Real 당근모임-style structured date/capacity display (2026-07-25
                // backend/Android; ported to iOS 2026-07-28) -- see backend
                // CommunityPost.eventDate/capacity's own doc comment.
                if let eventDate = post.eventDate {
                    let capacityLabel = post.capacity.map { " · \(joinedCount)/\($0)" } ?? ""
                    Text("🗓️ \(formatMeetupDate(eventDate))\(capacityLabel)")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                // Real 참여하기 (join) tap (2026-07-24) -- a real join, not just a
                // "view" navigation: it adds the tapper to a real GroupConversation
                // (see backend CommunityService.joinMeetup's own doc comment), shown
                // with a real "N joined" count rather than a bare label.
                if isMine {
                    Button("View meetup", action: onOpen)
                        .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                } else {
                    HStack(spacing: 12) {
                        Button("View meetup", action: onOpen)
                            .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                        Button(action: onJoin) {
                            Text(joining ? "Joining…" : "참여하기 · \(joinedCount) joined")
                                .font(.caption).bold().foregroundColor(.white)
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .background(IDS.Colors.brand).cornerRadius(10)
                        }
                        .disabled(joining)
                    }
                }
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .confirmationDialog("Report this post", isPresented: $showingReportOptions, titleVisibility: .visible) {
            Button("Harassment or hateful content") { Task { await report("The post contains harassment or hateful content") } }
            Button("Spam or misleading information") { Task { await report("The post is spam or misleading information") } }
            Button("Unsafe or illegal content") { Task { await report("The post appears unsafe or illegal") } }
        } message: { Text("Reports go to Itunda’s review queue.") }
        .contentShape(Rectangle())
        .onTapGesture(perform: onOpen)
    }

    private func remove() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.removeCommunityPost(post.id)
            onRemoved()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func report(_ reason: String) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.reportHoodContent(targetType: "COMMUNITY_POST", targetId: post.id, reason: reason)
            error = "Thanks. Your report was sent for review."
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            error = "You already reported this post."
        } catch {
            self.error = "Couldn't send the report. Check your connection and try again."
        }
    }
}

private struct CommunityPostDetailView: View {
    let postId: String
    let onBack: () -> Void

    @State private var post: CommunityPostDto?
    @State private var authorName = ""
    @State private var likedByMe = false
    @State private var comments: [CommunityCommentWithAuthorDto]?
    @State private var commentBody = ""
    @State private var error: String?
    @State private var liking = false
    @State private var commenting = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left") }
                Text("Post").font(IDS.Typography.bodyBold)
                Spacer()
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error {
                        Text(error).font(.caption).foregroundColor(.red)
                    }
                    if let post {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(post.title).font(.title3).bold().foregroundColor(IDS.Colors.textPrimary)
                            Text("by \(authorName)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            Text(post.body).font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                            Button(action: { Task { await toggleLike() } }) {
                                Text(likedByMe ? "❤️ \(post.likeCount)" : "🤍 \(post.likeCount)")
                                    .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                    .padding(.horizontal, 16).padding(.vertical, 10)
                                    .background(IDS.Colors.chipBackground).cornerRadius(12)
                            }
                            .disabled(liking)
                        }
                        .padding(18)
                        .background(IDS.Colors.card)
                        .cornerRadius(IDS.Layout.cardCornerRadius)
                    }

                    Text("Comments").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    if comments == nil {
                        ProgressView().frame(maxWidth: .infinity, minHeight: 60)
                    } else if comments!.isEmpty {
                        Text("No comments yet -- be the first to reply.").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(comments!) { c in
                            VStack(alignment: .leading, spacing: 2) {
                                Text(c.authorName).font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary)
                                Text(c.comment.body).font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(12)
                        }
                    }
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 12)
                .padding(.bottom, 12)
            }

            HStack {
                TextField("Add a comment", text: $commentBody)
                    .padding(12).background(IDS.Colors.chipBackground).cornerRadius(10)
                Button(action: { Task { await addComment() } }) {
                    Text(commenting ? "…" : "Send").foregroundColor(.white)
                        .padding(.horizontal, 16).padding(.vertical, 12)
                        .background(commentBody.isEmpty ? IDS.Colors.textSecondary : IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(commenting || commentBody.isEmpty)
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
    }

    private func load() async {
        do {
            let detail = try await NetworkClient.shared.getCommunityPost(postId)
            post = detail.post; authorName = detail.authorName; likedByMe = detail.likedByMe
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
        comments = (try? await NetworkClient.shared.getCommunityComments(postId).comments) ?? comments
    }

    private func toggleLike() async {
        liking = true
        defer { liking = false }
        do {
            let liked = try await NetworkClient.shared.toggleCommunityLike(postId).liked
            likedByMe = liked
            if let p = post { post = CommunityPostDto(id: p.id, authorId: p.authorId, category: p.category, title: p.title, body: p.body, status: p.status, likeCount: p.likeCount + (liked ? 1 : -1), commentCount: p.commentCount, createdAt: p.createdAt, latitude: p.latitude, longitude: p.longitude) }
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func addComment() async {
        commenting = true
        defer { commenting = false }
        do {
            _ = try await NetworkClient.shared.addCommunityComment(postId, body: commentBody)
            commentBody = ""
            await load()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

// ============================== JOBS (당근알바) ==============================

private struct JobsContent: View {
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    // Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6, see backend JobPostRepository's own doc comment.
    private enum JobsView { case browse, nearby, neighborhood, mine, worked, wishlist }

    @State private var view: JobsView = .browse
    @State private var categories: [JobCategoryDto] = []
    @State private var activeCategory: String?
    @State private var posts: [JobPostDto]?
    // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment.
    @State private var trustScores: [String: Int] = [:]
    @State private var error: String?
    @State private var showNewPost = false
    @State private var neighborhoodName: String?
    @State private var neighborhoodChecked = false
    @StateObject private var locationFetcher = HoodLocationFetcher()
    private let currentUserId = KeychainTokenStore.shared.getUserId()
    @State private var favoriteIds: Set<String> = []
    @State private var favoritingId: String?
    @State private var favoriteNotice: String?
    @State private var nearbyRadiusKm = 3.0

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                    Picker("", selection: $view) {
                        Text("Find work").tag(JobsView.browse)
                        Text("Near me").tag(JobsView.nearby)
                        Text("Neighborhood").tag(JobsView.neighborhood)
                        Text("My posts").tag(JobsView.mine)
                        Text("Jobs I did").tag(JobsView.worked)
                        Text("Saved").tag(JobsView.wishlist)
                }
                .pickerStyle(.segmented)

                if let favoriteNotice { Text(favoriteNotice).font(.caption).foregroundColor(IDS.Colors.brand).frame(maxWidth: .infinity, alignment: .leading) }

                if view == .nearby { HoodRadiusControl(radiusKm: $nearbyRadiusKm, onChanged: { locationFetcher.requestLocation() }) }

                HStack(alignment: .top, spacing: 8) {
                    Text("Safe work")
                        .font(IDS.Typography.sectionLabel)
                        .foregroundColor(IDS.Colors.textPrimary)
                    Text("Never pay a fee to get a job. Keep pay and work details in Itunda chat before you travel.")
                        .font(IDS.Typography.caption)
                        .foregroundColor(IDS.Colors.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12)
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)

                if (view == .browse || view == .neighborhood) && !categories.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(categories) { c in
                                let active = activeCategory == c.id
                                Text(c.label)
                                    .font(.caption).bold()
                                    .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                                    .padding(.horizontal, 12).padding(.vertical, 6)
                                    .background(active ? IDS.Colors.brand : Color.clear)
                                    .overlay(RoundedRectangle(cornerRadius: 999).stroke(active ? IDS.Colors.brand : IDS.Colors.textSecondary.opacity(0.3), lineWidth: 1))
                                    .cornerRadius(999)
                                    .onTapGesture { activeCategory = active ? nil : c.id }
                            }
                        }
                    }
                }

                if view == .mine {
                    if showNewPost {
                        NewJobPostForm(categories: categories, onCreated: { showNewPost = false; Task { await load() } }, onCancel: { showNewPost = false })
                    } else {
                        Button(action: { showNewPost = true }) {
                            Text("+ Post a job")
                                .font(IDS.Typography.bodyBold).foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 14)
                                .background(IDS.Colors.brand).cornerRadius(14)
                        }
                    }
                }

                if view == .neighborhood && neighborhoodChecked && neighborhoodName == nil {
                    NeighborhoodSetupPrompt(onDone: { _ in Task { await load() } })
                }
                if view == .neighborhood, let neighborhoodName {
                    Text("Your neighborhood: \(neighborhoodName)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                if view == .wishlist {
                    JobPostWishlistView(onRemoved: { Task { await loadFavoriteIds() } })
                } else if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                } else if posts == nil {
                    HoodFeedSkeleton()
                } else if posts!.isEmpty && (view != .neighborhood || neighborhoodName != nil) {
                    Text(
                        view == .browse ? "No jobs posted yet."
                            : view == .neighborhood ? "No jobs in your neighborhood yet."
                            : view == .worked ? "No completed jobs recorded yet."
                            : "You haven't posted any jobs yet."
                    ).foregroundColor(IDS.Colors.textSecondary)
                } else if !posts!.isEmpty {
                    ForEach(posts!) { post in
                        JobPostCard(
                            post: post,
                            categoryLabel: categories.first(where: { $0.id == post.category })?.label ?? post.category,
                            isMine: view == .mine || post.posterId == currentUserId,
                            onChanged: { Task { await load() } },
                            onContact: { Task { await contact(post.id) } },
                            favorited: favoriteIds.contains(post.id),
                            favoriteBusy: favoritingId == post.id,
                            onToggleFavorite: { Task { await toggleFavorite(post.id) } },
                            posterTrustScore: trustScores[post.posterId]
                        )
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            if categories.isEmpty {
                categories = (try? await NetworkClient.shared.getJobCategories().categories) ?? []
            }
            await load()
            locationFetcher.onLocation = { coordinate in Task { await loadNearby(coordinate) } }
        }
        .task { await loadFavoriteIds() }
        .onChange(of: view) { _ in Task { await load() } }
        .onChange(of: activeCategory) { _ in Task { await load() } }
        .onChange(of: locationFetcher.errorMessage) { message in
            guard view == .nearby, let message else { return }
            error = message + " You can still use Find work or Neighborhood."
            posts = []
        }
    }

    private func load() async {
        posts = nil
        if view == .wishlist {
            posts = []
            error = nil
            return
        }
        if view == .nearby {
            locationFetcher.requestLocation()
            return
        }
        if view == .neighborhood {
            neighborhoodChecked = false
            do {
                let profile = try await NetworkClient.shared.getProfile()
                let res = try await NetworkClient.shared.getJobPostsMyNeighborhood(category: activeCategory)
                neighborhoodName = profile.user.neighborhood
                posts = res.posts
                trustScores = res.trustScores ?? [:]
                error = nil
            } catch let NetworkError.httpError(statusCode) where statusCode == 400 {
                neighborhoodName = nil
                posts = []
                error = nil
            } catch {
                self.error = "Couldn't reach itunda. Check your connection and try again."
            }
            neighborhoodChecked = true
            return
        }
        do {
            let res: JobPostsResponse
            switch view {
            case .browse: res = try await NetworkClient.shared.browseJobPosts(category: activeCategory)
            case .worked: res = try await NetworkClient.shared.getMyWorkedJobPosts()
            default: res = try await NetworkClient.shared.getMyJobPosts()
            }
            posts = res.posts
            trustScores = res.trustScores ?? [:]
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadNearby(_ coordinate: CLLocationCoordinate2D) async {
        do {
            let res = try await NetworkClient.shared.getNearbyJobPosts(lat: coordinate.latitude, lng: coordinate.longitude, radiusKm: nearbyRadiusKm)
            posts = res.posts
            trustScores = res.trustScores ?? [:]
            error = nil
        } catch {
            self.error = "Couldn't load nearby work. Check your connection and try again."
            posts = []
        }
    }

    private func loadFavoriteIds() async {
        if let favorites = try? await NetworkClient.shared.getMyFavoriteJobPosts().favorites {
            favoriteIds = Set(favorites.map(\.jobPostId))
        }
    }

    private func toggleFavorite(_ jobPostId: String) async {
        favoritingId = jobPostId
        defer { favoritingId = nil }
        do {
            if favoriteIds.contains(jobPostId) {
                _ = try await NetworkClient.shared.removeJobPostFavorite(jobPostId)
                favoriteIds.remove(jobPostId)
                favoriteNotice = "Removed from saved jobs."
            } else {
                _ = try await NetworkClient.shared.addJobPostFavorite(jobPostId)
                favoriteIds.insert(jobPostId)
                favoriteNotice = "Saved to your jobs list."
            }
        } catch {
            self.error = "Couldn't update your saved jobs. Check your connection and try again."
        }
    }

    private func contact(_ jobPostId: String) async {
        do {
            let res = try await NetworkClient.shared.contactPoster(jobPostId)
            pendingConversationId = res.conversation.id
            onSwitchToTalk()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct JobPostWishlistView: View {
    let onRemoved: () -> Void

    @State private var favorites: [FavoriteJobPostDto]?
    @State private var error: String?
    @State private var removingId: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
            } else if favorites == nil {
                HoodFeedSkeleton()
            } else if favorites!.isEmpty {
                Text("No saved jobs yet — tap ♡ on a job to keep it here.")
                    .foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(favorites!) { favorite in
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(favorite.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            Text("\(favorite.category) · \(Int(favorite.payAmount)) RWF")
                                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        Spacer()
                        Button(action: { Task { await remove(favorite.jobPostId) } }) {
                            Text(removingId == favorite.jobPostId ? "Removing…" : "Remove")
                                .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(IDS.Colors.chipBackground).cornerRadius(10)
                        }
                        .disabled(removingId != nil)
                    }
                    .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            favorites = try await NetworkClient.shared.getMyFavoriteJobPosts().favorites
            error = nil
        } catch {
            self.error = "Couldn't load your saved jobs. Check your connection and try again."
        }
    }

    private func remove(_ jobPostId: String) async {
        removingId = jobPostId
        defer { removingId = nil }
        do {
            _ = try await NetworkClient.shared.removeJobPostFavorite(jobPostId)
            favorites?.removeAll { $0.jobPostId == jobPostId }
            onRemoved()
        } catch {
            self.error = "Couldn't remove this saved job. Check your connection and try again."
        }
    }
}

private struct NewJobPostForm: View {
    let categories: [JobCategoryDto]
    let onCreated: () -> Void
    let onCancel: () -> Void

    @State private var category: String
    @State private var title = ""
    @State private var description = ""
    @State private var payType = "HOURLY"
    @State private var payAmount = ""
    @State private var error: String?
    @State private var submitting = false
    @State private var shareLocation = false
    @State private var myLocation: CLLocationCoordinate2D?
    @StateObject private var locationFetcher = HoodLocationFetcher()

    init(categories: [JobCategoryDto], onCreated: @escaping () -> Void, onCancel: @escaping () -> Void) {
        self.categories = categories
        self.onCreated = onCreated
        self.onCancel = onCancel
        _category = State(initialValue: categories.first?.id ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Post a job").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(categories) { c in
                        let selected = category == c.id
                        Text(c.label)
                            .font(.caption).bold()
                            .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(selected ? IDS.Colors.brand : IDS.Colors.chipBackground)
                            .cornerRadius(999)
                            .onTapGesture { category = c.id }
                    }
                }
            }
            TextField("What do you need done?", text: $title).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            TextField("Describe the work", text: $description).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            HStack {
                Picker("", selection: $payType) {
                    Text("Per hour").tag("HOURLY")
                    Text("Fixed price").tag("FIXED")
                }
                .pickerStyle(.segmented)
                TextField("Pay (RWF)", text: $payAmount).keyboardType(.numberPad).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            }
            Button(action: {
                if shareLocation { shareLocation = false } else { locationFetcher.requestLocation() }
            }) {
                Text(shareLocation ? "📍 Work location shared with nearby applicants" : "📍 Share work location for nearby applicants (optional)")
                    .font(.caption).foregroundColor(shareLocation ? IDS.Colors.brand : IDS.Colors.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 14).padding(.vertical, 12)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            HStack {
                Button("Cancel", action: onCancel).frame(maxWidth: .infinity)
                Button(action: { Task { await submit() } }) {
                    Text(submitting ? "Posting…" : "Post job")
                        .foregroundColor(.white).frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(14)
                }
                .disabled(submitting)
            }
        }
        .padding(20)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                shareLocation = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { message in
            if let message { error = message }
        }
    }

    private func submit() async {
        guard let amount = Double(payAmount), amount > 0, !title.isEmpty, !description.isEmpty, !category.isEmpty else {
            error = "Fill in every field with a real pay amount."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            let location = shareLocation ? myLocation : nil
            _ = try await NetworkClient.shared.createJobPost(category: category, title: title, description: description, payType: payType, payAmount: amount, latitude: location?.latitude, longitude: location?.longitude)
            onCreated()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct JobPostCard: View {
    let post: JobPostDto
    let categoryLabel: String
    let isMine: Bool
    let onChanged: () -> Void
    let onContact: () -> Void
    var favorited: Bool = false
    var favoriteBusy: Bool = false
    var onToggleFavorite: () -> Void = {}
    var posterTrustScore: Int?

    @State private var busy = false
    @State private var error: String?
    @State private var myLocation: CLLocationCoordinate2D?
    @State private var showRoute = false
    @StateObject private var locationFetcher = HoodLocationFetcher()
    @State private var showingReportOptions = false

    // Real optional worker identification at mark-filled time (2026-07-24) -- see
    // backend JobPostService.markFilled's own doc comment.
    @State private var markingFilled = false
    @State private var workerPhone = ""

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @State private var showReviewSheet = false
    @State private var selectedGoodPoints: Set<String> = []
    @State private var selectedUncomfortablePoints: Set<String> = []
    @State private var submittingReview = false
    @State private var reviewSubmitted = false

    private var payLabel: String {
        let base = "\(Int(post.payAmount)) RWF"
        return post.payType == "HOURLY" ? "\(base)/hr" : base
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                HStack(spacing: 6) {
                    Text("\(categoryLabel) · \(hoodRelativeTime(post.createdAt))").font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                    if post.status == "FILLED" {
                        Text("FILLED").font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                }
                Spacer()
                if !isMine {
                    Button(action: onToggleFavorite) {
                        Image(systemName: favorited ? "heart.fill" : "heart")
                            .foregroundColor(favorited ? .red : IDS.Colors.textSecondary)
                    }
                    .disabled(favoriteBusy)
                    .padding(.trailing, 6)
                }
                Text(payLabel).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            Text(post.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
            // comment. Only shown for someone else's post.
            if !isMine, let posterTrustScore {
                TrustBadge(score: posterTrustScore)
            }
            Text(post.description).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            // Real optional "who did you hire?" prompt (2026-07-24) -- see backend
            // JobPostService.markFilled's own doc comment.
            if markingFilled {
                TextField("Worker's phone (optional)", text: $workerPhone)
                    .keyboardType(.phonePad)
                    .padding(10)
                    .background(IDS.Colors.chipBackground)
                    .cornerRadius(10)
                HStack(spacing: 10) {
                    actionButton("Skip", filled: false) { await markFilled(workerPhoneNumber: nil) }
                    actionButton("Confirm", filled: true) { await markFilled(workerPhoneNumber: workerPhone.trimmingCharacters(in: .whitespaces)) }
                }
            }
            // Real post-transaction review, preset checklist with asymmetric public/
            // private visibility (2026-07-24) -- see backend HoodReviewService's own
            // doc comment. Only offered once a real worker was recorded at mark-filled
            // time; no pre-check for "already reviewed" (a real, honest v1 -- a second
            // attempt just surfaces the backend's own REVIEW_ALREADY_SUBMITTED error).
            if isMine, post.status == "FILLED", post.workerId != nil, !reviewSubmitted {
                if showReviewSheet {
                    HoodReviewForm(
                        selectedGoodPoints: $selectedGoodPoints,
                        selectedUncomfortablePoints: $selectedUncomfortablePoints,
                        submitting: submittingReview,
                        onCancel: { showReviewSheet = false },
                        onSubmit: { await submitReview() }
                    )
                } else {
                    actionButton("Rate this worker", filled: true) { showReviewSheet = true }
                }
            }
            HStack(spacing: 10) {
                if isMine {
                    if post.status == "OPEN" && !markingFilled {
                        actionButton("Mark filled", filled: false) { markingFilled = true }
                    }
                    if post.status != "REMOVED" {
                        actionButton("Remove", filled: false) { await remove() }
                    }
                } else if post.status == "OPEN" {
                    actionButton("Message poster", filled: true) { onContact() }
                    actionButton("Report", filled: false) { showingReportOptions = true }
                }
            }
            if !isMine, post.status == "OPEN", let toLat = post.latitude, let toLng = post.longitude {
                Button(action: {
                    if showRoute { showRoute = false } else if myLocation != nil { showRoute = true } else { locationFetcher.requestLocation() }
                }) {
                    Text(showRoute ? "Hide directions" : "🚗 Directions to this work")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(IDS.Colors.chipBackground).cornerRadius(12)
                }
                if showRoute, let myLocation {
                    RouteMiniMap(fromLat: myLocation.latitude, fromLng: myLocation.longitude, toLat: toLat, toLng: toLng, fromLabel: "You", toLabel: post.title)
                }
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .confirmationDialog("Report this job", isPresented: $showingReportOptions, titleVisibility: .visible) {
            Button("Asks for money or a fee") { Task { await report("The post asks applicants to pay money or a fee") } }
            Button("Pay or work details are misleading") { Task { await report("The pay or work details appear misleading") } }
            Button("Looks unsafe or illegal") { Task { await report("The post appears unsafe or illegal") } }
        } message: { Text("Reports go to Itunda’s review queue.") }
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                showRoute = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { message in
            if let message { error = message }
        }
    }

    private func actionButton(_ label: String, filled: Bool, action: @escaping () async -> Void) -> some View {
        Button(action: { Task { await action() } }) {
            Text(label)
                .font(.subheadline).bold()
                .foregroundColor(filled ? .white : IDS.Colors.textPrimary)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(filled ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .cornerRadius(12)
        }
        .disabled(busy)
    }

    private func markFilled(workerPhoneNumber: String?) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.markJobPostFilled(post.id, workerPhoneNumber: workerPhoneNumber?.isEmpty == true ? nil : workerPhoneNumber)
            markingFilled = false
            onChanged()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    private func submitReview() async {
        submittingReview = true
        defer { submittingReview = false }
        do {
            _ = try await NetworkClient.shared.submitJobPostReview(
                post.id, goodPoints: Array(selectedGoodPoints), uncomfortablePoints: Array(selectedUncomfortablePoints),
            )
            reviewSubmitted = true
            showReviewSheet = false
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func remove() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.removeJobPost(post.id)
            onChanged()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func report(_ reason: String) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.reportHoodContent(targetType: "JOB_POST", targetId: post.id, reason: reason)
            error = "Thanks. Your report was sent for review."
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            error = "You already reported this job."
        } catch {
            self.error = "Couldn't send the report. Check your connection and try again."
        }
    }
}

// ============================== PROPERTY (당근부동산) ==============================

private struct PropertyContent: View {
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    // Real "Places I got" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6, see backend PropertyListingRepository's own doc comment.
    private enum PropertyView { case browse, nearby, neighborhood, mine, acquired, saved }

    @State private var view: PropertyView = .browse
    @State private var propertyTypes: [PropertyTypeDto] = []
    @State private var listingTypeFilter: String?
    @State private var propertyTypeFilter: String?
    @State private var listings: [PropertyListingDto]?
    // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment.
    @State private var trustScores: [String: Int] = [:]
    @State private var error: String?
    @State private var showNewListing = false
    @State private var neighborhoodName: String?
    @State private var neighborhoodChecked = false
    @StateObject private var locationFetcher = HoodLocationFetcher()
    private let currentUserId = KeychainTokenStore.shared.getUserId()
    @State private var favoriteIds: Set<String> = []
    @State private var favoritingId: String?
    @State private var favoriteNotice: String?
    @State private var nearbyRadiusKm = 3.0

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                Picker("", selection: $view) {
                    Text("Browse").tag(PropertyView.browse)
                    Text("Near me").tag(PropertyView.nearby)
                    Text("Neighborhood").tag(PropertyView.neighborhood)
                    Text("My listings").tag(PropertyView.mine)
                    Text("Places I got").tag(PropertyView.acquired)
                    Text("Saved").tag(PropertyView.saved)
                }
                .pickerStyle(.segmented)

                if let favoriteNotice { Text(favoriteNotice).font(.caption).foregroundColor(IDS.Colors.brand).frame(maxWidth: .infinity, alignment: .leading) }

                if view == .nearby { HoodRadiusControl(radiusKm: $nearbyRadiusKm, onChanged: { locationFetcher.requestLocation() }) }

                if view == .browse {
                    HStack(spacing: 6) {
                        ForEach([("RENT", "For rent"), ("SALE", "For sale")], id: \.0) { v, label in
                            let active = listingTypeFilter == v
                            Text(label)
                                .font(.caption).bold()
                                .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .background(active ? IDS.Colors.brand : Color.clear)
                                .overlay(RoundedRectangle(cornerRadius: 999).stroke(active ? IDS.Colors.brand : IDS.Colors.textSecondary.opacity(0.3), lineWidth: 1))
                                .cornerRadius(999)
                                .onTapGesture { listingTypeFilter = active ? nil : v }
                        }
                        Spacer()
                    }
                    if !propertyTypes.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(propertyTypes) { t in
                                    let active = propertyTypeFilter == t.id
                                    Text(t.label)
                                        .font(.caption).bold()
                                        .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 12).padding(.vertical, 6)
                                        .background(active ? IDS.Colors.brand : Color.clear)
                                        .overlay(RoundedRectangle(cornerRadius: 999).stroke(active ? IDS.Colors.brand : IDS.Colors.textSecondary.opacity(0.3), lineWidth: 1))
                                        .cornerRadius(999)
                                        .onTapGesture { propertyTypeFilter = active ? nil : t.id }
                                }
                            }
                        }
                    }
                }

                if view == .mine {
                    if showNewListing {
                        NewPropertyListingForm(propertyTypes: propertyTypes, onCreated: { showNewListing = false; Task { await load() } }, onCancel: { showNewListing = false })
                    } else {
                        Button(action: { showNewListing = true }) {
                            Text("+ List a property")
                                .font(IDS.Typography.bodyBold).foregroundColor(.white)
                                .frame(maxWidth: .infinity).padding(.vertical, 14)
                                .background(IDS.Colors.brand).cornerRadius(14)
                        }
                    }
                }

                if view == .neighborhood && neighborhoodChecked && neighborhoodName == nil {
                    NeighborhoodSetupPrompt(onDone: { _ in Task { await load() } })
                }
                if view == .neighborhood, let neighborhoodName {
                    Text("Your neighborhood: \(neighborhoodName)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                if view == .saved {
                    PropertyWishlistView(onRemoved: { Task { await loadFavoriteIds() } })
                } else if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                } else if listings == nil {
                    HoodFeedSkeleton()
                } else if listings!.isEmpty && (view != .neighborhood || neighborhoodName != nil) {
                    Text(
                        view == .browse ? "No properties listed yet."
                            : view == .nearby ? "No properties near you yet."
                            : view == .neighborhood ? "No properties in your neighborhood yet."
                            : view == .acquired ? "No properties acquired yet."
                            : "You haven't listed any properties yet."
                    ).foregroundColor(IDS.Colors.textSecondary)
                } else if !listings!.isEmpty {
                    ForEach(listings!) { listing in
                        propertyListingRow(for: listing)
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task {
            if propertyTypes.isEmpty {
                propertyTypes = (try? await NetworkClient.shared.getPropertyTypes().propertyTypes) ?? []
            }
            locationFetcher.onLocation = { coordinate in Task { await loadNearby(coordinate) } }
            await load()
        }
        .task { await loadFavoriteIds() }
        .onChange(of: view) { _ in Task { await load() } }
        .onChange(of: listingTypeFilter) { _ in Task { await load() } }
        .onChange(of: propertyTypeFilter) { _ in Task { await load() } }
        .onChange(of: locationFetcher.errorMessage) { message in
            guard view == .nearby, let message else { return }
            error = message + " You can still use Browse or Neighborhood."
            listings = []
        }
    }

    // Extracted (2026-07-22) -- the previous inline ForEach body (computing
    // propertyTypeLabel/isMine/favorited/favoriteBusy directly inside
    // PropertyListingCard's own call, nested in this large PropertyContent.body) made
    // real Swift type-checking time out ("unable to type-check this expression in
    // reasonable time") -- a real build failure found live via xcodebuild against a
    // real Simulator, not assumed. A concrete, explicitly-typed function the ForEach
    // closure just calls resolves it, the standard fix for this class of SwiftUI
    // compiler timeout.
    private func propertyListingRow(for listing: PropertyListingDto) -> some View {
        PropertyListingRow(
            listing: listing,
            propertyTypeLabel: propertyTypes.first(where: { $0.id == listing.propertyType })?.label ?? listing.propertyType,
            isMine: view == .mine || listing.listerId == currentUserId,
            favorited: favoriteIds.contains(listing.id),
            favoriteBusy: favoritingId == listing.id,
            onChanged: { Task { await load() } },
            onToggleFavorite: { Task { await toggleFavorite(listing.id) } },
            onContact: { Task { await contact(listing.id) } },
            onMakeOffer: { id, amount in Task { await makeOffer(id, amount) } },
            listerTrustScore: trustScores[listing.listerId]
        )
    }

    private func load() async {
        listings = nil
        if view == .saved { listings = []; error = nil; return }
        if view == .nearby {
            locationFetcher.requestLocation()
            return
        }
        if view == .neighborhood {
            neighborhoodChecked = false
            do {
                let profile = try await NetworkClient.shared.getProfile()
                let res = try await NetworkClient.shared.getPropertyListingsMyNeighborhood()
                neighborhoodName = profile.user.neighborhood
                listings = res.listings
                trustScores = res.trustScores ?? [:]
                error = nil
            } catch let NetworkError.httpError(statusCode) where statusCode == 400 {
                neighborhoodName = nil
                listings = []
                error = nil
            } catch {
                self.error = "Couldn't reach itunda. Check your connection and try again."
            }
            neighborhoodChecked = true
            return
        }
        do {
            let res: PropertyListingsResponse
            switch view {
            case .browse: res = try await NetworkClient.shared.browsePropertyListings(listingType: listingTypeFilter, propertyType: propertyTypeFilter)
            case .acquired: res = try await NetworkClient.shared.getMyAcquiredPropertyListings()
            default: res = try await NetworkClient.shared.getMyPropertyListings()
            }
            listings = res.listings
            trustScores = res.trustScores ?? [:]
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadFavoriteIds() async { if let result = try? await NetworkClient.shared.getMyFavoritePropertyListings().favorites { favoriteIds = Set(result.map(\.propertyListingId)) } }
    private func toggleFavorite(_ id: String) async { favoritingId = id; defer { favoritingId = nil }; do { if favoriteIds.contains(id) { _ = try await NetworkClient.shared.removePropertyListingFavorite(id); favoriteIds.remove(id); favoriteNotice = "Removed from saved properties." } else { _ = try await NetworkClient.shared.addPropertyListingFavorite(id); favoriteIds.insert(id); favoriteNotice = "Saved to your properties list." } } catch { self.error = "Couldn't update your saved properties. Check your connection and try again." } }

    private func loadNearby(_ coordinate: CLLocationCoordinate2D) async {
        do {
            let res = try await NetworkClient.shared.getNearbyPropertyListings(lat: coordinate.latitude, lng: coordinate.longitude, radiusKm: nearbyRadiusKm)
            listings = res.listings
            trustScores = res.trustScores ?? [:]
            error = nil
        } catch {
            self.error = "Couldn't load nearby properties. Check your connection and try again."
            listings = []
        }
    }

    private func contact(_ propertyListingId: String) async {
        do {
            let res = try await NetworkClient.shared.contactLister(propertyListingId)
            pendingConversationId = res.conversation.id
            onSwitchToTalk()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func makeOffer(_ propertyListingId: String, _ amount: Double) async {
        do {
            let res = try await NetworkClient.shared.makePropertyOffer(listingId: propertyListingId, amount: amount)
            pendingConversationId = res.offer.conversationId
            onSwitchToTalk()
        } catch {
            self.error = "Couldn't send this offer. Check your connection and try again."
        }
    }
}

private struct NewPropertyListingForm: View {
    let propertyTypes: [PropertyTypeDto]
    let onCreated: () -> Void
    let onCancel: () -> Void

    @State private var listingType = "RENT"
    @State private var propertyType: String
    @State private var title = ""
    @State private var description = ""
    @State private var price = ""
    @State private var bedrooms = ""
    @State private var sizeSqm = ""
    @State private var error: String?
    @State private var submitting = false
    @State private var shareLocation = false
    @State private var myLocation: CLLocationCoordinate2D?
    @StateObject private var locationFetcher = HoodLocationFetcher()

    init(propertyTypes: [PropertyTypeDto], onCreated: @escaping () -> Void, onCancel: @escaping () -> Void) {
        self.propertyTypes = propertyTypes
        self.onCreated = onCreated
        self.onCancel = onCancel
        _propertyType = State(initialValue: propertyTypes.first?.id ?? "")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("List a property").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            Picker("", selection: $listingType) {
                Text("For rent").tag("RENT")
                Text("For sale").tag("SALE")
            }
            .pickerStyle(.segmented)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(propertyTypes) { t in
                        let selected = propertyType == t.id
                        Text(t.label)
                            .font(.caption).bold()
                            .foregroundColor(selected ? .white : IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(selected ? IDS.Colors.brand : IDS.Colors.chipBackground)
                            .cornerRadius(999)
                            .onTapGesture { propertyType = t.id }
                    }
                }
            }
            TextField("e.g. 2-bedroom apartment in Kacyiru", text: $title).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            TextField("Describe the property", text: $description).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            HStack {
                TextField(listingType == "RENT" ? "Rent/mo (RWF)" : "Price (RWF)", text: $price)
                    .keyboardType(.numberPad).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
                TextField("Bedrooms", text: $bedrooms).keyboardType(.numberPad).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
                TextField("Size (m²)", text: $sizeSqm).keyboardType(.numberPad).padding(12).background(IDS.Colors.chipBackground).cornerRadius(12)
            }
            Button(action: {
                if shareLocation { shareLocation = false } else { locationFetcher.requestLocation() }
            }) {
                Text(shareLocation ? "📍 Property area shared for nearby search" : "📍 Share property area for nearby search (optional)")
                    .font(.caption).foregroundColor(shareLocation ? IDS.Colors.brand : IDS.Colors.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading).padding(.horizontal, 14).padding(.vertical, 12)
                    .background(IDS.Colors.chipBackground).cornerRadius(12)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            HStack {
                Button("Cancel", action: onCancel).frame(maxWidth: .infinity)
                Button(action: { Task { await submit() } }) {
                    Text(submitting ? "Listing…" : "List it")
                        .foregroundColor(.white).frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(14)
                }
                .disabled(submitting)
            }
        }
        .padding(20)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                shareLocation = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { message in
            if let message { error = message }
        }
    }

    private func submit() async {
        guard let priceValue = Double(price), priceValue > 0, !title.isEmpty, !description.isEmpty, !propertyType.isEmpty else {
            error = "Fill in every field with a real price."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            let location = shareLocation ? myLocation : nil
            _ = try await NetworkClient.shared.createPropertyListing(
                listingType: listingType, propertyType: propertyType, title: title, description: description, price: priceValue,
                bedrooms: Int(bedrooms), sizeSqm: Double(sizeSqm), latitude: location?.latitude, longitude: location?.longitude,
            )
            onCreated()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct PropertyWishlistView: View {
    let onRemoved: () -> Void
    @State private var favorites: [FavoritePropertyListingDto]?
    @State private var error: String?
    var body: some View {
        Group {
            if let error { VStack(alignment: .leading, spacing: 10) { Text(error).foregroundColor(.red); Button("Retry") { Task { await load() } } }.padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius) }
            else if favorites == nil { HoodFeedSkeleton() }
            else if favorites!.isEmpty { Text("No saved properties yet — tap ♡ on a property to keep it here.").foregroundColor(IDS.Colors.textSecondary) }
            else { ForEach(favorites!) { favorite in HStack { VStack(alignment: .leading) { Text(favorite.title).font(IDS.Typography.bodyBold); Text("\(favorite.listingType == "RENT" ? "For rent" : "For sale") · \(Int(favorite.price)) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary) }; Spacer(); Button("Remove") { Task { await remove(favorite.propertyListingId) } }.font(.caption).padding(8).background(IDS.Colors.chipBackground).cornerRadius(8) }.padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius) } }
        }.task { await load() }
    }
    private func load() async { do { favorites = try await NetworkClient.shared.getMyFavoritePropertyListings().favorites; error = nil } catch { self.error = "Couldn't load your saved properties. Check your connection and try again." } }
    private func remove(_ id: String) async { do { _ = try await NetworkClient.shared.removePropertyListingFavorite(id); favorites?.removeAll { $0.propertyListingId == id }; onRemoved() } catch { self.error = "Couldn't remove this saved property. Check your connection and try again." } }
}

// Extracted into its own concretely-typed View (2026-07-22) -- the previous inline
// ForEach body (computing propertyTypeLabel/isMine inline via .first(where:)/??/||
// directly inside PropertyListingCard's call) made the surrounding PropertyView.body
// (a large ScrollView/VStack with many conditional branches) time out real Swift
// type-checking ("unable to type-check this expression in reasonable time") -- a real
// build failure found live via xcodebuild against a real Simulator, not assumed.
// Wrapping the row in its own struct with a concrete initializer resolves it, the
// standard fix for this class of SwiftUI compiler timeout.
private struct PropertyListingRow: View {
    let listing: PropertyListingDto
    let propertyTypeLabel: String
    let isMine: Bool
    let favorited: Bool
    let favoriteBusy: Bool
    let onChanged: () -> Void
    let onToggleFavorite: () -> Void
    let onContact: () -> Void
    let onMakeOffer: (String, Double) -> Void
    var listerTrustScore: Int?

    var body: some View {
        PropertyListingCard(
            listing: listing,
            propertyTypeLabel: propertyTypeLabel,
            isMine: isMine,
            onChanged: onChanged,
            onContact: onContact,
            onMakeOffer: onMakeOffer,
            favorited: favorited,
            favoriteBusy: favoriteBusy,
            onToggleFavorite: onToggleFavorite,
            listerTrustScore: listerTrustScore
        )
    }
}

private struct PropertyListingCard: View {
    let listing: PropertyListingDto
    let propertyTypeLabel: String
    let isMine: Bool
    let onChanged: () -> Void
    let onContact: () -> Void
    let onMakeOffer: (String, Double) -> Void
    var favorited: Bool = false
    var favoriteBusy: Bool = false
    var onToggleFavorite: () -> Void = {}
    var listerTrustScore: Int?

    @State private var busy = false
    @State private var error: String?
    // Real 당근-style price-offer negotiation (2026-07-19) -- see
    // PropertyPriceOfferService's own doc comment; mirrors ListingCard's own offering
    // state exactly.
    @State private var offering = false
    @State private var offerAmount = ""
    @State private var showingSafetyChecklist = false
    @State private var showingReportOptions = false
    @State private var myLocation: CLLocationCoordinate2D?
    @State private var showRoute = false
    @StateObject private var locationFetcher = HoodLocationFetcher()

    // Real optional buyer/tenant identification at mark-taken time (2026-07-24) --
    // see backend PropertyListingService.markTaken's own doc comment.
    @State private var markingTaken = false
    @State private var counterpartyPhone = ""

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @State private var showReviewSheet = false
    @State private var selectedGoodPoints: Set<String> = []
    @State private var selectedUncomfortablePoints: Set<String> = []
    @State private var submittingReview = false
    @State private var reviewSubmitted = false

    private var priceLabel: String {
        let base = "\(Int(listing.price)) RWF"
        return listing.listingType == "RENT" ? "\(base)/mo" : base
    }

    private var detailsLabel: String {
        var parts: [String] = []
        if let bedrooms = listing.bedrooms { parts.append("\(bedrooms) bd") }
        if let sizeSqm = listing.sizeSqm { parts.append("\(Int(sizeSqm)) m²") }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                HStack(spacing: 6) {
                    Text("\(listing.listingType == "RENT" ? "For rent" : "For sale") · \(propertyTypeLabel) · \(hoodRelativeTime(listing.createdAt))")
                        .font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                    if listing.status == "TAKEN" {
                        Text("TAKEN").font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                }
                Spacer()
                if !isMine { Button(action: onToggleFavorite) { Image(systemName: favorited ? "heart.fill" : "heart").foregroundColor(favorited ? .red : IDS.Colors.textSecondary) }.disabled(favoriteBusy).padding(.trailing, 6) }
                Text(priceLabel).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            Text(listing.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            if !detailsLabel.isEmpty {
                Text(detailsLabel).font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
            // comment. Only shown for someone else's listing.
            if !isMine, let listerTrustScore {
                TrustBadge(score: listerTrustScore)
            }
            Text(listing.description).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            if offering {
                HStack(spacing: 8) {
                    TextField("Your offer (RWF)", text: $offerAmount)
                        .keyboardType(.numberPad)
                        .padding(10)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(10)
                    Button(action: {
                        guard let amount = Double(offerAmount) else { return }
                        offering = false
                        offerAmount = ""
                        onMakeOffer(listing.id, amount)
                    }) {
                        Text("Send").font(.subheadline).bold().foregroundColor(.white)
                            .padding(.horizontal, 16).padding(.vertical, 10)
                            .background(IDS.Colors.brand).cornerRadius(12)
                    }
                    .disabled(Double(offerAmount) == nil)
                }
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            // Real optional "who's the buyer/tenant?" prompt (2026-07-24) -- see
            // backend PropertyListingService.markTaken's own doc comment.
            if markingTaken {
                TextField("Their phone (optional)", text: $counterpartyPhone)
                    .keyboardType(.phonePad)
                    .padding(10)
                    .background(IDS.Colors.chipBackground)
                    .cornerRadius(10)
                HStack(spacing: 10) {
                    actionButton("Skip", filled: false) { await markTaken(counterpartyPhoneNumber: nil) }
                    actionButton("Confirm", filled: true) { await markTaken(counterpartyPhoneNumber: counterpartyPhone.trimmingCharacters(in: .whitespaces)) }
                }
            }
            // Real post-transaction review, preset checklist with asymmetric public/
            // private visibility (2026-07-24) -- see backend HoodReviewService's own
            // doc comment. Only offered once a real counterparty was recorded at
            // mark-taken time; no pre-check for "already reviewed" (a real, honest v1
            // -- a second attempt just surfaces the backend's own
            // REVIEW_ALREADY_SUBMITTED error).
            if isMine, listing.status == "TAKEN", listing.counterpartyId != nil, !reviewSubmitted {
                if showReviewSheet {
                    HoodReviewForm(
                        selectedGoodPoints: $selectedGoodPoints,
                        selectedUncomfortablePoints: $selectedUncomfortablePoints,
                        submitting: submittingReview,
                        onCancel: { showReviewSheet = false },
                        onSubmit: { await submitReview() }
                    )
                } else {
                    actionButton(listing.listingType == "RENT" ? "Rate this tenant" : "Rate this buyer", filled: true) { showReviewSheet = true }
                }
            }
            HStack(spacing: 10) {
                if isMine {
                    if listing.status == "AVAILABLE" && !markingTaken {
                        actionButton("Mark taken", filled: false) { markingTaken = true }
                    }
                    if listing.status != "REMOVED" {
                        actionButton("Remove", filled: false) { await remove() }
                    }
                } else if listing.status == "AVAILABLE" && !offering {
                    actionButton("Message lister", filled: false) { onContact() }
                    actionButton("Make an offer", filled: true) { offering = true }
                    actionButton("Report", filled: false) { showingReportOptions = true }
                }
            }
            if !isMine, listing.status == "AVAILABLE", let toLat = listing.latitude, let toLng = listing.longitude {
                Button(action: {
                    if showRoute { showRoute = false } else if myLocation != nil { showRoute = true } else { locationFetcher.requestLocation() }
                }) {
                    Text(showRoute ? "Hide directions" : "🚗 Directions to this property")
                        .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 16).padding(.vertical, 10)
                        .background(IDS.Colors.chipBackground).cornerRadius(12)
                }
                if showRoute, let myLocation {
                    RouteMiniMap(fromLat: myLocation.latitude, fromLng: myLocation.longitude, toLat: toLat, toLng: toLng, fromLabel: "You", toLabel: listing.title)
                }
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
        .confirmationDialog("Report this property", isPresented: $showingReportOptions, titleVisibility: .visible) {
            Button("Suspected fake or unavailable property") { Task { await report("The property may be fake or unavailable") } }
            Button("Misleading price or property details") { Task { await report("The price or property details appear misleading") } }
            Button("Unsafe payment request") { Task { await report("The lister made an unsafe payment request") } }
        } message: { Text("Reports go to Itunda’s review queue.") }
        .onAppear {
            locationFetcher.onLocation = { coordinate in
                myLocation = coordinate
                showRoute = true
            }
        }
        .onChange(of: locationFetcher.errorMessage) { message in
            if let message { error = message }
        }
    }

    private func actionButton(_ label: String, filled: Bool, action: @escaping () async -> Void) -> some View {
        Button(action: { Task { await action() } }) {
            Text(label)
                .font(.subheadline).bold()
                .foregroundColor(filled ? .white : IDS.Colors.textPrimary)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(filled ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .cornerRadius(12)
        }
        .disabled(busy)
    }

    private func markTaken(counterpartyPhoneNumber: String?) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.markPropertyListingTaken(
                listing.id, counterpartyPhoneNumber: counterpartyPhoneNumber?.isEmpty == true ? nil : counterpartyPhoneNumber,
            )
            markingTaken = false
            onChanged()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    private func submitReview() async {
        submittingReview = true
        defer { submittingReview = false }
        do {
            _ = try await NetworkClient.shared.submitPropertyListingReview(
                listing.id, goodPoints: Array(selectedGoodPoints), uncomfortablePoints: Array(selectedUncomfortablePoints),
            )
            reviewSubmitted = true
            showReviewSheet = false
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func remove() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.removePropertyListing(listing.id)
            onChanged()
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func report(_ reason: String) async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.reportHoodContent(targetType: "PROPERTY_LISTING", targetId: listing.id, reason: reason)
            error = "Thanks. Your report was sent for review."
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            error = "You already reported this property."
        } catch {
            self.error = "Couldn't send the report. Check your connection and try again."
        }
    }
}
