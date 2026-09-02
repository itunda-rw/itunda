package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * Extracted out of SavingsServiceTest.kt (2026-08-31, second crossing of the 500-line
 * file-size guideline) -- the real gap this feature closes (per-goal ledger isolation,
 * see SavingsService.ensureGoalLedgerAccount's own doc comment) is its own coherent
 * theme, distinct from the deposit/withdraw/auto-save/interest-jar coverage that stays
 * in SavingsServiceTest.kt. Mirrors this repo's own SavingsGoalLimitsAndRemindersTest.kt
 * precedent for splitting one service's test file by theme rather than growing it
 * further.
 */
class SavingsGoalLedgerIsolationTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.MAIN, balance: BigDecimal = BigDecimal("100000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = balance, availableBalance = balance,
    )

    Given("a goal's own per-bucket ledger, the real gap this feature closes") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val ledgerAccountRepository = mockk<LedgerAccountRepository>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val service = SavingsService(accountRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository, notificationRepository, pushNotificationService, ledgerAccountRepository, ledgerEntryRepository)

        When("a brand-new goal (zero balance) gets its first-ever deposit") {
            val goal = SavingsGoal(
                id = "sg_new_1", userId = "user_1", accountId = "account_savings", name = "Brand new goal",
                targetAmount = BigDecimal("100000"), currentAmount = BigDecimal.ZERO,
                monthlyContribution = BigDecimal.ZERO, interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findByIdForUpdate("sg_new_1") } returns Optional.of(goal)
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
            every { ledgerAccountRepository.existsById("sg_ledger_sg_new_1") } returns false
            every { ledgerAccountRepository.save(any()) } answers { firstArg() }
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_new", emptyList())
            every { savingsGoalRepository.save(any()) } answers { firstArg() }

            service.depositToGoal("user_1", "sg_new_1", BigDecimal("5000"), null)

            Then("its own dedicated ledger account is created, but no migration posting fires -- there was nothing to migrate") {
                verify(exactly = 1) { ledgerAccountRepository.save(match { it.id == "sg_ledger_sg_new_1" }) }
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("a pre-existing goal (already holding real money in the old shared pool) gets its first deposit after this feature ships") {
            val goal = SavingsGoal(
                id = "sg_old_1", userId = "user_1", accountId = "account_savings", name = "Pre-existing goal",
                targetAmount = BigDecimal("500000"), currentAmount = BigDecimal("120000"),
                monthlyContribution = BigDecimal.ZERO, interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findByIdForUpdate("sg_old_1") } returns Optional.of(goal)
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
            every { ledgerAccountRepository.existsById("sg_ledger_sg_old_1") } returns false
            every { ledgerAccountRepository.save(any()) } answers { firstArg() }
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_old", emptyList())
            every { savingsGoalRepository.save(any()) } answers { firstArg() }

            service.depositToGoal("user_1", "sg_old_1", BigDecimal("5000"), null)

            Then("a real, auditable opening-balance entry migrates its true existing balance out of the shared pool first, then the real deposit posts on top") {
                verify(exactly = 2) { ledgerService.postLedgerTransaction(any(), any()) }
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        any(),
                        match { legs -> legs.any { it.memo == "Opening balance for Pre-existing goal" && it.amount.compareTo(BigDecimal("120000")) == 0 } },
                    )
                }
            }
        }

        When("fetching a goal's own transaction history") {
            val goal = SavingsGoal(
                id = "sg_hist_1", userId = "user_1", accountId = "account_savings", name = "History goal",
                targetAmount = BigDecimal("100000"), currentAmount = BigDecimal("30000"),
                monthlyContribution = BigDecimal.ZERO, interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findById("sg_hist_1") } returns Optional.of(goal)
            val entry = LedgerEntry(
                id = "entry_1", transactionId = "ledgertxn_1", accountId = "sg_ledger_sg_hist_1",
                accountType = LedgerAccountType.SAVINGS_GOAL_PAYABLE,
                direction = LedgerDirection.CREDIT, amount = BigDecimal("30000"), currency = "RWF",
                balanceAfter = BigDecimal("30000"), memo = "Deposit to History goal", createdAt = Instant.now(),
            )
            every { ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc("sg_ledger_sg_hist_1") } returns listOf(entry)

            val transactions = service.getGoalTransactions("user_1", "sg_hist_1")

            Then("it returns only that goal's own ledger entries, normalized into the shared BucketTransactionDto shape") {
                transactions.size shouldBe 1
                transactions[0].description shouldBe "Deposit to History goal"
                transactions[0].isCredit shouldBe true
                transactions[0].balanceAfter shouldBe BigDecimal("30000")
            }
        }

        When("a stranger requests a goal's transaction history") {
            val goal = SavingsGoal(
                id = "sg_owner_1", userId = "owner_1", accountId = "account_savings", name = "Not yours",
                targetAmount = BigDecimal("100000"), currentAmount = BigDecimal.ZERO,
                monthlyContribution = BigDecimal.ZERO, interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findById("sg_owner_1") } returns Optional.of(goal)

            Then("it throws GoalNotFoundException -- same 404-not-403 pattern as every other goal lookup") {
                try {
                    service.getGoalTransactions("attacker", "sg_owner_1")
                    error("expected GoalNotFoundException")
                } catch (e: GoalNotFoundException) {
                    verify(exactly = 0) { ledgerEntryRepository.findByAccountIdOrderByCreatedAtDesc(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
