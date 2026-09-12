package rw.itunda.loans

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LedgerAccountType
import rw.itunda.core.domain.LedgerDirection
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.StudentLoan
import rw.itunda.core.domain.StudentLoanLevel
import rw.itunda.core.domain.StudentLoanStatus
import rw.itunda.core.domain.AccountType
import rw.itunda.core.ledger.LedgerLeg
import rw.itunda.core.ledger.LedgerService
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.StudentLoanRepository
import rw.itunda.core.repository.AccountRepository
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class InvalidGraduationDateException(message: String) : RuntimeException(message)
class InvalidStudentLoanAmountException(message: String) : RuntimeException(message)
class StudentLoanAlreadyActiveException(message: String) : RuntimeException(message)
class StudentLoanNotFoundException(message: String) : RuntimeException(message)
class StudentLoanNotRequestedException(message: String) : RuntimeException(message)
class StudentLoanNotDisbursedException(message: String) : RuntimeException(message)
class StudentLoanNotRepayableException(message: String) : RuntimeException(message)
class StudentLoanNoAccountException(message: String) : RuntimeException(message)

// itunda's own honest ceiling on a single BRD student loan: 2,000,000 RWF -- a
// reasonable itunda-chosen bound, not a claimed reproduction of any real published
// per-student cap (the sourcing didn't give an exact figure).
private val MAX_STUDENT_LOAN_AMOUNT = BigDecimal("2000000")

// Real, documented BRD fixed rates (brd.rw): 11% undergraduate, 12% postgraduate.
private const val UNDERGRADUATE_RATE = 0.11
private const val POSTGRADUATE_RATE = 0.12

// Real student-loan-servicer grace-period-ending reminder window -- see
// StudentLoan.graceEndReminderSentAt's own doc comment for the full sourced account
// (Navient/Nelnet/MOHELA). Same order of magnitude as this codebase's other
// multi-month-horizon reminder windows (GiftVoucher.EXPIRY_REMINDER_WINDOW's own 7 days).
// LocalDate-scoped (graceEndsAt is a LocalDate, not an Instant), so a plain day count
// rather than a java.time.Duration.
private const val GRACE_END_REMINDER_WINDOW_DAYS = 7L

// A loan is "active" (blocks a second application) at every status except REPAID.
private val ACTIVE_STATUSES = listOf(
    StudentLoanStatus.REQUESTED, StudentLoanStatus.DISBURSED, StudentLoanStatus.IN_GRACE_PERIOD,
    StudentLoanStatus.REPAYING, StudentLoanStatus.OVERDUE,
)

/**
 * Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan -- see
 * `StudentLoan.kt`'s own doc comment for the full sourced account, including the
 * honest v1 limitation on the real 8%-of-income payroll deduction this backend has
 * no path to enforce.
 *
 * Genuinely distinct from every other lending feature in this codebase: eligibility
 * on self-declared household income (not Ubudehe, unlike `VupLoanService`), a
 * mandatory grace period between disbursement and first-repayment obligation, and
 * income-percentage-SUGGESTED (not fixed-installment) repayment.
 */
