package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Real Uber Safety "Trusted Contacts" (help.uber.com -- riders can pre-select trusted
 * contacts once in settings, then one tap sends their live trip status to all of them;
 * Uber's own real cap is 5). itunda's honest equivalent reuses `MessagingService`'s
 * existing Talk conversations rather than SMS to non-users (itunda has no SMS gateway):
 * a trusted contact must be an existing itunda user, resolved by phone number the same
 * way `P2pService.resolveRecipient` already does for P2P transfers.
 *
 * Distinct from `RideTripService.shareTripStatus` (2026-08 item 109), which sends a
 * one-off share into a conversation the passenger already picked -- this table is the
 * persistent, reusable contact list Uber's own "Manage Trusted Contacts" screen models,
 * so a passenger sets it up once and "Send Status" (`RideTripService
 * .sendStatusToTrustedContacts`) fans out to everyone on it in one action.
 */
@Entity
@Table(name = "ride_trusted_contacts")
class RideTrustedContact(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "contact_user_id", nullable = false, length = 64)
    val contactUserId: String,

    @Column(name = "contact_name", nullable = false, length = 255)
    val contactName: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "", contactUserId = "", contactName = "")
}
