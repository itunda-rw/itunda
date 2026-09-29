package rw.itunda.jobs

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.JobApplication
import rw.itunda.core.domain.JobApplicationStatus
import rw.itunda.core.domain.JobPayType
import rw.itunda.core.domain.JobPost
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.repository.JobApplicationRepository
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.messaging.MessagingService
import java.math.BigDecimal
import java.util.Optional

private fun openJobPost(id: String, posterId: String) = JobPost(
    id = id, posterId = posterId, category = "delivery", title = "Weekend rider",
    description = "Deliver parcels", payType = JobPayType.HOURLY, payAmount = BigDecimal("1500"),
)

class JobApplicationServiceTest : BehaviorSpec({

    Given("an applicant applying to an open job post they've never applied to") {
        val jobApplicationRepository = mockk<JobApplicationRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val resumeService = mockk<ResumeService>(relaxed = true)
        val objectMapper = mockk<com.fasterxml.jackson.databind.ObjectMapper>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = JobApplicationService(jobApplicationRepository, jobPostRepository, rateLimiter, messagingService, resumeService, objectMapper, notificationRepository, pushNotificationService)

        val post = openJobPost("job_post_1", "poster_1")
        every { jobPostRepository.findById("job_post_1") } returns Optional.of(post)
        every { jobPostRepository.findByIdForUpdate("job_post_1") } returns Optional.of(post)
        every { jobApplicationRepository.existsByJobPostIdAndApplicantIdAndStatus("job_post_1", "applicant_1", JobApplicationStatus.PENDING) } returns false
        val savedSlot = slot<JobApplication>()
        every { jobApplicationRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("applying with a real self-introduction") {
            val application = service.apply("applicant_1", "job_post_1", "  I have 2 years of delivery experience  ")

            Then("it creates a real PENDING application") {
                application.status shouldBe JobApplicationStatus.PENDING
                application.jobPostId shouldBe "job_post_1"
                application.applicantId shouldBe "applicant_1"
                savedSlot.captured.message shouldBe "I have 2 years of delivery experience"
            }
            // Real bug found live (2026-08-02) -- see apply's own doc comment: this
            // asserts the actual fix mechanism, the same "lock a different
            // already-existing row" precedent this codebase already establishes for a
            // reject-if-already-exists check-then-CREATE race.
            Then("it real-locks the job post row before creating the application") {
                verify(exactly = 1) { jobPostRepository.findByIdForUpdate("job_post_1") }
            }

            // Real gap found live (repo-wide rate-limiter-verification sweep,
            // 2026-09-08): rateLimiter was relaxed = true with zero verify{} anywhere
            // in this file, so a future accidental removal of the real checkLimit call
            // would have compiled and passed silently.
            Then("the real rate limiter is actually consulted, not just mocked away") {
                verify(exactly = 1) { rateLimiter.checkLimit("jobs:application:applicant_1", limit = 10, window = java.time.Duration.ofHours(1)) }
            }
            Then("the poster is notified a new application arrived -- the sibling gap to respond()'s own decision notification") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "poster_1" && it.type == "JOB_APPLICATION_RECEIVED" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("poster_1", "New application received", any(), any()) }
            }
        }
    }

    Given("an applicant who already has a PENDING application for this job post") {
        val jobApplicationRepository = mockk<JobApplicationRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val resumeService = mockk<ResumeService>(relaxed = true)
        val objectMapper = mockk<com.fasterxml.jackson.databind.ObjectMapper>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = JobApplicationService(jobApplicationRepository, jobPostRepository, rateLimiter, messagingService, resumeService, objectMapper, notificationRepository, pushNotificationService)

        val post = openJobPost("job_post_2", "poster_2")
        every { jobPostRepository.findById("job_post_2") } returns Optional.of(post)
        every { jobPostRepository.findByIdForUpdate("job_post_2") } returns Optional.of(post)
        every { jobApplicationRepository.existsByJobPostIdAndApplicantIdAndStatus("job_post_2", "applicant_2", JobApplicationStatus.PENDING) } returns true

        When("applying again") {
            Then("it throws JobApplicationAlreadyPendingException rather than creating a second row") {
                try {
                    service.apply("applicant_2", "job_post_2", "Please consider me again")
                    error("expected JobApplicationAlreadyPendingException")
                } catch (e: JobApplicationAlreadyPendingException) {
                    verify(exactly = 0) { jobApplicationRepository.save(any()) }
                }
            }
        }
    }

    // Real bug found live (2026-08-02): the plain
    // "existsByJobPostIdAndApplicantIdAndStatus(..., PENDING)" check used to read-then-
    // CREATE with nothing serializing two concurrent callers -- this simulates the
    // second caller's view of the world AFTER the first caller has already locked and
    // committed a PENDING application, the exact real-world moment the fix's locked
    // re-check exists to catch.
    Given("two concurrent applications racing for the same applicant and job post") {
        val jobApplicationRepository = mockk<JobApplicationRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val resumeService = mockk<ResumeService>(relaxed = true)
        val objectMapper = mockk<com.fasterxml.jackson.databind.ObjectMapper>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = JobApplicationService(jobApplicationRepository, jobPostRepository, rateLimiter, messagingService, resumeService, objectMapper, notificationRepository, pushNotificationService)

        val post = openJobPost("job_post_3", "poster_3")
        every { jobPostRepository.findById("job_post_3") } returns Optional.of(post)
        every { jobPostRepository.findByIdForUpdate("job_post_3") } returns Optional.of(post)
        // The racing caller's PENDING application only becomes visible AFTER this
        // caller acquires the post-row lock -- modeled directly as the post-lock
        // existence check returning true, since that's the only check this service
        // makes.
        every { jobApplicationRepository.existsByJobPostIdAndApplicantIdAndStatus("job_post_3", "applicant_3", JobApplicationStatus.PENDING) } returns true

        When("this caller's lock-acquire happens to observe the other caller's already-committed PENDING application") {
            Then("it real-rejects with JobApplicationAlreadyPendingException instead of creating a real duplicate PENDING row") {
                try {
                    service.apply("applicant_3", "job_post_3", "I'd love this job")
                    error("expected JobApplicationAlreadyPendingException")
                } catch (e: JobApplicationAlreadyPendingException) {
                    verify(exactly = 1) { jobPostRepository.findByIdForUpdate("job_post_3") }
                    verify(exactly = 0) { jobApplicationRepository.save(any()) }
                }
            }
        }
    }

    Given("an applicant trying to apply to their own job post") {
        val jobApplicationRepository = mockk<JobApplicationRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val resumeService = mockk<ResumeService>(relaxed = true)
        val objectMapper = mockk<com.fasterxml.jackson.databind.ObjectMapper>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = JobApplicationService(jobApplicationRepository, jobPostRepository, rateLimiter, messagingService, resumeService, objectMapper, notificationRepository, pushNotificationService)

        val post = openJobPost("job_post_4", "poster_4")
        every { jobPostRepository.findById("job_post_4") } returns Optional.of(post)

        When("applying to their own post") {
            Then("it throws OwnJobPostException before ever touching the lock or the application table") {
                try {
                    service.apply("poster_4", "job_post_4", "Hiring myself")
                    error("expected OwnJobPostException")
                } catch (e: OwnJobPostException) {
                    verify(exactly = 0) { jobPostRepository.findByIdForUpdate(any()) }
                    verify(exactly = 0) { jobApplicationRepository.save(any()) }
                }
            }
        }
    }

    Given("an applicant applying to a job post that's no longer open") {
        val jobApplicationRepository = mockk<JobApplicationRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val resumeService = mockk<ResumeService>(relaxed = true)
        val objectMapper = mockk<com.fasterxml.jackson.databind.ObjectMapper>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = JobApplicationService(jobApplicationRepository, jobPostRepository, rateLimiter, messagingService, resumeService, objectMapper, notificationRepository, pushNotificationService)

        val post = openJobPost("job_post_5", "poster_5").apply { status = JobPostStatus.FILLED }
        every { jobPostRepository.findById("job_post_5") } returns Optional.of(post)

        When("applying to a FILLED post") {
            Then("it throws JobPostNotOpenException") {
                try {
                    service.apply("applicant_5", "job_post_5", "Still interested?")
                    error("expected JobPostNotOpenException")
                } catch (e: JobPostNotOpenException) {
                    verify(exactly = 0) { jobApplicationRepository.save(any()) }
                }
            }
        }
    }

    Given("a poster reviewing and accepting a real pending application") {
        val jobApplicationRepository = mockk<JobApplicationRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val resumeService = mockk<ResumeService>(relaxed = true)
        val objectMapper = mockk<com.fasterxml.jackson.databind.ObjectMapper>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = JobApplicationService(jobApplicationRepository, jobPostRepository, rateLimiter, messagingService, resumeService, objectMapper, notificationRepository, pushNotificationService)

        val post = openJobPost("job_post_6", "poster_6")
        val application = JobApplication(id = "job_application_1", jobPostId = "job_post_6", applicantId = "applicant_6", message = "Pick me")
        every { jobApplicationRepository.findById("job_application_1") } returns Optional.of(application)
        every { jobPostRepository.findById("job_post_6") } returns Optional.of(post)
        every { jobApplicationRepository.save(any()) } answers { firstArg() }
        val conversation = rw.itunda.core.domain.Conversation(id = "conversation_1", participantAId = "applicant_6", participantBId = "poster_6")
        every { messagingService.startOrGetConversation("poster_6", "applicant_6") } returns conversation

        When("accepting") {
            val (decided, resultConversation) = service.respond("poster_6", "job_application_1", accept = true)

            Then("it marks the application ACCEPTED and opens a real conversation") {
                decided.status shouldBe JobApplicationStatus.ACCEPTED
                resultConversation?.id shouldBe "conversation_1"
            }

            // Real gap found live (2026-09-13, sibling-asymmetry sweep): this terminal
            // decision used to notify nobody -- only a silent Conversation row on
            // accept, absolutely nothing on decline.
            Then("the real applicant is notified their application was accepted") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "applicant_6" && it.type == "JOB_APPLICATION_DECIDED" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("applicant_6", "Your application was accepted", any(), any()) }
            }
        }
    }

    Given("a poster reviewing and declining a real pending application") {
        val jobApplicationRepository = mockk<JobApplicationRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>(relaxed = true)
        val resumeService = mockk<ResumeService>(relaxed = true)
        val objectMapper = mockk<com.fasterxml.jackson.databind.ObjectMapper>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = JobApplicationService(jobApplicationRepository, jobPostRepository, rateLimiter, messagingService, resumeService, objectMapper, notificationRepository, pushNotificationService)

        val post = openJobPost("job_post_8", "poster_8")
        val application = JobApplication(id = "job_application_3", jobPostId = "job_post_8", applicantId = "applicant_8", message = "Pick me")
        every { jobApplicationRepository.findById("job_application_3") } returns Optional.of(application)
        every { jobPostRepository.findById("job_post_8") } returns Optional.of(post)
        every { jobApplicationRepository.save(any()) } answers { firstArg() }

        When("declining") {
            val (decided, resultConversation) = service.respond("poster_8", "job_application_3", accept = false)

            Then("it marks the application DECLINED and never opens a conversation") {
                decided.status shouldBe JobApplicationStatus.DECLINED
                resultConversation shouldBe null
            }

            // Real gap found live (2026-09-13, sibling-asymmetry sweep): a decline used
            // to notify the applicant of absolutely nothing at all.
            Then("the real applicant is still notified, even though there's no conversation to open") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "applicant_8" && it.type == "JOB_APPLICATION_DECIDED" }) }
                verify(exactly = 1) { pushNotificationService.sendToUser("applicant_8", "Your application wasn't selected", any(), any()) }
            }
        }
    }

    Given("a stranger (not the real poster) trying to review an application") {
        val jobApplicationRepository = mockk<JobApplicationRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val resumeService = mockk<ResumeService>(relaxed = true)
        val objectMapper = mockk<com.fasterxml.jackson.databind.ObjectMapper>(relaxed = true)
        val notificationRepository = mockk<rw.itunda.core.repository.NotificationRepository>(relaxed = true).also { every { it.save(any()) } answers { firstArg() } }
        val pushNotificationService = mockk<rw.itunda.core.push.PushNotificationService>(relaxed = true)
        val service = JobApplicationService(jobApplicationRepository, jobPostRepository, rateLimiter, messagingService, resumeService, objectMapper, notificationRepository, pushNotificationService)

        val post = openJobPost("job_post_7", "poster_7")
        val application = JobApplication(id = "job_application_2", jobPostId = "job_post_7", applicantId = "applicant_7", message = "Pick me")
        every { jobApplicationRepository.findById("job_application_2") } returns Optional.of(application)
        every { jobPostRepository.findById("job_post_7") } returns Optional.of(post)

        When("a stranger tries to accept it") {
            Then("it throws JobApplicationNotFoundException -- 404, never 403, so a stranger can't even confirm the application exists") {
                try {
                    service.respond("stranger_1", "job_application_2", accept = true)
                    error("expected JobApplicationNotFoundException")
                } catch (e: JobApplicationNotFoundException) {
                    verify(exactly = 0) { jobApplicationRepository.save(any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
