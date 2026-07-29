import SwiftUI
import CoreDesignSystem
import CoreNetwork

/// Real 1:1 messaging (Kakao-style Talk tab, 2026-07-18) -- iOS mirror of Android's
/// TalkTab (SuperAppTabs.kt). See NetworkClient.swift's Messaging extension and
/// rw.itunda.messaging.MessagingService's own doc comment for the full backend
/// account, including the honest "poll-based delivery, no live transport yet" scope
/// this screen matches exactly (a 4s poll while a thread is open, same interval
/// bank-mfe/Android already use).
private enum TalkView { case direct, groups }

// Real group chat (2026-07-18) -- itunda's own KakaoTalk-style group messaging, ported
// to iOS from bank-mfe's own Direct/Groups toggle (the "single most defining KakaoTalk
// capability" this session's own project memory names). TalkScreen is now a thin
// Direct/Groups toggle wrapper; DirectMessagesList holds the exact same 1:1 logic this
// screen used to own directly.
struct TalkScreen: View {
    /// Real "message seller" hand-off from HoodScreen -- ContentView stashes the real
    /// conversation id returned by `contactSeller` here and switches to this tab; once
    /// it shows up in this screen's own real conversation list, it opens directly,
    /// mirroring Android's initialConversationId/onConsumedInitial pair exactly.
    @Binding var pendingConversationId: String?

    @State private var view: TalkView = .direct
    @State private var conversations: [ConversationSummaryDto]?
    @State private var conversationsError: String?
    @State private var openConversation: ConversationSummaryDto?
    @State private var groups: [GroupSummaryDto]?
    @State private var groupsError: String?
    @State private var openGroup: GroupSummaryDto?
    // Real online/offline presence for the list view (2026-07-19) -- a bulk on-demand
    // check for every listed contact, refreshed on a 10s cadence, a real coarser signal
    // than the 4s message poll. Per-thread real-time push happens in ChatThreadScreen.
    @State private var presence: [String: Bool] = [:]

    var body: some View {
        Group {
            if let openConversation {
                ChatThreadScreen(conversation: openConversation, onBack: {
                    self.openConversation = nil
                    Task { await loadConversations() }
                })
            } else if let openGroup {
                GroupThreadScreen(group: openGroup, onBack: {
                    self.openGroup = nil
                    Task { await loadGroups() }
                })
            } else {
                listBody
            }
        }
        .task { await loadConversations() }
        .task { await loadGroups() }
        .task(id: conversations?.map { $0.otherUserId }) { await pollPresence() }
        .onChange(of: pendingConversationId) { _ in tryOpenPending() }
        .onChange(of: conversations?.count) { _ in tryOpenPending() }
        .onChange(of: groups?.count) { _ in tryOpenPending() }
    }

    private func pollPresence() async {
        guard let otherIds = conversations?.map({ $0.otherUserId }), !otherIds.isEmpty else { return }
        while !Task.isCancelled {
            do {
                presence = try await NetworkClient.shared.getPresence(userIds: otherIds).presence
            } catch {
                // Real, non-critical -- only backs the presence dot.
            }
            try? await Task.sleep(nanoseconds: 10_000_000_000)
        }
    }

