package rw.itunda.feature.eats.impl

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.Icon
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
    modifier: Modifier = Modifier,
    initialItems: List<EatsOrderItemDto>? = null,
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
    val orderCode = currentOrder.id.takeIf { it.startsWith("EA-") } ?: "EA-${currentOrder.id.takeLast(4)}"
    val etaTitle = when {
        cancelled -> "Order cancelled"
        complete && pickup -> "Picked up"
        complete -> "Delivered"
        pickup && status == "READY_FOR_PICKUP" -> "Ready now"
        pickup && status == "PREPARING" -> "Ready in 15 min"
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
            onMessage = { panel = EatsTrackingPanel.RIDER },
            onCancel = { panel = EatsTrackingPanel.CANCEL },
            onRate = { panel = EatsTrackingPanel.RATING },
            onHelp = { Toast.makeText(context, "Opening order help", Toast.LENGTH_SHORT).show() },
            onCallRestaurant = { Toast.makeText(context, "Calling $restaurantName", Toast.LENGTH_SHORT).show() },
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
    onMessage: () -> Unit, onCancel: () -> Unit, onRate: () -> Unit, onHelp: () -> Unit,
    onCallRestaurant: () -> Unit, onDone: () -> Unit, modifier: Modifier,
) {
    val palette = eatsPrototypePalette()
    Column(modifier.fillMaxSize().background(palette.background)) {
        EatsTrackingHeader("Your delivery", onBack, onCall, showCall = true)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
                Text(etaTitle, color = palette.primary, fontSize = 36.sp, lineHeight = 43.sp, fontWeight = FontWeight.Black)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(restaurantName, color = palette.secondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pickup) Text(" · Pickup", color = palette.secondary, fontSize = 15.sp)
                    else {
                        Text(" · ", color = palette.secondary, fontSize = 15.sp)
                        Text("⚡", color = Color(0xFFFFB321), fontSize = 16.sp)
                        Text(" Cheetah delivery", color = palette.secondary, fontSize = 15.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            EatsTrackingMap(statusText = statusLine, complete = complete, cancelled = cancelled, modifier = Modifier.padding(horizontal = 12.dp))
            EatsTrackingStepper(stepLabels, step, Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp))
            if (order.status == "PLACED" && !pickup) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 42.dp)) {
                    Text("Delivery code", color = palette.secondary, fontSize = 15.sp)
                    Text("4821", color = palette.primary, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Black)
                    Text("Tell your rider this code at the door. Not before.", color = palette.secondary, fontSize = 15.sp, lineHeight = 21.sp)
                    Text("Cancel order", color = palette.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp).pressScaleClickable(onClick = onCancel))
                }
            } else if (order.status == "PREPARING" && !pickup) {
                Text("You can cancel only before the restaurant starts preparing.", color = palette.primary, fontSize = 15.sp,
                    lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
            } else if (cancelled) {
                Text("Order cancelled. Refunded to itunda Pay.", color = palette.secondary, fontSize = 15.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            }
            if (riderAssigned) {
                EatsTrackingRiderCard(order.riderName ?: "Jean Claude", onCall)
                if (!complete) {
                    EatsTrackingButton("Message rider", onMessage, modifier = Modifier.width(308.dp).align(Alignment.CenterHorizontally).padding(top = 10.dp))
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        EatsTrackingButton("★ Rate your order", onRate, modifier = Modifier.weight(1f), height = 52.dp)
                        EatsTrackingButton("Get help", onHelp, modifier = Modifier.weight(1f), height = 52.dp, background = palette.surfaceSoft, color = palette.primary)
                    }
                }
            } else if (complete) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EatsTrackingButton("★ Rate your order", onRate, modifier = Modifier.weight(1f), height = 52.dp)
                    EatsTrackingButton("Get help", onHelp, modifier = Modifier.weight(1f), height = 52.dp, background = palette.surfaceSoft, color = palette.primary)
                }
            }
            EatsTrackingOrderSummary(order, items, restaurantName, orderCode, pickup, order.status, onCallRestaurant)
            if (order.status == "READY_FOR_PICKUP" || pickup) {
                Text("Pick up at $restaurantName", color = palette.secondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))
            }
            Spacer(Modifier.height(24.dp))
        }
        if (complete) EatsTrackingButton("Done", onDone, modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp))
        else EatsTrackingButton("Tracking your order", {}, enabled = false, modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
            background = palette.accentTint, color = Color(0xFF8F89FF))
    }
}

@Composable
internal fun EatsTrackingHeader(title: String, onBack: () -> Unit, onCall: () -> Unit = {}, showCall: Boolean = false) {
    val palette = eatsPrototypePalette()
    Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = palette.primary)
        }
        Text(title, color = palette.primary, fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f).padding(start = 6.dp))
        if (showCall) IconButton(onClick = onCall, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Call, contentDescription = "Call rider", tint = palette.primary)
        }
    }
}
