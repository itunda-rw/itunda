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
}
