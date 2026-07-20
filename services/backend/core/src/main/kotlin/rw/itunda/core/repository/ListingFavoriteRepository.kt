package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ListingFavorite

interface ListingFavoriteRepository : JpaRepository<ListingFavorite, String> {
    fun findByUserIdAndListingId(userId: String, listingId: String): ListingFavorite?

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<ListingFavorite>

    fun deleteByUserIdAndListingId(userId: String, listingId: String): Long
}
