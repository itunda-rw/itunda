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
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).pressScaleClickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
            ItundaArt("back", ItundaArtSet.Line, 24.dp, tint = Ids.colors.textPrimary)
        }
        Row(
            Modifier.weight(1f).padding(horizontal = 12.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Ids.colors.surfaceSoft).padding(4.dp),
        ) {
            Pill("My loan" + if (myCount > 0) " ($myCount)" else "", mineSelected, onMine, Modifier.weight(1f))
            Pill("Find loan", findSelected, onFind, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(if (selected) Ids.colors.brand else androidx.compose.ui.graphics.Color.Transparent)
            .pressScaleClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Ids.colors.textPrimary else Ids.colors.textTertiary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
