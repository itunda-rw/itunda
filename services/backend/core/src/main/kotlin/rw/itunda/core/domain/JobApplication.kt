package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

enum class JobApplicationStatus { PENDING, ACCEPTED, DECLINED }

/**
 * Real 당근알바-style structured application -- see V95's migration comment for the
 * sourced correction this closes. `message` is the applicant's real self-introduction
 * (Karrot's own research: "사진과 진솔한 자기소개" -- photo + sincere self-introduction
 * is what posters actually look for), free text and bounded, unlike HoodTransactionReview's
 * deliberate preset-tags-only design -- a private one-to-one submission to a single poster
 * carries none of that mechanic's public-review-abuse risk.
 */
@Entity
@Table(name = "job_applications")
class JobApplication(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "job_post_id", nullable = false, length = 64)
    val jobPostId: String,

    @Column(name = "applicant_id", nullable = false, length = 64)
    val applicantId: String,

    @Column(nullable = false, length = 1000)
    val message: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var status: JobApplicationStatus = JobApplicationStatus.PENDING,

    @Column(name = "submitted_at", nullable = false)
    val submittedAt: Instant = Instant.now(),

    @Column(name = "responded_at")
    var respondedAt: Instant? = null,

    // A poster may respond from multiple sessions; only one terminal decision should
    // resolve a pending application.
    @Version
    @Column(nullable = false)
    var version: Long = 0,
) {
    protected constructor() : this(id = "", jobPostId = "", applicantId = "", message = "")
}
