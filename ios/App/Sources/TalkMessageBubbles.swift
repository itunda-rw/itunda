import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork


// Real quick-react palette (2026-07-19) -- a small fixed set matching bank-mfe's own
// MessageReactions component exactly, kept simple rather than a full emoji picker.
let quickReactions = ["👍", "❤️", "😂", "😮", "😢"]

// Real emoji reactions -- shared between 1:1 and group threads. Tapping an existing
// reaction badge toggles the current user's own reaction for that emoji (the fast,
// one-tap path real chat apps use); the smile button opens the quick palette for a
// first reaction.
struct MessageReactionsRow: View {
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
                    HStack(spacing: 3) {
                        ReactionGlyph(emoji: r.emoji, size: 12)
                        Text("\(r.userIds.count)")
                    }
                    .font(.caption2)
                    .foregroundColor(IDS.Colors.textSecondary)
                    .padding(.horizontal, 8).padding(.vertical, 2)
                    .background(mine ? IDS.Colors.brand.opacity(0.15) : IDS.Colors.chipBackground)
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            // Real custom quick-react picker (2026-08-22, replacing a native `Menu`) --
            // matches web/Android's own itundaface picker exactly. A native SwiftUI
            // `Menu` can only show system-rendered rows (plain text or an SF Symbol/
            // asset-catalog image via `Label`), not an arbitrary custom-drawn View, so
            // it couldn't render itundaface's own hand-drawn reaction glyphs -- this
            // custom overlay (a real, deliberate UX change, not just an icon swap) is
            // what closing that gap actually required.
            ZStack(alignment: isMine ? .bottomTrailing : .bottomLeading) {
                Button(action: { pickerOpen.toggle() }) {
                    Image(systemName: "face.smiling").font(.caption).foregroundColor(IDS.Colors.textSecondary)
                }
                .accessibilityLabel("Add reaction")
                if pickerOpen {
                    HStack(spacing: 6) {
                        ForEach(quickReactions, id: \.self) { emoji in
                            Button(action: {
                                onToggle(emoji)
                                pickerOpen = false
                            }) {
                                ReactionGlyph(emoji: emoji, size: 22)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 8).padding(.vertical, 4)
                    .background(IDS.Colors.card)
                    .clipShape(Capsule())
                    .shadow(color: .black.opacity(0.15), radius: 4, y: 1)
                    .offset(y: -32)
                }
            }
            if !isMine { Spacer() }
        }
    }
}

// Real KakaoTalk Emoticon Store (item 136) -- a received emoticon renders as just the
// sticker image, no chat-bubble background, matching real KakaoTalk and bank-mfe/
// Android's own EmoticonBubble (items 133/135).
struct EmoticonBubble: View {
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

// ISO8601DateFormatter(withFractionalSeconds:) moved to Core/DesignSystem/Sources/
// Components/ISO8601DateFormatterExtensions.swift (2026-09-02, Pay Feature-module
// decomposition) -- see that file's doc comment for why.

func chatMessageTime(_ sentAt: String) -> String {
    let date = ISO8601DateFormatter(withFractionalSeconds: true).date(from: sentAt)
        ?? ISO8601DateFormatter().date(from: sentAt)
    guard let date else { return "" }
    let formatter = DateFormatter()
    formatter.dateFormat = "h:mm a"
    return formatter.string(from: date)
}

/// Real Kakao/Toss/iMessage-style collapsed-per-run timestamp convention
/// (docs/DESIGN_REFERENCES.md Talk section, recommendation #8's "remaining polish
/// gap": "each message shows its own timestamp, not grouped by consecutive-run"),
/// ported from the same-day Android fix (HoodShared.kt's shouldShowChatTimestamp).
/// A message shows its timestamp only when it's the last in a consecutive run from
/// the same sender within the same local minute. Compares full local date+minute,
/// not chatMessageTime's "h:mm a" clock-face string alone -- that would
/// false-positive "same run" for two messages sent at the same clock time on
/// different days, a real risk in a search-results list where adjacent entries
/// aren't temporally adjacent in the real conversation.
func shouldShowChatTimestamp<T>(_ messages: [T], _ index: Int, senderId: (T) -> String, sentAt: (T) -> String) -> Bool {
    guard index < messages.count - 1 else { return true }
    let current = messages[index]
    let next = messages[index + 1]
    if senderId(current) != senderId(next) { return true }
    func minuteKey(_ isoTimestamp: String) -> String? {
        guard let date = ISO8601DateFormatter(withFractionalSeconds: true).date(from: isoTimestamp)
            ?? ISO8601DateFormatter().date(from: isoTimestamp) else { return nil }
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm"
        return formatter.string(from: date)
    }
    guard let currentKey = minuteKey(sentAt(current)), let nextKey = minuteKey(sentAt(next)) else { return true }
    return currentKey != nextKey
}

/// Builds MessageBubble's trailing status line without a dangling "1 · " separator
/// when the timestamp itself is collapsed (showTimestamp == false) -- the unread
/// marker still needs to show on every unread message, independent of whether this
/// particular message is the one that renders the run's timestamp.
func messageStatusText(isMine: Bool, unread: Bool, showTimestamp: Bool, sentAt: String) -> String? {
    var parts: [String] = []
    if isMine && unread { parts.append("1") }
    if showTimestamp { parts.append(chatMessageTime(sentAt)) }
    return parts.isEmpty ? nil : parts.joined(separator: " · ")
}

// Real message Forward destination picker -- ports Android TalkScreen.kt's own
// ForwardDestinationDialog to iOS, deliberately scoped to a single destination (not
// Android's up-to-10 multi-select) to match bank-mfe's own simpler forwardMessage
// convention. Loads real conversations + groups, calls whichever of
// forwardDirectMessage/forwardGroupMessage matches this message's source thread type.
struct ForwardPickerView: View {
    let onForward: (_ destinationType: String, _ destinationId: String) async -> Bool
    let onDismiss: () -> Void

