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
import rw.itunda.feature.menu.impl.ProtoExploreContent

/** Explore with the prototype's sample data. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class ExploreScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun render(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                ProtoExploreContent("", {}, {}, {}, {})
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun exploreLight() = render(false, "explore-light")
    @Test fun exploreDark() = render(true, "explore-dark")
}
