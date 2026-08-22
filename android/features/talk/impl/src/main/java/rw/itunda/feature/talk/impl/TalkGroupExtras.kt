package rw.itunda.feature.talk.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.chatMessageTime
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddGroupMemberRequest
import rw.itunda.core.network.GroupMessageResponse
import rw.itunda.core.network.SetGroupDescriptionRequest
import rw.itunda.core.network.SetGroupPhotoUrlRequest
import rw.itunda.core.network.GroupMemberDto
import rw.itunda.core.network.ForwardMessageRequest
import rw.itunda.core.network.GroupMessageDto
import rw.itunda.core.network.GroupSummaryDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SendMessageRequest
import rw.itunda.core.network.TalkContactDto




// Real leave-group/add-member (2026-07-22) -- found fully built on the backend
// (GroupMessagingController's POST/DELETE .../members) with zero client UI anywhere,
// despite group chat itself being fully wired. Add-member picks from the caller's
// real Talk contacts (GET /api/v1/messages/contacts), same list DirectMessagesList
// already uses to start a 1:1 chat, filtered to exclude people already in the group.
@Composable
internal fun GroupManageMembersView(
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
    // Real group photo/description (2026-07-28) -- see ApiService.kt's own doc
    // comment. Found 2026-08-01 via a defined-but-uncalled-endpoint sweep: real on
    // backend since it shipped, zero client anywhere on any of the 3 platforms until
    // now.
    var photoUrl by remember { mutableStateOf(group.photoUrl ?: "") }
    var description by remember { mutableStateOf(group.description ?: "") }
    var savingInfo by remember { mutableStateOf(false) }
    var infoSaved by remember { mutableStateOf(false) }
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
            item { Text("Group info", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            item {
                IdsTextField(
                    value = photoUrl,
                    onValueChange = { photoUrl = it },
                    label = "Photo URL (blank to clear)",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                IdsTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Group description (blank to clear)",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                IdsButton(
                    text = if (savingInfo) "Saving…" else if (infoSaved) "Saved" else "Save group info",
                    enabled = !savingInfo,
                    onClick = {
                        savingInfo = true
                        infoSaved = false
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.setGroupPhotoUrl(group.groupId, SetGroupPhotoUrlRequest(photoUrl.trim()))
                                NetworkClient.apiService.setGroupDescription(group.groupId, SetGroupDescriptionRequest(description.trim()))
                                infoSaved = true
                            } catch (_: Exception) {
                                error = "Could not update group info."
                            } finally { savingInfo = false }
                        }
                    },
                )
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { Text("Members (${members.size})", fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            items(members, key = { it.userId }) { member ->
                Text(if (member.userId == currentUserId) "${member.name} (you)" else member.name, fontSize = 14.sp, modifier = Modifier.padding(vertical = 4.dp))
            }
            item {
                IdsButton(
                    text = if (leaving) "Leaving…" else "Leave group",
                    enabled = !leaving,
                    variant = IdsButtonVariant.Tinted,
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
                )
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
                        IdsButton(
                            text = if (busyUserId == contact.userId) "Adding…" else "Add",
                            enabled = busyUserId == null,
                            size = IdsButtonSize.Small,
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
                        )
                    }
                }
            }
        }
    }
}

// Real KakaoTalk-style long-press message menu -- see MessageBubble's own doc comment
// for the full sourced account; same treatment applied here for group threads.
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GroupMessageBubble(
    message: GroupMessageDto, isMine: Boolean, senderName: String, currentUserId: String?, onToggleReaction: (String) -> Unit, onDelete: (String) -> Unit = {}, onReply: (GroupMessageDto) -> Unit = {}, onOpenThread: (GroupMessageDto) -> Unit = {}, onPin: (GroupMessageDto) -> Unit = {}, onForward: (GroupMessageDto) -> Unit = {}, emoticonImageUrl: String? = null,
    showTimestamp: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxWidth()) {
        // Real forwarded-message provenance (2026-08-04) -- see ForwardMessageRequest's
        // own doc comment. Always genuine: the backend only ever stamps this on a real
        // forward, never client-asserted.
        if (message.forwardedFromMessageId != null) {
            // The backend stamps a real DIRECT/GROUP origin on every forward
            // (MessageForwardService), which this label ignored until 2026-08-14 --
            // "forwarded from a group" is materially different context for the reader
            // than a forward out of a 1:1 chat, same distinction Telegram/KakaoTalk draw.
            val forwardedLabel = when (message.forwardedFromType) {
                "GROUP" -> "↪ Forwarded from a group chat"
                "DIRECT" -> "↪ Forwarded from a chat"
                else -> "↪ Forwarded"
            }
            Text(forwardedLabel, color = Ids.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = if (isMine) TextAlign.End else TextAlign.Start)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
            Box(modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { menuOpen = true })) {
                if (message.emoticonId != null) {
                    if (!isMine) {
                        Text(senderName, color = Ids.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    EmoticonBubble(emoticonImageUrl)
                } else if (message.imageUrl != null) {
                    // Real photo message (2026-08-04) -- see SendMessageRequest's own doc
                    // comment.
                    Column {
                        if (!isMine) {
                            Text(senderName, color = Ids.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                        }
                        AsyncImage(
                            model = message.imageUrl,
                            contentDescription = "Photo",
                            modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(16.dp)),
                        )
                    }
                } else {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    if (!isMine) {
                        Text(senderName, color = Ids.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                    }
                    MessageBodyWithEmoji(message.body, color = if (isMine) Color.White else Ids.colors.textPrimary, fontSize = 14.sp)
                }
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (message.emoticonId == null && message.imageUrl == null) {
                        DropdownMenuItem(text = { Text("Copy") }, onClick = { clipboardManager.setText(AnnotatedString(message.body)); menuOpen = false })
                    }
                    DropdownMenuItem(text = { Text("Reply") }, onClick = { onReply(message); menuOpen = false })
                    DropdownMenuItem(text = { Text("Pin") }, onClick = { onPin(message); menuOpen = false })
                    DropdownMenuItem(text = { Text("Forward") }, onClick = { onForward(message); menuOpen = false })
                    if (isMine && message.deletedAt == null) {
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(message.id); menuOpen = false })
                    }
                }
            }
        }
        MessageReactionsRow(message.reactions, currentUserId, isMine, onToggleReaction)
        val statusParts = buildList {
            if (isMine && message.unreadCount > 0) add("${message.unreadCount}")
            if (showTimestamp) add(chatMessageTime(message.sentAt))
        }
        if (statusParts.isNotEmpty()) {
            Text(
                statusParts.joinToString(" · "),
                color = Ids.colors.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                textAlign = if (isMine) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
            )
        }
        // Real Thread support (2026-08-05) -- see MessageBubble's own identical
        // affordance.
        if (message.replyCount > 0) {
            Text(
                "${message.replyCount} ${if (message.replyCount == 1L) "reply" else "replies"} →",
                color = Ids.colors.brand,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).pressScaleClickable { onOpenThread(message) },
                textAlign = if (isMine) androidx.compose.ui.text.style.TextAlign.End else androidx.compose.ui.text.style.TextAlign.Start,
            )
        }
    }
}

