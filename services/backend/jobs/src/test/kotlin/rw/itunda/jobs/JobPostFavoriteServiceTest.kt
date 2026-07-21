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
import rw.itunda.core.domain.JobPayType
import rw.itunda.core.domain.JobPost
import rw.itunda.core.domain.JobPostFavorite
import rw.itunda.core.repository.JobPostFavoriteRepository
import rw.itunda.core.repository.JobPostRepository
import java.math.BigDecimal
import java.util.Optional

class JobPostFavoriteServiceTest : BehaviorSpec({

    Given("a real job post on the board") {
        val jobPostFavoriteRepository = mockk<JobPostFavoriteRepository>()
        val jobPostRepository = mockk<JobPostRepository>()
        val service = JobPostFavoriteService(jobPostFavoriteRepository, jobPostRepository)

        val post = JobPost(
            id = "job_post_1", posterId = "poster_1", category = "delivery", title = "Real evening delivery run",
            description = "3 hours, real pay", payType = JobPayType.HOURLY, payAmount = BigDecimal("2500"),
        )

        When("favoriting it for the real first time") {
            every { jobPostRepository.findById("job_post_1") } returns Optional.of(post)
            every { jobPostFavoriteRepository.findByUserIdAndJobPostId("worker_1", "job_post_1") } returns null
            val savedSlot = slot<JobPostFavorite>()
            every { jobPostFavoriteRepository.save(capture(savedSlot)) } answers { firstArg() }

            val favorite = service.addFavorite("worker_1", "job_post_1")

            Then("it persists a real new favorite") {
                favorite.userId shouldBe "worker_1"
                favorite.jobPostId shouldBe "job_post_1"
                savedSlot.captured.jobPostId shouldBe "job_post_1"
            }
        }

        When("favoriting an already-favorited job post") {
            every { jobPostRepository.findById("job_post_1") } returns Optional.of(post)
            val existing = JobPostFavorite(id = "job_post_favorite_1", userId = "worker_1", jobPostId = "job_post_1")
            every { jobPostFavoriteRepository.findByUserIdAndJobPostId("worker_1", "job_post_1") } returns existing

            val favorite = service.addFavorite("worker_1", "job_post_1")

            Then("it idempotently returns the real existing favorite, never a duplicate") {
                favorite shouldBe existing
                verify(exactly = 0) { jobPostFavoriteRepository.save(any()) }
            }
        }

        When("favoriting a job post that doesn't exist") {
            every { jobPostRepository.findById("ghost") } returns Optional.empty()

            Then("it throws FavoriteJobPostNotFoundException") {
                try {
                    service.addFavorite("worker_1", "ghost")
                    error("expected FavoriteJobPostNotFoundException")
                } catch (e: FavoriteJobPostNotFoundException) {
                    // expected
                }
            }
        }

        When("un-favoriting a job post that was never favorited") {
            every { jobPostFavoriteRepository.deleteByUserIdAndJobPostId("worker_1", "never_favorited") } returns 0L

            Then("it silently no-ops rather than throwing") {
                service.removeFavorite("worker_1", "never_favorited")
                verify { jobPostFavoriteRepository.deleteByUserIdAndJobPostId("worker_1", "never_favorited") }
            }
        }

        When("listing a real worker's favorites") {
            val favorite = JobPostFavorite(id = "job_post_favorite_1", userId = "worker_1", jobPostId = "job_post_1")
            every { jobPostFavoriteRepository.findByUserIdOrderByCreatedAtDesc("worker_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 20), 1)
            every { jobPostRepository.findAllById(listOf("job_post_1")) } returns listOf(post)

            val page = service.getMyFavorites("worker_1", PageRequest.of(0, 20))

            Then("it resolves the real job post's current title/pay/category") {
                page.content.single().jobPostId shouldBe "job_post_1"
                page.content.single().title shouldBe "Real evening delivery run"
                page.content.single().payAmount shouldBe BigDecimal("2500")
                page.content.single().category shouldBe "delivery"
            }
        }

        When("a favorited job post no longer exists") {
            val favorite = JobPostFavorite(id = "job_post_favorite_2", userId = "worker_1", jobPostId = "deleted_post")
            every { jobPostFavoriteRepository.findByUserIdOrderByCreatedAtDesc("worker_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 20), 1)
            every { jobPostRepository.findAllById(listOf("deleted_post")) } returns emptyList()

            val page = service.getMyFavorites("worker_1", PageRequest.of(0, 20))

            Then("it falls back to an honest placeholder rather than crashing") {
                page.content.single().title shouldBe "Job post no longer available"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
