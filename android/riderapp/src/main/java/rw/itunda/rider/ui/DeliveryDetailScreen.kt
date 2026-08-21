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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import rw.itunda.core.designsystem.components.IdsButton
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import rw.itunda.rider.network.EatsOrderDto
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.network.UpdateEatsOrderStatusRequest
import rw.itunda.rider.network.UpdateRiderLocationRequest

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
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            Text("Delivery", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }

        val current = order
        if (current == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error) else CircularProgressIndicator()
            }
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(restaurantName ?: current.restaurantId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                StatusBadge(current.status)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Deliver to", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(current.deliveryAddress, style = MaterialTheme.typography.bodyLarge)
                current.deliveryNotes?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Note: $it", style = MaterialTheme.typography.bodyMedium)
                }
                current.distanceKm?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("${"%.1f".format(it)} km", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Order total", style = MaterialTheme.typography.bodyMedium)
                    Text("${"%,.0f".format(current.itemsSubtotal)} RWF", style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Your delivery fee", fontWeight = FontWeight.Bold)
                    Text("${"%,.0f".format(current.deliveryFee)} RWF", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (!locationPermissionGranted) {
            Text(
                "Location permission is needed so the buyer can see you're on the way.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

        val nextAction = when (current.status) {
            "RIDER_ASSIGNED" -> "PICKED_UP" to "I've picked up the order"
            "PICKED_UP" -> "DELIVERED" to "I've delivered the order"
            else -> null
        }
        if (nextAction != null) {
            val (nextStatus, label) = nextAction
            IdsButton(
                text = if (advancing) "Updating…" else label,
                enabled = !advancing,
                modifier = Modifier.padding(16.dp),
                onClick = {
                    advancing = true
                    scope.launch {
                        try {
                            order = NetworkClient.apiService.updateRiderOrderStatus(orderId, UpdateEatsOrderStatusRequest(nextStatus)).order
                            if (nextStatus == "DELIVERED") onBack()
                        } catch (e: Exception) {
                            error = "Couldn't update this delivery's status. Try again."
                        } finally {
                            advancing = false
                        }
                    }
                },
            )
        } else if (current.status == "DELIVERED") {
            Text(
                "Delivered -- ${"%,.0f".format(current.deliveryFee)} RWF paid to your account.",
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
