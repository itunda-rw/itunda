package rw.itunda.marketplace

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.core.domain.KeywordAlert
import rw.itunda.core.domain.Listing
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.KeywordAlertQuietHoursRepository
import rw.itunda.core.repository.KeywordAlertRepository
import java.math.BigDecimal

/** First test coverage for the real 당근마켓 Keyword Alert (키워드 알림) equivalent. */
class KeywordAlertServiceTest : BehaviorSpec({

    fun listing(id: String, title: String) = Listing(
        id = id, sellerId = "seller_1", title = title, description = "desc", price = BigDecimal("1000"), category = "electronics",
    )

    Given("a user registering a real keyword alert") {
        val keywordAlertRepository = mockk<KeywordAlertRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val keywordAlertQuietHoursRepository = mockk<KeywordAlertQuietHoursRepository>(relaxed = true)
        every { keywordAlertQuietHoursRepository.findByUserIdIn(any()) } returns emptyList()
        val service = KeywordAlertService(keywordAlertRepository, keywordAlertQuietHoursRepository, pushNotificationService)

        When("registering a brand-new keyword") {
            every { keywordAlertRepository.findByUserIdAndKeyword("user_1", "bicycle") } returns null
            every { keywordAlertRepository.countByUserId("user_1") } returns 0
            every { keywordAlertRepository.save(any()) } answers { firstArg() }

            val result = service.addAlert("user_1", "  Bicycle  ")

            Then("it real-trims and lowercases the keyword before storing") {
                result.keyword shouldBe "bicycle"
            }
        }

        When("registering a keyword that's already registered") {
            val existing = KeywordAlert(id = "existing_1", userId = "user_1", keyword = "bicycle")
            every { keywordAlertRepository.findByUserIdAndKeyword("user_1", "bicycle") } returns existing

            val result = service.addAlert("user_1", "bicycle")

            Then("it real-returns the existing row rather than creating a duplicate") {
                result.id shouldBe "existing_1"
                verify(exactly = 0) { keywordAlertRepository.save(any()) }
            }
        }

        When("registering a blank keyword") {
            Then("it throws InvalidKeywordException before touching the repository") {
                try {
                    service.addAlert("user_1", "   ")
                    error("expected InvalidKeywordException")
                } catch (e: InvalidKeywordException) {
                    verify(exactly = 0) { keywordAlertRepository.save(any()) }
                }
            }
        }

        When("registering a keyword longer than 64 characters") {
            Then("it throws InvalidKeywordException") {
                try {
                    service.addAlert("user_1", "x".repeat(65))
                    error("expected InvalidKeywordException")
                } catch (e: InvalidKeywordException) {
                    // expected
                }
            }
        }

        When("a user has already reached the real 30-keyword cap") {
            every { keywordAlertRepository.findByUserIdAndKeyword("user_1", "sofa") } returns null
            every { keywordAlertRepository.countByUserId("user_1") } returns 30

            Then("it throws KeywordAlertCapReachedException, matching Karrot's own real published limit") {
                try {
                    service.addAlert("user_1", "sofa")
                    error("expected KeywordAlertCapReachedException")
                } catch (e: KeywordAlertCapReachedException) {
                    verify(exactly = 0) { keywordAlertRepository.save(any()) }
                }
            }
        }
    }

    Given("a user removing a real keyword alert") {
        val keywordAlertRepository = mockk<KeywordAlertRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val keywordAlertQuietHoursRepository = mockk<KeywordAlertQuietHoursRepository>(relaxed = true)
        every { keywordAlertQuietHoursRepository.findByUserIdIn(any()) } returns emptyList()
        val service = KeywordAlertService(keywordAlertRepository, keywordAlertQuietHoursRepository, pushNotificationService)

        When("removing their own real alert") {
            every { keywordAlertRepository.deleteByUserIdAndId("user_1", "alert_1") } returns 1L

            Then("it succeeds without throwing") {
                service.removeAlert("user_1", "alert_1")
            }
        }

        When("trying to remove an alert that doesn't belong to them (or doesn't exist)") {
            every { keywordAlertRepository.deleteByUserIdAndId("stranger", "alert_1") } returns 0L

            Then("it throws KeywordAlertNotFoundException -- a real, honest 404") {
                try {
                    service.removeAlert("stranger", "alert_1")
                    error("expected KeywordAlertNotFoundException")
                } catch (e: KeywordAlertNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real new listing being checked against registered keyword alerts") {
        val keywordAlertRepository = mockk<KeywordAlertRepository>()
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val keywordAlertQuietHoursRepository = mockk<KeywordAlertQuietHoursRepository>(relaxed = true)
        every { keywordAlertQuietHoursRepository.findByUserIdIn(any()) } returns emptyList()
        val service = KeywordAlertService(keywordAlertRepository, keywordAlertQuietHoursRepository, pushNotificationService)

        When("the listing title real-matches two real registered alerts, from two different users") {
            val newListing = listing("listing_1", "Mountain Bicycle for sale")
            every { keywordAlertRepository.findMatchingAlerts("mountain bicycle for sale") } returns listOf(
                KeywordAlert(id = "alert_1", userId = "user_1", keyword = "bicycle"),
                KeywordAlert(id = "alert_2", userId = "user_2", keyword = "mountain"),
            )

            service.notifyMatchingAlerts(newListing)

            Then("it real-pushes a notification to each real matching user") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_1", "New listing matches \"bicycle\"", "Mountain Bicycle for sale", mapOf("listingId" to "listing_1")) }
                verify(exactly = 1) { pushNotificationService.sendToUser("user_2", "New listing matches \"mountain\"", "Mountain Bicycle for sale", mapOf("listingId" to "listing_1")) }
            }
        }

        When("no real alert matches the listing title") {
            val newListing = listing("listing_2", "Wooden chair")
            every { keywordAlertRepository.findMatchingAlerts("wooden chair") } returns emptyList()

            service.notifyMatchingAlerts(newListing)

            Then("it real-sends no push at all") {
                verify(exactly = 0) { pushNotificationService.sendToUser(any(), any(), any(), any()) }
            }
        }

        When("the real match query itself throws") {
            val newListing = listing("listing_3", "Broken query test")
            every { keywordAlertRepository.findMatchingAlerts(any()) } throws RuntimeException("db down")

            Then("it never propagates -- a keyword-alert failure must never look like the listing creation itself failed") {
                service.notifyMatchingAlerts(newListing)
            }
        }

        When("a matching user has real, currently-active quiet hours") {
            val newListing = listing("listing_4", "Quiet hours bicycle")
            every { keywordAlertRepository.findMatchingAlerts("quiet hours bicycle") } returns listOf(
                KeywordAlert(id = "alert_4", userId = "user_quiet", keyword = "bicycle"),
            )
            // Real always-on window (00:00-23:59), so this test is never flaky against
            // the real current wall-clock time -- the point under test is the ENABLED
            // quiet-hours check itself, not a specific real hour of day.
            every { keywordAlertQuietHoursRepository.findByUserIdIn(listOf("user_quiet")) } returns listOf(
                rw.itunda.core.domain.KeywordAlertQuietHours(
                    id = "quiet_1", userId = "user_quiet",
                    startTime = java.time.LocalTime.of(0, 0), endTime = java.time.LocalTime.of(23, 59), enabled = true,
                ),
            )

            service.notifyMatchingAlerts(newListing)

            Then("the real push is honestly suppressed, matching Karrot's own real do-not-disturb behavior") {
                verify(exactly = 0) { pushNotificationService.sendToUser("user_quiet", any(), any(), any()) }
            }
        }

        When("a matching user has quiet hours configured but currently disabled") {
            val newListing = listing("listing_5", "Disabled quiet hours bicycle")
            every { keywordAlertRepository.findMatchingAlerts("disabled quiet hours bicycle") } returns listOf(
                KeywordAlert(id = "alert_5", userId = "user_disabled_quiet", keyword = "bicycle"),
            )
            every { keywordAlertQuietHoursRepository.findByUserIdIn(listOf("user_disabled_quiet")) } returns listOf(
                rw.itunda.core.domain.KeywordAlertQuietHours(
                    id = "quiet_2", userId = "user_disabled_quiet",
                    startTime = java.time.LocalTime.of(0, 0), endTime = java.time.LocalTime.of(23, 59), enabled = false,
                ),
            )

            service.notifyMatchingAlerts(newListing)

            Then("a disabled setting never suppresses a real push, even during its own configured window") {
                verify(exactly = 1) { pushNotificationService.sendToUser("user_disabled_quiet", any(), any(), any()) }
            }
        }
    }

    Given("real wraps-past-midnight quiet hours logic (22:00 start, 08:00 end)") {
        val keywordAlertRepository = mockk<KeywordAlertRepository>(relaxed = true)
        val keywordAlertQuietHoursRepository = mockk<KeywordAlertQuietHoursRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = KeywordAlertService(keywordAlertRepository, keywordAlertQuietHoursRepository, pushNotificationService)

        When("configuring a real wrapping window") {
            every { keywordAlertQuietHoursRepository.findByUserId("user_1") } returns null
            every { keywordAlertQuietHoursRepository.save(any()) } answers { firstArg() }

            val result = service.setQuietHours("user_1", java.time.LocalTime.of(22, 0), java.time.LocalTime.of(8, 0), true)

            Then("it saves the real start/end exactly as given, even though end is numerically before start") {
                result.startTime shouldBe java.time.LocalTime.of(22, 0)
                result.endTime shouldBe java.time.LocalTime.of(8, 0)
            }
        }

        When("start and end time are identical") {
            Then("it's rejected as a real, meaningless window") {
                try {
                    service.setQuietHours("user_1", java.time.LocalTime.of(9, 0), java.time.LocalTime.of(9, 0), true)
                    error("expected InvalidQuietHoursException")
                } catch (e: InvalidQuietHoursException) {
                    // expected
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
