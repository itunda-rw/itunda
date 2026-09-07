import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real fix (2026-09-08): split out of HoodCommunityPosts.swift once that file crossed
// the 500-line file-size-lint guideline for the first time (the session-attendance
// view pushed it over) -- matching Android's own identical
// CommunityMeetupsGroupBuy.kt split precedent.

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
    // Real "who attended" view (Hood product-completeness pass, 2026-09-08) -- a real
    // joined member could always check in, but nobody could ever see who else did.
    @State private var expandedSessionId: String?
    @State private var attendanceBySession: [String: [SessionAttendeeDto]] = [:]
    @State private var loadingAttendanceId: String?
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
                    VStack(alignment: .leading, spacing: 4) {
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
                        Button(action: { Task { await toggleAttendance(s.id) } }) {
                            Text(expandedSessionId == s.id ? "Hide attendance" : "View attendance")
                                .font(.caption).foregroundColor(IDS.Colors.brand)
                        }
                        if expandedSessionId == s.id {
                            if loadingAttendanceId == s.id {
                                SkeletonBlock(height: 20)
                            } else if let attendees = attendanceBySession[s.id], !attendees.isEmpty {
                                ForEach(attendees) { a in
                                    Text("✓ \(a.userName)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                }
                            } else {
                                Text("No one has checked in yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            }
                        }
                    }
                    .padding(.vertical, 10)
                    Divider().overlay(IDS.Colors.divider)
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
                .padding(.vertical, 10)
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
        } catch let NetworkError.httpError(statusCode) where statusCode == 409 {
            checkedInIds.insert(sessionId)
        } catch {
            self.error = "Could not check in to this session."
        }
    }

    private func toggleAttendance(_ sessionId: String) async {
        if expandedSessionId == sessionId {
            expandedSessionId = nil
            return
        }
        expandedSessionId = sessionId
        guard attendanceBySession[sessionId] == nil else { return }
        loadingAttendanceId = sessionId
        defer { loadingAttendanceId = nil }
        do {
            let res = try await NetworkClient.shared.getSessionAttendance(sessionId)
            attendanceBySession[sessionId] = res.attendance
        } catch {
            self.error = "Could not load attendance for this session."
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
                .padding(.vertical, 10).frame(maxWidth: .infinity, alignment: .leading)
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
            .padding(.vertical, 10)
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
        } catch let NetworkError.httpErrorWithMessage(_, message) {
            self.error = message ?? "Could not split this cost."
        } catch {
            self.error = "Could not split this cost."
        }
    }
}
