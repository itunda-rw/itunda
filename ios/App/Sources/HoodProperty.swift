import SwiftUI
import UIKit
import CoreLocation
import CoreDesignSystem
import CoreNetwork


struct PropertyContent: View {
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    // Real "Places I got" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6, see backend PropertyListingRepository's own doc comment.
    private enum PropertyView { case browse, nearby, neighborhood, mine, acquired, saved, valuation }

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
                    Text("시세 Value").tag(PropertyView.valuation)
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
                if view == .valuation {
                    PropertyValuationCard(propertyTypes: propertyTypes)
                } else if view == .saved {
                    PropertyWishlistView(onRemoved: { Task { await loadFavoriteIds() } })
                } else if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
                } else if listings == nil {
                    HoodFeedSkeleton()
                } else if listings!.isEmpty && (view != .neighborhood || neighborhoodName != nil) {
                    // Real copy-voice fix (item 244, round 5 of the empty-state pass,
                    // ported from the same-day Android fix): say what's missing AND
                    // what fixes it, per this screen's own real "+ List a property"
                    // button above in the MINE view.
                    Text(
                        view == .browse ? "No properties listed yet — check back soon, or list your own."
                            : view == .nearby ? "No properties near you yet — try Browse to see properties from everywhere."
                            : view == .neighborhood ? "No properties in your neighborhood yet — try Browse to see properties from everywhere."
                            : view == .acquired ? "No properties acquired yet — properties you acquire will show up here."
                            : "You haven't listed any properties yet — tap \"+ List a property\" above to list your first one."
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
            listerTrustScore: trustScores[listing.listerId],
            currentUserId: currentUserId
        )
    }

    private func load() async {
        listings = nil
        if view == .saved || view == .valuation { listings = []; error = nil; return }
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

struct NewPropertyListingForm: View {
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
            IdsTextField("e.g. 2-bedroom apartment in Kacyiru", text: $title)
            IdsTextField("Describe the property", text: $description)
            HStack {
                IdsTextField(listingType == "RENT" ? "Rent/mo (RWF)" : "Price (RWF)", text: $price, keyboardType: .numberPad)
                IdsTextField("Bedrooms", text: $bedrooms, keyboardType: .numberPad)
                IdsTextField("Size (m²)", text: $sizeSqm, keyboardType: .numberPad)
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
        .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
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

// Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- see the backend's
// PropertyListingService.estimateValue doc comment. Read-only: enter a location + size,
// get a real comparable-listings-based estimate, nothing persisted. bank-mfe/Android
// already have this; this is the first iOS client.
struct PropertyValuationCard: View {
    let propertyTypes: [PropertyTypeDto]

    @State private var latitude = ""
    @State private var longitude = ""
    @State private var propertyType: String?
    @State private var listingType = "SALE"
    @State private var sizeSqm = ""
    @State private var estimate: PropertyValuationEstimateDto?
    @State private var error: String?
    @State private var loading = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("우리집 시세 — Estimate my home's value").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            Text("A real estimate based on comparable listings near you, not a fabricated number.")
                .font(.caption).foregroundColor(IDS.Colors.textSecondary)
            IdsTextField("Latitude", text: $latitude, keyboardType: .decimalPad)
            IdsTextField("Longitude", text: $longitude, keyboardType: .decimalPad)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(propertyTypes) { t in
                        let active = propertyType == t.id
                        Text(t.label)
                            .font(.caption).bold()
                            .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                            .padding(.horizontal, 12).padding(.vertical, 6)
                            .background(active ? IDS.Colors.brand : Color.clear)
                            .overlay(RoundedRectangle(cornerRadius: 999).stroke(active ? IDS.Colors.brand : IDS.Colors.textSecondary.opacity(0.3), lineWidth: 1))
                            .cornerRadius(999)
                            .onTapGesture { propertyType = t.id }
                    }
                }
            }
            HStack(spacing: 6) {
                ForEach([("SALE", "For sale"), ("RENT", "For rent")], id: \.0) { v, label in
                    let active = listingType == v
                    Text(label)
                        .font(.caption).bold()
                        .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(active ? IDS.Colors.brand : Color.clear)
                        .overlay(RoundedRectangle(cornerRadius: 999).stroke(active ? IDS.Colors.brand : IDS.Colors.textSecondary.opacity(0.3), lineWidth: 1))
                        .cornerRadius(999)
                        .onTapGesture { listingType = v }
                }
            }
            IdsTextField("Size (sqm)", text: $sizeSqm, keyboardType: .decimalPad)
            if let error { Text(error).font(.caption).foregroundColor(.red) }
            Button(action: { Task { await estimateValue() } }) {
                Text(loading ? "Estimating…" : "Estimate value")
                    .font(.caption).bold().foregroundColor(.white)
                    .frame(maxWidth: .infinity).padding(.vertical, 10)
                    .background(IDS.Colors.brand).cornerRadius(10)
            }
            .disabled(loading)
            if let estimate {
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(Int(estimate.estimatedValue)) RWF").font(.title2).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("Based on \(estimate.comparableCount) comparable listings within \(Int(estimate.radiusKm)) km (\(Int(estimate.averagePricePerSqm)) RWF/sqm avg)")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(12).background(Color(.tertiarySystemBackground)).cornerRadius(10)
            }
        }
        .padding(16).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
    }

    private func estimateValue() async {
        guard let propertyType, let lat = Double(latitude), let lng = Double(longitude), let size = Double(sizeSqm), size > 0 else {
            error = "Fill in a real location, property type, and size."
            return
        }
        loading = true
        error = nil
        estimate = nil
        defer { loading = false }
        do {
            estimate = try await NetworkClient.shared.getPropertyValuation(latitude: lat, longitude: lng, propertyType: propertyType, listingType: listingType, sizeSqm: size).estimate
        } catch let NetworkError.httpError(statusCode) where statusCode == 422 {
            error = "Not enough comparable listings nearby to estimate a value."
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

