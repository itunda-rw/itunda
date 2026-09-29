package rw.itunda.agents

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.agents.WithdrawalAuthorizationInvalidException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal

/** Store-facing API: the operator's JWT determines the agent; callers never supply an agent id. */
@RestController
@RequestMapping("/api/v1/agent")
class AgentOperatorController(
    private val agentService: AgentService,
    private val idempotencyService: IdempotencyService,
) {
    data class SubmitTillCountRequest(val countedCash: BigDecimal)
    data class SetAgentLocationRequest(val latitude: Double, val longitude: Double)

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "operator" to agentService.getMyOperator(currentUser.userId)))

    // Real gap found live (uncalled-endpoint sweep, 2026-08-16) -- see
    // AgentService.setLocationForOperator's own doc comment. The customer-facing
    // "nearby agents" feature (AgentDiscoveryController.getNearbyAgents) already
    // depends on this data; no real agent had any way to actually report it.
    @PostMapping("/location")
    fun setLocation(@RequestBody request: SetAgentLocationRequest, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "agent" to agentService.setLocationForOperator(currentUser.userId, request.latitude, request.longitude)))

    @GetMapping("/till")
    fun till(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "till" to agentService.getTillSnapshot(currentUser.userId)))

    @GetMapping("/activity")
    fun activity(
        @RequestParam(defaultValue = "30") limit: Int,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ) = ResponseEntity.ok(mapOf("success" to true, "activity" to agentService.getActivity(currentUser.userId, limit)))

    @PostMapping("/cash-ins")
    fun cashIn(
        @RequestBody request: CashInRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> = execute("POST /api/v1/agent/cash-ins", idempotencyKey, request) {
        agentService.cashInForOperator(currentUser.userId, request.accountNumber, request.amount, request.receiptNumber)
    }

    @PostMapping("/cash-outs")
    fun cashOut(
        @RequestBody request: CashOutRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/agent/cash-outs", idempotencyKey, request) {
            200 to (mapOf("success" to true) + agentService.cashOutForOperator(currentUser.userId, request.accountNumber, request.amount, request.receiptNumber, request.authorizationCode))
        }
        return ResponseEntity.status(status).body(body)
    }

    // Real gap found 2026-09-05 (see feedback_idempotency_key_sweep memory) -- this
    // is the exact scenario AgentReceiptAlreadyUsedException/
    // TillReconciliationAlreadySubmittedException were already flagged as the
    // highest-severity item in feedback_toss_error_handling's own 2026-08-30
    // priority list for (real cash-handling, real operational confusion on a
    // false-negative "already submitted" error), but that earlier fix (commit
    // 5d21bc16) only taught the CLIENT to parse the real error code -- it never
    // protected this endpoint from the underlying cause: a lost response after a
    // successful till count would resubmit here and hit
    // TillReconciliationAlreadySubmittedException on the retry, showing the
    // operator a confusing conflict for a count that actually already succeeded.
    // cash-ins/cash-outs above were already protected; this was the outlier.
    @PostMapping("/till-reconciliations")
    fun submitTillCount(
        @RequestBody request: SubmitTillCountRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/agent/till-reconciliations", idempotencyKey, request) {
            HttpStatus.CREATED.value() to mapOf("success" to true, "reconciliation" to agentService.submitTillCount(currentUser.userId, request.countedCash))
        }
        return ResponseEntity.status(status).body(body)
    }

    private fun execute(endpoint: String, key: String, request: Any, action: () -> Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute(endpoint, key, request) { 200 to (mapOf("success" to true) + action()) }
        return ResponseEntity.status(status).body(body)
    }

    @ExceptionHandler(AgentOperatorNotAuthorizedException::class)
    fun handleUnauthorized(ex: AgentOperatorNotAuthorizedException) = ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("AGENT_OPERATOR_NOT_AUTHORIZED", ex.message ?: "Forbidden"))
    @ExceptionHandler(AgentNotFoundException::class)
    fun handleNotFound(ex: AgentNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("AGENT_NOT_FOUND", ex.message ?: "Not found"))
    @ExceptionHandler(AgentSuspendedException::class)
    fun handleSuspended(ex: AgentSuspendedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("AGENT_SUSPENDED", ex.message ?: "Conflict"))
    @ExceptionHandler(AgentReceiptAlreadyUsedException::class)
    fun handleReceipt(ex: AgentReceiptAlreadyUsedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CASH_RECEIPT_ALREADY_USED", ex.message ?: "Conflict"))
    @ExceptionHandler(AgentDailyLimitExceededException::class)
    fun handleLimit(ex: AgentDailyLimitExceededException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("AGENT_DAILY_LIMIT_EXCEEDED", ex.message ?: "Limit exceeded"))
    @ExceptionHandler(AgentInsufficientCashException::class)
    fun handleInsufficientCash(ex: AgentInsufficientCashException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("AGENT_INSUFFICIENT_CASH", ex.message ?: "Insufficient cash"))
    @ExceptionHandler(WithdrawalAuthorizationInvalidException::class)
    fun handleWithdrawalAuthorization(ex: WithdrawalAuthorizationInvalidException) = ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("WITHDRAWAL_AUTHORIZATION_INVALID", ex.message ?: "Invalid authorization"))
    @ExceptionHandler(TillReconciliationAlreadySubmittedException::class)
    fun handleTillDuplicate(ex: TillReconciliationAlreadySubmittedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("TILL_COUNT_ALREADY_SUBMITTED", ex.message ?: "Conflict"))
    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))
    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleInvalid(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_AGENT_TRANSACTION", ex.message ?: "Bad request"))
    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingHeader(ex: MissingRequestHeaderException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required"))
}
