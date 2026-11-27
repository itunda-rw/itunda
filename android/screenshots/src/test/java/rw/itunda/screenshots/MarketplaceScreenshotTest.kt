package rw.itunda.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
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
import rw.itunda.feature.marketplace.impl.MarketplaceBrowseControls
import rw.itunda.feature.marketplace.impl.MarketplaceFilterSheet

/** Marketplace rows, detail, browse controls and filter sheet in both themes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h788dp-xxhdpi")
class MarketplaceScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun listing(id: String, title: String, price: Double, hood: String) = ListingDto(
        id = id,
        sellerId = "seller",
        title = title,
        description = "128 GB, comes with charger and case. Pick up in Kimironko.",
        price = price,
        category = "Electronics",
        status = "ACTIVE",
        createdAt = "2026-11-14T08:00:00Z",
        neighborhood = hood,
        likeCount = 10,
    )

    private fun captureRows(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    ListingRow(listing("1", "Samsung phone, barely used", 140000.0, "Kimironko"), isMine = false, onOpen = {})
                    ListingRow(listing("2", "Baby clothes, 0–12 months", 0.0, "Kimironko"), isMine = false, onOpen = {})
                    ListingRow(listing("3", "Wooden dining table, 6 seats", 90000.0, "Remera"), isMine = false, onOpen = {})
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun captureDetail(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                ListingDetailScreen(
                    listing = listing("1", "Samsung phone, barely used", 140000.0, "Kimironko"),
                    isMine = false,
                    onBack = {},
                    onChanged = {},
                    onMessageSeller = {},
                    onMakeOffer = { _, _ -> },
                    currentUserId = "buyer",
                )
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun captureBrowseControls(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    MarketplaceBrowseControls(
                        selectedCategory = "Electronics",
                        onCategorySelected = {},
                        sortLabel = "Latest",
                        filterCount = 1,
                        onFilterClick = {},
                        onSortClick = {},
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun captureFilterSheet(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                MarketplaceFilterSheet(
                    visible = true,
                    maxPrice = 50000,
                    selectedNeighborhood = "Kimironko",
                    neighborhoods = listOf("Kimironko", "Remera", "Kacyiru"),
                    onDismiss = {},
                    onApply = { _, _ -> },
                )
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun rowsLight() = captureRows(dark = false, name = "market-rows")
    @Test fun rowsDark() = captureRows(dark = true, name = "market-rows-dark")
    @Test fun detailLight() = captureDetail(dark = false, name = "market-detail")
    @Test fun detailDark() = captureDetail(dark = true, name = "market-detail-dark")
    @Test fun browseControlsLight() = captureBrowseControls(dark = false, name = "market-browse-controls")
    @Test fun browseControlsDark() = captureBrowseControls(dark = true, name = "market-browse-controls-dark")
    @Test fun filterSheetLight() = captureFilterSheet(dark = false, name = "market-filter")
    @Test fun filterSheetDark() = captureFilterSheet(dark = true, name = "market-filter-dark")
}
