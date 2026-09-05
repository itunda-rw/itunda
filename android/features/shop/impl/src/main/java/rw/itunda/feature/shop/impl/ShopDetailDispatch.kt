package rw.itunda.feature.shop.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import retrofit2.HttpException
import rw.itunda.core.network.MerchantBillingPlanDto
import rw.itunda.core.network.MerchantBillingSubscriptionDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.NetworkClient
import rw.itunda.core.network.OrderDto
import rw.itunda.core.network.RecentlyViewedProductDto
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.core.network.ShoppingMissionDto
import rw.itunda.core.network.SpinOutcomeDto
import rw.itunda.core.network.superAppErrorMessage
import java.io.IOException

// Real fix (2026-08-26): split out of ShopScreen.kt once that file grew past its
// file-size-lint baseline. This is the checkout-results/cart/product/merchant detail
// dispatch that used to sit inline in CommerceShopContent -- mechanically identical
// behavior, just parameterized (explicit callbacks instead of captured `var`s) so it
// can live in its own file, same technique already used for EatsOrderFlowDispatch.kt.
// Returns true when it rendered a dispatch view (caller should `return` immediately
// after), false when the normal browse UI should render instead.
@Composable
internal fun ShopDetailDispatch(
    results: List<CommerceCheckoutResult>?,
    showCart: Boolean,
    cart: SnapshotStateMap<String, CommerceCartLine>,
    selectedMerchant: ShoppingMerchantDto?,
    selectedProduct: MerchantProductDto?,
    products: List<MerchantProductDto>?,
    favoriteProductIds: Set<String>,
    favoritingProductId: String?,
    followedMerchantIds: Set<String>,
    followBusyMerchantId: String?,
    billingPlans: List<MerchantBillingPlanDto>,
    mySubscriptions: List<MerchantBillingSubscriptionDto>,
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
    onResultsDone: () -> Unit,
    onCartBack: () -> Unit,
    onCartOrderPlaced: (List<CommerceCheckoutResult>) -> Unit,
    onRecentlyViewedAdded: (RecentlyViewedProductDto) -> Unit,
    onProductBack: () -> Unit,
    onViewCartFromProduct: () -> Unit,
    onToggleProductFavorite: (String) -> Unit,
    onMerchantBack: () -> Unit,
    onViewCart: () -> Unit,
    onOpenProduct: (MerchantProductDto) -> Unit,
    onToggleFollow: (String) -> Unit,
    onBillingChanged: (String) -> Unit,
    onContactSeller: (ShoppingMerchantDto) -> Unit,
): Boolean {
    val currentResults = results
    if (currentResults != null) {
        MultiCartResultsView(currentResults, onDone = onResultsDone)
        return true
    }

    if (showCart) {
        MultiCartView(
            cart = cart,
            onBack = onCartBack,
            onOrderPlaced = onCartOrderPlaced,
            deviceStepUpHost = deviceStepUpHost,
        )
        return true
    }

    val merchant = selectedMerchant
    val product = selectedProduct
    if (merchant != null && product != null) {
        LaunchedEffect(product.id) {
            onRecentlyViewedAdded(
                RecentlyViewedProductDto(product.id, merchant.merchantId, merchant.businessName, product.name, product.price, product.imageUrl, product.discountPercent),
            )
        }
        ProductDetailScreen(
            merchant = merchant,
            product = product,
            cart = cart,
            onBack = onProductBack,
            onViewCart = onViewCartFromProduct,
            favorited = product.id in favoriteProductIds,
            favoriteBusy = favoritingProductId == product.id,
            onToggleFavorite = { onToggleProductFavorite(product.id) },
            onContactSeller = { onContactSeller(merchant) },
        )
        return true
    }
    if (merchant != null) {
        MerchantDetailView(
            merchant = merchant,
            products = products,
            cart = cart,
            onBack = onMerchantBack,
            onViewCart = onViewCart,
            onOpenProduct = onOpenProduct,
            favoriteProductIds = favoriteProductIds,
            favoritingProductId = favoritingProductId,
            onToggleFavorite = onToggleProductFavorite,
            following = merchant.merchantId in followedMerchantIds,
            followBusy = followBusyMerchantId == merchant.merchantId,
            onToggleFollow = { onToggleFollow(merchant.merchantId) },
            billingPlans = billingPlans,
            mySubscriptions = mySubscriptions,
            onBillingChanged = { onBillingChanged(merchant.merchantId) },
            onContactSeller = { onContactSeller(merchant) },
        )
        return true
    }
    return false
}

