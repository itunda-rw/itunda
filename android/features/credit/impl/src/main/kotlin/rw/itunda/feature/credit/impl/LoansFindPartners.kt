package rw.itunda.feature.credit.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

private data class LoanPartner(val badge: String, val tint: Color, val title: String, val detail: String)

private val partners = listOf(
    LoanPartner("B", Color(0xFF5B54D6), "itunda Bank loans", "Small loans for people with a steady income. Repay monthly, no hidden fees."),
    LoanPartner("M", Color(0xFF1FA67A), "Mortgage loan", "Buy or build a home. Terms up to 20 years, rates shown before you apply."),
    LoanPartner("R", Color(0xFF2F9E8F), "Refinance", "Move a loan from another lender to a lower rate."),
    LoanPartner("L", Color(0xFF3B82F6), "Manage other loans", "See loans from other lenders in one place."),
)

/** Prototype "Find loan" partner list: tap a row to open its one-line explanation (dummy copy). */
@Composable
fun LoansFindPartners() {
    var open by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        partners.forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().pressScaleClickable { open = if (open == i) null else i }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(p.tint), contentAlignment = Alignment.Center) {
                    Text(p.badge, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(p.title, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 14.dp))
                Text(if (open == i) "⌃" else "⌄", color = Ids.colors.textTertiary, fontSize = 18.sp)
            }
            if (open == i) {
                Text(p.detail, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 50.dp, bottom = 12.dp))
            }
        }
    }
}
