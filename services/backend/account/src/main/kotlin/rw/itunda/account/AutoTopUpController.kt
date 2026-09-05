package rw.itunda.account

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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

data class ConfigureAutoTopUpRequest(
    val linkedAccountId: String,
    val thresholdAmount: BigDecimal,
    val topUpAmount: BigDecimal,
    val dailyTriggerCap: Int = 3,
    val enabled: Boolean = true,
)

// Real Naver Pay Money 자동충전 (auto-charge) equivalent -- see AutoTopUpService's own
// doc comment. Normal itunda-user JWT gate, same as every other user-facing feature.
@RestController
@RequestMapping("/api/v1/account/{accountId}/auto-topup")
class AutoTopUpController(
    private val autoTopUpService: AutoTopUpService,
    private val idempotencyService: IdempotencyService,
) {

    @GetMapping
    fun getSetting(@PathVariable accountId: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "setting" to autoTopUpService.getSetting(currentUser.userId, accountId)))

    @PutMapping
    fun configure(
        @PathVariable accountId: String,
        @RequestBody request: ConfigureAutoTopUpRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val setting = autoTopUpService.configure(
            currentUser.userId, accountId, request.linkedAccountId, request.thresholdAmount,
            request.topUpAmount, request.dailyTriggerCap, request.enabled,
        )
        return ResponseEntity.ok(mapOf("success" to true, "setting" to setting))
    }

    // Real, exercisable manual/demo trigger -- kept even after AutoTopUpScheduler's own
    // real background automation closed this feature's original gap, since it's still
    // useful for an on-demand check.
    // Correction, 2026-09-05 (see feedback_idempotency_key_sweep memory): the "idempotent
    // no-op if real conditions aren't met" reasoning this used to give is incomplete --
    // evaluateAndTopUp's own threshold check only blocks a retry once the balance has
    // already crossed back above the threshold. If topUpAmount is small relative to
    // thresholdAmount, a lost-response retry can re-trigger a real second top-up before
    // that happens, bounded only by dailyTriggerCap (a real but silent double-charge
    // risk, not a confusing error).
    @PostMapping("/trigger")
    fun trigger(
        @PathVariable accountId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/account/$accountId/auto-topup/trigger", idempotencyKey, accountId) {
            val result = autoTopUpService.evaluateAndTopUp(currentUser.userId, accountId)
            200 to mapOf("success" to true, "triggered" to result.triggered, "reason" to result.reason)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFound(ex: AccountNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(AutoTopUpLinkedAccountNotFoundException::class)
    fun handleLinkedAccountNotFound(ex: AutoTopUpLinkedAccountNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LINKED_ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(AutoTopUpLinkedAccountNotLinkedException::class)
    fun handleLinkedAccountNotLinked(ex: AutoTopUpLinkedAccountNotLinkedException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("LINKED_ACCOUNT_NOT_LINKED", ex.message ?: "Not linked"))

    @ExceptionHandler(AutoTopUpInvalidAmountException::class)
    fun handleInvalidAmount(ex: AutoTopUpInvalidAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(AutoTopUpSettingNotFoundException::class)
    fun handleSettingNotFound(ex: AutoTopUpSettingNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("AUTO_TOPUP_SETTING_NOT_FOUND", ex.message ?: "Not found"))

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
