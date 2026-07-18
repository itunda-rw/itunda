import SwiftUI
import CoreDesignSystem

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
            TdsPlainTopBar(title: "Talk")
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

    private func tryOpenPending() {
        guard let pending = pendingConversationId,
              let match = conversations?.first(where: { $0.conversationId == pending }) else { return }
        openConversation = match
        pendingConversationId = nil
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

    static func errorMessage(_ statusCode: Int) -> String {
        switch statusCode {
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

    var body: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                VStack(alignment: .leading, spacing: 12) {
                    Text("New chat").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    Text("Enter their phone number to start a conversation.")
                        .font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1))
                        .foregroundColor(IDS.Colors.textSecondary)
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
                } else if conversations!.isEmpty {
                    Text("No conversations yet.").foregroundColor(IDS.Colors.textSecondary)
                } else {
                    ForEach(conversations!) { conversation in
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
    @State private var sending = false
    @State private var error: String?
    @State private var socketTask: URLSessionWebSocketTask?
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
                                GroupMessageBubble(message: message, isMine: message.senderId == currentUserId, senderName: name(for: message.senderId))
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

            if let error {
                Text(error).font(.caption).foregroundColor(.red).padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            HStack {
                TextField("Message", text: $draft)
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
                guard case .groupMessage(let groupId, let pushedMessage) = push, groupId == group.groupId else { return }
                Task { @MainActor in
                    if !(messages ?? []).contains(where: { $0.id == pushedMessage.id }) {
                        messages = (messages ?? []) + [pushedMessage]
                    }
                }
            }
        }
        .onDisappear {
            socketTask?.cancel(with: .goingAway, reason: nil)
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

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        sending = true
        error = nil
        defer { sending = false }
        do {
            let res = try await NetworkClient.shared.sendGroupMessage(groupId: group.groupId, body: body)
            draft = ""
            messages = (messages ?? []) + [res.message]
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct GroupMessageBubble: View {
    let message: GroupMessageDto
    let isMine: Bool
    let senderName: String

    var body: some View {
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

private struct ChatThreadScreen: View {
    let conversation: ConversationSummaryDto
    let onBack: () -> Void

    @State private var messages: [MessageDto]?
    @State private var draft = ""
    @State private var sending = false
    @State private var error: String?
    @State private var socketTask: URLSessionWebSocketTask?
    @State private var otherOnline: Bool?
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
                                MessageBubble(message: message, isMine: message.senderId == currentUserId)
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

            if let error {
                Text(error).font(.caption).foregroundColor(.red).padding(.horizontal, IDS.Layout.screenHorizontal)
            }

            HStack {
                TextField("Message", text: $draft)
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
                        if !(messages ?? []).contains(where: { $0.id == pushedMessage.id }) {
                            messages = (messages ?? []) + [pushedMessage]
                        }
                    }
                case .presenceChange(let userId, let online) where userId == conversation.otherUserId:
                    Task { @MainActor in otherOnline = online }
                default:
                    break
                }
            }
        }
        .onDisappear {
            socketTask?.cancel(with: .goingAway, reason: nil)
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
    }

    private func send() async {
        let body = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty else { return }
        sending = true
        error = nil
        defer { sending = false }
        do {
            let res = try await NetworkClient.shared.sendMessage(conversationId: conversation.conversationId, body: body)
            draft = ""
            messages = (messages ?? []) + [res.message]
        } catch let NetworkError.httpError(statusCode) {
            error = TalkScreen.errorMessage(statusCode)
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
}

private struct MessageBubble: View {
    let message: MessageDto
    let isMine: Bool

    var body: some View {
        HStack {
            if isMine { Spacer() }
            Text(message.body)
                .font(.subheadline)
                .foregroundColor(isMine ? .white : IDS.Colors.textPrimary)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(isMine ? IDS.Colors.brand : IDS.Colors.chipBackground)
                .cornerRadius(16)
            if !isMine { Spacer() }
        }
    }
}
