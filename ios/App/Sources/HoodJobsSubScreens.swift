import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork

// Real fix (2026-09-13): split out of HoodJobs.swift once that file crossed the
// 500-line file-size-lint guideline for the first time (the pagination-discard fix
// on JobPostWishlistView pushed it over). These 2 sub-screens (wishlist, my
// applications) are self-contained, only rendered inside JobsContent's own sheet/tab
// flow -- distinct from the main feed (JobsContent) left behind. Same target, so zero
// import changes anywhere else.

struct JobPostWishlistView: View {
    let onRemoved: () -> Void

    @State private var favorites: [FavoriteJobPostDto]?
    @State private var error: String?
    @State private var removingId: String?
    // Real pagination-discard fix (2026-09-13, porting web's/Android's own fix -- see
    // project_itunda_pagination_discard_sweep memory) -- getMyFavoriteJobPosts
    // silently capped this list at the first 20 favorited jobs.
    @State private var page = 0
    @State private var hasMore = false
    @State private var loadingMore = false

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
                if hasMore {
                    Button(action: { Task { await loadMore() } }) {
                        Text(loadingMore ? "Loading…" : "Load more").bold().font(.caption)
                            .frame(maxWidth: .infinity).padding(.vertical, 12)
                            .background(Color(.secondarySystemBackground)).cornerRadius(10)
                    }
                    .disabled(loadingMore)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getMyFavoriteJobPosts(page: 0)
            favorites = res.favorites
            page = 0
            hasMore = res.page + 1 < res.totalPages
            error = nil
        } catch {
            self.error = "Couldn't load your saved jobs. Check your connection and try again."
        }
    }

    private func loadMore() async {
        let nextPage = page + 1
        loadingMore = true
        defer { loadingMore = false }
        do {
            let res = try await NetworkClient.shared.getMyFavoriteJobPosts(page: nextPage)
            favorites = (favorites ?? []) + res.favorites
            page = nextPage
            hasMore = res.page + 1 < res.totalPages
        } catch {
            // Non-critical -- the already-loaded page stays visible; the user
            // can retry by tapping "Load more" again.
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
