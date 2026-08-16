package rw.itunda.eats

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.EatsFavorite
import rw.itunda.core.domain.Merchant
import rw.itunda.core.domain.MerchantStatus
import rw.itunda.core.domain.Message
import rw.itunda.core.repository.EatsFavoriteRepository
import rw.itunda.core.repository.MerchantRepository
import rw.itunda.messaging.ConversationNotFoundException
import rw.itunda.messaging.MessagingService
import java.util.Optional

class EatsFavoriteServiceTest : BehaviorSpec({

    Given("a real registered restaurant") {
        val eatsFavoriteRepository = mockk<EatsFavoriteRepository>()
        val merchantRepository = mockk<MerchantRepository>()
        val messagingService = mockk<MessagingService>()
        val service = EatsFavoriteService(eatsFavoriteRepository, merchantRepository, messagingService)

        val restaurant = Merchant(
            id = "restaurant_1", ownerUserId = "owner_1", walletId = "wallet_1",
            businessName = "Kigali Coffee", status = MerchantStatus.ACTIVE, category = "Cafe",
        )

        When("favoriting it for the real first time") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            every { eatsFavoriteRepository.findByUserIdAndRestaurantId("buyer_1", "restaurant_1") } returns null
            val savedSlot = slot<EatsFavorite>()
            every { eatsFavoriteRepository.save(capture(savedSlot)) } answers { firstArg() }

            val favorite = service.addFavorite("buyer_1", "restaurant_1")

            Then("it persists a real new favorite") {
                favorite.userId shouldBe "buyer_1"
                favorite.restaurantId shouldBe "restaurant_1"
                savedSlot.captured.restaurantId shouldBe "restaurant_1"
            }
        }

        When("favoriting an already-favorited restaurant") {
            every { merchantRepository.findById("restaurant_1") } returns Optional.of(restaurant)
            val existing = EatsFavorite(id = "eats_favorite_1", userId = "buyer_1", restaurantId = "restaurant_1")
            every { eatsFavoriteRepository.findByUserIdAndRestaurantId("buyer_1", "restaurant_1") } returns existing

            val favorite = service.addFavorite("buyer_1", "restaurant_1")

            Then("it idempotently returns the real existing favorite, never a duplicate") {
                favorite shouldBe existing
                verify(exactly = 0) { eatsFavoriteRepository.save(any()) }
            }
        }

        When("favoriting a restaurant that doesn't exist") {
            every { merchantRepository.findById("ghost") } returns Optional.empty()

            Then("it throws RestaurantNotFoundException") {
                try {
                    service.addFavorite("buyer_1", "ghost")
                    error("expected RestaurantNotFoundException")
                } catch (e: RestaurantNotFoundException) {
                    // expected
                }
            }
        }

        When("un-favoriting a restaurant that was never favorited") {
            every { eatsFavoriteRepository.deleteByUserIdAndRestaurantId("buyer_1", "never_favorited") } returns 0L

            Then("it silently no-ops rather than throwing") {
                service.removeFavorite("buyer_1", "never_favorited")
                verify { eatsFavoriteRepository.deleteByUserIdAndRestaurantId("buyer_1", "never_favorited") }
            }
        }

        When("listing a real buyer's favorites") {
            val favorite = EatsFavorite(id = "eats_favorite_1", userId = "buyer_1", restaurantId = "restaurant_1")
            every { eatsFavoriteRepository.findByUserIdOrderByCreatedAtDesc("buyer_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 20), 1)
            every { merchantRepository.findAllById(listOf("restaurant_1")) } returns listOf(restaurant)

            val page = service.getMyFavorites("buyer_1", PageRequest.of(0, 20))

            Then("it resolves the real restaurant's current name and category") {
                page.content.single().restaurantId shouldBe "restaurant_1"
                page.content.single().businessName shouldBe "Kigali Coffee"
                page.content.single().category shouldBe "Cafe"
            }
        }

        When("a favorited restaurant no longer exists") {
            val favorite = EatsFavorite(id = "eats_favorite_2", userId = "buyer_1", restaurantId = "deleted_restaurant")
            every { eatsFavoriteRepository.findByUserIdOrderByCreatedAtDesc("buyer_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 20), 1)
            every { merchantRepository.findAllById(listOf("deleted_restaurant")) } returns emptyList()

            val page = service.getMyFavorites("buyer_1", PageRequest.of(0, 20))

            Then("it falls back to an honest placeholder rather than crashing") {
                page.content.single().businessName shouldBe "Restaurant no longer available"
            }
        }

        When("sharing a real favorites list into a real conversation the caller is a participant of") {
            val favorite = EatsFavorite(id = "eats_favorite_1", userId = "buyer_1", restaurantId = "restaurant_1")
            every { messagingService.getConversationForParticipant("buyer_1", "conv_1") } returns mockk()
            every { eatsFavoriteRepository.findByUserIdOrderByCreatedAtDesc("buyer_1", PageRequest.of(0, 5)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 5), 1)
            every { merchantRepository.findAllById(listOf("restaurant_1")) } returns listOf(restaurant)
            val sentMessage = mockk<Message>()
            val bodySlot = slot<String>()
            every { messagingService.sendMessage("buyer_1", "conv_1", capture(bodySlot)) } returns sentMessage

            val result = service.shareFavoritesToConversation("buyer_1", "conv_1")

            Then("it sends a real message naming the real favorited restaurant") {
                result shouldBe sentMessage
                bodySlot.captured shouldBe "\u2764\uFE0F My favorite restaurants:\n1. Kigali Coffee"
            }
        }

        When("sharing with zero favorites") {
            every { messagingService.getConversationForParticipant("buyer_2", "conv_2") } returns mockk()
            every { eatsFavoriteRepository.findByUserIdOrderByCreatedAtDesc("buyer_2", PageRequest.of(0, 5)) } returns
                PageImpl(emptyList(), PageRequest.of(0, 5), 0)
            every { merchantRepository.findAllById(emptyList()) } returns emptyList()

            Then("it throws NoFavoritesToShareException before ever sending a message") {
                try {
                    service.shareFavoritesToConversation("buyer_2", "conv_2")
                    error("expected NoFavoritesToShareException")
                } catch (e: NoFavoritesToShareException) {
                    verify(exactly = 0) { messagingService.sendMessage(any(), any(), any()) }
                }
            }
        }

        When("sharing into a conversation the caller is not a real participant of") {
            every { messagingService.getConversationForParticipant("stranger", "conv_3") } throws ConversationNotFoundException("Conversation not found")

            Then("it propagates the real IDOR-safe 404, never leaking favorites into a conversation the caller can't access") {
                try {
                    service.shareFavoritesToConversation("stranger", "conv_3")
                    error("expected ConversationNotFoundException")
                } catch (e: ConversationNotFoundException) {
                    verify(exactly = 0) { messagingService.sendMessage(any(), any(), any()) }
                }
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
