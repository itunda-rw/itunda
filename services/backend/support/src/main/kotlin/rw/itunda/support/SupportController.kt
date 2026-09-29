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
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.SupportTicketCategory
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.support.SupportService
import rw.itunda.core.support.SupportTransactionNotFoundException
import rw.itunda.core.web.ApiError
import java.time.Duration

data class CreateTicketRequest(val transactionId: String, val category: SupportTicketCategory, val description: String)

/**
 * Real customer support ticket creation/listing -- see SupportTicket's own doc comment
 * for the gap this closes. Regular authenticated-user access (not ADMIN-gated); the
 * review queue and decide flow live separately in :system's SupportAdminController,
 * inheriting SecurityConfig's existing hasRole("ADMIN") gate on the system path prefix.
 */
@RestController
@RequestMapping("/api/v1/support")
class SupportController(private val supportService: SupportService, private val rateLimiter: RateLimiter) {

    // Real bug found live (2026-08-02): ticket creation had no real rate limit at all
    // -- every other real content/request-creation endpoint in this codebase
    // (VehicleValuationService.registerVehicle, IkiminaService.createIkimina,
    // WeeklySavingsService, ...) already gates on rateLimiter.checkLimit. Without one
    // here, an authenticated caller could spam-create tickets against their own real
    // transaction ids without limit, each one a real row a real support reviewer has
    // to triage (and, for ACCOUNT_TAKEOVER, each one real-freezing a account). :support
    // didn't depend on :auth before this fix (SupportService itself lives in :core,
    // which :auth depends ON -- the limiter can't live there without a circular
    // dependency), so the check is applied here in the controller instead, the same
    // module boundary VehicleValuationService's own real rate limit already respects.
    private val CREATE_TICKET_LIMIT = 10
    private val CREATE_TICKET_WINDOW: Duration = Duration.ofHours(1)

    @PostMapping("/tickets")
    fun createTicket(
        @RequestBody request: CreateTicketRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any>> {
        rateLimiter.checkLimit("support:create-ticket:${currentUser.userId}", limit = CREATE_TICKET_LIMIT, window = CREATE_TICKET_WINDOW)
        val ticket = supportService.createTicket(currentUser.userId, request.transactionId, request.category, request.description)
        return ResponseEntity.ok(mapOf("success" to true, "ticket" to ticket))
    }

    @GetMapping("/tickets")
    fun myTickets(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(mapOf("success" to true, "tickets" to supportService.getMyTickets(currentUser.userId)))

    @ExceptionHandler(SupportTransactionNotFoundException::class)
    fun handleNotFound(ex: SupportTransactionNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("TRANSACTION_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
