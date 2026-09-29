import SwiftUI
import UIKit
import CoreLocation
import CoreDesignSystem
import CoreNetwork


struct CommunityPostCard: View {
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
            HStack(spacing: 4) {
                HeartFilled(size: 12)
                Text("\(post.likeCount) ·")
                SpeechBubbleGlyph(size: 12)
                Text("\(post.commentCount)")
            }
            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
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
                // Real AI-generated 모임 summary (2026-08-28) -- see backend
                // HoodAiSummaryService's own doc comment. Never shown without this
                // visible "AI" disclosure badge, same convention this session's Maps
                // AI-summary work already established.
                if let summary = post.aiSummary {
                    HStack(alignment: .top, spacing: 6) {
                        Text("AI").font(.caption2).bold().foregroundColor(.white)
                            .padding(.horizontal, 5).padding(.vertical, 2)
                            .background(IDS.Colors.brand).cornerRadius(4)
                        Text(summary).font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    .padding(10)
                    .background(IDS.Colors.chipBackground)
                    .cornerRadius(10)
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
        .cornerRadius(IDS.Layout.cardCornerRadius).idsCardBorder(cornerRadius: IDS.Layout.cardCornerRadius)
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

struct CommunityPostDetailView: View {
    let postId: String
    let onBack: () -> Void

    @State private var post: CommunityPostDto?
    @State private var authorName = ""
    @State private var likedByMe = false
    @State private var comments: [CommunityCommentWithAuthorDto]?
    // Real pagination-discard fix (2026-09-13, porting web/Android's own fix -- see
    // project_itunda_pagination_discard_sweep memory) -- getCommunityComments silently
    // capped this thread at its oldest 20 comments (backend sorts ascending by
    // createdAt), so a post with 20+ comments never showed any newer ones at all --
    // including a user's own comment right after posting it.
    @State private var commentsPage = 0
    @State private var commentsHasMore = false
    @State private var loadingMoreComments = false
    @State private var myName: String?
    @State private var commentBody = ""
    @State private var error: String?
    @State private var liking = false
    @State private var commenting = false
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 17, relativeTo: .body) }.accessibilityLabel("Back")
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
                                HStack(spacing: 6) {
                                    WishlistHeart(favorited: likedByMe, size: 14)
                                    Text("\(post.likeCount)")
                                }
                                    .font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                    .padding(.horizontal, 16).padding(.vertical, 10)
                                    .background(IDS.Colors.chipBackground).cornerRadius(12)
                            }
                            .disabled(liking)
                        }
                        .padding(.vertical, 10)

                        if post.category == "meetup" {
                            MeetupSessionsSection(post: post, currentUserId: currentUserId)
                        }
                        if post.category == "group_buy" {
                            GroupBuyFinalizeSection(post: post, currentUserId: currentUserId)
                        }
                    }

                    Text("Comments").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    if comments == nil {
                        SkeletonBlock(height: 60)
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
                        if commentsHasMore {
                            Button(loadingMoreComments ? "Loading…" : "Load more comments") {
                                Task { await loadMoreComments() }
                            }
                            .disabled(loadingMoreComments)
                        }
                    }
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, 12)
                .padding(.bottom, 12)
            }

            HStack {
                IdsTextField("Add a comment", text: $commentBody)
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
        if let res = try? await NetworkClient.shared.getCommunityComments(postId, page: 0) {
            comments = res.comments
            commentsPage = 0
            commentsHasMore = res.page + 1 < res.totalPages
        }
        if let profile = try? await NetworkClient.shared.getProfile().user {
            myName = "\(profile.firstName) \(profile.lastName)"
        }
    }

    private func loadMoreComments() async {
        let nextPage = commentsPage + 1
        loadingMoreComments = true
        defer { loadingMoreComments = false }
        guard let res = try? await NetworkClient.shared.getCommunityComments(postId, page: nextPage) else { return }
        let existingIds = Set((comments ?? []).map { $0.comment.id })
        comments = (comments ?? []) + res.comments.filter { !existingIds.contains($0.comment.id) }
        commentsPage = nextPage
        commentsHasMore = res.page + 1 < res.totalPages
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
            let comment = try await NetworkClient.shared.addCommunityComment(postId, body: commentBody).comment
            commentBody = ""
            // Real fix: append directly rather than reloading page 0 -- page 0 only
            // ever holds the OLDEST comments (ascending sort), so once a post has
            // 20+ comments, reloading page 0 would make the user's own just-posted
            // comment disappear entirely.
            comments = (comments ?? []) + [CommunityCommentWithAuthorDto(comment: comment, authorName: myName ?? "You")]
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

// MeetupSessionsSection/GroupBuyFinalizeSection moved out to
// HoodCommunityMeetupsGroupBuy.swift (2026-09-08) once this file crossed the 500-line
// file-size-lint guideline, matching Android's own identical split precedent.

// Real Karrot 동네생활 "새 댓글 알림 끄기" (turn off new-comment notifications) -- ported
// from bank-mfe (2026-09-03, real gap: fully built on the backend, wired on web, zero
// client on native). Scoped to MY posts (the preference only affects notifications
// about comments on posts the caller authored), same real reason this renders only
// inside the "My posts" view.
struct CommentNotificationToggle: View {
    @State private var enabled: Bool?
    @State private var busy = false

    var body: some View {
        if let current = enabled {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Notify me about new comments").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    Text("On your own posts, in this neighborhood").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
                Toggle("", isOn: Binding(
                    get: { current },
                    set: { next in
                        busy = true
                        Task {
                            if let result = try? await NetworkClient.shared.setCommentNotificationsEnabled(next) {
                                enabled = result.commentNotificationsEnabled
                            }
                            busy = false
                        }
                    },
                ))
                .labelsHidden()
                .disabled(busy)
            }
            .padding(.vertical, 10)
        } else {
            Color.clear.frame(height: 0)
                .task {
                    enabled = (try? await NetworkClient.shared.getCommentNotificationsEnabled().commentNotificationsEnabled) ?? true
                }
        }
    }
}

// ============================== JOBS (당근알바) ==============================

