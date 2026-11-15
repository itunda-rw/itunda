package rw.itunda.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.components.IdsAppBarTitle
import rw.itunda.core.designsystem.theme.IdsTheme

/** Pipeline proof: renders the shared app-bar title in light and dark. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h200dp-xxhdpi")
class SmokeScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun appBarLight() {
        rule.setContent { IdsTheme(darkTheme = false) { Column(Modifier.padding(16.dp)) { IdsAppBarTitle(area = "pay", onClick = {}) } } }
        rule.onRoot().captureRoboImage("build/screenshots/appbar-light.png")
    }

    @Test fun appBarDark() {
        rule.setContent { IdsTheme(darkTheme = true) { Column(Modifier.padding(16.dp)) { IdsAppBarTitle(area = "pay", onClick = {}) } } }
        rule.onRoot().captureRoboImage("build/screenshots/appbar-dark.png")
    }
}
