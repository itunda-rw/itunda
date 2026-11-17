package rw.itunda.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun eatsLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background).padding(20.dp)) { EatsRestaurantHero("Kigali Bakery", {}) }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/eats-restaurant-light.png")
    }

    private fun pickupGrid(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(20.dp)) {
                    rw.itunda.feature.eats.impl.EatsRestaurantGridRow(
                        listOf(
                            rw.itunda.core.network.ShoppingMerchantDto("a", "Kigali Bakery", "Bakery", "2%", rating = 4.8, reviewCount = 320, deliveryTimeMinutes = 20, participatesInEatsMembership = true),
                            rw.itunda.core.network.ShoppingMerchantDto("b", "Inka Fried Chicken", "Local", "2%", rating = 4.6, reviewCount = 389, deliveryTimeMinutes = 25, minOrderAmount = 1000.0, maxDiscountPercent = 20),
                        ),
                        emptySet(), {}, {}, isPickup = true,
                    )
                }
            }
        }
        rule.onNodeWithText("Ready in 10 min", substring = true).assertExists()
        rule.onNodeWithText("Ready in 15 min", substring = true).assertExists()
        assertEquals(2, rule.onAllNodesWithText("Pickup · No delivery fee", substring = true).fetchSemanticsNodes().size)
        rule.onRoot().captureRoboImage("build/screenshots/eats-pickup-grid-$name.png")
    }

    @Test fun pickupGridLight() = pickupGrid(false, "light")
    @Test fun pickupGridDark() = pickupGrid(true, "dark")

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

    @Test fun restaurantMenuHasPrototypeTabsAndPickupMeta() {
        val restaurant = rw.itunda.core.network.ShoppingMerchantDto(
            merchantId = "a",
            businessName = "Kigali Bakery",
            category = "Bakery",
            cashbackRate = "2%",
            distanceKm = 1.2,
            deliveryTimeMinutes = 20,
            phoneNumber = "0788 000 101",
            openingHours = "06:30 – 20:00",
            minOrderAmount = 2000.0,
        )
        rule.setContent {
            IdsTheme(darkTheme = false) {
                rw.itunda.feature.eats.impl.RestaurantMenuView(
                    restaurant = restaurant,
                    menu = emptyList(),
                    cart = androidx.compose.runtime.mutableStateMapOf<String, rw.itunda.feature.eats.impl.EatsCartLine>(),
                    onBack = {},
                    onCheckout = {},
                    isPickup = true,
                )
            }
        }
        rule.onNodeWithText("Ready in 10 min", substring = true).assertExists()
        rule.onNodeWithText("Pickup · No delivery fee", substring = true).assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-restaurant-hero-bakery.png")
        rule.onNodeWithText("Menu").assertExists()
        rule.onNodeWithText("Info").performClick()
        rule.onNodeWithText("Opening hours").assertExists()
        rule.onNodeWithText("06:30 – 20:00").assertExists()
        rule.onNodeWithText("Reviews").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Reviews").assertExists()
    }

    @Test fun eatsCheckoutDeliveryShowsRiderRequestsAndMinimumOrder() {
        val restaurant = rw.itunda.core.network.ShoppingMerchantDto(
            merchantId = "a", businessName = "Kigali Bakery", category = "Bakery",
            cashbackRate = "2%", minOrderAmount = 5000.0,
        )
        val item = rw.itunda.core.network.MerchantProductDto(
            id = "item", merchantId = "a", name = "Croissant", price = 2000.0,
            active = true, createdAt = "2026-01-01",
        )
        rule.setContent {
            IdsTheme(darkTheme = false) {
                rw.itunda.feature.eats.impl.EatsCheckoutView(
                    restaurant = restaurant,
                    cart = androidx.compose.runtime.mutableStateMapOf("item" to rw.itunda.feature.eats.impl.EatsCartLine("item", 1)),
                    menu = listOf(item),
                    initialMode = rw.itunda.feature.eats.impl.EatsCheckoutMode.DELIVERY,
                    savedCoupon = "EATS20",
                    onBack = {},
                    onOrderPlaced = {},
                    onDineInOrderPlaced = {},
                    deviceStepUpHost = { _, _, _ -> },
                )
            }
        }
        rule.onNodeWithText("Saved coupon").assertExists()
        rule.onNodeWithText("EATS20").assertExists()
        rule.onNodeWithText("Request for the rider").assertExists()
        rule.onNodeWithText("Leave at the door").assertExists()
        rule.onNodeWithText("Hand it to me").assertExists()
        rule.onNodeWithText("Call when nearby").assertExists()
        rule.onNodeWithText("Add 3,000 RWF more to reach the minimum order.").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-delivery.png")
    }

    @Test fun eatsCheckoutPickupShowsSavedCoupon() {
        val restaurant = rw.itunda.core.network.ShoppingMerchantDto(
            merchantId = "a", businessName = "Kigali Bakery", category = "Bakery", cashbackRate = "2%",
        )
        val item = rw.itunda.core.network.MerchantProductDto(
            id = "item", merchantId = "a", name = "Croissant", price = 2000.0,
            active = true, createdAt = "2026-01-01",
        )
        rule.setContent {
            IdsTheme(darkTheme = true) {
                rw.itunda.feature.eats.impl.EatsCheckoutView(
                    restaurant = restaurant,
                    cart = androidx.compose.runtime.mutableStateMapOf("item" to rw.itunda.feature.eats.impl.EatsCartLine("item", 1)),
                    menu = listOf(item),
                    initialMode = rw.itunda.feature.eats.impl.EatsCheckoutMode.PICKUP,
                    savedCoupon = "EATS20",
                    onBack = {},
                    onOrderPlaced = {},
                    onDineInOrderPlaced = {},
                    deviceStepUpHost = { _, _, _ -> },
                )
            }
        }
        rule.onNodeWithText("Saved coupon").assertExists()
        rule.onNodeWithText("EATS20").assertExists()
        rule.onNodeWithText("No delivery fee -- collect your order at the restaurant once it's ready").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-checkout-pickup-dark.png")
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
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state, darkTheme = dark) }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-browse-$name.png")
    }

    @Test fun browseHeaderLight() = browseHeader(false, "light")
    @Test fun browseHeaderDark() = browseHeader(true, "dark")

    @Test fun headerCouponEntryOpensSheet() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state) }

        rule.onNodeWithText("Kimironko").assertExists()
        rule.onNodeWithText("Search restaurants or dishes").assertExists()
        rule.onNodeWithContentDescription("Delivery").assertExists()
        rule.onNodeWithContentDescription("Pickup").assertExists()
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Your coupons").assertExists()
    }

    private fun couponsSheet(dark: Boolean, name: String) {
        rule.activity.actionBar?.hide()
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state, darkTheme = dark) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Your coupons").assertExists()
        rule.onNodeWithText("Free delivery").assertExists()
        rule.onNodeWithText("20% off").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-coupons-$name.png")
    }

    @Test fun couponsSheetLight() = couponsSheet(false, "light")
    @Test fun couponsSheetDark() = couponsSheet(true, "dark")

    @Test fun promoBannerOnlySavesCouponForDelivery() {
        var deliverySaved = false
        rule.setContent {
            IdsTheme(darkTheme = false) {
                rw.itunda.feature.eats.impl.EatsPromoBanner(false, false, { deliverySaved = true })
            }
        }
        rule.onNodeWithText("Free delivery on your first order").performClick()
        assertTrue(deliverySaved)

        var pickupSaved = false
        rule.setContent {
            IdsTheme(darkTheme = false) {
                rw.itunda.feature.eats.impl.EatsPromoBanner(true, false, { pickupSaved = true })
            }
        }
        rule.onNodeWithText("20% off your meal").performClick()
        assertTrue(pickupSaved)
    }

    @Test fun pickupBlocksDeliveryOnlyCoupon() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState().apply { isPickup = true }
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use")[0].performClick()
        rule.waitForIdle()
        assertNull(state.selectedCoupon)
        assertTrue(rule.onAllNodesWithText("Your coupons").fetchSemanticsNodes().isEmpty())
    }

    private fun addressPickerSheet(dark: Boolean, name: String) {
        rule.activity.actionBar?.hide()
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state, darkTheme = dark) }
        rule.onNodeWithContentDescription("Change delivery address").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Your rider will go to the address you pick.").assertExists()
        rule.onNodeWithText("Add a new address").assertExists()
        rule.onNodeWithText("Use this address").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-address-picker-$name.png")
    }

    private fun addressPickerForm(dark: Boolean, name: String) {
        rule.activity.actionBar?.hide()
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state, darkTheme = dark) }
        rule.onNodeWithContentDescription("Change delivery address").performClick()
        rule.onNodeWithText("Add a new address").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Street or landmark").assertExists()
        rule.onRoot().captureRoboImage("build/screenshots/eats-address-form-$name.png")
    }

    @Test fun addressPickerSheetLight() = addressPickerSheet(false, "light")
    @Test fun addressPickerSheetDark() = addressPickerSheet(true, "dark")
    @Test fun addressPickerFormLight() = addressPickerForm(false, "light")
    @Test fun addressPickerFormDark() = addressPickerForm(true, "dark")

    @Test fun addressPickerAppliesSelectedAddress() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Change delivery address").performClick()
        rule.onNodeWithText("Work").performClick()
        rule.onNodeWithText("Use this address").performClick()
        rule.onNodeWithText("Kacyiru").assertExists()
    }

    @Test fun addressPickerSavesNewAddress() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Change delivery address").performClick()
        rule.onNodeWithText("Add a new address").performClick()
        rule.onNodeWithContentDescription("Street or landmark").performTextInput("Remera, KG 5 Ave")
        rule.onNodeWithText("Save and use").performClick()
        rule.onNodeWithText("Remera").assertExists()
    }

    @Test fun browseHeaderPickupDark() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState().apply { isPickup = true }
        rule.setContent { EatsBrowseTestSurface(state, darkTheme = true) }
        rule.onRoot().captureRoboImage("build/screenshots/eats-browse-pickup-dark.png")
    }

    @Test fun deliveryAndPickupSelectionStayInSync() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Pickup").performClick()
        assertTrue(state.isPickup)
        rule.onNodeWithContentDescription("Delivery").performClick()
        assertFalse(state.isPickup)
    }

    @Test fun topBarActionsAndSearchAreInteractive() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState().apply {
            cartRestaurant = rw.itunda.core.network.ShoppingMerchantDto(
                merchantId = "a",
                businessName = "Kigali Bakery",
                category = "Bakery",
                cashbackRate = "2%",
            )
        }
        var ordersOpened = false
        var cartOpened = false
        var query = ""
        rule.setContent {
            EatsBrowseTestSurface(
                state = state,
                searchInput = query,
                cartCount = 1,
                onSearchChange = { query = it },
                onOrdersClick = { ordersOpened = true },
                onCartClick = { cartOpened = true },
            )
        }

        rule.onNodeWithContentDescription("Orders").performClick()
        rule.onNodeWithContentDescription("Cart").performClick()
        rule.onNodeWithContentDescription("Search restaurants or dishes").performTextInput("pizza")

        assertTrue(ordersOpened)
        assertTrue(cartOpened)
        assertEquals("pizza", query)
    }

    @Test fun couponCanBeSaved() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use")[1].performClick()
        assertEquals("EATS20", state.selectedCoupon)
        rule.onNodeWithText("EATS20 ✓", substring = true).assertExists()
    }

    @Test fun fulfillmentAndCouponStateStayConsistent() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state) }

        rule.onNodeWithContentDescription("Pickup").performClick()
        assertTrue(state.isPickup)
        rule.onNodeWithContentDescription("Delivery").performClick()
        assertFalse(state.isPickup)

        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use")[0].performClick()
        assertEquals("FREEDEL", state.selectedCoupon)

        rule.onNodeWithContentDescription("Pickup").performClick()
        assertTrue(state.isPickup)
        assertNull(state.selectedCoupon)

        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use")[0].performClick()
        rule.waitForIdle()
        assertNull(state.selectedCoupon)

        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use")[1].performClick()
        assertEquals("EATS20", state.selectedCoupon)
    }

    @Test fun browseHeaderPickupLight() {
        val state = rw.itunda.feature.eats.impl.EatsBrowseState().apply { isPickup = true }
        rule.setContent { EatsBrowseTestSurface(state, darkTheme = false) }
        rule.onRoot().captureRoboImage("build/screenshots/eats-browse-pickup-light.png")
    }
}
