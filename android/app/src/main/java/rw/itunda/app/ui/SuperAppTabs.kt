package rw.itunda.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.app.network.ConversationSummaryDto
import rw.itunda.app.network.CreateListingRequest
import rw.itunda.app.network.EatsOrderDto
import rw.itunda.app.network.AddressSuggestionDto
import rw.itunda.app.network.CreateGroupRequest
import rw.itunda.app.network.EatsRatingResponse
import rw.itunda.app.network.GroupMemberDto
import rw.itunda.app.network.GroupMessageDto
import rw.itunda.app.network.GroupSummaryDto
import rw.itunda.app.network.MessagingSocketPush
import rw.itunda.app.network.SendGroupMessageRequest
import rw.itunda.app.network.SubmitEatsReviewRequest
import rw.itunda.app.network.EatsOrderItemRequest
import rw.itunda.app.network.ListingDto
import rw.itunda.app.network.MerchantProductDto
import rw.itunda.app.network.MessageDto
import rw.itunda.app.network.NetworkClient
import rw.itunda.app.network.OrderDto
import rw.itunda.app.network.OrderItemRequest
import rw.itunda.app.network.PlaceEatsOrderRequest
import rw.itunda.app.network.PlaceOrderRequest
import rw.itunda.app.network.RiderDto
import rw.itunda.app.network.SendMessageRequest
import rw.itunda.app.network.SetRiderAvailabilityRequest
import rw.itunda.app.network.ShoppingMerchantDto
import rw.itunda.app.network.StartConversationRequest
import rw.itunda.app.network.TokenStore
import rw.itunda.app.network.UpdateEatsOrderStatusRequest
import rw.itunda.core.designsystem.theme.Tds
import java.io.IOException
import java.util.UUID

/**
 * Real Talk (Kakao-style 1:1 messaging), Hood (당근마켓-style neighborhood
 * marketplace), and Shop (Coupang-style multi-item checkout) tabs -- the mobile
 * clients for the three new "super app" phase backends (rw.itunda.messaging,
 * rw.itunda.marketplace, rw.itunda.commerce) built earlier this session, replacing
 * the old Home/Benefits/Shop/Pay/All bottom nav's Benefits/Shop/Pay tabs with
 * Shop/Hood/Talk (2026-07-18). Kept in their own file, not ItundaAppScreen.kt, since
 * that file was already large before these three real, full features were added.
 *
 * Deliberately local Composable state (`remember`, refetched on every tab entry via
 * `LaunchedEffect(Unit)`), not new MainViewModel StateFlows -- matches the existing
 * on-demand-fetch precedent SettingsScreen/loadSettingsData() already established,
 * rather than bloating MainViewModel's single shared fetchData() with three new
 * unrelated feature areas. Refetching on every tab entry is also the *correct* UX for
 * a chat/marketplace/shop surface (freshness matters more here than for Home's mostly-
 * static balance), not merely a shortcut.
 */

private fun superAppErrorMessage(e: HttpException): String = when (e.code()) {
    401, 403 -> "You don't have access to do that."
    404 -> "That couldn't be found."
    409 -> "That's already been done, or is being processed."
    422 -> "Insufficient funds for this order."
    429 -> "Too many attempts -- please wait a moment and try again."
    else -> "Something went wrong. Please try again."
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Tds.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = TossCard),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(message, color = Tds.colors.danger, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Retry", color = TossBlue, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onRetry))
        }
    }
}

