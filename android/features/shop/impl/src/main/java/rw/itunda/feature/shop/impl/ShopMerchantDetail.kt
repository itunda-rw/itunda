package rw.itunda.feature.shop.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import rw.itunda.core.designsystem.components.pressScaleClickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import rw.itunda.core.designsystem.components.BackTopBar
import rw.itunda.core.designsystem.components.EmptyState
import rw.itunda.core.designsystem.components.QtyButton
import rw.itunda.core.designsystem.components.SkeletonBlock
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.CreateAffiliateLinkRequest
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.MerchantBillingPlanDto
import rw.itunda.core.network.MerchantBillingSubscriptionDto
import rw.itunda.core.network.ShoppingMerchantDto




@Composable
internal fun MerchantDetailView(
    merchant: ShoppingMerchantDto,
    products: List<MerchantProductDto>?,
    cart: SnapshotStateMap<String, CommerceCartLine>,
    onBack: () -> Unit,
    onViewCart: () -> Unit,
    onOpenProduct: (MerchantProductDto) -> Unit,
    favoriteProductIds: Set<String> = emptySet(),
    favoritingProductId: String? = null,
    onToggleFavorite: (String) -> Unit = {},
    following: Boolean = false,
    followBusy: Boolean = false,
    onToggleFollow: () -> Unit = {},
    billingPlans: List<MerchantBillingPlanDto> = emptyList(),
    mySubscriptions: List<MerchantBillingSubscriptionDto> = emptyList(),
    onBillingChanged: () -> Unit = {},
    onContactSeller: () -> Unit = {},
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
            ShopMessageSellerButton(onClick = onContactSeller)
            Spacer(modifier = Modifier.width(8.dp))
            // Real Naver Smart Store-style "알림받기" follow toggle -- see this
            // function's own doc comment above (item 117).
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (following) Ids.colors.surface else Ids.colors.brand)
                    .pressScaleClickable(enabled = !followBusy, onClick = onToggleFollow)
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
                        Column(modifier = Modifier.pressScaleClickable { onOpenProduct(p) }) {
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
                                            .pressScaleClickable(enabled = favoritingProductId != p.id) { onToggleFavorite(p.id) },
                                    )
                                    Icon(
                                        Icons.Outlined.Share,
                                        contentDescription = "Share & earn 3%",
                                        tint = Color.White,
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(18.dp)
                                            .pressScaleClickable(enabled = sharingProductId != p.id) { shareProduct(p.id) },
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
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProductRatingBadge(p.id)
                            if (p.isBestSeller) ShopBestSellerBadge()
                        }
                        ShopDeliveryEtaPill(merchant.deliveryTimeMinutes)
                        Spacer(modifier = Modifier.height(8.dp))
                        // Bookable-service products (a real durationMinutes set) are
                        // filtered out before reaching this grid -- see ShopScreen.kt's
                        // openMerchant's own doc comment. Every product here is a real
                        // cart-able good, so the qty stepper is unconditional now.
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            QtyButton("-") { setQty(p, qty - 1) }
                            Text(qty.toString(), modifier = Modifier.width(28.dp), textAlign = TextAlign.Center, color = Ids.colors.textPrimary, fontWeight = FontWeight.Bold)
                            QtyButton("+") { val stock = p.stockQuantity; if (stock == null || qty < stock) setQty(p, qty + 1) }
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
