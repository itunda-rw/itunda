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
import rw.itunda.feature.credit.impl.LoansFindHero
import rw.itunda.feature.banking.impl.BankSub
import rw.itunda.feature.banking.impl.BankSubOverlay
import rw.itunda.feature.credit.impl.CreditScoreBody
import rw.itunda.feature.credit.impl.demoScoreFactors
import rw.itunda.feature.wealth.impl.InvestIndexHeader
import rw.itunda.feature.wealth.impl.InvestStartCard
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

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

    @Test fun investLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Box(Modifier.fillMaxSize().background(Ids.colors.background).padding(24.dp)) { Column { InvestIndexHeader(); InvestStartCard {} } }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/invest-light.png")
    }

    @Test fun loansLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) { Box(Modifier.fillMaxSize().background(Ids.colors.background).padding(24.dp)) { LoansFindHero() } }
        }
        rule.onRoot().captureRoboImage("build/screenshots/loans-light.png")
    }

    @Test fun scoreLight() {
        rule.setContent {
            IdsTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().background(Ids.colors.background).padding(horizontal = 20.dp)) { CreditScoreBody(692, demoScoreFactors) }
            }
        }
        rule.onRoot().captureRoboImage("build/screenshots/score-light.png")
    }

    private fun sub(sub: BankSub, name: String) {
        rule.setContent { IdsTheme(darkTheme = false) { BankSubOverlay(sub, onClose = {}, onOpen = {}) } }
        rule.onRoot().captureRoboImage("build/screenshots/bank-$name.png")
    }

    @Test fun subAccount() = sub(BankSub.ACCOUNT, "account")
    @Test fun subDeposit() = sub(BankSub.DEPOSIT, "deposit")
    @Test fun subSend() = sub(BankSub.SEND, "send")
    @Test fun subMove() = sub(BankSub.MOVE, "move")
    @Test fun subStatement() = sub(BankSub.STATEMENT, "statement")
    @Test fun subPockets() = sub(BankSub.POCKETS, "pockets")
}
