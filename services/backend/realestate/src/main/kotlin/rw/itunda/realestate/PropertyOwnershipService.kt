package rw.itunda.realestate

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.PropertyOwnershipSubmission
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.PropertyListingRepository
import rw.itunda.core.repository.PropertyOwnershipSubmissionRepository
import java.time.Instant
import java.util.UUID

class PropertyOwnershipSubmissionAlreadyPendingException(message: String) : RuntimeException(message)
class PropertyOwnershipSubmissionNotFoundException(message: String) : RuntimeException(message)
class PropertyOwnershipSubmissionNotPendingException(message: String) : RuntimeException(message)

/**
 * Real ownership verification (2026-07-25) -- see `PropertyOwnershipSubmission`'s own doc
 * comment for the full "document-upload + human-review, no fabricated registry pre-check"
 * scope. Mirrors `IdentityService`'s real submit/queue/decide shape field-for-field, the
 * same precedent `ComplianceController` already established for KYC/KYB.
 *
 * 2026-08-17: `decide` now notifies the real submitter of the outcome, closing the same
 * "terminal decision, zero notification to the real person it happened to" gap Sections
 * 146 (`InsuranceService.decideClaim`)/147 (`MarketplaceService.resolveDispute`) already
 * closed elsewhere -- the exact same shape `OrderReturnService.decide` established first.
 * Notably `IdentityService.decide` (the very precedent this class's own doc comment says
 * it mirrors "field-for-field") has the identical gap and remains open; not touched here
 * to keep this change scoped to one real, tested fix.
 */
@Service
class PropertyOwnershipService(
    private val propertyOwnershipSubmissionRepository: PropertyOwnershipSubmissionRepository,
    private val propertyListingRepository: PropertyListingRepository,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(PropertyOwnershipService::class.java)

    @Transactional
    fun submit(userId: String, listingId: String, documentUrl: String): PropertyOwnershipSubmission {
        val listing = propertyListingRepository.findById(listingId)
            .orElseThrow { PropertyListingNotFoundException("Property listing not found") }
        if (listing.listerId != userId) {
            throw PropertyListingNotFoundException("Property listing not found")
        }
        val trimmedUrl = documentUrl.trim()
        if (trimmedUrl.isEmpty() || trimmedUrl.length > 255) {
            throw InvalidPropertyListingException("A document URL is required and must be 255 characters or fewer")
        }
        if (!trimmedUrl.startsWith("/api/v1/uploads/")) {
            throw InvalidPropertyListingException("Document URL must reference a real uploaded file")
        }
        val existing = propertyOwnershipSubmissionRepository.findByListingIdOrderBySubmittedAtDesc(listingId)
        if (existing.any { it.status == "PENDING" }) {
            throw PropertyOwnershipSubmissionAlreadyPendingException("An ownership verification is already pending review for this listing")
        }

        val submission = propertyOwnershipSubmissionRepository.save(
            PropertyOwnershipSubmission(
                id = "property_ownership_${UUID.randomUUID()}", listingId = listingId, userId = userId,
                documentUrl = trimmedUrl, status = "PENDING", submittedAt = Instant.now(),
            ),
        )
        listing.ownershipVerificationStatus = "PENDING"
        propertyListingRepository.save(listing)
        return submission
    }

    fun getQueue(pageable: Pageable): Page<PropertyOwnershipSubmission> =
        propertyOwnershipSubmissionRepository.findByStatusOrderBySubmittedAtAsc("PENDING", pageable)

    @Transactional
    fun decide(submissionId: String, reviewerId: String, approve: Boolean, reason: String?): PropertyOwnershipSubmission {
        val submission = propertyOwnershipSubmissionRepository.findById(submissionId)
            .orElseThrow { PropertyOwnershipSubmissionNotFoundException("Submission not found") }
        if (submission.status != "PENDING") {
            throw PropertyOwnershipSubmissionNotPendingException("Submission is already ${submission.status}")
        }
        submission.status = if (approve) "VERIFIED" else "REJECTED"
        submission.reviewedBy = reviewerId
        submission.reviewedAt = Instant.now()
        submission.decisionReason = reason
        propertyOwnershipSubmissionRepository.save(submission)

        // Reject falls the listing back to NONE, not stuck on PENDING, so the lister can
        // fix the document and resubmit -- same "not a dead end" discipline
        // IdentityService.decide's approve-only side effect implies for KYC/KYB.
        val listing = propertyListingRepository.findById(submission.listingId).orElse(null)
        if (listing != null) {
            listing.ownershipVerificationStatus = if (approve) "VERIFIED" else "NONE"
            propertyListingRepository.save(listing)
        }

        val title = listing?.title ?: "your property listing"
        val notifTitle = if (approve) "Ownership verified" else "Ownership verification rejected"
        val body = if (approve) {
            "Your ownership document for \"$title\" was verified. The listing now shows as ownership-verified."
        } else {
            "Your ownership document for \"$title\" was rejected.${reason?.let { " Reason: $it" } ?: ""} You can upload a new document and resubmit."
        }
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = submission.userId, type = "PROPERTY_OWNERSHIP_DECIDED",
                title = notifTitle, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"submissionId\":\"${submission.id}\"}",
            ),
        )
        sendPushAfterCommit(submission.userId, notifTitle, body, submission.id)

        return submission
    }

    // Same real "defer the mobile push until the real status change is durable, but the
    // in-app Notification row is saved immediately" discipline OrderReturnService
    // .sendPushAfterCommit/InsuranceService.sendPushAfterCommit/MarketplaceService's own
    // dispute-resolution notification already establish for a structurally identical
    // terminal decision.
    private fun sendPushAfterCommit(userId: String, title: String, body: String, submissionId: String) {
        val data = mapOf("submissionId" to submissionId)
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body, data)
            } catch (e: Exception) {
                log.warn("Could not send property-ownership-decision push for submission {}", submissionId, e)
            }
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send()
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() = send()
        })
    }
}
