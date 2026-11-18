package rw.itunda.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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

/** Production-entry browse parity coverage for the v1038 category, sort and sheet states. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h900dp-xxhdpi")
class EatsBrowseParityScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Before fun enableDemoFixtures() { NetworkClient.demoMode = true }
    @After fun disableDemoFixtures() { NetworkClient.demoMode = false }

    private fun show(dark: Boolean) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                EatsContent(deviceStepUpHost = { _, _, _ -> })
            }
        }
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText("Nyamirambo", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun category(name: String, dark: Boolean) {
        show(dark)
        rule.onAllNodesWithText(name).get(0).performScrollTo().performClick()
        capture("eats-prototype-browse-category-${name.lowercase()}-${if (dark) "dark" else "light"}")
    }

    @Test fun chickenCategoryLight() = category("Chicken", false)
    @Test fun chickenCategoryDark() = category("Chicken", true)
    @Test fun pizzaCategoryLight() = category("Pizza", false)
    @Test fun pizzaCategoryDark() = category("Pizza", true)
    @Test fun burgersCategoryLight() = category("Burgers", false)
    @Test fun burgersCategoryDark() = category("Burgers", true)
    @Test fun localCategoryLight() = category("Local", false)
    @Test fun localCategoryDark() = category("Local", true)
    @Test fun chipsCategoryLight() = category("Chips", false)
    @Test fun chipsCategoryDark() = category("Chips", true)
    @Test fun bakeryCategoryLight() = category("Bakery", false)
    @Test fun bakeryCategoryDark() = category("Bakery", true)
    @Test fun coffeeCategoryLight() = category("Coffee", false)
    @Test fun coffeeCategoryDark() = category("Coffee", true)
    @Test fun juiceCategoryLight() = category("Juice", false)
    @Test fun juiceCategoryDark() = category("Juice", true)
    @Test fun healthyCategoryLight() = category("Healthy", false)
    @Test fun healthyCategoryDark() = category("Healthy", true)
    @Test fun dessertCategoryLight() = category("Dessert", false)
    @Test fun dessertCategoryDark() = category("Dessert", true)

    private fun sort(label: String, dark: Boolean, filename: String) {
        show(dark)
        if (label == "Fastest") {
            rule.onAllNodesWithText(label).get(1).performScrollTo().performClick()
        } else {
            rule.onNodeWithText(label).performScrollTo().performClick()
        }
        capture("eats-prototype-browse-sort-$filename-${if (dark) "dark" else "light"}")
    }

    @Test fun fastestSortLight() = sort("Fastest", false, "fastest")
    @Test fun fastestSortDark() = sort("Fastest", true, "fastest")
    @Test fun freeDeliverySortLight() = sort("Free delivery", false, "free-delivery")
    @Test fun freeDeliverySortDark() = sort("Free delivery", true, "free-delivery")
    @Test fun nearestSortLight() = sort("Nearest", false, "nearest")
    @Test fun nearestSortDark() = sort("Nearest", true, "nearest")

    private fun coupons(dark: Boolean) {
        show(dark)
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onNodeWithText("Your coupons").assertExists()
        rule.onNodeWithText("Free delivery").assertExists()
        rule.onNodeWithText("20% off your meal").assertExists()
        capture("eats-prototype-browse-coupons-sheet-${if (dark) "dark" else "light"}")
    }

    @Test fun couponsSheetLight() = coupons(false)
    @Test fun couponsSheetDark() = coupons(true)

    private fun addressPicker(dark: Boolean) {
        show(dark)
        rule.onNodeWithContentDescription("Change delivery address").performClick()
        rule.onNodeWithText("Your rider will go to the address you pick.").assertExists()
        capture("eats-prototype-browse-address-sheet-${if (dark) "dark" else "light"}")
    }

    @Test fun addressPickerLight() = addressPicker(false)
    @Test fun addressPickerDark() = addressPicker(true)

    private fun newAddressForm(dark: Boolean) {
        show(dark)
        rule.onNodeWithContentDescription("Change delivery address").performClick()
        rule.onNodeWithText("Add a new address").performClick()
        rule.onNodeWithText("Street or landmark").assertExists()
        capture("eats-prototype-browse-address-new-form-${if (dark) "dark" else "light"}")
    }

    @Test fun newAddressFormLight() = newAddressForm(false)
    @Test fun newAddressFormDark() = newAddressForm(true)

    private fun eatsClub(dark: Boolean) {
        show(dark)
        rule.onNodeWithText("Eats Club").performClick()
        rule.onNodeWithText("Not now").assertExists()
        capture("eats-prototype-browse-club-sheet-${if (dark) "dark" else "light"}")
    }

    @Test fun eatsClubSheetLight() = eatsClub(false)
    @Test fun eatsClubSheetDark() = eatsClub(true)

    private fun savedPromo(dark: Boolean) {
        show(dark)
        rule.onNodeWithText("Free delivery on your first", substring = true).performClick()
        rule.onNodeWithText("Coupons ✓").assertExists()
        capture("eats-prototype-browse-promo-delivery-saved-${if (dark) "dark" else "light"}")
    }

    @Test fun savedDeliveryPromoLight() = savedPromo(false)
    @Test fun savedDeliveryPromoDark() = savedPromo(true)

    @Test fun pickupAndDeliveryToggle() {
        show(false)
        rule.onNodeWithContentDescription("Pickup").performClick()
        capture("eats-prototype-browse-pickup-toggle-light")
        rule.onNodeWithContentDescription("Delivery").performClick()
        capture("eats-prototype-browse-delivery-toggle-light")
    }

    @Test fun bakerySearchResults() {
        show(false)
        rule.onNodeWithContentDescription("Search restaurants").performTextInput("bakery")
        rule.waitForIdle()
        capture("eats-prototype-browse-search-bakery-entered-light")
    }
}
