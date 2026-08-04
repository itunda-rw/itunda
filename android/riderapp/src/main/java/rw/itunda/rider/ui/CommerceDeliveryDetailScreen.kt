package rw.itunda.rider.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
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
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Package delivery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(merchantName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusBadge(order.status)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Deliver to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(order.deliveryAddress, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Order total", fontWeight = FontWeight.Bold)
                    Text("${"%,.0f".format(order.totalAmount)} RWF", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (!locationPermissionGranted && order.status == "SHIPPED") {
            Text(
                "Location permission is needed so the buyer can see you're on the way.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

        if (order.status == "SHIPPED") {
            Button(
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
                enabled = !advancing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
            ) {
                Text(if (advancing) "Updating…" else "I've delivered this order")
            }
        } else if (order.status == "DELIVERED") {
            Text(
                "Delivered.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
