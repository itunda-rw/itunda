package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.PropertyOwnershipSubmission

interface PropertyOwnershipSubmissionRepository : JpaRepository<PropertyOwnershipSubmission, String> {
    fun findByListingIdOrderBySubmittedAtDesc(listingId: String): List<PropertyOwnershipSubmission>
    fun findByStatusOrderBySubmittedAtAsc(status: String, pageable: Pageable): Page<PropertyOwnershipSubmission>
}
