package rw.itunda.screenshots

import androidx.compose.runtime.Composable
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
import rw.itunda.feature.menu.impl.MiniAppsHubScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class MiniAppsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean, tab: Int) {
        rule.setContent { IdsTheme(darkTheme = dark) { MiniAppsHubScreen(onBack = {}, initialTab = tab) } }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun featuredLight() = shot("mini-featured-light", false, 0)
    @Test fun featuredDark() = shot("mini-featured-dark", true, 0)
    @Test fun gamesLight() = shot("mini-games-light", false, 1)
    @Test fun pointsLight() = shot("mini-points-light", false, 2)
    @Test fun categoriesLight() = shot("mini-categories-light", false, 3)
}
