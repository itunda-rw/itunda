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
        val service = KeywordAlertService(keywordAlertRepository, pushNotificationService)

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
        val service = KeywordAlertService(keywordAlertRepository, pushNotificationService)

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
        val service = KeywordAlertService(keywordAlertRepository, pushNotificationService)

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
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
