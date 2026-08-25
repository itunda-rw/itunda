package rw.itunda.feature.eats.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import rw.itunda.core.network.DineInOrderDto
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.ShoppingMerchantDto

// Real fix (2026-08-26): split out of EatsScreen.kt once that file grew past its
// file-size-lint baseline. This is the order-confirmation/checkout/menu dispatch
// that used to sit inline at the top of OrderFoodContent -- mechanically identical
// behavior, just parameterized (explicit callbacks instead of captured `var`s) so it
// can live in its own file. Returns true when it rendered a dispatch view (caller
// should `return` immediately after), false when none of confirmedOrder/
// confirmedDineInOrder/selectedRestaurant apply and the normal browse UI should render.
@Composable
internal fun EatsOrderFlowDispatch(
    confirmedOrder: EatsOrderDto?,
    confirmedDineInOrder: DineInOrderDto?,
    selectedRestaurant: ShoppingMerchantDto?,
    showCheckout: Boolean,
    menu: List<MerchantProductDto>?,
    cart: SnapshotStateMap<String, EatsCartLine>,
    deviceStepUpHost: @Composable (Boolean, () -> Unit, suspend () -> Unit) -> Unit,
    onOrderConfirmationDone: () -> Unit,
    onDineInConfirmationDone: () -> Unit,
    onCheckoutBack: () -> Unit,
    onOrderPlaced: (EatsOrderDto) -> Unit,
    onDineInOrderPlaced: (DineInOrderDto) -> Unit,
    onMenuBack: () -> Unit,
    onCheckoutRequested: () -> Unit,
): Boolean {
    if (confirmedOrder != null) {
        EatsOrderConfirmationView(confirmedOrder, onDone = onOrderConfirmationDone)
        return true
    }
    if (confirmedDineInOrder != null) {
        DineInOrderConfirmationView(confirmedDineInOrder, onDone = onDineInConfirmationDone)
        return true
    }
    if (selectedRestaurant != null) {
        if (showCheckout) {
            EatsCheckoutView(
                restaurant = selectedRestaurant,
                cart = cart,
                menu = menu.orEmpty(),
                onBack = onCheckoutBack,
                onOrderPlaced = onOrderPlaced,
                onDineInOrderPlaced = onDineInOrderPlaced,
                deviceStepUpHost = deviceStepUpHost,
            )
        } else {
            RestaurantMenuView(
                restaurant = selectedRestaurant,
                menu = menu,
                cart = cart,
                onBack = onMenuBack,
                onCheckout = onCheckoutRequested,
            )
        }
        return true
    }
    return false
}
