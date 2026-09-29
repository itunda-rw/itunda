package rw.itunda.feature.talk.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AiChatMessageDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.SendAiChatMessageRequest
import java.io.IOException

// Real AI chatbot channel (itunda Talk redesign, 2026-08-28), matching the real
// "ChatGPT for Kakao" bot-channel reference -- reuses the self-hosted llama-server via
// the new AiChatService. A 429 ("busy") response means the one shared model instance
// is already answering someone else right now -- rendered as a real, always-visible
// system bubble (never a toast that disappears), matching this app's own established
// AI-honesty-disclosure convention (see PlaceAiSummaryCard's "AI" badge).
@Composable
internal fun AiChatThreadView(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var messages by remember { mutableStateOf<List<AiChatMessageDto>?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var busyNotice by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix
    // -- see project_itunda_pagination_discard_sweep memory): the retrofit
    // method already accepted page/size, but this screen never sent anything
    // past page 0 or exposed a way to load older messages. Older pages get
    // reversed the same way as page 0 and PREPENDED once loaded.
    var historyPage by remember { mutableStateOf(0) }
    var olderMessagesHasMore by remember { mutableStateOf(false) }
    var loadingOlderMessages by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun loadHistory() {
        try {
            val res = NetworkClient.talkApi.getAiChatHistory(page = 0)
            // Real fix, found live on a physical device (2026-08-29): the backend
            // returns history newest-first (a real pagination convention), but this
            // screen renders top-down in chronological reading order like every
            // other Talk thread, and appends new sends to the END of the list --
            // rendering the raw newest-first response put the most recent exchange
            // at the TOP of the screen instead of the bottom, backwards from every
            // real chat convention (and inconsistent with the order right after a
            // send, which appends and therefore reads correctly). Reverse once here
            // so `messages` is always oldest-first, matching send()'s own append.
            if (res.success) {
                messages = res.messages.reversed()
                olderMessagesHasMore = res.page + 1 < res.totalPages
            }
        } catch (_: Exception) {
            messages = emptyList()
        }
    }
    LaunchedEffect(Unit) { loadHistory() }

    suspend fun loadOlderMessages() {
        val nextPage = historyPage + 1
        loadingOlderMessages = true
        try {
            val res = NetworkClient.talkApi.getAiChatHistory(page = nextPage)
            if (res.success) {
                messages = res.messages.reversed() + (messages ?: emptyList())
                historyPage = nextPage
                olderMessagesHasMore = res.page + 1 < res.totalPages
            }
        } catch (_: Exception) {
            // Non-critical -- leave state as-is, the button just stays visible to retry.
        } finally {
            loadingOlderMessages = false
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        input = ""
        sending = true
        busyNotice = false
        error = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.talkApi.sendAiChatMessage(SendAiChatMessageRequest(text))
                if (res.success) messages = (messages ?: emptyList()) + res.message + res.reply
            } catch (e: HttpException) {
                if (e.code() == 429) {
                    busyNotice = true
                } else {
                    error = "itunda AI couldn't respond just now -- try again shortly."
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                sending = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar("itunda AI", onBack)
        Box(modifier = Modifier.weight(1f)) {
            when {
                messages == null -> SkeletonBlock()
                messages!!.isEmpty() && !busyNotice && error == null -> EmptyState("Ask itunda AI anything -- replies are generated by a small, self-hosted model.", icon = Icons.Outlined.SmartToy)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    if (olderMessagesHasMore) {
                        item {
                            IdsButton(
                                text = if (loadingOlderMessages) "Loading…" else "Load older messages",
                                onClick = { coroutineScope.launch { loadOlderMessages() } },
                                enabled = !loadingOlderMessages,
                                variant = IdsButtonVariant.Tinted,
                                size = IdsButtonSize.Medium,
                            )
                        }
                    }
                    items(messages.orEmpty(), key = { it.id }) { m -> AiChatBubble(m) }
                    if (busyNotice) item { AiSystemNotice("itunda AI is busy right now -- try again shortly.") }
                    if (error != null) item { AiSystemNotice(error!!) }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IdsTextField(value = input, onValueChange = { input = it }, label = "Message", placeholder = "Message itunda AI", modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sending || input.isBlank()) Ids.colors.textTertiary else Ids.colors.brand)
                    .pressScaleClickable(enabled = !sending && input.isNotBlank()) { send() }
                    .padding(12.dp),
            ) {
                Icon(Icons.Outlined.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AiChatBubble(message: AiChatMessageDto) {
    val isAssistant = message.role == "assistant"
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isAssistant) Arrangement.Start else Arrangement.End) {
        Column(horizontalAlignment = if (isAssistant) Alignment.Start else Alignment.End, modifier = Modifier.widthIn(max = 280.dp)) {
            if (isAssistant) {
                Text(
                    "AI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .background(Ids.colors.brand, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isAssistant) Ids.colors.surfaceSoft else Ids.colors.brand)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(message.content, color = if (isAssistant) Ids.colors.textPrimary else Color.White, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun AiSystemNotice(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Ids.colors.surfaceSoft)
            .padding(10.dp),
    ) {
        Text(text, color = Ids.colors.textSecondary, fontSize = 12.sp)
    }
}
