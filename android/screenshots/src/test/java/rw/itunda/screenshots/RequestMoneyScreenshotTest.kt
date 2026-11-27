package rw.itunda.screenshots

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runOnIdle
import androidx.compose.runtime.mutableStateOf
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rw.itunda.core.designsystem.components.IdsToast
import rw.itunda.core.designsystem.theme.IdsTheme
import rw.itunda.feature.pay.impl.RequestMoneyScreen
import rw.itunda.feature.pay.impl.RequestMoneyState

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h788dp-xxhdpi")
class RequestMoneyScreenshotTest {
    @get:Rule val rule = createComposeRule()

    private fun render(dark: Boolean, name: String, amount: Long = 5_000L, note: String = "") {
        val state = RequestMoneyState(initialAmount = amount, initialNote = note)
        rule.setContent {
            IdsTheme(darkTheme = dark) {
                RequestMoneyScreen(onBack = {}, state = state)
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test fun initialLight() = render(false, "request-money-light")
    @Test fun initialDark() = render(true, "request-money-dark")
    @Test fun customLight() = render(false, "request-money-custom-light", 12_345L, "Lunch")
    @Test fun customDark() = render(true, "request-money-custom-dark", 12_345L, "Lunch")
    @Test fun minimumErrorLight() = render(false, "request-money-minimum-error-light", 99L)
    @Test fun minimumErrorDark() = render(true, "request-money-minimum-error-dark", 99L)
    @Test fun maximumErrorLight() = render(false, "request-money-maximum-error-light", 5_000_001L)
    @Test fun maximumErrorDark() = render(true, "request-money-maximum-error-dark", 5_000_001L)
    @Test fun emptyLight() = render(false, "request-money-empty-light", 0L)
    @Test fun emptyDark() = render(true, "request-money-empty-dark", 0L)

    @Test fun quickAmountsAccumulateAndRegenerateCode() {
        val state = RequestMoneyState()
        rule.setContent { IdsTheme(darkTheme = false) { RequestMoneyScreen({}, state) } }
        rule.onNodeWithText("+2,000").performClick()
        rule.runOnIdle { assertEquals(7_000L, state.amount) }
        rule.onNodeWithText("ITD-4280-90").assertExists()
        rule.onNodeWithText("+5,000").performClick()
        rule.runOnIdle { assertEquals(12_000L, state.amount) }
        rule.onNodeWithText("+10,000").performClick()
        rule.runOnIdle { assertEquals(22_000L, state.amount) }
        rule.onNodeWithText("+25,000").performClick()
        rule.runOnIdle { assertEquals(47_000L, state.amount) }
        rule.onNodeWithText("ITD-4680-90").assertExists()
    }

    @Test fun amountValidationDisablesCopyAndShare() {
        val state = RequestMoneyState(initialAmount = 99L)
        rule.setContent { IdsTheme(darkTheme = false) { RequestMoneyScreen({}, state) } }
        rule.onNodeWithText("The least you can request is 100 RWF.").assertExists()
        rule.onNodeWithText("Copy").assertIsNotEnabled()
        rule.onNodeWithText("Share request").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Amount in RWF").performTextClearance()
        rule.onNodeWithContentDescription("Amount in RWF").performTextInput("100")
        rule.onNodeWithText("ITD-4211-90").assertExists()
        rule.onNodeWithText("Copy").assertExists()
    }

    @Test fun fractionalAmountsKeepPrototypeCodeFormatting() {
        val state = RequestMoneyState(initialAmount = 150L)
        rule.setContent { IdsTheme(darkTheme = false) { RequestMoneyScreen({}, state) } }
        rule.onNodeWithText("ITD-4211.5-90").assertExists()
    }

    @Test fun amountAndNotePersistWhenScreenIsReopened() {
        val state = RequestMoneyState()
        rule.setContent { IdsTheme(darkTheme = false) { RequestMoneyScreen({}, state) } }
        rule.onNodeWithText("+2,000").performClick()
        rule.onNodeWithContentDescription("Note").performTextInput("Lunch")
        rule.setContent { IdsTheme(darkTheme = false) { RequestMoneyScreen({}, state) } }
        rule.onNodeWithText("ITD-4280-90").assertExists()
        rule.onNodeWithContentDescription("Note").performTextInput("!")
        rule.runOnIdle { assertEquals("Lunch!", state.note) }
    }

    @Test fun noteIsTrimmedAndLimitedToFortyCharacters() {
        val state = RequestMoneyState()
        rule.setContent { IdsTheme(darkTheme = false) { RequestMoneyScreen({}, state) } }
        rule.onNodeWithContentDescription("Note").performTextInput(" Lunch ")
        rule.runOnIdle { assertEquals("Lunch", state.note) }
        rule.onNodeWithContentDescription("Note").performTextClearance()
        rule.onNodeWithContentDescription("Note").performTextInput("12345678901234567890123456789012345678901")
        rule.runOnIdle { assertEquals(40, state.note.length) }
    }

    @Test fun copyWritesCompleteRequestAndShowsToast() {
        IdsToast.dismiss()
        val state = RequestMoneyState(initialNote = "Lunch")
        var context: Context? = null
        rule.setContent {
            context = LocalContext.current
            IdsTheme(darkTheme = false) { RequestMoneyScreen({}, state) }
        }
        rule.onNodeWithText("Copy").performClick()
        rule.runOnIdle { assertEquals("Request copied", IdsToast.message) }
        val clipboard = context!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = clipboard.primaryClip!!.getItemAt(0).text.toString()
        assertEquals("Please pay me 5,000 RWF on itunda. Code: ITD-4260-90 · Lunch", copied)
        rule.onNodeWithText("Request code").assertExists()
    }

    @Test fun shareOpensNativeChooserWithCompleteRequest() {
        val state = RequestMoneyState(initialNote = "Lunch")
        var activity: Activity? = null
        rule.setContent {
            activity = LocalContext.current as Activity
            IdsTheme(darkTheme = false) { RequestMoneyScreen({}, state) }
        }
        rule.onNodeWithText("Share request").performClick()
        val chooser = shadowOf(activity!!).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val sendIntent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, sendIntent!!.action)
        assertEquals(
            "Please pay me 5,000 RWF on itunda. Code: ITD-4260-90 · Lunch",
            sendIntent.getStringExtra(Intent.EXTRA_TEXT),
        )
    }

    @Test fun backButtonCallsNavigationCallback() {
        val backCalled = mutableStateOf(false)
        rule.setContent { IdsTheme(darkTheme = false) { RequestMoneyScreen({ backCalled.value = true }, RequestMoneyState()) } }
        rule.onNodeWithContentDescription("Back").performClick()
        rule.runOnIdle { assertTrue(backCalled.value) }
    }
}
