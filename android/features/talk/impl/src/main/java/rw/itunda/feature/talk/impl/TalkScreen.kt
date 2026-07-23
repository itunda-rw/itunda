package rw.itunda.feature.talk.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.TabHeader
import rw.itunda.core.designsystem.components.chatMessageTime
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddGroupMemberRequest
import rw.itunda.core.network.ConversationSummaryDto
import rw.itunda.core.network.CreateChatReportRequest
import rw.itunda.core.network.CreateGroupRequest
import rw.itunda.core.network.CreateSplitBillRequest
import rw.itunda.core.network.GiftDto
import rw.itunda.core.network.GroupMemberDto
import rw.itunda.core.network.GroupMessageDto
import rw.itunda.core.network.GroupSummaryDto
import rw.itunda.core.network.MessageDto
import rw.itunda.core.network.MessagingSocketPush
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PriceOfferDto
import rw.itunda.core.network.PropertyPriceOfferDto
import rw.itunda.core.network.ReactionGroupDto
import rw.itunda.core.network.RespondToOfferRequest
import rw.itunda.core.network.RespondToPropertyOfferRequest
import rw.itunda.core.network.SendGiftInConversationRequest
import rw.itunda.core.network.SendGroupMessageRequest
import rw.itunda.core.network.SendMessageRequest
import rw.itunda.core.network.SetConversationQuietRequest
import rw.itunda.core.network.SplitBillWithParticipants
import rw.itunda.core.network.StartConversationRequest
import rw.itunda.core.network.TalkContactDto
import rw.itunda.core.network.ToggleReactionRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.time.Instant
import java.util.UUID

// Seventh and final Feature extraction (2026-07-23) after Marketplace/Jobs/Property/
// Community/Shop/Eats, same template -- see features/marketplace/impl/.../
// MarketplaceScreen.kt's own header comment for the full account. Talk is the one
// section that isn't REST-CRUD shaped: real-time WebSocket connections
// (NetworkClient.connectMessagingSocket, already living in :core:network) are opened
// and torn down per-thread via DisposableEffect, both for 1:1 chat (ChatThreadView) and
// group chat (GroupThreadView) -- confirming the same module boundary that worked for
// every REST-shaped Feature also holds for a real-time one, since the WebSocket
// lifecycle itself is just NetworkClient, no different from any other call.
//
// Only one injected slot needed here, and only in one place: deviceStepUpHost, used by
// ChatThreadView's gift send/claim flow -- same reasoning as every other extraction
// this session (DeviceStepUpHost.kt wraps :features:payments:impl's dialog, so it
// can't become a direct Feature-to-Feature dependency). No routeMiniMap slot is needed
// -- Talk has no lat/lng "directions" feature anywhere.

// ============================== TALK (Messaging) ==============================

