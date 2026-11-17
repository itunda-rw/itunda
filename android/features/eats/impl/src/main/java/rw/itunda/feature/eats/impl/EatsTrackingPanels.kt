package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable

@Composable
internal fun EatsTrackingRiderCard(name: String, onCall: () -> Unit) {
    val palette = eatsPrototypePalette()
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).padding(horizontal = 24.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(palette.surfaceSoft), contentAlignment = Alignment.Center) {
            ItundaArt("moto", set = ItundaArtSet.Color, size = 36.dp)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, color = palette.primary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold)
            Text("Your rider · ★ 4.9 · Moto RAF 214 K · Helmet on", color = palette.secondary, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(palette.surfaceSoft).pressScaleClickable(onClick = onCall),
            contentAlignment = Alignment.Center,
        ) {
            ItundaArt("airtime", set = ItundaArtSet.Color, size = 29.dp)
        }
    }
}

@Composable
internal fun EatsTrackingRiderPanel(riderName: String, onBack: () -> Unit, onQuickMessage: (String) -> Unit,
    onCall: () -> Unit, modifier: Modifier) {
    val palette = eatsPrototypePalette()
    Column(modifier.fillMaxSize().background(palette.background)) {
        EatsTrackingHeader("", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 24.dp)) {
            Text(riderName, color = palette.primary, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 16.dp))
            Text("Quick message", color = palette.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp))
            listOf("I’m at the gate", "Please call me", "Leave it at the door").forEach { message ->
                Box(Modifier.padding(start = 24.dp, bottom = 10.dp).clip(RoundedCornerShape(28.dp)).background(palette.surfaceSoft)
                    .pressScaleClickable(onClick = { onQuickMessage(message) }).padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text(message, color = palette.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        EatsTrackingButton("Call", onCall, modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp))
    }
}

@Composable
internal fun EatsTrackingCancelPanel(refund: String, busy: Boolean, onBack: () -> Unit, onKeep: () -> Unit,
    onCancel: () -> Unit, modifier: Modifier) {
    val palette = eatsPrototypePalette()
    Column(modifier.fillMaxSize().background(palette.background)) {
        EatsTrackingHeader("", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 24.dp).padding(top = 16.dp)) {
            Text("Cancel this order?", color = palette.primary, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
            Text("You will get $refund back in itunda Pay.", color = palette.secondary, fontSize = 16.sp, lineHeight = 23.sp,
                modifier = Modifier.padding(top = 12.dp))
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EatsTrackingButton(if (busy) "Cancelling…" else "Cancel order", onCancel, enabled = !busy)
            EatsTrackingButton("Keep order", onKeep)
        }
    }
}

@Composable
internal fun EatsTrackingRatingPanel(rating: Int, busy: Boolean, onBack: () -> Unit, onRatingChange: (Int) -> Unit,
    onSubmit: () -> Unit, modifier: Modifier) {
    val palette = eatsPrototypePalette()
    Column(modifier.fillMaxSize().background(palette.background)) {
        EatsTrackingHeader("", onBack)
        Column(Modifier.weight(1f).padding(horizontal = 24.dp).padding(top = 16.dp)) {
            Text("How was your order?", color = palette.primary, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black)
            Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                (1..5).forEach { value ->
                    Text("★", color = if (value <= rating) Color(0xFFFFB321) else palette.surfaceSoft, fontSize = 36.sp,
                        modifier = Modifier.pressScaleClickable(onClick = { onRatingChange(value) }))
                }
            }
        }
        EatsTrackingButton(if (busy) "Sending…" else "Send rating", onSubmit, enabled = !busy,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp))
    }
}

@Composable
internal fun EatsTrackingButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    height: Dp = 58.dp, background: Color = Color(0xFF8F89FF), color: Color = Color(0xFF17171C)) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(28.dp)).background(background)
        .pressScaleClickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
