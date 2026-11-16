package rw.itunda.feature.banking.impl

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

/** Prototype "Start a savings goal" card: soft brand tint, art tile, one line of copy, a Start link. */
@Composable
fun BankSavingsGoalCard(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.brand.copy(alpha = 0.14f)).pressScaleClickable(onClick = onStart).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) { ItundaArt("savings-goal", ItundaArtSet.Color, 34.dp) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Start a savings goal", color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("A target and a date make saving easier", color = Ids.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            Text("Start  ›", color = Ids.colors.textBrand, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
