import SwiftUI
import UIKit
import CoreDesignSystem
import CoreNetwork


// Talk tab entry point + tab switcher. Sub-views live in
// TalkLists.swift / TalkGroupsBrowse.swift / TalkGroupThread.swift /
// TalkSplitBills.swift / TalkGroupExtras.swift / TalkChatThread.swift /
// TalkChatBubbles.swift / TalkMessageBubbles.swift / TalkEmoticons.swift
// (split 2026-08-19 for real file-size decomposition).

/// Real 1:1 messaging (Kakao-style Talk tab, 2026-07-18) -- iOS mirror of Android's
/// TalkTab (SuperAppTabs.kt). See NetworkClient.swift's Messaging extension and
/// rw.itunda.messaging.MessagingService's own doc comment for the full backend
/// account, including the honest "poll-based delivery, no live transport yet" scope
/// this screen matches exactly (a 4s poll while a thread is open, same interval
/// bank-mfe/Android already use).
enum TalkView { case direct, groups, friends }

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
    // Real service-channel ctaRoute navigation (itunda Talk redesign, 2026-08-28) --
    // see ContentView.swift's own doc comment on the honest partial-router scope.
    var onNavigateRoute: (String) -> Void = { _ in }

    @State private var view: TalkView = .direct
    // Real itunda service channel + AI chatbot channel (itunda Talk redesign,
    // 2026-08-28) -- see TalkServiceChannelThread.swift/TalkAiChatThread.swift's own
    // doc comments. Client-side-synthesized rows, not real conversations -- neither
    // has a persisted conversation row on the backend.
    @State private var openServiceChannel = false
    @State private var openAiChat = false
    @State private var conversations: [ConversationSummaryDto]?
    @State private var conversationsError: String?
    // Real recoverable archive (2026-08-05) -- see backend ConversationPreference
    // .archived's own doc comment. Loaded alongside the active list so the
    // "Archived (N)" toggle has a real count without an extra round-trip.
    @State private var archivedConversations: [ConversationSummaryDto]?
    @State private var openConversation: ConversationSummaryDto?
    @State private var groups: [GroupSummaryDto]?
    @State private var groupsError: String?
    @State private var openGroup: GroupSummaryDto?
    // Real pagination-discard fix (same systemic gap fixed on web/Android,
    // 2026-09-11) -- independent page/hasMore/loadingMore per list, since a
    // request never asked past page 0 for any of the 3 real lists here.
    @State private var conversationsPage = 0
    @State private var conversationsHasMore = false
    @State private var conversationsLoadingMore = false
    @State private var archivedPage = 0
    @State private var archivedHasMore = false
    @State private var archivedLoadingMore = false
    @State private var groupsPage = 0
    @State private var groupsHasMore = false
    @State private var groupsLoadingMore = false
    // Real online/offline presence for the list view (2026-07-19) -- a bulk on-demand
    // check for every listed contact, refreshed on a 10s cadence, a real coarser signal
    // than the 4s message poll. Per-thread real-time push happens in ChatThreadScreen.
    @State private var presence: [String: Bool] = [:]
    // Real chat-list filter tabs (전체/안읽음/통화) (itunda Talk redesign, 2026-08-28)
    // -- see TalkFilterTabs.swift's own doc comment.
    @State private var listFilter: TalkListFilter = .all
    @State private var calls: [CallSessionDto]?
    @State private var callsError: String?
    // Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix
    // -- see project_itunda_pagination_discard_sweep memory).
    @State private var callsPage = 0
    @State private var callsHasMore = false
    @State private var loadingMoreCalls = false

    var body: some View {
        Group {
            if let openConversation {
                RoomLockGate(roomId: openConversation.conversationId) {
                    ChatThreadScreen(conversation: openConversation, onBack: {
                        self.openConversation = nil
                        Task { await loadConversations() }
                    })
                }
            } else if let openGroup {
                RoomLockGate(roomId: openGroup.groupId) {
                    GroupThreadScreen(group: openGroup, onBack: {
                        self.openGroup = nil
                        Task { await loadGroups() }
                    })
                }
            } else if openServiceChannel {
                TalkServiceChannelThread(onBack: { openServiceChannel = false }, onNavigate: onNavigateRoute)
            } else if openAiChat {
                TalkAiChatThread(onBack: { openAiChat = false })
            } else {
                listBody
            }
        }
        .task { await loadConversations() }
        .task { await loadArchivedConversations() }
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
                Text("Friends").tag(TalkView.friends)
            }
            .pickerStyle(.segmented)
            .padding(.horizontal, IDS.Layout.screenHorizontal)
            .padding(.top, 8)

            if view == .friends {
                FriendsList(onStarted: { conversationId in
                    Task {
                        await loadConversations()
                        if let match = conversations?.first(where: { $0.conversationId == conversationId }) {
                            openConversation = match
                        }
                        view = .direct
                    }
                })
            } else if view == .direct {
                TalkFilterTabsBar(selection: $listFilter)
                if listFilter == .all {
                    ServiceChannelRow(onOpen: { openServiceChannel = true })
                        .padding(.horizontal, IDS.Layout.screenHorizontal)
                    AiChatRow(onOpen: { openAiChat = true })
                        .padding(.horizontal, IDS.Layout.screenHorizontal)
                }
                if listFilter == .calls {
                    CallHistoryList(
                        calls: calls, error: callsError, onRetry: { Task { await loadCalls() } },
                        hasMore: callsHasMore, loadingMore: loadingMoreCalls,
                        onLoadMore: { Task { await loadMoreCalls() } }
                    )
                        .task { await loadCalls() }
                } else {
                    DirectMessagesList(
                        conversations: listFilter == .unread ? conversations?.filter { $0.unreadCount > 0 } : conversations,
                        archivedConversations: archivedConversations,
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
                        onOpen: { openConversation = $0 },
                        onArchiveChanged: {
                            Task {
                                await loadConversations()
                                await loadArchivedConversations()
                            }
                        },
                        hasMore: conversationsHasMore,
                        archivedHasMore: archivedHasMore,
                        loadingMore: conversationsLoadingMore,
                        archivedLoadingMore: archivedLoadingMore,
                        onLoadMore: { Task { await loadMoreConversations() } },
                        onLoadMoreArchived: { Task { await loadMoreArchivedConversations() } }
                    )
                }
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
                    onOpen: { openGroup = $0 },
                    hasMore: groupsHasMore,
                    loadingMore: groupsLoadingMore,
                    onLoadMore: { Task { await loadMoreGroups() } }
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
            let res = try await NetworkClient.shared.getConversations(page: 0)
            // Real Toss-sourced "layering illusion" reorder animation (2026-08-29,
            // toss.tech/article/interaction's own real "Account Organization
            // Animation" example -- reordering a list should animate the move, not
            // jump). Pinning a conversation to the top used to snap the whole list
            // to its new order on the next reload with zero motion -- web/Android's
            // identical gap closed the same session. SwiftUI's List/ForEach diffing
            // animates row moves automatically once the state change itself is
            // wrapped in withAnimation.
            withAnimation(.easeInOut(duration: 0.25)) { conversations = res.conversations }
            conversationsPage = 0
            conversationsHasMore = res.page + 1 < res.totalPages
            conversationsError = nil
        } catch {
            conversationsError = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadMoreConversations() async {
        let nextPage = conversationsPage + 1
        conversationsLoadingMore = true
        defer { conversationsLoadingMore = false }
        do {
            let res = try await NetworkClient.shared.getConversations(page: nextPage)
            conversations = (conversations ?? []) + res.conversations
            conversationsPage = nextPage
            conversationsHasMore = res.page + 1 < res.totalPages
        } catch {
            // Non-critical -- the already-loaded page stays visible; the user
            // can retry by tapping "Load more" again.
        }
    }

    private func loadArchivedConversations() async {
        // Real, non-critical -- the active list and "Archived (N)" count still work
        // even if this background fetch fails; retried on next load.
        if let res = try? await NetworkClient.shared.getConversations(archived: true, page: 0) {
            archivedConversations = res.conversations
            archivedPage = 0
            archivedHasMore = res.page + 1 < res.totalPages
        }
    }

    private func loadMoreArchivedConversations() async {
        let nextPage = archivedPage + 1
        archivedLoadingMore = true
        defer { archivedLoadingMore = false }
        if let res = try? await NetworkClient.shared.getConversations(archived: true, page: nextPage) {
            archivedConversations = (archivedConversations ?? []) + res.conversations
            archivedPage = nextPage
            archivedHasMore = res.page + 1 < res.totalPages
        }
    }

    private func loadCalls() async {
        do {
            let res = try await NetworkClient.shared.getCallHistory(page: 0)
            calls = res.calls
            callsHasMore = res.page + 1 < res.totalPages
            callsError = nil
        } catch {
            callsError = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadMoreCalls() async {
        let nextPage = callsPage + 1
        loadingMoreCalls = true
        defer { loadingMoreCalls = false }
        guard let res = try? await NetworkClient.shared.getCallHistory(page: nextPage) else { return }
        calls = (calls ?? []) + res.calls
        callsPage = nextPage
        callsHasMore = res.page + 1 < res.totalPages
    }

    private func loadGroups() async {
        do {
            let res = try await NetworkClient.shared.getMyGroups(page: 0)
            groups = res.groups
            groupsPage = 0
            groupsHasMore = res.page + 1 < res.totalPages
            groupsError = nil
        } catch {
            groupsError = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    private func loadMoreGroups() async {
        let nextPage = groupsPage + 1
        groupsLoadingMore = true
        defer { groupsLoadingMore = false }
        do {
            let res = try await NetworkClient.shared.getMyGroups(page: nextPage)
            groups = (groups ?? []) + res.groups
            groupsPage = nextPage
            groupsHasMore = res.page + 1 < res.totalPages
        } catch {
            // Non-critical, same reasoning as loadMoreConversations above.
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

