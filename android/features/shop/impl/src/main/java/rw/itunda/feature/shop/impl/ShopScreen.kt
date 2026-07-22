package rw.itunda.feature.shop.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.ErrorCard
import rw.itunda.core.designsystem.components.QtyButton
import rw.itunda.core.designsystem.components.SearchAndCategoryChips
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.components.StarGold
import rw.itunda.core.designsystem.components.StarRatingRow
import rw.itunda.core.designsystem.components.TabHeader
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OrderDto
import rw.itunda.core.network.OrderItemDto
import rw.itunda.core.network.OrderItemRequest
import rw.itunda.core.network.PlaceOrderRequest
import rw.itunda.core.network.ProductRatingResponse
import rw.itunda.core.network.ProductReviewDto
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.SubmitProductReviewRequest
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

private enum class CommerceView { BROWSE, ORDERS }

// Real cross-merchant cart (2026-07-20) -- closes the "real Coupang splits a
// multi-seller cart into per-seller orders, not attempted here" simplification the
// matrix named. Flattened (not nested maps) so a plain SnapshotStateMap keyed by
// "merchantId:productId" works cleanly with Compose recomposition -- each entry
// carries its own merchant/product context, so grouping-by-merchant at checkout
// time is a plain in-memory groupBy, no separate lookup needed.
private data class CommerceCartLine(val merchantId: String, val businessName: String, val product: MerchantProductDto, val quantity: Int)
private data class CommerceCheckoutResult(val merchantId: String, val businessName: String, val order: OrderDto?, val error: String?)

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
    val cart = remember { mutableStateMapOf<String, CommerceCartLine>() }
    var showCart by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<CommerceCheckoutResult>?>(null) }
    val coroutineScope = rememberCoroutineScope()

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

    fun openMerchant(m: ShoppingMerchantDto) {
        selectedMerchant = m
        products = null
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
    val product = selectedProduct
    if (merchant != null && product != null) {
        ProductDetailScreen(
            merchant = merchant,
            product = product,
            cart = cart,
            onBack = { selectedProduct = null },
            onViewCart = { selectedProduct = null; showCart = true },
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
        )
        return
    }

    val totalItems = cart.values.sumOf { it.quantity }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical),
        verticalArrangement = Arrangement.spacedBy(Ids.layout.cardGap),
    ) {
        item { TabHeader("Shop") }
        item {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ids.colors.surfaceSoft).padding(4.dp)) {
                listOf(CommerceView.BROWSE to "Merchants", CommerceView.ORDERS to "My orders").forEach { (v, label) ->
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
        if (view == CommerceView.ORDERS) {
            item { MyCommerceOrdersView() }
        } else {
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
                        if (selectedCategory != null || searchInput.isNotBlank()) "No merchants match your search." else "No stores registered yet.",
                        icon = Icons.Outlined.Storefront,
                    )
                }
            } else {
                items(merchants!!, key = { it.merchantId }) { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Ids.layout.cardCornerRadius))
                            .background(Ids.colors.surface)
                            .clickable { openMerchant(m) }
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Ids.colors.surfaceSoft), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Storefront, contentDescription = null, modifier = Modifier.size(20.dp), tint = Ids.colors.brand)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(m.businessName, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${m.cashbackRate} cashback on QR/code payments", color = Ids.colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
}

@Composable
private fun MerchantDetailView(
    merchant: ShoppingMerchantDto,
    products: List<MerchantProductDto>?,
    cart: SnapshotStateMap<String, CommerceCartLine>,
    onBack: () -> Unit,
    onViewCart: () -> Unit,
    onOpenProduct: (MerchantProductDto) -> Unit,
) {
    BackHandler(onBack = onBack)
    val totalItems = cart.values.sumOf { it.quantity }
    fun qtyFor(productId: String) = cart["${merchant.merchantId}:$productId"]?.quantity ?: 0
    fun setQty(product: MerchantProductDto, qty: Int) {
        val key = "${merchant.merchantId}:${product.id}"
        if (qty <= 0) cart.remove(key) else cart[key] = CommerceCartLine(merchant.merchantId, merchant.businessName, product, qty)
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar(merchant.businessName, onBack)
        if (products == null) {
            SkeletonBlock(height = 72.dp)
        } else if (products.isEmpty()) {
            EmptyState("No products yet.", icon = Icons.Outlined.ShoppingBag)
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
                            ProductImageThumb(p.imageUrl, size = 96.dp, corner = 12.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(p.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
                            Spacer(modifier = Modifier.height(2.dp))
                            ProductPriceRow(p)
                        }
                        ProductRatingBadge(p.id)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            QtyButton("-") { setQty(p, qty - 1) }
                            Text(qty.toString(), modifier = Modifier.width(28.dp), textAlign = TextAlign.Center, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                            QtyButton("+") { setQty(p, qty + 1) }
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

// Real dedicated product-detail screen (2026-07-21), closing
// docs/DESIGN_REFERENCES.md Section 5 recommendation #6 -- until now tapping a product
// anywhere in Commerce only ever revealed the flat catalog grid's inline qty stepper;
// there was no tap-through view showing the full-size image, discount breakdown, a
// description, and written reviews together. Reuses already-proven pieces
// (ProductImageThumb larger, ProductPriceRow, ProductRatingBadge which already lazily
// expands into the written-review list, QtyButton) rather than inventing new ones --
// this is a real second surface for the same real data, not new business logic.
@Composable
private fun ProductDetailScreen(
    merchant: ShoppingMerchantDto,
    product: MerchantProductDto,
    cart: SnapshotStateMap<String, CommerceCartLine>,
    onBack: () -> Unit,
    onViewCart: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val totalItems = cart.values.sumOf { it.quantity }
    val key = "${merchant.merchantId}:${product.id}"
    val qty = cart[key]?.quantity ?: 0
    fun setQty(newQty: Int) {
        if (newQty <= 0) cart.remove(key) else cart[key] = CommerceCartLine(merchant.merchantId, merchant.businessName, product, newQty)
    }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Ids.layout.screenHorizontal, vertical = Ids.layout.screenVertical)) {
        BackTopBar(merchant.businessName, onBack)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                ProductImageThumb(product.imageUrl, size = 220.dp, corner = 16.dp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(product.name, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            ProductPriceRow(product)
            Spacer(modifier = Modifier.height(6.dp))
            ProductRatingBadge(product.id)
            if (!product.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(product.description, color = Ids.colors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                QtyButton("-") { setQty(qty - 1) }
                Text(qty.toString(), modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                QtyButton("+") { setQty(qty + 1) }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ids.colors.brand)
                    .clickable { setQty(maxOf(1, qty)) }
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
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        placeholder = { Text("Delivery address") },
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
                        } else if (o.status == "DELIVERED") {
                            OrderItemReviews(o)
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
                    EmptyState("No written reviews yet.", icon = Icons.Outlined.RateReview)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 4.dp)) {
                        list.forEach { rv ->
                            val stars = "★".repeat(rv.rating) + "☆".repeat(5 - rv.rating)
                            Text(
                                if (rv.comment.isNullOrBlank()) stars else "$stars — ${rv.comment}",
                                color = Ids.colors.textSecondary,
                                fontSize = 12.sp,
                            )
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
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            placeholder = { Text("How was it? (optional)") },
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