// Real Thread support (2026-08-05) -- see RepliesThreadView's own doc comment; identical
// shape for group chat.
@Composable
internal fun GroupRepliesThreadView(
    rootMessage: GroupMessageDto,
    currentUserId: String?,
    fetchThreadMessages: suspend () -> List<GroupMessageDto>,
    onSend: suspend (String) -> GroupMessageResponse,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var messages by remember { mutableStateOf<List<GroupMessageDto>?>(null) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try { messages = fetchThreadMessages() } catch (_: Exception) { error = "Could not load this thread." }
        }
    }
    LaunchedEffect(rootMessage.id) { load() }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar("Thread", onBack)
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val msgs = messages
            if (msgs == null) {
                item { SkeletonBlock(height = 72.dp) }
            } else {
                itemsIndexed(msgs, key = { _, m -> m.id }) { index, m ->
                    val isMine = m.senderId == currentUserId
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (index == 0) {
                            Text("Original message", color = Ids.colors.textSecondary, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start) {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (isMine) Ids.colors.brand else Ids.colors.surfaceSoft).padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                if (m.deletedAt == null) {
                                    MessageBodyWithEmoji(m.body, color = if (isMine) Color.White else Ids.colors.textPrimary, fontSize = 14.sp)
                                } else {
                                    Text("This message was deleted", color = if (isMine) Color.White else Ids.colors.textPrimary, fontSize = 14.sp)
                                }
                            }
                        }
                        Text(
                            chatMessageTime(m.sentAt), color = Ids.colors.textSecondary, fontSize = 10.sp,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            textAlign = if (isMine) TextAlign.End else TextAlign.Start,
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IdsTextField(value = draft, onValueChange = { draft = it }, label = "Reply in thread", modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            IdsButton(
                text = if (sending) "…" else "Send",
                enabled = !sending && draft.isNotBlank(),
                size = IdsButtonSize.Medium,
                onClick = {
                    val body = draft.trim()
                    if (body.isEmpty()) return@IdsButton
                    sending = true
                    coroutineScope.launch {
                        try { onSend(body); draft = ""; load() } catch (_: Exception) { error = "Could not send this reply." } finally { sending = false }
                    }
                },
            )
        }
    }
}


internal fun activeMentionQuery(draft: String): String? {
    val at = draft.lastIndexOf('@')
    if (at == -1) return null
    val tail = draft.substring(at + 1)
    if (tail.contains(' ') || tail.contains('\n')) return null
    return tail
}

internal fun applyMention(draft: String, memberName: String): String {
    val at = draft.lastIndexOf('@')
    if (at == -1) return draft
    val firstName = memberName.trim().substringBefore(' ')
    return draft.substring(0, at) + "@$firstName "
}

@Composable
internal fun MentionSuggestions(draft: String, members: List<GroupMemberDto>, currentUserId: String?, onPick: (String) -> Unit) {
    val query = activeMentionQuery(draft) ?: return
    val matches = members.filter { it.userId != currentUserId && it.name.substringBefore(' ').startsWith(query, ignoreCase = true) }
    if (matches.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        matches.forEach { member ->
            Text(
                "@${member.name.substringBefore(' ')}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Ids.colors.brand)
                    .pressScaleClickable { onPick(member.name) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
