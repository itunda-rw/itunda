package rw.itunda.feature.marketplace.impl

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

/** Prototype category strip: "All" plus the categories present in the loaded listings. */
@Composable
internal fun MarketplaceCategoryChips(categories: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (listOf<String?>(null) + categories).forEach { c ->
            val on = c == selected
            Text(
                c ?: "All", maxLines = 1,
                color = if (on) Ids.colors.textPrimary else Ids.colors.textTertiary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Ids.colors.surfaceSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .pressScaleClickable { onSelect(c) }.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
