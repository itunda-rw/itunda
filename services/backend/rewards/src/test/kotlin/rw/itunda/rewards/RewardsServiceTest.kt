package rw.itunda.rewards

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.RewardClaim
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.RewardClaimRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant

class RewardsServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("10000"), availableBalance = BigDecimal("10000"),
    )

    Given("a user who hasn't claimed anything yet and hasn't done any of the checkable tasks") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = RewardsService(rewardClaimRepository, walletRepository, ledgerService, transactionRepository, savingsGoalRepository)

        every { rewardClaimRepository.findByUserId("user_1") } returns emptyList()
        every { transactionRepository.existsBySenderIdAndTypeAndStatus("user_1", any(), TransactionStatus.COMPLETED) } returns false
        every { savingsGoalRepository.existsByUserId("user_1") } returns false

        When("listing tasks") {
            val result = service.getTasks("user_1")

            Then("every catalog task shows claimed=false and the total is zero") {
                result.tasks.size shouldBe service.taskCatalog.size
                result.tasks.all { !it.claimed } shouldBe true
                result.rewardsTotal shouldBe BigDecimal.ZERO
            }
            Then("only the two unverifiable tasks (profile, referral) show eligible=true -- the other three require real activity that hasn't happened") {
                val byId = result.tasks.associateBy { it.id }
                byId.getValue("task_first_transfer").eligible shouldBe false
                byId.getValue("task_first_bill").eligible shouldBe false
                byId.getValue("task_savings_goal").eligible shouldBe false
                byId.getValue("task_profile").eligible shouldBe true
                byId.getValue("task_referral").eligible shouldBe true
            }
        }
    }

    Given("a user who has completed a real transfer, claiming task_first_transfer for the first time") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = RewardsService(rewardClaimRepository, walletRepository, ledgerService, transactionRepository, savingsGoalRepository)

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_2", "task_first_transfer") } returns false
        every { transactionRepository.existsBySenderIdAndTypeAndStatus("user_2", TransactionType.TRANSFER, TransactionStatus.COMPLETED) } returns true
        every { walletRepository.findByUserIdAndType("user_2", WalletType.MAIN) } returns wallet("wallet_main", "user_2")
        val legsSlot = slot<List<LedgerLeg>>()
        every { ledgerService.postLedgerTransaction("RWF", capture(legsSlot)) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { rewardClaimRepository.save(any()) } answers { firstArg() }
        every { rewardClaimRepository.findByUserId("user_2") } returns listOf(
            RewardClaim(id = "rwc_1", userId = "user_2", taskId = "task_first_transfer", amount = BigDecimal("1000"), claimedAt = Instant.now()),
        )

        When("claiming task_first_transfer (1,000 RWF)") {
            val result = service.claim("user_2", "task_first_transfer")

            Then("it debits rewards_expense and credits the MAIN wallet for the exact same amount") {
                val expenseLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.REWARDS_EXPENSE }
                expenseLeg.accountId shouldBe "rewards_expense"
                expenseLeg.direction shouldBe LedgerDirection.DEBIT
                expenseLeg.amount shouldBe BigDecimal("1000")

                val walletLeg = legsSlot.captured.first { it.accountType == LedgerAccountType.WALLET }
                walletLeg.accountId shouldBe "wallet_main"
                walletLeg.direction shouldBe LedgerDirection.CREDIT
                walletLeg.amount shouldBe BigDecimal("1000")
            }
            Then("it saves a real claim record and returns the new total") {
                result.rewardAmount shouldBe BigDecimal("1000")
                result.newBalance shouldBe BigDecimal("1000")
                verify(exactly = 1) { rewardClaimRepository.save(any()) }
            }
        }
    }

    Given("a user who has NOT completed a real transfer, trying to claim task_first_transfer") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = RewardsService(rewardClaimRepository, walletRepository, ledgerService, transactionRepository, savingsGoalRepository)

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_6", "task_first_transfer") } returns false
        every { transactionRepository.existsBySenderIdAndTypeAndStatus("user_6", TransactionType.TRANSFER, TransactionStatus.COMPLETED) } returns false

        When("claiming it") {
            Then("it throws RewardTaskNotEligibleException before ever touching the wallet or ledger -- this is the real fix for the honor-system gap") {
                try {
                    service.claim("user_6", "task_first_transfer")
                    error("expected RewardTaskNotEligibleException")
                } catch (e: RewardTaskNotEligibleException) {
                    verify(exactly = 0) { walletRepository.findByUserIdAndType(any(), any()) }
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                    verify(exactly = 0) { rewardClaimRepository.save(any()) }
                }
            }
        }
    }

    Given("a task that's already been claimed") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = RewardsService(rewardClaimRepository, walletRepository, ledgerService, transactionRepository, savingsGoalRepository)

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_3", "task_profile") } returns true

        When("claiming it again") {
            Then("it throws RewardTaskAlreadyClaimedException before touching the ledger -- the whole point of the claim-once guard") {
                try {
                    service.claim("user_3", "task_profile")
                    error("expected RewardTaskAlreadyClaimedException")
                } catch (e: RewardTaskAlreadyClaimedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                    verify(exactly = 0) { rewardClaimRepository.save(any()) }
                }
            }
        }
    }

    Given("a task ID that isn't in the catalog") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = RewardsService(rewardClaimRepository, walletRepository, ledgerService, transactionRepository, savingsGoalRepository)

        When("claiming it") {
            Then("it throws RewardTaskNotFoundException before checking claim status at all") {
                try {
                    service.claim("user_4", "task_does_not_exist")
                    error("expected RewardTaskNotFoundException")
                } catch (e: RewardTaskNotFoundException) {
                    verify(exactly = 0) { rewardClaimRepository.existsByUserIdAndTaskId(any(), any()) }
                }
            }
        }
    }

    Given("a user with no wallet at all") {
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val transactionRepository = mockk<TransactionRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = RewardsService(rewardClaimRepository, walletRepository, ledgerService, transactionRepository, savingsGoalRepository)

        every { rewardClaimRepository.existsByUserIdAndTaskId("user_5", "task_profile") } returns false
        every { walletRepository.findByUserIdAndType("user_5", WalletType.MAIN) } returns null

        When("claiming a valid, unclaimed task") {
            Then("it throws RewardsNoWalletException before touching the ledger") {
                try {
                    service.claim("user_5", "task_profile")
                    error("expected RewardsNoWalletException")
                } catch (e: RewardsNoWalletException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
