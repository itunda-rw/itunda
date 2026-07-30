package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

/**
 * documentReference is a demo-mode stand-in for an uploaded ID scan/selfie -- this backend
 * has no file-storage layer, so it accepts a reference string (e.g. a pre-uploaded object
 * key) rather than binary content. Real NIDA verification is blocked on regulatory/vendor
 * access (see docs/TOSS_PARITY_MATRIX.md's Compliance row) -- this entity models the
 * submission/review workflow itself, which previously didn't exist at all.
 */
@Entity
@Table(name = "kyc_submissions")
class KycSubmission(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "user_id", nullable = false, length = 64)
    val userId: String,

    @Column(name = "document_type", nullable = false, length = 32)
    val documentType: String,

    @Column(name = "document_number", nullable = false, length = 32)
    val documentNumber: String,

    @Column(name = "document_reference", nullable = false)
    val documentReference: String,

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

    // Real, honestly-scoped demo NIDA structural verification (2026-07-17) -- see
    // DemoNidaVerificationService's own doc comment. Populated at submit time, shown to
    // the human reviewer alongside the submission; never auto-decides on its own.
    @Column(name = "auto_verification_status", length = 24)
    var autoVerificationStatus: String? = null,

    @Column(name = "auto_verification_detail")
    var autoVerificationDetail: String? = null,

    // A verification review is a terminal compliance decision; concurrent reviewers
    // must not overwrite each other's outcome.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", userId = "", documentType = "", documentNumber = "", documentReference = "", status = "", submittedAt = Instant.now())
}
