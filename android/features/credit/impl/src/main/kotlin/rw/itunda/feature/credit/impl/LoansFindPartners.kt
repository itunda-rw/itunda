package rw.itunda.feature.credit.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.layout.height
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

private data class LoanPartner(val badge: String, val tint: Color, val title: String, val detail: String, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null)

private val partners = listOf(
    LoanPartner("B", Color(0xFF5A51EC), "itunda Bank loans", "Small loans for people with a steady income. Repay monthly, no hidden fees."),
    LoanPartner("M", Color(0xFF0A9F6B), "Mortgage loan", "Buy or build a home. Terms up to 20 years, rates shown before you apply.", Icons.Filled.Apartment),
    LoanPartner("R", Color(0xFF12A39A), "Refinance", "Move a loan from another lender to a lower rate.", Icons.Filled.Sync),
    LoanPartner("L", Color(0xFF3B82F6), "Manage other loans", "See loans from other lenders in one place.", Icons.Filled.CreditCard),
)

/** Prototype "Find loan" partner list (measured: 64dp rows, 40dp r14 badge, 17sp/600 title, chevron 18dp). */
@Composable
fun LoansFindPartners(onBankLoans: () -> Unit = {}) {
    var open by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        partners.forEachIndexed { i, p ->
            Row(
                Modifier.fillMaxWidth().height(64.dp).pressScaleClickable { if (i == 0) onBankLoans() else open = if (open == i) null else i }.padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(p.tint), contentAlignment = Alignment.Center) {
                    when {
                        p.icon != null -> androidx.compose.material3.Icon(p.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        else -> Text(p.badge, color = Color.White, fontSize = if (p.tint == Color.Transparent) 26.sp else 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(p.title, color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 23.8.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 14.dp))
                androidx.compose.material3.Icon(
                    if (i == 0) Icons.Filled.KeyboardArrowRight else if (open == i) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp),
                )
            }
            if (open == i) {
                Text(p.detail, color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(start = 78.dp, end = 24.dp, bottom = 12.dp))
            }
        }
    }
}

/** Overdraft / Postpaid credit shortcuts, shown with the real offers list. */
@Composable
fun LoansFindExtras(onOverdraft: () -> Unit, onPostpaid: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        listOf("Overdraft" to onOverdraft, "Postpaid credit" to onPostpaid).forEach { (label, go) ->
            Row(Modifier.fillMaxWidth().pressScaleClickable(onClick = go).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("\u203A", color = Ids.colors.textTertiary, fontSize = 22.sp)
            }
        }
    }
}
