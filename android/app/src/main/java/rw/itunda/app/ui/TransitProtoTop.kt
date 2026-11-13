package rw.itunda.app.ui

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

/** Prototype Transit top: balance + bus art, low-balance pill, Top up chip, Operator/Fare card. */
@Composable
fun TransitProtoTop(balance: String, fare: String, operator: String, lowBalance: Boolean, onTopUp: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("itunda Transit balance", color = Ids.colors.textTertiary, fontSize = 14.sp)
                Text("$balance RWF", color = Ids.colors.textPrimary, fontSize = 44.sp, fontWeight = FontWeight.ExtraBold)
            }
            ItundaArt("bus-duo", ItundaArtSet.Duo, size = 56.dp, tint = Ids.colors.success)
        }
        if (lowBalance) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Not enough for a $fare RWF ride", color = Ids.colors.danger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ids.colors.danger.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Top up", color = Ids.colors.textBrand, fontSize = 15.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand.copy(alpha = 0.14f))
                .pressScaleClickable(onClick = onTopUp).padding(horizontal = 20.dp, vertical = 18.dp),
        )
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            ProtoRow("Operator", operator)
            ProtoRow("Fare", "$fare RWF")
        }
    }
}

@Composable
private fun ProtoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ids.colors.textTertiary, fontSize = 13.sp)
            Text(value, color = Ids.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Text("›", color = Ids.colors.textTertiary, fontSize = 22.sp)
    }
}
