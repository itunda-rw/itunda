package rw.itunda.feature.talk.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ServiceChannelBubbleDto
import rw.itunda.core.network.superAppErrorMessage
import retrofit2.HttpException
import java.io.IOException

// Real itunda service channel (itunda Talk redesign, 2026-08-28) -- itunda's own
// already-real automated Notification rows, rendered as a read-only alimtalk-style
// chat thread, matching the real 카카오페이/토스 channel-notification reference. No
// send capability -- this is a projection, not a real conversation. See backend
// ServiceChannelService's own doc comment for why there's no persisted conversation
// row backing this.
@Composable
internal fun ServiceChannelThreadView(onBack: () -> Unit, onNavigate: (String) -> Unit) {
    BackHandler(onBack = onBack)
    var bubbles by remember { mutableStateOf<List<ServiceChannelBubbleDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix
    // -- see project_itunda_pagination_discard_sweep memory): the retrofit
    // method already accepted page/size, but this screen never sent anything
    // past page 0 or exposed a way to load more.
    var bubblesPage by remember { mutableStateOf(0) }
    var bubblesHasMore by remember { mutableStateOf(false) }
    var loadingMoreBubbles by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val res = NetworkClient.talkApi.getServiceChannel(page = 0)
            if (res.success) {
                bubbles = res.bubbles
                bubblesHasMore = res.page + 1 < res.totalPages
            }
            error = null
        } catch (e: HttpException) {
            error = superAppErrorMessage(e)
        } catch (e: IOException) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }
    LaunchedEffect(Unit) { load() }

    suspend fun loadMore() {
        val nextPage = bubblesPage + 1
        loadingMoreBubbles = true
        try {
            val res = NetworkClient.talkApi.getServiceChannel(page = nextPage)
            if (res.success) {
                bubbles = (bubbles ?: emptyList()) + res.bubbles
                bubblesPage = nextPage
                bubblesHasMore = res.page + 1 < res.totalPages
            }
        } catch (_: Exception) {
            // Non-critical -- leave state as-is, the button just stays visible to retry.
        } finally {
            loadingMoreBubbles = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar("itunda", onBack)
        when {
            error != null -> ErrorCard(error!!, onRetry = {})
            bubbles == null -> SkeletonBlock()
            bubbles!!.isEmpty() -> EmptyState("No itunda notifications yet.", icon = Icons.Outlined.NotificationsNone)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                items(bubbles!!, key = { it.id }) { bubble -> ServiceChannelBubbleRow(bubble, onNavigate) }
                if (bubblesHasMore) {
                    item {
                        IdsButton(
                            text = if (loadingMoreBubbles) "Loading…" else "Load more",
                            onClick = { coroutineScope.launch { loadMore() } },
                            enabled = !loadingMoreBubbles,
                            variant = IdsButtonVariant.Tinted,
                            size = IdsButtonSize.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceChannelBubbleRow(bubble: ServiceChannelBubbleDto, onNavigate: (String) -> Unit) {
    val ctaRoute = bubble.ctaRoute
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
            .background(Ids.colors.surfaceSoft)
            .then(
                if (ctaRoute != null) Modifier.pressScaleClickable { onNavigate(ctaRoute) } else Modifier,
            )
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(bubble.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
                if (!bubble.isRead) {
                    Box(modifier = Modifier.padding(start = 6.dp).size(6.dp).clip(CircleShape).background(Ids.colors.brand))
                }
            }
            Text(bubble.body, color = Ids.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
