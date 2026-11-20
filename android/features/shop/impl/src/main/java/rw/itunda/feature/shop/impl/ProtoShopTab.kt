package rw.itunda.feature.shop.impl

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

private sealed interface ShopStep {
    data object Home : ShopStep
    data class Product(val index: Int) : ShopStep
    data object Cart : ShopStep
    /** The existing backend-driven Shop (real orders, wishlist, checkout) stays reachable from the prototype home. */
    data object Orders : ShopStep
}

/**
 * Prototype-layout Shop flow: home -> product -> cart. Browsing uses demo data; the existing real Shop
 * (orders / wishlist / checkout against the backend) is reached via the home header and the cart's Pay button.
 */
@Composable
fun ProtoShopTab(
    deviceStepUpHost: @Composable (visible: Boolean, onDismiss: () -> Unit, onVerified: suspend () -> Unit) -> Unit,
    onMessageSeller: (String) -> Unit = {},
) {
    var step by remember { mutableStateOf<ShopStep>(ShopStep.Home) }
    val cart = remember { mutableStateMapOf<Int, Int>() }
    fun add(i: Int) { cart[i] = (cart[i] ?: 0) + 1 }
    BackHandler(enabled = step != ShopStep.Home) {
        step = when (step) { ShopStep.Cart, ShopStep.Orders -> ShopStep.Home; is ShopStep.Product -> ShopStep.Home; ShopStep.Home -> ShopStep.Home }
    }
    when (val s = step) {
        ShopStep.Home -> ShopHomeContent(
            onBack = {}, onOrders = { step = ShopStep.Orders }, onWishlist = { step = ShopStep.Orders },
            onProduct = { step = ShopStep.Product(it) }, onQuickAdd = { add(it) },
        )
        is ShopStep.Product -> ShopProductContent(
            index = s.index, onBack = { step = ShopStep.Home },
            onBuy = { add(s.index); step = ShopStep.Cart }, onAddToBag = { add(s.index) },
        )
        ShopStep.Cart -> ShopCartContent(
            lines = cart.entries.filter { it.value > 0 }.map { it.key to it.value },
            onBack = { step = ShopStep.Home },
            onAdjust = { i, d -> cart[i] = ((cart[i] ?: 0) + d).coerceAtLeast(0); if (cart.values.sum() == 0) step = ShopStep.Home },
            onPay = { step = ShopStep.Orders },
        )
        ShopStep.Orders -> CommerceShopContent(deviceStepUpHost, onMessageSeller)
    }
}
