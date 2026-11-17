package rw.itunda.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTheme
import androidx.compose.foundation.layout.Column
import rw.itunda.feature.eats.impl.EatsMenuItemRow
import rw.itunda.feature.eats.impl.EatsRestaurantHero

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun eatsLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background).padding(20.dp)) { EatsRestaurantHero("Kigali Bakery", {}) }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/eats-restaurant-light.png")
    }

    @Test fun menuRows() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(20.dp)) {
                    EatsMenuItemRow("Croissant", "Buttery, baked this morning", "1,200 RWF", null, null, 0, null, {}, {})
                    EatsMenuItemRow("Mandazi, 6 pieces", "Sweet fried dough", "1,000 RWF", null, null, 2, null, {}, {})
                    EatsMenuItemRow("Fruit juice", null, "1,800 RWF", "2,200 RWF", null, 0, "Options required — tap + to choose", {}, {})
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/eats-menu-rows.png")
    }

    @Test fun restaurantGrid() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(20.dp)) {
                    rw.itunda.feature.eats.impl.EatsRestaurantGridRow(
                        listOf(
                            rw.itunda.core.network.ShoppingMerchantDto("a", "Kigali Bakery", "Bakery", "2%", rating = 4.8, reviewCount = 320, deliveryTimeMinutes = 20, participatesInEatsMembership = true),
                            rw.itunda.core.network.ShoppingMerchantDto("b", "Inka Fried Chicken", "Local", "2%", rating = 4.6, reviewCount = 389, deliveryTimeMinutes = 25, minOrderAmount = 1000.0, maxDiscountPercent = 20),
                        ),
                        setOf("a"), {}, {},
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/eats-grid.png")
    }

    private fun header(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(20.dp)) {
                    rw.itunda.feature.eats.impl.EatsSearchHeader("", {}, listOf("Brochette", "Chicken", "Pizza", "Burgers"), null, {})
                    rw.itunda.feature.eats.impl.EatsPromoBanner(false, false, {}, Modifier.padding(top = 14.dp))
                    rw.itunda.feature.eats.impl.EatsQuickActions({}, {}, {}, {}, Modifier.padding(top = 14.dp))
                    androidx.compose.foundation.layout.Row(Modifier.padding(top = 14.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        rw.itunda.feature.eats.impl.EatsSortChip("Fastest", null, true) {}
                        rw.itunda.feature.eats.impl.EatsSortChip("Top rated", null, false) {}
                        rw.itunda.feature.eats.impl.EatsSortChip("Max discount", null, false) {}
                    }
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/eats-$name.png")
    }

    @Test fun headerLight() = header(false, "header-light")
    @Test fun headerDark() = header(true, "header-dark")

    @Test fun popularRail() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(20.dp)) {
                    rw.itunda.feature.eats.impl.EatsPopularRail(
                        listOf(
                            rw.itunda.core.network.ShoppingMerchantDto("a", "Nyamirambo Grill", "Local", "2%", rating = 4.7, reviewCount = 512, deliveryTimeMinutes = 30),
                            rw.itunda.core.network.ShoppingMerchantDto("b", "Remera Fries & Co.", "Fast food", "2%", rating = 4.4, reviewCount = 412, deliveryTimeMinutes = 20),
                            rw.itunda.core.network.ShoppingMerchantDto("c", "Inka Fried Chicken", "Local", "2%", rating = 4.6, reviewCount = 389, deliveryTimeMinutes = 25),
                        ),
                        {},
                    )
                    rw.itunda.feature.eats.impl.EatsSectionHeader("Restaurants near you", 14)
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/eats-popular.png")
    }
    private fun browseHeader(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background)) {
                    rw.itunda.feature.eats.impl.EatsBrowseHeader(
                        state = rw.itunda.feature.eats.impl.EatsBrowseState(),
                        searchInput = "",
                        cartCount = 2,
                        onSearchChange = {},
                        onOrdersClick = {},
                        onCartClick = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-browse-$name.png")
    }

    @Test fun browseHeaderLight() = browseHeader(false, "light")
    @Test fun browseHeaderDark() = browseHeader(true, "dark")

    private fun couponsSheet(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background)) {
                    rw.itunda.feature.eats.impl.EatsCouponsSheet(false, null, {}, {})
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-coupons-$name.png")
    }

    @Test fun couponsSheetLight() = couponsSheet(false, "light")
    @Test fun couponsSheetDark() = couponsSheet(true, "dark")

    @Test fun pickupBlocksDeliveryOnlyCoupon() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        state.isPickup = true
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column {
                    rw.itunda.feature.eats.impl.EatsBrowseHeader(state, "", 0, {}, {}, {})
                    rw.itunda.feature.eats.impl.EatsQuickActions({}, { state.showCoupons = true }, {}, {})
                }
            }
        }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use")[0].performClick()
        rule.onNodeWithText("Free delivery only applies to delivery orders.").assertExists()
    }

    private fun addressPickerSheet(dark: Boolean, name: String) {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                rw.itunda.feature.eats.impl.EatsBrowseHeader(state, "", 0, {}, {}, {})
            }
        }
        rule.onNodeWithContentDescription("Change delivery address").performClick()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-address-picker-$name.png")
    }

    @Test fun addressPickerSheetLight() = addressPickerSheet(false, "light")
    @Test fun addressPickerSheetDark() = addressPickerSheet(true, "dark")

    @Test fun browseHeaderPickupDark() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState().apply { isPickup = true }
        rule.setContent {
            IdsTheme(darkTheme = true) {
                rw.itunda.feature.eats.impl.EatsBrowseHeader(state, "", 2, {}, {}, {})
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/eats-browse-pickup-dark.png")
    }

    @Test fun deliveryAndPickupSelectionStayInSync() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent {
            IdsTheme(darkTheme = false) {
                rw.itunda.feature.eats.impl.EatsBrowseHeader(state, "", 0, {}, {}, {})
            }
        }
        rule.onNodeWithContentDescription("Pickup").performClick()
        assertTrue(state.isPickup)
        rule.onNodeWithContentDescription("Delivery").performClick()
        assertTrue(!state.isPickup)
    }

    @Test fun couponCanBeSaved() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent {
            IdsTheme(darkTheme = false) {
                rw.itunda.feature.eats.impl.EatsBrowseHeader(state, "", 0, {}, {}, {})
            }
        }
        rule.onNodeWithText("Coupons").performClick()
        rule.onAllNodesWithText("Use")[1].performClick()
        assertTrue(state.selectedCoupon == "EATS20")
    }

}
