package rw.itunda.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.network.EatsOrderDto
import rw.itunda.core.network.EatsOrderItemDto
import rw.itunda.core.network.ShoppingMerchantDto
import rw.itunda.feature.eats.impl.EatsTrackingScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsTrackingScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val items = listOf(EatsOrderItemDto("item-1", "EA-7207", "p1", "Croissant", 1200.0, 2))
    private val restaurant = ShoppingMerchantDto("m1", "Kigali Bakery", "BAKERY", "0", phoneNumber = "0780000000")

    private fun order(status: String, pickup: Boolean = false): EatsOrderDto {
        val hasRider = status in setOf("RIDER_ASSIGNED", "PICKED_UP", "DELIVERED") && !pickup
        return EatsOrderDto(
            id = "EA-7207",
            buyerId = "demo-buyer",
            restaurantId = "m1",
            riderId = if (hasRider) "rider-1" else null,
            deliveryAddress = if (pickup) "" else "Kimironko, KG 11 Ave",
            itemsSubtotal = 2400.0,
            deliveryFee = 0.0,
            platformFee = 0.0,
            totalAmount = 2400.0,
            transactionId = "demo-txn-7207",
            deliveryPayoutTransactionId = null,
            status = status,
            createdAt = "2026-11-17T14:00:00Z",
            updatedAt = "2026-11-17T14:00:00Z",
            deliveryNotes = "Please call when you arrive.",
            riderName = if (hasRider) "Jean Claude" else null,
            estimatedArrivalMinutes = if (hasRider) 8 else 20,
        )
    }

    private fun show(status: String, pickup: Boolean = false, dark: Boolean = false, name: String) {
        rule.activity.actionBar?.hide()
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                EatsTrackingScreen(
                    order = order(status, pickup),
                    restaurant = restaurant,
                    onBack = {},
                    onDone = {},
                    initialItems = items,
                )
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun deliveryAcceptedLight() {
        show("PLACED", name = "eats-prototype-tracking-delivery-accepted-light")
        rule.onNodeWithText("Call restaurant").assertExists()
        rule.onNodeWithText("Kigali Bakery, EA-7207 · 2 items").assertExists()
    }
    @Test fun deliveryPreparingLight() {
        show("PREPARING", name = "eats-prototype-tracking-delivery-preparing-light")
        rule.onNodeWithText("Arrives by", substring = true).assertExists()
        rule.onNodeWithText("Call restaurant").assertDoesNotExist()
        rule.onNodeWithText("Kigali Bakery, EA-7207").assertExists()
    }
    @Test fun deliveryOnTheWayLight() {
        show("PICKED_UP", name = "eats-prototype-tracking-delivery-on-the-way-light")
        rule.onNodeWithText("Your order").assertExists()
        rule.onNodeWithText("Call restaurant").assertDoesNotExist()
    }
    @Test fun deliveryCompleteLight() {
        show("DELIVERED", name = "eats-prototype-tracking-delivery-complete-light")
        rule.onNodeWithText("Call restaurant").assertExists()
    }
    @Test fun deliveryCancelledLight() {
        show("CANCELLED", name = "eats-prototype-tracking-cancelled-light")
        rule.onNodeWithText("Your order").assertExists()
        rule.onNodeWithText("Call restaurant").assertDoesNotExist()
    }
    @Test fun pickupReadyLight() {
        show("READY_FOR_PICKUP", pickup = true, name = "eats-prototype-tracking-pickup-ready-light")
        rule.onNodeWithText("Ready by", substring = true).assertExists()
        rule.onNodeWithText("Call restaurant").assertExists()
        rule.onNodeWithText("Kigali Bakery, EA-7207 · 2 items").assertExists()
    }
    @Test fun pickupCompleteLight() {
        show("PICKED_UP", pickup = true, name = "eats-prototype-tracking-pickup-complete-light")
        rule.onNodeWithText("Your order").assertExists()
        rule.onNodeWithText("Call restaurant").assertDoesNotExist()
    }
    @Test fun deliveryOnTheWayDark() = show("PICKED_UP", dark = true, name = "eats-prototype-tracking-delivery-on-the-way-dark")
    @Test fun deliveryCompleteDark() = show("DELIVERED", dark = true, name = "eats-prototype-tracking-delivery-complete-dark")
    @Test fun pickupReadyDark() {
        show("READY_FOR_PICKUP", pickup = true, dark = true, name = "eats-prototype-tracking-pickup-ready-dark")
        rule.onNodeWithText("Ready by", substring = true).assertExists()
    }

    @Test fun riderSheetLight() {
        rule.activity.actionBar?.hide()
        rule.setContent { IdsTheme(darkTheme = false) {
            EatsTrackingScreen(order("PICKED_UP"), restaurant, {}, {}, initialItems = items)
        } }
        rule.waitForIdle()
        rule.onNodeWithText("Message rider").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-prototype-tracking-rider-sheet-light.png")
    }

    @Test fun cancelSheetLight() {
        rule.activity.actionBar?.hide()
        rule.setContent { IdsTheme(darkTheme = false) {
            EatsTrackingScreen(order("PLACED"), restaurant, {}, {}, initialItems = items)
        } }
        rule.waitForIdle()
        rule.onNodeWithText("Cancel order").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-prototype-tracking-cancel-sheet-light.png")
    }

    @Test fun ratingSheetLight() {
        rule.activity.actionBar?.hide()
        rule.setContent { IdsTheme(darkTheme = false) {
            EatsTrackingScreen(order("DELIVERED"), restaurant, {}, {}, initialItems = items)
        } }
        rule.waitForIdle()
        rule.onNodeWithText("Rate your order").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-prototype-tracking-rating-sheet-light.png")
    }

    private fun showScrolled(
        status: String,
        pickup: Boolean = false,
        dark: Boolean = false,
        scrollOffset: Int,
        name: String,
    ) {
        rule.activity.actionBar?.hide()
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                EatsTrackingScreen(
                    order(status, pickup),
                    restaurant,
                    {},
                    {},
                    initialItems = items,
                    initialScrollOffset = scrollOffset,
                )
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun showPanel(status: String, dark: Boolean, action: String, name: String) {
        rule.activity.actionBar?.hide()
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                EatsTrackingScreen(order(status), null, {}, {}, initialItems = items)
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText(action).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun deliveryAcceptedDark() =
        show("PLACED", dark = true, name = "eats-prototype-tracking-delivery-accepted-dark")
    @Test fun deliveryPreparingDark() {
        show("PREPARING", dark = true, name = "eats-prototype-tracking-delivery-preparing-dark")
        rule.onNodeWithText("Arrives by", substring = true).assertExists()
    }
    @Test fun deliveryCancelledDark() =
        show("CANCELLED", dark = true, name = "eats-prototype-tracking-cancelled-dark")
    @Test fun pickupAcceptedLight() {
        show("PLACED", pickup = true, name = "eats-prototype-tracking-pickup-accepted-light")
        rule.onNodeWithText("Your order").assertExists()
        rule.onNodeWithText("Call restaurant").assertDoesNotExist()
        rule.onNodeWithText("Kigali Bakery, EA-7207").assertExists()
    }
    @Test fun pickupAcceptedDark() =
        show("PLACED", pickup = true, dark = true, name = "eats-prototype-tracking-pickup-accepted-dark")
    @Test fun pickupPreparingLight() =
        show("PREPARING", pickup = true, name = "eats-prototype-tracking-pickup-preparing-light")
    @Test fun pickupPreparingDark() =
        show("PREPARING", pickup = true, dark = true, name = "eats-prototype-tracking-pickup-preparing-dark")
    @Test fun pickupCompleteDark() =
        show("PICKED_UP", pickup = true, dark = true, name = "eats-prototype-tracking-pickup-complete-dark")

    @Test fun riderSheetDark() =
        showPanel("PICKED_UP", true, "Message rider", "eats-prototype-tracking-rider-sheet-dark")
    @Test fun cancelSheetDark() =
        showPanel("PLACED", true, "Cancel order", "eats-prototype-tracking-cancel-sheet-dark")
    @Test fun ratingSheetDark() =
        showPanel("DELIVERED", true, "Rate your order", "eats-prototype-tracking-rating-sheet-dark")

    @Test fun ratingSheetCanSelectThreeStarsLight() {
        rule.activity.actionBar?.hide()
        rule.setContent { IdsTheme(darkTheme = false) {
            EatsTrackingScreen(order("DELIVERED"), restaurant, {}, {}, initialItems = items)
        } }
        rule.waitForIdle()
        rule.onNodeWithText("Rate your order").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Rate 3 stars").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-prototype-tracking-rating-three-stars-light.png")
    }

    @Test fun ratingSheetCanSelectThreeStarsDark() {
        rule.activity.actionBar?.hide()
        rule.setContent { IdsTheme(darkTheme = true) {
            EatsTrackingScreen(order("DELIVERED"), restaurant, {}, {}, initialItems = items)
        } }
        rule.waitForIdle()
        rule.onNodeWithText("Rate your order").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Rate 3 stars").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-prototype-tracking-rating-three-stars-dark.png")
    }

    @Test fun deliveryAcceptedLightScroll19() =
        showScrolled("PLACED", scrollOffset = 19, name = "eats-prototype-tracking-delivery-accepted-light-scroll-19")
    @Test fun deliveryAcceptedLightScroll38() =
        showScrolled("PLACED", scrollOffset = 38, name = "eats-prototype-tracking-delivery-accepted-light-scroll-38")
    @Test fun deliveryAcceptedDarkScroll19() =
        showScrolled("PLACED", dark = true, scrollOffset = 19, name = "eats-prototype-tracking-delivery-accepted-dark-scroll-19")
    @Test fun deliveryAcceptedDarkScroll38() =
        showScrolled("PLACED", dark = true, scrollOffset = 38, name = "eats-prototype-tracking-delivery-accepted-dark-scroll-38")

    @Test fun deliveryCompleteLightScroll171() =
        showScrolled("DELIVERED", scrollOffset = 171, name = "eats-prototype-tracking-delivery-complete-light-scroll-171")
    @Test fun deliveryCompleteLightScroll342() =
        showScrolled("DELIVERED", scrollOffset = 342, name = "eats-prototype-tracking-delivery-complete-light-scroll-342")
    @Test fun deliveryCompleteDarkScroll171() =
        showScrolled("DELIVERED", dark = true, scrollOffset = 171, name = "eats-prototype-tracking-delivery-complete-dark-scroll-171")
    @Test fun deliveryCompleteDarkScroll342() =
        showScrolled("DELIVERED", dark = true, scrollOffset = 342, name = "eats-prototype-tracking-delivery-complete-dark-scroll-342")

    @Test fun deliveryOnTheWayLightScroll18() =
        showScrolled("PICKED_UP", scrollOffset = 18, name = "eats-prototype-tracking-delivery-on-the-way-light-scroll-18")
    @Test fun deliveryOnTheWayLightScroll35() =
        showScrolled("PICKED_UP", scrollOffset = 35, name = "eats-prototype-tracking-delivery-on-the-way-light-scroll-35")
    @Test fun deliveryOnTheWayDarkScroll18() =
        showScrolled("PICKED_UP", dark = true, scrollOffset = 18, name = "eats-prototype-tracking-delivery-on-the-way-dark-scroll-18")
    @Test fun deliveryOnTheWayDarkScroll35() =
        showScrolled("PICKED_UP", dark = true, scrollOffset = 35, name = "eats-prototype-tracking-delivery-on-the-way-dark-scroll-35")

    @Test fun cancelledDarkScroll118() =
        showScrolled("CANCELLED", dark = true, scrollOffset = 118, name = "eats-prototype-tracking-cancelled-dark-scroll-118")
    @Test fun cancelledDarkScroll236() =
        showScrolled("CANCELLED", dark = true, scrollOffset = 236, name = "eats-prototype-tracking-cancelled-dark-scroll-236")


    @Test fun pickupPreparingLightScroll112() =
        showScrolled("PREPARING", pickup = true, scrollOffset = 112, name = "eats-prototype-tracking-pickup-preparing-light-scroll-112")
    @Test fun pickupPreparingLightScroll223() =
        showScrolled("PREPARING", pickup = true, scrollOffset = 223, name = "eats-prototype-tracking-pickup-preparing-light-scroll-223")
    @Test fun pickupPreparingDarkScroll112() =
        showScrolled("PREPARING", pickup = true, dark = true, scrollOffset = 112, name = "eats-prototype-tracking-pickup-preparing-dark-scroll-112")
    @Test fun pickupPreparingDarkScroll223() =
        showScrolled("PREPARING", pickup = true, dark = true, scrollOffset = 223, name = "eats-prototype-tracking-pickup-preparing-dark-scroll-223")
    @Test fun deliveryPreparingLightScroll116() =
        showScrolled("PREPARING", scrollOffset = 116, name = "eats-prototype-tracking-delivery-preparing-light-scroll-116")
    @Test fun deliveryPreparingLightScroll233() =
        showScrolled("PREPARING", scrollOffset = 233, name = "eats-prototype-tracking-delivery-preparing-light-scroll-233")
    @Test fun deliveryPreparingDarkScroll116() =
        showScrolled("PREPARING", dark = true, scrollOffset = 116, name = "eats-prototype-tracking-delivery-preparing-dark-scroll-116")
    @Test fun deliveryPreparingDarkScroll233() =
        showScrolled("PREPARING", dark = true, scrollOffset = 233, name = "eats-prototype-tracking-delivery-preparing-dark-scroll-233")

    @Test fun cancelledLightScroll118() =
        showScrolled("CANCELLED", scrollOffset = 118, name = "eats-prototype-tracking-cancelled-light-scroll-118")
    @Test fun cancelledLightScroll236() =
        showScrolled("CANCELLED", scrollOffset = 236, name = "eats-prototype-tracking-cancelled-light-scroll-236")

    @Test fun pickupReadyLightScroll92() =
        showScrolled("READY_FOR_PICKUP", pickup = true, scrollOffset = 92, name = "eats-prototype-tracking-pickup-ready-light-scroll-92")
    @Test fun pickupReadyLightScroll183() =
        showScrolled("READY_FOR_PICKUP", pickup = true, scrollOffset = 183, name = "eats-prototype-tracking-pickup-ready-light-scroll-183")
    @Test fun pickupReadyDarkScroll92() =
        showScrolled("READY_FOR_PICKUP", pickup = true, dark = true, scrollOffset = 92, name = "eats-prototype-tracking-pickup-ready-dark-scroll-92")
    @Test fun pickupReadyDarkScroll183() =
        showScrolled("READY_FOR_PICKUP", pickup = true, dark = true, scrollOffset = 183, name = "eats-prototype-tracking-pickup-ready-dark-scroll-183")

}
