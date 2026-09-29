package rw.itunda.jobs

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
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

class ResumeServiceTest : BehaviorSpec({

    Given("a real user with no résumé yet, updating their profile") {
        val resumeRepository = mockk<ResumeRepository>()
        val experienceRepository = mockk<ResumeExperienceRepository>(relaxed = true)
        val educationRepository = mockk<ResumeEducationRepository>(relaxed = true)
        val certificationRepository = mockk<ResumeCertificationRepository>(relaxed = true)
        val service = ResumeService(resumeRepository, experienceRepository, educationRepository, certificationRepository, mockk(relaxed = true))

        every { resumeRepository.findByUserId("user_1") } returns null
        val savedSlot = slot<Resume>()
        every { resumeRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("submitting a real self-intro and real preset strengths") {
            val resume = service.updateProfile("user_1", "  I'm a hard worker.  ", listOf("friendly", "diligent"), null)

            Then("it real-creates a résumé row (lazily, on first real write)") {
                resume.selfIntro shouldBe "I'm a hard worker."
                resume.strengths shouldBe "friendly,diligent"
                verify { resumeRepository.save(any()) }
            }
        }

        When("submitting an unknown strength") {
            Then("it throws InvalidResumeException") {
                try {
                    service.updateProfile("user_1", null, listOf("not_a_real_strength"), null)
                    error("expected InvalidResumeException")
                } catch (e: InvalidResumeException) {
                    // expected
                }
            }
        }

        When("submitting a self-intro over the real 2000-char DB column bound") {
            Then("it throws InvalidResumeException rather than risking a raw DB insert failure") {
                try {
                    service.updateProfile("user_1", "x".repeat(2001), emptyList(), null)
                    error("expected InvalidResumeException")
                } catch (e: InvalidResumeException) {
                    // expected
                }
            }
        }
    }

    Given("a real résumé with real experience/education/certification entries") {
        val resumeRepository = mockk<ResumeRepository>()
        val experienceRepository = mockk<ResumeExperienceRepository>()
        val educationRepository = mockk<ResumeEducationRepository>()
        val certificationRepository = mockk<ResumeCertificationRepository>()
        val service = ResumeService(resumeRepository, experienceRepository, educationRepository, certificationRepository, mockk(relaxed = true))

        val resume = Resume(id = "resume_1", userId = "user_1", selfIntro = "Hi", strengths = "friendly")
        every { resumeRepository.findByUserId("user_1") } returns resume
        every { experienceRepository.findByResumeIdOrderByCreatedAtDesc("resume_1") } returns
            listOf(ResumeExperience(id = "exp_1", resumeId = "resume_1", company = "Acme", role = "Rider", period = "2024-2025"))
        every { educationRepository.findByResumeIdOrderByCreatedAtDesc("resume_1") } returns
            listOf(ResumeEducation(id = "edu_1", resumeId = "resume_1", school = "Kigali High"))
        every { certificationRepository.findByResumeIdOrderByCreatedAtDesc("resume_1") } returns emptyList()

        When("fetching the real résumé") {
            val detail = service.getResume("user_1")

            Then("it returns real experience/education, and an honest completion percent (self-intro + strengths + experience + education = 4/5 steps, no certifications)") {
                detail.experiences.size shouldBe 1
                detail.educations.size shouldBe 1
                detail.certifications.size shouldBe 0
                detail.completionPercent shouldBe 80
            }
        }

        When("adding a real certification") {
            val savedSlot = slot<ResumeCertification>()
            every { certificationRepository.save(capture(savedSlot)) } answers { firstArg() }

            service.addCertification("user_1", "First Aid", "2024-01")

            Then("it real-persists against the real existing résumé, not a new one") {
                savedSlot.captured.resumeId shouldBe "resume_1"
                savedSlot.captured.name shouldBe "First Aid"
            }
        }

        When("removing an experience entry that doesn't belong to this user's résumé") {
            every { experienceRepository.deleteByIdAndResumeId("not_mine", "resume_1") } returns 0L

            Then("it throws ResumeEntryNotFoundException rather than silently no-op-ing") {
                try {
                    service.removeExperience("user_1", "not_mine")
                    error("expected ResumeEntryNotFoundException")
                } catch (e: ResumeEntryNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a caller who has exceeded the real rate limit") {
        val resumeRepository = mockk<ResumeRepository>()
        val experienceRepository = mockk<ResumeExperienceRepository>()
        val educationRepository = mockk<ResumeEducationRepository>()
        val certificationRepository = mockk<ResumeCertificationRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val service = ResumeService(resumeRepository, experienceRepository, educationRepository, certificationRepository, rateLimiter)
        every {
            rateLimiter.checkLimit("resume:update:user_1", limit = 10, window = Duration.ofHours(1))
        } throws RateLimitExceededException("Too many requests")

        When("updating their profile") {
            Then("it real-propagates RateLimitExceededException rather than a silent unbounded write") {
                try {
                    service.updateProfile("user_1", "Hi", emptyList(), null)
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    verify(exactly = 0) { resumeRepository.save(any()) }
                }
            }
        }
    }

    Given("a user with genuinely no résumé at all") {
        val resumeRepository = mockk<ResumeRepository>()
        val experienceRepository = mockk<ResumeExperienceRepository>(relaxed = true)
        val educationRepository = mockk<ResumeEducationRepository>(relaxed = true)
        val certificationRepository = mockk<ResumeCertificationRepository>(relaxed = true)
        val service = ResumeService(resumeRepository, experienceRepository, educationRepository, certificationRepository, mockk(relaxed = true))
        every { resumeRepository.findByUserId("user_2") } returns null

        When("fetching it") {
            val detail = service.getResume("user_2")

            Then("it honestly returns an empty résumé with 0% completion, not an error") {
                detail.resume shouldBe null
                detail.completionPercent shouldBe 0
            }
        }
    }
})
