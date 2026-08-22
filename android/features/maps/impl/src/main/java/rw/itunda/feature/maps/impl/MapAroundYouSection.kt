package rw.itunda.feature.maps.impl

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.itundaface.GlobeGlyph
import rw.itunda.core.designsystem.itundaface.LockGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.MAP_NEARBY_CATEGORIES
import rw.itunda.core.network.MapBookmarkDto
import rw.itunda.core.network.NearbyPlaceDto
import rw.itunda.core.network.PlaceSearchResultDto
import rw.itunda.core.network.ShoppingMerchantDto

// Extracted from MapScreen's own bottom-sheet lambda (2026-08-20), same real reason
// SharedFolderSection/ItineraryBuilderCard were already extracted before it. Pure
// "values in, callbacks out" rendering -- MapScreen still owns every var this reads.
@Composable
internal fun AroundYouSection(
    bookmarks: List<MapBookmarkDto>,
    activeCategory: String?,
    categoryResults: List<NearbyPlaceDto>?,
    isAgentCashDiscovery: Boolean,
    merchants: List<ShoppingMerchantDto>,
    initialSharedFolder: Pair<String, String>?,
    loadingSharedFolder: Boolean,
    sharedFolderError: String?,
    sharedFolderBookmarks: List<MapBookmarkDto>?,
    subscribingSharedFolder: Boolean,
    subscribedSharedFolderCount: Int?,
    shareConfirmation: String?,
    sharingFolder: String?,
    movingBookmark: MapBookmarkDto?,
    moveFolderNameInput: String,
    moveFolderColorInput: String,
    onSelectAndRoute: (PlaceSearchResultDto) -> Unit,
    onSelectPlace: (PlaceSearchResultDto) -> Unit,
    onSubscribeSharedFolder: () -> Unit,
    onToggleFolderShare: (String, Boolean) -> Unit,
    onToggleMovingBookmark: (MapBookmarkDto) -> Unit,
    onMoveFolderNameChange: (String) -> Unit,
    onMoveFolderColorChange: (String) -> Unit,
    onConfirmMove: () -> Unit,
) {
    Text("Around you", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ids.colors.textPrimary)
    val home = bookmarks.firstOrNull { it.folderName.equals("Home", ignoreCase = true) }
    val work = bookmarks.firstOrNull { it.folderName.equals("Work", ignoreCase = true) }
    if (home != null || work != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (home != null) Text("⌂ Home", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Ids.colors.surfaceSoft).pressScaleClickable { onSelectAndRoute(PlaceSearchResultDto(home.displayName, home.latitude, home.longitude)) }.padding(horizontal = 12.dp, vertical = 8.dp))
            if (work != null) Text("▣ Work", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Ids.colors.surfaceSoft).pressScaleClickable { onSelectAndRoute(PlaceSearchResultDto(work.displayName, work.latitude, work.longitude)) }.padding(horizontal = 12.dp, vertical = 8.dp))
        }
    }
    if (activeCategory != null && categoryResults != null) {
        val label = MAP_NEARBY_CATEGORIES.firstOrNull { it.id == activeCategory }?.label?.lowercase() ?: "places"
        if (categoryResults.isEmpty()) {
            Text("No real matches found nearby for $label.", color = Ids.colors.textSecondary, fontSize = 13.sp)
        } else {
            categoryResults.forEach { nearby ->
                val (nearbyName, nearbyAddress) = splitPlaceName(nearby.displayName)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScaleClickable { onSelectPlace(PlaceSearchResultDto(nearby.displayName, nearby.latitude, nearby.longitude)) }
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        "${if (isAgentCashDiscovery && activeCategory == "ITUNDA_AGENT") "Itunda agent · " else ""}$nearbyName",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ids.colors.textPrimary,
                    )
                    Text(
                        listOfNotNull(nearbyAddress, "${"%.1f".format(nearby.distanceKm)} km").joinToString(" · "),
                        fontSize = 11.sp,
                        color = Ids.colors.textSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    } else {
        Text(
            if (merchants.isEmpty()) "Search a real place or pick a category above to explore Rwanda."
            else "${merchants.size} real merchant${if (merchants.size == 1) "" else "s"} on the map. Search a place or pick a category above to explore.",
            color = Ids.colors.textSecondary,
            fontSize = 13.sp,
        )
    }

    // A folder someone shared with this user, shown above their own saved places
    // since it's the reason they opened the app.
    if (initialSharedFolder != null) {
        SharedFolderSection(
            folderName = initialSharedFolder.second,
            loading = loadingSharedFolder,
            error = sharedFolderError,
            sharedBookmarks = sharedFolderBookmarks,
            subscribing = subscribingSharedFolder,
            subscribedCount = subscribedSharedFolderCount,
            onSubscribe = onSubscribeSharedFolder,
            onOpenPlace = { shared -> onSelectPlace(PlaceSearchResultDto(shared.displayName, shared.latitude, shared.longitude)) },
        )
    }
    Text(
        "★ Your saved places",
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = Ids.colors.textSecondary,
        modifier = Modifier.padding(top = 8.dp),
    )
    shareConfirmation?.let {
        Text(it, fontSize = 11.sp, color = Ids.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
    if (bookmarks.isEmpty()) {
        EmptyState("No saved places yet — tap ☆ on a place to save it here.", icon = Icons.Outlined.BookmarkBorder)
    } else {
        // Real "My Places" folder grouping (2026-07-22) -- ported from bank-mfe's own
        // real grouping. groupBy preserves encounter order, so a folder's position here
        // is simply wherever its most-recently-saved place falls (bookmarks is already
        // createdAt-desc), not a separate alphabetic re-sort.
        val bookmarksByFolder = bookmarks.groupBy { it.folderName }
        bookmarksByFolder.forEach { (folderName, folderBookmarks) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                if (bookmarksByFolder.size > 1) {
                    Text(folderName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary)
                } else {
                    Box(modifier = Modifier)
                }
                val isPublic = folderBookmarks.any { it.isPublic }
                if (sharingFolder == folderName) {
                    Text("…", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Ids.colors.textSecondary)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.pressScaleClickable(enabled = sharingFolder == null) { onToggleFolderShare(folderName, !isPublic) },
                    ) {
                        if (isPublic) GlobeGlyph(size = 11.dp) else LockGlyph(size = 11.dp)
                        Text(
                            if (isPublic) "Public · Share" else "Private · Share",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isPublic) Ids.colors.brand else Ids.colors.textSecondary,
                        )
                    }
                }
            }
            folderBookmarks.forEach { bookmark ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScaleClickable { onSelectPlace(PlaceSearchResultDto(bookmark.displayName, bookmark.latitude, bookmark.longitude)) }
                        .padding(vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                try { androidx.compose.ui.graphics.Color(AndroidColor.parseColor(bookmark.color)) } catch (_: Exception) { androidx.compose.ui.graphics.Color(0xFFF5A623) },
                                CircleShape,
                            ),
                    )
                    Text(bookmark.displayName, fontSize = 13.sp, color = Ids.colors.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        "Move", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary,
                        modifier = Modifier.pressScaleClickable { onToggleMovingBookmark(bookmark) },
                    )
                }
                if (movingBookmark?.let { it.latitude == bookmark.latitude && it.longitude == bookmark.longitude } == true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IdsTextField(
                            value = moveFolderNameInput,
                            onValueChange = onMoveFolderNameChange,
                            label = "Folder name",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BOOKMARK_COLOR_PALETTE.forEach { c ->
                                val color = try { androidx.compose.ui.graphics.Color(AndroidColor.parseColor(c)) } catch (_: Exception) { androidx.compose.ui.graphics.Color(0xFFF5A623) }
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(color, CircleShape)
                                        .then(if (moveFolderColorInput == c) Modifier.border(2.dp, Ids.colors.textPrimary, CircleShape) else Modifier)
                                        .pressScaleClickable { onMoveFolderColorChange(c) },
                                )
                            }
                        }
                        Text(
                            "Save", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                            modifier = Modifier.pressScaleClickable(enabled = moveFolderNameInput.isNotBlank(), onClick = onConfirmMove),
                        )
                    }
                }
            }
        }
    }
}
