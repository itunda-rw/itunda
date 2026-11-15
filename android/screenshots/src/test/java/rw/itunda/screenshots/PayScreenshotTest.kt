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
import rw.itunda.feature.pay.impl.PayHub
import rw.itunda.feature.pay.impl.ProtoPayContent
import rw.itunda.feature.pay.impl.ProtoPayMode

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
}
