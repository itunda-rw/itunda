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
import androidx.compose.foundation.layout.Column
import rw.itunda.feature.eats.impl.EatsMenuItemRow
import rw.itunda.feature.eats.impl.EatsRestaurantHero

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun eatsLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background).padding(20.dp)) { EatsRestaurantHero("Kigali Bakery", {}) }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/eats-restaurant-light.png")
    }

    @Test fun menuRows() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(20.dp)) {
                    EatsMenuItemRow("Croissant", "Buttery, baked this morning", "1,200 RWF", null, null, 0, null, {}, {})
                    EatsMenuItemRow("Mandazi, 6 pieces", "Sweet fried dough", "1,000 RWF", null, null, 2, null, {}, {})
                    EatsMenuItemRow("Fruit juice", null, "1,800 RWF", "2,200 RWF", null, 0, "Options required — tap + to choose", {}, {})
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/eats-menu-rows.png")
    }
}
