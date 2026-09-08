package rw.itunda.loans

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
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta

data class DecideHarvestAdvanceReviewRequest(val writeOff: Boolean, val note: String? = null)

// Real Bank product-completeness pass, cycle 2 (2026-09-08) -- mirrors
// VupLoanAdminController exactly. Mapped under api/v1/system/loans so it inherits
// SecurityConfig's existing hasRole("ADMIN") rule on the shared /api/v1/system/**
// prefix, same real RBAC gate every other admin review queue already uses -- no
// new attack surface. See CooperativeService.getDefaultReviewQueue/decide's own
// doc comments for why this deliberately does not touch the ledger on write-off.
@RestController
@RequestMapping("/api/v1/system/loans")
class HarvestAdvanceAdminController(private val cooperativeService: CooperativeService) {

    @GetMapping("/harvest-overdue")
    fun queue(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any>> {
        val page = cooperativeService.getDefaultReviewQueue(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "queue" to page.content) + pageMeta(page))
    }

    @PostMapping("/harvest-overdue/{advanceId}/decide")
    fun decide(
        @PathVariable advanceId: String,
        @RequestBody request: DecideHarvestAdvanceReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val advance = cooperativeService.decide(advanceId, currentUser.userId, request.writeOff, request.note)
        return ResponseEntity.ok(mapOf("success" to true, "advance" to advance))
    }

    @ExceptionHandler(HarvestAdvanceNotFoundException::class)
    fun handleNotFound(ex: HarvestAdvanceNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("HARVEST_ADVANCE_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(HarvestAdvanceNotOverdueException::class)
    fun handleNotOverdue(ex: HarvestAdvanceNotOverdueException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("HARVEST_ADVANCE_NOT_OVERDUE", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidHarvestAdvanceReviewNoteException::class)
    fun handleInvalidReviewNote(ex: InvalidHarvestAdvanceReviewNoteException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REVIEW_NOTE", ex.message ?: "Invalid review note"))
}
