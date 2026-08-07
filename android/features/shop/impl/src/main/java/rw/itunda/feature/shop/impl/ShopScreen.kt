package rw.itunda.feature.shop.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.StatusBadge
import kotlin.math.roundToInt
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.IdsTextField
import rw.itunda.core.designsystem.components.QtyButton
import rw.itunda.core.designsystem.components.rememberRealLocationRequester
import rw.itunda.core.designsystem.components.SearchAndCategoryChips
import rw.itunda.core.designsystem.components.SimpleLiveRiderMiniMap
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.StarRatingRow
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.BookingSlotDto
import rw.itunda.core.network.CreateBookingRequest
import rw.itunda.core.network.CreateProductSubscriptionRequest
import rw.itunda.core.network.DealProductDto
import rw.itunda.core.network.TimeDealViewDto
import rw.itunda.core.network.MembershipDayStatusResponse
import rw.itunda.core.network.ProductSubscriptionDto
import rw.itunda.core.network.ProductSearchResultDto
import rw.itunda.core.network.FavoriteProductDto
import rw.itunda.core.network.MerchantBookingDto
import rw.itunda.core.network.MerchantBookingRatingDto
import rw.itunda.core.network.MerchantBookingReviewDto
import rw.itunda.core.network.SubmitBookingReviewRequest
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.CreateAffiliateLinkRequest
import rw.itunda.core.network.DecideOrderReturnRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.ORDER_RETURN_REASON_CODES
import rw.itunda.core.network.OrderDto
import rw.itunda.core.network.OrderItemDto
import rw.itunda.core.network.OrderItemRequest
import rw.itunda.core.network.OrderReturnRequestDto
import rw.itunda.core.network.PlaceOrderRequest
import rw.itunda.core.network.ProductRatingResponse
import rw.itunda.core.network.AskProductInquiryRequest
import rw.itunda.core.network.MerchantBillingPlanDto
import rw.itunda.core.network.MerchantBillingSubscriptionDto
import rw.itunda.core.network.NearbyMerchantAdDto
import rw.itunda.core.network.ProductInquiryDto
import rw.itunda.core.network.ProductReviewDto
import rw.itunda.core.network.CollectPaymentRequest
import rw.itunda.core.network.CollectPaymentResultDto
import rw.itunda.core.network.MerchantCouponPreviewDto
import rw.itunda.core.network.MerchantCouponViewDto
import rw.itunda.core.network.PaymentIntentPreviewResponse
import rw.itunda.core.network.RequestOrderReturnRequest
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.StaticQrPayRequest
import rw.itunda.core.network.SubmitProductReviewRequest
import rw.itunda.core.network.UpdateOrderStatusRequest
import rw.itunda.core.network.isDeviceNotVerifiedError
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException
import java.util.UUID

// Fifth Feature extraction (2026-07-23) after the four Hood-mode modules, same template
// -- see features/marketplace/impl/.../MarketplaceScreen.kt's own header comment for the
// full account. Two things this module needed that Hood didn't:
//
// - StarGold/StarRatingRow, shared with Eats (still in :app), promoted to
//   core/designsystem/components/HoodShared.kt same as everything else.
// - deviceStepUpHost: unlike routeMiniMap (out of scope because of MapLibre), this one
//   is out of scope for architectural reasons -- DeviceStepUpHost.kt's own doc comment
//   explains it wraps :features:payments:impl's DeviceStepUpDialog, so it bridges two
//   Feature modules. Toss's real architecture explicitly forbids sideways
//   Feature-to-Feature dependencies (that's the entire reason the Interface/DI-container
//   split exists) -- so this stays an app-level composition, injected here exactly like
//   routeMiniMap, rather than pulled into this module.

private enum class CommerceView { BROWSE, ORDERS, WISHLIST, SUBSCRIPTIONS, QUESTIONS }

// Real cross-merchant cart (2026-07-20) -- closes the "real Coupang splits a
// multi-seller cart into per-seller orders, not attempted here" simplification the
// matrix named. Flattened (not nested maps) so a plain SnapshotStateMap keyed by
// "merchantId:productId" works cleanly with Compose recomposition -- each entry
// carries its own merchant/product context, so grouping-by-merchant at checkout
// time is a plain in-memory groupBy, no separate lookup needed.
private data class CommerceCartLine(val merchantId: String, val businessName: String, val product: MerchantProductDto, val quantity: Int)
private data class CommerceCheckoutResult(val merchantId: String, val businessName: String, val order: OrderDto?, val error: String?)

// Real Coupang 타임특가 (Time Deal, item 226) countdown -- mirrors bank-mfe's own
// formatDealCountdown exactly.
private fun formatTimeDealCountdown(endsAt: String): String {
    val msLeft = java.time.Instant.parse(endsAt).toEpochMilli() - java.time.Instant.now().toEpochMilli()
    if (msLeft <= 0) return "Ending soon"
    val totalMinutes = msLeft / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m left" else "${minutes}m left"
}

