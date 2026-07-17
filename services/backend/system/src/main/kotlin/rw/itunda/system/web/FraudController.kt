package rw.itunda.system.web

import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
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
import rw.itunda.core.domain.FraudFlagDecision
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta
import rw.itunda.system.FraudFlagAlreadyReviewedException
import rw.itunda.system.FraudFlagNotFoundException
import rw.itunda.system.FraudReviewService

data class DecideFraudFlagRequest(val decision: FraudFlagDecision)

// Mapped under api/v1/system/fraud specifically so it inherits SecurityConfig's existing
// hasRole("ADMIN") gate on the system path prefix, same convention as ComplianceController.
@RestController
@RequestMapping("/api/v1/system/fraud")
class FraudController(private val fraudReviewService: FraudReviewService) {

    @GetMapping("/queue")
    fun queue(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any>> {
        val page = fraudReviewService.getQueue(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "queue" to page.content) + pageMeta(page))
    }

    @PostMapping("/{flagId}/decide")
    fun decide(
        @PathVariable flagId: String,
        @RequestBody request: DecideFraudFlagRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val flag = fraudReviewService.decide(flagId, currentUser.userId, request.decision)
        return ResponseEntity.ok(mapOf("success" to true, "flag" to flag))
    }

    @ExceptionHandler(FraudFlagNotFoundException::class)
    fun handleNotFound(ex: FraudFlagNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("FRAUD_FLAG_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(FraudFlagAlreadyReviewedException::class)
    fun handleAlreadyReviewed(ex: FraudFlagAlreadyReviewedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("FRAUD_FLAG_ALREADY_REVIEWED", ex.message ?: "Conflict"))
}
