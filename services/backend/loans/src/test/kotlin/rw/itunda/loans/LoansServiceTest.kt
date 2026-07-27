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
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.LoanAccountRepository
import rw.itunda.core.repository.NotificationRepository
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
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = LoansService(walletRepository, loanAccountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService)

        When("applying for a high amount (80% of the offer's max) with a real qualifying score") {
            every { loanAccountRepository.findByUserId("user_1") } returns emptyList()
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(700, emptyList(), Instant.now())
            every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
            every { loanAccountRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val result = service.applyForLoan("user_1", "loan_1", BigDecimal("400000"))

            Then("it disburses via the ledger and creates an active loan") {
                result["status"] shouldBe "approved"
                result["creditScore"] shouldBe 700
                verify(exactly = 1) { ledgerService.postLedgerTransaction(any(), any()) }
            }

            Then("it real-alerts the account owner, a Toss 자산 보호 알림-style security notification") {
                verify(exactly = 1) {
                    notificationRepository.save(match { it.userId == "user_1" && it.type == "NEW_LOAN_DISBURSED" })
                }
            }

            Then("the account owner also gets a real push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "New loan opened in your name", any(), any()) }
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
            every { notificationRepository.save(any()) } answers { firstArg() }

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

        When("refinancing a real active loan_1 (5.0%) for a high-scoring borrower") {
            // outstanding is small relative to loan_3's real 5,000,000 max, so the
            // high-amount gate doesn't even engage -- a real qualifying score of 700
            // clears the base minimum either way.
            val loan = LoanAccount(
                id = "loan_refi_1", userId = "user_1", walletId = "wallet_1", offerId = "loan_1",
                principal = BigDecimal("400000"), outstanding = BigDecimal("300000"), interestRate = 5.0,
                status = LoanStatus.ACTIVE, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_refi_1") } returns Optional.of(loan)
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(700, emptyList(), Instant.now())
            every { walletRepository.findById("wallet_1") } returns Optional.of(wallet("wallet_1", "user_1"))
            every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_refi", emptyList())
            val newLoanSlot = mutableListOf<LoanAccount>()
            every { loanAccountRepository.save(capture(newLoanSlot)) } answers { firstArg() }

            val result = service.refinanceLoan("user_1", "loan_refi_1")

            Then("it real-picks the single lowest-rate itunda offer it qualifies for -- loan_3's real 2.8%, not just any lower rate") {
                result["newInterestRate"] shouldBe 2.8
                result["oldInterestRate"] shouldBe 5.0
                result["amount"] shouldBe BigDecimal("300000")
            }
            Then("it posts two real, separate ledger transactions -- new disbursement, then old payoff -- and retires the old loan") {
                verify(exactly = 2) { ledgerService.postLedgerTransaction(any(), any()) }
                loan.status shouldBe LoanStatus.PAID
                loan.outstanding shouldBe BigDecimal.ZERO
                val newLoan = newLoanSlot.first { it.id != loan.id }
                newLoan.status shouldBe LoanStatus.ACTIVE
                newLoan.principal shouldBe BigDecimal("300000")
                newLoan.interestRate shouldBe 2.8
            }
        }

        When("refinancing when no itunda offer beats the real current rate") {
            // Already at loan_3's real 2.8% -- the best rate in the whole real itunda catalog.
            val loan = LoanAccount(
                id = "loan_refi_2", userId = "user_1", walletId = "wallet_1", offerId = "loan_3",
                principal = BigDecimal("400000"), outstanding = BigDecimal("300000"), interestRate = 2.8,
                status = LoanStatus.ACTIVE, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_refi_2") } returns Optional.of(loan)
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(700, emptyList(), Instant.now())

            Then("it throws NoBetterRateAvailableException before touching the ledger") {
                try {
                    service.refinanceLoan("user_1", "loan_refi_2")
                    error("expected NoBetterRateAvailableException")
                } catch (e: NoBetterRateAvailableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("refinancing with a real score too low to qualify for anything") {
            val loan = LoanAccount(
                id = "loan_refi_3", userId = "user_1", walletId = "wallet_1", offerId = "loan_1",
                principal = BigDecimal("400000"), outstanding = BigDecimal("300000"), interestRate = 5.0,
                status = LoanStatus.ACTIVE, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_refi_3") } returns Optional.of(loan)
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(300, emptyList(), Instant.now())

            Then("it throws NoBetterRateAvailableException, matching applyForLoan's own real underwriting gate") {
                try {
                    service.refinanceLoan("user_1", "loan_refi_3")
                    error("expected NoBetterRateAvailableException")
                } catch (e: NoBetterRateAvailableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("refinancing a real outstanding balance too large for any lower-rate offer's cap") {
            // loan_2 (3.5%) caps at 2,000,000 and loan_3 (2.8%) caps at 5,000,000 -- an
            // outstanding balance above both means no itunda offer can even cover the
            // payoff, regardless of score.
            val loan = LoanAccount(
                id = "loan_refi_4", userId = "user_1", walletId = "wallet_1", offerId = "loan_1",
                principal = BigDecimal("6000000"), outstanding = BigDecimal("5500000"), interestRate = 5.0,
                status = LoanStatus.ACTIVE, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_refi_4") } returns Optional.of(loan)
            every { creditScoreService.computeScore("user_1") } returns CreditScoreResult(700, emptyList(), Instant.now())

            Then("it throws NoBetterRateAvailableException rather than refinancing into an offer that can't cover the real balance") {
                try {
                    service.refinanceLoan("user_1", "loan_refi_4")
                    error("expected NoBetterRateAvailableException")
                } catch (e: NoBetterRateAvailableException) {
                    verify(exactly = 0) { ledgerService.postLedgerTransaction(any(), any()) }
                }
            }
        }

        When("a different user tries to refinance someone else's loan") {
            val loan = LoanAccount(
                id = "loan_refi_5", userId = "owner_1", walletId = "wallet_owner", offerId = "loan_1",
                principal = BigDecimal("400000"), outstanding = BigDecimal("300000"), interestRate = 5.0,
                status = LoanStatus.ACTIVE, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_refi_5") } returns Optional.of(loan)

            Then("it throws LoanNotOwnedException before ever checking credit score") {
                try {
                    service.refinanceLoan("attacker", "loan_refi_5")
                    error("expected LoanNotOwnedException")
                } catch (e: LoanNotOwnedException) {
                    verify(exactly = 0) { creditScoreService.computeScore(any()) }
                }
            }
        }

        When("trying to refinance an already-paid loan") {
            val loan = LoanAccount(
                id = "loan_refi_6", userId = "user_1", walletId = "wallet_1", offerId = "loan_1",
                principal = BigDecimal("400000"), outstanding = BigDecimal.ZERO, interestRate = 5.0,
                status = LoanStatus.PAID, disbursedAt = Instant.now(),
            )
            every { loanAccountRepository.findById("loan_refi_6") } returns Optional.of(loan)

            Then("it throws LoanAlreadyPaidException -- there's nothing left to refinance") {
                try {
                    service.refinanceLoan("user_1", "loan_refi_6")
                    error("expected LoanAlreadyPaidException")
                } catch (e: LoanAlreadyPaidException) {
                    verify(exactly = 0) { creditScoreService.computeScore(any()) }
                }
            }
        }
    }

    Given("the multi-lender marketplace") {
        val walletRepository = mockk<WalletRepository>()
        val loanAccountRepository = mockk<LoanAccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val creditScoreService = mockk<CreditScoreService>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = LoansService(walletRepository, loanAccountRepository, ledgerService, creditScoreService, notificationRepository, pushNotificationService)

        When("listing all offers with no lender filter") {
            val offers = service.getOffers()

            Then("it returns offers from more than one real lender, not just itunda's own book") {
                offers.map { it.lenderId }.toSet().size shouldBe 4
                offers.any { it.lenderId == "lender_itunda" } shouldBe true
                offers.any { it.lenderId == "lender_bk" } shouldBe true
            }
        }

        When("filtering offers by a specific lender") {
            val offers = service.getOffers("lender_bk")

            Then("it returns only that lender's offers") {
                offers.size shouldBe 1
                offers.first().lenderName shouldBe "Bank of Kigali"
            }
        }

        When("listing lenders") {
            val lenders = service.getLenders()

            Then("it returns the real, named lender catalog") {
                lenders.map { it.name } shouldBe listOf("Itunda", "Bank of Kigali", "Equity Bank Rwanda", "Urwego Bank")
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
