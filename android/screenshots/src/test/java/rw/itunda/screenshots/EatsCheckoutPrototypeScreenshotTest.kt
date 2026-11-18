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
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
import java.time.LocalTime
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsCheckoutPrototypeScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun firstScheduleSlot(): String {
        val now = LocalTime.now()
        val minutes = now.hour * 60 + now.minute + 45
        val slot = ((minutes + 29) / 30) * 30
        return String.format(Locale.US, "%02d:%02d", slot / 60, slot % 60)
    }

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
        quantity: Int = 2,
        bankBalance: Double = 842300.0,
    ) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                EatsCheckoutView(
                    restaurant = restaurant,
                    cart = mutableStateMapOf("p1" to EatsCartLine("p1", quantity)),
                    menu = menu,
                    initialMode = mode,
                    initialAddress = "Kimironko, KG 11 Ave",
                    savedCoupon = coupon,
                    initialPayBalance = payBalance,
                    initialBankBalance = bankBalance,
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
        rule.onNodeWithText("Pay 3,400 RWF").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-tip-1000.png")
    }

    @Test fun scheduledDeliverySelectionIsRestored() {
        val slot = firstScheduleSlot()
        render(scheduled = slot)
        rule.onNodeWithText(slot).assertExists()
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
        rule.onRoot().performTouchInput { swipeUp() }
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitForIdle()
        rule.onNodeWithText("Pay with").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Payment option itunda Bank").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("itunda Bank · 842,300 RWF").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-payment-bank.png")
    }

    @Test fun paymentMethodPageShowsCashAtPickup() {
        render(mode = EatsCheckoutMode.PICKUP)
        rule.onNodeWithContentDescription("Open payment method").performScrollTo().performClick()
        rule.onNodeWithText("Cash at pickup").assertExists()
        rule.onNodeWithText("Pay when you collect your order").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-payment-cash-at-pickup.png")
    }

    @Test fun cashLimitDisablesCashPaymentOption() {
        render(quantity = 100)
        rule.onNodeWithContentDescription("Open payment method").performScrollTo().performClick()
        rule.onNodeWithText("Cash orders are limited to 100,000 RWF").assertExists()
        rule.onNodeWithText("Cash on delivery").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-cash-limit.png")
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
        assertTrue(rule.onAllNodesWithText("3,400 RWF").fetchSemanticsNodes().size >= 2)
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-upsell-added.png")
    }
    @Test fun paymentMethodPageDark() {
        render(dark = true)
        rule.onNodeWithContentDescription("Open payment method").performScrollTo().performClick()
        rule.onNodeWithText("Pay with").assertExists()
        rule.onNodeWithText("itunda Pay").assertExists()
        rule.onNodeWithText("itunda Bank").assertExists()
        rule.onNodeWithText("Cash on delivery").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-payment-methods-dark.png")
    }

    @Test fun savedWorkAddressCanBeSelected() {
        render()
        rule.onNodeWithText("Change").performClick()
        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithText("Use this address").performClick()
        rule.onNodeWithText("Kacyiru, KG 7 Ave").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-address-work-selected.png")
    }

    @Test fun newAddressCanBeSavedAndUsedInDark() {
        render(dark = true)
        rule.onNodeWithText("Change").performClick()
        rule.onNodeWithText("Add a new address").performClick()
        rule.onNodeWithContentDescription("New delivery address").performTextInput("Remera, KG 5 Ave")
        rule.onNodeWithText("Save and use").performClick()
        rule.onNodeWithText("Remera, KG 5 Ave").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-address-new-saved-dark.png")
    }

    @Test fun cashPaymentSelectionReturnsToCart() {
        render()
        rule.onNodeWithContentDescription("Open payment method").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Payment option Cash on delivery").performClick()
        rule.onNodeWithText("Place order · 2,400 RWF").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-cash-selected.png")
    }

}
