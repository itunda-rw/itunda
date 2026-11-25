package rw.itunda.screenshots

import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.IdsTheme

/**
 * Diagnostic capture for the v1038 Browse font-width mismatch.
 * Prototype CSS measurements: Kimironko = 78.5dp at 14px; Popular this week = 185.6dp at 19px.
 * Keep the candidate labels in the capture so the closest available Android family is measurable.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class EatsFontMetricsScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val candidates = listOf(
        "Default" to FontFamily.Default,
        "Sans serif" to FontFamily.SansSerif,
        "Noto Sans KR" to FontFamily(Typeface.create("Noto Sans KR", Typeface.NORMAL)),
        "Noto Sans CJK KR" to FontFamily(Typeface.create("Noto Sans CJK KR", Typeface.NORMAL)),
        "Arial" to FontFamily(Typeface.create("Arial", Typeface.NORMAL)),
        "Plus Jakarta Sans" to FontFamily(Typeface.create("Plus Jakarta Sans", Typeface.NORMAL)),
        "Toss Product Sans" to FontFamily(Typeface.create("Toss Product Sans", Typeface.NORMAL)),
        "Serif control" to FontFamily.Serif,
    )

    @Test fun captureFontWidthCandidates() {
        rule.activity.actionBar?.hide()
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(
                    Modifier.fillMaxSize()
                        .background(Color.White)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    Text("EATS font-width candidates", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    candidates.forEach { (name, family) ->
                        Column(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                            Text(name, color = Color.Gray, fontSize = 10.sp)
                            Text(
                                "Kimironko · Fastest",
                                color = Color(0xFF191F28),
                                fontFamily = family,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.176).sp,
                            )
                            Text(
                                "Popular this week",
                                color = Color(0xFF191F28),
                                fontFamily = family,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.4).sp,
                            )
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/eats-font-candidates.png")
    }
}
