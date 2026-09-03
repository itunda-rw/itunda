package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for savings goals. Interest jar coverage moved to
 * InterestJarServiceTest.kt (2026-09-03) -- see InterestJarService.kt's own doc comment.
 */
class SavingsServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.MAIN, balance: BigDecimal = BigDecimal("100000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = balance, availableBalance = balance,
    )

    Given("a user with a savings goal") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        // Real mockk gotcha found while adding the goal-completed celebratory push test
        // below: JpaRepository's generic `<S extends T> S save(S entity)` return type
        // defeats relaxed auto-mocking (a bare relaxed mock throws a real
        // ClassCastException trying to synthesize a default return value for it), the
        // same reason sendMaturityReminder's own test below needs this explicit stub.
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val ledgerAccountRepository = mockk<LedgerAccountRepository>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        // Real per-bucket ledger isolation (2026-08-31) -- pretend every goal already
        // has its own dedicated ledger account so existing assertions below (which
        // predate this feature and count exactly one postLedgerTransaction call per
        // deposit/withdraw/auto-contribute) don't also see the one-time migration
        // posting ensureGoalLedgerAccount fires for a goal that doesn't have one yet
        // -- that specific behavior gets its own dedicated test further down.
        every { ledgerAccountRepository.existsById(any()) } returns true
        val service = SavingsService(accountRepository, savingsGoalRepository, ledgerService, rateLimiter, notificationRepository, pushNotificationService, ledgerAccountRepository, ledgerEntryRepository)

        When("depositing to an owned goal from the default MAIN account") {
            val goal = SavingsGoal(
                id = "sg_1", userId = "user_1", accountId = "account_savings", name = "Emergency Fund",
                targetAmount = BigDecimal("500000"), currentAmount = BigDecimal("100000"),
                monthlyContribution = BigDecimal("50000"), interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findByIdForUpdate("sg_1") } returns Optional.of(goal)
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { savingsGoalRepository.save(any()) } answers { firstArg() }

            val result = service.depositToGoal("user_1", "sg_1", BigDecimal("50000"), null)

            Then("it posts to the ledger and increases the goal's progress, staying active") {
                result.currentAmount shouldBe BigDecimal("150000")
                result.status shouldBe SavingsGoalStatus.active
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("a deposit reaches the goal's target amount") {
            val goal = SavingsGoal(
                id = "sg_2", userId = "user_1", accountId = "account_savings", name = "New Laptop",
                targetAmount = BigDecimal("250000"), currentAmount = BigDecimal("240000"),
                monthlyContribution = BigDecimal("30000"), interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findByIdForUpdate("sg_2") } returns Optional.of(goal)
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { savingsGoalRepository.save(any()) } answers { firstArg() }

            val result = service.depositToGoal("user_1", "sg_2", BigDecimal("50000"), null)

            Then("progress is capped at the target, not overshot, and the goal completes") {
                result.currentAmount shouldBe BigDecimal("250000")
                result.status shouldBe SavingsGoalStatus.completed
            }

            // Real bug found live (2026-08-31, via InsuranceService.contributeToFund's own
            // build-time review comment naming this exact bug class here first): the
            // FIELD was already correctly capped (the Then above), but the ledger legs
            // used to move the full 50,000 requested regardless -- permanently stranding
            // the 40,000 overshoot in savings_goal_payable with no path back to the user,
            // since withdrawFromGoal can only ever reclaim up to the capped
            // goal.currentAmount. Only the real 10,000 gap should ever touch the ledger.
            Then("only the real 10,000 RWF gap is ever moved through the ledger, not the full 50,000 requested") {
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        any(),
                        match { legs -> legs.all { it.amount.compareTo(BigDecimal("10000")) == 0 } },
                    )
                }
            }

            Then("a real celebratory notification and push fire exactly once for the completion") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "SAVINGS_GOAL_COMPLETED" && it.userId == "user_1" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any(), "SAVINGS_GOAL_COMPLETED") }
            }
        }

        When("depositing into a goal that has already reached its target") {
            val goal = SavingsGoal(
                id = "sg_done_1", userId = "user_1", accountId = "account_savings", name = "Already done",
                targetAmount = BigDecimal("100000"), currentAmount = BigDecimal("100000"),
                monthlyContribution = BigDecimal.ZERO, interestRate = 7.5, createdAt = Instant.now(),
                status = SavingsGoalStatus.completed,
            )
            every { savingsGoalRepository.findByIdForUpdate("sg_done_1") } returns Optional.of(goal)

            Then("it throws GoalAlreadyCompletedException before moving any real money") {
                try {
                    service.depositToGoal("user_1", "sg_done_1", BigDecimal("1000"), null)
                    error("expected GoalAlreadyCompletedException")
                } catch (e: GoalAlreadyCompletedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("depositing from an explicit account that belongs to someone else") {
            val goal = SavingsGoal(
                id = "sg_3", userId = "user_1", accountId = "account_savings", name = "Goal",
                targetAmount = BigDecimal("100000"), currentAmount = BigDecimal.ZERO,
                monthlyContribution = BigDecimal.ZERO, interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findByIdForUpdate("sg_3") } returns Optional.of(goal)
            every { accountRepository.findById("account_other") } returns Optional.of(account("account_other", "someone_else"))

            Then("it throws AccountNotOwnedException before touching the ledger") {
                try {
                    service.depositToGoal("user_1", "sg_3", BigDecimal("1000"), "account_other")
                    error("expected AccountNotOwnedException")
                } catch (e: AccountNotOwnedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("depositing to a goal that belongs to someone else") {
            val goal = SavingsGoal(
                id = "sg_4", userId = "owner_1", accountId = "account_savings", name = "Goal",
                targetAmount = BigDecimal("100000"), currentAmount = BigDecimal.ZERO,
                monthlyContribution = BigDecimal.ZERO, interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findByIdForUpdate("sg_4") } returns Optional.of(goal)

            Then("it throws GoalNotFoundException, not a distinct ownership error -- same 404-not-403 pattern as account lookups") {
                try {
                    service.depositToGoal("attacker", "sg_4", BigDecimal("1000"), null)
                    error("expected GoalNotFoundException")
                } catch (e: GoalNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

    }

    Given("goals due for real recurring auto-save") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val ledgerAccountRepository = mockk<LedgerAccountRepository>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        // Real per-bucket ledger isolation (2026-08-31) -- pretend every goal already
        // has its own dedicated ledger account so existing assertions below (which
        // predate this feature and count exactly one postLedgerTransaction call per
        // deposit/withdraw/auto-contribute) don't also see the one-time migration
        // posting ensureGoalLedgerAccount fires for a goal that doesn't have one yet
        // -- that specific behavior gets its own dedicated test further down.
        every { ledgerAccountRepository.existsById(any()) } returns true
        val service = SavingsService(accountRepository, savingsGoalRepository, ledgerService, rateLimiter, notificationRepository, pushNotificationService, ledgerAccountRepository, ledgerEntryRepository)

        fun goal(id: String, monthlyContribution: String, lastAutoContributionAt: Instant?, status: SavingsGoalStatus = SavingsGoalStatus.active) = SavingsGoal(
            id = id, userId = "user_1", accountId = "account_savings", name = "Goal $id",
            targetAmount = BigDecimal("500000"), currentAmount = BigDecimal("10000"),
            monthlyContribution = BigDecimal(monthlyContribution), interestRate = 7.5,
            status = status, lastAutoContributionAt = lastAutoContributionAt,
        )

        When("finding what's due") {
            val neverContributed = goal("sg_new", "10000", null)
            val overdue = goal("sg_overdue", "10000", Instant.now().minus(31, java.time.temporal.ChronoUnit.DAYS))
            val recentlyContributed = goal("sg_recent", "10000", Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS))
            val zeroContribution = goal("sg_zero", "0", null)
            val completedGoal = goal("sg_done", "10000", null, SavingsGoalStatus.completed)

            every { savingsGoalRepository.findAll() } returns listOf(neverContributed, overdue, recentlyContributed, zeroContribution, completedGoal)

            val due = service.getGoalsDueForAutoContribution()

            Then("only never-contributed and truly-overdue active goals with a real nonzero contribution qualify") {
                due.map { it.id }.toSet() shouldBe setOf("sg_new", "sg_overdue")
            }
        }

        When("auto-contributing to a goal with sufficient funds") {
            val g = goal("sg_ok", "20000", null)
            every { savingsGoalRepository.findByIdForUpdate(g.id) } returns Optional.of(g)
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_auto", emptyList())
            every { savingsGoalRepository.save(any()) } answers { firstArg() }

            val succeeded = service.autoContribute(g)

            Then("it posts to the ledger, advances the goal, and stamps a real lastAutoContributionAt") {
                succeeded shouldBe true
                g.currentAmount shouldBe BigDecimal("30000")
                (g.lastAutoContributionAt != null) shouldBe true
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("auto-contributing to a goal without enough balance") {
            val g = goal("sg_poor", "999999999", null)
            every { savingsGoalRepository.findByIdForUpdate(g.id) } returns Optional.of(g)
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")

            val succeeded = service.autoContribute(g)

            Then("it skips gracefully -- no exception, no ledger call, goal untouched for a later retry") {
                succeeded shouldBe false
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                g.lastAutoContributionAt shouldBe null
            }
        }

        // Real bug found live (2026-08-31, same overshoot bug class as depositToGoal's
        // own regression test above): a recurring contribution larger than the real
        // remaining gap to the target used to move its FULL amount through the ledger
        // regardless, stranding the excess the same way an overshooting manual deposit
        // did.
        When("auto-contributing an amount larger than what's left to reach the target") {
            val g = SavingsGoal(
                id = "sg_almost", userId = "user_1", accountId = "account_savings", name = "Almost there",
                targetAmount = BigDecimal("500000"), currentAmount = BigDecimal("490000"),
                monthlyContribution = BigDecimal("20000"), interestRate = 7.5,
            )
            every { savingsGoalRepository.findByIdForUpdate(g.id) } returns Optional.of(g)
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_auto2", emptyList())
            every { savingsGoalRepository.save(any()) } answers { firstArg() }

            val succeeded = service.autoContribute(g)

            Then("only the real 10,000 RWF gap moves through the ledger, not the full 20,000 contribution, and the goal completes") {
                succeeded shouldBe true
                g.currentAmount shouldBe BigDecimal("500000")
                verify(exactly = 1) {
                    ledgerService.postLedgerTransaction(
                        any(),
                        match { legs -> legs.all { it.amount.compareTo(BigDecimal("10000")) == 0 } },
                    )
                }
            }
        }
    }

}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
