package rw.itunda.loans

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.VupLoan
import rw.itunda.core.domain.VupLoanPurpose
import rw.itunda.core.domain.VupLoanStatus
import rw.itunda.core.domain.Wallet
import rw.itunda.core.domain.WalletType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.VupLoanRepository
import rw.itunda.core.repository.WalletRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * First test coverage for real Rwanda VUP (Vision 2020 Umurenge Programme) Financial
 * Services micro-loan -- see VupLoanService's own doc comment for the full sourced
 * account. The ledger-account-correctness tests specifically guard against the same
 * real solvency-bug class this session caught and fixed in
 * SaccoService.declareDividend (a payout accidentally funded from a shared/pooled
 * wallet instead of itunda's own capital) -- this feature was built to never have that
 * bug in the first place. The repay-clamp test guards against the same overshoot-clamp
 * regression class just found in InsuranceService.contributeToFund: it asserts the
 * actual ledger leg amount, not just the resulting field.
 */
class VupLoanServiceTest : BehaviorSpec({

    fun wallet(id: String, userId: String) = Wallet(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test wallet",
        type = WalletType.MAIN, balance = BigDecimal("1000000"), availableBalance = BigDecimal("1000000"),
    )

    fun newService(
        vupLoanRepository: VupLoanRepository = mockk(),
        walletRepository: WalletRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk(relaxed = true),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ) = VupLoanService(vupLoanRepository, walletRepository, ledgerService, rateLimiter, notificationRepository, pushNotificationService)

    Given("a user applying for a VUP loan") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val walletRepository = mockk<WalletRepository>()
        val service = newService(vupLoanRepository = vupLoanRepository, walletRepository = walletRepository)
        val savedSlot = slot<VupLoan>()
        every { vupLoanRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { vupLoanRepository.findByUserIdAndStatusIn("user_1", any()) } returns emptyList()
        val applicantWallet = wallet("wallet_1", "user_1")
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns applicantWallet
        every { walletRepository.findByIdForUpdate("wallet_1") } returns Optional.of(applicantWallet)

        When("declaring a real eligible Ubudehe category (2) and a valid amount") {
            val result = service.applyForLoan("user_1", 2, VupLoanPurpose.FARMING, BigDecimal("100000"))

            Then("a real REQUESTED loan is saved") {
                result.status shouldBe VupLoanStatus.REQUESTED
                result.declaredUbudeheCategory shouldBe 2
                savedSlot.captured.principalAmount shouldBe BigDecimal("100000")
                savedSlot.captured.outstandingPrincipal shouldBe BigDecimal("100000")
            }
        }

        When("declaring Ubudehe category 4") {
            Then("the eligibility gate real-400s before touching the repository") {
                shouldThrow<IneligibleUbudeheCategoryException> {
                    service.applyForLoan("user_1", 4, VupLoanPurpose.FARMING, BigDecimal("100000"))
                }
            }
        }

        When("requesting an amount over the real ceiling") {
            Then("the amount guard real-400s before touching the repository") {
                shouldThrow<InvalidVupLoanAmountException> {
                    service.applyForLoan("user_1", 2, VupLoanPurpose.FARMING, BigDecimal("999999999"))
                }
            }
        }
    }

    Given("a user who already has an active VUP loan") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val walletRepository = mockk<WalletRepository>()
        val service = newService(vupLoanRepository = vupLoanRepository, walletRepository = walletRepository)
        val existing = VupLoan(
            id = "vuploan_existing", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("50000"), outstandingPrincipal = BigDecimal("50000"), status = VupLoanStatus.DISBURSED,
        )
        every { vupLoanRepository.findByUserIdAndStatusIn("user_1", any()) } returns listOf(existing)
        val applicantWallet = wallet("wallet_1", "user_1")
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns applicantWallet
        every { walletRepository.findByIdForUpdate("wallet_1") } returns Optional.of(applicantWallet)

        When("applying for a second loan") {
            Then("the one-active-loan guard fires, after locking the caller's own wallet row first (closing the double-apply race)") {
                shouldThrow<VupLoanAlreadyActiveException> {
                    service.applyForLoan("user_1", 2, VupLoanPurpose.BUSINESS, BigDecimal("50000"))
                }
                verify(exactly = 1) { walletRepository.findByIdForUpdate("wallet_1") }
            }
        }
    }

    Given("a real REQUESTED VUP loan being disbursed") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(vupLoanRepository = vupLoanRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val loan = VupLoan(
            id = "vuploan_1", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("100000"),
        )
        every { vupLoanRepository.findById("vuploan_1") } returns Optional.of(loan)
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { vupLoanRepository.save(any()) } answers { firstArg() }

        When("itunda disburses it") {
            val result = service.disburse("user_1", "vuploan_1")

            Then("real bug class this feature was built to avoid: disbursement must come from itunda's own loan_payable receivable, never SaccoService's pooled wallet") {
                result.status shouldBe VupLoanStatus.DISBURSED
                (result.disbursedAt != null) shouldBe true
                result.dueDate shouldBe LocalDate.now().plusMonths(12)
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.size == 2 &&
                            legs.any { it.accountId == "wallet_1" && it.direction == LedgerDirection.CREDIT && it.amount == BigDecimal("100000") } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.DEBIT && it.amount == BigDecimal("100000") }
                    })
                }
            }
        }

        When("someone else tries to disburse it (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<VupLoanNotFoundException> { service.disburse("attacker", "vuploan_1") }
            }
        }
    }

    Given("attempting to disburse an already-disbursed VUP loan") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val service = newService(vupLoanRepository = vupLoanRepository)
        val loan = VupLoan(
            id = "vuploan_2", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("100000"), status = VupLoanStatus.DISBURSED,
        )
        every { vupLoanRepository.findById("vuploan_2") } returns Optional.of(loan)

        When("disbursing it again") {
            Then("the status guard fires") {
                shouldThrow<VupLoanNotRequestedException> { service.disburse("user_1", "vuploan_2") }
            }
        }
    }

    // Separate Given block, not a sibling When -- Kotest's BehaviorSpec shares the same
    // mutable `loan` instance across sibling Whens under one Given, and a repayment in
    // one When would leak its mutation into the others.
    Given("a real DISBURSED VUP loan being overpaid") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(vupLoanRepository = vupLoanRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val loan = VupLoan(
            id = "vuploan_3", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("30000"), status = VupLoanStatus.DISBURSED,
            dueDate = LocalDate.now().plusMonths(6),
        )
        every { vupLoanRepository.findById("vuploan_3") } returns Optional.of(loan)
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_repay", emptyList())
        every { vupLoanRepository.save(any()) } answers { firstArg() }

        When("repaying 100,000 against a real 30,000 outstanding balance") {
            val result = service.repay("user_1", "vuploan_3", BigDecimal("100000"))

            Then("the ledger only ever sees the real clamped 30,000, not the raw overshooting amount") {
                result.status shouldBe VupLoanStatus.REPAID
                result.outstandingPrincipal shouldBe BigDecimal.ZERO
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.all { it.amount == BigDecimal("30000") } &&
                            legs.any { it.accountId == "wallet_1" && it.direction == LedgerDirection.DEBIT } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.CREDIT }
                    })
                }
            }
        }
    }

    Given("a real DISBURSED VUP loan being partially repaid") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val walletRepository = mockk<WalletRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(vupLoanRepository = vupLoanRepository, walletRepository = walletRepository, ledgerService = ledgerService)

        val loan = VupLoan(
            id = "vuploan_4", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("100000"), status = VupLoanStatus.DISBURSED,
            dueDate = LocalDate.now().plusMonths(6),
        )
        every { vupLoanRepository.findById("vuploan_4") } returns Optional.of(loan)
        every { walletRepository.findByUserIdAndType("user_1", WalletType.MAIN) } returns wallet("wallet_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_repay", emptyList())
        every { vupLoanRepository.save(any()) } answers { firstArg() }

        When("repaying 40,000 of the real 100,000 principal") {
            val result = service.repay("user_1", "vuploan_4", BigDecimal("40000"))

            Then("the loan stays DISBURSED with a real reduced outstanding balance") {
                result.status shouldBe VupLoanStatus.DISBURSED
                result.outstandingPrincipal shouldBe BigDecimal("60000")
            }
        }

        When("someone else tries to repay it (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<VupLoanNotFoundException> { service.repay("attacker", "vuploan_4", BigDecimal("10000")) }
            }
        }
    }
})
