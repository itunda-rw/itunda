package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.PropertyListingFavorite

interface PropertyListingFavoriteRepository : JpaRepository<PropertyListingFavorite, String> {
    fun findByUserIdAndPropertyListingId(userId: String, propertyListingId: String): PropertyListingFavorite?

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<PropertyListingFavorite>

    fun deleteByUserIdAndPropertyListingId(userId: String, propertyListingId: String): Long
}
