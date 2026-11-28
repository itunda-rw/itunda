package rw.itunda.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@Config(sdk = [34], qualifiers = "w390dp-h900dp-xxhdpi")
class EatsCouponInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun couponCardAppliesAndUpdatesSummary() {
        val state = EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use").get(1).performClick()
        assertEquals("EATS20", state.selectedCoupon)
        assertFalse(state.showCoupons)
        rule.onNodeWithText("Coupons ✓").assertExists()
    }

    @Test fun freeDeliveryIsDisabledForPickupAndCodeEntryShowsError() {
        val state = EatsBrowseState().apply { isPickup = true }
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onNodeWithText("Delivery only").assertExists()
        assertEquals(1, rule.onAllNodesWithText("Use").fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Promo code").performTextInput("FREEDEL")
        rule.onNodeWithText("Apply").performClick()
        rule.onNodeWithText("Free delivery only applies to delivery orders.").assertExists()
        assertNull(state.selectedCoupon)
        assertTrue(state.showCoupons)
    }

    @Test fun selectedCouponCanBeRemoved() {
        val state = EatsBrowseState().apply { selectedCoupon = "EATS20" }
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onNodeWithText("Applied ✓").assertExists()
        rule.onNodeWithText("Remove").performClick()
        assertNull(state.selectedCoupon)
        assertFalse(state.showCoupons)
    }

    @Test fun validCodeCanBeAppliedFromTheCodeField() {
        val state = EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onNodeWithContentDescription("Promo code").performTextInput("EATS20")
        rule.onNodeWithText("Apply").performClick()
        assertEquals("EATS20", state.selectedCoupon)
        assertFalse(state.showCoupons)
    }

    @Test fun invalidCodeShowsAnErrorWithoutClosingTheSheet() {
        val state = EatsBrowseState()
        rule.setContent { EatsBrowseTestSurface(state) }
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onNodeWithContentDescription("Promo code").performTextInput("NOTREAL")
        rule.onNodeWithText("Apply").performClick()
        rule.onNodeWithText("Code not valid. Try EATS20.").assertExists()
        assertNull(state.selectedCoupon)
        assertTrue(state.showCoupons)
    }
}
