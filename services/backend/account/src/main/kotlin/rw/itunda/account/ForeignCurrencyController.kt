package rw.itunda.account

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
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
import rw.itunda.core.web.pageMeta
import java.math.BigDecimal

data class OpenForeignAccountRequest(val currency: String)
data class ConvertCurrencyRequest(val fromCurrency: String, val toCurrency: String, val amount: BigDecimal)
data class SetRateAlertRequest(val fromCurrency: String, val toCurrency: String, val targetRate: Double, val direction: String)

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent -- see
// ForeignCurrencyAccountService's own doc comment for the full account.
// Correction, 2026-09-05 (see feedback_idempotency_key_sweep memory): the
// "not money-moving via an external rail, so no Idempotency-Key requirement"
// reasoning below missed two real risks. (1) openAccount is guarded by
// ForeignCurrencyAccountAlreadyExistsException, so a lost-response retry hits that
// guard regardless of whether money moved. (2) convert has NO guard at all and
// genuinely moves real money between the caller's own accounts -- a lost-response
// retry would silently execute the SAME conversion twice, not just show a
// confusing error. Both are now Idempotency-Key protected.
@RestController
@RequestMapping("/api/v1/account/foreign-currency")
class ForeignCurrencyController(
    private val foreignCurrencyAccountService: ForeignCurrencyAccountService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping("/accounts")
    fun openAccount(
        @RequestBody request: OpenForeignAccountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/account/foreign-currency/accounts", idempotencyKey, request) {
            val account = foreignCurrencyAccountService.openAccount(currentUser.userId, request.currency)
            HttpStatus.CREATED.value() to mapOf("success" to true, "account" to account)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/accounts")
    fun getMyAccounts(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "accounts" to foreignCurrencyAccountService.getMyAccounts(currentUser.userId)))

    @GetMapping("/rate")
    fun getRate(@RequestParam from: String, @RequestParam to: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "from" to from.uppercase(), "to" to to.uppercase(), "rate" to foreignCurrencyAccountService.getRate(from, to)))

    @PostMapping("/convert")
    fun convert(
        @RequestBody request: ConvertCurrencyRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/account/foreign-currency/convert", idempotencyKey, request) {
            val conversion = foreignCurrencyAccountService.convert(currentUser.userId, request.fromCurrency, request.toCurrency, request.amount)
            HttpStatus.CREATED.value() to mapOf("success" to true, "conversion" to conversion)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/conversions")
    fun getMyConversions(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = foreignCurrencyAccountService.getMyConversions(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "conversions" to page.content) + pageMeta(page))
    }

    // Real Toss 외환 환율 알림 (exchange rate alert) -- see
    // ForeignCurrencyAccountService.setRateAlert's own doc comment.
    @PostMapping("/rate-alert")
    fun setRateAlert(
        @RequestBody request: SetRateAlertRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val alert = foreignCurrencyAccountService.setRateAlert(currentUser.userId, request.fromCurrency, request.toCurrency, request.targetRate, request.direction)
        return ResponseEntity.ok(mapOf("success" to true, "alert" to alert))
    }

    @DeleteMapping("/rate-alert")
    fun clearRateAlert(
        @RequestParam fromCurrency: String,
        @RequestParam toCurrency: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        foreignCurrencyAccountService.clearRateAlert(currentUser.userId, fromCurrency, toCurrency)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/rate-alerts")
    fun getMyRateAlerts(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "alerts" to foreignCurrencyAccountService.getMyRateAlerts(currentUser.userId)))

    @ExceptionHandler(UnsupportedCurrencyException::class)
    fun handleUnsupportedCurrency(ex: UnsupportedCurrencyException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("UNSUPPORTED_CURRENCY", ex.message ?: "Bad request"))

    @ExceptionHandler(ForeignCurrencyAccountAlreadyExistsException::class)
    fun handleAlreadyExists(ex: ForeignCurrencyAccountAlreadyExistsException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("FOREIGN_ACCOUNT_ALREADY_EXISTS", ex.message ?: "Conflict"))

    @ExceptionHandler(ForeignCurrencyAccountNotFoundException::class)
    fun handleNotFound(ex: ForeignCurrencyAccountNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("FOREIGN_ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidConversionException::class)
    fun handleInvalidConversion(ex: InvalidConversionException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CONVERSION", ex.message ?: "Bad request"))

    @ExceptionHandler(ExchangeRateUnavailableException::class)
    fun handleRateUnavailable(ex: ExchangeRateUnavailableException) =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiError("EXCHANGE_RATE_UNAVAILABLE", ex.message ?: "Service unavailable"))

    @ExceptionHandler(InvalidRateAlertException::class)
    fun handleInvalidRateAlert(ex: InvalidRateAlertException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_RATE_ALERT", ex.message ?: "Bad request"))

    @ExceptionHandler(ExchangeRateAlertNotFoundException::class)
    fun handleRateAlertNotFound(ex: ExchangeRateAlertNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RATE_ALERT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFound(ex: AccountNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
