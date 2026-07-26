package rw.itunda.wallet

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
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
@RequestMapping("/api/v1/wallet/{walletId}/auto-topup")
class AutoTopUpController(private val autoTopUpService: AutoTopUpService) {

    @GetMapping
    fun getSetting(@PathVariable walletId: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "setting" to autoTopUpService.getSetting(currentUser.userId, walletId)))

    @PutMapping
    fun configure(
        @PathVariable walletId: String,
        @RequestBody request: ConfigureAutoTopUpRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val setting = autoTopUpService.configure(
            currentUser.userId, walletId, request.linkedAccountId, request.thresholdAmount,
            request.topUpAmount, request.dailyTriggerCap, request.enabled,
        )
        return ResponseEntity.ok(mapOf("success" to true, "setting" to setting))
    }

    // Real, exercisable manual/demo trigger -- kept even after AutoTopUpScheduler's own
    // real background automation closed this feature's original gap, since it's still
    // useful for an on-demand check. Idempotent no-op (200, triggered: false) if real
    // conditions (threshold, daily cap, linked-account status) aren't met.
    @PostMapping("/trigger")
    fun trigger(@PathVariable walletId: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true) + autoTopUpService.evaluateAndTopUp(currentUser.userId, walletId).let {
            mapOf("triggered" to it.triggered, "reason" to it.reason)
        })

    @ExceptionHandler(WalletNotFoundException::class)
    fun handleWalletNotFound(ex: WalletNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(WalletNotOwnedException::class)
    fun handleWalletNotOwned(ex: WalletNotOwnedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_NOT_OWNED", ex.message ?: "Forbidden"))

    @ExceptionHandler(AutoTopUpLinkedAccountNotFoundException::class)
    fun handleLinkedAccountNotFound(ex: AutoTopUpLinkedAccountNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("LINKED_ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(AutoTopUpLinkedAccountNotOwnedException::class)
    fun handleLinkedAccountNotOwned(ex: AutoTopUpLinkedAccountNotOwnedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("LINKED_ACCOUNT_NOT_OWNED", ex.message ?: "Forbidden"))

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

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))
}
