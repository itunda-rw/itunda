package rw.itunda.feature.shop.impl

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.SimpleLiveRiderMiniMap
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.DecideOrderReturnRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OrderDto
import rw.itunda.core.network.OrderReturnRequestDto
import rw.itunda.core.network.UpdateOrderStatusRequest
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException




// Real merchant-side Commerce order fulfillment queue (item 234) -- found via a
// sibling-consistency audit against bank-mfe's own MerchantOrdersView, which has had
// this since before this session: a real itunda user who also runs a merchant
// storefront could manage their store's orders on bank-mfe but had zero client
// anywhere on Android. Android port, straight mirror of bank-mfe's own component.
internal val COMMERCE_MERCHANT_STATUS_CHAIN = listOf("PLACED", "PACKED", "SHIPPED", "DELIVERED")

@Composable
internal fun MerchantOrdersView() {
    var orders by remember { mutableStateOf<List<OrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyOrderId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMerchantOrders()
                if (res.success) orders = res.orders
                error = null
            } catch (e: HttpException) {
                // A real, expected error for any account that hasn't registered as a
                // merchant -- stays silent rather than alarming the common case of a
                // buyer-only account, matching bank-mfe's own MerchantOrdersView.
                if (rw.itunda.core.network.apiErrorCode(e) == "MERCHANT_NOT_FOUND") {
                    orders = emptyList()
                } else {
                    error = superAppErrorMessage(e)
                }
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

    fun advance(order: OrderDto) {
        val idx = COMMERCE_MERCHANT_STATUS_CHAIN.indexOf(order.status)
        val next = COMMERCE_MERCHANT_STATUS_CHAIN.getOrNull(idx + 1) ?: return
        busyOrderId = order.id
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.updateOrderStatus(order.id, UpdateOrderStatusRequest(next))
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyOrderId = null
            }
        }
    }

    val list = orders
    if (error != null) {
        ErrorCard(error!!, onRetry = ::load)
        return
    }
    if (list == null) {
        SkeletonBlock()
        return
    }
    if (list.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
        Text("Orders for your store", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        list.forEach { o ->
            val idx = COMMERCE_MERCHANT_STATUS_CHAIN.indexOf(o.status)
            val next = COMMERCE_MERCHANT_STATUS_CHAIN.getOrNull(idx + 1)
            CommerceOrderRow(o) {
                if (next != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (busyOrderId == o.id) Ids.colors.textTertiary else Ids.colors.brand)
                            .pressScaleClickable(enabled = busyOrderId != o.id) { advance(o) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (busyOrderId == o.id) "Updating…" else "Mark ${(COMMERCE_STATUS_LABEL[next] ?: next).lowercase()}",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun MerchantReturnQueueView() {
    var requests by remember { mutableStateOf<List<OrderReturnRequestDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMerchantReturnQueue()
                if (res.success) requests = res.returnRequests
                error = null
            } catch (e: HttpException) {
                if (rw.itunda.core.network.apiErrorCode(e) == "MERCHANT_NOT_FOUND") {
                    requests = emptyList()
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(8000)
        }
    }

    fun decide(id: String, approve: Boolean) {
        busyId = id
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.decideOrderReturn(id, DecideOrderReturnRequest(approve))
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyId = null
            }
        }
    }

    val list = requests
    if (error != null) {
        ErrorCard(error!!, onRetry = ::load)
        return
    }
    if (list == null) {
        SkeletonBlock()
        return
    }
    val open = list.filter { it.status == "REQUESTED" }
    if (open.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
        Text("Return & exchange requests", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        open.forEach { r ->
            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(if (r.type == "RETURN") "Return requested" else "Exchange requested", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(r.reasonCode.replace("_", " ").lowercase(), color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                    r.reasonNote?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 13.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (busyId == r.id) Ids.colors.textTertiary else Ids.colors.brand)
                                .pressScaleClickable(enabled = busyId != r.id) { decide(r.id, true) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busyId == r.id) "…" else "Approve", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.surface)
                                .pressScaleClickable(enabled = busyId != r.id) { decide(r.id, false) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Reject", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MyCommerceOrdersView(
    onReorder: (OrderDto) -> Unit,
    reorderingId: String?,
) {
    var orders by remember { mutableStateOf<List<OrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyOrders()
                if (res.success) orders = res.orders
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    // Real poll for order-tracking status, same 4s cadence as Eats' own poll.
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
                NetworkClient.apiService.cancelOrder(orderId)
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MyReturnRequestsView()
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else if (orders == null) {
            SkeletonBlock()
        } else if (orders!!.isEmpty()) {
            EmptyState("No orders yet — browse a merchant's shop and your first order will show up here.", icon = Icons.AutoMirrored.Outlined.ReceiptLong)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                orders!!.forEach { o ->
                    CommerceOrderRow(o) {
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
                        } else if (o.status == "SHIPPED") {
                            LiveTrackingToggle(o.id)
                        } else if (o.status == "DELIVERED") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OrderItemReviews(o)
                                ReturnExchangeAction(o.id)
                                CommerceReorderButton(reordering = reorderingId == o.id, onClick = { onReorder(o) })
                            }
                        } else if (o.status == "CANCELLED") {
                            CommerceReorderButton(reordering = reorderingId == o.id, onClick = { onReorder(o) })
                        }
                    }
                }
            }
        }
    }
}

// Real Coupang/Amazon-style "Buy it again" (2026-08-23) -- direct port of Eats'
// own real "Reorder" button (EatsOrders.kt's ReorderButton) -- can't be shared
// directly (Feature module isolation, Konsist-enforced, forbids
// :features:shop:impl importing :features:eats:impl's internal composables), so
// duplicated here, same real precedent CameraQrScanner.kt's merchantapp copy
// already established for a deliberate, isolation-respecting duplicate.
@Composable
private fun CommerceReorderButton(reordering: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ids.colors.brand)
            .pressScaleClickable(enabled = !reordering, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            if (reordering) "Reordering…" else "Buy again",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

// Real live rider-location tracking for Commerce orders (item 230) -- see
// SimpleLiveRiderMiniMap's own doc comment. bank-mfe shipped this first
// (SimpleLiveRiderMap.tsx, 2026-08-05); this is the Android port.
@Composable
internal fun LiveTrackingToggle(orderId: String) {
    var tracking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Ids.colors.brand)
                .pressScaleClickable { tracking = !tracking }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(if (tracking) "Hide live tracking" else "🛵 Track your rider live", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        if (tracking) {
            SimpleLiveRiderMiniMap(orderId)
        }
    }
}

