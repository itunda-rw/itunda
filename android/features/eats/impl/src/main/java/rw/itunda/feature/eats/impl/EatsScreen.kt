package rw.itunda.feature.eats.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.LiveRiderMiniMap
import rw.itunda.core.designsystem.components.QtyButton
import rw.itunda.core.designsystem.components.RouteMiniMap
import rw.itunda.core.designsystem.components.SearchAndCategoryChips
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.StarRatingRow
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddressSuggestionDto
import rw.itunda.core.network.DineInOrderDto
import rw.itunda.core.network.DineInOrderItemRequest
import rw.itunda.core.network.EATS_MEMBERSHIP_TIERS
import rw.itunda.core.network.PLATFORM_MEMBERSHIP_TIERS
import rw.itunda.core.network.PlatformMembershipDto
import rw.itunda.core.network.SubscribePlatformMembershipRequest
import rw.itunda.core.network.EatsDishDto
import rw.itunda.core.network.EatsMembershipDto
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemRequest
import rw.itunda.core.network.EatsRatingResponse
import rw.itunda.core.network.EatsReviewDto
import rw.itunda.core.network.FavoriteRestaurantDto
import rw.itunda.core.network.SubscribeEatsMembershipRequest
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PlaceDineInOrderRequest
import rw.itunda.core.network.PlaceEatsOrderRequest
import rw.itunda.core.network.RiderDto
import rw.itunda.core.network.SetRiderAvailabilityRequest
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.SubmitEatsReviewRequest
import rw.itunda.core.network.UpdateEatsOrderStatusRequest
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID

// Sixth Feature extraction (2026-07-23) after Marketplace/Jobs/Property/Community/Shop,
// same template -- see features/marketplace/impl/.../MarketplaceScreen.kt's own header
// comment for the full account. Deliver (the rider role) is folded into this same module
// rather than its own -- EatsContent's own EatsMode.DELIVER toggle calls DeliverContent
// directly (a real, tight coupling that already existed in app/ui/SuperAppTabs.kt: rider
// deliveries reuse EatsOrderDto/EatsOrderRow/nextRiderStatus/EATS_STATUS_LABEL directly),
// so splitting them into two Gradle modules would just recreate that coupling as a
// forbidden Feature-to-Feature dependency instead of removing it.
//
// One injected slot remains, same reasoning as every other extraction this session:
// deviceStepUpHost wraps :features:payments:impl's DeviceStepUpDialog, so it bridges two
// Feature modules -- must stay an app-level composition (see ShopScreen.kt's own header
// comment for the fuller explanation of why this one is architectural, not just a
// build-scope convenience). RouteMiniMap itself no longer needs injecting -- MapConfig.kt
// (2026-07-23) gave it the same BuildConfig-avoidance NetworkClient.init already had, so
// it's imported directly from core/designsystem, same as every other shared UI atom.

private val EATS_STATUS_LABEL = mapOf(
    "PLACED" to "Placed",
    "ACCEPTED" to "Accepted by restaurant",
    "PREPARING" to "Preparing",
    "READY_FOR_PICKUP" to "Ready for pickup",
    "RIDER_ASSIGNED" to "Rider on the way to restaurant",
    "PICKED_UP" to "Picked up — on the way",
    "DELIVERED" to "Delivered",
    "CANCELLED" to "Cancelled — refunded",
)

private val DINE_IN_STATUS_LABEL = mapOf(
    "PLACED" to "Placed",
    "ACCEPTED" to "Accepted by restaurant",
    "PREPARING" to "Preparing",
    "SERVED" to "Served",
    "CANCELLED" to "Cancelled — refunded",
)

private val RIDER_STATUS_CHAIN = listOf("RIDER_ASSIGNED", "PICKED_UP", "DELIVERED")

// Real order-status stepper (2026-08-10) -- Uber Eats/Coupang Eats/배민 all render the
// pipeline as a visual progress track, not just a status word, so a customer can see at
// a glance how close their food is without reading. Two variants because the real
// backend pipeline forks at READY_FOR_PICKUP (EatsOrderService.kt): a delivery order
// gets a rider hop (RIDER_ASSIGNED -> PICKED_UP), a pickup order goes straight to
// DELIVERED with no rider -- order.riderId being null throughout a pickup order's life
// is what distinguishes the two without needing a client-side fulfillmentType field.
private val EATS_DELIVERY_STEPS = listOf(
    "PLACED" to "Placed",
    "ACCEPTED" to "Accepted",
    "PREPARING" to "Preparing",
    "READY_FOR_PICKUP" to "Ready",
    "RIDER_ASSIGNED" to "Rider assigned",
    "PICKED_UP" to "On the way",
    "DELIVERED" to "Delivered",
)
private val EATS_PICKUP_STEPS = listOf(
    "PLACED" to "Placed",
    "ACCEPTED" to "Accepted",
    "PREPARING" to "Preparing",
    "READY_FOR_PICKUP" to "Ready for pickup",
    "DELIVERED" to "Picked up",
)

@Composable
private fun EatsStatusStepper(status: String, hasRider: Boolean) {
    val steps = if (hasRider) EATS_DELIVERY_STEPS else EATS_PICKUP_STEPS
    val currentIndex = steps.indexOfFirst { it.first == status }
    if (currentIndex < 0) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, (_, label) ->
            val reached = i <= currentIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (reached) Ids.colors.brand else Ids.colors.surfaceSoft),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, fontSize = 10.sp, color = if (reached) Ids.colors.textPrimary else Ids.colors.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
            }
            if (i < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .height(2.dp)
                        .background(if (i < currentIndex) Ids.colors.brand else Ids.colors.surfaceSoft),
                )
            }
        }
    }
}

private fun nextRiderStatus(current: String): String? {
    val idx = RIDER_STATUS_CHAIN.indexOf(current)
    return if (idx >= 0 && idx + 1 < RIDER_STATUS_CHAIN.size) RIDER_STATUS_CHAIN[idx + 1] else null
}

private enum class EatsMode { ORDER, DELIVER }

@Composable
fun EatsContent(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    // Real "Delivery" pill deep-link from Maps (2026-08-09) -- see
    // ItundaAppScreen.kt's own doc comment on pendingEatsMerchantId for the full
    // account. Forces ORDER mode (not DELIVER) since a pending target is always a real
    // merchant to order FROM, never a rider-role entry point.
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
) {
    var mode by remember { mutableStateOf(EatsMode.ORDER) }
    LaunchedEffect(pendingMerchantId) {
        if (pendingMerchantId != null) mode = EatsMode.ORDER
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal)) {
        // Real de-emphasis (2026-07-24) -- "Deliver" (the rider role) previously got
        // equal 50% visual weight next to "Order food" as a full segmented toggle,
        // even though itunda already ships a dedicated, separate riderapp
        // (rw.itunda.rider) for exactly this role. Stacked on top of Shop/Eats' own
        // toggle above and Restaurants/Favorites/My orders below, that read as three
        // full tiers of chrome before any real content -- most people opening Eats
        // are ordering, not delivering. Kept reachable (a rider without the separate
        // app installed can still use it here) as a small secondary link instead of
        // an equal peer tab.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Text(
                text = if (mode == EatsMode.ORDER) "Deliver instead" else "Back to ordering",
                color = Ids.colors.textBrand,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.clickable { mode = if (mode == EatsMode.ORDER) EatsMode.DELIVER else EatsMode.ORDER },
            )
        }
        when (mode) {
            EatsMode.ORDER -> OrderFoodContent(
                deviceStepUpHost,
                pendingMerchantId = pendingMerchantId,
                pendingMerchantName = pendingMerchantName,
                onPendingMerchantConsumed = onPendingMerchantConsumed,
            )
            EatsMode.DELIVER -> DeliverContent()
        }
    }
}

