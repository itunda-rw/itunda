package rw.itunda.feature.talk.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallMissed
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Videocam
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.IdsSegmentedControl
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.CallSessionDto
import rw.itunda.core.network.ConversationSummaryDto
import rw.itunda.core.network.NetworkClient
import java.io.IOException

// Real KakaoTalk 전체/안읽음/통화 chat-list filter tabs (itunda Talk redesign,
// 2026-08-28). 전체/안읽음 are pure client-side filters over the already-loaded
// conversation list's real unreadCount -- no backend call. 통화 shows real call
// history from the new calling backend; this pass is call-log-display only, no
// dial/answer/end UI (that's a separate, later pass -- see CallService.kt's own
// doc comment on why calling is scoped to 1:1 this pass).
internal enum class TalkDirectFilter(val label: String) { ALL("전체"), UNREAD("안읽음"), CALLS("통화") }

@Composable
internal fun TalkFilterTabsRow(selected: TalkDirectFilter, onSelect: (TalkDirectFilter) -> Unit) {
    IdsSegmentedControl(
        options = TalkDirectFilter.entries.map { it to it.label },
        selected = selected,
        onSelect = onSelect,
        modifier = Modifier.padding(bottom = Ids.layout.cardGap),
    )
}

@Composable
internal fun CallHistoryList(conversations: List<ConversationSummaryDto>?) {
    var calls by remember { mutableStateOf<List<CallSessionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix
    // -- see project_itunda_pagination_discard_sweep memory): the retrofit
    // method already accepted page/size, but this screen never sent
    // anything past page 0 or exposed a way to load more.
    var callsPage by remember { mutableStateOf(0) }
    var callsHasMore by remember { mutableStateOf(false) }
    var loadingMoreCalls by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            val res = NetworkClient.talkApi.getCallHistory(page = 0)
            if (res.success) {
                calls = res.calls
                callsHasMore = res.page + 1 < res.totalPages
            }
            error = null
        } catch (_: IOException) {
            error = "Couldn't reach itunda. Check your connection and try again."
        } catch (_: Exception) {
            error = "Couldn't load call history."
        }
    }
    LaunchedEffect(Unit) { load() }

    suspend fun loadMore() {
        val nextPage = callsPage + 1
        loadingMoreCalls = true
        try {
            val res = NetworkClient.talkApi.getCallHistory(page = nextPage)
            if (res.success) {
                calls = (calls ?: emptyList()) + res.calls
                callsPage = nextPage
                callsHasMore = res.page + 1 < res.totalPages
            }
        } catch (_: Exception) {
            // Non-critical -- leave state as-is, the button just stays visible to retry.
        } finally {
            loadingMoreCalls = false
        }
    }

    // Real conversation-name lookup -- CallSessionDto only carries raw
    // caller/calleeId, no display name. Cross-referencing the already-loaded
    // conversation list by conversationId (not by user id -- more reliable, avoids
    // needing to know which id is "me") gives a real name; a call whose conversation
    // fell out of the active list (e.g. archived) honestly falls back to "Contact",
    // never a fabricated name.
    val nameByConversationId = remember(conversations) { conversations.orEmpty().associate { it.conversationId to it.otherUserName } }

    val coroutineScope = rememberCoroutineScope()
    when {
        error != null -> ErrorCard(error!!, onRetry = {})
        calls == null -> SkeletonBlock()
        calls!!.isEmpty() -> EmptyState("No calls yet.", icon = Icons.Outlined.Call)
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(calls!!, key = { it.id }) { call -> CallHistoryRow(call, nameByConversationId[call.conversationId] ?: "Contact") }
            if (callsHasMore) {
                item {
                    IdsButton(
                        text = if (loadingMoreCalls) "Loading…" else "Load more",
                        onClick = { coroutineScope.launch { loadMore() } },
                        enabled = !loadingMoreCalls,
                        variant = IdsButtonVariant.Tinted,
                        size = IdsButtonSize.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun CallHistoryRow(call: CallSessionDto, name: String) {
    val missed = call.answeredAt == null
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (missed) Icons.Outlined.CallMissed else if (call.callType == "VIDEO") Icons.Outlined.Videocam else Icons.Outlined.Phone,
            contentDescription = null,
            tint = if (missed) Ids.colors.danger else Ids.colors.textTertiary,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            name,
            color = if (missed) Ids.colors.danger else Ids.colors.textPrimary,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Text(call.startedAt.take(10), color = Ids.colors.textTertiary, fontSize = 12.sp)
    }
}
