package rw.itunda.feature.front.impl

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Back arrow, title/subtitle and the 3-segment Menu / Bill / Pay stepper. */
@Composable
internal fun FrontStepHeader(step: Int, title: String, subtitle: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
                ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
            }
            if (subtitle == null) {
                Text(title, color = Ids.colors.textTertiary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            } else {
                Column {
                    Text(title, color = Ids.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = Ids.colors.textTertiary, fontSize = 13.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Menu", "Bill", "Pay").forEachIndexed { i, label ->
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) Ids.colors.textBrand else Ids.colors.brand.copy(alpha = 0.15f)))
                    Spacer(Modifier.height(8.dp))
                    Text(label, color = if (i == step) Ids.colors.textPrimary else Ids.colors.textTertiary, fontSize = 12.sp, fontWeight = if (i == step) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
    }
}

/** Full-width 56dp bottom call to action; brand-filled when enabled, soft when not. */
@Composable
internal fun FrontCta(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).height(56.dp).clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Ids.colors.brand else Ids.colors.brand.copy(alpha = 0.18f))
            .then(if (enabled) Modifier.pressScaleClickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = if (enabled) Ids.colors.textPrimary else Ids.colors.brand, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

/** Pill-segmented picker (tip and split choices). */
@Composable
internal fun FrontSegments(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.surfaceSoft).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Ids.colors.textPrimary else androidx.compose.ui.graphics.Color.Transparent)
                    .pressScaleClickable { onPick(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Ids.colors.background else Ids.colors.textPrimary) }
        }
    }
}

@Composable
internal fun FrontSoftRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.padding(horizontal = 24.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).padding(horizontal = 18.dp, vertical = 14.dp)) { content() }
}
