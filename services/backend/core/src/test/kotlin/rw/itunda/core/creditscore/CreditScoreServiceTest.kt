package rw.itunda.core.creditscore

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.SavingsGoal
import rw.itunda.core.domain.SavingsGoalStatus
import rw.itunda.core.domain.Transaction
import rw.itunda.core.domain.TransactionStatus
import rw.itunda.core.domain.TransactionType
import rw.itunda.core.domain.User
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.SavingsGoalRepository
import rw.itunda.core.repository.TransactionRepository
import rw.itunda.core.repository.UserRepository
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

class CreditScoreServiceTest : BehaviorSpec({

    fun transaction(status: TransactionStatus) = Transaction(
        id = "txn_${(0..999999).random()}", referenceNumber = "REF-${(0..999999).random()}",
        senderId = "user_1", recipientId = "user_2", amount = BigDecimal("1000"), fee = BigDecimal.ZERO,
        currency = "RWF", type = TransactionType.TRANSFER, status = status, description = "test",
    )

    Given("a brand new user with no history at all") {
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = CreditScoreService(userRepository, transactionRepository, loanAccountRepository, savingsGoalRepository)

        val user = User(id = "user_1", phoneNumber = "0788000001", firstName = "New", lastName = "User", passwordHash = "hash", kycVerified = false, createdAt = Instant.now())
        every { userRepository.findById("user_1") } returns Optional.of(user)
        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_1", "user_1") } returns emptyList()
        every { loanAccountRepository.findByUserId("user_1") } returns emptyList()
        every { savingsGoalRepository.findByUserId("user_1") } returns emptyList()
        every { userRepository.save(any()) } answers { firstArg() }

        When("computing the score") {
            val result = service.computeScore("user_1")

            Then("it's exactly the base score -- no unearned points") {
                result.score shouldBe 300
                result.factors.size shouldBe 1
                result.factors[0].name shouldBe "Base score"
            }
            Then("it writes the computed score back onto the user for profile display") {
                user.creditScore shouldBe 300
                verify(exactly = 1) { userRepository.save(user) }
            }
        }
    }

    Given("a well-established, fully-verified user with a repaid loan and real savings") {
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = CreditScoreService(userRepository, transactionRepository, loanAccountRepository, savingsGoalRepository)

        val oldCreatedAt = Instant.now().minus(1000, ChronoUnit.DAYS)
        val user = User(id = "user_2", phoneNumber = "0788000002", firstName = "Established", lastName = "User", passwordHash = "hash", kycVerified = true, createdAt = oldCreatedAt)
        every { userRepository.findById("user_2") } returns Optional.of(user)
        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_2", "user_2") } returns
            (1..80).map { transaction(TransactionStatus.COMPLETED) }
        every { loanAccountRepository.findByUserId("user_2") } returns listOf(
            LoanAccount(id = "loan_1", userId = "user_2", walletId = "w1", offerId = "offer_1", principal = BigDecimal("100000"), outstanding = BigDecimal.ZERO, interestRate = 0.1, status = LoanStatus.PAID),
        )
        every { savingsGoalRepository.findByUserId("user_2") } returns listOf(
            SavingsGoal(id = "goal_1", userId = "user_2", walletId = "w1", name = "Rainy day", targetAmount = BigDecimal("50000"), currentAmount = BigDecimal("10000"), monthlyContribution = BigDecimal("5000"), interestRate = 0.02, status = SavingsGoalStatus.active),
        )
        every { userRepository.save(any()) } answers { firstArg() }

        When("computing the score") {
            val result = service.computeScore("user_2")

            Then("every real signal contributes, and the total is capped at 850") {
                // 300 base + 100 KYC + 100 age (capped) + 100 transactions (capped at 50 txns) + 100 paid loan + 50 savings = 750
                result.score shouldBe 750
                result.factors.map { it.name } shouldBe listOf(
                    "Base score", "Identity verified", "Account history", "Transaction activity", "Loan repayment history", "Savings activity",
                )
            }
        }
    }

    Given("a user with an active, not-yet-repaid loan") {
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = CreditScoreService(userRepository, transactionRepository, loanAccountRepository, savingsGoalRepository)

        val user = User(id = "user_3", phoneNumber = "0788000003", firstName = "Borrower", lastName = "User", passwordHash = "hash", createdAt = Instant.now())
        every { userRepository.findById("user_3") } returns Optional.of(user)
        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_3", "user_3") } returns emptyList()
        every { loanAccountRepository.findByUserId("user_3") } returns listOf(
            LoanAccount(id = "loan_2", userId = "user_3", walletId = "w1", offerId = "offer_1", principal = BigDecimal("50000"), outstanding = BigDecimal("30000"), interestRate = 0.1, status = LoanStatus.ACTIVE),
        )
        every { savingsGoalRepository.findByUserId("user_3") } returns emptyList()
        every { userRepository.save(any()) } answers { firstArg() }

        When("computing the score") {
            val result = service.computeScore("user_3")

            Then("an unproven active loan earns far less than a real repayment record") {
                val loanFactor = result.factors.first { it.name == "Active credit usage" }
                loanFactor.points shouldBe 20
            }
        }
    }

    Given("a brand new user checking real improvement suggestions") {
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = CreditScoreService(userRepository, transactionRepository, loanAccountRepository, savingsGoalRepository)

        val user = User(id = "user_4", phoneNumber = "0788000004", firstName = "New", lastName = "User", passwordHash = "hash", kycVerified = false, createdAt = Instant.now())
        every { userRepository.findById("user_4") } returns Optional.of(user)
        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_4", "user_4") } returns emptyList()
        every { loanAccountRepository.findByUserId("user_4") } returns emptyList()
        every { savingsGoalRepository.findByUserId("user_4") } returns emptyList()

        When("getting suggestions") {
            val suggestions = service.getImprovementSuggestions("user_4")

            Then("it real-suggests KYC, more transactions, and starting savings -- but NOT paying off a loan, since they have none") {
                suggestions.map { it.action } shouldBe listOf(
                    "Verify your identity", "Complete more real transactions", "Start a savings goal",
                )
                suggestions.first { it.action == "Verify your identity" }.pointsGain shouldBe 100
                suggestions.first { it.action == "Complete more real transactions" }.pointsGain shouldBe 100
                suggestions.first { it.action == "Start a savings goal" }.pointsGain shouldBe 50
            }
            Then("it never mutates real state -- unlike computeScore, this is read-only") {
                verify(exactly = 0) { userRepository.save(any()) }
            }
        }
    }

    Given("a user with an active, unpaid loan checking real improvement suggestions") {
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = CreditScoreService(userRepository, transactionRepository, loanAccountRepository, savingsGoalRepository)

        val user = User(id = "user_5", phoneNumber = "0788000005", firstName = "Borrower", lastName = "User", passwordHash = "hash", kycVerified = true, createdAt = Instant.now())
        every { userRepository.findById("user_5") } returns Optional.of(user)
        every { transactionRepository.findBySenderIdOrRecipientIdOrderByCreatedAtDesc("user_5", "user_5") } returns (1..50).map { transaction(TransactionStatus.COMPLETED) }
        every { loanAccountRepository.findByUserId("user_5") } returns listOf(
            LoanAccount(id = "loan_3", userId = "user_5", walletId = "w1", offerId = "offer_1", principal = BigDecimal("50000"), outstanding = BigDecimal("30000"), interestRate = 0.1, status = LoanStatus.ACTIVE),
        )
        every { savingsGoalRepository.findByUserId("user_5") } returns listOf(
            SavingsGoal(id = "goal_2", userId = "user_5", walletId = "w1", name = "Fund", targetAmount = BigDecimal("50000"), currentAmount = BigDecimal("10000"), monthlyContribution = BigDecimal("5000"), interestRate = 0.02, status = SavingsGoalStatus.active),
        )

        When("getting suggestions") {
            val suggestions = service.getImprovementSuggestions("user_5")

            Then("it real-suggests paying off the loan for the exact real 80-point delta (100 paid - 20 active), and nothing else -- KYC/transactions/savings are all already maxed or satisfied") {
                suggestions.map { it.action } shouldBe listOf("Pay off your active loan in full")
                suggestions[0].pointsGain shouldBe 80
            }
        }
    }

    Given("a user id that doesn't exist") {
        val userRepository = mockk<UserRepository>()
        val transactionRepository = mockk<TransactionRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val savingsGoalRepository = mockk<SavingsGoalRepository>()
        val service = CreditScoreService(userRepository, transactionRepository, loanAccountRepository, savingsGoalRepository)

        every { userRepository.findById("ghost") } returns Optional.empty()

        When("computing the score") {
            Then("it throws CreditScoreUserNotFoundException rather than a null-pointer surprise") {
                try {
                    service.computeScore("ghost")
                    error("expected CreditScoreUserNotFoundException")
                } catch (e: CreditScoreUserNotFoundException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
