package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.PropertyListing
import rw.itunda.core.domain.PropertyListingStatus
import rw.itunda.core.domain.PropertyListingType

interface PropertyListingRepository : JpaRepository<PropertyListing, String> {
    fun findByStatusOrderByCreatedAtDesc(status: PropertyListingStatus, pageable: Pageable): Page<PropertyListing>
    fun findByStatusAndListingTypeOrderByCreatedAtDesc(
        status: PropertyListingStatus, listingType: PropertyListingType, pageable: Pageable,
    ): Page<PropertyListing>
    fun findByStatusAndPropertyTypeOrderByCreatedAtDesc(
        status: PropertyListingStatus, propertyType: String, pageable: Pageable,
    ): Page<PropertyListing>
    fun findByStatusAndListingTypeAndPropertyTypeOrderByCreatedAtDesc(
        status: PropertyListingStatus, listingType: PropertyListingType, propertyType: String, pageable: Pageable,
    ): Page<PropertyListing>
    fun findByListerIdOrderByCreatedAtDesc(listerId: String, pageable: Pageable): Page<PropertyListing>

    // Real proximity "near me" browse -- same bounded-candidate-set-then-Haversine-in-app
    // shape ListingRepository/CommunityPostRepository/JobPostRepository already established.
    fun findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(status: PropertyListingStatus): List<PropertyListing>

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see
    // PropertyListingService.myNeighborhood and User.neighborhood's own doc comments.
    // Deliberately not combined with listingType/propertyType (unlike browse()'s four
    // variants) -- an honest v1 scoping choice, named here not silently dropped, matching
    // this project's own "one new capability per turn" precedent.
    fun findByStatusAndNeighborhoodOrderByCreatedAtDesc(status: PropertyListingStatus, neighborhood: String, pageable: Pageable): Page<PropertyListing>

    // Real Karrot-Score-style trust badge input (2026-07-21) -- see
    // rw.itunda.core.trust.TrustScoreService's own doc comment; identical shape to
    // ListingRepository.countBySellerIdAndStatus.
    fun countByListerIdAndStatus(listerId: String, status: PropertyListingStatus): Long
}
