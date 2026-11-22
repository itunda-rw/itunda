package rw.itunda.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsNotEnabled
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
import rw.itunda.feature.eats.impl.EatsHelpSheet
import rw.itunda.feature.eats.impl.EatsReceiptSheet

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsOrdersScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val order = EatsOrderDto(
        id = "EA-7207",
        buyerId = "demo-buyer",
        restaurantId = "bakery",
        riderId = null,
        deliveryAddress = "Kimironko, KG 11 Ave",
        itemsSubtotal = 2400.0,
        deliveryFee = 0.0,
        platformFee = 0.0,
        totalAmount = 2400.0,
        transactionId = "demo-txn-7207",
        deliveryPayoutTransactionId = null,
        status = "DELIVERED",
        createdAt = "2026-11-17T14:00:00Z",
        updatedAt = "2026-11-17T14:00:00Z",
        deliveryNotes = "Please call when you arrive.",
    )
    private val items = listOf(EatsOrderItemDto("item-1", "EA-7207", "croissant", "Croissant", 1200.0, 2))

    private fun receipt(dark: Boolean, name: String) {
        rule.activity.actionBar?.hide()
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                EatsReceiptSheet(order, "Kigali Bakery", items, onDismiss = {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Receipt").assertExists()
        rule.onNodeWithText("2 × Croissant").assertExists()
        rule.onNodeWithText("2,400 RWF").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-orders-receipt-$name.png")
    }

    @Test fun receiptLight() = receipt(false, "light")
    @Test fun receiptDark() = receipt(true, "dark")

    private fun help(dark: Boolean, selected: Boolean, name: String) {
        rule.activity.actionBar?.hide()
        var reason: String? = null
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                EatsHelpSheet(
                    order = order,
                    restaurantName = "Kigali Bakery",
                    selectedReason = reason,
                    onReasonSelected = { reason = it },
                    onSubmit = {},
                    onDismiss = {},
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Report a problem").assertExists()
        if (selected) {
            rule.onNodeWithText("Food was cold").performClick()
            rule.waitForIdle()
        } else {
            rule.onNodeWithText("Send report").assertIsNotEnabled()
        }
        rule.onRoot().captureRoboImage("build/screenshots/eats-orders-help-$name.png")
    }

    @Test fun helpLight() = help(false, false, "light")
    @Test fun helpSelectedLight() = help(false, true, "selected-light")
    @Test fun helpDark() = help(true, false, "dark")
    @Test fun helpSelectedDark() = help(true, true, "selected-dark")
}
