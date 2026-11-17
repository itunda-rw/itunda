package rw.itunda.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.feature.eats.impl.EatsBrowseState

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsCouponInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun couponCardAppliesAndUpdatesSummary() {
        val state = EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use")[1].performClick()
        assertEquals("EATS20", state.selectedCoupon)
        rule.onNodeWithText("EATS20 ✓", substring = true).assertExists()
    }

    @Test fun freeDeliveryCouponClosesAndRejectsPickup() {
        val state = EatsBrowseState().apply { isPickup = true }
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use")[0].performClick()
        rule.waitForIdle()
        assertNull(state.selectedCoupon)
        assertTrue(rule.onAllNodesWithText("Your coupons").fetchSemanticsNodes().isEmpty())
    }

    @Test fun selectedCouponShowsSavedWithoutExtraControls() {
        val state = EatsBrowseState().apply { selectedCoupon = "EATS20" }
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onNodeWithText("Saved").assertExists()
        assertTrue(rule.onAllNodesWithText("Remove").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Enter a code").fetchSemanticsNodes().isEmpty())
    }
}
