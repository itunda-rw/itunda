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
 * Marketplace v1038 / v502 feed controls.
 *
 * Categories and saved-state filters are deliberately driven by real ListingDto
 * values and the caller's real favorite IDs. No sample counts or placeholder
 * neighborhood data are introduced here.
 */
@Composable
internal fun MarketplaceFeedControls(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    feedScope: String,
    onFeedScopeSelected: (String) -> Unit,
    nearestFirst: Boolean,
    onSortSelected: (Boolean) -> Unit,
) {
    val scopes = listOf("All", "Free", "Saved", "Mine")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            scopes.forEach { scope ->
                MarketplaceFilterChip(
                    label = scope,
                    selected = feedScope == scope,
                    onClick = { onFeedScopeSelected(scope) },
                )
            }
            categories.distinct().forEach { category ->
                MarketplaceFilterChip(
                    label = category,
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(if (selectedCategory == category) null else category) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MarketplaceFilterChip(
                label = "Latest",
                selected = !nearestFirst,
                onClick = { onSortSelected(false) },
            )
            MarketplaceFilterChip(
                label = "Nearest",
                selected = nearestFirst,
                onClick = { onSortSelected(true) },
            )
        }
    }
}

@Composable
private fun MarketplaceFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) Ids.colors.background else Ids.colors.textPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Ids.colors.textPrimary else Ids.colors.surfaceSoft)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}
