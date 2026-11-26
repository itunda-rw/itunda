package rw.itunda.feature.maps.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.MapPlaceCategory
import rw.itunda.core.network.PlaceSearchResultDto

private data class MapSearchShortcut(val id: String, val emoji: String, val label: String)

private val mapSearchShortcuts = listOf(
    MapSearchShortcut("CAFE", "☕", "Cafe"),
    MapSearchShortcut("RESTAURANT", "🍽️", "Food"),
    MapSearchShortcut("BUS_STOP", "🚌", "Bus stops"),
    MapSearchShortcut("PHARMACY", "💊", "Pharmacy"),
    MapSearchShortcut("ATM", "🏧", "ATM"),
)

/**
 * Full-screen Maps search, matching the prototype's mapsearch state.
 * Search itself stays backed by the existing Nominatim API and local recent-search store.
 */
@Composable
fun MapSearchScreen(
    query: String,
    searching: Boolean,
    searchResults: List<PlaceSearchResultDto>?,
    error: String?,
    recentSearches: List<PlaceSearchResultDto>,
    categories: List<MapPlaceCategory>,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRunSearch: () -> Unit,
    onSelectPlace: (PlaceSearchResultDto) -> Unit,
    onRemoveRecent: (PlaceSearchResultDto) -> Unit,
    onClearRecentSearches: () -> Unit,
    onQuickSearch: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
) {
    BackHandler(onBack = onBack)
    var filter by remember { mutableStateOf("Recent") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        focusRequester.requestFocus()
    }

    val filteredRecent = when (filter) {
        "Places" -> recentSearches.filterNot { isBusSearchResult(it) }
        "Bus" -> recentSearches.filter { isBusSearchResult(it) }
        else -> recentSearches
    }
    val filteredResults = when (filter) {
        "Places" -> searchResults.orEmpty().filterNot { isBusSearchResult(it) }
        "Bus" -> searchResults.orEmpty().filter { isBusSearchResult(it) }
        else -> searchResults.orEmpty()
    }
    val visibleCategories = mapSearchShortcuts.filter { shortcut -> categories.any { it.id == shortcut.id } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ids.colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .pressScaleClickable(onClick = onBack),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(IdsIcons.Back, contentDescription = "Back", tint = Ids.colors.textPrimary, modifier = Modifier.size(22.dp))
                }
                Text(
                    "Kigali Maps",
                    modifier = Modifier.weight(1f),
                    color = Ids.colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(40.dp))
            }

            Row(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Ids.colors.surfaceSoft, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(IdsIcons.Search, contentDescription = "Search", tint = Ids.colors.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isBlank()) {
                        Text("Search places, bus stops, addresses", color = Ids.colors.textTertiary, fontSize = 15.sp, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = Ids.colors.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(Ids.colors.brand),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onRunSearch() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                if (query.isNotBlank()) {
                    Icon(
                        IdsIcons.Close,
                        contentDescription = "Clear search",
                        tint = Ids.colors.textSecondary,
                        modifier = Modifier.size(18.dp).pressScaleClickable { onQueryChange("") },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapQuickChip("🏠 Home") { onQuickSearch("KG 7 Ave 22, Kacyiru") }
                MapQuickChip("💼 Work") { onQuickSearch("Kigali Heights, KG 7 Ave") }
                MapQuickChip("📍 Kimironko") { onQuickSearch("Kimironko") }
            }

            Row(
                modifier = Modifier
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Recent", "Bookings", "Places", "Bus", "Routes").forEach { label ->
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .background(
                                if (filter == label) Ids.colors.textPrimary else Ids.colors.surfaceSoft,
                                RoundedCornerShape(18.dp),
                            )
                            .pressScaleClickable { filter = label }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = if (filter == label) Ids.colors.background else Ids.colors.textSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) }

            if (query.isNotBlank()) {
                if (searching) {
                    Text("Searching…", color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                } else if (searchResults != null) {
                    if (filteredResults.isEmpty()) {
                        Text("No results", color = Ids.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp))
                    } else {
                        filteredResults.forEach { place ->
                            MapSearchResultRow(place, onClick = { onSelectPlace(place) })
                        }
                    }
                }
            } else {
                if (filter == "Recent" && recentSearches.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Recently viewed", color = Ids.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                        Text("Clear", color = Ids.colors.brand, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.pressScaleClickable { onClearRecentSearches() })
                    }
                    Row(
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, top = 8.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        recentSearches.take(4).forEach { place ->
                            Box(
                                modifier = Modifier
                                    .background(Ids.colors.surfaceSoft, RoundedCornerShape(18.dp))
                                    .pressScaleClickable { onSelectPlace(place) }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                Text(place.displayName.substringBefore(','), color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }

                when (filter) {
                    "Bookings" -> MapSearchEmptyState("No bookings yet")
                    "Routes" -> MapSearchEmptyState("No recent routes")
                    else -> {
                        if (filteredRecent.isNotEmpty()) {
                            Text(
                                if (filter == "Bus") "Recent bus stops" else "Recent searches",
                                color = Ids.colors.textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 4.dp),
                            )
                            filteredRecent.forEach { place ->
                                MapSearchResultRow(place, onClick = { onSelectPlace(place) }, onRemove = { onRemoveRecent(place) })
                            }
                        } else {
                            MapSearchEmptyState(if (filter == "Bus") "No recent bus stops" else "No recent searches")
                        }
                    }
                }
            }

            if (query.isBlank()) {
                Text(
                    "Explore nearby",
                    color = Ids.colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 4.dp),
                )
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    visibleCategories.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { category ->
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                        .background(Ids.colors.surfaceSoft, RoundedCornerShape(16.dp))
                                        .pressScaleClickable { onCategoryClick(category.id) }
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Text(category.emoji, fontSize = 21.sp)
                                    Text(category.label, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun MapQuickChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .background(Ids.colors.surfaceSoft, RoundedCornerShape(18.dp))
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Ids.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MapSearchResultRow(
    place: PlaceSearchResultDto,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .pressScaleClickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📍", fontSize = 20.sp)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(place.displayName.substringBefore(','), color = Ids.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val address = place.displayName.substringAfter(',', "").trim()
            if (address.isNotEmpty()) Text(address, color = Ids.colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onRemove != null) {
            Icon(IdsIcons.Close, contentDescription = "Remove recent search", tint = Ids.colors.textTertiary, modifier = Modifier.size(18.dp).pressScaleClickable { onRemove() })
        }
    }
}

@Composable
private fun MapSearchEmptyState(message: String) {
    Text(message, color = Ids.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp))
}

private fun isBusSearchResult(place: PlaceSearchResultDto): Boolean {
    val value = place.displayName.lowercase()
    return value.contains("bus") || value.contains("stop") || value.contains("station")
}
