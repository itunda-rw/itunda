import SwiftUI
import CoreDesignSystem
import CoreNetwork

// Real AI chatbot channel (itunda Talk redesign, 2026-08-28) -- see backend
// AiChatService's own doc comment (reuses the same self-hosted llama-server the
// Maps/Hood AI summaries already use, single-flight-guarded + per-user
// rate-limited). A client-side-synthesized row -- no persisted conversation.
struct AiChatRow: View {
    let onOpen: () -> Void

    var body: some View {
        Button(action: onOpen) {
            HStack(spacing: 14) {
                ZStack {
                    Circle().fill(IDS.Colors.chipBackground)
                    Image(systemName: "sparkles").font(.system(size: 18)).foregroundColor(IDS.Colors.brand)
                }
                .frame(width: 44, height: 44)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Text("itunda AI").font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                        AiBadge()
                    }
                    Text("Ask anything about itunda").font(IDS.scaledFont(size: 12, weight: .regular, relativeTo: .caption1)).foregroundColor(IDS.Colors.textSecondary)
                }
                Spacer()
            }
        }
        .buttonStyle(.plain)
        .padding(.vertical, 18)
    }
}

// Real, small "AI" disclosure badge -- matches this session's own established
// AI-generated-content honesty convention (Maps/Hood AI summaries carry the same).
struct AiBadge: View {
    var body: some View {
        Text("AI")
            .font(.system(size: 9, weight: .bold))
            .foregroundColor(.white)
            .padding(.horizontal, 5).padding(.vertical, 2)
            .background(IDS.Colors.brand)
            .cornerRadius(4)
    }
}

struct TalkAiChatThread: View {
    let onBack: () -> Void

    @State private var messages: [AiChatMessageDto] = []
    @State private var draft = ""
    @State private var sending = false
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Button(action: onBack) { IDS.Icons.back(size: 18, relativeTo: .title3).frame(width: 44, height: 44) }
                    .accessibilityLabel("Back")
                HStack(spacing: 4) {
                    Text("itunda AI").font(IDS.Typography.title).foregroundColor(IDS.Colors.textPrimary)
                    AiBadge()
                }
                Spacer()
            }
            .padding(.horizontal, 8)

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 10) {
                        if messages.isEmpty {
                            EmptyStateView("Ask itunda AI anything -- it's a small, self-hosted assistant, so answers may be brief or imperfect.")
                                .padding(.horizontal, IDS.Layout.screenHorizontal)
                                .padding(.top, 40)
                        }
                        ForEach(messages) { message in
                            AiChatBubble(message: message).id(message.id)
                        }
                        // Real, always-visible "busy" system bubble -- the shared model
                        // instance is single-flight-guarded, so a second concurrent
                        // request gets this honest signal, never a silently dropped or
                        // fabricated reply. Never a toast that disappears.
                        if busy {
                            HStack {
                                Spacer()
                                Text("itunda AI is busy right now -- try again shortly")
                                    .font(.caption)
                                    .foregroundColor(IDS.Colors.textSecondary)
                                    .padding(10)
                                    .background(IDS.Colors.chipBackground)
                                    .cornerRadius(10)
                                Spacer()
                            }
                            .padding(.horizontal, IDS.Layout.screenHorizontal)
                        }
                        if let error {
                            Text(error).font(.caption).foregroundColor(.red).padding(.horizontal, IDS.Layout.screenHorizontal)
                        }
                    }
                    .padding(.vertical, 12)
                }
                .onChange(of: messages.count) { _ in
                    if let last = messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
                }
            }

            HStack(spacing: 8) {
                IdsTextField("Ask itunda AI...", text: $draft)
                Button(action: { Task { await send() } }) {
                    Text(sending ? "..." : "Send").font(IDS.Typography.bodyBold).foregroundColor(.white)
                        .padding(.horizontal, 16).padding(.vertical, 12)
                        .background(IDS.Colors.brand).cornerRadius(12)
                }
                .disabled(sending || draft.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .padding(IDS.Layout.screenHorizontal)
        }
        .background(IDS.Colors.backgroundPrimary.ignoresSafeArea())
        .task { await loadHistory() }
    }

    private func loadHistory() async {
        messages = (try? await NetworkClient.shared.getAiChatHistory().messages.reversed()) ?? []
    }

    private func send() async {
        let text = draft.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty else { return }
        draft = ""
        sending = true
        busy = false
        error = nil
        defer { sending = false }
        do {
            let res = try await NetworkClient.shared.sendAiChatMessage(text: text)
            messages.append(res.message)
            messages.append(res.reply)
        } catch let NetworkError.httpError(statusCode) where statusCode == 429 {
            // The user's message is genuinely persisted server-side even when the
            // shared model is busy (AiChatService saves it before acquiring the
            // single-flight guard) -- refresh real history rather than fabricating a
            // local echo with a made-up id/timestamp.
            await loadHistory()
            busy = true
        } catch {
            self.error = "Couldn't reach itunda AI. Check your connection and try again."
        }
    }
}

private struct AiChatBubble: View {
    let message: AiChatMessageDto

    private var isUser: Bool { message.role == "user" }

    var body: some View {
        HStack {
            if isUser { Spacer(minLength: 40) }
            VStack(alignment: .leading, spacing: 2) {
                if !isUser {
                    HStack(spacing: 4) {
                        Text("itunda AI").font(.system(size: 10, weight: .semibold)).foregroundColor(IDS.Colors.textSecondary)
                        AiBadge()
                    }
                }
                Text(message.content)
                    .font(IDS.Typography.bodyMedium)
                    .foregroundColor(isUser ? .white : IDS.Colors.textPrimary)
            }
            .padding(12)
            .background(isUser ? IDS.Colors.brand : IDS.Colors.chipBackground)
            .cornerRadius(14)
            if !isUser { Spacer(minLength: 40) }
        }
        .padding(.horizontal, IDS.Layout.screenHorizontal)
    }
}
