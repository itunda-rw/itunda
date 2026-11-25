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
import rw.itunda.feature.maps.impl.DirectionsHubScreen
import rw.itunda.feature.maps.impl.PlacesHubScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h788dp-xxhdpi")
class MapsHubScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        rule.setContent { IdsTheme(darkTheme = dark) { content() } }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun placesLight() = shot("maps-places-light", false) { PlacesHubScreen(onBack = {}) { _, _ -> } }
    @Test fun placesDark() = shot("maps-places-dark", true) { PlacesHubScreen(onBack = {}) { _, _ -> } }
    @Test fun directionsLight() = shot("maps-directions-light", false) { DirectionsHubScreen(onBack = {}) { _ -> } }
    @Test fun directionsDark() = shot("maps-directions-dark", true) { DirectionsHubScreen(onBack = {}) { _ -> } }
}
