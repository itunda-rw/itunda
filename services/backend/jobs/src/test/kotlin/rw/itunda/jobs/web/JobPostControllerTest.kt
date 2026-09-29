package rw.itunda.jobs.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.HoodTransactionReview
import rw.itunda.core.domain.HoodTransactionType
import rw.itunda.core.domain.JobPayType
import rw.itunda.core.domain.JobPost
import rw.itunda.core.repository.UserRepository
import rw.itunda.core.review.HoodReviewService
import rw.itunda.core.security.CurrentUser
import rw.itunda.jobs.JobApplicationService
import rw.itunda.jobs.JobPostFavoriteService
import rw.itunda.jobs.JobPostService
import java.math.BigDecimal

/**
 * First test coverage for JobPostController -- one of the 4 highest-transaction-
 * volume Hood controllers, previously untested despite every service class in this
 * ecosystem already having its own test file. Covers real delegation (caller-scoped
 * userId, never client-supplied). Exception-handler mapping lives in
 * JobPostControllerExceptionHandlingTest.kt, split out to stay under the 500-line
 * guideline.
 */
class JobPostControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")
    val pageable = PageRequest.of(0, 20)

    fun controller(
        jobPostService: JobPostService = mockk(),
        jobPostFavoriteService: JobPostFavoriteService = mockk(relaxed = true),
        userRepository: UserRepository = mockk(),
        hoodReviewService: HoodReviewService = mockk(),
        jobApplicationService: JobApplicationService = mockk(),
    ) = JobPostController(jobPostService, jobPostFavoriteService, userRepository, hoodReviewService, jobApplicationService)

    Given("a real categories request") {
        val ctl = controller()

        When("fetching them") {
            val response = ctl.categories()
            Then("it real-returns the backend's own category list") {
                response.body?.get("categories") shouldBe JobPostService.CATEGORIES
            }
        }
    }

    Given("a real job-post creation") {
        val jobPostService = mockk<JobPostService>()
        val ctl = controller(jobPostService = jobPostService)
        val post = mockk<JobPost>(relaxed = true)
        val request = CreateJobPostRequest("delivery", "Move a couch", "Need help moving", JobPayType.FIXED, BigDecimal("5000"))
        every {
            jobPostService.createPost("user_1", "delivery", "Move a couch", "Need help moving", JobPayType.FIXED, BigDecimal("5000"), null, null)
        } returns post

        When("creating it") {
            ctl.createPost(request, currentUser)
            Then("it queries scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) {
                    jobPostService.createPost("user_1", "delivery", "Move a couch", "Need help moving", JobPayType.FIXED, BigDecimal("5000"), null, null)
                }
            }
        }
    }

    Given("a real browse request") {
        val jobPostService = mockk<JobPostService>()
        val ctl = controller(jobPostService = jobPostService)
        every { jobPostService.browse(pageable, "delivery") } returns PageImpl(emptyList())

        When("browsing by category") {
            ctl.browse("delivery", pageable)
            Then("it real-delegates the caller-selected category") {
                verify(exactly = 1) { jobPostService.browse(pageable, "delivery") }
            }
        }
    }

    Given("a real nearby request") {
        val jobPostService = mockk<JobPostService>()
        val ctl = controller(jobPostService = jobPostService)
        every { jobPostService.nearby(-1.9, 30.0, 5.0, pageable) } returns PageImpl(emptyList())

        When("fetching nearby posts") {
            ctl.nearby(-1.9, 30.0, 5.0, pageable)
            Then("it real-delegates the caller's real coordinates") {
                verify(exactly = 1) { jobPostService.nearby(-1.9, 30.0, 5.0, pageable) }
            }
        }
    }

    Given("a real my-neighborhood request") {
        val jobPostService = mockk<JobPostService>()
        val ctl = controller(jobPostService = jobPostService)
        every { jobPostService.myNeighborhood("user_1", null, pageable) } returns PageImpl(emptyList())

        When("browsing it") {
            ctl.myNeighborhood(null, pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { jobPostService.myNeighborhood("user_1", null, pageable) }
            }
        }
    }

    Given("a real search request") {
        val jobPostService = mockk<JobPostService>()
        val ctl = controller(jobPostService = jobPostService)
        every { jobPostService.search("couch", pageable) } returns PageImpl(emptyList())

        When("searching") {
            ctl.search("couch", pageable)
            Then("it real-delegates the caller's real query") {
                verify(exactly = 1) { jobPostService.search("couch", pageable) }
            }
        }
    }

    Given("a real my-posts request") {
        val jobPostService = mockk<JobPostService>()
        val ctl = controller(jobPostService = jobPostService)
        every { jobPostService.getMyPosts("user_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.myPosts(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { jobPostService.getMyPosts("user_1", pageable) }
            }
        }
    }

    Given("a real my-worked-posts request") {
        val jobPostService = mockk<JobPostService>()
        val ctl = controller(jobPostService = jobPostService)
        every { jobPostService.getMyWorkedPosts("user_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.myWorkedPosts(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { jobPostService.getMyWorkedPosts("user_1", pageable) }
            }
        }
    }

    Given("a real single-post request") {
        val jobPostService = mockk<JobPostService>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val ctl = controller(jobPostService = jobPostService, userRepository = userRepository)
        val post = mockk<JobPost>(relaxed = true)
        every { post.posterId } returns "poster_1"
        every { jobPostService.getPost("post_1") } returns post

        When("fetching it") {
            ctl.getPost("post_1")
            Then("it real-delegates by the real post id") {
                verify(exactly = 1) { jobPostService.getPost("post_1") }
            }
        }
    }

    Given("a real mark-filled request") {
        val jobPostService = mockk<JobPostService>()
        val jobPostFavoriteService = mockk<JobPostFavoriteService>(relaxed = true)
        val ctl = controller(jobPostService = jobPostService, jobPostFavoriteService = jobPostFavoriteService)
        val post = mockk<JobPost>(relaxed = true)
        every { jobPostService.markFilled("user_1", "post_1", "0788000000") } returns post

        When("marking it filled") {
            ctl.markFilled("post_1", MarkFilledRequest("0788000000"), currentUser)
            Then("it queries scoped to the caller's own userId and real-notifies favoriters of closure") {
                verify(exactly = 1) { jobPostService.markFilled("user_1", "post_1", "0788000000") }
                verify(exactly = 1) { jobPostFavoriteService.notifyFavoritersOfClosure(post) }
            }
        }
    }

    Given("a real review submission") {
        val hoodReviewService = mockk<HoodReviewService>()
        val ctl = controller(hoodReviewService = hoodReviewService)
        val review = mockk<HoodTransactionReview>(relaxed = true)
        every {
            hoodReviewService.submitReview("user_1", HoodTransactionType.JOB_POST, "post_1", listOf("punctual"), emptyList())
        } returns review

        When("submitting it") {
            ctl.submitReview("post_1", SubmitHoodReviewRequest(listOf("punctual")), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { hoodReviewService.submitReview("user_1", HoodTransactionType.JOB_POST, "post_1", listOf("punctual"), emptyList()) }
            }
        }
    }

    Given("a real review-list request") {
        val hoodReviewService = mockk<HoodReviewService>()
        val ctl = controller(hoodReviewService = hoodReviewService)
        every { hoodReviewService.getTransactionReviews("user_1", HoodTransactionType.JOB_POST, "post_1") } returns emptyList()

        When("fetching them") {
            ctl.getReviews("post_1", currentUser)
            Then("it queries scoped to the caller's own userId, never an unrelated viewer") {
                verify(exactly = 1) { hoodReviewService.getTransactionReviews("user_1", HoodTransactionType.JOB_POST, "post_1") }
            }
        }
    }

    Given("a real post removal") {
        val jobPostService = mockk<JobPostService>()
        val jobPostFavoriteService = mockk<JobPostFavoriteService>(relaxed = true)
        val ctl = controller(jobPostService = jobPostService, jobPostFavoriteService = jobPostFavoriteService)
        val post = mockk<JobPost>(relaxed = true)
        every { jobPostService.removePost("user_1", "post_1") } returns post

        When("removing it") {
            ctl.removePost("post_1", currentUser)
            Then("it queries scoped to the caller's own userId and real-notifies favoriters of closure") {
                verify(exactly = 1) { jobPostService.removePost("user_1", "post_1") }
                verify(exactly = 1) { jobPostFavoriteService.notifyFavoritersOfClosure(post) }
            }
        }
    }

    Given("a real favorite-add") {
        val jobPostFavoriteService = mockk<JobPostFavoriteService>()
        val ctl = controller(jobPostFavoriteService = jobPostFavoriteService)
        every { jobPostFavoriteService.addFavorite("user_1", "post_1") } returns mockk(relaxed = true)

        When("adding it") {
            ctl.addFavorite("post_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { jobPostFavoriteService.addFavorite("user_1", "post_1") }
            }
        }
    }

    Given("a real favorite-remove") {
        val jobPostFavoriteService = mockk<JobPostFavoriteService>(relaxed = true)
        val ctl = controller(jobPostFavoriteService = jobPostFavoriteService)

        When("removing it") {
            ctl.removeFavorite("post_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { jobPostFavoriteService.removeFavorite("user_1", "post_1") }
            }
        }
    }

    Given("a real favorites-list request") {
        val jobPostFavoriteService = mockk<JobPostFavoriteService>()
        val ctl = controller(jobPostFavoriteService = jobPostFavoriteService)
        every { jobPostFavoriteService.getMyFavorites("user_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.getMyFavoritePosts(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { jobPostFavoriteService.getMyFavorites("user_1", pageable) }
            }
        }
    }

    Given("a real contact-poster request") {
        val jobPostService = mockk<JobPostService>()
        val ctl = controller(jobPostService = jobPostService)
        every { jobPostService.contactPoster("user_1", "post_1") } returns mockk(relaxed = true)

        When("contacting the poster") {
            ctl.contactPoster("post_1", currentUser)
            Then("it real-passes the caller as applicant, never a client-supplied id") {
                verify(exactly = 1) { jobPostService.contactPoster("user_1", "post_1") }
            }
        }
    }

    Given("a real job application") {
        val jobApplicationService = mockk<JobApplicationService>()
        val ctl = controller(jobApplicationService = jobApplicationService)
        every { jobApplicationService.apply("user_1", "post_1", "I can help") } returns mockk(relaxed = true)

        When("applying") {
            ctl.apply("post_1", ApplyToJobRequest("I can help"), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { jobApplicationService.apply("user_1", "post_1", "I can help") }
            }
        }
    }

    Given("a real request for a post's own applications") {
        val jobApplicationService = mockk<JobApplicationService>()
        val ctl = controller(jobApplicationService = jobApplicationService)
        every { jobApplicationService.getApplicationsForPost("user_1", "post_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.getApplicationsForPost("post_1", pageable, currentUser)
            Then("it queries scoped to the caller's own userId, never an unrelated poster") {
                verify(exactly = 1) { jobApplicationService.getApplicationsForPost("user_1", "post_1", pageable) }
            }
        }
    }

    Given("a real request for the caller's own applications") {
        val jobApplicationService = mockk<JobApplicationService>()
        val ctl = controller(jobApplicationService = jobApplicationService)
        every { jobApplicationService.getMyApplications("user_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.getMyApplications(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { jobApplicationService.getMyApplications("user_1", pageable) }
            }
        }
    }

    Given("a real application response") {
        val jobApplicationService = mockk<JobApplicationService>()
        val ctl = controller(jobApplicationService = jobApplicationService)
        val application = mockk<rw.itunda.core.domain.JobApplication>(relaxed = true)
        every { jobApplicationService.respond("user_1", "app_1", true) } returns (application to mockk(relaxed = true))

        When("accepting it") {
            ctl.respondToApplication("app_1", RespondToApplicationRequest(true), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { jobApplicationService.respond("user_1", "app_1", true) }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
