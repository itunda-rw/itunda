package rw.itunda.community

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
import rw.itunda.core.domain.CommunityComment
import rw.itunda.core.domain.CommunityLike
import rw.itunda.core.domain.CommunityPost
import rw.itunda.core.domain.CommunityPostStatus
import rw.itunda.core.domain.Notification
import rw.itunda.core.domain.User
import rw.itunda.core.repository.CommunityCommentRepository
import rw.itunda.core.repository.CommunityLikeRepository
import rw.itunda.core.repository.CommunityPostRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.util.Optional

private fun realUser(id: String, first: String, last: String) =
    User(id = id, phoneNumber = "+25078800$id", firstName = first, lastName = last, passwordHash = "x")

class CommunityServiceTest : BehaviorSpec({

    Given("a real author creating a post") {
        val postRepository = mockk<CommunityPostRepository>()
        val commentRepository = mockk<CommunityCommentRepository>()
        val likeRepository = mockk<CommunityLikeRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = CommunityService(postRepository, commentRepository, likeRepository, userRepository, notificationRepository, rateLimiter)

        When("posting with valid fields") {
            val savedSlot = slot<CommunityPost>()
            every { postRepository.save(capture(savedSlot)) } answers { firstArg() }

            val post = service.createPost("author_1", "question", "  Any good plumbers nearby?  ", "  Kitchen sink is leaking  ")

            Then("it trims text and defaults to ACTIVE with zero counters") {
                post.title shouldBe "Any good plumbers nearby?"
                post.body shouldBe "Kitchen sink is leaking"
                post.status shouldBe CommunityPostStatus.ACTIVE
                post.likeCount shouldBe 0
                post.commentCount shouldBe 0
            }
        }

        When("posting with an unknown category") {
            Then("it throws InvalidCommunityPostException") {
                try {
                    service.createPost("author_1", "not_a_real_category", "Title", "Body")
                    error("expected InvalidCommunityPostException")
                } catch (e: InvalidCommunityPostException) {
                    // expected
                }
            }
        }

        When("posting with a blank title") {
            Then("it throws InvalidCommunityPostException") {
                try {
                    service.createPost("author_1", "question", "   ", "Body")
                    error("expected InvalidCommunityPostException")
                } catch (e: InvalidCommunityPostException) {
                    // expected
                }
            }
        }

        When("posting with only one of latitude/longitude") {
            Then("it throws InvalidCommunityCoordinatesException") {
                try {
                    service.createPost("author_1", "question", "Title", "Body", latitude = -1.9441, longitude = null)
                    error("expected InvalidCommunityCoordinatesException")
                } catch (e: InvalidCommunityCoordinatesException) {
                    // expected
                }
            }
        }

        When("a real author exceeds the real post-creation rate limit") {
            every { rateLimiter.checkLimit("community:post:author_1", limit = 10, window = Duration.ofHours(1)) } throws
                RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException") {
                try {
                    service.createPost("author_1", "question", "Title", "Body")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
            }
        }
    }

    Given("an existing real post") {
        val postRepository = mockk<CommunityPostRepository>()
        val commentRepository = mockk<CommunityCommentRepository>()
        val likeRepository = mockk<CommunityLikeRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = CommunityService(postRepository, commentRepository, likeRepository, userRepository, notificationRepository, rateLimiter)
        val post = CommunityPost(id = "post_1", authorId = "author_1", category = "question", title = "T", body = "B")

        When("the real author removes it") {
            every { postRepository.findById("post_1") } returns Optional.of(post)
            every { postRepository.save(any()) } answers { firstArg() }

            val result = service.removePost("author_1", "post_1")

            Then("its status flips to REMOVED") {
                result.status shouldBe CommunityPostStatus.REMOVED
            }
        }

        When("someone who doesn't own it tries to remove it") {
            val freshPost = CommunityPost(id = "post_1", authorId = "author_1", category = "question", title = "T", body = "B")
            every { postRepository.findById("post_1") } returns Optional.of(freshPost)

            Then("it throws CommunityPostNotFoundException, not revealing the post exists") {
                try {
                    service.removePost("stranger", "post_1")
                    error("expected CommunityPostNotFoundException")
                } catch (e: CommunityPostNotFoundException) {
                    // expected
                }
            }
        }

        When("a real stranger comments on it") {
            val freshPost = CommunityPost(id = "post_1", authorId = "author_1", category = "question", title = "T", body = "B")
            every { postRepository.findById("post_1") } returns Optional.of(freshPost)
            every { postRepository.save(any()) } answers { firstArg() }
            every { commentRepository.save(any()) } answers { firstArg() }
            every { userRepository.findAllById(listOf("commenter_1")) } returns listOf(realUser("commenter_1", "Jane", "Doe"))
            val notifSlot = slot<Notification>()
            every { notificationRepository.save(capture(notifSlot)) } answers { firstArg() }

            val comment = service.addComment("commenter_1", "post_1", "  Try Kigali Plumbing Co  ")

            Then("the comment is trimmed, the post's comment_count increments, and the real author is notified") {
                comment.body shouldBe "Try Kigali Plumbing Co"
                freshPost.commentCount shouldBe 1
                notifSlot.captured.userId shouldBe "author_1"
                notifSlot.captured.title shouldBe "Jane Doe"
            }
        }

        When("the real author comments on their own post") {
            val freshPost = CommunityPost(id = "post_1", authorId = "author_1", category = "question", title = "T", body = "B")
            every { postRepository.findById("post_1") } returns Optional.of(freshPost)
            every { postRepository.save(any()) } answers { firstArg() }
            every { commentRepository.save(any()) } answers { firstArg() }

            service.addComment("author_1", "post_1", "Following up myself")

            Then("no self-notification is ever sent") {
                verify(exactly = 0) { notificationRepository.save(any()) }
            }
        }

        When("commenting with a blank body") {
            val freshPost = CommunityPost(id = "post_1", authorId = "author_1", category = "question", title = "T", body = "B")
            every { postRepository.findById("post_1") } returns Optional.of(freshPost)

            Then("it throws InvalidCommunityCommentException") {
                try {
                    service.addComment("commenter_1", "post_1", "   ")
                    error("expected InvalidCommunityCommentException")
                } catch (e: InvalidCommunityCommentException) {
                    // expected
                }
            }
        }

        When("a real user likes it for the first time") {
            val freshPost = CommunityPost(id = "post_1", authorId = "author_1", category = "question", title = "T", body = "B")
            every { postRepository.findById("post_1") } returns Optional.of(freshPost)
            every { postRepository.save(any()) } answers { firstArg() }
            every { likeRepository.findByPostIdAndUserId("post_1", "liker_1") } returns null
            every { likeRepository.save(any()) } answers { firstArg() }

            val liked = service.toggleLike("liker_1", "post_1")

            Then("a real like row is created and like_count increments") {
                liked shouldBe true
                freshPost.likeCount shouldBe 1
            }
        }

        When("the same real user toggles the like again") {
            val freshPost = CommunityPost(id = "post_1", authorId = "author_1", category = "question", title = "T", body = "B", likeCount = 1)
            val existingLike = CommunityLike(id = "community_like_1", postId = "post_1", userId = "liker_1")
            every { postRepository.findById("post_1") } returns Optional.of(freshPost)
            every { postRepository.save(any()) } answers { firstArg() }
            every { likeRepository.findByPostIdAndUserId("post_1", "liker_1") } returns existingLike
            every { likeRepository.delete(existingLike) } returns Unit

            val liked = service.toggleLike("liker_1", "post_1")

            Then("the real like row is removed and like_count decrements -- an idempotent toggle, matching EatsFavoriteService's own precedent") {
                liked shouldBe false
                freshPost.likeCount shouldBe 0
            }
        }

        When("a real user exceeds the real like-toggle rate limit") {
            val freshPost = CommunityPost(id = "post_1", authorId = "author_1", category = "question", title = "T", body = "B")
            every { postRepository.findById("post_1") } returns Optional.of(freshPost)
            every { rateLimiter.checkLimit("community:like:liker_1", limit = 60, window = Duration.ofMinutes(1)) } throws
                RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException -- a toggle endpoint gets the same discipline every other mutating endpoint does, per this session's own security-sweep lesson") {
                try {
                    service.toggleLike("liker_1", "post_1")
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
            }
        }
    }

    Given("a real browse request") {
        val postRepository = mockk<CommunityPostRepository>()
        val commentRepository = mockk<CommunityCommentRepository>()
        val likeRepository = mockk<CommunityLikeRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = CommunityService(postRepository, commentRepository, likeRepository, userRepository, notificationRepository, rateLimiter)

        When("no category filter is given") {
            val page = PageImpl(listOf<CommunityPost>())
            every { postRepository.findByStatusOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, any()) } returns page

            service.browse(PageRequest.of(0, 20), null)

            Then("it browses ACTIVE posts unfiltered") {
                verify { postRepository.findByStatusOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, any()) }
            }
        }

        When("a category filter is given") {
            val page = PageImpl(listOf<CommunityPost>())
            every { postRepository.findByStatusAndCategoryOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, "meetup", any()) } returns page

            service.browse(PageRequest.of(0, 20), "meetup")

            Then("it filters by that real category") {
                verify { postRepository.findByStatusAndCategoryOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, "meetup", any()) }
            }
        }
    }

    Given("a real proximity ('near me') browse") {
        val postRepository = mockk<CommunityPostRepository>()
        val commentRepository = mockk<CommunityCommentRepository>()
        val likeRepository = mockk<CommunityLikeRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = CommunityService(postRepository, commentRepository, likeRepository, userRepository, notificationRepository, rateLimiter)

        // Real Kigali-area coordinates, same convention every geo test in this codebase uses.
        val near = CommunityPost(id = "post_near", authorId = "a", category = "news", title = "T", body = "B", latitude = -1.9500, longitude = 30.0619)
        val far = CommunityPost(id = "post_far", authorId = "a", category = "news", title = "T", body = "B", latitude = -1.5, longitude = 30.0619)

        When("searching a 5km radius around real Kigali-center coordinates") {
            every { postRepository.findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(CommunityPostStatus.ACTIVE) } returns listOf(near, far)

            val result = service.nearby(-1.9441, 30.0619, 5.0, PageRequest.of(0, 20))

            Then("only the real near post is returned") {
                result.content.map { it.id } shouldBe listOf("post_near")
            }
        }

        When("radiusKm is zero or negative") {
            Then("it throws InvalidCommunityCoordinatesException") {
                try {
                    service.nearby(-1.9441, 30.0619, 0.0, PageRequest.of(0, 20))
                    error("expected InvalidCommunityCoordinatesException")
                } catch (e: InvalidCommunityCoordinatesException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
