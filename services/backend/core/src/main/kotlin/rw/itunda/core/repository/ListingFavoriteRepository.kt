package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.ListingFavorite

interface ListingFavoriteRepository : JpaRepository<ListingFavorite, String> {
    fun findByUserIdAndListingId(userId: String, listingId: String): ListingFavorite?

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<ListingFavorite>

    fun deleteByUserIdAndListingId(userId: String, listingId: String): Long

    // Real Karrot 가격 하락 알림 (price-drop alert on a favorited/관심 listing) -- see
    // MarketplaceService.updatePrice's own doc comment for the real sourcing. Every
    // user who has favorited this listing, resolved in one query so the price-drop
    // fan-out never N+1s, same discipline PropertyListingFavoriteRepository
    // .findByPropertyListingId already establishes.
    fun findByListingId(listingId: String): List<ListingFavorite>
}
