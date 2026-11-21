package rw.itunda.rider.ui

import androidx.compose.foundation.layout.Arrangement
import rw.itunda.core.designsystem.theme.Ids
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import java.util.Locale
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.FixedBottomCta
import rw.itunda.core.designsystem.theme.IdsTypography
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsLoading
import rw.itunda.core.designsystem.components.IdsErrorText
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.rider.network.EatsOrderDto
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.network.UpdateEatsOrderStatusRequest
import rw.itunda.rider.network.UpdateRiderLocationRequest
import rw.itunda.rider.network.parseApiError

/**
 * A single active (or just-completed) delivery. Pushes this rider's real live
 * coordinates every 15s while the order is in an in-progress status (RIDER_ASSIGNED
 * or PICKED_UP) -- closes a real, previously-unused backend endpoint
 * (POST /riders/location) that powers both nearest-rider dispatch ranking and the
 * buyer's own "watch your order arrive" GET /orders/{id}/rider-location. Only pushes
 * while this screen is open and in the foreground -- background/closed-app tracking
 * is a real, named v1 scope cut, not attempted this pass.
 */
@Composable
fun DeliveryDetailScreen(orderId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf<EatsOrderDto?>(null) }
    var restaurantName by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var advancing by remember { mutableStateOf(false) }
    val locationPermissionGranted = rememberFineLocationPermissionGranted()

    suspend fun refresh() {
        try {
            val fetched = NetworkClient.apiService.getOrder(orderId).order
            order = fetched
            if (restaurantName == null) {
                restaurantName = runCatching {
                    NetworkClient.apiService.getShoppingMerchants().merchants.firstOrNull { it.merchantId == fetched.restaurantId }?.businessName
                }.getOrNull() ?: fetched.restaurantId
            }
            error = null
        } catch (e: Exception) {
            error = "Couldn't load this delivery."
        }
    }

    LaunchedEffect(orderId) { refresh() }

    LaunchedEffect(orderId, locationPermissionGranted) {
        if (!locationPermissionGranted) return@LaunchedEffect
        while (true) {
            val status = order?.status
            if (status == "RIDER_ASSIGNED" || status == "PICKED_UP") {
                fetchLocationOnce(context)?.let { loc ->
                    runCatching { NetworkClient.apiService.updateRiderLocation(UpdateRiderLocationRequest(loc.latitude, loc.longitude)) }
                }
            }
            delay(15000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Delivery", onBack = onBack)

        val current = order
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error != null) IdsErrorText(error!!) else IdsLoading()
            }
            return@Column
        }

        val nextAction = when (current.status) {
            "RIDER_ASSIGNED" -> "PICKED_UP" to "I've picked up the order"
            "PICKED_UP" -> "DELIVERED" to "I've delivered the order"
            else -> null
        }
        // Flat rows on the page + one pinned action at the bottom, like the Picker
        // prototype's job screen (UI_UX_GUIDELINES rules 1 and 10) -- was a Material Card
        // with MaterialTheme colors, which ignored the itunda tokens and dark theme.
        Box(modifier = Modifier.weight(1f)) {
            FixedBottomCta(
                content = {
                    Text(restaurantName ?: current.restaurantId, style = IdsTypography.Title1, color = Ids.colors.textPrimary)
                    StatusBadge(current.status)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Deliver to", current.deliveryAddress)
                    current.deliveryNotes?.takeIf { it.isNotBlank() }?.let { DetailRow("Note", it) }
                    current.distanceKm?.let { DetailRow("Distance", "${String.format(Locale.US, "%.1f", it)} km") }
                    DetailRow("Order total", "${String.format(Locale.US, "%,.0f", current.itemsSubtotal)} RWF")
                    DetailRow("Your delivery fee", "${String.format(Locale.US, "%,.0f", current.deliveryFee)} RWF", emphasize = true)
                    if (!locationPermissionGranted) {
                        Text(
                            "Location permission is needed so the buyer can see you're on the way.",
                            style = IdsTypography.Body2,
                            color = Ids.colors.danger,
                        )
                    }
                    error?.let { IdsErrorText(it) }
                    if (current.status == "DELIVERED") {
                        Text(
                            "Delivered -- ${String.format(Locale.US, "%,.0f", current.deliveryFee)} RWF paid to your account.",
                            style = IdsTypography.Subtitle1,
                            color = Ids.colors.brand,
                        )
                    }
                },
                cta = {
                    if (nextAction != null) {
                        val (nextStatus, label) = nextAction
                        IdsButton(
                            text = if (advancing) "Updating…" else label,
                            enabled = !advancing,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                advancing = true
                                scope.launch {
                                    try {
                                        order = NetworkClient.apiService.updateRiderOrderStatus(orderId, UpdateEatsOrderStatusRequest(nextStatus)).order
                                        if (nextStatus == "DELIVERED") onBack()
                                    } catch (e: retrofit2.HttpException) {
                                        error = parseApiError(e).message ?: "Couldn't update this delivery's status. Try again."
                                    } catch (e: Exception) {
                                        error = "Couldn't update this delivery's status. Try again."
                                    } finally {
                                        advancing = false
                                    }
                                }
                            },
                        )
                    } else {
                        IdsButton(text = "Back to work", onClick = onBack, modifier = Modifier.fillMaxWidth())
                    }
                },
            )
        }
    }
}

@Composable
internal fun DetailRow(label: String, value: String, emphasize: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = IdsTypography.Body1, color = Ids.colors.textSecondary, modifier = Modifier.padding(end = 16.dp))
            Text(
                value,
                style = if (emphasize) IdsTypography.Subtitle1 else IdsTypography.Body1,
                color = if (emphasize) Ids.colors.brand else Ids.colors.textPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        Divider(color = Ids.colors.divider, thickness = 0.5.dp)
    }
}
