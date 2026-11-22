package rw.itunda.feature.credit.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Prototype Loans top bar: back arrow plus a "My loan | Find loan" pill toggle. */
@Composable
fun LoansTopBar(
    findSelected: Boolean,
    mineSelected: Boolean,
    myCount: Int,
    onBack: () -> Unit,
    onFind: () -> Unit,
    onMine: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(66.dp)) {
        Box(Modifier.padding(start = 12.dp, top = 14.dp).size(48.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.Center) {
            ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
        }
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 16.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Ids.colors.surfaceSoft).padding(3.dp),
        ) {
            Pill("My loan" + if (myCount > 0) " ($myCount)" else "", mineSelected, onMine, Modifier)
            Pill("Find loan", findSelected, onFind, Modifier)
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(if (selected) Ids.colors.brand else androidx.compose.ui.graphics.Color.Transparent)
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color(0xFF17171C) else Ids.colors.textTertiary, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.3).sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 18.dp))
    }
}
