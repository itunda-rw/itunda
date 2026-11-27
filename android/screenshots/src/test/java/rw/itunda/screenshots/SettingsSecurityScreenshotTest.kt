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
import rw.itunda.feature.my.impl.SettingsSecurityScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h788dp-xxhdpi")
class SettingsSecurityScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun render(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                SettingsSecurityScreen(
                    language = "en",
                    pinCheckIndex = 0,
                    onPinCheckIndexChanged = {},
                    quickSignInEnabled = true,
                    onQuickSignInChanged = {},
                    biometricAvailable = true,
                    appLockEnabled = false,
                    onAppLockChanged = {},
                    hasDeviceKey = true,
                    onRemoveDeviceKey = {},
                    onRegisterDeviceKey = { _, onResult -> onResult(true, null) },
                    onChangePin = {},
                    onOpenDevices = {},
                    onBack = {},
                )
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun securityLight() = render(false, "settings-security-light")
    @Test fun securityDark() = render(true, "settings-security-dark")
}