@Composable
private fun TabHeader(title: String) {
    Text(title, color = TossText, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
}

// ============================== TALK (Messaging) ==============================

private enum class TalkView { DIRECT, GROUPS }

// Real group chat (2026-07-18) -- itunda's own KakaoTalk-style group messaging, ported
// to Android from bank-mfe's own Direct/Groups toggle (the "single most defining
// KakaoTalk capability" this session's own project memory names). TalkTab is now a
// thin Direct/Groups toggle wrapper; DirectMessagesList holds the exact same 1:1 logic
// this composable used to own directly.
@Composable
internal fun TalkTab(initialConversationId: String?, onConsumedInitial: () -> Unit) {
    var view by remember { mutableStateOf(TalkView.DIRECT) }
    var conversations by remember { mutableStateOf<List<ConversationSummaryDto>?>(null) }
    var conversationsError by remember { mutableStateOf<String?>(null) }
    var openConversationId by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<GroupSummaryDto>?>(null) }
    var groupsError by remember { mutableStateOf<String?>(null) }
    var openGroupId by remember { mutableStateOf<String?>(null) }
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
        ChatThreadView(conversation = openConversation, onBack = { openConversationId = null; loadConversations() })
        return
    }
    val openGroup = groups?.find { it.groupId == openGroupId }
    if (openGroup != null) {
        GroupThreadView(group = openGroup, onBack = { openGroupId = null; loadGroups() })
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical)) {
        TabHeader("Talk")
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Tds.layout.cardGap).clip(RoundedCornerShape(12.dp)).background(TossTertiary),
        ) {
            listOf(TalkView.DIRECT to "Direct", TalkView.GROUPS to "Groups").forEach { (v, label) ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (view == v) TossBlue else Color.Transparent)
                        .clickable { view = v }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (view == v) Color.White else TossText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        if (view == TalkView.DIRECT) {
            DirectMessagesList(
                conversations = conversations,
                error = conversationsError,
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
    onRetry: () -> Unit,
    onStarted: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var startPhoneNumber by remember { mutableStateOf("") }
    var startError by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap)) {
        item {
            Card(
                shape = RoundedCornerShape(Tds.layout.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = TossCard),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("New chat", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Enter their phone number to start a conversation.", color = TossSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
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
                                .background(TossBlue)
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
                    startError?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
        if (error != null) {
            item { ErrorCard(error, onRetry = onRetry) }
        } else if (conversations == null) {
            item { Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {} }
        } else if (conversations.isEmpty()) {
            item { Text("No conversations yet.", color = TossSecondary, fontSize = 14.sp) }
        } else {
            items(conversations, key = { it.conversationId }) { c -> ConversationRow(c, onClick = { onOpen(c.conversationId) }) }
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

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap)) {
        item {
            Card(
                shape = RoundedCornerShape(Tds.layout.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = TossCard),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("New group", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("A group name and everyone's real phone number, comma-separated.", color = TossSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
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
                            .background(if (creating || name.isBlank() || phoneNumbers.isBlank()) TossTertiary else TossBlue)
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
                    createError?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
        if (error != null) {
            item { ErrorCard(error, onRetry = onRetry) }
        } else if (groups == null) {
            item { Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {} }
        } else if (groups.isEmpty()) {
            item { Text("No groups yet.", color = TossSecondary, fontSize = 14.sp) }
        } else {
            items(groups, key = { it.groupId }) { g -> GroupRow(g, onClick = { onOpen(g.groupId) }) }
        }
    }
}

@Composable
private fun GroupRow(group: GroupSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Tds.layout.cardCornerRadius)).background(TossCard).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.name, color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("${group.memberCount} members", color = TossSecondary, fontSize = 11.sp)
            }
            Text(group.lastMessagePreview ?: "No messages yet.", color = TossSecondary, fontSize = 13.sp, maxLines = 1)
        }
        if (group.unreadCount > 0) {
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TossBlue).padding(horizontal = 8.dp, vertical = 3.dp)) {
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
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
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
        val socket = NetworkClient.connectMessagingSocket { push ->
            if (push is MessagingSocketPush.GroupMessagePush && push.groupConversationId == group.groupId) {
                coroutineScope.launch(Dispatchers.Main) {
                    val current = messages ?: emptyList()
                    if (current.none { it.id == push.message.id }) {
                        messages = current + push.message
                    }
                }
            }
        }
        onDispose { socket.close(1000, "leaving group thread") }
    }
    LaunchedEffect(messages?.size) {
        val count = messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical)) {
        BackTopBar(group.name, onBack)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val msgs = messages
            if (msgs == null) {
                item { Text("Loading…", color = TossSecondary, fontSize = 13.sp) }
            } else if (msgs.isEmpty()) {
                item { Text("Say hello — no messages yet.", color = TossSecondary, fontSize = 13.sp) }
            } else {
                // Real, honest limitation: bubbles show a truncated sender id, not a
                // real display name -- no "list group members" endpoint exists yet to
                // resolve names client-side, matching bank-mfe's own known gap.
                items(msgs, key = { it.id }) { m ->
                    val senderName = members.find { it.userId == m.senderId }?.name ?: m.senderId.take(8)
                    GroupMessageBubble(m, isMine = m.senderId == currentUserId, senderName = senderName)
                }
            }
        }
        error?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(Tds.layout.minTouchTarget)
                    .clip(CircleShape)
                    .background(if (draft.isBlank() || sending) TossTertiary else TossBlue)
                    .clickable(enabled = draft.isNotBlank() && !sending) {
                        val body = draft.trim()
                        sending = true
                        error = null
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.sendGroupMessage(group.groupId, SendGroupMessageRequest(body))
                                if (res.success) {
                                    draft = ""
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

@Composable
private fun GroupMessageBubble(message: GroupMessageDto, isMine: Boolean, senderName: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isMine) TossBlue else TossCardSoft)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (!isMine) {
                Text(senderName, color = TossSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
            }
            Text(message.body, color = if (isMine) Color.White else TossText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ConversationRow(conversation: ConversationSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tds.layout.cardCornerRadius))
            .background(TossCard)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(TossCardSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp), tint = TossBlue)
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(conversation.otherUserName, color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(conversation.lastMessagePreview ?: "No messages yet", color = TossSecondary, fontSize = 12.sp, maxLines = 1)
        }
        if (conversation.unreadCount > 0) {
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(TossBlue).padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(conversation.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ChatThreadView(conversation: ConversationSummaryDto, onBack: () -> Unit) {
    // Real system-back interception (2026-07-18, found live on-device: pressing back
    // here fell through to the Activity's default back behavior and exited the app
    // instead of returning to the conversation list) -- matches the BackHandler
    // convention every other multi-step flow in ItundaAppScreen.kt already uses
    // (TransferStep/SavingsFlowStep/showSettings/etc).
    BackHandler(onBack = onBack)
    var messages by remember { mutableStateOf<List<MessageDto>?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val listState: LazyListState = rememberLazyListState()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }

    suspend fun refresh() {
        try {
            val res = NetworkClient.apiService.getMessages(conversation.conversationId)
            if (res.success) messages = res.messages.reversed()
        } catch (_: Exception) {
            // Keep showing the last-known messages rather than blanking the thread
            // on a transient poll failure.
        }
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
        val socket = NetworkClient.connectMessagingSocket { push ->
            if (push is MessagingSocketPush.DirectMessage && push.message.conversationId == conversation.conversationId) {
                coroutineScope.launch(Dispatchers.Main) {
                    val current = messages ?: emptyList()
                    if (current.none { it.id == push.message.id }) {
                        messages = current + push.message
                    }
                }
            }
        }
        onDispose { socket.close(1000, "leaving chat thread") }
    }
    LaunchedEffect(messages?.size) {
        val count = messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical)) {
        BackTopBar(conversation.otherUserName, onBack)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val msgs = messages
            if (msgs == null) {
                item { Text("Loading…", color = TossSecondary, fontSize = 13.sp) }
            } else if (msgs.isEmpty()) {
                item { Text("Say hello — no messages yet.", color = TossSecondary, fontSize = 13.sp) }
            } else {
                items(msgs, key = { it.id }) { m -> MessageBubble(m, isMine = m.senderId == currentUserId) }
            }
        }
        error?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(Tds.layout.minTouchTarget)
                    .clip(CircleShape)
                    .background(if (draft.isBlank() || sending) TossTertiary else TossBlue)
                    .clickable(enabled = draft.isNotBlank() && !sending) {
                        val body = draft.trim()
                        sending = true
                        error = null
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.sendMessage(conversation.conversationId, SendMessageRequest(body))
                                if (res.success) {
                                    draft = ""
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

@Composable
private fun MessageBubble(message: MessageDto, isMine: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isMine) TossBlue else TossCardSoft)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(message.body, color = if (isMine) Color.White else TossText, fontSize = 14.sp)
        }
    }
}

// ============================== HOOD (Marketplace) ==============================

private enum class HoodView { BROWSE, MINE }

@Composable
internal fun HoodTab(onMessageSeller: (String) -> Unit) {
    var view by remember { mutableStateOf(HoodView.BROWSE) }
    var listings by remember { mutableStateOf<List<ListingDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showNewListing by remember { mutableStateOf(false) }
    if (showNewListing) {
        BackHandler { showNewListing = false }
    }
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = remember { NetworkClient.currentTokenStore().let(TokenStore::getUserId) }

    fun load() {
        listings = null
        coroutineScope.launch {
            try {
                val res = if (view == HoodView.BROWSE) NetworkClient.apiService.browseListings() else NetworkClient.apiService.getMyListings()
                if (res.success) listings = res.listings
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(view) { load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap),
    ) {
        item { TabHeader("Hood") }
        item {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossCardSoft).padding(4.dp)) {
                listOf(HoodView.BROWSE to "Browse", HoodView.MINE to "My listings").forEach { (v, label) ->
                    val selected = v == view
                    Text(
                        label,
                        color = if (selected) Color.White else TossSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) TossBlue else Color.Transparent)
                            .clickable { view = v }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
        if (view == HoodView.MINE) {
            item {
                if (!showNewListing) {
                    Box(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TossBlue).clickable { showNewListing = true }.padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("+ List an item", color = Color.White, fontWeight = FontWeight.Bold) }
                } else {
                    NewListingForm(onCreated = { showNewListing = false; load() }, onCancel = { showNewListing = false })
                }
            }
        }
        if (error != null) {
            item { ErrorCard(error!!, onRetry = ::load) }
        } else if (listings == null) {
            item { Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {} }
        } else if (listings!!.isEmpty()) {
            item { Text(if (view == HoodView.BROWSE) "No listings yet." else "You haven't listed anything yet.", color = TossSecondary, fontSize = 14.sp) }
        } else {
            items(listings!!, key = { it.id }) { listing ->
                ListingCard(
                    listing = listing,
                    isMine = view == HoodView.MINE || listing.sellerId == currentUserId,
                    onChanged = ::load,
                    onMessageSeller = { id ->
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.contactSeller(id)
                                if (res.success) onMessageSeller(res.conversation.id)
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun NewListingForm(onCreated: () -> Unit, onCancel: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("List an item", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("What are you selling?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = description, onValueChange = { description = it }, placeholder = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = price, onValueChange = { price = it }, placeholder = { Text("Price (RWF)") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = category, onValueChange = { category = it }, placeholder = { Text("Category") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            error?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(TossBlue)
                        .clickable(enabled = !submitting) {
                            val priceValue = price.toDoubleOrNull()
                            if (title.isBlank() || description.isBlank() || category.isBlank() || priceValue == null || priceValue <= 0) {
                                error = "Fill in every field with a real price."
                                return@clickable
                            }
                            submitting = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.createListing(CreateListingRequest(title, description, priceValue, category))
                                    if (res.success) onCreated()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    submitting = false
                                }
                            }
                        }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(if (submitting) "Listing…" else "List it", color = Color.White, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun ListingCard(listing: ListingDto, isMine: Boolean, onChanged: () -> Unit, onMessageSeller: (String) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(listing.title, color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (listing.status == "SOLD") {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(TossCardSoft).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                Text("SOLD", color = TossSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(listing.category, color = TossSecondary, fontSize = 12.sp)
                }
                Text("%,.0f RWF".format(listing.price), color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Text(listing.description, color = TossSecondary, fontSize = 13.sp)
            error?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isMine) {
                    if (listing.status == "ACTIVE") {
                        ListingActionButton("Mark sold", busy) {
                            busy = true
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.markListingSold(listing.id)
                                    onChanged()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                    if (listing.status != "REMOVED") {
                        ListingActionButton("Remove", busy) {
                            busy = true
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.removeListing(listing.id)
                                    onChanged()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    }
                } else if (listing.status == "ACTIVE") {
                    ListingActionButton(if (busy) "Starting…" else "Message seller", busy, filled = true) {
                        busy = true
                        onMessageSeller(listing.id)
                        busy = false
                    }
                }
            }
        }
    }
}

@Composable
private fun ListingActionButton(label: String, disabled: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (filled) TossBlue else TossCardSoft)
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(label, color = if (filled) Color.White else TossText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ============================== SHOP (Commerce + Eats) ==============================

private enum class ShopMode { SHOP, EATS }

@Composable
internal fun ShopTab() {
    var mode by remember { mutableStateOf(ShopMode.SHOP) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tds.layout.screenHorizontal, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TossCardSoft)
                .padding(4.dp),
        ) {
            listOf(ShopMode.SHOP to "Shop", ShopMode.EATS to "Eats").forEach { (m, label) ->
                val selected = m == mode
                Text(
                    label,
                    color = if (selected) Color.White else TossSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) TossBlue else Color.Transparent)
                        .clickable { mode = m }
                        .padding(vertical = 8.dp),
                )
            }
        }
        when (mode) {
            ShopMode.SHOP -> CommerceShopContent()
            ShopMode.EATS -> EatsContent()
        }
    }
}

private enum class CommerceView { BROWSE, ORDERS }

@Composable
private fun CommerceShopContent() {
    var view by remember { mutableStateOf(CommerceView.BROWSE) }
    var merchants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMerchant by remember { mutableStateOf<ShoppingMerchantDto?>(null) }
    var products by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    val cart = remember { mutableStateMapOf<String, Int>() }
    var showCheckout by remember { mutableStateOf(false) }
    var confirmedOrder by remember { mutableStateOf<OrderDto?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadMerchants() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getShoppingMerchants()
                if (res.success) merchants = res.merchants
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { loadMerchants() }

    fun openMerchant(m: ShoppingMerchantDto) {
        selectedMerchant = m
        cart.clear()
        products = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMerchantProducts(m.merchantId)
                if (res.success) products = res.products
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    val confirmed = confirmedOrder
    if (confirmed != null) {
        OrderConfirmationView(confirmed, onDone = {
            confirmedOrder = null
            selectedMerchant = null
            products = null
            cart.clear()
            showCheckout = false
            view = CommerceView.ORDERS
        })
        return
    }

    val merchant = selectedMerchant
    if (merchant != null) {
        if (showCheckout) {
            CheckoutView(
                merchant = merchant,
                cart = cart,
                products = products.orEmpty(),
                onBack = { showCheckout = false },
                onOrderPlaced = { order -> confirmedOrder = order },
            )
        } else {
            MerchantDetailView(
                merchant = merchant,
                products = products,
                cart = cart,
                onBack = { selectedMerchant = null },
                onCheckout = { showCheckout = true },
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap),
    ) {
        item { TabHeader("Shop") }
        item {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossCardSoft).padding(4.dp)) {
                listOf(CommerceView.BROWSE to "Merchants", CommerceView.ORDERS to "My orders").forEach { (v, label) ->
                    val selected = v == view
                    Text(
                        label,
                        color = if (selected) Color.White else TossSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) TossBlue else Color.Transparent)
                            .clickable { view = v }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
        if (view == CommerceView.ORDERS) {
            item { MyCommerceOrdersView() }
        } else if (error != null) {
            item { ErrorCard(error!!, onRetry = ::loadMerchants) }
        } else if (merchants == null) {
            item { Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {} }
        } else if (merchants!!.isEmpty()) {
            item { Text("No stores registered yet.", color = TossSecondary, fontSize = 14.sp) }
        } else {
            items(merchants!!, key = { it.merchantId }) { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Tds.layout.cardCornerRadius))
                        .background(TossCard)
                        .clickable { openMerchant(m) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(TossCardSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(20.dp), tint = TossBlue)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.businessName, color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${m.cashbackRate} cashback on QR/code payments", color = TossSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun MerchantDetailView(
    merchant: ShoppingMerchantDto,
    products: List<MerchantProductDto>?,
    cart: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>,
    onBack: () -> Unit,
    onCheckout: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val cartCount = cart.values.sum()
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical)) {
        BackTopBar(merchant.businessName, onBack)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (products == null) {
                item { Text("Loading…", color = TossSecondary, fontSize = 13.sp) }
            } else if (products.isEmpty()) {
                item { Text("No products yet.", color = TossSecondary, fontSize = 13.sp) }
            } else {
                items(products, key = { it.id }) { p ->
                    val qty = cart[p.id] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Tds.layout.cardCornerRadius)).background(TossCard).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, color = TossText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("%,.0f RWF".format(p.price), color = TossSecondary, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QtyButton("-") { if (qty > 0) cart[p.id] = qty - 1 }
                            Text(qty.toString(), modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = TossText, fontWeight = FontWeight.Bold)
                            QtyButton("+") { cart[p.id] = qty + 1 }
                        }
                    }
                }
            }
        }
        if (cartCount > 0) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TossBlue).clickable(onClick = onCheckout).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checkout ($cartCount item${if (cartCount == 1) "" else "s"})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QtyButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(TossCardSoft).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = TossText, fontWeight = FontWeight.Bold) }
}

@Composable
private fun CheckoutView(
    merchant: ShoppingMerchantDto,
    cart: Map<String, Int>,
    products: List<MerchantProductDto>,
    onBack: () -> Unit,
    onOrderPlaced: (OrderDto) -> Unit,
) {
    BackHandler(onBack = onBack)
    var address by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val idempotencyKey = remember { UUID.randomUUID().toString() }

    val lines = cart.filter { it.value > 0 }.mapNotNull { (productId, qty) -> products.find { it.id == productId }?.let { it to qty } }
    val total = lines.sumOf { (p, qty) -> p.price * qty }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal, vertical = Tds.layout.screenVertical)) {
        BackTopBar("Checkout", onBack)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(lines) { (p, qty) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.name} x$qty", color = TossText, fontSize = 14.sp)
                    Text("%,.0f RWF".format(p.price * qty), color = TossText, fontSize = 14.sp)
                }
            }
            item { Divider(color = TossLine, modifier = Modifier.padding(vertical = 10.dp)) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("%,.0f RWF".format(total), color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            item { Spacer(modifier = Modifier.height(14.dp)) }
            item {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = { Text("Delivery address") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { item { Text(it, color = Tds.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) } }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (submitting || address.isBlank()) TossTertiary else TossBlue)
                .clickable(enabled = !submitting && address.isNotBlank()) {
                    submitting = true
                    error = null
                    coroutineScope.launch {
                        try {
                            val res = NetworkClient.apiService.placeOrder(
                                idempotencyKey = idempotencyKey,
                                request = PlaceOrderRequest(
                                    merchantId = merchant.merchantId,
                                    items = lines.map { (p, qty) -> OrderItemRequest(p.id, qty) },
                                    deliveryAddress = address.trim(),
                                ),
                            )
                            if (res.success) onOrderPlaced(res.order)
                        } catch (e: HttpException) {
                            error = superAppErrorMessage(e)
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            submitting = false
                        }
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (submitting) "Placing order…" else "Place order", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun OrderConfirmationView(order: OrderDto, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Order placed") },
        text = {
            Column {
                Text("%,.0f RWF".format(order.totalAmount), color = TossText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Delivering to ${order.deliveryAddress}", color = TossSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}

private val COMMERCE_STATUS_LABEL = mapOf(
    "PLACED" to "Placed",
    "PACKED" to "Packed",
    "SHIPPED" to "Shipped",
    "DELIVERED" to "Delivered",
    "CANCELLED" to "Cancelled — refunded",
)

@Composable
private fun CommerceOrderRow(order: OrderDto, action: (@Composable () -> Unit)? = null) {
    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(COMMERCE_STATUS_LABEL[order.status] ?: order.status, color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(order.deliveryAddress, color = TossSecondary, fontSize = 12.sp)
                }
                Text("%,.0f RWF".format(order.totalAmount), color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            action?.invoke()
        }
    }
}

@Composable
private fun MyCommerceOrdersView() {
    var orders by remember { mutableStateOf<List<OrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyOrders()
                if (res.success) orders = res.orders
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    // Real poll for order-tracking status, same 4s cadence as Eats' own poll.
    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(4000)
        }
    }

    fun cancel(orderId: String) {
        cancellingId = orderId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelOrder(orderId)
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                cancellingId = null
            }
        }
    }

    Column {
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else if (orders == null) {
            Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {}
        } else if (orders!!.isEmpty()) {
            Text("No orders yet.", color = TossSecondary, fontSize = 14.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                orders!!.forEach { o ->
                    CommerceOrderRow(o) {
                        if (o.status == "PLACED") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Tds.colors.danger)
                                    .clickable(enabled = cancellingId != o.id) { cancel(o.id) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    if (cancellingId == o.id) "Cancelling…" else "Cancel order",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================== EATS (Coupang Eats-style) ==============================
// Real food ordering + a real rider role, folded into the Shop tab (2026-07-18) since the
// bottom nav has no free tab slot -- see rw.itunda.eats.EatsOrderService's own doc comment
// for the full backend account, including the honest "flat delivery fee, no geo data"
// scope. Restaurant/menu browsing reuses ShoppingMerchantDto/MerchantProductDto and the
// existing getShoppingMerchants()/getMerchantProducts() calls above -- zero new browse
// endpoint, matching CommerceShopContent's own reuse. Mirrors bank-mfe's EatsView 1:1.

private val EATS_STATUS_LABEL = mapOf(
    "PLACED" to "Placed",
    "ACCEPTED" to "Accepted by restaurant",
    "PREPARING" to "Preparing",
    "READY_FOR_PICKUP" to "Ready for pickup",
    "RIDER_ASSIGNED" to "Rider on the way to restaurant",
    "PICKED_UP" to "Picked up — on the way",
    "DELIVERED" to "Delivered",
    "CANCELLED" to "Cancelled — refunded",
)

private val RIDER_STATUS_CHAIN = listOf("RIDER_ASSIGNED", "PICKED_UP", "DELIVERED")

private fun nextRiderStatus(current: String): String? {
    val idx = RIDER_STATUS_CHAIN.indexOf(current)
    return if (idx >= 0 && idx + 1 < RIDER_STATUS_CHAIN.size) RIDER_STATUS_CHAIN[idx + 1] else null
}

private enum class EatsMode { ORDER, DELIVER }

@Composable
private fun EatsContent() {
    var mode by remember { mutableStateOf(EatsMode.ORDER) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Tds.layout.screenHorizontal)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(TossCardSoft).padding(4.dp)) {
            listOf(EatsMode.ORDER to "Order food", EatsMode.DELIVER to "Deliver").forEach { (m, label) ->
                val selected = m == mode
                Text(
                    label,
                    color = if (selected) Color.White else TossSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) TossBlue else Color.Transparent)
                        .clickable { mode = m }
                        .padding(vertical = 8.dp),
                )
            }
        }
        when (mode) {
            EatsMode.ORDER -> OrderFoodContent()
            EatsMode.DELIVER -> DeliverContent()
        }
    }
}

private enum class OrderFoodView { BROWSE, ORDERS }

@Composable
private fun OrderFoodContent() {
    var view by remember { mutableStateOf(OrderFoodView.BROWSE) }
    var restaurants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedRestaurant by remember { mutableStateOf<ShoppingMerchantDto?>(null) }
    var menu by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    val cart = remember { mutableStateMapOf<String, Int>() }
    var showCheckout by remember { mutableStateOf(false) }
    var confirmedOrder by remember { mutableStateOf<EatsOrderDto?>(null) }
    var reorderingId by remember { mutableStateOf<String?>(null) }
    var reorderError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadRestaurants() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getShoppingMerchants()
                if (res.success) restaurants = res.merchants
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { loadRestaurants() }

    fun openRestaurant(m: ShoppingMerchantDto) {
        selectedRestaurant = m
        cart.clear()
        menu = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMerchantProducts(m.merchantId)
                if (res.success) menu = res.products
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    // Real "Reorder" button (2026-07-19): re-populate the cart from a past order's real
    // items, cross-referenced against the restaurant's current menu -- discontinued items
    // are silently dropped rather than added as phantom cart lines.
    fun handleReorder(order: EatsOrderDto) {
        val restaurant = restaurants?.find { it.merchantId == order.restaurantId }
        if (restaurant == null) {
            reorderError = "This restaurant is no longer available."
            return
        }
        reorderingId = order.id
        reorderError = null
        coroutineScope.launch {
            try {
                val orderDetail = NetworkClient.apiService.getEatsOrder(order.id)
                val menuRes = NetworkClient.apiService.getMerchantProducts(order.restaurantId)
                if (!orderDetail.success || !menuRes.success) {
                    reorderError = "Could not reorder."
                    return@launch
                }
                val activeProductIds = menuRes.products.filter { it.active }.map { it.id }.toSet()
                val newCart = mutableMapOf<String, Int>()
                orderDetail.items.forEach { item ->
                    if (item.productId in activeProductIds) {
                        newCart[item.productId] = (newCart[item.productId] ?: 0) + item.quantity
                    }
                }
                if (newCart.isEmpty()) {
                    reorderError = "None of the items from that order are on the menu anymore."
                    return@launch
                }
                selectedRestaurant = restaurant
                menu = menuRes.products
                cart.clear()
                cart.putAll(newCart)
                view = OrderFoodView.BROWSE
            } catch (e: HttpException) {
                reorderError = superAppErrorMessage(e)
            } catch (e: IOException) {
                reorderError = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                reorderingId = null
            }
        }
    }

    val confirmed = confirmedOrder
    if (confirmed != null) {
        EatsOrderConfirmationView(confirmed, onDone = {
            confirmedOrder = null
            selectedRestaurant = null
            menu = null
            cart.clear()
            showCheckout = false
            view = OrderFoodView.ORDERS
        })
        return
    }

    val restaurant = selectedRestaurant
    if (restaurant != null) {
        if (showCheckout) {
            EatsCheckoutView(
                restaurant = restaurant,
                cart = cart,
                menu = menu.orEmpty(),
                onBack = { showCheckout = false },
                onOrderPlaced = { order -> confirmedOrder = order },
            )
        } else {
            RestaurantMenuView(
                restaurant = restaurant,
                menu = menu,
                cart = cart,
                onBack = { selectedRestaurant = null },
                onCheckout = { showCheckout = true },
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap)) {
        item {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TossCardSoft).padding(4.dp)) {
                listOf(OrderFoodView.BROWSE to "Restaurants", OrderFoodView.ORDERS to "My orders").forEach { (v, label) ->
                    val selected = v == view
                    Text(
                        label,
                        color = if (selected) Color.White else TossSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) TossBlue else Color.Transparent)
                            .clickable { view = v }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
        if (view == OrderFoodView.ORDERS) {
            item {
                MyEatsOrdersView(onReorder = ::handleReorder, reorderingId = reorderingId)
                val reorderErr = reorderError
                if (reorderErr != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(reorderErr, color = Tds.colors.danger, fontSize = 13.sp)
                }
            }
        } else if (error != null) {
            item { ErrorCard(error!!, onRetry = ::loadRestaurants) }
        } else if (restaurants == null) {
            item { Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {} }
        } else if (restaurants!!.isEmpty()) {
            item { Text("No restaurants registered yet.", color = TossSecondary, fontSize = 14.sp) }
        } else {
            items(restaurants!!, key = { it.merchantId }) { m ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Tds.layout.cardCornerRadius))
                        .background(TossCard)
                        .clickable { openRestaurant(m) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(TossCardSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(20.dp), tint = TossBlue)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(m.businessName, color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Real menu, real delivery", color = TossSecondary, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

// Real post-delivery ratings & reviews (2026-07-18) -- itunda's own self-hosted rating
// system, ported from bank-mfe's own review UI (the template for this Android version).
private val StarGold = Color(0xFFF5A623)

@Composable
private fun StarRatingRow(value: Int, onChange: (Int) -> Unit) {
    Row {
        for (n in 1..5) {
            Icon(
                Icons.Outlined.Star,
                contentDescription = "$n star${if (n == 1) "" else "s"}",
                tint = if (n <= value) StarGold else TossTertiary,
                modifier = Modifier.size(26.dp).clickable { onChange(n) },
            )
        }
    }
}

@Composable
private fun RestaurantRatingBadge(restaurantId: String) {
    var rating by remember { mutableStateOf<EatsRatingResponse?>(null) }
    LaunchedEffect(restaurantId) {
        try {
            rating = NetworkClient.apiService.getRestaurantRating(restaurantId)
        } catch (e: Exception) {
            // Real, non-critical -- a rating fetch failure shouldn't block browsing the menu.
        }
    }
    val r = rating
    if (r != null && r.count > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("%.1f (%d)".format(r.average ?: 0.0, r.count), color = TossSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ReviewOrderCard(order: EatsOrderDto) {
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var restaurantRating by remember { mutableStateOf(0) }
    var restaurantComment by remember { mutableStateOf("") }
    var riderRating by remember { mutableStateOf(0) }
    var riderComment by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("Thanks for your review!", color = TossSecondary, fontSize = 13.sp)
        return
    }
    if (!open) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(TossTertiary).clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Rate this order", color = TossText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        Column {
            Text("Restaurant", color = TossSecondary, fontSize = 12.sp)
            StarRatingRow(restaurantRating) { restaurantRating = it }
            OutlinedTextField(
                value = restaurantComment,
                onValueChange = { restaurantComment = it },
                placeholder = { Text("How was the food? (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column {
            Text("Rider", color = TossSecondary, fontSize = 12.sp)
            StarRatingRow(riderRating) { riderRating = it }
            OutlinedTextField(
                value = riderComment,
                onValueChange = { riderComment = it },
                placeholder = { Text("How was the delivery? (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(TossTertiary).clickable { open = false }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", color = TossText, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (submitting) TossTertiary else TossBlue)
                    .clickable(enabled = !submitting) {
                        if (restaurantRating == 0 || riderRating == 0) {
                            error = "Rate both the restaurant and the rider."
                            return@clickable
                        }
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.submitEatsReview(
                                    order.id,
                                    SubmitEatsReviewRequest(restaurantRating, restaurantComment.trim().ifBlank { null }, riderRating, riderComment.trim().ifBlank { null }),
                                )
                                done = true
                            } catch (e: HttpException) {
                                // A 409 here is the real ORDER_ALREADY_REVIEWED case in
                                // practice -- this form only ever renders for a real
                                // DELIVERED order, so the sibling "not yet delivered"
                                // 409 can't actually occur through this UI path.
                                if (e.code() == 409) {
                                    done = true
                                } else {
                                    error = superAppErrorMessage(e)
                                }
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (submitting) "Submitting…" else "Submit review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun RestaurantMenuView(
    restaurant: ShoppingMerchantDto,
    menu: List<MerchantProductDto>?,
    cart: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>,
    onBack: () -> Unit,
    onCheckout: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val cartCount = cart.values.sum()
    Column(modifier = Modifier.fillMaxSize().padding(vertical = Tds.layout.screenVertical)) {
        BackTopBar(restaurant.businessName, onBack)
        RestaurantRatingBadge(restaurant.merchantId)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (menu == null) {
                item { Text("Loading…", color = TossSecondary, fontSize = 13.sp) }
            } else if (menu.isEmpty()) {
                item { Text("No menu items yet.", color = TossSecondary, fontSize = 13.sp) }
            } else {
                items(menu, key = { it.id }) { p ->
                    val qty = cart[p.id] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Tds.layout.cardCornerRadius)).background(TossCard).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, color = TossText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("%,.0f RWF".format(p.price), color = TossSecondary, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QtyButton("-") { if (qty > 0) cart[p.id] = qty - 1 }
                            Text(qty.toString(), modifier = Modifier.width(28.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = TossText, fontWeight = FontWeight.Bold)
                            QtyButton("+") { cart[p.id] = qty + 1 }
                        }
                    }
                }
            }
        }
        if (cartCount > 0) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TossBlue).clickable(onClick = onCheckout).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checkout ($cartCount item${if (cartCount == 1) "" else "s"})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Real self-hosted address-search autocomplete (2026-07-18) -- itunda's own Nominatim
// geocoder, not a third-party Maps API. Mirrors bank-mfe's AddressAutocomplete component:
// debounced real search-as-you-type, a real suggestion dropdown, and on selection the
// real resolved coordinates are handed back so the caller can submit them explicitly
// (taking priority over EatsOrderService's own automatic single-best-match fallback).
// Typing without selecting still places a real order via that fallback.
@Composable
private fun AddressAutocompleteField(
    address: String,
    onAddressChange: (String) -> Unit,
    onSuggestionSelected: (AddressSuggestionDto) -> Unit,
) {
    var suggestions by remember { mutableStateOf<List<AddressSuggestionDto>>(emptyList()) }
    var justSelected by remember { mutableStateOf(false) }

    LaunchedEffect(address) {
        if (justSelected) {
            justSelected = false
            return@LaunchedEffect
        }
        if (address.trim().length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        suggestions = try {
            val res = NetworkClient.apiService.searchDeliveryAddress(address.trim())
            if (res.success) res.suggestions else emptyList()
        } catch (e: Exception) {
            // Real, non-critical -- a failed suggestion fetch shouldn't block typing a
            // plain address; the order still places, just without a confirmed pin.
            emptyList()
        }
    }

    Column {
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            placeholder = { Text("Delivery address") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(Tds.layout.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = TossCard),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column {
                    suggestions.forEach { s ->
                        Text(
                            s.displayName,
                            color = TossText,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    justSelected = true
                                    suggestions = emptyList()
                                    onSuggestionSelected(s)
                                }
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EatsCheckoutView(
    restaurant: ShoppingMerchantDto,
    cart: Map<String, Int>,
    menu: List<MerchantProductDto>,
    onBack: () -> Unit,
    onOrderPlaced: (EatsOrderDto) -> Unit,
) {
    BackHandler(onBack = onBack)
    var address by remember { mutableStateOf("") }
    var addressLatitude by remember { mutableStateOf<Double?>(null) }
    var addressLongitude by remember { mutableStateOf<Double?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val idempotencyKey = remember { UUID.randomUUID().toString() }

    val lines = cart.filter { it.value > 0 }.mapNotNull { (productId, qty) -> menu.find { it.id == productId }?.let { it to qty } }
    val total = lines.sumOf { (p, qty) -> p.price * qty }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = Tds.layout.screenVertical)) {
        BackTopBar("Checkout", onBack)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(lines) { (p, qty) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.name} x$qty", color = TossText, fontSize = 14.sp)
                    Text("%,.0f RWF".format(p.price * qty), color = TossText, fontSize = 14.sp)
                }
            }
            item { Divider(color = TossLine, modifier = Modifier.padding(vertical = 10.dp)) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = TossText, fontSize = 14.sp)
                    Text("%,.0f RWF".format(total), color = TossText, fontSize = 14.sp)
                }
            }
            item { Text("Plus a real delivery fee, added at checkout", color = TossSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
            item { Spacer(modifier = Modifier.height(14.dp)) }
            item {
                AddressAutocompleteField(
                    address = address,
                    onAddressChange = { address = it; addressLatitude = null; addressLongitude = null },
                    onSuggestionSelected = { s ->
                        address = s.displayName
                        addressLatitude = s.latitude
                        addressLongitude = s.longitude
                    },
                )
            }
            if (addressLatitude != null) {
                item { Text("Pinned -- real distance-based delivery fee applies", color = Tds.colors.success, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
            error?.let { item { Text(it, color = Tds.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) } }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (submitting || address.isBlank()) TossTertiary else TossBlue)
                .clickable(enabled = !submitting && address.isNotBlank()) {
                    submitting = true
                    error = null
                    coroutineScope.launch {
                        try {
                            val res = NetworkClient.apiService.placeEatsOrder(
                                idempotencyKey = idempotencyKey,
                                request = PlaceEatsOrderRequest(
                                    restaurantId = restaurant.merchantId,
                                    items = lines.map { (p, qty) -> EatsOrderItemRequest(p.id, qty) },
                                    deliveryAddress = address.trim(),
                                    deliveryLatitude = addressLatitude,
                                    deliveryLongitude = addressLongitude,
                                ),
                            )
                            if (res.success) onOrderPlaced(res.order)
                        } catch (e: HttpException) {
                            error = superAppErrorMessage(e)
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            submitting = false
                        }
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (submitting) "Placing order…" else "Place order", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun EatsOrderConfirmationView(order: EatsOrderDto, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Order placed") },
        text = {
            Column {
                Text("%,.0f RWF".format(order.totalAmount), color = TossText, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Delivering to ${order.deliveryAddress}", color = TossSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Track order") } },
    )
}

@Composable
private fun EatsOrderRow(order: EatsOrderDto, action: (@Composable () -> Unit)? = null) {
    Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(EATS_STATUS_LABEL[order.status] ?: order.status, color = TossBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(order.deliveryAddress, color = TossSecondary, fontSize = 12.sp)
                }
                Text("%,.0f RWF".format(order.totalAmount), color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            action?.invoke()
        }
    }
}

@Composable
private fun MyEatsOrdersView(onReorder: (EatsOrderDto) -> Unit, reorderingId: String?) {
    var orders by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyEatsOrders()
                if (res.success) orders = res.orders
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    // Real poll for order-tracking status, same 4s cadence as Talk's own poll.
    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(4000)
        }
    }

    fun cancel(orderId: String) {
        cancellingId = orderId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelEatsOrder(orderId)
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                cancellingId = null
            }
        }
    }

    Column {
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else if (orders == null) {
            Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {}
        } else if (orders!!.isEmpty()) {
            Text("No orders yet.", color = TossSecondary, fontSize = 14.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                orders!!.forEach { o ->
                    EatsOrderRow(o) {
                        if (o.status == "PLACED") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Tds.colors.danger)
                                    .clickable(enabled = cancellingId != o.id) { cancel(o.id) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    if (cancellingId == o.id) "Cancelling…" else "Cancel order",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        } else if (o.status == "DELIVERED") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReviewOrderCard(o)
                                ReorderButton(reordering = reorderingId == o.id, onClick = { onReorder(o) })
                            }
                        } else if (o.status == "CANCELLED") {
                            ReorderButton(reordering = reorderingId == o.id, onClick = { onReorder(o) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderButton(reordering: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(TossBlue)
            .clickable(enabled = !reordering, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            if (reordering) "Reordering…" else "Reorder",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

// ============================== DELIVER (Rider) ==============================

@Composable
private fun DeliverContent() {
    var rider by remember { mutableStateOf<RiderDto?>(null) }
    var loadedRider by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var registering by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var mine by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var busyOrderId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadRider() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyRiderProfile()
                if (res.success) rider = res.rider
                error = null
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    rider = null
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                loadedRider = true
            }
        }
    }
    LaunchedEffect(Unit) { loadRider() }

    suspend fun loadDeliveries() {
        try {
            val a = NetworkClient.apiService.getAvailableDeliveries()
            val m = NetworkClient.apiService.getRiderDeliveries()
            if (a.success) available = a.orders
            if (m.success) mine = m.orders
        } catch (_: Exception) {
            // Keep showing the last-known lists on a transient poll failure.
        }
    }
    LaunchedEffect(rider?.id) {
        if (rider == null) return@LaunchedEffect
        while (true) {
            loadDeliveries()
            delay(4000)
        }
    }

    if (!loadedRider) {
        Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(120.dp)) {}
        return
    }

    val currentRider = rider
    if (currentRider == null) {
        Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Deliver with Itunda", color = TossText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Earn a real delivery fee for every order you deliver, paid straight to your wallet.",
                    color = TossSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(TossBlue)
                        .clickable(enabled = !registering) {
                            registering = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.registerRider()
                                    if (res.success) rider = res.rider
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    registering = false
                                }
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                ) { Text(if (registering) "Registering…" else "Become a rider", color = Color.White, fontWeight = FontWeight.Bold) }
                error?.let { Text(it, color = Tds.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        return
    }

    val activeDeliveries = mine.orEmpty().filter { it.status != "DELIVERED" }
    val pastDeliveries = mine.orEmpty().filter { it.status == "DELIVERED" }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Tds.layout.cardGap), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = TossCard), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (currentRider.available) "You're online" else "You're offline", color = TossText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(if (currentRider.available) "Visible for new deliveries" else "Go online to see deliveries", color = TossSecondary, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (currentRider.available) Tds.colors.danger else TossBlue)
                            .clickable {
                                coroutineScope.launch {
                                    try {
                                        val res = NetworkClient.apiService.setRiderAvailability(SetRiderAvailabilityRequest(!currentRider.available))
                                        if (res.success) rider = res.rider
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text(if (currentRider.available) "Go offline" else "Go online", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
        }
        error?.let { item { Text(it, color = Tds.colors.danger, fontSize = 12.sp) } }
        if (activeDeliveries.isNotEmpty()) {
            item { Text("Your active deliveries", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(activeDeliveries, key = { it.id }) { o ->
                val next = nextRiderStatus(o.status)
                EatsOrderRow(o) {
                    if (next != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(TossBlue)
                                .clickable(enabled = busyOrderId != o.id) {
                                    busyOrderId = o.id
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.updateRiderOrderStatus(o.id, UpdateEatsOrderStatusRequest(next))
                                            loadDeliveries()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } finally {
                                            busyOrderId = null
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                if (busyOrderId == o.id) "Updating…" else "Mark ${(EATS_STATUS_LABEL[next] ?: next).lowercase()}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
        if (currentRider.available) {
            item { Text("Available deliveries", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            if (available == null) {
                item { Card(shape = RoundedCornerShape(Tds.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(100.dp)) {} }
            } else if (available!!.isEmpty()) {
                item { Text("No deliveries waiting right now.", color = TossSecondary, fontSize = 13.sp) }
            } else {
                items(available!!, key = { it.id }) { o ->
                    EatsOrderRow(o) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(TossBlue)
                                .clickable(enabled = busyOrderId != o.id) {
                                    busyOrderId = o.id
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.claimDelivery(o.id)
                                            loadDeliveries()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } finally {
                                            busyOrderId = null
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) { Text(if (busyOrderId == o.id) "Claiming…" else "Claim delivery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
        if (pastDeliveries.isNotEmpty()) {
            item { Text("Completed", color = TossText, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(pastDeliveries, key = { it.id }) { o -> EatsOrderRow(o) }
        }
    }
}
