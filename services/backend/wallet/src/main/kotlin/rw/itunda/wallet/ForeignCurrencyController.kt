package rw.itunda.wallet

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import java.math.BigDecimal

data class OpenForeignWalletRequest(val currency: String)
data class ConvertCurrencyRequest(val fromCurrency: String, val toCurrency: String, val amount: BigDecimal)
data class SetRateAlertRequest(val fromCurrency: String, val toCurrency: String, val targetRate: Double, val direction: String)

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent -- see
// ForeignCurrencyWalletService's own doc comment for the full account. Not
// money-moving via an external rail (conversion stays entirely between the caller's own
// wallets), so no Idempotency-Key requirement, same discipline non-external-rail writes
// elsewhere in this codebase already follow (e.g. MerchantProductController).
@RestController
@RequestMapping("/api/v1/wallet/foreign-currency")
class ForeignCurrencyController(
    private val foreignCurrencyWalletService: ForeignCurrencyWalletService,
) {
    @PostMapping("/wallets")
    fun openWallet(
        @RequestBody request: OpenForeignWalletRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val wallet = foreignCurrencyWalletService.openWallet(currentUser.userId, request.currency)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "wallet" to wallet))
    }

    @GetMapping("/wallets")
    fun getMyWallets(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "wallets" to foreignCurrencyWalletService.getMyWallets(currentUser.userId)))

    @GetMapping("/rate")
    fun getRate(@RequestParam from: String, @RequestParam to: String): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "from" to from.uppercase(), "to" to to.uppercase(), "rate" to foreignCurrencyWalletService.getRate(from, to)))

    @PostMapping("/convert")
    fun convert(
        @RequestBody request: ConvertCurrencyRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val conversion = foreignCurrencyWalletService.convert(currentUser.userId, request.fromCurrency, request.toCurrency, request.amount)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "conversion" to conversion))
    }

    @GetMapping("/conversions")
    fun getMyConversions(
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val page = foreignCurrencyWalletService.getMyConversions(currentUser.userId, pageable)
        return ResponseEntity.ok(mapOf("success" to true, "conversions" to page.content) + pageMeta(page))
    }

    // Real Toss 외환 환율 알림 (exchange rate alert) -- see
    // ForeignCurrencyWalletService.setRateAlert's own doc comment.
    @PostMapping("/rate-alert")
    fun setRateAlert(
        @RequestBody request: SetRateAlertRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val alert = foreignCurrencyWalletService.setRateAlert(currentUser.userId, request.fromCurrency, request.toCurrency, request.targetRate, request.direction)
        return ResponseEntity.ok(mapOf("success" to true, "alert" to alert))
    }

    @DeleteMapping("/rate-alert")
    fun clearRateAlert(
        @RequestParam fromCurrency: String,
        @RequestParam toCurrency: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        foreignCurrencyWalletService.clearRateAlert(currentUser.userId, fromCurrency, toCurrency)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @GetMapping("/rate-alerts")
    fun getMyRateAlerts(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "alerts" to foreignCurrencyWalletService.getMyRateAlerts(currentUser.userId)))

    @ExceptionHandler(UnsupportedCurrencyException::class)
    fun handleUnsupportedCurrency(ex: UnsupportedCurrencyException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("UNSUPPORTED_CURRENCY", ex.message ?: "Bad request"))

    @ExceptionHandler(ForeignCurrencyWalletAlreadyExistsException::class)
    fun handleAlreadyExists(ex: ForeignCurrencyWalletAlreadyExistsException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("FOREIGN_WALLET_ALREADY_EXISTS", ex.message ?: "Conflict"))

    @ExceptionHandler(ForeignCurrencyWalletNotFoundException::class)
    fun handleNotFound(ex: ForeignCurrencyWalletNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("FOREIGN_WALLET_NOT_FOUND", ex.message ?: "Not found"))

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

    @ExceptionHandler(WalletNotFoundException::class)
    fun handleWalletNotFound(ex: WalletNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))
}
