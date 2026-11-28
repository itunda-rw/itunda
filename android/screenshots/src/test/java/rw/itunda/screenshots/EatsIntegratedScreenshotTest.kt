package rw.itunda.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
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

/** Full production Eats entry point, using the same deterministic fixture as device demo mode. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsIntegratedScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Before fun enablePrototypeDemo() { NetworkClient.demoMode = true }
    @After fun disablePrototypeDemo() { NetworkClient.demoMode = false }

    private fun show(dark: Boolean, waitForBrowseData: Boolean = true) {
        NetworkClient.demoMode = true
        rule.activity.actionBar?.hide()
        val sessionKey = Any()
        rule.setContent {
            key(sessionKey) {
                IdsTheme(darkTheme = dark) {
                    EatsContent(deviceStepUpHost = { _, _, _ -> })
                }
            }
        }
        rule.waitUntil(10_000) {
            rule.onAllNodesWithContentDescription("Search restaurants").fetchSemanticsNodes().isNotEmpty()
        }
        // Drain the first Compose frame before waiting for the demo fixture to populate the list.
        rule.runOnIdle { NetworkClient.demoMode = true }
        rule.waitForIdle()
        if (waitForBrowseData) {
            // The section count sits below the Popular rail and may not be composed until a LazyColumn scroll.
            // The first rail card is visible in the initial viewport and deterministic in the demo fixture.
            rule.waitUntil(60_000) {
                rule.onAllNodesWithText("Popular this week").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun capture(name: String) = rule.onRoot().captureRoboImage("build/screenshots/$name.png")

    private fun scrollBrowseBy(delta: Float) {
        rule.onNodeWithTag("eats-browse-list").performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
            scrollBy(0f, delta)
        }
        rule.waitForIdle()
    }

    @Test fun browseDefaultLight() {
        show(false)
        capture("eats-prototype-browse-default-light")
    }

    @Test fun browseDefaultDark() {
        show(true)
        capture("eats-prototype-browse-default-dark")
    }

    @Test fun browseCategoryBrochetteLight() {
        show(false)
        rule.onNodeWithText("Brochette").performClick()
        rule.waitForIdle()
        capture("eats-prototype-browse-category-brochette-light")
    }

    @Test fun browseCategoryBrochetteDark() {
        show(true)
        rule.onNodeWithText("Brochette").performClick()
        rule.waitForIdle()
        capture("eats-prototype-browse-category-brochette-dark")
    }

    @Test fun browseSortTopRatedLight() {
        show(false)
        rule.onNodeWithText("Top rated").performScrollTo()
        rule.onNodeWithText("Top rated").performClick()
        rule.waitForIdle()
        capture("eats-prototype-browse-sort-top-rated-light")
    }

    @Test fun browseSortTopRatedDark() {
        show(true)
        rule.onNodeWithText("Top rated").performScrollTo()
        rule.onNodeWithText("Top rated").performClick()
        rule.waitForIdle()
        capture("eats-prototype-browse-sort-top-rated-dark")
    }

    @Test fun browsePickupLight() {
        show(false)
        rule.onNodeWithContentDescription("Pickup").performClick()
        rule.waitForIdle()
        capture("eats-prototype-browse-pickup-light")
    }

    @Test fun browsePickupDark() {
        show(true)
        rule.onNodeWithContentDescription("Pickup").performClick()
        rule.waitForIdle()
        capture("eats-prototype-browse-pickup-dark")
    }

    @Test fun browseSearchBakeryLight() {
        show(false)
        rule.onNodeWithContentDescription("Search restaurants").performTextInput("bakery")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Nyamirambo Grill").fetchSemanticsNodes().isEmpty() }
        capture("eats-prototype-browse-search-bakery-light")
    }

    @Test fun browseSearchNoResultsLight() {
        show(false)
        rule.onNodeWithContentDescription("Search restaurants").performTextInput("zzzz")
        rule.waitUntil(10_000) { rule.onAllNodesWithText("No restaurants match.").fetchSemanticsNodes().isNotEmpty() }
        capture("eats-prototype-browse-search-no-results-light")
    }

    @Test fun browseOpenNowLight() {
        show(false)
        rule.onNodeWithText("Open now").performScrollTo()
        rule.onNodeWithText("Open now").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Gisozi Street Kitchen").fetchSemanticsNodes().isEmpty() }
        capture("eats-prototype-browse-open-now-light")
    }

    @Test fun browseSavedLight() {
        show(false)
        rule.onNodeWithContentDescription("Saved").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("2").fetchSemanticsNodes().isNotEmpty() }
        capture("eats-prototype-browse-saved-light")
    }

    @Test fun browseMiddleLight() {
        show(false)
        scrollBrowseBy(888f)
        capture("eats-prototype-browse-middle-light")
    }

    @Test fun browseMiddleDark() {
        show(true)
        scrollBrowseBy(888f)
        capture("eats-prototype-browse-middle-dark")
    }

    @Test fun browseLowerLight() {
        show(false)
        scrollBrowseBy(1776f)
        capture("eats-prototype-browse-lower-light")
    }

    @Test fun browseLowerDark() {
        show(true)
        scrollBrowseBy(1776f)
        capture("eats-prototype-browse-lower-dark")
    }

    private fun browseCategory(category: String, dark: Boolean) {
        show(dark)
        rule.onAllNodesWithText(category).get(0).performScrollTo().performClick()
        capture("eats-prototype-browse-category-${category.lowercase()}-${if (dark) "dark" else "light"}")
    }

    @Test fun browseCategoryChickenLight() = browseCategory("Chicken", false)
    @Test fun browseCategoryChickenDark() = browseCategory("Chicken", true)
    @Test fun browseCategoryPizzaLight() = browseCategory("Pizza", false)
    @Test fun browseCategoryPizzaDark() = browseCategory("Pizza", true)
    @Test fun browseCategoryBurgersLight() = browseCategory("Burgers", false)
    @Test fun browseCategoryBurgersDark() = browseCategory("Burgers", true)
    @Test fun browseCategoryLocalLight() = browseCategory("Local", false)
    @Test fun browseCategoryLocalDark() = browseCategory("Local", true)
    @Test fun browseCategoryChipsLight() = browseCategory("Chips", false)
    @Test fun browseCategoryChipsDark() = browseCategory("Chips", true)
    @Test fun browseCategoryBakeryLight() = browseCategory("Bakery", false)
    @Test fun browseCategoryBakeryDark() = browseCategory("Bakery", true)
    @Test fun browseCategoryCoffeeLight() = browseCategory("Coffee", false)
    @Test fun browseCategoryCoffeeDark() = browseCategory("Coffee", true)
    @Test fun browseCategoryJuiceLight() = browseCategory("Juice", false)
    @Test fun browseCategoryJuiceDark() = browseCategory("Juice", true)
    @Test fun browseCategoryHealthyLight() = browseCategory("Healthy", false)
    @Test fun browseCategoryHealthyDark() = browseCategory("Healthy", true)
    @Test fun browseCategoryDessertLight() = browseCategory("Dessert", false)
    @Test fun browseCategoryDessertDark() = browseCategory("Dessert", true)

    private fun browseSort(label: String, dark: Boolean, file: String) {
        show(dark)
        if (label == "Fastest") {
            rule.onAllNodesWithText(label).get(1).performScrollTo().performClick()
        } else {
            rule.onNodeWithText(label).performScrollTo().performClick()
        }
        capture("eats-prototype-browse-sort-$file-${if (dark) "dark" else "light"}")
    }

    @Test fun browseSortFastestLight() = browseSort("Fastest", false, "fastest")
    @Test fun browseSortFastestDark() = browseSort("Fastest", true, "fastest")
    @Test fun browseSortFreeDeliveryLight() = browseSort("Free delivery", false, "free-delivery")
    @Test fun browseSortFreeDeliveryDark() = browseSort("Free delivery", true, "free-delivery")
    @Test fun browseSortNearestLight() = browseSort("Nearest", false, "nearest")
    @Test fun browseSortNearestDark() = browseSort("Nearest", true, "nearest")

    private fun browseCoupons(dark: Boolean) {
        show(dark)
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onNodeWithText("Your coupons").assertExists()
        rule.onAllNodesWithText("Free delivery").get(0).assertExists()
        rule.onNodeWithText("20% off your meal").assertExists()
        capture("eats-prototype-browse-coupons-sheet-${if (dark) "dark" else "light"}")
    }

    @Test fun browseCouponsSheetLight() = browseCoupons(false)
    @Test fun browseCouponsSheetDark() = browseCoupons(true)

    private fun browseAddressPicker(dark: Boolean) {
        show(dark, waitForBrowseData = false)
        rule.onNodeWithContentDescription("Change delivery address").performClick()
        rule.onNodeWithText("Your rider will go to the address you pick.").assertExists()
        capture("eats-prototype-browse-address-sheet-${if (dark) "dark" else "light"}")
    }

    @Test fun browseAddressPickerLight() = browseAddressPicker(false)
    @Test fun browseAddressPickerDark() = browseAddressPicker(true)

    private fun browseNewAddressForm(dark: Boolean) {
        show(dark, waitForBrowseData = false)
        rule.onNodeWithContentDescription("Change delivery address").performClick()
        rule.onNodeWithText("Add a new address").performClick()
        rule.onNodeWithText("Street or landmark").assertExists()
        capture("eats-prototype-browse-address-new-form-${if (dark) "dark" else "light"}")
    }

    @Test fun browseNewAddressFormLight() = browseNewAddressForm(false)
    @Test fun browseNewAddressFormDark() = browseNewAddressForm(true)

    private fun browseClubSheet(dark: Boolean) {
        show(dark)
        rule.onNodeWithText("Eats Club").performClick()
        rule.onNodeWithText("Not now").assertExists()
        capture("eats-prototype-browse-club-sheet-${if (dark) "dark" else "light"}")
    }

    @Test fun browseClubSheetLight() = browseClubSheet(false)
    @Test fun browseClubSheetDark() = browseClubSheet(true)

    private fun browseSavedDeliveryPromo(dark: Boolean) {
        show(dark)
        rule.onNodeWithText("Free delivery on your first", substring = true).performClick()
        rule.onNodeWithText("Coupons ✓").assertExists()
        capture("eats-prototype-browse-promo-delivery-saved-${if (dark) "dark" else "light"}")
    }

    @Test fun browseSavedDeliveryPromoLight() = browseSavedDeliveryPromo(false)
    @Test fun browseSavedDeliveryPromoDark() = browseSavedDeliveryPromo(true)

    @Test fun browsePickupAndDeliveryToggle() {
        show(false)
        rule.onNodeWithContentDescription("Pickup").performClick()
        capture("eats-prototype-browse-pickup-toggle-light")
        rule.onNodeWithContentDescription("Delivery").performClick()
        capture("eats-prototype-browse-delivery-toggle-light")
    }

    @Test fun browseBakerySearchResults() {
        show(false)
        rule.onNodeWithContentDescription("Search restaurants").performTextInput("bakery")
        rule.waitForIdle()
        capture("eats-prototype-browse-search-bakery-entered-light")
    }

    private fun openBakery(dark: Boolean) {
        show(dark)
        rule.onNodeWithText("Kigali Bakery").performScrollTo().performClick()
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("Croissant").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun restaurantMenuLight() {
        openBakery(false)
        rule.onNodeWithText("Popular").assertExists()
        capture("eats-prototype-restaurant-menu-light")
    }

    @Test fun restaurantMenuDark() {
        openBakery(true)
        rule.onNodeWithText("Popular").assertExists()
        capture("eats-prototype-restaurant-menu-dark")
    }

    @Test fun restaurantInfoTabLight() {
        openBakery(false)
        rule.onNodeWithText("Info").performClick()
        rule.onNodeWithText("About this restaurant").assertExists()
        capture("eats-prototype-restaurant-info-light")
    }

    @Test fun restaurantReviewsTabDark() {
        openBakery(true)
        rule.onNodeWithText("Reviews").performClick()
        capture("eats-prototype-restaurant-reviews-dark")
    }

    @Test fun restaurantItemSheetLight() {
        openBakery(false)
        rule.onNodeWithText("Croissant").performClick()
        rule.onNodeWithText("Add-ons").assertExists()
        rule.onNodeWithText("Butter portion").assertExists()
        rule.onNodeWithText("Honey").assertExists()
        rule.onNodeWithText("Preferences").assertExists()
        rule.onNodeWithText("No onions").assertExists()
        rule.onNodeWithText("Extra spicy").assertExists()
        capture("eats-prototype-restaurant-item-sheet-light")
    }

    @Test fun restaurantItemSheetDark() {
        openBakery(true)
        rule.onNodeWithText("Croissant").performClick()
        rule.onNodeWithText("Add-ons").assertExists()
        rule.onNodeWithText("Butter portion").assertExists()
        rule.onNodeWithText("Honey").assertExists()
        rule.onNodeWithText("Preferences").assertExists()
        capture("eats-prototype-restaurant-item-sheet-dark")
    }

    @Test fun restaurantItemSheetCustomizedLight() {
        openBakery(false)
        rule.onNodeWithText("Croissant").performClick()
        rule.onNodeWithText("Extra spicy").performClick()
        rule.onNodeWithContentDescription("Add one").performClick()
        rule.onNodeWithText("Add · 2,400 RWF").assertExists()
        capture("eats-prototype-restaurant-item-customized-sheet-light")
    }

    @Test fun restaurantItemSheetCustomizedDark() {
        openBakery(true)
        rule.onNodeWithText("Croissant").performClick()
        rule.onNodeWithText("Extra spicy").performClick()
        rule.onNodeWithContentDescription("Add one").performClick()
        rule.onNodeWithText("Add · 2,400 RWF").assertExists()
        capture("eats-prototype-restaurant-item-customized-sheet-dark")
    }

    @Test fun restaurantItemSheetAddOnSelectionChangesPriceAndAdds() {
        openBakery(false)
        rule.onNodeWithText("Croissant").performClick()
        rule.onNodeWithText("Butter portion").performClick()
        rule.onNodeWithText("Extra spicy").performClick()
        rule.onNodeWithContentDescription("Add one").performClick()
        rule.onNodeWithText("Add · 3,000 RWF").performClick()
        rule.onNodeWithText("Checkout (2 items)").assertExists()
    }
}
