package rw.itunda.rider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.rider.network.EatsOrderDto
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.network.NotificationDto
import rw.itunda.rider.network.RiderDto
import rw.itunda.rider.network.SetRiderAvailabilityRequest

private enum class HomeTab { AVAILABLE, MINE }

@Composable
fun RiderHomeScreen(
    onOpenDelivery: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rider by remember { mutableStateOf<RiderDto?>(null) }
    var tab by remember { mutableStateOf(HomeTab.AVAILABLE) }
    var available by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var mine by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var offers by remember { mutableStateOf<List<Pair<NotificationDto, String>>>(emptyList()) }
    var restaurantNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var togglingAvailability by remember { mutableStateOf(false) }

    suspend fun refreshMerchants() {
        try {
            restaurantNames = NetworkClient.apiService.getShoppingMerchants().merchants.associate { it.merchantId to it.businessName }
        } catch (e: Exception) { /* names just won't resolve; ids still shown */ }
    }

    suspend fun refreshRiderProfile() {
        try { rider = NetworkClient.apiService.getMyRiderProfile().rider } catch (e: Exception) { /* keep last known state */ }
    }

    suspend fun refreshDeliveries() {
        try {
            available = NetworkClient.apiService.getAvailableDeliveries().orders
            mine = NetworkClient.apiService.getRiderDeliveries().orders
            error = null
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
    }

    suspend fun refreshOffers() {
        try {
            val notifications = NetworkClient.apiService.getNotifications().notifications
            offers = notifications
                .filter { !it.isRead && it.type == "DELIVERY_OFFER" }
                .mapNotNull { n ->
                    val orderId = n.dataJson?.let { runCatching { JsonParser.parseString(it).asJsonObject.get("orderId")?.asString }.getOrNull() }
                    orderId?.let { n to it }
                }
        } catch (e: Exception) { /* offer banner just won't show this tick */ }
    }

    LaunchedEffect(Unit) {
        refreshMerchants()
        refreshRiderProfile()
        refreshDeliveries()
        refreshOffers()
    }

    // Real polling for new dispatch offers + delivery-list changes while this screen
    // is open -- matches this codebase's own established poll-based-first precedent
    // (messaging's own 4s poll before its later WebSocket layer).
    LaunchedEffect(Unit) {
        while (true) {
            delay(8000)
            refreshOffers()
            refreshDeliveries()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Itunda Rider", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (rider?.available == true) "You're online" else "You're offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rider?.available == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = rider?.available == true,
                    enabled = !togglingAvailability && rider != null,
                    onCheckedChange = { newValue ->
                        togglingAvailability = true
                        scope.launch {
                            try {
                                rider = NetworkClient.apiService.setRiderAvailability(SetRiderAvailabilityRequest(newValue)).rider
                                if (newValue) {
                                    fetchLocationOnce(context)?.let { loc ->
                                        runCatching { NetworkClient.apiService.updateRiderLocation(rw.itunda.rider.network.UpdateRiderLocationRequest(loc.latitude, loc.longitude)) }
                                    }
                                }
                            } catch (e: Exception) {
                                error = "Couldn't update your availability. Try again."
                            } finally {
                                togglingAvailability = false
                            }
                        }
                    },
                )
                IconButton(onClick = {
                    NetworkClient.currentTokenStore().clearSession()
                    onLogout()
                }) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = "Log out")
                }
            }
        }

        if (offers.isNotEmpty()) {
            offers.forEach { (notification, orderId) ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(notification.title, fontWeight = FontWeight.Bold)
                        Text(notification.body, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        NetworkClient.apiService.claimDelivery(orderId)
                                        runCatching { NetworkClient.apiService.markNotificationRead(notification.id) }
                                        refreshOffers(); refreshDeliveries()
                                        onOpenDelivery(orderId)
                                    } catch (e: Exception) {
                                        error = "This delivery is no longer available."
                                        refreshOffers(); refreshDeliveries()
                                    }
                                }
                            }) { Text("Accept") }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching { NetworkClient.apiService.declineDelivery(orderId) }
                                    runCatching { NetworkClient.apiService.markNotificationRead(notification.id) }
                                    refreshOffers()
                                }
                            }) { Text("Decline") }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            listOf(HomeTab.AVAILABLE to "Available", HomeTab.MINE to "My deliveries").forEach { (t, label) ->
                val selected = tab == t
                Text(
                    label,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 20.dp).clickable { tab = t },
                )
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        val list = if (tab == HomeTab.AVAILABLE) available else mine
        if (list == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (tab == HomeTab.AVAILABLE) "No open deliveries right now." else "No deliveries yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            if (tab == HomeTab.MINE) {
                val delivered = list.filter { it.status == "DELIVERED" }
                if (delivered.isNotEmpty()) {
                    Text(
                        "Recent earnings: ${"%,.0f".format(delivered.sumOf { it.deliveryFee })} RWF (${delivered.size} deliveries)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { order ->
                    DeliveryRow(
                        order = order,
                        restaurantName = restaurantNames[order.restaurantId] ?: order.restaurantId,
                        showClaim = tab == HomeTab.AVAILABLE,
                        onClaim = {
                            scope.launch {
                                try {
                                    NetworkClient.apiService.claimDelivery(order.id)
                                    refreshDeliveries()
                                    onOpenDelivery(order.id)
                                } catch (e: Exception) {
                                    error = "Someone else just claimed this delivery."
                                    refreshDeliveries()
                                }
                            }
                        },
                        onOpen = { onOpenDelivery(order.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryRow(
    order: EatsOrderDto,
    restaurantName: String,
    showClaim: Boolean,
    onClaim: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !showClaim, onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(restaurantName, fontWeight = FontWeight.Bold)
                Text("${"%,.0f".format(order.deliveryFee)} RWF", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text(order.deliveryAddress, style = MaterialTheme.typography.bodySmall)
            order.distanceKm?.let {
                Text("${"%.1f".format(it)} km away", style = MaterialTheme.typography.bodySmall)
            }
            StatusBadge(order.status)
            if (showClaim) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onClaim, modifier = Modifier.fillMaxWidth()) { Text("Claim this delivery") }
            }
        }
    }
}

@Composable
internal fun StatusBadge(status: String) {
    val label = when (status) {
        "RIDER_ASSIGNED" -> "Heading to pickup"
        "PICKED_UP" -> "On the way"
        "DELIVERED" -> "Delivered"
        else -> status
    }
    Box(
        modifier = Modifier
            .padding(top = 4.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
