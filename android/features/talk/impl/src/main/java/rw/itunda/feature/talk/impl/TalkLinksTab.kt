package rw.itunda.feature.talk.impl

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient

// Real KakaoTalk Links tab (itunda Talk redesign, 2026-08-28) -- deliberately
// scoped to links only, NOT a general Files tab: no file-attachment-in-chat
// capability exists anywhere in this codebase today (only images), so a Files tab
// would be a fabricated capability. Client-side URL extraction over the real
// message history -- no new backend endpoint, this conversation's messages are
// already a real, complete source of truth. Independently fetches its own copy of
// message history (ChatThreadView, TalkChatThread.kt, keeps its own internal copy
// and is baseline-frozen at file-size-lint's cap, so its state can't be exposed
// here without editing that file) -- a second real fetch, not fabricated data.
private val URL_REGEX = Regex("""https?://\S+""")

@Composable
internal fun TalkLinksTabView(conversationId: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var links by remember { mutableStateOf<List<String>?>(null) }
    val context = LocalContext.current

    LaunchedEffect(conversationId) {
        links = try {
            val res = NetworkClient.apiService.getMessages(conversationId)
            if (res.success) {
                res.messages
                    .flatMap { URL_REGEX.findAll(it.body).map { m -> m.value.trimEnd('.', ',', ')') } }
                    .distinct()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar("Links", onBack)
        when {
            links == null -> SkeletonBlock()
            links!!.isEmpty() -> EmptyState("No links shared in this chat yet.", icon = Icons.Outlined.Link)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(links!!) { url ->
                    Text(
                        url,
                        color = Ids.colors.brand,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressScaleClickable {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (_: Exception) { /* real, non-critical -- a malformed URL just doesn't open */ }
                            }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}
