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
 * Prototype "Find loan" hero: headline plus two stat tiles. The lowest-rate tile uses the best rate
 * among the real offers when they are loaded; the approval rate is demo data until the backend has it.
 */
@Composable
fun LoansFindHero(lowestRate: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Check your loan rate and limit in 1 minute", color = Ids.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(Modifier.weight(1f), "Lowest rate on offer", lowestRate?.let { "$it%" } ?: "1.2% a month")
            StatTile(Modifier.weight(1f), "Approval rate", "72%")
        }
    }
}

@Composable
private fun StatTile(modifier: Modifier, label: String, value: String) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 16.dp, vertical = 18.dp)) {
        Text(label, color = Ids.colors.textTertiary, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text(value, color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
    }
}
