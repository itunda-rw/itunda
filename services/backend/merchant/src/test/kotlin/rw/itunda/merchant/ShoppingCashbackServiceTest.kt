package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.LocalDate

class ShoppingCashbackServiceTest : BehaviorSpec({

    fun payerAccount() = Account(
        id = "account_payer", userId = "user_1", accountNumber = "ACC-1", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real purchase at an itunda-registered merchant") {
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val service = ShoppingCashbackService(ledgerService, transactionRepository)

        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_cashback", emptyList())

        When("awarding cashback on a 5,000 RWF purchase on a real non-Membership-Day date") {
            // Pinned, not real LocalDate.now() -- 2026-07-28 is a Tuesday, never a real
            // Membership Day, so this real assertion can never flake depending on when
            // the real test suite happens to run.
            val cashback = service.awardCashback(payerAccount(), BigDecimal("5000"), "Kigali Coffee", today = LocalDate.of(2026, 7, 28))

            Then("it's itunda's own real flat 1% rate, not an invented-looking sourced number") {
                cashback shouldBe BigDecimal("50.00")
            }
            Then("it posts a real, balanced 2-leg ledger transaction: payer credit, rewards_expense debit") {
                val legs = legsSlot.captured
                legs.size shouldBe 2
                val creditLeg = legs.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "account_payer"
                creditLeg.accountType shouldBe LedgerAccountType.WALLET
                creditLeg.amount shouldBe BigDecimal("50.00")
                val debitLeg = legs.first { it.direction == LedgerDirection.DEBIT }
                debitLeg.accountId shouldBe "rewards_expense"
                debitLeg.accountType shouldBe LedgerAccountType.REWARDS_EXPENSE
                debitLeg.amount shouldBe BigDecimal("50.00")
            }
            Then("a real Transaction row is saved so it shows up in the payer's own transaction history") {
                val txSlot = slot<Transaction>()
                verify(exactly = 1) { transactionRepository.save(capture(txSlot)) }
                txSlot.captured.recipientId shouldBe "user_1"
                txSlot.captured.type shouldBe TransactionType.DEPOSIT
                txSlot.captured.channel shouldBe "CASHBACK"
            }
        }
    }

    Given("a real purchase on a real Naver Pay 멤버십 데이-style Membership Day (itunda's own first-Monday-of-the-month rule)") {
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val service = ShoppingCashbackService(ledgerService, transactionRepository)

        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_cashback_boosted", emptyList())

        When("awarding cashback -- 2026-08-03 is a real first Monday of the month") {
            val cashback = service.awardCashback(payerAccount(), BigDecimal("5000"), "Kigali Coffee", today = LocalDate.of(2026, 8, 3))

            Then("it real-multiplies the base rate by the real sourced 5x Membership Day boost") {
                cashback shouldBe BigDecimal("250.00")
            }
        }

        When("awarding cashback with a real merchant-boosted rate -- the multiplier applies on top of that too") {
            val cashback = service.awardCashback(payerAccount(), BigDecimal("5000"), "Kigali Coffee", rate = BigDecimal("0.02"), today = LocalDate.of(2026, 8, 3))

            Then("it real-multiplies the boosted rate, not just the default one") {
                cashback shouldBe BigDecimal("500.00")
            }
        }

        When("the boosted amount would exceed the real per-transaction cap") {
            val cashback = service.awardCashback(payerAccount(), BigDecimal("50000"), "Kigali Coffee", today = LocalDate.of(2026, 8, 3))

            Then("it's real-capped at MAX_CASHBACK_PER_TRANSACTION, same as any other day") {
                cashback shouldBe ShoppingCashbackService.MAX_CASHBACK_PER_TRANSACTION
            }
        }

        When("the same weekday falls on the real second Monday of the month instead") {
            val cashback = service.awardCashback(payerAccount(), BigDecimal("5000"), "Kigali Coffee", today = LocalDate.of(2026, 8, 10))

            Then("it's real-excluded -- only the real first Monday counts, not every Monday") {
                cashback shouldBe BigDecimal("50.00")
            }
        }
    }

    Given("a purchase small enough that cashback would round to zero") {
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val service = ShoppingCashbackService(ledgerService, transactionRepository)

        When("awarding cashback on a tiny purchase") {
            val cashback = service.awardCashback(payerAccount(), BigDecimal("0.01"), "Tiny Shop")

            Then("no real ledger transaction is posted for a zero-value reward") {
                cashback shouldBe BigDecimal.ZERO
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
