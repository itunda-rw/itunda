package rw.itunda.jobs

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Resume
import rw.itunda.core.domain.ResumeCertification
import rw.itunda.core.domain.ResumeEducation
import rw.itunda.core.domain.ResumeExperience
import rw.itunda.core.repository.ResumeCertificationRepository
import rw.itunda.core.repository.ResumeEducationRepository
import rw.itunda.core.repository.ResumeExperienceRepository
import rw.itunda.core.repository.ResumeRepository
import java.time.Duration
import java.time.Instant
import java.util.UUID

class InvalidResumeException(message: String) : RuntimeException(message)
class ResumeEntryNotFoundException(message: String) : RuntimeException(message)

data class ResumeStrength(val id: String, val label: String)

data class ResumeDetail(
    val resume: Resume?,
    val experiences: List<ResumeExperience>,
    val educations: List<ResumeEducation>,
    val certifications: List<ResumeCertification>,
    val completionPercent: Int,
)

/**
 * A real 이력서 (Karrot 당근알바-style résumé) builder -- see `Resume`'s own doc
 * comment for the full account. v1, honestly scoped: a real self-intro + preset
 * strength tags + free-text additional-info section, plus real experience/education/
 * certification entries. No résumé photo upload -- a real, separate follow-up
 * feature (Hood product-completeness pass, 2026-09-07): `UploadController`
 * (`marketplace/.../web/UploadController.kt`) has provided a real upload/storage
 * pipeline since 2026-07-24, already used by `PropertyOwnershipService`, so a
 * future pass can wire a photo field the same "bring your own already-hosted URL"
 * way -- this just hasn't been done yet, not because the pipeline doesn't exist.
 */
