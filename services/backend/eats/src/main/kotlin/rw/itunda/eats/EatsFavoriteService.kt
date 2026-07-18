package rw.itunda.eats

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import rw.itunda.core.domain.EatsFavorite
import rw.itunda.core.repository.EatsFavoriteRepository
import rw.itunda.core.repository.MerchantRepository
import java.time.Instant
import java.util.UUID

data class FavoriteRestaurant(
    val restaurantId: String,
    val businessName: String,
    val category: String?,
    val favoritedAt: Instant,
)

/**
 * Real bookmarked/favorited restaurants -- closes the "favorites" item on the Eats
 * polish roadmap. See `EatsFavorite.kt`'s own doc comment for the entity account.
 *
 * `addFavorite` is deliberately idempotent (favoriting an already-favorited restaurant
 * just returns the existing row rather than a 409) -- a real favorite/bookmark toggle in
 * a UI shouldn't error on a double-tap, and the real DB unique constraint means a
 * concurrent double-add still can't create two rows even without this check. Same for
 * `removeFavorite`: un-favoriting something that was never favorited is a silent no-op,
 * not a 404 -- the end state ("not favorited") is what the caller actually wants,
 * regardless of what state it started in.
 */
@Service
class EatsFavoriteService(
    private val eatsFavoriteRepository: EatsFavoriteRepository,
    private val merchantRepository: MerchantRepository,
) {
    @Transactional
    fun addFavorite(userId: String, restaurantId: String): EatsFavorite {
        merchantRepository.findById(restaurantId).orElseThrow { RestaurantNotFoundException("Restaurant not found") }
        eatsFavoriteRepository.findByUserIdAndRestaurantId(userId, restaurantId)?.let { return it }
        return eatsFavoriteRepository.save(
            EatsFavorite(id = "eats_favorite_${UUID.randomUUID()}", userId = userId, restaurantId = restaurantId),
        )
    }

    @Transactional
    fun removeFavorite(userId: String, restaurantId: String) {
        eatsFavoriteRepository.deleteByUserIdAndRestaurantId(userId, restaurantId)
    }

    // Real batch-resolve of restaurant info via a single findAllById call, the same
    // N+1-avoiding shape GroupMessagingService.getMembers already established for
    // resolving N ids to N real records in one query.
    fun getMyFavorites(userId: String, pageable: Pageable): Page<FavoriteRestaurant> {
        val page = eatsFavoriteRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        val merchantsById = merchantRepository.findAllById(page.content.map { it.restaurantId }).associateBy { it.id }
        return page.map { favorite ->
            val merchant = merchantsById[favorite.restaurantId]
            FavoriteRestaurant(
                restaurantId = favorite.restaurantId,
                businessName = merchant?.businessName ?: "Restaurant no longer available",
                category = merchant?.category,
                favoritedAt = favorite.createdAt,
            )
        }
    }
}