// Real Coupang/Amazon-style "Buy it again" -- direct port of Eats' own real "Reorder"
// (see EatsScreen.kt's handleReorder). Re-populates the cross-merchant `cart` from a
// past order's still-active products and opens the cart for review. Extracted from
// ShopScreen.kt's own handleReorder with the same technique as the dispatch above --
// explicit callbacks instead of captured `var`s.
internal fun performShopReorder(
    order: OrderDto,
    cart: SnapshotStateMap<String, CommerceCartLine>,
    coroutineScope: CoroutineScope,
    onReorderingIdChanged: (String?) -> Unit,
    onError: (String?) -> Unit,
    onCartOpened: () -> Unit,
) {
    onReorderingIdChanged(order.id)
    onError(null)
    coroutineScope.launch {
        try {
            val orderDetail = NetworkClient.apiService.getOrder(order.id)
            val menuRes = NetworkClient.apiService.getMerchantProducts(order.merchantId)
            if (!orderDetail.success || !menuRes.success) {
                onError("Could not reorder.")
                return@launch
            }
            val activeProducts = menuRes.products.filter { it.active }.associateBy { it.id }
            var addedAny = false
            orderDetail.items.forEach { item ->
                val product = activeProducts[item.productId] ?: return@forEach
                val key = "${order.merchantId}:${product.id}"
                val existing = cart[key]
                cart[key] = CommerceCartLine(order.merchantId, menuRes.merchant.businessName, product, (existing?.quantity ?: 0) + item.quantity)
                addedAny = true
            }
            if (!addedAny) {
                onError("None of the items from that order are available anymore.")
                return@launch
            }
            onCartOpened()
        } catch (e: HttpException) {
            onError(superAppErrorMessage(e))
        } catch (e: IOException) {
            onError("Couldn't reach itunda. Check your connection and try again.")
        } finally {
            onReorderingIdChanged(null)
        }
    }
}

// Real Coupang-style shopping missions (check-in tasks/spin rewards) -- extracted
// from ShopScreen.kt's own loadMissions/completeMission.
internal fun loadShopMissions(
    coroutineScope: CoroutineScope,
    onLoaded: (List<ShoppingMissionDto>, List<SpinOutcomeDto>) -> Unit,
) {
    coroutineScope.launch {
        try {
            val res = NetworkClient.apiService.getShoppingMissions()
            if (res.success) onLoaded(res.missions, res.spinOutcomes)
        } catch (e: Exception) {
            // Real, non-critical -- the mission row just won't render if this fails.
        }
    }
}

internal fun completeShopMission(
    type: String,
    coroutineScope: CoroutineScope,
    onBusyTypeChanged: (String?) -> Unit,
    onFeedback: (String?) -> Unit,
    onCompleted: () -> Unit,
) {
    onBusyTypeChanged(type)
    coroutineScope.launch {
        try {
            val res = NetworkClient.apiService.completeShoppingMission(type, java.util.UUID.randomUUID().toString())
            onFeedback(String.format(Locale.US, "+%,.0f RWF", res.amountEarned))
            onCompleted()
        } catch (e: HttpException) {
            onFeedback(superAppErrorMessage(e))
        } catch (e: IOException) {
            onFeedback("Couldn't reach itunda. Check your connection and try again.")
        } finally {
            onBusyTypeChanged(null)
        }
    }
}

