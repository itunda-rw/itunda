package rw.itunda.merchant

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import rw.itunda.auth.RateLimiter
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantFollow
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.MerchantFollowRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.repository.NotificationRepository
import java.util.Optional

class MerchantFollowServiceTest : BehaviorSpec({

    Given("a real user following a real merchant") {
        val merchantFollowRepository = mockk<MerchantFollowRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantFollowService(merchantFollowRepository, merchantRepository, notificationRepository, rateLimiter)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", walletId = "wallet_1", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)

        When("following a merchant for the first time") {
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { merchantFollowRepository.findByUserIdAndMerchantId("user_1", "merchant_1") } returns null
            every { merchantFollowRepository.save(any()) } answers { firstArg() }

            val follow = service.follow("user_1", "merchant_1")

            Then("it real-creates a new follow row") {
                follow.userId shouldBe "user_1"
                follow.merchantId shouldBe "merchant_1"
            }
        }

        When("following the same merchant a second time") {
            val existing = MerchantFollow(id = "merchant_follow_1", userId = "user_1", merchantId = "merchant_1")
            every { merchantRepository.findById("merchant_1") } returns Optional.of(merchant)
            every { merchantFollowRepository.findByUserIdAndMerchantId("user_1", "merchant_1") } returns existing

            val follow = service.follow("user_1", "merchant_1")

            Then("it real-returns the existing row, never a duplicate") {
                follow.id shouldBe "merchant_follow_1"
            }
        }

        When("following a merchant that doesn't exist") {
            every { merchantRepository.findById("ghost") } returns Optional.empty()

            Then("it throws MerchantNotFoundException") {
                try {
                    service.follow("user_1", "ghost")
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real merchant owner checking their own follower count") {
        val merchantFollowRepository = mockk<MerchantFollowRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantFollowService(merchantFollowRepository, merchantRepository, notificationRepository, rateLimiter)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", walletId = "wallet_1", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)

        When("they have 5 real followers") {
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant
            every { merchantFollowRepository.countByMerchantId("merchant_1") } returns 5L

            Then("it returns the real count") {
                service.getFollowerCount("seller_1") shouldBe 5L
            }
        }

        When("someone who isn't a registered merchant checks") {
            every { merchantRepository.findByOwnerUserId("stranger") } returns null

            Then("it throws MerchantNotFoundException") {
                try {
                    service.getFollowerCount("stranger")
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real merchant broadcasting a real promotional notice to their followers") {
        val merchantFollowRepository = mockk<MerchantFollowRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantFollowService(merchantFollowRepository, merchantRepository, notificationRepository, rateLimiter)

        val merchant = Merchant(id = "merchant_1", ownerUserId = "seller_1", walletId = "wallet_1", businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE)
        val followers = listOf(
            MerchantFollow(id = "f1", userId = "follower_1", merchantId = "merchant_1"),
            MerchantFollow(id = "f2", userId = "follower_2", merchantId = "merchant_1"),
        )

        When("broadcasting to 2 real followers") {
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant
            every { merchantFollowRepository.findByMerchantId("merchant_1") } returns followers
            every { notificationRepository.save(any()) } answers { firstArg() }

            val result = service.broadcastToFollowers("seller_1", "20% off today", "Come visit us for a real discount!")

            Then("it real-notifies every real follower and reports the real recipient count") {
                result.recipientCount shouldBe 2
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "follower_1" && it.type == "MERCHANT_BROADCAST" }) }
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "follower_2" && it.type == "MERCHANT_BROADCAST" }) }
            }
        }

        When("broadcasting with an empty title") {
            every { merchantRepository.findByOwnerUserId("seller_1") } returns merchant

            Then("it throws InvalidBroadcastException before ever touching a follower") {
                try {
                    service.broadcastToFollowers("seller_1", "   ", "body")
                    error("expected InvalidBroadcastException")
                } catch (e: InvalidBroadcastException) {
                    verify(exactly = 0) { merchantFollowRepository.findByMerchantId(any()) }
                }
            }
        }

        When("a non-merchant tries to broadcast") {
            every { merchantRepository.findByOwnerUserId("stranger") } returns null

            Then("it throws MerchantNotFoundException") {
                try {
                    service.broadcastToFollowers("stranger", "title", "body")
                    error("expected MerchantNotFoundException")
                } catch (e: MerchantNotFoundException) {
                    // expected
                }
            }
        }
    }

    Given("a real user unfollowing a merchant") {
        val merchantFollowRepository = mockk<MerchantFollowRepository>(relaxed = true)
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val rateLimiter = mockk<RateLimiter>(relaxed = true)
        val service = MerchantFollowService(merchantFollowRepository, merchantRepository, notificationRepository, rateLimiter)

        When("unfollowing succeeds without throwing, even if they never followed") {
            service.unfollow("user_1", "merchant_1")
            // No exception -- silent no-op, same discipline ProductFavoriteService/EatsFavoriteService already establish.
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
