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
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class ApplyLoanRequest(val loanId: String, val amount: BigDecimal)
data class RepayLoanRequest(val loanId: String, val amount: BigDecimal)

@RestController
@RequestMapping("/api/v1/loans")
class LoansController(private val loansService: LoansService, private val idempotencyService: IdempotencyService) {

    @GetMapping("/offers")
    fun getOffers() = ResponseEntity.ok(mapOf("success" to true, "offers" to loansService.getOffers()))

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

    @ExceptionHandler(NoWalletException::class)
    fun handleNoWallet(ex: NoWalletException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

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

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))
}
