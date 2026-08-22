package rw.itunda.feature.eats.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
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
import rw.itunda.core.designsystem.components.SearchAndCategoryChips
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.DineInOrderDto
import rw.itunda.core.network.EatsDishDto
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

internal enum class EatsMode { ORDER, DELIVER }

@Composable
fun EatsContent(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    // Real "Delivery" pill deep-link from Maps (2026-08-09) -- see
    // ItundaAppScreen.kt's own doc comment on pendingEatsMerchantId for the full
    // account. Forces ORDER mode (not DELIVER) since a pending target is always a real
    // merchant to order FROM, never a rider-role entry point.
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
) {
    var mode by remember { mutableStateOf(EatsMode.ORDER) }
    LaunchedEffect(pendingMerchantId) {
        if (pendingMerchantId != null) mode = EatsMode.ORDER
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal)) {
        // Real de-emphasis (2026-07-24) -- "Deliver" (the rider role) previously got
        // equal 50% visual weight next to "Order food" as a full segmented toggle,
        // even though itunda already ships a dedicated, separate riderapp
        // (rw.itunda.rider) for exactly this role. Stacked on top of Shop/Eats' own
        // toggle above and Restaurants/Favorites/My orders below, that read as three
        // full tiers of chrome before any real content -- most people opening Eats
        // are ordering, not delivering. Kept reachable (a rider without the separate
        // app installed can still use it here) as a small secondary link instead of
        // an equal peer tab.
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            Text(
                text = if (mode == EatsMode.ORDER) "Deliver instead" else "Back to ordering",
                color = Ids.colors.textBrand,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier.pressScaleClickable { mode = if (mode == EatsMode.ORDER) EatsMode.DELIVER else EatsMode.ORDER },
            )
        }
        when (mode) {
            EatsMode.ORDER -> OrderFoodContent(
                deviceStepUpHost,
                pendingMerchantId = pendingMerchantId,
                pendingMerchantName = pendingMerchantName,
                onPendingMerchantConsumed = onPendingMerchantConsumed,
            )
            EatsMode.DELIVER -> DeliverContent()
        }
    }
}


internal enum class OrderFoodView { BROWSE, FAVORITES, ORDERS }

