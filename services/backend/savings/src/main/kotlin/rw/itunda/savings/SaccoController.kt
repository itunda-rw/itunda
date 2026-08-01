package rw.itunda.savings

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
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class SaccoAmountRequest(val amount: BigDecimal)

// Real Umurenge SACCO-style shares & dividends -- see SaccoService's own doc comment
// for the full sourced account (Rwanda's real 416-sector government-backed
// cooperative savings model). Genuinely distinct from every Toss/Kakao/Naver/
// Coupang-sourced feature in this backend and from Ikimina (rotating-pot ROSCA).
@RestController
@RequestMapping("/api/v1/sacco")
class SaccoController(
    private val saccoService: SaccoService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/shares/buy")
    fun buyShares(
        @RequestBody request: SaccoAmountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/sacco/shares/buy", idempotencyKey, currentUser.userId) {
            val view = saccoService.buyShares(currentUser.userId, request.amount)
            200 to mapOf("success" to true, "shareholding" to view.shareholding, "currentValue" to view.currentValue)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/shares/redeem")
    fun redeemShares(
        @RequestBody request: SaccoAmountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/sacco/shares/redeem", idempotencyKey, currentUser.userId) {
            val view = saccoService.redeemShares(currentUser.userId, request.amount)
            200 to mapOf("success" to true, "shareholding" to view.shareholding, "currentValue" to view.currentValue)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/shares/me")
    fun myShareholding(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val view = saccoService.getMyShareholding(currentUser.userId)
        return ResponseEntity.ok(mapOf("success" to true, "shareholding" to view?.shareholding, "currentValue" to view?.currentValue))
    }

    @GetMapping("/dividends/me")
    fun myDividendHistory(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "payouts" to saccoService.getMyDividendHistory(currentUser.userId)))

    @ExceptionHandler(SaccoNoWalletException::class)
    fun handleNoWallet(ex: SaccoNoWalletException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SaccoNoShareholdingException::class)
    fun handleNoShareholding(ex: SaccoNoShareholdingException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("SACCO_NO_SHAREHOLDING", ex.message ?: "Not found"))

    @ExceptionHandler(SaccoInsufficientSharesException::class)
    fun handleInsufficientShares(ex: SaccoInsufficientSharesException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("SACCO_INSUFFICIENT_SHARES", ex.message ?: "Unprocessable"))

    @ExceptionHandler(SaccoNoSharesOutstandingException::class)
    fun handleNoSharesOutstanding(ex: SaccoNoSharesOutstandingException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("SACCO_NO_SHARES_OUTSTANDING", ex.message ?: "Unprocessable"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMIT_EXCEEDED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REQUEST", ex.message ?: "Invalid request"))
}
