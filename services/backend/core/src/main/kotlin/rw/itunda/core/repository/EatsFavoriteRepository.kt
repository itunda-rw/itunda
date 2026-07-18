package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.EatsFavorite

interface EatsFavoriteRepository : JpaRepository<EatsFavorite, String> {
    fun findByUserIdAndRestaurantId(userId: String, restaurantId: String): EatsFavorite?

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<EatsFavorite>

    fun deleteByUserIdAndRestaurantId(userId: String, restaurantId: String): Long
}