    @State private var conversations: [ConversationSummaryDto] = []
    @State private var groups: [GroupSummaryDto] = []
    @State private var sending = false
    @State private var error: String?

    var body: some View {
        NavigationView {
            List {
                Section("Direct messages") {
                    ForEach(conversations) { c in
                        Button(c.otherUserName) { Task { await forward(to: "DIRECT", id: c.conversationId) } }
                            .disabled(sending)
                    }
                }
                Section("Groups") {
                    ForEach(groups) { g in
                        Button(g.name) { Task { await forward(to: "GROUP", id: g.groupId) } }
                            .disabled(sending)
                    }
                }
            }
            .navigationTitle("Forward to…")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: onDismiss) }
            }
            .task {
                conversations = (try? await NetworkClient.shared.getConversations().conversations) ?? []
                groups = (try? await NetworkClient.shared.getMyGroups().groups) ?? []
            }
            if let error {
                Text(error).font(.caption).foregroundColor(.red)
            }
        }
    }

    private func forward(to destinationType: String, id destinationId: String) async {
        sending = true
        defer { sending = false }
        let ok = await onForward(destinationType, destinationId)
        if ok { onDismiss() } else { error = "Couldn't forward this message. Check your connection and try again." }
    }
}

// Real per-thread shared-media gallery (Kakao's real "Chat Room Drawer") -- ports
// Android TalkScreen.kt's own identical addition (2026-08-04) to iOS. Scoped honestly
// to photos only: itunda has real photo messages but no file-attachment type and no
// link-preview system, so a real "files/links" tab would have nothing genuine to show.
// Built entirely client-side from the conversation's own already-loaded messages
// (filtered to real imageUrl != nil entries) -- no new backend endpoint.
struct MediaGalleryView: View {
    let imageUrls: [String]
    private let columns = [GridItem(.flexible(), spacing: 4), GridItem(.flexible(), spacing: 4), GridItem(.flexible(), spacing: 4)]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Shared photos (\(imageUrls.count))")
                .font(.headline).foregroundColor(IDS.Colors.textPrimary)
                .padding(16)
            if imageUrls.isEmpty {
                Text("No photos shared in this conversation yet.")
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    .padding(.horizontal, 16)
                Spacer()
            } else {
                ScrollView {
                    LazyVGrid(columns: columns, spacing: 4) {
                        ForEach(imageUrls, id: \.self) { url in
                            AsyncImage(url: URL(string: url)) { image in
                                image.resizable().aspectRatio(1, contentMode: .fill)
                            } placeholder: {
                                Color(.tertiarySystemBackground)
                            }
                            .aspectRatio(1, contentMode: .fill)
                            .clipped()
                            .cornerRadius(6)
                        }
                    }
                    .padding(.horizontal, 4)
                }
            }
        }
    }
}
