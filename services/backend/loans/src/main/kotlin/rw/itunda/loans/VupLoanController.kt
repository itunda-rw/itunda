package rw.itunda.loans

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
import rw.itunda.core.domain.VupLoanPurpose
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class ApplyForVupLoanRequest(val declaredUbudeheCategory: Int, val purpose: VupLoanPurpose, val amount: BigDecimal)
data class RepayVupLoanRequest(val amount: BigDecimal)

// Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services micro-loan --
// see VupLoanService's own doc comment for the full sourced account. Sourced beyond
// this session's usual Toss/Kakao/Naver/Coupang reference ecosystems.
@RestController
@RequestMapping("/api/v1/loans/vup")
class VupLoanController(
    private val vupLoanService: VupLoanService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/apply")
    fun apply(@RequestBody request: ApplyForVupLoanRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val loan = vupLoanService.applyForLoan(currentUser.userId, request.declaredUbudeheCategory, request.purpose, request.amount)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "loan" to loan))
    }

    @PostMapping("/{loanId}/disburse")
    fun disburse(
        @PathVariable loanId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/vup/$loanId/disburse", idempotencyKey, currentUser.userId) {
            val loan = vupLoanService.disburse(currentUser.userId, loanId)
            200 to mapOf("success" to true, "loan" to loan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/{loanId}/repay")
    fun repay(
        @PathVariable loanId: String,
        @RequestBody request: RepayVupLoanRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/loans/vup/$loanId/repay", idempotencyKey, request) {
            val loan = vupLoanService.repay(currentUser.userId, loanId, request.amount)
            200 to mapOf("success" to true, "loan" to loan)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/my")
    fun getMyLoans(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "loans" to vupLoanService.getMyLoans(currentUser.userId)))

    @GetMapping("/eligibility")
    fun getEligibility(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true) + vupLoanService.getEligibility(currentUser.userId))

    @GetMapping("/{loanId}")
    fun getLoan(@PathVariable loanId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "loan" to vupLoanService.getLoan(currentUser.userId, loanId)))

    @ExceptionHandler(IneligibleUbudeheCategoryException::class)
    fun handleIneligibleCategory(ex: IneligibleUbudeheCategoryException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INELIGIBLE_UBUDEHE_CATEGORY", ex.message ?: "Bad request"))

    @ExceptionHandler(InvalidVupLoanAmountException::class)
    fun handleInvalidAmount(ex: InvalidVupLoanAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_VUP_LOAN_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(VupLoanInvalidRepayAmountException::class)
    fun handleInvalidRepayAmount(ex: VupLoanInvalidRepayAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REPAY_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(VupLoanAlreadyActiveException::class)
    fun handleAlreadyActive(ex: VupLoanAlreadyActiveException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("VUP_LOAN_ALREADY_ACTIVE", ex.message ?: "Conflict"))

    @ExceptionHandler(VupLoanNotFoundException::class)
    fun handleNotFound(ex: VupLoanNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("VUP_LOAN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(VupLoanNoAccountException::class)
    fun handleNoAccount(ex: VupLoanNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(VupLoanNotRequestedException::class)
    fun handleNotRequested(ex: VupLoanNotRequestedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("VUP_LOAN_NOT_REQUESTED", ex.message ?: "Conflict"))

    @ExceptionHandler(VupLoanNotRepayableException::class)
    fun handleNotRepayable(ex: VupLoanNotRepayableException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("VUP_LOAN_NOT_REPAYABLE", ex.message ?: "Conflict"))

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