@Service
class StudentLoanService(
    private val studentLoanRepository: StudentLoanRepository,
    private val accountRepository: AccountRepository,
    private val ledgerService: LedgerService,
    private val rateLimiter: RateLimiter,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    // Real bug class this session has hit repeatedly: the "reject if already active"
    // check-then-CREATE race -- @Version can't protect a row that doesn't exist yet.
    // Locking the caller's own MAIN account row first (same fix VupLoanService.applyForLoan
    // and YouthAccountService.openYouthAccount already needed for this exact shape)
    // serializes concurrent applications for the same user without needing a new lock
    // table.
    @Transactional
    fun applyForLoan(
        userId: String,
        level: StudentLoanLevel,
        declaredAnnualHouseholdIncome: BigDecimal,
        amount: BigDecimal,
        expectedGraduationDate: LocalDate,
    ): StudentLoan {
        if (!expectedGraduationDate.isAfter(LocalDate.now())) {
            throw InvalidGraduationDateException("Expected graduation date must be in the future")
        }
        if (amount <= BigDecimal.ZERO || amount > MAX_STUDENT_LOAN_AMOUNT) {
            throw InvalidStudentLoanAmountException("Amount must be between 1 and $MAX_STUDENT_LOAN_AMOUNT RWF")
        }

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw StudentLoanNoAccountException("No account found for this account")
        accountRepository.findByIdForUpdate(account.id)

        val activeLoans = studentLoanRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES)
        if (activeLoans.isNotEmpty()) {
            throw StudentLoanAlreadyActiveException("You already have an active student loan -- repay it before applying for another")
        }

        rateLimiter.checkLimit("student-loan:apply:$userId", limit = 5, window = Duration.ofDays(1))

        val interestRate = if (level == StudentLoanLevel.POSTGRADUATE) POSTGRADUATE_RATE else UNDERGRADUATE_RATE

        return studentLoanRepository.save(
            StudentLoan(
                id = "studentloan_${UUID.randomUUID()}", userId = userId, level = level,
                declaredAnnualHouseholdIncome = declaredAnnualHouseholdIncome,
                principalAmount = amount, outstandingBalance = amount, interestRate = interestRate,
                expectedGraduationDate = expectedGraduationDate,
            ),
        )
    }

    @Transactional
    fun disburse(userId: String, loanId: String): StudentLoan {
        val loan = studentLoanRepository.findById(loanId).orElseThrow { StudentLoanNotFoundException("Student loan not found") }
        if (loan.userId != userId) throw StudentLoanNotFoundException("Student loan not found")
        if (loan.status != StudentLoanStatus.REQUESTED) throw StudentLoanNotRequestedException("Only a REQUESTED loan can be disbursed")

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw StudentLoanNoAccountException("No account found for this account")

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.CREDIT, loan.principalAmount, "BRD student loan disbursement"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.DEBIT, loan.principalAmount, "BRD student loan principal owed"),
            ),
        )

        loan.status = StudentLoanStatus.DISBURSED
        loan.disbursedAt = Instant.now()
        return studentLoanRepository.save(loan)
    }

    @Transactional
    fun declareGraduated(userId: String, loanId: String): StudentLoan {
        val loan = studentLoanRepository.findById(loanId).orElseThrow { StudentLoanNotFoundException("Student loan not found") }
        if (loan.userId != userId) throw StudentLoanNotFoundException("Student loan not found")
        if (loan.status != StudentLoanStatus.DISBURSED) throw StudentLoanNotDisbursedException("Only a DISBURSED loan can be marked graduated")

        // Real sourced range is 6-12 months (brd.rw); itunda's own honest pick within
        // that range is 6 months -- the shortest real grace period sourced, not a
        // claimed reproduction of BRD's own exact per-student term.
        loan.graceEndsAt = LocalDate.now().plusMonths(6)
        loan.status = StudentLoanStatus.IN_GRACE_PERIOD
        return studentLoanRepository.save(loan)
    }

    @Transactional
    fun repay(userId: String, loanId: String, amount: BigDecimal): StudentLoan {
        // Real anti-spam/cost limit -- same "frequent, repeatable money-movement action"
        // convention LoansService.repayLoan/OverdraftService.repay already establish,
        // never wired in here until now.
        rateLimiter.checkLimit("student-loan:repay:$userId", limit = 30, window = Duration.ofHours(1))
        val loan = studentLoanRepository.findById(loanId).orElseThrow { StudentLoanNotFoundException("Student loan not found") }
        if (loan.userId != userId) throw StudentLoanNotFoundException("Student loan not found")
        // IN_GRACE_PERIOD/REQUESTED/DISBURSED are explicitly NOT repayable -- repayment
        // can't start before the grace period ends, that's the whole point of this
        // feature.
        if (loan.status != StudentLoanStatus.REPAYING && loan.status != StudentLoanStatus.OVERDUE) {
            throw StudentLoanNotRepayableException("Only a REPAYING or OVERDUE loan can be repaid -- repayment can't start before the grace period ends")
        }
        if (amount <= BigDecimal.ZERO) throw InvalidStudentLoanAmountException("Repayment amount must be positive")

        val account = accountRepository.findByUserIdAndType(userId, AccountType.MAIN)
            ?: throw StudentLoanNoAccountException("No account found for this account")

        // Clamp BEFORE ever touching the ledger -- the exact overshoot-clamp lesson
        // this session learned fixing InsuranceService.contributeToFund/VupLoanService.repay.
        // Never post the raw amount to the ledger and cap the field separately.
        val actualAmount = amount.min(loan.outstandingBalance)

        ledgerService.postLedgerTransaction(
            account.currency,
            listOf(
                LedgerLeg(account.id, LedgerAccountType.WALLET, LedgerDirection.DEBIT, actualAmount, "BRD student loan repayment"),
                LedgerLeg("loan_payable", LedgerAccountType.LOAN_PAYABLE, LedgerDirection.CREDIT, actualAmount, "BRD student loan repayment"),
            ),
        )

        loan.outstandingBalance = loan.outstandingBalance.subtract(actualAmount)
        loan.status = if (loan.outstandingBalance <= BigDecimal.ZERO) StudentLoanStatus.REPAID else StudentLoanStatus.REPAYING
        return studentLoanRepository.save(loan)
    }

    fun getMyLoans(userId: String): List<StudentLoan> = studentLoanRepository.findByUserId(userId)

    fun getLoan(userId: String, loanId: String): StudentLoan {
        val loan = studentLoanRepository.findById(loanId).orElseThrow { StudentLoanNotFoundException("Student loan not found") }
        if (loan.userId != userId) throw StudentLoanNotFoundException("Student loan not found")
        return loan
    }

    // Real BRD 8%-of-monthly-income deduction (brd.rw) is fundamentally an
    // employer-payroll/RRA-integration mechanic itunda has no path to -- see
    // StudentLoan.kt's own doc comment. This is surfaced only as a SUGGESTED amount,
    // labeled honestly in the response itself, never automatically enforced or
    // deducted.
    fun getSuggestedMonthlyPayment(userId: String, loanId: String): Map<String, Any?> {
        val loan = getLoan(userId, loanId)
        val suggestedMonthlyPayment = loan.declaredAnnualHouseholdIncome
            .divide(BigDecimal(12), 2, java.math.RoundingMode.HALF_UP)
            .multiply(BigDecimal("0.08"))
        return mapOf(
            "loanId" to loan.id,
            "outstandingBalance" to loan.outstandingBalance,
            "suggestedMonthlyPayment" to suggestedMonthlyPayment,
            "note" to "This is a suggested amount based on your declared income -- itunda does not automatically deduct from your paycheck or account.",
        )
    }

    // For the grace-period scheduler: IN_GRACE_PERIOD loans whose grace period has
    // already elapsed.
    fun getLoansDueForGracePeriodEnd(): List<StudentLoan> {
        val today = LocalDate.now()
        return studentLoanRepository.findAll().filter {
            it.status == StudentLoanStatus.IN_GRACE_PERIOD && it.graceEndsAt != null && !it.graceEndsAt!!.isAfter(today)
        }
    }

    @Transactional
    fun markRepaying(loan: StudentLoan) {
        loan.status = StudentLoanStatus.REPAYING
        studentLoanRepository.save(loan)
    }

    // Real pre-end grace-period reminder sweep -- see
    // StudentLoanGraceEndReminderScheduler's own doc comment and
    // GRACE_END_REMINDER_WINDOW_DAYS's own doc comment for the full real sourcing.
    // Coarse repo filter (every IN_GRACE_PERIOD loan, same "cheap DB-level filter, exact
    // condition in-service" split PostpaidCreditService.getLinesDueSoonForPaymentReminder
    // already establishes), exact "due soon, not yet reminded" condition in-service.
    // Deliberately does NOT overlap with the already-past case (graceEndsAt in the past
    // is left to StudentLoanGracePeriodScheduler's own status-flip sweep) -- this is
    // honestly the earlier, friendlier nudge, not a duplicate of it.
    fun getLoansDueSoonForGraceEndReminder(): List<StudentLoan> {
        val today = LocalDate.now()
        val cutoff = today.plusDays(GRACE_END_REMINDER_WINDOW_DAYS)
        return studentLoanRepository.findByStatus(StudentLoanStatus.IN_GRACE_PERIOD).filter { loan ->
            loan.graceEndReminderSentAt == null && loan.graceEndsAt != null &&
                !loan.graceEndsAt!!.isBefore(today) && !loan.graceEndsAt!!.isAfter(cutoff)
        }
    }

    /** One real grace-period-ending-soon notification, called per-loan by the scheduler --
     * re-checks `graceEndReminderSentAt`/`status`/`graceEndsAt` right before sending so a
     * genuine race (e.g. StudentLoanGracePeriodScheduler flipping the loan to REPAYING mid-
     * sweep) can't fire a stale reminder, same resilience discipline
     * PostpaidCreditService.sendPaymentReminder's own doc comment already establishes. */
    @Transactional
    fun sendGraceEndReminder(loanId: String) {
        val loan = studentLoanRepository.findById(loanId).orElse(null) ?: return
        if (loan.graceEndReminderSentAt != null || loan.status != StudentLoanStatus.IN_GRACE_PERIOD || loan.graceEndsAt == null) return

        val title = "Your student loan grace period is ending soon"
        val body = "Your BRD student loan grace period ends on ${loan.graceEndsAt} -- repayment begins automatically after that. Check the suggested monthly payment in the Loans tab so you're ready."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = loan.userId, type = "STUDENT_LOAN_GRACE_PERIOD_ENDING_SOON",
                title = title, body = body, isRead = false, createdAt = Instant.now(), dataJson = "{\"loanId\":\"${loan.id}\"}",
            ),
        )
        pushNotificationService.sendToUser(loan.userId, title, body, mapOf("loanId" to loan.id))
        loan.graceEndReminderSentAt = Instant.now()
        studentLoanRepository.save(loan)
    }
}
