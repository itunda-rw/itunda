package rw.itunda.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.feature.pay.impl.PayTillScreen
import rw.itunda.feature.pay.impl.PayOrderScreen
import rw.itunda.feature.pay.impl.PayHistoryScreen
import rw.itunda.feature.pay.impl.PaySavedMerchantsScreen
import rw.itunda.feature.pay.impl.PayPointsScreen
import rw.itunda.feature.pay.impl.PayDoneScreen
import rw.itunda.feature.pay.impl.PaySettingsScreen
import rw.itunda.feature.pay.impl.PayAmountScreen
import rw.itunda.feature.pay.impl.AddMoneyScreen
import rw.itunda.feature.pay.impl.PayCouponAddScreen
import rw.itunda.feature.pay.impl.CouponBoxContent
import rw.itunda.feature.pay.impl.PayAddCardScreen
import rw.itunda.feature.pay.impl.PayReceiptScreen
import rw.itunda.feature.pay.impl.PayLimitsScreen
import rw.itunda.feature.pay.impl.PayMyCardsScreen
import rw.itunda.feature.pay.impl.PayNewCard
import rw.itunda.feature.pay.impl.PayHub
import rw.itunda.feature.pay.impl.ProtoPayContent
import rw.itunda.feature.pay.impl.ProtoPayMode
import rw.itunda.feature.pay.impl.SplitBillAmountScreen

