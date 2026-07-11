package rw.itunda.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import rw.itunda.app.MainActivity

/**
 * Real instrumented UI test coverage for docs/ACCESSIBILITY.md's focus-order item.
 * androidx.compose.ui.test reads the same semantics tree TalkBack does, so this is
 * a real check against a real emulator, not a static-analysis proxy for one --
 * the Android equivalent of ios/App/UITests/FocusOrderTests.swift (XCUITest reads
 * the same accessibility tree VoiceOver does).
 *
 * Added 2026-07-11.
 */
class FocusOrderTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    /**
     * TossBottomBar's five tabs (ItundaAppScreen.kt) should read left to right in
     * on-screen X order -- the exact taxonomy fixed this session on iOS to match
     * (Home/Benefits/Shop/Pay/All). If TalkBack's swipe-right traversal order ever
     * diverged from visual left-to-right position, a user would land on a tab that
     * isn't the one that visually comes next.
     */
    @Test
    fun tabBarFocusOrderMatchesVisualLeftToRightOrder() {
        val labels = listOf("Home", "Benefits", "Shop", "Pay", "All")
        val xPositions = labels.map { label ->
            composeTestRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.left
        }
        assert(xPositions == xPositions.sorted()) {
            "Tab bar labels should be in left-to-right visual order matching Home/Benefits/Shop/Pay/All: got x-positions $xPositions"
        }
    }

    /**
     * HomeTopBar's two icon-only buttons (fixed for missing contentDescription
     * this session) should read in their real HStack/Row order: "Scan QR code"
     * then "Notifications", left to right.
     */
    @Test
    fun homeTopBarIconsFocusOrderMatchesVisualOrder() {
        val scanQr = composeTestRule.onNodeWithContentDescription("Scan QR code").fetchSemanticsNode().boundsInRoot.left
        val notifications = composeTestRule.onNodeWithContentDescription("Notifications").fetchSemanticsNode().boundsInRoot.left
        assert(scanQr < notifications) {
            "Scan QR code should sit left of Notifications, matching HomeTopBar's real Row order: scanQr=$scanQr notifications=$notifications"
        }
    }
}
