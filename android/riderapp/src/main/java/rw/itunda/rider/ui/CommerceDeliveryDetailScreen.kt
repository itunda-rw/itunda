package rw.itunda.rider.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import java.util.Locale
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.FixedBottomCta
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTypography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.rider.network.CommerceOrderDto
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.network.UpdateRiderLocationRequest

/**
 * A single active (or just-completed) Commerce/Shop package delivery -- see
 * ApiService.kt's CommerceOrderDto doc comment for the real backend account this
 * ports (2026-08-04). Simpler than DeliveryDetailScreen's Eats flow: Commerce models
 * only PACKED -> SHIPPED (claim) -> DELIVERED (complete), no RIDER_ASSIGNED/PICKED_UP
 * midpoint, so there's exactly one advance action here. Received as a full object
 * (not re-fetched by id) since OrderService.getOrderDetail only authorizes the buyer
 * or seller, never the rider -- the claim response and the my-deliveries list are the
 * only two places a rider legitimately sees this order's data, and both already hand
 * over the complete object.
 */
@Composable
fun CommerceDeliveryDetailScreen(initialOrder: CommerceOrderDto, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf(initialOrder) }
    var merchantName by remember { mutableStateOf(initialOrder.merchantId) }
    var error by remember { mutableStateOf<String?>(null) }
    var advancing by remember { mutableStateOf(false) }
    val locationPermissionGranted = rememberFineLocationPermissionGranted()

    LaunchedEffect(order.merchantId) {
        merchantName = runCatching {
            NetworkClient.apiService.getShoppingMerchants().merchants.firstOrNull { it.merchantId == order.merchantId }?.businessName
        }.getOrNull() ?: order.merchantId
    }

    // Real live rider-location push while this delivery is in transit -- reuses the
    // same shared Rider.currentLatitude/currentLongitude endpoint Eats deliveries
    // already push to (POST /eats/riders/location), which is what
    // OrderService.getRiderLocation reads for the buyer's own "watch it arrive" view.
    LaunchedEffect(order.status, locationPermissionGranted) {
        if (!locationPermissionGranted || order.status != "SHIPPED") return@LaunchedEffect
        while (true) {
            fetchLocationOnce(context)?.let { loc ->
                runCatching { NetworkClient.apiService.updateRiderLocation(UpdateRiderLocationRequest(loc.latitude, loc.longitude)) }
            }
            delay(15000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackTopBar(title = "Package delivery", onBack = onBack)
        // Flat rows + one pinned action, same shape as DeliveryDetailScreen.
        Box(modifier = Modifier.weight(1f)) {
            FixedBottomCta(
                content = {
                    Text(merchantName, style = IdsTypography.Title1, color = Ids.colors.textPrimary)
                    StatusBadge(order.status)
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailRow("Deliver to", order.deliveryAddress)
                    DetailRow("Order total", "${String.format(Locale.US, "%,.0f", order.totalAmount)} RWF", emphasize = true)
                    if (!locationPermissionGranted && order.status == "SHIPPED") {
                        Text(
                            "Location permission is needed so the buyer can see you're on the way.",
                            style = IdsTypography.Body2,
                            color = Ids.colors.danger,
                        )
                    }
                    error?.let { Text(it, style = IdsTypography.Body2, color = Ids.colors.danger) }
                    if (order.status == "DELIVERED") {
                        Text("Delivered.", style = IdsTypography.Subtitle1, color = Ids.colors.brand)
                    }
                },
                cta = {
                    if (order.status == "SHIPPED") {
                        IdsButton(
                            text = if (advancing) "Updating…" else "I've delivered this order",
                            enabled = !advancing,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                advancing = true
                                scope.launch {
                                    try {
                                        order = NetworkClient.apiService.completeCommerceDelivery(order.id).order
                                        onBack()
                                    } catch (e: Exception) {
                                        error = "Couldn't mark this delivered. Try again."
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
