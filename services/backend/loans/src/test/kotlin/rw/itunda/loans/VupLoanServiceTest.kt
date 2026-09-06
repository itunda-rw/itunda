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
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.VupLoanRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional

/**
 * First test coverage for real Rwanda VUP (Vision 2020 Umurenge Programme) Financial
 * Services micro-loan -- see VupLoanService's own doc comment for the full sourced
 * account. The ledger-account-correctness tests specifically guard against the same
 * real solvency-bug class this session caught and fixed in
 * SaccoService.declareDividend (a payout accidentally funded from a shared/pooled
 * account instead of itunda's own capital) -- this feature was built to never have that
 * bug in the first place. The repay-clamp test guards against the same overshoot-clamp
 * regression class just found in InsuranceService.contributeToFund: it asserts the
 * actual ledger leg amount, not just the resulting field.
 */
class VupLoanServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("1000000"), availableBalance = BigDecimal("1000000"),
    )

    fun newService(
        vupLoanRepository: VupLoanRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk(relaxed = true),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ) = VupLoanService(vupLoanRepository, accountRepository, ledgerService, rateLimiter, notificationRepository, pushNotificationService)

    Given("a user applying for a VUP loan") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(vupLoanRepository = vupLoanRepository, accountRepository = accountRepository)
        val savedSlot = slot<VupLoan>()
        every { vupLoanRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { vupLoanRepository.findByUserIdAndStatusIn("user_1", any()) } returns emptyList()
        val applicantAccount = account("account_1", "user_1")
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns applicantAccount
        every { accountRepository.findByIdForUpdate("account_1") } returns Optional.of(applicantAccount)

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
        val accountRepository = mockk<AccountRepository>()
        val service = newService(vupLoanRepository = vupLoanRepository, accountRepository = accountRepository)
        val existing = VupLoan(
            id = "vuploan_existing", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("50000"), outstandingPrincipal = BigDecimal("50000"), status = VupLoanStatus.DISBURSED,
        )
        every { vupLoanRepository.findByUserIdAndStatusIn("user_1", any()) } returns listOf(existing)
        val applicantAccount = account("account_1", "user_1")
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns applicantAccount
        every { accountRepository.findByIdForUpdate("account_1") } returns Optional.of(applicantAccount)

        When("applying for a second loan") {
            Then("the one-active-loan guard fires, after locking the caller's own account row first (closing the double-apply race)") {
                shouldThrow<VupLoanAlreadyActiveException> {
                    service.applyForLoan("user_1", 2, VupLoanPurpose.BUSINESS, BigDecimal("50000"))
                }
                verify(exactly = 1) { accountRepository.findByIdForUpdate("account_1") }
            }
        }
    }

    Given("a real REQUESTED VUP loan being disbursed") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(vupLoanRepository = vupLoanRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val loan = VupLoan(
            id = "vuploan_1", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("100000"),
        )
        every { vupLoanRepository.findById("vuploan_1") } returns Optional.of(loan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { vupLoanRepository.save(any()) } answers { firstArg() }

        When("itunda disburses it") {
            val result = service.disburse("user_1", "vuploan_1")

            Then("real bug class this feature was built to avoid: disbursement must come from itunda's own loan_payable receivable, never SaccoService's pooled account") {
                result.status shouldBe VupLoanStatus.DISBURSED
                (result.disbursedAt != null) shouldBe true
                result.dueDate shouldBe LocalDate.now().plusMonths(12)
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.size == 2 &&
                            legs.any { it.accountId == "account_1" && it.direction == LedgerDirection.CREDIT && it.amount == BigDecimal("100000") } &&
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
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(vupLoanRepository = vupLoanRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val loan = VupLoan(
            id = "vuploan_3", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("30000"), status = VupLoanStatus.DISBURSED,
            dueDate = LocalDate.now().plusMonths(6),
        )
        every { vupLoanRepository.findById("vuploan_3") } returns Optional.of(loan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
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
                            legs.any { it.accountId == "account_1" && it.direction == LedgerDirection.DEBIT } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.CREDIT }
                    })
                }
            }
        }
    }

    Given("a real DISBURSED VUP loan being partially repaid") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(vupLoanRepository = vupLoanRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val loan = VupLoan(
            id = "vuploan_4", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("100000"), status = VupLoanStatus.DISBURSED,
            dueDate = LocalDate.now().plusMonths(6),
        )
        every { vupLoanRepository.findById("vuploan_4") } returns Optional.of(loan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
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

    // Real ops loan-default review queue (2026-09-06, Bank product-completeness
    // pass) -- see VupLoanService.decide's own doc comment for why this
    // deliberately never touches the ledger. Separate Given blocks, not sibling
    // Whens -- same "shared mutable loan instance leaks mutation across Whens"
    // reasoning the repay tests above already document.
    Given("a real OVERDUE VUP loan being written off by an admin") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(
            vupLoanRepository = vupLoanRepository,
            notificationRepository = notificationRepository,
            pushNotificationService = pushNotificationService,
        )
        val loan = VupLoan(
            id = "vuploan_5", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("100000"), status = VupLoanStatus.OVERDUE,
        )
        every { vupLoanRepository.findById("vuploan_5") } returns Optional.of(loan)
        every { vupLoanRepository.save(any()) } answers { firstArg() }
        // Relaxed mockk's default generic-method auto-answer doesn't satisfy
        // JpaRepository.save's own <S extends T> S signature -- a real
        // ClassCastException at runtime, not just a style preference. Same
        // explicit-stub fix as vupLoanRepository.save above.
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("an admin writes it off with a note") {
            val result = service.decide("vuploan_5", "admin_1", writeOff = true, note = "borrower unreachable")

            Then("the loan transitions to WRITTEN_OFF with a real audit trail, and the outstanding balance is left untouched") {
                result.status shouldBe VupLoanStatus.WRITTEN_OFF
                result.reviewedBy shouldBe "admin_1"
                result.reviewNote shouldBe "borrower unreachable"
                (result.reviewedAt != null) shouldBe true
                result.outstandingPrincipal shouldBe BigDecimal("100000")
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", any(), any(), any()) }
            }
        }
    }

    Given("a real OVERDUE VUP loan being merely acknowledged by an admin") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(vupLoanRepository = vupLoanRepository, pushNotificationService = pushNotificationService)
        val loan = VupLoan(
            id = "vuploan_6", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("100000"), status = VupLoanStatus.OVERDUE,
        )
        every { vupLoanRepository.findById("vuploan_6") } returns Optional.of(loan)
        every { vupLoanRepository.save(any()) } answers { firstArg() }

        When("an admin acknowledges it without writing it off") {
            val result = service.decide("vuploan_6", "admin_1", writeOff = false, note = "contacted borrower, monitoring")

            Then("the loan stays OVERDUE but records the review, and no write-off notification fires") {
                result.status shouldBe VupLoanStatus.OVERDUE
                result.reviewedBy shouldBe "admin_1"
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
            }
        }
    }

    Given("a real OVERDUE VUP loan reviewed with an over-length note") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val service = newService(vupLoanRepository = vupLoanRepository)
        val loan = VupLoan(
            id = "vuploan_7", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("100000"), status = VupLoanStatus.OVERDUE,
        )
        every { vupLoanRepository.findById("vuploan_7") } returns Optional.of(loan)

        When("the review note exceeds the real 255-character bound") {
            Then("the length guard fires before touching the repository") {
                shouldThrow<InvalidVupLoanReviewNoteException> {
                    service.decide("vuploan_7", "admin_1", writeOff = false, note = "x".repeat(256))
                }
            }
        }
    }

    Given("a VUP loan that is not OVERDUE") {
        val vupLoanRepository = mockk<VupLoanRepository>()
        val service = newService(vupLoanRepository = vupLoanRepository)
        val loan = VupLoan(
            id = "vuploan_8", userId = "user_1", declaredUbudeheCategory = 2, purpose = VupLoanPurpose.FARMING,
            principalAmount = BigDecimal("100000"), outstandingPrincipal = BigDecimal("100000"), status = VupLoanStatus.DISBURSED,
        )
        every { vupLoanRepository.findById("vuploan_8") } returns Optional.of(loan)

        When("an admin tries to review it anyway") {
            Then("the status guard fires -- only OVERDUE loans belong in this queue") {
                shouldThrow<VupLoanNotOverdueException> {
                    service.decide("vuploan_8", "admin_1", writeOff = false, note = null)
                }
            }
        }
    }
})
