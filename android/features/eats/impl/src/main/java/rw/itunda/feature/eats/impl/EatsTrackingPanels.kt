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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
            ItundaArt("phone", set = ItundaArtSet.Line, size = 20.dp, tint = palette.primary)
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
            Text(riderName, color = palette.primary, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
            Text("Quick message", color = palette.secondary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            listOf("I’m at the gate", "Please call me", "Leave it at the door").forEachIndexed { index, message ->
                Box(
                    Modifier.padding(start = 24.dp, bottom = if (index < 2) 10.dp else 0.dp).height(40.dp)
                        .clip(RoundedCornerShape(18.dp)).background(palette.surfaceSoft)
                        .pressScaleClickable(onClick = { onQuickMessage(message) }).padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(message, color = palette.primary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
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
            Text("Cancel this order?", color = palette.primary, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Text("You will get $refund back in itunda Pay.", color = palette.secondary, fontSize = 15.sp, lineHeight = 21.sp,
                modifier = Modifier.padding(top = 8.dp))
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
            Text("How was your order?", color = palette.primary, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { value ->
                    Box(
                        Modifier.size(48.dp)
                            .pressScaleClickable(onClick = { onRatingChange(value) })
                            .semantics { contentDescription = "Rate $value stars" },
                        contentAlignment = Alignment.Center,
                    ) {
                        ItundaArt(
                            "star-solid",
                            set = ItundaArtSet.Solid,
                            size = 32.dp,
                            tint = if (value <= rating) Color(0xFFFFB800) else palette.surfaceSoft,
                        )
                    }
                }
            }
        }
        EatsTrackingButton(if (busy) "Sending…" else "Send rating", onSubmit, enabled = !busy,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp))
    }
}

@Composable
internal fun EatsTrackingButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 52.dp,
    background: Color = Color(0xFF8F89FF),
    color: Color = Color(0xFF17171C),
    fontSize: androidx.compose.ui.unit.TextUnit = 15.sp,
    cornerRadius: Dp = 16.dp,
    leadingStar: Boolean = false,
) {
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(cornerRadius)).background(background)
            .pressScaleClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingStar) {
                ItundaArt("star-solid", set = ItundaArtSet.Solid, size = 14.dp, tint = Color(0xFFFFB800))
            }
            Text(label, color = color, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
