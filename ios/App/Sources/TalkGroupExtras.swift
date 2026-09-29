import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork


struct GroupMessageBubble: View {
    let message: GroupMessageDto
    let isMine: Bool
    let senderName: String
    let currentUserId: String?
    let onToggleReaction: (String) -> Void
    let onReply: (GroupMessageDto) -> Void
    let onOpenThread: (GroupMessageDto) -> Void
    let onDelete: (String) -> Void
    let onPin: (GroupMessageDto) -> Void
    let onForward: (GroupMessageDto) -> Void
    var emoticonImageUrl: String?
    var showTimestamp: Bool = true

    var body: some View {
        VStack(alignment: isMine ? .trailing : .leading, spacing: 2) {
            HStack {
                if isMine { Spacer() }
                if message.emoticonId != nil {
                    VStack(alignment: .leading, spacing: 2) {
                        if !isMine {
                            Text(senderName).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                        }
                        EmoticonBubble(imageUrl: emoticonImageUrl)
                    }
                } else if let imageUrl = message.imageUrl {
                    // Real photo message -- ports Android TalkScreen.kt's own identical
                    // addition (2026-08-04) to iOS.
                    VStack(alignment: .leading, spacing: 2) {
                        if !isMine {
                            Text(senderName).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                        }
                        AsyncImage(url: URL(string: imageUrl)) { image in
                            image.resizable().aspectRatio(contentMode: .fit)
                        } placeholder: {
                            ProgressView()
                        }
                        .frame(maxWidth: 220)
                        .cornerRadius(16)
                    }
                } else {
                VStack(alignment: .leading, spacing: 2) {
                    if !isMine {
                        Text(senderName).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    }
                    MessageBodyWithEmoji(messageText: message.body, color: isMine ? .white : IDS.Colors.textPrimary, fontSize: 15)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .cornerRadius(16)
                }
                if !isMine { Spacer() }
            }
            MessageReactionsRow(reactions: message.reactions, currentUserId: currentUserId, isMine: isMine, onToggle: onToggleReaction)
            Button("Reply") { onReply(message) }
                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            if isMine && message.deletedAt == nil { Button("Delete") { onDelete(message.id) }.font(.caption2).foregroundColor(IDS.Colors.textSecondary) }
            Button("Pin") { onPin(message) }
                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            Button("Forward") { onForward(message) }
                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            if message.forwardedFromMessageId != nil {
                Text("↪ Forwarded").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            }
            if showTimestamp {
                Text(chatMessageTime(message.sentAt))
                    .font(.caption2)
                    .foregroundColor(IDS.Colors.textSecondary)
            }
            // Real Thread support (2026-08-05) -- see MessageBubble's own identical
            // affordance (docs/DESIGN_REFERENCES.md Talk section recommendation #3).
            if message.replyCount > 0 {
                Button("\(message.replyCount) \(message.replyCount == 1 ? "reply" : "replies") →") { onOpenThread(message) }
                    .font(.caption2).fontWeight(.bold).foregroundColor(IDS.Colors.brand)
            }
        }
    }
}

// Real leave-group/add-member (2026-07-22) -- found fully built on the backend
// (GroupMessagingController's POST/DELETE .../members) with zero client UI anywhere.
// Add-member picks from the caller's real Talk contacts, filtered to exclude people
// already in the group.
struct GroupManageMembersView: View {
    let group: GroupSummaryDto
    let members: [GroupMemberDto]
    let currentUserId: String?
    let onMembersChanged: () -> Void
    let onLeft: () -> Void
    @Environment(\.dismiss) private var dismiss

    @State private var contacts: [TalkContactDto] = []
    @State private var error: String?
    @State private var busyUserId: String?
    @State private var leaving = false
    // Real group photo/description (2026-07-28) -- see NetworkClient.swift's own doc
    // comment. Found 2026-08-01 via a defined-but-uncalled-endpoint sweep: real on
    // backend since it shipped, zero client anywhere on any of the 3 platforms until
    // now.
    @State private var photoUrl: String
    @State private var groupDescription: String
    @State private var savingInfo = false
    @State private var infoSaved = false

    init(group: GroupSummaryDto, members: [GroupMemberDto], currentUserId: String?, onMembersChanged: @escaping () -> Void, onLeft: @escaping () -> Void) {
        self.group = group
        self.members = members
        self.currentUserId = currentUserId
        self.onMembersChanged = onMembersChanged
        self.onLeft = onLeft
        _photoUrl = State(initialValue: group.photoUrl ?? "")
        _groupDescription = State(initialValue: group.description ?? "")
    }

