package rw.itunda.feature.shop.impl

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import java.util.Locale
import rw.itunda.core.designsystem.components.pressScaleClickable
import rw.itunda.core.designsystem.components.trackScrollPressedKey
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
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
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.StatusBadge
import kotlin.math.roundToInt
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.components.SearchAndCategoryChips
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.itundaface.ClockGlyph
import rw.itunda.core.designsystem.itundaface.FlameGlyph
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.DealProductDto
import rw.itunda.core.network.RecentlyViewedProductDto
import rw.itunda.core.network.RecentlyViewedProductsStore
import rw.itunda.core.network.TimeDealViewDto
import rw.itunda.core.network.MembershipDayStatusResponse
import rw.itunda.core.network.ProductSearchResultDto
import rw.itunda.core.network.FavoriteProductDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OrderDto
import rw.itunda.core.network.MerchantBillingPlanDto
import rw.itunda.core.network.MerchantBillingSubscriptionDto
import rw.itunda.core.network.NearbyMerchantAdDto
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException




// Shop screen entry point + browse content. Sub-views live in
// ShopBrowseComponents.kt / ShopMerchantDetail.kt / ShopBooking.kt /
// ShopProductDetail.kt / ShopOrders.kt / ShopMerchantOrders.kt /
// ShopReturns.kt / ShopReviews.kt (split 2026-08-19 for real file-size
// decomposition, not a single-slice extraction).

internal enum class CommerceView { BROWSE, ORDERS, WISHLIST, SUBSCRIPTIONS, QUESTIONS }

// Real cross-merchant cart (2026-07-20) -- closes the "real Coupang splits a
// multi-seller cart into per-seller orders, not attempted here" simplification the
// matrix named. Flattened (not nested maps) so a plain SnapshotStateMap keyed by
// "merchantId:productId" works cleanly with Compose recomposition -- each entry
// carries its own merchant/product context, so grouping-by-merchant at checkout
// time is a plain in-memory groupBy, no separate lookup needed.
internal data class CommerceCartLine(val merchantId: String, val businessName: String, val product: MerchantProductDto, val quantity: Int)
internal data class CommerceCheckoutResult(val merchantId: String, val businessName: String, val order: OrderDto?, val error: String?)

// Real Coupang 타임특가 (Time Deal, item 226) countdown -- mirrors bank-mfe's own
// formatDealCountdown exactly.
internal fun formatTimeDealCountdown(endsAt: String): String {
    val msLeft = java.time.Instant.parse(endsAt).toEpochMilli() - java.time.Instant.now().toEpochMilli()
    if (msLeft <= 0) return "Ending soon"
    val totalMinutes = msLeft / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m left" else "${minutes}m left"
}

