package rw.itunda.marketplace

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
    }
}) {
    override fun isolationMode() = IsolationMode.InstancePerLeaf
}
