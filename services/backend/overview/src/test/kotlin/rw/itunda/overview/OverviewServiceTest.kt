package rw.itunda.overview

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import rw.itunda.core.domain.DebitCard
import rw.itunda.core.domain.Holding
import rw.itunda.core.domain.InsurancePolicy
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.RewardClaim
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.domain.LinkedAccount
import rw.itunda.core.domain.LinkedAccountStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.Vehicle
import rw.itunda.core.repository.DebitCardRepository
import rw.itunda.core.repository.HoldingRepository
import rw.itunda.core.repository.InsurancePolicyRepository
import rw.itunda.core.repository.LinkedAccountRepository
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.RewardClaimRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.AccountRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.VehicleRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class OverviewServiceTest : BehaviorSpec({

    Given("a user with real activity across every real itunda product") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val holdingRepository = mockk<HoldingRepository>()
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val debitCardRepository = mockk<DebitCardRepository>()
        val vehicleRepository = mockk<VehicleRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val service = OverviewService(
            accountRepository, savingsGoalRepository, loanAccountRepository, holdingRepository, insurancePolicyRepository, linkedAccountRepository,
            debitCardRepository, vehicleRepository, transactionRepository, rewardClaimRepository,
        )

        every { accountRepository.findByUserId("user_1") } returns listOf(
            Account(id = "w1", userId = "user_1", accountNumber = "ACC1", accountName = "Main", type = AccountType.MAIN, balance = BigDecimal("50000"), availableBalance = BigDecimal("50000")),
            Account(id = "pay1", userId = "user_1", accountNumber = "ACC2", accountName = "Pay", type = AccountType.PAY, balance = BigDecimal("3000"), availableBalance = BigDecimal("3000")),
        )
        every { savingsGoalRepository.findByUserId("user_1") } returns listOf(
            SavingsGoal(id = "g1", userId = "user_1", accountId = "w1", name = "Rainy day", targetAmount = BigDecimal("100000"), currentAmount = BigDecimal("20000"), monthlyContribution = BigDecimal("5000"), interestRate = 0.02, status = SavingsGoalStatus.active),
        )
        every { loanAccountRepository.findByUserId("user_1") } returns listOf(
            LoanAccount(id = "l1", userId = "user_1", accountId = "w1", offerId = "offer_1", principal = BigDecimal("30000"), outstanding = BigDecimal("15000"), interestRate = 0.1, status = LoanStatus.ACTIVE),
            LoanAccount(id = "l2", userId = "user_1", accountId = "w1", offerId = "offer_2", principal = BigDecimal("10000"), outstanding = BigDecimal.ZERO, interestRate = 0.1, status = LoanStatus.PAID),
        )
        every { holdingRepository.findByUserId("user_1") } returns listOf(
            Holding(id = "h1", userId = "user_1", accountId = "w1", stockId = "BOA", shares = BigDecimal("10"), avgPrice = BigDecimal("500")),
        )
        every { insurancePolicyRepository.findByUserId("user_1") } returns listOf(
            InsurancePolicy(id = "p1", userId = "user_1", planId = "ins_1", planName = "Health Shield", category = "health", status = "active", startDate = LocalDate.now(), endDate = LocalDate.now().plusYears(1), monthlyPremium = BigDecimal("15000"), nextPaymentDate = LocalDate.now(), policyNumber = "POL-1"),
        )
        every { linkedAccountRepository.findByUserIdOrderByLinkedAtDesc("user_1") } returns listOf(
            LinkedAccount(
                id = "linked_1", userId = "user_1", provider = "MTN MoMo", externalAccountNumberMasked = "•••• 1234",
                status = LinkedAccountStatus.LINKED, demoBalance = BigDecimal("450000"), demoBalanceCurrency = "RWF",
            ),
            LinkedAccount(id = "linked_2", userId = "user_1", provider = "Bank of Kigali", externalAccountNumberMasked = "•••• 5678", status = LinkedAccountStatus.UNLINKED),
        )
        every { debitCardRepository.findByUserId("user_1") } returns DebitCard(
            id = "card_1", userId = "user_1", last4 = "1234", dailyLimit = BigDecimal("500000"), monthlyLimit = BigDecimal("5000000"), design = "onyx_indigo",
        )
        every { vehicleRepository.findByUserIdOrderByCreatedAtDesc("user_1") } returns listOf(
            Vehicle(id = "v1", userId = "user_1", make = "Toyota", model = "RAV4", modelYear = 2019, purchasePrice = BigDecimal("8000000"), purchaseDate = LocalDate.now(), mileageKm = 40000),
        )
        every { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual("user_1", TransactionType.BILL, TransactionStatus.COMPLETED, Instant.EPOCH) } returns listOf(
            Transaction(
                id = "t1", referenceNumber = "REF1", senderId = "user_1", recipientId = "external", amount = BigDecimal("500"), fee = BigDecimal.ZERO,
                currency = "RWF", type = TransactionType.BILL, status = TransactionStatus.COMPLETED, description = "Bill payment - b8 (TIN-123456789)",
            ),
            // A real electricity payment (b1) must never be misclassified as tax just
            // because "b1" is a literal substring of "b10" -- the exact point of this row.
            Transaction(
                id = "t2", referenceNumber = "REF2", senderId = "user_1", recipientId = "external", amount = BigDecimal("35000"), fee = BigDecimal.ZERO,
                currency = "RWF", type = TransactionType.BILL, status = TransactionStatus.COMPLETED, description = "Bill payment - b1 (REG-12345)",
            ),
        )
        every { rewardClaimRepository.findByUserId("user_1") } returns listOf(
            RewardClaim(id = "rc1", userId = "user_1", taskId = "task_1", amount = BigDecimal("200"), claimedAt = Instant.now()),
        )

        When("computing the overview") {
            val result = service.getOverview("user_1")

            Then("net worth is accounts + savings + investment cost basis - active loan outstanding, not double-counted") {
                // 50000 (MAIN) + 3000 (PAY) + 20000 (savings) + 5000 (10 shares * 500 avgPrice) - 15000 (only the ACTIVE loan, not the PAID one) = 63000
                // Deliberately does NOT include the linked account's real 450000 demo balance below --
                // that's the actual point of the assertion two blocks down.
                result.netWorth shouldBe BigDecimal("63000")
            }
            Then("each product summary reflects real data, not just the net figure") {
                result.accounts.size shouldBe 2
                result.savings.totalSaved shouldBe BigDecimal("20000")
                result.savings.goalCount shouldBe 1
                result.loans.totalOutstanding shouldBe BigDecimal("15000")
                result.loans.activeCount shouldBe 1
                result.investments.totalCostBasis shouldBe BigDecimal("5000")
                result.investments.holdingCount shouldBe 1
                result.insurance.activePolicyCount shouldBe 1
                result.insurance.totalMonthlyPremium shouldBe BigDecimal("15000")
            }
            Then("only genuinely linked external accounts surface, unlinked ones don't") {
                result.linkedAccounts.size shouldBe 1
                result.linkedAccounts[0].id shouldBe "linked_1"
                result.linkedAccounts[0].provider shouldBe "MTN MoMo"
                result.linkedAccounts[0].maskedAccountNumber shouldBe "•••• 1234"
                result.linkedAccounts[0].status shouldBe "LINKED"
            }
            Then("a real demo balance is carried through, explicitly marked as demo, and never silently presented as real") {
                result.linkedAccounts[0].demoBalance shouldBe BigDecimal("450000")
                result.linkedAccounts[0].demoBalanceCurrency shouldBe "RWF"
                result.linkedAccounts[0].isDemoBalance shouldBe true
            }
            Then("the real issued card is surfaced") {
                result.cards.hasCard shouldBe true
                result.cards.last4 shouldBe "1234"
                result.cards.design shouldBe "onyx_indigo"
                result.cards.frozen shouldBe false
            }
            Then("the real vehicle is surfaced as count + raw purchase-price total, not a duplicated valuation") {
                result.vehicles.vehicleCount shouldBe 1
                result.vehicles.totalPurchasePrice shouldBe BigDecimal("8000000")
            }
            Then("only the real RRA tax-biller payment counts, not the real electricity payment sharing its 'b1' prefix") {
                result.tax.paymentCount shouldBe 1
                result.tax.totalPaid shouldBe BigDecimal("500")
            }
            Then("real reward claims and the real Pay Money balance are both surfaced") {
                result.points.rewardsTotal shouldBe BigDecimal("200")
                result.points.payMoneyBalance shouldBe BigDecimal("3000")
            }
        }
    }

    Given("a brand new user with nothing at all") {
        val accountRepository = mockk<AccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val holdingRepository = mockk<HoldingRepository>()
        val insurancePolicyRepository = mockk<InsurancePolicyRepository>()
        val linkedAccountRepository = mockk<LinkedAccountRepository>()
        val debitCardRepository = mockk<DebitCardRepository>()
        val vehicleRepository = mockk<VehicleRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val rewardClaimRepository = mockk<RewardClaimRepository>()
        val service = OverviewService(
            accountRepository, savingsGoalRepository, loanAccountRepository, holdingRepository, insurancePolicyRepository, linkedAccountRepository,
            debitCardRepository, vehicleRepository, transactionRepository, rewardClaimRepository,
        )

        every { accountRepository.findByUserId("user_2") } returns emptyList()
        every { savingsGoalRepository.findByUserId("user_2") } returns emptyList()
        every { loanAccountRepository.findByUserId("user_2") } returns emptyList()
        every { holdingRepository.findByUserId("user_2") } returns emptyList()
        every { insurancePolicyRepository.findByUserId("user_2") } returns emptyList()
        every { linkedAccountRepository.findByUserIdOrderByLinkedAtDesc("user_2") } returns emptyList()
        every { debitCardRepository.findByUserId("user_2") } returns null
        every { vehicleRepository.findByUserIdOrderByCreatedAtDesc("user_2") } returns emptyList()
        every { transactionRepository.findBySenderIdAndTypeAndStatusAndCreatedAtGreaterThanEqual("user_2", TransactionType.BILL, TransactionStatus.COMPLETED, Instant.EPOCH) } returns emptyList()
        every { rewardClaimRepository.findByUserId("user_2") } returns emptyList()

        When("computing the overview") {
            val result = service.getOverview("user_2")

            Then("everything is real zero, not an error or a null") {
                result.netWorth shouldBe BigDecimal.ZERO
                result.accounts shouldBe emptyList()
                result.linkedAccounts shouldBe emptyList()
            }
            Then("the 4 new categories are also real zero/empty states, never a fabricated placeholder") {
                result.cards.hasCard shouldBe false
                result.cards.last4 shouldBe null
                result.vehicles.vehicleCount shouldBe 0
                result.vehicles.totalPurchasePrice shouldBe BigDecimal.ZERO
                result.tax.paymentCount shouldBe 0
                result.tax.totalPaid shouldBe BigDecimal.ZERO
                result.points.rewardsTotal shouldBe BigDecimal.ZERO
                result.points.payMoneyBalance shouldBe BigDecimal.ZERO
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
