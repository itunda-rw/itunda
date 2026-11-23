package rw.itunda.feature.marketplace.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** v1038 Marketplace category rail and quiet Latest/Nearest/Filter action row. */
@Composable
internal fun MarketplaceBrowseControls(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    sortLabel: String,
    filterCount: Int,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
) {
    val categories = listOf("All", "Electronics", "Phones", "Home", "Fashion", "Kids", "Sports", "Free", "Saved", "Bought", "Mine")
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            categories.forEach { category ->
                val selected = selectedCategory == category
                Column(
                    modifier = Modifier.pressScaleClickable { onCategorySelected(category) }.padding(vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = category,
                        color = if (selected) Ids.colors.textPrimary else Ids.colors.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (selected) Ids.colors.textPrimary else Color.Transparent),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BrowseSortAction("↕ $sortLabel", selected = true, onClick = onSortClick)
            BrowseSortAction(if (filterCount > 0) "Filter · $filterCount" else "Filter", selected = filterCount > 0, onClick = onFilterClick)
        }
    }
}

@Composable
private fun BrowseSortAction(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text = label,
        color = when {
            !enabled -> Ids.colors.textTertiary
            selected -> Ids.colors.textPrimary
            else -> Ids.colors.textSecondary
        },
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.pressScaleClickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
    )
}
