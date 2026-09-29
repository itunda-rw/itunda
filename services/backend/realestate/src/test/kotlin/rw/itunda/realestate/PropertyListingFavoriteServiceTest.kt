package rw.itunda.realestate

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import rw.itunda.core.domain.PropertyListing
import rw.itunda.core.domain.PropertyListingFavorite
import rw.itunda.core.domain.PropertyListingType
import rw.itunda.core.repository.PropertyListingFavoriteRepository
import rw.itunda.core.repository.PropertyListingRepository
import java.math.BigDecimal
import java.util.Optional

class PropertyListingFavoriteServiceTest : BehaviorSpec({

    Given("a real property listing on the board") {
        val propertyListingFavoriteRepository = mockk<PropertyListingFavoriteRepository>()
        val propertyListingRepository = mockk<PropertyListingRepository>()
        val service = PropertyListingFavoriteService(propertyListingFavoriteRepository, propertyListingRepository)

        val listing = PropertyListing(
            id = "property_listing_1", listerId = "lister_1", listingType = PropertyListingType.RENT,
            propertyType = "apartment", title = "Real 2-bedroom near Kimironko", description = "Bright, quiet",
            price = BigDecimal("180000"),
        )

        When("favoriting it for the real first time") {
            every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(listing)
            every { propertyListingFavoriteRepository.findByUserIdAndPropertyListingId("renter_1", "property_listing_1") } returns null
            val savedSlot = slot<PropertyListingFavorite>()
            every { propertyListingFavoriteRepository.save(capture(savedSlot)) } answers { firstArg() }

            val favorite = service.addFavorite("renter_1", "property_listing_1")

            Then("it persists a real new favorite") {
                favorite.userId shouldBe "renter_1"
                favorite.propertyListingId shouldBe "property_listing_1"
                savedSlot.captured.propertyListingId shouldBe "property_listing_1"
            }
        }

        When("favoriting an already-favorited property listing") {
            every { propertyListingRepository.findById("property_listing_1") } returns Optional.of(listing)
            val existing = PropertyListingFavorite(id = "property_listing_favorite_1", userId = "renter_1", propertyListingId = "property_listing_1")
            every { propertyListingFavoriteRepository.findByUserIdAndPropertyListingId("renter_1", "property_listing_1") } returns existing

            val favorite = service.addFavorite("renter_1", "property_listing_1")

            Then("it idempotently returns the real existing favorite, never a duplicate") {
                favorite shouldBe existing
                verify(exactly = 0) { propertyListingFavoriteRepository.save(any()) }
            }
        }

        When("favoriting a property listing that doesn't exist") {
            every { propertyListingRepository.findById("ghost") } returns Optional.empty()

            Then("it throws FavoritePropertyListingNotFoundException") {
                try {
                    service.addFavorite("renter_1", "ghost")
                    error("expected FavoritePropertyListingNotFoundException")
                } catch (e: FavoritePropertyListingNotFoundException) {
                    // expected
                }
            }
        }

        When("un-favoriting a property listing that was never favorited") {
            every { propertyListingFavoriteRepository.deleteByUserIdAndPropertyListingId("renter_1", "never_favorited") } returns 0L

            Then("it silently no-ops rather than throwing") {
                service.removeFavorite("renter_1", "never_favorited")
                verify { propertyListingFavoriteRepository.deleteByUserIdAndPropertyListingId("renter_1", "never_favorited") }
            }
        }

        When("listing a real renter's favorites") {
            val favorite = PropertyListingFavorite(id = "property_listing_favorite_1", userId = "renter_1", propertyListingId = "property_listing_1")
            every { propertyListingFavoriteRepository.findByUserIdOrderByCreatedAtDesc("renter_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 20), 1)
            every { propertyListingRepository.findAllById(listOf("property_listing_1")) } returns listOf(listing)

            val page = service.getMyFavorites("renter_1", PageRequest.of(0, 20))

            Then("it resolves the real property listing's current title/price/type") {
                page.content.single().propertyListingId shouldBe "property_listing_1"
                page.content.single().title shouldBe "Real 2-bedroom near Kimironko"
                page.content.single().price shouldBe BigDecimal("180000")
                page.content.single().propertyType shouldBe "apartment"
            }
        }

        When("a favorited property listing no longer exists") {
            val favorite = PropertyListingFavorite(id = "property_listing_favorite_2", userId = "renter_1", propertyListingId = "deleted_listing")
            every { propertyListingFavoriteRepository.findByUserIdOrderByCreatedAtDesc("renter_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(favorite), PageRequest.of(0, 20), 1)
            every { propertyListingRepository.findAllById(listOf("deleted_listing")) } returns emptyList()

            val page = service.getMyFavorites("renter_1", PageRequest.of(0, 20))

            Then("it falls back to an honest placeholder rather than crashing") {
                page.content.single().title shouldBe "Property listing no longer available"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
