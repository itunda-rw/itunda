package rw.itunda.screenshots

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.feature.home.impl.DaySpend
import rw.itunda.feature.home.impl.ProtoBill
import rw.itunda.feature.home.impl.ProtoHomeContent
import rw.itunda.feature.home.impl.ProtoHomeState

/** Home with the prototype's own sample data, so it can be compared with the HTML screenshot. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class HomeScreenshotTest {
    @get:Rule val rule = createComposeRule()

    // Sat 7 Nov 2026 is "today" in the prototype; bar heights 31,41,17,51,28,56,24 -> amounts.
    private val sat = LocalDate.of(2026, 11, 7)
    private val state = ProtoHomeState(
        bankBalance = 842_300.0, payBalance = 120_500.0, interestDays = 3,
        spentThisMonth = 312_000.0, monthName = "November",
        week = listOf(31, 41, 17, 51, 28, 56, 24).mapIndexed { i, h -> DaySpend(sat.minusDays((6 - i).toLong()), (h - 17) / 39.0 * 165_000) }
            .let { it.dropLast(1) + DaySpend(sat, 29_700.0) },
        bill = ProtoBill("REG electricity", "Fri 13 Nov", 18_500.0),
        earned = 6_400.0, today = sat,
    )

    private fun render(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                ProtoHomeContent(state, true, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun homeLight() = render(false, "home-light")
    @Test fun homeDark() = render(true, "home-dark")
}
