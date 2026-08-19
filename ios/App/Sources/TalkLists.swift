import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork


struct DirectMessagesList: View {
    let conversations: [ConversationSummaryDto]?
    let archivedConversations: [ConversationSummaryDto]?
    let error: String?
    let presence: [String: Bool]
    let onRetry: () -> Void
    let onStarted: (String) -> Void
    let onOpen: (ConversationSummaryDto) -> Void
    let onArchiveChanged: () -> Void

    @State private var newChatPhone = ""
    @State private var startError: String?
    @State private var starting = false
    @State private var contacts: [TalkContactDto]?
    // Real fix, found live 2026-08-05 (same audit that found the identical Android
    // bug): this used to filter on `quiet` (mute) and mislabel the result "Archived"
    // -- there was no real archive concept on the backend yet, so muting had been
    // repurposed to also hide a conversation from the main list. Now that a real,
    // distinct `archived` field exists, muted conversations stay visible in the main
    // list (matching real KakaoTalk: muting only silences notifications, it never
    // hides a room) and this toggle switches to the real archived list.
    @State private var showArchived = false

    // Real swipe actions (2026-08-05) -- closes docs/DESIGN_REFERENCES.md Talk
    // recommendation #4's remaining swipe-gesture half (archive itself is the
    // setConversationArchived call below). `.swipeActions` is a real, hard iOS
    // constraint: it only works on rows inside a `List`, never inside a plain
    // ScrollView/VStack (silently no-ops there, no compile error) -- this whole body
    // moved from ScrollView+VStack to List for that reason, with `.listRowInsets`/
    // `.listRowSeparator(.hidden)`/`.listRowBackground(Color.clear)` on every "row" to
    // preserve the exact same custom-card look the ScrollView version had. Scoped
    // honestly to a single swipe action, matching the Android port's own identical
    // scope note (itunda's Talk has no per-conversation "favorite" to wire a second
    // swipe to).
    var body: some View {
        List {
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
                    IdsTextField("+250788123456", text: $newChatPhone, keyboardType: .phonePad)
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
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 12)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)

            let archivedCount = archivedConversations?.count ?? 0
            if archivedCount > 0 {
                Button(showArchived ? "Show active chats" : "Archived (\(archivedCount))") { showArchived.toggle() }
                    .font(.caption).foregroundColor(IDS.Colors.textSecondary)
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            }

            // Real KakaoTalk pinned-rooms-float-to-top behavior (2026-08-18) -- see
            // ConversationSummaryDto.pinnedToTop's own doc comment. Array.sorted(by:)
            // has been a guaranteed-stable sort since Swift 5, so unpinned rows keep
            // their existing lastMessageAt order beneath the pinned ones, matching
            // bank-mfe/Android's own identical sort. Archived rooms are never
            // re-sorted -- pinning only ever applies to the active list, same gating
            // the swipeActions below already use.
            let visibleList = showArchived
                ? archivedConversations
                : conversations?.sorted { a, b in (a.pinnedToTop ?? false) && !(b.pinnedToTop ?? false) }
            if let error {
                VStack(alignment: .leading, spacing: 10) {
                    Text(error).foregroundColor(.red).font(.subheadline)
                    Button("Retry", action: onRetry)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(20)
                .background(IDS.Colors.card)
                .cornerRadius(IDS.Layout.cardCornerRadius)
                .padding(.horizontal, IDS.Layout.screenHorizontal)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
                .listRowSeparator(.hidden)
            } else if visibleList == nil {
                SkeletonBlock(height: 120)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            } else if visibleList!.isEmpty {
                EmptyStateView(showArchived ? "You haven't archived any chats." : "No conversations yet — start one from Friends, or say hi to someone you already know.")
                    .padding(.horizontal, IDS.Layout.screenHorizontal)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
            } else {
                ForEach(visibleList!) { conversation in
                    Button(action: { onOpen(conversation) }) {
                        ConversationRow(conversation: conversation, online: presence[conversation.otherUserId] == true)
                            .padding(.horizontal, IDS.Layout.screenHorizontal)
                    }
                    .buttonStyle(.plain)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
                    .listRowSeparator(.hidden)
                    .swipeActions(edge: .trailing) {
                        Button(showArchived ? "Unarchive" : "Archive") {
                            Task {
                                _ = try? await NetworkClient.shared.setConversationArchived(
                                    conversationId: conversation.conversationId, archived: !showArchived
                                )
                                onArchiveChanged()
                            }
                        }
                        .tint(showArchived ? IDS.Colors.brand : IDS.Colors.danger)
                    }
                    // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18)
                    // -- see ConversationSummaryDto.pinnedToTop's own doc comment. Only
                    // offered on the active list -- pinning an archived room to the top
                    // of the active list would be a confusing, silently-unarchiving
                    // side effect (same gating bank-mfe/Android already use). This
                    // closes the honest "no per-conversation favorite to wire a second
                    // swipe to" limitation the trailing-swipe comment above named on
                    // 2026-08-05 -- a real one now exists.
                    .swipeActions(edge: .leading) {
                        if !showArchived {
                            let pinned = conversation.pinnedToTop ?? false
                            Button(pinned ? "Unpin" : "Pin") {
                                Task {
                                    _ = try? await NetworkClient.shared.setConversationPinnedToTop(
                                        conversationId: conversation.conversationId, pinned: !pinned
                                    )
                                    onArchiveChanged()
                                }
                            }
                            .tint(IDS.Colors.brand)
                        }
                    }
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(IDS.Colors.backgroundPrimary)
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

struct ConversationRow: View {
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
                    Circle().fill(IDS.Colors.success)
                        .frame(width: 12, height: 12)
                        .overlay(Circle().stroke(IDS.Colors.card, lineWidth: 2))
                }
            }
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(conversation.otherUserName).font(IDS.Typography.bodyBold).foregroundColor(IDS.Colors.textPrimary)
                    // Real KakaoTalk 채팅방 상단 고정 at-a-glance indicator (2026-08-18)
                    // -- see ConversationSummaryDto.pinnedToTop's own doc comment. Same
                    // small-glyph-next-to-name treatment this row would give quiet/
                    // pinnedMessageId if it tracked them (it doesn't -- this row is a
                    // deliberately simpler card than Android's ConversationRow).
                    if conversation.pinnedToTop == true {
                        Image(systemName: "pin.fill")
                            .font(.system(size: 10))
                            .foregroundColor(IDS.Colors.brand)
                    }
                }
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

