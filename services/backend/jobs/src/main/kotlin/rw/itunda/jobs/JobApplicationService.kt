package rw.itunda.jobs

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.JobApplication
import rw.itunda.core.domain.JobApplicationStatus
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.JobApplicationRepository
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.core.repository.NotificationRepository
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
    private val resumeService: ResumeService,
    private val objectMapper: ObjectMapper,
    private val notificationRepository: NotificationRepository,
    private val pushNotificationService: PushNotificationService,
) {
    private val log = LoggerFactory.getLogger(JobApplicationService::class.java)
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
        // Real anti-spam limit, same 10/hour convention every other Hood creation
        // endpoint already established.
        rateLimiter.checkLimit("jobs:application:$applicantId", limit = 10, window = Duration.ofHours(1))
        // Real bug found live (2026-08-02): the plain "existsByJobPostIdAndApplicantId
        // AndStatus(..., PENDING)" check just below reads-then-CREATEs a brand-new row --
        // there's no existing PENDING application for this (jobPostId, applicantId) pair
        // to put an @Version guard on yet, and job_applications has no unique constraint
        // on (job_post_id, applicant_id, status) either, so two concurrent apply() calls
        // by the same applicant to the same job post could both pass that check before
        // either committed and both create a real duplicate PENDING application. Fixed
        // the same way this codebase's own "reject if already exists" race precedent
        // works: lock a DIFFERENT already-existing row (the job post itself) via
        // JobPostRepository.findByIdForUpdate to serialize concurrent applies against
        // it, then re-check existence under that lock.
        jobPostRepository.findByIdForUpdate(jobPostId)
        if (jobApplicationRepository.existsByJobPostIdAndApplicantIdAndStatus(jobPostId, applicantId, JobApplicationStatus.PENDING)) {
            throw JobApplicationAlreadyPendingException("You already have a pending application for this job post")
        }

        // Real résumé attach at submission time -- see JobApplication.resumeSnapshotJson's
        // own doc comment for why this is a snapshot, not a live resumeId reference.
        // Best-effort: a serialization hiccup must never block a real application.
        val resumeSnapshotJson = try {
            resumeService.getResume(applicantId).takeIf { it.resume != null }?.let { objectMapper.writeValueAsString(it) }
        } catch (e: Exception) {
            null
        }

        val saved = jobApplicationRepository.save(
            JobApplication(
                id = "job_application_${UUID.randomUUID()}", jobPostId = jobPostId, applicantId = applicantId,
                message = trimmedMessage, resumeSnapshotJson = resumeSnapshotJson,
            ),
        )
        // Real sibling-asymmetry gap found live (2026-09-14): respond()'s own doc
        // comment already establishes that a terminal decision on someone else's real
        // application deserves a Notification+push -- but the poster-facing event at
        // the OTHER end of the same workflow, a brand-new application arriving, notified
        // nobody at all. A poster previously had no way to know an application existed
        // short of manually re-checking the app.
        val title = "New application received"
        val body = "Someone applied to \"${post.title}\"."
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = post.posterId, type = "JOB_APPLICATION_RECEIVED",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"applicationId\":\"${saved.id}\"}",
            ),
        )
        sendPushAfterCommit(post.posterId, title, body, saved.id)
        return saved
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

        // Real gap found live (2026-09-13, sibling-asymmetry sweep): this terminal
        // decision on someone else's real application used to notify nobody -- on
        // decline, absolutely nothing; on accept, only a silent Conversation row (no
        // message, no push). Same "a terminal decision deserves a real notification to
        // the person it happened to" discipline PropertyOwnershipService.decide/
        // InsuranceService.decideClaim/OrderReturnService.decide already establish.
        val title = if (accept) "Your application was accepted" else "Your application wasn't selected"
        val body = if (accept) {
            "\"${post.title}\" accepted your application. Start chatting to arrange the details."
        } else {
            "\"${post.title}\" wasn't a match this time. Keep applying -- new jobs are posted every day."
        }
        notificationRepository.save(
            Notification(
                id = "notif_${UUID.randomUUID()}", userId = application.applicantId, type = "JOB_APPLICATION_DECIDED",
                title = title, body = body, isRead = false, createdAt = Instant.now(),
                dataJson = "{\"applicationId\":\"${application.id}\"}",
            ),
        )
        sendPushAfterCommit(application.applicantId, title, body, application.id)

        return application to conversation
    }

    // Same real "defer the mobile push until the real status change is durable, but the
    // in-app Notification row is saved immediately" discipline PropertyOwnershipService
    // .sendPushAfterCommit/InsuranceService.sendPolicyPushAfterCommit already establish
    // for a structurally identical terminal decision.
    private fun sendPushAfterCommit(userId: String, title: String, body: String, applicationId: String) {
        val data = mapOf("applicationId" to applicationId)
        val send = {
            try {
                pushNotificationService.sendToUser(userId, title, body, data)
            } catch (e: Exception) {
                log.warn("Could not send job-application-decision push for application {}", applicationId, e)
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
