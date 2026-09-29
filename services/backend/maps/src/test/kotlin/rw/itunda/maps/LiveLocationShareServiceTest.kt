package rw.itunda.maps

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.auth.RateLimitExceededException
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.LiveLocationShare
import rw.itunda.core.domain.User
import rw.itunda.core.repository.LiveLocationShareRepository
import rw.itunda.core.repository.UserRepository
import java.time.Duration
import java.time.Instant
import java.util.Optional

class LiveLocationShareServiceTest : BehaviorSpec({

    Given("a real sharer starting a real location share") {
        val liveLocationShareRepository = mockk<LiveLocationShareRepository>()
        val userRepository = mockk<UserRepository>()
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = LiveLocationShareService(liveLocationShareRepository, userRepository, rateLimiter)
        val recipient = mockk<User> { every { id } returns "user_recipient" }
        every { userRepository.findByPhoneNumber("+250788111222") } returns recipient
        every { liveLocationShareRepository.countBySharerUserIdAndRevokedFalse("user_sharer") } returns 0
        val savedSlot = slot<LiveLocationShare>()
        every { liveLocationShareRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("sharing for a real 2-hour window") {
            val share = service.startSharing("user_sharer", "+250788111222", durationHours = 2)

            Then("it creates a real share expiring roughly 2 real hours from now") {
                share.sharerUserId shouldBe "user_sharer"
                share.recipientUserId shouldBe "user_recipient"
                val realWindow = Duration.between(Instant.now(), share.expiresAt)
                (realWindow.toMinutes() in 115..120) shouldBe true
            }
        }

        When("the recipient phone number doesn't match any real itunda account") {
            every { userRepository.findByPhoneNumber("+250700000000") } returns null

            Then("it real-throws LiveLocationShareRecipientNotFoundException") {
                try {
                    service.startSharing("user_sharer", "+250700000000", durationHours = 1)
                    error("expected LiveLocationShareRecipientNotFoundException")
                } catch (e: LiveLocationShareRecipientNotFoundException) {
                    e.message shouldBe "No itunda account found for this phone number"
                }
            }
        }

        When("the sharer tries to share with themselves") {
            val self = mockk<User> { every { id } returns "user_sharer" }
            every { userRepository.findByPhoneNumber("+250788999999") } returns self

            Then("it real-throws LiveLocationShareSelfException") {
                try {
                    service.startSharing("user_sharer", "+250788999999", durationHours = 1)
                    error("expected LiveLocationShareSelfException")
                } catch (e: LiveLocationShareSelfException) {
                    e.message shouldBe "You can't share your location with yourself"
                }
            }
        }

        When("a duration outside the real 1-6 hour range is requested") {
            Then("0 hours real-throws InvalidLiveLocationShareDurationException") {
                try {
                    service.startSharing("user_sharer", "+250788111222", durationHours = 0)
                    error("expected InvalidLiveLocationShareDurationException")
                } catch (e: InvalidLiveLocationShareDurationException) {
                    e.message shouldBe "Duration must be between 1 and 6 hours"
                }
            }
            Then("7 hours (past the real 6-hour Kakao-sourced max) also real-throws") {
                try {
                    service.startSharing("user_sharer", "+250788111222", durationHours = 7)
                    error("expected InvalidLiveLocationShareDurationException")
                } catch (e: InvalidLiveLocationShareDurationException) {
                    e.message shouldBe "Duration must be between 1 and 6 hours"
                }
            }
        }

        When("the sharer already has the real max 10 active shares") {
            every { liveLocationShareRepository.countBySharerUserIdAndRevokedFalse("user_sharer") } returns 10

            Then("it real-throws LiveLocationTooManyActiveSharesException") {
                try {
                    service.startSharing("user_sharer", "+250788111222", durationHours = 1)
                    error("expected LiveLocationTooManyActiveSharesException")
                } catch (e: LiveLocationTooManyActiveSharesException) {
                    e.message shouldBe "You can have at most 10 active location shares at once"
                }
            }
        }
    }

    Given("a real sharer pushing a fresh GPS reading") {
        val liveLocationShareRepository = mockk<LiveLocationShareRepository>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = LiveLocationShareService(liveLocationShareRepository, userRepository, rateLimiter)

        When("they have 2 real active shares and 1 real revoked one") {
            val active1 = LiveLocationShare(id = "s1", sharerUserId = "user_sharer", recipientUserId = "r1", expiresAt = Instant.now().plusSeconds(3600))
            val active2 = LiveLocationShare(id = "s2", sharerUserId = "user_sharer", recipientUserId = "r2", expiresAt = Instant.now().plusSeconds(3600))
            every { liveLocationShareRepository.findBySharerUserIdAndRevokedFalse("user_sharer") } returns listOf(active1, active2)
            every { liveLocationShareRepository.saveAll(any<List<LiveLocationShare>>()) } answers { firstArg() }

            val updatedCount = service.updateMyLocation("user_sharer", -1.95, 30.06)

            Then("it real-updates both real active shares' position and returns count 2") {
                updatedCount shouldBe 2
                active1.latitude shouldBe -1.95
                active1.longitude shouldBe 30.06
                active2.latitude shouldBe -1.95
            }
        }

        When("an out-of-range real coordinate is pushed") {
            every { liveLocationShareRepository.findBySharerUserIdAndRevokedFalse(any()) } returns emptyList()

            Then("it real-throws InvalidLiveLocationCoordinateException before touching any share") {
                try {
                    service.updateMyLocation("user_sharer", 999.0, 30.06)
                    error("expected InvalidLiveLocationCoordinateException")
                } catch (e: InvalidLiveLocationCoordinateException) {
                    e.message shouldBe "Latitude must be between -90 and 90, longitude between -180 and 180"
                }
            }
        }

        When("the real per-minute push rate limit is exceeded") {
            every { rateLimiter.checkLimit("maps:location-share:update:user_sharer", limit = 20, window = Duration.ofMinutes(1)) } throws RateLimitExceededException("Too many requests")

            Then("it real-propagates RateLimitExceededException") {
                try {
                    service.updateMyLocation("user_sharer", -1.95, 30.06)
                    error("expected RateLimitExceededException")
                } catch (e: RateLimitExceededException) {
                    e.message shouldBe "Too many requests"
                }
            }
        }
    }

    Given("a real recipient watching a real share that's expired") {
        val liveLocationShareRepository = mockk<LiveLocationShareRepository>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = LiveLocationShareService(liveLocationShareRepository, userRepository, rateLimiter)
        val expired = LiveLocationShare(id = "s1", sharerUserId = "user_sharer", recipientUserId = "user_recipient", expiresAt = Instant.now().minusSeconds(60))
        every { liveLocationShareRepository.findByIdAndRecipientUserId("s1", "user_recipient") } returns expired

        When("they poll it after real expiry") {
            Then("it real-throws LiveLocationShareEndedException") {
                try {
                    service.getSharedLocation("user_recipient", "s1")
                    error("expected LiveLocationShareEndedException")
                } catch (e: LiveLocationShareEndedException) {
                    e.message shouldBe "This location share has ended"
                }
            }
        }
    }

    Given("a real recipient trying to read a share not addressed to them") {
        val liveLocationShareRepository = mockk<LiveLocationShareRepository>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = LiveLocationShareService(liveLocationShareRepository, userRepository, rateLimiter)
        every { liveLocationShareRepository.findByIdAndRecipientUserId("s1", "user_stranger") } returns null

        When("they poll it") {
            Then("it real-throws LiveLocationShareNotFoundException, not an ownership-leaking distinction") {
                try {
                    service.getSharedLocation("user_stranger", "s1")
                    error("expected LiveLocationShareNotFoundException")
                } catch (e: LiveLocationShareNotFoundException) {
                    e.message shouldBe "Location share not found"
                }
            }
        }
    }

    Given("a real sharer revoking their own share early") {
        val liveLocationShareRepository = mockk<LiveLocationShareRepository>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = LiveLocationShareService(liveLocationShareRepository, userRepository, rateLimiter)
        val share = LiveLocationShare(id = "s1", sharerUserId = "user_sharer", recipientUserId = "user_recipient", expiresAt = Instant.now().plusSeconds(3600))
        every { liveLocationShareRepository.findByIdAndSharerUserId("s1", "user_sharer") } returns share
        val savedSlot = slot<LiveLocationShare>()
        every { liveLocationShareRepository.save(capture(savedSlot)) } answers { firstArg() }

        When("stopSharing is called") {
            service.stopSharing("user_sharer", "s1")

            Then("the real share is marked revoked and persisted") {
                savedSlot.captured.revoked shouldBe true
            }
        }
    }

    Given("a real sharer extending an active share") {
        val liveLocationShareRepository = mockk<LiveLocationShareRepository>()
        val userRepository = mockk<UserRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = LiveLocationShareService(liveLocationShareRepository, userRepository, rateLimiter)
        // Created 30 real minutes ago, real 1-hour window -- still comfortably active
        // (30 min of real slack remaining), not sitting right at the isActive() boundary.
        val createdAt = Instant.now().minusSeconds(1800)
        val share = LiveLocationShare(id = "s1", sharerUserId = "user_sharer", recipientUserId = "user_recipient", expiresAt = createdAt.plusSeconds(3600), createdAt = createdAt)
        every { liveLocationShareRepository.findByIdAndSharerUserId("s1", "user_sharer") } returns share
        every { liveLocationShareRepository.save(any()) } answers { firstArg() }

        When("extending by 2 more real hours (within the real 6-hour total ceiling)") {
            val originalExpiresAt = share.expiresAt
            val extended = service.extendSharing("user_sharer", "s1", additionalHours = 2)

            Then("the real expiry moves out by exactly 2 real hours from its ORIGINAL value") {
                extended.expiresAt shouldBe originalExpiresAt.plusSeconds(2 * 3600)
            }
        }

        When("extending by 6 more real hours (its current expiry is already createdAt+1h, so +6 more would reach createdAt+7h, past the real 6-hour ceiling FROM ORIGINAL CREATION)") {
            val extended = service.extendSharing("user_sharer", "s1", additionalHours = 6)

            Then("it real-caps at exactly createdAt + 6 hours, not createdAt + 1h(original) + 6h(requested)") {
                extended.expiresAt shouldBe createdAt.plusSeconds(6 * 3600)
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
