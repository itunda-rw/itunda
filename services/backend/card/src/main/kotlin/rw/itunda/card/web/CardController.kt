package rw.itunda.card.web

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.card.CardAlreadyIssuedException
import rw.itunda.card.CardDailyLimitExceededException
import rw.itunda.card.CardFrozenException
import rw.itunda.card.CardInvalidAmountException
import rw.itunda.card.CardInvalidLimitException
import rw.itunda.card.CardMonthlyLimitExceededException
import rw.itunda.card.CardNoAccountException
import rw.itunda.card.CardNotFoundException
import rw.itunda.card.CardService
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class SetCardLimitsRequest(val dailyLimit: BigDecimal, val monthlyLimit: BigDecimal)
data class ChargeCardRequest(val amount: BigDecimal, val merchantName: String)

@RestController
@RequestMapping("/api/v1/card")
class CardController(
    private val cardService: CardService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/issue")
    fun issue(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val card = cardService.issueCard(currentUser.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "card" to cardService.getMyCard(currentUser.userId), "cardId" to card.id))
    }

    @GetMapping("/my-card")
    fun getMyCard(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "card" to cardService.getMyCard(currentUser.userId)))

    @GetMapping("/transactions")
    fun getTransactions(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Map<String, Any?>> {
        val result: Page<*> = cardService.getMyTransactions(currentUser.userId, PageRequest.of(page, size.coerceIn(1, 100)))
        return ResponseEntity.ok(mapOf("success" to true, "transactions" to result.content, "totalElements" to result.totalElements, "totalPages" to result.totalPages))
    }

    @PutMapping("/limits")
    fun setLimits(@RequestBody request: SetCardLimitsRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "card" to cardService.setLimits(currentUser.userId, request.dailyLimit, request.monthlyLimit)))

    @PostMapping("/freeze")
    fun freeze(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "card" to cardService.freeze(currentUser.userId)))

    @PostMapping("/unfreeze")
    fun unfreeze(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "card" to cardService.unfreeze(currentUser.userId)))

    @PostMapping("/charge")
    fun charge(
        @RequestBody request: ChargeCardRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/card/charge", idempotencyKey, request) {
            val result = cardService.chargeWithCard(currentUser.userId, request.amount, request.merchantName)
            201 to mapOf("success" to true, "transaction" to result.transaction, "card" to result.card)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(CardAlreadyIssuedException::class)
    fun handleAlreadyIssued(ex: CardAlreadyIssuedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CARD_ALREADY_ISSUED", ex.message ?: "Conflict"))

    @ExceptionHandler(CardNotFoundException::class)
    fun handleNotFound(ex: CardNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CARD_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(CardNoAccountException::class)
    fun handleNoAccount(ex: CardNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CARD_NO_WALLET", ex.message ?: "Not found"))

    @ExceptionHandler(CardFrozenException::class)
    fun handleFrozen(ex: CardFrozenException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CARD_FROZEN", ex.message ?: "Conflict"))

    @ExceptionHandler(CardInvalidLimitException::class)
    fun handleInvalidLimit(ex: CardInvalidLimitException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CARD_LIMIT", ex.message ?: "Bad request"))

    @ExceptionHandler(CardInvalidAmountException::class)
    fun handleInvalidAmount(ex: CardInvalidAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(CardDailyLimitExceededException::class)
    fun handleDailyLimit(ex: CardDailyLimitExceededException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CARD_DAILY_LIMIT_EXCEEDED", ex.message ?: "Conflict"))

    @ExceptionHandler(CardMonthlyLimitExceededException::class)
    fun handleMonthlyLimit(ex: CardMonthlyLimitExceededException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CARD_MONTHLY_LIMIT_EXCEEDED", ex.message ?: "Conflict"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("WALLET_FROZEN", ex.message ?: "Conflict"))

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
