package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

internal data class EatsCheckoutGroupMember(
    val name: String,
    val itemsSummary: String,
    val amount: Double,
)

internal data class EatsCheckoutGroupSummary(
    val peopleCount: Int,
    val yourAmount: Double,
    val members: List<EatsCheckoutGroupMember>,
    val friendsTotal: Double,
    val splitBill: Boolean,
)

@Composable
internal fun EatsCheckoutGroupSplitCard(
    summary: EatsCheckoutGroupSummary,
    palette: EatsPrototypePalette,
    onManage: (() -> Unit)?,
    onSplitBillChange: (Boolean) -> Unit,
) {
    var splitBill by remember(summary) { mutableStateOf(summary.splitBill) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceSoft).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Group order · ${summary.peopleCount} people",
                color = palette.primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            if (onManage != null) {
                Text(
                    "Manage",
                    color = palette.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.pressScaleClickable(onClick = onManage).padding(start = 8.dp, vertical = 4.dp),
                )
            }
        }
        EatsCheckoutGroupMemberRow("You", "", summary.yourAmount, palette)
        summary.members.forEach { member ->
            EatsCheckoutGroupMemberRow(member.name, member.itemsSummary, member.amount, palette)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EatsCheckoutGroupModeButton(
                label = "I pay for everyone",
                selected = !splitBill,
                palette = palette,
                modifier = Modifier.weight(1f),
                onClick = {
                    splitBill = false
                    onSplitBillChange(false)
                },
            )
            EatsCheckoutGroupModeButton(
                label = "Split the bill",
                selected = splitBill,
                palette = palette,
                modifier = Modifier.weight(1f),
                onClick = {
                    splitBill = true
                    onSplitBillChange(true)
                },
            )
        }
        if (splitBill && summary.members.isNotEmpty()) {
            Text(
                "You pay now. Each friend gets a request for their items: ${formatEatsRwf(summary.friendsTotal)} in total.",
                color = palette.secondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun EatsCheckoutGroupMemberRow(
    name: String,
    itemsSummary: String,
    amount: Double,
    palette: EatsPrototypePalette,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (itemsSummary.isNotBlank()) {
                Text(itemsSummary, color = palette.secondary, fontSize = 12.sp)
            }
        }
        Text(formatEatsRwf(amount), color = palette.primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EatsCheckoutGroupModeButton(
    label: String,
    selected: Boolean,
    palette: EatsPrototypePalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Box(
        modifier.height(44.dp).clip(RoundedCornerShape(14.dp))
            .background(if (selected) palette.accentTint else palette.background)
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) palette.accent else palette.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
