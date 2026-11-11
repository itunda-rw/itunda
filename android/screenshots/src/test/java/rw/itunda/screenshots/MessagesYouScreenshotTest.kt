package rw.itunda.screenshots

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
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
        NetworkClient.init(org.robolectric.RuntimeEnvironment.getApplication(), "http://localhost/")
        NetworkClient.demoMode = true
        rule.setContent { IdsTheme(darkTheme = false) { TalkEntryPoint(null, {}, { _, _, _ -> }) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Patrick H.").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("build/screenshots/messages-light.png")
    }

    @Test fun youLight() {
        NetworkClient.init(org.robolectric.RuntimeEnvironment.getApplication(), "http://localhost/")
        NetworkClient.demoMode = true
        rule.setContent { IdsTheme(darkTheme = false) { MyEntryPoint(onBack = {}) } }
        rule.waitUntil(10_000) { rule.onAllNodesWithText("Aline Uwase").fetchSemanticsNodes().isNotEmpty() }
        rule.onRoot().captureRoboImage("build/screenshots/you-light.png")
    }
}
