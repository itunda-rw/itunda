package rw.itunda.feature.talk.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.IdsSegmentedControl
import rw.itunda.core.designsystem.components.TabHeader
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

    fun loadConversations() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getConversations()
                if (res.success) conversations = res.conversations
                conversationsError = null
            } catch (e: HttpException) {
                conversationsError = superAppErrorMessage(e)
            } catch (e: IOException) {
                conversationsError = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    fun loadArchivedConversations() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getConversations(archived = true)
                if (res.success) archivedConversations = res.conversations
            } catch (_: Exception) {
                // Real, non-critical -- the active list and "Archived (N)" count still
                // work even if this background fetch fails; retried on next load.
            }
        }
    }
    fun loadGroups() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyGroups()
                if (res.success) groups = res.groups
                groupsError = null
            } catch (e: HttpException) {
                groupsError = superAppErrorMessage(e)
            } catch (e: IOException) {
                groupsError = "Couldn't reach itunda. Check your connection and try again."
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

    val openConversation = conversations?.find { it.conversationId == openConversationId }
    if (openConversation != null) {
        ChatThreadView(conversation = openConversation, onBack = { openConversationId = null; loadConversations() }, deviceStepUpHost = deviceStepUpHost)
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
            DirectMessagesList(
                conversations = conversations,
                archivedConversations = archivedConversations,
                error = conversationsError,
                presence = presence,
                onRetry = ::loadConversations,
                onStarted = { conversationId -> loadConversations(); openConversationId = conversationId },
                onOpen = { openConversationId = it },
                onArchiveChanged = { loadConversations(); loadArchivedConversations() },
            )
        } else {
            GroupsList(
                groups = groups,
                error = groupsError,
                onRetry = ::loadGroups,
                onCreated = { groupId -> loadGroups(); openGroupId = groupId },
                onOpen = { openGroupId = it },
            )
        }
    }
}

