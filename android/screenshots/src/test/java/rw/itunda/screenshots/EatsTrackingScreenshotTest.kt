package rw.itunda.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import rw.itunda.feature.eats.impl.EatsTrackingScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsTrackingScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private val items = listOf(EatsOrderItemDto("item-1", "EA-7207", "p1", "Croissant", 1200.0, 2))

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
            deliveryNotes = "Leave it at the door",
            riderName = if (hasRider) "Jean Claude" else null,
            estimatedArrivalMinutes = if (hasRider) 8 else 20,
        )
    }

    private fun show(status: String, pickup: Boolean = false, dark: Boolean = false, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                EatsTrackingScreen(
                    order = order(status, pickup),
                    restaurant = null,
                    onBack = {},
                    onDone = {},
                    initialItems = items,
                )
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun deliveryAcceptedLight() = show("PLACED", name = "eats-prototype-tracking-delivery-accepted-light")
    @Test fun deliveryPreparingLight() = show("PREPARING", name = "eats-prototype-tracking-delivery-preparing-light")
    @Test fun deliveryOnTheWayLight() = show("PICKED_UP", name = "eats-prototype-tracking-delivery-on-the-way-light")
    @Test fun deliveryCompleteLight() = show("DELIVERED", name = "eats-prototype-tracking-delivery-complete-light")
    @Test fun deliveryCancelledLight() = show("CANCELLED", name = "eats-prototype-tracking-cancelled-light")
    @Test fun pickupReadyLight() = show("READY_FOR_PICKUP", pickup = true, name = "eats-prototype-tracking-pickup-ready-light")
    @Test fun pickupCompleteLight() = show("PICKED_UP", pickup = true, name = "eats-prototype-tracking-pickup-complete-light")
    @Test fun deliveryOnTheWayDark() = show("PICKED_UP", dark = true, name = "eats-prototype-tracking-delivery-on-the-way-dark")
    @Test fun deliveryCompleteDark() = show("DELIVERED", dark = true, name = "eats-prototype-tracking-delivery-complete-dark")
    @Test fun pickupReadyDark() = show("READY_FOR_PICKUP", pickup = true, dark = true, name = "eats-prototype-tracking-pickup-ready-dark")

    @Test fun riderSheetLight() {
        rule.setContent { IdsTheme(darkTheme = false) {
            EatsTrackingScreen(order("PICKED_UP"), null, {}, {}, initialItems = items)
        } }
        rule.waitForIdle()
        rule.onNodeWithText("Message rider").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-prototype-tracking-rider-sheet-light.png")
    }

    @Test fun cancelSheetLight() {
        rule.setContent { IdsTheme(darkTheme = false) {
            EatsTrackingScreen(order("PLACED"), null, {}, {}, initialItems = items)
        } }
        rule.waitForIdle()
        rule.onNodeWithText("Cancel order").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-prototype-tracking-cancel-sheet-light.png")
    }

    @Test fun ratingSheetLight() {
        rule.setContent { IdsTheme(darkTheme = false) {
            EatsTrackingScreen(order("DELIVERED"), null, {}, {}, initialItems = items)
        } }
        rule.waitForIdle()
        rule.onNodeWithText("★ Rate your order").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-prototype-tracking-rating-sheet-light.png")
    }
}
