package rw.itunda.feature.marketplace.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids

/** v1038 Marketplace category tabs and browse actions. */
@Composable
fun MarketplaceBrowseControls(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    sortLabel: String,
    filterCount: Int,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
) {
    val categories = listOf("All", "Electronics", "Phones", "Home", "Fashion", "Kids", "Sports", "Free")
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        categories.forEach { category ->
            val selected = selectedCategory == category
            Text(
                text = category,
                color = if (selected) Ids.colors.textPrimary else Ids.colors.textTertiary,
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Ids.colors.surfaceSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .pressScaleClickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "↕ $sortLabel",
            color = Ids.colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.pressScaleClickable(onClick = onSortClick).padding(vertical = 8.dp),
        )
        Text(
            text = if (filterCount > 0) "Filter · $filterCount" else "Filter",
            color = if (filterCount > 0) Ids.colors.textPrimary else Ids.colors.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.pressScaleClickable(onClick = onFilterClick).padding(vertical = 8.dp),
        )
    }
}
