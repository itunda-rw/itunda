package rw.itunda.marketplace

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.ListingHide
import rw.itunda.core.repository.ListingHideRepository
import rw.itunda.core.repository.ListingRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class HideListingNotFoundException(message: String) : RuntimeException(message)

// Real "Hidden listings" list (Hood product-completeness pass, 2026-09-07) -- see
// ListingHideRepository.findByUserIdOrderByCreatedAtDesc's own doc comment for why
// this exists now. Mirrors FavoriteListing's exact shape.
data class HiddenListing(
    val listingId: String,
    val title: String,
    val price: BigDecimal,
    val category: String,
    val hiddenAt: Instant,
)

// Real Karrot "이 글 숨기기" (hide this post) -- see careers.daangn.com's own real,
// sourced blog post on Karrot's feed product: hiding a listing is a real, named,
// primary Karrot feature (distinct from reporting -- reporting is for policy
// violations, hiding is a personal "not interested" signal), and their own real
// measured result was hide-usage falling 40% once negative feedback started actually
// suppressing similar unwanted content. itunda's own honest scope here is deliberately
// SMALLER than Karrot's: Karrot's fuller mechanism also trains a personalized ranking
// ML model on hides (a real ML platform itunda doesn't have) -- this ships the real,
// immediately useful, honestly-scoped piece (hide a listing, never see it again in
// browse) without fabricating a "smarter" ranking claim itunda can't back up. Mirrors
// ListingFavoriteService's exact shape and idempotency discipline (hide is idempotent,
// unhide is a silent no-op for something never hidden -- the end state is what the
// caller wants, regardless of starting state).
@Service
class ListingHideService(
    private val listingHideRepository: ListingHideRepository,
    private val listingRepository: ListingRepository,
) {
    @Transactional
    fun hideListing(userId: String, listingId: String): ListingHide {
        listingRepository.findById(listingId).orElseThrow { HideListingNotFoundException("Listing not found") }
        listingHideRepository.findByUserIdAndListingId(userId, listingId)?.let { return it }
        return listingHideRepository.save(
            ListingHide(id = "listing_hide_${UUID.randomUUID()}", userId = userId, listingId = listingId),
        )
    }

    @Transactional
    fun unhideListing(userId: String, listingId: String) {
        listingHideRepository.deleteByUserIdAndListingId(userId, listingId)
    }

    // Real batch-resolve of listing info via one findAllById call, the same N+1-avoiding
    // shape ListingFavoriteService.getMyFavorites already establishes.
    fun getMyHiddenListings(userId: String, pageable: Pageable): Page<HiddenListing> {
        val page = listingHideRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        val listingsById = listingRepository.findAllById(page.content.map { it.listingId }).associateBy { it.id }
        return page.map { hide ->
            val listing = listingsById[hide.listingId]
            HiddenListing(
                listingId = hide.listingId,
                title = listing?.title ?: "Listing no longer available",
                price = listing?.price ?: BigDecimal.ZERO,
                category = listing?.category ?: "",
                hiddenAt = hide.createdAt,
            )
        }
    }
}
