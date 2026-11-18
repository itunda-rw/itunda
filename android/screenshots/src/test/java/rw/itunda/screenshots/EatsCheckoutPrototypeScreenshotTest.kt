package rw.itunda.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.network.MerchantProductDto
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.feature.eats.impl.EatsCartLine
import rw.itunda.feature.eats.impl.EatsCheckoutMode
import rw.itunda.feature.eats.impl.EatsCheckoutView
import rw.itunda.feature.eats.impl.EatsPaymentMethod

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsCheckoutPrototypeScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val restaurant = ShoppingMerchantDto(
        merchantId = "m1",
        businessName = "Kigali Bakery",
        category = "Bakery",
        cashbackRate = "2%",
        minOrderAmount = 0.0,
    )

    private val menu = listOf(
        MerchantProductDto("p1", "m1", "Croissant", 1200.0, true, "2026-11-01"),
        MerchantProductDto("p2", "m1", "Mandazi, 6 pieces", 1000.0, true, "2026-11-01"),
        MerchantProductDto("p4", "m1", "Fruit juice", 1800.0, true, "2026-11-01"),
        MerchantProductDto("p5", "m1", "Hot milk tea", 1000.0, true, "2026-11-01"),
    )

    private fun render(
        dark: Boolean = false,
        mode: EatsCheckoutMode = EatsCheckoutMode.DELIVERY,
        coupon: String? = null,
        tip: Int = 0,
        scheduled: String? = null,
        payBalance: Double = 120500.0,
        paymentMethod: EatsPaymentMethod = EatsPaymentMethod.ITUNDA_PAY,
    ) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                EatsCheckoutView(
                    restaurant = restaurant,
                    cart = mutableStateMapOf("p1" to EatsCartLine("p1", 2)),
                    menu = menu,
                    initialMode = mode,
                    initialAddress = "Kimironko, KG 11 Ave",
                    savedCoupon = coupon,
                    initialPayBalance = payBalance,
                    initialTipAmount = tip,
                    initialScheduledTime = scheduled,
                    initialPaymentMethod = paymentMethod,
                    onBack = {},
                    onOrderPlaced = {},
                    onDineInOrderPlaced = {},
                    deviceStepUpHost = { _, _, _ -> },
                )
            }
        }
        rule.waitForIdle()
    }

    @Test fun deliveryLightTopAndSummaryScrollPositions() {
        render()
        rule.onNodeWithText("Cart").assertExists()
        rule.onNodeWithText("Add a side or drink").assertExists()
        rule.onNodeWithText("Delivery time").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-delivery-light-top.png")
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithText("You earn 24 points with this order").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-delivery-light-summary.png")
    }

    @Test fun deliveryDark() {
        render(dark = true)
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-delivery-dark.png")
    }

    @Test fun eats20CouponCalculatesTwentyPercentDiscount() {
        render(coupon = "EATS20")
        rule.onNodeWithText("EATS20 · 20% off items (max 2,000 RWF)").assertExists()
        rule.onNodeWithText("−480 RWF").assertExists()
        rule.onNodeWithText("1,920 RWF").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-eats20.png")
    }

    @Test fun freeDeliveryCouponCanBeRemoved() {
        render(coupon = "FREEDEL")
        rule.onNodeWithText("FREEDEL · Free delivery").assertExists()
        rule.onNodeWithContentDescription("Remove coupon").performScrollTo().performClick()
        assertFalse(rule.onAllNodesWithText("FREEDEL · Free delivery").fetchSemanticsNodes().isNotEmpty())
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-freedel-removed.png")
    }

    @Test fun pickupOmitsDeliveryTimeAndRiderTip() {
        render(mode = EatsCheckoutMode.PICKUP, coupon = "EATS20")
        rule.onNodeWithText("Pick up at").assertExists()
        assertFalse(rule.onAllNodesWithText("Delivery time").fetchSemanticsNodes().isNotEmpty())
        assertFalse(rule.onAllNodesWithText("Tip your rider").fetchSemanticsNodes().isNotEmpty())
        assertFalse(rule.onAllNodesWithText("Pickup").fetchSemanticsNodes().isEmpty())
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-pickup-light.png")
    }

    @Test fun riderTipChangesOrderTotal() {
        render(tip = 1000)
        rule.onNodeWithText("Rider tip").assertExists()
        rule.onNodeWithText("3,400 RWF").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-tip-1000.png")
    }

    @Test fun scheduledDeliverySelectionIsRestored() {
        render(scheduled = "12:30")
        rule.onNodeWithText("12:30").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-scheduled.png")
    }

    @Test fun insufficientBalanceDisablesPayment() {
        render(payBalance = 1000.0)
        rule.onNodeWithText("Not enough balance — you have 1,000 RWF").assertExists()
        rule.onNodeWithText("Pay 2,400 RWF").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-insufficient-balance.png")
    }

    @Test fun paymentMethodPageShowsAllMethods() {
        render()
        rule.onNodeWithContentDescription("Open payment method").performScrollTo().performClick()
        rule.onNodeWithText("itunda Pay").assertExists()
        rule.onNodeWithText("itunda Bank").assertExists()
        rule.onNodeWithText("Cash on delivery").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-payment-methods.png")
    }

    @Test fun bankPaymentSelectionReturnsToCart() {
        render(paymentMethod = EatsPaymentMethod.ITUNDA_BANK)
        rule.onNodeWithText("Pay with").performClick()
        rule.onNodeWithContentDescription("Payment option itunda Bank").performClick()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("itunda Bank · 842,300 RWF").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-payment-bank.png")
    }

    @Test fun deliveryAddressPageShowsSavedAddresses() {
        render()
        rule.onNodeWithText("Change").performClick()
        rule.onNodeWithText("Delivery address").assertExists()
        rule.onNodeWithText("Home").assertExists()
        rule.onNodeWithText("Work").assertExists()
        rule.onNodeWithText("Use this address").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-address-page.png")
    }

    @Test fun payOpensIdentityConfirmationBeforeSubmitting() {
        var placed = false
        rule.setContent {
            IdsTheme(darkTheme = false) {
                EatsCheckoutView(
                    restaurant = restaurant,
                    cart = mutableStateMapOf("p1" to EatsCartLine("p1", 2)),
                    menu = menu,
                    initialAddress = "Kimironko, KG 11 Ave",
                    onBack = {},
                    onOrderPlaced = { placed = true },
                    onDineInOrderPlaced = {},
                    deviceStepUpHost = { _, _, _ -> },
                )
            }
        }
        rule.onNodeWithText("Pay 2,400 RWF").performClick()
        rule.onNodeWithText("Confirm it’s you").assertExists()
        rule.onNodeWithText("Pay 2,400 RWF · Kigali Bakery").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-confirm-identity.png")
        assertFalse("The order must not submit before identity confirmation", placed)
    }

    @Test fun upsellAddsHotMilkTeaToCart() {
        render()
        rule.onNodeWithContentDescription("Add Hot milk tea").performClick()
        assertFalse(rule.onAllNodesWithText("Hot milk tea").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("3,400 RWF").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-upsell-added.png")
    }
}
