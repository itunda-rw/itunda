package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import rw.itunda.core.domain.EatsFavorite

interface RestaurantFavoriteCountProjection {
    val restaurantId: String
    val count: Long
}

interface EatsFavoriteRepository : JpaRepository<EatsFavorite, String> {
    fun findByUserIdAndRestaurantId(userId: String, restaurantId: String): EatsFavorite?

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<EatsFavorite>

    fun deleteByUserIdAndRestaurantId(userId: String, restaurantId: String): Long

    // Real Baemin "찜한 가게" new-menu-item notification (2026-08-17) -- see
    // MerchantProductService.addProduct's own doc comment for the real sourcing. Every
    // user who has favorited this restaurant, resolved in one query so a new-menu-item
    // notification fan-out never N+1s.
    fun findByRestaurantId(restaurantId: String): List<EatsFavorite>

    // Real Baemin 찜 (favorites) count (2026-08-16) -- see ShoppingController's own use
    // of this. One GROUP BY query for a whole restaurant browse page, same real
    // "batch, don't N+1" discipline EatsReviewRepository.getRestaurantRatingSummaries
    // already established. A restaurant with zero favorites is simply absent from the
    // result -- the caller treats a missing id as count 0, never a fabricated row.
    @Query("SELECT f.restaurantId as restaurantId, COUNT(f) as count FROM EatsFavorite f WHERE f.restaurantId IN :restaurantIds GROUP BY f.restaurantId")
    fun getFavoriteCounts(@Param("restaurantIds") restaurantIds: List<String>): List<RestaurantFavoriteCountProjection>
}
