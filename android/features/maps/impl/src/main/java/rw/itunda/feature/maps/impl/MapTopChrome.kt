package rw.itunda.feature.maps.impl

import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.itundaface.PlaceGlyph
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsIcons
import rw.itunda.core.network.MAP_NEARBY_CATEGORIES
import rw.itunda.core.network.NearbyPlaceDto
import rw.itunda.core.network.PlaceSearchResultDto
import rw.itunda.core.network.TrendingPlaceDto

// Extracted from MapScreen's own bottom-sheet lambda (2026-08-20), same real reason
// SharedFolderSection/ItineraryBuilderCard/AroundYouSection were already extracted
// before it. Pure "values in, callbacks out" rendering -- MapScreen still owns every
// var this reads.
@Composable
internal fun MapTopChrome(
    onBack: () -> Unit,
    query: String,
    searching: Boolean,
    activeCategory: String?,
    categoryLoading: Boolean,
    categoryResults: List<NearbyPlaceDto>?,
    itineraryBuilding: Boolean,
    itineraryStopCount: Int,
    selectedPlace: PlaceSearchResultDto?,
    searchResults: List<PlaceSearchResultDto>?,
    error: String?,
    aroundMePlaces: List<NearbyPlaceDto>?,
    trendingPlaces: List<TrendingPlaceDto>?,
    searchFocused: Boolean,
    recentSearches: List<PlaceSearchResultDto>,
    isAgentCashDiscovery: Boolean,
    onQueryChange: (String) -> Unit,
    onRunSearch: () -> Unit,
    onClearQuery: () -> Unit,
    onSearchFocusChange: (Boolean) -> Unit,
    onCategoryClick: (String) -> Unit,
    onToggleItinerary: () -> Unit,
    onSelectPlace: (PlaceSearchResultDto) -> Unit,
    onSearchResultTap: (PlaceSearchResultDto) -> Unit,
    onClearRecentSearches: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .shadow(3.dp, CircleShape)
                .background(Ids.colors.surface, CircleShape)
                .clip(CircleShape)
                .pressScaleClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(IdsIcons.Back, contentDescription = "Back", modifier = Modifier.size(16.dp), tint = Ids.colors.textPrimary)
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .shadow(3.dp, RoundedCornerShape(999.dp))
                .background(Ids.colors.surface, RoundedCornerShape(999.dp))
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                IdsIcons.Search,
                contentDescription = "Search",
                tint = if (searching) Ids.colors.textSecondary else Ids.colors.brand,
                modifier = Modifier.size(18.dp).pressScaleClickable(enabled = !searching && query.isNotBlank()) { onRunSearch() },
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search a real place in Rwanda", fontSize = 13.sp) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    disabledBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onRunSearch() }),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                    .onFocusChanged { onSearchFocusChange(it.isFocused) },
            )
            if (query.isNotBlank()) {
                Icon(
                    IdsIcons.Close,
                    contentDescription = "Clear search",
                    tint = Ids.colors.textSecondary,
                    modifier = Modifier.size(16.dp).pressScaleClickable { onClearQuery() },
                )
                Box(modifier = Modifier.width(6.dp))
            }
        }
    }

    // Real category-chip "nearby places" search (Naver/Kakao's own convention) --
    // mirrors bank-mfe's MapView.tsx chip row, now with itundaface's own hand-drawn
    // PlaceGlyph per category so chips read at a glance instead of as text-only pills.
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MAP_NEARBY_CATEGORIES.forEach { category ->
            val active = activeCategory == category.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .shadow(if (active) 3.dp else 1.dp, RoundedCornerShape(999.dp))
                    .background(if (active) Ids.colors.brand else Ids.colors.surface, RoundedCornerShape(999.dp))
                    .pressScaleClickable(enabled = !categoryLoading || active) { onCategoryClick(category.id) }
                    .padding(start = if (active) 12.dp else 6.dp, end = 12.dp, top = if (active) 8.dp else 6.dp, bottom = if (active) 8.dp else 6.dp),
            ) {
                if (active) {
                    PlaceGlyph(category.id, size = 16.dp)
                } else {
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape).background(Ids.colors.warningTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlaceGlyph(category.id, size = 15.dp)
                    }
                }
                Text(
                    if (active && categoryLoading) "…" else category.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) androidx.compose.ui.graphics.Color.White else Ids.colors.textPrimary,
                )
            }
        }
    }

    // A real multi-stop planner, not a second fake map mode. While active, search
    // results become ordered stops for the bounded OSRM itinerary API.
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .shadow(1.dp, RoundedCornerShape(999.dp))
            .background(Ids.colors.surface, RoundedCornerShape(999.dp))
            .pressScaleClickable { onToggleItinerary() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(if (itineraryBuilding) "✓ Planning ${itineraryStopCount + 1} stops" else "＋ Plan multi-stop trip", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (itineraryBuilding) Ids.colors.brand else Ids.colors.textPrimary)
        if (itineraryBuilding) Text("Tap to cancel", fontSize = 11.sp, color = Ids.colors.textSecondary)
    }

    // Real "Smart Around"-style default state (2026-08-04) -- see loadAroundMe's own
    // doc comment for the real, re-verified Naver Map sourcing and honest scope.
    if (selectedPlace == null && activeCategory == null && searchResults == null && !searchFocused && !itineraryBuilding) {
        if (!aroundMePlaces.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                    .background(Ids.colors.surface, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                    .padding(vertical = 8.dp),
            ) {
                Text("주변 · Nearby", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    aroundMePlaces.forEach { place ->
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.surfaceSoft)
                                .pressScaleClickable { onSelectPlace(PlaceSearchResultDto(place.displayName, place.latitude, place.longitude)) }
                                .padding(10.dp),
                        ) {
                            Text(splitPlaceName(place.displayName).first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("%.1f km".format(place.distanceKm), fontSize = 11.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
        if (!trendingPlaces.isNullOrEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(3.dp, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                    .background(Ids.colors.surface, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                    .padding(vertical = 8.dp),
            ) {
                Text("이번 주에 많이 저장한 · Popular this week", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    trendingPlaces.forEach { place ->
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.surfaceSoft)
                                .pressScaleClickable { onSelectPlace(PlaceSearchResultDto(place.displayName, place.latitude, place.longitude)) }
                                .padding(10.dp),
                        ) {
                            Text(splitPlaceName(place.displayName).first, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("★ saved by ${place.saveCount}", fontSize = 11.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }

    if ((activeCategory != null && categoryResults != null) || searchResults != null || error != null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                .background(Ids.colors.surface, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                .padding(vertical = 4.dp),
        ) {
            if (activeCategory != null && categoryResults != null) {
                val label = MAP_NEARBY_CATEGORIES.firstOrNull { it.id == activeCategory }?.label?.lowercase()
                Text(
                    if (categoryResults.isEmpty()) "No real matches found nearby for that category."
                    else if (isAgentCashDiscovery && activeCategory == "ITUNDA_AGENT") {
                        "${categoryResults.size} Itunda agents found nearby, closest first. Select one for directions."
                    } else "${categoryResults.size} real $label found nearby, closest first.",
                    color = Ids.colors.textSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }

            searchResults?.let { results ->
                if (results.isEmpty()) {
                    Text("No real places found for that search.", color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(14.dp))
                } else {
                    results.forEach { place ->
                        val (name, address) = splitPlaceName(place.displayName)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScaleClickable { onSearchResultTap(place) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textPrimary)
                            if (address != null) {
                                Text(address, fontSize = 11.sp, color = Ids.colors.textSecondary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                            }
                        }
                    }
                }
            }

            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
        }
    }

    // Real recent-searches list (2026-07-22) -- its own card, separately gated from
    // the search-results/category-results card above (that one only renders when
    // there's a real result set; this one renders instead of it, only while the
    // search box is focused and empty).
    if (searchFocused && query.isBlank() && recentSearches.isNotEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                .background(Ids.colors.surface, RoundedCornerShape(Ids.layout.sectionCornerRadius))
                .padding(vertical = 4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text("Recent searches", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary)
                Text(
                    "Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                    modifier = Modifier.pressScaleClickable { onClearRecentSearches() },
                )
            }
            recentSearches.forEach { place ->
                Text(
                    "🕐 ${place.displayName}",
                    fontSize = 13.sp,
                    color = Ids.colors.textPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScaleClickable { onSearchResultTap(place) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}
