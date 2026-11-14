package rw.itunda.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.feature.front.impl.FrontEntryPoint
import rw.itunda.feature.front.impl.FrontTableFlow

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class FrontScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun frontLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background)) { FrontEntryPoint({}) }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/front-light.png")
    }

    @Test fun frontMenuLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background)) { FrontTableFlow({}, 0, mapOf("chicken" to 1, "chips" to 2)) }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/front-menu-light.png")
    }

    @Test fun frontBillLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background)) { FrontTableFlow({}, 1, mapOf("chicken" to 1, "chips" to 2)) }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/front-bill-light.png")
    }

    @Test fun frontPayLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background)) { FrontTableFlow({}, 2, mapOf("chicken" to 1, "chips" to 2)) }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/front-pay-light.png")
    }

    @Test fun frontDoneLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background)) { FrontTableFlow({}, 3, mapOf("chicken" to 1, "chips" to 2)) }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/front-done-light.png")
    }
}
