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
import rw.itunda.core.domain.ListingHide
import rw.itunda.core.repository.ListingHideRepository
import rw.itunda.core.repository.ListingRepository
import java.math.BigDecimal
import java.util.Optional

// Mirrors ListingFavoriteServiceTest's exact shape -- see ListingHideService's own doc
// comment for the full sourced Karrot account.
class ListingHideServiceTest : BehaviorSpec({

    Given("a real listing in the Marketplace") {
        val listingHideRepository = mockk<ListingHideRepository>()
        val listingRepository = mockk<ListingRepository>()
        val service = ListingHideService(listingHideRepository, listingRepository)

        val listing = Listing(
            id = "listing_1", sellerId = "seller_1", title = "Real bicycle", description = "Barely used",
            price = BigDecimal("45000"), category = "sports",
        )

        When("hiding it for the real first time") {
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            every { listingHideRepository.findByUserIdAndListingId("viewer_1", "listing_1") } returns null
            val savedSlot = slot<ListingHide>()
            every { listingHideRepository.save(capture(savedSlot)) } answers { firstArg() }

            val hide = service.hideListing("viewer_1", "listing_1")

            Then("it persists a real new hide") {
                hide.userId shouldBe "viewer_1"
                hide.listingId shouldBe "listing_1"
                savedSlot.captured.listingId shouldBe "listing_1"
            }
        }

        When("hiding an already-hidden listing") {
            every { listingRepository.findById("listing_1") } returns Optional.of(listing)
            val existing = ListingHide(id = "listing_hide_1", userId = "viewer_1", listingId = "listing_1")
            every { listingHideRepository.findByUserIdAndListingId("viewer_1", "listing_1") } returns existing

            val hide = service.hideListing("viewer_1", "listing_1")

            Then("it idempotently returns the real existing hide, never a duplicate") {
                hide shouldBe existing
                verify(exactly = 0) { listingHideRepository.save(any()) }
            }
        }

        When("hiding a listing that doesn't exist") {
            every { listingRepository.findById("ghost") } returns Optional.empty()

            Then("it throws HideListingNotFoundException") {
                try {
                    service.hideListing("viewer_1", "ghost")
                    error("expected HideListingNotFoundException")
                } catch (e: HideListingNotFoundException) {
                    // expected
                }
            }
        }

        When("un-hiding a listing that was never hidden") {
            every { listingHideRepository.deleteByUserIdAndListingId("viewer_1", "never_hidden") } returns 0L

            Then("it silently no-ops rather than throwing") {
                service.unhideListing("viewer_1", "never_hidden")
                verify { listingHideRepository.deleteByUserIdAndListingId("viewer_1", "never_hidden") }
            }
        }

        // Real "Hidden listings" list (Hood product-completeness pass, 2026-09-07) --
        // see ListingHideRepository.findByUserIdOrderByCreatedAtDesc's own doc comment.
        When("fetching the caller's own real hidden listings") {
            val hide = ListingHide(id = "listing_hide_1", userId = "viewer_1", listingId = "listing_1")
            every { listingHideRepository.findByUserIdOrderByCreatedAtDesc("viewer_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(hide), PageRequest.of(0, 20), 1)
            every { listingRepository.findAllById(listOf("listing_1")) } returns listOf(listing)

            val page = service.getMyHiddenListings("viewer_1", PageRequest.of(0, 20))

            Then("it resolves the real listing's current title/price/category") {
                page.content.single().listingId shouldBe "listing_1"
                page.content.single().title shouldBe "Real bicycle"
                page.content.single().price shouldBe BigDecimal("45000")
                page.content.single().category shouldBe "sports"
            }
        }

        When("a hidden listing no longer exists") {
            val hide = ListingHide(id = "listing_hide_2", userId = "viewer_1", listingId = "deleted_listing")
            every { listingHideRepository.findByUserIdOrderByCreatedAtDesc("viewer_1", PageRequest.of(0, 20)) } returns
                PageImpl(listOf(hide), PageRequest.of(0, 20), 1)
            every { listingRepository.findAllById(listOf("deleted_listing")) } returns emptyList()

            val page = service.getMyHiddenListings("viewer_1", PageRequest.of(0, 20))

            Then("it falls back to an honest placeholder rather than crashing") {
                page.content.single().title shouldBe "Listing no longer available"
            }
        }
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
