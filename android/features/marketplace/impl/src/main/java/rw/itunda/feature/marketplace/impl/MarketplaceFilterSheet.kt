package rw.itunda.feature.marketplace.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** Filters backed by fields currently returned by ListingDto. */
@Composable
internal fun MarketplaceFilterSheet(
    visible: Boolean,
    maxPrice: Int,
    selectedNeighborhood: String?,
    neighborhoods: List<String>,
    onDismiss: () -> Unit,
    onApply: (Int, String?) -> Unit,
) {
    if (!visible) return
    var draftMaxPrice by remember(visible, maxPrice) { mutableStateOf(maxPrice) }
    var draftNeighborhood by remember(visible, selectedNeighborhood) { mutableStateOf(selectedNeighborhood) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Filter", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
            Text("Maximum price (RWF)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textSecondary)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0, 20000, 50000, 100000).forEach { amount ->
                    FilterOption(
                        label = when (amount) { 0 -> "Any"; 20000 -> "20k"; 50000 -> "50k"; else -> "100k" },
                        selected = draftMaxPrice == amount,
                        onClick = { draftMaxPrice = amount },
                    )
                }
            }
            if (neighborhoods.isNotEmpty()) {
                Text("Area", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FilterOption("All areas", draftNeighborhood == null) { draftNeighborhood = null }
                    neighborhoods.forEach { area ->
                        FilterOption(area, draftNeighborhood == area) { draftNeighborhood = area }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    draftMaxPrice = 0
                    draftNeighborhood = null
                    onApply(0, null)
                    onDismiss()
                }) { Text("Reset") }
                TextButton(onClick = {
                    onApply(draftMaxPrice, draftNeighborhood)
                    onDismiss()
                }) { Text("Show results", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun FilterOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) Ids.colors.background else Ids.colors.textPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(999.dp))
            .background(if (selected) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
