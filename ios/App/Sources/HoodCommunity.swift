import SwiftUI
import UIKit
import CoreLocation
import CoreDesignSystem
import CoreNetwork


struct CommunityContent: View {
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
    // Real 동네생활 topic-chip filter row (2026-08-28) -- a lifestyle axis independent
    // of the functional category above, see backend CommunityService.TOPICS' own doc
    // comment.
    @State private var topics: [CommunityCategoryDto] = []
    @State private var activeTopic: String?
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

                    if view == .browse && !topics.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 6) {
                                ForEach(topics) { t in
                                    let active = activeTopic == t.id
                                    Text(t.label)
                                        .font(.caption).bold()
                                        .foregroundColor(active ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 12).padding(.vertical, 6)
                                        .background(active ? IDS.Colors.brand : Color.clear)
                                        .overlay(RoundedRectangle(cornerRadius: 999).stroke(active ? IDS.Colors.brand : IDS.Colors.textSecondary.opacity(0.3), lineWidth: 1))
                                        .cornerRadius(999)
                                        .onTapGesture { activeTopic = active ? nil : t.id }
                                }
                            }
                        }
                    }

                    if view == .mine {
                        CommentNotificationToggle()
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
                        // Real fix (2026-08-24, flat-design sweep): dropped the Card
                        // wrapper -- a lone error state.
                        .padding(20)
                    } else if posts == nil {
                        HoodFeedSkeleton()
                    } else if posts!.isEmpty && (view != .neighborhood || neighborhoodName != nil) {
                        // Real copy-voice fix (item 244, round 5 of the empty-state pass,
                        // ported from the same-day Android fix): say what's missing AND
                        // what fixes it, per this screen's own real "+ Write a post"
                        // button above in the MINE view.
                        Text(
                            view == .browse ? "No posts yet — be the first to share something with your neighbors."
                                : view == .nearby ? "No posts near you yet — try Browse to see posts from everywhere."
                                : view == .neighborhood ? "No posts in your neighborhood yet — try Browse to see posts from everywhere."
                                : "You haven't posted anything yet — tap \"+ Write a post\" above to share your first one."
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
                if topics.isEmpty {
                    topics = (try? await NetworkClient.shared.getCommunityTopics().topics) ?? []
                }
                locationFetcher.onLocation = { coordinate in Task { await loadNearby(coordinate) } }
                await load()
            }
            .onChange(of: view) { _ in Task { await load() } }
            .onChange(of: activeCategory) { _ in Task { await load() } }
            .onChange(of: activeTopic) { _ in Task { await load() } }
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
            let res = view == .browse ? try await NetworkClient.shared.browseCommunityPosts(category: activeCategory, topic: activeTopic) : try await NetworkClient.shared.getMyCommunityPosts()
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

struct NewCommunityPostForm: View {
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
            IdsTextField("Title", text: $title)
            IdsTextField("What's going on in the neighborhood?", text: $postBody)
            if category == "meetup" {
                HStack(spacing: 8) {
                    IdsTextField("Date (YYYY-MM-DD)", text: $eventDateText)
                    IdsTextField("Time (HH:mm)", text: $eventTimeText)
                }
                IdsTextField("Max people (optional -- blank means unlimited)", text: $capacityText, keyboardType: .numberPad)
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
        // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- matches
        // the identical form conversions in HoodProperty.swift/HoodMarketplace.swift/
        // HoodJobsForms.swift.
        .padding(.vertical, 10)
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

