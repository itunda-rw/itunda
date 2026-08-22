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

struct CommunityPostDetailView: View {
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
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button(action: onBack) { Image(systemName: "chevron.left") }.accessibilityLabel("Back")
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
                        .padding(18)
                        .background(IDS.Colors.card)
                        .cornerRadius(IDS.Layout.cardCornerRadius)

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

/// Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- see
/// rw.itunda.community.CommunityService.scheduleMeetupSessions/checkIntoSession's own doc
/// comments. Only rendered for a real category == "meetup" post; the author gets a real
/// schedule form, any real joined member gets a real per-session check-in button.
/// bank-mfe/Android already have this; this is the first iOS client.
struct MeetupSessionsSection: View {
    let post: CommunityPostDto
    let currentUserId: String?

    @State private var sessions: [MeetupSessionDto]?
    @State private var dates: [String] = [""]
    @State private var scheduling = false
    @State private var checkingInId: String?
    @State private var checkedInIds: Set<String> = []
    @State private var error: String?

    private var isAuthor: Bool { currentUserId != nil && currentUserId == post.authorId }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Sessions").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            if sessions == nil {
                SkeletonBlock(height: 40)
            } else if sessions!.isEmpty {
                Text("No sessions scheduled yet.").font(.subheadline).foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(sessions!) { s in
                    HStack {
                        Text(String(s.scheduledFor.replacingOccurrences(of: "T", with: " ").prefix(16)))
                            .font(.subheadline).foregroundColor(IDS.Colors.textPrimary)
                        Spacer()
                        Button(action: { Task { await checkIn(s.id) } }) {
                            Text(checkedInIds.contains(s.id) ? "✓ Checked in" : (checkingInId == s.id ? "…" : "Check in"))
                                .font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 6)
                                .background(IDS.Colors.chipBackground).cornerRadius(8)
                        }
                        .disabled(checkingInId == s.id || checkedInIds.contains(s.id))
                    }
                    .padding(12).background(IDS.Colors.card).cornerRadius(12)
                }
            }
            if isAuthor {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Schedule sessions (up to 6)").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    ForEach(dates.indices, id: \.self) { i in
                        IdsTextField("Hours from now", text: Binding(get: { dates[i] }, set: { dates[i] = $0 }), keyboardType: .decimalPad)
                    }
                    HStack(spacing: 8) {
                        if dates.count < 6 {
                            Button("+ Add date") { dates.append("") }
                                .font(.caption).bold().foregroundColor(IDS.Colors.brand)
                        }
                        Button(action: { Task { await schedule() } }) {
                            Text(scheduling ? "Scheduling…" : "Schedule").bold().foregroundColor(.white)
                                .padding(.horizontal, 14).padding(.vertical, 8)
                                .background(IDS.Colors.brand).cornerRadius(8)
                        }
                        .disabled(scheduling)
                    }
                }
                .padding(12).background(IDS.Colors.card).cornerRadius(12)
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
        }
        .task { await load() }
    }

    private func load() async {
        sessions = (try? await NetworkClient.shared.getMeetupSessions(post.id).sessions) ?? []
    }

    private func checkIn(_ sessionId: String) async {
        checkingInId = sessionId
        defer { checkingInId = nil }
        do {
            _ = try await NetworkClient.shared.checkIntoMeetupSession(sessionId)
            checkedInIds.insert(sessionId)
        } catch {
            self.error = "Could not check in to this session."
        }
    }

    private func schedule() async {
        let isoDates = dates.compactMap { Double($0) }.map { hours in
            ISO8601DateFormatter().string(from: Date().addingTimeInterval(hours * 3600))
        }
        guard !isoDates.isEmpty else {
            error = "Add at least one session date."
            return
        }
        scheduling = true
        error = nil
        defer { scheduling = false }
        do {
            _ = try await NetworkClient.shared.scheduleMeetupSessions(post.id, dates: isoDates)
            dates = [""]
            await load()
        } catch {
            self.error = "Could not schedule these sessions."
        }
    }
}

/// Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see
/// rw.itunda.community.CommunityService.finalizeGroupBuy's own doc comment. Author-only:
/// once real participants have joined via the same 참여하기 flow a meetup already uses,
/// the organizer fronts the total cost and splits it via the already-real SplitBill
/// mechanic. bank-mfe/Android already have this; this is the first iOS client.
struct GroupBuyFinalizeSection: View {
    let post: CommunityPostDto
    let currentUserId: String?

    @State private var totalAmount = ""
    @State private var description = ""
    @State private var submitting = false
    @State private var done = false
    @State private var error: String?

    var body: some View {
        if currentUserId == nil || currentUserId != post.authorId {
            EmptyView()
        } else if done {
            Text("Split request sent -- see it in your group chat's Split bill tab.")
                .font(.subheadline).bold().foregroundColor(IDS.Colors.brand)
                .padding(14).frame(maxWidth: .infinity, alignment: .leading)
                .background(IDS.Colors.card).cornerRadius(12)
        } else {
            VStack(alignment: .leading, spacing: 8) {
                Text("Split the cost").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                Text("Enter what you paid up front -- every real member who joined will be asked for their even share.")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                IdsTextField("Total amount (RWF)", text: $totalAmount, keyboardType: .decimalPad)
                IdsTextField("What was this for?", text: $description)
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                Button(action: { Task { await finalize() } }) {
                    Text(submitting ? "Splitting…" : "Request even split").bold().foregroundColor(.white)
                        .frame(maxWidth: .infinity).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(10)
                }
                .disabled(submitting)
            }
            .padding(14).background(IDS.Colors.card).cornerRadius(12)
        }
    }

    private func finalize() async {
        guard let amount = Double(totalAmount), amount > 0, !description.trimmingCharacters(in: .whitespaces).isEmpty else {
            error = "Enter a real total amount and a short description."
            return
        }
        submitting = true
        error = nil
        defer { submitting = false }
        do {
            _ = try await NetworkClient.shared.finalizeGroupBuy(post.id, totalAmount: amount, description: description.trimmingCharacters(in: .whitespaces))
            done = true
        } catch {
            self.error = "Could not split this cost."
        }
    }
}

// ============================== JOBS (당근알바) ==============================