// Real per-configuration cart line (2026-07-21) -- ports bank-mfe's own EatsCartLine
// (BankDashboard.tsx) 1:1. `choiceIds` is empty for any item with no option groups --
// the pre-existing, unaffected case. Two lines for the same productId with DIFFERENT
// choiceIds are genuinely distinct cart entries (e.g. a Regular and a Large of the same
// burger, side by side) -- closes docs/DESIGN_REFERENCES.md's Eats recommendation #6.
private data class EatsCartLine(val productId: String, val quantity: Int, val choiceIds: List<String> = emptyList())

private fun eatsCartKey(productId: String, choiceIds: List<String>): String =
    if (choiceIds.isEmpty()) productId else "$productId::${choiceIds.sorted().joinToString(",")}"

// Real Coupang Eats-style photo-forward restaurant card (2026-07-24) -- confirmed via
// real reference research that Coupang Eats leads every restaurant card with real food
// photography (not a store icon/logo) specifically because it reads faster than text,
// and deliberately keeps the info-dense secondary line (rating/distance/ETA/min-order)
// itunda already had -- the same research flagged that Coupang Eats itself hides that
// line until you open the restaurant, which it calls out as a real usability flaw, so
// this keeps it visible rather than copying that specific weakness.
@Composable
private fun RestaurantCard(m: ShoppingMerchantDto, isFavorite: Boolean, favoriteBusy: Boolean, onOpen: () -> Unit, onToggleFavorite: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = Ids.layout.cardCornerRadius, topEnd = Ids.layout.cardCornerRadius))) {
                if (m.photoUrl != null) {
                    SubcomposeAsyncImage(
                        model = m.photoUrl,
                        contentDescription = m.businessName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (painter.state) {
                            is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                            else -> RestaurantPhotoPlaceholder()
                        }
                    }
                } else {
                    RestaurantPhotoPlaceholder()
                }
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) Ids.colors.danger else Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(22.dp)
                        .clickable(enabled = !favoriteBusy, onClick = onToggleFavorite),
                )
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(m.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    listOfNotNull(m.category, "${m.cashbackRate} cashback").joinToString(" · "),
                    color = Ids.colors.textSecondary,
                    fontSize = 12.sp,
                )
                if (m.rating != null || m.distanceKm != null || m.minOrderAmount != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        if (m.rating != null) {
                            Icon(Icons.Outlined.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("%.1f (%d)".format(m.rating, m.reviewCount), color = Ids.colors.textSecondary, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            listOfNotNull(
                                m.distanceKm?.let { "%.1f km".format(it) },
                                m.deliveryTimeMinutes?.let { "~$it min" },
                                m.minOrderAmount?.let { "Min ${it.toLong()} RWF" },
                            ).joinToString(" · "),
                            color = Ids.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

// Real Coupang Eats-style dish grid (2026-08-03) -- see EatsDishDto's own doc comment
// for the sourcing (a real Coupang Eats UX teardown, re-verified directly against the
// primary article text: "actual food photographs arranged in a three-column grid...
// much more intuitive"; the same article separately confirms rating/delivery-time/fee
// are NOT shown here, only surfacing once you open the restaurant -- matching this
// grid's deliberately bare tile). Tapping a dish opens its restaurant (itunda's own
// domain model requires the restaurant context to price/order any item, not a claim
// about Coupang Eats specifically) -- there's no standalone dish-detail concept.
// Additive, not a replacement for the restaurant list below: that list's search-by-name
// and full alphabetical browse are real, working, and this dish endpoint has no
// text-search of its own, so removing the list would be a real functionality loss, not
// just a visual one. Hidden once a real search is active for the same reason.
@Composable
private fun EatsDishGrid(dishes: List<EatsDishDto>, onOpen: (EatsDishDto) -> Unit) {
    if (dishes.isEmpty()) return
    val rows = (dishes.size + 2) / 3
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(148.dp * rows),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        gridItems(dishes, key = { it.id }) { dish ->
            Column(modifier = Modifier.clickable { onOpen(dish) }) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(Ids.layout.cardCornerRadius))) {
                    if (dish.imageUrl != null) {
                        SubcomposeAsyncImage(
                            model = dish.imageUrl,
                            contentDescription = dish.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            when (painter.state) {
                                is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                                else -> RestaurantPhotoPlaceholder()
                            }
                        }
                    } else {
                        RestaurantPhotoPlaceholder()
                    }
                }
                Text(dish.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Text(dish.merchantName, color = Ids.colors.textSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RestaurantPhotoPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Storefront, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(40.dp))
    }
}

// Real, human-readable summary of a resolved cart line's selected options -- mirrors
// the backend's own EatsOrderService.buildSelectedOptionsJson, but purely for display;
// pricing always comes from the real menu item + real choice deltas, never this string.
private fun eatsOptionsSummary(item: MerchantProductDto, choiceIds: List<String>): String {
    if (choiceIds.isEmpty()) return ""
    val names = item.optionGroups.flatMap { it.choices }.filter { it.id in choiceIds }.map { it.name }
    return if (names.isEmpty()) "" else " (${names.joinToString(", ")})"
}

private fun eatsLineUnitPrice(item: MerchantProductDto, choiceIds: List<String>): Double {
    val delta = item.optionGroups.flatMap { it.choices }.filter { it.id in choiceIds }.sumOf { it.priceDelta }
    return item.price + delta
}

private enum class OrderFoodView { BROWSE, FAVORITES, ORDERS }

// Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
// PlatformMembershipDto's own doc comment. bank-mfe already has this; this is the
// first Android client. Deliberately a separate card from EatsMembershipCard below,
// not a replacement: waives the fee at every restaurant, no merchant opt-in required.
@Composable
private fun PlatformMembershipCard() {
    var membership by remember { mutableStateOf<PlatformMembershipDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                membership = NetworkClient.apiService.getMyPlatformMembership().membership
            } catch (e: Exception) {
                // Real, non-critical -- the rest of Eats still works without this card.
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    val current = membership
    if (!loaded) return

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("itunda Plus", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (current != null && java.time.Instant.parse(current.activeUntil).isAfter(java.time.Instant.now())) {
                val activeUntilDate = java.time.Instant.parse(current.activeUntil).let {
                    java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.systemDefault()).toLocalDate()
                }
                Text(
                    "Free delivery active until $activeUntilDate at every restaurant, no participation required.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    "Free delivery at every restaurant -- no minimum order, no restaurant opt-in required.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PLATFORM_MEMBERSHIP_TIERS.forEach { tier ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.brand)
                                .clickable(enabled = !busy) {
                                    busy = true
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.subscribePlatformMembership(
                                                java.util.UUID.randomUUID().toString(),
                                                SubscribePlatformMembershipRequest(tier.days),
                                            )
                                            load()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            error = "Couldn't reach itunda. Check your connection and try again."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (busy) "…" else "${tier.days} days -- %,d RWF".format(tier.priceRwf),
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// Real Baemin Club (배민클럽)-style free-delivery membership (item 209) -- see
// EatsMembershipDto's own doc comment. First Android client; bank-mfe already has this
// (item 102).
@Composable
private fun EatsMembershipCard() {
    var membership by remember { mutableStateOf<EatsMembershipDto?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                membership = NetworkClient.apiService.getMyEatsMembership().membership
            } catch (e: Exception) {
                // Real, non-critical -- the rest of Eats still works without this card.
            } finally {
                loaded = true
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    val current = membership
    if (!loaded) return

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Eats Club", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (current != null && java.time.Instant.parse(current.activeUntil).isAfter(java.time.Instant.now())) {
                val activeUntilDate = java.time.Instant.parse(current.activeUntil).let {
                    java.time.LocalDateTime.ofInstant(it, java.time.ZoneId.systemDefault()).toLocalDate()
                }
                Text(
                    "Free delivery active until $activeUntilDate at participating restaurants.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                Text(
                    "Free delivery at participating restaurants -- no minimum order.",
                    color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EATS_MEMBERSHIP_TIERS.forEach { tier ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.brand)
                                .clickable(enabled = !busy) {
                                    busy = true
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.subscribeEatsMembership(
                                                java.util.UUID.randomUUID().toString(),
                                                SubscribeEatsMembershipRequest(tier.days),
                                            )
                                            load()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            error = "Couldn't reach itunda. Check your connection and try again."
                                        } finally {
                                            busy = false
                                        }
                                    }
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (busy) "…" else "${tier.days} days -- %,d RWF".format(tier.priceRwf),
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderFoodContent(
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
) {
    var view by remember { mutableStateOf(OrderFoodView.BROWSE) }
    var restaurants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    // Unfiltered, fetched once -- used to resolve a past order's restaurant for Reorder
    // even when that restaurant has been filtered out of the currently-browsed list.
    var allRestaurants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedRestaurant by remember { mutableStateOf<ShoppingMerchantDto?>(null) }
    var menu by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    val cart = remember { mutableStateMapOf<String, EatsCartLine>() }
    var showCheckout by remember { mutableStateOf(false) }
    var confirmedOrder by remember { mutableStateOf<EatsOrderDto?>(null) }
    var confirmedDineInOrder by remember { mutableStateOf<DineInOrderDto?>(null) }
    var reorderingId by remember { mutableStateOf<String?>(null) }
    var reorderError by remember { mutableStateOf<String?>(null) }
    // Real bookmarked/favorited restaurants (2026-07-19) -- a set of restaurant ids for
    // a fast star-toggle lookup on each browse card.
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoritingId by remember { mutableStateOf<String?>(null) }
    // Real dish grid (2026-08-03) -- see EatsDishGrid's own doc comment.
    var dishes by remember { mutableStateOf<List<EatsDishDto>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    fun loadDishes() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getEatsDishes(selectedCategory)
                if (res.success) dishes = res.dishes
            } catch (e: Exception) { /* non-critical -- the restaurant list below still works */ }
        }
    }
    LaunchedEffect(selectedCategory) { loadDishes() }

    fun loadFavorites() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteRestaurants()
                if (res.success) favoriteIds = res.favorites.map { it.restaurantId }.toSet()
            } catch (e: Exception) { /* non-critical, only backs the star toggle */ }
        }
    }

    fun toggleFavorite(restaurantId: String) {
        favoritingId = restaurantId
        coroutineScope.launch {
            try {
                if (restaurantId in favoriteIds) {
                    NetworkClient.apiService.removeFavoriteRestaurant(restaurantId)
                    favoriteIds = favoriteIds - restaurantId
                } else {
                    NetworkClient.apiService.addFavoriteRestaurant(restaurantId)
                    favoriteIds = favoriteIds + restaurantId
                }
            } catch (e: Exception) { /* real, non-critical -- a failed toggle just leaves the star as-is */ } finally {
                favoritingId = null
            }
        }
    }

    fun loadRestaurants() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getShoppingMerchants(selectedCategory, searchInput.trim().ifBlank { null })
                if (res.success) restaurants = res.merchants
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) {
        loadRestaurants()
        loadFavorites()
        try {
            val allRes = NetworkClient.apiService.getShoppingMerchants()
            if (allRes.success) allRestaurants = allRes.merchants
        } catch (e: Exception) { /* non-critical, only backs the Reorder lookup */ }
        try {
            val catRes = NetworkClient.apiService.getMerchantCategories()
            if (catRes.success) categories = catRes.categories
        } catch (e: Exception) { /* non-critical, only backs the category chip row */ }
    }
    // Real category/search filter (2026-07-19), debounced so typing doesn't re-fetch on
    // every keystroke -- LaunchedEffect's own cancel-and-restart-on-key-change is the
    // debounce mechanism here.
    LaunchedEffect(selectedCategory, searchInput) {
        delay(300)
        loadRestaurants()
    }

    fun openRestaurant(m: ShoppingMerchantDto) {
        selectedRestaurant = m
        cart.clear()
        menu = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMerchantProducts(m.merchantId)
                if (res.success) menu = res.products
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    // Real dish-grid tap-through (2026-08-03) -- see EatsDishGrid's own doc comment on
    // why this opens the restaurant rather than a standalone dish page. Resolves against
    // the already-loaded allRestaurants for the real category/cashbackRate, falling back
    // to a minimal stub built from the dish's own merchantId/merchantName -- same
    // fallback shape FavoriteRestaurantsView's onOpen already uses below.
    fun openDish(dish: EatsDishDto) {
        val restaurant = allRestaurants?.find { it.merchantId == dish.merchantId }
            ?: ShoppingMerchantDto(merchantId = dish.merchantId, businessName = dish.merchantName, category = null, cashbackRate = "1%")
        openRestaurant(restaurant)
    }

    // Real "Delivery" pill deep-link from Maps (2026-08-09) -- same real fallback shape
    // openDish above already established (resolve against the already-loaded real
    // catalog, fall back to a minimal stub carrying the real businessName Maps already
    // had, never a blank one) applied to a second real entry point into the same
    // openRestaurant flow. Consumed exactly once -- onPendingMerchantConsumed clears the
    // shell's own state so navigating away and back to Shop doesn't reopen it.
    LaunchedEffect(pendingMerchantId) {
        val merchantId = pendingMerchantId ?: return@LaunchedEffect
        val restaurant = allRestaurants?.find { it.merchantId == merchantId }
            ?: ShoppingMerchantDto(merchantId = merchantId, businessName = pendingMerchantName ?: "", category = null, cashbackRate = "1%")
        openRestaurant(restaurant)
        onPendingMerchantConsumed()
    }

    // Real "Reorder" button (2026-07-19): re-populate the cart from a past order's real
    // items, cross-referenced against the restaurant's current menu -- discontinued items
    // are silently dropped rather than added as phantom cart lines.
    fun handleReorder(order: EatsOrderDto) {
        val restaurant = allRestaurants?.find { it.merchantId == order.restaurantId }
        if (restaurant == null) {
            reorderError = "This restaurant is no longer available."
            return
        }
        reorderingId = order.id
        reorderError = null
        coroutineScope.launch {
            try {
                val orderDetail = NetworkClient.apiService.getEatsOrder(order.id)
                val menuRes = NetworkClient.apiService.getMerchantProducts(order.restaurantId)
                if (!orderDetail.success || !menuRes.success) {
                    reorderError = "Could not reorder."
                    return@launch
                }
                val activeProducts = menuRes.products.filter { it.active }.associateBy { it.id }
                // Real reorder-cart sanitization (2026-07-21) -- a reordered past order carries
                // no option selections. If the item now genuinely requires one, that bare line
                // can never check out -- drop it rather than let checkout silently 422, same
                // "discontinued item silently dropped" precedent already established above for
                // a menu item that's gone entirely.
                val newCart = mutableMapOf<String, EatsCartLine>()
                orderDetail.items.forEach { item ->
                    val product = activeProducts[item.productId]
                    if (product != null && product.optionGroups.isEmpty()) {
                        val key = eatsCartKey(item.productId, emptyList())
                        newCart[key] = EatsCartLine(item.productId, (newCart[key]?.quantity ?: 0) + item.quantity)
                    }
                }
                if (newCart.isEmpty()) {
                    reorderError = "None of the items from that order are on the menu anymore."
                    return@launch
                }
                selectedRestaurant = restaurant
                menu = menuRes.products
                cart.clear()
                cart.putAll(newCart)
                view = OrderFoodView.BROWSE
            } catch (e: HttpException) {
                reorderError = superAppErrorMessage(e)
            } catch (e: IOException) {
                reorderError = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                reorderingId = null
            }
        }
    }

    val confirmed = confirmedOrder
    if (confirmed != null) {
        EatsOrderConfirmationView(confirmed, onDone = {
            confirmedOrder = null
            selectedRestaurant = null
            menu = null
            cart.clear()
            showCheckout = false
            view = OrderFoodView.ORDERS
        })
        return
    }
    val confirmedDineIn = confirmedDineInOrder
    if (confirmedDineIn != null) {
        DineInOrderConfirmationView(confirmedDineIn, onDone = {
            confirmedDineInOrder = null
            selectedRestaurant = null
            menu = null
            cart.clear()
            showCheckout = false
            view = OrderFoodView.ORDERS
        })
        return
    }

    val restaurant = selectedRestaurant
    if (restaurant != null) {
        if (showCheckout) {
            EatsCheckoutView(
                restaurant = restaurant,
                cart = cart,
                menu = menu.orEmpty(),
                onBack = { showCheckout = false },
                onOrderPlaced = { order -> confirmedOrder = order },
                onDineInOrderPlaced = { order -> confirmedDineInOrder = order },
                deviceStepUpHost = deviceStepUpHost,
            )
        } else {
            RestaurantMenuView(
                restaurant = restaurant,
                menu = menu,
                cart = cart,
                onBack = { selectedRestaurant = null },
                onCheckout = { showCheckout = true },
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)) {
        item { PlatformMembershipCard() }
        item { EatsMembershipCard() }
        item {
            // Flat, horizontally-scrolling category strip (2026-07-24), same
            // Karrot/Toss-Shopping-style treatment as Hood's own nav rows --
            // replacing a filled-pill segmented control.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                listOf(OrderFoodView.BROWSE to "Restaurants", OrderFoodView.FAVORITES to "Favorites", OrderFoodView.ORDERS to "My orders").forEach { (v, label) ->
                    val selected = v == view
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { view = v }) {
                        Text(
                            label,
                            color = if (selected) Ids.colors.textPrimary else Ids.colors.textSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        )
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(18.dp)
                                .background(if (selected) Ids.colors.brand else Color.Transparent, RoundedCornerShape(1.dp)),
                        )
                    }
                }
            }
        }
        if (view == OrderFoodView.ORDERS) {
            item {
                MyEatsOrdersView(onReorder = ::handleReorder, reorderingId = reorderingId, restaurants = allRestaurants)
                val reorderErr = reorderError
                if (reorderErr != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(reorderErr, color = Ids.colors.danger, fontSize = 13.sp)
                }
                MyDineInOrdersView()
            }
        } else if (view == OrderFoodView.FAVORITES) {
            item {
                FavoriteRestaurantsView(
                    onOpen = { fav ->
                        val restaurant = allRestaurants?.find { it.merchantId == fav.restaurantId }
                            ?: ShoppingMerchantDto(merchantId = fav.restaurantId, businessName = fav.businessName, category = fav.category, cashbackRate = "1%")
                        openRestaurant(restaurant)
                    },
                    onChanged = ::loadFavorites,
                )
            }
        } else {
            item {
                SearchAndCategoryChips(
                    searchInput = searchInput,
                    onSearchChange = { searchInput = it },
                    placeholder = "Search restaurants",
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { c -> selectedCategory = if (c == selectedCategory) null else c },
                )
            }
            if (searchInput.isBlank() && dishes.isNotEmpty()) {
                item { EatsDishGrid(dishes, onOpen = ::openDish) }
            }
            if (error != null) {
                item { ErrorCard(error!!, onRetry = ::loadRestaurants) }
            } else if (restaurants == null) {
                item { SkeletonBlock() }
            } else if (restaurants!!.isEmpty()) {
                item {
                    EmptyState(
                        // Real copy-voice fix (item 244, round 5 of the empty-state pass):
                        // the "registered yet" case is honest about whose gap this is --
                        // no restaurant has joined yet, not something the reader is missing
                        // a step on, matching docs/COPY_VOICE.md's "when the cause is
                        // someone else's, say so honestly" rule.
                        if (selectedCategory != null || searchInput.isNotBlank()) "No restaurants match your search — try a different category or search term." else "No restaurants registered yet — check back once restaurants in your area join itunda Eats.",
                        icon = Icons.Outlined.Restaurant,
                    )
                }
            } else {
                items(restaurants!!, key = { it.merchantId }) { m -> RestaurantCard(m, isFavorite = m.merchantId in favoriteIds, favoriteBusy = favoritingId == m.merchantId, onOpen = { openRestaurant(m) }, onToggleFavorite = { toggleFavorite(m.merchantId) }) }
            }
        }
    }
}

@Composable
private fun FavoriteRestaurantsView(onOpen: (FavoriteRestaurantDto) -> Unit, onChanged: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoriteRestaurantDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteRestaurants()
                if (res.success) favorites = res.favorites
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun remove(restaurantId: String) {
        removingId = restaurantId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.removeFavoriteRestaurant(restaurantId)
                favorites = favorites?.filterNot { it.restaurantId == restaurantId }
                onChanged()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                removingId = null
            }
        }
    }

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        favorites == null -> SkeletonBlock()
        favorites!!.isEmpty() -> EmptyState("No favorite restaurants yet. Tap the heart on a restaurant to save it here.", icon = Icons.Outlined.FavoriteBorder)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            favorites!!.forEach { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                        .background(Ids.colors.surface)
                        .clickable { onOpen(f) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ids.colors.brand)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(f.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (f.category != null) "${f.category} · Real menu, real delivery" else "Real menu, real delivery",
                            color = Ids.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Remove from favorites",
                        tint = Ids.colors.danger,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(enabled = removingId != f.restaurantId) { remove(f.restaurantId) },
                    )
                }
            }
        }
    }
}

