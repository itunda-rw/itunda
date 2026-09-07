package rw.itunda.community.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.community.CommunityPostDetail
import rw.itunda.community.CommunityService
import rw.itunda.core.domain.CommunityPost
import rw.itunda.core.idempotency.IdempotencyService
import rw.itunda.core.security.CurrentUser
import java.math.BigDecimal

/**
 * First test coverage for CommunityController -- one of the 4 highest-transaction-
 * volume Hood controllers, previously untested despite every service class in this
 * ecosystem already having its own test file. Covers real delegation (caller-scoped
 * userId, never client-supplied). Exception-handler mapping lives in
 * CommunityControllerExceptionHandlingTest.kt, split out to stay under the 500-line
 * guideline.
 */
class CommunityControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")
    val pageable = PageRequest.of(0, 20)

    fun controller(
        communityService: CommunityService = mockk(),
        idempotencyService: IdempotencyService = mockk(),
    ) = CommunityController(communityService, idempotencyService)

    Given("a real categories request") {
        val ctl = controller()
        When("fetching them") {
            val response = ctl.categories()
            Then("it real-returns the backend's own category list") {
                response.body?.get("categories") shouldBe CommunityService.CATEGORIES
            }
        }
    }

    Given("a real topics request") {
        val ctl = controller()
        When("fetching them") {
            val response = ctl.topics()
            Then("it real-returns the backend's own topic list") {
                response.body?.get("topics") shouldBe CommunityService.TOPICS
            }
        }
    }

    Given("a real post creation") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        val post = mockk<CommunityPost>(relaxed = true)
        val request = CreateCommunityPostRequest("free_talk", "Hello neighbors", "Just moved in")
        every {
            communityService.createPost("user_1", "free_talk", "Hello neighbors", "Just moved in", null, null, null, null, null)
        } returns post

        When("creating it") {
            ctl.createPost(request, currentUser)
            Then("it queries scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) {
                    communityService.createPost("user_1", "free_talk", "Hello neighbors", "Just moved in", null, null, null, null, null)
                }
            }
        }
    }

    Given("a real upcoming-meetups request") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.upcomingMeetups(pageable) } returns PageImpl(emptyList())
        every { communityService.joinedCounts(emptyList()) } returns emptyMap()

        When("fetching them") {
            ctl.upcomingMeetups(pageable)
            Then("it real-delegates to the real service") {
                verify(exactly = 1) { communityService.upcomingMeetups(pageable) }
            }
        }
    }

    Given("a real browse request") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.browse(pageable, "free_talk", "moving") } returns PageImpl(emptyList())
        every { communityService.joinedCounts(emptyList()) } returns emptyMap()

        When("browsing by category and topic") {
            ctl.browse("free_talk", "moving", pageable)
            Then("it real-delegates the caller-selected filters") {
                verify(exactly = 1) { communityService.browse(pageable, "free_talk", "moving") }
            }
        }
    }

    Given("a real nearby request") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.nearby(-1.9, 30.0, 5.0, pageable) } returns PageImpl(emptyList())
        every { communityService.joinedCounts(emptyList()) } returns emptyMap()

        When("fetching nearby posts") {
            ctl.nearby(-1.9, 30.0, 5.0, pageable)
            Then("it real-delegates the caller's real coordinates") {
                verify(exactly = 1) { communityService.nearby(-1.9, 30.0, 5.0, pageable) }
            }
        }
    }

    Given("a real my-neighborhood request") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.myNeighborhood("user_1", null, pageable) } returns PageImpl(emptyList())
        every { communityService.joinedCounts(emptyList()) } returns emptyMap()

        When("browsing it") {
            ctl.myNeighborhood(null, pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { communityService.myNeighborhood("user_1", null, pageable) }
            }
        }
    }

    Given("a real search request") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.search("moving", pageable) } returns PageImpl(emptyList())
        every { communityService.joinedCounts(emptyList()) } returns emptyMap()

        When("searching") {
            ctl.search("moving", pageable)
            Then("it real-delegates the caller's real query") {
                verify(exactly = 1) { communityService.search("moving", pageable) }
            }
        }
    }

    Given("a real my-posts request") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.getMyPosts("user_1", pageable) } returns PageImpl(emptyList())
        every { communityService.joinedCounts(emptyList()) } returns emptyMap()

        When("fetching them") {
            ctl.myPosts(pageable, currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { communityService.getMyPosts("user_1", pageable) }
            }
        }
    }

    Given("a real single-post request") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        val post = mockk<CommunityPost>(relaxed = true)
        val detail = CommunityPostDetail(post, "Jane", true)
        every { communityService.getPost("user_1", "post_1") } returns detail

        When("fetching it") {
            val response = ctl.getPost("post_1", currentUser)
            Then("it queries scoped to the caller's own userId, so likedByMe reflects the real caller") {
                verify(exactly = 1) { communityService.getPost("user_1", "post_1") }
                response.body?.get("likedByMe") shouldBe true
            }
        }
    }

    Given("a real post removal") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.removePost("user_1", "post_1") } returns mockk(relaxed = true)

        When("removing it") {
            ctl.removePost("post_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { communityService.removePost("user_1", "post_1") }
            }
        }
    }

    Given("a real comment-list request") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.getComments("post_1", pageable) } returns PageImpl(emptyList())

        When("fetching them") {
            ctl.getComments("post_1", pageable)
            Then("it real-delegates by the real post id") {
                verify(exactly = 1) { communityService.getComments("post_1", pageable) }
            }
        }
    }

    Given("a real comment addition") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.addComment("user_1", "post_1", "Welcome!") } returns mockk(relaxed = true)

        When("adding it") {
            ctl.addComment("post_1", AddCommunityCommentRequest("Welcome!"), currentUser)
            Then("it queries scoped to the caller's own userId, never a client-supplied one") {
                verify(exactly = 1) { communityService.addComment("user_1", "post_1", "Welcome!") }
            }
        }
    }

    Given("a real comment-notification preference change") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.setCommentNotificationsEnabled("user_1", false) } returns mockk(relaxed = true)

        When("disabling it") {
            ctl.setCommentNotificationsEnabled(SetCommentNotificationsEnabledRequest(false), currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { communityService.setCommentNotificationsEnabled("user_1", false) }
            }
        }
    }

    Given("a real comment-notification preference read") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.areCommentNotificationsEnabled("user_1") } returns true

        When("reading it") {
            ctl.getCommentNotificationsEnabled(currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { communityService.areCommentNotificationsEnabled("user_1") }
            }
        }
    }

    Given("a real like toggle") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.toggleLike("user_1", "post_1") } returns true

        When("toggling it") {
            ctl.toggleLike("post_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { communityService.toggleLike("user_1", "post_1") }
            }
        }
    }

    Given("a real meetup join") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.joinMeetup("user_1", "post_1") } returns mockk(relaxed = true)

        When("joining it") {
            ctl.joinMeetup("post_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { communityService.joinMeetup("user_1", "post_1") }
            }
        }
    }

    Given("a real meetup-session schedule") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        val dates = listOf(java.time.Instant.now())
        every { communityService.scheduleMeetupSessions("user_1", "post_1", dates) } returns emptyList()

        When("scheduling it") {
            ctl.scheduleMeetupSessions("post_1", ScheduleMeetupSessionsRequest(dates), currentUser)
            Then("it queries scoped to the caller's own userId, never a client-supplied organizer") {
                verify(exactly = 1) { communityService.scheduleMeetupSessions("user_1", "post_1", dates) }
            }
        }
    }

    Given("a real meetup-sessions list request") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.getMeetupSessions("post_1") } returns emptyList()

        When("fetching them") {
            ctl.getMeetupSessions("post_1")
            Then("it real-delegates by the real post id") {
                verify(exactly = 1) { communityService.getMeetupSessions("post_1") }
            }
        }
    }

    Given("a real session check-in") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.checkIntoSession("user_1", "session_1") } returns mockk(relaxed = true)

        When("checking in") {
            ctl.checkIntoSession("session_1", currentUser)
            Then("it queries scoped to the caller's own userId") {
                verify(exactly = 1) { communityService.checkIntoSession("user_1", "session_1") }
            }
        }
    }

    Given("a real session-attendance request") {
        val communityService = mockk<CommunityService>()
        val ctl = controller(communityService = communityService)
        every { communityService.getSessionAttendance("user_1", "session_1") } returns emptyList()

        When("fetching it") {
            ctl.getSessionAttendance("session_1", currentUser)
            Then("it queries scoped to the caller's own userId, real-enforcing the membership check") {
                verify(exactly = 1) { communityService.getSessionAttendance("user_1", "session_1") }
            }
        }
    }

    Given("a real group-buy finalization") {
        val communityService = mockk<CommunityService>()
        val idempotencyService = mockk<IdempotencyService>()
        val ctl = controller(communityService = communityService, idempotencyService = idempotencyService)
        val request = FinalizeGroupBuyRequest(BigDecimal("15000"), "Group order from Kigali Market")
        val actionSlot = slot<() -> Pair<Int, Map<String, Any?>>>()
        every { communityService.finalizeGroupBuy("user_1", "post_1", BigDecimal("15000"), "Group order from Kigali Market") } returns mockk(relaxed = true)
        every {
            idempotencyService.replayOrExecute("POST /api/v1/community/posts/post_1/finalize-group-buy", "key-1", request, capture(actionSlot))
        } answers { actionSlot.captured.invoke() }

        When("finalizing it") {
            ctl.finalizeGroupBuy("post_1", request, "key-1", currentUser)
            Then("it routes through the real idempotency service scoped to the caller's own userId") {
                verify(exactly = 1) { idempotencyService.replayOrExecute("POST /api/v1/community/posts/post_1/finalize-group-buy", "key-1", request, any()) }
                verify(exactly = 1) { communityService.finalizeGroupBuy("user_1", "post_1", BigDecimal("15000"), "Group order from Kigali Market") }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
