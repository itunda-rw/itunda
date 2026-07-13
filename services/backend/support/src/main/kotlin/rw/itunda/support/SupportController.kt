package rw.itunda.support

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.domain.SupportTicketCategory
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.support.SupportService
import rw.itunda.core.support.SupportTransactionNotFoundException
import rw.itunda.core.support.SupportTransactionNotOwnedException
import rw.itunda.core.web.ApiError

data class CreateTicketRequest(val transactionId: String, val category: SupportTicketCategory, val description: String)

/**
 * Real customer support ticket creation/listing -- see SupportTicket's own doc comment
 * for the gap this closes. Regular authenticated-user access (not ADMIN-gated); the
 * review queue and decide flow live separately in :system's SupportAdminController,
 * inheriting SecurityConfig's existing hasRole("ADMIN") gate on the system path prefix.
 */
@RestController
@RequestMapping("/api/v1/support")
class SupportController(private val supportService: SupportService) {

    @PostMapping("/tickets")
    fun createTicket(
        @RequestBody request: CreateTicketRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        val ticket = supportService.createTicket(currentUser.userId, request.transactionId, request.category, request.description)
        return ResponseEntity.ok(mapOf("success" to true, "ticket" to ticket))
    }

    @GetMapping("/tickets")
    fun myTickets(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "tickets" to supportService.getMyTickets(currentUser.userId)))

    @ExceptionHandler(SupportTransactionNotFoundException::class)
    fun handleNotFound(ex: SupportTransactionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("TRANSACTION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(SupportTransactionNotOwnedException::class)
    fun handleNotOwned(ex: SupportTransactionNotOwnedException) =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiError("TRANSACTION_NOT_OWNED", ex.message ?: "Forbidden"))
}
