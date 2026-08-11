package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.Optional

/** First test coverage for savings goals and the interest jar. */
class SavingsServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, type: WalletType = WalletType.MAIN, balance: BigDecimal = BigDecimal("100000")) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = type, balance = balance, availableBalance = balance,
    )

    Given("a user with a savings goal and an interest jar") {
        val walletRepository = mockk<WalletRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val service = SavingsService(walletRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository)

        When("depositing to an owned goal from the default MAIN wallet") {
            val goal = SavingsGoal(
                id = "sg_1", userId = "user_1", walletId = "wallet_savings", name = "Emergency Fund",
                targetAmount = BigDecimal("500000"), currentAmount = BigDecimal("100000"),
                monthlyContribution = BigDecimal("50000"), interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findById("sg_1") } returns Optional.of(goal)
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
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
                id = "sg_2", userId = "user_1", walletId = "wallet_savings", name = "New Laptop",
                targetAmount = BigDecimal("250000"), currentAmount = BigDecimal("240000"),
                monthlyContribution = BigDecimal("30000"), interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findById("sg_2") } returns Optional.of(goal)
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { savingsGoalRepository.save(any()) } answers { firstArg() }

            val result = service.depositToGoal("user_1", "sg_2", BigDecimal("50000"), null)

            Then("progress is capped at the target, not overshot, and the goal completes") {
                result.currentAmount shouldBe BigDecimal("250000")
                result.status shouldBe SavingsGoalStatus.completed
            }
        }

        When("depositing from an explicit wallet that belongs to someone else") {
            val goal = SavingsGoal(
                id = "sg_3", userId = "user_1", walletId = "wallet_savings", name = "Goal",
                targetAmount = BigDecimal("100000"), currentAmount = BigDecimal.ZERO,
                monthlyContribution = BigDecimal.ZERO, interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findById("sg_3") } returns Optional.of(goal)
            every { walletRepository.findById("wallet_other") } returns Optional.of(wallet("wallet_other", "someone_else"))

            Then("it throws WalletNotOwnedException before touching the ledger") {
                try {
                    service.depositToGoal("user_1", "sg_3", BigDecimal("1000"), "wallet_other")
                    error("expected WalletNotOwnedException")
                } catch (e: WalletNotOwnedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("depositing to a goal that belongs to someone else") {
            val goal = SavingsGoal(
                id = "sg_4", userId = "owner_1", walletId = "wallet_savings", name = "Goal",
                targetAmount = BigDecimal("100000"), currentAmount = BigDecimal.ZERO,
                monthlyContribution = BigDecimal.ZERO, interestRate = 7.5, createdAt = Instant.now(),
            )
            every { savingsGoalRepository.findById("sg_4") } returns Optional.of(goal)

            Then("it throws GoalNotFoundException, not a distinct ownership error -- same 404-not-403 pattern as wallet lookups") {
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
                userId = "user_1", walletId = "wallet_1", balance = BigDecimal("45000"), rate = 7.5,
                earnedThisMonth = BigDecimal("2500"), earnedTotal = BigDecimal("45000"),
            )
            every { interestJarRepository.findById("user_1") } returns Optional.of(jar)
            every { walletRepository.findById("wallet_1") } returns Optional.of(wallet("wallet_1", "user_1"))
            every { interestJarRepository.save(any()) } answers { firstArg() }

            val result = service.claimInterest("user_1")

            // Real fix (2026-08-11): interest now auto-credits the wallet the instant
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
                userId = "user_1", walletId = "wallet_1", balance = BigDecimal("45000"), rate = 7.5,
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
                userId = "user_1", walletId = "wallet_1", balance = BigDecimal("45000"), rate = 7.5,
                earnedThisMonth = BigDecimal("2500"), earnedTotal = BigDecimal("45000"),
            )
            every { interestJarRepository.findById("user_1") } returns Optional.of(jar)
            every { walletRepository.findById("wallet_1") } returns Optional.of(wallet("wallet_1", "user_1"))
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
        val walletRepository = mockk<WalletRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val service = SavingsService(walletRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository)

        fun goal(id: String, monthlyContribution: String, lastAutoContributionAt: Instant?, status: SavingsGoalStatus = SavingsGoalStatus.active) = SavingsGoal(
            id = id, userId = "user_1", walletId = "wallet_savings", name = "Goal $id",
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
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
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
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")

            val succeeded = service.autoContribute(g)

            Then("it skips gracefully -- no exception, no ledger call, goal untouched for a later retry") {
                succeeded shouldBe false
                verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                g.lastAutoContributionAt shouldBe null
            }
        }
    }

    Given("interest jars due for real daily accrual") {
        val walletRepository = mockk<WalletRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val service = SavingsService(walletRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository)

        fun jar(userId: String, nextPayoutAt: Instant, rate: Double = 7.5) = InterestJar(
            userId = userId, walletId = "wallet_$userId", balance = BigDecimal.ZERO, rate = rate,
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
            every { walletRepository.findById("wallet_user_1") } returns Optional.of(wallet("wallet_user_1", "user_1", WalletType.SAVINGS, BigDecimal("36500")))
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_interest_1", emptyList())
            every { interestJarRepository.save(any()) } answers { firstArg() }
            every { transactionRepository.save(any()) } answers { firstArg() }

            service.accrueInterest(theJar)

            Then("it grows earnedThisMonth/earnedTotal off the real wallet balance, syncs the cached balance, and advances a real day") {
                // 36500 * 7.5% / 365 = 7.50 per day
                theJar.earnedThisMonth shouldBe BigDecimal("7.50")
                theJar.earnedTotal shouldBe BigDecimal("7.50")
                theJar.balance shouldBe BigDecimal("36500")
                verify(exactly = 1) { interestJarRepository.save(theJar) }
            }

            // Real fix (2026-08-11): interest now credits the real wallet balance the
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

        When("accruing interest for a jar whose savings wallet is still empty") {
            val originalNextPayoutAt = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS)
            val theJar = jar("user_2", originalNextPayoutAt)
            every { walletRepository.findById("wallet_user_2") } returns Optional.of(wallet("wallet_user_2", "user_2", WalletType.SAVINGS, BigDecimal.ZERO))
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
            every { walletRepository.findById("wallet_user_3") } returns Optional.of(wallet("wallet_user_3", "user_3", WalletType.SAVINGS, BigDecimal("36500")))
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
        val walletRepository = mockk<WalletRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>()
        val transactionRepository = mockk<TransactionRepository>(relaxed = true)
        val service = SavingsService(walletRepository, savingsGoalRepository, interestJarRepository, ledgerService, rateLimiter, transactionRepository)
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
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
