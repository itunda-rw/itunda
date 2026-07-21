package rw.itunda.realestate

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.PropertyListingFavorite
import rw.itunda.core.repository.PropertyListingFavoriteRepository
import rw.itunda.core.repository.PropertyListingRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class FavoritePropertyListingNotFoundException(message: String) : RuntimeException(message)

data class FavoritePropertyListing(
    val propertyListingId: String,
    val title: String,
    val price: BigDecimal,
    val propertyType: String,
    val favoritedAt: Instant,
)

/**
 * Real 당근부동산 property-listing wishlist -- closes the same
 * `docs/DESIGN_REFERENCES.md`-named Hood gap as `JobPostFavoriteService`: Marketplace
 * listings already got a real wishlist (2026-07-21, `ListingFavoriteService`) but
 * Property never did. Mirrors `ListingFavoriteService`'s exact shape and idempotency
 * discipline field-for-field.
 *
 * `addFavorite` is deliberately idempotent (favoriting an already-favorited listing
 * just returns the existing row rather than a 409); `removeFavorite` is a silent no-op
 * for something that was never favorited -- same reasoning `ListingFavoriteService`
 * already established.
 */
@Service
class PropertyListingFavoriteService(
    private val propertyListingFavoriteRepository: PropertyListingFavoriteRepository,
    private val propertyListingRepository: PropertyListingRepository,
) {
    @Transactional
    fun addFavorite(userId: String, propertyListingId: String): PropertyListingFavorite {
        propertyListingRepository.findById(propertyListingId).orElseThrow { FavoritePropertyListingNotFoundException("Property listing not found") }
        propertyListingFavoriteRepository.findByUserIdAndPropertyListingId(userId, propertyListingId)?.let { return it }
        return propertyListingFavoriteRepository.save(
            PropertyListingFavorite(id = "property_listing_favorite_${UUID.randomUUID()}", userId = userId, propertyListingId = propertyListingId),
        )
    }

    @Transactional
    fun removeFavorite(userId: String, propertyListingId: String) {
        propertyListingFavoriteRepository.deleteByUserIdAndPropertyListingId(userId, propertyListingId)
    }

    // Real batch-resolve of property-listing info via one findAllById call, the same
    // N+1-avoiding shape ListingFavoriteService.getMyFavorites already established.
    fun getMyFavorites(userId: String, pageable: Pageable): Page<FavoritePropertyListing> {
        val page = propertyListingFavoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        val listingsById = propertyListingRepository.findAllById(page.content.map { it.propertyListingId }).associateBy { it.id }
        return page.map { favorite ->
            val listing = listingsById[favorite.propertyListingId]
            FavoritePropertyListing(
                propertyListingId = favorite.propertyListingId,
                title = listing?.title ?: "Property listing no longer available",
                price = listing?.price ?: BigDecimal.ZERO,
                propertyType = listing?.propertyType ?: "",
                favoritedAt = favorite.createdAt,
            )
        }
    }
}
