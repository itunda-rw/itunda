package rw.itunda.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.feature.eats.impl.EatsBrowseHeader
import rw.itunda.feature.eats.impl.EatsBrowseState
import rw.itunda.feature.eats.impl.EatsDeliveryPickupRow

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsCouponInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setBrowse(state: EatsBrowseState) {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().background(Ids.colors.background)) {
                    EatsBrowseHeader(state, "", 0, {}, {}, {})
                    EatsDeliveryPickupRow(state)
                }
            }
        }
    }

    @Test fun couponSelectionAppliesEats20() {
        val state = EatsBrowseState()
        setBrowse(state)
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use").get(1).performClick()
        assertEquals("EATS20", state.selectedCoupon)
        assertFalse(state.showCoupons)
    }

    @Test fun freeDeliveryIsRejectedForPickup() {
        val state = EatsBrowseState().apply { isPickup = true }
        setBrowse(state)
        rule.onNodeWithContentDescription("Coupons").performClick()
        rule.onAllNodesWithText("Use").get(0).performClick()
        assertNull(state.selectedCoupon)
        assertFalse(state.showCoupons)
    }

    @Test fun pickupClearsPreviouslySavedDeliveryCoupon() {
        val state = EatsBrowseState().apply { selectedCoupon = "FREEDEL" }
        setBrowse(state)
        rule.onNodeWithContentDescription("Pickup").performClick()
        assertTrue(state.isPickup)
        assertNull(state.selectedCoupon)
    }
}
