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
import rw.itunda.core.domain.SupportTicketResolution
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.support.SupportService
import rw.itunda.core.support.SupportTicketAlreadyResolvedException
import rw.itunda.core.support.SupportTicketNotFoundException
import rw.itunda.core.support.SupportTransactionNotFoundException
import rw.itunda.core.web.ApiError
import rw.itunda.core.web.pageMeta

data class ResolveTicketRequest(val resolution: SupportTicketResolution, val notes: String? = null)

// Mapped under api/v1/system/support specifically so it inherits SecurityConfig's
// existing hasRole("ADMIN") gate on the system path prefix, same convention as
// FraudController/ComplianceController.
@RestController
@RequestMapping("/api/v1/system/support")
class SupportAdminController(private val supportService: SupportService) {

    @GetMapping("/queue")
    fun queue(@PageableDefault(size = 20) pageable: Pageable): ResponseEntity<Map<String, Any>> {
        val page = supportService.getQueue(pageable)
        return ResponseEntity.ok(mapOf("success" to true, "queue" to page.content) + pageMeta(page))
    }

    @PostMapping("/{ticketId}/resolve")
    fun resolve(
        @PathVariable ticketId: String,
        @RequestBody request: ResolveTicketRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val ticket = supportService.resolve(ticketId, currentUser.userId, request.resolution, request.notes)
        return ResponseEntity.ok(mapOf("success" to true, "ticket" to ticket))
    }

    @ExceptionHandler(SupportTicketNotFoundException::class)
    fun handleNotFound(ex: SupportTicketNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("SUPPORT_TICKET_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SupportTicketAlreadyResolvedException::class)
    fun handleAlreadyResolved(ex: SupportTicketAlreadyResolvedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("SUPPORT_TICKET_ALREADY_RESOLVED", ex.message ?: "Conflict"))

    @ExceptionHandler(SupportTransactionNotFoundException::class)
    fun handleTransactionNotFound(ex: SupportTransactionNotFoundException) =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ApiError("REFUND_SOURCE_TRANSACTION_MISSING", ex.message ?: "Unprocessable"))
}
