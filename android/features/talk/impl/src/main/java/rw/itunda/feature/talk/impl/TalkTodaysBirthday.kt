package rw.itunda.feature.talk.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.StartConversationRequest
import rw.itunda.core.network.TalkContactDto

// Real KakaoTalk "오늘의 생일" (Today's Birthday) -- ported from bank-mfe (2026-09-03).
// KakaoTalk's own real feature shows friends with a birthday today at the top of the
// friend list, letting you message them directly without hunting through the full
// contact list. Renders nothing when the caller has no real contacts with a birthday
// today -- never an empty placeholder. Extracted out of TalkGroupsBrowse.kt the same
// day it was added (first crossing of the 500-line file-size guideline).
@Composable
internal fun TodaysBirthdaySection(onOpenConversation: (String) -> Unit) {
    var birthdays by remember { mutableStateOf<List<TalkContactDto>?>(null) }
    var startingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        birthdays = try { NetworkClient.apiService.getTodaysBirthdays().contacts } catch (_: Exception) { emptyList() }
    }

    val list = birthdays
    if (list.isNullOrEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ids.colors.pressed).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("🎂 Today's birthday", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        list.forEach { contact ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Ids.colors.surface)
                    .pressScaleClickable(enabled = startingId != contact.userId) {
                        startingId = contact.userId
                        coroutineScope.launch {
                            try {
                                val res = NetworkClient.apiService.startConversation(StartConversationRequest(otherUserId = contact.userId))
                                if (res.success) onOpenConversation(res.conversation.id)
                            } catch (_: Exception) {
                                // Fails quietly -- the user can still reach this same person
                                // from the regular friends list below.
                            } finally {
                                startingId = null
                            }
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(contact.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    if (startingId == contact.userId) "…" else "Say happy birthday",
                    color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                )
            }
        }
    }
}
