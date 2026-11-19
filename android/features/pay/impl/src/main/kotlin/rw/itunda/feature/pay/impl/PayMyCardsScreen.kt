package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

/** CSS `linear-gradient(158deg, ...)` over the box being drawn into. */
internal fun Modifier.cssGradient158(vararg stops: Pair<Float, Color>): Modifier = drawBehind {
    val dir = Offset(0.3746f, 0.9272f)
    val half = (size.width * 0.3746f + size.height * 0.9272f) / 2f
    drawRect(Brush.linearGradient(*stops, start = center - dir * half, end = center + dir * half))
}

/**
 * Prototype `mycards` ("Manage cards"): the two itunda accounts, then the cards the person added
 * (each with a Remove button), then "Add a card". The cards are only what the caller passes in:
 * full numbers are never held here.
 */
@Composable
fun PayMyCardsScreen(
    payBalance: String, bankBalance: String, cards: List<PayNewCard>, onBack: () -> Unit, onAddCard: () -> Unit, onRemove: (Int) -> Unit,
) {
    PaySubScreen(onBack = onBack, title = "Manage cards") { p ->
        Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp)) {
            CardRow("itunda Pay Money", "$payBalance RWF", p, Modifier.cssGradient158(0f to Color(0xFF3DF58A), 1f to Color(0xFF06A845)))
            CardRow("itunda Bank", "$bankBalance RWF", p, Modifier.cssGradient158(0f to Color(0xFFA9A5FF), 1f to Color(0xFF4B43C9)))
            cards.forEachIndexed { i, c ->
                CardRow(
                    "${c.brand} ·· ${c.last4}", "Expires ${c.expiry}", p,
                    when (c.brand) {
                        "Visa" -> Modifier.cssGradient158(0f to Color(0xFF3F6BFF), 1f to Color(0xFF0B1F6B))
                        "Mastercard" -> Modifier.cssGradient158(0f to Color(0xFFFF8A4C), 1f to Color(0xFF6E1B13))
                        else -> Modifier.cssGradient158(0f to Color(0xFF4FD1C5), 1f to Color(0xFF0C4A47))
                    },
                ) {
                    Box(
                        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(p.tint).pressScaleClickable { onRemove(i) }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Remove", color = p.tintFg, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).background(p.tint).pressScaleClickable(onClick = onAddCard),
                contentAlignment = Alignment.Center,
            ) { Text("Add a card", color = p.tintFg, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp) }
        }
    }
}

@Composable
private fun CardRow(title: String, sub: String, p: PayPalette, tile: Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(width = 64.dp, height = 32.dp).clip(RoundedCornerShape(8.dp)).then(tile))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 17.sp, lineHeight = 23.8.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
            Text(sub, color = p.tt, fontSize = 13.sp, lineHeight = 18.2.sp)
        }
        trailing?.invoke()
    }
}
