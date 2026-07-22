package rw.itunda.feature.eats.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.ListingActionButton
import rw.itunda.core.designsystem.components.QtyButton
import rw.itunda.core.designsystem.components.RouteMiniMap
import rw.itunda.core.designsystem.components.SearchAndCategoryChips
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.StarRatingRow
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.AddressSuggestionDto
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemRequest
import rw.itunda.core.network.EatsRatingResponse
import rw.itunda.core.network.FavoriteRestaurantDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.PlaceEatsOrderRequest
import rw.itunda.core.network.RiderDto
import rw.itunda.core.network.SetRiderAvailabilityRequest
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.SubmitEatsReviewRequest
import rw.itunda.core.network.UpdateEatsOrderStatusRequest
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID

// Sixth Feature extraction (2026-07-23) after Marketplace/Jobs/Property/Community/Shop,
// same template -- see features/marketplace/impl/.../MarketplaceScreen.kt's own header
// comment for the full account. Deliver (the rider role) is folded into this same module
// rather than its own -- EatsContent's own EatsMode.DELIVER toggle calls DeliverContent
// directly (a real, tight coupling that already existed in app/ui/SuperAppTabs.kt: rider
// deliveries reuse EatsOrderDto/EatsOrderRow/nextRiderStatus/EATS_STATUS_LABEL directly),
// so splitting them into two Gradle modules would just recreate that coupling as a
// forbidden Feature-to-Feature dependency instead of removing it.
//
// One injected slot remains, same reasoning as every other extraction this session:
// deviceStepUpHost wraps :features:payments:impl's DeviceStepUpDialog, so it bridges two
// Feature modules -- must stay an app-level composition (see ShopScreen.kt's own header
// comment for the fuller explanation of why this one is architectural, not just a
// build-scope convenience). RouteMiniMap itself no longer needs injecting -- MapConfig.kt
// (2026-07-23) gave it the same BuildConfig-avoidance NetworkClient.init already had, so
// it's imported directly from core/designsystem, same as every other shared UI atom.

private val EATS_STATUS_LABEL = mapOf(
    "PLACED" to "Placed",
    "ACCEPTED" to "Accepted by restaurant",
    "PREPARING" to "Preparing",
    "READY_FOR_PICKUP" to "Ready for pickup",
    "RIDER_ASSIGNED" to "Rider on the way to restaurant",
    "PICKED_UP" to "Picked up — on the way",
    "DELIVERED" to "Delivered",
    "CANCELLED" to "Cancelled — refunded",
)

private val RIDER_STATUS_CHAIN = listOf("RIDER_ASSIGNED", "PICKED_UP", "DELIVERED")

private fun nextRiderStatus(current: String): String? {
    val idx = RIDER_STATUS_CHAIN.indexOf(current)
    return if (idx >= 0 && idx + 1 < RIDER_STATUS_CHAIN.size) RIDER_STATUS_CHAIN[idx + 1] else null
}

private enum class EatsMode { ORDER, DELIVER }

@Composable
fun EatsContent(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
) {
    var mode by remember { mutableStateOf(EatsMode.ORDER) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft).padding(4.dp)) {
            listOf(EatsMode.ORDER to "Order food", EatsMode.DELIVER to "Deliver").forEach { (m, label) ->
                val selected = m == mode
                Text(
                    label,
                    color = if (selected) Color.White else Ids.colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Ids.colors.brand else Color.Transparent)
                        .clickable { mode = m }
                        .padding(vertical = 8.dp),
                )
            }
        }
        when (mode) {
            EatsMode.ORDER -> OrderFoodContent(deviceStepUpHost)
            EatsMode.DELIVER -> DeliverContent()
        }
    }
}

private enum class OrderFoodView { BROWSE, FAVORITES, ORDERS }

