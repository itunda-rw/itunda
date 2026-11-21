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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import java.util.Locale

/** Prototype itunda Bank bar: back, 20sp title, gear (48dp buttons, 70dp tall). Fixed above the scrolling hub. */
@Composable
internal fun BankHubBar(title: String, onBack: () -> Unit, onSettings: () -> Unit) {
    BankSubTopBar(title, onBack) {
        Box(Modifier.size(48.dp).pressScaleClickable(onClick = onSettings), contentAlignment = Alignment.Center) {
            ItundaArt("gear", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
        }
    }
}

/** Prototype `.b7a` .. `.b7bd`: account label, balance, interest line, recent activity, Deposit / Send, divider band. Scrolls with the hub. */
@Composable
internal fun BankHubBalance(
    title: String, accountNumber: String, balance: Double, currency: String,
    onOpenAccountDetail: () -> Unit, onDeposit: () -> Unit, onSend: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().pressScaleClickable(onClick = onOpenAccountDetail).padding(start = 20.dp, end = 20.dp, top = 19.dp)) {
            Text("$title \u2022\u2022${accountNumber.takeLast(4)}", color = Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 22.sp,)
            Row(Modifier.padding(top = 4.dp).fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(String.format(Locale.US, "%,.0f", balance), color = Ids.colors.textPrimary, fontSize = 34.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
                    Text(" $currency", color = Ids.colors.textPrimary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 3.dp))
                }
                ItundaArt("chev", ItundaArtSet.Line, 18.dp, tint = Ids.colors.textTertiary)
            }
        }
        Text("Interest 1,462 $currency so far", color = Ids.colors.textBrand, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(20.dp))
        RecentActivity()
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand.copy(alpha = 0.15f)).pressScaleClickable(onClick = onDeposit),
                contentAlignment = Alignment.Center,
            ) { Text("Deposit", color = Ids.colors.textBrand, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp) }
            Box(
                Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.brand).pressScaleClickable(onClick = onSend),
                contentAlignment = Alignment.Center,
            ) { Text("Send", color = Color(0xFF17171C), fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp) }
        }
        Box(Modifier.fillMaxWidth().height(12.dp).background(Ids.colors.surfaceSoft))
        Spacer(Modifier.height(6.dp))
    }
}

// Dummy recent activity, as in the prototype, until the hub reads real transactions.
private data class RecentRow(val date: String, val emoji: String, val name: String, val amount: String)

private val recentRows = listOf(
    RecentRow("", "\uD83D\uDE0A", "Amina K.", "+25,000 RWF"),
    RecentRow("", "\uD83C\uDFE0", "Rent", "\u2212150,000 RWF"),
    RecentRow("", "\uD83D\uDE42", "Jean Paul", "\u221230,000 RWF"),
)

@Composable
private fun RecentActivity() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        val yesterday = java.time.LocalDate.now().minusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        recentRows.forEachIndexed { i, r ->
            Row(Modifier.height(25.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (i == 0) yesterday else r.date, color = Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 22.sp, modifier = Modifier.width(64.dp))
                Text(r.emoji, fontSize = 20.sp)
                Text(r.name, color = Ids.colors.textPrimary, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text(r.amount, color = Ids.colors.textPrimary, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
