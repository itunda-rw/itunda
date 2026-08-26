package rw.itunda.transit.web

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
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
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.domain.TransitTrip
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.transit.TransitInsufficientBalanceException
import rw.itunda.transit.TransitInvalidAmountException
import rw.itunda.transit.TransitInvalidFareException
import rw.itunda.transit.TransitInvalidOperatorException
import rw.itunda.transit.TransitNoAccountException
import rw.itunda.transit.TransitService
import java.math.BigDecimal

data class TopUpTransitRequest(val amount: BigDecimal)
data class TapFareRequest(val operator: String, val fare: BigDecimal)

@RestController
@RequestMapping("/api/v1/transit")
class TransitController(
    private val transitService: TransitService,
    private val idempotencyService: IdempotencyService,
) {
    @GetMapping("/balance")
    fun getMyBalance(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "balance" to transitService.getMyBalance(currentUser.userId)))

    @GetMapping("/trips")
    fun getTrips(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Map<String, Any?>> {
        val result: Page<TransitTrip> = transitService.getMyTrips(currentUser.userId, PageRequest.of(page, size.coerceIn(1, 100)))
        return ResponseEntity.ok(mapOf("success" to true, "trips" to result.content, "totalElements" to result.totalElements, "totalPages" to result.totalPages))
    }

    @PostMapping("/topup")
    fun topUp(
        @RequestBody request: TopUpTransitRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/transit/topup", idempotencyKey, request) {
            200 to mapOf("success" to true, "balance" to transitService.topUp(currentUser.userId, request.amount))
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/tap")
    fun tap(
        @RequestBody request: TapFareRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/transit/tap", idempotencyKey, request) {
            val result = transitService.tapFare(currentUser.userId, request.operator, request.fare)
            201 to mapOf("success" to true, "trip" to result.trip, "balance" to result.balance)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(TransitNoAccountException::class)
    fun handleNoAccount(ex: TransitNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("TRANSIT_NO_ACCOUNT", ex.message ?: "Not found"))

    @ExceptionHandler(TransitInvalidAmountException::class)
    fun handleInvalidAmount(ex: TransitInvalidAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(TransitInvalidOperatorException::class)
    fun handleInvalidOperator(ex: TransitInvalidOperatorException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_TRANSIT_OPERATOR", ex.message ?: "Bad request"))

    @ExceptionHandler(TransitInvalidFareException::class)
    fun handleInvalidFare(ex: TransitInvalidFareException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_TRANSIT_FARE", ex.message ?: "Bad request"))

    @ExceptionHandler(TransitInsufficientBalanceException::class)
    fun handleInsufficientBalance(ex: TransitInsufficientBalanceException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("TRANSIT_INSUFFICIENT_BALANCE", ex.message ?: "Conflict"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Conflict"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMIT_EXCEEDED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
