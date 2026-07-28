package rw.itunda.agents

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.AgentStatus
import rw.itunda.core.idempotency.IdempotencyConflictException
import rw.itunda.core.idempotency.IdempotencyInProgressException
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.math.BigDecimal
import java.time.LocalDate

data class RegisterAgentRequest(val displayName: String, val dailyCashInLimit: BigDecimal, val dailyCashOutLimit: BigDecimal)
data class SetAgentStatusRequest(val status: AgentStatus)
data class CashInRequest(val accountNumber: String, val amount: BigDecimal, val receiptNumber: String)
data class CashOutRequest(val accountNumber: String, val amount: BigDecimal, val receiptNumber: String, val authorizationCode: String)
data class AssignAgentOperatorRequest(val userId: String)
data class ResolveTillReconciliationRequest(val note: String)
data class SetAgentLocationRequest(val latitude: Double, val longitude: Double)
data class SetAgentOperatorStatusRequest(val isActive: Boolean)
data class FundAgentTillRequest(val amount: BigDecimal, val reference: String)

/** Admin-operated until the separate agent-staff authentication flow is introduced. */
@RestController
@RequestMapping("/api/v1/system/agents")
class AgentAdminController(
    private val agentService: AgentService,
    private val idempotencyService: IdempotencyService,
) {
    @GetMapping
    fun list() = ResponseEntity.ok(mapOf("success" to true, "agents" to agentService.list()))

    @PostMapping
    fun register(@RequestBody request: RegisterAgentRequest) =
        ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "agent" to agentService.register(request.displayName, request.dailyCashInLimit, request.dailyCashOutLimit)))

    @PostMapping("/{agentId}/status")
    fun setStatus(@PathVariable agentId: String, @RequestBody request: SetAgentStatusRequest) =
        ResponseEntity.ok(mapOf("success" to true, "agent" to agentService.setStatus(agentId, request.status)))

    @GetMapping("/{agentId}/operators")
    fun operators(@PathVariable agentId: String) =
        ResponseEntity.ok(mapOf("success" to true, "operators" to agentService.getOperators(agentId)))

    @PostMapping("/{agentId}/operators")
    fun assignOperator(@PathVariable agentId: String, @RequestBody request: AssignAgentOperatorRequest) =
        ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "operator" to agentService.assignOperator(agentId, request.userId)))

    @PostMapping("/{agentId}/location")
    fun setLocation(@PathVariable agentId: String, @RequestBody request: SetAgentLocationRequest) =
        ResponseEntity.ok(mapOf("success" to true, "agent" to agentService.setLocation(agentId, request.latitude, request.longitude)))

    @PostMapping("/{agentId}/operators/{userId}/status")
    fun setOperatorStatus(
        @PathVariable agentId: String,
        @PathVariable userId: String,
        @RequestBody request: SetAgentOperatorStatusRequest,
    ) = ResponseEntity.ok(mapOf("success" to true, "operator" to agentService.setOperatorStatus(agentId, userId, request.isActive)))

    @PostMapping("/{agentId}/float")
    fun fundTill(
        @PathVariable agentId: String,
        @RequestBody request: FundAgentTillRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/system/agents/$agentId/float", idempotencyKey, request) {
            200 to (mapOf("success" to true) + agentService.fundTill(agentId, request.amount, request.reference))
        }
        return ResponseEntity.status(status).body(body)
    }

    @GetMapping("/till-reconciliations/pending")
    fun pendingTillReconciliations() = ResponseEntity.ok(mapOf("success" to true, "reconciliations" to agentService.pendingTillReconciliations()))

    @GetMapping("/till-reconciliations")
    fun reconciliationReport(
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
    ) = ResponseEntity.ok(mapOf("success" to true, "report" to agentService.reconciliationReport(from, to)))

    @PostMapping("/till-reconciliations/{id}/resolve")
    fun resolveTillReconciliation(@PathVariable id: String, @RequestBody request: ResolveTillReconciliationRequest, @AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "reconciliation" to agentService.resolveTillReconciliation(id, currentUser.userId, request.note)))

    @PostMapping("/{agentId}/cash-ins")
    fun cashIn(
        @PathVariable agentId: String,
        @RequestBody request: CashInRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/system/agents/$agentId/cash-ins", idempotencyKey, request) {
            200 to (mapOf("success" to true) + agentService.cashIn(agentId, request.accountNumber, request.amount, request.receiptNumber, currentUser.userId))
        }
        return ResponseEntity.status(status).body(body)
    }

    @PostMapping("/{agentId}/cash-outs")
    fun cashOut(
        @PathVariable agentId: String,
        @RequestBody request: CashOutRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val (status, body) = idempotencyService.replayOrExecute("POST /api/v1/system/agents/$agentId/cash-outs", idempotencyKey, request) {
            200 to (mapOf("success" to true) + agentService.cashOut(agentId, request.accountNumber, request.amount, request.receiptNumber, request.authorizationCode, currentUser.userId))
        }
        return ResponseEntity.status(status).body(body)
    }

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
    @ExceptionHandler(AgentOperatorAlreadyAssignedException::class)
    fun handleAssigned(ex: AgentOperatorAlreadyAssignedException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("AGENT_OPERATOR_ALREADY_ASSIGNED", ex.message ?: "Conflict"))
    @ExceptionHandler(TillReconciliationNotFoundException::class)
    fun handleTillNotFound(ex: TillReconciliationNotFoundException) = ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("TILL_RECONCILIATION_NOT_FOUND", ex.message ?: "Not found"))
    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENCY_KEY_CONFLICT", ex.message ?: "Conflict"))
    @ExceptionHandler(IdempotencyInProgressException::class)
    fun handleIdempotencyProgress(ex: IdempotencyInProgressException) = ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("IDEMPOTENT_REQUEST_PROCESSING", ex.message ?: "Conflict"))
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleInvalid(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_CASH_IN", ex.message ?: "Bad request"))
}
