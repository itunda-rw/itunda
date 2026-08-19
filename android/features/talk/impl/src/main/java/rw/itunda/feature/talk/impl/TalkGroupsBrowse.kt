package rw.itunda.feature.talk.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsAvatar
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CreateGroupRequest
import rw.itunda.core.network.GroupSummaryDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.StartConversationRequest
import rw.itunda.core.network.TalkContactDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException


// Real Kakao Friends-tab equivalent (item 237, sourced -- Kakao's Sept 2025 attempt
// to bury this tab caused a rating collapse and was reverted within 3 months, per
// docs/DESIGN_REFERENCES.md's Talk section recommendation #1). Talk only ever let a
// user switch between chat-*history* views (Direct/Groups) -- no way to browse
// contacts who are on itunda but you haven't messaged yet. The backend infra
// (getTalkContacts/getPresence) was already fully real and already used inline in the
// New-chat/add-member composers -- this is a client-only addition, no new endpoint.
// bank-mfe shipped this first (2026-08-06); this is the Android port.
@Composable
internal fun FriendsView(onStarted: (String) -> Unit) {
    var contacts by remember { mutableStateOf<List<TalkContactDto>?>(null) }
    var presence by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var startingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getTalkContacts()
                val list = if (res.success) res.contacts else emptyList()
                contacts = list
                error = null
                if (list.isNotEmpty()) {
                    try {
                        val presenceRes = NetworkClient.apiService.getPresence(list.map { it.userId })
                        if (presenceRes.success) presence = presenceRes.presence
                    } catch (_: Exception) {
                        // Real, non-critical -- only backs the online-status dot.
                    }
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun startChat(contact: TalkContactDto) {
        startingId = contact.userId
        error = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.startConversation(StartConversationRequest(otherUserId = contact.userId))
                if (res.success) onStarted(res.conversation.id)
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                startingId = null
            }
        }
    }

    val list = contacts
    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        list == null -> SkeletonBlock()
        list.isEmpty() -> EmptyState("No friends yet -- save someone's contact and they'll show up here once they're on itunda.", icon = Icons.Outlined.PersonOutline)
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.userId }) { contact ->
                Card(
                    shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                    colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
                    modifier = Modifier.fillMaxWidth().clickable(enabled = startingId != contact.userId) { startChat(contact) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(Ids.colors.brand.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.PersonOutline, contentDescription = null, tint = Ids.colors.brand) }
                            if (presence[contact.userId] == true) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Ids.colors.success),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            if (presence[contact.userId] == true) {
                                Text("Active now", color = Ids.colors.success, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                        if (startingId == contact.userId) {
                            Text("…", color = Ids.colors.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupsList(
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
                    IdsTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "Group name",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    IdsTextField(
                        value = phoneNumbers,
                        onValueChange = { phoneNumbers = it },
                        label = "Members",
                        placeholder = "+250788123456, +250788987654",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    IdsButton(
                        text = if (creating) "Creating…" else "Create group",
                        enabled = !creating && name.isNotBlank() && phoneNumbers.isNotBlank(),
                        onClick = {
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
                        },
                        size = IdsButtonSize.Medium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    createError?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
        }
        if (error != null) {
            item { ErrorCard(error, onRetry = onRetry) }
        } else if (groups == null) {
            item { SkeletonBlock() }
        } else if (groups.isEmpty()) {
            item { EmptyState("No groups yet — start one to chat with more than one person at a time.", icon = Icons.Outlined.ChatBubbleOutline) }
        } else {
            items(groups, key = { it.groupId }) { g -> GroupRow(g, onClick = { onOpen(g.groupId) }) }
        }
    }
}

@Composable
internal fun GroupRow(group: GroupSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Real fix, found live 2026-08-05: GroupRow had no avatar at all, unlike
        // ConversationRow -- an inconsistency between the two list types on the same
        // screen. group.photoUrl has been real since 2026-07-28 (setGroupPhotoUrl);
        // this is the first client surface to actually render it in the list.
        IdsAvatar(name = group.name, photoUrl = group.photoUrl)
        Spacer(modifier = Modifier.width(12.dp))
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

