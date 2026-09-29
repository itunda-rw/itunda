package rw.itunda.jobs

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Conversation
import rw.itunda.core.domain.JobPayType
import rw.itunda.core.domain.JobPost
import rw.itunda.core.domain.JobPostStatus
import rw.itunda.core.domain.User
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.repository.JobPostRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.trust.TrustScoreService
import rw.itunda.messaging.MessagingService
import rw.itunda.messaging.SelfConversationException
import java.math.BigDecimal
import java.time.Duration
import java.util.Optional

class JobPostServiceTest : BehaviorSpec({

    Given("a real poster creating a job listing") {
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = JobPostService(jobPostRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)

        When("posting with valid fields") {
            val savedSlot = slot<JobPost>()
            every { jobPostRepository.save(capture(savedSlot)) } answers { firstArg() }
            every { userRepository.findById("poster_1") } returns java.util.Optional.empty()

            val post = service.createPost(
                "poster_1", "delivery", "  Weekend delivery rider needed  ", "  Deliver parcels in Kimironko  ",
                JobPayType.HOURLY, BigDecimal("1500"),
            )

            Then("it trims text and defaults to OPEN") {
                post.title shouldBe "Weekend delivery rider needed"
                post.description shouldBe "Deliver parcels in Kimironko"
                post.status shouldBe JobPostStatus.OPEN
                post.payType shouldBe JobPayType.HOURLY
            }
        }

        When("posting with an unknown category") {
            Then("it throws InvalidJobPostException") {
                try {
                    service.createPost("poster_1", "not_real", "Title", "Body", JobPayType.FIXED, BigDecimal("1000"))
                    error("expected InvalidJobPostException")
                } catch (e: InvalidJobPostException) {
                    // expected
                }
            }
        }

        When("posting with a zero pay amount") {
            Then("it throws InvalidJobPostException") {
                try {
                    service.createPost("poster_1", "delivery", "Title", "Body", JobPayType.FIXED, BigDecimal.ZERO)
                    error("expected InvalidJobPostException")
                } catch (e: InvalidJobPostException) {
                    // expected
                }
            }
        }

        When("posting with a blank title") {
            Then("it throws InvalidJobPostException") {
                try {
                    service.createPost("poster_1", "delivery", "   ", "Body", JobPayType.FIXED, BigDecimal("1000"))
                    error("expected InvalidJobPostException")
                } catch (e: InvalidJobPostException) {
                    // expected
                }
            }
        }

        When("posting with a title longer than the real 200-char DB column bound") {
            Then("it throws InvalidJobPostException rather than risking a raw DB insert failure") {
                try {
                    service.createPost("poster_1", "delivery", "x".repeat(201), "Body", JobPayType.FIXED, BigDecimal("1000"))
                    error("expected InvalidJobPostException")
                } catch (e: InvalidJobPostException) {
                    // expected
                }
            }
        }

        When("posting with only one of latitude/longitude") {
            Then("it throws InvalidJobCoordinatesException") {
                try {
                    service.createPost(
                        "poster_1", "delivery", "Title", "Body", JobPayType.FIXED, BigDecimal("1000"),
                        latitude = -1.9441, longitude = null,
                    )
                    error("expected InvalidJobCoordinatesException")
                } catch (e: InvalidJobCoordinatesException) {
                    // expected
                }
            }
        }

        When("a real poster exceeds the real post-creation rate limit") {
            every { rateLimiter.checkLimit("jobs:post:poster_1", limit = 10, window = Duration.ofHours(1)) } throws
                RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException") {
                try {
                    service.createPost("poster_1", "delivery", "Title", "Body", JobPayType.FIXED, BigDecimal("1000"))
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
            }
        }
    }

    Given("an existing real job post") {
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = JobPostService(jobPostRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)
        val post = JobPost(
            id = "job_post_1", posterId = "poster_1", category = "delivery", title = "T", description = "D",
            payType = JobPayType.FIXED, payAmount = BigDecimal("5000"),
        )

        When("the real poster marks it filled") {
            every { jobPostRepository.findById("job_post_1") } returns Optional.of(post)
            every { jobPostRepository.save(any()) } answers { firstArg() }

            val result = service.markFilled("poster_1", "job_post_1")

            Then("its status flips to FILLED") {
                result.status shouldBe JobPostStatus.FILLED
            }
            Then("the real Karrot-Score-style trust badge is recomputed for the poster immediately") {
                verify(exactly = 1) { trustScoreService.computeScore("poster_1") }
            }
        }

        When("someone who doesn't own it tries to mark it filled") {
            val freshPost = JobPost(
                id = "job_post_1", posterId = "poster_1", category = "delivery", title = "T", description = "D",
                payType = JobPayType.FIXED, payAmount = BigDecimal("5000"),
            )
            every { jobPostRepository.findById("job_post_1") } returns Optional.of(freshPost)

            Then("it throws JobPostNotFoundException, not revealing the post exists") {
                try {
                    service.markFilled("stranger", "job_post_1")
                    error("expected JobPostNotFoundException")
                } catch (e: JobPostNotFoundException) {
                    // expected
                }
            }
        }

        When("the real poster tries to mark an already-FILLED post filled again") {
            val filledPost = JobPost(
                id = "job_post_1", posterId = "poster_1", category = "delivery", title = "T", description = "D",
                payType = JobPayType.FIXED, payAmount = BigDecimal("5000"), status = JobPostStatus.FILLED,
            )
            every { jobPostRepository.findById("job_post_1") } returns Optional.of(filledPost)

            Then("it throws JobPostNotOpenException") {
                try {
                    service.markFilled("poster_1", "job_post_1")
                    error("expected JobPostNotOpenException")
                } catch (e: JobPostNotOpenException) {
                    // expected
                }
            }
        }

        When("a real remove by the real poster") {
            val freshPost = JobPost(
                id = "job_post_1", posterId = "poster_1", category = "delivery", title = "T", description = "D",
                payType = JobPayType.FIXED, payAmount = BigDecimal("5000"),
            )
            every { jobPostRepository.findById("job_post_1") } returns Optional.of(freshPost)
            every { jobPostRepository.save(any()) } answers { firstArg() }

            val result = service.removePost("poster_1", "job_post_1")

            Then("its status flips to REMOVED") {
                result.status shouldBe JobPostStatus.REMOVED
            }
        }

        When("a real applicant contacts the poster") {
            val freshPost = JobPost(
                id = "job_post_1", posterId = "poster_1", category = "delivery", title = "T", description = "D",
                payType = JobPayType.FIXED, payAmount = BigDecimal("5000"),
            )
            val conversation = Conversation(id = "conv_1", participantAId = "applicant_1", participantBId = "poster_1")
            every { jobPostRepository.findById("job_post_1") } returns Optional.of(freshPost)
            every { messagingService.startOrGetConversation("applicant_1", "poster_1") } returns conversation

            val result = service.contactPoster("applicant_1", "job_post_1")

            Then("it reuses MessagingService's real conversation unmodified") {
                result.id shouldBe "conv_1"
            }
        }

        When("the real poster tries to contact themselves about their own post") {
            val freshPost = JobPost(
                id = "job_post_1", posterId = "poster_1", category = "delivery", title = "T", description = "D",
                payType = JobPayType.FIXED, payAmount = BigDecimal("5000"),
            )
            every { jobPostRepository.findById("job_post_1") } returns Optional.of(freshPost)
            every { messagingService.startOrGetConversation("poster_1", "poster_1") } throws SelfConversationException("self")

            Then("it throws OwnJobPostException") {
                try {
                    service.contactPoster("poster_1", "job_post_1")
                    error("expected OwnJobPostException")
                } catch (e: OwnJobPostException) {
                    // expected
                }
            }
        }
    }

    Given("a real browse request") {
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = JobPostService(jobPostRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)

        When("no category filter is given") {
            val page = PageImpl(listOf<JobPost>())
            every { jobPostRepository.findByStatusOrderByCreatedAtDesc(JobPostStatus.OPEN, any()) } returns page

            service.browse(PageRequest.of(0, 20), null)

            Then("it browses OPEN posts unfiltered") {
                verify { jobPostRepository.findByStatusOrderByCreatedAtDesc(JobPostStatus.OPEN, any()) }
            }
        }

        When("a category filter is given") {
            val page = PageImpl(listOf<JobPost>())
            every { jobPostRepository.findByStatusAndCategoryOrderByCreatedAtDesc(JobPostStatus.OPEN, "tutoring", any()) } returns page

            service.browse(PageRequest.of(0, 20), "tutoring")

            Then("it filters by that real category") {
                verify { jobPostRepository.findByStatusAndCategoryOrderByCreatedAtDesc(JobPostStatus.OPEN, "tutoring", any()) }
            }
        }
    }

    Given("a real proximity ('near me') browse") {
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = JobPostService(jobPostRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)

        val near = JobPost(
            id = "job_near", posterId = "a", category = "cleaning", title = "T", description = "D",
            payType = JobPayType.HOURLY, payAmount = BigDecimal("1000"), latitude = -1.9500, longitude = 30.0619,
        )
        val far = JobPost(
            id = "job_far", posterId = "a", category = "cleaning", title = "T", description = "D",
            payType = JobPayType.HOURLY, payAmount = BigDecimal("1000"), latitude = -1.5, longitude = 30.0619,
        )

        When("searching a 5km radius around real Kigali-center coordinates") {
            every { jobPostRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(JobPostStatus.OPEN) } returns listOf(near, far)

            val result = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("only the real near post is returned") {
                result.content.map { it.id } shouldBe listOf("job_near")
            }
        }

        When("radiusKm is zero or negative") {
            Then("it throws InvalidJobCoordinatesException") {
                try {
                    service.nearby(-1.9441, 30.0619, 0.0, PageRequest.of(0, 20))
                    error("expected InvalidJobCoordinatesException")
                } catch (e: InvalidJobCoordinatesException) {
                    // expected
                }
            }
        }
    }

    Given("a real caller browsing their own real neighborhood") {
        val jobPostRepository = mockk<JobPostRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val messagingService = mockk<MessagingService>()
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val userRepository = mockk<UserRepository>()
        val trustScoreService = mockk<TrustScoreService>(relaxed = true)
        val service = JobPostService(jobPostRepository, rateLimiter, messagingService, nominatimGeocodingClient, userRepository, trustScoreService)

        When("the caller has a real neighborhood set") {
            val caller = User(id = "user_1", phoneNumber = "+250780000001", firstName = "A", lastName = "B", passwordHash = "x", neighborhood = "Kimironko")
            val expectedPage = PageImpl(listOf(mockk<JobPost>()))
            every { userRepository.findById("user_1") } returns Optional.of(caller)
            every { jobPostRepository.findByStatusAndNeighborhoodInOrderByCreatedAtDesc(JobPostStatus.OPEN, listOf("Kimironko"), any()) } returns expectedPage

            val page = service.myNeighborhood("user_1", null, PageRequest.of(0, 20))

            Then("it real-filters to exactly that neighborhood") {
                page shouldBe expectedPage
            }
        }

        When("the caller hasn't set a real neighborhood yet") {
            val caller = User(id = "user_1", phoneNumber = "+250780000001", firstName = "A", lastName = "B", passwordHash = "x")
            every { userRepository.findById("user_1") } returns Optional.of(caller)

            Then("it throws JobsNeighborhoodNotSetException rather than silently returning an empty page") {
                try {
                    service.myNeighborhood("user_1", null, PageRequest.of(0, 20))
                    error("expected JobsNeighborhoodNotSetException")
                } catch (e: JobsNeighborhoodNotSetException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
