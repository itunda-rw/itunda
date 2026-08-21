package rw.itunda.loans

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
import java.time.Instant

data class RegisterCooperativeRequest(val name: String, val cropType: String, val registrationNumber: String?)
data class RequestAdvanceRequest(val membershipId: String, val principalAmount: BigDecimal, val purpose: String, val expectedHarvestDate: Instant)
data class RepayAdvanceRequest(val amount: BigDecimal)

// Real Rwanda coffee-cooperative harvest-advance / input financing -- see
// CooperativeService's own doc comment for the full sourced account. Sourced beyond
// this session's usual Toss/Kakao/Naver/Coupang reference ecosystems.
@RestController
@RequestMapping("/api/v1/cooperatives")
class CooperativeController(
    private val cooperativeService: CooperativeService,
    private val idempotencyService: IdempotencyService,
) {
    @PostMapping
    fun registerCooperative(@RequestBody request: RegisterCooperativeRequest): ResponseEntity<Map<String, Any?>> {
        val cooperative = cooperativeService.registerCooperative(request.name, request.cropType, request.registrationNumber)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "cooperative" to cooperative))
    }

    @PostMapping("/{cooperativeId}/join")
    fun joinCooperative(@PathVariable cooperativeId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val membership = cooperativeService.joinCooperative(currentUser.userId, cooperativeId)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "membership" to membership))
    }

    @GetMapping("/my-memberships")
    fun getMyMemberships(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "memberships" to cooperativeService.getMyMemberships(currentUser.userId)))

    @GetMapping("/{cooperativeId}/overview")
    fun getCooperativeOverview(@PathVariable cooperativeId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true) + cooperativeService.getCooperativeOverview(currentUser.userId, cooperativeId))

    @PostMapping("/advances")
    fun requestAdvance(@RequestBody request: RequestAdvanceRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        val advance = cooperativeService.requestAdvance(
            currentUser.userId, request.membershipId, request.principalAmount, request.purpose, request.expectedHarvestDate,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "advance" to advance))
    }

    @PostMapping("/advances/{advanceId}/disburse")
    fun disburseAdvance(
        @PathVariable advanceId: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/cooperatives/advances/$advanceId/disburse", idempotencyKey, currentUser.userId) {
            val advance = cooperativeService.disburseAdvance(currentUser.userId, advanceId)
            200 to mapOf("success" to true, "advance" to advance)
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/advances/{advanceId}/repay")
    fun repayAdvance(
        @PathVariable advanceId: String,
        @RequestBody request: RepayAdvanceRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/cooperatives/advances/$advanceId/repay", idempotencyKey, request) {
            val advance = cooperativeService.repayAdvance(currentUser.userId, advanceId, request.amount)
            200 to mapOf("success" to true, "advance" to advance)
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/advances/my-advances")
    fun getMyAdvances(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "advances" to cooperativeService.getMyAdvances(currentUser.userId)))

    @ExceptionHandler(CooperativeNotFoundException::class)
    fun handleCoopNotFound(ex: CooperativeNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("COOPERATIVE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(CooperativeInvalidNameException::class)
    fun handleInvalidName(ex: CooperativeInvalidNameException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_COOPERATIVE_NAME", ex.message ?: "Bad request"))

    @ExceptionHandler(AlreadyMemberException::class)
    fun handleAlreadyMember(ex: AlreadyMemberException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("ALREADY_MEMBER", ex.message ?: "Conflict"))

    @ExceptionHandler(NotMemberException::class)
    fun handleNotMember(ex: NotMemberException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("NOT_MEMBER", ex.message ?: "Not found"))

    @ExceptionHandler(HarvestAdvanceNoAccountException::class)
    fun handleNoAccount(ex: HarvestAdvanceNoAccountException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("WALLET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(HarvestAdvanceInvalidAmountException::class)
    fun handleInvalidAmount(ex: HarvestAdvanceInvalidAmountException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AMOUNT", ex.message ?: "Bad request"))

    @ExceptionHandler(HarvestAdvanceNotFoundException::class)
    fun handleAdvanceNotFound(ex: HarvestAdvanceNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("HARVEST_ADVANCE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(HarvestAdvanceInvalidStatusException::class)
    fun handleInvalidStatus(ex: HarvestAdvanceInvalidStatusException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("INVALID_ADVANCE_STATUS", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))

    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleInProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(AccountFrozenException::class)
    fun handleAccountFrozen(ex: AccountFrozenException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("WALLET_FROZEN", ex.message ?: "Account is frozen"))
}
