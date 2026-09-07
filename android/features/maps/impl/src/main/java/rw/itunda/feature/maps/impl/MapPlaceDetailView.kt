package rw.itunda.feature.maps.impl

import android.content.Intent
import android.net.Uri
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rw.itunda.core.designsystem.itundaface.ClockGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.BusTripDto
import rw.itunda.core.network.EatsReviewDto
import rw.itunda.core.network.MapPlaceDetailDto
import rw.itunda.core.network.MapsDirectionsResponse
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.PlaceSearchResultDto
import rw.itunda.core.network.RouteResultDto
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.TransitJourneyDto

// Extracted from MapScreen's own bottom-sheet lambda (2026-08-20), same real reason
// SharedFolderSection/ItineraryBuilderCard/AroundYouSection/MapTopChrome were already
// extracted before it. Pure "values in, callbacks out" rendering -- MapScreen still owns
// every var this reads; fetchDirections/clearRoute stay real functions in MapScreen
// (they need routing/error/myLocation/etc) and are passed down as plain callbacks.
@Composable
internal fun PlaceDetailAndRouteView(
    place: PlaceSearchResultDto,
    selectedMerchant: ShoppingMerchantDto?,
    placeProducts: List<MerchantProductDto>?,
    placeReviews: List<EatsReviewDto>?,
    placeDetail: MapPlaceDetailDto?,
    placeTab: PlaceTab,
    followedMerchantIds: Set<String>,
    following: Boolean,
    isBookmarked: Boolean,
    bookmarking: Boolean,
    savingToFolder: PlaceSearchResultDto?,
    folderNameInput: String,
    folderColorInput: String,
    isAgentCashDiscovery: Boolean,
    activeCategory: String?,
    routing: Boolean,
    route: MapsDirectionsResponse?,
    navigating: Boolean,
    travelMode: String,
    otherModeEtaMinutes: Double?,
    busSearching: Boolean,
    busTrips: List<BusTripDto>?,
    transitSearching: Boolean,
    transitJourneys: List<TransitJourneyDto>?,
    routeAlternatives: List<RouteResultDto>?,
    selectedRouteIndex: Int,
    showSteps: Boolean,
    currentStepIndex: Int,
    voiceEnabled: Boolean,
    onBack: () -> Unit,
    onOrderDelivery: (merchantId: String, businessName: String) -> Unit,
    onBookService: (MerchantProductDto) -> Unit,
    onFetchDirections: (String) -> Unit,
    onClearRoute: () -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleFollow: (String) -> Unit,
    onPlaceTabChange: (PlaceTab) -> Unit,
    onFolderNameChange: (String) -> Unit,
    onFolderColorChange: (String) -> Unit,
    onConfirmSaveToFolder: () -> Unit,
    onCancelSaveToFolder: () -> Unit,
    onSearchBus: (String) -> Unit,
    onSearchTransit: () -> Unit,
    onSelectRouteAlternative: (Int, RouteResultDto) -> Unit,
    onToggleShowSteps: () -> Unit,
    onStartNavigation: () -> Unit,
    onEndNavigation: () -> Unit,
    onToggleVoice: () -> Unit,
) {
    val context = LocalContext.current
    val (placeName, placeAddress) = splitPlaceName(place.displayName)
    val matchedMerchant = selectedMerchant
    // Real "one thing per page" fix (2026-08-09) -- direct user feedback: "flower of
    // info, you can't just put everything on one page" (Toss's own product principle
    // #8). Place browsing (this whole block) and route planning/navigation (below) are
    // mutually exclusive, not stacked.
    if (route == null) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    placeName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Ids.colors.textPrimary,
                )
                if (placeAddress != null) {
                    Text(placeAddress, fontSize = 12.sp, color = Ids.colors.textSecondary, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        // Real Naver Maps-style pill action row (2026-08-09) -- Share and Save
        // (bookmark), always real; Call, only when this merchant actually has a real
        // phoneNumber set.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
            PlaceActionPill(
                icon = if (isBookmarked) "★" else "☆",
                label = if (isBookmarked) "Saved" else "Save",
                filled = isBookmarked,
                enabled = !bookmarking,
                onClick = onToggleBookmark,
            )
            PlaceActionPill(
                icon = "📤",
                label = "Share",
                filled = false,
                enabled = true,
                onClick = {
                    // Real "share this place" (2026-07-22) -- ported from bank-mfe's own
                    // real Web Share/clipboard action. Plain name+coordinate text via
                    // Android's native share sheet, not a link into itunda's own domain.
                    val text = "${place.displayName} (${String.format(java.util.Locale.US, "%.6f", place.latitude)}, ${String.format(java.util.Locale.US, "%.6f", place.longitude)})"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(intent, place.displayName))
                },
            )
            val callNumber = selectedMerchant?.phoneNumber
            if (callNumber != null) {
                PlaceActionPill(
                    icon = "📞",
                    label = "Call",
                    filled = false,
                    enabled = true,
                    onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$callNumber"))) },
                )
            }
            // Real "배달" (Delivery) pill (2026-08-09) -- only when a real orderable catalog exists.
            if (selectedMerchant != null && !placeProducts.isNullOrEmpty()) {
                PlaceActionPill(
                    icon = "🛵",
                    label = "Delivery",
                    filled = false,
                    enabled = true,
                    onClick = { onOrderDelivery(selectedMerchant.merchantId, selectedMerchant.businessName) },
                )
            }
            val followMerchantId = selectedMerchant?.merchantId
            if (followMerchantId != null) {
                val followed = followMerchantId in followedMerchantIds
                PlaceActionPill(
                    icon = if (followed) "🔔" else "🔕",
                    label = if (followed) "Following" else "Notify me",
                    filled = followed,
                    enabled = !following,
                    onClick = { onToggleFollow(followMerchantId) },
                )
            }
        }
        if (matchedMerchant != null) {
            // Real "Itunda Places" tab row (2026-08-09) -- each tab only appears once its
            // real fetch returned content; INFO is unconditional (a real dead-code bug
            // fixed this pass -- defined in PlaceTab but never added to this row before).
            val showMenuTab = !placeProducts.isNullOrEmpty()
            val showReviewsTab = !placeReviews.isNullOrEmpty()
            val showPhotosTab = !placeDetail?.photoUrls.isNullOrEmpty() || placeDetail?.photoUrl != null
            val showNewsTab = !placeDetail?.updates.isNullOrEmpty()
            if (showMenuTab || showReviewsTab || showPhotosTab || showNewsTab) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                    listOfNotNull(
                        PlaceTab.HOME,
                        PlaceTab.MENU.takeIf { showMenuTab },
                        PlaceTab.REVIEWS.takeIf { showReviewsTab },
                        PlaceTab.PHOTOS.takeIf { showPhotosTab },
                        PlaceTab.NEWS.takeIf { showNewsTab },
                        PlaceTab.INFO,
                    ).forEach { tab ->
                        val label = when (tab) {
                            PlaceTab.HOME -> "Home"
                            PlaceTab.MENU -> "Menu (${placeProducts?.size ?: 0})"
                            PlaceTab.REVIEWS -> "Reviews (${placeReviews?.size ?: 0})"
                            PlaceTab.PHOTOS -> "Photos"
                            PlaceTab.NEWS -> "News"
                            PlaceTab.INFO -> "Info"
                        }
                        val active = placeTab == tab
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.pressScaleClickable { onPlaceTabChange(tab) },
                        ) {
                            Text(
                                label, fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) Ids.colors.brand else Ids.colors.textSecondary,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .height(2.dp)
                                    .width(if (active) 20.dp else 0.dp)
                                    .background(Ids.colors.brand, RoundedCornerShape(1.dp)),
                            )
                        }
                    }
                }
            }
        }
        if (matchedMerchant != null && placeTab == PlaceTab.MENU) {
            // Real per-merchant menu (2026-08-09) -- the exact same MerchantProductDto
            // Commerce/Eats checkout already uses, not new or invented data.
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                placeProducts.orEmpty().forEach { product ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (product.imageUrl != null) {
                            AsyncImage(
                                model = product.imageUrl,
                                contentDescription = product.name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            val original = product.originalPrice
                            if (original != null && original > product.price) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("${String.format(java.util.Locale.US, "%,.0f", original)} RWF", fontSize = 11.sp, color = Ids.colors.textTertiary, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                                    Text("${String.format(java.util.Locale.US, "%,.0f", product.price)} RWF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.danger)
                                }
                            } else {
                                Text("${String.format(java.util.Locale.US, "%,.0f", product.price)} RWF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textPrimary)
                            }
                        }
                        // Real bookable-service entry point (moved here 2026-08-25 from
                        // :features:shop:impl -- see MapsBooking.kt's own doc comment).
                        // A product with a real durationMinutes set is a real-time
                        // appointment at this physical place, not a cart-able good.
                        if (product.durationMinutes != null && matchedMerchant != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Ids.colors.brand)
                                    .pressScaleClickable { onBookService(product) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) { Text("Book", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
        if (matchedMerchant != null && placeTab == PlaceTab.PHOTOS) {
            PlacePhotosTab(placeDetail)
        }
        if (matchedMerchant != null && placeTab == PlaceTab.NEWS) { PlaceNewsTab(placeDetail) }
        if (matchedMerchant != null && placeTab == PlaceTab.INFO) { PlaceInfoTab(matchedMerchant.category, matchedMerchant.openingHours, matchedMerchant.phoneNumber) }
        if (matchedMerchant != null && placeTab == PlaceTab.REVIEWS) {
            // Real preset-tag aggregate (2026-08-28) -- real counts from real submitted tags only, never fabricated.
            PlaceGoodPointsRow(placeDetail)
            // Real transaction-verified reviews (2026-08-09) -- the exact same
            // EatsReviewDto Eats' own review UI already renders.
            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                placeReviews.orEmpty().forEach { review ->
                    Column {
                        Text("⭐".repeat(review.restaurantRating), fontSize = 12.sp)
                        val comment = review.restaurantComment
                        if (!comment.isNullOrBlank()) {
                            Text(comment, fontSize = 13.sp, color = Ids.colors.textPrimary, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (review.photoUrl != null) {
                            AsyncImage(
                                model = review.photoUrl,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.padding(top = 4.dp).size(width = 120.dp, height = 80.dp).clip(RoundedCornerShape(8.dp)),
                            )
                        }
                        val ownerReply = review.ownerReply
                        if (!ownerReply.isNullOrBlank()) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .background(Ids.colors.surfaceSoft, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                            ) {
                                Text("Owner's reply", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Ids.colors.textSecondary)
                                Text(ownerReply, fontSize = 12.sp, color = Ids.colors.textPrimary, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            }
        }
        if (matchedMerchant != null && placeTab == PlaceTab.HOME) {
            // Real self-hosted AI summary (2026-08-28, itunda Maps redesign) -- see
            // AiSummaryService's own doc comment on the backend: generated only from
            // real, already-known facts, always shown with a visible "AI" disclosure.
            PlaceAiSummaryCard(placeDetail)
            // Real simplicity fix (2026-08-09) -- grouped into 3 lines: (category ·
            // rating · distance), (cashback · min order), (hours), plus phone as the
            // one real tappable action.
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (matchedMerchant.photoUrl != null) {
                    AsyncImage(
                        model = matchedMerchant.photoUrl,
                        contentDescription = matchedMerchant.businessName,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val glanceLine = listOfNotNull(
                        matchedMerchant.category,
                        matchedMerchant.rating?.let { r ->
                            "⭐ ${String.format(java.util.Locale.US, "%.1f", r)}" + if (matchedMerchant.reviewCount > 0) " (${matchedMerchant.reviewCount})" else ""
                        },
                        matchedMerchant.distanceKm?.let { d ->
                            val eta = matchedMerchant.deliveryTimeMinutes?.let { " · ~$it min" } ?: ""
                            "${String.format(java.util.Locale.US, "%.1f", d)} km$eta"
                        },
                    ).joinToString(" · ")
                    if (glanceLine.isNotEmpty()) {
                        Text(glanceLine, fontSize = 12.sp, color = Ids.colors.textSecondary)
                    }
                    val valueLine = listOfNotNull(
                        "${matchedMerchant.cashbackRate} cashback",
                        matchedMerchant.minOrderAmount?.let { "Min. ${String.format(java.util.Locale.US, "%,.0f", it)} RWF" },
                    ).joinToString(" · ")
                    Text(valueLine, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand)
                    val openingHours = matchedMerchant.openingHours
                    if (openingHours != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            ClockGlyph(size = 11.dp)
                            Text(openingHours, fontSize = 11.sp, color = Ids.colors.textSecondary)
                        }
                    }
                    val phoneNumber = matchedMerchant.phoneNumber
                    if (phoneNumber != null) {
                        // Real "call + copy" row (2026-08-09).
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                            Text(
                                "📞 $phoneNumber",
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ids.colors.brand,
                                modifier = Modifier.pressScaleClickable {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")))
                                },
                            )
                            Text(
                                "Copy",
                                fontSize = 11.sp, color = Ids.colors.textTertiary,
                                modifier = Modifier.pressScaleClickable {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Phone number", phoneNumber))
                                },
                            )
                        }
                    }
                }
            }
        }
        // Real folder/color picker (2026-07-22) -- only expanded for the place
        // actually being saved right now.
        if (savingToFolder != null && savingToFolder.latitude == place.latitude && savingToFolder.longitude == place.longitude) {
            BookmarkFolderDialog(
                folderNameInput = folderNameInput,
                folderColorInput = folderColorInput,
                bookmarking = bookmarking,
                onFolderNameChange = onFolderNameChange,
                onFolderColorChange = onFolderColorChange,
                onConfirmSaveToFolder = onConfirmSaveToFolder,
                onCancelSaveToFolder = onCancelSaveToFolder,
            )
        }
        if (isAgentCashDiscovery && activeCategory == "ITUNDA_AGENT") {
            Text(
                "This is an Itunda agent location. Confirm the cash is ready before showing your withdrawal code.",
                fontSize = 12.sp,
                color = Ids.colors.textSecondary,
            )
        }
        // Real single "Directions" entry point (2026-08-09) -- mode selection now
        // happens on the dedicated route-planning view below, not here.
        Box(
            modifier = Modifier
                .background(Ids.colors.brand, RoundedCornerShape(12.dp))
                .pressScaleClickable(enabled = !routing) { onFetchDirections(travelMode) }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) { Text(if (routing) "Finding real route…" else "Directions", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp) }
        if (isAgentCashDiscovery && activeCategory == "ITUNDA_AGENT") {
            Text(
                "Back to cash-out codes",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Ids.colors.brand,
                modifier = Modifier
                    .fillMaxWidth()
                    .pressScaleClickable(onClick = onBack)
                    .padding(vertical = 8.dp),
            )
        }
    } else {
        // Real route-planning / active-navigation view (2026-08-09) -- its own screen
        // now, never stacked beneath place info. Only one of {place info, this} is
        // ever visible at a time. Extracted to RoutePlanningView (2026-08-20) since
        // this file itself crossed the 500-line guideline.
        val currentRoute = route
        if (currentRoute != null) {
            RoutePlanningView(
                placeName = placeName,
                currentRoute = currentRoute,
                navigating = navigating,
                travelMode = travelMode,
                otherModeEtaMinutes = otherModeEtaMinutes,
                routing = routing,
                busSearching = busSearching,
                busTrips = busTrips,
                transitSearching = transitSearching,
                transitJourneys = transitJourneys,
                routeAlternatives = routeAlternatives,
                selectedRouteIndex = selectedRouteIndex,
                showSteps = showSteps,
                currentStepIndex = currentStepIndex,
                voiceEnabled = voiceEnabled,
                onClearRoute = onClearRoute,
                onFetchDirections = onFetchDirections,
                onSearchBus = onSearchBus,
                onSearchTransit = onSearchTransit,
                onSelectRouteAlternative = onSelectRouteAlternative,
                onToggleShowSteps = onToggleShowSteps,
                onStartNavigation = onStartNavigation,
                onEndNavigation = onEndNavigation,
                onToggleVoice = onToggleVoice,
            )
        }
    }
    // A little breathing room below so the drag-to-Full state doesn't cut the last
    // line off against the screen edge.
    Box(modifier = Modifier.height(24.dp))
}
