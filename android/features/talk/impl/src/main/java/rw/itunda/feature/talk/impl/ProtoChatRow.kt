package rw.itunda.feature.talk.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.ConversationSummaryDto

// People without a profile photo get one of itunda's own character faces, picked from their name so it stays stable.
private val chatFaces = listOf("shopkeeper", "farmer", "student", "teacher", "fisherman", "moto-rider", "tea-picker", "community-health-worker", "intore-dancer", "momo-agent")

@Composable
internal fun ChatFace(name: String, size: Int) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
        ItundaArt(chatFaces[Math.floorMod(name.hashCode(), chatFaces.size)], ItundaArtSet.Face, (size * 0.8f).dp)
    }
}

/** "now", "8 min", "1 h", "Yesterday", or a short date -- the prototype's own row timestamps. */
internal fun chatTimeLabel(iso: String, now: java.time.Instant = java.time.Instant.now()): String = try {
    val t = java.time.Instant.parse(iso)
    val mins = java.time.Duration.between(t, now).toMinutes()
    when {
        mins < 1 -> "now"
        mins < 60 -> "$mins min"
        mins < 24 * 60 -> "${mins / 60} h"
        mins < 48 * 60 -> "Yesterday"
        else -> java.time.LocalDate.ofInstant(t, java.time.ZoneId.systemDefault()).let { "${it.dayOfMonth} ${it.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)}" }
    }
} catch (_: Exception) { "" }

/**
 * Messages row as in the prototype: 48dp avatar, name with the time inline, one-line preview
 * (bold and dark when unread), purple count badge on the right. Long-press toggles pin-to-top.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProtoChatRow(conversation: ConversationSummaryDto, online: Boolean, onClick: () -> Unit, onPinToggle: (() -> Unit)?) {
    val unread = conversation.unreadCount > 0
    Row(
        Modifier.fillMaxWidth().height(80.dp).combinedClickable(onClick = onClick, onLongClick = onPinToggle).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.BottomEnd) {
            ChatFace(conversation.otherUserName, 48)
            if (online) Box(Modifier.size(12.dp).clip(CircleShape).background(Ids.colors.background).padding(2.dp).clip(CircleShape).background(Ids.colors.success))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(conversation.otherUserName, color = Ids.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("  ${chatTimeLabel(conversation.lastMessageAt)}", color = Ids.colors.textTertiary, fontSize = 15.sp, maxLines = 1)
            }
            Text(
                conversation.lastMessagePreview ?: "No messages yet",
                color = if (unread) Ids.colors.textPrimary else Ids.colors.textTertiary,
                fontSize = 16.sp, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (unread) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(26.dp).clip(CircleShape).background(Ids.colors.brand), contentAlignment = Alignment.Center) {
                Text("${conversation.unreadCount}", color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
/** Filter chips under the Messages header: the selected one is solid ink, the rest soft grey. */
@Composable
internal fun <T> ProtoChips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.height(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .background(if (on) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
                    .combinedClickable(onClick = { onSelect(value) }).padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.background else Ids.colors.textTertiary)
            }
        }
    }
}
