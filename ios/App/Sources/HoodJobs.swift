import SwiftUI
import UIKit
import CoreLocation
import CoreDesignSystem
import CoreNetwork

struct JobsContent: View {
    @Binding var pendingConversationId: String?
    let onSwitchToTalk: () -> Void

    // Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6, see backend JobPostRepository's own doc comment.
    private enum JobsView { case browse, nearby, neighborhood, mine, worked, applications, wishlist, resume }

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
    // Real relevance-ranked search (2026-08-14) -- see NetworkClient.searchJobPosts's
    // own doc comment: this endpoint shipped Android-only and was never ported here
    // until now. Mirrors ShopScreen's own real cross-merchant product-search shape
    // field-for-field (explicit Search/Clear buttons, not a debounced live search).
    @State private var jobSearchInput = ""
    @State private var jobSearchResults: [JobPostDto]?
    @State private var jobSearchTrustScores: [String: Int] = [:]
    @State private var jobSearching = false

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                    Picker("", selection: $view) {
                        Text("Find work").tag(JobsView.browse)
                        Text("Near me").tag(JobsView.nearby)
                        Text("Neighborhood").tag(JobsView.neighborhood)
                        Text("My posts").tag(JobsView.mine)
                        Text("Jobs I did").tag(JobsView.worked)
                        Text("My applications").tag(JobsView.applications)
                        Text("Saved").tag(JobsView.wishlist)
                        Text("My résumé").tag(JobsView.resume)
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

                if view == .browse {
                    HStack(spacing: 8) {
                        TextField("Search jobs", text: $jobSearchInput)
                            .padding(12).background(IDS.Colors.backgroundPrimary).cornerRadius(10)
                        Button(action: { Task { await searchJobs() } }) {
                            Text(jobSearching ? "…" : "Search").bold().foregroundColor(.white)
                                .padding(.horizontal, 16).padding(.vertical, 14)
                                .background(jobSearching || jobSearchInput.trimmingCharacters(in: .whitespaces).isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand)
                                .cornerRadius(10)
                        }
                        .disabled(jobSearching || jobSearchInput.trimmingCharacters(in: .whitespaces).isEmpty)
                        if jobSearchResults != nil {
                            Button(action: { jobSearchResults = nil; jobSearchInput = "" }) {
                                Text("Clear").bold().foregroundColor(IDS.Colors.textPrimary)
                                    .padding(.horizontal, 16).padding(.vertical, 14)
                                    .background(IDS.Colors.textTertiary).cornerRadius(10)
                            }
                        }
                    }
                }

                if (view == .browse || view == .neighborhood) && jobSearchResults == nil && !categories.isEmpty {
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
                if view == .browse, let jobSearchResults {
                    if jobSearchResults.isEmpty {
                        Text("No jobs matched \"\(jobSearchInput)\".").foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(jobSearchResults) { post in
                            JobPostCard(
                                post: post,
                                categoryLabel: categories.first(where: { $0.id == post.category })?.label ?? post.category,
                                isMine: post.posterId == currentUserId,
                                onChanged: { Task { await searchJobs() } },
                                onContact: { Task { await contact(post.id) } },
                                favorited: favoriteIds.contains(post.id),
                                favoriteBusy: favoritingId == post.id,
                                onToggleFavorite: { Task { await toggleFavorite(post.id) } },
                                posterTrustScore: jobSearchTrustScores[post.posterId],
                                currentUserId: currentUserId
                            )
                        }
                    }
                } else if view == .resume {
                    ResumeBuilderView()
                } else if view == .applications {
                    MyJobApplicationsView()
                } else if view == .wishlist {
                    JobPostWishlistView(onRemoved: { Task { await loadFavoriteIds() } })
                } else if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry") { Task { await load() } }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 10)
                } else if posts == nil {
                    HoodFeedSkeleton()
                } else if posts!.isEmpty && (view != .neighborhood || neighborhoodName != nil) {
                    // Real copy-voice fix (item 244, round 5 of the empty-state pass,
                    // ported from the same-day Android fix): say what's missing AND
                    // what fixes it, per this screen's own real "+ Post a job" button
                    // above in the MINE view.
                    Text(
                        view == .browse ? "No jobs posted yet — check back soon, or post one yourself."
                            : view == .neighborhood ? "No jobs in your neighborhood yet — try Browse to see jobs from everywhere."
                            : view == .worked ? "No completed jobs recorded yet — jobs you complete will show up here."
                            : "You haven't posted any jobs yet — tap \"+ Post a job\" above to post your first one."
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
                            posterTrustScore: trustScores[post.posterId],
                            currentUserId: currentUserId
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
        if view == .wishlist || view == .applications || view == .resume {
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

    private func searchJobs() async {
        let q = jobSearchInput.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        jobSearching = true
        do {
            let res = try await NetworkClient.shared.searchJobPosts(q)
            jobSearchResults = res.posts
            jobSearchTrustScores = res.trustScores ?? [:]
        } catch {
            jobSearchResults = []
        }
        jobSearching = false
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

struct JobPostWishlistView: View {
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
                .padding(.vertical, 10)
            } else if favorites == nil {
                HoodFeedSkeleton()
            } else if favorites!.isEmpty {
                EmptyStateView("No saved jobs yet — tap ♡ on a job to keep it here.")
                    .foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(favorites!) { favorite in
                    HStack {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(favorite.title).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                            Text("\(favorite.category) · \(formatAmount(Int(favorite.payAmount))) RWF")
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
                    .padding(.vertical, 10)
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

// Real "My applications" status view (2026-07-25 on Android as item 196, ported here
// 2026-07-29) -- an applicant could submit a real structured application and message
// the poster, but never see whether it was pending/accepted/declined. JobApplicationDto
// carries no job-post title snapshot, so this fans out one real getJobPost per
// application to resolve the title, same N+1 shape Android's own port uses.
struct MyJobApplicationsView: View {
    @State private var applications: [(application: JobApplicationDto, title: String?)]?
    @State private var error: String?

    var body: some View {
        Group {
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry") { Task { await load() } }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 10)
            } else if applications == nil {
                HoodFeedSkeleton()
            } else if applications!.isEmpty {
                Text("You haven't applied to any jobs yet.").foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(applications!, id: \.application.id) { entry in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(entry.title ?? "Job post").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        Text(entry.application.message).font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
                        Text(entry.application.status)
                            .font(.caption2).bold()
                            .foregroundColor(entry.application.status == "ACCEPTED" ? IDS.Colors.brand : entry.application.status == "DECLINED" ? .red : IDS.Colors.textSecondary)
                            .padding(.horizontal, 8).padding(.vertical, 2)
                            .background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                    .padding(.vertical, 10)
                    Divider().overlay(IDS.Colors.divider)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let apps = try await NetworkClient.shared.getMyJobApplications().applications
            var withTitles: [(application: JobApplicationDto, title: String?)] = []
            for app in apps {
                let title = try? await NetworkClient.shared.getJobPost(app.jobPostId).post.title
                withTitles.append((application: app, title: title))
            }
            applications = withTitles
            error = nil
        } catch {
            self.error = "Couldn't load your applications. Check your connection and try again."
        }
    }
}

