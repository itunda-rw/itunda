package rw.itunda.rider.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.rider.network.CommerceOrderDto
import rw.itunda.rider.network.EatsOrderDto
import rw.itunda.rider.network.NetworkClient
import rw.itunda.rider.network.NotificationDto
import rw.itunda.rider.network.RiderDto
import rw.itunda.rider.network.RiderRatingResponse
import rw.itunda.rider.network.SetRiderAvailabilityRequest
import rw.itunda.rider.network.parseApiError

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


/**
 * The rider app's shell: owns loading + polling for every tab (so Earnings/Me never
 * refetch when you switch tabs) and hosts the Work / Earnings / Me bottom tabs, the
 * same three-way split as the Picker prototype. Claiming, offers and availability keep
 * the exact backend calls this screen always made -- only the layout moved.
 */
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

    var tab by remember { mutableStateOf(RiderTab.Work) }
    var rider by remember { mutableStateOf<RiderDto?>(null) }
    // Real rider rating (item 142) -- see ApiService.kt's own doc comment on getRiderRating.
    var rating by remember { mutableStateOf<RiderRatingResponse?>(null) }
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

    fun toggleAvailability(newValue: Boolean) {
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
    }

    fun acceptOffer(notification: NotificationDto, orderId: String) {
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
    }

    fun declineOffer(notification: NotificationDto, orderId: String) {
        scope.launch {
            runCatching { NetworkClient.apiService.declineDelivery(orderId) }
            runCatching { NetworkClient.apiService.markNotificationRead(notification.id) }
            refreshOffers()
        }
    }

    fun claim(delivery: RiderDelivery) {
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
    }

    fun openDelivery(delivery: RiderDelivery) {
        when (delivery) {
            is RiderDelivery.Eats -> onOpenDelivery(delivery.id)
            is RiderDelivery.Commerce -> onOpenCommerceDelivery(delivery.order)
        }
    }

    // Real combined feed (2026-08-04): Eats (food) + Commerce (Shop packages) are two
    // separate backend order streams sharing one claim queue. "Loading" means neither
    // request has come back yet, not that either one is empty.
    val availableFeed: List<RiderDelivery>? =
        if (available == null && availableCommerce == null) null
        else available.orEmpty().map { RiderDelivery.Eats(it) } + availableCommerce.orEmpty().map { RiderDelivery.Commerce(it) }
    val inProgress: List<RiderDelivery> =
        mine.orEmpty().filter { it.status != "DELIVERED" }.map { RiderDelivery.Eats(it) } +
            mineCommerce.orEmpty().filter { it.status != "DELIVERED" }.map { RiderDelivery.Commerce(it) }
    val deliveredCount = mine.orEmpty().count { it.status == "DELIVERED" } + mineCommerce.orEmpty().count { it.status == "DELIVERED" }

    Scaffold(
        containerColor = Ids.colors.background,
        bottomBar = { RiderBottomBar(selected = tab, onSelect = { tab = it }) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                RiderTab.Work -> RiderWorkTab(
                    state = RiderWorkState(
                        rider = rider,
                        togglingAvailability = togglingAvailability,
                        offers = offers,
                        inProgress = inProgress,
                        available = availableFeed,
                        merchantNames = restaurantNames,
                        error = error,
                    ),
                    onToggleAvailability = ::toggleAvailability,
                    onAcceptOffer = ::acceptOffer,
                    onDeclineOffer = { n -> offers.firstOrNull { it.first.id == n.id }?.let { declineOffer(n, it.second) } },
                    onClaim = ::claim,
                    onOpen = ::openDelivery,
                )
                RiderTab.Earnings -> RiderEarningsTab(eatsMine = mine, commerceMine = mineCommerce)
                RiderTab.Me -> RiderMeTab(
                    rider = rider,
                    rating = rating,
                    deliveredCount = deliveredCount,
                    onLogout = {
                        NetworkClient.currentTokenStore().clearSession()
                        onLogout()
                    },
                )
            }
        }
    }
}
