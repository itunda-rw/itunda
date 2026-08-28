import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real group 공지/투표 (announcement/poll) (itunda Talk redesign, 2026-08-28) -- see
// backend GroupPollAnnouncementService's own doc comment. Deliberately open to ANY
// group member -- no admin/role gate exists in this codebase's group chat, and this
// doesn't invent one as a side effect. Opens as a sibling sheet over the group
// thread, same pattern GroupManageMembersView/TalkSplitBills already establish.
struct TalkGroupAnnouncementPollView: View {
    let groupId: String

    @Environment(\.dismiss) private var dismiss
    @State private var announcement: GroupAnnouncementDto?
    @State private var polls: [GroupPollWithOptionsDto]?
    @State private var error: String?
    @State private var showComposeAnnouncement = false
    @State private var showCreatePoll = false
    @State private var draftAnnouncement = ""
    @State private var posting = false

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    announcementSection
                    pollsSection
                }
                .padding(20)
            }
            .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
            .navigationTitle("Announcement & polls")
            .navigationBarItems(trailing: Button("Close") { dismiss() })
        }
        .task { await load() }
        .sheet(isPresented: $showComposeAnnouncement) {
            ComposeAnnouncementSheet(draft: $draftAnnouncement, posting: posting, onPost: {
                Task { await postAnnouncement() }
            })
        }
        .sheet(isPresented: $showCreatePoll) {
            CreatePollSheet(onCreate: { question, options in
                Task { await createPoll(question: question, options: options) }
            })
        }
    }

    private var announcementSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Announcement").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Button(announcement == nil ? "Post" : "Update") { showComposeAnnouncement = true }
                    .font(.caption)
            }
            if let announcement {
                Text(announcement.body)
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(IDS.Colors.textPrimary)
                    .padding(14)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(IDS.Colors.chipBackground)
                    .cornerRadius(12)
            } else {
                Text("No announcement yet.")
                    .font(.caption)
                    .foregroundColor(IDS.Colors.textSecondary)
            }
        }
    }

    private var pollsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Polls").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Button("New poll") { showCreatePoll = true }.font(.caption)
            }
            if let error {
                Text(error).foregroundColor(.red).font(.caption)
            }
            if polls == nil {
                SkeletonBlock(height: 80)
            } else if polls!.isEmpty {
                Text("No polls yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            } else {
                ForEach(polls!) { poll in
                    PollCard(poll: poll, onVote: { optionId in Task { await vote(pollId: poll.id, optionId: optionId) } })
                }
            }
        }
    }

    private func load() async {
        async let a = try? NetworkClient.shared.getGroupAnnouncement(groupId: groupId)
        async let p = try? NetworkClient.shared.getGroupPolls(groupId: groupId)
        announcement = await a?.announcement
        polls = await p?.polls ?? []
    }

    private func postAnnouncement() async {
        posting = true
        defer { posting = false }
        do {
            let res = try await NetworkClient.shared.postGroupAnnouncement(groupId: groupId, body: draftAnnouncement)
            announcement = res.announcement
            draftAnnouncement = ""
            showComposeAnnouncement = false
        } catch {
            self.error = "Couldn't post that announcement. Try again."
        }
    }

    private func createPoll(question: String, options: [String]) async {
        do {
            let res = try await NetworkClient.shared.createGroupPoll(groupId: groupId, question: question, options: options)
            polls = (polls ?? []) + [res.poll]
            showCreatePoll = false
        } catch {
            self.error = "Couldn't create that poll. Try again."
        }
    }

    private func vote(pollId: String, optionId: String) async {
        do {
            let res = try await NetworkClient.shared.voteGroupPoll(groupId: groupId, pollId: pollId, optionId: optionId)
            polls = polls?.map { $0.id == pollId ? res.poll : $0 }
        } catch {
            self.error = "Couldn't record your vote. Try again."
        }
    }
}

private struct PollCard: View {
    let poll: GroupPollWithOptionsDto
    let onVote: (String) -> Void

    private var totalVotes: Int { poll.voteCountByOptionId.values.reduce(0, +) }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(poll.poll.question).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
            ForEach(poll.options) { option in
                let count = poll.voteCountByOptionId[option.id] ?? 0
                let mine = poll.myVoteOptionIds.contains(option.id)
                let fraction = totalVotes > 0 ? Double(count) / Double(totalVotes) : 0
                Button(action: { onVote(option.id) }) {
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text(option.text).font(IDS.Typography.bodyMedium).foregroundColor(IDS.Colors.textPrimary)
                            if mine { Image(systemName: "checkmark.circle.fill").foregroundColor(IDS.Colors.brand).font(.caption) }
                            Spacer()
                            Text("\(count)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Capsule().fill(IDS.Colors.chipBackground).frame(height: 6)
                                Capsule().fill(IDS.Colors.brand).frame(width: geo.size.width * fraction, height: 6)
                            }
                        }
                        .frame(height: 6)
                    }
                }
                .buttonStyle(.plain)
            }
        }
        .padding(14)
        .background(IDS.Colors.chipBackground.opacity(0.5))
        .cornerRadius(12)
    }
}

private struct ComposeAnnouncementSheet: View {
    @Binding var draft: String
    let posting: Bool
    let onPost: () -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                IdsTextField("What's the announcement?", text: $draft)
                Button(posting ? "Posting..." : "Post") { onPost() }
                    .disabled(posting || draft.trimmingCharacters(in: .whitespaces).isEmpty)
                Spacer()
            }
            .padding(20)
            .navigationTitle("New announcement")
            .navigationBarItems(trailing: Button("Cancel") { dismiss() })
        }
    }
}

private struct CreatePollSheet: View {
    let onCreate: (String, [String]) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var question = ""
    @State private var options: [String] = ["", ""]

    private var validOptions: [String] { options.map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } }

    var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 16) {
                IdsTextField("Question", text: $question)
                ForEach(options.indices, id: \.self) { i in
                    IdsTextField("Option \(i + 1)", text: $options[i])
                }
                Button("Add option") { options.append("") }.font(.caption)
                Button("Create poll") { onCreate(question, validOptions) }
                    .disabled(question.trimmingCharacters(in: .whitespaces).isEmpty || validOptions.count < 2)
                Spacer()
            }
            .padding(20)
            .navigationTitle("New poll")
            .navigationBarItems(trailing: Button("Cancel") { dismiss() })
        }
    }
}
