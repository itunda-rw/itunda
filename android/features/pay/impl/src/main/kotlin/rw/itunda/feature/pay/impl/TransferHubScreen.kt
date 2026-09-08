package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons

/**
 * Real Toss Bank 송금 (Transfer) full page (2026-07-24) -- Home's own "Send" button
 * previously jumped straight into the 2-step Recipient/Amount flow with no page in
 * between, unlike real Toss where Home shows only a thin curated teaser and every
 * money-feature area (Transfer, Pay, Account) has its own full dedicated page grouping
 * every related action (confirmed via real Toss screenshots: the 송금 page groups
 * 송금하기/자동이체/해외송금/더치페이/etc, not just a single send action). This groups
 * itunda's own equivalents -- Send money now, real Auto-transfer (see AutoTransfer.kt's
 * own doc comment on the backend), and Transfer history -- the same real
 * curated-home-plus-full-page structure, scoped to what itunda actually has rather than
 * inventing Toss features itunda has no backend for (overseas remittance, political
 * donations).
 */
@Composable
fun TransferHubScreen(
    autoTransferCount: Int,
    onBack: () -> Unit,
    onSendMoney: () -> Unit,
    onOpenAutoTransfers: () -> Unit,
    onOpenScheduledTransfers: () -> Unit,
    onOpenDelayedTransfers: () -> Unit,
    onSplitBill: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Ids.colors.background)) {
        BackTopBar("Transfer", onBack)
        Column(modifier = Modifier.padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
            TransferHubRow(
                icon = Icons.AutoMirrored.Outlined.Send,
                title = "Send money",
                subtitle = "Account number · contact",
                onClick = onSendMoney,
            )
            Spacer(Modifier.height(8.dp))
            TransferHubRow(
                icon = Icons.Outlined.AutoMode,
                title = "Auto-transfer",
                subtitle = if (autoTransferCount > 0) "$autoTransferCount active" else "Set up a recurring transfer",
                onClick = onOpenAutoTransfers,
            )
            Spacer(Modifier.height(8.dp))
            // Real Toss 예약송금 (scheduled/reserved one-time transfer) row (2026-08-04)
            // -- see ScheduledTransferDto's own doc comment on the backend. Genuinely
            // distinct from Auto-transfer above (recurring): explicitly one-time on a
            // single future date.
            TransferHubRow(
                icon = Icons.Outlined.Schedule,
                title = "Scheduled transfer",
                subtitle = "Send on a future date, one time",
                onClick = onOpenScheduledTransfers,
            )
            Spacer(Modifier.height(8.dp))
            // Real Korean 지연이체서비스 (Delayed Transfer Service) row -- see
            // DelayedTransferListScreen.kt's own doc comment. Already real on iOS/web;
            // this closes the Android-only gap.
            TransferHubRow(
                icon = Icons.Outlined.Shield,
                title = "Delayed transfer",
                subtitle = "Send safely, cancel within a few hours",
                onClick = onOpenDelayedTransfers,
            )
            Spacer(Modifier.height(8.dp))
            // Real 더치페이 (Split bill) row (2026-07-24) -- completes real Toss's
            // own 송금 page grouping (송금하기/자동이체/더치페이), deliberately
            // deferred when this screen was first built. Split Bill itself already
            // lives inside a group's own Talk thread (see MenuScreen's identical
            // "Split a bill with friends" row) -- this just adds the same real
            // entry point here too, not a second implementation.
            TransferHubRow(
                icon = Icons.Outlined.Groups,
                title = "Split a bill",
                subtitle = "Settle up with friends in Talk",
                onClick = onSplitBill,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Transfer history",
                color = Ids.colors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .pressScaleClickable(onClick = onOpenHistory)
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TransferHubRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
            .background(Ids.colors.surface)
            .pressScaleClickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Ids.colors.brand, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = Ids.colors.textSecondary, fontSize = 12.sp)
        }
        Icon(IdsIcons.ChevronRight, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp))
    }
}
