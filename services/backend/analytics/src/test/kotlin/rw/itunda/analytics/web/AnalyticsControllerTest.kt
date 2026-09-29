package rw.itunda.analytics.web

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.HttpStatus
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.AnalyticsEvent
import rw.itunda.core.repository.AnalyticsEventRepository
import rw.itunda.core.security.CurrentUser
import java.time.Instant

/**
 * First test coverage for AnalyticsController -- no test directory existed at
 * all for :analytics before this (not even a service-layer test, since all
 * logic lives directly in the controller).
 */
class AnalyticsControllerTest : BehaviorSpec({

    val currentUser = CurrentUser(userId = "user_1")

    Given("a real request to record a known event") {
        val repository = mockk<AnalyticsEventRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val controller = AnalyticsController(repository, rateLimiter)
        val savedSlot = slot<AnalyticsEvent>()
        every { repository.save(capture(savedSlot)) } answers { firstArg() }

        When("recording it") {
            val response = controller.recordEvent(currentUser, RecordEventRequest("home_view", "android"))

            Then("a real row is saved, scoped to the caller's own userId, after a real rate-limit check") {
                verify(exactly = 1) { rateLimiter.checkLimit("analytics:event:user_1", limit = 60, window = any()) }
                verify(exactly = 1) { repository.save(any()) }
                savedSlot.captured.userId shouldBe "user_1"
                savedSlot.captured.eventName shouldBe "home_view"
                savedSlot.captured.platform shouldBe "android"
                response.body?.get("success") shouldBe true
            }
        }
    }

    Given("a caller who has already exceeded the analytics event rate limit") {
        val repository = mockk<AnalyticsEventRepository>()
        val rateLimiter = mockk<RateLimiter>()
        val controller = AnalyticsController(repository, rateLimiter)
        every { rateLimiter.checkLimit("analytics:event:user_1", limit = 60, window = any()) } throws RateLimitExceededException("Too many requests")

        When("recording another event") {
            Then("a real RateLimitExceededException fires, and nothing is saved") {
                try {
                    controller.recordEvent(currentUser, RecordEventRequest("home_view", "android"))
                    throw AssertionError("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    // expected
                }
                verify(exactly = 0) { repository.save(any()) }
            }
        }

        When("its exception handler maps it to a real HTTP response") {
            val response = controller.handleRateLimit(RateLimitExceededException("Too many requests"))

            Then("it maps to 429 with code RATE_LIMITED, not a generic 500") {
                response.statusCode shouldBe HttpStatus.TOO_MANY_REQUESTS
                response.body?.code shouldBe "RATE_LIMITED"
            }
        }
    }

    Given("a real request to record an unknown event name") {
        val repository = mockk<AnalyticsEventRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val controller = AnalyticsController(repository, rateLimiter)

        When("recording it") {
            Then("a real UnknownAnalyticsEventException fires, and nothing is saved") {
                try {
                    controller.recordEvent(currentUser, RecordEventRequest("made_up_event", "ios"))
                    throw AssertionError("expected UnknownAnalyticsEventException")
                } catch (e: UnknownAnalyticsEventException) {
                    e.message shouldBe "Unknown event name: made_up_event"
                }
                verify(exactly = 0) { repository.save(any()) }
            }
        }

        When("its exception handler maps it to a real HTTP response") {
            val response = controller.handleUnknownEvent(UnknownAnalyticsEventException("made_up_event"))

            Then("it maps to 400 with code UNKNOWN_EVENT, not a generic 500") {
                response.statusCode shouldBe HttpStatus.BAD_REQUEST
                response.body?.code shouldBe "UNKNOWN_EVENT"
            }
        }
    }

    Given("a real usage window with events for every known type and a real coop-rail return") {
        val repository = mockk<AnalyticsEventRepository>()
        val controller = AnalyticsController(repository, mockk(relaxed = true))
        val since = slot<Instant>()

        every { repository.countByEventNameSince(any(), capture(since)) } returns 10L
        every { repository.countDistinctUsersByEventNameSince(any(), any()) } returns 4L
        every { repository.countDistinctUsersSince(any()) } returns 7L
        every { repository.findDistinctUserIdsByEventNameSince("coop_rail_tap", any()) } returns listOf("user_1", "user_2", "user_3")
        val firstTap1 = AnalyticsEvent(id = "e1", userId = "user_1", eventName = "coop_rail_tap", platform = "web", createdAt = Instant.now().minusSeconds(200_000))
        val firstTap3 = AnalyticsEvent(id = "e3", userId = "user_3", eventName = "coop_rail_tap", platform = "web", createdAt = Instant.now().minusSeconds(200_000))
        every { repository.findFirstByUserIdAndEventNameOrderByCreatedAtAsc("user_1", "coop_rail_tap") } returns firstTap1
        every { repository.findFirstByUserIdAndEventNameOrderByCreatedAtAsc("user_2", "coop_rail_tap") } returns null
        every { repository.findFirstByUserIdAndEventNameOrderByCreatedAtAsc("user_3", "coop_rail_tap") } returns firstTap3
        every { repository.existsByUserIdAndCreatedAtAfter("user_1", any()) } returns true
        // user_3 has a real first tap but genuinely never returned -- distinguishes
        // "counted because firstTap exists" from "counted because they actually
        // returned," which a naive `firstTap != null` (dropping the exists() check)
        // would incorrectly conflate.
        every { repository.existsByUserIdAndCreatedAtAfter("user_3", any()) } returns false

        When("fetching the summary") {
            val response = controller.getSummary(days = 30)

            Then("it real-aggregates within the requested window and computes a real retention rate") {
                response.body?.get("windowDays") shouldBe 30L
                response.body?.get("totalActiveUsers") shouldBe 7L
                @Suppress("UNCHECKED_CAST")
                val coopRail = response.body?.get("coopRailReturnRate") as Map<String, Any?>
                coopRail["usersWhoTapped"] shouldBe 3
                coopRail["returnedNextDayOrLater"] shouldBe 1
                coopRail["rate"] shouldBe (1.0 / 3.0)
            }
        }
    }

    Given("a real usage window with nobody tapping the coop rail") {
        val repository = mockk<AnalyticsEventRepository>()
        val controller = AnalyticsController(repository, mockk(relaxed = true))

        every { repository.countByEventNameSince(any(), any()) } returns 0L
        every { repository.countDistinctUsersByEventNameSince(any(), any()) } returns 0L
        every { repository.countDistinctUsersSince(any()) } returns 0L
        every { repository.findDistinctUserIdsByEventNameSince("coop_rail_tap", any()) } returns emptyList()

        When("fetching the summary") {
            val response = controller.getSummary(days = 30)

            Then("the rate is null rather than a division by zero") {
                @Suppress("UNCHECKED_CAST")
                val coopRail = response.body?.get("coopRailReturnRate") as Map<String, Any?>
                coopRail["usersWhoTapped"] shouldBe 0
                coopRail["rate"] shouldBe null
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
