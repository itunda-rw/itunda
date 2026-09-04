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
import rw.itunda.card.CardClosedException
import rw.itunda.card.CardDailyLimitExceededException
import rw.itunda.card.CardFrozenException
import rw.itunda.card.CardIncorrectCredentialException
import rw.itunda.card.CardInvalidAmountException
import rw.itunda.card.CardInvalidDesignException
import rw.itunda.card.CardInvalidLimitException
import rw.itunda.card.CardInvalidPinException
import rw.itunda.card.CardLostException
import rw.itunda.card.CardMonthlyLimitExceededException
import rw.itunda.card.CardNoAccountException
import rw.itunda.card.CardNotEligibleForReissueException
import rw.itunda.card.CardNotFoundException
import rw.itunda.card.CardService
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.domain.DebitCardDesign
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class SetCardLimitsRequest(val dailyLimit: BigDecimal, val monthlyLimit: BigDecimal)
data class ChargeCardRequest(val amount: BigDecimal, val merchantName: String)
data class SetCardPinRequest(val newPin: String, val currentCredential: String)

// `design` is nullable/optional (2026-08-27) so an old, not-yet-updated client that
// still calls POST /issue with no body at all keeps working exactly as before --
// falls back to DebitCardDesign.DEFAULT, the same real default the Flyway migration
// backfilled onto every card issued before this feature existed.
data class IssueCardRequest(val design: String? = null)

@RestController
@RequestMapping("/api/v1/card")
class CardController(
    private val cardService: CardService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/issue")
    fun issue(
        @RequestBody(required = false) request: IssueCardRequest?,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val card = cardService.issueCard(currentUser.userId, request?.design ?: DebitCardDesign.DEFAULT)
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

    @PostMapping("/report-lost")
    fun reportLost(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "card" to cardService.reportLost(currentUser.userId)))

    @PostMapping("/close")
    fun close(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "card" to cardService.closeCard(currentUser.userId)))

    @PostMapping("/reissue")
    fun reissue(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "card" to cardService.reissue(currentUser.userId)))

    @PutMapping("/pin")
    fun setPin(@RequestBody request: SetCardPinRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "card" to cardService.setPin(currentUser.userId, request.newPin, request.currentCredential)))

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
    fun handleNoAccount(ex: CardNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CARD_NO_ACCOUNT", ex.message ?: "Not found"))

    @ExceptionHandler(CardFrozenException::class)
    fun handleFrozen(ex: CardFrozenException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CARD_FROZEN", ex.message ?: "Conflict"))

    @ExceptionHandler(CardInvalidLimitException::class)
    fun handleInvalidLimit(ex: CardInvalidLimitException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CARD_LIMIT", ex.message ?: "Bad request"))

    @ExceptionHandler(CardInvalidDesignException::class)
    fun handleInvalidDesign(ex: CardInvalidDesignException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CARD_DESIGN", ex.message ?: "Bad request"))

    @ExceptionHandler(CardLostException::class)
    fun handleLost(ex: CardLostException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CARD_LOST", ex.message ?: "Conflict"))

    @ExceptionHandler(CardClosedException::class)
    fun handleClosed(ex: CardClosedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CARD_CLOSED", ex.message ?: "Conflict"))

    @ExceptionHandler(CardNotEligibleForReissueException::class)
    fun handleNotEligibleForReissue(ex: CardNotEligibleForReissueException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CARD_NOT_ELIGIBLE_FOR_REISSUE", ex.message ?: "Conflict"))

    @ExceptionHandler(CardInvalidPinException::class)
    fun handleInvalidPin(ex: CardInvalidPinException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CARD_PIN", ex.message ?: "Bad request"))

    @ExceptionHandler(CardIncorrectCredentialException::class)
    fun handleIncorrectCredential(ex: CardIncorrectCredentialException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("INCORRECT_CREDENTIAL", ex.message ?: "Forbidden"))

    @ExceptionHandler(CardInvalidAmountException::class)
    fun handleInvalidAmount(ex: CardInvalidAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(CardDailyLimitExceededException::class)
    fun handleDailyLimit(ex: CardDailyLimitExceededException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CARD_DAILY_LIMIT_EXCEEDED", ex.message ?: "Conflict"))

    @ExceptionHandler(CardMonthlyLimitExceededException::class)
    fun handleMonthlyLimit(ex: CardMonthlyLimitExceededException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CARD_MONTHLY_LIMIT_EXCEEDED", ex.message ?: "Conflict"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Conflict"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Conflict"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
