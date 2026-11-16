package rw.itunda.feature.banking.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.theme.Ids

/** Hub row opening Manage cards. */
@Composable
internal fun BankCardRow(onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().pressScaleClickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) { ItundaArt("card", ItundaArtSet.Color, 26.dp) }
        Text("Card", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 14.dp))
        Text("  ›", color = Ids.colors.textTertiary, fontSize = 20.sp)
    }
}

private class CardRow(val name: String, val balance: String, val from: Color, val to: Color)

private val cardRows = listOf(
    CardRow("itunda Pay Money", "120,000 RWF", Color(0xFF3DF58A), Color(0xFF06A845)),
    CardRow("itunda Bank", "842,300 RWF", Color(0xFFA9A5FF), Color(0xFF4B43C9)),
)

/** Prototype "Manage cards": the itunda wallets that pay at checkout (dummy balances). Linking outside cards is not built yet. */
@Composable
internal fun BankCardsScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
        BankSubTopBar("Manage cards", onBack)
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp)) {
            cardRows.forEach { c ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(44.dp).height(30.dp).clip(RoundedCornerShape(7.dp)).background(Brush.linearGradient(listOf(c.from, c.to))))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(c.name, color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(c.balance, color = Ids.colors.textTertiary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
