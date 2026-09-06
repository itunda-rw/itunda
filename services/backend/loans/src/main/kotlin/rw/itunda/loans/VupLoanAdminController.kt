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

data class DecideVupLoanReviewRequest(val writeOff: Boolean, val note: String? = null)

// Real Bank product-completeness pass (2026-09-06) -- Bank's first ops-mfe review
// queue (14 already existed for other products -- fraud/compliance/disputes/etc.
// -- none Bank-domain). Mapped under api/v1/system/loans specifically so it
// inherits SecurityConfig's existing hasRole("ADMIN") rule on the shared
// /api/v1/system/** prefix, same real RBAC gate PropertyOwnershipAdminController/
// MarketplaceEscrowAdminController already use -- no new attack surface. See
// VupLoanService.getDefaultReviewQueue/decide's own doc comments for why this
// covers VUP loans only (the one loan product with a real, already-computed
// OVERDUE status) and deliberately does not touch the ledger on write-off.
@RestController
@RequestMapping("/api/v1/system/loans")
class VupLoanAdminController(private val vupLoanService: VupLoanService) {

    @GetMapping("/vup-overdue")
    fun queue(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any>> {
        val page = vupLoanService.getDefaultReviewQueue(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "queue" to page.content) + pageMeta(page))
    }

    @PostMapping("/vup-overdue/{loanId}/decide")
    fun decide(
        @PathVariable loanId: String,
        @RequestBody request: DecideVupLoanReviewRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val loan = vupLoanService.decide(loanId, currentUser.userId, request.writeOff, request.note)
        return ResponseEntity.ok(mapOf("success" to true, "loan" to loan))
    }

    @ExceptionHandler(VupLoanNotFoundException::class)
    fun handleNotFound(ex: VupLoanNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("VUP_LOAN_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(VupLoanNotOverdueException::class)
    fun handleNotOverdue(ex: VupLoanNotOverdueException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("VUP_LOAN_NOT_OVERDUE", ex.message ?: "Conflict"))

    @ExceptionHandler(InvalidVupLoanReviewNoteException::class)
    fun handleInvalidReviewNote(ex: InvalidVupLoanReviewNoteException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REVIEW_NOTE", ex.message ?: "Invalid review note"))
}
