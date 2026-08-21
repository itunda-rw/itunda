package rw.itunda.merchant

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
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class MoveBusinessMoneyRequest(val amount: BigDecimal)

// Real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent -- see
// MerchantBusinessAccountService's own doc comment. Move actions are real money
// movement (even though it's the same person on both sides), so both require a real
// Idempotency-Key, same discipline every other money-moving write in this codebase uses.
@RestController
@RequestMapping("/api/v1/merchant/business-account")
class MerchantBusinessAccountController(
    private val merchantBusinessAccountService: MerchantBusinessAccountService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping
    fun openBusinessAccount(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val account = merchantBusinessAccountService.openBusinessAccount(currentUser.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "account" to account))
    }

    @GetMapping
    fun getBusinessAccount(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "account" to merchantBusinessAccountService.getBusinessAccount(currentUser.userId)))

    @GetMapping("/transactions")
    fun getBusinessTransactions(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "transactions" to merchantBusinessAccountService.getBusinessTransactions(currentUser.userId)))

    @PostMapping("/move-to-business")
    fun moveToBusiness(
        @RequestBody request: MoveBusinessMoneyRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/business-account/move-to-business", idempotencyKey, request) {
            200 to mapOf("success" to true, "account" to merchantBusinessAccountService.moveToBusiness(currentUser.userId, request.amount))
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/move-to-personal")
    fun moveToPersonal(
        @RequestBody request: MoveBusinessMoneyRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/merchant/business-account/move-to-personal", idempotencyKey, request) {
            200 to mapOf("success" to true, "account" to merchantBusinessAccountService.moveToPersonal(currentUser.userId, request.amount))
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(ex: MerchantNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("MERCHANT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(BusinessAccountAlreadyExistsException::class)
    fun handleAlreadyExists(ex: BusinessAccountAlreadyExistsException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("BUSINESS_ACCOUNT_ALREADY_EXISTS", ex.message ?: "Conflict"))

    @ExceptionHandler(BusinessAccountNotFoundException::class)
    fun handleNotFound(ex: BusinessAccountNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("BUSINESS_ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidMoveAmountException::class)
    fun handleInvalidAmount(ex: InvalidMoveAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_MOVE_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(MerchantNoAccountException::class)
    fun handleNoAccount(ex: MerchantNoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
