package rw.itunda.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.feature.my.impl.SettingsDeleteAccountScreen
import rw.itunda.feature.my.impl.SettingsHomeScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h788dp-xxhdpi")
class SettingsHomeScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun renderHome(dark: Boolean, name: String, scrollToIndex: Int? = null) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                SettingsHomeScreen(
                    profile = null,
                    language = "en",
                    textSizeLabel = "Default",
                    deviceModel = "Pixel 8",
                    deviceCount = 3,
                    sendLimitLabel = "500,000 RWF",
                    payLabel = "Pay balance",
                    moneyNotificationsEnabled = true,
                    generalNotificationsEnabled = true,
                    onMoneyNotificationsClick = {},
                    onGeneralNotificationsClick = {},
                    onBack = {},
                    onOpenDisplay = {},
                    onOpenTextSize = {},
                    onOpenLanguage = {},
                    onOpenManageContacts = {},
                    onOpenNotifications = {},
                    onOpenSecurity = {},
                    onOpenDevices = {},
                    onOpenSendLimits = {},
                    onOpenPay = {},
                    onMarkAllRead = {},
                    onOpenLegal = {},
                    onSignOut = {},
                )
            }
        }
        scrollToIndex?.let { rule.onNodeWithTag("settings-home-list").performScrollToIndex(it) }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun renderDeleteAccount(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                SettingsDeleteAccountScreen(
                    language = "en",
                    profileName = "Amina Uwase",
                    onBack = {},
                )
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun settingsHomeLightTop() = renderHome(false, "settings-home-light-top")
    @Test fun settingsHomeDarkTop() = renderHome(true, "settings-home-dark-top")
    @Test fun settingsHomeLightMiddle() = renderHome(false, "settings-home-light-middle", 3)
    @Test fun settingsHomeDarkMiddle() = renderHome(true, "settings-home-dark-middle", 3)
    @Test fun settingsHomeLightBottom() = renderHome(false, "settings-home-light-bottom", 6)
    @Test fun settingsHomeDarkBottom() = renderHome(true, "settings-home-dark-bottom", 6)
    @Test fun deleteAccountLight() = renderDeleteAccount(false, "settings-delete-account-light")
    @Test fun deleteAccountDark() = renderDeleteAccount(true, "settings-delete-account-dark")
}
