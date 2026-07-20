package rw.itunda.marketplace

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingFavorite
import rw.itunda.core.repository.ListingFavoriteRepository
import rw.itunda.core.repository.ListingRepository
import java.math.BigDecimal
import java.util.Optional

class ListingFavoriteServiceTest : BehaviorSpec({

    Given("a real listing in the Marketplace") {
        val listingFavoriteRepository = mockk<ListingFavoriteRepository>()
        val listingRepository = mockk<ListingRepository>()
        val service = ListingFavoriteService(listingFavoriteRepository, listingRepository)

        val listing = Listing(
            id = "listing_1", sellerId = "seller_1", title = "Real bicycle", description = "Barely used",
            price = BigDecimal("45000"), category = "sports",
        )

        When("favoriting it for the real first time") {
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            every { listingFavoriteRepository.findByUserIdAndListingId("buyer_1", "listing_1") } returns null
            val savedSlot = slot<ListingFavorite>()
            every { listingFavoriteRepository.save(capture(savedSlot)) } answers { firstArg() }

            val favorite = service.addFavorite("buyer_1", "listing_1")

            Then("it persists a real new favorite") {
                favorite.userId shouldBe "buyer_1"
                favorite.listingId shouldBe "listing_1"
                savedSlot.captured.listingId shouldBe "listing_1"
            }
        }

        When("favoriting an already-favorited listing") {
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            val existing = ListingFavorite(id = "listing_favorite_1", userId = "buyer_1", listingId = "listing_1")
            every { listingFavoriteRepository.findByUserIdAndListingId("buyer_1", "listing_1") } returns existing

            val favorite = service.addFavorite("buyer_1", "listing_1")

            Then("it idempotently returns the real existing favorite, never a duplicate") {
                favorite shouldBe existing
                verify(exactly = 0) { listingFavoriteRepository.save(any()) }
            }
        }

        When("favoriting a listing that doesn't exist") {
            every { listingRepository.findById("ghost") } returns Optional.empty()

            Then("it throws FavoriteListingNotFoundException") {
                try {
                    service.addFavorite("buyer_1", "ghost")
                    error("expected FavoriteListingNotFoundException")
                } catch (e: FavoriteListingNotFoundException) {
                    // expected
                }
            }
        }

        When("un-favoriting a listing that was never favorited") {
            every { listingFavoriteRepository.deleteByUserIdAndListingId("buyer_1", "never_favorited") } returns 0L

            Then("it silently no-ops rather than throwing") {
                service.removeFavorite("buyer_1", "never_favorited")
                verify { listingFavoriteRepository.deleteByUserIdAndListingId("buyer_1", "never_favorited") }
            }
        }

        When("listing a real buyer's favorites") {
            val favorite = ListingFavorite(id = "listing_favorite_1", userId = "buyer_1", listingId = "listing_1")
            every { listingFavoriteRepository.findByUserIdOrderByCreatedAtDesc("buyer_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 20), 1)
            every { listingRepository.findAllById(listOf("listing_1")) } returns listOf(listing)

            val page = service.getMyFavorites("buyer_1", PageRequest.of(0, 20))

            Then("it resolves the real listing's current title/price/category") {
                page.content.single().listingId shouldBe "listing_1"
                page.content.single().title shouldBe "Real bicycle"
                page.content.single().price shouldBe BigDecimal("45000")
                page.content.single().category shouldBe "sports"
            }
        }

        When("a favorited listing no longer exists") {
            val favorite = ListingFavorite(id = "listing_favorite_2", userId = "buyer_1", listingId = "deleted_listing")
            every { listingFavoriteRepository.findByUserIdOrderByCreatedAtDesc("buyer_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 20), 1)
            every { listingRepository.findAllById(listOf("deleted_listing")) } returns emptyList()

            val page = service.getMyFavorites("buyer_1", PageRequest.of(0, 20))

            Then("it falls back to an honest placeholder rather than crashing") {
                page.content.single().title shouldBe "Listing no longer available"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
