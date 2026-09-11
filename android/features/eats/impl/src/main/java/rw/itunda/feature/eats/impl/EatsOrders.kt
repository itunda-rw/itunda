package rw.itunda.feature.eats.impl

import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.LiveRiderMiniMap
import rw.itunda.core.designsystem.components.RouteMiniMap
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.DineInOrderDto
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException




@Composable
internal fun EatsOrderRow(
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
    // Real fix (2026-08-24, flat-design sweep): dropped the Card wrapper -- a real
    // order-history row, kept the per-row Divider convention (matching
    // ShopOrders.kt's identical CommerceOrderRow fix, docs/DESIGN_REFERENCES.md
    // §274).
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(EATS_STATUS_LABEL[order.status] ?: order.status, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(order.deliveryAddress, color = Ids.colors.textSecondary, fontSize = 12.sp)
                }
                Text(String.format(Locale.US, "%,.0f RWF", order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            if (order.status != "DELIVERED" && order.status != "CANCELLED") {
                // A PICKUP order's deliveryLatitude/Longitude are unconditionally null
                // (EatsOrderService.placeOrder) from creation on -- a real, always-present
                // signal, unlike riderId which is null for a DELIVERY order too until a
                // rider actually gets assigned partway through.
                EatsStatusStepper(order.status, hasRider = order.deliveryLatitude != null || order.riderId != null)
            }
            // Real fresh Uber Eats research (2026-08-15) -- see EatsOrderDto's own doc
            // comment. Rider name + a real distance-derived arrival estimate, matching
            // Uber Eats' own sourced tracker redesign ("Latest Arrival By" shown
            // alongside the driver's name). Only meaningful once a rider is actually
            // assigned -- both fields are null until then, never a fabricated ETA.
            if (order.riderName != null || order.estimatedArrivalMinutes != null) {
                Text(
                    listOfNotNull(
                        order.riderName?.let { "Rider: $it" },
                        order.estimatedArrivalMinutes?.let { "Latest arrival by ~$it min" },
                    ).joinToString(" · "),
                    color = Ids.colors.textSecondary, fontSize = 12.sp,
                )
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
    Divider(color = Ids.colors.divider, thickness = 0.5.dp)
}

@Composable
internal fun MyEatsOrdersView(
    onReorder: (EatsOrderDto) -> Unit,
    reorderingId: String?,
    restaurants: List<ShoppingMerchantDto>?,
) {
    var orders by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    // Real optimistic-hide for TipRiderPrompt -- same pattern bank-mfe's own
    // tippedOrderIds establishes, since a fresh getMyEatsOrders() poll would otherwise
    // briefly still show the prompt until this order's own tipAmount round-trips back.
    var tippedOrderIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Real pagination fix (2026-09-11, ported from bank-mfe's own fix -- see
    // project_itunda_pagination_discard_sweep memory): page 0 is polled every
    // 4s for real-time order-status accuracy, so it must always stay a live,
    // page-0-only fetch. olderOrders is a separate accumulator populated only
    // by loadMoreOrders, never touched by the poll.
    var olderOrders by remember { mutableStateOf<List<EatsOrderDto>>(emptyList()) }
    var ordersPage by remember { mutableStateOf(0) }
    var ordersHasMore by remember { mutableStateOf(false) }
    var loadingMoreOrders by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyEatsOrders(page = 0)
                if (res.success) {
                    orders = res.orders
                    ordersHasMore = res.page + 1 < res.totalPages
                }
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    fun loadMoreOrders() {
        val nextPage = ordersPage + 1
        loadingMoreOrders = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyEatsOrders(page = nextPage)
                if (res.success) {
                    olderOrders = olderOrders + res.orders
                    ordersPage = nextPage
                    ordersHasMore = res.page + 1 < res.totalPages
                }
            } catch (_: Exception) {
                // Non-critical -- leave state as-is, the button just stays visible to retry.
            } finally {
                loadingMoreOrders = false
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

    val allOrders = (orders ?: emptyList()) + olderOrders
    Column {
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else if (orders == null) {
            SkeletonBlock()
        } else if (allOrders.isEmpty()) {
            EmptyState("No orders yet — order from a nearby restaurant and it'll show up here.", icon = Icons.AutoMirrored.Outlined.ReceiptLong)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                allOrders.forEach { o ->
                    EatsOrderRow(o, restaurant = restaurants?.find { it.merchantId == o.restaurantId }) {
                        if (o.status == "PLACED") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Ids.colors.danger)
                                    .pressScaleClickable(enabled = cancellingId != o.id) { cancel(o.id) }
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
                                if (o.riderId != null && o.tipAmount == null && !tippedOrderIds.contains(o.id)) {
                                    TipRiderPrompt(orderId = o.id, onTipped = { tippedOrderIds = tippedOrderIds + o.id })
                                }
                                ReorderButton(reordering = reorderingId == o.id, onClick = { onReorder(o) })
                            }
                        } else if (o.status == "CANCELLED") {
                            ReorderButton(reordering = reorderingId == o.id, onClick = { onReorder(o) })
                        }
                    }
                }
                if (ordersHasMore) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ids.colors.surfaceSoft)
                            .pressScaleClickable(enabled = !loadingMoreOrders) { loadMoreOrders() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (loadingMoreOrders) "Loading…" else "Load more",
                            color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
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
internal fun MyDineInOrdersView() {
    var orders by remember { mutableStateOf<List<DineInOrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    // Real pagination fix (2026-09-11, ported from bank-mfe's own fix -- see
    // project_itunda_pagination_discard_sweep memory): page 0 is polled
    // every 4s for real-time order-status accuracy, so it must always stay
    // a live, page-0-only fetch. olderOrders is a separate accumulator
    // populated only by loadMoreOrders, never touched by the poll.
    var olderOrders by remember { mutableStateOf<List<DineInOrderDto>>(emptyList()) }
    var ordersPage by remember { mutableStateOf(0) }
    var ordersHasMore by remember { mutableStateOf(false) }
    var loadingMoreOrders by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyDineInOrders(page = 0)
                if (res.success) {
                    orders = res.orders
                    ordersHasMore = res.page + 1 < res.totalPages
                }
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    fun loadMoreOrders() {
        val nextPage = ordersPage + 1
        loadingMoreOrders = true
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyDineInOrders(page = nextPage)
                if (res.success) {
                    olderOrders = olderOrders + res.orders
                    ordersPage = nextPage
                    ordersHasMore = res.page + 1 < res.totalPages
                }
            } catch (_: Exception) {
                // Non-critical -- leave state as-is, the button just stays visible to retry.
            } finally {
                loadingMoreOrders = false
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

    val list = (orders ?: emptyList()) + olderOrders
    if (list.isEmpty() && error == null) return
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text("Table orders", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Real fix (2026-08-24, flat-design sweep): dropped the per-row Card
                // -- a history log of table orders, kept the per-row Divider
                // convention (docs/DESIGN_REFERENCES.md §274).
                list.forEach { o ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Table ${o.tableNumber}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(String.format(Locale.US, "%,.0f RWF", o.totalAmount), color = Ids.colors.textPrimary, fontSize = 14.sp)
                        }
                        Text(DINE_IN_STATUS_LABEL[o.status] ?: o.status, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        if (o.status == "PLACED") {
                            Box(
                                modifier = Modifier
                                    .padding(top = 10.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Ids.colors.danger)
                                    .pressScaleClickable(enabled = cancellingId != o.id) { cancel(o.id) }
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
                    Divider(color = Ids.colors.divider, thickness = 0.5.dp)
                }
                if (ordersHasMore) {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Ids.colors.surfaceSoft)
                            .pressScaleClickable(enabled = !loadingMoreOrders) { loadMoreOrders() }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (loadingMoreOrders) "Loading…" else "Load more",
                            color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReorderButton(reordering: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ids.colors.brand)
            .pressScaleClickable(enabled = !reordering, onClick = onClick)
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

// DeliverContent moved to EatsDeliver.kt (2026-08-19) -- see that file's own
// header comment.
