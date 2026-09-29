package rw.itunda.feature.talk.impl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddReaction
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ConversationSummaryDto
import rw.itunda.core.network.ForwardMessageRequest
import rw.itunda.core.network.GroupSummaryDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ReactionGroupDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException


// Real quick-react palette (2026-07-19) -- a small fixed set matching bank-mfe's own
// MessageReactions component exactly, kept simple rather than a full emoji picker.
internal val QUICK_REACTIONS = listOf("👍", "❤️", "😂", "😮", "😢")

@Composable
internal fun MessageReactionsRow(reactions: List<ReactionGroupDto>, currentUserId: String?, isMine: Boolean, onToggle: (String) -> Unit) {
    var pickerOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        reactions.filter { it.userIds.isNotEmpty() }.forEach { r ->
            val mine = currentUserId != null && r.userIds.contains(currentUserId)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (mine) Ids.colors.brand.copy(alpha = 0.15f) else Ids.colors.surfaceSoft)
                    .pressScaleClickable { onToggle(r.emoji) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                ReactionGlyph(r.emoji, size = 12.dp)
                Text("${r.userIds.size}", fontSize = 11.sp, color = Ids.colors.textSecondary)
            }
        }
        Box {
            Icon(
                Icons.Outlined.AddReaction,
                contentDescription = "Add reaction",
                tint = Ids.colors.textSecondary,
                modifier = Modifier.size(16.dp).clip(CircleShape).pressScaleClickable { pickerOpen = !pickerOpen },
            )
            if (pickerOpen) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ids.colors.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        ReactionGlyph(
                            emoji,
                            size = 22.dp,
                            modifier = Modifier.padding(2.dp).pressScaleClickable {
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

// Real message forwarding (2026-08-04) -- see ForwardMessageRequest's own doc comment
// for the full "defined but uncalled" backend account (MessageForwardService, real since
// 2026-07-25). isGroupSource picks which pair of Retrofit methods to call since the
// source message's own origin (1:1 vs group) determines the real endpoint URL; the
// destination the user picks independently determines DIRECT vs GROUP per selection.
// Capped at 10 total destinations, matching Kakao's own real, sourced limit (References
// table) -- enforced by disabling further selection past 10, not just documented.
@Composable
internal fun ForwardDestinationDialog(sourceMessageId: String, isGroupSource: Boolean, onDismiss: () -> Unit) {
    var conversations by remember { mutableStateOf<List<ConversationSummaryDto>?>(null) }
    var groups by remember { mutableStateOf<List<GroupSummaryDto>?>(null) }
    var selectedConversationIds by remember { mutableStateOf(setOf<String>()) }
    var selectedGroupIds by remember { mutableStateOf(setOf<String>()) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val totalSelected = selectedConversationIds.size + selectedGroupIds.size

    LaunchedEffect(Unit) {
        try { conversations = NetworkClient.apiService.getConversations().conversations } catch (_: Exception) { conversations = emptyList() }
        try { groups = NetworkClient.apiService.getMyGroups().groups } catch (_: Exception) { groups = emptyList() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (done) "Forwarded" else "Forward to (up to 10)") },
        text = {
            if (done) {
                Text("Sent to $totalSelected destination${if (totalSelected == 1) "" else "s"}.", color = Ids.colors.textPrimary)
            } else {
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp)) }
                    if (conversations == null || groups == null) {
                        Text("Loading…", color = Ids.colors.textSecondary)
                    } else {
                        (conversations ?: emptyList()).forEach { c ->
                            val checked = c.conversationId in selectedConversationIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .pressScaleClickable(enabled = checked || totalSelected < 10) {
                                        selectedConversationIds = if (checked) selectedConversationIds - c.conversationId else selectedConversationIds + c.conversationId
                                    }
                                    .padding(vertical = 4.dp),
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, enabled = checked || totalSelected < 10)
                                Text(c.otherUserName, fontSize = 14.sp, color = Ids.colors.textPrimary, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                        (groups ?: emptyList()).forEach { g ->
                            val checked = g.groupId in selectedGroupIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                                    .pressScaleClickable(enabled = checked || totalSelected < 10) {
                                        selectedGroupIds = if (checked) selectedGroupIds - g.groupId else selectedGroupIds + g.groupId
                                    }
                                    .padding(vertical = 4.dp),
                            ) {
                                Checkbox(checked = checked, onCheckedChange = null, enabled = checked || totalSelected < 10)
                                Text("${g.name} (group)", fontSize = 14.sp, color = Ids.colors.textPrimary, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (done) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                TextButton(
                    enabled = totalSelected > 0 && !sending,
                    onClick = {
                        sending = true
                        error = null
                        coroutineScope.launch {
                            try {
                                selectedConversationIds.forEach { destId ->
                                    val req = ForwardMessageRequest("DIRECT", destId)
                                    if (isGroupSource) NetworkClient.apiService.forwardGroupMessageToConversation(sourceMessageId, req)
                                    else NetworkClient.apiService.forwardDirectMessageToConversation(sourceMessageId, req)
                                }
                                selectedGroupIds.forEach { destId ->
                                    val req = ForwardMessageRequest("GROUP", destId)
                                    if (isGroupSource) NetworkClient.apiService.forwardGroupMessageToGroup(sourceMessageId, req)
                                    else NetworkClient.apiService.forwardDirectMessageToGroup(sourceMessageId, req)
                                }
                                done = true
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                sending = false
                            }
                        }
                    },
                ) { Text(if (sending) "…" else if (totalSelected > 0) "Send ($totalSelected)" else "Send") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// Real per-thread shared-media gallery (2026-08-04, Kakao's real "Chat Room Drawer")
// -- see GroupThreadView's own doc comment for the full sourced account and the honest
// photos-only scope. imageUrls is already in newest-first order (reversed by the caller
// from the thread's own oldest-first message list) -- a real Chat Room Drawer shows the
// most recently shared media first.
@Composable
internal fun MediaGalleryView(imageUrls: List<String>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            TextButton(onClick = onBack) { Text("← Back", color = Ids.colors.textSecondary) }
            Text("Shared photos (${imageUrls.size})", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, modifier = Modifier.padding(start = 4.dp))
        }
        if (imageUrls.isEmpty()) {
            Text("No photos shared in this conversation yet.", color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                gridItems(imageUrls) { url ->
                    AsyncImage(model = url, contentDescription = "Shared photo", contentScale = ContentScale.Crop, modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}

// Real KakaoTalk Emoticon Store (item 135) -- a received emoticon renders as just the
// sticker image, no chat-bubble background, matching real KakaoTalk and bank-mfe's own
// EmoticonBubble (item 133).
@Composable
internal fun EmoticonBubble(imageUrl: String?) {
    if (imageUrl == null) {
        Text("[emoticon]", color = Ids.colors.textSecondary, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
        return
    }
    AsyncImage(model = imageUrl, contentDescription = "emoticon", modifier = Modifier.size(96.dp))
}

