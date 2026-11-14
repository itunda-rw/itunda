package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import java.util.Locale

/** Prototype itunda Bank top: back + title, account label with last digits, big balance, Deposit / Send. */
@Composable
internal fun BankHubHeader(
    title: String,
    accountNumber: String,
    balance: Double,
    currency: String,
    onBack: () -> Unit,
    onOpenAccountDetail: () -> Unit,
    onDeposit: () -> Unit,
    onSend: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
                ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
            }
            Text(title, color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenAccountDetail).padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text("$title ••${accountNumber.takeLast(4)}", color = Ids.colors.textTertiary, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(String.format(Locale.US, "%,.0f", balance), color = Ids.colors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Text(" $currency", color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(
            "Interest 1,462 $currency so far", color = Ids.colors.textBrand, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(16.dp))
        RecentActivity()
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand.copy(alpha = 0.15f)).pressScaleClickable(onClick = onDeposit),
                contentAlignment = Alignment.Center,
            ) { Text("Deposit", color = Ids.colors.textBrand, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand).pressScaleClickable(onClick = onSend),
                contentAlignment = Alignment.Center,
            ) { Text("Send", color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

// Dummy recent activity, as in the prototype, until the hub reads real transactions.
private data class RecentRow(val date: String, val emoji: String, val name: String, val amount: String, val incoming: Boolean)

private val recentRows = listOf(
    RecentRow("12 Nov", "\uD83D\uDE0A", "Amina K.", "+25,000 RWF", true),
    RecentRow("", "\uD83C\uDFE0", "Rent", "\u2212150,000 RWF", false),
    RecentRow("", "\uD83D\uDE42", "Jean Paul", "\u221230,000 RWF", false),
)

@Composable
private fun RecentActivity() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        recentRows.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.date, color = Ids.colors.textTertiary, fontSize = 14.sp, modifier = Modifier.width(52.dp))
                Text(r.emoji, fontSize = 20.sp)
                Text(r.name, color = Ids.colors.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(r.amount, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
