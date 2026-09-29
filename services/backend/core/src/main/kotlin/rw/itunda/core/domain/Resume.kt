package rw.itunda.core.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A real 이력서 (Karrot 당근알바-style résumé) -- one per user. Genuinely new: itunda
 * had a real job-application self-intro message (`JobApplication.message`) but no
 * standing profile a job-seeker builds once and reuses across applications, matching
 * the real reference's own "내 이력서" screen (경력/학력/자격/장점 sections, a
 * completion-percentage nudge).
 *
 * `strengths` is a delimited string (comma-separated preset tags), same convention as
 * `Merchant.closedWeekdays`/`EatsReview.goodPoints` -- a small, fixed vocabulary, not
 * freeform text, matching the reference's own tappable "친절해요"/"성실해요"-style chips.
 *
 * Experience/education/certifications are flat child entities with a `resumeId` FK
 * (see `ResumeExperience`/`ResumeEducation`/`ResumeCertification`), not
 * `@OneToMany`/`@ElementCollection` -- matches this codebase's own repo-wide
 * convention (confirmed zero hits for either annotation anywhere in this backend
 * during this session's Maps work).
 */
@Entity
@Table(name = "resumes")
class Resume(
    @Id
    @Column(length = 64)
    val id: String,

    // One résumé per user -- a real DB unique constraint (V-migration), not just an
    // application-level assumption.
    @Column(name = "user_id", nullable = false, length = 64, unique = true)
    val userId: String,

    @Column(name = "self_intro", nullable = true, length = 2000)
    var selfIntro: String? = null,

    @Column(nullable = true, length = 500)
    var strengths: String? = null,

    @Column(name = "additional_info", nullable = true, length = 500)
    var additionalInfo: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", userId = "")

    fun strengthList(): List<String> = strengths?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}

@Entity
@Table(name = "resume_experiences")
class ResumeExperience(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "resume_id", nullable = false, length = 64)
    val resumeId: String,

    @Column(nullable = false, length = 200)
    var company: String,

    @Column(nullable = false, length = 200)
    var role: String,

    // Free-text period, matching the reference's own "2020년 2월 ~ 근무중 (6년 7개월)"
    // display -- a real date-range picker is a client concern, not a backend gap.
    @Column(nullable = false, length = 100)
    var period: String,

    @Column(nullable = true, length = 1000)
    var description: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", resumeId = "", company = "", role = "", period = "")
}

@Entity
@Table(name = "resume_educations")
class ResumeEducation(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "resume_id", nullable = false, length = 64)
    val resumeId: String,

    @Column(nullable = false, length = 200)
    var school: String,

    @Column(nullable = true, length = 100)
    var degree: String? = null,

    @Column(nullable = true, length = 200)
    var major: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", resumeId = "", school = "")
}

@Entity
@Table(name = "resume_certifications")
class ResumeCertification(
    @Id
    @Column(length = 64)
    val id: String,

    @Column(name = "resume_id", nullable = false, length = 64)
    val resumeId: String,

    @Column(nullable = false, length = 200)
    var name: String,

    // Free-text date, same reasoning as ResumeExperience.period.
    @Column(nullable = true, length = 50)
    var issuedDate: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    protected constructor() : this(id = "", resumeId = "", name = "")
}
