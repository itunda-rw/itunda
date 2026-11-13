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
import rw.itunda.core.network.Account
import rw.itunda.core.network.MoneyActionResult
import rw.itunda.feature.banking.impl.BankHubEntryPoint
import rw.itunda.feature.banking.impl.Grow31IntroContent

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class BankScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun bankLight() {
        val account = Account("a1", "u1", "00001980", "itunda Bank", null, "SAVINGS", 842300.0, 842300.0, "RWF", true)
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background)) {
                    BankHubEntryPoint(
                        primaryAccount = account, savingsGoals = emptyList(), interestJar = null, roundUpSettings = null, spendingInsight = null,
                        onSetRoundUpSettings = { _, _, _, _ -> MoneyActionResult.Success("") },
                        onCreateSavingsGoal = { _, _, _, _ -> MoneyActionResult.Success("") },
                        onBack = {}, onDepositToGoal = { _, _ -> }, onWithdrawFromGoal = { _, _, _ -> }, onClaimInterest = {},
                        onOpenSacco = {}, onOpenIkimina = {}, onOpenMotoOwnership = {}, onOpenHarvestAdvance = {}, onOpenLoans = {},
                        onOpenInvest = {}, onOpenWeeklySavings = {}, onOpenGrow31Savings = {}, onOpenUpfrontDeposit = {},
                        onOpenVupLoan = {}, onOpenStudentLoan = {},
                    )
                }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/bank-light.png")
    }

    @Test fun grow31Light() {
        rule.setContent {
            IdsTheme(darkTheme = false) { Box(Modifier.fillMaxSize().background(Ids.colors.background)) { Grow31IntroContent {} } }
        }
        rule.onRoot().captureRoboImage("build/screenshots/save31-light.png")
    }
}