// Real Kakao Pay 정기결제/Toss 빌링키-style recurring merchant billing plans --
// extracted from ShopScreen.kt's own loadBillingForMerchant.
internal fun loadShopMerchantBilling(
    merchantId: String,
    coroutineScope: CoroutineScope,
    onBillingPlansLoaded: (List<MerchantBillingPlanDto>) -> Unit,
    onSubscriptionsLoaded: (List<MerchantBillingSubscriptionDto>) -> Unit,
) {
    coroutineScope.launch {
        try {
            onBillingPlansLoaded(NetworkClient.apiService.getMerchantBillingPlans(merchantId).plans)
        } catch (e: Exception) {
            // Real, non-critical -- same discipline as follow/wishlist status.
        }
    }
    coroutineScope.launch {
        try {
            onSubscriptionsLoaded(NetworkClient.apiService.getMyBillingSubscriptions().subscriptions.filter { it.merchantId == merchantId })
        } catch (e: Exception) {
            // Real, non-critical -- same discipline as follow/wishlist status.
        }
    }
}

// Real product-favorite (heart) state -- extracted from ShopScreen.kt's own
// loadFavoriteProductIds/toggleProductFavorite with the same explicit-callback
// technique as the rest of this file.
internal fun loadShopFavoriteProductIds(coroutineScope: CoroutineScope, onLoaded: (Set<String>) -> Unit) {
    coroutineScope.launch {
        try {
            val res = NetworkClient.apiService.getMyFavoriteProducts()
            if (res.success) onLoaded(res.favorites.map { it.productId }.toSet())
        } catch (e: Exception) {
            // Best-effort -- hearts just won't render as filled if this fails.
        }
    }
}

internal fun toggleShopProductFavorite(
    productId: String,
    favoriteProductIds: Set<String>,
    coroutineScope: CoroutineScope,
    onBusyIdChanged: (String?) -> Unit,
    onFavoriteIdsChanged: (Set<String>) -> Unit,
    onError: (String?) -> Unit,
) {
    onBusyIdChanged(productId)
    coroutineScope.launch {
        try {
            if (productId in favoriteProductIds) {
                NetworkClient.apiService.removeProductFavorite(productId)
                onFavoriteIdsChanged(favoriteProductIds - productId)
            } else {
                NetworkClient.apiService.addProductFavorite(productId)
                onFavoriteIdsChanged(favoriteProductIds + productId)
            }
        } catch (e: HttpException) {
            onError(superAppErrorMessage(e))
        } catch (e: IOException) {
            onError("Couldn't reach itunda. Check your connection and try again.")
        } finally {
            onBusyIdChanged(null)
        }
    }
}

// Real merchant-follow (Coupang/Amazon "Follow this store") state -- extracted from
// ShopScreen.kt's own loadFollowedMerchantIds/toggleFollow with the same explicit-
// callback technique as the rest of this file.
internal fun loadShopFollowedMerchantIds(coroutineScope: CoroutineScope, onLoaded: (Set<String>) -> Unit) {
    coroutineScope.launch {
        try {
            val res = NetworkClient.apiService.getMyFollowedMerchants()
            if (res.success) onLoaded(res.follows.map { it.merchantId }.toSet())
        } catch (e: Exception) {
            // Best-effort -- see ShopScreen.kt's loadFavoriteProductIds's own doc comment.
        }
    }
}

internal fun toggleShopMerchantFollow(
    merchantId: String,
    followedMerchantIds: Set<String>,
    coroutineScope: CoroutineScope,
    onBusyIdChanged: (String?) -> Unit,
    onFollowedIdsChanged: (Set<String>) -> Unit,
    onError: (String?) -> Unit,
) {
    onBusyIdChanged(merchantId)
    coroutineScope.launch {
        try {
            if (merchantId in followedMerchantIds) {
                NetworkClient.apiService.unfollowMerchant(merchantId)
                onFollowedIdsChanged(followedMerchantIds - merchantId)
            } else {
                NetworkClient.apiService.followMerchant(merchantId)
                onFollowedIdsChanged(followedMerchantIds + merchantId)
            }
        } catch (e: HttpException) {
            onError(superAppErrorMessage(e))
        } catch (e: IOException) {
            onError("Couldn't reach itunda. Check your connection and try again.")
        } finally {
            onBusyIdChanged(null)
        }
    }
}