@Service
class ResumeService(
    private val resumeRepository: ResumeRepository,
    private val experienceRepository: ResumeExperienceRepository,
    private val educationRepository: ResumeEducationRepository,
    private val certificationRepository: ResumeCertificationRepository,
    private val rateLimiter: RateLimiter,
) {
    companion object {
        val STRENGTHS = listOf(
            ResumeStrength("friendly", "Friendly"),
            ResumeStrength("diligent", "Diligent"),
            ResumeStrength("punctual", "Punctual"),
            ResumeStrength("communicative", "Communicative"),
            ResumeStrength("quick_learner", "Quick learner"),
            ResumeStrength("team_player", "Team player"),
        )
        private val STRENGTH_IDS = STRENGTHS.map { it.id }.toSet()

        // Real completion-percent nudge (see the reference's own "이력서를 완성해보세요 90%"
        // bar) -- 5 real, checkable signals. Deliberately excludes a photo (see class
        // doc comment): the reference's own 6th signal is never satisfiable here.
        private const val COMPLETION_STEPS = 5
    }

    private fun getOrCreateResume(userId: String): Resume =
        resumeRepository.findByUserId(userId) ?: resumeRepository.save(
            Resume(id = "resume_${UUID.randomUUID()}", userId = userId),
        )

    // Rate limit added (Hood product-completeness pass, 2026-09-07) -- this was the
    // one Hood content-creation service missing this repo-wide convention (every
    // sibling create action -- MarketplaceService.createListing, CommunityService
    // .createPost, JobPostService.createPost, PropertyListingService.createListing --
    // already has one).
    @Transactional
    fun updateProfile(userId: String, selfIntro: String?, strengths: List<String>, additionalInfo: String?): Resume {
        rateLimiter.checkLimit("resume:update:$userId", limit = 10, window = Duration.ofHours(1))
        val trimmedIntro = selfIntro?.trim()?.ifEmpty { null }
        if (trimmedIntro != null && trimmedIntro.length > 2000) {
            throw InvalidResumeException("Self-intro must be 2000 characters or fewer")
        }
        val unknownStrengths = strengths.filter { it !in STRENGTH_IDS }
        if (unknownStrengths.isNotEmpty()) {
            throw InvalidResumeException("Unknown strength: ${unknownStrengths.first()}")
        }
        val trimmedInfo = additionalInfo?.trim()?.ifEmpty { null }
        if (trimmedInfo != null && trimmedInfo.length > 500) {
            throw InvalidResumeException("Additional info must be 500 characters or fewer")
        }
        val resume = getOrCreateResume(userId)
        resume.selfIntro = trimmedIntro
        resume.strengths = strengths.distinct().takeIf { it.isNotEmpty() }?.joinToString(",")
        resume.additionalInfo = trimmedInfo
        resume.updatedAt = Instant.now()
        return resumeRepository.save(resume)
    }

    fun getResume(userId: String): ResumeDetail {
        val resume = resumeRepository.findByUserId(userId)
        val experiences = resume?.let { experienceRepository.findByResumeIdOrderByCreatedAtDesc(it.id) } ?: emptyList()
        val educations = resume?.let { educationRepository.findByResumeIdOrderByCreatedAtDesc(it.id) } ?: emptyList()
        val certifications = resume?.let { certificationRepository.findByResumeIdOrderByCreatedAtDesc(it.id) } ?: emptyList()
        val stepsComplete = listOf(
            !resume?.selfIntro.isNullOrBlank(),
            !resume?.strengths.isNullOrBlank(),
            experiences.isNotEmpty(),
            educations.isNotEmpty(),
            certifications.isNotEmpty(),
        ).count { it }
        return ResumeDetail(
            resume = resume, experiences = experiences, educations = educations, certifications = certifications,
            completionPercent = (stepsComplete * 100) / COMPLETION_STEPS,
        )
    }

    @Transactional
    fun addExperience(userId: String, company: String, role: String, period: String, description: String?): ResumeExperience {
        rateLimiter.checkLimit("resume:experience:$userId", limit = 10, window = Duration.ofHours(1))
        val trimmedCompany = company.trim()
        val trimmedRole = role.trim()
        val trimmedPeriod = period.trim()
        if (trimmedCompany.isEmpty() || trimmedRole.isEmpty() || trimmedPeriod.isEmpty()) {
            throw InvalidResumeException("Company, role, and period are all required")
        }
        if (trimmedCompany.length > 200 || trimmedRole.length > 200 || trimmedPeriod.length > 100) {
            throw InvalidResumeException("Company/role must be 200 characters or fewer, period 100 or fewer")
        }
        val resume = getOrCreateResume(userId)
        return experienceRepository.save(
            ResumeExperience(
                id = "resume_experience_${UUID.randomUUID()}", resumeId = resume.id,
                company = trimmedCompany, role = trimmedRole, period = trimmedPeriod,
                description = description?.trim()?.ifEmpty { null }?.take(1000),
            ),
        )
    }

    @Transactional
    fun removeExperience(userId: String, experienceId: String) {
        val resume = resumeRepository.findByUserId(userId) ?: throw ResumeEntryNotFoundException("Entry not found")
        if (experienceRepository.deleteByIdAndResumeId(experienceId, resume.id) == 0L) {
            throw ResumeEntryNotFoundException("Entry not found")
        }
    }

    @Transactional
    fun addEducation(userId: String, school: String, degree: String?, major: String?): ResumeEducation {
        rateLimiter.checkLimit("resume:education:$userId", limit = 10, window = Duration.ofHours(1))
        val trimmedSchool = school.trim()
        if (trimmedSchool.isEmpty()) {
            throw InvalidResumeException("School is required")
        }
        if (trimmedSchool.length > 200) {
            throw InvalidResumeException("School must be 200 characters or fewer")
        }
        val resume = getOrCreateResume(userId)
        return educationRepository.save(
            ResumeEducation(
                id = "resume_education_${UUID.randomUUID()}", resumeId = resume.id, school = trimmedSchool,
                degree = degree?.trim()?.ifEmpty { null }?.take(100), major = major?.trim()?.ifEmpty { null }?.take(200),
            ),
        )
    }

    @Transactional
    fun removeEducation(userId: String, educationId: String) {
        val resume = resumeRepository.findByUserId(userId) ?: throw ResumeEntryNotFoundException("Entry not found")
        if (educationRepository.deleteByIdAndResumeId(educationId, resume.id) == 0L) {
            throw ResumeEntryNotFoundException("Entry not found")
        }
    }

    @Transactional
    fun addCertification(userId: String, name: String, issuedDate: String?): ResumeCertification {
        rateLimiter.checkLimit("resume:certification:$userId", limit = 10, window = Duration.ofHours(1))
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            throw InvalidResumeException("Certification name is required")
        }
        if (trimmedName.length > 200) {
            throw InvalidResumeException("Certification name must be 200 characters or fewer")
        }
        val resume = getOrCreateResume(userId)
        return certificationRepository.save(
            ResumeCertification(
                id = "resume_certification_${UUID.randomUUID()}", resumeId = resume.id,
                name = trimmedName, issuedDate = issuedDate?.trim()?.ifEmpty { null }?.take(50),
            ),
        )
    }

    @Transactional
    fun removeCertification(userId: String, certificationId: String) {
        val resume = resumeRepository.findByUserId(userId) ?: throw ResumeEntryNotFoundException("Entry not found")
        if (certificationRepository.deleteByIdAndResumeId(certificationId, resume.id) == 0L) {
            throw ResumeEntryNotFoundException("Entry not found")
        }
    }
}
