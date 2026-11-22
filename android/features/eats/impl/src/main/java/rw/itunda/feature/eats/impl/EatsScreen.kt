package rw.itunda.feature.eats.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.trackScrollPressedKey
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.DineInOrderDto
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException




// Eats screen entry point + order-food browse content. Sub-views live in
// EatsStatusShared.kt / EatsCategoryBrowse.kt / EatsMembership.kt /
// EatsFavoritesReview.kt / EatsRestaurantMenu.kt / EatsCheckout.kt /
// EatsOrders.kt (split 2026-08-19 for real file-size decomposition, not
// a single-slice extraction). Deliver's own DeliverContent stays in
// EatsDeliver.kt, unaffected by this split.

internal enum class OrderFoodView { BROWSE, ORDERS, GROUP, TRACKING }

@Composable
internal fun OrderFoodContent(
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
    onReportIssue: (String) -> Unit = {},
) {
    var view by remember { mutableStateOf(OrderFoodView.BROWSE) }
    var restaurants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    // Unfiltered, fetched once -- used to resolve a past order's restaurant for Reorder
    // even when that restaurant has been filtered out of the currently-browsed list.
    var allRestaurants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedRestaurant by remember { mutableStateOf<ShoppingMerchantDto?>(null) }
    var menu by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    val cart = remember { mutableStateMapOf<String, EatsCartLine>() }
    var showCheckout by remember { mutableStateOf(false) }
    var confirmedOrder by remember { mutableStateOf<EatsOrderDto?>(null) }
    var selectedOrder by remember { mutableStateOf<EatsOrderDto?>(null) }
    var confirmedDineInOrder by remember { mutableStateOf<DineInOrderDto?>(null) }
    var reorderingId by remember { mutableStateOf<String?>(null) }
    var reorderError by remember { mutableStateOf<String?>(null) }
    // Real bookmarked/favorited restaurants (2026-07-19) -- a set of restaurant ids for
    // a fast star-toggle lookup on each browse card.
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoritingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Real distance/ETA enrichment (2026-08-14) -- see ShoppingMerchantDto's own
    // buyerLat/buyerLng doc comment: the backend has real distanceKm/
    // deliveryTimeMinutes support and RestaurantCard already has the UI to render
    // them (see its own rating/distance/ETA row), but no client anywhere -- Android,
    // web, this file included -- ever actually sent the caller's location, so those
    // fields silently never populated. Same "opt-in, never assumed" discipline
    // MarketplaceContent's own browseLocation already establishes: only fetches if
    // ACCESS_FINE_LOCATION is already granted, never prompts for it here.
    val locationContext = LocalContext.current
    var browseLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val requestBrowseLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng -> browseLocation = lat to lng },
        onError = {},
    )
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(locationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) requestBrowseLocation()
    }
    // Real Baemin/Coupang Eats-style sort mode (itunda Eats redesign, 2026-08-28,
    // extending the previous fastest-delivery-only boolean toggle) -- "delivery_time"
    // only takes effect once a real browseLocation exists (deliveryTimeMinutes is
    // null server-side without one, see that chip's own gated rendering below);
    // "discount"/"min_order" are real, always-available signals adapting the
    // reference's own 최대할인/최소주문낮은매장 quick-filter chips onto itunda's own
    // real per-merchant data (see ShoppingMerchantBrowseService.browse's own doc
    // comment on both).
    val browseFilters = rememberEatsBrowseFilters()
    val browseState = rememberEatsBrowseState()

    fun loadFavorites() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteRestaurants()
                if (res.success) favoriteIds = res.favorites.map { it.restaurantId }.toSet()
            } catch (e: Exception) { /* non-critical, only backs the star toggle */ }
        }
    }

    fun toggleFavorite(restaurantId: String) {
        favoritingId = restaurantId
        coroutineScope.launch {
            try {
                if (restaurantId in favoriteIds) {
                    NetworkClient.apiService.removeFavoriteRestaurant(restaurantId)
                    favoriteIds = favoriteIds - restaurantId
                } else {
                    NetworkClient.apiService.addFavoriteRestaurant(restaurantId)
                    favoriteIds = favoriteIds + restaurantId
                }
            } catch (e: Exception) { /* real, non-critical -- a failed toggle just leaves the star as-is */ } finally {
                favoritingId = null
            }
        }
    }

    fun loadRestaurants() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getShoppingMerchants(
                    category = selectedCategory, businessType = "RESTAURANT", q = searchInput.trim().ifBlank { null },
                    buyerLat = browseLocation?.first, buyerLng = browseLocation?.second,
                    sortBy = when (browseFilters.sortMode) { "delivery_time" -> "delivery_time"; "rating" -> "rating"; "free_delivery" -> "free_delivery"; "nearest" -> "distance"; else -> null },
                )
                if (res.success) restaurants = when (browseFilters.sortMode) {
                    "rating" -> res.merchants.sortedByDescending { it.rating ?: 0.0 }
                    "free_delivery" -> res.merchants.sortedBy { it.toEatsPrototypeRestaurant().deliveryFeeRwf }
                    "nearest" -> res.merchants.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
                    "delivery_time" -> res.merchants.sortedBy { it.deliveryTimeMinutes ?: Int.MAX_VALUE }
                    else -> res.merchants
                }
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    // Re-fetch once a real location fix lands, so a browse that already rendered
    // (no distance/ETA yet, permission check + GPS fix both take real time) upgrades
    // in place instead of requiring the user to pull-to-refresh themselves.
    LaunchedEffect(browseLocation) { if (browseLocation != null) loadRestaurants() }
    LaunchedEffect(Unit) {
        loadRestaurants()
        loadFavorites()
        try {
            val allRes = NetworkClient.apiService.getShoppingMerchants(businessType = "RESTAURANT")
            if (allRes.success) allRestaurants = allRes.merchants
        } catch (e: Exception) { /* non-critical, only backs the Reorder lookup */ }
    }
    // Real category/search filter (2026-07-19), debounced so typing doesn't re-fetch on
    // every keystroke -- LaunchedEffect's own cancel-and-restart-on-key-change is the
    // debounce mechanism here.
    LaunchedEffect(selectedCategory, searchInput, browseFilters.sortMode) {
        delay(rw.itunda.core.network.SEARCH_DEBOUNCE_MS)
        loadRestaurants()
    }

    fun openRestaurant(m: ShoppingMerchantDto) {
        selectedRestaurant = m
        browseState.cartRestaurant = m
        cart.clear()
        menu = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMerchantProducts(m.merchantId)
                if (res.success) menu = res.products
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    // Real "Delivery" pill deep-link from Maps (2026-08-09) -- same real fallback shape
    // openDish above already established (resolve against the already-loaded real
    // catalog, fall back to a minimal stub carrying the real businessName Maps already
    // had, never a blank one) applied to a second real entry point into the same
    // openRestaurant flow. Consumed exactly once -- onPendingMerchantConsumed clears the
    // shell's own state so navigating away and back to Shop doesn't reopen it.
    LaunchedEffect(pendingMerchantId) {
        val merchantId = pendingMerchantId ?: return@LaunchedEffect
        val restaurant = allRestaurants?.find { it.merchantId == merchantId }
            ?: ShoppingMerchantDto(merchantId = merchantId, businessName = pendingMerchantName ?: "", category = null, cashbackRate = "1%")
        openRestaurant(restaurant)
        onPendingMerchantConsumed()
    }

    // Real "Reorder" button (2026-07-19): re-populate the cart from a past order's real
    // items, cross-referenced against the restaurant's current menu -- discontinued items
    // are silently dropped rather than added as phantom cart lines.
    fun handleReorder(order: EatsOrderDto) {
        val restaurant = allRestaurants?.find { it.merchantId == order.restaurantId }
        if (restaurant == null) {
            reorderError = "This restaurant is no longer available."
            return
        }
        reorderingId = order.id
        reorderError = null
        coroutineScope.launch {
            try {
                val orderDetail = NetworkClient.apiService.getEatsOrder(order.id)
                val menuRes = NetworkClient.apiService.getMerchantProducts(order.restaurantId)
                if (!orderDetail.success || !menuRes.success) {
                    reorderError = "Could not reorder."
                    return@launch
                }
                val activeProducts = menuRes.products.filter { it.active }.associateBy { it.id }
                // Real reorder-cart sanitization (2026-07-21) -- a reordered past order carries
                // no option selections. If the item now genuinely requires one, that bare line
                // can never check out -- drop it rather than let checkout silently 422, same
                // "discontinued item silently dropped" precedent already established above for
                // a menu item that's gone entirely.
                val newCart = mutableMapOf<String, EatsCartLine>()
                orderDetail.items.forEach { item ->
                    val product = activeProducts[item.productId]
                    if (product != null && product.eatsOptionGroups().isEmpty()) {
                        val key = eatsCartKey(item.productId, emptyList())
                        newCart[key] = EatsCartLine(item.productId, (newCart[key]?.quantity ?: 0) + item.quantity)
                    }
                }
                if (newCart.isEmpty()) {
                    reorderError = "None of the items from that order are on the menu anymore."
                    return@launch
                }
                selectedRestaurant = restaurant
                browseState.cartRestaurant = restaurant
                menu = menuRes.products
                cart.clear()
                cart.putAll(newCart)
                view = OrderFoodView.BROWSE
            } catch (e: HttpException) {
                reorderError = superAppErrorMessage(e)
            } catch (e: IOException) {
                reorderError = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                reorderingId = null
            }
        }
    }

    // Keep scroll state alive across detail screens; the browse list is re-fetched,
    // so restoring a stale saved index can crash while its loading placeholder is shown.
    val eatsListState = remember { LazyListState() }
    val eatsTouchedKey = remember { mutableStateOf<Any?>(null) }

    if (EatsOrderFlowDispatch(
            confirmedOrder = confirmedOrder,
            confirmedDineInOrder = confirmedDineInOrder,
            selectedRestaurant = selectedRestaurant,
            pickupInitially = browseState.isPickup,
            deliveryAddress = browseState.address,
            initialDeliveryNotes = browseState.kitchenNote,
            onItemPreferences = { preference ->
                browseState.kitchenNote = listOf(browseState.kitchenNote, preference)
                    .filter { it.isNotBlank() }.joinToString("; ").take(80)
            },
            savedCoupon = browseState.selectedCoupon,
            onCheckoutModeChange = { mode ->
                browseState.isPickup = mode == rw.itunda.feature.eats.impl.EatsCheckoutMode.PICKUP
                if (mode != rw.itunda.feature.eats.impl.EatsCheckoutMode.DELIVERY && browseState.selectedCoupon == "FREEDEL") {
                    browseState.selectedCoupon = null
                }
            },
            showCheckout = showCheckout,
            menu = menu,
            cart = cart,
            deviceStepUpHost = deviceStepUpHost,
            onOrderConfirmationDone = { order ->
                confirmedOrder = null; selectedRestaurant = null; menu = null
                cart.clear(); browseState.cartRestaurant = null; showCheckout = false
                selectedOrder = order; view = OrderFoodView.TRACKING
            },
            onDineInConfirmationDone = {
                confirmedDineInOrder = null; selectedRestaurant = null; menu = null
                cart.clear(); browseState.cartRestaurant = null; showCheckout = false; view = OrderFoodView.ORDERS
            },
            onCheckoutBack = { showCheckout = false },
            onOrderPlaced = { order -> confirmedOrder = order },
            onDineInOrderPlaced = { order -> confirmedDineInOrder = order },
            onMenuBack = { selectedRestaurant = null },
            onCheckoutRequested = { showCheckout = true },
            onGroupOrder = {
                selectedRestaurant = null; menu = null; cart.clear()
                browseState.cartRestaurant = null; showCheckout = false; view = OrderFoodView.GROUP
            },
        )
    ) return

    Column(modifier = Modifier.fillMaxSize().background(eatsPrototypePalette().background)) {
        if (view == OrderFoodView.TRACKING && selectedOrder != null) {
            EatsTrackingScreen(
                order = selectedOrder!!,
                restaurant = allRestaurants?.firstOrNull { it.merchantId == selectedOrder!!.restaurantId },
                onBack = { selectedOrder = null; view = OrderFoodView.ORDERS },
                onDone = { selectedOrder = null; view = OrderFoodView.ORDERS },
                onReportIssue = onReportIssue,
                modifier = Modifier.weight(1f),
            )
        } else {
        EatsBrowseHeader(
            state = browseState,
            searchInput = searchInput,
            cartCount = cart.values.sumOf { it.quantity },
            onSearchChange = { searchInput = it },
            onOrdersClick = { view = if (view == OrderFoodView.ORDERS) OrderFoodView.BROWSE else OrderFoodView.ORDERS },
            onCartClick = { selectedRestaurant = browseState.cartRestaurant; showCheckout = true },
        )
        LazyColumn(
            state = eatsListState, modifier = Modifier.weight(1f).padding(horizontal = Ids.layout.screenHorizontal).trackScrollPressedKey(eatsListState, eatsTouchedKey),
            verticalArrangement = Arrangement.Top,
        ) {
        if (view == OrderFoodView.ORDERS) {
            item {
                MyEatsOrdersView(onReorder = ::handleReorder, onTrack = { selectedOrder = it; view = OrderFoodView.TRACKING }, reorderingId = reorderingId, restaurants = allRestaurants)
                val reorderErr = reorderError
                if (reorderErr != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(reorderErr, color = Ids.colors.danger, fontSize = 13.sp)
                }
                MyDineInOrdersView()
            }
        } else if (view == OrderFoodView.GROUP) {
            item { GroupEatsOrderView(restaurants = allRestaurants) }
        } else {
            eatsBrowseItems(
                state = browseState,
                searchInput = searchInput,
                onSearchChange = { searchInput = it },
                selectedCategory = selectedCategory,
                onSelectCategory = { category -> selectedCategory = if (category == selectedCategory) null else category },
                filters = browseFilters,
                restaurants = restaurants,
                favoriteIds = favoriteIds,
                error = error,
                onRetry = ::loadRestaurants,
                onOpen = ::openRestaurant,
                onToggleFavorite = ::toggleFavorite,
                onClearFilters = {
                    searchInput = ""
                    selectedCategory = null
                    browseFilters.openNowOnly = false
                    browseFilters.favoritesOnly = false
                    browseFilters.sortMode = "delivery_time"
                },
            )
        }
        }
        }
    }
}

