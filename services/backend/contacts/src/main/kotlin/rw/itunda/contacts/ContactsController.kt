package rw.itunda.contacts

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Contact
import rw.itunda.core.repository.ContactRepository
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import java.time.Duration
import java.util.UUID

data class AddContactRequest(val name: String, val bank: String? = null, val phoneNumber: String)

/**
 * Port of backend/src/controllers/contacts.controller.ts. That controller had a real
 * IDOR: getContacts returned every user's saved contacts to any authenticated caller,
 * with no per-user filtering at all. Filtered by currentUser.userId from the start here.
 */
@RestController
@RequestMapping("/api/v1/contacts")
class ContactsController(private val contactRepository: ContactRepository, private val rateLimiter: RateLimiter) {

    @GetMapping
    fun getContacts(@AuthenticationPrincipal currentUser: CurrentUser) =
        ResponseEntity.ok(mapOf("success" to true, "contacts" to contactRepository.findByUserId(currentUser.userId)))

    @PostMapping
    fun addContact(@RequestBody request: AddContactRequest, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Any> {
        if (request.name.isBlank() || request.phoneNumber.isBlank()) {
            return ResponseEntity.badRequest().body(ApiError("INVALID_REQUEST", "Name and phone number are required"))
        }
        // Real bug found live (2026-08-02): this content-creation endpoint had shipped
        // with zero rate limiting -- every other real content/request-creation endpoint
        // in this codebase (P2pService.generateRequest, GiftService.sendGift,
        // FamilyLinkService.inviteChild, MessagingService.sendMessage, etc) already has
        // one; an authenticated caller could otherwise spam unlimited Contact rows.
        rateLimiter.checkLimit("contacts:add:${currentUser.userId}", limit = 60, window = Duration.ofHours(1))
        val contact = contactRepository.save(
            Contact(
                id = "c_${UUID.randomUUID()}", userId = currentUser.userId, name = request.name,
                bank = request.bank ?: "MTN MoMo", acc = request.phoneNumber, phoneNumber = request.phoneNumber,
                color = "#F5FAFF", letter = request.name.take(1).uppercase(),
            ),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "contact" to contact))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException) = ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("INVALID_REQUEST", ex.message ?: "Bad request"))

    @ExceptionHandler(RateLimitExceededException::class)
    fun handleRateLimit(ex: RateLimitExceededException) =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ApiError("RATE_LIMITED", ex.message ?: "Too many requests"))
}
