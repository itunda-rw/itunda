package rw.itunda.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
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
    @get:Rule val rule = createComposeRule()

    @Before fun enablePrototypeDemo() { NetworkClient.demoMode = true }
    @After fun disablePrototypeDemo() { NetworkClient.demoMode = false }

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

    private fun capture(name: String) = rule.onRoot().captureRoboImage("build/screenshots/$name.png")

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

    @Test fun browseLowerLight() {
        show(false)
        repeat(3) { rule.onRoot().performTouchInput { swipeUp() } }
        capture("eats-prototype-browse-lower-light")
    }

    @Test fun browseLowerDark() {
        show(true)
        repeat(3) { rule.onRoot().performTouchInput { swipeUp() } }
        capture("eats-prototype-browse-lower-dark")
    }
}
