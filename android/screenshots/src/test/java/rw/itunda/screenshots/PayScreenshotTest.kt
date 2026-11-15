package rw.itunda.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
                ProtoPayContent(mode, {}, "ITUNDA-DEMO-1", 4, "itunda Pay Money", "120,500", {}, {}, {}, {}, {})
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun payLight() = render(false, "pay-light")
    @Test fun payDark() = render(true, "pay-dark")
    @Test fun tapLight() = render(false, "pay-tap-light", ProtoPayMode.TAP)
    @Test fun tapDark() = render(true, "pay-tap-dark", ProtoPayMode.TAP)
    // Scan hosts the real camera (CameraX, not available on the JVM); only the dark chrome is checked here.
    @Test fun scanDark() = render(false, "pay-scan-dark", ProtoPayMode.SCAN)

    @Test fun hubLight() {
        rule.setContent { IdsTheme(darkTheme = false) { PayHub(onBack = {}, onFindStores = {}) } }
        rule.onRoot().captureRoboImage("build/screenshots/pay-hub-light.png")
    }

    @Test fun splitLight() {
        rule.setContent { IdsTheme(darkTheme = false) { SplitBillAmountScreen(onBack = {}, onContinue = {}) } }
        rule.onRoot().captureRoboImage("build/screenshots/pay-split-light.png")
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { IdsTheme(darkTheme = false) { content() } }
        rule.onRoot().captureRoboImage("build/screenshots/pay-$name.png")
    }
    @Test fun till() = shot("till") { PayTillScreen({}, {}) }
    @Test fun order() = shot("order") { PayOrderScreen("Nyamirambo Coffee House", {}, {}) }
    @Test fun done() = shot("done") { PayDoneScreen("Nyamirambo Coffee House", {}) }
    @Test fun history() = shot("history") { PayHistoryScreen({}) }
    @Test fun saved() = shot("saved") { PaySavedMerchantsScreen({}) }
    @Test fun points() = shot("points") { PayPointsScreen({}) }
    @Test fun settings() = shot("settings") { PaySettingsScreen({}) }
    @Test fun amount() = shot("amount") { PayAmountScreen("Amina K.", {}, {}) }
    @Test fun scanChips() = render(false, "pay-scan-chips-light", ProtoPayMode.SCAN)
    @Test fun topUp() = shot("topup") {
        val bank = rw.itunda.core.network.Account("b", "u", "0001", "itunda Bank", null, "SAVINGS", 842300.0, 842300.0, "RWF", true)
        val pay = rw.itunda.core.network.Account("p", "u", "0002", "itunda Pay", null, "PAY", 120500.0, 120500.0, "RWF", true)
        AddMoneyScreen(pay, listOf(bank), {}, {})
    }
    @Test fun couponAdd() = shot("coupon-add") { PayCouponAddScreen({}) }
}
