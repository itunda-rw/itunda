package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

/**
 * Extracted out of SavingsServiceTest.kt (2026-08-31, first crossing of the 500-line
 * file-size guideline) -- the goal-creation rate limit and the real KB국민은행-style
 * maturity reminder are their own coherent theme, distinct from deposit/withdraw/
 * auto-save/interest-jar coverage that stays in SavingsServiceTest.kt. Mirrors this
 * repo's own EatsOrderServiceTest.kt/EatsOrderDeliveryBrowseTest.kt precedent for
 * splitting one service's test file by theme rather than growing it further.
 */
class SavingsGoalLimitsAndRemindersTest : BehaviorSpec({

    Given("a real user exceeds the real goal-creation rate limit") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>()
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val ledgerAccountRepository = mockk<LedgerAccountRepository>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val service = SavingsService(accountRepository, savingsGoalRepository, ledgerService, rateLimiter, notificationRepository, pushNotificationService, ledgerAccountRepository, ledgerEntryRepository)
        every { rateLimiter.checkLimit("savings:goal:user_9", limit = 10, window = Duration.ofHours(1)) } throws RateLimitExceededException("Too many requests")

        When("they try to create another real goal") {
            Then("it real-propagates RateLimitExceededException, found missing in a 2026-07-19 security sweep") {
                try {
                    service.createGoal("user_9", "Goal", BigDecimal("100000"), null, null, null)
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { savingsGoalRepository.save(any()) }
                }
            }
        }
    }

    // Real gap found live (2026-09-14, sibling-asymmetry sweep): depositToGoal/
    // withdrawFromGoal both reject a non-positive amount before it reaches the ledger;
    // createGoal's own equally attacker-controlled targetAmount never did, letting a
    // zero/negative goal reach depositToGoal later and throw an unhandled
    // LedgerImbalanceException (500) with no controller-level handler anywhere in this
    // codebase.
    Given("a real user trying to create a goal with a real non-positive target amount") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val ledgerAccountRepository = mockk<LedgerAccountRepository>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val service = SavingsService(accountRepository, savingsGoalRepository, ledgerService, rateLimiter, notificationRepository, pushNotificationService, ledgerAccountRepository, ledgerEntryRepository)

        When("targetAmount is zero") {
            Then("it real-400s (via the existing InsufficientGoalBalanceException mapping) before ever touching the account or the goal table") {
                try {
                    service.createGoal("user_10", "Goal", BigDecimal.ZERO, null, null, null)
                    error("expected InsufficientGoalBalanceException")
                } catch (e: InsufficientGoalBalanceException) {
                    verify(exactly = 0) { accountRepository.findByUserIdAndType(any(), any()) }
                    verify(exactly = 0) { savingsGoalRepository.save(any()) }
                }
            }
        }

        When("targetAmount is negative") {
            Then("it real-rejects the same way") {
                try {
                    service.createGoal("user_10", "Goal", BigDecimal("-500"), null, null, null)
                    error("expected InsufficientGoalBalanceException")
                } catch (e: InsufficientGoalBalanceException) {
                    verify(exactly = 0) { savingsGoalRepository.save(any()) }
                }
            }
        }
    }

    Given("a real KB국민은행-style savings goal maturity reminder") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>()
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val ledgerAccountRepository = mockk<LedgerAccountRepository>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        val service = SavingsService(accountRepository, savingsGoalRepository, ledgerService, rateLimiter, notificationRepository, pushNotificationService, ledgerAccountRepository, ledgerEntryRepository)

        fun goalWithTarget(id: String, userId: String, targetDate: String?, status: SavingsGoalStatus = SavingsGoalStatus.active, maturityNotifiedAt: Instant? = null) = SavingsGoal(
            id = id, userId = userId, accountId = "account_$userId", name = "Goal $id",
            targetAmount = BigDecimal("500000"), currentAmount = BigDecimal("500000"),
            monthlyContribution = BigDecimal.ZERO, interestRate = 7.5, targetDate = targetDate,
            status = status, maturityNotifiedAt = maturityNotifiedAt,
        )

        When("a real active goal's targetDate has already passed") {
            val past = goalWithTarget("sg_past", "user_a", java.time.LocalDate.now().minusDays(1).toString())
            every { savingsGoalRepository.findByStatusAndTargetDateIsNotNullAndMaturityNotifiedAtIsNull(SavingsGoalStatus.active) } returns listOf(past)

            Then("it is a real due candidate") {
                service.getGoalsDueForMaturityReminder().map { it.id } shouldBe listOf("sg_past")
            }
        }

        When("a real active goal's targetDate is genuinely still in the future") {
            val future = goalWithTarget("sg_future", "user_b", java.time.LocalDate.now().plusDays(5).toString())
            every { savingsGoalRepository.findByStatusAndTargetDateIsNotNullAndMaturityNotifiedAtIsNull(SavingsGoalStatus.active) } returns listOf(future)

            Then("it is real-excluded -- not due yet") {
                service.getGoalsDueForMaturityReminder() shouldBe emptyList()
            }
        }

        When("a real goal's targetDate is genuinely unparseable free text") {
            val bad = goalWithTarget("sg_bad", "user_c", "not-a-real-date")
            every { savingsGoalRepository.findByStatusAndTargetDateIsNotNullAndMaturityNotifiedAtIsNull(SavingsGoalStatus.active) } returns listOf(bad)

            Then("it is real-skipped rather than crashing the whole sweep") {
                service.getGoalsDueForMaturityReminder() shouldBe emptyList()
            }
        }

        When("sending a real reminder for a genuinely due goal") {
            val goal = goalWithTarget("sg_due", "user_d", java.time.LocalDate.now().toString())
            every { savingsGoalRepository.findById("sg_due") } returns Optional.of(goal)
            every { savingsGoalRepository.save(any()) } answers { firstArg() }

            service.sendMaturityReminder("sg_due")

            Then("it real-notifies once and real-marks maturityNotifiedAt") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "SAVINGS_GOAL_MATURED" && it.userId == "user_d" }) }
                goal.maturityNotifiedAt shouldNotBe null
            }

            // Real fix (2026-09-13, push-before-commit ordering sweep): maturityNotifiedAt
            // must be saved BEFORE the push fires -- otherwise a rollback after the push
            // leaves the flag unset and the next scheduler pass resends it.
            Then("the maturityNotifiedAt flag is saved before the push is sent") {
                verifyOrder {
                    savingsGoalRepository.save(any())
                    pushNotificationService.sendToUser("user_d", any(), any(), any())
                }
            }
        }

        When("sending a reminder for a goal that already has one") {
            val goal = goalWithTarget("sg_already", "user_e", java.time.LocalDate.now().toString(), maturityNotifiedAt = Instant.now())
            every { savingsGoalRepository.findById("sg_already") } returns Optional.of(goal)

            service.sendMaturityReminder("sg_already")

            Then("it real-skips -- no double notification for the same real maturity") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
