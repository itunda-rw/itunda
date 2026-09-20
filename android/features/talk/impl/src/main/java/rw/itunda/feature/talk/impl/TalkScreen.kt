package rw.itunda.feature.talk.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.IdsAvatar
import rw.itunda.core.designsystem.components.IdsSegmentedControl
import rw.itunda.core.designsystem.components.TabHeader
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ConversationSummaryDto
import rw.itunda.core.network.GroupSummaryDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException


// Talk screen entry point + tab switcher. Body composables live in
// TalkLists.kt / TalkGroupsBrowse.kt / TalkGroupThread.kt / TalkGroupExtras.kt /
// TalkChatThread.kt / TalkChatBubbles.kt / TalkMessageBubbles.kt / TalkEmoticons.kt
// (split 2026-08-19 for real file-size decomposition, not a single-slice extraction).

private enum class TalkView { DIRECT, GROUPS, FRIENDS }

// Real group chat (2026-07-18) -- itunda's own KakaoTalk-style group messaging, ported
// to Android from bank-mfe's own Direct/Groups toggle (the "single most defining
// KakaoTalk capability" this session's own project memory names). TalkTab is now a
// thin Direct/Groups toggle wrapper; DirectMessagesList holds the exact same 1:1 logic
// this composable used to own directly.
@Composable
fun TalkTab(
    initialConversationId: String?,
    onConsumedInitial: () -> Unit,
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
) {
    var view by remember { mutableStateOf(TalkView.DIRECT) }
    // Real KakaoTalk 전체/안읽음/통화 filter (itunda Talk redesign, 2026-08-28) -- see
    // TalkFilterTabs.kt's own doc comment.
    var directFilter by remember { mutableStateOf(TalkDirectFilter.ALL) }
    var showServiceChannel by remember { mutableStateOf(false) }
    var showAiChat by remember { mutableStateOf(false) }
    // Real per-room Links/Settings + input-lock (itunda Talk redesign, 2026-08-28) --
    // see TalkLinksTab.kt/TalkRoomSettings.kt's own doc comments.
    var showLinksTab by remember { mutableStateOf(false) }
    var showRoomSettings by remember { mutableStateOf(false) }
    var unlockedConversationIds by remember { mutableStateOf(setOf<String>()) }
    val context = LocalContext.current
    val roomPreferenceStore = remember { TalkRoomPreferenceStore(context) }
    var conversations by remember { mutableStateOf<List<ConversationSummaryDto>?>(null) }
    var conversationsError by remember { mutableStateOf<String?>(null) }
    // Real recoverable archive (2026-08-05) -- see backend ConversationPreference
    // .archived's own doc comment. Loaded alongside the active list so the "Archived
    // (N)" toggle has a real count without an extra round-trip when first tapped.
    var archivedConversations by remember { mutableStateOf<List<ConversationSummaryDto>?>(null) }
    var openConversationId by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<GroupSummaryDto>?>(null) }
    var groupsError by remember { mutableStateOf<String?>(null) }
    var openGroupId by remember { mutableStateOf<String?>(null) }
    // Real online/offline presence for the list view (2026-07-19) -- a bulk on-demand
    // check for every listed contact, refreshed on a 10s cadence, a real coarser signal
    // than the 4s message poll. Per-thread real-time push happens in ChatThreadView.
    var presence by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val coroutineScope = rememberCoroutineScope()
    // Real pagination-discard fix (same systemic gap fixed on web, 2026-09-11
    // -- see project_itunda_pagination_discard_sweep memory) -- a request
    // never asked past page 0 for any of the 3 real lists here, so a user
    // with more than 20 conversations, archived chats, or groups couldn't
    // reach anything past the first page.
    var conversationsPage by remember { mutableStateOf(0) }
    var conversationsHasMore by remember { mutableStateOf(false) }
    var conversationsLoadingMore by remember { mutableStateOf(false) }
    var archivedPage by remember { mutableStateOf(0) }
    var archivedHasMore by remember { mutableStateOf(false) }
    var archivedLoadingMore by remember { mutableStateOf(false) }
    var groupsPage by remember { mutableStateOf(0) }
    var groupsHasMore by remember { mutableStateOf(false) }
    var groupsLoadingMore by remember { mutableStateOf(false) }

    fun loadConversations() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getConversations(page = 0)
                if (res.success) {
                    conversations = res.conversations
                    conversationsPage = 0
                    conversationsHasMore = res.page + 1 < res.totalPages
                }
                conversationsError = null
            } catch (e: HttpException) {
                conversationsError = superAppErrorMessage(e)
            } catch (e: IOException) {
                conversationsError = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    fun loadMoreConversations() {
        val nextPage = conversationsPage + 1
        conversationsLoadingMore = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getConversations(page = nextPage)
                if (res.success) {
                    conversations = (conversations ?: emptyList()) + res.conversations
                    conversationsPage = nextPage
                    conversationsHasMore = res.page + 1 < res.totalPages
                }
            } catch (_: Exception) {
                // Non-critical -- the already-loaded page stays visible; the
                // user can retry by tapping "Load more" again.
            } finally {
                conversationsLoadingMore = false
            }
        }
    }
    fun loadArchivedConversations() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getConversations(archived = true, page = 0)
                if (res.success) {
                    archivedConversations = res.conversations
                    archivedPage = 0
                    archivedHasMore = res.page + 1 < res.totalPages
                }
            } catch (_: Exception) {
                // Real, non-critical -- the active list and "Archived (N)" count still
                // work even if this background fetch fails; retried on next load.
            }
        }
    }
    fun loadMoreArchivedConversations() {
        val nextPage = archivedPage + 1
        archivedLoadingMore = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getConversations(archived = true, page = nextPage)
                if (res.success) {
                    archivedConversations = (archivedConversations ?: emptyList()) + res.conversations
                    archivedPage = nextPage
                    archivedHasMore = res.page + 1 < res.totalPages
                }
            } catch (_: Exception) {
                // Non-critical, same reasoning as loadMoreConversations above.
            } finally {
                archivedLoadingMore = false
            }
        }
    }
    fun loadGroups() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyGroups(page = 0)
                if (res.success) {
                    groups = res.groups
                    groupsPage = 0
                    groupsHasMore = res.page + 1 < res.totalPages
                }
                groupsError = null
            } catch (e: HttpException) {
                groupsError = superAppErrorMessage(e)
            } catch (e: IOException) {
                groupsError = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    fun loadMoreGroups() {
        val nextPage = groupsPage + 1
        groupsLoadingMore = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyGroups(page = nextPage)
                if (res.success) {
                    groups = (groups ?: emptyList()) + res.groups
                    groupsPage = nextPage
                    groupsHasMore = res.page + 1 < res.totalPages
                }
            } catch (_: Exception) {
                // Non-critical, same reasoning as loadMoreConversations above.
            } finally {
                groupsLoadingMore = false
            }
        }
    }
    LaunchedEffect(Unit) { loadConversations(); loadArchivedConversations(); loadGroups() }

    LaunchedEffect(conversations?.map { it.otherUserId }) {
        val otherIds = conversations?.map { it.otherUserId }?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        while (true) {
            try {
                val res = NetworkClient.apiService.getPresence(otherIds)
                if (res.success) presence = res.presence
            } catch (e: Exception) { /* real, non-critical -- only backs the presence dot */ }
            delay(10000)
        }
    }

    // Real "message seller" hand-off from HoodTab -- opens straight into the real
    // chat thread once it shows up in this tab's own real conversation list, same
    // pattern bank-mfe's MessagesView/initialConversationId prop already established.
    // Extended 2026-07-24 to also check `groups` -- Community's "join meetup" hand-off
    // (CommunityContent's onOpenGroupChat, reusing this exact same callback) hands off
    // a real GroupConversation id, not a 1:1 conversation id, so this needs to open the
    // group view instead when that's what matches.
    LaunchedEffect(initialConversationId, conversations, groups) {
        if (initialConversationId == null) return@LaunchedEffect
        if (conversations?.any { it.conversationId == initialConversationId } == true) {
            openConversationId = initialConversationId
            onConsumedInitial()
        } else if (groups?.any { it.groupId == initialConversationId } == true) {
            view = TalkView.GROUPS
            openGroupId = initialConversationId
            onConsumedInitial()
        }
    }

    // Real itunda service channel + AI chatbot channel (itunda Talk redesign,
    // 2026-08-28) -- both are client-side-synthesized entries (no real backend
    // conversation row exists for either, see ServiceChannelService.kt's own doc
    // comment), so they're gated here rather than matched against `conversations`.
    if (showServiceChannel) {
        // onNavigate is a real, deliberate no-op for now -- this app has no generic
        // route-string navigator anywhere yet (ItundaTab is a fixed 5-tab enum with
        // no "Bank" tab at all; see project_itunda_tabs_as_access_points memory), and
        // a real ctaRoute like "/bank/transactions" doesn't map onto it. Building a
        // real app-wide deep-link router is a separate, larger piece of work, not
        // something to fake here as a side effect of the service channel.
        ServiceChannelThreadView(onBack = { showServiceChannel = false }, onNavigate = {})
        return
    }
    if (showAiChat) {
        AiChatThreadView(onBack = { showAiChat = false })
        return
    }

    val openConversation = conversations?.find { it.conversationId == openConversationId }
    if (openConversation != null) {
        if (showLinksTab) {
            TalkLinksTabView(conversationId = openConversation.conversationId, onBack = { showLinksTab = false })
            return
        }
        if (showRoomSettings) {
            TalkRoomSettingsView(conversationId = openConversation.conversationId, roomName = openConversation.otherUserName, onBack = { showRoomSettings = false })
            return
        }
        // Real per-room input lock -- reuses the same real device-step-up gate this
        // tab already receives for payment-adjacent flows (see this file's own header
        // comment on why deviceStepUpHost is injected), rather than adding a second,
        // separate biometric dependency to this Feature module.
        if (roomPreferenceStore.isLocked(openConversation.conversationId) && openConversation.conversationId !in unlockedConversationIds) {
            deviceStepUpHost(
                true,
                { openConversationId = null },
                { unlockedConversationIds = unlockedConversationIds + openConversation.conversationId },
            )
            return
        }
        Box(modifier = Modifier.fillMaxSize()) {
            ChatThreadView(conversation = openConversation, onBack = { openConversationId = null; loadConversations() }, deviceStepUpHost = deviceStepUpHost)
            // Real Links/Settings entry point -- an overlay rather than an edit to
            // ChatThreadView itself, which is frozen at its file-size-lint baseline
            // (TalkChatThread.kt, 751/751 lines) with zero real headroom. BackTopBar's
            // own title Text has no weight(1f) -- it sits left-aligned right after the
            // back icon, so this overlay lands in real empty space for any
            // reasonably-sized conversation name rather than colliding with it.
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)) {
                IconButton(onClick = { showLinksTab = true }) {
                    Icon(Icons.Outlined.Link, contentDescription = "Links", tint = Ids.colors.textPrimary)
                }
                IconButton(onClick = { showRoomSettings = true }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Room settings", tint = Ids.colors.textPrimary)
                }
            }
        }
        return
    }
    val openGroup = groups?.find { it.groupId == openGroupId }
    if (openGroup != null) {
        GroupThreadView(group = openGroup, onBack = { openGroupId = null; loadGroups() })
        return
    }

    // Real fix (2026-08-25) -- same redundant-bottom-padding-vs-Scaffold-inset bug as
    // ItundaAppScreen.kt's PayTab/MenuScreen/MyTab (that file's own doc comment has
    // the full account): this bottom padding was stacking with the Scaffold's already
    // -correct bottomBar inset. Top kept, bottom now comes from Scaffold alone.
    Column(modifier = Modifier.fillMaxSize().padding(start = Ids.layout.screenHorizontal, end = Ids.layout.screenHorizontal, top = Ids.layout.screenVertical)) {
        TabHeader("Talk")
        IdsSegmentedControl(
            options = listOf(TalkView.DIRECT to "Direct", TalkView.GROUPS to "Groups", TalkView.FRIENDS to "Friends"),
            selected = view,
            onSelect = { view = it },
            modifier = Modifier.padding(bottom = Ids.layout.cardGap),
        )
        if (view == TalkView.FRIENDS) {
            FriendsView(
                onStarted = { conversationId ->
                    loadConversations()
                    openConversationId = conversationId
                    view = TalkView.DIRECT
                },
            )
        } else if (view == TalkView.DIRECT) {
            // Real client-side-synthesized itunda service channel + AI chatbot rows
            // (itunda Talk redesign, 2026-08-28) -- pinned above the filter tabs so
            // they're always reachable regardless of the current filter. Neither is a
            // real conversation row (see ServiceChannelService.kt's own doc comment),
            // so they're rendered here rather than injected into `conversations`.
            SyntheticTalkChannelRow("itunda", "Payments, security, and account updates", onClick = { showServiceChannel = true })
            SyntheticTalkChannelRow("itunda AI", "Ask anything -- powered by a self-hosted model", onClick = { showAiChat = true })
            TalkFilterTabsRow(selected = directFilter, onSelect = { directFilter = it })
            if (directFilter == TalkDirectFilter.CALLS) {
                CallHistoryList(conversations = conversations)
            } else {
                DirectMessagesList(
                    conversations = if (directFilter == TalkDirectFilter.UNREAD) conversations?.filter { it.unreadCount > 0 } else conversations,
                    archivedConversations = archivedConversations,
                    error = conversationsError,
                    presence = presence,
                    onRetry = ::loadConversations,
                    onStarted = { conversationId -> loadConversations(); openConversationId = conversationId },
                    onOpen = { openConversationId = it },
                    onArchiveChanged = { loadConversations(); loadArchivedConversations() },
                    hasMore = conversationsHasMore,
                    archivedHasMore = archivedHasMore,
                    loadingMore = conversationsLoadingMore,
                    archivedLoadingMore = archivedLoadingMore,
                    onLoadMore = ::loadMoreConversations,
                    onLoadMoreArchived = ::loadMoreArchivedConversations,
                )
            }
        } else {
            GroupsList(
                groups = groups,
                error = groupsError,
                onRetry = ::loadGroups,
                onCreated = { groupId -> loadGroups(); openGroupId = groupId },
                onOpen = { openGroupId = it },
                hasMore = groupsHasMore,
                loadingMore = groupsLoadingMore,
                onLoadMore = ::loadMoreGroups,
            )
        }
    }
}

// Real client-side-synthesized channel row (itunda service channel + AI chatbot,
// itunda Talk redesign, 2026-08-28) -- a lighter version of ConversationRow
// (TalkLists.kt) without online/unread/pin state, since neither channel has any of
// that real state to show.
@Composable
private fun SyntheticTalkChannelRow(name: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .pressScaleClickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IdsAvatar(name = name)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Text(subtitle, color = Ids.colors.textSecondary, fontSize = 13.sp, maxLines = 1)
        }
    }
}


/** Feature composition entry point. Keeps the app shell independent of concrete screen names. */
@Composable
fun TalkEntryPoint(
    initialConversationId: String?,
    onConsumedInitial: () -> Unit,
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
) {
    TalkTab(initialConversationId, onConsumedInitial, deviceStepUpHost)
}
