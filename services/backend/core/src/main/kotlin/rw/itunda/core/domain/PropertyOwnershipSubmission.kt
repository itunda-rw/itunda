package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * documentUrl points at a real uploaded file (`UploadController`'s `/api/v1/uploads/{name}`),
 * unlike `KycSubmission.documentReference`'s demo-mode string -- this entity postdates the
 * real upload pipeline. Deliberately no automated pre-check field the way `KycSubmission`
 * has `autoVerificationStatus`: Rwanda has no publicly documented land-registry number
 * format to structurally validate against (unlike the National ID's real, sourced 16-digit
 * structure), so fabricating one would be a real, honest-scope violation rather than a
 * genuine pre-check. This is document-upload + human-review only.
 */
@Entity
@Table(name = "property_ownership_submissions")
class PropertyOwnershipSubmission(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "listing_id", nullable = false, length = 64)
    val listingId: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "document_url", nullable = false)
    val documentUrl: String,

    @Column(nullable = false, length = 16)
    var status: String,

    @Column(name = "submitted_at", nullable = false)
    val submittedAt: Instant,

    @Column(name = "reviewed_by", length = 64)
    var reviewedBy: String? = null,

    @Column(name = "reviewed_at")
    var reviewedAt: Instant? = null,

    @Column(name = "decision_reason")
    var decisionReason: String? = null,

    // Human-review decisions must be exclusive so a submission cannot be simultaneously
    // accepted and rejected by concurrent reviewers.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", listingId = "", userId = "", documentUrl = "", status = "", submittedAt = Instant.now())
}
