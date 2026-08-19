import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork


// Real @mention composer UI -- ports Android TalkScreen.kt's own identical addition
// (2026-08-04) to iOS. GroupMessagingService.parseMentions (backend) already resolves
// `@FirstName` tokens against real group members purely from the message body text --
// no separate mentionedUserIds field on the send request, so this composer only needs
// to insert the right text, not call any new endpoint. v1 scope matches the backend's
// own honest limitation (first-name collisions resolve to whichever member matches
// first): only suggests/inserts a plain `@FirstName` token, not a richer inline chip.
func activeMentionQuery(_ draft: String) -> String? {
    guard let atIndex = draft.lastIndex(of: "@") else { return nil }
    let tail = draft[draft.index(after: atIndex)...]
    if tail.contains(" ") || tail.contains("\n") { return nil }
    return String(tail)
}

func applyMention(_ draft: String, memberName: String) -> String {
    guard let atIndex = draft.lastIndex(of: "@") else { return draft }
    let firstName = memberName.trimmingCharacters(in: .whitespaces).split(separator: " ").first.map(String.init) ?? memberName
    return String(draft[draft.startIndex..<atIndex]) + "@\(firstName) "
}

struct MentionSuggestions: View {
    let draft: String
    let members: [GroupMemberDto]
    let currentUserId: String?
    let onPick: (String) -> Void

    var body: some View {
        if let query = activeMentionQuery(draft) {
            let matches = members.filter {
                $0.userId != currentUserId && ($0.name.split(separator: " ").first.map(String.init) ?? $0.name).lowercased().hasPrefix(query.lowercased())
            }
            if !matches.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(matches) { member in
                            let firstName = member.name.split(separator: " ").first.map(String.init) ?? member.name
                            Button(action: { onPick(member.name) }) {
                                Text("@\(firstName)")
                                    .font(.caption).bold().foregroundColor(.white)
                                    .padding(.horizontal, 12).padding(.vertical, 6)
                                    .background(IDS.Colors.brand)
                                    .clipShape(Capsule())
                            }
                        }
                    }
                }
                .padding(.bottom, 6)
            }
        }
    }
}

struct GroupThreadScreen: View {
    let group: GroupSummaryDto
    let onBack: () -> Void

    @State private var messages: [GroupMessageDto]?
    @State private var members: [GroupMemberDto] = []
    @State private var draft = ""
    @State private var replyingTo: GroupMessageDto?
    // Real group Pin -- exact mirror of ChatThreadScreen's own 1:1 pinnedMessage state.
    @State private var pinnedMessage: GroupMessageDto?
    @State private var updatingPin = false
    // Real Forward -- see ForwardPickerView's own doc comment.
    @State private var forwarding: GroupMessageDto?
    // Real Thread support (2026-08-05) -- see RepliesThreadView's own doc comment.
    @State private var openThreadFor: GroupMessageDto?
    @State private var sending = false
    @State private var error: String?
    @State private var socketTask: URLSessionWebSocketTask?
    @State private var typingUserIds: [String: Task<Void, Never>] = [:]
    @State private var lastTypingSentAt: Date = .distantPast
    // Real split-bill/manage-members (found 2026-07-22 fully built on the backend
    // with zero UI anywhere) -- opens as a sibling sheet over this same thread.
    @State private var showSplitBills = false
    @State private var showManageMembers = false
    // Real KakaoTalk Emoticon Store, group-send side (item 133/204) -- see
    // NetworkClient.sendGroupEmoticon's own doc comment. 1:1 chat has had this since
    // the Emoticon Store shipped; group chat never got a client for the identical,
    // already-real backend endpoint. Found 2026-07-29 via the defined-but-uncalled-
    // method sweep.
    @State private var emoticonPickerOpen = false
    @State private var emoticonStoreOpen = false
    @State private var emoticonImageById: [String: String] = [:]
    // Real attach ("+") menu + photo send/gallery -- ports Android TalkScreen.kt's own
    // identical addition (2026-08-04) to iOS. Reuses ImagePickerView (HoodScreen.swift)
    // and NetworkClient.uploadPhoto, both already real since 2026-08-01; this is just
    // the Talk-composer wiring. SwiftUI's native `Menu` manages its own open/closed
    // state, unlike Android's DropdownMenu, so no separate `showAttachMenu` flag is
    // needed here.
    @State private var showPhotoPicker = false
    @State private var uploadingPhoto = false
    @State private var showMediaGallery = false
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    private func name(for senderId: String) -> String {
        members.first(where: { $0.userId == senderId })?.name ?? String(senderId.prefix(8))
    }

