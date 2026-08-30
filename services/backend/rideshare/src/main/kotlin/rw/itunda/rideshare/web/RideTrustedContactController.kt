package rw.itunda.rideshare.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import rw.itunda.core.security.CurrentUser
import rw.itunda.core.web.ApiError
import rw.itunda.rideshare.RideTooManyTrustedContactsException
import rw.itunda.rideshare.RideTrustedContactAlreadyAddedException
import rw.itunda.rideshare.RideTrustedContactNotFoundException
import rw.itunda.rideshare.RideTrustedContactRecipientNotFoundException
import rw.itunda.rideshare.RideTrustedContactSelfException
import rw.itunda.rideshare.RideTrustedContactService

// Real Uber Safety "Trusted Contacts" -- see RideTrustedContact.kt's own doc comment. A
// persistent contact list set up once, distinct from RideController's own per-trip
// "Send Status" share (/trips/{tripId}/send-status), which stays on RideController since
// it's a trip action, not contact management, and throws none of these exceptions.
//
// Extracted out of RideController.kt (2026-08-31, market-readiness audit) the moment
// that file first crossed the file-size-lint 500-line guideline -- a self-contained,
// single-purpose controller with no dependency back on RideController, same "extract,
// don't baseline a first-time crossing" discipline this repo already established.
// Mirrors MerchantController's own precedent of multiple @RestController classes
// sharing one URL prefix family (MerchantBookingController/MerchantCouponController/
// PaymentsApiController all sit alongside MerchantController itself).
data class AddTrustedContactRequest(val phoneNumber: String, val name: String)

@RestController
@RequestMapping("/api/v1/rides/trusted-contacts")
class RideTrustedContactController(
    private val rideTrustedContactService: RideTrustedContactService,
) {
    @GetMapping
    fun listTrustedContacts(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.ok(mapOf("success" to true, "contacts" to rideTrustedContactService.list(currentUser.userId)))

    @PostMapping
    fun addTrustedContact(
        @RequestBody request: AddTrustedContactRequest,
        @AuthenticationPrincipal currentUser: CurrentUser,
    ): ResponseEntity<Map<String, Any?>> {
        val contact = rideTrustedContactService.add(currentUser.userId, request.phoneNumber, request.name)
        return ResponseEntity.status(HttpStatus.CREATED).body(mapOf("success" to true, "contact" to contact))
    }

    @DeleteMapping("/{contactId}")
    fun removeTrustedContact(@PathVariable contactId: String, @AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<Map<String, Any?>> {
        rideTrustedContactService.remove(currentUser.userId, contactId)
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @ExceptionHandler(RideTrustedContactNotFoundException::class)
    fun handleTrustedContactNotFound(ex: RideTrustedContactNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("TRUSTED_CONTACT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RideTrustedContactRecipientNotFoundException::class)
    fun handleTrustedContactRecipientNotFound(ex: RideTrustedContactRecipientNotFoundException) =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiError("TRUSTED_CONTACT_RECIPIENT_NOT_FOUND", ex.message ?: "Not found"))

    @ExceptionHandler(RideTrustedContactSelfException::class)
    fun handleTrustedContactSelf(ex: RideTrustedContactSelfException) =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiError("CANNOT_ADD_SELF_AS_TRUSTED_CONTACT", ex.message ?: "Bad request"))

    @ExceptionHandler(RideTrustedContactAlreadyAddedException::class)
    fun handleTrustedContactAlreadyAdded(ex: RideTrustedContactAlreadyAddedException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("TRUSTED_CONTACT_ALREADY_ADDED", ex.message ?: "Conflict"))

    @ExceptionHandler(RideTooManyTrustedContactsException::class)
    fun handleTooManyTrustedContacts(ex: RideTooManyTrustedContactsException) =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError("TOO_MANY_TRUSTED_CONTACTS", ex.message ?: "Conflict"))
}