@Composable
fun CommerceShopContent(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
) {
    var view by remember { mutableStateOf(CommerceView.BROWSE) }
    var merchants by remember { mutableStateOf<List<ShoppingMerchantDto>?>(null) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchInput by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedMerchant by remember { mutableStateOf<ShoppingMerchantDto?>(null) }
    var products by remember { mutableStateOf<List<MerchantProductDto>?>(null) }
    var selectedProduct by remember { mutableStateOf<MerchantProductDto?>(null) }
    var bookingService by remember { mutableStateOf<MerchantProductDto?>(null) }
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
    var deals by remember { mutableStateOf<List<DealProductDto>?>(null) }
    LaunchedEffect(Unit) {
        try {
            val res = NetworkClient.apiService.getShopDeals()
            if (res.success) deals = res.products
        } catch (e: Exception) {
            // Real, non-critical -- the Deals rail just won't render if this fails.
        }
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
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteProducts()
                if (res.success) favoriteProductIds = res.favorites.map { it.productId }.toSet()
            } catch (e: Exception) {
                // Best-effort -- hearts just won't render as filled if this fails.
            }
        }
    }
    LaunchedEffect(Unit) { loadFavoriteProductIds() }

    fun toggleProductFavorite(productId: String) {
        favoritingProductId = productId
        coroutineScope.launch {
            try {
                if (productId in favoriteProductIds) {
                    NetworkClient.apiService.removeProductFavorite(productId)
                    favoriteProductIds = favoriteProductIds - productId
                } else {
                    NetworkClient.apiService.addProductFavorite(productId)
                    favoriteProductIds = favoriteProductIds + productId
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                favoritingProductId = null
            }
        }
    }

    fun loadFollowedMerchantIds() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFollowedMerchants()
                if (res.success) followedMerchantIds = res.follows.map { it.merchantId }.toSet()
            } catch (e: Exception) {
                // Best-effort -- see loadFavoriteProductIds's own doc comment above.
            }
        }
    }
    LaunchedEffect(Unit) { loadFollowedMerchantIds() }

    fun toggleFollow(merchantId: String) {
        followBusyMerchantId = merchantId
        coroutineScope.launch {
            try {
                if (merchantId in followedMerchantIds) {
                    NetworkClient.apiService.unfollowMerchant(merchantId)
                    followedMerchantIds = followedMerchantIds - merchantId
                } else {
                    NetworkClient.apiService.followMerchant(merchantId)
                    followedMerchantIds = followedMerchantIds + merchantId
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                followBusyMerchantId = null
            }
        }
    }

    fun loadMerchants() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getShoppingMerchants(selectedCategory, searchInput.trim().ifBlank { null })
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
            val catRes = NetworkClient.apiService.getMerchantCategories()
            if (catRes.success) categories = catRes.categories
        } catch (e: Exception) { /* non-critical, only backs the category chip row */ }
    }
    // Same debounced category/search filter as Eats' OrderFoodContent -- see
    // SearchAndCategoryChips's own doc comment for why this is now shared.
    LaunchedEffect(selectedCategory, searchInput) {
        delay(300)
        loadMerchants()
    }

    // Real Kakao Pay 정기결제/Toss 빌링키-style recurring merchant billing plans this
    // merchant itself has published -- see MerchantBillingService's own doc comment.
    // bank-mfe already has this; this is the first Android client.
    var billingPlans by remember { mutableStateOf<List<MerchantBillingPlanDto>>(emptyList()) }
    var mySubscriptions by remember { mutableStateOf<List<MerchantBillingSubscriptionDto>>(emptyList()) }

    fun loadBillingForMerchant(merchantId: String) {
        coroutineScope.launch {
            try {
                billingPlans = NetworkClient.apiService.getMerchantBillingPlans(merchantId).plans
            } catch (e: Exception) {
                // Real, non-critical -- same discipline as follow/wishlist status.
            }
        }
        coroutineScope.launch {
            try {
                mySubscriptions = NetworkClient.apiService.getMyBillingSubscriptions().subscriptions.filter { it.merchantId == merchantId }
            } catch (e: Exception) {
                // Real, non-critical -- same discipline as follow/wishlist status.
            }
        }
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
                if (res.success) products = res.products
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }

    val currentResults = results
    if (currentResults != null) {
        MultiCartResultsView(currentResults, onDone = {
            results = null
            selectedMerchant = null
            products = null
            showCart = false
            view = CommerceView.ORDERS
        })
        return
    }

    if (showCart) {
        MultiCartView(
            cart = cart,
            onBack = { showCart = false },
            onOrderPlaced = { checkoutResults ->
                checkoutResults.filter { it.order != null }.forEach { r ->
                    cart.keys.filter { it.startsWith("${r.merchantId}:") }.forEach(cart::remove)
                }
                results = checkoutResults
            },
            deviceStepUpHost = deviceStepUpHost,
        )
        return
    }

    val merchant = selectedMerchant
    val bookableService = bookingService
    if (merchant != null && bookableService != null) {
        MerchantBookingFlowView(
            merchant = merchant,
            service = bookableService,
            onBack = { bookingService = null },
            onBooked = { bookingService = null },
        )
        return
    }
    val product = selectedProduct
    if (merchant != null && product != null) {
        ProductDetailScreen(
            merchant = merchant,
            product = product,
            cart = cart,
            onBack = { selectedProduct = null },
            onViewCart = { selectedProduct = null; showCart = true },
            favorited = product.id in favoriteProductIds,
            favoriteBusy = favoritingProductId == product.id,
            onToggleFavorite = { toggleProductFavorite(product.id) },
        )
        return
    }
    if (merchant != null) {
        MerchantDetailView(
            merchant = merchant,
            products = products,
            cart = cart,
            onBack = { selectedMerchant = null },
            onViewCart = { showCart = true },
            onOpenProduct = { selectedProduct = it },
            onBookService = { bookingService = it },
            favoriteProductIds = favoriteProductIds,
            favoritingProductId = favoritingProductId,
            onToggleFavorite = ::toggleProductFavorite,
            following = merchant.merchantId in followedMerchantIds,
            followBusy = followBusyMerchantId == merchant.merchantId,
            onToggleFollow = { toggleFollow(merchant.merchantId) },
            billingPlans = billingPlans,
            mySubscriptions = mySubscriptions,
            onBillingChanged = { loadBillingForMerchant(merchant.merchantId) },
        )
        return
    }

    val totalItems = cart.values.sumOf { it.quantity }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
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
                listOf(CommerceView.BROWSE to "Merchants", CommerceView.ORDERS to "My orders", CommerceView.WISHLIST to "♡ Wishlist", CommerceView.SUBSCRIPTIONS to "Subscriptions", CommerceView.QUESTIONS to "My questions").forEach { (v, label) ->
                    val selected = v == view
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { view = v }) {
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
            item { MyCommerceOrdersView() }
            item { MyBookingsView() }
        } else if (view == CommerceView.WISHLIST) {
            item { ProductWishlistView(onRemoved = ::loadFavoriteProductIds) }
        } else if (view == CommerceView.SUBSCRIPTIONS) {
            item { MyProductSubscriptionsView() }
        } else if (view == CommerceView.QUESTIONS) {
            item { MyProductInquiriesView() }
        } else {
            item {
                PayAMerchantSection(deviceStepUpHost = deviceStepUpHost)
            }
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
                            .clickable(enabled = !productSearching && productSearchInput.isNotBlank()) { searchProducts() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (productSearching) "…" else "Search", color = Color.White, fontWeight = FontWeight.Bold) }
                    if (productSearchResults != null) {
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Ids.colors.textTertiary)
                                .clickable { productSearchResults = null; productSearchInput = "" }
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
                    items(searchResults, key = { it.id }) { r ->
                        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                                    .clickable { openMerchant(ShoppingMerchantDto(merchantId = r.merchantId, businessName = r.merchantName, category = null, cashbackRate = "1%")) },
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
                                    }
                                }
                                Text("%,.0f RWF".format(r.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
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
                                        .clickable { openMerchant(ShoppingMerchantDto(merchantId = a.ad.merchantId, businessName = a.businessName, category = null, cashbackRate = "1%")) }
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
                        Text("🔥 Deals", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            deals!!.forEach { d ->
                                Column(
                                    modifier = Modifier.width(120.dp).clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface)
                                        .clickable { openMerchant(ShoppingMerchantDto(merchantId = d.merchantId, businessName = d.merchantName, category = null, cashbackRate = "1%")) }
                                        .padding(10.dp),
                                ) {
                                    ProductImageThumb(d.imageUrl, size = 96.dp, corner = 10.dp)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(d.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 2)
                                    val discountPercent = d.discountPercent
                                    if (discountPercent != null && discountPercent > 0) {
                                        Text("$discountPercent% off", color = Ids.colors.danger, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Text("%,.0f RWF".format(d.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                        .clickable { openMerchant(ShoppingMerchantDto(merchantId = v.deal.merchantId, businessName = v.businessName, category = null, cashbackRate = "1%")) }
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
                                    Text("%,.0f RWF".format(v.deal.dealPrice), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                items(merchants!!, key = { it.merchantId }) { m -> StoreCard(m, onOpen = { openMerchant(m) }) }
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

@Composable
private fun CartFab(totalItems: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Ids.colors.brand).clickable(onClick = onClick).padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("View cart ($totalItems item${if (totalItems == 1) "" else "s"})", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

// Real Coupang-style photo-forward store card (2026-07-24) -- same treatment as the
// Eats restaurant card and Marketplace's ListingCard: a real merchant-set photo leads,
// same ShoppingMerchantDto.photoUrl field Eats already uses (this endpoint and Eats'
// share the same DTO), so no backend change was needed here.
@Composable
private fun StoreCard(m: ShoppingMerchantDto, onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Ids.layout.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = Ids.colors.surface),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = Ids.layout.cardCornerRadius, topEnd = Ids.layout.cardCornerRadius))) {
                if (m.photoUrl != null) {
                    SubcomposeAsyncImage(
                        model = m.photoUrl,
                        contentDescription = m.businessName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        when (painter.state) {
                            is coil.compose.AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                            else -> StorePhotoPlaceholder()
                        }
                    }
                } else {
                    StorePhotoPlaceholder()
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(m.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    listOfNotNull(m.category, "${m.cashbackRate} cashback").joinToString(" · "),
                    color = Ids.colors.textSecondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun StorePhotoPlaceholder() {
    Box(modifier = Modifier.fillMaxSize().background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Storefront, contentDescription = null, tint = Ids.colors.textTertiary, modifier = Modifier.size(40.dp))
    }
}

// Real product-image thumbnail (2026-07-21) -- imageUrl is a merchant-supplied external
// URL (see backend MerchantProduct.kt's own doc comment: no upload/storage layer exists
// in this backend, so this is a real "bring your own URL" v1, not a fake pipeline). Coil
// handles the null/broken-URL case itself (falls through to `error`), same fallback icon
// shown for a product that simply has no image set at all -- both are real, valid states.
@Composable
private fun ProductImageThumb(imageUrl: String?, size: Dp = 44.dp, corner: Dp = 14.dp) {
    if (imageUrl.isNullOrBlank()) {
        Box(modifier = Modifier.size(size).clip(RoundedCornerShape(corner)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ShoppingBag, contentDescription = null, modifier = Modifier.size(size / 2), tint = Ids.colors.brand)
        }
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(corner)).background(Ids.colors.surfaceSoft),
        )
    }
}

// Real discount-price display (2026-07-21) -- Baymard Institute's own placement research
// (docs/DESIGN_REFERENCES.md Section 5): the discount % must sit immediately next to the
// struck-through original price, not elsewhere on the card. discountPercent is always
// server-computed (see backend doc comment), never trusted from the client, so this is
// purely a rendering of numbers the server already validated.
@Composable
private fun ProductPriceRow(p: MerchantProductDto) {
    val discountPercent = p.discountPercent
    if (p.originalPrice != null && discountPercent != null && discountPercent > 0) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$discountPercent%",
                color = Ids.colors.danger,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("%,.0f RWF".format(p.price), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text(
            "%,.0f RWF".format(p.originalPrice),
            color = Ids.colors.textSecondary,
            fontSize = 11.sp,
            textDecoration = TextDecoration.LineThrough,
        )
    } else {
        Text("%,.0f RWF".format(p.price), color = Ids.colors.textSecondary, fontSize = 13.sp)
    }
    // Real bulk/wholesale pricing (2026-07-25) -- closes the gap named in Baemin's own
    // real 배민상회 B2B supplies marketplace research. Shows the best (highest-quantity)
    // real tier as a hint; the actual price used at checkout is always resolved
    // server-side from the real ordered quantity, never trusted from this display.
    p.priceTiers.maxByOrNull { it.minQuantity }?.let { bestTier ->
        Text(
            "Buy ${bestTier.minQuantity}+ for %,.0f RWF each".format(bestTier.unitPrice),
            color = Ids.colors.success, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun MerchantDetailView(
    merchant: ShoppingMerchantDto,
    products: List<MerchantProductDto>?,
    cart: SnapshotStateMap<String, CommerceCartLine>,
    onBack: () -> Unit,
    onViewCart: () -> Unit,
    onOpenProduct: (MerchantProductDto) -> Unit,
    onBookService: (MerchantProductDto) -> Unit = {},
    favoriteProductIds: Set<String> = emptySet(),
    favoritingProductId: String? = null,
    onToggleFavorite: (String) -> Unit = {},
    following: Boolean = false,
    followBusy: Boolean = false,
    onToggleFollow: () -> Unit = {},
    billingPlans: List<MerchantBillingPlanDto> = emptyList(),
    mySubscriptions: List<MerchantBillingSubscriptionDto> = emptyList(),
    onBillingChanged: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val totalItems = cart.values.sumOf { it.quantity }
    // Real 쿠팡파트너스 (Coupang Partners)-style affiliate link generation (item 229)
    // -- see AffiliateLinkDto's own doc comment. bank-mfe already has this; this is
    // the first Android client. Referral capture-at-checkout stays bank-mfe-only, a
    // named, honest v1 scope-down (no deep-link precedent exists on this app).
    val shareContext = LocalContext.current
    val shareScope = rememberCoroutineScope()
    var sharingProductId by remember { mutableStateOf<String?>(null) }
    fun shareProduct(productId: String) {
        sharingProductId = productId
        shareScope.launch {
            try {
                val link = NetworkClient.apiService.createAffiliateLink(CreateAffiliateLinkRequest(productId))
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, "Check this out on itunda! Use code ${link.link.code} — https://itunda.rw/shop?ref=${link.link.code}")
                }
                shareContext.startActivity(android.content.Intent.createChooser(intent, "Share & earn 3%"))
            } catch (e: Exception) {
                // Real, non-critical -- a share-link failure shouldn't block browsing.
            } finally {
                sharingProductId = null
            }
        }
    }
    fun qtyFor(productId: String) = cart["${merchant.merchantId}:$productId"]?.quantity ?: 0
    fun setQty(product: MerchantProductDto, qty: Int) {
        val key = "${merchant.merchantId}:${product.id}"
        val stock = product.stockQuantity
        if (qty <= 0) cart.remove(key)
        else if (stock == null || qty <= stock) {
            cart[key] = CommerceCartLine(merchant.merchantId, merchant.businessName, product, qty)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) { BackTopBar(merchant.businessName, onBack) }
            // Real Naver Smart Store-style "알림받기" follow toggle -- see this
            // function's own doc comment above (item 117).
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (following) Ids.colors.surface else Ids.colors.brand)
                    .clickable(enabled = !followBusy, onClick = onToggleFollow)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    if (following) "Following" else "Follow",
                    color = if (following) Ids.colors.textPrimary else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
        if (products == null) {
            SkeletonBlock(height = 72.dp)
        } else if (products.isEmpty()) {
            EmptyState("This store hasn't added products yet — check back soon.", icon = Icons.Outlined.ShoppingBag)
        } else {
            // Real 2-column image-led grid (2026-07-21), replacing the previous
            // single-column text-only row -- closes docs/DESIGN_REFERENCES.md Section 5
            // recommendation #5 (Chloe Youn's Coupang case study: real cards are
            // image-led, not name+price text rows).
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                if (billingPlans.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Subscription plans", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            billingPlans.forEach { plan ->
                                BillingPlanRow(plan = plan, subscription = mySubscriptions.firstOrNull { it.planId == plan.id && it.status == "ACTIVE" }, onChanged = onBillingChanged)
                            }
                        }
                    }
                }
                gridItems(products, key = { it.id }) { p ->
                    val qty = qtyFor(p.id)
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(12.dp),
                    ) {
                        // Real tap-through to the dedicated product-detail screen
                        // (2026-07-21) -- see ProductDetailScreen's own doc comment.
                        // Only the image/name/price area is tappable so the qty
                        // stepper below stays independently clickable for quick
                        // add-to-cart.
                        Column(modifier = Modifier.clickable { onOpenProduct(p) }) {
                            Box {
                                ProductImageThumb(p.imageUrl, size = 96.dp, corner = 12.dp)
                                // Real Shop product wishlist heart (2026-07-24) --
                                // same top-end overlay treatment as Marketplace's
                                // ListingCard, closing docs/DESIGN_REFERENCES.md
                                // Section 5 recommendation #3.
                                val favorited = p.id in favoriteProductIds
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                                    Icon(
                                        if (favorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                        contentDescription = if (favorited) "Remove from wishlist" else "Add to wishlist",
                                        tint = if (favorited) Ids.colors.danger else Color.White,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable(enabled = favoritingProductId != p.id) { onToggleFavorite(p.id) },
                                    )
                                    Icon(
                                        Icons.Outlined.Share,
                                        contentDescription = "Share & earn 3%",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(18.dp)
                                            .clickable(enabled = sharingProductId != p.id) { shareProduct(p.id) },
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(p.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
                            Spacer(modifier = Modifier.height(2.dp))
                            ProductPriceRow(p)
                        }
                        Text(
                            p.stockQuantity?.let { if (it == 0) "Out of stock" else "$it available" } ?: "Available",
                            color = if (p.stockQuantity == 0) Ids.colors.danger else Ids.colors.textSecondary,
                            fontSize = 11.sp,
                        )
                        ProductRatingBadge(p.id)
                        Spacer(modifier = Modifier.height(8.dp))
                        // Real bookable-service entry point (2026-07-25) -- a product
                        // with a real durationMinutes set is an appointment, not a
                        // cart-able good, so it gets a "Book" action instead of the
                        // qty stepper. See MerchantBookingFlowView's own doc comment.
                        if (p.durationMinutes != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Ids.colors.brand)
                                    .clickable { onBookService(p) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text("Book", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        } else {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                QtyButton("-") { setQty(p, qty - 1) }
                                Text(qty.toString(), modifier = Modifier.width(28.dp), textAlign = TextAlign.Center, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                                QtyButton("+") { val stock = p.stockQuantity; if (stock == null || qty < stock) setQty(p, qty + 1) }
                            }
                        }
                    }
                }
            }
        }
        if (totalItems > 0) {
            CartFab(totalItems, onClick = onViewCart)
        }
    }
}

private val BOOKING_STATUS_LABEL = mapOf(
    "REQUESTED" to "Requested",
    "CONFIRMED" to "Confirmed",
    "DECLINED" to "Declined",
    "CANCELLED" to "Cancelled",
    "COMPLETED" to "Completed",
)

@Composable
private fun MyBookingsView() {
    var bookings by remember { mutableStateOf<List<MerchantBookingDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyBookings()
                if (res.success) bookings = res.bookings
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun cancel(bookingId: String) {
        cancellingId = bookingId
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.cancelBooking(bookingId)
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

    val list = bookings
    if (list.isNullOrEmpty() && error == null) return
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text("Bookings", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 10.dp))
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list!!.forEach { b ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(b.serviceName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(BOOKING_STATUS_LABEL[b.status] ?: b.status, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text("${b.bookingDate} at ${b.startTime.take(5)}", color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            if (b.status == "REQUESTED" || b.status == "CONFIRMED") {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 10.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Ids.colors.danger)
                                        .clickable(enabled = cancellingId != b.id) { cancel(b.id) }
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        if (cancellingId == b.id) "Cancelling…" else "Cancel booking",
                                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    )
                                }
                            }
                            if (b.status == "COMPLETED") {
                                BookingReviewButton(bookingId = b.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Real customer-side post-appointment review (item 143) -- see lib/booking.ts's own
// doc comment on bank-mfe. Mirrors ProductReviewRow's exact shape (star rating +
// optional comment, a real BOOKING_ALREADY_REVIEWED 409 is treated as already-done).
@Composable
private fun BookingReviewButton(bookingId: String) {
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("Thanks for your review!", color = Ids.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        return
    }
    if (!open) {
        Box(
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ids.colors.textTertiary)
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Rate this visit", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
        StarRatingRow(rating) { rating = it }
        IdsTextField(
            value = comment,
            onValueChange = { comment = it },
            label = "How was it? (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
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
                        if (rating == 0) {
                            error = "Pick a star rating."
                            return@clickable
                        }
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.submitBookingReview(bookingId, SubmitBookingReviewRequest(rating, comment.trim().ifBlank { null }))
                                done = true
                            } catch (e: HttpException) {
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

/**
 * Real local-business appointment booking (2026-07-25) -- closes the "business profile
 * + real booking" gap independently converged on by Naver Smart Place, Kakao Hair Shop,
 * and Karrot's Business Profile research (docs/DESIGN_REFERENCES.md). Date picker is a
 * plain next-14-days strip (no calendar widget -- itunda has no calendar-sync/external
 * scheduling to justify one); slots come straight from the real backend-computed
 * availability (see MerchantBookingService.getAvailableSlots), never client-guessed.
 */
@Composable
private fun MerchantBookingFlowView(
    merchant: ShoppingMerchantDto,
    service: MerchantProductDto,
    onBack: () -> Unit,
    onBooked: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val today = remember { java.time.LocalDate.now() }
    var selectedDate by remember { mutableStateOf(today) }
    var slots by remember { mutableStateOf<List<BookingSlotDto>?>(null) }
    var selectedSlot by remember { mutableStateOf<BookingSlotDto?>(null) }
    var notes by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var booked by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun loadSlots() {
        selectedSlot = null
        slots = null
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getBookingSlots(merchant.merchantId, service.id, selectedDate.toString())
                slots = if (res.success) res.slots else emptyList()
            } catch (e: Exception) {
                error = "Couldn't load available times."
                slots = emptyList()
            }
        }
    }
    LaunchedEffect(selectedDate) { loadSlots() }

    if (booked) {
        AlertDialog(
            onDismissRequest = onBooked,
            title = { Text("Booking requested") },
            text = { Text("${service.name} on $selectedDate at ${selectedSlot?.startTime?.take(5)} -- ${merchant.businessName} will confirm shortly.") },
            confirmButton = { TextButton(onClick = onBooked) { Text("Done") } },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar("Book ${service.name}", onBack)
        Text(
            "${service.name} · ${service.durationMinutes} min · %,.0f RWF".format(service.price),
            color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        MerchantBookingInfoSection(merchant.merchantId)
        Text("Choose a date", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (0 until 14).map { today.plusDays(it.toLong()) }.forEach { date ->
                val selected = date == selectedDate
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) Ids.colors.brand else Ids.colors.surface)
                        .clickable { selectedDate = date }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH),
                        color = if (selected) Color.White else Ids.colors.textSecondary, fontSize = 11.sp,
                    )
                    Text(date.dayOfMonth.toString(), color = if (selected) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
        Text("Choose a time", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Box(modifier = Modifier.weight(1f)) {
            val slotList = slots
            if (slotList == null) {
                SkeletonBlock()
            } else if (slotList.isEmpty()) {
                EmptyState("No open times on this date — try another day.", icon = Icons.Outlined.Storefront)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    gridItems(slotList) { slot ->
                        val selected = slot == selectedSlot
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selected) Ids.colors.brand else Ids.colors.surface)
                                .clickable { selectedSlot = slot }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(slot.startTime.take(5), color = if (selected) Color.White else Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        IdsTextField(
            value = notes,
            onValueChange = { if (it.length <= 500) notes = it },
            label = "Notes (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (submitting || selectedSlot == null) Ids.colors.textTertiary else Ids.colors.brand)
                .clickable(enabled = !submitting && selectedSlot != null) {
                    val slot = selectedSlot ?: return@clickable
                    submitting = true
                    error = null
                    coroutineScope.launch {
                        try {
                            val res = NetworkClient.apiService.createBooking(
                                CreateBookingRequest(merchant.merchantId, service.id, selectedDate.toString(), slot.startTime, notes.trim().ifBlank { null }),
                            )
                            if (res.success) booked = true
                        } catch (e: HttpException) {
                            error = superAppErrorMessage(e)
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            submitting = false
                        }
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (submitting) "Requesting…" else "Request booking", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

// Real pre-booking browsing (item 231) -- found via a defined-but-uncalled-endpoint
// sweep, see ApiService.getMerchantReviews/getCouponsForCustomer's own doc comments.
// bank-mfe shipped this first (2026-08-05); this is the Android port.
@Composable
private fun MerchantBookingInfoSection(merchantId: String) {
    var reviews by remember { mutableStateOf<List<MerchantBookingReviewDto>?>(null) }
    var rating by remember { mutableStateOf<MerchantBookingRatingDto?>(null) }
    var coupons by remember { mutableStateOf<List<MerchantCouponPreviewDto>?>(null) }

    LaunchedEffect(merchantId) {
        try {
            val res = NetworkClient.apiService.getMerchantReviews(merchantId)
            if (res.success) {
                reviews = res.reviews
                rating = res.rating
            }
        } catch (e: Exception) {
            reviews = emptyList()
        }
        try {
            val res = NetworkClient.apiService.getCouponsForCustomer(merchantId)
            if (res.success) coupons = res.coupons
        } catch (e: Exception) {
            coupons = emptyList()
        }
    }

    val couponList = coupons
    val reviewList = reviews
    if (couponList.isNullOrEmpty() && reviewList.isNullOrEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 12.dp)) {
        if (!couponList.isNullOrEmpty()) {
            Text("Coupons for you", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            couponList.forEach { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.brand.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(c.title, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        c.description?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 11.sp) }
                    }
                    Text(
                        if (c.discountType == "PERCENT") "${c.discountValue}% off" else "%,.0f RWF off".format(c.discountValue),
                        color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
            }
        }
        if (!reviewList.isNullOrEmpty()) {
            Text(
                "Reviews" + (rating?.average?.let { " · ⭐ %.1f (%d)".format(it, rating?.count ?: 0) } ?: ""),
                color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp,
            )
            reviewList.take(3).forEach { r ->
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surface).padding(12.dp)) {
                    Text("${"⭐".repeat(r.rating)} · ${r.serviceName}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    r.comment?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 12.sp) }
                    r.ownerReply?.let { Text("↳ $it", color = Ids.colors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
}

// Real dedicated product-detail screen (2026-07-21), closing
// docs/DESIGN_REFERENCES.md Section 5 recommendation #6 -- until now tapping a product
// anywhere in Commerce only ever revealed the flat catalog grid's inline qty stepper;
// there was no tap-through view showing the full-size image, discount breakdown, a
// description, and written reviews together. Reuses already-proven pieces
// (ProductImageThumb larger, ProductPriceRow, ProductRatingBadge which already lazily
// expands into the written-review list, QtyButton) rather than inventing new ones --
// this is a real second surface for the same real data, not new business logic.
// Real Coupang 정기배송 (subscribe & save) -- see core/network's ProductSubscriptionDto
// doc comment. A minimal delivery-address prompt via AlertDialog rather than a full
// address form, matching bank-mfe's own compact-card scope (fixed qty=1, every 30d).
@Composable
private fun SubscribeAndSaveButton(merchantId: String, productId: String) {
    var showDialog by remember { mutableStateOf(false) }
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("✓ Subscribed -- delivered every 30 days", color = Ids.colors.success, fontSize = 13.sp)
        return
    }

    TextButton(onClick = { showDialog = true }) {
        Text("Subscribe & save (every 30 days)", fontSize = 13.sp)
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Subscribe & save") },
            text = {
                Column {
                    Text("Delivered every 30 days. Cancel anytime.", color = Ids.colors.textSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    IdsTextField(value = address, onValueChange = { address = it }, label = "Delivery address")
                    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            },
            confirmButton = {
                TextButton(enabled = address.isNotBlank() && !busy, onClick = {
                    busy = true
                    coroutineScope.launch {
                        try {
                            NetworkClient.apiService.subscribeToProduct(
                                CreateProductSubscriptionRequest(merchantId, productId, 1, 30, address.trim()),
                                UUID.randomUUID().toString(),
                            )
                            done = true
                            showDialog = false
                        } catch (e: HttpException) {
                            error = superAppErrorMessage(e)
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(if (busy) "…" else "Subscribe") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProductDetailScreen(
    merchant: ShoppingMerchantDto,
    product: MerchantProductDto,
    cart: SnapshotStateMap<String, CommerceCartLine>,
    onBack: () -> Unit,
    onViewCart: () -> Unit,
    favorited: Boolean = false,
    favoriteBusy: Boolean = false,
    onToggleFavorite: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    val totalItems = cart.values.sumOf { it.quantity }
    val key = "${merchant.merchantId}:${product.id}"
    val qty = cart[key]?.quantity ?: 0
    fun setQty(newQty: Int) {
        val stock = product.stockQuantity
        if (newQty <= 0) cart.remove(key)
        else if (stock == null || newQty <= stock) {
            cart[key] = CommerceCartLine(merchant.merchantId, merchant.businessName, product, newQty)
        }
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar(merchant.businessName, onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProductImageThumb(product.imageUrl, size = 220.dp, corner = 16.dp)
                // Real Shop product wishlist button on the detail page (2026-07-24) --
                // Chloe Youn's Coupang case study (docs/DESIGN_REFERENCES.md Section 5)
                // names wishlist as available directly alongside add-to-cart, not
                // buried behind a sub-menu.
                Icon(
                    if (favorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (favorited) "Remove from wishlist" else "Add to wishlist",
                    tint = if (favorited) Ids.colors.danger else Ids.colors.textTertiary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clickable(enabled = !favoriteBusy, onClick = onToggleFavorite),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(product.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            ProductPriceRow(product)
            Spacer(modifier = Modifier.height(6.dp))
            ProductRatingBadge(product.id)
            Text(
                product.stockQuantity?.let { if (it == 0) "Out of stock" else "$it available" } ?: "Available",
                color = if (product.stockQuantity == 0) Ids.colors.danger else Ids.colors.textSecondary,
                fontSize = 12.sp,
            )
            val description = product.description
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(description, color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            ProductInquirySection(product.id)
            Spacer(modifier = Modifier.height(12.dp))
            SubscribeAndSaveButton(merchantId = merchant.merchantId, productId = product.id)
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                QtyButton("-") { setQty(qty - 1) }
                Text(qty.toString(), modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                QtyButton("+") { val stock = product.stockQuantity; if (stock == null || qty < stock) setQty(qty + 1) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ids.colors.brand)
                    .clickable(enabled = product.stockQuantity.let { it == null || it > 0 }) {
                        setQty(maxOf(1, qty))
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (qty > 0) "Update cart" else "Add to cart", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        if (totalItems > 0) {
            CartFab(totalItems, onClick = onViewCart)
        }
    }
}

/**
 * Real per-seller order splitting -- the merchant-grouped cart's checkout screen.
 * Each merchant group becomes its own real, independent placeOrder() call (its own
 * Idempotency-Key, its own wallet-to-wallet ledger transaction) -- sequential, not
 * parallel: these are real money-moving calls against the same buyer wallet, and a
 * clear one-at-a-time result list is more honest than a swallowed batch result. A
 * failure on one merchant's order does not block or roll back any other, matching
 * how a real multi-seller checkout behaves (each seller is charged/fulfilled
 * independently in real life).
 */
@Composable
private fun MultiCartView(
    cart: SnapshotStateMap<String, CommerceCartLine>,
    onBack: () -> Unit,
    onOrderPlaced: (List<CommerceCheckoutResult>) -> Unit,
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
) {
    BackHandler(onBack = onBack)
    var address by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Real device binding step-up (2026-07-21) -- every order in this batch shares
    // the same device/session, so hitting this once means every remaining order
    // would fail identically -- the loop below stops at the first one rather than
    // collecting N duplicate failures, same fix already applied to bank-mfe's
    // MultiCartView.
    var needsDeviceVerification by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val groups = cart.values.groupBy { it.merchantId }
    val grandTotal = cart.values.sumOf { it.product.price * it.quantity }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar("Your cart", onBack)
        if (groups.isEmpty()) {
            Text("Your cart is empty.", color = Ids.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            return@Column
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            groups.forEach { (merchantId, lines) ->
                item(key = merchantId) {
                    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(16.dp)) {
                        Text(lines.first().businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        lines.forEach { line ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${line.product.name} x${line.quantity}", color = Ids.colors.textPrimary, fontSize = 13.sp)
                                Text("%,.0f RWF".format(line.product.price * line.quantity), color = Ids.colors.textPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
            item {
                Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Ids.layout.cardCornerRadius)).background(Ids.colors.surface).padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total (${groups.size} order${if (groups.size == 1) "" else "s"})", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("%,.0f RWF".format(grandTotal), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    IdsTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = "Delivery address",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            }
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
                        val results = mutableListOf<CommerceCheckoutResult>()
                        for ((merchantId, lines) in groups) {
                            try {
                                val res = NetworkClient.apiService.placeOrder(
                                    idempotencyKey = UUID.randomUUID().toString(),
                                    request = PlaceOrderRequest(
                                        merchantId = merchantId,
                                        items = lines.map { OrderItemRequest(it.product.id, it.quantity) },
                                        deliveryAddress = address.trim(),
                                    ),
                                )
                                results.add(CommerceCheckoutResult(merchantId, lines.first().businessName, res.order, null))
                            } catch (e: HttpException) {
                                if (isDeviceNotVerifiedError(e)) {
                                    needsDeviceVerification = true
                                    submitting = false
                                    return@launch
                                }
                                results.add(CommerceCheckoutResult(merchantId, lines.first().businessName, null, superAppErrorMessage(e)))
                            } catch (e: IOException) {
                                results.add(CommerceCheckoutResult(merchantId, lines.first().businessName, null, "Couldn't reach itunda. Check your connection and try again."))
                            }
                        }
                        submitting = false
                        onOrderPlaced(results)
                    }
                }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) { Text(if (submitting) "Placing orders…" else "Place ${groups.size} order${if (groups.size == 1) "" else "s"}", color = Color.White, fontWeight = FontWeight.Bold) }
        deviceStepUpHost(
            needsDeviceVerification,
            { needsDeviceVerification = false },
            { needsDeviceVerification = false },
        )
    }
}

@Composable
private fun MultiCartResultsView(results: List<CommerceCheckoutResult>, onDone: () -> Unit) {
    val successCount = results.count { it.order != null }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("$successCount of ${results.size} order${if (results.size == 1) "" else "s"} placed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                results.forEach { r ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        if (r.order != null) {
                            Text("%,.0f RWF — placed".format(r.order.totalAmount), color = Ids.colors.success, fontSize = 13.sp)
                        } else {
                            Text(r.error ?: "Failed", color = Ids.colors.danger, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text(if (results.any { it.order == null }) "Back to cart" else "Done") } },
    )
}

private val COMMERCE_STATUS_LABEL = mapOf(
    "PLACED" to "Placed",
    "PACKED" to "Packed",
    "SHIPPED" to "Shipped",
    "DELIVERED" to "Delivered",
    "CANCELLED" to "Cancelled — refunded",
)

@Composable
private fun CommerceOrderRow(order: OrderDto, action: (@Composable () -> Unit)? = null) {
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(COMMERCE_STATUS_LABEL[order.status] ?: order.status, color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(order.deliveryAddress, color = Ids.colors.textSecondary, fontSize = 12.sp)
                }
                Text("%,.0f RWF".format(order.totalAmount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            action?.invoke()
        }
    }
}

// Real Shop product wishlist view (2026-07-24) -- Android port of bank-mfe's
// ProductCatalogView wishlist tab, mirroring Marketplace's own ListingWishlistView
// field-for-field. Closes docs/DESIGN_REFERENCES.md Section 5 recommendation #3.
@Composable
private fun ProductWishlistView(onRemoved: () -> Unit) {
    var favorites by remember { mutableStateOf<List<FavoriteProductDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyFavoriteProducts()
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

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        favorites == null -> SkeletonBlock()
        favorites!!.isEmpty() -> EmptyState("No saved products yet -- tap ♡ on any product to save it here.", icon = Icons.Outlined.FavoriteBorder)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            favorites!!.forEach { f ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProductImageThumb(f.imageUrl, size = 48.dp, corner = 10.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(f.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${f.businessName} · %,.0f RWF".format(f.price), color = Ids.colors.textSecondary, fontSize = 13.sp)
                                // Real Naver Shopping price-drop alert (item 227) -- see
                                // FavoriteProductDto's own doc comment.
                                if (f.priceDropped) {
                                    Text("🔻 Price dropped", color = Ids.colors.brand, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                        TextButton(onClick = {
                            removingId = f.productId
                            coroutineScope.launch {
                                try {
                                    NetworkClient.apiService.removeProductFavorite(f.productId)
                                    favorites = favorites?.filterNot { it.productId == f.productId }
                                    onRemoved()
                                } catch (e: HttpException) {
                                    error = superAppErrorMessage(e)
                                } catch (e: IOException) {
                                    error = "Couldn't reach itunda. Check your connection and try again."
                                } finally {
                                    removingId = null
                                }
                            }
                        }) {
                            Text(if (removingId == f.productId) "Removing…" else "Remove", color = Ids.colors.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// Real "my questions across every product I've ever asked about" (2026-08-04) -- see
// core/network's getMyProductInquiries doc comment. ProductInquirySection (on a single
// product's detail page) already lets a buyer ask/view that one product's Q&A; this is
// the first place a buyer can see every question they've ever asked, across every
// product, in one list. Read-only from here -- answering is the merchant app's job.
@Composable
private fun MyProductInquiriesView() {
    var inquiries by remember { mutableStateOf<List<ProductInquiryDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                inquiries = NetworkClient.apiService.getMyProductInquiries().inquiries
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        inquiries == null -> SkeletonBlock()
        inquiries!!.isEmpty() -> EmptyState("No questions asked yet -- ask one from any product's detail page.", icon = Icons.Outlined.RateReview)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            inquiries!!.forEach { q ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(q.question, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (!q.answer.isNullOrBlank()) {
                            Text("Answered: ${q.answer}", color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        } else {
                            Text("Waiting for an answer…", color = Ids.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

// Real Coupang 정기배송 (subscribe & save)-style recurring product delivery -- see
// core/network's ProductSubscriptionDto doc comment. bank-mfe already had this;
// this is the first Android client, mirroring bank-mfe's MyProductSubscriptionsCard.
@Composable
private fun MyProductSubscriptionsView() {
    var subscriptions by remember { mutableStateOf<List<ProductSubscriptionDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyProductSubscriptions()
                if (res.success) subscriptions = res.subscriptions
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) { load() }

    when {
        error != null -> ErrorCard(error!!, onRetry = ::load)
        subscriptions == null -> SkeletonBlock()
        subscriptions!!.isEmpty() -> EmptyState("No recurring deliveries yet -- subscribe from any product's detail page.", icon = Icons.Outlined.Autorenew)
        else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            subscriptions!!.forEach { s ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Qty ${s.quantity} · every ${s.intervalDays}d", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        val cancelledAt = s.cancelledAt
                        Text(
                            if (s.status == "CANCELLED" && cancelledAt != null) "Cancelled ${cancelledAt.take(10)}" else "${s.status} · ${s.deliveryCount} delivered",
                            color = Ids.colors.textSecondary, fontSize = 13.sp,
                        )
                        if (s.lastFailureReason != null && s.status == "ACTIVE") {
                            Text("Last delivery failed: ${s.lastFailureReason}", color = Ids.colors.danger, fontSize = 12.sp)
                        }
                        if (s.status != "CANCELLED") {
                            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = {
                                    busyId = s.id
                                    coroutineScope.launch {
                                        try {
                                            if (s.status == "ACTIVE") NetworkClient.apiService.pauseProductSubscription(s.id)
                                            else NetworkClient.apiService.resumeProductSubscription(s.id)
                                            load()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            error = "Couldn't reach itunda. Check your connection and try again."
                                        } finally {
                                            busyId = null
                                        }
                                    }
                                }, enabled = busyId != s.id) {
                                    Text(if (busyId == s.id) "…" else if (s.status == "ACTIVE") "Pause" else "Resume", fontSize = 13.sp)
                                }
                                TextButton(onClick = {
                                    busyId = s.id
                                    coroutineScope.launch {
                                        try {
                                            NetworkClient.apiService.cancelProductSubscription(s.id)
                                            load()
                                        } catch (e: HttpException) {
                                            error = superAppErrorMessage(e)
                                        } catch (e: IOException) {
                                            error = "Couldn't reach itunda. Check your connection and try again."
                                        } finally {
                                            busyId = null
                                        }
                                    }
                                }, enabled = busyId != s.id) {
                                    Text("Cancel", color = Ids.colors.textSecondary, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Real merchant-side Commerce order fulfillment queue (item 234) -- found via a
// sibling-consistency audit against bank-mfe's own MerchantOrdersView, which has had
// this since before this session: a real itunda user who also runs a merchant
// storefront could manage their store's orders on bank-mfe but had zero client
// anywhere on Android. Android port, straight mirror of bank-mfe's own component.
private val COMMERCE_MERCHANT_STATUS_CHAIN = listOf("PLACED", "PACKED", "SHIPPED", "DELIVERED")

@Composable
private fun MerchantOrdersView() {
    var orders by remember { mutableStateOf<List<OrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyOrderId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMerchantOrders()
                if (res.success) orders = res.orders
                error = null
            } catch (e: HttpException) {
                // A real, expected error for any account that hasn't registered as a
                // merchant -- stays silent rather than alarming the common case of a
                // buyer-only account, matching bank-mfe's own MerchantOrdersView.
                if (rw.itunda.core.network.apiErrorCode(e) == "MERCHANT_NOT_FOUND") {
                    orders = emptyList()
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(4000)
        }
    }

    fun advance(order: OrderDto) {
        val idx = COMMERCE_MERCHANT_STATUS_CHAIN.indexOf(order.status)
        val next = COMMERCE_MERCHANT_STATUS_CHAIN.getOrNull(idx + 1) ?: return
        busyOrderId = order.id
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.updateOrderStatus(order.id, UpdateOrderStatusRequest(next))
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyOrderId = null
            }
        }
    }

    val list = orders
    if (error != null) {
        ErrorCard(error!!, onRetry = ::load)
        return
    }
    if (list == null) {
        SkeletonBlock()
        return
    }
    if (list.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
        Text("Orders for your store", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        list.forEach { o ->
            val idx = COMMERCE_MERCHANT_STATUS_CHAIN.indexOf(o.status)
            val next = COMMERCE_MERCHANT_STATUS_CHAIN.getOrNull(idx + 1)
            CommerceOrderRow(o) {
                if (next != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (busyOrderId == o.id) Ids.colors.textTertiary else Ids.colors.brand)
                            .clickable(enabled = busyOrderId != o.id) { advance(o) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (busyOrderId == o.id) "Updating…" else "Mark ${(COMMERCE_STATUS_LABEL[next] ?: next).lowercase()}",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MerchantReturnQueueView() {
    var requests by remember { mutableStateOf<List<OrderReturnRequestDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMerchantReturnQueue()
                if (res.success) requests = res.returnRequests
                error = null
            } catch (e: HttpException) {
                if (rw.itunda.core.network.apiErrorCode(e) == "MERCHANT_NOT_FOUND") {
                    requests = emptyList()
                } else {
                    error = superAppErrorMessage(e)
                }
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(8000)
        }
    }

    fun decide(id: String, approve: Boolean) {
        busyId = id
        error = null
        coroutineScope.launch {
            try {
                NetworkClient.apiService.decideOrderReturn(id, DecideOrderReturnRequest(approve))
                load()
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            } finally {
                busyId = null
            }
        }
    }

    val list = requests
    if (error != null) {
        ErrorCard(error!!, onRetry = ::load)
        return
    }
    if (list == null) {
        SkeletonBlock()
        return
    }
    val open = list.filter { it.status == "REQUESTED" }
    if (open.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
        Text("Return & exchange requests", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        open.forEach { r ->
            Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(if (r.type == "RETURN") "Return requested" else "Exchange requested", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(r.reasonCode.replace("_", " ").lowercase(), color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                    r.reasonNote?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 13.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (busyId == r.id) Ids.colors.textTertiary else Ids.colors.brand)
                                .clickable(enabled = busyId != r.id) { decide(r.id, true) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(if (busyId == r.id) "…" else "Approve", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Ids.colors.surface)
                                .clickable(enabled = busyId != r.id) { decide(r.id, false) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text("Reject", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyCommerceOrdersView() {
    var orders by remember { mutableStateOf<List<OrderDto>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var cancellingId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                val res = NetworkClient.apiService.getMyOrders()
                if (res.success) orders = res.orders
                error = null
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
            } catch (e: IOException) {
                error = "Couldn't reach itunda. Check your connection and try again."
            }
        }
    }
    // Real poll for order-tracking status, same 4s cadence as Eats' own poll.
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
                NetworkClient.apiService.cancelOrder(orderId)
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

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MyReturnRequestsView()
        if (error != null) {
            ErrorCard(error!!, onRetry = ::load)
        } else if (orders == null) {
            SkeletonBlock()
        } else if (orders!!.isEmpty()) {
            EmptyState("No orders yet — browse a merchant's shop and your first order will show up here.", icon = Icons.AutoMirrored.Outlined.ReceiptLong)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                orders!!.forEach { o ->
                    CommerceOrderRow(o) {
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
                        } else if (o.status == "SHIPPED") {
                            LiveTrackingToggle(o.id)
                        } else if (o.status == "DELIVERED") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OrderItemReviews(o)
                                ReturnExchangeAction(o.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Real live rider-location tracking for Commerce orders (item 230) -- see
// SimpleLiveRiderMiniMap's own doc comment. bank-mfe shipped this first
// (SimpleLiveRiderMap.tsx, 2026-08-05); this is the Android port.
@Composable
private fun LiveTrackingToggle(orderId: String) {
    var tracking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Ids.colors.brand)
                .clickable { tracking = !tracking }
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(if (tracking) "Hide live tracking" else "🛵 Track your rider live", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        if (tracking) {
            SimpleLiveRiderMiniMap(orderId)
        }
    }
}

// Real Coupang-style post-delivery Return & Exchange request (반품/교환 신청) (item 166/174)
// -- see OrderReturnService's own doc comment for the full account: a real 7-day window
// from delivery, an approved RETURN triggers a real refund via reversed ledger legs, an
// approved EXCHANGE moves no money. Merchant-side approve/reject queue has zero Android
// client anywhere (Shop has no merchant order-management screen on this platform at all
// yet) -- a real, separate, not-yet-started gap; this is the buyer-side request form only,
// mirroring bank-mfe's own ReturnExchangeAction (item 166).
@Composable
private fun ReturnExchangeAction(orderId: String) {
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var type by remember { mutableStateOf("RETURN") }
    var reasonCode by remember { mutableStateOf(ORDER_RETURN_REASON_CODES.first()) }
    var note by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("Return/exchange requested -- the seller will review it.", color = Ids.colors.textSecondary, fontSize = 12.sp)
        return
    }
    if (!open) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Return or exchange", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("RETURN" to "Return", "EXCHANGE" to "Exchange").forEach { (value, label) ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (type == value) Ids.colors.brand else Ids.colors.textTertiary)
                        .clickable { type = value }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(label, color = if (type == value) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ORDER_RETURN_REASON_CODES.forEach { code ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (reasonCode == code) Ids.colors.brand else Ids.colors.textTertiary)
                        .clickable { reasonCode = code }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(code, color = if (reasonCode == code) Color.White else Ids.colors.textPrimary, fontSize = 11.sp)
                }
            }
        }
        IdsTextField(
            value = note,
            onValueChange = { note = it },
            label = "Add a note (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
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
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.requestOrderReturn(orderId, RequestOrderReturnRequest(type, reasonCode, note.trim().ifBlank { null }))
                                done = true
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } catch (e: IOException) {
                                error = "Couldn't reach itunda. Check your connection and try again."
                            } finally {
                                submitting = false
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text(if (submitting) "Submitting…" else "Submit request", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
    }
}

private val RETURN_STATUS_LABEL = mapOf("REQUESTED" to "Pending review", "APPROVED" to "Approved", "REJECTED" to "Rejected")

@Composable
private fun MyReturnRequestsView() {
    var requests by remember { mutableStateOf<List<OrderReturnRequestDto>?>(null) }

    LaunchedEffect(Unit) {
        try {
            requests = NetworkClient.apiService.getMyReturnRequests().returnRequests
        } catch (e: Exception) {
            requests = emptyList()
        }
    }
    val list = requests
    if (list != null && list.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("My return/exchange requests", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            list.forEach { r ->
                Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(if (r.type == "RETURN") "Return" else "Exchange", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                RETURN_STATUS_LABEL[r.status] ?: r.status,
                                color = when (r.status) { "APPROVED" -> Ids.colors.brand; "REJECTED" -> Ids.colors.danger; else -> Ids.colors.textSecondary },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                        Text(r.reasonCode, color = Ids.colors.textSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Real Kakao Pay 정기결제/Toss 빌링키-style subscribe/cancel -- subscribing charges the
 * first cycle immediately (real "인증 + 첫결제"), same as
 * rw.itunda.merchant.MerchantBillingService.subscribe's own doc comment. One real
 * active subscription per plan; cancelling stops future charges but doesn't refund the
 * current cycle already paid for. bank-mfe already has this; this is the first Android
 * client.
 */
@Composable
private fun BillingPlanRow(plan: MerchantBillingPlanDto, subscription: MerchantBillingSubscriptionDto?, onChanged: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Ids.colors.surfaceSoft).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(plan.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "%,.0f RWF every ${plan.intervalDays} days".format(plan.amount),
                    color = Ids.colors.textSecondary, fontSize = 12.sp,
                )
                plan.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = Ids.colors.textSecondary, fontSize = 11.sp) }
            }
            if (subscription != null) {
                ListingActionButtonShop(if (busy) "…" else "Cancel", busy) {
                    busy = true
                    error = null
                    coroutineScope.launch {
                        try {
                            NetworkClient.apiService.cancelBillingSubscription(subscription.id)
                            onChanged()
                        } catch (e: Exception) {
                            error = "Could not cancel this subscription."
                        } finally {
                            busy = false
                        }
                    }
                }
            } else {
                ListingActionButtonShop(if (busy) "…" else "Subscribe", busy, filled = true) {
                    busy = true
                    error = null
                    coroutineScope.launch {
                        try {
                            NetworkClient.apiService.subscribeToBillingPlan(plan.id, UUID.randomUUID().toString())
                            onChanged()
                        } catch (e: Exception) {
                            error = "Could not subscribe to this plan."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }
        if (subscription != null) {
            Text(
                if (subscription.status == "ACTIVE") "Next charge ${subscription.nextChargeAt.take(10)}" else "Cancelled",
                color = Ids.colors.textSecondary, fontSize = 11.sp,
            )
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 11.sp) }
    }
}

/**
 * Real Coupang-style pre-purchase product Q&A (상품문의) -- see
 * rw.itunda.commerce.ProductInquiryService's own doc comment. Genuinely distinct from
 * ProductRatingBadge's reviews below: no order/purchase required at all, so this is
 * always visible on a product's detail page, not gated behind having bought it.
 * bank-mfe already has this; this is the first Android client.
 */
@Composable
private fun ProductInquirySection(productId: String) {
    var inquiries by remember { mutableStateOf<List<ProductInquiryDto>?>(null) }
    var question by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun load() {
        coroutineScope.launch {
            try {
                inquiries = NetworkClient.apiService.getProductInquiries(productId).inquiries
            } catch (e: Exception) {
                inquiries = emptyList()
            }
        }
    }
    LaunchedEffect(productId) { load() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Questions & answers", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IdsTextField(value = question, onValueChange = { question = it }, label = "Ask the seller a question", modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(if (asking || question.isBlank()) Ids.colors.textTertiary else Ids.colors.surfaceSoft)
                    .clickable(enabled = !asking && question.isNotBlank()) {
                        asking = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.askProductInquiry(productId, AskProductInquiryRequest(question.trim()))
                                question = ""
                                load()
                            } catch (e: HttpException) {
                                error = superAppErrorMessage(e)
                            } finally {
                                asking = false
                            }
                        }
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
            ) { Text("Ask", color = Ids.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
        error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
        val list = inquiries
        Spacer(modifier = Modifier.height(8.dp))
        when {
            list == null -> Text("Loading questions…", color = Ids.colors.textSecondary, fontSize = 12.sp)
            list.isEmpty() -> Text("No questions yet -- be the first to ask.", color = Ids.colors.textSecondary, fontSize = 12.sp)
            else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                list.forEach { q ->
                    Column {
                        Row {
                            Text("Q. ", color = Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(q.question, color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                        val answer = q.answer
                        if (answer != null) {
                            Row(modifier = Modifier.padding(start = 12.dp, top = 2.dp)) {
                                Text("A. ", color = Ids.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(answer, color = Ids.colors.textSecondary, fontSize = 12.sp)
                            }
                        } else {
                            Text("Awaiting seller response", color = Ids.colors.textTertiary, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

// Real post-delivery product reviews (2026-07-20), mirroring RestaurantRatingBadge/
// ReviewOrderCard (Eats, still in :app) -- see ProductReviewService's own doc comment
// for the full backend account. One real review per real delivered order line item.
@Composable
private fun ProductRatingBadge(productId: String) {
    var rating by remember { mutableStateOf<ProductRatingResponse?>(null) }
    var open by remember { mutableStateOf(false) }
    var reviews by remember { mutableStateOf<List<ProductReviewDto>?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(productId) {
        try {
            rating = NetworkClient.apiService.getProductRating(productId)
        } catch (e: Exception) {
            // Real, non-critical -- a rating fetch failure shouldn't block browsing the catalog.
        }
    }
    val r = rating
    if (r != null && r.count > 0) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    val next = !open
                    open = next
                    if (next && reviews == null) {
                        coroutineScope.launch {
                            try {
                                reviews = NetworkClient.apiService.getProductReviews(productId).reviews
                            } catch (e: Exception) {
                                reviews = emptyList()
                            }
                        }
                    }
                },
            ) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = StarGold, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("%.1f (%d)".format(r.average ?: 0.0, r.count), color = Ids.colors.textSecondary, fontSize = 12.sp)
            }
            if (open) {
                val list = reviews
                if (list == null) {
                    Text("Loading reviews…", color = Ids.colors.textSecondary, fontSize = 12.sp)
                } else if (list.isEmpty()) {
                    EmptyState("No written reviews yet — be the first to share how it went.", icon = Icons.Outlined.RateReview)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
                        list.forEach { rv ->
                            val stars = "★".repeat(rv.rating) + "☆".repeat(5 - rv.rating)
                            Text(
                                if (rv.comment.isNullOrBlank()) stars else "$stars — ${rv.comment}",
                                color = Ids.colors.textSecondary,
                                fontSize = 12.sp,
                            )
                            if (!rv.ownerReply.isNullOrBlank()) {
                                Text(
                                    "↳ Seller: ${rv.ownerReply}",
                                    color = Ids.colors.textTertiary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductReviewRow(item: OrderItemDto) {
    var open by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var rating by remember { mutableStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (done) {
        Text("${item.productName}: thanks for your review!", color = Ids.colors.textSecondary, fontSize = 12.sp)
        return
    }
    if (!open) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Ids.colors.textTertiary).clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text("Rate ${item.productName}", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        Text(item.productName, color = Ids.colors.textSecondary, fontSize = 12.sp)
        StarRatingRow(rating) { rating = it }
        IdsTextField(
            value = comment,
            onValueChange = { comment = it },
            label = "How was it? (optional)",
            modifier = Modifier.fillMaxWidth(),
        )
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
                        if (rating == 0) {
                            error = "Pick a star rating."
                            return@clickable
                        }
                        submitting = true
                        error = null
                        coroutineScope.launch {
                            try {
                                NetworkClient.apiService.submitProductReview(item.id, SubmitProductReviewRequest(rating, comment.trim().ifBlank { null }))
                                done = true
                            } catch (e: HttpException) {
                                // A 409 here is the real PRODUCT_ALREADY_REVIEWED case in
                                // practice -- this form only ever renders for a real
                                // DELIVERED order.
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
private fun OrderItemReviews(order: OrderDto) {
    var items by remember { mutableStateOf<List<OrderItemDto>?>(null) }
    LaunchedEffect(order.id) {
        try {
            val res = NetworkClient.apiService.getOrder(order.id)
            if (res.success) items = res.items
        } catch (e: Exception) {
            // Real, non-critical -- if item fetch fails, the order row itself still renders fine.
        }
    }
    val list = items
    if (list != null && list.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { item -> ProductReviewRow(item) }
        }
    }
}

/**
 * Real "pay a merchant" -- the manual-code-entry alternative to camera QR scanning
 * (this app has no scanner), mirrors bank-mfe's `PayByCodeCard`/`PayByStaticQrCard`
 * exactly. bank-mfe already has both; this is the first Android client for either --
 * previously neither the dynamic per-sale flow nor the static QR flow existed anywhere
 * on this native consumer app. Coupon-preview-before-pay (bank-mfe's own
 * `previewPaymentIntent` flow, item 149/146) closed 2026-08-01 -- see PayByCodeCard's
 * own doc comment.
 */
@Composable
private fun PayAMerchantSection(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
) {
    var paymentResult by remember { mutableStateOf<CollectPaymentResultDto?>(null) }
    // Real Face Pay -- see FacePaySettingsCard/PayByCodeCard's own doc comments. Lifted
    // here, same as bank-mfe's own ShoppingView, so this card and PayByCodeCard don't
    // each fetch enrollment status independently (PayByCodeCard would otherwise never
    // learn about an enrollment that happened in the same session until a full reload).
    var facePayEnrolled by remember { mutableStateOf<Boolean?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun loadFacePayStatus() {
        coroutineScope.launch {
            try {
                facePayEnrolled = NetworkClient.apiService.getFacePayStatus().enrolled
            } catch (e: Exception) {
                // Real, non-critical -- the toggle just won't render if this fails.
            }
        }
    }
    LaunchedEffect(Unit) { loadFacePayStatus() }

    val result = paymentResult
    if (result != null) {
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Payment complete", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(result.merchantName, color = Ids.colors.textPrimary, fontSize = 14.sp)
                Text("%,.0f RWF".format(result.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (result.cashbackEarned > java.math.BigDecimal.ZERO) {
                    Text("+ %,.0f RWF cashback".format(result.cashbackEarned), color = Ids.colors.brand, fontSize = 13.sp)
                }
                ListingActionButtonShop("Done", false) { paymentResult = null }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FacePaySettingsCard(enrolled = facePayEnrolled, onChanged = ::loadFacePayStatus)
        PayByCodeCard(deviceStepUpHost = deviceStepUpHost, facePayEnrolled = facePayEnrolled ?: false, onPaid = { paymentResult = it })
        PayByStaticQrCard(onPaid = { paymentResult = it })
    }
}

/**
 * Real Face Pay enroll/disable toggle -- see rw.itunda.merchant.FacePayService's own
 * doc comment. bank-mfe already has this; this is the first Android client. Enrolling
 * swaps Pay-by-code's own collect call to the Face Pay channel -- same manual code
 * entry, just a different real ledger channel label, matching bank-mfe's own honest
 * scope exactly (no device biometric prompt gates it on any client, itunda's own).
 */
@Composable
private fun FacePaySettingsCard(enrolled: Boolean?, onChanged: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    if (enrolled == null) {
        Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), modifier = Modifier.fillMaxWidth().height(64.dp)) {}
        return
    }
    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("😊 Face Pay", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        if (enrolled) "Enabled -- authorize payment codes with your face, no code re-entry needed" else "Not enabled on this account",
                        color = Ids.colors.textSecondary, fontSize = 12.sp,
                    )
                }
                ListingActionButtonShop(if (busy) "…" else if (enrolled) "Disable" else "Enable", busy, filled = !enrolled) {
                    busy = true
                    error = null
                    coroutineScope.launch {
                        try {
                            if (enrolled) NetworkClient.apiService.revokeFacePay() else NetworkClient.apiService.enrollFacePay()
                            onChanged()
                        } catch (e: Exception) {
                            error = "Could not update Face Pay."
                        } finally {
                            busy = false
                        }
                    }
                }
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        }
    }
}

private fun couponDiscountLabel(c: MerchantCouponPreviewDto): String =
    if (c.discountType == "PERCENT") "${c.discountValue}% off" else "%,.0f RWF off".format(c.discountValue)

/**
 * Real coupon-preview-before-pay (item 149/146) -- closes the deliberate scope-down
 * this composable's own doc comment previously named. Mirrors bank-mfe's PayByCodeCard
 * exactly: a non-Face-Pay code with real eligible coupons stops at a preview step
 * (merchant/amount + coupon picker) before the actual collect() call; Face Pay and a
 * code with zero eligible coupons both skip straight to a direct pay, same as
 * bank-mfe's own payDirect()/handleSubmit() branching.
 */
@Composable
private fun PayByCodeCard(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    facePayEnrolled: Boolean,
    onPaid: (CollectPaymentResultDto) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsDeviceVerification by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<PaymentIntentPreviewResponse?>(null) }
    var eligibleCoupons by remember { mutableStateOf<List<MerchantCouponViewDto>>(emptyList()) }
    var selectedCouponId by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    fun payDirect(couponId: String? = null) {
        needsDeviceVerification = false
        submitting = true
        error = null
        coroutineScope.launch {
            try {
                val idempotencyKey = UUID.randomUUID().toString()
                val result = if (facePayEnrolled) {
                    NetworkClient.apiService.collectWithFacePay(code.trim(), idempotencyKey)
                } else {
                    NetworkClient.apiService.collectPayment(code.trim(), idempotencyKey, CollectPaymentRequest(couponId))
                }
                code = ""
                preview = null
                eligibleCoupons = emptyList()
                selectedCouponId = null
                onPaid(result)
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

    fun submit() {
        error = null
        if (facePayEnrolled) {
            payDirect()
            return
        }
        submitting = true
        coroutineScope.launch {
            try {
                val r = NetworkClient.apiService.previewPaymentIntent(code.trim())
                val eligible = r.coupons.filter { it.eligible && !it.alreadyRedeemed }
                if (eligible.isEmpty()) {
                    payDirect()
                } else {
                    preview = r
                    eligibleCoupons = eligible
                    submitting = false
                }
            } catch (e: HttpException) {
                error = superAppErrorMessage(e)
                submitting = false
            } catch (e: IOException) {
                error = "Could not look up this payment code."
                submitting = false
            }
        }
    }

    fun cancelPreview() {
        preview = null
        eligibleCoupons = emptyList()
        selectedCouponId = null
        error = null
    }

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pay by code", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                if (facePayEnrolled) {
                    "Face Pay is on -- enter the code the merchant shows you to authorize with your face."
                } else {
                    "No scanner handy? Enter the payment code the merchant shows you to pay instantly and earn cashback."
                },
                color = Ids.colors.textSecondary, fontSize = 12.sp,
            )
            val currentPreview = preview
            if (currentPreview != null) {
                Text(currentPreview.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("%,.0f RWF".format(currentPreview.amount), color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Apply a coupon?", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selectedCouponId == null, onClick = { selectedCouponId = null })
                    Text("No coupon", color = Ids.colors.textPrimary, fontSize = 13.sp)
                }
                eligibleCoupons.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selectedCouponId == c.coupon.id, onClick = { selectedCouponId = c.coupon.id })
                        Text("${c.coupon.title} -- ${couponDiscountLabel(c.coupon)}", color = Ids.colors.textPrimary, fontSize = 13.sp)
                    }
                }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ListingActionButtonShop(if (submitting) "Paying…" else "Pay", submitting, filled = true) { payDirect(selectedCouponId) }
                    ListingActionButtonShop("Cancel", submitting, filled = false) { cancelPreview() }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IdsTextField(value = code, onValueChange = { code = it }, label = "Payment code", modifier = Modifier.weight(1f))
                    ListingActionButtonShop(
                        if (submitting) (if (facePayEnrolled) "Authorizing…" else "Paying…") else if (facePayEnrolled) "😊 Pay" else "Pay",
                        submitting || code.isBlank(), filled = true,
                    ) { submit() }
                }
                error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
            }
        }
    }
    deviceStepUpHost(needsDeviceVerification, { needsDeviceVerification = false }, { needsDeviceVerification = false })
}

@Composable
private fun PayByStaticQrCard(onPaid: (CollectPaymentResultDto) -> Unit) {
    var merchantId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Card(shape = RoundedCornerShape(Ids.layout.cardCornerRadius), colors = CardDefaults.cardColors(containerColor = Ids.colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pay a merchant's static QR", color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "For a merchant with one permanent code (like a market stall) -- enter their merchant ID and how much you're paying.",
                color = Ids.colors.textSecondary, fontSize = 12.sp,
            )
            IdsTextField(value = merchantId, onValueChange = { merchantId = it }, label = "Merchant ID", modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IdsTextField(value = amount, onValueChange = { amount = it }, label = "Amount (RWF)", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                ListingActionButtonShop(
                    if (submitting) "Paying…" else "Pay",
                    submitting || merchantId.isBlank() || amount.toBigDecimalOrNull() == null,
                    filled = true,
                ) {
                    val numericAmount = amount.toBigDecimalOrNull()
                    if (numericAmount == null || numericAmount <= java.math.BigDecimal.ZERO) {
                        error = "Enter a valid amount."
                        return@ListingActionButtonShop
                    }
                    submitting = true
                    error = null
                    coroutineScope.launch {
                        try {
                            val result = NetworkClient.apiService.payByStaticQr(merchantId.trim(), UUID.randomUUID().toString(), StaticQrPayRequest(numericAmount))
                            merchantId = ""
                            amount = ""
                            onPaid(result)
                        } catch (e: HttpException) {
                            error = superAppErrorMessage(e)
                        } catch (e: IOException) {
                            error = "Couldn't reach itunda. Check your connection and try again."
                        } finally {
                            submitting = false
                        }
                    }
                }
            }
            error?.let { Text(it, color = Ids.colors.danger, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun ListingActionButtonShop(label: String, disabled: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (disabled) Ids.colors.textTertiary else if (filled) Ids.colors.brand else Ids.colors.surfaceSoft)
            .clickable(enabled = !disabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (filled || disabled) Color.White else Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}
