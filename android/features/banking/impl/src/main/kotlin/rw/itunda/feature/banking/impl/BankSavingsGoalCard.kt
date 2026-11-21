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

/** Prototype `.b7p`: 20dp-radius tint card, 56dp white art tile at x=16, 17sp/700 title, two-line caption, Start link. */
@Composable
fun BankSavingsGoalCard(onStart: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.brand.copy(alpha = 0.14f)).pressScaleClickable(onClick = onStart).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) { ItundaArt("money", ItundaArtSet.Proto, 38.dp, fit = true) }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text("Start a savings goal", color = Ids.colors.textPrimary, fontSize = 17.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp)
            Text("A target and a date make saving easier", color = Ids.colors.textTertiary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Start", color = Ids.colors.textBrand, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
                ItundaArt("chev", ItundaArtSet.Line, 14.dp, tint = Ids.colors.textBrand)
            }
        }
    }
}
