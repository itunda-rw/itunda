package rw.itunda.feature.pay.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Scan footer: "Till number" entry plus recent merchants and friends as one-tap chips. */
@Composable
internal fun PayScanChips(onTill: () -> Unit, onMerchant: (String) -> Unit) {
    Row(Modifier.padding(vertical = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Till number", onTill)
        listOf("Amina K.", "Kigali Bakery", "Nyamirambo Coffee House").forEach { name -> Chip(name) { onMerchant(name) } }
    }
}

@Composable
private fun Chip(label: String, onClick: () -> Unit) {
    Text(
        label, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Ids.colors.surfaceSoft).pressScaleClickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
