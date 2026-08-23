package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

/** First test coverage for savings goals and the interest jar. */
class SavingsServiceTest : BehaviorSpec({

    fun account(id: String, userId: String, type: AccountType = AccountType.MAIN, balance: BigDecimal = BigDecimal("100000")) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = type, balance = balance, availableBalance = balance,
    )

    Given("a user with a savings goal and an interest jar") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        // Real mockk gotcha found while adding the goal-completed celebratory push test
        // below: JpaRepository's generic `<S extends T> S save(S entity)` return type
        // defeats relaxed auto-mocking (a bare relaxed mock throws a real
        // ClassCastException trying to synthesize a default return value for it), the
        // same reason sendMaturityReminder's own test below needs this explicit stub.
        every { notificationRepository.save(any()) } answers { firstArg() }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = SavingsService(accountRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository, notificationRepository, pushNotificationService)

        When("depositing to an owned goal from the default MAIN account") {
            val goal = SavingsGoal(
                id = "sg_1", userId = "user_1", accountId = "account_savings", name = "Emergency Fund",
                targetAmount = BigDecimal("500000"), currentAmount = BigDecimal("100000"),
                monthlyContribution = BigDecimal("50000"), interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findById("sg_1") } returns Optional.of(goal)
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
            every { savingsGoalRepository.findById("sg_2") } returns Optional.of(goal)
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { savingsGoalRepository.save(any()) } answers { firstArg() }

            val result = service.depositToGoal("user_1", "sg_2", BigDecimal("50000"), null)

            Then("progress is capped at the target, not overshot, and the goal completes") {
                result.currentAmount shouldBe BigDecimal("250000")
                result.status shouldBe SavingsGoalStatus.completed
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
            every { savingsGoalRepository.findById("sg_done_1") } returns Optional.of(goal)

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
            every { savingsGoalRepository.findById("sg_3") } returns Optional.of(goal)
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
            every { savingsGoalRepository.findById("sg_4") } returns Optional.of(goal)

            Then("it throws GoalNotFoundException, not a distinct ownership error -- same 404-not-403 pattern as account lookups") {
                try {
                    service.depositToGoal("attacker", "sg_4", BigDecimal("1000"), null)
                    error("expected GoalNotFoundException")
                } catch (e: GoalNotFoundException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("claiming available interest") {
            val jar = InterestJar(
                userId = "user_1", accountId = "account_1", balance = BigDecimal("45000"), rate = 7.5,
                earnedThisMonth = BigDecimal("2500"), earnedTotal = BigDecimal("45000"),
            )
            every { interestJarRepository.findById("user_1") } returns Optional.of(jar)
            every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1"))
            every { interestJarRepository.save(any()) } answers { firstArg() }

            val result = service.claimInterest("user_1")

            // Real fix (2026-08-11): interest now auto-credits the account the instant
            // it accrues (see accrueInterest's own doc comment) -- claimInterest no
            // longer posts a second ledger transaction for money that already
            // arrived, it just clears the running display counter.
            Then("it reports the earned amount and zeroes the jar's display counter, without posting a second ledger credit") {
                result["claimed"] shouldBe BigDecimal("2500")
                jar.earnedThisMonth shouldBe BigDecimal.ZERO
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("claiming interest with nothing earned this month") {
            val jar = InterestJar(
                userId = "user_1", accountId = "account_1", balance = BigDecimal("45000"), rate = 7.5,
                earnedThisMonth = BigDecimal.ZERO, earnedTotal = BigDecimal("45000"),
            )
            every { interestJarRepository.findById("user_1") } returns Optional.of(jar)

            Then("it throws NoInterestAvailableException rather than paying out zero") {
                try {
                    service.claimInterest("user_1")
                    error("expected NoInterestAvailableException")
                } catch (e: NoInterestAvailableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a second claim immediately follows a successful one (double-claim attempt)") {
            val jar = InterestJar(
                userId = "user_1", accountId = "account_1", balance = BigDecimal("45000"), rate = 7.5,
                earnedThisMonth = BigDecimal("2500"), earnedTotal = BigDecimal("45000"),
            )
            every { interestJarRepository.findById("user_1") } returns Optional.of(jar)
            every { accountRepository.findById("account_1") } returns Optional.of(account("account_1", "user_1"))
            every { interestJarRepository.save(any()) } answers { firstArg() }
            service.claimInterest("user_1")

            Then("the second claim throws NoInterestAvailableException since the jar was already zeroed") {
                try {
                    service.claimInterest("user_1")
                    error("expected NoInterestAvailableException")
                } catch (e: NoInterestAvailableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }

    Given("goals due for real recurring auto-save") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = SavingsService(accountRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository, notificationRepository, pushNotificationService)

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
            every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")

            val succeeded = service.autoContribute(g)

            Then("it skips gracefully -- no exception, no ledger call, goal untouched for a later retry") {
                succeeded shouldBe false
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                g.lastAutoContributionAt shouldBe null
            }
        }
    }

    Given("interest jars due for real daily accrual") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = SavingsService(accountRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository, notificationRepository, pushNotificationService)

        fun jar(userId: String, nextPayoutAt: Instant, rate: Double = 7.5) = InterestJar(
            userId = userId, accountId = "account_$userId", balance = BigDecimal.ZERO, rate = rate,
            earnedThisMonth = BigDecimal.ZERO, earnedTotal = BigDecimal.ZERO,
            lastPaidAt = Instant.now(), nextPayoutAt = nextPayoutAt,
        )

        When("finding what's due") {
            val overdue = jar("user_overdue", Instant.now().minus(2, java.time.temporal.ChronoUnit.DAYS))
            val notYet = jar("user_notyet", Instant.now().plus(20, java.time.temporal.ChronoUnit.HOURS))
            every { interestJarRepository.findAll() } returns listOf(overdue, notYet)

            val due = service.getJarsDueForAccrual()

            Then("only the jar whose real day has actually elapsed qualifies") {
                due.map { it.userId } shouldBe listOf("user_overdue")
            }
        }

        When("accruing interest for a jar backed by a real nonzero savings balance") {
            val theJar = jar("user_1", Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
            every { accountRepository.findById("account_user_1") } returns Optional.of(account("account_user_1", "user_1", AccountType.SAVINGS, BigDecimal("36500")))
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_interest_1", emptyList())
            every { interestJarRepository.save(any()) } answers { firstArg() }
            every { transactionRepository.save(any()) } answers { firstArg() }

            service.accrueInterest(theJar)

            Then("it grows earnedThisMonth/earnedTotal off the real account balance, syncs the cached balance, and advances a real day") {
                // 36500 * 7.5% / 365 = 7.50 per day
                theJar.earnedThisMonth shouldBe BigDecimal("7.50")
                theJar.earnedTotal shouldBe BigDecimal("7.50")
                theJar.balance shouldBe BigDecimal("36500")
                verify(exactly = 1) { interestJarRepository.save(theJar) }
            }

            // Real fix (2026-08-11): interest now credits the real account balance the
            // instant it accrues, matching real Toss Bank passbook interest (user-
            // provided screenshots) posting as its own real transaction-history line
            // item, not a separate manually-claimed jar. Replaces the old "unclaimed
            // interest nudge" behavior this same accrual path used to trigger --
            // there's nothing left unclaimed to nudge about anymore.
            Then("it posts a real double-entry ledger credit and a real transaction-history row, not just a display counter") {
                verify(exactly = 1) { ledgerService.postLedgerTransaction("RWF", any()) }
                verify(exactly = 1) {
                    transactionRepository.save(match { it.type == TransactionType.INTEREST && it.recipientId == "user_1" && it.amount == BigDecimal("7.50") })
                }
            }
        }

        When("accruing interest for a jar whose savings account is still empty") {
            val originalNextPayoutAt = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS)
            val theJar = jar("user_2", originalNextPayoutAt)
            every { accountRepository.findById("account_user_2") } returns Optional.of(account("account_user_2", "user_2", AccountType.SAVINGS, BigDecimal.ZERO))
            every { interestJarRepository.save(any()) } answers { firstArg() }

            service.accrueInterest(theJar)

            Then("no interest accrues on a zero balance, but the payout window still advances -- no stuck jar") {
                theJar.earnedThisMonth shouldBe BigDecimal.ZERO
                theJar.earnedTotal shouldBe BigDecimal.ZERO
                theJar.nextPayoutAt shouldBe originalNextPayoutAt.plus(1, java.time.temporal.ChronoUnit.DAYS)
            }
        }

        When("accruing across repeated real days keeps building on top of the running total") {
            val theJar = jar("user_3", Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
            every { accountRepository.findById("account_user_3") } returns Optional.of(account("account_user_3", "user_3", AccountType.SAVINGS, BigDecimal("36500")))
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_interest_3", emptyList())
            every { interestJarRepository.save(any()) } answers { firstArg() }
            every { transactionRepository.save(any()) } answers { firstArg() }

            service.accrueInterest(theJar)
            service.accrueInterest(theJar)

            Then("two real days of accrual add up rather than overwrite") {
                theJar.earnedThisMonth shouldBe BigDecimal("15.00")
                theJar.earnedTotal shouldBe BigDecimal("15.00")
            }

            Then("each real day posts its own real ledger credit and transaction row -- two real days, two real postings") {
                verify(exactly = 2) { ledgerService.postLedgerTransaction("RWF", any()) }
                verify(exactly = 2) { transactionRepository.save(match { it.type == TransactionType.INTEREST }) }
            }
        }
    }

    Given("a real user exceeds the real goal-creation rate limit") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = SavingsService(accountRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository, notificationRepository, pushNotificationService)
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

    Given("a real KB국민은행-style savings goal maturity reminder") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>()
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        every { notificationRepository.save(any()) } answers { firstArg() }
        val service = SavingsService(accountRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository, notificationRepository, pushNotificationService)

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
