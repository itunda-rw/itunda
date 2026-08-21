package rw.itunda.loans

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class ApplyLoanRequest(val loanId: String, val amount: BigDecimal)
data class RepayLoanRequest(val loanId: String, val amount: BigDecimal)
data class RefinanceLoanRequest(val loanId: String)
data class OpenOverdraftRequest(val requestedLimit: BigDecimal)
data class OverdraftAmountRequest(val amount: BigDecimal)
data class PostpaidCreditAmountRequest(val amount: BigDecimal)

@RestController
@RequestMapping("/api/v1/loans")
class LoansController(
    private val loansService: LoansService,
    private val idempotencyService: IdempotencyService,
    private val overdraftService: OverdraftService,
    private val postpaidCreditService: PostpaidCreditService,
    private val postpaidCreditPaymentReminderScheduler: PostpaidCreditPaymentReminderScheduler,
) {

    @GetMapping("/offers")
    fun getOffers(@RequestParam(required = false) lenderId: String?) =
        ResponseEntity.ok(mapOf("success" to true, "offers" to loansService.getOffers(lenderId)))

    @GetMapping("/lenders")
    fun getLenders() = ResponseEntity.ok(mapOf("success" to true, "lenders" to loansService.getLenders()))

    @GetMapping("/my-loans")
    fun getMyLoans(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "loans" to loansService.getMyLoans(currentUser.userId)))

    @PostMapping("/apply")
    fun apply(
        @RequestBody request: ApplyLoanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/apply", idempotencyKey, request) {
            val loan = loansService.applyForLoan(currentUser.userId, request.loanId, request.amount)
            200 to mapOf("success" to true, "message" to "Loan application submitted successfully", "loan" to loan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/repay")
    fun repay(
        @RequestBody request: RepayLoanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/repay", idempotencyKey, request) {
            val result = loansService.repayLoan(currentUser.userId, request.loanId, request.amount)
            200 to (mapOf("success" to true, "message" to "Loan repayment successful") + result)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real 대환대출 (loan refinancing) -- see LoansService.refinanceLoan's own doc comment.
    @PostMapping("/refinance")
    fun refinance(
        @RequestBody request: RefinanceLoanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/refinance", idempotencyKey, request) {
            val result = loansService.refinanceLoan(currentUser.userId, request.loanId)
            200 to (mapOf("success" to true, "message" to "Loan refinanced successfully") + result)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
    // OverdraftAccount.kt's own doc comment for the full sourced account.
    @PostMapping("/overdraft/open")
    fun openOverdraft(
        @RequestBody request: OpenOverdraftRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/overdraft/open", idempotencyKey, request) {
            val account = overdraftService.openOverdraft(currentUser.userId, request.requestedLimit)
            201 to mapOf("success" to true, "account" to account)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/overdraft")
    fun getMyOverdraft(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "account" to overdraftService.getMyOverdraft(currentUser.userId)))

    @PostMapping("/overdraft/draw")
    fun drawOverdraft(
        @RequestBody request: OverdraftAmountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/overdraft/draw", idempotencyKey, request) {
            val result = overdraftService.draw(currentUser.userId, request.amount)
            200 to (mapOf("success" to true) + result)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/overdraft/repay")
    fun repayOverdraft(
        @RequestBody request: OverdraftAmountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/overdraft/repay", idempotencyKey, request) {
            val result = overdraftService.repay(currentUser.userId, request.amount)
            200 to (mapOf("success" to true) + result)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line) -- see
    // PostpaidCreditLine.kt's own doc comment for the full sourced account.
    @PostMapping("/postpaid-credit/apply")
    fun applyForPostpaidCredit(
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/postpaid-credit/apply", idempotencyKey, currentUser.userId) {
            val line = postpaidCreditService.applyForPostpaidCredit(currentUser.userId)
            201 to mapOf("success" to true, "line" to line)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/postpaid-credit")
    fun getMyPostpaidCredit(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "line" to postpaidCreditService.getMyPostpaidCredit(currentUser.userId)))

    @PostMapping("/postpaid-credit/spend")
    fun spendPostpaidCredit(
        @RequestBody request: PostpaidCreditAmountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/postpaid-credit/spend", idempotencyKey, request) {
            val result = postpaidCreditService.spend(currentUser.userId, request.amount)
            200 to (mapOf("success" to true) + result)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/postpaid-credit/repay")
    fun repayPostpaidCredit(
        @RequestBody request: PostpaidCreditAmountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/postpaid-credit/repay", idempotencyKey, request) {
            val result = postpaidCreditService.repay(currentUser.userId, request.amount)
            200 to (mapOf("success" to true) + result)
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real postpaid credit payment-due-soon reminder manual trigger -- same "expose the
    // scheduler's own real logic as a callable endpoint" convention
    // MerchantCouponController.processExpiryReminders/SavingsController/InsuranceController
    // already establish, so a real line's real cycleDueAt can be verified without waiting
    // actual wall-clock days for it to enter the reminder window.
    @PostMapping("/postpaid-credit/process-payment-reminders")
    fun processPostpaidCreditPaymentReminders(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = postpaidCreditPaymentReminderScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    @ExceptionHandler(PostpaidCreditAlreadyOpenException::class)
    fun handlePostpaidAlreadyOpen(ex: PostpaidCreditAlreadyOpenException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("POSTPAID_CREDIT_ALREADY_OPEN", ex.message ?: "Conflict"))

    @ExceptionHandler(PostpaidCreditNotActiveException::class)
    fun handlePostpaidNotActive(ex: PostpaidCreditNotActiveException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("POSTPAID_CREDIT_NOT_ACTIVE", ex.message ?: "Not found"))

    @ExceptionHandler(PostpaidCreditLimitExceededException::class)
    fun handlePostpaidLimitExceeded(ex: PostpaidCreditLimitExceededException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("POSTPAID_CREDIT_LIMIT_EXCEEDED", ex.message ?: "Unprocessable"))

    @ExceptionHandler(PostpaidCreditInvalidAmountException::class)
    fun handlePostpaidInvalidAmount(ex: PostpaidCreditInvalidAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(PostpaidCreditNoAccountException::class)
    fun handlePostpaidNoAccount(ex: PostpaidCreditNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(PostpaidCreditSuspendedException::class)
    fun handlePostpaidSuspended(ex: PostpaidCreditSuspendedException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("POSTPAID_CREDIT_SUSPENDED", ex.message ?: "Forbidden"))

    @ExceptionHandler(OverdraftAlreadyActiveException::class)
    fun handleOverdraftAlreadyActive(ex: OverdraftAlreadyActiveException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("OVERDRAFT_ALREADY_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(OverdraftLimitInvalidException::class)
    fun handleOverdraftLimitInvalid(ex: OverdraftLimitInvalidException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_OVERDRAFT_LIMIT", ex.message ?: "Bad request"))

    @ExceptionHandler(OverdraftApplicationDeclinedException::class)
    fun handleOverdraftDeclined(ex: OverdraftApplicationDeclinedException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("OVERDRAFT_APPLICATION_DECLINED", ex.message ?: "Declined"))

    @ExceptionHandler(OverdraftNotActiveException::class)
    fun handleOverdraftNotActive(ex: OverdraftNotActiveException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("OVERDRAFT_NOT_ACTIVE", ex.message ?: "Not found"))

    @ExceptionHandler(OverdraftLimitExceededException::class)
    fun handleOverdraftLimitExceeded(ex: OverdraftLimitExceededException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("OVERDRAFT_LIMIT_EXCEEDED", ex.message ?: "Unprocessable"))

    @ExceptionHandler(OverdraftInvalidAmountException::class)
    fun handleOverdraftInvalidAmount(ex: OverdraftInvalidAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(OverdraftNoAccountException::class)
    fun handleOverdraftNoAccount(ex: OverdraftNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(NoBetterRateAvailableException::class)
    fun handleNoBetterRate(ex: NoBetterRateAvailableException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("NO_BETTER_RATE_AVAILABLE", ex.message ?: "No better rate available"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(LoanOfferNotFoundException::class)
    fun handleOfferNotFound(ex: LoanOfferNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LOAN_OFFER_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(LoanNotFoundException::class)
    fun handleNotFound(ex: LoanNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LOAN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(NoAccountException::class)
    fun handleNoAccount(ex: NoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(BusinessAccountRequiredException::class)
    fun handleBusinessAccountRequired(ex: BusinessAccountRequiredException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("BUSINESS_ACCOUNT_REQUIRED", ex.message ?: "Conflict"))

    @ExceptionHandler(LoanNotOwnedException::class)
    fun handleNotOwned(ex: LoanNotOwnedException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("LOAN_NOT_OWNED", ex.message ?: "Forbidden"))

    @ExceptionHandler(LoanAlreadyPaidException::class)
    fun handleAlreadyPaid(ex: LoanAlreadyPaidException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("LOAN_ALREADY_PAID", ex.message ?: "Conflict"))

    @ExceptionHandler(LoanAmountInvalidException::class)
    fun handleInvalidAmount(ex: LoanAmountInvalidException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INVALID_LOAN_AMOUNT", ex.message ?: "Invalid amount"))

    @ExceptionHandler(LoanApplicationDeclinedException::class)
    fun handleDeclined(ex: LoanApplicationDeclinedException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("LOAN_APPLICATION_DECLINED", ex.message ?: "Declined"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))
}
