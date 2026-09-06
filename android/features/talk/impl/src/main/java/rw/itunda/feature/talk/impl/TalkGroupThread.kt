package rw.itunda.feature.talk.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.shouldShowChatTimestamp
import rw.itunda.core.designsystem.itundaface.CameraGlyph
import rw.itunda.core.designsystem.itundaface.PinGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.SendEmoticonRequest
import rw.itunda.core.network.GroupMemberDto
import rw.itunda.core.network.GroupMessageDto
import rw.itunda.core.network.GroupSummaryDto
import rw.itunda.core.network.MessagingSocketPush
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SendGroupMessageRequest
import rw.itunda.core.network.SendMessageRequest
import rw.itunda.core.network.ToggleReactionRequest
import rw.itunda.core.network.TokenStore
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
@Composable
internal fun GroupThreadView(group: GroupSummaryDto, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var messages by remember { mutableStateOf<List<GroupMessageDto>?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<GroupMessageDto>?>(null) }
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
    // Real per-thread shared-media gallery (2026-08-04, Kakao's real "Chat Room Drawer")
    // -- see the References table's own "KakaoTalk — Chat Room Drawer" row. Scoped
    // honestly to photos only: itunda has real photo messages (just shipped, this same
    // pass) but no file-attachment type and no link-preview system, so a real "files/
    // links" tab would have nothing genuine to show. Built entirely client-side from the
    // conversation's own already-loaded `messages` list (filtered to real `imageUrl !=
    // null` entries) -- no new backend endpoint, since the data already exists in memory.
    var showMediaGallery by remember { mutableStateOf(false) }
    // Real Thread support (2026-08-05) -- see RepliesThreadView's own doc comment; same
    // real sub-conversation concept for group chat.
    var openThreadFor by remember { mutableStateOf<GroupMessageDto?>(null) }
    // Real message forwarding (2026-08-04) -- see ForwardDestinationDialog's own doc
    // comment.
    var forwardingMessageId by remember { mutableStateOf<String?>(null) }
    // Real KakaoTalk Emoticon Store, group-send side (item 133/204) -- see
    // sendGroupEmoticon's own doc comment. 1:1 chat has had this since the Emoticon
    // Store shipped; group chat never got a client for the identical, already-real
    // backend endpoint. Found 2026-07-29 via the defined-but-uncalled-method sweep.
    var emoticonPickerOpen by remember { mutableStateOf(false) }
    var emoticonStoreOpen by remember { mutableStateOf(false) }
    // Real itundaface emoji picker -- see ItundaFaceEmoji.kt's own doc comment.
    var emojiPickerOpen by remember { mutableStateOf(false) }
    var emoticonImageById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    // Real attach ("+") menu + photo send (2026-08-04) -- closes
    // docs/DESIGN_REFERENCES.md's Talk recommendation #6. Reuses the exact real upload
    // flow MarketplaceScreen/PropertyScreen already established (GetContent() picker ->
    // uploadPhoto() -> real server URL) -- see SendMessageRequest's own doc comment for
    // the full "defined but uncalled" backend account.
    var attachMenuOpen by remember { mutableStateOf(false) }
    var uploadingPhoto by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Real group-chat pin (2026-07-26 backend, wired 2026-08-04) -- found via the same
    // defined-but-uncalled-method sweep this file's own doc history already names for
    // emoticons above: GroupMessagingController's real /{groupId}/pin endpoints existed
    // with zero Retrofit method or UI anywhere. Mirrors ChatThreadView's own real
    // pinnedMessage/updatingPin pattern for 1:1 exactly.
    var pinnedMessage by remember { mutableStateOf<GroupMessageDto?>(null) }
    var updatingPin by remember { mutableStateOf(false) }
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

    LaunchedEffect(Unit) {
        try {
            val packs = NetworkClient.apiService.getEmoticonPacks().packs
            val map = packs.flatMap { pack ->
                try { NetworkClient.apiService.getPackEmoticons(pack.id).emoticons } catch (_: Exception) { emptyList() }
            }.associate { it.id to it.imageUrl }
            emoticonImageById = map
        } catch (_: Exception) {
            // Real, non-critical -- only backs the inline emoticon bubble.
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
    LaunchedEffect(group.groupId) {
        try { pinnedMessage = NetworkClient.apiService.getPinnedGroupMessage(group.groupId).message } catch (_: Exception) { }
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
        // Real fix, found live on a physical device (2026-08-29): BackTopBar itself is
        // Modifier.fillMaxWidth() (a shared component, also used standalone elsewhere --
        // see its own definition), so nesting it in a Row(SpaceBetween) alongside a
        // sibling icon Row left the icons zero width to lay out in -- Photos/Manage
        // members/Split a bill (and therefore Announcement & polls, reachable only
        // through Manage members) were all completely invisible and unreachable on this
        // screen. This predates the itunda Talk redesign (the header itself is unchanged
        // since an earlier session's TalkScreen.kt decomposition) but blocked verifying
        // the new group announcement/poll feature, so fixed here. Same Box-overlay
        // technique TalkScreen.kt's own 1:1-thread Links/Settings entry point already
        // uses for the identical reason (see its own doc comment) -- BackTopBar keeps its
        // real fillMaxWidth, the icon row is absolutely positioned into the empty space
        // to its right instead of competing for width in the same Row.
        Box(modifier = Modifier.fillMaxWidth()) {
            BackTopBar(group.name, onBack)
            Row(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Outlined.Search, contentDescription = "Search this group")
                }
                IconButton(onClick = { showMediaGallery = true }) {
                    Icon(Icons.Outlined.Photo, contentDescription = "Shared photos")
                }
                IconButton(onClick = { showManageMembers = true }) {
                    Icon(Icons.Outlined.Group, contentDescription = "Manage members")
                }
                IconButton(onClick = { showSplitBills = true }) {
                    Icon(Icons.Outlined.Receipt, contentDescription = "Split a bill")
                }
            }
        }
        if (showSearch) {
            GroupSearchBar(group.groupId, onResultsChange = { searchResults = it }, onError = { error = it })
        }
        if (showMediaGallery) {
            MediaGalleryView(imageUrls = (messages ?: emptyList()).mapNotNull { it.imageUrl }.reversed(), onBack = { showMediaGallery = false })
            return@Column
        }
        openThreadFor?.let { root ->
            GroupRepliesThreadView(
                rootMessage = root,
                currentUserId = currentUserId,
                fetchThreadMessages = { NetworkClient.apiService.getGroupThread(group.groupId, root.id).messages },
                onSend = { body -> NetworkClient.apiService.sendGroupMessage(group.groupId, SendGroupMessageRequest(body, root.id)) },
                onBack = { openThreadFor = null; coroutineScope.launch { refresh() } },
            )
            return@Column
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
        pinnedMessage?.let { pinned ->
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    PinGlyph(size = 12.dp)
                    Text(pinned.body, color = Ids.colors.textPrimary, fontSize = 12.sp, maxLines = 1)
                }
                TextButton(onClick = {
                    updatingPin = true
                    coroutineScope.launch { try { NetworkClient.apiService.unpinGroupMessage(group.groupId); pinnedMessage = null } catch (_: Exception) { error = "Couldn't unpin this message." } finally { updatingPin = false } }
                }, enabled = !updatingPin) { Text("Unpin", fontSize = 11.sp) }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val msgs = searchResults ?: messages
            if (msgs == null) {
                item { SkeletonBlock(height = 72.dp) }
            } else if (msgs.isEmpty()) {
                item { Text("Say hello — no messages yet.", color = Ids.colors.textSecondary, fontSize = 13.sp) }
            } else {
                // Real, honest limitation: bubbles show a truncated sender id, not a
                // real display name -- no "list group members" endpoint exists yet to
                // resolve names client-side, matching bank-mfe's own known gap.
                itemsIndexed(msgs, key = { _, m -> m.id }) { index, m ->
                    val senderName = members.find { it.userId == m.senderId }?.name ?: m.senderId.take(8)
                    GroupMessageBubble(
                        m,
                        isMine = m.senderId == currentUserId,
                        senderName = senderName,
                        currentUserId = currentUserId,
                        emoticonImageUrl = m.emoticonId?.let(emoticonImageById::get),
                        showTimestamp = searchResults != null || shouldShowChatTimestamp(msgs, index, { it.senderId }, { it.sentAt }),
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
                        onOpenThread = { openThreadFor = it },
                        onDelete = { messageId -> coroutineScope.launch {
                            try { NetworkClient.apiService.deleteGroupMessage(group.groupId, messageId); refresh() }
                            catch (_: Exception) { error = "Couldn't delete this message." }
                        } },
                        onPin = { message ->
                            updatingPin = true
                            coroutineScope.launch { try { NetworkClient.apiService.pinGroupMessage(group.groupId, message.id); pinnedMessage = message } catch (_: Exception) { error = "Couldn't pin this message." } finally { updatingPin = false } }
                        },
                        onForward = { message -> forwardingMessageId = message.id },
                    )
                }
            }
        }
        forwardingMessageId?.let { messageId ->
            ForwardDestinationDialog(sourceMessageId = messageId, isGroupSource = true, onDismiss = { forwardingMessageId = null })
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
        if (emojiPickerOpen) {
            ItundaFaceEmojiPicker(onPick = { emoji -> draft += emoji })
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (emoticonPickerOpen) {
            EmoticonPickerPanel(
                onSend = { emoticonId ->
                    coroutineScope.launch {
                        try {
                            val res = NetworkClient.apiService.sendGroupEmoticon(group.groupId, SendEmoticonRequest(emoticonId))
                            if (res.success) messages = (messages ?: emptyList()) + res.message
                            emoticonPickerOpen = false
                        } catch (_: Exception) {
                            error = "Couldn't send this emoticon."
                        }
                    }
                },
                onOpenStore = { emoticonStoreOpen = true },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (emoticonStoreOpen) {
            EmoticonStoreDialog(onDismiss = { emoticonStoreOpen = false })
        }
        val pickGroupPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            uploadingPhoto = true
            error = null
            coroutineScope.launch {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null) {
                        error = "Couldn't read that photo."
                        return@launch
                    }
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val part = MultipartBody.Part.createFormData("file", "photo.jpg", bytes.toRequestBody(mimeType.toMediaTypeOrNull()))
                    val photoUrl = NetworkClient.apiService.uploadPhoto(part).url
                    val res = NetworkClient.apiService.sendGroupMessage(group.groupId, SendGroupMessageRequest("", replyingTo?.id, photoUrl))
                    if (res.success) {
                        replyingTo = null
                        messages = (messages ?: emptyList()) + res.message
                    }
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: IOException) {
                    error = "Couldn't upload that photo. Check your connection and try again."
                } finally {
                    uploadingPhoto = false
                }
            }
        }
        MentionSuggestions(draft, members, currentUserId, onPick = { name -> draft = applyMention(draft, name) })
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Box {
                Box(
                    modifier = Modifier
                        .size(Ids.layout.minTouchTarget)
                        .clip(CircleShape)
                        .background(Ids.colors.surfaceSoft)
                        .pressScaleClickable(enabled = !uploadingPhoto) { attachMenuOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (uploadingPhoto) "…" else "+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                }
                // Real attach menu (2026-08-04) -- Kakao's own real "+"-opens-a-menu
                // pattern (References table: "'+' opens a multi-function attach menu").
                DropdownMenu(expanded = attachMenuOpen, onDismissRequest = { attachMenuOpen = false }) {
                    DropdownMenuItem(text = { Text("Photo") }, leadingIcon = { CameraGlyph(size = 18.dp) }, onClick = { attachMenuOpen = false; pickGroupPhoto.launch("image/*") })
                    DropdownMenuItem(text = { Text("Emoji") }, leadingIcon = { SmileySlight(size = 18.dp) }, onClick = { attachMenuOpen = false; emojiPickerOpen = !emojiPickerOpen })
                    DropdownMenuItem(text = { Text("😊 Emoticon") }, onClick = { attachMenuOpen = false; emoticonPickerOpen = !emoticonPickerOpen })
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            IdsTextField(
                value = draft,
                onValueChange = { newValue ->
                    draft = newValue
                    val now = System.currentTimeMillis()
                    if (now - lastTypingSentAt > 2000) {
                        lastTypingSentAt = now
                        socket?.let { NetworkClient.sendTyping(it, groupConversationId = group.groupId) }
                    }
                },
                label = "Message",
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(Ids.layout.minTouchTarget)
                    .clip(CircleShape)
                    .background(if (draft.isBlank() || sending) Ids.colors.textTertiary else Ids.colors.brand)
                    .pressScaleClickable(enabled = draft.isNotBlank() && !sending) {
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
                Icon(IdsIcons.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
// Real split-bill feature (GroupSplitBillsView/DirectSplitBillsView) moved to
// TalkSplitBills.kt (2026-08-19) -- see that file's own header comment.

