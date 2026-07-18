import SwiftUI
import CoreDesignSystem

/// Real 1:1 messaging (Kakao-style Talk tab, 2026-07-18) -- iOS mirror of Android's
/// TalkTab (SuperAppTabs.kt). See NetworkClient.swift's Messaging extension and
/// rw.itunda.messaging.MessagingService's own doc comment for the full backend
/// account, including the honest "poll-based delivery, no live transport yet" scope
/// this screen matches exactly (a 4s poll while a thread is open, same interval
/// bank-mfe/Android already use).
struct TalkScreen: View {
    /// Real "message seller" hand-off from HoodScreen -- ContentView stashes the real
    /// conversation id returned by `contactSeller` here and switches to this tab; once
    /// it shows up in this screen's own real conversation list, it opens directly,
    /// mirroring Android's initialConversationId/onConsumedInitial pair exactly.
    @Binding var pendingConversationId: String?

    @State private var conversations: [ConversationSummaryDto]?
    @State private var error: String?
    @State private var openConversation: ConversationSummaryDto?
    @State private var newChatPhone = ""
    @State private var startError: String?
    @State private var starting = false

    var body: some View {
        Group {
            if let openConversation {
                ChatThreadScreen(conversation: openConversation, onBack: {
                    self.openConversation = nil
                    Task { await load() }
                })
            } else {
                listBody
            }
        }
        .task { await load() }
        .onChange(of: pendingConversationId) { _ in tryOpenPending() }
        .onChange(of: conversations?.count) { _ in tryOpenPending() }
    }

    private var listBody: some View {
        ScrollView {
            VStack(spacing: IDS.Layout.cardGap) {
                TdsPlainTopBar(title: "Talk")

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
                        Button("Retry") { Task { await load() } }
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
                        Button(action: { openConversation = conversation }) {
                            ConversationRow(conversation: conversation)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, IDS.Layout.screenTop)
            .padding(.bottom, IDS.Layout.sectionSpacing)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
    }

    private func tryOpenPending() {
        guard let pending = pendingConversationId,
              let match = conversations?.first(where: { $0.conversationId == pending }) else { return }
        openConversation = match
        pendingConversationId = nil
    }

    private func load() async {
        do {
            let res = try await NetworkClient.shared.getConversations()
            conversations = res.conversations
            error = nil
        } catch {
            self.error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func startConversation() async {
        starting = true
        startError = nil
        defer { starting = false }
        do {
            let res = try await NetworkClient.shared.startConversation(phoneNumber: newChatPhone.trimmingCharacters(in: .whitespaces))
            newChatPhone = ""
            await load()
            if let match = conversations?.first(where: { $0.conversationId == res.conversation.id }) {
                openConversation = match
            }
        } catch let NetworkError.httpError(statusCode) {
            startError = Self.errorMessage(statusCode)
        } catch {
            startError = "Couldn't reach itunda. Check your connection and try again."
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

private struct ConversationRow: View {
    let conversation: ConversationSummaryDto

    var body: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle().fill(IDS.Colors.chipBackground)
                Image(systemName: "paperplane.fill").foregroundColor(IDS.Colors.brand)
            }
            .frame(width: 44, height: 44)
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
                Text(conversation.otherUserName).font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
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
        // Real poll-based "live" delivery -- see this file's own header comment for
        // why (matches bank-mfe/Android's identical 4s interval).
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                await refresh()
            }
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
