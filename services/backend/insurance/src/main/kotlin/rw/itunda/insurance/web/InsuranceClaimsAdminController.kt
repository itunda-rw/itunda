package rw.itunda.insurance.web

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
import rw.itunda.core.ledger.InsufficientFundsException
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.insurance.ClaimNotFoundException
import rw.itunda.insurance.ClaimNotPendingException
import rw.itunda.insurance.InsuranceService
import rw.itunda.insurance.InvalidClaimDecisionReasonException
import rw.itunda.insurance.NoAccountException

data class DecideClaimRequest(val approve: Boolean, val reason: String? = null)

// Mapped under api/v1/system/insurance-claims specifically so it inherits SecurityConfig's
// existing hasRole("ADMIN") gate, same convention as ComplianceController/FraudController.
@RestController
@RequestMapping("/api/v1/system/insurance-claims")
class InsuranceClaimsAdminController(private val insuranceService: InsuranceService) {

    @GetMapping("/queue")
    fun queue(): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "queue" to insuranceService.getClaimsQueue()))

    @PostMapping("/{claimId}/decide")
    fun decide(
        @PathVariable claimId: String,
        @RequestBody request: DecideClaimRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val claim = insuranceService.decideClaim(claimId, currentUser.userId, request.approve, request.reason)
        return ResponseEntity.ok(mapOf("success" to true, "claim" to claim))
    }

    @ExceptionHandler(ClaimNotFoundException::class)
    fun handleNotFound(ex: ClaimNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("CLAIM_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(ClaimNotPendingException::class)
    fun handleNotPending(ex: ClaimNotPendingException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("CLAIM_NOT_PENDING", ex.message ?: "Conflict"))

    @ExceptionHandler(NoAccountException::class)
    fun handleNoAccount(ex: NoAccountException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("ACCOUNT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(InsufficientFundsException::class)
    fun handleInsufficientFunds(ex: InsufficientFundsException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("INSUFFICIENT_FUNDS", ex.message ?: "Insufficient funds"))

    @ExceptionHandler(InvalidClaimDecisionReasonException::class)
    fun handleInvalidDecisionReason(ex: InvalidClaimDecisionReasonException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_DECISION_REASON", ex.message ?: "Invalid decision reason"))
}
