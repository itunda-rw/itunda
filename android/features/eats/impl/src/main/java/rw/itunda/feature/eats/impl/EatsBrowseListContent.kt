package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.network.ShoppingMerchantDto

internal class EatsBrowseFilters {
    var sortMode by mutableStateOf("delivery_time")
    var openNowOnly by mutableStateOf(false)
    var favoritesOnly by mutableStateOf(false)
}

@Composable
internal fun rememberEatsBrowseFilters() = androidx.compose.runtime.remember { EatsBrowseFilters() }

/** All browse sections and filter states in prototype order, kept in the parent's LazyColumn for real scrolling. */
internal fun LazyListScope.eatsBrowseItems(
    state: EatsBrowseState,
    searchInput: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    filters: EatsBrowseFilters,
    restaurants: List<ShoppingMerchantDto>?,
    favoriteIds: Set<String>,
    error: String?,
    onRetry: () -> Unit,
    onOpen: (ShoppingMerchantDto) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onClearFilters: () -> Unit,
) {
    val filterActive = searchInput.isNotBlank() || selectedCategory != null || filters.openNowOnly || filters.favoritesOnly
    val visible = restaurants?.filter { merchant ->
        (!filters.openNowOnly || merchant.toEatsPrototypeRestaurant().isOpenNow) &&
            (!filters.favoritesOnly || merchant.merchantId in favoriteIds)
    }
    item {
        Column {
            EatsDeliveryPickupRow(state)
            EatsSearchHeader(
                searchInput = searchInput,
                onSearchChange = onSearchChange,
                categories = EATS_PROTOTYPE_CATEGORIES,
                selectedCategory = selectedCategory,
                onSelectCategory = onSelectCategory,
                showSearch = false,
            )
        }
    }
    if (!filterActive) {
        item {
            EatsBrowseOffers(
                state = state,
                onFastestClick = { filters.sortMode = "delivery_time" },
                onSavedClick = { filters.favoritesOnly = true },
            )
        }
        if (!restaurants.isNullOrEmpty()) item { EatsPopularRail(restaurants, onOpen, isPickup = state.isPickup) }
    }
    item {
        EatsSectionHeader(
            if (filterActive) "Results" else "Restaurants near you",
            visible?.size,
        )
    }
    item {
        val palette = eatsPrototypePalette()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Row(
                Modifier.requiredWidth(maxWidth + 40.dp).horizontalScroll(rememberScrollState())
                    .padding(start = 24.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            EatsSortChip("Fastest", null, active = filters.sortMode == "delivery_time") { filters.sortMode = "delivery_time" }
            EatsSortChip("Top rated", null, active = filters.sortMode == "rating") { filters.sortMode = "rating" }
            EatsSortChip("Free delivery", null, active = filters.sortMode == "free_delivery") { filters.sortMode = "free_delivery" }
            EatsSortChip("Nearest", null, active = filters.sortMode == "nearest") { filters.sortMode = "nearest" }
            Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(20.dp).background(palette.divider))
            EatsSortChip("Open now", null, active = filters.openNowOnly) { filters.openNowOnly = !filters.openNowOnly }
        }
    }
    when {
        error != null -> item { ErrorCard(error, onRetry = onRetry) }
        restaurants == null -> item { SkeletonBlock() }
        visible.isNullOrEmpty() -> item {
            Column(
                Modifier.fillMaxWidth().padding(top = 64.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val palette = eatsPrototypePalette()
                Text("No restaurants match.", color = palette.secondary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Box(
                    Modifier.padding(top = 18.dp).clip(RoundedCornerShape(24.dp)).background(palette.accentTint)
                        .pressScaleClickable(onClick = onClearFilters).padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Clear filters", color = palette.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        else -> items(visible!!.chunked(2), key = { row -> row.first().merchantId }) { row ->
            Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                EatsRestaurantGridRow(
                    row = row,
                    favoriteIds = favoriteIds,
                    onOpen = onOpen,
                    onToggleFavorite = { merchant -> onToggleFavorite(merchant.merchantId) },
                    isPickup = state.isPickup,
                )
            }
        }
    }
}
