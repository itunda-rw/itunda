package rw.itunda.overview

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.InsurancePolicy
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.LocalDate

class OverviewServiceTest : BehaviorSpec({

    Given("a user with real activity across every real itunda product") {
        val walletRepository = mockk<WalletRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val holdingRepository = mockk<HoldingRepository>()
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val service = OverviewService(walletRepository, savingsGoalRepository, loanAccountRepository, holdingRepository, insurancePolicyRepository)

        every { walletRepository.findByUserId("user_1") } returns listOf(
            Wallet(id = "w1", userId = "user_1", accountNumber = "ACC1", accountName = "Main", type = WalletType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000")),
        )
        every { savingsGoalRepository.findByUserId("user_1") } returns listOf(
            SavingsGoal(id = "g1", userId = "user_1", walletId = "w1", name = "Rainy day", targetAmount = BigDecimal("100000"), currentAmount = BigDecimal("20000"), monthlyContribution = BigDecimal("5000"), interestRate = 0.02, status = SavingsGoalStatus.active),
        )
        every { loanAccountRepository.findByUserId("user_1") } returns listOf(
            LoanAccount(id = "l1", userId = "user_1", walletId = "w1", offerId = "offer_1", principal = BigDecimal("30000"), outstanding = BigDecimal("15000"), interestRate = 0.1, status = LoanStatus.ACTIVE),
            LoanAccount(id = "l2", userId = "user_1", walletId = "w1", offerId = "offer_2", principal = BigDecimal("10000"), outstanding = BigDecimal.ZERO, interestRate = 0.1, status = LoanStatus.PAID),
        )
        every { holdingRepository.findByUserId("user_1") } returns listOf(
            Holding(id = "h1", userId = "user_1", walletId = "w1", stockId = "BOA", shares = BigDecimal("10"), avgPrice = BigDecimal("500")),
        )
        every { insurancePolicyRepository.findByUserId("user_1") } returns listOf(
            InsurancePolicy(id = "p1", userId = "user_1", planId = "ins_1", planName = "Health Shield", category = "health", status = "active", startDate = LocalDate.now(), endDate = LocalDate.now().plusYears(1), monthlyPremium = BigDecimal("15000"), nextPaymentDate = LocalDate.now(), policyNumber = "POL-1"),
        )

        When("computing the overview") {
            val result = service.getOverview("user_1")

            Then("net worth is wallets + savings + investment cost basis - active loan outstanding, not double-counted") {
                // 50000 (wallet) + 20000 (savings) + 5000 (10 shares * 500 avgPrice) - 15000 (only the ACTIVE loan, not the PAID one) = 60000
                result.netWorth shouldBe BigDecimal("60000")
            }
            Then("each product summary reflects real data, not just the net figure") {
                result.accounts.size shouldBe 1
                result.savings.totalSaved shouldBe BigDecimal("20000")
                result.savings.goalCount shouldBe 1
                result.loans.totalOutstanding shouldBe BigDecimal("15000")
                result.loans.activeCount shouldBe 1
                result.investments.totalCostBasis shouldBe BigDecimal("5000")
                result.investments.holdingCount shouldBe 1
                result.insurance.activePolicyCount shouldBe 1
                result.insurance.totalMonthlyPremium shouldBe BigDecimal("15000")
            }
        }
    }

    Given("a brand new user with nothing at all") {
        val walletRepository = mockk<WalletRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val holdingRepository = mockk<HoldingRepository>()
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val service = OverviewService(walletRepository, savingsGoalRepository, loanAccountRepository, holdingRepository, insurancePolicyRepository)

        every { walletRepository.findByUserId("user_2") } returns emptyList()
        every { savingsGoalRepository.findByUserId("user_2") } returns emptyList()
        every { loanAccountRepository.findByUserId("user_2") } returns emptyList()
        every { holdingRepository.findByUserId("user_2") } returns emptyList()
        every { insurancePolicyRepository.findByUserId("user_2") } returns emptyList()

        When("computing the overview") {
            val result = service.getOverview("user_2")

            Then("everything is real zero, not an error or a null") {
                result.netWorth shouldBe BigDecimal.ZERO
                result.accounts shouldBe emptyList()
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
