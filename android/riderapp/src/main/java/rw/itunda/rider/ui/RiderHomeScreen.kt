package rw.itunda.rider.ui

import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
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
import rw.itunda.core.designsystem.components.IdsButton
import rw.itunda.core.designsystem.components.IdsButtonSize
import rw.itunda.core.designsystem.components.IdsButtonVariant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import rw.itunda.rider.network.CommerceOrderDto
import rw.itunda.rider.network.EatsOrderDto
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.network.NotificationDto
import rw.itunda.rider.network.RiderDto
import rw.itunda.rider.network.RiderRatingResponse
import rw.itunda.rider.network.SetRiderAvailabilityRequest
import rw.itunda.rider.network.parseApiError

private enum class HomeTab { AVAILABLE, MINE }

// Real itunda-own-fleet Commerce (Shop) delivery claim/tracking -- see
// ApiService.kt's own CommerceOrderDto doc comment. A rider can now see and claim
// both food (Eats) and package (Shop) deliveries from this one screen, distinguished
// by this tag since the two are separate real backend order streams with different
// status vocabularies.
enum class DeliverySource { EATS, COMMERCE }

sealed class RiderDelivery {
    abstract val id: String
    abstract val status: String
    abstract val merchantId: String
    abstract val deliveryAddress: String
    abstract val source: DeliverySource

    data class Eats(val order: EatsOrderDto) : RiderDelivery() {
        override val id get() = order.id
        override val status get() = order.status
        override val merchantId get() = order.restaurantId
        override val deliveryAddress get() = order.deliveryAddress
        override val source get() = DeliverySource.EATS
    }

    data class Commerce(val order: CommerceOrderDto) : RiderDelivery() {
        override val id get() = order.id
        override val status get() = order.status
        override val merchantId get() = order.merchantId
        override val deliveryAddress get() = order.deliveryAddress
        override val source get() = DeliverySource.COMMERCE
    }
}