// Real written-review list + owner-reply display (item 184/185) -- bank-mfe already has
// this (item 184); this is the first Android client. Mirrors ProductRatingBadge's own
// expand-on-click pattern exactly (ShopScreen.kt, this app's Commerce equivalent).
@Composable
private fun RestaurantRatingBadge(restaurantId: String) {
    var rating by remember { mutableStateOf<EatsRatingResponse?>(null) }
    var open by remember { mutableStateOf(false) }
    var reviews by remember { mutableStateOf<List<EatsReviewDto>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(restaurantId) {
        try {
            rating = NetworkClient.apiService.getRestaurantRating(restaurantId)
        } catch (e: Exception) {
            // Real, non-critical -- a rating fetch failure shouldn't block browsing the menu.
        }
    }
    val r = rating
    if (r != null && r.count > 0) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    val next = !open
                    open = next
                    if (next && reviews == null) {
                        coroutineScope.launch {
                            try {
                                reviews = NetworkClient.apiService.getRestaurantReviews(restaurantId).reviews
                            } catch (e: Exception) {
                                reviews = emptyList()
                            }
                        }
                    }
                },
            ) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("%.1f (%d)".format(r.average ?: 0.0, r.count), color = Ids.colors.textSecondary, fontSize = 13.sp)
            }
            if (open) {
                val list = reviews
                if (list == null) {
                    Text("Loading reviews…", color = Ids.colors.textSecondary, fontSize = 12.sp)
                } else if (list.isEmpty()) {
                    EmptyState("No written reviews yet — be the first to share how it went.", icon = Icons.Outlined.RateReview)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
                        list.forEach { rv ->
                            val stars = "★".repeat(rv.restaurantRating) + "☆".repeat(5 - rv.restaurantRating)
                            Text(
                                if (rv.restaurantComment.isNullOrBlank()) stars else "$stars — ${rv.restaurantComment}",
                                color = Ids.colors.textSecondary,
                                fontSize = 12.sp,
                            )
                            // Real review photo (2026-08-04) -- see EatsReviewDto.photoUrl's
                            // own doc comment.
                            if (!rv.photoUrl.isNullOrBlank()) {
                                SubcomposeAsyncImage(
                                    model = rv.photoUrl,
                                    contentDescription = "Review photo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp).size(72.dp).clip(RoundedCornerShape(8.dp)),
                                ) {
                                    when (painter.state) {
                                        is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                                        else -> RestaurantPhotoPlaceholder()
                                    }
                                }
                            }
                            if (!rv.ownerReply.isNullOrBlank()) {
                                Text(
                                    "↳ Restaurant: ${rv.ownerReply}",
                                    color = Ids.colors.textTertiary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewOrderCard(order: EatsOrderDto) {
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var restaurantRating by remember { mutableStateOf(0) }
    var restaurantComment by remember { mutableStateOf("") }
    // Real optional review photo (2026-08-04) -- see EatsReviewDto.photoUrl's own doc
    // comment. Same real-external-URL-only convention as Merchant.photoUrl's own input
    // elsewhere in this app -- a real URL the buyer pastes, never an upload pipeline.
    var photoUrl by remember { mutableStateOf("") }
    var riderRating by remember { mutableStateOf(0) }
    var riderComment by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("Thanks for your review!", color = Ids.colors.textSecondary, fontSize = 13.sp)
        return
    }
    if (!open) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Rate this order", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        Column {
            Text("Restaurant", color = Ids.colors.textSecondary, fontSize = 12.sp)
            StarRatingRow(restaurantRating) { restaurantRating = it }
            IdsTextField(
                value = restaurantComment,
                onValueChange = { restaurantComment = it },
                label = "How was the food? (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
            IdsTextField(
                value = photoUrl,
                onValueChange = { photoUrl = it },
                label = "Photo URL of your food (optional)",
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        Column {
            Text("Rider", color = Ids.colors.textSecondary, fontSize = 12.sp)
            StarRatingRow(riderRating) { riderRating = it }
            IdsTextField(
                value = riderComment,
                onValueChange = { riderComment = it },
                label = "How was the delivery? (optional)",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).clickable { open = false }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (submitting) Ids.colors.textTertiary else Ids.colors.brand)
                    .clickable(enabled = !submitting) {
                        if (restaurantRating == 0 || riderRating == 0) {
                            error = "Rate both the restaurant and the rider."
                            return@clickable
                        }
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.submitEatsReview(
                                    order.id,
                                    SubmitEatsReviewRequest(restaurantRating, restaurantComment.trim().ifBlank { null }, riderRating, riderComment.trim().ifBlank { null }, photoUrl.trim().ifBlank { null }),
                                )
                                done = true
                            } catch (e: HttpException) {
                                // A 409 here is the real ORDER_ALREADY_REVIEWED case in
                                // practice -- this form only ever renders for a real
                                // DELIVERED order, so the sibling "not yet delivered"
                                // 409 can't actually occur through this UI path.
                                if (e.code() == 409) {
                                    done = true
                                } else {
                                    error = superAppErrorMessage(e)
                                }
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (submitting) "Submitting…" else "Submit review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun RestaurantMenuView(
    restaurant: ShoppingMerchantDto,
    menu: List<MerchantProductDto>?,
    cart: SnapshotStateMap<String, EatsCartLine>,
    onBack: () -> Unit,
    onCheckout: () -> Unit,
) {
    BackHandler(onBack = onBack)
    // Real menu-options selection UI (2026-07-21, v1: required single-select only) --
    // ports bank-mfe's own MenuView 1:1. Only one item's option panel is expanded at a
    // time, matching this file's own established "inline-card-replaces-trigger"
    // convention (no modal-overlay pattern exists anywhere in this app).
    var expandedProductId by remember { mutableStateOf<String?>(null) }
    val pendingChoices = remember { mutableStateMapOf<String, String>() }
    val cartCount = cart.values.sumOf { it.quantity }

    fun setSimpleQty(productId: String, qty: Int) {
        val key = eatsCartKey(productId, emptyList())
        cart[key] = EatsCartLine(productId, maxOf(0, qty))
    }

    fun toggleExpand(productId: String) {
        pendingChoices.clear()
        expandedProductId = if (expandedProductId == productId) null else productId
    }

    fun addConfiguredToCart(item: MerchantProductDto) {
        val groups = item.optionGroups
        val choiceIds = groups.mapNotNull { pendingChoices[it.id] }
        if (choiceIds.size != groups.size) return // one real required choice per group, enforced client-side too
        val key = eatsCartKey(item.id, choiceIds)
        cart[key] = EatsCartLine(item.id, (cart[key]?.quantity ?: 0) + 1, choiceIds)
        pendingChoices.clear()
        expandedProductId = null
    }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = Ids.layout.screenVertical)) {
        BackTopBar(restaurant.businessName, onBack)
        RestaurantRatingBadge(restaurant.merchantId)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (menu == null) {
                item { SkeletonBlock(height = 72.dp) }
            } else if (menu.isEmpty()) {
                item { EmptyState("This restaurant hasn't added menu items yet — check back soon.", icon = Icons.Outlined.RestaurantMenu) }
            } else {
                items(menu, key = { it.id }) { p ->
                    val hasOptions = p.optionGroups.isNotEmpty()
                    val simpleKey = eatsCartKey(p.id, emptyList())
                    val simpleQty = if (hasOptions) 0 else (cart[simpleKey]?.quantity ?: 0)
                    val isExpanded = expandedProductId == p.id
                    val allGroupsChosen = p.optionGroups.all { pendingChoices[it.id] != null }
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(16.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(
                                    "%,.0f RWF".format(p.price) + if (hasOptions) " · options required" else "",
                                    color = Ids.colors.textSecondary, fontSize = 13.sp,
                                )
                            }
                            if (hasOptions) {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).clickable { toggleExpand(p.id) }.padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(if (isExpanded) "Close" else "Choose options", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    QtyButton("-") { if (simpleQty > 0) setSimpleQty(p.id, simpleQty - 1) }
                                    Text(simpleQty.toString(), modifier = Modifier.width(28.dp), textAlign = TextAlign.Center, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                                    QtyButton("+") { setSimpleQty(p.id, simpleQty + 1) }
                                }
                            }
                        }
                        if (hasOptions && isExpanded) {
                            Column(modifier = Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                p.optionGroups.forEach { group ->
                                    Column {
                                        Row {
                                            Text(group.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("· choose 1", color = Ids.colors.textTertiary, fontSize = 13.sp)
                                        }
                                        Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            group.choices.forEach { choice ->
                                                val selected = pendingChoices[group.id] == choice.id
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.fillMaxWidth().clickable { pendingChoices[group.id] = choice.id },
                                                ) {
                                                    androidx.compose.material3.RadioButton(selected = selected, onClick = { pendingChoices[group.id] = choice.id })
                                                    Text(
                                                        choice.name + if (choice.priceDelta > 0) " (+%,.0f RWF)".format(choice.priceDelta) else "",
                                                        color = Ids.colors.textPrimary, fontSize = 13.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (allGroupsChosen) Ids.colors.brand else Ids.colors.textTertiary)
                                        .clickable(enabled = allGroupsChosen) { addConfiguredToCart(p) }
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text("Add to cart", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
        if (cartCount > 0) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand).clickable(onClick = onCheckout).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checkout ($cartCount item${if (cartCount == 1) "" else "s"})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Real self-hosted address-search autocomplete (2026-07-18) -- itunda's own Nominatim
// geocoder, not a third-party Maps API. Mirrors bank-mfe's AddressAutocomplete component:
// debounced real search-as-you-type, a real suggestion dropdown, and on selection the
// real resolved coordinates are handed back so the caller can submit them explicitly
// (taking priority over EatsOrderService's own automatic single-best-match fallback).
// Typing without selecting still places a real order via that fallback.
@Composable
private fun AddressAutocompleteField(
    address: String,
    onAddressChange: (String) -> Unit,
    onSuggestionSelected: (AddressSuggestionDto) -> Unit,
) {
    var suggestions by remember { mutableStateOf<List<AddressSuggestionDto>>(emptyList()) }
    var justSelected by remember { mutableStateOf(false) }

    LaunchedEffect(address) {
        if (justSelected) {
            justSelected = false
            return@LaunchedEffect
        }
        if (address.trim().length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        suggestions = try {
            val res = NetworkClient.apiService.searchDeliveryAddress(address.trim())
            if (res.success) res.suggestions else emptyList()
        } catch (e: Exception) {
            // Real, non-critical -- a failed suggestion fetch shouldn't block typing a
            // plain address; the order still places, just without a confirmed pin.
            emptyList()
        }
    }

    Column {
        IdsTextField(
            value = address,
            onValueChange = onAddressChange,
            label = "Delivery address",
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column {
                    suggestions.forEach { s ->
                        Text(
                            s.displayName,
                            color = Ids.colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    justSelected = true
                                    suggestions = emptyList()
                                    onSuggestionSelected(s)
                                }
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

private enum class EatsCheckoutMode { DELIVERY, PICKUP, DINE_IN }

@Composable
private fun EatsCheckoutView(
    restaurant: ShoppingMerchantDto,
    cart: Map<String, EatsCartLine>,
    menu: List<MerchantProductDto>,
    onBack: () -> Unit,
    onOrderPlaced: (EatsOrderDto) -> Unit,
    onDineInOrderPlaced: (DineInOrderDto) -> Unit,
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
) {
    BackHandler(onBack = onBack)
    var mode by remember { mutableStateOf(EatsCheckoutMode.DELIVERY) }
    var address by remember { mutableStateOf("") }
    var addressLatitude by remember { mutableStateOf<Double?>(null) }
    var addressLongitude by remember { mutableStateOf<Double?>(null) }
    var deliveryNotes by remember { mutableStateOf("") }
    // Real 배민오더-style table/QR ordering (2026-07-25) -- the table number a buyer
    // reads off the physical tag/QR at their table. See DineInOrder.kt's own doc
    // comment for why this is free text rather than a validated table registry.
    var tableNumber by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real device binding step-up (2026-07-21) -- Eats checkout was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
    // showed only a generic error, same fix already applied to Transfer/Savings.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val idempotencyKey = remember { UUID.randomUUID().toString() }

    val lines = cart.values.filter { it.quantity > 0 }.mapNotNull { line -> menu.find { it.id == line.productId }?.let { it to line } }
    val total = lines.sumOf { (p, line) -> eatsLineUnitPrice(p, line.choiceIds) * line.quantity }
    val canSubmit = when (mode) {
        EatsCheckoutMode.DELIVERY -> address.isNotBlank()
        EatsCheckoutMode.PICKUP -> true
        EatsCheckoutMode.DINE_IN -> tableNumber.isNotBlank()
    }

    suspend fun submitDelivery() {
        val res = NetworkClient.apiService.placeEatsOrder(
            idempotencyKey = idempotencyKey,
            request = PlaceEatsOrderRequest(
                restaurantId = restaurant.merchantId,
                items = lines.map { (p, line) -> EatsOrderItemRequest(p.id, line.quantity, line.choiceIds.ifEmpty { null }) },
                deliveryAddress = address.trim(),
                deliveryLatitude = addressLatitude,
                deliveryLongitude = addressLongitude,
                deliveryNotes = deliveryNotes.trim().ifBlank { null },
            ),
        )
        if (res.success) onOrderPlaced(res.order)
    }

    // Real Baemin-style 포장주문 (Pickup) order type (item 208) -- see
    // PlaceEatsOrderRequest.fulfillmentType's own doc comment.
    suspend fun submitPickup() {
        val res = NetworkClient.apiService.placeEatsOrder(
            idempotencyKey = idempotencyKey,
            request = PlaceEatsOrderRequest(
                restaurantId = restaurant.merchantId,
                items = lines.map { (p, line) -> EatsOrderItemRequest(p.id, line.quantity, line.choiceIds.ifEmpty { null }) },
                deliveryAddress = "",
                deliveryNotes = deliveryNotes.trim().ifBlank { null },
                fulfillmentType = "PICKUP",
            ),
        )
        if (res.success) onOrderPlaced(res.order)
    }

    suspend fun submitDineIn() {
        val res = NetworkClient.apiService.placeDineInOrder(
            idempotencyKey = idempotencyKey,
            request = PlaceDineInOrderRequest(
                restaurantId = restaurant.merchantId,
                tableNumber = tableNumber.trim(),
                items = lines.map { (p, line) -> DineInOrderItemRequest(p.id, line.quantity, line.choiceIds.ifEmpty { null }) },
            ),
        )
        if (res.success) onDineInOrderPlaced(res.order)
    }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = Ids.layout.screenVertical)) {
        BackTopBar("Checkout", onBack)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(lines, key = { (p, line) -> eatsCartKey(p.id, line.choiceIds) }) { (p, line) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.name}${eatsOptionsSummary(p, line.choiceIds)} x${line.quantity}", color = Ids.colors.textPrimary, fontSize = 14.sp)
                    Text("%,.0f RWF".format(eatsLineUnitPrice(p, line.choiceIds) * line.quantity), color = Ids.colors.textPrimary, fontSize = 14.sp)
                }
            }
            item { Divider(color = Ids.colors.divider, modifier = Modifier.padding(vertical = 10.dp)) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = Ids.colors.textPrimary, fontSize = 14.sp)
                    Text("%,.0f RWF".format(total), color = Ids.colors.textPrimary, fontSize = 14.sp)
                }
            }
            item { Spacer(modifier = Modifier.height(14.dp)) }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Ids.colors.surfaceSoft),
                ) {
                    listOf(EatsCheckoutMode.DELIVERY to "Delivery", EatsCheckoutMode.PICKUP to "Pickup", EatsCheckoutMode.DINE_IN to "Order at table").forEach { (m, label) ->
                        val selected = m == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) Ids.colors.brand else Color.Transparent)
                                .clickable(enabled = !submitting) { mode = m; error = null }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = if (selected) Color.White else Ids.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (mode == EatsCheckoutMode.DELIVERY) {
                item { Text("Plus a real delivery fee, added at checkout", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                item {
                    AddressAutocompleteField(
                        address = address,
                        onAddressChange = { address = it; addressLatitude = null; addressLongitude = null },
                        onSuggestionSelected = { s ->
                            address = s.displayName
                            addressLatitude = s.latitude
                            addressLongitude = s.longitude
                        },
                    )
                }
                if (addressLatitude != null) {
                    item { Text("Pinned -- real distance-based delivery fee applies", color = Ids.colors.success, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
                item {
                    IdsTextField(
                        value = deliveryNotes,
                        onValueChange = { if (it.length <= 500) deliveryNotes = it },
                        label = "Delivery notes (optional) -- e.g. Leave at the gate",
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            } else if (mode == EatsCheckoutMode.PICKUP) {
                item { Text("No delivery fee -- collect your order at the restaurant once it's ready", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                item {
                    IdsTextField(
                        value = deliveryNotes,
                        onValueChange = { if (it.length <= 500) deliveryNotes = it },
                        label = "Pickup notes (optional)",
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            } else {
                item { Text("No delivery fee -- served straight to your table", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp)) }
                item {
                    IdsTextField(
                        value = tableNumber,
                        onValueChange = { if (it.length <= 50) tableNumber = it },
                        label = "Table number -- e.g. 12",
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    )
                }
            }
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) } }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (submitting || !canSubmit) Ids.colors.textTertiary else Ids.colors.brand)
                .clickable(enabled = !submitting && canSubmit) {
                    submitting = true
                    error = null
                    needsDeviceVerification = false
                    coroutineScope.launch {
                        try {
                            when (mode) { EatsCheckoutMode.DELIVERY -> submitDelivery(); EatsCheckoutMode.PICKUP -> submitPickup(); EatsCheckoutMode.DINE_IN -> submitDineIn() }
                        } catch (e: HttpException) {
                            if (isDeviceNotVerifiedError(e)) {
                                needsDeviceVerification = true
                            } else {
                                error = superAppErrorMessage(e)
                            }
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            submitting = false
                        }
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (submitting) "Placing order…" else "Place order", color = Color.White, fontWeight = FontWeight.Bold) }
        deviceStepUpHost(
            needsDeviceVerification,
            { needsDeviceVerification = false },
            {
                needsDeviceVerification = false
                submitting = true
                try {
                    when (mode) { EatsCheckoutMode.DELIVERY -> submitDelivery(); EatsCheckoutMode.PICKUP -> submitPickup(); EatsCheckoutMode.DINE_IN -> submitDineIn() }
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: IOException) {
                    error = "Couldn't reach itunda. Check your connection and try again."
                } finally {
                    submitting = false
                }
            },
        )
    }
}

@Composable
private fun EatsOrderConfirmationView(order: EatsOrderDto, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Order placed") },
        text = {
            Column {
                Text("%,.0f RWF".format(order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Delivering to ${order.deliveryAddress}", color = Ids.colors.textSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Track order") } },
    )
}

@Composable
private fun DineInOrderConfirmationView(order: DineInOrderDto, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Order placed") },
        text = {
            Column {
                Text("%,.0f RWF".format(order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Table ${order.tableNumber} -- the kitchen has your order", color = Ids.colors.textSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}

@Composable
private fun EatsOrderRow(
    order: EatsOrderDto,
    restaurant: ShoppingMerchantDto? = null,
    action: (@Composable () -> Unit)? = null,
) {
    var showRoute by remember { mutableStateOf(false) }
    var showLiveTracking by remember { mutableStateOf(false) }
    val restaurantLat = restaurant?.latitude
    val restaurantLng = restaurant?.longitude
    val deliveryLat = order.deliveryLatitude
    val deliveryLng = order.deliveryLongitude
    val canShowRoute = restaurantLat != null && restaurantLng != null && deliveryLat != null && deliveryLng != null
    // Real live rider-location tracking (item 182) -- only while a rider is actually
    // en route, same gating as bank-mfe's own canShowLiveTracking.
    val canShowLiveTracking = canShowRoute && (order.status == "RIDER_ASSIGNED" || order.status == "PICKED_UP")
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(EATS_STATUS_LABEL[order.status] ?: order.status, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(order.deliveryAddress, color = Ids.colors.textSecondary, fontSize = 12.sp)
                }
                Text("%,.0f RWF".format(order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            if (order.status != "DELIVERED" && order.status != "CANCELLED") {
                // A PICKUP order's deliveryLatitude/Longitude are unconditionally null
                // (EatsOrderService.placeOrder) from creation on -- a real, always-present
                // signal, unlike riderId which is null for a DELIVERY order too until a
                // rider actually gets assigned partway through.
                EatsStatusStepper(order.status, hasRider = order.deliveryLatitude != null || order.riderId != null)
            }
            if (!order.deliveryNotes.isNullOrBlank()) {
                Text(
                    "Note: ${order.deliveryNotes}",
                    color = Ids.colors.textPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ids.colors.surfaceSoft)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            if (canShowLiveTracking) {
                ListingActionButton(if (showLiveTracking) "Hide live tracking" else "🛵 Track your rider live", false) {
                    showLiveTracking = !showLiveTracking
                    showRoute = false
                }
            }
            if (showLiveTracking && restaurant != null && restaurantLat != null && restaurantLng != null && deliveryLat != null && deliveryLng != null) {
                LiveRiderMiniMap(order.id, restaurantLat, restaurantLng, deliveryLat, deliveryLng, restaurant.businessName, "Delivery address")
            }
            // Real "view delivery route" (2026-07-19, item 8 on the Maps "100%" roadmap)
            // -- reuses itunda's own self-hosted OSRM directions.
            if (canShowRoute && !showLiveTracking) {
                ListingActionButton(if (showRoute) "Hide route" else "🚗 View real delivery route", false) { showRoute = !showRoute }
            }
            if (showRoute && !showLiveTracking && restaurant != null && restaurantLat != null && restaurantLng != null && deliveryLat != null && deliveryLng != null) {
                RouteMiniMap(restaurantLat, restaurantLng, deliveryLat, deliveryLng, restaurant.businessName, "Delivery address")
            }
            action?.invoke()
        }
    }
}

@Composable
private fun MyEatsOrdersView(
    onReorder: (EatsOrderDto) -> Unit,
    reorderingId: String?,
    restaurants: List<ShoppingMerchantDto>?,
) {
    var orders by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyEatsOrders()
                if (res.success) orders = res.orders
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    // Real poll for order-tracking status, same 4s cadence as Talk's own poll.
    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(4000)
        }
    }

    fun cancel(orderId: String) {
        cancellingId = orderId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelEatsOrder(orderId)
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                cancellingId = null
            }
        }
    }

    Column {
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else if (orders == null) {
            SkeletonBlock()
        } else if (orders!!.isEmpty()) {
            EmptyState("No orders yet — order from a nearby restaurant and it'll show up here.", icon = Icons.AutoMirrored.Outlined.ReceiptLong)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                orders!!.forEach { o ->
                    EatsOrderRow(o, restaurant = restaurants?.find { it.merchantId == o.restaurantId }) {
                        if (o.status == "PLACED") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Ids.colors.danger)
                                    .clickable(enabled = cancellingId != o.id) { cancel(o.id) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    if (cancellingId == o.id) "Cancelling…" else "Cancel order",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        } else if (o.status == "DELIVERED") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReviewOrderCard(o)
                                ReorderButton(reordering = reorderingId == o.id, onClick = { onReorder(o) })
                            }
                        } else if (o.status == "CANCELLED") {
                            ReorderButton(reordering = reorderingId == o.id, onClick = { onReorder(o) })
                        }
                    }
                }
            }
        }
    }
}

// Real 배민오더-style table/QR order history (2026-07-25) -- own real polling loop and
// own real cancel action, same shape MyEatsOrdersView already established, kept as a
// separate view rather than merged into it since DineInOrderDto/EatsOrderDto are
// genuinely different real types with no shared row component to reuse.
@Composable
private fun MyDineInOrdersView() {
    var orders by remember { mutableStateOf<List<DineInOrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyDineInOrders()
                if (res.success) orders = res.orders
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(4000)
        }
    }

    fun cancel(orderId: String) {
        cancellingId = orderId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelDineInOrder(orderId)
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                cancellingId = null
            }
        }
    }

    val list = orders
    if (list.isNullOrEmpty() && error == null) return
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text("Table orders", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list!!.forEach { o ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Table ${o.tableNumber}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("%,.0f RWF".format(o.totalAmount), color = Ids.colors.textPrimary, fontSize = 14.sp)
                            }
                            Text(DINE_IN_STATUS_LABEL[o.status] ?: o.status, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            if (o.status == "PLACED") {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 10.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Ids.colors.danger)
                                        .clickable(enabled = cancellingId != o.id) { cancel(o.id) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        if (cancellingId == o.id) "Cancelling…" else "Cancel order",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderButton(reordering: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ids.colors.brand)
            .clickable(enabled = !reordering, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            if (reordering) "Reordering…" else "Reorder",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun DeliverContent() {
    var rider by remember { mutableStateOf<RiderDto?>(null) }
    var loadedRider by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var registering by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var mine by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var busyOrderId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadRider() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyRiderProfile()
                if (res.success) rider = res.rider
                error = null
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    rider = null
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                loadedRider = true
            }
        }
    }
    LaunchedEffect(Unit) { loadRider() }

    suspend fun loadDeliveries() {
        try {
            val a = NetworkClient.apiService.getAvailableDeliveries()
            val m = NetworkClient.apiService.getRiderDeliveries()
            if (a.success) available = a.orders
            if (m.success) mine = m.orders
        } catch (_: Exception) {
            // Keep showing the last-known lists on a transient poll failure.
        }
    }
    LaunchedEffect(rider?.id) {
        if (rider == null) return@LaunchedEffect
        while (true) {
            loadDeliveries()
            delay(4000)
        }
    }

    if (!loadedRider) {
        SkeletonBlock()
        return
    }

    val currentRider = rider
    if (currentRider == null) {
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Deliver with Itunda", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Earn a real delivery fee for every order you deliver, paid straight to your wallet.",
                    color = Ids.colors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.brand)
                        .clickable(enabled = !registering) {
                            registering = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.registerRider()
                                    if (res.success) rider = res.rider
                                } catch (e: HttpException) {
                                    // Real Toss-style resolution (2026-08-10), matching
                                    // riderapp's own BecomeRiderScreen fix: registerRider's
                                    // only real 409 is RiderAlreadyRegisteredException --
                                    // the account genuinely IS already a rider, so load
                                    // their real profile and move forward instead of
                                    // showing an error for something that isn't actually
                                    // wrong.
                                    if (e.code() == 409) {
                                        loadRider()
                                    } else {
                                        error = superAppErrorMessage(e)
                                    }
                                } finally {
                                    registering = false
                                }
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                ) { Text(if (registering) "Registering…" else "Become a rider", color = Color.White, fontWeight = FontWeight.Bold) }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        return
    }

    val activeDeliveries = mine.orEmpty().filter { it.status != "DELIVERED" }
    val pastDeliveries = mine.orEmpty().filter { it.status == "DELIVERED" }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (currentRider.available) "You're online" else "You're offline", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(if (currentRider.available) "Visible for new deliveries" else "Go online to see deliveries", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (currentRider.available) Ids.colors.danger else Ids.colors.brand)
                            .clickable {
                                coroutineScope.launch {
                                    try {
                                        val res = NetworkClient.apiService.setRiderAvailability(SetRiderAvailabilityRequest(!currentRider.available))
                                        if (res.success) rider = res.rider
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text(if (currentRider.available) "Go offline" else "Go online", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
        }
        error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 12.sp) } }
        if (activeDeliveries.isNotEmpty()) {
            item { Text("Your active deliveries", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(activeDeliveries, key = { it.id }) { o ->
                val next = nextRiderStatus(o.status)
                EatsOrderRow(o) {
                    if (next != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ids.colors.brand)
                                .clickable(enabled = busyOrderId != o.id) {
                                    busyOrderId = o.id
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.updateRiderOrderStatus(o.id, UpdateEatsOrderStatusRequest(next))
                                            loadDeliveries()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } finally {
                                            busyOrderId = null
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                if (busyOrderId == o.id) "Updating…" else "Mark ${(EATS_STATUS_LABEL[next] ?: next).lowercase()}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
        if (currentRider.available) {
            item { Text("Available deliveries", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            if (available == null) {
                item { Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(100.dp)) {} }
            } else if (available!!.isEmpty()) {
                item { EmptyState("No deliveries waiting right now.", icon = Icons.AutoMirrored.Outlined.ReceiptLong) }
            } else {
                items(available!!, key = { it.id }) { o ->
                    EatsOrderRow(o) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ids.colors.brand)
                                .clickable(enabled = busyOrderId != o.id) {
                                    busyOrderId = o.id
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.claimDelivery(o.id)
                                            loadDeliveries()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } finally {
                                            busyOrderId = null
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) { Text(if (busyOrderId == o.id) "Claiming…" else "Claim delivery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
        if (pastDeliveries.isNotEmpty()) {
            item { Text("Completed", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(pastDeliveries, key = { it.id }) { o -> EatsOrderRow(o) }
        }
    }
}
