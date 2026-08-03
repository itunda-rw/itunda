package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import rw.itunda.core.domain.EatsFulfillmentType
import rw.itunda.core.domain.EatsOrder
import rw.itunda.core.domain.EatsOrderStatus
import rw.itunda.core.domain.EatsReview
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.repository.EatsOrderRepository
import rw.itunda.core.repository.EatsReviewRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.core.push.PushNotificationService
import rw.itunda.core.repository.NotificationRepository
import rw.itunda.core.repository.RatingSummaryProjection
import java.math.BigDecimal
import java.util.Optional

class EatsReviewServiceTest : BehaviorSpec({

    Given("a real delivered order") {
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = EatsReviewService(eatsOrderRepository, eatsReviewRepository, merchantRepository, notificationRepository, pushNotificationService)

        val deliveredOrder = EatsOrder(
            id = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1", riderId = "rider_1", deliveryAddress = "addr",
            itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
            totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_1", status = EatsOrderStatus.DELIVERED,
        )

        When("the real buyer submits a real review") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_1", 5, "  Great food!  ", 4, "  Fast delivery  ")

            Then("it trims comments, resolves the real restaurant/rider from the order, and persists it") {
                review.restaurantId shouldBe "restaurant_1"
                review.riderId shouldBe "rider_1"
                review.restaurantRating shouldBe 5
                review.restaurantComment shouldBe "Great food!"
                review.riderRating shouldBe 4
                review.riderComment shouldBe "Fast delivery"
            }
        }

        When("submitting comments longer than the real 1000-char DB column bound") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }
            val longComment = "x".repeat(1500)

            val review = service.submitReview("buyer_1", "eats_order_1", 5, longComment, 4, longComment)

            Then("it truncates both comments to 1000 chars rather than risking a raw DB insert failure") {
                review.restaurantComment?.length shouldBe 1000
                review.riderComment?.length shouldBe 1000
            }
        }

        When("the real buyer submits a review with a real photo URL") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_1", 5, "Great food!", 4, "Fast delivery", "  https://example.com/food.jpg  ")

            Then("it trims and persists the photo URL") {
                review.photoUrl shouldBe "https://example.com/food.jpg"
            }
        }

        When("submitting a photo URL longer than the real 500-char DB column bound") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }
            val longUrl = "https://example.com/" + "x".repeat(600)

            val review = service.submitReview("buyer_1", "eats_order_1", 5, null, 4, null, longUrl)

            Then("it truncates the photo URL to 500 chars rather than risking a raw DB insert failure") {
                review.photoUrl?.length shouldBe 500
            }
        }

        When("submitting no photo URL at all") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns null
            val savedSlot = slot<EatsReview>()
            every { eatsReviewRepository.save(capture(savedSlot)) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_1", 5, null, 4, null)

            Then("it stays null -- a review with no photo is still a complete, honest review") {
                review.photoUrl shouldBe null
            }
        }

        When("submitting a rating outside 1-5") {
            Then("it throws InvalidEatsRatingException before even looking up the order") {
                try {
                    service.submitReview("buyer_1", "eats_order_1", 6, null, 3, null)
                    error("expected InvalidEatsRatingException")
                } catch (e: InvalidEatsRatingException) {
                    // expected
                }
            }
        }

        When("a stranger (not the real buyer) tries to review the order") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)

            Then("it throws EatsOrderNotFoundException, not a 403 that would confirm the order exists") {
                try {
                    service.submitReview("stranger", "eats_order_1", 5, null, 5, null)
                    error("expected EatsOrderNotFoundException")
                } catch (e: EatsOrderNotFoundException) {
                    // expected
                }
            }
        }

        When("the real buyer tries to review an order that isn't DELIVERED yet") {
            val placedOrder = EatsOrder(
                id = "eats_order_2", buyerId = "buyer_1", restaurantId = "restaurant_1", riderId = null, deliveryAddress = "addr",
                itemsSubtotal = BigDecimal("6000"), deliveryFee = BigDecimal("1500"), platformFee = BigDecimal("90"),
                totalAmount = BigDecimal("7500"), transactionId = "ledgertxn_2", status = EatsOrderStatus.PLACED,
            )
            every { eatsOrderRepository.findById("eats_order_2") } returns Optional.of(placedOrder)

            Then("it throws EatsOrderNotYetDeliveredException") {
                try {
                    service.submitReview("buyer_1", "eats_order_2", 5, null, 5, null)
                    error("expected EatsOrderNotYetDeliveredException")
                } catch (e: EatsOrderNotYetDeliveredException) {
                    // expected
                }
            }
        }

        When("the real buyer tries to review the same order twice") {
            every { eatsOrderRepository.findById("eats_order_1") } returns Optional.of(deliveredOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_1") } returns EatsReview(
                id = "eats_review_1", orderId = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1",
                riderId = "rider_1", restaurantRating = 5, restaurantComment = null, riderRating = 5, riderComment = null,
            )

            Then("it throws EatsOrderAlreadyReviewedException") {
                try {
                    service.submitReview("buyer_1", "eats_order_1", 4, null, 4, null)
                    error("expected EatsOrderAlreadyReviewedException")
                } catch (e: EatsOrderAlreadyReviewedException) {
                    // expected
                }
            }
        }

        When("fetching a real restaurant's aggregate rating") {
            every { eatsReviewRepository.getRestaurantRatingSummary("restaurant_1") } returns object : RatingSummaryProjection {
                override val average = 4.5
                override val count = 2L
            }

            val summary = service.getRestaurantRating("restaurant_1")

            Then("it returns the real average and count") {
                summary.average shouldBe 4.5
                summary.count shouldBe 2L
            }
        }
    }

    Given("a real restaurant owner replying to a real review of their own restaurant") {
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = EatsReviewService(eatsOrderRepository, eatsReviewRepository, merchantRepository, notificationRepository, pushNotificationService)

        val restaurant = Merchant(id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_1", businessName = "Kigali Diner", status = MerchantStatus.ACTIVE)
        val review = EatsReview(
            id = "eats_review_1", orderId = "eats_order_1", buyerId = "buyer_1", restaurantId = "restaurant_1",
            riderId = "rider_1", restaurantRating = 5, restaurantComment = "Great!", riderRating = 5, riderComment = null,
        )

        When("the real owner replies") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant
            every { eatsReviewRepository.findById("eats_review_1") } returns Optional.of(review)
            every { eatsReviewRepository.save(any()) } answers { firstArg() }
            every { notificationRepository.save(any()) } answers { firstArg() }

            val result = service.replyToRestaurantReview("owner_1", "eats_review_1", "  Thank you for visiting!  ")

            Then("it real-trims and saves the reply with a timestamp") {
                result.ownerReply shouldBe "Thank you for visiting!"
                (result.ownerRepliedAt != null) shouldBe true
            }

            Then("it real-notifies the real reviewing buyer") {
                verify(exactly = 1) { notificationRepository.save(match { it.userId == "buyer_1" && it.type == "EATS_REVIEW_REPLY" }) }
            }
        }

        When("someone who isn't the real restaurant owner tries to reply") {
            every { merchantRepository.findByOwnerUserId("stranger") } returns null

            Then("it throws RestaurantNotFoundException") {
                try {
                    service.replyToRestaurantReview("stranger", "eats_review_1", "hi")
                    error("expected RestaurantNotFoundException")
                } catch (e: RestaurantNotFoundException) {
                    // expected
                }
            }
        }

        When("a different restaurant's owner tries to reply to this review") {
            val otherRestaurant = Merchant(id = "restaurant_2", ownerUserId = "owner_2", walletId = "wallet_2", businessName = "Other Place", status = MerchantStatus.ACTIVE)
            every { merchantRepository.findByOwnerUserId("owner_2") } returns otherRestaurant
            every { eatsReviewRepository.findById("eats_review_1") } returns Optional.of(review)

            Then("it throws EatsReviewNotFoundException, not a 403 that would confirm the review exists") {
                try {
                    service.replyToRestaurantReview("owner_2", "eats_review_1", "hi")
                    error("expected EatsReviewNotFoundException")
                } catch (e: EatsReviewNotFoundException) {
                    // expected
                }
            }
        }

        When("replying with an empty string") {
            every { merchantRepository.findByOwnerUserId("owner_1") } returns restaurant

            Then("it throws InvalidEatsReviewReplyException before ever touching the review") {
                try {
                    service.replyToRestaurantReview("owner_1", "eats_review_1", "   ")
                    error("expected InvalidEatsReviewReplyException")
                } catch (e: InvalidEatsReviewReplyException) {
                    // expected
                }
            }
        }
    }

    Given("a real Baemin-style PICKUP order delivered with no rider ever assigned") {
        val eatsOrderRepository = mockk<EatsOrderRepository>()
        val eatsReviewRepository = mockk<EatsReviewRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val notificationRepository = mockk<NotificationRepository>(relaxed = true)
        val pushNotificationService = mockk<PushNotificationService>(relaxed = true)
        val service = EatsReviewService(eatsOrderRepository, eatsReviewRepository, merchantRepository, notificationRepository, pushNotificationService)

        val deliveredPickupOrder = EatsOrder(
            id = "eats_order_pickup_1", buyerId = "buyer_1", restaurantId = "restaurant_1", riderId = null, deliveryAddress = "Pickup at Diner",
            itemsSubtotal = BigDecimal("4000"), deliveryFee = BigDecimal.ZERO, platformFee = BigDecimal("60"),
            totalAmount = BigDecimal("4000"), transactionId = "ledgertxn_pickup_1", status = EatsOrderStatus.DELIVERED,
            fulfillmentType = EatsFulfillmentType.PICKUP,
        )

        When("the real buyer reviews it with no rider rating at all -- the real bug this fixes") {
            every { eatsOrderRepository.findById("eats_order_pickup_1") } returns Optional.of(deliveredPickupOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_pickup_1") } returns null
            every { eatsReviewRepository.save(any()) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_pickup_1", 5, "Great pickup experience!", null, null)

            Then("it real-succeeds with a null riderId/riderRating -- previously this real-threw for every PICKUP order") {
                review.riderId shouldBe null
                review.riderRating shouldBe null
                review.riderComment shouldBe null
                review.restaurantRating shouldBe 5
            }
        }

        When("a caller passes a rider rating anyway for a PICKUP order with no real rider") {
            every { eatsOrderRepository.findById("eats_order_pickup_1") } returns Optional.of(deliveredPickupOrder)
            every { eatsReviewRepository.findByOrderId("eats_order_pickup_1") } returns null
            every { eatsReviewRepository.save(any()) } answers { firstArg() }

            val review = service.submitReview("buyer_1", "eats_order_pickup_1", 4, null, 3, "some comment")

            Then("it real-discards the rider rating/comment -- there is no real rider to attribute it to") {
                review.riderRating shouldBe null
                review.riderComment shouldBe null
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
