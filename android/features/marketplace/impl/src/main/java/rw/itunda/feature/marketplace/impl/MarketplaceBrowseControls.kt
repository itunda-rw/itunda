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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/**
 * Browse controls aligned to the v1038 Marketplace feed:
 * one horizontally scrolling category/filter rail and a compact Latest/Nearest
 * sort row. "Saved" and "Mine" operate on the real viewer/favorite IDs.
 */
@Composable
internal fun MarketplaceBrowseControls(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    nearestFirst: Boolean,
    nearestAvailable: Boolean,
    onSortSelected: (Boolean) -> Unit,
) {
    val categories = listOf("All", "Electronics", "Phones", "Home", "Fashion", "Kids", "Sports", "Free", "Saved", "Mine")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.forEach { category ->
                val selected = selectedCategory == category
                Text(
                    text = category,
                    color = if (selected) Ids.colors.background else Ids.colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
                        .pressScaleClickable { onCategorySelected(category) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BrowseSortChip(
                label = "Latest",
                selected = !nearestFirst,
                onClick = { onSortSelected(false) },
            )
            BrowseSortChip(
                label = "Nearest",
                selected = nearestFirst,
                enabled = nearestAvailable,
                onClick = { onSortSelected(true) },
            )
        }
    }
}

@Composable
private fun BrowseSortChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = when {
            !enabled -> Ids.colors.textTertiary
            selected -> Ids.colors.background
            else -> Ids.colors.textPrimary
        },
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
            .pressScaleClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