@Composable
fun RiderHomeScreen(
    onOpenDelivery: (String) -> Unit,
    // Real Commerce/Shop package delivery -- see CommerceDeliveryDetailScreen's own
    // doc comment on why this hands over the full order object rather than an id
    // (the rider is never authorized to GET a Commerce order by id directly).
    onOpenCommerceDelivery: (CommerceOrderDto) -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rider by remember { mutableStateOf<RiderDto?>(null) }
    // Real rider rating (item 142) -- see ApiService.kt's own doc comment on
    // getRiderRating.
    var rating by remember { mutableStateOf<RiderRatingResponse?>(null) }
    var tab by remember { mutableStateOf(HomeTab.AVAILABLE) }
    var available by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var mine by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var availableCommerce by remember { mutableStateOf<List<CommerceOrderDto>?>(null) }
    var mineCommerce by remember { mutableStateOf<List<CommerceOrderDto>?>(null) }
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
        try {
            rider = NetworkClient.apiService.getMyRiderProfile().rider
            rider?.let { r -> rating = try { NetworkClient.apiService.getRiderRating(r.id) } catch (e: Exception) { null } }
        } catch (e: Exception) { /* keep last known state */ }
    }

    suspend fun refreshDeliveries() {
        try {
            available = NetworkClient.apiService.getAvailableDeliveries().orders
            mine = NetworkClient.apiService.getRiderDeliveries().orders
            error = null
        } catch (e: Exception) {
            error = "Couldn't reach itunda. Check your connection and try again."
        }
        // Real, separate Commerce delivery stream (2026-08-04) -- kept in its own
        // try/catch so an outage in one order type never blanks the other.
        try {
            availableCommerce = NetworkClient.apiService.getAvailableCommerceDeliveries().orders
            mineCommerce = NetworkClient.apiService.getMyCommerceDeliveries().orders
        } catch (e: Exception) { /* package deliveries just won't show this tick */ }
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
                rating?.average?.let { avg ->
                    Text(
                        String.format(Locale.US, "⭐ %.1f (%d)", avg, rating?.count ?: 0),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                rw.itunda.core.designsystem.components.IdsSwitch(
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
                            } catch (e: retrofit2.HttpException) {
                                error = parseApiError(e).message ?: "Couldn't update your availability. Try again."
                            } catch (e: Exception) {
                                error = "Couldn't update your availability. Try again."
                            } finally {
                                togglingAvailability = false
                            }
                        }
                    },
                    label = "Availability",
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
                            IdsButton(
                                text = "Accept",
                                size = IdsButtonSize.Medium,
                                onClick = {
                                    scope.launch {
                                        try {
                                            NetworkClient.apiService.claimDelivery(orderId, java.util.UUID.randomUUID().toString())
                                            runCatching { NetworkClient.apiService.markNotificationRead(notification.id) }
                                            refreshOffers(); refreshDeliveries()
                                            onOpenDelivery(orderId)
                                        } catch (e: retrofit2.HttpException) {
                                            error = parseApiError(e).message ?: "This delivery is no longer available."
                                            refreshOffers(); refreshDeliveries()
                                        } catch (e: Exception) {
                                            error = "This delivery is no longer available."
                                            refreshOffers(); refreshDeliveries()
                                        }
                                    }
                                },
                            )
                            IdsButton(
                                text = "Decline",
                                variant = IdsButtonVariant.Tinted,
                                size = IdsButtonSize.Medium,
                                onClick = {
                                    scope.launch {
                                        runCatching { NetworkClient.apiService.declineDelivery(orderId) }
                                        runCatching { NetworkClient.apiService.markNotificationRead(notification.id) }
                                        refreshOffers()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        rw.itunda.core.designsystem.components.IdsTabs(
            labels = listOf("Available", "My deliveries"),
            selectedIndex = if (tab == HomeTab.AVAILABLE) 0 else 1,
            onSelectedIndexChange = { tab = if (it == 0) HomeTab.AVAILABLE else HomeTab.MINE },
            modifier = Modifier.fillMaxWidth(),
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        val eatsList = if (tab == HomeTab.AVAILABLE) available else mine
        val commerceList = if (tab == HomeTab.AVAILABLE) availableCommerce else mineCommerce
        // Real combined delivery feed (2026-08-04): Eats (food) + Commerce (Shop
        // packages) are two separate real backend order streams sharing this one
        // rider's claim queue -- loading is "both requests have come back", not
        // "either one has."
        if (eatsList == null && commerceList == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val combined: List<RiderDelivery> =
                eatsList.orEmpty().map { RiderDelivery.Eats(it) } + commerceList.orEmpty().map { RiderDelivery.Commerce(it) }
            if (combined.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    // Real copy-voice fix (item 244, round 8 of the empty-state pass):
                    // AVAILABLE is genuinely passive (new deliveries just appear), MINE
                    // has a real fix -- switch to Available and claim one.
                    Text(
                        if (tab == HomeTab.AVAILABLE) "No open deliveries right now — check back soon, new ones appear automatically." else "You haven't claimed any deliveries yet — switch to Available to claim your first one.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                if (tab == HomeTab.MINE) {
                    val deliveredEatsFees = eatsList.orEmpty().filter { it.status == "DELIVERED" }.sumOf { it.deliveryFee }
                    val deliveredCount = eatsList.orEmpty().count { it.status == "DELIVERED" } + commerceList.orEmpty().count { it.status == "DELIVERED" }
                    if (deliveredCount > 0) {
                        Text(
                            "Recent earnings: ${String.format(Locale.US, "%,.0f", deliveredEatsFees)} RWF (${deliveredCount} deliveries)",
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
                    items(combined, key = { "${it.source}:${it.id}" }) { delivery ->
                        RiderDeliveryRow(
                            delivery = delivery,
                            merchantName = restaurantNames[delivery.merchantId] ?: delivery.merchantId,
                            showClaim = tab == HomeTab.AVAILABLE,
                            onClaim = {
                                scope.launch {
                                    try {
                                        when (delivery) {
                                            is RiderDelivery.Eats -> {
                                                NetworkClient.apiService.claimDelivery(delivery.id, java.util.UUID.randomUUID().toString())
                                                refreshDeliveries()
                                                onOpenDelivery(delivery.id)
                                            }
                                            is RiderDelivery.Commerce -> {
                                                val claimed = NetworkClient.apiService.claimCommerceDelivery(delivery.id, java.util.UUID.randomUUID().toString()).order
                                                refreshDeliveries()
                                                onOpenCommerceDelivery(claimed)
                                            }
                                        }
                                    } catch (e: retrofit2.HttpException) {
                                        error = parseApiError(e).message ?: "Someone else just claimed this delivery."
                                        refreshDeliveries()
                                    } catch (e: Exception) {
                                        error = "Someone else just claimed this delivery."
                                        refreshDeliveries()
                                    }
                                }
                            },
                            onOpen = {
                                when (delivery) {
                                    is RiderDelivery.Eats -> onOpenDelivery(delivery.id)
                                    is RiderDelivery.Commerce -> onOpenCommerceDelivery(delivery.order)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RiderDeliveryRow(
    delivery: RiderDelivery,
    merchantName: String,
    showClaim: Boolean,
    onClaim: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().pressScaleClickable(enabled = !showClaim, onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column {
                    // Real combined feed source tag -- see RiderDelivery's own doc comment.
                    Text(
                        if (delivery.source == DeliverySource.EATS) "🍔 Food" else "📦 Package",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(merchantName, fontWeight = FontWeight.Bold)
                }
                when (delivery) {
                    is RiderDelivery.Eats -> Text(
                        "${String.format(Locale.US, "%,.0f", delivery.order.deliveryFee)} RWF",
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    )
                    // Commerce's Order entity models no separate rider-payout field --
                    // showing the real order total rather than fabricating a delivery-fee
                    // figure the backend doesn't compute.
                    is RiderDelivery.Commerce -> Text(
                        "${String.format(Locale.US, "%,.0f", delivery.order.totalAmount)} RWF order",
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(delivery.deliveryAddress, style = MaterialTheme.typography.bodySmall)
            if (delivery is RiderDelivery.Eats) {
                delivery.order.distanceKm?.let {
                    Text("${String.format(Locale.US, "%.1f", it)} km away", style = MaterialTheme.typography.bodySmall)
                }
            }
            StatusBadge(delivery.status)
            if (showClaim) {
                Spacer(modifier = Modifier.height(8.dp))
                IdsButton(text = "Claim this delivery", onClick = onClaim)
            }
        }
    }
}

@Composable
internal fun StatusBadge(status: String) {
    val label = when (status) {
        "RIDER_ASSIGNED" -> "Heading to pickup"
        "PICKED_UP" -> "On the way"
        "PACKED" -> "Ready for pickup"
        "SHIPPED" -> "On the way"
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