    private func sendPhoto(_ image: UIImage) async {
        guard let jpegData = image.jpegData(compressionQuality: 0.8) else {
            error = "Couldn't read that photo."
            return
        }
        uploadingPhoto = true
        defer { uploadingPhoto = false }
        do {
            let uploaded = try await NetworkClient.shared.uploadPhoto(data: jpegData, filename: "photo.jpg", mimeType: "image/jpeg")
            let res = try await NetworkClient.shared.sendGroupMessage(groupId: group.groupId, body: "", replyToMessageId: replyingTo?.id, imageUrl: uploaded.url)
            if res.success {
                replyingTo = nil
                messages = (messages ?? []) + [res.message]
            }
        } catch {
            self.error = "Couldn't upload that photo. Check your connection and try again."
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left").font(.system(size: 18, weight: .medium)).frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                Text(group.name).font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                Spacer()
                Button(action: { showMediaGallery = true }) {
                    Image(systemName: "photo.on.rectangle").font(.system(size: 18)).frame(width: 40, height: 40)
                }
                .accessibilityLabel("Shared photos")
                Button(action: { showManageMembers = true }) {
                    Image(systemName: "person.2").font(.system(size: 18)).frame(width: 40, height: 40)
                }
                .accessibilityLabel("Manage members")
                Button(action: { showSplitBills = true }) {
                    Image(systemName: "receipt").font(.system(size: 18)).frame(width: 40, height: 40)
                }
                .accessibilityLabel("Split a bill")
            }
            .padding(.horizontal, 8)

            if let pinnedMessage {
                HStack(spacing: 8) {
                    Text("📌 \(pinnedMessage.body)").font(.caption).lineLimit(1)
                    Spacer()
                    Button("Unpin") { Task { await unpinMessage() } }
                        .font(.caption).disabled(updatingPin)
                }
                .padding(8).background(IDS.Colors.chipBackground).cornerRadius(10)
                .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        if let messages {
                            if messages.isEmpty {
                                Text("Say hello — no messages yet.").foregroundColor(IDS.Colors.textSecondary).padding(.top, 20)
                            }
                            ForEach(Array(messages.enumerated()), id: \.element.id) { index, message in
                                GroupMessageBubble(
                                    message: message, isMine: message.senderId == currentUserId, senderName: name(for: message.senderId),
                                    currentUserId: currentUserId,
                                    onToggleReaction: { emoji in Task { await toggleReaction(message.id, emoji) } },
                                    onReply: { replyingTo = $0 },
                                    onOpenThread: { openThreadFor = $0 },
                                    onDelete: { messageId in Task { await deleteGroupMessage(messageId) } },
                                    onPin: { pinned in Task { await pinMessage(pinned) } },
                                    onForward: { forwarding = $0 },
                                    emoticonImageUrl: message.emoticonId.flatMap { emoticonImageById[$0] },
                                    showTimestamp: shouldShowChatTimestamp(messages, index, senderId: { $0.senderId }, sentAt: { $0.sentAt }),
                                )
                                .id(message.id)
                            }
                        } else {
                            ProgressView().padding(.top, 20)
                        }
                    }
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                    .padding(.top, 12)
                }
                .onChange(of: messages?.count) { _ in
                    if let last = messages?.last?.id {
                        withAnimation { proxy.scrollTo(last, anchor: .bottom) }
                    }
                }
            }

            if !typingUserIds.isEmpty {
                let names = typingUserIds.keys.map { name(for: $0) }
                Text("\(names.joined(separator: ", ")) \(names.count == 1 ? "is" : "are") typing…")
                    .font(.caption)
                    .foregroundColor(IDS.Colors.textSecondary)
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            if let error {
                Text(error).font(.caption).foregroundColor(.red).padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            if let replyingTo {
                HStack {
                    Text("Replying to: \(replyingTo.body.prefix(80))")
                        .font(.caption).foregroundColor(IDS.Colors.textSecondary).lineLimit(1)
                    Spacer()
                    Button("×") { self.replyingTo = nil }.foregroundColor(IDS.Colors.textSecondary)
                }
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.bottom, 4)
            }
            if emoticonPickerOpen {
                EmoticonPickerPanel(
                    onSend: { emoticonId in
                        Task {
                            if let res = try? await NetworkClient.shared.sendGroupEmoticon(groupId: group.groupId, emoticonId: emoticonId) {
                                messages = (messages ?? []) + [res.message]
                            }
                            emoticonPickerOpen = false
                        }
                    },
                    onOpenStore: { emoticonStoreOpen = true }
                )
                .padding(.horizontal, IDS.Layout.screenHorizontal)
            }
            MentionSuggestions(draft: draft, members: members, currentUserId: currentUserId, onPick: { name in draft = applyMention(draft, memberName: name) })
                .padding(.horizontal, IDS.Layout.screenHorizontal)
            HStack {
                // Real attach ("+") menu (2026-08-04 on Android, ported to iOS) -- Kakao's
                // own real "+"-opens-a-menu pattern (References table: "'+' opens a
                // multi-function attach menu").
                Menu {
                    Button("📷 Photo") { showPhotoPicker = true }
                    Button("😊 Emoticon") { emoticonPickerOpen.toggle() }
                } label: {
                    Text(uploadingPhoto ? "…" : "+")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(IDS.Colors.textPrimary)
                        .frame(width: 44, height: 44)
                        .background(IDS.Colors.chipBackground)
                        .clipShape(Circle())
                }
                .disabled(uploadingPhoto)
                .accessibilityLabel("Attach")
                IdsTextField("Message", text: Binding(
                    get: { draft },
                    set: { newValue in
                        draft = newValue
                        if Date().timeIntervalSince(lastTypingSentAt) > 2 {
                            lastTypingSentAt = Date()
                            if let socketTask {
                                NetworkClient.shared.sendTyping(socketTask, groupConversationId: group.groupId)
                            }
                        }
                    }
                ))
                Button(action: { Task { await send() } }) {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(draft.isEmpty || sending ? IDS.Colors.textTertiary : IDS.Colors.brand)
                        .clipShape(Circle())
                }.accessibilityLabel("Send")
                .disabled(draft.isEmpty || sending)
            }
            .padding(IDS.Layout.screenHorizontal)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
        .task {
            pinnedMessage = try? await NetworkClient.shared.getPinnedGroupMessage(groupId: group.groupId).message
        }
        // Real member list with real resolved display names (2026-07-18), fetched once
        // per thread open -- closes the honest, named limitation this UI carried since
        // group chat first shipped (a truncated sender id instead of a real name).
        .task {
            do {
                members = try await NetworkClient.shared.getGroupMembers(groupId: group.groupId).members
            } catch {
                // Real, non-critical -- a failed member-list fetch shouldn't block the
                // thread; bubbles just fall back to a truncated sender id.
            }
        }
        // Real KakaoTalk Emoticon Store -- see ConversationThread's own identical
        // image-map loader doc comment.
        .task {
            guard let packs = try? await NetworkClient.shared.getEmoticonPacks().packs else { return }
            var map: [String: String] = [:]
            for pack in packs {
                if let emoticons = try? await NetworkClient.shared.getPackEmoticons(packId: pack.id).emoticons {
                    for e in emoticons { map[e.id] = e.imageUrl }
                }
            }
            emoticonImageById = map
        }
        // Real poll, kept as an always-correct fallback delivery path alongside the
        // real WebSocket push below -- matches 1:1 messaging's own scope exactly.
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                await refresh()
            }
        }
        // Real WebSocket live-transport for group chat -- same socket 1:1 already
        // uses, routing on push type via MessagingSocketPush.
        .onAppear {
            socketTask = NetworkClient.shared.connectMessagingSocket { push in
                switch push {
                case .groupMessage(let groupId, let pushedMessage) where groupId == group.groupId:
                    Task { @MainActor in
                        typingUserIds[pushedMessage.senderId]?.cancel()
                        typingUserIds[pushedMessage.senderId] = nil
                        if !(messages ?? []).contains(where: { $0.id == pushedMessage.id }) {
                            messages = (messages ?? []) + [pushedMessage]
                        }
                    }
                case .typingChange(_, let groupId, let userId) where groupId == group.groupId:
                    Task { @MainActor in
                        typingUserIds[userId]?.cancel()
                        typingUserIds[userId] = Task {
                            try? await Task.sleep(nanoseconds: 3_000_000_000)
                            if !Task.isCancelled { typingUserIds[userId] = nil }
                        }
                    }
                case .reactionChange(_, let groupId, let messageId, let reactions) where groupId == group.groupId:
                    Task { @MainActor in
                        messages = messages?.map { $0.id == messageId ? $0.withReactions(reactions) : $0 }
                    }
                default:
                    break
                }
            }
        }
        .onDisappear {
            socketTask?.cancel(with: .goingAway, reason: nil)
            typingUserIds.values.forEach { $0.cancel() }
        }
        .sheet(isPresented: $showSplitBills) {
            GroupSplitBillsView(groupConversationId: group.groupId, members: members, currentUserId: currentUserId)
        }
        .sheet(isPresented: $showManageMembers) {
            GroupManageMembersView(
                group: group, members: members, currentUserId: currentUserId,
                onMembersChanged: { Task { members = (try? await NetworkClient.shared.getGroupMembers(groupId: group.groupId).members) ?? members } },
                onLeft: { showManageMembers = false; onBack() }
            )
        }
        .sheet(isPresented: $emoticonStoreOpen) {
            EmoticonStoreView(onClose: { emoticonStoreOpen = false })
        }
        .sheet(isPresented: $showPhotoPicker) {
            ImagePickerView { image in
                showPhotoPicker = false
                if let image { Task { await sendPhoto(image) } }
            }
        }
        .sheet(isPresented: $showMediaGallery) {
            MediaGalleryView(imageUrls: (messages ?? []).compactMap { $0.imageUrl }.reversed())
        }
        .sheet(item: $forwarding) { message in
            ForwardPickerView(
                onForward: { destinationType, destinationId in
                    (try? await NetworkClient.shared.forwardGroupMessage(messageId: message.id, destinationType: destinationType, destinationId: destinationId).success) ?? false
                },
                onDismiss: { forwarding = nil }
            )
        }
        // Real Thread support (2026-08-05) -- see RepliesThreadView's own doc comment.
        .sheet(item: $openThreadFor) { root in
            GroupRepliesThreadView(
                rootMessage: root,
                currentUserId: currentUserId,
                fetchThreadMessages: { try await NetworkClient.shared.getGroupThread(groupId: group.groupId, messageId: root.id).messages },
                onSend: { body in try await NetworkClient.shared.sendGroupMessage(groupId: group.groupId, body: body, replyToMessageId: root.id) },
                onDismiss: { openThreadFor = nil; Task { await refresh() } }
            )
        }
    }

    private func refresh() async {
        do {
            let res = try await NetworkClient.shared.getGroupMessages(groupId: group.groupId)
            messages = res.messages.reversed()
        } catch {
            // Keep showing the last-known messages rather than blanking the thread on
            // a transient poll failure.
        }
    }

    private func pinMessage(_ message: GroupMessageDto) async {
        updatingPin = true
        defer { updatingPin = false }
        do {
            _ = try await NetworkClient.shared.pinGroupMessage(groupId: group.groupId, messageId: message.id)
            pinnedMessage = message
        } catch { self.error = "Couldn't pin this message. Check your connection and try again." }
    }

    private func unpinMessage() async {
        updatingPin = true
        defer { updatingPin = false }
        do {
            _ = try await NetworkClient.shared.unpinGroupMessage(groupId: group.groupId)
            pinnedMessage = nil
        } catch { self.error = "Couldn't unpin this message. Check your connection and try again." }
    }

    private func toggleReaction(_ groupMessageId: String, _ emoji: String) async {
        do {
            let res = try await NetworkClient.shared.toggleGroupReaction(groupMessageId: groupMessageId, emoji: emoji)
            messages = messages?.map { $0.id == groupMessageId ? $0.withReactions(res.reactions) : $0 }
        } catch {
            // Best-effort -- a failed reaction toggle just leaves the badge as it was.
        }
    }

    private func deleteGroupMessage(_ messageId: String) async {
        do { _ = try await NetworkClient.shared.deleteGroupMessage(groupId: group.groupId, messageId: messageId); await refresh() }
        catch { self.error = "Couldn't delete this message. Check your connection and try again." }
    }

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        sending = true
        error = nil
        defer { sending = false }
        do {
            let res = try await NetworkClient.shared.sendGroupMessage(groupId: group.groupId, body: body, replyToMessageId: replyingTo?.id)
            draft = ""
            replyingTo = nil
            messages = (messages ?? []) + [res.message]
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

