package rw.itunda.rideshare

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.RideTrustedContact
import rw.itunda.core.repository.RideDriverRepository
import rw.itunda.core.repository.RideTripRepository
import rw.itunda.core.repository.RideTrustedContactRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.messaging.MessagingService
import java.util.UUID

class RideTrustedContactNotFoundException(message: String) : RuntimeException(message)
class RideTrustedContactRecipientNotFoundException(message: String) : RuntimeException(message)
class RideTrustedContactSelfException(message: String) : RuntimeException(message)
class RideTrustedContactAlreadyAddedException(message: String) : RuntimeException(message)
class RideTooManyTrustedContactsException(message: String) : RuntimeException(message)

/**
 * Real Uber Safety "Trusted Contacts" -- see `RideTrustedContact.kt`'s own doc comment
 * for the full sourcing. Kept as its own service (not folded into `RideTripService`,
 * already itunda's single largest ride-domain file) since contact-list CRUD and the
 * "Send Status" fan-out are one coherent, separable real feature.
 */
@Service
class RideTrustedContactService(
    private val rideTrustedContactRepository: RideTrustedContactRepository,
    private val rideTripRepository: RideTripRepository,
    private val rideDriverRepository: RideDriverRepository,
    private val userRepository: UserRepository,
    private val messagingService: MessagingService,
) {
    companion object {
        // Real Uber cap -- "You can add up to 5 trusted contacts" (help.uber.com).
        const val MAX_TRUSTED_CONTACTS = 5
    }

    fun list(userId: String): List<RideTrustedContact> = rideTrustedContactRepository.findByUserIdOrderByCreatedAtDesc(userId)

    @Transactional
    fun add(userId: String, phoneNumber: String, name: String): RideTrustedContact {
        val trimmedName = name.trim().ifBlank { "Trusted contact" }.take(255)
        val contactUser = userRepository.findByPhoneNumber(phoneNumber.trim())
            ?: throw RideTrustedContactRecipientNotFoundException("No itunda account found for this phone number")
        if (contactUser.id == userId) throw RideTrustedContactSelfException("You can't add yourself as a trusted contact")
        if (rideTrustedContactRepository.existsByUserIdAndContactUserId(userId, contactUser.id)) {
            throw RideTrustedContactAlreadyAddedException("This contact is already on your trusted contacts list")
        }
        if (rideTrustedContactRepository.countByUserId(userId) >= MAX_TRUSTED_CONTACTS) {
            throw RideTooManyTrustedContactsException("You can have at most $MAX_TRUSTED_CONTACTS trusted contacts")
        }
        return rideTrustedContactRepository.save(
            RideTrustedContact(
                id = "ride_trusted_contact_${UUID.randomUUID()}", userId = userId,
                contactUserId = contactUser.id, contactName = trimmedName,
            ),
        )
    }

    @Transactional
    fun remove(userId: String, contactId: String) {
        val contact = rideTrustedContactRepository.findByIdAndUserId(contactId, userId)
            ?: throw RideTrustedContactNotFoundException("Trusted contact not found")
        rideTrustedContactRepository.delete(contact)
    }

    // Real Uber "Send Status" -- one tap fans a trip's live status out to every trusted
    // contact at once, distinct from RideTripService.shareTripStatus's one-off share
    // into a single conversation the passenger picks per-share. Returns the number of
    // contacts actually messaged so the client can show "Sent to N contacts" the same
    // way Uber's own confirmation toast does.
    @Transactional
    fun sendStatusToTrustedContacts(passengerUserId: String, tripId: String): Int {
        val trip = rideTripRepository.findById(tripId).orElseThrow { RideTripNotFoundException("Trip not found") }
        if (trip.passengerId != passengerUserId) throw RideTripNotFoundException("Trip not found")

        val contacts = rideTrustedContactRepository.findByUserIdOrderByCreatedAtDesc(passengerUserId)
        val driverLocation = trip.driverId?.let { driverId ->
            rideDriverRepository.findById(driverId).orElse(null)
                ?.takeIf { it.currentLatitude != null && it.currentLongitude != null }
        }
        val locationLine = driverLocation?.let {
            "\nDriver's last known location: ${it.currentLatitude}, ${it.currentLongitude}"
        } ?: ""
        val body = "🚗 My ride status: ${trip.status}\n" +
            "From: ${trip.pickupAddress}\n" +
            "To: ${trip.dropoffAddress}$locationLine"

        contacts.forEach { contact ->
            val conversation = messagingService.startOrGetConversation(passengerUserId, contact.contactUserId)
            messagingService.sendMessage(passengerUserId, conversation.id, body)
        }
        return contacts.size
    }
}
