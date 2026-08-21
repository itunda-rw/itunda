package rw.itunda.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.RewardTaskDto

// Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up:
// "our pay home screen should also look 100% like toss pay home screen") -- split out
// of PayTab (ItundaAppScreen.kt) to keep that file under its file-size-lint baseline,
// matching the same convention this session's earlier PayMoneyDetailScreen.kt
// extraction already established. FacePayStatusRow replaces the reference's "Facepay
// Earning 3%" promo card -- itunda's real FacePayEnrollmentDto has no per-transaction
// cashback-rate field, so this states the real enrolled/not-enrolled fact rather than
// inventing a percentage. RewardsPreviewSection replaces the reference's "Get more
// rewards" list (Toss Prime / Google gift codes / etc, none of which itunda has a real
// backend for) with itunda's own already-real RewardsService task list
// (getRewardTasks, ApiService.kt), never surfaced on Android's Pay tab before -- a real
// preview capped at 3 unclaimed+eligible tasks, matching bank-mfe's identical fix.

@Composable
fun FacePayStatusRow(enrolled: Boolean, busy: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(38.dp).background(if (enrolled) Ids.colors.brand else Ids.colors.chip, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Face, contentDescription = null, tint = if (enrolled) Color.White else Ids.colors.textSecondary, modifier = Modifier.size(19.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("FacePay", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                Text(
                    if (enrolled) "Enrolled — pay with your face at any itunda merchant" else "Not enrolled",
                    fontSize = 12.sp, color = Ids.colors.textSecondary,
                )
            }
        }
        IdsButton(
            if (busy) "…" else if (enrolled) "Turn off" else "Enroll",
            onClick = onToggle,
            variant = if (enrolled) IdsButtonVariant.Tinted else IdsButtonVariant.Filled,
            size = IdsButtonSize.Small,
        )
    }
}

// No "View all" link to a dedicated Rewards screen -- Android has no native one
// (RewardsService is otherwise reached only through the Saronite RN mini-app
// bridge, not from a Compose screen). Each row is directly tap-to-claim instead
// (real claimRewardTask, ApiService.kt), the same "real destination, not a dead
// link" discipline this session's own doc comments repeatedly apply elsewhere.
@Composable
fun RewardsPreviewSection(tasks: List<RewardTaskDto>, claimingId: String?, onClaim: (String) -> Unit) {
    val preview = tasks.filter { !it.claimed && it.eligible }.take(3)
    if (preview.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Get more rewards", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
        preview.forEach { task ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(task.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                    Text(task.subtitle, fontSize = 12.sp, color = Ids.colors.textSecondary)
                }
                IdsButton(
                    if (claimingId == task.id) "…" else "+${task.rewardAmount.toInt()} RWF",
                    onClick = { onClaim(task.id) },
                    enabled = claimingId == null,
                    variant = IdsButtonVariant.Tinted,
                    size = IdsButtonSize.Small,
                )
            }
        }
    }
}