    private var addable: [TalkContactDto] { contacts.filter { contact in !members.contains { $0.userId == contact.userId } } }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    Text("Group info").bold()
                    IdsTextField("Photo URL (blank to clear)", text: $photoUrl)
                    IdsTextField("Group description (blank to clear)", text: $groupDescription)
                    Button(action: { Task { await saveInfo() } }) {
                        Text(savingInfo ? "Saving…" : (infoSaved ? "Saved" : "Save group info")).bold().frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                    .disabled(savingInfo)
                    Text("Members (\(members.count))").bold().padding(.top, 8)
                    ForEach(members) { m in
                        Text(m.userId == currentUserId ? "\(m.name) (you)" : m.name).font(.subheadline)
                    }
                    Button(action: { Task { await leave() } }) {
                        Text(leaving ? "Leaving…" : "Leave group").bold().frame(maxWidth: .infinity).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                    }
                    .disabled(leaving)
                    Text("Add from your contacts").bold().padding(.top, 8)
                    if addable.isEmpty {
                        Text("No contacts left to add.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                    ForEach(addable) { contact in
                        HStack {
                            Text(contact.name)
                            Spacer()
                            Button(action: { Task { await add(contact) } }) {
                                Text(busyUserId == contact.userId ? "Adding…" : "Add").bold()
                            }
                            .disabled(busyUserId != nil)
                        }
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
            .navigationTitle("Manage members")
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("Close") { dismiss() } } }
            .task {
                contacts = (try? await NetworkClient.shared.getTalkContacts().contacts) ?? []
            }
        }
    }

    private func leave() async {
        leaving = true; error = nil
        do {
            _ = try await NetworkClient.shared.leaveGroup(groupId: group.groupId)
            onLeft()
        } catch {
            self.error = "Could not leave this group."
            leaving = false
        }
    }

    private func add(_ contact: TalkContactDto) async {
        busyUserId = contact.userId; error = nil
        defer { busyUserId = nil }
        do {
            _ = try await NetworkClient.shared.addGroupMember(groupId: group.groupId, userId: contact.userId)
            onMembersChanged()
        } catch NetworkError.httpErrorWithMessage(let statusCode, _) where statusCode == 409 {
            // Real gap found live (Toss-style error-handling audit, 2026-08-30): adding
            // a contact already in the group isn't really a failure -- resolve forward.
            // 409 is unambiguous on this specific endpoint (ALREADY_MEMBER is the only
            // exception this controller maps to 409).
            onMembersChanged()
        } catch {
            self.error = "Could not add \(contact.name)."
        }
    }

    private func saveInfo() async {
        savingInfo = true; error = nil; infoSaved = false
        defer { savingInfo = false }
        do {
            _ = try await NetworkClient.shared.setGroupPhotoUrl(groupId: group.groupId, photoUrl: photoUrl.trimmingCharacters(in: .whitespaces))
            _ = try await NetworkClient.shared.setGroupDescription(groupId: group.groupId, description: groupDescription.trimmingCharacters(in: .whitespaces))
            infoSaved = true
        } catch {
            self.error = "Could not update group info."
        }
    }
}

// Real Thread support (2026-08-05) -- see RepliesThreadView's own doc comment; identical
// shape for group chat.
struct GroupRepliesThreadView: View {
    let rootMessage: GroupMessageDto
    let currentUserId: String?
    let fetchThreadMessages: () async throws -> [GroupMessageDto]
    let onSend: (String) async throws -> GroupMessageResponse
    let onDismiss: () -> Void

    @State private var messages: [GroupMessageDto]?
    @State private var draft = ""
    @State private var sending = false
    @State private var error: String?

    var body: some View {
        NavigationView {
            VStack {
                if let error {
                    Text(error).font(.caption).foregroundColor(.red)
                }
                ScrollView {
                    LazyVStack(spacing: 8) {
                        if let messages {
                            ForEach(Array(messages.enumerated()), id: \.element.id) { index, m in
                                let isMine = m.senderId == currentUserId
                                VStack(alignment: isMine ? .trailing : .leading, spacing: 2) {
                                    if index == 0 {
                                        Text("Original message").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    HStack {
                                        if isMine { Spacer() }
                                        Group {
                                            if m.deletedAt == nil {
                                                MessageBodyWithEmoji(messageText: m.body, color: isMine ? .white : IDS.Colors.textPrimary, fontSize: 15)
                                            } else {
                                                Text("This message was deleted").font(.subheadline).foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
                                            }
                                        }
                                            .padding(.horizontal, 14).padding(.vertical, 10)
                                            .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
                                            .cornerRadius(16)
                                        if !isMine { Spacer() }
                                    }
                                    Text(chatMessageTime(m.sentAt)).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                                }
                                .frame(maxWidth: .infinity, alignment: isMine ? .trailing : .leading)
                            }
                        } else {
                            ProgressView().padding(.top, 20)
                        }
                    }
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                }
                HStack {
                    IdsTextField("Reply in thread", text: $draft)
                    Button(sending ? "…" : "Send") { Task { await send() } }
                        .disabled(sending || draft.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.bottom, 8)
            }
            .navigationTitle("Thread")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Close", action: onDismiss) }
            }
            .task { await load() }
        }
    }

    private func load() async {
        do { messages = try await fetchThreadMessages() } catch { self.error = "Could not load this thread." }
    }

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        sending = true
        defer { sending = false }
        do {
            _ = try await onSend(body)
            draft = ""
            await load()
        } catch {
            self.error = "Could not send this reply."
        }
    }
}

