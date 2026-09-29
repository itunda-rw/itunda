import SwiftUI
import UIKit
import CoreLocation
import CoreDesignSystem
import CoreNetwork


struct MarketplaceContent: View {
    /// Real "message seller" hand-off to TalkScreen -- see TalkScreen.swift's own
    /// doc comment on `pendingConversationId` for the full mechanism.
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    // wishlist added 2026-07-21, porting bank-mfe's Marketplace wishlist (shipped
    // earlier the same day) to iOS -- see favoriteIds state and ListingWishlistView
    // below for the full account.
    // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6, see backend ListingRepository's own doc comment.
    private enum HoodView { case browse, nearby, neighborhood, mine, purchases, wishlist, alerts, hidden }

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
    // Real pagination-discard fix (same systemic gap fixed for Knowledge/
    // Community/Marketplace-web/Marketplace-Android, 2026-09-09) -- a
    // request never asked past page 0 across browse/mine/purchases/
    // neighborhood, so any feed with more than 20 real listings was
    // silently unreachable beyond the first page. `.nearby` is a separate
    // location-driven flow (loadNearby below), not covered by this state.
    @State private var page = 0
    @State private var hasMore = false
    @State private var loadingMore = false

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
                    Text("Wishlist").tag(HoodView.wishlist)
                    Text("Alerts").tag(HoodView.alerts)
                    Text("Hidden").tag(HoodView.hidden)
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
                } else if view == .alerts {
                    KeywordAlertsView()
                } else if view == .hidden {
                    HiddenListingsView()
                } else if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper
                    // -- a lone error state.
                    .padding(20)
                } else if listings == nil {
                    HoodFeedSkeleton()
                } else if listings!.isEmpty && (view != .neighborhood || neighborhoodName != nil) {
                    // Real copy-voice fix (item 244, round 5 of the empty-state pass --
                    // docs/COPY_VOICE.md's rules, ported from the same-day Android fix):
                    // say what's missing AND what fixes it, per this screen's own real
                    // "+ List an item" button above in the MINE view.
                    Text(
                        view == .browse ? "No listings yet — be the first to list something for sale."
                            : view == .nearby ? "No listings near you yet — try Browse to see listings from everywhere."
                            : view == .neighborhood ? "No listings in your neighborhood yet — try Browse to see listings from everywhere."
                            : view == .purchases ? "No purchases recorded yet — items you buy will show up here."
                            : "You haven't listed anything yet — tap \"+ List an item\" above to list your first one."
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
                            sellerTrustScore: trustScores[listing.sellerId],
                            currentUserId: currentUserId
                        )
                    }
                    if hasMore && view != .nearby {
                        Button(action: { Task { await loadMore() } }) {
                            Text(loadingMore ? "Loading…" : "Load more").bold().font(.caption)
                                .frame(maxWidth: .infinity).padding(.vertical, 12)
                                .background(Color(.secondarySystemBackground)).cornerRadius(10)
                        }
                        .disabled(loadingMore)
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
        page = 0
        hasMore = false
        if view == .wishlist {
            // ListingWishlistView below owns its own fetch (it needs title/price/
            // category straight from the favorites endpoint, not the ListingDto
            // shape) -- nothing to load into `listings` here.
            return
        }
        if view == .alerts {
            // KeywordAlertsView below owns its own fetch -- nothing to load into
            // `listings` here.
            return
        }
        if view == .hidden {
            // HiddenListingsView below owns its own fetch -- nothing to load into
            // `listings` here.
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
                let res = try await NetworkClient.shared.getListingsMyNeighborhood(page: 0)
                neighborhoodName = profile.user.neighborhood
                listings = res.listings
                trustScores = res.trustScores ?? [:]
                hasMore = res.page + 1 < res.totalPages
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
            case .browse: res = try await NetworkClient.shared.browseListings(page: 0)
            case .purchases: res = try await NetworkClient.shared.getMyPurchases(page: 0)
            default: res = try await NetworkClient.shared.getMyListings(page: 0)
            }
            listings = res.listings
            trustScores = res.trustScores ?? [:]
            hasMore = res.page + 1 < res.totalPages
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadMore() async {
        let nextPage = page + 1
        loadingMore = true
        defer { loadingMore = false }
        do {
            let res: ListingsResponse
            switch view {
            case .neighborhood: res = try await NetworkClient.shared.getListingsMyNeighborhood(page: nextPage)
            case .browse: res = try await NetworkClient.shared.browseListings(page: nextPage)
            case .purchases: res = try await NetworkClient.shared.getMyPurchases(page: nextPage)
            default: res = try await NetworkClient.shared.getMyListings(page: nextPage)
            }
            listings = (listings ?? []) + res.listings
            trustScores.merge(res.trustScores ?? [:]) { _, new in new }
            page = nextPage
            hasMore = res.page + 1 < res.totalPages
        } catch {
            // Non-critical -- the already-loaded page stays visible; the user
            // can retry by tapping "Load more" again.
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

struct NewListingForm: View {
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

    // Real seller-uploaded photo (2026-08-01) -- see NetworkClient.swift's own doc
    // comment on ListingDto.photoUrl. Android already has this
    // (MarketplaceScreen.kt's pickPhoto flow); iOS never had a photo field at all
    // until now. Reuses the same ImagePickerView -> uploadPhoto chain the ownership-
    // verification form (PropertyListingCard) already established.
    @State private var showPhotoPicker = false
    @State private var photoUrl: String?
    @State private var uploadingPhoto = false
    @StateObject private var vehicleState = VehicleListingState()

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("List an item").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            IdsTextField("What are you selling?", text: $title)
            IdsTextField("Description", text: $description)
            HStack {
                IdsTextField("Price (RWF)", text: $price, keyboardType: .numberPad)
                IdsTextField("Category", text: $category)
            }
            IdsTextField("Suggested meeting place (optional)", text: $meetingPlace)
            Text("Use a public landmark, not a home address.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            VehicleListingFieldsSection(state: vehicleState)
            Button(action: { showPhotoPicker = true }) {
                Text(uploadingPhoto ? "Uploading…" : photoUrl != nil ? "✓ Photo uploaded" : "Add a photo (optional)")
                    .font(.caption)
                    .foregroundColor(photoUrl != nil ? IDS.Colors.brand : IDS.Colors.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .background(IDS.Colors.chipBackground)
                    .cornerRadius(12)
            }
            .disabled(uploadingPhoto)
            .sheet(isPresented: $showPhotoPicker) {
                ImagePickerView { image in
                    showPhotoPicker = false
                    if let image { Task { await uploadPhoto(image: image) } }
                }
            }
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
                .disabled(submitting || uploadingPhoto)
            }
        }
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- matches
        // HoodProperty.swift's identical NewPropertyListingForm conversion.
        .padding(.vertical, 10)
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

    private func uploadPhoto(image: UIImage) async {
        guard let jpegData = image.jpegData(compressionQuality: 0.8) else {
            error = "Couldn't read that photo."
            return
        }
        uploadingPhoto = true
        defer { uploadingPhoto = false }
        do {
            let uploaded = try await NetworkClient.shared.uploadPhoto(data: jpegData, filename: "listing.jpg", mimeType: "image/jpeg")
            photoUrl = uploaded.url
        } catch {
            self.error = "Could not upload this photo."
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
                photoUrl: photoUrl,
                vehicleMileageKm: vehicleState.isVehicle ? Int(vehicleState.mileageKm) : nil,
                vehicleInsuranceClaimCount: vehicleState.isVehicle ? Int(vehicleState.insuranceClaimCount) : nil,
                vehicleIsLeaseTakeover: vehicleState.isVehicle && vehicleState.isLeaseTakeover,
                leaseTotalAcquisitionCost: vehicleState.isLeaseTakeover ? Double(vehicleState.leaseTotalAcquisitionCost) : nil,
                leaseRemainingMonths: vehicleState.isLeaseTakeover ? Int(vehicleState.leaseRemainingMonths) : nil,
                leaseTotalMonths: vehicleState.isLeaseTakeover ? Int(vehicleState.leaseTotalMonths) : nil,
                leaseMonthlyPayment: vehicleState.isLeaseTakeover ? Double(vehicleState.leaseMonthlyPayment) : nil,
                leaseSubsidyAmount: vehicleState.isLeaseTakeover ? Double(vehicleState.leaseSubsidyAmount) : nil,
                leaseReturnFee: vehicleState.isLeaseTakeover ? Double(vehicleState.leaseReturnFee) : nil
            )
            onCreated()
        } catch let NetworkError.httpErrorWithMessage(statusCode, message) {
            error = message ?? TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

// TrustBadge and the hood*PointLabels arrays moved to HoodMarketplaceReview.swift
// (2026-09-09, file-size-lint extraction -- this file crossed 500 lines for the
// first time while adding the pagination-discard fix) -- both are genuinely
// cross-feature (ListingCard/JobPostCard/PropertyListingCard/the review flow),
// not Marketplace-specific, so this doc comment's own old "all in this file"
// claim was already stale before the move.

