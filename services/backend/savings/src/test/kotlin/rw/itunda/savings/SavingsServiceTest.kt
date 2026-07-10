package rw.itunda.savings

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.InterestJar
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.InterestJarRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/** First test coverage for savings goals and the interest jar. */
class SavingsServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String, type: WalletType = WalletType.MAIN) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = type, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a user with a savings goal and an interest jar") {
        val walletRepository = mockk<WalletRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val interestJarRepository = mockk<InterestJarRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = SavingsService(walletRepository, savingsGoalRepository, interestJarRepository, ledgerService)

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
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_3", emptyList())
            every { interestJarRepository.save(any()) } answers { firstArg() }

            val result = service.claimInterest("user_1")

            Then("it pays out the full earned amount and zeroes the jar so it can't be claimed twice") {
                result["claimed"] shouldBe BigDecimal("2500")
                jar.earnedThisMonth shouldBe BigDecimal.ZERO
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
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_4", emptyList())
            every { interestJarRepository.save(any()) } answers { firstArg() }
            service.claimInterest("user_1")

            Then("the second claim throws NoInterestAvailableException since the jar was already zeroed") {
                try {
                    service.claimInterest("user_1")
                    error("expected NoInterestAvailableException")
                } catch (e: NoInterestAvailableException) {
                    verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
