package rw.itunda.loans

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.StudentLoan
import rw.itunda.core.domain.StudentLoanLevel
import rw.itunda.core.domain.StudentLoanStatus
import rw.itunda.core.domain.Account
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerPostResult
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.StudentLoanRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

/**
 * First test coverage for the real Rwanda BRD (Development Bank of Rwanda)
 * higher-education student loan -- see StudentLoanService's own doc comment for the
 * full sourced account. The double-apply-race test mirrors VupLoanServiceTest's own
 * such test, asserting the caller's account lock happened before the active-loan check.
 * The repay-clamp test guards against the same overshoot-clamp regression class found
 * in InsuranceService.contributeToFund/VupLoanService.repay: it asserts the actual
 * ledger leg amount, not just the resulting field. The grace-period-blocks-repay test
 * is this feature's own core distinguishing behavior -- repayment cannot start before
 * the grace period ends.
 *
 * Kotest lesson from this session: sibling `When` blocks under the same `Given` share
 * ONE mutable entity created in the `Given` block -- a mutation in one `When` leaks
 * into siblings. Any test needing a distinct starting state gets its own separate
 * `Given` block, not a sibling `When`.
 */
class StudentLoanServiceTest : BehaviorSpec({

    fun account(id: String, userId: String) = Account(
        id = id, userId = userId, accountNumber = "ACC-$id", accountName = "Test account",
        type = AccountType.MAIN, balance = BigDecimal("1000000"), availableBalance = BigDecimal("1000000"),
    )

    fun newService(
        studentLoanRepository: StudentLoanRepository = mockk(),
        accountRepository: AccountRepository = mockk(),
        ledgerService: LedgerService = mockk(),
        rateLimiter: RateLimiter = mockk(relaxed = true),
        notificationRepository: NotificationRepository = mockk(relaxed = true),
        pushNotificationService: PushNotificationService = mockk(relaxed = true),
    ) = StudentLoanService(studentLoanRepository, accountRepository, ledgerService, rateLimiter, notificationRepository, pushNotificationService)

    val futureGraduation = LocalDate.now().plusYears(1)

    Given("a user applying for a student loan") {
        val studentLoanRepository = mockk<StudentLoanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(studentLoanRepository = studentLoanRepository, accountRepository = accountRepository)
        val savedSlot = slot<StudentLoan>()
        every { studentLoanRepository.save(capture(savedSlot)) } answers { firstArg() }
        every { studentLoanRepository.findByUserIdAndStatusIn("user_1", any()) } returns emptyList()
        val applicantAccount = account("account_1", "user_1")
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns applicantAccount
        every { accountRepository.findByIdForUpdate("account_1") } returns Optional.of(applicantAccount)

        When("applying as UNDERGRADUATE") {
            val result = service.applyForLoan("user_1", StudentLoanLevel.UNDERGRADUATE, BigDecimal("1500000"), BigDecimal("500000"), futureGraduation)

            Then("a real REQUESTED loan is saved with the real 11% undergraduate rate") {
                result.status shouldBe StudentLoanStatus.REQUESTED
                result.interestRate shouldBe 0.11
                savedSlot.captured.principalAmount shouldBe BigDecimal("500000")
                savedSlot.captured.outstandingBalance shouldBe BigDecimal("500000")
            }
        }

        When("applying as POSTGRADUATE") {
            val result = service.applyForLoan("user_1", StudentLoanLevel.POSTGRADUATE, BigDecimal("1500000"), BigDecimal("500000"), futureGraduation)

            Then("the real 12% postgraduate rate is derived") {
                result.interestRate shouldBe 0.12
            }
        }

        When("the expected graduation date is not in the future") {
            Then("the date guard real-400s before touching the repository") {
                shouldThrow<InvalidGraduationDateException> {
                    service.applyForLoan("user_1", StudentLoanLevel.UNDERGRADUATE, BigDecimal("1500000"), BigDecimal("500000"), LocalDate.now())
                }
            }
        }

        When("requesting an amount over the real itunda-chosen ceiling") {
            Then("the amount guard real-400s before touching the repository") {
                shouldThrow<InvalidStudentLoanAmountException> {
                    service.applyForLoan("user_1", StudentLoanLevel.UNDERGRADUATE, BigDecimal("1500000"), BigDecimal("999999999"), futureGraduation)
                }
            }
        }
    }

    Given("a user who already has an active student loan") {
        val studentLoanRepository = mockk<StudentLoanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(studentLoanRepository = studentLoanRepository, accountRepository = accountRepository)
        val existing = StudentLoan(
            id = "studentloan_existing", userId = "user_1", level = StudentLoanLevel.UNDERGRADUATE,
            declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
            outstandingBalance = BigDecimal("500000"), interestRate = 0.11, status = StudentLoanStatus.DISBURSED,
            expectedGraduationDate = futureGraduation,
        )
        every { studentLoanRepository.findByUserIdAndStatusIn("user_1", any()) } returns listOf(existing)
        val applicantAccount = account("account_1", "user_1")
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns applicantAccount
        every { accountRepository.findByIdForUpdate("account_1") } returns Optional.of(applicantAccount)

        When("applying for a second loan") {
            Then("the one-active-loan guard fires, after locking the caller's own account row first (closing the double-apply race)") {
                shouldThrow<StudentLoanAlreadyActiveException> {
                    service.applyForLoan("user_1", StudentLoanLevel.UNDERGRADUATE, BigDecimal("1500000"), BigDecimal("100000"), futureGraduation)
                }
                verify(exactly = 1) { accountRepository.findByIdForUpdate("account_1") }
            }
        }
    }

    Given("a real REQUESTED student loan being disbursed") {
        val studentLoanRepository = mockk<StudentLoanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(studentLoanRepository = studentLoanRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val loan = StudentLoan(
            id = "studentloan_1", userId = "user_1", level = StudentLoanLevel.UNDERGRADUATE,
            declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
            outstandingBalance = BigDecimal("500000"), interestRate = 0.11, expectedGraduationDate = futureGraduation,
        )
        every { studentLoanRepository.findById("studentloan_1") } returns Optional.of(loan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_1", emptyList())
        every { studentLoanRepository.save(any()) } answers { firstArg() }

        When("itunda disburses it") {
            val result = service.disburse("user_1", "studentloan_1")

            Then("disbursement CREDITs the account and DEBITs loan_payable -- mirroring OverdraftService's exact ledger shape") {
                result.status shouldBe StudentLoanStatus.DISBURSED
                (result.disbursedAt != null) shouldBe true
                verify {
                    ledgerService.postLedgerTransaction(any(), match { legs ->
                        legs.size == 2 &&
                            legs.any { it.accountId == "account_1" && it.direction == LedgerDirection.CREDIT && it.amount == BigDecimal("500000") } &&
                            legs.any { it.accountId == "loan_payable" && it.accountType == LedgerAccountType.LOAN_PAYABLE && it.direction == LedgerDirection.DEBIT && it.amount == BigDecimal("500000") }
                    })
                }
            }
        }

        When("someone else tries to disburse it (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<StudentLoanNotFoundException> { service.disburse("attacker", "studentloan_1") }
            }
        }
    }

    Given("a real DISBURSED student loan being declared graduated") {
        val studentLoanRepository = mockk<StudentLoanRepository>()
        val service = newService(studentLoanRepository = studentLoanRepository)
        val loan = StudentLoan(
            id = "studentloan_2", userId = "user_1", level = StudentLoanLevel.UNDERGRADUATE,
            declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
            outstandingBalance = BigDecimal("500000"), interestRate = 0.11, status = StudentLoanStatus.DISBURSED,
            expectedGraduationDate = futureGraduation,
        )
        every { studentLoanRepository.findById("studentloan_2") } returns Optional.of(loan)
        every { studentLoanRepository.save(any()) } answers { firstArg() }

        When("the borrower declares graduation") {
            val result = service.declareGraduated("user_1", "studentloan_2")

            Then("a real future graceEndsAt (itunda's own 6-month pick) is set and the loan enters IN_GRACE_PERIOD") {
                result.status shouldBe StudentLoanStatus.IN_GRACE_PERIOD
                result.graceEndsAt shouldBe LocalDate.now().plusMonths(6)
            }
        }

        When("someone else tries to declare it graduated (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<StudentLoanNotFoundException> { service.declareGraduated("attacker", "studentloan_2") }
            }
        }
    }

    Given("a real loan still IN_GRACE_PERIOD") {
        val studentLoanRepository = mockk<StudentLoanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val service = newService(studentLoanRepository = studentLoanRepository, accountRepository = accountRepository)
        val loan = StudentLoan(
            id = "studentloan_3", userId = "user_1", level = StudentLoanLevel.UNDERGRADUATE,
            declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
            outstandingBalance = BigDecimal("500000"), interestRate = 0.11, status = StudentLoanStatus.IN_GRACE_PERIOD,
            expectedGraduationDate = futureGraduation, graceEndsAt = LocalDate.now().plusMonths(3),
        )
        every { studentLoanRepository.findById("studentloan_3") } returns Optional.of(loan)

        When("the borrower tries to repay before the grace period ends") {
            Then("this feature's own core distinguishing behavior fires: repayment cannot start before the grace period ends") {
                shouldThrow<StudentLoanNotRepayableException> {
                    service.repay("user_1", "studentloan_3", BigDecimal("10000"))
                }
            }
        }
    }

    Given("a real REPAYING student loan being overpaid") {
        val studentLoanRepository = mockk<StudentLoanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val service = newService(studentLoanRepository = studentLoanRepository, accountRepository = accountRepository, ledgerService = ledgerService)

        val loan = StudentLoan(
            id = "studentloan_4", userId = "user_1", level = StudentLoanLevel.UNDERGRADUATE,
            declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
            outstandingBalance = BigDecimal("30000"), interestRate = 0.11, status = StudentLoanStatus.REPAYING,
            expectedGraduationDate = futureGraduation,
        )
        every { studentLoanRepository.findById("studentloan_4") } returns Optional.of(loan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_repay", emptyList())
        every { studentLoanRepository.save(any()) } answers { firstArg() }

        When("repaying 100,000 against a real 30,000 outstanding balance") {
            val result = service.repay("user_1", "studentloan_4", BigDecimal("100000"))

            Then("the ledger only ever sees the real clamped 30,000, not the raw overshooting amount, and status becomes REPAID") {
                result.status shouldBe StudentLoanStatus.REPAID
                result.outstandingBalance shouldBe BigDecimal.ZERO
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

    Given("a real REPAYING student loan being partially repaid") {
        val studentLoanRepository = mockk<StudentLoanRepository>()
        val accountRepository = mockk<AccountRepository>()
        val ledgerService = mockk<LedgerService>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = newService(studentLoanRepository = studentLoanRepository, accountRepository = accountRepository, ledgerService = ledgerService, rateLimiter = rateLimiter)

        val loan = StudentLoan(
            id = "studentloan_5", userId = "user_1", level = StudentLoanLevel.UNDERGRADUATE,
            declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
            outstandingBalance = BigDecimal("500000"), interestRate = 0.11, status = StudentLoanStatus.REPAYING,
            expectedGraduationDate = futureGraduation,
        )
        every { studentLoanRepository.findById("studentloan_5") } returns Optional.of(loan)
        every { accountRepository.findByUserIdAndType("user_1", AccountType.MAIN) } returns account("account_1", "user_1")
        every { ledgerService.postLedgerTransaction(any(), any()) } returns LedgerPostResult("ledgertxn_repay", emptyList())
        every { studentLoanRepository.save(any()) } answers { firstArg() }

        When("repaying 200,000 of the real 500,000 principal") {
            val result = service.repay("user_1", "studentloan_5", BigDecimal("200000"))

            Then("the loan stays REPAYING with a real reduced outstanding balance") {
                result.status shouldBe StudentLoanStatus.REPAYING
                result.outstandingBalance shouldBe BigDecimal("300000")
            }

            // Real gap found live (sibling comparison against LoansService.repayLoan/
            // OverdraftService.repay, 2026-09-13): repay had no rate limit at all.
            Then("the real rate limiter is actually consulted, not just mocked away") {
                verify(exactly = 1) { rateLimiter.checkLimit("student-loan:repay:user_1", limit = 30, window = Duration.ofHours(1)) }
            }
        }

        When("someone else tries to repay it (IDOR)") {
            Then("it real-404s, not 403s") {
                shouldThrow<StudentLoanNotFoundException> { service.repay("attacker", "studentloan_5", BigDecimal("10000")) }
            }
        }
    }

    Given("a real IN_GRACE_PERIOD student loan whose grace period ends within the reminder window") {
        val studentLoanRepository = mockk<StudentLoanRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(studentLoanRepository = studentLoanRepository, notificationRepository = notificationRepository, pushNotificationService = pushNotificationService)

        val loan = StudentLoan(
            id = "studentloan_6", userId = "user_1", level = StudentLoanLevel.UNDERGRADUATE,
            declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
            outstandingBalance = BigDecimal("500000"), interestRate = 0.11, status = StudentLoanStatus.IN_GRACE_PERIOD,
            expectedGraduationDate = futureGraduation, graceEndsAt = LocalDate.now().plusDays(3),
        )
        every { studentLoanRepository.findByStatus(StudentLoanStatus.IN_GRACE_PERIOD) } returns listOf(loan)
        every { studentLoanRepository.findById("studentloan_6") } returns Optional.of(loan)
        every { studentLoanRepository.save(any()) } answers { firstArg() }
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("the reminder sweep runs") {
            val due = service.getLoansDueSoonForGraceEndReminder()

            Then("this feature's own core distinguishing behavior fires: the loan is found due for a reminder") {
                due shouldBe listOf(loan)
            }
        }

        When("sendGraceEndReminder is called for it") {
            service.sendGraceEndReminder("studentloan_6")

            Then("a real notification and push fire, and graceEndReminderSentAt is stamped so the next sweep skips it") {
                loan.graceEndReminderSentAt shouldNotBe null
                verify { notificationRepository.save(match<Notification> { it.userId == "user_1" && it.type == "STUDENT_LOAN_GRACE_PERIOD_ENDING_SOON" }) }
                verify { pushNotificationService.sendToUser("user_1", any(), any(), mapOf("loanId" to "studentloan_6")) }
                verify { studentLoanRepository.save(loan) }
            }

            // Real fix (2026-09-13, push-before-commit ordering sweep): graceEndReminderSentAt
            // must be saved BEFORE the push fires -- otherwise a rollback after the push
            // leaves the flag unset and the next scheduler pass resends it.
            Then("the graceEndReminderSentAt flag is saved before the push is sent") {
                verifyOrder {
                    studentLoanRepository.save(loan)
                    pushNotificationService.sendToUser("user_1", any(), any(), any())
                }
            }
        }
    }

    Given("a real IN_GRACE_PERIOD student loan whose grace period ends far in the future") {
        val studentLoanRepository = mockk<StudentLoanRepository>()
        val service = newService(studentLoanRepository = studentLoanRepository)

        val loan = StudentLoan(
            id = "studentloan_7", userId = "user_1", level = StudentLoanLevel.UNDERGRADUATE,
            declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
            outstandingBalance = BigDecimal("500000"), interestRate = 0.11, status = StudentLoanStatus.IN_GRACE_PERIOD,
            expectedGraduationDate = futureGraduation, graceEndsAt = LocalDate.now().plusMonths(5),
        )
        every { studentLoanRepository.findByStatus(StudentLoanStatus.IN_GRACE_PERIOD) } returns listOf(loan)

        When("the reminder sweep runs") {
            val due = service.getLoansDueSoonForGraceEndReminder()

            Then("it is honestly not due yet -- outside the real reminder window") {
                due shouldBe emptyList()
            }
        }
    }

    Given("a real IN_GRACE_PERIOD student loan that already had its grace-end reminder sent") {
        val studentLoanRepository = mockk<StudentLoanRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = newService(studentLoanRepository = studentLoanRepository, notificationRepository = notificationRepository, pushNotificationService = pushNotificationService)

        val loan = StudentLoan(
            id = "studentloan_8", userId = "user_1", level = StudentLoanLevel.UNDERGRADUATE,
            declaredAnnualHouseholdIncome = BigDecimal("1500000"), principalAmount = BigDecimal("500000"),
            outstandingBalance = BigDecimal("500000"), interestRate = 0.11, status = StudentLoanStatus.IN_GRACE_PERIOD,
            expectedGraduationDate = futureGraduation, graceEndsAt = LocalDate.now().plusDays(2),
            graceEndReminderSentAt = Instant.now(),
        )
        every { studentLoanRepository.findByStatus(StudentLoanStatus.IN_GRACE_PERIOD) } returns listOf(loan)
        every { studentLoanRepository.findById("studentloan_8") } returns Optional.of(loan)

        When("the reminder sweep runs") {
            val due = service.getLoansDueSoonForGraceEndReminder()

            Then("it is honestly excluded -- already reminded once") {
                due shouldBe emptyList()
            }
        }

        When("sendGraceEndReminder is called anyway (e.g. a stale scheduler tick)") {
            service.sendGraceEndReminder("studentloan_8")

            Then("the real re-check right before sending stops a duplicate notification") {
                verify(exactly = 0) { notificationRepository.save(any()) }
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
            }
        }
    }
})
