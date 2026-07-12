package rw.itunda.loans

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.creditscore.CreditScoreResult
import rw.itunda.core.creditscore.CreditScoreService
import rw.itunda.core.domain.LoanAccount
import rw.itunda.core.domain.LoanStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional

/**
 * First test coverage for loans. repayLoan's ownership check is documented in
 * LoansService.kt's own header as mirroring a real SECURITY.md fix in the Express
 * backend (without it, any authenticated user could pay down someone else's loan using
 * that loan's own wallet) -- the ownership test here exists to guard that specifically,
 * same spirit as AuthServiceTest guarding the login vulnerability.
 */
class LoansServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("100000"), availableBalance = BigDecimal("100000"),
    )

    Given("a user applying for and repaying a loan") {
        val walletRepository = mockk<WalletRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val service = LoansService(walletRepository, loanAccountRepository, ledgerService, creditScoreService)

        When("applying for a high amount (80% of the offer's max) with a real qualifying score") {
            every { loanAccountRepository.findByUserId("user_1") } returns emptyList()
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(700, emptyList(), Instant.now())
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { loanAccountRepository.save(any()) } answers { firstArg() }

            val result = service.applyForLoan("user_1", "loan_1", BigDecimal("400000"))

            Then("it disburses via the ledger and creates an active loan") {
                result["status"] shouldBe "approved"
                result["creditScore"] shouldBe 700
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
            }
        }

        When("applying for more than the offer's max amount") {
            Then("it throws LoanAmountInvalidException before touching the ledger") {
                try {
                    service.applyForLoan("user_1", "loan_1", BigDecimal("999999999"))
                    error("expected LoanAmountInvalidException")
                } catch (e: LoanAmountInvalidException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("applying for a loan offer that doesn't exist") {
            Then("it throws LoanOfferNotFoundException") {
                try {
                    service.applyForLoan("user_1", "loan_does_not_exist", BigDecimal("1000"))
                    error("expected LoanOfferNotFoundException")
                } catch (e: LoanOfferNotFoundException) {
                    // expected
                }
            }
        }

        When("a low-value, low-amount applicant whose score is below the minimum") {
            every { loanAccountRepository.findByUserId("user_low") } returns emptyList()
            every { creditScoreService.computeScore("user_low") } returns CreditScoreResult(300, emptyList(), Instant.now())

            Then("it throws LoanApplicationDeclinedException before touching the ledger, even for a small amount") {
                try {
                    service.applyForLoan("user_low", "loan_1", BigDecimal("10000"))
                    error("expected LoanApplicationDeclinedException")
                } catch (e: LoanApplicationDeclinedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a qualifying-for-small-amounts applicant requests a high amount they don't qualify for") {
            every { loanAccountRepository.findByUserId("user_mid") } returns emptyList()
            every { creditScoreService.computeScore("user_mid") } returns CreditScoreResult(450, emptyList(), Instant.now())

            Then("it throws LoanApplicationDeclinedException for the high-amount tier specifically") {
                try {
                    // 400000 is 80% of loan_1's 500000 max -- above the 50% high-amount threshold
                    service.applyForLoan("user_mid", "loan_1", BigDecimal("400000"))
                    error("expected LoanApplicationDeclinedException")
                } catch (e: LoanApplicationDeclinedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("that same mid-score applicant requests a small amount instead") {
            every { loanAccountRepository.findByUserId("user_mid2") } returns emptyList()
            every { creditScoreService.computeScore("user_mid2") } returns CreditScoreResult(450, emptyList(), Instant.now())
            every { walletRepository.findByUserIdAndType("user_mid2", WalletType.MAIN) } returns wallet("wallet_mid2", "user_mid2")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_mid", emptyList())
            every { loanAccountRepository.save(any()) } answers { firstArg() }

            Then("450 clears the base minimum, so a low-tier amount is approved") {
                val result = service.applyForLoan("user_mid2", "loan_1", BigDecimal("50000"))
                result["status"] shouldBe "approved"
            }
        }

        When("an applicant already has 2 concurrent active loans") {
            val existingActive = (1..2).map {
                LoanAccount(id = "loan_existing_$it", userId = "user_maxed", walletId = "w1", offerId = "loan_1", principal = BigDecimal("10000"), outstanding = BigDecimal("5000"), interestRate = 5.0, status = LoanStatus.ACTIVE)
            }
            every { loanAccountRepository.findByUserId("user_maxed") } returns existingActive

            Then("it throws LoanApplicationDeclinedException before ever checking credit score") {
                try {
                    service.applyForLoan("user_maxed", "loan_1", BigDecimal("10000"))
                    error("expected LoanApplicationDeclinedException")
                } catch (e: LoanApplicationDeclinedException) {
                    verify(exactly = 0) { creditScoreService.computeScore(any()) }
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("repaying part of an owned, active loan") {
            val loan = LoanAccount(
                id = "loan_x", userId = "user_1", walletId = "wallet_1", offerId = "loan_1",
                principal = BigDecimal("100000"), outstanding = BigDecimal("60000"), interestRate = 5.0,
                status = LoanStatus.ACTIVE, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_x") } returns Optional.of(loan)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_2", emptyList())
            every { walletRepository.findById("wallet_1") } returns Optional.of(wallet("wallet_1", "user_1"))

            val result = service.repayLoan("user_1", "loan_x", BigDecimal("20000"))

            Then("it reduces the outstanding balance and stays active") {
                loan.outstanding shouldBe BigDecimal("40000")
                loan.status shouldBe LoanStatus.ACTIVE
                result["remaining"] shouldBe BigDecimal("40000")
            }
        }

        When("repaying the exact outstanding balance") {
            val loan = LoanAccount(
                id = "loan_y", userId = "user_1", walletId = "wallet_1", offerId = "loan_1",
                principal = BigDecimal("100000"), outstanding = BigDecimal("15000"), interestRate = 5.0,
                status = LoanStatus.ACTIVE, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_y") } returns Optional.of(loan)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_3", emptyList())
            every { walletRepository.findById("wallet_1") } returns Optional.of(wallet("wallet_1", "user_1"))

            service.repayLoan("user_1", "loan_y", BigDecimal("15000"))

            Then("the loan is marked PAID") {
                loan.status shouldBe LoanStatus.PAID
            }
        }

        When("overpaying a loan") {
            val loan = LoanAccount(
                id = "loan_z", userId = "user_1", walletId = "wallet_1", offerId = "loan_1",
                principal = BigDecimal("100000"), outstanding = BigDecimal("5000"), interestRate = 5.0,
                status = LoanStatus.ACTIVE, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_z") } returns Optional.of(loan)
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_4", emptyList())
            every { walletRepository.findById("wallet_1") } returns Optional.of(wallet("wallet_1", "user_1"))

            val result = service.repayLoan("user_1", "loan_z", BigDecimal("50000"))

            Then("it only debits the actual outstanding amount, not the full requested amount") {
                result["transaction"].let { it as Map<*, *> }["amount"] shouldBe BigDecimal("5000")
                loan.status shouldBe LoanStatus.PAID
            }
        }

        When("a different user tries to repay someone else's loan") {
            val loan = LoanAccount(
                id = "loan_w", userId = "owner_1", walletId = "wallet_owner", offerId = "loan_1",
                principal = BigDecimal("100000"), outstanding = BigDecimal("50000"), interestRate = 5.0,
                status = LoanStatus.ACTIVE, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_w") } returns Optional.of(loan)

            Then("it throws LoanNotOwnedException and never touches the ledger -- the exact bug this file's own SECURITY.md-mirrored fix prevents") {
                try {
                    service.repayLoan("attacker", "loan_w", BigDecimal("50000"))
                    error("expected LoanNotOwnedException")
                } catch (e: LoanNotOwnedException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("repaying an already-fully-paid loan") {
            val loan = LoanAccount(
                id = "loan_v", userId = "user_1", walletId = "wallet_1", offerId = "loan_1",
                principal = BigDecimal("100000"), outstanding = BigDecimal.ZERO, interestRate = 5.0,
                status = LoanStatus.PAID, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_v") } returns Optional.of(loan)

            Then("it throws LoanAlreadyPaidException rather than posting a zero-value transaction") {
                try {
                    service.repayLoan("user_1", "loan_v", BigDecimal("1000"))
                    error("expected LoanAlreadyPaidException")
                } catch (e: LoanAlreadyPaidException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
