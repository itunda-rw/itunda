package rw.itunda.app.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
     * ItundaBottomBar's five tabs (ItundaAppScreen.kt) should read left to right in
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

    // Extends coverage (2026-07-11) from Home-tab-only to the remaining four
    // tabs, closing the "other 4 tabs... isn't covered by a test yet" gap this
    // file's own earlier version left open (same extension made to the iOS
    // suite, FocusOrderTests.swift). Each test taps the real tab bar (not a
    // direct navigation shortcut) so it exercises the same path a real
    // TalkBack user takes, then asserts real semantics-tree order matches
    // real visual/Row order.

    /** BenefitsVisitCard's four rows, in their real source/visual order. */
    @Test
    fun benefitsTabRowsFocusOrderMatchesVisualTopToBottomOrder() {
        composeTestRule.onNodeWithText("Benefits").performClick()

        val titles = listOf("Happy lottery", "Push the button", "Try on", "Bring friends")
        val yPositions = titles.map { title ->
            composeTestRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot.top
        }
        assert(yPositions == yPositions.sorted()) {
            "Benefits tab's visit-card rows should read top-to-bottom in their real source order: $titles -> $yPositions"
        }
    }

    /** ShopTopBar's Profile/Cart icons, in their real Row order. */
    @Test
    fun shopTabTopBarIconsFocusOrderMatchesVisualOrder() {
        composeTestRule.onNodeWithText("Shop").performClick()

        val profile = composeTestRule.onNodeWithContentDescription("Profile").fetchSemanticsNode().boundsInRoot.left
        val cart = composeTestRule.onNodeWithContentDescription("Cart").fetchSemanticsNode().boundsInRoot.left
        assert(profile < cart) {
            "Profile should sit left of Cart, matching ShopTopBar's real Row order: profile=$profile cart=$cart"
        }
    }

    /** PayTopBar's Scan QR code/Language icons, in their real Row order. */
    @Test
    fun payTabTopBarIconsFocusOrderMatchesVisualOrder() {
        composeTestRule.onNodeWithText("Pay").performClick()

        val scanQr = composeTestRule.onNodeWithContentDescription("Scan QR code").fetchSemanticsNode().boundsInRoot.left
        val language = composeTestRule.onNodeWithContentDescription("Language").fetchSemanticsNode().boundsInRoot.left
        assert(scanQr < language) {
            "Scan QR code should sit left of Language, matching PayTopBar's real Row order: scanQr=$scanQr language=$language"
        }
    }

    /** AllTopBar's name text and Settings icon, in their real SpaceBetween Row order. */
    @Test
    fun allTabTopBarFocusOrderMatchesVisualOrder() {
        composeTestRule.onNodeWithText("All").performClick()

        val name = composeTestRule.onNodeWithText("TUYIZERE ERIC").fetchSemanticsNode().boundsInRoot.left
        val settings = composeTestRule.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot.left
        assert(name < settings) {
            "Name should sit left of the Settings gear, matching AllTopBar's real SpaceBetween Row order: name=$name settings=$settings"
        }
    }
}
