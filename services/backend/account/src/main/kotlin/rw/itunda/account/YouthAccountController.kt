package rw.itunda.account

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
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

data class DepositYouthAccountRequest(val amount: BigDecimal)

// Real KakaoBank mini-style capped starter account -- see YouthAccountService's own doc
// comment. Normal itunda-user JWT gate, same as every other user-facing feature.
@RestController
@RequestMapping("/api/v1/account/youth")
class YouthAccountController(
    private val youthAccountService: YouthAccountService,
    private val idempotencyService: IdempotencyService,
) {

    @PostMapping("/open")
    fun open(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val account = youthAccountService.openYouthAccount(currentUser.userId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "account" to account))
    }

    // Real bug found live 2026-08-05 via a repo-wide idempotency-coverage audit (the
    // same technique DesignatedDriverController's own doc comment already names finding
    // a real gap): unlike AccountController's own transfer/confirm (its direct structural
    // template), this endpoint had no Idempotency-Key requirement -- deposit() posts a
    // real account-to-account ledger transaction on every call with no existing row for a
    // retry to conflict against, so a client's network-timeout retry of the exact same
    // deposit would move the same money twice (the daily/monthly caps only catch this by
    // accident, if the retried amount happens to push a running total over a threshold).
    @PostMapping("/deposit")
    fun deposit(
        @RequestBody request: DepositYouthAccountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/account/youth/deposit", idempotencyKey, request) {
            val result = youthAccountService.deposit(currentUser.userId, request.amount)
            HttpStatus.OK.value() to (mapOf("success" to true) + result)
        }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(AccountNotFoundException::class)
    fun handleAccountNotFound(ex: AccountNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidYouthAccountDepositAmountException::class)
    fun handleInvalidAmount(ex: InvalidYouthAccountDepositAmountException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(YouthAccountBalanceCapExceededException::class)
    fun handleBalanceCapExceeded(ex: YouthAccountBalanceCapExceededException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("YOUTH_ACCOUNT_BALANCE_CAP_EXCEEDED", ex.message ?: "Balance cap exceeded"))

    @ExceptionHandler(YouthAccountDailyLimitExceededException::class)
    fun handleDailyLimitExceeded(ex: YouthAccountDailyLimitExceededException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("YOUTH_ACCOUNT_DAILY_LIMIT_EXCEEDED", ex.message ?: "Daily limit exceeded"))

    @ExceptionHandler(YouthAccountMonthlyLimitExceededException::class)
    fun handleMonthlyLimitExceeded(ex: YouthAccountMonthlyLimitExceededException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("YOUTH_ACCOUNT_MONTHLY_LIMIT_EXCEEDED", ex.message ?: "Monthly limit exceeded"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(YouthAccountBirthDateRequiredException::class)
    fun handleBirthDateRequired(ex: YouthAccountBirthDateRequiredException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("YOUTH_ACCOUNT_BIRTH_DATE_REQUIRED", ex.message ?: "Birth date required"))

    @ExceptionHandler(YouthAccountAgeIneligibleException::class)
    fun handleAgeIneligible(ex: YouthAccountAgeIneligibleException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("YOUTH_ACCOUNT_AGE_INELIGIBLE", ex.message ?: "Age ineligible"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))
}
