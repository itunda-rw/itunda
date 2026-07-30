package rw.itunda.realestate

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.PropertyOwnershipSubmission
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
 */
@Service
class PropertyOwnershipService(
    private val propertyOwnershipSubmissionRepository: PropertyOwnershipSubmissionRepository,
    private val propertyListingRepository: PropertyListingRepository,
) {
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
        return submission
    }
}
