import SwiftUI
import CoreLocation
import CoreDesignSystem

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

            switch mode {
            case .marketplace:
                MarketplaceContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            case .community:
                CommunityContent()
            case .jobs:
                JobsContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            case .property:
                PropertyContent(pendingConversationId: $pendingConversationId, onSwitchToTalk: onSwitchToTalk)
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }
}

private struct MarketplaceContent: View {
    /// Real "message seller" hand-off to TalkScreen -- see TalkScreen.swift's own
    /// doc comment on `pendingConversationId` for the full mechanism.
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    private enum HoodView { case browse, mine }

    @State private var view: HoodView = .browse
    @State private var listings: [ListingDto]?
    @State private var error: String?
    @State private var showNewListing = false
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                TdsPlainTopBar(title: "Hood")

                Picker("", selection: $view) {
                    Text("Browse").tag(HoodView.browse)
                    Text("My listings").tag(HoodView.mine)
                }
                .pickerStyle(.segmented)

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

                if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(IDS.Colors.card)
                    .cornerRadius(IDS.Layout.cardCornerRadius)
                } else if listings == nil {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                } else if listings!.isEmpty {
                    Text(view == .browse ? "No listings yet." : "You haven't listed anything yet.")
                        .foregroundColor(IDS.Colors.textSecondary)
                } else {
                    ForEach(listings!) { listing in
                        ListingCard(
                            listing: listing,
                            isMine: view == .mine || listing.sellerId == currentUserId,
                            onChanged: { Task { await load() } },
                            onMessageSeller: { id in Task { await messageSeller(id) } },
                            onMakeOffer: { id, amount in Task { await makeOffer(id, amount) } }
                        )
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await load() }
        .onChange(of: view) { _ in Task { await load() } }
    }

    private func load() async {
        listings = nil
        do {
            let res = view == .browse ? try await NetworkClient.shared.browseListings() : try await NetworkClient.shared.getMyListings()
            listings = res.listings
            error = nil
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
            )
            onCreated()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct ListingCard: View {
    let listing: ListingDto
    let isMine: Bool
    let onChanged: () -> Void
    let onMessageSeller: (String) -> Void
    let onMakeOffer: (String, Double) -> Void

    @State private var busy = false
    @State private var error: String?
    @State private var offering = false
    @State private var offerAmount = ""

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
                    Text(listing.category).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                Text("\(Int(listing.price)) RWF").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            Text(listing.description).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
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
            HStack(spacing: 10) {
                if isMine {
                    if listing.status == "ACTIVE" {
                        actionButton("Mark sold", filled: false) { await markSold() }
                    }
                    if listing.status != "REMOVED" {
                        actionButton("Remove", filled: false) { await remove() }
                    }
                } else if listing.status == "ACTIVE" && !offering {
                    actionButton(busy ? "Starting…" : "Message seller", filled: false) {
                        onMessageSeller(listing.id)
                    }
                    actionButton("Make an offer", filled: true) { offering = true }
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

    private func markSold() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.markListingSold(listing.id)
            onChanged()
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
}

// ============================== COMMUNITY (동네생활) ==============================

private struct CommunityContent: View {
    private enum CommunityView { case browse, mine }

    @State private var view: CommunityView = .browse
    @State private var categories: [CommunityCategoryDto] = []
    @State private var activeCategory: String?
    @State private var posts: [CommunityPostDto]?
    @State private var error: String?
    @State private var showNewPost = false
    @State private var openPostId: String?
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    var body: some View {
        if let openPostId {
            CommunityPostDetailView(postId: openPostId, onBack: { self.openPostId = nil; Task { await load() } })
        } else {
            ScrollView {
                VStack(spacing: IDS.Layout.cardGap) {
                    Picker("", selection: $view) {
                        Text("Neighborhood feed").tag(CommunityView.browse)
                        Text("My posts").tag(CommunityView.mine)
                    }
                    .pickerStyle(.segmented)

                    if view == .browse && !categories.isEmpty {
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
                            NewCommunityPostForm(categories: categories, onCreated: { showNewPost = false; Task { await load() } }, onCancel: { showNewPost = false })
                        } else {
                            Button(action: { showNewPost = true }) {
                                Text("+ Write a post")
                                    .font(IDS.Typography.bodyBold).foregroundColor(.white)
                                    .frame(maxWidth: .infinity).padding(.vertical, 14)
                                    .background(IDS.Colors.brand).cornerRadius(14)
                            }
                        }
                    }

                    if let error {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(error).foregroundColor(.red).font(.subheadline)
                            Button("Retry") { Task { await load() } }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    } else if posts == nil {
                        ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                    } else if posts!.isEmpty {
                        Text(view == .browse ? "No posts yet." : "You haven't posted anything yet.").foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(posts!) { post in
                            CommunityPostCard(
                                post: post,
                                categoryLabel: categories.first(where: { $0.id == post.category })?.label ?? post.category,
                                isMine: view == .mine || post.authorId == currentUserId,
                                onOpen: { openPostId = post.id },
                                onRemoved: { Task { await load() } }
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
                await load()
            }
            .onChange(of: view) { _ in Task { await load() } }
            .onChange(of: activeCategory) { _ in Task { await load() } }
        }
    }

    private func load() async {
        posts = nil
        do {
            let res = view == .browse ? try await NetworkClient.shared.browseCommunityPosts(category: activeCategory) : try await NetworkClient.shared.getMyCommunityPosts()
            posts = res.posts
            error = nil
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
    }

    private func submit() async {
        guard !title.isEmpty, !postBody.isEmpty, !category.isEmpty else {
            error = "Fill in every field."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.createCommunityPost(category: category, title: title, body: postBody)
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

    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(categoryLabel).font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                Spacer()
                if isMine {
                    Button(action: { Task { await remove() } }) {
                        Text("Remove").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            .padding(.horizontal, 10).padding(.vertical, 4)
                            .background(IDS.Colors.chipBackground).cornerRadius(10)
                    }
                    .disabled(busy)
                }
            }
            Text(post.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            Text(post.body).font(.subheadline).foregroundColor(IDS.Colors.textSecondary).lineLimit(2)
            Text("❤️ \(post.likeCount) · 💬 \(post.commentCount)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
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

    private enum JobsView { case browse, mine }

    @State private var view: JobsView = .browse
    @State private var categories: [JobCategoryDto] = []
    @State private var activeCategory: String?
    @State private var posts: [JobPostDto]?
    @State private var error: String?
    @State private var showNewPost = false
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                Picker("", selection: $view) {
                    Text("Find work").tag(JobsView.browse)
                    Text("My posts").tag(JobsView.mine)
                }
                .pickerStyle(.segmented)

                if view == .browse && !categories.isEmpty {
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

                if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                } else if posts == nil {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                } else if posts!.isEmpty {
                    Text(view == .browse ? "No jobs posted yet." : "You haven't posted any jobs yet.").foregroundColor(IDS.Colors.textSecondary)
                } else {
                    ForEach(posts!) { post in
                        JobPostCard(
                            post: post,
                            categoryLabel: categories.first(where: { $0.id == post.category })?.label ?? post.category,
                            isMine: view == .mine || post.posterId == currentUserId,
                            onChanged: { Task { await load() } },
                            onContact: { Task { await contact(post.id) } }
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
        }
        .onChange(of: view) { _ in Task { await load() } }
        .onChange(of: activeCategory) { _ in Task { await load() } }
    }

    private func load() async {
        posts = nil
        do {
            let res = view == .browse ? try await NetworkClient.shared.browseJobPosts(category: activeCategory) : try await NetworkClient.shared.getMyJobPosts()
            posts = res.posts
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
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
            _ = try await NetworkClient.shared.createJobPost(category: category, title: title, description: description, payType: payType, payAmount: amount)
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

    @State private var busy = false
    @State private var error: String?

    private var payLabel: String {
        let base = "\(Int(post.payAmount)) RWF"
        return post.payType == "HOURLY" ? "\(base)/hr" : base
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                HStack(spacing: 6) {
                    Text(categoryLabel).font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                    if post.status == "FILLED" {
                        Text("FILLED").font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                }
                Spacer()
                Text(payLabel).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            Text(post.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            Text(post.description).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
            HStack(spacing: 10) {
                if isMine {
                    if post.status == "OPEN" {
                        actionButton("Mark filled", filled: false) { await markFilled() }
                    }
                    if post.status != "REMOVED" {
                        actionButton("Remove", filled: false) { await remove() }
                    }
                } else if post.status == "OPEN" {
                    actionButton("Message poster", filled: true) { onContact() }
                }
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
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

    private func markFilled() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.markJobPostFilled(post.id)
            onChanged()
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
}

// ============================== PROPERTY (당근부동산) ==============================

private struct PropertyContent: View {
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    private enum PropertyView { case browse, mine }

    @State private var view: PropertyView = .browse
    @State private var propertyTypes: [PropertyTypeDto] = []
    @State private var listingTypeFilter: String?
    @State private var propertyTypeFilter: String?
    @State private var listings: [PropertyListingDto]?
    @State private var error: String?
    @State private var showNewListing = false
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                Picker("", selection: $view) {
                    Text("Browse").tag(PropertyView.browse)
                    Text("My listings").tag(PropertyView.mine)
                }
                .pickerStyle(.segmented)

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

                if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                } else if listings == nil {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                } else if listings!.isEmpty {
                    Text(view == .browse ? "No properties listed yet." : "You haven't listed any properties yet.").foregroundColor(IDS.Colors.textSecondary)
                } else {
                    ForEach(listings!) { listing in
                        PropertyListingCard(
                            listing: listing,
                            propertyTypeLabel: propertyTypes.first(where: { $0.id == listing.propertyType })?.label ?? listing.propertyType,
                            isMine: view == .mine || listing.listerId == currentUserId,
                            onChanged: { Task { await load() } },
                            onContact: { Task { await contact(listing.id) } },
                            onMakeOffer: { id, amount in Task { await makeOffer(id, amount) } }
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
            if propertyTypes.isEmpty {
                propertyTypes = (try? await NetworkClient.shared.getPropertyTypes().propertyTypes) ?? []
            }
            await load()
        }
        .onChange(of: view) { _ in Task { await load() } }
        .onChange(of: listingTypeFilter) { _ in Task { await load() } }
        .onChange(of: propertyTypeFilter) { _ in Task { await load() } }
    }

    private func load() async {
        listings = nil
        do {
            let res = view == .browse
                ? try await NetworkClient.shared.browsePropertyListings(listingType: listingTypeFilter, propertyType: propertyTypeFilter)
                : try await NetworkClient.shared.getMyPropertyListings()
            listings = res.listings
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
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
            _ = try await NetworkClient.shared.createPropertyListing(
                listingType: listingType, propertyType: propertyType, title: title, description: description, price: priceValue,
                bedrooms: Int(bedrooms), sizeSqm: Double(sizeSqm),
            )
            onCreated()
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct PropertyListingCard: View {
    let listing: PropertyListingDto
    let propertyTypeLabel: String
    let isMine: Bool
    let onChanged: () -> Void
    let onContact: () -> Void
    let onMakeOffer: (String, Double) -> Void

    @State private var busy = false
    @State private var error: String?
    // Real 당근-style price-offer negotiation (2026-07-19) -- see
    // PropertyPriceOfferService's own doc comment; mirrors ListingCard's own offering
    // state exactly.
    @State private var offering = false
    @State private var offerAmount = ""

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
                    Text("\(listing.listingType == "RENT" ? "For rent" : "For sale") · \(propertyTypeLabel)")
                        .font(.caption2).bold().foregroundColor(IDS.Colors.brand)
                    if listing.status == "TAKEN" {
                        Text("TAKEN").font(.caption2).bold().foregroundColor(IDS.Colors.textSecondary)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                }
                Spacer()
                Text(priceLabel).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            }
            Text(listing.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            if !detailsLabel.isEmpty {
                Text(detailsLabel).font(.caption).foregroundColor(IDS.Colors.textSecondary)
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
            HStack(spacing: 10) {
                if isMine {
                    if listing.status == "AVAILABLE" {
                        actionButton("Mark taken", filled: false) { await markTaken() }
                    }
                    if listing.status != "REMOVED" {
                        actionButton("Remove", filled: false) { await remove() }
                    }
                } else if listing.status == "AVAILABLE" && !offering {
                    actionButton("Message lister", filled: false) { onContact() }
                    actionButton("Make an offer", filled: true) { offering = true }
                }
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
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

    private func markTaken() async {
        busy = true
        defer { busy = false }
        do {
            _ = try await NetworkClient.shared.markPropertyListingTaken(listing.id)
            onChanged()
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
}
