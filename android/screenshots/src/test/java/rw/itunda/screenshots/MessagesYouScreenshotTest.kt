package rw.itunda.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import rw.itunda.core.designsystem.theme.Ids
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.core.network.NetworkClient
import rw.itunda.feature.my.impl.MyEntryPoint
import rw.itunda.feature.talk.impl.TalkEntryPoint

/** Messages and You against the demo backend (DemoInterceptor), so rows and profile are populated. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class MessagesYouScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun messagesLight() {
        NetworkClient.demoMode = true
        rule.setContent { IdsTheme(darkTheme = false) { Box(Modifier.fillMaxSize().background(Ids.colors.background)) { TalkEntryPoint(null, {}, { _, _, _ -> }) } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Patrick H.").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("build/screenshots/messages-light.png")
    }

    @Test fun youLight() {
        NetworkClient.demoMode = true
        rule.setContent { IdsTheme(darkTheme = false) { Box(Modifier.fillMaxSize().background(Ids.colors.background)) { MyEntryPoint(onBack = {}) } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Aline Uwase").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("build/screenshots/you-light.png")
    }

    @Test fun profileLight() {
        NetworkClient.demoMode = true
        rule.setContent { IdsTheme(darkTheme = false) { Box(Modifier.fillMaxSize().background(Ids.colors.background)) { MyEntryPoint(onBack = {}) } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Aline Uwase").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Aline Uwase").performClick()
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Verification").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("build/screenshots/profile-light.png")
    }

    @Test fun messagesDark() {
        NetworkClient.demoMode = true
        rule.setContent { IdsTheme(darkTheme = true) { Box(Modifier.fillMaxSize().background(Ids.colors.background)) { TalkEntryPoint(null, {}, { _, _, _ -> }) } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Patrick H.").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("build/screenshots/messages-dark.png")
    }

    @Test fun youDark() {
        NetworkClient.demoMode = true
        rule.setContent { IdsTheme(darkTheme = true) { Box(Modifier.fillMaxSize().background(Ids.colors.background)) { MyEntryPoint(onBack = {}) } } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Aline Uwase").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("build/screenshots/you-dark.png")
    }
}
