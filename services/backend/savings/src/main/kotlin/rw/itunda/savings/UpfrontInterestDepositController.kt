package rw.itunda.savings

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.WalletFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class OpenUpfrontDepositRequest(val principal: BigDecimal)

// Real Toss Bank 먼저 이자받는 정기예금 equivalent -- see UpfrontInterestDepositService's
// own doc comment. Normal itunda-user JWT gate.
@RestController
@RequestMapping("/api/v1/upfront-deposits")
class UpfrontInterestDepositController(
    private val upfrontInterestDepositService: UpfrontInterestDepositService,
    private val upfrontInterestDepositScheduler: UpfrontInterestDepositScheduler,
) {
    @PostMapping
    fun open(@RequestBody request: OpenUpfrontDepositRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val deposit = upfrontInterestDepositService.open(currentUser.userId, request.principal)
        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf("success" to true, "deposit" to deposit, "message" to "Interest paid to your main wallet now -- principal locked for 12 months"),
        )
    }

    @GetMapping
    fun myDeposits(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "deposits" to upfrontInterestDepositService.getMyDeposits(currentUser.userId)))

    @PostMapping("/{id}/withdraw")
    fun withdraw(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val deposit = upfrontInterestDepositService.withdraw(currentUser.userId, id)
        return ResponseEntity.ok(mapOf("success" to true, "deposit" to deposit, "message" to "Principal withdrawn to your main wallet"))
    }

    // Demo/ops convenience endpoint, same real "expose the scheduler's own due-processing
    // logic" discipline WeeklySavingsController.processDue already established -- lets a
    // real 12-month maturity be verified without waiting real wall-clock months.
    @PostMapping("/process-due")
    fun processDue(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val processed = upfrontInterestDepositScheduler.processDue()
        return ResponseEntity.ok(mapOf("success" to true, "processed" to processed))
    }

    @ExceptionHandler(UpfrontDepositNotFoundException::class)
    fun handleNotFound(ex: UpfrontDepositNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("UPFRONT_DEPOSIT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidUpfrontDepositAmountException::class)
    fun handleInvalidAmount(ex: InvalidUpfrontDepositAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Invalid request"))

    @ExceptionHandler(UpfrontDepositNotMaturedException::class)
    fun handleNotMatured(ex: UpfrontDepositNotMaturedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("UPFRONT_DEPOSIT_NOT_MATURED", ex.message ?: "Conflict"))

    @ExceptionHandler(UpfrontDepositAlreadyWithdrawnException::class)
    fun handleAlreadyWithdrawn(ex: UpfrontDepositAlreadyWithdrawnException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("UPFRONT_DEPOSIT_ALREADY_WITHDRAWN", ex.message ?: "Conflict"))

    @ExceptionHandler(NoWalletException::class)
    fun handleNoWallet(ex: NoWalletException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(WalletFrozenException::class)
    fun handleWalletFrozen(ex: WalletFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Wallet is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMIT_EXCEEDED", ex.message ?: "Too many requests"))
}
