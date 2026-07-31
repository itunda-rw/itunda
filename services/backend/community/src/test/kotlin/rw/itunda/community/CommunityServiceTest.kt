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
import rw.itunda.core.geo.NominatimGeocodingClient
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.CommunityCommentRepository
import rw.itunda.core.repository.CommunityLikeRepository
import rw.itunda.core.repository.CommunityPostRepository
import rw.itunda.core.repository.GroupConversationMemberRepository
import rw.itunda.core.repository.GroupConversationRepository
import rw.itunda.core.repository.MeetupAttendanceRepository
import rw.itunda.core.repository.MeetupSessionRepository
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.UserRepository
import rw.itunda.splitbill.SplitBillService
import java.math.BigDecimal
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
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val splitBillService = mockk<SplitBillService>(relaxed = true)
        val groupConversationRepository = mockk<GroupConversationRepository>(relaxed = true)
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val meetupSessionRepository = mockk<MeetupSessionRepository>(relaxed = true)
        val meetupAttendanceRepository = mockk<MeetupAttendanceRepository>(relaxed = true)
        val service = CommunityService(
            postRepository, commentRepository, likeRepository, userRepository, notificationRepository,
            groupConversationRepository, groupConversationMemberRepository, meetupSessionRepository, meetupAttendanceRepository,
            rateLimiter, nominatimGeocodingClient, pushNotificationService, splitBillService,
        )

        When("posting with valid fields") {
            val savedSlot = slot<CommunityPost>()
            every { postRepository.save(capture(savedSlot)) } answers { firstArg() }
            every { userRepository.findById("author_1") } returns java.util.Optional.empty()

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

        When("posting with a title longer than the real 200-char DB column bound") {
            Then("it throws InvalidCommunityPostException rather than risking a raw DB insert failure") {
                try {
                    service.createPost("author_1", "question", "x".repeat(201), "Body")
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
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val splitBillService = mockk<SplitBillService>(relaxed = true)
        val groupConversationRepository = mockk<GroupConversationRepository>(relaxed = true)
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val meetupSessionRepository = mockk<MeetupSessionRepository>(relaxed = true)
        val meetupAttendanceRepository = mockk<MeetupAttendanceRepository>(relaxed = true)
        val service = CommunityService(
            postRepository, commentRepository, likeRepository, userRepository, notificationRepository,
            groupConversationRepository, groupConversationMemberRepository, meetupSessionRepository, meetupAttendanceRepository,
            rateLimiter, nominatimGeocodingClient, pushNotificationService, splitBillService,
        )
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

            Then("the real post author also gets a real push notification, not just the in-app one") {
                verify(exactly = 1) { pushNotificationService.sendToUser("author_1", "Jane Doe", "Try Kigali Plumbing Co", any()) }
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
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
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
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val splitBillService = mockk<SplitBillService>(relaxed = true)
        val groupConversationRepository = mockk<GroupConversationRepository>(relaxed = true)
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val meetupSessionRepository = mockk<MeetupSessionRepository>(relaxed = true)
        val meetupAttendanceRepository = mockk<MeetupAttendanceRepository>(relaxed = true)
        val service = CommunityService(
            postRepository, commentRepository, likeRepository, userRepository, notificationRepository,
            groupConversationRepository, groupConversationMemberRepository, meetupSessionRepository, meetupAttendanceRepository,
            rateLimiter, nominatimGeocodingClient, pushNotificationService, splitBillService,
        )

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
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val splitBillService = mockk<SplitBillService>(relaxed = true)
        val groupConversationRepository = mockk<GroupConversationRepository>(relaxed = true)
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val meetupSessionRepository = mockk<MeetupSessionRepository>(relaxed = true)
        val meetupAttendanceRepository = mockk<MeetupAttendanceRepository>(relaxed = true)
        val service = CommunityService(
            postRepository, commentRepository, likeRepository, userRepository, notificationRepository,
            groupConversationRepository, groupConversationMemberRepository, meetupSessionRepository, meetupAttendanceRepository,
            rateLimiter, nominatimGeocodingClient, pushNotificationService, splitBillService,
        )

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

    Given("a real caller browsing their own real neighborhood") {
        val postRepository = mockk<CommunityPostRepository>()
        val commentRepository = mockk<CommunityCommentRepository>()
        val likeRepository = mockk<CommunityLikeRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val splitBillService = mockk<SplitBillService>(relaxed = true)
        val groupConversationRepository = mockk<GroupConversationRepository>(relaxed = true)
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val meetupSessionRepository = mockk<MeetupSessionRepository>(relaxed = true)
        val meetupAttendanceRepository = mockk<MeetupAttendanceRepository>(relaxed = true)
        val service = CommunityService(
            postRepository, commentRepository, likeRepository, userRepository, notificationRepository,
            groupConversationRepository, groupConversationMemberRepository, meetupSessionRepository, meetupAttendanceRepository,
            rateLimiter, nominatimGeocodingClient, pushNotificationService, splitBillService,
        )

        When("the caller has a real neighborhood set") {
            val caller = realUser("user_1", "A", "B").also { it.neighborhood = "Kimironko" }
            val expectedPage = PageImpl(listOf(mockk<CommunityPost>()))
            every { userRepository.findById("user_1") } returns Optional.of(caller)
            every { postRepository.findByStatusAndNeighborhoodOrderByCreatedAtDesc(CommunityPostStatus.ACTIVE, "Kimironko", any()) } returns expectedPage

            val page = service.myNeighborhood("user_1", null, PageRequest.of(0, 20))

            Then("it real-filters to exactly that neighborhood") {
                page shouldBe expectedPage
            }
        }

        When("the caller hasn't set a real neighborhood yet") {
            val caller = realUser("user_1", "A", "B")
            every { userRepository.findById("user_1") } returns Optional.of(caller)

            Then("it throws CommunityNeighborhoodNotSetException rather than silently returning an empty page") {
                try {
                    service.myNeighborhood("user_1", null, PageRequest.of(0, 20))
                    error("expected CommunityNeighborhoodNotSetException")
                } catch (e: CommunityNeighborhoodNotSetException) {
                    // expected
                }
            }
        }
    }

    Given("a real meetup author scheduling a real recurring session series") {
        val postRepository = mockk<CommunityPostRepository>()
        val commentRepository = mockk<CommunityCommentRepository>()
        val likeRepository = mockk<CommunityLikeRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val splitBillService = mockk<SplitBillService>(relaxed = true)
        val groupConversationRepository = mockk<GroupConversationRepository>(relaxed = true)
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val meetupSessionRepository = mockk<MeetupSessionRepository>()
        val meetupAttendanceRepository = mockk<MeetupAttendanceRepository>()
        val service = CommunityService(
            postRepository, commentRepository, likeRepository, userRepository, notificationRepository,
            groupConversationRepository, groupConversationMemberRepository, meetupSessionRepository, meetupAttendanceRepository,
            rateLimiter, nominatimGeocodingClient, pushNotificationService, splitBillService,
        )

        val meetupPost = CommunityPost(id = "post_1", authorId = "author_1", category = "meetup", title = "Weekly run", body = "Join us")
        every { postRepository.findById("post_1") } returns Optional.of(meetupPost)
        every { meetupSessionRepository.findByPostIdOrderBySequenceAsc("post_1") } returns emptyList()
        every { meetupSessionRepository.deleteAll(any<List<rw.itunda.core.domain.MeetupSession>>()) } returns Unit
        val savedSlot = mutableListOf<rw.itunda.core.domain.MeetupSession>()
        every { meetupSessionRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("scheduling a real 3-session weekly series") {
            val dates = listOf(
                java.time.Instant.now().plusSeconds(86400 * 7),
                java.time.Instant.now().plusSeconds(86400 * 14),
                java.time.Instant.now().plusSeconds(86400 * 21),
            )
            val sessions = service.scheduleMeetupSessions("author_1", "post_1", dates)

            Then("it real-creates a session per date, real-ordered by sequence") {
                sessions.size shouldBe 3
                sessions.map { it.sequence } shouldBe listOf(0, 1, 2)
            }
        }

        When("a non-author tries to schedule sessions") {
            Then("it throws CommunityPostNotFoundException, not a 403 that would confirm the post exists") {
                try {
                    service.scheduleMeetupSessions("stranger", "post_1", listOf(java.time.Instant.now().plusSeconds(86400)))
                    error("expected CommunityPostNotFoundException")
                } catch (e: CommunityPostNotFoundException) {
                    // expected
                }
            }
        }

        When("scheduling more than the real Karrot-sourced max of 6 sessions") {
            Then("it throws InvalidMeetupScheduleException before ever touching the repository") {
                val tooMany = (1..7).map { java.time.Instant.now().plusSeconds(86400L * it) }
                try {
                    service.scheduleMeetupSessions("author_1", "post_1", tooMany)
                    error("expected InvalidMeetupScheduleException")
                } catch (e: InvalidMeetupScheduleException) {
                    verify(exactly = 0) { meetupSessionRepository.save(any()) }
                }
            }
        }

        When("scheduling a real session date in the past") {
            Then("it throws InvalidMeetupScheduleException") {
                try {
                    service.scheduleMeetupSessions("author_1", "post_1", listOf(java.time.Instant.now().minusSeconds(3600)))
                    error("expected InvalidMeetupScheduleException")
                } catch (e: InvalidMeetupScheduleException) {
                    // expected
                }
            }
        }

        When("scheduling sessions for a non-meetup post") {
            val questionPost = CommunityPost(id = "post_2", authorId = "author_1", category = "question", title = "Q", body = "B")
            every { postRepository.findById("post_2") } returns Optional.of(questionPost)

            Then("it throws InvalidMeetupScheduleException") {
                try {
                    service.scheduleMeetupSessions("author_1", "post_2", listOf(java.time.Instant.now().plusSeconds(86400)))
                    error("expected InvalidMeetupScheduleException")
                } catch (e: InvalidMeetupScheduleException) {
                    // expected
                }
            }
        }
    }

    Given("a real joined member of a real meetup, checking in to a real scheduled session") {
        val postRepository = mockk<CommunityPostRepository>()
        val commentRepository = mockk<CommunityCommentRepository>()
        val likeRepository = mockk<CommunityLikeRepository>()
        val userRepository = mockk<UserRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val splitBillService = mockk<SplitBillService>(relaxed = true)
        val groupConversationRepository = mockk<GroupConversationRepository>(relaxed = true)
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>()
        val meetupSessionRepository = mockk<MeetupSessionRepository>()
        val meetupAttendanceRepository = mockk<MeetupAttendanceRepository>()
        val service = CommunityService(
            postRepository, commentRepository, likeRepository, userRepository, notificationRepository,
            groupConversationRepository, groupConversationMemberRepository, meetupSessionRepository, meetupAttendanceRepository,
            rateLimiter, nominatimGeocodingClient, pushNotificationService, splitBillService,
        )

        val meetupPost = CommunityPost(
            id = "post_1", authorId = "author_1", category = "meetup", title = "Weekly run", body = "Join us",
            groupConversationId = "group_1",
        )
        val session = rw.itunda.core.domain.MeetupSession(id = "session_1", postId = "post_1", sequence = 0, scheduledFor = java.time.Instant.now().plusSeconds(3600))
        every { meetupSessionRepository.findById("session_1") } returns Optional.of(session)
        every { postRepository.findById("post_1") } returns Optional.of(meetupPost)
        every { meetupAttendanceRepository.save(any()) } answers { firstArg() }

        When("a real joined member checks in, never having checked in before") {
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "member_1") } returns
                rw.itunda.core.domain.GroupConversationMember(id = "gm_1", groupConversationId = "group_1", userId = "member_1")
            every { meetupAttendanceRepository.findBySessionIdAndUserId("session_1", "member_1") } returns null

            val attendance = service.checkIntoSession("member_1", "session_1")

            Then("it real-records the real attendance row") {
                attendance.sessionId shouldBe "session_1"
                attendance.userId shouldBe "member_1"
            }
        }

        When("someone who never joined the meetup tries to check in") {
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "stranger") } returns null

            Then("it throws MeetupAttendanceNotAMemberException, never saving anything") {
                try {
                    service.checkIntoSession("stranger", "session_1")
                    error("expected MeetupAttendanceNotAMemberException")
                } catch (e: MeetupAttendanceNotAMemberException) {
                    verify(exactly = 0) { meetupAttendanceRepository.save(any()) }
                }
            }
        }

        When("a real member who already checked in tries again") {
            every { groupConversationMemberRepository.findByGroupConversationIdAndUserId("group_1", "member_1") } returns
                rw.itunda.core.domain.GroupConversationMember(id = "gm_1", groupConversationId = "group_1", userId = "member_1")
            every { meetupAttendanceRepository.findBySessionIdAndUserId("session_1", "member_1") } returns
                rw.itunda.core.domain.MeetupAttendance(id = "attendance_1", sessionId = "session_1", userId = "member_1")

            Then("it throws MeetupAttendanceAlreadyCheckedInException") {
                try {
                    service.checkIntoSession("member_1", "session_1")
                    error("expected MeetupAttendanceAlreadyCheckedInException")
                } catch (e: MeetupAttendanceAlreadyCheckedInException) {
                    // expected
                }
            }
        }
    }

    Given("a real user creating a real 당근마켓 같이사요 (group-buy) post") {
        val postRepository = mockk<CommunityPostRepository>()
        val commentRepository = mockk<CommunityCommentRepository>()
        val likeRepository = mockk<CommunityLikeRepository>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val groupConversationRepository = mockk<GroupConversationRepository>(relaxed = true)
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>(relaxed = true)
        val meetupSessionRepository = mockk<MeetupSessionRepository>(relaxed = true)
        val meetupAttendanceRepository = mockk<MeetupAttendanceRepository>(relaxed = true)
        val splitBillService = mockk<SplitBillService>(relaxed = true)
        val service = CommunityService(
            postRepository, commentRepository, likeRepository, userRepository, notificationRepository,
            groupConversationRepository, groupConversationMemberRepository, meetupSessionRepository, meetupAttendanceRepository,
            rateLimiter, nominatimGeocodingClient, pushNotificationService, splitBillService,
        )
        val savedSlot = slot<CommunityPost>()
        every { postRepository.save(capture(savedSlot)) } answers { firstArg() }
        // Real, explicit stub, not relaxed=true's default -- same known "relaxed mockk
        // can't correctly infer a generic Optional<T> return" gotcha this codebase's own
        // tests already document repeatedly for save().
        every { userRepository.findById(any()) } returns Optional.empty()

        When("posting with a real valid capacity of 3") {
            val post = service.createPost("author_1", "group_buy", "Bulk rice order", "Splitting a 25kg bag", capacity = 3)

            Then("it real-persists the real capacity") {
                post.capacity shouldBe 3
                post.category shouldBe "group_buy"
            }
        }

        When("posting with no capacity at all") {
            Then("it throws InvalidMeetupException -- a group buy needs a real headcount cap") {
                try {
                    service.createPost("author_1", "group_buy", "Bulk rice order", "Splitting a 25kg bag")
                    error("expected InvalidMeetupException")
                } catch (e: InvalidMeetupException) {
                    // expected
                }
            }
        }

        When("posting with a real capacity above the real Karrot-sourced max of 4") {
            Then("it throws InvalidMeetupException") {
                try {
                    service.createPost("author_1", "group_buy", "Bulk rice order", "Splitting a 25kg bag", capacity = 5)
                    error("expected InvalidMeetupException")
                } catch (e: InvalidMeetupException) {
                    // expected
                }
            }
        }
    }

    Given("a real group-buy organizer finalizing the real cost split") {
        val postRepository = mockk<CommunityPostRepository>()
        val commentRepository = mockk<CommunityCommentRepository>()
        val likeRepository = mockk<CommunityLikeRepository>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val nominatimGeocodingClient = mockk<NominatimGeocodingClient>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val groupConversationRepository = mockk<GroupConversationRepository>(relaxed = true)
        val groupConversationMemberRepository = mockk<GroupConversationMemberRepository>()
        val meetupSessionRepository = mockk<MeetupSessionRepository>(relaxed = true)
        val meetupAttendanceRepository = mockk<MeetupAttendanceRepository>(relaxed = true)
        val splitBillService = mockk<SplitBillService>()
        val service = CommunityService(
            postRepository, commentRepository, likeRepository, userRepository, notificationRepository,
            groupConversationRepository, groupConversationMemberRepository, meetupSessionRepository, meetupAttendanceRepository,
            rateLimiter, nominatimGeocodingClient, pushNotificationService, splitBillService,
        )

        val groupBuyPost = CommunityPost(
            id = "post_1", authorId = "organizer_1", category = "group_buy", title = "Bulk rice order", body = "Splitting a 25kg bag",
            capacity = 4, groupConversationId = "group_1",
        )
        every { postRepository.findById("post_1") } returns Optional.of(groupBuyPost)
        every { groupConversationMemberRepository.findByGroupConversationId("group_1") } returns listOf(
            rw.itunda.core.domain.GroupConversationMember(id = "gm_0", groupConversationId = "group_1", userId = "organizer_1"),
            rw.itunda.core.domain.GroupConversationMember(id = "gm_1", groupConversationId = "group_1", userId = "member_1"),
            rw.itunda.core.domain.GroupConversationMember(id = "gm_2", groupConversationId = "group_1", userId = "member_2"),
        )

        When("the real organizer finalizes with the real total amount") {
            val resultSlot = slot<List<String>>()
            every {
                splitBillService.createSplitBill("organizer_1", "group_1", BigDecimal("9000"), "25kg rice", capture(resultSlot))
            } returns mockk(relaxed = true)

            service.finalizeGroupBuy("organizer_1", "post_1", BigDecimal("9000"), "25kg rice")

            Then("it real-delegates to SplitBillService with every real member except the organizer") {
                resultSlot.captured.toSet() shouldBe setOf("member_1", "member_2")
            }
        }

        When("a non-organizer tries to finalize") {
            Then("it throws CommunityPostNotFoundException, not a 403 that would confirm the post exists") {
                try {
                    service.finalizeGroupBuy("member_1", "post_1", BigDecimal("9000"), "25kg rice")
                    error("expected CommunityPostNotFoundException")
                } catch (e: CommunityPostNotFoundException) {
                    // expected
                }
            }
        }

        When("finalizing a group buy with real zero joined participants yet") {
            val emptyGroupPost = CommunityPost(
                id = "post_2", authorId = "organizer_1", category = "group_buy", title = "Solo so far", body = "Nobody joined",
                capacity = 4, groupConversationId = null,
            )
            every { postRepository.findById("post_2") } returns Optional.of(emptyGroupPost)

            Then("it throws InvalidGroupBuyFinalizeException before ever touching SplitBillService") {
                try {
                    service.finalizeGroupBuy("organizer_1", "post_2", BigDecimal("9000"), "25kg rice")
                    error("expected InvalidGroupBuyFinalizeException")
                } catch (e: InvalidGroupBuyFinalizeException) {
                    verify(exactly = 0) { splitBillService.createSplitBill(any(), any(), any(), any(), any()) }
                }
            }
        }

        When("finalizing a non-group-buy post") {
            val meetupPost = CommunityPost(
                id = "post_3", authorId = "organizer_1", category = "meetup", title = "Run club", body = "Weekly run",
                groupConversationId = "group_3",
            )
            every { postRepository.findById("post_3") } returns Optional.of(meetupPost)

            Then("it throws InvalidGroupBuyFinalizeException") {
                try {
                    service.finalizeGroupBuy("organizer_1", "post_3", BigDecimal("9000"), "25kg rice")
                    error("expected InvalidGroupBuyFinalizeException")
                } catch (e: InvalidGroupBuyFinalizeException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