// Real live HH:MM:SS countdown (2026-08-25, direct Toss Shopping reference screenshot
// -- "⏰ 23:24:20 Limited time offer") -- same real TimeDeal.endsAt this file's own
// formatTimeDealCountdown already reads, just ticking to the second and formatted for
// the hero banner rather than a small rail badge.
internal fun formatTimeDealCountdownHms(endsAt: String): String {
    val msLeft = java.time.Instant.parse(endsAt).toEpochMilli() - java.time.Instant.now().toEpochMilli()
    if (msLeft <= 0) return "00:00:00"
    val totalSeconds = msLeft / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

@Composable
fun CommerceShopContent(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    onMessageSeller: (String) -> Unit = {},
) {
    var view by remember { mutableStateOf(CommerceView.BROWSE) }
    var merchants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMerchant by remember { mutableStateOf<ShoppingMerchantDto?>(null) }
    var contactingMerchant by remember { mutableStateOf<ShoppingMerchantDto?>(null) } // see ShopSellerContactPickerOverlay
    var products by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    var selectedProduct by remember { mutableStateOf<MerchantProductDto?>(null) }
    val cart = remember { mutableStateMapOf<String, CommerceCartLine>() }
    var showCart by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<CommerceCheckoutResult>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Real Shop product wishlist (2026-07-24) -- lifted here same as Marketplace's own
    // favoriteIds, so the heart on a product card (grid or detail) stays correct
    // whichever screen toggled it. See ApiService.kt's FavoriteProductDto doc comment.
    var favoriteProductIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoritingProductId by remember { mutableStateOf<String?>(null) }

    // Real Naver Smart Store-style "알림받기" (follow a store) -- first Android client
    // for this feature (item 117, found via a content-grep sweep: bank-mfe has it,
    // Android/iOS didn't). Lifted here same as favoriteProductIds above, so the
    // Follow/Following state on a merchant's detail view stays correct across
    // re-opens without a per-open refetch.
    var followedMerchantIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var followBusyMerchantId by remember { mutableStateOf<String?>(null) }

    // Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
    // recommendation #8. Every entry is a real merchant-set discount, never a
    // fabricated promo -- see backend MerchantProductRepository.findDeals's own doc
    // comment.
    // Real "Recently viewed" rail (2026-08-10) -- see RecentlyViewedProductsStore.kt's
    // own doc comment. Purely local, same as Maps' recent searches.
    val recentlyViewedContext = LocalContext.current
    val recentlyViewedStore = remember { RecentlyViewedProductsStore(recentlyViewedContext) }
    var recentlyViewed by remember { mutableStateOf(recentlyViewedStore.getAll()) }

    var deals by remember { mutableStateOf<List<DealProductDto>?>(null) }
    LaunchedEffect(Unit) {
        try {
            val res = NetworkClient.apiService.getShopDeals()
            if (res.success) deals = res.products
        } catch (e: Exception) {
            // Real, non-critical -- the Deals rail just won't render if this fails.
        }
    }

    // Real Toss Shopping banner carousel (2026-08-12, direct user screenshot) -- see
    // backend TimeDealService.getBanners's own doc comment: every banner IS a real,
    // currently-active Time Deal, never fabricated promotional content.
    var banners by remember { mutableStateOf<List<rw.itunda.core.network.TimeDealViewDto>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val res = NetworkClient.apiService.getShoppingBanners()
            if (res.success) banners = res.banners
        } catch (e: Exception) {
            // Real, non-critical -- the carousel just won't render if this fails.
        }
    }

    // Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row --
    // see backend ShoppingMissionService's own doc comment. Every mission credits real
    // RWF to the real account; itunda has never had a separate points currency.
    var missions by remember { mutableStateOf<List<rw.itunda.core.network.ShoppingMissionDto>>(emptyList()) }
    var spinOutcomes by remember { mutableStateOf<List<rw.itunda.core.network.SpinOutcomeDto>>(emptyList()) }
    var missionFeedback by remember { mutableStateOf<String?>(null) }
    var missionBusyType by remember { mutableStateOf<String?>(null) }

    fun loadMissions() {
        loadShopMissions(coroutineScope) { loadedMissions, loadedSpinOutcomes ->
            missions = loadedMissions
            spinOutcomes = loadedSpinOutcomes
        }
    }
    LaunchedEffect(Unit) { loadMissions() }

    fun completeMission(type: String) {
        completeShopMission(
            type = type,
            coroutineScope = coroutineScope,
            onBusyTypeChanged = { missionBusyType = it },
            onFeedback = { missionFeedback = it },
            onCompleted = ::loadMissions,
        )
    }

    // Real Coupang 타임특가 (Time Deal, item 226) -- see TimeDealDto's own doc comment
    // on the backend. A time-boxed, quantity-capped event, distinct from the
    // always-on Deals rail above. Re-fetched every 30s so a deal that just sold out
    // or expired stops showing without a manual refresh, matching bank-mfe's own
    // established re-fetch interval for this exact feature.
    var timeDeals by remember { mutableStateOf<List<TimeDealViewDto>?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val res = NetworkClient.apiService.getActiveTimeDeals()
                if (res.success) timeDeals = res.deals
            } catch (e: Exception) {
                // Real, non-critical -- the Time Deals rail just won't render if this fails.
            }
            kotlinx.coroutines.delay(30000)
        }
    }

    // Real Naver Pay 멤버십 데이 (Membership Day) cashback boost -- bank-mfe already has
    // this; this is the first Android client. See the backend's ShoppingCashbackService
    // doc comment for the real "first Monday of the month" eligibility rule.
    var membershipDay by remember { mutableStateOf<MembershipDayStatusResponse?>(null) }
    LaunchedEffect(Unit) {
        try {
            membershipDay = NetworkClient.apiService.getMembershipDayStatus()
        } catch (e: Exception) {
            // Real, non-critical -- the banner just won't render if this fails.
        }
    }

    // Real 당근(Karrot) 반경 타기팅-style nearby ads rail -- see lib/shopping.ts's own
    // NearbyMerchantAd doc comment. Silent, non-blocking: a customer who denies/lacks
    // location just never sees this rail, same discipline rememberRealLocationRequester
    // already establishes elsewhere. bank-mfe already has this; this is the first
    // Android client.
    var nearbyAds by remember { mutableStateOf<List<NearbyMerchantAdDto>>(emptyList()) }
    val requestNearbyAdsLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng ->
            coroutineScope.launch {
                try {
                    nearbyAds = NetworkClient.apiService.getNearbyMerchantAds(lat, lng).ads
                } catch (e: Exception) {
                    // Real, non-critical -- the rail just won't render if this fails.
                }
            }
        },
        onError = {},
    )
    LaunchedEffect(Unit) { requestNearbyAdsLocation() }

    // Real cross-merchant product search (item 190) -- closes docs/DESIGN_REFERENCES.md
    // Section 5 recommendation #1: bank-mfe has had "search across every merchant" since
    // 2026-07-20 (lib/shopping.ts's own doc comment), but Android's Shop tab never called
    // this real, pre-existing endpoint at all -- its own search box only ever filtered
    // merchants by name/category, never products. Opening a result constructs a minimal
    // ShoppingMerchantDto from the search row (merchantId/businessName only, matching
    // bank-mfe's own openSearchResult shortcut) rather than a second real merchant fetch.
    var productSearchInput by remember { mutableStateOf("") }
    var productSearchResults by remember { mutableStateOf<List<ProductSearchResultDto>?>(null) }
    var productSearching by remember { mutableStateOf(false) }

    fun searchProducts() {
        val q = productSearchInput.trim()
        if (q.isEmpty()) return
        productSearching = true
        coroutineScope.launch {
            try {
                productSearchResults = NetworkClient.apiService.searchProducts(q).products
            } catch (e: Exception) {
                productSearchResults = emptyList()
            } finally {
                productSearching = false
            }
        }
    }

    fun loadFavoriteProductIds() {
        loadShopFavoriteProductIds(coroutineScope) { favoriteProductIds = it }
    }
    LaunchedEffect(Unit) { loadFavoriteProductIds() }

    fun toggleProductFavorite(productId: String) {
        toggleShopProductFavorite(
            productId = productId,
            favoriteProductIds = favoriteProductIds,
            coroutineScope = coroutineScope,
            onBusyIdChanged = { favoritingProductId = it },
            onFavoriteIdsChanged = { favoriteProductIds = it },
            onError = { error = it },
        )
    }

    fun loadFollowedMerchantIds() {
        loadShopFollowedMerchantIds(coroutineScope) { followedMerchantIds = it }
    }
    LaunchedEffect(Unit) { loadFollowedMerchantIds() }

    fun toggleFollow(merchantId: String) {
        toggleShopMerchantFollow(
            merchantId = merchantId,
            followedMerchantIds = followedMerchantIds,
            coroutineScope = coroutineScope,
            onBusyIdChanged = { followBusyMerchantId = it },
            onFollowedIdsChanged = { followedMerchantIds = it },
            onError = { error = it },
        )
    }

    // Real rating/distance/ETA enrichment (2026-08-14) -- see StoreCard's own doc
    // comment. Deliberately checks permission first rather than reusing
    // requestNearbyAdsLocation's own unconditional call (which does auto-prompt) --
    // matches MarketplaceContent's/EatsContent's own careful "opt-in, never assumed"
    // discipline: never trigger a permission prompt on a screen the user didn't ask
    // location-based content from.
    var storeBrowseLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val requestStoreBrowseLocation = rememberRealLocationRequester(
        onLocating = {},
        onSuccess = { lat, lng -> storeBrowseLocation = lat to lng },
        onError = {},
    )
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(recentlyViewedContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) requestStoreBrowseLocation()
    }

    fun loadMerchants() {
        coroutineScope.launch {
            try {
                // Real fix (2026-08-25, direct user directive: "we need everything
                // separated to avoid confusion, that's toss style, clear isolation").
                // The 2026-08-13 fix that added businessType filtered Eats' own
                // restaurant list down to real restaurants (a live user report caught
                // Electronics/Fashion merchants leaking into it) but deliberately left
                // Shop's own browse unfiltered "same as before this param existed" --
                // meaning the exact same leak ran the other direction, unnoticed,
                // ever since: real restaurants (e.g. "Review Reply Diner") showing up
                // in what's supposed to be itunda's pure online-shopping catalog, a
                // live device screenshot just confirmed. Shop is a real online
                // catalog (browse/cart/rider-delivery) -- Eats is a separate real
                // surface for restaurants; they share one Merchant directory on the
                // backend but should never share a screen. Mirrors EatsScreen.kt's
                // own identical businessType = "RESTAURANT" fix, one line different.
                val res = NetworkClient.apiService.getShoppingMerchants(
                    category = selectedCategory, businessType = "SHOP", q = searchInput.trim().ifBlank { null },
                    buyerLat = storeBrowseLocation?.first, buyerLng = storeBrowseLocation?.second,
                )
                if (res.success) merchants = res.merchants
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) {
        loadMerchants()
        try {
            val catRes = NetworkClient.apiService.getMerchantCategories(businessType = "SHOP")
            if (catRes.success) categories = catRes.categories
        } catch (e: Exception) { /* non-critical, only backs the category chip row */ }
    }
    // Same debounced category/search filter as Eats' OrderFoodContent -- see
    // SearchAndCategoryChips's own doc comment for why this is now shared.
    LaunchedEffect(selectedCategory, searchInput) {
        delay(rw.itunda.core.network.SEARCH_DEBOUNCE_MS)
        loadMerchants()
    }
    // Re-fetch once a real location fix lands, so a browse that already rendered
    // (permission check + GPS fix both take real time) upgrades in place -- same
    // discipline EatsContent's own identical LaunchedEffect establishes.
    LaunchedEffect(storeBrowseLocation) { if (storeBrowseLocation != null) loadMerchants() }

    // Real Kakao Pay 정기결제/Toss 빌링키-style recurring merchant billing plans this
    // merchant itself has published -- see MerchantBillingService's own doc comment.
    // bank-mfe already has this; this is the first Android client.
    var billingPlans by remember { mutableStateOf<List<MerchantBillingPlanDto>>(emptyList()) }
    var mySubscriptions by remember { mutableStateOf<List<MerchantBillingSubscriptionDto>>(emptyList()) }

    fun loadBillingForMerchant(merchantId: String) {
        loadShopMerchantBilling(
            merchantId = merchantId,
            coroutineScope = coroutineScope,
            onBillingPlansLoaded = { billingPlans = it },
            onSubscriptionsLoaded = { mySubscriptions = it },
        )
    }

    // Real Coupang/Amazon-style "Buy it again" (2026-08-23) -- direct port of Eats'
    // own real "Reorder" (see EatsScreen.kt's handleReorder). Re-populates the
    // cross-merchant `cart` from a past order's still-active products and opens the
    // cart for review, same "review before a real-money action, not an instant
    // one-tap purchase" precedent Eats already established (a delivery address
    // could be stale, a price could have changed since) -- rather than a riskier
    // instant re-checkout. Commerce products never carry option groups (only Eats'
    // menu items do, see MerchantProductDto.optionGroups's own doc comment), so
    // unlike Eats this needs no "drop items that now require an option selection"
    // sanitization -- only "drop items that are no longer active."
    var reorderingOrderId by remember { mutableStateOf<String?>(null) }
    var reorderError by remember { mutableStateOf<String?>(null) }

    fun handleReorder(order: OrderDto) {
        performShopReorder(
            order = order,
            cart = cart,
            coroutineScope = coroutineScope,
            onReorderingIdChanged = { reorderingOrderId = it },
            onError = { reorderError = it },
            onCartOpened = { showCart = true },
        )
    }

    fun openMerchant(m: ShoppingMerchantDto) {
        selectedMerchant = m
        products = null
        billingPlans = emptyList()
        mySubscriptions = emptyList()
        loadBillingForMerchant(m.merchantId)
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMerchantProducts(m.merchantId)
                // Real filter (2026-08-25, direct user feedback: "booking... supposed
                // to be in itunda place not in itunda shopping") -- a product with a
                // real durationMinutes set is a real-time appointment at this
                // merchant's physical location, not a cart-able online good, so it no
                // longer shows in Shop's own catalog at all. Booking now lives in
                // itunda Place (MapsBooking.kt), reachable from the same real merchant
                // pinned on the map.
                if (res.success) products = res.products.filter { it.durationMinutes == null }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    ShopSellerContactPickerOverlay(contactingMerchant, onDismiss = { contactingMerchant = null }, onOpened = { conversationId -> contactingMerchant = null; onMessageSeller(conversationId) })

    if (ShopDetailDispatch(
            results = results,
            showCart = showCart,
            cart = cart,
            selectedMerchant = selectedMerchant,
            selectedProduct = selectedProduct,
            products = products,
            favoriteProductIds = favoriteProductIds,
            favoritingProductId = favoritingProductId,
            followedMerchantIds = followedMerchantIds,
            followBusyMerchantId = followBusyMerchantId,
            billingPlans = billingPlans,
            mySubscriptions = mySubscriptions,
            deviceStepUpHost = deviceStepUpHost,
            onResultsDone = {
                results = null; selectedMerchant = null; products = null
                showCart = false; view = CommerceView.ORDERS
            },
            onCartBack = { showCart = false },
            onCartOrderPlaced = { checkoutResults ->
                checkoutResults.filter { it.order != null }.forEach { r ->
                    cart.keys.filter { it.startsWith("${r.merchantId}:") }.forEach(cart::remove)
                }
                results = checkoutResults
            },
            onRecentlyViewedAdded = { entry -> recentlyViewed = recentlyViewedStore.add(entry) },
            onProductBack = { selectedProduct = null },
            onViewCartFromProduct = { selectedProduct = null; showCart = true },
            onToggleProductFavorite = ::toggleProductFavorite,
            onMerchantBack = { selectedMerchant = null },
            onViewCart = { showCart = true },
            onOpenProduct = { selectedProduct = it },
            onToggleFollow = ::toggleFollow,
            onBillingChanged = ::loadBillingForMerchant,
            onContactSeller = { contactingMerchant = it },
        )
    ) return

    val totalItems = cart.values.sumOf { it.quantity }
    // Real fix (2026-08-25, direct user directive: "implant that into our designs
    // and apply it across our ecosystems", following the account ledger's own
    // shipped version) -- see trackScrollPressedKey's own doc comment
    // (ScrollPressTracker.kt) for why plain Modifier.clickable can't show a
    // pressed state for a row a finger is dragging over, only a stationary tap.
    val shopListState = rememberLazyListState()
    val shopTouchedKey = remember { mutableStateOf<Any?>(null) }
    LazyColumn(
        state = shopListState,
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)
            .trackScrollPressedKey(shopListState, shopTouchedKey),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        item {
            // No TabHeader here (2026-07-24, was `TabHeader("Shop")`): the bottom
            // nav / ShopTab's own Shop/Eats row already establish where the user
            // is, same redundant-title fix as Hood. Flat category strip below,
            // same treatment as everywhere else.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                listOf(CommerceView.BROWSE to "Merchants", CommerceView.ORDERS to "My orders", CommerceView.WISHLIST to "Wishlist", CommerceView.SUBSCRIPTIONS to "Subscriptions", CommerceView.QUESTIONS to "My questions").forEach { (v, label) ->
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
        // Real merchant-side Commerce order fulfillment queue (item 234) -- see
        // ApiService.getMerchantOrders's own doc comment. Shown above every sub-tab,
        // same placement as bank-mfe's own MerchantOrdersView/MerchantReturnQueueView
        // (self-hides for a buyer-only account -- MERCHANT_NOT_FOUND is a real, expected,
        // silent case, not an error).
        item { MerchantOrdersView() }
        item { MerchantReturnQueueView() }
        if (view == CommerceView.ORDERS) {
            item {
                MyCommerceOrdersView(onReorder = ::handleReorder, reorderingId = reorderingOrderId)
                val reorderErr = reorderError
                if (reorderErr != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(reorderErr, color = Ids.colors.danger, fontSize = 13.sp)
                }
            }
            // MyBookingsView moved to itunda Place (2026-08-25) -- see MapsBooking.kt's
            // own doc comment. "My orders" only shows real cart-able orders now.
        } else if (view == CommerceView.WISHLIST) {
            item { ProductWishlistView(onRemoved = ::loadFavoriteProductIds) }
        } else if (view == CommerceView.SUBSCRIPTIONS) {
            item { MyProductSubscriptionsView() }
        } else if (view == CommerceView.QUESTIONS) {
            item { MyProductInquiriesView() }
        } else {
            // Real Toss Shopping landing surface (2026-08-12, direct user screenshot):
            // banner carousel, then "Get points and coupons" mission row, then a
            // "Recommended for you" 2-column grid -- all three real, backend-connected,
            // shown only on the unfiltered landing state (same discipline the Deals/
            // Time Deals/Nearby/Recently-viewed rails below already established).
            if (selectedCategory == null && searchInput.isBlank() && banners.isNotEmpty()) {
                item { ShoppingBannerCarousel(banners) }
            }
            if (selectedCategory == null && searchInput.isBlank() && missions.isNotEmpty()) {
                item {
                    ShoppingPointsRow(
                        missions = missions,
                        spinOutcomes = spinOutcomes,
                        busyType = missionBusyType,
                        onComplete = ::completeMission,
                    )
                }
            }
            missionFeedback?.let { feedback ->
                item {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { missionFeedback = null },
                        title = { Text("Nice!") },
                        text = { Text(feedback) },
                        confirmButton = {
                            TextButton(onClick = { missionFeedback = null }) { Text("OK") }
                        },
                    )
                }
            }
            if (selectedCategory == null && searchInput.isBlank() && !deals.isNullOrEmpty()) {
                item {
                    Text("Recommended for you", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                item {
                    RecommendedForYouGrid(
                        deals = deals!!,
                        favoriteProductIds = favoriteProductIds,
                        onToggleFavorite = ::toggleProductFavorite,
                        onOpen = { d -> openMerchant(ShoppingMerchantDto(merchantId = d.merchantId, businessName = d.merchantName, category = null, cashbackRate = "1%")) },
                    )
                }
            }
            // Real fix (2026-08-25, direct user follow-up: "why do we have pay in
            // there?" -- the same "everything separated ... clear isolation"
            // directive this whole thread has been applying to Shop, just caught in
            // a different spot). PayAMerchantSection (Face Pay/Scan QR/Pay by code)
            // is real, in-person merchant payment -- it already has its own real
            // home, the Pay tab (ItundaAppScreen.kt, PayTab -- see
            // PayAMerchantSection's own doc comment in ShopPay.kt for why it lives
            // in :features:shop:impl rather than being duplicated there, a reuse
            // decision, not a rendering decision). This call rendered the exact
            // same section a SECOND time, unconditionally, at the top of Shop's own
            // online-catalog browse screen -- online shopping and in-person QR
            // payment are two different real Toss/Coupang products, not one screen.
            if (membershipDay?.isMembershipDay == true) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                            .background(Ids.colors.surface).padding(14.dp),
                    ) {
                        Text(
                            "🎉 Membership Day -- ${membershipDay!!.multiplier}x cashback today",
                            color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                        Text(
                            "Every purchase you make today earns ${membershipDay!!.multiplier}x the usual cashback.",
                            color = Ids.colors.textSecondary, fontSize = 12.sp,
                        )
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IdsTextField(
                        value = productSearchInput,
                        onValueChange = { productSearchInput = it },
                        label = "Search products across every merchant",
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(10.dp))
                            .background(if (productSearching || productSearchInput.isBlank()) Ids.colors.textTertiary else Ids.colors.brand)
                            .pressScaleClickable(enabled = !productSearching && productSearchInput.isNotBlank()) { searchProducts() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (productSearching) "…" else "Search", color = Color.White, fontWeight = FontWeight.Bold) }
                    if (productSearchResults != null) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.textTertiary)
                                .pressScaleClickable { productSearchResults = null; productSearchInput = "" }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Clear", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            val searchResults = productSearchResults
            if (searchResults != null) {
                if (searchResults.isEmpty()) {
                    item { EmptyState("No products matched \"$productSearchInput\".", icon = Icons.Outlined.ShoppingCart) }
                } else {
                    // Real fix (2026-08-24, flat-design sweep): dropped the per-row
                    // Card -- a product-browse list a user picks from, no divider,
                    // matching GroupAccountScreen's precedent (docs/UI_UX_GUIDELINES.md
                    // §10).
                    items(searchResults, key = { it.id }) { r ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                                .pressScaleClickable(isScrollTouched = shopTouchedKey.value == r.id) { openMerchant(ShoppingMerchantDto(merchantId = r.merchantId, businessName = r.merchantName, category = null, cashbackRate = "1%")) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ProductImageThumb(r.imageUrl, size = 48.dp, corner = 10.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(r.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(r.merchantName, color = Ids.colors.textSecondary, fontSize = 13.sp)
                                    Text(r.stockQuantity?.let { if (it == 0) "Out of stock" else "$it available" } ?: "Available", color = if (r.stockQuantity == 0) Ids.colors.danger else Ids.colors.textSecondary, fontSize = 11.sp)
                                    if (r.isBestSeller) ShopBestSellerBadge()
                                }
                            }
                            Text(String.format(Locale.US, "%,.0f RWF", r.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
                return@LazyColumn
            }
            // Real 당근(Karrot) 반경 타기팅-style nearby ads rail -- only shown on the
            // unfiltered landing state, same discipline the Deals rail below follows.
            // Tapping one opens that merchant's real catalog, same minimal-
            // ShoppingMerchantDto shortcut the Deals rail below already uses.
            if (selectedCategory == null && searchInput.isBlank() && nearbyAds.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📍 Near you", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            nearbyAds.forEach { a ->
                                Column(
                                    modifier = Modifier.width(160.dp).clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface)
                                        .pressScaleClickable { openMerchant(ShoppingMerchantDto(merchantId = a.ad.merchantId, businessName = a.businessName, category = null, cashbackRate = "1%")) }
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(a.ad.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(a.businessName, color = Ids.colors.textSecondary, fontSize = 12.sp)
                                    a.ad.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 11.sp) }
                                    Text("%.1f km away".format(a.distanceKm), color = Ids.colors.brand, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            // Real "Recently viewed" rail (2026-08-10) -- Coupang/Naver/Toss/Kakao
            // Shopping all show this on the landing surface; itunda had none. Same
            // "hidden once the user starts filtering" discipline as the Deals rail
            // below. Tapping a card jumps back to the real merchant (same shortcut
            // the Deals/Time Deals rails use) rather than reopening a possibly-stale
            // cached product snapshot.
            if (selectedCategory == null && searchInput.isBlank() && recentlyViewed.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ClockGlyph(size = 16.dp)
                            Text("Recently viewed", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            recentlyViewed.forEach { rv ->
                                Column(
                                    modifier = Modifier.width(120.dp).clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface)
                                        .pressScaleClickable { openMerchant(ShoppingMerchantDto(merchantId = rv.merchantId, businessName = rv.merchantName, category = null, cashbackRate = "1%")) }
                                        .padding(10.dp),
                                ) {
                                    ProductImageThumb(rv.imageUrl, size = 96.dp, corner = 10.dp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(rv.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2)
                                    val discountPercent = rv.discountPercent
                                    if (discountPercent != null && discountPercent > 0) {
                                        Text("$discountPercent% off", color = Ids.colors.danger, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Text(String.format(Locale.US, "%,.0f RWF", rv.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
            // Real "Deals" rail (2026-07-25) -- only shown on the unfiltered landing
            // state, same "merchandising above the raw list, hidden once the user
            // starts filtering" discipline a real Coupang/Naver home surface follows.
            // Tapping a deal jumps straight to that real merchant via the same minimal-
            // ShoppingMerchantDto shortcut the product search results above now use
            // (previously fell back to a merchant-name text search -- this closes that
            // same gap for the same reason).
            if (selectedCategory == null && searchInput.isBlank() && !deals.isNullOrEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FlameGlyph(size = 17.dp)
                            Text("Deals", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            deals!!.forEach { d ->
                                Column(
                                    modifier = Modifier.width(120.dp).clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface)
                                        .pressScaleClickable { openMerchant(ShoppingMerchantDto(merchantId = d.merchantId, businessName = d.merchantName, category = null, cashbackRate = "1%")) }
                                        .padding(10.dp),
                                ) {
                                    ProductImageThumb(d.imageUrl, size = 96.dp, corner = 10.dp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(d.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2)
                                    val discountPercent = d.discountPercent
                                    if (discountPercent != null && discountPercent > 0) {
                                        Text("$discountPercent% off", color = Ids.colors.danger, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Text(String.format(Locale.US, "%,.0f RWF", d.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(d.stockQuantity?.let { if (it == 0) "Out of stock" else "$it available" } ?: "Available", color = if (d.stockQuantity == 0) Ids.colors.danger else Ids.colors.textSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            // Real Coupang 타임특가 (Time Deal, item 226) -- see TimeDealDto's own doc
            // comment. Tapping a deal jumps straight to that real merchant, same
            // minimal-ShoppingMerchantDto shortcut the Deals rail above already uses.
            if (selectedCategory == null && searchInput.isBlank() && !timeDeals.isNullOrEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("⏰ Time Deals", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            timeDeals!!.forEach { v ->
                                Column(
                                    modifier = Modifier.width(120.dp).clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface)
                                        .pressScaleClickable { openMerchant(ShoppingMerchantDto(merchantId = v.deal.merchantId, businessName = v.businessName, category = null, cashbackRate = "1%")) }
                                        .padding(10.dp),
                                ) {
                                    Box {
                                        ProductImageThumb(v.productImageUrl, size = 96.dp, corner = 10.dp)
                                        val discountPercent = if (v.deal.originalPrice > 0) {
                                            (100 - (v.deal.dealPrice / v.deal.originalPrice * 100)).roundToInt()
                                        } else 0
                                        if (discountPercent > 0) {
                                            StatusBadge(
                                                "$discountPercent% OFF",
                                                tint = Ids.colors.danger,
                                                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(v.productName, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2)
                                    Text(String.format(Locale.US, "%,.0f RWF", v.deal.dealPrice), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        StatusBadge(formatTimeDealCountdown(v.deal.endsAt), filled = false, tint = Ids.colors.brand)
                                        if (v.deal.remainingQuantity <= 3) {
                                            StatusBadge("${v.deal.remainingQuantity} left", tint = Ids.colors.danger)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SearchAndCategoryChips(
                    searchInput = searchInput,
                    onSearchChange = { searchInput = it },
                    placeholder = "Search merchants",
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onSelectCategory = { c -> selectedCategory = if (c == selectedCategory) null else c },
                )
            }
            if (error != null) {
                item { ErrorCard(error!!, onRetry = ::loadMerchants) }
            } else if (merchants == null) {
                item { SkeletonBlock() }
            } else if (merchants!!.isEmpty()) {
                item {
                    EmptyState(
                        // Real copy-voice fix (item 244, round 5 of the empty-state pass):
                        // "registered yet" is honest about whose gap this is -- no store
                        // has joined yet, not something the reader is missing a step on,
                        // matching docs/COPY_VOICE.md's "when the cause is someone else's,
                        // say so honestly" rule.
                        if (selectedCategory != null || searchInput.isNotBlank()) "No merchants match your search — try a different category or search term." else "No stores registered yet — check back once merchants in your area join itunda Shop.",
                        icon = Icons.Outlined.Storefront,
                    )
                }
            } else {
                items(merchants!!, key = { it.merchantId }) { m ->
                    StoreCard(m, onOpen = { openMerchant(m) }, isScrollTouched = shopTouchedKey.value == m.merchantId)
                }
            }
        }
        if (view == CommerceView.BROWSE && totalItems > 0) {
            item { Spacer(modifier = Modifier.height(64.dp)) }
        }
    }
    if (view == CommerceView.BROWSE && totalItems > 0) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
            CartFab(totalItems, onClick = { showCart = true })
        }
    }
}

