package rw.itunda.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.theme.Ids
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.feature.payments.impl.ContactUi
import rw.itunda.feature.payments.impl.RecipientEntryScreen

/** Send > "Send to" with the prototype's two sample contacts. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class SendScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun render(dark: Boolean, name: String) {
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background)) {
                    RecipientEntryScreen(
                        onBack = {},
                        onNext = {},
                        contacts = listOf(
                            ContactUi("Amina K.", "0788 000 214", "itunda", "#EEEDFF", "A"),
                            ContactUi("Eric M.", "0781 234 567", "itunda", "#E3F5EC", "E"),
                        ),
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun sendLight() = render(false, "send-light")
    @Test fun sendDark() = render(true, "send-dark")
}
