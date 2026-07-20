package rw.itunda.marketplace

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.ListingFavorite
import rw.itunda.core.repository.ListingFavoriteRepository
import rw.itunda.core.repository.ListingRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class FavoriteListingNotFoundException(message: String) : RuntimeException(message)

data class FavoriteListing(
    val listingId: String,
    val title: String,
    val price: BigDecimal,
    val category: String,
    val favoritedAt: Instant,
)

/**
 * Real Marketplace listing wishlist -- the real 관심목록 every real Karrot-style
 * secondhand marketplace has, closing a gap `docs/DESIGN_REFERENCES.md`'s Hood research
 * named directly: itunda already had this exact pattern for Shop products
 * (`ProductFavoriteService`) and Eats restaurants (`EatsFavoriteService`) but not Hood
 * listings. Mirrors `ProductFavoriteService`'s exact shape and idempotency discipline.
 *
 * `addFavorite` is deliberately idempotent (favoriting an already-favorited listing
 * just returns the existing row rather than a 409); `removeFavorite` is a silent no-op
 * for something that was never favorited -- same reasoning `ProductFavoriteService`/
 * `EatsFavoriteService` already established: the end state ("not favorited") is what
 * the caller actually wants, regardless of what state it started in.
 */
@Service
class ListingFavoriteService(
    private val listingFavoriteRepository: ListingFavoriteRepository,
    private val listingRepository: ListingRepository,
) {
    @Transactional
    fun addFavorite(userId: String, listingId: String): ListingFavorite {
        listingRepository.findById(listingId).orElseThrow { FavoriteListingNotFoundException("Listing not found") }
        listingFavoriteRepository.findByUserIdAndListingId(userId, listingId)?.let { return it }
        return listingFavoriteRepository.save(
            ListingFavorite(id = "listing_favorite_${UUID.randomUUID()}", userId = userId, listingId = listingId),
        )
    }

    @Transactional
    fun removeFavorite(userId: String, listingId: String) {
        listingFavoriteRepository.deleteByUserIdAndListingId(userId, listingId)
    }

    // Real batch-resolve of listing info via one findAllById call, the same N+1-avoiding
    // shape ProductFavoriteService.getMyFavorites/EatsFavoriteService.getMyFavorites
    // already established.
    fun getMyFavorites(userId: String, pageable: Pageable): Page<FavoriteListing> {
        val page = listingFavoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        val listingsById = listingRepository.findAllById(page.content.map { it.listingId }).associateBy { it.id }
        return page.map { favorite ->
            val listing = listingsById[favorite.listingId]
            FavoriteListing(
                listingId = favorite.listingId,
                title = listing?.title ?: "Listing no longer available",
                price = listing?.price ?: BigDecimal.ZERO,
                category = listing?.category ?: "",
                favoritedAt = favorite.createdAt,
            )
        }
    }
}
