package rw.itunda.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.network.ListingDto
import rw.itunda.feature.marketplace.impl.ListingDetailScreen
import rw.itunda.feature.marketplace.impl.ListingRow

/** Marketplace list rows and listing detail with the prototype's sample listing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class MarketplaceScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun listing(id: String, title: String, price: Double, hood: String) = ListingDto(
        id = id, sellerId = "seller", title = title, description = "128 GB, comes with charger and case. Pick up in Kimironko.",
        price = price, category = "Electronics", status = "ACTIVE", createdAt = "2026-11-14T08:00:00Z", neighborhood = hood, likeCount = 10,
    )

    @Test fun rows() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(androidx.compose.ui.Modifier.padding(horizontal = 20.dp)) {
                    ListingRow(listing("1", "Samsung phone, barely used", 140000.0, "Kimironko"), isMine = false, onOpen = {})
                    ListingRow(listing("2", "Baby clothes, 0–12 months", 0.0, "Kimironko"), isMine = false, onOpen = {})
                    ListingRow(listing("3", "Wooden dining table, 6 seats", 90000.0, "Remera"), isMine = false, onOpen = {})
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/market-rows.png")
    }

    @Test fun detail() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                ListingDetailScreen(
                    listing = listing("1", "Samsung phone, barely used", 140000.0, "Kimironko"), isMine = false,
                    onBack = {}, onChanged = {}, onMessageSeller = {}, onMakeOffer = { _, _ -> },
                    currentUserId = "buyer",
                )
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/market-detail.png")
    }
}
