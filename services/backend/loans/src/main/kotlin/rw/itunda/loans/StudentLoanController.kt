package rw.itunda.loans

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.StudentLoanLevel
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal
import java.time.LocalDate

data class ApplyForStudentLoanRequest(
    val level: StudentLoanLevel,
    val declaredAnnualHouseholdIncome: BigDecimal,
    val amount: BigDecimal,
    val expectedGraduationDate: LocalDate,
)
data class RepayStudentLoanRequest(val amount: BigDecimal)

// Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan -- see
// StudentLoanService's own doc comment for the full sourced account. Sourced beyond
// this session's usual Toss/Kakao/Naver/Coupang reference ecosystems.
@RestController
@RequestMapping("/api/v1/loans/student")
class StudentLoanController(
    private val studentLoanService: StudentLoanService,
    private val idempotencyService: IdempotencyService,
    private val studentLoanGraceEndReminderScheduler: StudentLoanGraceEndReminderScheduler,
) {
    // Real gap found 2026-09-05 (matching the exact "AlreadyX guard with no
    // idempotency-key protection" bug class first found in EatsOrderService's
    // tipRider, see feedback_toss_error_handling's own note on that fix): a lost
    // response after a successful apply (client timeout, retry before the button
    // disables) would resubmit here, and StudentLoanService.applyForLoan's own
    // StudentLoanAlreadyActiveException guard would fire on the retry -- a confusing
    // "you already have an active loan" error for someone whose FIRST application
    // actually just succeeded. disburse/repay below were already correctly
    // protected; this create endpoint was the one outlier, matching the dominant
    // convention every other loans/* controller's own apply/open endpoint already
    // follows (LoansController.apply, LoansController's overdraft/open and
    // postpaid-credit/apply).
    @PostMapping("/apply")
    fun apply(
        @RequestBody request: ApplyForStudentLoanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/student/apply", idempotencyKey, request) {
            val loan = studentLoanService.applyForLoan(
                currentUser.userId, request.level, request.declaredAnnualHouseholdIncome, request.amount, request.expectedGraduationDate,
            )
            HttpStatus.CREATED.value() to mapOf("success" to true, "loan" to loan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/{loanId}/disburse")
    fun disburse(
        @PathVariable loanId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/student/$loanId/disburse", idempotencyKey, currentUser.userId) {
            val loan = studentLoanService.disburse(currentUser.userId, loanId)
            200 to mapOf("success" to true, "loan" to loan)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real gap found 2026-09-05 (see feedback_idempotency_key_sweep memory's own
    // disclosed-not-fixed note on this exact endpoint) -- a retry after a
    // successful declare-graduated (lost response) would hit
    // StudentLoanNotDisbursedException since the loan is no longer DISBURSED.
    // Lower-frequency/lower-stakes than the 5 originally-fixed endpoints (a
    // one-time-per-loan lifecycle transition, not a hot financial action), but
    // cheap enough to close now that the pattern's already established here.
    @PostMapping("/{loanId}/declare-graduated")
    fun declareGraduated(
        @PathVariable loanId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/student/$loanId/declare-graduated", idempotencyKey, currentUser.userId) {
            val loan = studentLoanService.declareGraduated(currentUser.userId, loanId)
            200 to mapOf("success" to true, "loan" to loan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/{loanId}/repay")
    fun repay(
        @PathVariable loanId: String,
        @RequestBody request: RepayStudentLoanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/student/$loanId/repay", idempotencyKey, request) {
            val loan = studentLoanService.repay(currentUser.userId, loanId, request.amount)
            200 to mapOf("success" to true, "loan" to loan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/my")
    fun getMyLoans(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "loans" to studentLoanService.getMyLoans(currentUser.userId)))

    @GetMapping("/{loanId}")
    fun getLoan(@PathVariable loanId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "loan" to studentLoanService.getLoan(currentUser.userId, loanId)))

    @GetMapping("/{loanId}/suggested-payment")
    fun getSuggestedPayment(@PathVariable loanId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true) + studentLoanService.getSuggestedMonthlyPayment(currentUser.userId, loanId))

    // Real grace-period-ending-soon reminder manual trigger -- same "expose the
    // scheduler's own real logic as a callable endpoint" convention
    // LoansController.processPostpaidCreditPaymentReminders already establishes, so a
    // real loan's real graceEndsAt can be verified without waiting actual wall-clock
    // days for it to enter the reminder window.
    // Real gap found live (2026-08-31, market-readiness audit): this fires the
    // reminder job for EVERY user's due loans system-wide, yet had no ADMIN gate -- any
    // authenticated user could call it. ADMIN-gated the same
    // @PreAuthorize("hasRole('ADMIN')") way WeeklySavingsController.processDue already
    // is (this route doesn't live under /api/v1/system/**, so it doesn't inherit
    // SecurityConfig's blanket ADMIN gate there).
    @PostMapping("/process-grace-end-reminders")
    @PreAuthorize("hasRole('ADMIN')")
    fun processGraceEndReminders(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = studentLoanGraceEndReminderScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    @ExceptionHandler(InvalidGraduationDateException::class)
    fun handleInvalidGraduationDate(ex: InvalidGraduationDateException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_GRADUATION_DATE", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidStudentLoanAmountException::class)
    fun handleInvalidAmount(ex: InvalidStudentLoanAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_STUDENT_LOAN_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(StudentLoanAlreadyActiveException::class)
    fun handleAlreadyActive(ex: StudentLoanAlreadyActiveException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("STUDENT_LOAN_ALREADY_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(StudentLoanNotFoundException::class)
    fun handleNotFound(ex: StudentLoanNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("STUDENT_LOAN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(StudentLoanNoAccountException::class)
    fun handleNoAccount(ex: StudentLoanNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(StudentLoanNotRequestedException::class)
    fun handleNotRequested(ex: StudentLoanNotRequestedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("STUDENT_LOAN_NOT_REQUESTED", ex.message ?: "Conflict"))

    @ExceptionHandler(StudentLoanNotDisbursedException::class)
    fun handleNotDisbursed(ex: StudentLoanNotDisbursedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("STUDENT_LOAN_NOT_DISBURSED", ex.message ?: "Conflict"))

    @ExceptionHandler(StudentLoanNotRepayableException::class)
    fun handleNotRepayable(ex: StudentLoanNotRepayableException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("STUDENT_LOAN_NOT_REPAYABLE", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))
}
