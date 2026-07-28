package rw.itunda.wallet

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class DepositMiniWalletRequest(val amount: BigDecimal)

// Real KakaoBank mini-style capped starter wallet -- see MiniWalletService's own doc
// comment. Normal itunda-user JWT gate, same as every other user-facing feature.
@RestController
@RequestMapping("/api/v1/wallet/mini")
class MiniWalletController(private val miniWalletService: MiniWalletService) {

    @PostMapping("/open")
    fun open(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val wallet = miniWalletService.openMiniWallet(currentUser.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "wallet" to wallet))
    }

    @PostMapping("/deposit")
    fun deposit(
        @RequestBody request: DepositMiniWalletRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val result = miniWalletService.deposit(currentUser.userId, request.amount)
        return ResponseEntity.ok(mapOf("success" to true) + result)
    }

    @ExceptionHandler(WalletNotFoundException::class)
    fun handleWalletNotFound(ex: WalletNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidMiniWalletDepositAmountException::class)
    fun handleInvalidAmount(ex: InvalidMiniWalletDepositAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(MiniWalletBalanceCapExceededException::class)
    fun handleBalanceCapExceeded(ex: MiniWalletBalanceCapExceededException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MINI_WALLET_BALANCE_CAP_EXCEEDED", ex.message ?: "Balance cap exceeded"))

    @ExceptionHandler(MiniWalletDailyLimitExceededException::class)
    fun handleDailyLimitExceeded(ex: MiniWalletDailyLimitExceededException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MINI_WALLET_DAILY_LIMIT_EXCEEDED", ex.message ?: "Daily limit exceeded"))

    @ExceptionHandler(MiniWalletMonthlyLimitExceededException::class)
    fun handleMonthlyLimitExceeded(ex: MiniWalletMonthlyLimitExceededException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MINI_WALLET_MONTHLY_LIMIT_EXCEEDED", ex.message ?: "Monthly limit exceeded"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))

    @ExceptionHandler(MiniWalletBirthDateRequiredException::class)
    fun handleBirthDateRequired(ex: MiniWalletBirthDateRequiredException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MINI_WALLET_BIRTH_DATE_REQUIRED", ex.message ?: "Birth date required"))

    @ExceptionHandler(MiniWalletAgeIneligibleException::class)
    fun handleAgeIneligible(ex: MiniWalletAgeIneligibleException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("MINI_WALLET_AGE_INELIGIBLE", ex.message ?: "Age ineligible"))
}
