package rw.itunda.jobs

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.JobApplication
import rw.itunda.core.domain.JobApplicationStatus
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.repository.JobApplicationRepository
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.messaging.MessagingService
import java.time.Duration
import java.time.Instant
import java.util.UUID

class JobApplicationAlreadyPendingException(message: String) : RuntimeException(message)
class JobApplicationNotFoundException(message: String) : RuntimeException(message)
class JobApplicationNotPendingException(message: String) : RuntimeException(message)
class InvalidJobApplicationException(message: String) : RuntimeException(message)

/**
 * Real 당근알바-style structured application review (2026-07-25) -- see
 * `JobApplication`'s own doc comment and V95's migration comment for the full sourced
 * account. `contactPoster` (a bare DM, still real and still available) covers a poster
 * who's fine talking to anyone who shows up; this is the missing "review who applied,
 * then decide who to talk to" step Karrot's real product actually has.
 */
@Service
class JobApplicationService(
    private val jobApplicationRepository: JobApplicationRepository,
    private val jobPostRepository: JobPostRepository,
    private val rateLimiter: RateLimiter,
    private val messagingService: MessagingService,
) {
    @Transactional
    fun apply(applicantId: String, jobPostId: String, message: String): JobApplication {
        val post = jobPostRepository.findById(jobPostId).orElseThrow { JobPostNotFoundException("Job post not found") }
        if (post.status != JobPostStatus.OPEN) {
            throw JobPostNotOpenException("Only an open job post accepts applications")
        }
        if (post.posterId == applicantId) {
            throw OwnJobPostException("You can't apply to your own job post")
        }
        val trimmedMessage = message.trim()
        if (trimmedMessage.isEmpty() || trimmedMessage.length > 1000) {
            throw InvalidJobApplicationException("A self-introduction is required and must be 1000 characters or fewer")
        }
        if (jobApplicationRepository.existsByJobPostIdAndApplicantIdAndStatus(jobPostId, applicantId, JobApplicationStatus.PENDING)) {
            throw JobApplicationAlreadyPendingException("You already have a pending application for this job post")
        }
        // Real anti-spam limit, same 10/hour convention every other Hood creation
        // endpoint already established.
        rateLimiter.checkLimit("jobs:application:$applicantId", limit = 10, window = Duration.ofHours(1))

        return jobApplicationRepository.save(
            JobApplication(id = "job_application_${UUID.randomUUID()}", jobPostId = jobPostId, applicantId = applicantId, message = trimmedMessage),
        )
    }

    fun getApplicationsForPost(posterId: String, jobPostId: String, pageable: Pageable): Page<JobApplication> {
        val post = jobPostRepository.findById(jobPostId).orElseThrow { JobPostNotFoundException("Job post not found") }
        if (post.posterId != posterId) {
            throw JobPostNotFoundException("Job post not found")
        }
        return jobApplicationRepository.findByJobPostIdOrderBySubmittedAtDesc(jobPostId, pageable)
    }

    fun getMyApplications(applicantId: String, pageable: Pageable): Page<JobApplication> =
        jobApplicationRepository.findByApplicantIdOrderBySubmittedAtDesc(applicantId, pageable)

    /** Accepting an application is real "review applicants, then chat" -- it opens the
     * conversation here, rather than the applicant needing a separate contactPoster call. */
    @Transactional
    fun respond(posterId: String, applicationId: String, accept: Boolean): Pair<JobApplication, Conversation?> {
        val application = jobApplicationRepository.findById(applicationId)
            .orElseThrow { JobApplicationNotFoundException("Application not found") }
        val post = jobPostRepository.findById(application.jobPostId).orElseThrow { JobPostNotFoundException("Job post not found") }
        if (post.posterId != posterId) {
            throw JobApplicationNotFoundException("Application not found")
        }
        if (application.status != JobApplicationStatus.PENDING) {
            throw JobApplicationNotPendingException("Application is already ${application.status}")
        }
        application.status = if (accept) JobApplicationStatus.ACCEPTED else JobApplicationStatus.DECLINED
        application.respondedAt = Instant.now()
        jobApplicationRepository.save(application)

        val conversation = if (accept) messagingService.startOrGetConversation(posterId, application.applicantId) else null
        return application to conversation
    }
}
