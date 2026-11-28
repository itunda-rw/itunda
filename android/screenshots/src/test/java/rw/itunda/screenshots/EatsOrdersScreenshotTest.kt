package rw.itunda.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.network.NetworkClient
import rw.itunda.feature.eats.impl.EatsContent

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsOrdersScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun enablePrototypeDemo() {
        NetworkClient.demoMode = true
        NetworkClient.demoEatsOrdersFixture = false
    }

    @After fun disablePrototypeDemo() {
        NetworkClient.demoEatsOrdersFixture = false
        NetworkClient.demoMode = false
    }

    private fun showOrders(
        dark: Boolean,
        filled: Boolean,
        report: AtomicReference<Pair<String, String?>?>? = null,
    ) {
        NetworkClient.demoMode = true
        NetworkClient.demoEatsOrdersFixture = filled
        rule.activity.actionBar?.hide()
        val sessionKey = Any()
        rule.setContent {
            key(sessionKey) {
                IdsTheme(darkTheme = dark) {
                    EatsContent(
                        deviceStepUpHost = { _, _, _ -> },
                        onReportIssue = { transactionId, reason -> report?.set(transactionId to reason) },
                    )
                }
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("Orders").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Orders").performClick()
        if (filled) {
            // Separate waits identify whether restaurant lookup or item-detail loading stalled.
            rule.waitUntil(60_000) {
                rule.onAllNodesWithText("Kigali Bakery").fetchSemanticsNodes().size >= 2
            }
            rule.waitUntil(60_000) {
                rule.onAllNodesWithText("2 × Croissant").fetchSemanticsNodes().size >= 2
            }
        } else {
            rule.waitUntil(60_000) {
                rule.onAllNodesWithText("No orders yet").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun capture(name: String) = rule.onRoot().captureRoboImage("build/screenshots/$name.png")

    @Test fun ordersEmptyLight() {
        showOrders(dark = false, filled = false)
        capture("eats-prototype-orders-empty-light")
        rule.onNodeWithText("Browse restaurants").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("Search restaurants").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun ordersEmptyDark() {
        showOrders(dark = true, filled = false)
        capture("eats-prototype-orders-empty-dark")
    }

    @Test fun ordersFilledLight() {
        showOrders(dark = false, filled = true)
        rule.onAllNodesWithText("Kigali Bakery").assertCountEquals(2)
        rule.onAllNodesWithText("2 × Croissant").assertCountEquals(2)
        rule.onNodeWithText("Cancelled").assertExists()
        rule.onAllNodesWithText("Cancelled — refunded").assertCountEquals(0)
        capture("eats-prototype-orders-filled-light")
    }

    @Test fun ordersFilledDark() {
        showOrders(dark = true, filled = true)
        rule.onAllNodesWithText("Kigali Bakery").assertCountEquals(2)
        rule.onAllNodesWithText("2 × Croissant").assertCountEquals(2)
        rule.onNodeWithText("Cancelled").assertExists()
        rule.onAllNodesWithText("Cancelled — refunded").assertCountEquals(0)
        capture("eats-prototype-orders-filled-dark")
    }

    private fun receipt(dark: Boolean, name: String) {
        showOrders(dark = dark, filled = true)
        rule.onAllNodesWithText("Receipt")[0].performClick()
        rule.waitUntil(60_000) {
            rule.onAllNodesWithText("2 × Croissant").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("2,400 RWF").assertCountEquals(3)
        capture("eats-prototype-orders-receipt-$name")
        rule.onNodeWithText("OK").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Orders").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun receiptLight() = receipt(false, "light")
    @Test fun receiptDark() = receipt(true, "dark")

    private fun help(dark: Boolean, selected: Boolean, name: String, verifySubmit: Boolean = false) {
        val report = AtomicReference<Pair<String, String?>?>(null)
        showOrders(dark = dark, filled = true, report = report)
        rule.onAllNodesWithText("Help")[0].performClick()
        rule.waitUntil(60_000) {
            rule.onAllNodesWithText("Report a problem").fetchSemanticsNodes().isNotEmpty()
        }
        if (selected) {
            rule.onNodeWithText("Food was cold").performClick()
            rule.waitForIdle()
        } else {
            rule.onNodeWithText("Send report").assertIsNotEnabled()
        }
        capture("eats-prototype-orders-help-$name")
        if (verifySubmit) {
            rule.onNodeWithText("Send report").performClick()
            rule.waitUntil(60_000) {
                rule.onAllNodesWithText("Orders").fetchSemanticsNodes().isNotEmpty()
            }
            assert(report.get() == ("demo-txn-7207" to "Food was cold")) {
                "Expected selected help reason to reach support hand-off; got ${report.get()}"
            }
        } else {
            rule.onNodeWithText("Cancel").performClick()
            rule.waitUntil(10_000) {
                rule.onAllNodesWithText("Orders").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test fun helpLight() = help(false, false, "light")
    @Test fun helpDark() = help(true, false, "dark")
    @Test fun helpSelectedLight() = help(false, true, "selected-light", verifySubmit = true)
    @Test fun helpSelectedDark() = help(true, true, "selected-dark", verifySubmit = true)

    @Test fun reorderKeepsProductsWithOptionalAddOns() {
        showOrders(dark = false, filled = true)
        rule.onAllNodesWithText("Reorder")[0].performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("Checkout (2 items)").fetchSemanticsNodes().isNotEmpty()
        }
        capture("eats-prototype-restaurant-menu-cart-filled-light")
    }

    @Test fun trackFromCancelledOrderOpensTracking() {
        showOrders(dark = false, filled = true)
        rule.onNodeWithText("Track").performClick()
        rule.onNodeWithText("Order cancelled").assertExists()
        capture("eats-prototype-orders-track-cancelled-light")
    }
}