private enum class TalkView { DIRECT, GROUPS }

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
    LaunchedEffect(Unit) { loadConversations(); loadGroups() }

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
    LaunchedEffect(initialConversationId, conversations) {
        if (initialConversationId != null && conversations?.any { it.conversationId == initialConversationId } == true) {
            openConversationId = initialConversationId
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        TabHeader("Talk")
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Ids.layout.cardGap).clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary),
        ) {
            listOf(TalkView.DIRECT to "Direct", TalkView.GROUPS to "Groups").forEach { (v, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (view == v) Ids.colors.brand else Color.Transparent)
                        .clickable { view = v }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (view == v) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        if (view == TalkView.DIRECT) {
            DirectMessagesList(
                conversations = conversations,
                error = conversationsError,
                presence = presence,
                onRetry = ::loadConversations,
                onStarted = { conversationId -> loadConversations(); openConversationId = conversationId },
                onOpen = { openConversationId = it },
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

@Composable
private fun DirectMessagesList(
    conversations: List<ConversationSummaryDto>?,
    error: String?,
    presence: Map<String, Boolean>,
    onRetry: () -> Unit,
    onStarted: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var startPhoneNumber by remember { mutableStateOf("") }
    var startError by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<TalkContactDto>?>(null) }
    var showArchived by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        contacts = try {
            NetworkClient.apiService.getTalkContacts().takeIf { it.success }?.contacts ?: emptyList()
        } catch (_: Exception) {
            // Starting by phone remains available when the contact directory cannot load.
            emptyList()
        }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)) {
        item {
            Card(
                shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("New chat", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Choose a saved contact, or enter their phone number.", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    if (!contacts.isNullOrEmpty()) {
                        Text("Your contacts", color = Ids.colors.textSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 4.dp, bottom = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            contacts.orEmpty().forEach { contact ->
                                TextButton(
                                    enabled = !starting,
                                    onClick = {
                                        starting = true
                                        startError = null
                                        coroutineScope.launch {
                                            try {
                                                val res = NetworkClient.apiService.startConversation(
                                                    StartConversationRequest(otherUserId = contact.userId),
                                                )
                                                if (res.success) onStarted(res.conversation.id)
                                            } catch (e: HttpException) {
                                                startError = superAppErrorMessage(e)
                                            } catch (e: IOException) {
                                                startError = "Couldn't reach itunda. Check your connection and try again."
                                            } finally {
                                                starting = false
                                            }
                                        }
                                    },
                                ) { Text(contact.name, maxLines = 1) }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = startPhoneNumber,
                            onValueChange = { startPhoneNumber = it },
                            placeholder = { Text("+250788123456") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Ids.colors.brand)
                                .clickable(enabled = !starting && startPhoneNumber.isNotBlank()) {
                                    starting = true
                                    startError = null
                                    coroutineScope.launch {
                                        try {
                                            val res = NetworkClient.apiService.startConversation(StartConversationRequest(phoneNumber = startPhoneNumber.trim()))
                                            if (res.success) {
                                                startPhoneNumber = ""
                                                onStarted(res.conversation.id)
                                            }
                                        } catch (e: HttpException) {
                                            startError = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            startError = "Couldn't reach itunda. Check your connection and try again."
                                        } finally {
                                            starting = false
                                        }
                                    }
                                }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                        ) {
                            Text(if (starting) "..." else "Chat", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    startError?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
        val archivedCount = conversations?.count { it.quiet } ?: 0
        if (archivedCount > 0) item {
            TextButton(onClick = { showArchived = !showArchived }) {
                Text(if (showArchived) "Show active chats" else "Archived ($archivedCount)", color = Ids.colors.textSecondary)
            }
        }
        if (error != null) {
            item { ErrorCard(error, onRetry = onRetry) }
        } else if (conversations == null) {
            item { SkeletonBlock() }
        } else if (conversations.filter { if (showArchived) it.quiet else !it.quiet }.isEmpty()) {
            item { EmptyState("No conversations yet.", icon = Icons.Outlined.ChatBubbleOutline) }
        } else {
            items(conversations.filter { if (showArchived) it.quiet else !it.quiet }, key = { it.conversationId }) { c -> ConversationRow(c, online = presence[c.otherUserId] == true, onClick = { onOpen(c.conversationId) }) }
        }
    }
}

@Composable
private fun GroupsList(
    groups: List<GroupSummaryDto>?,
    error: String?,
    onRetry: () -> Unit,
    onCreated: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var phoneNumbers by remember { mutableStateOf("") }
    var createError by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)) {
        item {
            Card(
                shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("New group", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("A group name and everyone's real phone number, comma-separated.", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Group name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phoneNumbers,
                        onValueChange = { phoneNumbers = it },
                        placeholder = { Text("+250788123456, +250788987654") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (creating || name.isBlank() || phoneNumbers.isBlank()) Ids.colors.textTertiary else Ids.colors.brand)
                            .clickable(enabled = !creating && name.isNotBlank() && phoneNumbers.isNotBlank()) {
                                creating = true
                                createError = null
                                val numbers = phoneNumbers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                coroutineScope.launch {
                                    try {
                                        val res = NetworkClient.apiService.createGroup(CreateGroupRequest(name = name.trim(), memberPhoneNumbers = numbers))
                                        if (res.success) {
                                            name = ""
                                            phoneNumbers = ""
                                            onCreated(res.group.groupId)
                                        }
                                    } catch (e: HttpException) {
                                        createError = superAppErrorMessage(e)
                                    } catch (e: IOException) {
                                        createError = "Couldn't reach itunda. Check your connection and try again."
                                    } finally {
                                        creating = false
                                    }
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (creating) "Creating…" else "Create group", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    createError?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
        if (error != null) {
            item { ErrorCard(error, onRetry = onRetry) }
        } else if (groups == null) {
            item { SkeletonBlock() }
        } else if (groups.isEmpty()) {
            item { EmptyState("No groups yet.", icon = Icons.Outlined.ChatBubbleOutline) }
        } else {
            items(groups, key = { it.groupId }) { g -> GroupRow(g, onClick = { onOpen(g.groupId) }) }
        }
    }
}

@Composable
private fun GroupRow(group: GroupSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("${group.memberCount} members", color = Ids.colors.textSecondary, fontSize = 11.sp)
            }
            Text(group.lastMessagePreview ?: "No messages yet.", color = Ids.colors.textSecondary, fontSize = 13.sp, maxLines = 1)
        }
        if (group.unreadCount > 0) {
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(group.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun GroupThreadView(group: GroupSummaryDto, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var messages by remember { mutableStateOf<List<GroupMessageDto>?>(null) }
    var members by remember { mutableStateOf<List<GroupMemberDto>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<GroupMessageDto?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var typingUserIds by remember { mutableStateOf<Map<String, Job>>(emptyMap()) }
    var socket by remember { mutableStateOf<WebSocket?>(null) }
    var lastTypingSentAt by remember { mutableStateOf(0L) }
    // Real KakaoPay-style split bill (found 2026-07-22 fully built on the backend
    // with zero UI anywhere) -- toggles a sibling view over this same group thread,
    // same pattern MarketplaceView/JobsView use for their own WISHLIST tab.
    var showSplitBills by remember { mutableStateOf(false) }
    // Real leave-group/add-member (found 2026-07-22: POST/DELETE .../members already
    // existed on the backend with zero UI anywhere -- same toggle pattern as above.
    var showManageMembers by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val listState: LazyListState = rememberLazyListState()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }

    suspend fun refresh() {
        try {
            val res = NetworkClient.apiService.getGroupMessages(group.groupId)
            if (res.success) messages = res.messages.reversed()
        } catch (_: Exception) {
            // Keep showing the last-known messages rather than blanking the thread on
            // a transient poll failure.
        }
    }

    LaunchedEffect(group.groupId) {
        while (true) {
            refresh()
            delay(4000)
        }
    }
    // Real member list with real resolved display names (2026-07-18), fetched once per
    // thread open -- closes the honest, named limitation this UI carried since group
    // chat first shipped (a truncated sender id instead of a real name).
    LaunchedEffect(group.groupId) {
        try {
            val res = NetworkClient.apiService.getGroupMembers(group.groupId)
            if (res.success) members = res.members
        } catch (_: Exception) {
            // Real, non-critical -- a failed member-list fetch shouldn't block the
            // thread; bubbles just fall back to a truncated sender id below.
        }
    }
    // Real WebSocket live-transport for group chat -- same socket 1:1 already uses,
    // routing on message type via MessagingSocketPush.
    DisposableEffect(group.groupId) {
        val ws = NetworkClient.connectMessagingSocket { push ->
            when {
                push is MessagingSocketPush.GroupMessagePush && push.groupConversationId == group.groupId -> {
                    coroutineScope.launch(Dispatchers.Main) {
                        typingUserIds = typingUserIds - push.message.senderId
                        val current = messages ?: emptyList()
                        if (current.none { it.id == push.message.id }) {
                            messages = current + push.message
                        }
                    }
                }
                push is MessagingSocketPush.ReactionChange && push.groupConversationId == group.groupId -> {
                    coroutineScope.launch(Dispatchers.Main) {
                        messages = messages?.map { if (it.id == push.messageId) it.copy(reactions = push.reactions) else it }
                    }
                }
                push is MessagingSocketPush.TypingChange && push.groupConversationId == group.groupId -> {
                    coroutineScope.launch(Dispatchers.Main) {
                        typingUserIds[push.userId]?.cancel()
                        val clearJob = coroutineScope.launch {
                            delay(3000)
                            typingUserIds = typingUserIds - push.userId
                        }
                        typingUserIds = typingUserIds + (push.userId to clearJob)
                    }
                }
            }
        }
        socket = ws
        onDispose {
            ws.close(1000, "leaving group thread")
            socket = null
            typingUserIds.values.forEach { it.cancel() }
        }
    }
    LaunchedEffect(messages?.size) {
        val count = messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BackTopBar(group.name, onBack)
            Row {
                IconButton(onClick = { showManageMembers = true }) {
                    Icon(Icons.Outlined.Group, contentDescription = "Manage members")
                }
                IconButton(onClick = { showSplitBills = true }) {
                    Icon(Icons.Outlined.Receipt, contentDescription = "Split a bill")
                }
            }
        }
        if (showSplitBills) {
            GroupSplitBillsView(groupConversationId = group.groupId, members = members, currentUserId = currentUserId, onBack = { showSplitBills = false })
            return@Column
        }
        if (showManageMembers) {
            GroupManageMembersView(
                group = group,
                members = members,
                currentUserId = currentUserId,
                onMembersChanged = { coroutineScope.launch {
                    try {
                        val res = NetworkClient.apiService.getGroupMembers(group.groupId)
                        if (res.success) members = res.members
                    } catch (_: Exception) { }
                } },
                onLeft = { showManageMembers = false; onBack() },
                onBack = { showManageMembers = false },
            )
            return@Column
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val msgs = messages
            if (msgs == null) {
                item { SkeletonBlock(height = 72.dp) }
            } else if (msgs.isEmpty()) {
                item { Text("Say hello — no messages yet.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else {
                // Real, honest limitation: bubbles show a truncated sender id, not a
                // real display name -- no "list group members" endpoint exists yet to
                // resolve names client-side, matching bank-mfe's own known gap.
                items(msgs, key = { it.id }) { m ->
                    val senderName = members.find { it.userId == m.senderId }?.name ?: m.senderId.take(8)
                    GroupMessageBubble(
                        m,
                        isMine = m.senderId == currentUserId,
                        senderName = senderName,
                        currentUserId = currentUserId,
                        onToggleReaction = { emoji ->
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.toggleGroupReaction(m.id, ToggleReactionRequest(emoji))
                                    if (res.success) messages = messages?.map { if (it.id == m.id) it.copy(reactions = res.reactions) else it }
                                } catch (_: Exception) {
                                    // Best-effort -- a failed toggle just leaves the badge as it was.
                                }
                            }
                        },
                        onReply = { replyingTo = it },
                        onDelete = { messageId -> coroutineScope.launch {
                            try { NetworkClient.apiService.deleteGroupMessage(group.groupId, messageId); refresh() }
                            catch (_: Exception) { error = "Couldn't delete this message." }
                        } },
                    )
                }
            }
        }
        if (typingUserIds.isNotEmpty()) {
            val names = typingUserIds.keys.map { id -> members.find { it.userId == id }?.name ?: id.take(8) }
            Text(
                "${names.joinToString(", ")} ${if (names.size == 1) "is" else "are"} typing…",
                color = Ids.colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        replyingTo?.let { reply ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Replying to: ${reply.body.take(80)}", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { replyingTo = null }) { Text("×") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { newValue ->
                    draft = newValue
                    val now = System.currentTimeMillis()
                    if (now - lastTypingSentAt > 2000) {
                        lastTypingSentAt = now
                        socket?.let { NetworkClient.sendTyping(it, groupConversationId = group.groupId) }
                    }
                },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(Ids.layout.minTouchTarget)
                    .clip(CircleShape)
                    .background(if (draft.isBlank() || sending) Ids.colors.textTertiary else Ids.colors.brand)
                    .clickable(enabled = draft.isNotBlank() && !sending) {
                        val body = draft.trim()
                        sending = true
                        error = null
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.sendGroupMessage(group.groupId, SendGroupMessageRequest(body, replyingTo?.id))
                                if (res.success) {
                                    draft = ""
                                    replyingTo = null
                                    messages = (messages ?: emptyList()) + res.message
                                }
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                sending = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// Real KakaoPay-style split bill (2026-07-22) -- found fully built on the backend
// (rw.itunda.splitbill) with zero client UI anywhere, despite group chat itself
// being fully wired. A flat, even split among picked group members (excluding the
// organizer); each participant pays their own share directly to the organizer via a
// real wallet-to-wallet push, no escrow -- see SplitBill.kt's own doc comment.
@Composable
private fun GroupSplitBillsView(
    groupConversationId: String,
    members: List<GroupMemberDto>,
    currentUserId: String?,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var splitBills by remember { mutableStateOf<List<SplitBillWithParticipants>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var showNewForm by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var selectedParticipantIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            splitBills = NetworkClient.apiService.getSplitBillsForGroup(groupConversationId).splitBills
            error = null
        } catch (_: Exception) {
            error = "Could not load split bills."
        }
    }
    LaunchedEffect(groupConversationId) { refresh() }

    val otherMembers = members.filter { it.userId != currentUserId }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar("Split bills", onBack)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 13.sp) } }
            item {
                if (!showNewForm) {
                    Button(onClick = { showNewForm = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Split a bill")
                    }
                } else {
                    Column {
                        OutlinedTextField(amountText, { amountText = it }, label = { Text("Total amount (RWF)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(descriptionText, { descriptionText = it }, label = { Text("What was it for?") }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Split with", fontSize = 13.sp, color = Ids.colors.textSecondary)
                        otherMembers.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selectedParticipantIds = if (member.userId in selectedParticipantIds) {
                                        selectedParticipantIds - member.userId
                                    } else {
                                        selectedParticipantIds + member.userId
                                    }
                                }.padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(member.name, fontSize = 14.sp)
                                Text(if (member.userId in selectedParticipantIds) "Selected" else "Tap to add", fontSize = 12.sp, color = Ids.colors.textSecondary)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            enabled = busyId == null && amountText.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true &&
                                descriptionText.isNotBlank() && selectedParticipantIds.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val amount = amountText.toBigDecimalOrNull() ?: return@Button
                                busyId = "new"
                                coroutineScope.launch {
                                    try {
                                        NetworkClient.apiService.createSplitBill(
                                            groupConversationId,
                                            UUID.randomUUID().toString(),
                                            CreateSplitBillRequest(amount, descriptionText, selectedParticipantIds.toList()),
                                        )
                                        amountText = ""; descriptionText = ""; selectedParticipantIds = emptySet(); showNewForm = false
                                        refresh()
                                    } catch (_: Exception) {
                                        error = "That split bill could not be created."
                                    } finally { busyId = null }
                                }
                            },
                        ) { Text(if (busyId == "new") "Creating…" else "Create split bill") }
                    }
                }
            }
            val current = splitBills
            if (current == null) item { SkeletonBlock() }
            else if (current.isEmpty()) item { Text("No split bills in this group yet.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            else items(current, key = { it.splitBill.id }) { entry ->
                val myShare = entry.participants.find { it.userId == currentUserId }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(entry.splitBill.description, fontWeight = FontWeight.SemiBold)
                        Text("Total RWF ${entry.splitBill.totalAmount} · ${entry.splitBill.status}", fontSize = 13.sp, color = Ids.colors.textSecondary)
                        entry.participants.forEach { participant ->
                            val name = members.find { it.userId == participant.userId }?.name ?: participant.userId.take(8)
                            Text("$name: RWF ${participant.shareAmount} (${participant.status})", fontSize = 13.sp)
                        }
                        if (myShare != null && myShare.status == "PENDING") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                enabled = busyId == null,
                                onClick = {
                                    busyId = entry.splitBill.id
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.paySplitBillShare(entry.splitBill.id, UUID.randomUUID().toString())
                                            refresh()
                                        } catch (_: Exception) {
                                            error = "That payment could not be completed."
                                        } finally { busyId = null }
                                    }
                                },
                            ) { Text(if (busyId == entry.splitBill.id) "Paying…" else "Pay my share (RWF ${myShare.shareAmount})") }
                        }
                    }
                }
            }
        }
    }
}

// Real leave-group/add-member (2026-07-22) -- found fully built on the backend
// (GroupMessagingController's POST/DELETE .../members) with zero client UI anywhere,
// despite group chat itself being fully wired. Add-member picks from the caller's
// real Talk contacts (GET /api/v1/messages/contacts), same list DirectMessagesList
// already uses to start a 1:1 chat, filtered to exclude people already in the group.
@Composable
private fun GroupManageMembersView(
    group: GroupSummaryDto,
    members: List<GroupMemberDto>,
    currentUserId: String?,
    onMembersChanged: () -> Unit,
    onLeft: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var contacts by remember { mutableStateOf<List<TalkContactDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyUserId by remember { mutableStateOf<String?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try { contacts = NetworkClient.apiService.getTalkContacts().contacts } catch (_: Exception) { }
    }

    val addableContacts = contacts.filter { contact -> members.none { it.userId == contact.userId } }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar("Manage members", onBack)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 13.sp) } }
            item { Text("Members (${members.size})", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            items(members, key = { it.userId }) { member ->
                Text(if (member.userId == currentUserId) "${member.name} (you)" else member.name, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            item {
                Button(
                    enabled = !leaving,
                    onClick = {
                        leaving = true
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.leaveGroup(group.groupId)
                                onLeft()
                            } catch (_: Exception) {
                                error = "Could not leave this group."
                                leaving = false
                            }
                        }
                    },
                ) { Text(if (leaving) "Leaving…" else "Leave group") }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { Text("Add from your contacts", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            if (addableContacts.isEmpty()) {
                item { Text("No contacts left to add.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else {
                items(addableContacts, key = { it.userId }) { contact ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(contact.name, fontSize = 14.sp)
                        Button(
                            enabled = busyUserId == null,
                            onClick = {
                                busyUserId = contact.userId
                                coroutineScope.launch {
                                    try {
                                        NetworkClient.apiService.addGroupMember(group.groupId, AddGroupMemberRequest(contact.userId))
                                        onMembersChanged()
                                    } catch (_: Exception) {
                                        error = "Could not add ${contact.name}."
                                    } finally { busyUserId = null }
                                }
                            },
                        ) { Text(if (busyUserId == contact.userId) "Adding…" else "Add") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(
    message: GroupMessageDto, isMine: Boolean, senderName: String, currentUserId: String?, onToggleReaction: (String) -> Unit, onDelete: (String) -> Unit = {}, onReply: (GroupMessageDto) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (!isMine) {
                    Text(senderName, color = Ids.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                }
                Text(message.body, color = if (isMine) Color.White else Ids.colors.textPrimary, fontSize = 14.sp)
            }
        }
        MessageReactionsRow(message.reactions, currentUserId, isMine, onToggleReaction)
        TextButton(onClick = { onReply(message) }) { Text("Reply", color = Ids.colors.textSecondary, fontSize = 11.sp) }
        if (isMine && message.deletedAt == null) TextButton(onClick = { onDelete(message.id) }) { Text("Delete", color = Ids.colors.textSecondary, fontSize = 11.sp) }
        Text(
            chatMessageTime(message.sentAt),
            color = Ids.colors.textSecondary,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            textAlign = if (isMine) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
        )
    }
}

@Composable
// Real structural fix, 2026-07-21: this row used to be shaped like this app's own
// banking/dashboard cards (rounded, backgrounded, individually spaced) -- the wrong
// reference for a chat list. A messaging list is scanned quickly and often, at real
// volume, unlike a dashboard someone glances at occasionally -- KakaoTalk's own real,
// sourced friend-list-row spec (48px rounded-square avatar/12px radius, 16px/14px
// text, 64dp fixed row height, 16dp horizontal padding) optimizes specifically for
// that fast, repeated scanning, which this app's chat list needs just as much as the
// real KakaoTalk does. Brand color is deliberately NOT changed to match (unread
// badge stays itunda's own blue, not Kakao yellow) -- a real, connected ecosystem
// like Kakao's own keeps one consistent brand color across every surface; only the
// row's real, sourced *structure* is worth borrowing here, not its color.
private fun ConversationRow(conversation: ConversationSummaryDto, online: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.BottomEnd) {
            Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ids.colors.brand)
            }
            if (online) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Ids.colors.success),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(conversation.otherUserName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(conversation.lastMessagePreview ?: "No messages yet", color = Ids.colors.textSecondary, fontSize = 14.sp, maxLines = 1)
        }
        if (conversation.unreadCount > 0) {
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(conversation.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ChatThreadView(
    conversation: ConversationSummaryDto,
    onBack: () -> Unit,
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
) {
    // Real system-back interception (2026-07-18, found live on-device: pressing back
    // here fell through to the Activity's default back behavior and exited the app
    // instead of returning to the conversation list) -- matches the BackHandler
    // convention every other multi-step flow in ItundaAppScreen.kt already uses
    // (TransferStep/SavingsFlowStep/showSettings/etc).
    BackHandler(onBack = onBack)
    var messages by remember { mutableStateOf<List<MessageDto>?>(null) }
    var offersByMessageId by remember { mutableStateOf<Map<String, OfferBubbleData>>(emptyMap()) }
    var giftsByMessageId by remember { mutableStateOf<Map<String, GiftDto>>(emptyMap()) }
    var draft by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<MessageDto?>(null) }
    var pinnedMessage by remember { mutableStateOf<MessageDto?>(null) }
    var updatingPin by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var blockConfirmationOpen by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    var isBlocked by remember { mutableStateOf(false) }
    var quiet by remember { mutableStateOf(false) }
    var updatingQuiet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MessageDto>?>(null) }
    var searching by remember { mutableStateOf(false) }
    // Real device binding step-up (2026-07-21) -- Gift send/claim was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
    // showed only a generic error, same fix already applied to Transfer/Savings/
    // Interest via MainViewModel.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    var pendingDeviceRetry by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var otherOnline by remember { mutableStateOf<Boolean?>(null) }
    var otherTyping by remember { mutableStateOf(false) }
    var giftComposerOpen by remember { mutableStateOf(false) }
    var giftAmount by remember { mutableStateOf("") }
    var giftNote by remember { mutableStateOf("") }
    var sendingGift by remember { mutableStateOf(false) }
    var typingClearJob by remember { mutableStateOf<Job?>(null) }
    var socket by remember { mutableStateOf<WebSocket?>(null) }
    var lastTypingSentAt by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    val listState: LazyListState = rememberLazyListState()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }

    LaunchedEffect(conversation.otherUserId) {
        try {
            val res = NetworkClient.apiService.getPresence(listOf(conversation.otherUserId))
            if (res.success) otherOnline = res.presence[conversation.otherUserId]
        } catch (e: Exception) { /* real, non-critical -- only backs the header subtitle */ }
    }
    LaunchedEffect(conversation.conversationId) {
        try { quiet = NetworkClient.apiService.getConversationQuiet(conversation.conversationId).quiet } catch (_: Exception) { }
        try { pinnedMessage = NetworkClient.apiService.getPinnedConversationMessage(conversation.conversationId).message } catch (_: Exception) { }
    }

    // Real-fetches both Marketplace and Real Estate offer history for this conversation
    // -- a given real conversation only ever carries one type in practice, but fetching
    // both is cheap and correct rather than guessing which one applies (mirrors
    // bank-mfe's own ConversationThread.loadOffers).
    suspend fun loadOffers() {
        val marketplaceOffers: List<PriceOfferDto> = try {
            NetworkClient.apiService.getOffersForConversation(conversation.conversationId).offers
        } catch (_: Exception) {
            emptyList()
        }
        val propertyOffers: List<PropertyPriceOfferDto> = try {
            NetworkClient.apiService.getPropertyOffersForConversation(conversation.conversationId).offers
        } catch (_: Exception) {
            emptyList()
        }
        offersByMessageId = (marketplaceOffers.map { it.toBubbleData() to it.messageId } + propertyOffers.map { it.toBubbleData() to it.messageId })
            .associate { (data, messageId) -> messageId to data }
    }

    suspend fun loadGifts() {
        try {
            val res = NetworkClient.apiService.getGiftsForConversation(conversation.conversationId)
            if (res.success) giftsByMessageId = res.gifts.associateBy { it.messageId }
        } catch (_: Exception) {
            // Real, non-critical -- only backs the inline gift bubble.
        }
    }

    suspend fun refresh() {
        try {
            val res = NetworkClient.apiService.getMessages(conversation.conversationId)
            if (res.success) messages = res.messages.reversed()
        } catch (_: Exception) {
            // Keep showing the last-known messages rather than blanking the thread
            // on a transient poll failure.
        }
        loadOffers()
        loadGifts()
    }

    // Real poll, kept as an always-correct fallback delivery path alongside the real
    // WebSocket push below -- matches bank-mfe's own ConversationThread exactly (poll
    // interval unchanged, push appended live on top).
    LaunchedEffect(conversation.conversationId) {
        while (true) {
            refresh()
            delay(4000)
        }
    }
    // Real WebSocket live-transport (2026-07-18) -- see NetworkClient.connectMessagingSocket's
    // own doc comment. Pushed messages are de-duped by id against whatever the poll
    // already fetched, and appended on the main thread since OkHttp's listener callback
    // runs on its own background thread, not safe to mutate Compose state from directly.
    DisposableEffect(conversation.conversationId) {
        val ws = NetworkClient.connectMessagingSocket { push ->
            when {
                push is MessagingSocketPush.DirectMessage && push.message.conversationId == conversation.conversationId -> {
                    coroutineScope.launch(Dispatchers.Main) {
                        otherTyping = false
                        val current = messages ?: emptyList()
                        if (current.none { it.id == push.message.id }) {
                            messages = current + push.message
                        }
                        // A pushed message might be a real offer/counter/accept/reject --
                        // refresh so it renders as an offer bubble immediately.
                        loadOffers()
                        loadGifts()
                    }
                }
                push is MessagingSocketPush.PresenceChange && push.userId == conversation.otherUserId -> {
                    coroutineScope.launch(Dispatchers.Main) { otherOnline = push.online }
                }
                push is MessagingSocketPush.ReactionChange && push.conversationId == conversation.conversationId -> {
                    coroutineScope.launch(Dispatchers.Main) {
                        messages = messages?.map { if (it.id == push.messageId) it.copy(reactions = push.reactions) else it }
                    }
                }
                push is MessagingSocketPush.TypingChange && push.conversationId == conversation.conversationId && push.userId == conversation.otherUserId -> {
                    coroutineScope.launch(Dispatchers.Main) {
                        otherTyping = true
                        typingClearJob?.cancel()
                        typingClearJob = coroutineScope.launch {
                            delay(3000)
                            otherTyping = false
                        }
                    }
                }
            }
        }
        socket = ws
        onDispose {
            ws.close(1000, "leaving chat thread")
            socket = null
            typingClearJob?.cancel()
        }
    }
    LaunchedEffect(messages?.size) {
        val count = messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar(conversation.otherUserName, onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { blockConfirmationOpen = true }, enabled = !blocking && !isBlocked) {
                Text(if (isBlocked) "Blocked" else if (blocking) "Blocking…" else "Block", color = Ids.colors.danger)
            }
            TextButton(onClick = {
                updatingQuiet = true
                coroutineScope.launch {
                    try { quiet = NetworkClient.apiService.setConversationQuiet(conversation.conversationId, SetConversationQuietRequest(!quiet)).quiet }
                    catch (_: IOException) { error = "Couldn't update this quiet room. Check your connection and try again." }
                    finally { updatingQuiet = false }
                }
            }, enabled = !updatingQuiet) { Text(if (updatingQuiet) "…" else if (quiet) "Resume alerts" else "Quiet room", color = Ids.colors.textSecondary) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it; if (it.isBlank()) searchResults = null }, placeholder = { Text("Search this conversation") }, singleLine = true, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                val query = searchQuery.trim(); if (query.length < 2) { error = "Enter at least 2 characters to search."; return@TextButton }
                searching = true
                coroutineScope.launch {
                    try { searchResults = NetworkClient.apiService.searchMessages(conversation.conversationId, query).messages }
                    catch (e: HttpException) { error = superAppErrorMessage(e) }
                    catch (_: IOException) { error = "Couldn't search this conversation. Check your connection and try again." }
                    finally { searching = false }
                }
            }, enabled = !searching) { Text(if (searching) "…" else "Search") }
        }
        searchResults?.let { Text("${it.size} matching message${if (it.size == 1) "" else "s"}", color = Ids.colors.textSecondary, fontSize = 12.sp) }
        pinnedMessage?.let { pinned ->
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("📌 ${pinned.body}", color = Ids.colors.textPrimary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    updatingPin = true
                    coroutineScope.launch { try { NetworkClient.apiService.unpinConversationMessage(conversation.conversationId); pinnedMessage = null } catch (_: Exception) { error = "Couldn't unpin this message." } finally { updatingPin = false } }
                }, enabled = !updatingPin) { Text("Unpin", fontSize = 11.sp) }
            }
        }
        otherOnline?.let { online ->
            Text(
                if (online) "Online" else "Offline",
                color = if (online) Ids.colors.success else Ids.colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val msgs = searchResults ?: messages
            if (msgs == null) {
                item { SkeletonBlock(height = 72.dp) }
            } else if (msgs.isEmpty()) {
                item { Text("Say hello — no messages yet.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else {
                items(msgs, key = { it.id }) { m ->
                    MessageBubble(
                        m,
                        isMine = m.senderId == currentUserId,
                        currentUserId = currentUserId,
                        offer = offersByMessageId[m.id],
                        gift = giftsByMessageId[m.id],
                        onReply = { replyingTo = it },
                        onDelete = { messageId -> coroutineScope.launch {
                            try { NetworkClient.apiService.deleteMessage(conversation.conversationId, messageId); refresh() }
                            catch (_: Exception) { error = "Couldn't delete this message." }
                        } },
                        onPin = { message ->
                            updatingPin = true
                            coroutineScope.launch { try { NetworkClient.apiService.pinConversationMessage(conversation.conversationId, message.id); pinnedMessage = message } catch (_: Exception) { error = "Couldn't pin this message." } finally { updatingPin = false } }
                        },
                        onClaimGift = { giftId ->
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.claimGift(giftId, UUID.randomUUID().toString())
                                    loadGifts()
                                } catch (e: HttpException) {
                                    if (isDeviceNotVerifiedError(e)) {
                                        pendingDeviceRetry = {
                                            try { NetworkClient.apiService.claimGift(giftId, UUID.randomUUID().toString()); loadGifts() }
                                            catch (e2: HttpException) { error = superAppErrorMessage(e2) }
                                            catch (_: IOException) { error = "Couldn't reach itunda. Check your connection and try again." }
                                        }
                                        needsDeviceVerification = true
                                    } else {
                                        error = superAppErrorMessage(e)
                                    }
                                } catch (_: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                }
                            }
                        },
                        onToggleReaction = { emoji ->
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.toggleReaction(m.id, ToggleReactionRequest(emoji))
                                    if (res.success) messages = messages?.map { if (it.id == m.id) it.copy(reactions = res.reactions) else it }
                                } catch (_: Exception) {
                                    // Best-effort -- a failed toggle just leaves the badge as it was.
                                }
                            }
                        },
                        onRespondToOffer = { offerId, action, counterAmount ->
                            coroutineScope.launch {
                                try {
                                    // Real offer ids are stably prefixed by their real owning
                                    // service ("price_offer_"/"property_offer_") -- a reliable
                                    // dispatch key, matching bank-mfe's own ConversationThread.
                                    if (offerId.startsWith("property_offer_")) {
                                        NetworkClient.apiService.respondToPropertyOffer(offerId, RespondToPropertyOfferRequest(action, counterAmount))
                                    } else {
                                        NetworkClient.apiService.respondToOffer(offerId, RespondToOfferRequest(action, counterAmount))
                                    }
                                    loadOffers()
                                    refresh()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (_: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                }
                            }
                        },
                        onReportMessage = { messageId, reason ->
                            coroutineScope.launch {
                                try { NetworkClient.apiService.reportChatMessage(CreateChatReportRequest(messageId, reason)); error = "Thanks. Your report was sent for review." }
                                catch (e: HttpException) { error = superAppErrorMessage(e) }
                                catch (_: IOException) { error = "Couldn't reach itunda. Check your connection and try again." }
                            }
                        },
                    )
                }
            }
        }
        if (otherTyping) {
            Text("${conversation.otherUserName} is typing…", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 4.dp))
        }
        deviceStepUpHost(
            needsDeviceVerification,
            { needsDeviceVerification = false; pendingDeviceRetry = null },
            {
                needsDeviceVerification = false
                val retry = pendingDeviceRetry
                pendingDeviceRetry = null
                retry?.invoke()
            },
        )
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        if (blockConfirmationOpen) {
            AlertDialog(
                onDismissRequest = { blockConfirmationOpen = false },
                title = { Text("Block ${conversation.otherUserName}?") },
                text = { Text("They will no longer be able to message you. You can unblock them later from this conversation.") },
                confirmButton = {
                    TextButton(onClick = {
                        blockConfirmationOpen = false; blocking = true
                        coroutineScope.launch {
                            try { NetworkClient.apiService.blockConversationParticipant(conversation.conversationId); isBlocked = true; error = "${conversation.otherUserName} is blocked." }
                            catch (e: HttpException) { error = superAppErrorMessage(e) }
                            catch (_: IOException) { error = "Couldn't reach itunda. Check your connection and try again." }
                            finally { blocking = false }
                        }
                    }) { Text("Block", color = Ids.colors.danger) }
                },
                dismissButton = { TextButton(onClick = { blockConfirmationOpen = false }) { Text("Cancel") } },
            )
        }
        if (giftComposerOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Ids.colors.surfaceSoft)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🎁 Send a gift", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Ids.colors.textPrimary)
                OutlinedTextField(
                    value = giftAmount,
                    onValueChange = { giftAmount = it },
                    placeholder = { Text("Amount (RWF)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = giftNote,
                    onValueChange = { if (it.length <= 200) giftNote = it },
                    placeholder = { Text("Add a note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val amountValue = giftAmount.toDoubleOrNull()
                    OfferActionButton(if (sendingGift) "Sending…" else "Send gift") {
                        if (amountValue == null || amountValue <= 0 || sendingGift) return@OfferActionButton
                        sendingGift = true
                        error = null
                        needsDeviceVerification = false
                        val sendGift: suspend () -> Unit = {
                            NetworkClient.apiService.sendGiftInConversation(
                                conversation.conversationId,
                                UUID.randomUUID().toString(),
                                SendGiftInConversationRequest(amountValue, giftNote.trim().ifBlank { null }),
                            )
                            giftAmount = ""
                            giftNote = ""
                            giftComposerOpen = false
                            refresh()
                        }
                        coroutineScope.launch {
                            try {
                                sendGift()
                            } catch (e: HttpException) {
                                if (isDeviceNotVerifiedError(e)) {
                                    pendingDeviceRetry = sendGift
                                    needsDeviceVerification = true
                                } else {
                                    error = superAppErrorMessage(e)
                                }
                            } catch (_: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                sendingGift = false
                            }
                        }
                    }
                    OfferActionButton("Cancel") { giftComposerOpen = false }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        replyingTo?.let { reply ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("Replying to: ${reply.body.take(80)}", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { replyingTo = null }) { Text("×", color = Ids.colors.textSecondary) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Box(
                modifier = Modifier
                    .size(Ids.layout.minTouchTarget)
                    .clip(CircleShape)
                    .background(Ids.colors.surfaceSoft)
                    .clickable { giftComposerOpen = !giftComposerOpen },
                contentAlignment = Alignment.Center,
            ) {
                Text("🎁", fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = { newValue ->
                    draft = newValue
                    // Real typing indicator send (2026-07-19), client-throttled to match
                    // the server's own 1-per-2s rate limit.
                    val now = System.currentTimeMillis()
                    if (now - lastTypingSentAt > 2000) {
                        lastTypingSentAt = now
                        socket?.let { NetworkClient.sendTyping(it, conversationId = conversation.conversationId) }
                    }
                },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(Ids.layout.minTouchTarget)
                    .clip(CircleShape)
                    .background(if (draft.isBlank() || sending) Ids.colors.textTertiary else Ids.colors.brand)
                    .clickable(enabled = draft.isNotBlank() && !sending) {
                        val body = draft.trim()
                        sending = true
                        error = null
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.sendMessage(conversation.conversationId, SendMessageRequest(body, replyingTo?.id))
                                if (res.success) {
                                draft = ""
                                replyingTo = null
                                    messages = (messages ?: emptyList()) + res.message
                                }
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                sending = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// Real quick-react palette (2026-07-19) -- a small fixed set matching bank-mfe's own
// MessageReactions component exactly, kept simple rather than a full emoji picker.
private val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢")

@Composable
private fun MessageReactionsRow(reactions: List<ReactionGroupDto>, currentUserId: String?, isMine: Boolean, onToggle: (String) -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        reactions.filter { it.userIds.isNotEmpty() }.forEach { r ->
            val mine = currentUserId != null && r.userIds.contains(currentUserId)
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (mine) Ids.colors.brand.copy(alpha = 0.15f) else Ids.colors.surfaceSoft)
                    .clickable { onToggle(r.emoji) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("${r.emoji} ${r.userIds.size}", fontSize = 11.sp, color = Ids.colors.textSecondary)
            }
        }
        Box {
            Icon(
                Icons.Outlined.AddReaction,
                contentDescription = "Add reaction",
                tint = Ids.colors.textSecondary,
                modifier = Modifier.size(16.dp).clip(CircleShape).clickable { pickerOpen = !pickerOpen },
            )
            if (pickerOpen) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ids.colors.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        Text(
                            emoji,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(2.dp).clickable {
                                onToggle(emoji)
                                pickerOpen = false
                            },
                        )
                    }
                }
            }
        }
    }
}

// Real 당근-style offer bubble (2026-07-19) -- see PriceOfferService's own doc comment.
// Renders inline wherever a message carries a real offer, replacing the plain-text
// bubble with amount + status + real Accept/Decline/Counter actions (only shown to
// whichever participant did NOT propose the current pending amount).
// Real minimal shape both PriceOfferDto (Marketplace) and PropertyPriceOfferDto (Real
// Estate) get mapped into for display -- narrowed to just the fields OfferBubble
// actually reads (id/amount/status/proposedByUserId), so this one component renders
// both offer types without duplication. Mirrors bank-mfe's own OfferBubbleData
// narrowing (2026-07-19).
private data class OfferBubbleData(val id: String, val amount: Double, val status: String, val proposedByUserId: String)
private fun PriceOfferDto.toBubbleData() = OfferBubbleData(id, amount, status, proposedByUserId)
private fun PropertyPriceOfferDto.toBubbleData() = OfferBubbleData(id, amount, status, proposedByUserId)

@Composable
private fun OfferBubble(offer: OfferBubbleData, isMine: Boolean, currentUserId: String?, onRespond: (String, String, Double?) -> Unit) {
    var countering by remember { mutableStateOf(false) }
    var counterAmount by remember { mutableStateOf("") }
    val canRespond = offer.status == "PENDING" && currentUserId != null && currentUserId != offer.proposedByUserId
    val statusLabel = when (offer.status) {
        "PENDING" -> "Pending"; "ACCEPTED" -> "Accepted"; "REJECTED" -> "Declined"; "COUNTERED" -> "Countered"
        else -> offer.status
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("💰 %,.0f RWF".format(offer.amount), color = if (isMine) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(statusLabel, color = if (isMine) Color.White.copy(alpha = 0.85f) else Ids.colors.textSecondary, fontSize = 12.sp)
            if (canRespond && !countering) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OfferActionButton("Accept") { onRespond(offer.id, "ACCEPT", null) }
                    OfferActionButton("Decline") { onRespond(offer.id, "REJECT", null) }
                    OfferActionButton("Counter") { countering = true }
                }
            }
            if (canRespond && countering) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = counterAmount,
                        onValueChange = { counterAmount = it },
                        placeholder = { Text("Counter (RWF)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    OfferActionButton("Send") {
                        val amount = counterAmount.toDoubleOrNull() ?: return@OfferActionButton
                        countering = false
                        counterAmount = ""
                        onRespond(offer.id, "COUNTER", amount)
                    }
                }
            }
        }
    }
}

@Composable
private fun OfferActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Ids.colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
    }
}

// Real KakaoTalk-style gift bubble (2026-07-20) -- see GiftService's own doc comment.
// Renders inline wherever a message carries a real gift, with a real Open/Claim button
// shown only to the recipient of a still-PENDING, not-yet-expired gift.
@Composable
private fun GiftBubble(gift: GiftDto, isMine: Boolean, currentUserId: String?, onClaim: (String) -> Unit) {
    val canClaim = gift.status == "PENDING" && currentUserId == gift.recipientId &&
        runCatching { Instant.parse(gift.expiresAt).isAfter(Instant.now()) }.getOrDefault(true)
    val statusLabel = when (gift.status) {
        "PENDING" -> if (isMine) "Waiting to be opened" else "Tap to open"
        "CLAIMED" -> "Opened"
        "EXPIRED" -> "Expired — refunded"
        else -> gift.status
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🎁 %,.0f RWF".format(gift.amount), color = if (isMine) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            gift.note?.let { Text("\"$it\"", color = if (isMine) Color.White.copy(alpha = 0.9f) else Ids.colors.textSecondary, fontSize = 12.sp) }
            Text(statusLabel, color = if (isMine) Color.White.copy(alpha = 0.85f) else Ids.colors.textSecondary, fontSize = 12.sp)
            if (canClaim) {
                OfferActionButton("Open gift") { onClaim(gift.id) }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageDto, isMine: Boolean, currentUserId: String?, offer: OfferBubbleData?, gift: GiftDto?,
    onToggleReaction: (String) -> Unit, onRespondToOffer: (String, String, Double?) -> Unit, onClaimGift: (String) -> Unit,
    onReply: (MessageDto) -> Unit = {},
    onDelete: (String) -> Unit = {},
    onPin: (MessageDto) -> Unit = {},
    onReportMessage: (String, String) -> Unit = { _, _ -> },
) {
    var reportOpen by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
            if (gift != null) {
                GiftBubble(gift, isMine, currentUserId, onClaimGift)
            } else if (offer != null) {
                OfferBubble(offer, isMine, currentUserId, onRespondToOffer)
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(message.body, color = if (isMine) Color.White else Ids.colors.textPrimary, fontSize = 14.sp)
                }
            }
        }
        MessageReactionsRow(message.reactions, currentUserId, isMine, onToggleReaction)
        TextButton(onClick = { onReply(message) }) { Text("Reply", color = Ids.colors.textSecondary, fontSize = 11.sp) }
        if (isMine && message.deletedAt == null) TextButton(onClick = { onDelete(message.id) }) { Text("Delete", color = Ids.colors.textSecondary, fontSize = 11.sp) }
        TextButton(onClick = { onPin(message) }) { Text("Pin", color = Ids.colors.textSecondary, fontSize = 11.sp) }
        Text(
            "${if (isMine && message.readAt == null) "1 · " else ""}${chatMessageTime(message.sentAt)}",
            color = Ids.colors.textSecondary,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            textAlign = if (isMine) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
        )
        if (!isMine) {
            TextButton(onClick = { reportOpen = true }) { Text("Report message", color = Ids.colors.textSecondary, fontSize = 11.sp) }
        }
    }
    if (reportOpen) AlertDialog(
        onDismissRequest = { reportOpen = false },
        title = { Text("Report message") },
        text = { OutlinedTextField(value = reportReason, onValueChange = { if (it.length <= 180) reportReason = it }, label = { Text("Reason") }) },
        confirmButton = { TextButton(onClick = { if (reportReason.trim().length >= 3) { onReportMessage(message.id, reportReason.trim()); reportReason = ""; reportOpen = false } }) { Text("Send") } },
        dismissButton = { TextButton(onClick = { reportOpen = false }) { Text("Cancel") } },
    )
}

// ============================== HOOD (Marketplace) ==============================
