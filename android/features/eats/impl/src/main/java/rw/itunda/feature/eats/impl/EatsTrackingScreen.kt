package rw.itunda.feature.eats.impl

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.ItundaArt
import rw.itunda.core.designsystem.components.ItundaArtSet
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.SubmitEatsReviewRequest

internal enum class EatsTrackingPanel { MAIN, RIDER, CANCEL, RATING }

@Composable
fun EatsTrackingScreen(
    order: EatsOrderDto,
    restaurant: ShoppingMerchantDto?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onReportIssue: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    initialItems: List<EatsOrderItemDto>? = null,
    initialScrollOffset: Int = 0,
) {
    var currentOrder by remember(order.id) { mutableStateOf(order) }
    var items by remember(order.id) { mutableStateOf(initialItems) }
    var panel by remember(order.id) { mutableStateOf(EatsTrackingPanel.MAIN) }
    var rating by remember(order.id) { mutableIntStateOf(5) }
    var busy by remember(order.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(order.id) {
        if (items == null) {
            if (NetworkClient.demoMode) {
                items = listOf(EatsOrderItemDto("item-1", order.id, "croissant", "Croissant", 1200.0, 2))
            } else {
                runCatching { NetworkClient.apiService.getEatsOrder(order.id).items }.onSuccess { items = it }
            }
        }
    }

    val pickup = currentOrder.deliveryAddress.isBlank()
    val status = currentOrder.status
    val complete = status == "DELIVERED" || (pickup && status == "PICKED_UP")
    val cancelled = status == "CANCELLED"
    val riderAssigned = !pickup && status in setOf("RIDER_ASSIGNED", "PICKED_UP", "DELIVERED")
    val step = when {
        cancelled -> 0
        complete -> 3
        status == "RIDER_ASSIGNED" || status == "PICKED_UP" || status == "READY_FOR_PICKUP" -> 2
        status == "PREPARING" -> 1
        else -> 0
    }
    val restaurantName = restaurant?.businessName ?: "Kigali Bakery"
    val onCallRestaurant: (() -> Unit)? = restaurant?.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone ->
        { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone.filter { it.isDigit() || it == '+' }, null))) }
    }
    val orderCode = currentOrder.id.takeIf { it.startsWith("EA-") } ?: "EA-${currentOrder.id.takeLast(4)}"
    val etaTitle = when {
        cancelled -> "Order cancelled"
        complete && pickup -> "Picked up"
        complete -> "Delivered"
        pickup && status == "READY_FOR_PICKUP" -> "Ready now"
        pickup && status == "PREPARING" -> "About 18 min"
        pickup -> "About 20 min"
        status == "PREPARING" -> "About 18 min"
        riderAssigned -> "Arriving in ${currentOrder.estimatedArrivalMinutes ?: 8} min"
        else -> "About ${currentOrder.estimatedArrivalMinutes ?: 20} min"
    }
    val statusLine = when {
        cancelled -> "Order cancelled"
        complete && pickup -> "Picked up"
        complete -> "Arrived at your door"
        pickup && status == "READY_FOR_PICKUP" -> "Ready for pickup"
        status == "PREPARING" -> "Your food is being prepared"
        riderAssigned -> "${currentOrder.riderName ?: "Jean Claude"} is on the way"
        else -> "Restaurant confirmed your order"
    }
    val stepLabels = if (pickup) listOf("Order accepted", "Preparing", "Ready for pickup", "Picked up")
    else listOf("Order accepted", "Preparing", "On the way", "Delivered")

    when (panel) {
        EatsTrackingPanel.MAIN -> EatsTrackingMain(
            order = currentOrder, items = items.orEmpty(), restaurantName = restaurantName, orderCode = orderCode,
            etaTitle = etaTitle, statusLine = statusLine, stepLabels = stepLabels, step = step, pickup = pickup,
            complete = complete, cancelled = cancelled, riderAssigned = riderAssigned, onBack = onBack,
            onCall = {
                if (riderAssigned) panel = EatsTrackingPanel.RIDER
                else Toast.makeText(context, "Calling Jean Claude… (sample)", Toast.LENGTH_SHORT).show()
            },
            onCallRestaurant = onCallRestaurant,
            initialScrollOffset = initialScrollOffset,
            onMessage = { panel = EatsTrackingPanel.RIDER },
            onCancel = { panel = EatsTrackingPanel.CANCEL },
            onRate = { panel = EatsTrackingPanel.RATING },
            onHelp = { onReportIssue(currentOrder.transactionId) },
            onDone = onDone,
            modifier = modifier,
        )
        EatsTrackingPanel.RIDER -> EatsTrackingRiderPanel(
            riderName = currentOrder.riderName ?: "Jean Claude",
            onBack = { panel = EatsTrackingPanel.MAIN },
            onQuickMessage = {
                Toast.makeText(context, "Message sent", Toast.LENGTH_SHORT).show()
                panel = EatsTrackingPanel.MAIN
            },
            onCall = {
                Toast.makeText(context, "Calling Jean Claude… (sample)", Toast.LENGTH_SHORT).show()
                panel = EatsTrackingPanel.MAIN
            },
            modifier = modifier,
        )
        EatsTrackingPanel.CANCEL -> EatsTrackingCancelPanel(
            refund = String.format(Locale.US, "%,.0f RWF", currentOrder.totalAmount),
            busy = busy,
            onBack = { panel = EatsTrackingPanel.MAIN },
            onKeep = { panel = EatsTrackingPanel.MAIN },
            onCancel = {
                scope.launch {
                    busy = true
                    runCatching { NetworkClient.apiService.cancelEatsOrder(currentOrder.id) }
                        .onSuccess {
                            currentOrder = it.order
                            panel = EatsTrackingPanel.MAIN
                            Toast.makeText(context, "Order cancelled", Toast.LENGTH_SHORT).show()
                        }
                        .onFailure { Toast.makeText(context, "Couldn't cancel this order", Toast.LENGTH_SHORT).show() }
                    busy = false
                }
            },
            modifier = modifier,
        )
        EatsTrackingPanel.RATING -> EatsTrackingRatingPanel(
            rating = rating, busy = busy,
            onBack = { panel = EatsTrackingPanel.MAIN },
            onRatingChange = { rating = it },
            onSubmit = {
                scope.launch {
                    busy = true
                    runCatching {
                        NetworkClient.apiService.submitEatsReview(
                            currentOrder.id, SubmitEatsReviewRequest(restaurantRating = rating, riderRating = rating),
                        )
                    }.onSuccess {
                        Toast.makeText(context, "Thanks for rating", Toast.LENGTH_SHORT).show()
                        panel = EatsTrackingPanel.MAIN
                    }.onFailure { Toast.makeText(context, "Couldn't send your rating", Toast.LENGTH_SHORT).show() }
                    busy = false
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
internal fun EatsTrackingMain(
    order: EatsOrderDto, items: List<EatsOrderItemDto>, restaurantName: String, orderCode: String,
    etaTitle: String, statusLine: String, stepLabels: List<String>, step: Int, pickup: Boolean,
    complete: Boolean, cancelled: Boolean, riderAssigned: Boolean, onBack: () -> Unit, onCall: () -> Unit,
    onCallRestaurant: (() -> Unit)?, initialScrollOffset: Int, onMessage: () -> Unit, onCancel: () -> Unit, onRate: () -> Unit,
    onHelp: () -> Unit, onDone: () -> Unit, modifier: Modifier,
) {
    val palette = eatsPrototypePalette()
    Column(modifier.fillMaxSize().background(palette.background)) {
        EatsTrackingHeader(if (pickup) "Your pickup" else "Your delivery", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState(initialScrollOffset))) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
                Text(etaTitle, color = palette.primary, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(restaurantName, color = palette.secondary, fontSize = 15.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pickup) Text(" · Pickup", color = palette.secondary, fontSize = 15.sp, lineHeight = 22.sp)
                    else {
                        Text(" · ", color = palette.secondary, fontSize = 15.sp, lineHeight = 22.sp)
                        ItundaArt("bolt-solid", set = ItundaArtSet.Solid, size = 16.dp, tint = Color(0xFFFFB321))
                        Text(" Cheetah delivery", color = palette.secondary, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
            }
            Spacer(Modifier.height(if (!pickup && !cancelled) 12.dp else 16.dp))
            EatsTrackingMap(
                statusText = statusLine,
                step = step,
                pickup = pickup,
                complete = complete,
                cancelled = cancelled,
                modifier = Modifier.padding(horizontal = if (!pickup && !cancelled) 12.dp else 24.dp),
            )
            EatsTrackingStepper(stepLabels, step, Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp))
            if (order.status == "PLACED" && !pickup) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
                    Text("Delivery code", color = palette.secondary, fontSize = 13.sp, lineHeight = 20.sp)
                    Text("4821", color = palette.primary, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Black)
                    Text("Tell your rider this code at the door. Not before.", color = palette.secondary, fontSize = 13.sp, lineHeight = 20.sp)
                }
            } else if (order.status == "PREPARING") {
                Text(
                    "You can cancel only before the restaurant starts preparing.",
                    color = palette.primary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 5.dp),
                )
            } else if (cancelled) {
                Text(
                    "Order cancelled. Refunded to itunda Pay.",
                    color = palette.secondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
            if (riderAssigned) {
                EatsTrackingRiderCard(order.riderName ?: "Jean Claude", onCall)
                if (!complete) {
                    EatsTrackingButton(
                        "Message rider",
                        onMessage,
                        modifier = Modifier.width(308.dp).padding(top = 10.dp),
                        background = palette.selected,
                        color = palette.selectedText,
                    )
                } else {
                    Row(Modifier.width(308.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EatsTrackingButton(
                            "Rate your order",
                            onRate,
                            modifier = Modifier.weight(1f),
                            background = Color(0xFF191F28),
                            color = Color.White,
                            leadingStar = true,
                        )
                        EatsTrackingButton("Get help", onHelp, modifier = Modifier.weight(1f), background = palette.surfaceSoft, color = palette.primary)
                    }
                }
            } else if (complete) {
                Row(Modifier.width(308.dp).padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EatsTrackingButton(
                        "Rate your order",
                        onRate,
                        modifier = Modifier.weight(1f),
                        background = Color(0xFF191F28),
                        color = Color.White,
                        leadingStar = true,
                    )
                    EatsTrackingButton("Get help", onHelp, modifier = Modifier.weight(1f), background = palette.surfaceSoft, color = palette.primary)
                }
            }

            val orderCardBeforeSummary = (pickup && order.status in setOf("PREPARING", "READY_FOR_PICKUP")) ||
                (!pickup && order.status in setOf("PREPARING", "DELIVERED"))
            if (pickup && order.status == "READY_FOR_PICKUP") Spacer(Modifier.height(26.dp))
            if (order.status == "PREPARING") Spacer(Modifier.height(16.dp))
            if (!pickup && order.status == "DELIVERED") Spacer(Modifier.height(16.dp))
            if (orderCardBeforeSummary) {
                EatsTrackingOrderCard(
                    order = order,
                    items = items,
                    restaurantName = restaurantName,
                    orderCode = orderCode,
                    pickup = pickup,
                    onCallRestaurant = if (pickup && order.status == "PREPARING") onCallRestaurant else null,
                )
                Spacer(Modifier.height(24.dp))
            }
            EatsTrackingStatusSummary(order, items, orderCode, pickup)
            if (order.status == "PLACED") {
                EatsTrackingButton(
                    "Cancel order",
                    onCancel,
                    modifier = Modifier.width(308.dp).padding(top = 10.dp),
                    height = 52.dp,
                    background = Color(0xFFC4281B),
                    color = Color.White,
                )
                if (!pickup) {
                    Spacer(Modifier.height(16.dp))
                    EatsTrackingOrderCard(order, items, restaurantName, orderCode, pickup)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        if (complete) EatsTrackingButton("Done", onDone, modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp))
        else EatsTrackingButton(
            "Tracking your order",
            {},
            enabled = false,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            background = palette.accentTint,
            color = Color(0xFF8F89FF),
        )
    }
}

@Composable
internal fun EatsTrackingHeader(title: String, onBack: () -> Unit, onCall: () -> Unit = {}, showCall: Boolean = false) {
    val palette = eatsPrototypePalette()
    Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 6.dp).padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            ItundaArt("back", set = ItundaArtSet.Line, size = 24.dp, tint = palette.primary)
        }
        Text(title, color = palette.primary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 4.dp))
        if (showCall) IconButton(onClick = onCall, modifier = Modifier.size(48.dp)) {
            ItundaArt("phone", set = ItundaArtSet.Line, size = 20.dp, tint = palette.primary)
        }
    }
}
