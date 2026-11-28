package rw.itunda.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.feature.eats.impl.EatsCheckoutMode
import rw.itunda.feature.eats.impl.EatsCheckoutView

/** Empty-cart checkout baselines in both prototype themes; empty carts must not submit. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h900dp-xxhdpi")
class EatsCheckoutParityTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun renderEmptyCheckout(dark: Boolean, mode: EatsCheckoutMode, name: String) {
        var orderPlaced = false
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                EatsCheckoutView(
                    restaurant = ShoppingMerchantDto(
                        merchantId = "eats-checkout-fixture",
                        businessName = "Kigali Bakery",
                        category = "Bakery",
                        cashbackRate = "2%",
                    ),
                    cart = mutableStateMapOf(),
                    menu = emptyList(),
                    initialMode = mode,
                    initialAddress = "Kimironko, KG 11 Ave",
                    onBack = {},
                    onOrderPlaced = { orderPlaced = true },
                    onDineInOrderPlaced = {},
                    deviceStepUpHost = { _, _, _ -> },
                )
            }
        }

        rule.onNodeWithText("Cart").assertExists()
        rule.onNodeWithText("Your cart is empty").assertExists()
        rule.onNodeWithText("Back to the menu").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-empty-$name.png")
        assertFalse("An empty cart must never place an order", orderPlaced)
    }

    @Test fun emptyDeliveryCheckoutLight() =
        renderEmptyCheckout(false, EatsCheckoutMode.DELIVERY, "delivery-light")

    @Test fun emptyDeliveryCheckoutDark() =
        renderEmptyCheckout(true, EatsCheckoutMode.DELIVERY, "delivery-dark")

    @Test fun emptyPickupCheckoutLight() =
        renderEmptyCheckout(false, EatsCheckoutMode.PICKUP, "pickup-light")

    @Test fun emptyPickupCheckoutDark() =
        renderEmptyCheckout(true, EatsCheckoutMode.PICKUP, "pickup-dark")
}
