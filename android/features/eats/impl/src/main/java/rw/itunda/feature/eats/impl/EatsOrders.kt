package rw.itunda.feature.eats.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.DineInOrderDto
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.Locale

@Composable
internal fun MyEatsOrdersView(
    onBack: () -> Unit,
    onReorder: (EatsOrderDto) -> Unit,
    onTrack: (EatsOrderDto) -> Unit,
    reorderingId: String?,
    restaurants: List<ShoppingMerchantDto>?,
    onReportIssue: (String, String?) -> Unit = { _, _ -> },
    onBrowseRestaurants: () -> Unit = {},
    reorderError: String? = null,
    modifier: Modifier = Modifier,
) {
    var orders by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    var tippedOrderIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var olderOrders by remember { mutableStateOf<List<EatsOrderDto>>(emptyList()) }
    var ordersPage by remember { mutableStateOf(0) }
    var ordersHasMore by remember { mutableStateOf(false) }
    var loadingMoreOrders by remember { mutableStateOf(false) }
    var orderItems by remember { mutableStateOf<Map<String, List<EatsOrderItemDto>>>(emptyMap()) }
    var receiptOrder by remember { mutableStateOf<EatsOrderDto?>(null) }
    var receiptItems by remember { mutableStateOf<List<EatsOrderItemDto>?>(null) }
    var helpOrder by remember { mutableStateOf<EatsOrderDto?>(null) }
    var helpReason by remember { mutableStateOf<String?>(null) }
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
                // Keep the current list and leave the control retryable.
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
    LaunchedEffect(allOrders.map { it.id }) {
        allOrders.forEach { order ->
            if (order.id !in orderItems) {
                runCatching { NetworkClient.apiService.getEatsOrder(order.id).items }
                    .onSuccess { orderItems = orderItems + (order.id to it) }
            }
        }
    }

    fun openReceipt(order: EatsOrderDto) {
        receiptOrder = order
        receiptItems = orderItems[order.id]
        if (receiptItems == null) {
            coroutineScope.launch {
                runCatching { NetworkClient.apiService.getEatsOrder(order.id).items }
                    .onSuccess {
                        orderItems = orderItems + (order.id to it)
                        receiptItems = it
                    }
            }
        }
    }

    BackHandler(enabled = receiptOrder == null && helpOrder == null, onBack = onBack)
    if (receiptOrder != null) {
        val order = receiptOrder!!
        EatsReceiptSheet(
            order = order,
            restaurantName = restaurants?.find { it.merchantId == order.restaurantId }?.businessName ?: "Restaurant",
            items = receiptItems ?: orderItems[order.id],
            onDismiss = { receiptOrder = null; receiptItems = null },
        )
        return
    }
    if (helpOrder != null) {
        val order = helpOrder!!
        EatsHelpSheet(
            order = order,
            restaurantName = restaurants?.find { it.merchantId == order.restaurantId }?.businessName ?: "Restaurant",
            selectedReason = helpReason,
            onReasonSelected = { helpReason = it },
            onSubmit = { reason ->
                onReportIssue(order.transactionId, reason)
                helpOrder = null
                helpReason = null
            },
            onDismiss = { helpOrder = null; helpReason = null },
        )
        return
    }

    val p = eatsPrototypePalette()
    Column(modifier.fillMaxSize().background(p.background)) {
        EatsOrdersPageHeader(title = "Orders", onBack = onBack)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when {
                error != null -> ErrorCard(error!!, onRetry = ::load)
                orders == null -> SkeletonBlock()
                allOrders.isEmpty() -> EatsOrdersEmptyState(onBrowseRestaurants)
                else -> {
                    if (NetworkClient.demoEatsOrdersFixture) EatsOrdersPointsRow()
                    allOrders.forEach { order ->
                        EatsOrderHistoryRow(
                            order = order,
                            restaurant = restaurants?.find { it.merchantId == order.restaurantId },
                            items = orderItems[order.id],
                            reordering = reorderingId == order.id,
                            cancelling = cancellingId == order.id,
                            onTrack = { onTrack(order) },
                            onReorder = { onReorder(order) },
                            onReceipt = { openReceipt(order) },
                            onHelp = { helpOrder = order; helpReason = null },
                            onCancel = { cancel(order.id) },
                        )
                        if (!NetworkClient.demoMode && order.status == "DELIVERED") {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                                ReviewOrderCard(order)
                                if (order.riderId != null && order.tipAmount == null && !tippedOrderIds.contains(order.id)) {
                                    TipRiderPrompt(orderId = order.id, onTipped = { tippedOrderIds = tippedOrderIds + order.id })
                                }
                            }
                        }
                    }
                    if (ordersHasMore) {
                        Box(
                            Modifier.padding(horizontal = 24.dp, vertical = 12.dp).clip(RoundedCornerShape(12.dp))
                                .background(p.surfaceSoft)
                                .pressScaleClickable(enabled = !loadingMoreOrders) { loadMoreOrders() }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                if (loadingMoreOrders) "Loading…" else "Load more",
                                color = p.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
            if (reorderError != null) {
                Text(reorderError, color = Ids.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
            }
            MyDineInOrdersView()
        }
    }
}

@Composable
private fun EatsOrdersEmptyState(onBrowseRestaurants: () -> Unit) {
    val p = eatsPrototypePalette()
    Column(
        Modifier.fillMaxWidth().padding(top = 72.dp).padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No orders yet", color = p.primary, fontSize = 18.sp, lineHeight = 25.2.sp, fontWeight = FontWeight.Bold)
        Text(
            "Your food orders will show up here.",
            color = p.secondary, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier.width(201.dp).height(40.dp).clip(RoundedCornerShape(18.dp)).background(p.selected)
                .pressScaleClickable(onClick = onBrowseRestaurants),
            contentAlignment = Alignment.Center,
        ) {
            Text("Browse restaurants", color = p.selectedText, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EatsOrdersPointsRow() {
    val p = eatsPrototypePalette()
    Row(
        Modifier.fillMaxWidth().height(33.dp).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Points", color = p.secondary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("453", color = p.primary, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.ExtraBold)
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

// DeliverContent moved to EatsDeliver.kt (2026-08-19) -- see that file's own
// header comment.
