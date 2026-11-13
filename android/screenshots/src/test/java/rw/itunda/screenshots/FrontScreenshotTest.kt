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
import rw.itunda.feature.menu.impl.FrontHome

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class FrontScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun frontLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background)) { FrontHome({}) }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/front-light.png")
    }
}
