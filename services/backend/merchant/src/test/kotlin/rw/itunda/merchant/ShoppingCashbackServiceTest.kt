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
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal

class ShoppingCashbackServiceTest : BehaviorSpec({

    fun payerWallet() = Wallet(
        id = "wallet_payer", userId = "user_1", accountNumber = "ACC-1", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a real purchase at an itunda-registered merchant") {
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        every { transactionRepository.save(any()) } answers { firstArg() }
        val service = ShoppingCashbackService(ledgerService, transactionRepository)

        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_cashback", emptyList())

        When("awarding cashback on a 5,000 RWF purchase") {
            val cashback = service.awardCashback(payerWallet(), BigDecimal("5000"), "Kigali Coffee")

            Then("it's itunda's own real flat 1% rate, not an invented-looking sourced number") {
                cashback shouldBe BigDecimal("50.00")
            }
            Then("it posts a real, balanced 2-leg ledger transaction: payer credit, rewards_expense debit") {
                val legs = legsSlot.captured
                legs.size shouldBe 2
                val creditLeg = legs.first { it.direction == LedgerDirection.CREDIT }
                creditLeg.accountId shouldBe "wallet_payer"
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

    Given("a purchase small enough that cashback would round to zero") {
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val service = ShoppingCashbackService(ledgerService, transactionRepository)

        When("awarding cashback on a tiny purchase") {
            val cashback = service.awardCashback(payerWallet(), BigDecimal("0.01"), "Tiny Shop")

            Then("no real ledger transaction is posted for a zero-value reward") {
                cashback shouldBe BigDecimal.ZERO
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