@Composable
private fun OrderFoodContent(
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
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
    val cart = remember { mutableStateMapOf<String, Int>() }
    var showCheckout by remember { mutableStateOf(false) }
    var confirmedOrder by remember { mutableStateOf<EatsOrderDto?>(null) }
    var reorderingId by remember { mutableStateOf<String?>(null) }
    var reorderError by remember { mutableStateOf<String?>(null) }
    // Real bookmarked/favorited restaurants (2026-07-19) -- a set of restaurant ids for
    // a fast star-toggle lookup on each browse card.
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoritingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

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
                val res = NetworkClient.apiService.getShoppingMerchants(selectedCategory, searchInput.trim().ifBlank { null })
                if (res.success) restaurants = res.merchants
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) {
        loadRestaurants()
        loadFavorites()
        try {
            val allRes = NetworkClient.apiService.getShoppingMerchants()
            if (allRes.success) allRestaurants = allRes.merchants
        } catch (e: Exception) { /* non-critical, only backs the Reorder lookup */ }
        try {
            val catRes = NetworkClient.apiService.getMerchantCategories()
            if (catRes.success) categories = catRes.categories
        } catch (e: Exception) { /* non-critical, only backs the category chip row */ }
    }
    // Real category/search filter (2026-07-19), debounced so typing doesn't re-fetch on
    // every keystroke -- LaunchedEffect's own cancel-and-restart-on-key-change is the
    // debounce mechanism here.
    LaunchedEffect(selectedCategory, searchInput) {
        delay(300)
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
                val activeProductIds = menuRes.products.filter { it.active }.map { it.id }.toSet()
                val newCart = mutableMapOf<String, Int>()
                orderDetail.items.forEach { item ->
                    if (item.productId in activeProductIds) {
                        newCart[item.productId] = (newCart[item.productId] ?: 0) + item.quantity
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

    val restaurant = selectedRestaurant
    if (restaurant != null) {
        if (showCheckout) {
            EatsCheckoutView(
                restaurant = restaurant,
                cart = cart,
                menu = menu.orEmpty(),
                onBack = { showCheckout = false },
                onOrderPlaced = { order -> confirmedOrder = order },
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
        item {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft).padding(4.dp)) {
                listOf(OrderFoodView.BROWSE to "Restaurants", OrderFoodView.FAVORITES to "Favorites", OrderFoodView.ORDERS to "My orders").forEach { (v, label) ->
                    val selected = v == view
                    Text(
                        label,
                        color = if (selected) Color.White else Ids.colors.textSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Ids.colors.brand else Color.Transparent)
                            .clickable { view = v }
                            .padding(vertical = 8.dp),
                    )
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
            if (error != null) {
                item { ErrorCard(error!!, onRetry = ::loadRestaurants) }
            } else if (restaurants == null) {
                item { SkeletonBlock() }
            } else if (restaurants!!.isEmpty()) {
                item {
                    EmptyState(
                        if (selectedCategory != null || searchInput.isNotBlank()) "No restaurants match your search." else "No restaurants registered yet.",
                        icon = Icons.Outlined.Restaurant,
                    )
                }
            } else {
                items(restaurants!!, key = { it.merchantId }) { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                            .background(Ids.colors.surface)
                            .clickable { openRestaurant(m) }
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ids.colors.brand)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            // Real fix, 2026-07-21: every restaurant card showed the exact same
                            // generic "Real menu, real delivery" filler regardless of which
                            // restaurant it was -- not real per-restaurant info a user could
                            // actually scan and compare, unlike a real Coupang Eats/Baemin card.
                            // ShoppingMerchantDto already carries a real cashbackRate; it just
                            // wasn't shown here.
                            Text(
                                listOfNotNull(m.category, "${m.cashbackRate} cashback").joinToString(" · "),
                                color = Ids.colors.textSecondary,
                                fontSize = 12.sp,
                            )
                            // Real browse-card enrichment (2026-07-21) -- closes
                            // docs/DESIGN_REFERENCES.md's Eats recommendations #1/#2: rating
                            // previously sat one tap deeper inside RestaurantMenuView only,
                            // and there was no distance/delivery-time/min-order signal on the
                            // browse card at all. Every clause conditionally rendered on real
                            // data being present -- never a fabricated placeholder.
                            if (m.rating != null || m.distanceKm != null || m.minOrderAmount != null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (m.rating != null) {
                                        Icon(Icons.Outlined.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("%.1f (%d)".format(m.rating, m.reviewCount), color = Ids.colors.textSecondary, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        listOfNotNull(
                                            m.distanceKm?.let { "%.1f km".format(it) },
                                            m.deliveryTimeMinutes?.let { "~$it min" },
                                            m.minOrderAmount?.let { "Min ${it.toLong()} RWF" },
                                        ).joinToString(" · "),
                                        color = Ids.colors.textSecondary,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                        val isFavorite = m.merchantId in favoriteIds
                        Icon(
                            if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) Ids.colors.danger else Ids.colors.textSecondary,
                            modifier = Modifier
                                .size(22.dp)
                                .clickable(enabled = favoritingId != m.merchantId) { toggleFavorite(m.merchantId) },
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun FavoriteRestaurantsView(onOpen: (FavoriteRestaurantDto) -> Unit, onChanged: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoriteRestaurantDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteRestaurants()
                if (res.success) favorites = res.favorites
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun remove(restaurantId: String) {
        removingId = restaurantId
        coroutineScope.launch {
            try {
                NetworkClient.apiService.removeFavoriteRestaurant(restaurantId)
                favorites = favorites?.filterNot { it.restaurantId == restaurantId }
                onChanged()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                removingId = null
            }
        }
    }

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        favorites == null -> SkeletonBlock()
        favorites!!.isEmpty() -> EmptyState("No favorite restaurants yet. Tap the heart on a restaurant to save it here.", icon = Icons.Outlined.FavoriteBorder)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            favorites!!.forEach { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                        .background(Ids.colors.surface)
                        .clickable { onOpen(f) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ids.colors.brand)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(f.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            if (f.category != null) "${f.category} · Real menu, real delivery" else "Real menu, real delivery",
                            color = Ids.colors.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Remove from favorites",
                        tint = Ids.colors.danger,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable(enabled = removingId != f.restaurantId) { remove(f.restaurantId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RestaurantRatingBadge(restaurantId: String) {
    var rating by remember { mutableStateOf<EatsRatingResponse?>(null) }
    LaunchedEffect(restaurantId) {
        try {
            rating = NetworkClient.apiService.getRestaurantRating(restaurantId)
        } catch (e: Exception) {
            // Real, non-critical -- a rating fetch failure shouldn't block browsing the menu.
        }
    }
    val r = rating
    if (r != null && r.count > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("%.1f (%d)".format(r.average ?: 0.0, r.count), color = Ids.colors.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ReviewOrderCard(order: EatsOrderDto) {
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var restaurantRating by remember { mutableStateOf(0) }
    var restaurantComment by remember { mutableStateOf("") }
    var riderRating by remember { mutableStateOf(0) }
    var riderComment by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("Thanks for your review!", color = Ids.colors.textSecondary, fontSize = 13.sp)
        return
    }
    if (!open) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Rate this order", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
        Column {
            Text("Restaurant", color = Ids.colors.textSecondary, fontSize = 12.sp)
            StarRatingRow(restaurantRating) { restaurantRating = it }
            OutlinedTextField(
                value = restaurantComment,
                onValueChange = { restaurantComment = it },
                placeholder = { Text("How was the food? (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Column {
            Text("Rider", color = Ids.colors.textSecondary, fontSize = 12.sp)
            StarRatingRow(riderRating) { riderRating = it }
            OutlinedTextField(
                value = riderComment,
                onValueChange = { riderComment = it },
                placeholder = { Text("How was the delivery? (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).clickable { open = false }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (submitting) Ids.colors.textTertiary else Ids.colors.brand)
                    .clickable(enabled = !submitting) {
                        if (restaurantRating == 0 || riderRating == 0) {
                            error = "Rate both the restaurant and the rider."
                            return@clickable
                        }
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.submitEatsReview(
                                    order.id,
                                    SubmitEatsReviewRequest(restaurantRating, restaurantComment.trim().ifBlank { null }, riderRating, riderComment.trim().ifBlank { null }),
                                )
                                done = true
                            } catch (e: HttpException) {
                                // A 409 here is the real ORDER_ALREADY_REVIEWED case in
                                // practice -- this form only ever renders for a real
                                // DELIVERED order, so the sibling "not yet delivered"
                                // 409 can't actually occur through this UI path.
                                if (e.code() == 409) {
                                    done = true
                                } else {
                                    error = superAppErrorMessage(e)
                                }
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (submitting) "Submitting…" else "Submit review", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun RestaurantMenuView(
    restaurant: ShoppingMerchantDto,
    menu: List<MerchantProductDto>?,
    cart: SnapshotStateMap<String, Int>,
    onBack: () -> Unit,
    onCheckout: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val cartCount = cart.values.sum()
    Column(modifier = Modifier.fillMaxSize().padding(vertical = Ids.layout.screenVertical)) {
        BackTopBar(restaurant.businessName, onBack)
        RestaurantRatingBadge(restaurant.merchantId)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (menu == null) {
                item { SkeletonBlock(height = 72.dp) }
            } else if (menu.isEmpty()) {
                item { EmptyState("No menu items yet.", icon = Icons.Outlined.RestaurantMenu) }
            } else {
                items(menu, key = { it.id }) { p ->
                    val qty = cart[p.id] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("%,.0f RWF".format(p.price), color = Ids.colors.textSecondary, fontSize = 13.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QtyButton("-") { if (qty > 0) cart[p.id] = qty - 1 }
                            Text(qty.toString(), modifier = Modifier.width(28.dp), textAlign = TextAlign.Center, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                            QtyButton("+") { cart[p.id] = qty + 1 }
                        }
                    }
                }
            }
        }
        if (cartCount > 0) {
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand).clickable(onClick = onCheckout).padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checkout ($cartCount item${if (cartCount == 1) "" else "s"})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// Real self-hosted address-search autocomplete (2026-07-18) -- itunda's own Nominatim
// geocoder, not a third-party Maps API. Mirrors bank-mfe's AddressAutocomplete component:
// debounced real search-as-you-type, a real suggestion dropdown, and on selection the
// real resolved coordinates are handed back so the caller can submit them explicitly
// (taking priority over EatsOrderService's own automatic single-best-match fallback).
// Typing without selecting still places a real order via that fallback.
@Composable
private fun AddressAutocompleteField(
    address: String,
    onAddressChange: (String) -> Unit,
    onSuggestionSelected: (AddressSuggestionDto) -> Unit,
) {
    var suggestions by remember { mutableStateOf<List<AddressSuggestionDto>>(emptyList()) }
    var justSelected by remember { mutableStateOf(false) }

    LaunchedEffect(address) {
        if (justSelected) {
            justSelected = false
            return@LaunchedEffect
        }
        if (address.trim().length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(400)
        suggestions = try {
            val res = NetworkClient.apiService.searchDeliveryAddress(address.trim())
            if (res.success) res.suggestions else emptyList()
        } catch (e: Exception) {
            // Real, non-critical -- a failed suggestion fetch shouldn't block typing a
            // plain address; the order still places, just without a confirmed pin.
            emptyList()
        }
    }

    Column {
        OutlinedTextField(
            value = address,
            onValueChange = onAddressChange,
            placeholder = { Text("Delivery address") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            Card(
                shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
                colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                Column {
                    suggestions.forEach { s ->
                        Text(
                            s.displayName,
                            color = Ids.colors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    justSelected = true
                                    suggestions = emptyList()
                                    onSuggestionSelected(s)
                                }
                                .padding(12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EatsCheckoutView(
    restaurant: ShoppingMerchantDto,
    cart: Map<String, Int>,
    menu: List<MerchantProductDto>,
    onBack: () -> Unit,
    onOrderPlaced: (EatsOrderDto) -> Unit,
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
) {
    BackHandler(onBack = onBack)
    var address by remember { mutableStateOf("") }
    var addressLatitude by remember { mutableStateOf<Double?>(null) }
    var addressLongitude by remember { mutableStateOf<Double?>(null) }
    var deliveryNotes by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real device binding step-up (2026-07-21) -- Eats checkout was a real gap:
    // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
    // showed only a generic error, same fix already applied to Transfer/Savings.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val idempotencyKey = remember { UUID.randomUUID().toString() }

    val lines = cart.filter { it.value > 0 }.mapNotNull { (productId, qty) -> menu.find { it.id == productId }?.let { it to qty } }
    val total = lines.sumOf { (p, qty) -> p.price * qty }

    Column(modifier = Modifier.fillMaxSize().padding(vertical = Ids.layout.screenVertical)) {
        BackTopBar("Checkout", onBack)
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            items(lines) { (p, qty) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${p.name} x$qty", color = Ids.colors.textPrimary, fontSize = 14.sp)
                    Text("%,.0f RWF".format(p.price * qty), color = Ids.colors.textPrimary, fontSize = 14.sp)
                }
            }
            item { Divider(color = Ids.colors.divider, modifier = Modifier.padding(vertical = 10.dp)) }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = Ids.colors.textPrimary, fontSize = 14.sp)
                    Text("%,.0f RWF".format(total), color = Ids.colors.textPrimary, fontSize = 14.sp)
                }
            }
            item { Text("Plus a real delivery fee, added at checkout", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)) }
            item { Spacer(modifier = Modifier.height(14.dp)) }
            item {
                AddressAutocompleteField(
                    address = address,
                    onAddressChange = { address = it; addressLatitude = null; addressLongitude = null },
                    onSuggestionSelected = { s ->
                        address = s.displayName
                        addressLatitude = s.latitude
                        addressLongitude = s.longitude
                    },
                )
            }
            if (addressLatitude != null) {
                item { Text("Pinned -- real distance-based delivery fee applies", color = Ids.colors.success, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
            item {
                OutlinedTextField(
                    value = deliveryNotes,
                    onValueChange = { if (it.length <= 500) deliveryNotes = it },
                    placeholder = { Text("Delivery notes (optional) -- e.g. Leave at the gate") },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
            error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) } }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (submitting || address.isBlank()) Ids.colors.textTertiary else Ids.colors.brand)
                .clickable(enabled = !submitting && address.isNotBlank()) {
                    submitting = true
                    error = null
                    needsDeviceVerification = false
                    coroutineScope.launch {
                        try {
                            val res = NetworkClient.apiService.placeEatsOrder(
                                idempotencyKey = idempotencyKey,
                                request = PlaceEatsOrderRequest(
                                    restaurantId = restaurant.merchantId,
                                    items = lines.map { (p, qty) -> EatsOrderItemRequest(p.id, qty) },
                                    deliveryAddress = address.trim(),
                                    deliveryLatitude = addressLatitude,
                                    deliveryLongitude = addressLongitude,
                                    deliveryNotes = deliveryNotes.trim().ifBlank { null },
                                ),
                            )
                            if (res.success) onOrderPlaced(res.order)
                        } catch (e: HttpException) {
                            if (isDeviceNotVerifiedError(e)) {
                                needsDeviceVerification = true
                            } else {
                                error = superAppErrorMessage(e)
                            }
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            submitting = false
                        }
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (submitting) "Placing order…" else "Place order", color = Color.White, fontWeight = FontWeight.Bold) }
        deviceStepUpHost(
            needsDeviceVerification,
            { needsDeviceVerification = false },
            {
                needsDeviceVerification = false
                submitting = true
                try {
                    val res = NetworkClient.apiService.placeEatsOrder(
                        idempotencyKey = idempotencyKey,
                        request = PlaceEatsOrderRequest(
                            restaurantId = restaurant.merchantId,
                            items = lines.map { (p, qty) -> EatsOrderItemRequest(p.id, qty) },
                            deliveryAddress = address.trim(),
                            deliveryLatitude = addressLatitude,
                            deliveryLongitude = addressLongitude,
                            deliveryNotes = deliveryNotes.trim().ifBlank { null },
                        ),
                    )
                    if (res.success) onOrderPlaced(res.order)
                } catch (e: HttpException) {
                    error = superAppErrorMessage(e)
                } catch (e: IOException) {
                    error = "Couldn't reach itunda. Check your connection and try again."
                } finally {
                    submitting = false
                }
            },
        )
    }
}

@Composable
private fun EatsOrderConfirmationView(order: EatsOrderDto, onDone: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Order placed") },
        text = {
            Column {
                Text("%,.0f RWF".format(order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("Delivering to ${order.deliveryAddress}", color = Ids.colors.textSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Track order") } },
    )
}

@Composable
private fun EatsOrderRow(
    order: EatsOrderDto,
    restaurant: ShoppingMerchantDto? = null,
    action: (@Composable () -> Unit)? = null,
) {
    var showRoute by remember { mutableStateOf(false) }
    val restaurantLat = restaurant?.latitude
    val restaurantLng = restaurant?.longitude
    val deliveryLat = order.deliveryLatitude
    val deliveryLng = order.deliveryLongitude
    val canShowRoute = restaurantLat != null && restaurantLng != null && deliveryLat != null && deliveryLng != null
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(EATS_STATUS_LABEL[order.status] ?: order.status, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(order.deliveryAddress, color = Ids.colors.textSecondary, fontSize = 12.sp)
                }
                Text("%,.0f RWF".format(order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            if (!order.deliveryNotes.isNullOrBlank()) {
                Text(
                    "Note: ${order.deliveryNotes}",
                    color = Ids.colors.textPrimary,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Ids.colors.surfaceSoft)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            // Real "view delivery route" (2026-07-19, item 8 on the Maps "100%" roadmap)
            // -- reuses itunda's own self-hosted OSRM directions.
            if (canShowRoute) {
                ListingActionButton(if (showRoute) "Hide route" else "🚗 View real delivery route", false) { showRoute = !showRoute }
            }
            if (showRoute && restaurant != null && restaurantLat != null && restaurantLng != null && deliveryLat != null && deliveryLng != null) {
                RouteMiniMap(restaurantLat, restaurantLng, deliveryLat, deliveryLng, restaurant.businessName, "Delivery address")
            }
            action?.invoke()
        }
    }
}

@Composable
private fun MyEatsOrdersView(
    onReorder: (EatsOrderDto) -> Unit,
    reorderingId: String?,
    restaurants: List<ShoppingMerchantDto>?,
) {
    var orders by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyEatsOrders()
                if (res.success) orders = res.orders
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    // Real poll for order-tracking status, same 4s cadence as Talk's own poll.
    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(4000)
        }
    }

    fun cancel(orderId: String) {
        cancellingId = orderId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelEatsOrder(orderId)
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                cancellingId = null
            }
        }
    }

    Column {
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else if (orders == null) {
            SkeletonBlock()
        } else if (orders!!.isEmpty()) {
            EmptyState("No orders yet.", icon = Icons.AutoMirrored.Outlined.ReceiptLong)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                orders!!.forEach { o ->
                    EatsOrderRow(o, restaurant = restaurants?.find { it.merchantId == o.restaurantId }) {
                        if (o.status == "PLACED") {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Ids.colors.danger)
                                    .clickable(enabled = cancellingId != o.id) { cancel(o.id) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    if (cancellingId == o.id) "Cancelling…" else "Cancel order",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        } else if (o.status == "DELIVERED") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                ReviewOrderCard(o)
                                ReorderButton(reordering = reorderingId == o.id, onClick = { onReorder(o) })
                            }
                        } else if (o.status == "CANCELLED") {
                            ReorderButton(reordering = reorderingId == o.id, onClick = { onReorder(o) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderButton(reordering: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Ids.colors.brand)
            .clickable(enabled = !reordering, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            if (reordering) "Reordering…" else "Reorder",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun DeliverContent() {
    var rider by remember { mutableStateOf<RiderDto?>(null) }
    var loadedRider by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var registering by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var mine by remember { mutableStateOf<List<EatsOrderDto>?>(null) }
    var busyOrderId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadRider() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyRiderProfile()
                if (res.success) rider = res.rider
                error = null
            } catch (e: HttpException) {
                if (e.code() == 404) {
                    rider = null
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                loadedRider = true
            }
        }
    }
    LaunchedEffect(Unit) { loadRider() }

    suspend fun loadDeliveries() {
        try {
            val a = NetworkClient.apiService.getAvailableDeliveries()
            val m = NetworkClient.apiService.getRiderDeliveries()
            if (a.success) available = a.orders
            if (m.success) mine = m.orders
        } catch (_: Exception) {
            // Keep showing the last-known lists on a transient poll failure.
        }
    }
    LaunchedEffect(rider?.id) {
        if (rider == null) return@LaunchedEffect
        while (true) {
            loadDeliveries()
            delay(4000)
        }
    }

    if (!loadedRider) {
        SkeletonBlock()
        return
    }

    val currentRider = rider
    if (currentRider == null) {
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Deliver with Itunda", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Earn a real delivery fee for every order you deliver, paid straight to your wallet.",
                    color = Ids.colors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Ids.colors.brand)
                        .clickable(enabled = !registering) {
                            registering = true
                            error = null
                            coroutineScope.launch {
                                try {
                                    val res = NetworkClient.apiService.registerRider()
                                    if (res.success) rider = res.rider
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } finally {
                                    registering = false
                                }
                            }
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                ) { Text(if (registering) "Registering…" else "Become a rider", color = Color.White, fontWeight = FontWeight.Bold) }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)) }
            }
        }
        return
    }

    val activeDeliveries = mine.orEmpty().filter { it.status != "DELIVERED" }
    val pastDeliveries = mine.orEmpty().filter { it.status == "DELIVERED" }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap), contentPadding = PaddingValues(bottom = 20.dp)) {
        item {
            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(18.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (currentRider.available) "You're online" else "You're offline", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(if (currentRider.available) "Visible for new deliveries" else "Go online to see deliveries", color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (currentRider.available) Ids.colors.danger else Ids.colors.brand)
                            .clickable {
                                coroutineScope.launch {
                                    try {
                                        val res = NetworkClient.apiService.setRiderAvailability(SetRiderAvailabilityRequest(!currentRider.available))
                                        if (res.success) rider = res.rider
                                    } catch (e: HttpException) {
                                        error = superAppErrorMessage(e)
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) { Text(if (currentRider.available) "Go offline" else "Go online", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
        }
        error?.let { item { Text(it, color = Ids.colors.danger, fontSize = 12.sp) } }
        if (activeDeliveries.isNotEmpty()) {
            item { Text("Your active deliveries", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(activeDeliveries, key = { it.id }) { o ->
                val next = nextRiderStatus(o.status)
                EatsOrderRow(o) {
                    if (next != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ids.colors.brand)
                                .clickable(enabled = busyOrderId != o.id) {
                                    busyOrderId = o.id
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.updateRiderOrderStatus(o.id, UpdateEatsOrderStatusRequest(next))
                                            loadDeliveries()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } finally {
                                            busyOrderId = null
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                if (busyOrderId == o.id) "Updating…" else "Mark ${(EATS_STATUS_LABEL[next] ?: next).lowercase()}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
        if (currentRider.available) {
            item { Text("Available deliveries", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            if (available == null) {
                item { Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(100.dp)) {} }
            } else if (available!!.isEmpty()) {
                item { EmptyState("No deliveries waiting right now.", icon = Icons.AutoMirrored.Outlined.ReceiptLong) }
            } else {
                items(available!!, key = { it.id }) { o ->
                    EatsOrderRow(o) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Ids.colors.brand)
                                .clickable(enabled = busyOrderId != o.id) {
                                    busyOrderId = o.id
                                    error = null
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.claimDelivery(o.id)
                                            loadDeliveries()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } finally {
                                            busyOrderId = null
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) { Text(if (busyOrderId == o.id) "Claiming…" else "Claim delivery", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
        if (pastDeliveries.isNotEmpty()) {
            item { Text("Completed", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            items(pastDeliveries, key = { it.id }) { o -> EatsOrderRow(o) }
        }
    }
}