@Composable
internal fun OrderFoodContent(
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
    pendingMerchantId: String? = null,
    pendingMerchantName: String? = null,
    onPendingMerchantConsumed: () -> Unit = {},
) {
    var view by remember { mutableStateOf(OrderFoodView.BROWSE) }
    var restaurants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    // Unfiltered, fetched once -- used to resolve a past order's restaurant for Reorder
    // even when that restaurant has been filtered out of the currently-browsed list.
    var allRestaurants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedRestaurant by remember { mutableStateOf<ShoppingMerchantDto?>(null) }
    var menu by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    val cart = remember { mutableStateMapOf<String, EatsCartLine>() }
    var showCheckout by remember { mutableStateOf(false) }
    var confirmedOrder by remember { mutableStateOf<EatsOrderDto?>(null) }
    var confirmedDineInOrder by remember { mutableStateOf<DineInOrderDto?>(null) }
    var reorderingId by remember { mutableStateOf<String?>(null) }
    var reorderError by remember { mutableStateOf<String?>(null) }
    // Real bookmarked/favorited restaurants (2026-07-19) -- a set of restaurant ids for
    // a fast star-toggle lookup on each browse card.
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoritingId by remember { mutableStateOf<String?>(null) }
    // Real dish grid (2026-08-03) -- see EatsDishGrid's own doc comment.
    var dishes by remember { mutableStateOf<List<EatsDishDto>>(emptyList()) }
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
    // Real Baemin/Coupang Eats-style "fastest delivery" sort tab -- only meaningful
    // once a real browseLocation exists (deliveryTimeMinutes is null server-side
    // without one), so the toggle itself only renders when browseLocation is real.
    var sortByFastestDelivery by remember { mutableStateOf(false) }

    fun loadDishes() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getEatsDishes(selectedCategory)
                if (res.success) dishes = res.dishes
            } catch (e: Exception) { /* non-critical -- the restaurant list below still works */ }
        }
    }
    LaunchedEffect(selectedCategory) { loadDishes() }

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
                    sortBy = if (sortByFastestDelivery) "delivery_time" else null,
                )
                if (res.success) restaurants = res.merchants
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
        try {
            val catRes = NetworkClient.apiService.getMerchantCategories(businessType = "RESTAURANT")
            if (catRes.success) categories = catRes.categories
        } catch (e: Exception) { /* non-critical, only backs the category chip row */ }
    }
    // Real category/search filter (2026-07-19), debounced so typing doesn't re-fetch on
    // every keystroke -- LaunchedEffect's own cancel-and-restart-on-key-change is the
    // debounce mechanism here.
    LaunchedEffect(selectedCategory, searchInput, sortByFastestDelivery) {
        delay(rw.itunda.core.network.SEARCH_DEBOUNCE_MS)
        loadRestaurants()
    }

    fun openRestaurant(m: ShoppingMerchantDto) {
        selectedRestaurant = m
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

    // Real dish-grid tap-through (2026-08-03) -- see EatsDishGrid's own doc comment on
    // why this opens the restaurant rather than a standalone dish page. Resolves against
    // the already-loaded allRestaurants for the real category/cashbackRate, falling back
    // to a minimal stub built from the dish's own merchantId/merchantName -- same
    // fallback shape FavoriteRestaurantsView's onOpen already uses below.
    fun openDish(dish: EatsDishDto) {
        val restaurant = allRestaurants?.find { it.merchantId == dish.merchantId }
            ?: ShoppingMerchantDto(merchantId = dish.merchantId, businessName = dish.merchantName, category = null, cashbackRate = "1%")
        openRestaurant(restaurant)
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
                    if (product != null && product.optionGroups.isEmpty()) {
                        val key = eatsCartKey(item.productId, emptyList())
                        newCart[key] = EatsCartLine(item.productId, (newCart[key]?.quantity ?: 0) + item.quantity)
                    }
                }
                if (newCart.isEmpty()) {
                    reorderError = "None of the items from that order are on the menu anymore."
                    return@launch
                }
                selectedRestaurant = restaurant
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

    val confirmed = confirmedOrder
    if (confirmed != null) {
        EatsOrderConfirmationView(confirmed, onDone = {
            confirmedOrder = null
            selectedRestaurant = null
            menu = null
            cart.clear()
            showCheckout = false
            view = OrderFoodView.ORDERS
        })
        return
    }
    val confirmedDineIn = confirmedDineInOrder
    if (confirmedDineIn != null) {
        DineInOrderConfirmationView(confirmedDineIn, onDone = {
            confirmedDineInOrder = null
            selectedRestaurant = null
            menu = null
            cart.clear()
            showCheckout = false
            view = OrderFoodView.ORDERS
        })
        return
    }

    val restaurant = selectedRestaurant
    if (restaurant != null) {
        if (showCheckout) {
            EatsCheckoutView(
                restaurant = restaurant,
                cart = cart,
                menu = menu.orEmpty(),
                onBack = { showCheckout = false },
                onOrderPlaced = { order -> confirmedOrder = order },
                onDineInOrderPlaced = { order -> confirmedDineInOrder = order },
                deviceStepUpHost = deviceStepUpHost,
            )
        } else {
            RestaurantMenuView(
                restaurant = restaurant,
                menu = menu,
                cart = cart,
                onBack = { selectedRestaurant = null },
                onCheckout = { showCheckout = true },
            )
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap)) {
        item { PlatformMembershipCard() }
        item { EatsMembershipCard() }
        item {
            // Flat, horizontally-scrolling category strip (2026-07-24), same
            // Karrot/Toss-Shopping-style treatment as Hood's own nav rows --
            // replacing a filled-pill segmented control.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                listOf(OrderFoodView.BROWSE to "Restaurants", OrderFoodView.FAVORITES to "Favorites", OrderFoodView.ORDERS to "My orders").forEach { (v, label) ->
                    val selected = v == view
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.pressScaleClickable { view = v }) {
                        Text(
                            label,
                            color = if (selected) Ids.colors.textPrimary else Ids.colors.textSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                        )
                        Box(
                            modifier = Modifier
                                .height(2.dp)
                                .width(18.dp)
                                .background(if (selected) Ids.colors.brand else Color.Transparent, RoundedCornerShape(1.dp)),
                        )
                    }
                }
            }
        }
        if (view == OrderFoodView.ORDERS) {
            item {
                MyEatsOrdersView(onReorder = ::handleReorder, reorderingId = reorderingId, restaurants = allRestaurants)
                val reorderErr = reorderError
                if (reorderErr != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(reorderErr, color = Ids.colors.danger, fontSize = 13.sp)
                }
                MyDineInOrdersView()
            }
        } else if (view == OrderFoodView.FAVORITES) {
            item {
                FavoriteRestaurantsView(
                    onOpen = { fav ->
                        val restaurant = allRestaurants?.find { it.merchantId == fav.restaurantId }
                            ?: ShoppingMerchantDto(merchantId = fav.restaurantId, businessName = fav.businessName, category = fav.category, cashbackRate = "1%")
                        openRestaurant(restaurant)
                    },
                    onChanged = ::loadFavorites,
                )
            }
        } else {
            // Real Coupang Eats category icon row (2026-08-12, direct user screenshot)
            // -- the real reference shows a horizontally-scrolling row of round
            // category icons (일식/회해물/구이/찜탕/한식 -- Japanese/Seafood/Grilled/
            // Stew/Korean) above the search bar, not just the plain text chips below.
            // Uses itunda's own real merchant categories (getMerchantCategories,
            // already fetched above -- real values like "Rwandan"/"Fast Food"/"Coffee
            // & Bakery" from real seeded merchants, not fabricated Korean cuisine
            // names) mapped to real Material icons -- no invented dish photography
            // itunda has no license or real source for. Additive: the text chip row
            // below still does the actual filtering; this is the same real
            // selectedCategory state, just a second, visually-matched way to reach it.
            if (categories.isNotEmpty()) {
                item { EatsCategoryIconRow(categories, selectedCategory) { c -> selectedCategory = if (c == selectedCategory) null else c } }
            }
            item {
                SearchAndCategoryChips(
                    searchInput = searchInput,
                    onSearchChange = { searchInput = it },
                    placeholder = "Search restaurants",
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { c -> selectedCategory = if (c == selectedCategory) null else c },
                )
            }
            // Real Baemin/Coupang Eats-style "fastest delivery" sort tab -- only shown
            // once a real browseLocation exists, since the sort is a no-op without one
            // (see ShoppingController.getEligibleMerchants's own doc comment).
            if (browseLocation != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().pressScaleClickable { sortByFastestDelivery = !sortByFastestDelivery },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Bolt, contentDescription = null,
                            tint = if (sortByFastestDelivery) Ids.colors.brand else Ids.colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Fastest delivery",
                            color = if (sortByFastestDelivery) Ids.colors.brand else Ids.colors.textSecondary,
                            fontWeight = if (sortByFastestDelivery) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            if (searchInput.isBlank() && dishes.isNotEmpty()) {
                item { EatsDishGrid(dishes, onOpen = ::openDish) }
            }
            if (error != null) {
                item { ErrorCard(error!!, onRetry = ::loadRestaurants) }
            } else if (restaurants == null) {
                item { SkeletonBlock() }
            } else if (restaurants!!.isEmpty()) {
                item {
                    EmptyState(
                        // Real copy-voice fix (item 244, round 5 of the empty-state pass):
                        // the "registered yet" case is honest about whose gap this is --
                        // no restaurant has joined yet, not something the reader is missing
                        // a step on, matching docs/COPY_VOICE.md's "when the cause is
                        // someone else's, say so honestly" rule.
                        if (selectedCategory != null || searchInput.isNotBlank()) "No restaurants match your search — try a different category or search term." else "No restaurants registered yet — check back once restaurants in your area join itunda Eats.",
                        icon = Icons.Outlined.Restaurant,
                    )
                }
            } else {
                items(restaurants!!, key = { it.merchantId }) { m -> RestaurantCard(m, isFavorite = m.merchantId in favoriteIds, favoriteBusy = favoritingId == m.merchantId, onOpen = { openRestaurant(m) }, onToggleFavorite = { toggleFavorite(m.merchantId) }) }
            }
        }
    }
}

