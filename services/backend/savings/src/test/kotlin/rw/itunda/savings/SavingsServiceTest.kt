package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.LedgerAccount
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.LedgerEntry
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.LedgerAccountRepository
import rw.itunda.core.repository.LedgerEntryRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
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
        val ledgerAccountRepository = mockk<LedgerAccountRepository>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        // Real per-bucket ledger isolation (2026-08-31) -- pretend every goal already
        // has its own dedicated ledger account so existing assertions below (which
        // predate this feature and count exactly one postLedgerTransaction call per
        // deposit/withdraw/auto-contribute) don't also see the one-time migration
        // posting ensureGoalLedgerAccount fires for a goal that doesn't have one yet
        // -- that specific behavior gets its own dedicated test further down.
        every { ledgerAccountRepository.existsById(any()) } returns true
        val service = SavingsService(accountRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository, notificationRepository, pushNotificationService, ledgerAccountRepository, ledgerEntryRepository)

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
        val ledgerAccountRepository = mockk<LedgerAccountRepository>(relaxed = true)
        val ledgerEntryRepository = mockk<LedgerEntryRepository>(relaxed = true)
        // Real per-bucket ledger isolation (2026-08-31) -- pretend every goal already
        // has its own dedicated ledger account so existing assertions below (which
        // predate this feature and count exactly one postLedgerTransaction call per
        // deposit/withdraw/auto-contribute) don't also see the one-time migration
        // posting ensureGoalLedgerAccount fires for a goal that doesn't have one yet
        // -- that specific behavior gets its own dedicated test further down.
        every { ledgerAccountRepository.existsById(any()) } returns true
        val service = SavingsService(accountRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository, notificationRepository, pushNotificationService, ledgerAccountRepository, ledgerEntryRepository)

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

    Given("interest jars due for real daily accrual") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true)
        // See the earlier "a user with a savings goal and an interest jar" Given
        // block's own identical comment -- relaxed mockk can't synthesize a valid
        // return for JpaRepository's generic save(), and this stub must live at the
        // Given level (IsolationMode.InstancePerLeaf only re-walks the path to each leaf).
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
        val service = SavingsService(accountRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository, notificationRepository, pushNotificationService, ledgerAccountRepository, ledgerEntryRepository)

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

            Then("a real celebratory notification and push fire once for this jar's first-ever accrual") {
                verify(exactly = 1) { notificationRepository.save(match { it.type == "FIRST_INTEREST_ACCRUAL" && it.userId == "user_1" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any(), "FIRST_INTEREST_ACCRUAL") }
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

            Then("no celebratory push fires when nothing was actually earned") {
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any(), "FIRST_INTEREST_ACCRUAL") }
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

            Then("the celebratory push fires only once -- day 2 already has a nonzero earnedTotal, not a first accrual") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_3", any(), any(), any(), "FIRST_INTEREST_ACCRUAL") }
            }
        }
    }

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
            every { savingsGoalRepository.findById("sg_new_1") } returns Optional.of(goal)
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
            every { savingsGoalRepository.findById("sg_old_1") } returns Optional.of(goal)
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
                accountType = rw.itunda.core.domain.LedgerAccountType.SAVINGS_GOAL_PAYABLE,
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
