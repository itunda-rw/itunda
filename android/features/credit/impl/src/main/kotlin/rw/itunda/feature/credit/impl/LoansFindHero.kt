package rw.itunda.feature.credit.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids

/**
 * Prototype "Find loan" hero (measured): h1 30sp/700 at y90, stat tiles at y190 (171.3 / 158.7 wide,
 * 110 tall, r20). The lowest-rate tile uses the best real offer rate when loaded; approval rate is demo data.
 */
@Composable
fun LoansFindHero(lowestRate: String? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text("Check your loan rate\nand limit in 1 minute", color = Ids.colors.textPrimary, fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Modifier.weight(171.3f), "Estimated minimum interest rate", lowestRate?.let { "$it% a month" } ?: "1.2% a month", 56.dp)
            StatTile(Modifier.weight(158.7f), "Approval rate", "72%", 28.dp)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StatTile(modifier: Modifier, label: String, value: String, labelBox: androidx.compose.ui.unit.Dp) {
    Column(modifier.height(110.dp).clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(16.dp)) {
        Text(label, color = Ids.colors.textTertiary, fontSize = 13.sp, lineHeight = 22.sp, modifier = Modifier.height(if (labelBox > 40.dp) 44.dp else 22.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, color = Ids.colors.textPrimary, fontSize = 19.sp, lineHeight = 28.sp, letterSpacing = (-0.4).sp, fontWeight = FontWeight.ExtraBold)
    }
}
