package rw.itunda.savings

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.ledger.AccountFrozenException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

data class CreateGroupAccountRequest(val name: String)
data class InviteMemberRequest(val phoneNumber: String)
data class GroupAccountAmountRequest(val amount: BigDecimal)
data class SetDuesAmountRequest(val amount: BigDecimal?)

@RestController
@RequestMapping("/api/v1/group-accounts")
class GroupAccountController(
    private val groupAccountService: GroupAccountService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping
    fun create(@RequestBody request: CreateGroupAccountRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val account = groupAccountService.createGroupAccount(currentUser.userId, request.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "groupAccount" to account))
    }

    @GetMapping
    fun myGroupAccounts(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "groupAccounts" to groupAccountService.getMyGroupAccounts(currentUser.userId)))

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true) + groupAccountService.getGroupAccount(currentUser.userId, id).toMap())

    @PostMapping("/{id}/members")
    fun invite(
        @PathVariable id: String,
        @RequestBody request: InviteMemberRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val member = groupAccountService.inviteMember(currentUser.userId, id, request.phoneNumber)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "member" to member))
    }

    @PostMapping("/{id}/deposit")
    fun deposit(
        @PathVariable id: String,
        @RequestBody request: GroupAccountAmountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/group-accounts/$id/deposit", idempotencyKey, request) {
            val view = groupAccountService.deposit(currentUser.userId, id, request.amount)
            200 to (mapOf("success" to true, "message" to "Deposited ${request.amount} RWF") + view.toMap())
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/{id}/withdraw")
    fun withdraw(
        @PathVariable id: String,
        @RequestBody request: GroupAccountAmountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/group-accounts/$id/withdraw", idempotencyKey, request) {
            val view = groupAccountService.withdraw(currentUser.userId, id, request.amount)
            200 to (mapOf("success" to true, "message" to "Withdrew ${request.amount} RWF") + view.toMap())
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real KakaoBank 회비 (dues) management -- see GroupAccountService's own doc comments.
    @PutMapping("/{id}/dues")
    fun setDuesAmount(
        @PathVariable id: String,
        @RequestBody request: SetDuesAmountRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val account = groupAccountService.setDuesAmount(currentUser.userId, id, request.amount)
        return ResponseEntity.ok(mapOf("success" to true, "groupAccount" to account))
    }

    @GetMapping("/{id}/dues")
    fun getDuesStatus(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "dues" to groupAccountService.getDuesStatus(currentUser.userId, id)))

    @PostMapping("/{id}/dues/remind")
    fun requestUnpaidDues(@PathVariable id: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val remindedCount = groupAccountService.requestUnpaidDues(currentUser.userId, id)
        return ResponseEntity.ok(mapOf("success" to true, "remindedCount" to remindedCount))
    }

    private fun GroupAccountView.toMap() = mapOf("groupAccount" to account, "balance" to balance, "members" to members)

    @ExceptionHandler(GroupAccountNotFoundException::class)
    fun handleNotFound(ex: GroupAccountNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("GROUP_ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InvalidGroupAccountNameException::class)
    fun handleInvalidName(ex: InvalidGroupAccountNameException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_GROUP_ACCOUNT_NAME", ex.message ?: "Bad request"))

    // Real fix (IDOR audit pass 1, 2026-08-08) fixed the STATUS (403->404) for
    // ownership/membership checks in withdraw/inviteMember/setDues/requestDues/
    // getGroupAccount/contribute, but kept distinguishable error CODES
    // (GROUP_ACCOUNT_NOT_OWNER/GROUP_ACCOUNT_NOT_MEMBER) -- the exact residual leak
    // pass 9 (2026-09-03) named and fixed elsewhere (LoanNotOwned/AccountNotOwned/
    // PaymentCodeAccountNotOwned) but missed here. A stranger probing groupAccountId
    // values could still distinguish "exists, not yours/not a member" from "doesn't
    // exist" purely from the response body, even though both returned 404. Fixed
    // 2026-09-04 by having every ownership/membership check in
    // GroupAccountService.kt throw the same GroupAccountNotFoundException instead --
    // both handlers (and their now-dead exception classes) removed.

    @ExceptionHandler(GroupAccountRecipientNotFoundException::class)
    fun handleRecipientNotFound(ex: GroupAccountRecipientNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(GroupAccountAlreadyMemberException::class)
    fun handleAlreadyMember(ex: GroupAccountAlreadyMemberException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ALREADY_MEMBER", ex.message ?: "Conflict"))

    @ExceptionHandler(GroupAccountFullException::class)
    fun handleFull(ex: GroupAccountFullException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("GROUP_ACCOUNT_FULL", ex.message ?: "Conflict"))

    @ExceptionHandler(GroupAccountNoAccountException::class)
    fun handleNoAccount(ex: GroupAccountNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("ACCOUNT_FROZEN", ex.message ?: "Account is frozen"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REQUEST", ex.message ?: "Invalid request"))
}