    private var listBody: some View {
        VStack(spacing: 0) {
            IdsPlainTopBar(title: "Talk")
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .padding(.top, IDS.Layout.screenTop)

            Picker("", selection: $view) {
                Text("Direct").tag(TalkView.direct)
                Text("Groups").tag(TalkView.groups)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 8)

            if view == .direct {
                DirectMessagesList(
                    conversations: conversations,
                    error: conversationsError,
                    presence: presence,
                    onRetry: { Task { await loadConversations() } },
                    onStarted: { conversationId in
                        Task {
                            await loadConversations()
                            if let match = conversations?.first(where: { $0.conversationId == conversationId }) {
                                openConversation = match
                            }
                        }
                    },
                    onOpen: { openConversation = $0 }
                )
            } else {
                GroupsList(
                    groups: groups,
                    error: groupsError,
                    onRetry: { Task { await loadGroups() } },
                    onCreated: { groupId in
                        Task {
                            await loadGroups()
                            if let match = groups?.first(where: { $0.groupId == groupId }) {
                                openGroup = match
                            }
                        }
                    },
                    onOpen: { openGroup = $0 }
                )
            }
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    // Extended 2026-07-24 to also check `groups` -- Community's "join meetup" hand-off
    // (CommunityContent's onOpenGroupChat, reusing this exact same pendingConversationId
    // mechanism) hands off a real GroupConversation id, not a 1:1 conversation id, so
    // this needs to open the group thread instead when that's what matches. Mirrors
    // Android's identical TalkScreen.kt extension.
    private func tryOpenPending() {
        guard let pending = pendingConversationId else { return }
        if let match = conversations?.first(where: { $0.conversationId == pending }) {
            openConversation = match
            pendingConversationId = nil
        } else if let match = groups?.first(where: { $0.groupId == pending }) {
            view = .groups
            openGroup = match
            pendingConversationId = nil
        }
    }

    private func loadConversations() async {
        do {
            let res = try await NetworkClient.shared.getConversations()
            conversations = res.conversations
            conversationsError = nil
        } catch {
            conversationsError = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadGroups() async {
        do {
            let res = try await NetworkClient.shared.getMyGroups()
            groups = res.groups
            groupsError = nil
        } catch {
            groupsError = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    // Real 400 case (found in a 2026-07-19 UX-copy sweep, prompted by the new
    // price-offer flow's own-offer/invalid-amount validation errors): a real,
    // user-actionable input problem was falling into the generic "Something went
    // wrong" default below, unlike bank-mfe which already surfaces the real backend
    // validation message directly. A full message pass-through would need
    // NetworkError.httpError to carry the response body, a broader networking-layer
    // change -- this generic-but-honest bucket closes the gap for every existing 400
    // across the app that already routes through this shared helper, not just price
    // offers (mirrors the identical fix made to Android's superAppErrorMessage).
    static func errorMessage(_ statusCode: Int) -> String {
        switch statusCode {
        case 400: return "Please check what you entered and try again."
        case 401, 403: return "You don't have access to do that."
        case 404: return "That couldn't be found."
        case 409: return "That's already been done, or is being processed."
        case 422: return "Insufficient funds for this order."
        case 429: return "Too many attempts -- please wait a moment and try again."
        default: return "Something went wrong. Please try again."
        }
    }
}

private struct DirectMessagesList: View {
    let conversations: [ConversationSummaryDto]?
    let error: String?
    let presence: [String: Bool]
    let onRetry: () -> Void
    let onStarted: (String) -> Void
    let onOpen: (ConversationSummaryDto) -> Void

    @State private var newChatPhone = ""
    @State private var startError: String?
    @State private var starting = false
    @State private var contacts: [TalkContactDto]?
    @State private var showArchived = false

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                VStack(alignment: .leading, spacing: 12) {
                    Text("New chat").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Text("Choose a saved contact, or enter their phone number.")
                        .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                        .foregroundColor(IDS.Colors.textSecondary)
                    if let contacts, !contacts.isEmpty {
                        Text("Your contacts")
                            .font(IDS.scaledFont(size: 12, weight: .semibold, relativeTo: .caption1))
                            .foregroundColor(IDS.Colors.textSecondary)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(contacts) { contact in
                                    Button(contact.name) { Task { await startConversation(contact: contact) } }
                                        .font(IDS.Typography.bodyBold)
                                        .lineLimit(1)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(IDS.Colors.chipBackground)
                                        .cornerRadius(12)
                                        .disabled(starting)
                                }
                            }
                        }
                    }
                    HStack {
                        TextField("+250788123456", text: $newChatPhone)
                            .keyboardType(.phonePad)
                            .padding(12)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(12)
                        Button(action: { Task { await startConversation() } }) {
                            Text(starting ? "..." : "Chat")
                                .font(IDS.Typography.bodyBold)
                                .foregroundColor(.white)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 14)
                                .background(IDS.Colors.brand)
                                .cornerRadius(14)
                        }
                        .disabled(starting || newChatPhone.isEmpty)
                    }
                    if let startError {
                        Text(startError).font(.caption).foregroundColor(.red)
                    }
                }
                .padding(20)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)

                if let conversations {
                    let archivedCount = conversations.filter { $0.quiet == true }.count
                    if archivedCount > 0 {
                        Button(showArchived ? "Show active chats" : "Archived (\(archivedCount))") { showArchived.toggle() }
                            .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    }
                }

                if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry", action: onRetry)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(IDS.Colors.card)
                    .cornerRadius(IDS.Layout.cardCornerRadius)
                } else if conversations == nil {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                } else if conversations!.filter({ showArchived ? $0.quiet == true : $0.quiet != true }).isEmpty {
                    Text("No conversations yet.").foregroundColor(IDS.Colors.textSecondary)
                } else {
                    ForEach(conversations!.filter { showArchived ? $0.quiet == true : $0.quiet != true }) { conversation in
                        Button(action: { onOpen(conversation) }) {
                            ConversationRow(conversation: conversation, online: presence[conversation.otherUserId] == true)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 12)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .task { await loadContacts() }
    }

    private func loadContacts() async {
        do {
            let response = try await NetworkClient.shared.getTalkContacts()
            contacts = response.success ? response.contacts : []
        } catch {
            // Phone-number entry remains available when the contact directory cannot load.
            contacts = []
        }
    }

    private func startConversation() async {
        starting = true
        startError = nil
        defer { starting = false }
        do {
            let res = try await NetworkClient.shared.startConversation(phoneNumber: newChatPhone.trimmingCharacters(in: .whitespaces))
            newChatPhone = ""
            onStarted(res.conversation.id)
        } catch let NetworkError.httpError(statusCode) {
            startError = TalkScreen.errorMessage(statusCode)
        } catch {
            startError = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func startConversation(contact: TalkContactDto) async {
        starting = true
        startError = nil
        defer { starting = false }
        do {
            let res = try await NetworkClient.shared.startConversation(otherUserId: contact.userId)
            onStarted(res.conversation.id)
        } catch let NetworkError.httpError(statusCode) {
            startError = TalkScreen.errorMessage(statusCode)
        } catch {
            startError = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct GroupsList: View {
    let groups: [GroupSummaryDto]?
    let error: String?
    let onRetry: () -> Void
    let onCreated: (String) -> Void
    let onOpen: (GroupSummaryDto) -> Void

    @State private var name = ""
    @State private var phoneNumbers = ""
    @State private var createError: String?
    @State private var creating = false

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                VStack(alignment: .leading, spacing: 12) {
                    Text("New group").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Text("A group name and everyone's real phone number, comma-separated.")
                        .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                        .foregroundColor(IDS.Colors.textSecondary)
                    TextField("Group name", text: $name)
                        .padding(12)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(12)
                    TextField("+250788123456, +250788987654", text: $phoneNumbers)
                        .padding(12)
                        .background(IDS.Colors.chipBackground)
                        .cornerRadius(12)
                    Button(action: { Task { await createGroup() } }) {
                        Text(creating ? "Creating…" : "Create group")
                            .font(IDS.Typography.bodyBold)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background((creating || name.isEmpty || phoneNumbers.isEmpty) ? IDS.Colors.textTertiary : IDS.Colors.brand)
                            .cornerRadius(14)
                    }
                    .disabled(creating || name.isEmpty || phoneNumbers.isEmpty)
                    if let createError {
                        Text(createError).font(.caption).foregroundColor(.red)
                    }
                }
                .padding(20)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)

                if let error {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(error).foregroundColor(.red).font(.subheadline)
                        Button("Retry", action: onRetry)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(20)
                    .background(IDS.Colors.card)
                    .cornerRadius(IDS.Layout.cardCornerRadius)
                } else if groups == nil {
                    ProgressView().frame(maxWidth: .infinity, minHeight: 120)
                } else if groups!.isEmpty {
                    Text("No groups yet.").foregroundColor(IDS.Colors.textSecondary)
                } else {
                    ForEach(groups!) { group in
                        Button(action: { onOpen(group) }) {
                            GroupRow(group: group)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 12)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
    }

    private func createGroup() async {
        creating = true
        createError = nil
        defer { creating = false }
        let numbers = phoneNumbers.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        do {
            let res = try await NetworkClient.shared.createGroup(name: name.trimmingCharacters(in: .whitespaces), memberPhoneNumbers: numbers)
            name = ""
            phoneNumbers = ""
            onCreated(res.group.groupId)
        } catch let NetworkError.httpError(statusCode) {
            createError = TalkScreen.errorMessage(statusCode)
        } catch {
            createError = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct GroupRow: View {
    let group: GroupSummaryDto

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle().fill(IDS.Colors.chipBackground)
                Image(systemName: "person.3.fill").foregroundColor(IDS.Colors.brand)
            }
            .frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    Text(group.name).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Text("\(group.memberCount) members").font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                }
                Text(group.lastMessagePreview ?? "No messages yet")
                    .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                    .foregroundColor(IDS.Colors.textSecondary)
                    .lineLimit(1)
            }
            Spacer()
            if group.unreadCount > 0 {
                Text("\(group.unreadCount)")
                    .font(.caption).bold()
                    .foregroundColor(.white)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(IDS.Colors.brand)
                    .clipShape(Capsule())
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }
}

private struct GroupThreadScreen: View {
    let group: GroupSummaryDto
    let onBack: () -> Void

    @State private var messages: [GroupMessageDto]?
    @State private var members: [GroupMemberDto] = []
    @State private var draft = ""
    @State private var replyingTo: GroupMessageDto?
    @State private var sending = false
    @State private var error: String?
    @State private var socketTask: URLSessionWebSocketTask?
    @State private var typingUserIds: [String: Task<Void, Never>] = [:]
    @State private var lastTypingSentAt: Date = .distantPast
    // Real split-bill/manage-members (found 2026-07-22 fully built on the backend
    // with zero UI anywhere) -- opens as a sibling sheet over this same thread.
    @State private var showSplitBills = false
    @State private var showManageMembers = false
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    private func name(for senderId: String) -> String {
        members.first(where: { $0.userId == senderId })?.name ?? String(senderId.prefix(8))
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

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 8) {
                        if let messages {
                            if messages.isEmpty {
                                Text("Say hello — no messages yet.").foregroundColor(IDS.Colors.textSecondary).padding(.top, 20)
                            }
                            ForEach(messages) { message in
                                GroupMessageBubble(
                                    message: message, isMine: message.senderId == currentUserId, senderName: name(for: message.senderId),
                                    currentUserId: currentUserId,
                                    onToggleReaction: { emoji in Task { await toggleReaction(message.id, emoji) } },
                                    onReply: { replyingTo = $0 },
                                    onDelete: { messageId in Task { await deleteGroupMessage(messageId) } },
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
            HStack {
                TextField("Message", text: Binding(
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
                    .padding(12)
                    .background(IDS.Colors.chipBackground)
                    .cornerRadius(14)
                Button(action: { Task { await send() } }) {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(draft.isEmpty || sending ? IDS.Colors.textTertiary : IDS.Colors.brand)
                        .clipShape(Circle())
                }
                .disabled(draft.isEmpty || sending)
            }
            .padding(IDS.Layout.screenHorizontal)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
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
                        messages = messages?.map { $0.id == messageId ? GroupMessageDto(id: $0.id, groupConversationId: $0.groupConversationId, senderId: $0.senderId, body: $0.body, sentAt: $0.sentAt, reactions: reactions) : $0 }
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

    private func toggleReaction(_ groupMessageId: String, _ emoji: String) async {
        do {
            let res = try await NetworkClient.shared.toggleGroupReaction(groupMessageId: groupMessageId, emoji: emoji)
            messages = messages?.map { $0.id == groupMessageId ? GroupMessageDto(id: $0.id, groupConversationId: $0.groupConversationId, senderId: $0.senderId, body: $0.body, sentAt: $0.sentAt, reactions: res.reactions) : $0 }
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

// Real quick-react palette (2026-07-19) -- a small fixed set matching bank-mfe's own
// MessageReactions component exactly, kept simple rather than a full emoji picker.
private let quickReactions = ["👍", "❤️", "😂", "😮", "😢"]

// Real emoji reactions -- shared between 1:1 and group threads. Tapping an existing
// reaction badge toggles the current user's own reaction for that emoji (the fast,
// one-tap path real chat apps use); the smile button opens the quick palette for a
// first reaction.
private struct MessageReactionsRow: View {
    let reactions: [ReactionGroupDto]
    let currentUserId: String?
    let isMine: Bool
    let onToggle: (String) -> Void

    @State private var pickerOpen = false

    var body: some View {
        HStack(spacing: 4) {
            if isMine { Spacer() }
            ForEach(reactions.filter { !$0.userIds.isEmpty }, id: \.emoji) { r in
                let mine = currentUserId.map { r.userIds.contains($0) } ?? false
                Button(action: { onToggle(r.emoji) }) {
                    Text("\(r.emoji) \(r.userIds.count)")
                        .font(.caption2)
                        .foregroundColor(IDS.Colors.textSecondary)
                        .padding(.horizontal, 8).padding(.vertical, 2)
                        .background(mine ? IDS.Colors.brand.opacity(0.15) : IDS.Colors.chipBackground)
                        .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            Menu {
                ForEach(quickReactions, id: \.self) { emoji in
                    Button(emoji) { onToggle(emoji) }
                }
            } label: {
                Image(systemName: "face.smiling").font(.caption).foregroundColor(IDS.Colors.textSecondary)
            }
            if !isMine { Spacer() }
        }
    }
}

private struct GroupMessageBubble: View {
    let message: GroupMessageDto
    let isMine: Bool
    let senderName: String
    let currentUserId: String?
    let onToggleReaction: (String) -> Void
    let onReply: (GroupMessageDto) -> Void
    let onDelete: (String) -> Void

    var body: some View {
        VStack(alignment: isMine ? .trailing : .leading, spacing: 2) {
            HStack {
                if isMine { Spacer() }
                VStack(alignment: .leading, spacing: 2) {
                    if !isMine {
                        Text(senderName).font(.caption2).foregroundColor(IDS.Colors.textSecondary)
                    }
                    Text(message.body)
                        .font(.subheadline)
                        .foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .cornerRadius(16)
                if !isMine { Spacer() }
            }
            MessageReactionsRow(reactions: message.reactions, currentUserId: currentUserId, isMine: isMine, onToggle: onToggleReaction)
            Button("Reply") { onReply(message) }
                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            if isMine && message.deletedAt == nil { Button("Delete") { onDelete(message.id) }.font(.caption2).foregroundColor(IDS.Colors.textSecondary) }
            Text(chatMessageTime(message.sentAt))
                .font(.caption2)
                .foregroundColor(IDS.Colors.textSecondary)
        }
    }
}

private struct ConversationRow: View {
    let conversation: ConversationSummaryDto
    let online: Bool

    var body: some View {
        HStack(spacing: 14) {
            ZStack(alignment: .bottomTrailing) {
                ZStack {
                    Circle().fill(IDS.Colors.chipBackground)
                    Image(systemName: "paperplane.fill").foregroundColor(IDS.Colors.brand)
                }
                .frame(width: 44, height: 44)
                if online {
                    Circle().fill(Color.green)
                        .frame(width: 12, height: 12)
                        .overlay(Circle().stroke(IDS.Colors.card, lineWidth: 2))
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(conversation.otherUserName).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                Text(conversation.lastMessagePreview ?? "No messages yet")
                    .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                    .foregroundColor(IDS.Colors.textSecondary)
                    .lineLimit(1)
            }
            Spacer()
            if conversation.unreadCount > 0 {
                Text("\(conversation.unreadCount)")
                    .font(.caption).bold()
                    .foregroundColor(.white)
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(IDS.Colors.brand)
                    .clipShape(Capsule())
            }
        }
        .padding(18)
        .background(IDS.Colors.card)
        .cornerRadius(IDS.Layout.cardCornerRadius)
    }
}

// Real KakaoPay-style split bill (2026-07-22) -- found fully built on the backend
// (rw.itunda.splitbill) with zero client UI anywhere, despite group chat itself being
// fully wired. A flat, even split among picked group members (excluding the
// organizer); each participant pays their own share directly to the organizer via a
// real wallet-to-wallet push, no escrow -- see SplitBill.kt's own doc comment.
private struct GroupSplitBillsView: View {
    let groupConversationId: String
    let members: [GroupMemberDto]
    let currentUserId: String?
    @Environment(\.dismiss) private var dismiss

    @State private var splitBills: [SplitBillWithParticipants]?
    @State private var error: String?
    @State private var busyId: String?
    @State private var showNewForm = false
    @State private var amountText = ""
    @State private var descriptionText = ""
    @State private var selectedIds: Set<String> = []

    private var otherMembers: [GroupMemberDto] { members.filter { $0.userId != currentUserId } }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    if !showNewForm {
                        Button(action: { showNewForm = true }) {
                            Text("Split a bill").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12).background(IDS.Colors.brand).cornerRadius(10)
                        }
                    } else {
                        VStack(alignment: .leading, spacing: 8) {
                            TextField("Total amount (RWF)", text: $amountText).keyboardType(.numberPad).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            TextField("What was it for?", text: $descriptionText).padding(10).background(IDS.Colors.chipBackground).cornerRadius(8)
                            Text("Split with").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                            ForEach(otherMembers) { member in
                                HStack {
                                    Text(member.name)
                                    Spacer()
                                    Image(systemName: selectedIds.contains(member.userId) ? "checkmark.square.fill" : "square")
                                }
                                .onTapGesture {
                                    if selectedIds.contains(member.userId) { selectedIds.remove(member.userId) } else { selectedIds.insert(member.userId) }
                                }
                            }
                            Button(action: { Task { await create() } }) {
                                Text(busyId == "new" ? "Creating…" : "Create").bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(12)
                                    .background(selectedIds.isEmpty ? IDS.Colors.textTertiary : IDS.Colors.brand).cornerRadius(10)
                            }
                            .disabled(busyId == "new" || selectedIds.isEmpty || amountText.isEmpty || descriptionText.isEmpty)
                        }
                        .padding(12).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                    }
                    if let splitBills {
                        if splitBills.isEmpty {
                            Text("No split bills in this group yet.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                        }
                        ForEach(splitBills) { entry in
                            let myShare = entry.participants.first { $0.userId == currentUserId }
                            VStack(alignment: .leading, spacing: 4) {
                                Text(entry.splitBill.description).bold()
                                Text("Total \(Int(entry.splitBill.totalAmount)) RWF · \(entry.splitBill.status)").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                ForEach(entry.participants) { p in
                                    let name = members.first(where: { $0.userId == p.userId })?.name ?? String(p.userId.prefix(8))
                                    Text("\(name): \(Int(p.shareAmount)) RWF (\(p.status))").font(.caption)
                                }
                                if let myShare, myShare.status == "PENDING" {
                                    Button(action: { Task { await pay(entry.splitBill.id) } }) {
                                        Text(busyId == entry.splitBill.id ? "Paying…" : "Pay my share (\(Int(myShare.shareAmount)) RWF)")
                                            .bold().foregroundColor(.white).frame(maxWidth: .infinity).padding(10).background(IDS.Colors.brand).cornerRadius(8)
                                    }
                                    .disabled(busyId != nil)
                                }
                            }
                            .padding(12).background(IDS.Colors.card).cornerRadius(IDS.Layout.cardCornerRadius)
                        }
                    } else {
                        ProgressView()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
            .navigationTitle("Split bills")
            .toolbar { ToolbarItem(placement: .navigationBarTrailing) { Button("Close") { dismiss() } } }
            .task { await refresh() }
        }
    }

    private func refresh() async {
        do { splitBills = try await NetworkClient.shared.getSplitBillsForGroup(groupConversationId: groupConversationId).splitBills }
        catch { self.error = "Could not load split bills." }
    }

    private func create() async {
        guard let amount = Double(amountText) else { return }
        busyId = "new"; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.createSplitBill(groupConversationId: groupConversationId, totalAmount: amount, description: descriptionText, participantUserIds: Array(selectedIds))
            amountText = ""; descriptionText = ""; selectedIds = []; showNewForm = false
            await refresh()
        } catch { self.error = "That split bill could not be created." }
    }

    private func pay(_ splitBillId: String) async {
        busyId = splitBillId; error = nil
        defer { busyId = nil }
        do {
            _ = try await NetworkClient.shared.paySplitBillShare(splitBillId: splitBillId)
            await refresh()
        } catch { self.error = "That payment could not be completed." }
    }
}

// Real leave-group/add-member (2026-07-22) -- found fully built on the backend
// (GroupMessagingController's POST/DELETE .../members) with zero client UI anywhere.
// Add-member picks from the caller's real Talk contacts, filtered to exclude people
// already in the group.
private struct GroupManageMembersView: View {
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

    private var addable: [TalkContactDto] { contacts.filter { contact in !members.contains { $0.userId == contact.userId } } }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let error { Text(error).font(.caption).foregroundColor(.red) }
                    Text("Members (\(members.count))").bold()
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
        } catch {
            self.error = "Could not add \(contact.name)."
        }
    }
}

private struct ChatThreadScreen: View {
    let conversation: ConversationSummaryDto
    let onBack: () -> Void

    @State private var messages: [MessageDto]?
    @State private var offersByMessageId: [String: OfferBubbleData] = [:]
    @State private var giftsByMessageId: [String: GiftDto] = [:]
    @State private var draft = ""
    @State private var replyingTo: MessageDto?
    @State private var pinnedMessage: MessageDto?
    @State private var updatingPin = false
    @State private var sending = false
    @State private var error: String?
    @State private var socketTask: URLSessionWebSocketTask?
    @State private var otherOnline: Bool?
    @State private var otherTyping = false
    @State private var typingClearTask: Task<Void, Never>?
    @State private var lastTypingSentAt: Date = .distantPast
    @State private var giftComposerOpen = false
    @State private var giftAmount = ""
    @State private var giftNote = ""
    @State private var sendingGift = false
    // Real KakaoTalk Emoticon Store (item 136) -- see EmoticonPickerPanel's own doc
    // comment.
    @State private var emoticonPickerOpen = false
    @State private var emoticonStoreOpen = false
    @State private var emoticonImageById: [String: String] = [:]
    // Real KakaoTalk-style 기프티콘 gift voucher (item 138) -- see
    // GiftVoucherComposerPanel's own doc comment.
    @State private var vouchersByMessageId: [String: GiftVoucherDto] = [:]
    @State private var voucherComposerOpen = false
    @State private var showingBlockConfirmation = false
    @State private var blocking = false
    @State private var isBlocked = false
    @State private var quiet = false
    @State private var updatingQuiet = false
    @State private var searchQuery = ""
    @State private var searchResults: [MessageDto]?
    @State private var searching = false
    // Real device binding step-up (2026-07-21) -- Gift send/claim was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but a
    // bare `catch { }` swallowed it into a generic error, same fix already applied to
    // Transfer/Savings via TransferFlowContainer/SavingsFlowContainer.
    @State private var needsDeviceVerification = false
    @State private var pendingGiftRetry: (() async -> Void)?
    private let currentUserId = KeychainTokenStore.shared.getUserId()

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .medium))
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back")
                VStack(alignment: .leading, spacing: 0) {
                    Text(conversation.otherUserName).font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                    if let otherOnline {
                        Text(otherOnline ? "Online" : "Offline")
                            .font(.caption)
                            .foregroundColor(otherOnline ? .green : IDS.Colors.textSecondary)
                    }
                }
                Spacer()
                Button(blocking ? "…" : (isBlocked ? "Unblock" : "Block")) {
                    if isBlocked {
                        Task { await unblockParticipant() }
                    } else {
                        showingBlockConfirmation = true
                    }
                }
                    .disabled(blocking)
                    .foregroundColor(.red)
                Button(updatingQuiet ? "…" : (quiet ? "Resume alerts" : "Quiet room")) { Task { await setQuietRoom() } }
                    .disabled(updatingQuiet)
                    .font(.caption)
                    .foregroundColor(IDS.Colors.textSecondary)
            }
            .padding(.horizontal, 8)
            .alert("Block \(conversation.otherUserName)?", isPresented: $showingBlockConfirmation) {
                Button("Block", role: .destructive) { Task { await blockParticipant() } }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("They will no longer be able to message you. You can unblock them later from this conversation.")
            }

            HStack(spacing: 8) {
                TextField("Search this conversation", text: $searchQuery)
                    .padding(9).background(IDS.Colors.chipBackground).cornerRadius(8)
                Button(searching ? "…" : "Search") { Task { await search() } }
                    .disabled(searching || searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).count < 2)
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            if let searchResults {
                Text("\(searchResults.count) matching message\(searchResults.count == 1 ? "" : "s")")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

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
                        if let messages = searchResults ?? messages {
                            if messages.isEmpty {
                                Text("Say hello — no messages yet.").foregroundColor(IDS.Colors.textSecondary).padding(.top, 20)
                            }
                            ForEach(messages) { message in
                                let reactionHandler: (String) -> Void = { emoji in Task { await toggleReaction(message.id, emoji) } }
                                let offerHandler: (String, String, Double?) -> Void = { offerId, action, counterAmount in Task { await respondToOffer(offerId, action, counterAmount) } }
                                let giftHandler: (String) -> Void = { giftId in Task { await claimGift(giftId) } }
                                let reportHandler: (String, String) -> Void = { messageId, reason in Task { await reportMessage(messageId, reason) } }
                                let extendVoucherHandler: (String) -> Void = { voucherId in Task { await extendVoucher(voucherId) } }
                                MessageBubble(
                                    message: message, isMine: message.senderId == currentUserId, currentUserId: currentUserId,
                                    offer: offersByMessageId[message.id],
                                    gift: giftsByMessageId[message.id],
                                    voucher: vouchersByMessageId[message.id],
                                    emoticonImageUrl: message.emoticonId.flatMap { emoticonImageById[$0] },
                                    onToggleReaction: reactionHandler,
                                    onRespondToOffer: offerHandler,
                                    onClaimGift: giftHandler,
                                    onExtendVoucher: extendVoucherHandler,
                                    onReply: { replyingTo = $0 },
                                    onDelete: { messageId in Task { await deleteMessage(messageId) } },
                                    onPin: { pinned in Task { await pinMessage(pinned) } },
                                    onReportMessage: reportHandler,
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

            if otherTyping {
                Text("\(conversation.otherUserName) is typing…")
                    .font(.caption)
                    .foregroundColor(IDS.Colors.textSecondary)
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            DeviceStepUpHost(
                visible: needsDeviceVerification,
                onDismiss: { needsDeviceVerification = false; pendingGiftRetry = nil },
                onVerified: {
                    needsDeviceVerification = false
                    let retry = pendingGiftRetry
                    pendingGiftRetry = nil
                    await retry?()
                }
            )

            if let error {
                Text(error).font(.caption).foregroundColor(.red).padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            if giftComposerOpen {
                VStack(alignment: .leading, spacing: 8) {
                    Text("🎁 Send a gift").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                    TextField("Amount (RWF)", text: $giftAmount)
                        .keyboardType(.numberPad)
                        .padding(10)
                        .background(IDS.Colors.card)
                        .cornerRadius(8)
                    TextField("Add a note (optional)", text: $giftNote)
                        .padding(10)
                        .background(IDS.Colors.card)
                        .cornerRadius(8)
                    HStack(spacing: 8) {
                        Button(action: { Task { await sendGift() } }) {
                            Text(sendingGift ? "Sending…" : "Send gift").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(IDS.Colors.card)
                                .cornerRadius(10)
                        }
                        .buttonStyle(.plain)
                        .disabled(sendingGift || Double(giftAmount) == nil || (Double(giftAmount) ?? 0) <= 0)
                        Button(action: { giftComposerOpen = false }) {
                            Text("Cancel").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 10).padding(.vertical, 6)
                                .background(IDS.Colors.card)
                                .cornerRadius(10)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(12)
                .background(IDS.Colors.chipBackground)
                .cornerRadius(12)
                .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            if emoticonPickerOpen {
                EmoticonPickerPanel(
                    onSend: { emoticonId in
                        Task {
                            _ = try? await NetworkClient.shared.sendEmoticon(conversationId: conversation.conversationId, emoticonId: emoticonId)
                            emoticonPickerOpen = false
                            await refresh()
                        }
                    },
                    onOpenStore: { emoticonStoreOpen = true }
                )
                .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            if voucherComposerOpen {
                GiftVoucherComposerPanel(
                    onSent: { voucherComposerOpen = false; Task { await refresh() } },
                    onCancel: { voucherComposerOpen = false }
                )
                .padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            HStack {
                Button(action: { giftComposerOpen.toggle() }) {
                    Text("🎁")
                        .frame(width: 44, height: 44)
                        .background(IDS.Colors.chipBackground)
                        .clipShape(Circle())
                }
                .accessibilityLabel("Send a gift")
                Button(action: { emoticonPickerOpen.toggle() }) {
                    Text("😊")
                        .frame(width: 44, height: 44)
                        .background(IDS.Colors.chipBackground)
                        .clipShape(Circle())
                }
                .accessibilityLabel("Send an emoticon")
                Button(action: { voucherComposerOpen.toggle() }) {
                    Text("🎟️")
                        .frame(width: 44, height: 44)
                        .background(IDS.Colors.chipBackground)
                        .clipShape(Circle())
                }
                .accessibilityLabel("Send a gift voucher")
                TextField("Message", text: Binding(
                    get: { draft },
                    set: { newValue in
                        draft = newValue
                        // Real typing indicator send (2026-07-19), client-throttled to
                        // match the server's own 1-per-2s rate limit.
                        if Date().timeIntervalSince(lastTypingSentAt) > 2 {
                            lastTypingSentAt = Date()
                            if let socketTask {
                                NetworkClient.shared.sendTyping(socketTask, conversationId: conversation.conversationId)
                            }
                        }
                    }
                ))
                    .padding(12)
                    .background(IDS.Colors.chipBackground)
                    .cornerRadius(14)
                Button(action: { Task { await send() } }) {
                    Image(systemName: "paperplane.fill")
                        .foregroundColor(.white)
                        .frame(width: 44, height: 44)
                        .background(draft.isEmpty || sending ? IDS.Colors.textTertiary : IDS.Colors.brand)
                        .clipShape(Circle())
                }
                .disabled(draft.isEmpty || sending)
            }
            .padding(IDS.Layout.screenHorizontal)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await refresh() }
        .task {
            quiet = (try? await NetworkClient.shared.getConversationQuiet(conversationId: conversation.conversationId).quiet) ?? false
            pinnedMessage = try? await NetworkClient.shared.getPinnedConversationMessage(conversationId: conversation.conversationId).message
        }
        // Real KakaoTalk Emoticon Store (item 136) -- no GET-emoticon-by-id endpoint
        // exists, so rendering a received emoticon needs a client-built id->imageUrl
        // map across the small, curated, server-seeded catalog. Mirrors bank-mfe/
        // Android's own image-map loaders (items 133/135).
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
        // real WebSocket push below -- matches bank-mfe/Android exactly (poll interval
        // unchanged, push appended live on top).
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                await refresh()
            }
        }
        // Real online/offline presence (2026-07-19) -- initial fetch, then kept live via
        // the same WebSocket connection's presence push below.
        .task {
            do {
                otherOnline = try await NetworkClient.shared.getPresence(userIds: [conversation.otherUserId]).presence[conversation.otherUserId]
            } catch {
                // Real, non-critical -- only backs the header subtitle.
            }
        }
        // Real WebSocket live-transport (2026-07-18) -- see
        // NetworkClient.connectMessagingSocket's own doc comment.
        .onAppear {
            socketTask = NetworkClient.shared.connectMessagingSocket { push in
                switch push {
                case .directMessage(let conversationId, let pushedMessage) where conversationId == conversation.conversationId:
                    Task { @MainActor in
                        otherTyping = false
                        if !(messages ?? []).contains(where: { $0.id == pushedMessage.id }) {
                            messages = (messages ?? []) + [pushedMessage]
                        }
                        // A pushed message might be a real offer/counter/accept/reject --
                        // refresh so it renders as an offer bubble immediately.
                        await loadOffers()
                        await loadGifts()
                        await loadVouchers()
                    }
                case .presenceChange(let userId, let online) where userId == conversation.otherUserId:
                    Task { @MainActor in otherOnline = online }
                case .typingChange(let conversationId, _, let userId) where conversationId == conversation.conversationId && userId == conversation.otherUserId:
                    Task { @MainActor in
                        otherTyping = true
                        typingClearTask?.cancel()
                        typingClearTask = Task {
                            try? await Task.sleep(nanoseconds: 3_000_000_000)
                            if !Task.isCancelled { otherTyping = false }
                        }
                    }
                case .reactionChange(let conversationId, _, let messageId, let reactions) where conversationId == conversation.conversationId:
                    Task { @MainActor in
                        messages = messages?.map { $0.id == messageId ? MessageDto(id: $0.id, conversationId: $0.conversationId, senderId: $0.senderId, body: $0.body, sentAt: $0.sentAt, readAt: $0.readAt, reactions: reactions) : $0 }
                    }
                default:
                    break
                }
            }
        }
        .onDisappear {
            socketTask?.cancel(with: .goingAway, reason: nil)
            typingClearTask?.cancel()
        }
        .sheet(isPresented: $emoticonStoreOpen) {
            EmoticonStoreView(onClose: { emoticonStoreOpen = false })
        }
    }

    private func refresh() async {
        do {
            let res = try await NetworkClient.shared.getMessages(conversationId: conversation.conversationId)
            messages = res.messages.reversed()
        } catch {
            // Keep showing the last-known messages rather than blanking the thread on
            // a transient poll failure.
        }
        await loadOffers()
        await loadGifts()
        await loadVouchers()
    }

    // Real per-thread gift history -- fetched alongside a conversation's messages so
    // the thread can render gift bubbles for whichever messages carry one.
    private func loadGifts() async {
        let gifts = (try? await NetworkClient.shared.getGiftsForConversation(conversationId: conversation.conversationId).gifts) ?? []
        giftsByMessageId = Dictionary(uniqueKeysWithValues: gifts.map { ($0.messageId, $0) })
    }

    // Real per-thread gift-voucher history -- see GiftVoucherComposerPanel's own doc
    // comment.
    private func loadVouchers() async {
        let vouchers = (try? await NetworkClient.shared.getGiftVouchersForConversation(conversationId: conversation.conversationId).vouchers) ?? []
        vouchersByMessageId = Dictionary(uniqueKeysWithValues: vouchers.map { ($0.messageId, $0) })
    }

    private func sendGift() async {
        guard let amount = Double(giftAmount), amount > 0 else { return }
        sendingGift = true
        error = nil
        needsDeviceVerification = false
        defer { sendingGift = false }
        do {
            _ = try await NetworkClient.shared.sendGiftInConversation(
                conversationId: conversation.conversationId,
                amount: amount,
                note: giftNote.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : giftNote
            )
            giftAmount = ""
            giftNote = ""
            giftComposerOpen = false
            await refresh()
        } catch NetworkError.deviceNotVerified {
            pendingGiftRetry = { await sendGift() }
            needsDeviceVerification = true
        } catch {
            self.error = "Couldn't send this gift. Check your connection and try again."
        }
    }

    private func claimGift(_ giftId: String) async {
        do {
            _ = try await NetworkClient.shared.claimGift(giftId: giftId)
            await loadGifts()
        } catch NetworkError.deviceNotVerified {
            pendingGiftRetry = { await claimGift(giftId) }
            needsDeviceVerification = true
        } catch {
            self.error = "Couldn't open this gift. Check your connection and try again."
        }
    }

    // Real one-time gift-voucher expiry extension (item 195) -- found via a
    // defined-but-uncalled-method sweep: extendGiftVoucherExpiry existed on all 3
    // platforms' network layers, wired on bank-mfe since 2026-07-27 and Android the
    // same session (item 194), never called here.
    private func extendVoucher(_ voucherId: String) async {
        do {
            _ = try await NetworkClient.shared.extendGiftVoucherExpiry(voucherId: voucherId)
            await loadVouchers()
        } catch {
            self.error = "Couldn't extend this voucher. Try again."
        }
    }

    // Real-fetches both Marketplace and Real Estate offer history for this conversation
    // -- a given real conversation only ever carries one type in practice, but fetching
    // both is cheap and correct rather than guessing which one applies (mirrors
    // bank-mfe's own ConversationThread.loadOffers).
    private func loadOffers() async {
        let marketplaceOffers = (try? await NetworkClient.shared.getOffersForConversation(conversationId: conversation.conversationId).offers) ?? []
        let propertyOffers = (try? await NetworkClient.shared.getPropertyOffersForConversation(conversationId: conversation.conversationId).offers) ?? []
        var merged: [String: OfferBubbleData] = [:]
        for o in marketplaceOffers { merged[o.messageId] = o.toBubbleData() }
        for o in propertyOffers { merged[o.messageId] = o.toBubbleData() }
        offersByMessageId = merged
    }

    private func respondToOffer(_ offerId: String, _ action: String, _ counterAmount: Double?) async {
        do {
            // Real offer ids are stably prefixed by their real owning service
            // ("price_offer_"/"property_offer_") -- a reliable dispatch key, matching
            // bank-mfe's own ConversationThread.
            if offerId.hasPrefix("property_offer_") {
                _ = try await NetworkClient.shared.respondToPropertyOffer(offerId: offerId, action: action, counterAmount: counterAmount)
            } else {
                _ = try await NetworkClient.shared.respondToOffer(offerId: offerId, action: action, counterAmount: counterAmount)
            }
            await refresh()
        } catch {
            self.error = "Couldn't respond to this offer. Check your connection and try again."
        }
    }

    private func toggleReaction(_ messageId: String, _ emoji: String) async {
        do {
            let res = try await NetworkClient.shared.toggleReaction(messageId: messageId, emoji: emoji)
            messages = messages?.map { $0.id == messageId ? MessageDto(id: $0.id, conversationId: $0.conversationId, senderId: $0.senderId, body: $0.body, sentAt: $0.sentAt, readAt: $0.readAt, reactions: res.reactions) : $0 }
        } catch {
            // Best-effort -- a failed reaction toggle just leaves the badge as it was.
        }
    }

    private func pinMessage(_ message: MessageDto) async {
        updatingPin = true
        defer { updatingPin = false }
        do {
            _ = try await NetworkClient.shared.pinConversationMessage(conversationId: conversation.conversationId, messageId: message.id)
            pinnedMessage = message
        } catch { self.error = "Couldn't pin this message. Check your connection and try again." }
    }

    private func deleteMessage(_ messageId: String) async {
        do { _ = try await NetworkClient.shared.deleteMessage(conversationId: conversation.conversationId, messageId: messageId); await refresh() }
        catch { self.error = "Couldn't delete this message. Check your connection and try again." }
    }

    private func unpinMessage() async {
        updatingPin = true
        defer { updatingPin = false }
        do {
            _ = try await NetworkClient.shared.unpinConversationMessage(conversationId: conversation.conversationId)
            pinnedMessage = nil
        } catch { self.error = "Couldn't unpin this message. Check your connection and try again." }
    }

    private func blockParticipant() async {
        blocking = true
        defer { blocking = false }
        do {
            _ = try await NetworkClient.shared.blockConversationParticipant(conversationId: conversation.conversationId)
            isBlocked = true
            error = "\(conversation.otherUserName) is blocked."
        } catch {
            self.error = "Couldn't block this person. Check your connection and try again."
        }
    }

    // Real unblock (item 197) -- found via a defined-but-uncalled-method sweep on
    // NetworkClient.swift: unblockConversationParticipant existed with zero call sites,
    // matching bank-mfe's own gap before item 193 closed it there. blockParticipant's
    // own confirmation copy already promises "You can unblock them later from this
    // conversation" (see the Block confirmation alert below).
    private func unblockParticipant() async {
        blocking = true
        defer { blocking = false }
        do {
            _ = try await NetworkClient.shared.unblockConversationParticipant(conversationId: conversation.conversationId)
            isBlocked = false
            error = "You unblocked \(conversation.otherUserName)."
        } catch {
            self.error = "Couldn't unblock this person. Check your connection and try again."
        }
    }

    private func setQuietRoom() async {
        updatingQuiet = true
        defer { updatingQuiet = false }
        do {
            quiet = try await NetworkClient.shared.setConversationQuiet(conversationId: conversation.conversationId, quiet: !quiet).quiet
        } catch {
            self.error = "Couldn't update this quiet room. Check your connection and try again."
        }
    }

    private func reportMessage(_ messageId: String, _ reason: String) async {
        do {
            _ = try await NetworkClient.shared.reportChatMessage(messageId: messageId, reason: reason)
            error = "Thanks. Your report was sent for review."
        } catch {
            self.error = "Couldn't send this report. Check your connection and try again."
        }
    }

    private func search() async {
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard query.count >= 2 else { return }
        searching = true
        defer { searching = false }
        do { searchResults = try await NetworkClient.shared.searchMessages(conversationId: conversation.conversationId, query: query).messages }
        catch { self.error = "Couldn't search this conversation. Check your connection and try again." }
    }

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        sending = true
        error = nil
        defer { sending = false }
        do {
            let res = try await NetworkClient.shared.sendMessage(conversationId: conversation.conversationId, body: body, replyToMessageId: replyingTo?.id)
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

// Real minimal shape both PriceOfferDto (Marketplace) and PropertyPriceOfferDto (Real
// Estate) get mapped into for display -- narrowed to just the fields OfferBubble
// actually reads (id/amount/status/proposedByUserId), so this one view renders both
// offer types without duplication. Mirrors bank-mfe's own OfferBubbleData narrowing
// (2026-07-19).
struct OfferBubbleData {
    let id: String
    let amount: Double
    let status: String
    let proposedByUserId: String
}

extension PriceOfferDto {
    func toBubbleData() -> OfferBubbleData { OfferBubbleData(id: id, amount: amount, status: status, proposedByUserId: proposedByUserId) }
}
extension PropertyPriceOfferDto {
    func toBubbleData() -> OfferBubbleData { OfferBubbleData(id: id, amount: amount, status: status, proposedByUserId: proposedByUserId) }
}

// Real 당근-style offer bubble (2026-07-19) -- see PriceOfferService's own doc comment.
// Renders inline wherever a message carries a real offer, replacing the plain-text
// bubble with amount + status + real Accept/Decline/Counter actions (only shown to
// whichever participant did NOT propose the current pending amount).
private struct OfferBubble: View {
    let offer: OfferBubbleData
    let isMine: Bool
    let currentUserId: String?
    let onRespond: (String, String, Double?) -> Void

    @State private var countering = false
    @State private var counterAmount = ""

    private var canRespond: Bool { offer.status == "PENDING" && currentUserId != nil && currentUserId != offer.proposedByUserId }
    private var statusLabel: String {
        switch offer.status {
        case "PENDING": return "Pending"
        case "ACCEPTED": return "Accepted"
        case "REJECTED": return "Declined"
        case "COUNTERED": return "Countered"
        default: return offer.status
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("💰 \(Int(offer.amount)) RWF").font(.subheadline).bold().foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
            Text(statusLabel).font(.caption).foregroundColor(isMine ? .white.opacity(0.85) : IDS.Colors.textSecondary)
            if canRespond && !countering {
                HStack(spacing: 6) {
                    offerActionButton("Accept") { onRespond(offer.id, "ACCEPT", nil) }
                    offerActionButton("Decline") { onRespond(offer.id, "REJECT", nil) }
                    offerActionButton("Counter") { countering = true }
                }
            }
            if canRespond && countering {
                HStack(spacing: 6) {
                    TextField("Counter (RWF)", text: $counterAmount)
                        .keyboardType(.numberPad)
                        .font(.caption)
                        .padding(6)
                        .background(IDS.Colors.card)
                        .cornerRadius(8)
                        .frame(width: 100)
                    offerActionButton("Send") {
                        guard let amount = Double(counterAmount) else { return }
                        countering = false
                        counterAmount = ""
                        onRespond(offer.id, "COUNTER", amount)
                    }
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
        .cornerRadius(16)
    }

    private func offerActionButton(_ label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label).font(.caption2).bold().foregroundColor(IDS.Colors.textPrimary)
                .padding(.horizontal, 10).padding(.vertical, 6)
                .background(IDS.Colors.card)
                .cornerRadius(10)
        }
        .buttonStyle(.plain)
    }
}

// Real KakaoTalk-style gift bubble (2026-07-20) -- see GiftService's own doc comment.
// Renders inline wherever a message carries a real gift, with a real Open/Claim button
// shown only to the recipient of a still-PENDING, not-yet-expired gift.
private struct GiftBubble: View {
    let gift: GiftDto
    let isMine: Bool
    let currentUserId: String?
    let onClaim: (String) -> Void

    private static let isoFormatter = ISO8601DateFormatter(withFractionalSeconds: true)

    private var canClaim: Bool {
        guard gift.status == "PENDING", currentUserId == gift.recipientId else { return false }
        guard let expiresAt = Self.isoFormatter.date(from: gift.expiresAt) else { return true }
        return expiresAt > Date()
    }
    private var statusLabel: String {
        switch gift.status {
        case "PENDING": return isMine ? "Waiting to be opened" : "Tap to open"
        case "CLAIMED": return "Opened"
        case "EXPIRED": return "Expired — refunded"
        default: return gift.status
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("🎁 \(Int(gift.amount)) RWF").font(.headline).foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
            if let note = gift.note {
                Text("\"\(note)\"").font(.caption).foregroundColor(isMine ? .white.opacity(0.9) : IDS.Colors.textSecondary)
            }
            Text(statusLabel).font(.caption).foregroundColor(isMine ? .white.opacity(0.85) : IDS.Colors.textSecondary)
            if canClaim {
                Button(action: { onClaim(gift.id) }) {
                    Text("Open gift").font(.caption2).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(IDS.Colors.card)
                        .cornerRadius(10)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
        .cornerRadius(16)
    }
}

// Real KakaoTalk-style 기프티콘 gift voucher bubble (item 138) -- see
// GiftVoucherComposerPanel's own doc comment. Redemption is merchant-side per
// GiftVoucherService.redeemVoucher's own doc comment, never a self-serve recipient
// redeem. Real one-time "extend expiry" action added item 195 (found via a
// defined-but-uncalled-method sweep); only offered once (!voucher.extended), and only
// within a real 30-day window of the current expiry, matching bank-mfe's own
// GIFT_VOUCHER_EXTENSION_WINDOW_MS / Android's item 194 exactly.
private struct GiftVoucherBubble: View {
    let voucher: GiftVoucherDto
    let isMine: Bool
    var onExtend: () -> Void = {}

    private var statusLabel: String {
        switch voucher.status {
        case "ACTIVE": return "Present this at the store to redeem"
        case "REDEEMED": return "Redeemed"
        case "EXPIRED": return "Expired"
        default: return voucher.status
        }
    }

    private var canExtend: Bool {
        guard voucher.status == "ACTIVE", !voucher.extended else { return false }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        guard let expiry = formatter.date(from: voucher.expiresAt) ?? ISO8601DateFormatter().date(from: voucher.expiresAt) else { return false }
        return expiry.timeIntervalSinceNow <= 30 * 24 * 60 * 60
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("🎟️ \(voucher.productNameSnapshot ?? "\(Int(voucher.amount)) RWF voucher")")
                .font(.headline).foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
            Text(statusLabel).font(.caption).foregroundColor(isMine ? .white.opacity(0.85) : IDS.Colors.textSecondary)
            if voucher.status == "ACTIVE" {
                Text("Expires \(String(voucher.expiresAt.prefix(10)))")
                    .font(.caption2).foregroundColor(isMine ? .white.opacity(0.7) : IDS.Colors.textSecondary)
            }
            if canExtend {
                Button(action: onExtend) {
                    Text("Extend expiry").font(.caption).bold().foregroundColor(isMine ? .white : IDS.Colors.brand)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
        .cornerRadius(16)
    }
}

// Real KakaoTalk Emoticon Store (item 136) -- a received emoticon renders as just the
// sticker image, no chat-bubble background, matching real KakaoTalk and bank-mfe/
// Android's own EmoticonBubble (items 133/135).
private struct EmoticonBubble: View {
    let imageUrl: String?

    var body: some View {
        if let imageUrl, let url = URL(string: imageUrl) {
            AsyncImage(url: url) { image in
                image.resizable().aspectRatio(contentMode: .fit)
            } placeholder: {
                ProgressView()
            }
            .frame(width: 96, height: 96)
        } else {
            Text("[emoticon]").font(.footnote).italic().foregroundColor(IDS.Colors.textSecondary)
        }
    }
}

// Real emoticon picker (item 136) -- shows the sender's own owned packs only (each
// tappable emoticon sends immediately); a real "Get more" link opens the full store.
// Mirrors bank-mfe/Android's own EmoticonPickerPanel (items 133/135).
// Real gift-voucher composer (item 138) -- search for a real product to gift (same
// real Kakao gifticon UX of searching for what to send, e.g. "스타벅스 아메리카노",
// rather than browsing a merchant catalog first), pick one, confirm with the
// recipient's phone number. Product-only v1 -- the flat-cash-amount-at-a-merchant
// path is a real, deliberately deferred follow-up. Mirrors bank-mfe/Android's own
// GiftVoucherComposerPanel (items 134/137).
private struct GiftVoucherComposerPanel: View {
    let onSent: () -> Void
    let onCancel: () -> Void

    @State private var phone = ""
    @State private var query = ""
    @State private var results: [ProductSearchResultDto]?
    @State private var searching = false
    @State private var selected: ProductSearchResultDto?
    @State private var sending = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("🎟️ Send a gift voucher").font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
            TextField("Recipient phone number", text: $phone)
                .keyboardType(.phonePad)
                .padding(10)
                .background(IDS.Colors.card)
                .cornerRadius(8)

            if let selected {
                HStack {
                    Text("\(selected.name) · \(selected.merchantName) · \(Int(selected.price)) RWF")
                        .font(.caption).foregroundColor(IDS.Colors.textPrimary)
                    Spacer()
                    Button("Change") { self.selected = nil }
                        .font(.caption).foregroundColor(IDS.Colors.brand)
                }
                .padding(10)
                .background(IDS.Colors.card)
                .cornerRadius(8)
            } else {
                HStack(spacing: 8) {
                    TextField("Search a product to gift", text: $query)
                        .padding(10)
                        .background(IDS.Colors.card)
                        .cornerRadius(8)
                    Button(action: { Task { await search() } }) {
                        Text(searching ? "…" : "Search").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                            .padding(.horizontal, 10).padding(.vertical, 8)
                            .background(IDS.Colors.card)
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                    .disabled(searching || query.trimmingCharacters(in: .whitespaces).count < 2)
                }
                if let results {
                    if results.isEmpty {
                        Text("No products found.").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    } else {
                        ForEach(results) { p in
                            Button(action: { selected = p }) {
                                HStack {
                                    Text("\(p.name) · \(p.merchantName)").font(.caption).foregroundColor(IDS.Colors.textPrimary)
                                    Spacer()
                                    Text("\(Int(p.price)) RWF").font(.caption).foregroundColor(IDS.Colors.textPrimary)
                                }
                                .padding(10)
                                .background(IDS.Colors.card)
                                .cornerRadius(8)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }

            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }

            HStack(spacing: 8) {
                Button(action: { Task { await send() } }) {
                    Text(sending ? "…" : "Send gift voucher").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(IDS.Colors.card)
                        .cornerRadius(10)
                }
                .buttonStyle(.plain)
                .disabled(selected == nil || phone.trimmingCharacters(in: .whitespaces).isEmpty || sending)
                Button(action: onCancel) {
                    Text("Cancel").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .background(IDS.Colors.card)
                        .cornerRadius(10)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .background(IDS.Colors.chipBackground)
        .cornerRadius(12)
    }

    private func search() async {
        guard query.trimmingCharacters(in: .whitespaces).count >= 2 else { return }
        searching = true
        error = nil
        defer { searching = false }
        do {
            results = try await NetworkClient.shared.searchProducts(query.trimmingCharacters(in: .whitespaces)).products
        } catch {
            self.error = "Could not search products."
        }
    }

    private func send() async {
        guard let selected, !phone.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        sending = true
        error = nil
        defer { sending = false }
        do {
            _ = try await NetworkClient.shared.purchaseGiftVoucher(
                PurchaseGiftVoucherRequest(recipientPhoneNumber: phone.trimmingCharacters(in: .whitespaces), merchantId: selected.merchantId, merchantProductId: selected.id)
            )
            onSent()
        } catch {
            self.error = "Could not send this gift voucher."
        }
    }
}

private struct EmoticonPickerPanel: View {
    let onSend: (String) -> Void
    let onOpenStore: () -> Void

    @State private var ownedPacks: [OwnedEmoticonPackDto]?
    @State private var packTitles: [String: String] = [:]
    @State private var selectedPackId: String?
    @State private var packEmoticons: [EmoticonDto]?

    private let columns = Array(repeating: GridItem(.flexible()), count: 4)

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let ownedPacks {
                if ownedPacks.isEmpty {
                    VStack(spacing: 8) {
                        Text("You don't own any emoticon packs yet.").font(.footnote).foregroundColor(IDS.Colors.textSecondary)
                        Button(action: onOpenStore) {
                            Text("Browse Emoticon Store").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                .padding(.horizontal, 12).padding(.vertical, 8)
                                .background(IDS.Colors.card).cornerRadius(10)
                        }
                        .buttonStyle(.plain)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(ownedPacks, id: \.packId) { op in
                                Button(action: { selectedPackId = op.packId }) {
                                    Text(packTitles[op.packId] ?? op.packId).font(.caption2).bold()
                                        .foregroundColor(selectedPackId == op.packId ? .white : IDS.Colors.textPrimary)
                                        .padding(.horizontal, 10).padding(.vertical, 6)
                                        .background(selectedPackId == op.packId ? IDS.Colors.brand : IDS.Colors.card)
                                        .cornerRadius(8)
                                }
                                .buttonStyle(.plain)
                            }
                            Button(action: onOpenStore) {
                                Text("Get more").font(.caption2).bold().foregroundColor(IDS.Colors.textPrimary)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .background(IDS.Colors.card).cornerRadius(8)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    if let packEmoticons {
                        LazyVGrid(columns: columns, spacing: 8) {
                            ForEach(packEmoticons) { e in
                                Button(action: { onSend(e.id) }) {
                                    AsyncImage(url: URL(string: e.imageUrl)) { image in
                                        image.resizable().aspectRatio(contentMode: .fit)
                                    } placeholder: {
                                        ProgressView()
                                    }
                                    .aspectRatio(1, contentMode: .fit)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    } else {
                        ProgressView()
                    }
                }
            } else {
                ProgressView()
            }
        }
        .padding(12)
        .background(IDS.Colors.chipBackground)
        .cornerRadius(12)
        .task {
            async let owned = try? NetworkClient.shared.getOwnedEmoticonPacks().packs
            async let allPacks = try? NetworkClient.shared.getEmoticonPacks().packs
            let ownedResult = (await owned) ?? []
            ownedPacks = ownedResult
            packTitles = Dictionary(uniqueKeysWithValues: ((await allPacks) ?? []).map { ($0.id, $0.title) })
            if let first = ownedResult.first { selectedPackId = first.packId }
        }
        .task(id: selectedPackId) {
            guard let selectedPackId else { return }
            packEmoticons = nil
            packEmoticons = (try? await NetworkClient.shared.getPackEmoticons(packId: selectedPackId).emoticons) ?? []
        }
    }
}

// Real Emoticon Store (item 136) -- browse every real active pack, buy (once-off
// purchase, same "buy it once, own it" model Shop/Insurance already use), or gift to
// a friend by phone number. Mirrors bank-mfe/Android's own EmoticonStoreModal/Dialog
// (items 133/135).
private struct EmoticonStoreView: View {
    let onClose: () -> Void

    @State private var packs: [EmoticonPackDto]?
    @State private var ownedPackIds: Set<String> = []
    @State private var busyPackId: String?
    @State private var giftingPackId: String?
    @State private var giftPhone = ""
    @State private var error: String?
    @State private var message: String?

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    if let error {
                        Text(error).font(.footnote).foregroundColor(.red)
                    }
                    if let message {
                        Text(message).font(.footnote).foregroundColor(IDS.Colors.brand)
                    }
                    if let packs {
                        ForEach(packs) { pack in
                            let owned = ownedPackIds.contains(pack.id)
                            VStack(alignment: .leading, spacing: 6) {
                                HStack(spacing: 10) {
                                    AsyncImage(url: URL(string: pack.thumbnailUrl)) { image in
                                        image.resizable().aspectRatio(contentMode: .fit)
                                    } placeholder: { ProgressView() }
                                        .frame(width: 48, height: 48)
                                    VStack(alignment: .leading) {
                                        Text(pack.title).font(.subheadline).bold().foregroundColor(IDS.Colors.textPrimary)
                                        Text("\(pack.artistName) · \(Int(pack.price)) RWF").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                                    }
                                    Spacer()
                                    Button(action: { Task { await buy(pack.id) } }) {
                                        Text(owned ? "Owned" : (busyPackId == pack.id ? "…" : "Buy")).font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                            .padding(.horizontal, 10).padding(.vertical, 6)
                                            .background(IDS.Colors.card).cornerRadius(8)
                                    }
                                    .buttonStyle(.plain)
                                    .disabled(owned || busyPackId != nil)
                                    Button(action: { giftingPackId = (giftingPackId == pack.id) ? nil : pack.id }) {
                                        Text("Gift").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                            .padding(.horizontal, 10).padding(.vertical, 6)
                                            .background(IDS.Colors.card).cornerRadius(8)
                                    }
                                    .buttonStyle(.plain)
                                    .disabled(busyPackId != nil)
                                }
                                if giftingPackId == pack.id {
                                    HStack {
                                        TextField("Recipient phone number", text: $giftPhone)
                                            .padding(8)
                                            .background(IDS.Colors.card)
                                            .cornerRadius(8)
                                        Button(action: { Task { await gift(pack.id) } }) {
                                            Text(busyPackId == pack.id ? "…" : "Send gift").font(.caption).bold().foregroundColor(IDS.Colors.textPrimary)
                                                .padding(.horizontal, 10).padding(.vertical, 6)
                                                .background(IDS.Colors.card).cornerRadius(8)
                                        }
                                        .buttonStyle(.plain)
                                        .disabled(busyPackId != nil || giftPhone.trimmingCharacters(in: .whitespaces).isEmpty)
                                    }
                                }
                            }
                            .padding(10)
                            .background(IDS.Colors.chipBackground)
                            .cornerRadius(10)
                        }
                    } else {
                        ProgressView()
                    }
                }
                .padding(IDS.Layout.screenHorizontal)
            }
            .navigationTitle("🛍 Emoticon Store")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Close", action: onClose)
                }
            }
        }
        .task { await load() }
    }

    private func load() async {
        do {
            packs = try await NetworkClient.shared.getEmoticonPacks().packs
            ownedPackIds = Set(try await NetworkClient.shared.getOwnedEmoticonPacks().packs.map { $0.packId })
        } catch {
            self.error = "Could not load the Emoticon Store."
        }
    }

    private func buy(_ packId: String) async {
        busyPackId = packId
        error = nil
        defer { busyPackId = nil }
        do {
            _ = try await NetworkClient.shared.purchaseEmoticonPack(packId: packId)
            await load()
        } catch {
            self.error = "Could not purchase this pack."
        }
    }

    private func gift(_ packId: String) async {
        busyPackId = packId
        error = nil
        message = nil
        defer { busyPackId = nil }
        do {
            _ = try await NetworkClient.shared.giftEmoticonPack(packId: packId, recipientPhoneNumber: giftPhone.trimmingCharacters(in: .whitespaces))
            message = "Pack gifted!"
            giftingPackId = nil
            giftPhone = ""
        } catch {
            self.error = "Could not gift this pack."
        }
    }
}

extension ISO8601DateFormatter {
    convenience init(withFractionalSeconds: Bool) {
        self.init()
        if withFractionalSeconds { formatOptions.insert(.withFractionalSeconds) }
    }
}

private func chatMessageTime(_ sentAt: String) -> String {
    let date = ISO8601DateFormatter(withFractionalSeconds: true).date(from: sentAt)
        ?? ISO8601DateFormatter().date(from: sentAt)
    guard let date else { return "" }
    let formatter = DateFormatter()
    formatter.dateFormat = "h:mm a"
    return formatter.string(from: date)
}

private struct MessageBubble: View {
    let message: MessageDto
    let isMine: Bool
    let currentUserId: String?
    let offer: OfferBubbleData?
    let gift: GiftDto?
    var voucher: GiftVoucherDto? = nil
    var emoticonImageUrl: String? = nil
    let onToggleReaction: (String) -> Void
    let onRespondToOffer: (String, String, Double?) -> Void
    let onClaimGift: (String) -> Void
    var onExtendVoucher: (String) -> Void = { _ in }
    let onReply: (MessageDto) -> Void
    let onDelete: (String) -> Void
    let onPin: (MessageDto) -> Void
    let onReportMessage: (String, String) -> Void
    @State private var reportOpen = false
    @State private var reportReason = ""

    var body: some View {
        VStack(alignment: isMine ? .trailing : .leading, spacing: 2) {
            HStack {
                if isMine { Spacer() }
                if let gift {
                    GiftBubble(gift: gift, isMine: isMine, currentUserId: currentUserId, onClaim: onClaimGift)
                } else if let voucher {
                    GiftVoucherBubble(voucher: voucher, isMine: isMine, onExtend: { onExtendVoucher(voucher.id) })
                } else if let offer {
                    OfferBubble(offer: offer, isMine: isMine, currentUserId: currentUserId, onRespond: onRespondToOffer)
                } else if message.emoticonId != nil {
                    EmoticonBubble(imageUrl: emoticonImageUrl)
                } else {
                    Text(message.body)
                        .font(.subheadline)
                        .foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
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
            if isMine && message.deletedAt == nil {
                Button("Delete") { onDelete(message.id) }
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            }
            Button("Pin") { onPin(message) }
                .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            Text("\(isMine && message.readAt == nil ? "1 · " : "")\(chatMessageTime(message.sentAt))")
                .font(.caption2)
                .foregroundColor(IDS.Colors.textSecondary)
            if !isMine {
                Button("Report message") { reportOpen = true }
                    .font(.caption2).foregroundColor(IDS.Colors.textSecondary)
            }
        }
        .alert("Report message", isPresented: $reportOpen) {
            TextField("Reason", text: $reportReason)
            Button("Send") {
                let trimmed = reportReason.trimmingCharacters(in: .whitespacesAndNewlines)
                if trimmed.count >= 3 { onReportMessage(message.id, trimmed); reportReason = "" }
            }
            Button("Cancel", role: .cancel) {}
        } message: { Text("Explain why this selected message should be reviewed.") }
    }
}