/** Pay > My code with the prototype's sample data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class PayScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun render(dark: Boolean, name: String, mode: ProtoPayMode = ProtoPayMode.MY_CODE) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                ProtoPayContent(mode, {}, "ITUNDA-DEMO-1", 43, "itunda Pay Money", "120,500", {}, {}, {}, {}, {}, onTopUp = {})
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun payLight() = render(false, "pay-light")
    @Test fun payDark() = render(true, "pay-dark")
    @Test fun tapLight() = render(false, "pay-tap-light", ProtoPayMode.TAP)
    @Test fun tapDark() = render(true, "pay-tap-dark", ProtoPayMode.TAP)
    @Test fun tapTopUpOpensFundingFlow() {
        val opened = mutableStateOf(false)
        rule.setContent {
            IdsTheme(darkTheme = false) {
                ProtoPayContent(ProtoPayMode.TAP, {}, "ITUNDA-DEMO-1", 43, "itunda Pay Money", "120,500", {}, {}, {}, {}, {}, onTopUp = { opened.value = true })
            }
        }
        rule.onNodeWithText("Top up").performClick()
        rule.runOnIdle { assertTrue(opened.value) }
    }
    // Scan hosts the real camera (CameraX, not available on the JVM); only the dark chrome is checked here.
    @Test fun scanLight() = render(false, "pay-scan-light", ProtoPayMode.SCAN)
    @Test fun scanDark() = render(true, "pay-scan-dark", ProtoPayMode.SCAN)

    private fun renderHub(dark: Boolean, name: String, scrollTo: String? = null) {
        rule.setContent { IdsTheme(darkTheme = dark) { PayHub(onBack = {}, onFindStores = {}) } }
        if (scrollTo != null) rule.onNode(androidx.compose.ui.test.hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasText(scrollTo))
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun hubLight() = renderHub(false, "pay-hub-light")
    @Test fun hubDark() = renderHub(true, "pay-hub-dark")
    @Test fun hubMidLight() = renderHub(false, "pay-hub-mid-light", "Get more benefits")
    @Test fun hubMidDark() = renderHub(true, "pay-hub-mid-dark", "Get more benefits")
    @Test fun hubLowerLight() = renderHub(false, "pay-hub-lower-light", "Send feedback")
    @Test fun hubLowerDark() = renderHub(true, "pay-hub-lower-dark", "Send feedback")

    @Test fun splitLight() {
        rule.setContent { IdsTheme(darkTheme = false) { SplitBillAmountScreen(onBack = {}, onContinue = {}) } }
        rule.onRoot().captureRoboImage("build/screenshots/pay-split-light.png")
    }
    @Test fun splitDark() {
        rule.setContent { IdsTheme(darkTheme = true) { SplitBillAmountScreen(onBack = {}, onContinue = {}) } }
        rule.onRoot().captureRoboImage("build/screenshots/pay-split-dark.png")
    }
    @Test fun hubCouponRowOpensCouponBox() {
        val opened = mutableStateOf(false)
        rule.setContent { IdsTheme(darkTheme = false) { PayHub({}, {}, onOpenCoupons = { opened.value = true }) } }
        rule.onNodeWithText("Coupons received").performClick()
        rule.runOnIdle { assertTrue(opened.value) }
    }
    @Test fun hubOnlineViewOpensExplore() {
        val opened = mutableStateOf(false)
        rule.setContent { IdsTheme(darkTheme = false) { PayHub({}, { opened.value = true }) } }
        rule.onNodeWithText("View").performClick()
        rule.runOnIdle { assertTrue(opened.value) }
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { IdsTheme(darkTheme = false) { content() } }
        rule.onRoot().captureRoboImage("build/screenshots/pay-$name.png")
    }
    private fun shotDark(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { IdsTheme(darkTheme = true) { content() } }
        rule.onRoot().captureRoboImage("build/screenshots/pay-$name-dark.png")
    }
    @Test fun till() = shot("till") { PayTillScreen({}, {}) }
    @Test fun tillDark() = shotDark("till") { PayTillScreen({}, {}) }
    @Test fun order() = shot("order") { PayOrderScreen("Nyamirambo Coffee House", {}, {}) }
    @Test fun orderDark() = shotDark("order") { PayOrderScreen("Nyamirambo Coffee House", {}, {}) }
    @Test fun done() = shot("done") { PayDoneScreen("Nyamirambo Coffee House", {}) }
    @Test fun doneDark() = shotDark("done") { PayDoneScreen("Nyamirambo Coffee House", {}) }
    @Test fun history() = shot("history") { PayHistoryScreen({}) }
    @Test fun historyDark() = shotDark("history") { PayHistoryScreen({}) }
    @Test fun saved() = shot("saved") { PaySavedMerchantsScreen({}) }
    @Test fun savedDark() = shotDark("saved") { PaySavedMerchantsScreen({}) }
    @Test fun points() = shot("points") { PayPointsScreen({}) }
    @Test fun pointsDark() = shotDark("points") { PayPointsScreen({}) }
    @Test fun settings() = shot("settings") { PaySettingsScreen({}) }
    @Test fun settingsDark() = shotDark("settings") { PaySettingsScreen({}) }
    @Test fun amount() = shot("amount") { PayAmountScreen("Amina K.", {}, {}) }
    @Test fun amountDark() = shotDark("amount") { PayAmountScreen("Amina K.", {}, {}) }
    @Test fun scanChips() = render(false, "pay-scan-chips-light", ProtoPayMode.SCAN)
    @Test fun scanChipsDark() = render(true, "pay-scan-chips-dark", ProtoPayMode.SCAN)
    @Test fun topUp() = shot("topup") {
        val bank = rw.itunda.core.network.Account("b", "u", "0001", "itunda Bank", null, "SAVINGS", 842300.0, 842300.0, "RWF", true)
        val pay = rw.itunda.core.network.Account("p", "u", "0002", "itunda Pay", null, "PAY", 120500.0, 120500.0, "RWF", true)
        AddMoneyScreen(pay, listOf(bank), {}, {})
    }
    @Test fun topUpDark() = shotDark("topup") {
        val bank = rw.itunda.core.network.Account("b", "u", "0001", "itunda Bank", null, "SAVINGS", 842300.0, 842300.0, "RWF", true)
        val pay = rw.itunda.core.network.Account("p", "u", "0002", "itunda Pay", null, "PAY", 120500.0, 120500.0, "RWF", true)
        AddMoneyScreen(pay, listOf(bank), {}, {})
    }
    @Test fun couponBoxEmpty() = shot("coupon-box") { CouponBoxContent(true, null, emptyList(), emptyList(), false, {}, {}, {}, {}) }
    @Test fun couponBoxEmptyDark() = shotDark("coupon-box") { CouponBoxContent(true, null, emptyList(), emptyList(), false, {}, {}, {}, {}) }
    @Test fun addCard() = shot("add-card") { PayAddCardScreen({}, {}) }
    @Test fun addCardDark() = shotDark("add-card") { PayAddCardScreen({}, {}) }
    @Test fun myCards() = shot("my-cards") { PayMyCardsScreen("120,500", "842,300", emptyList(), {}, {}, {}) }
    @Test fun myCardsDark() = shotDark("my-cards") { PayMyCardsScreen("120,500", "842,300", emptyList(), {}, {}, {}) }
    @Test fun myCardsWithCard() = shot("my-cards-visa") { PayMyCardsScreen("120,500", "842,300", listOf(PayNewCard("Visa", "4242", "AMINA UWASE", "12/28")), {}, {}, {}) }
    @Test fun myCardsWithCardDark() = shotDark("my-cards-visa") { PayMyCardsScreen("120,500", "842,300", listOf(PayNewCard("Visa", "4242", "AMINA UWASE", "12/28")), {}, {}, {}) }
    @Test fun limits() = shot("limits") { PayLimitsScreen(500_000, 2_000_000, 0, {}, { _, _ -> }) }
    @Test fun limitsDark() = shotDark("limits") { PayLimitsScreen(500_000, 2_000_000, 0, {}, { _, _ -> }) }
    @Test fun receipt() = shot("receipt") { PayReceiptScreen(0, {}, {}) }
    @Test fun receiptDark() = shotDark("receipt") { PayReceiptScreen(0, {}, {}) }
    @Test fun couponAdd() = shot("coupon-add") { PayCouponAddScreen({}) }
    @Test fun couponAddDark() = shotDark("coupon-add") { PayCouponAddScreen({}) }
}
