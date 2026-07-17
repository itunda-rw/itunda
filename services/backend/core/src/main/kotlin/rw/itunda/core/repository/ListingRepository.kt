package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.Listing
import rw.itunda.core.domain.ListingStatus

interface ListingRepository : JpaRepository<Listing, String> {
    // Real pagination from day one (see MessagingRepositories.kt's own note on why --
    // this session's earlier Partner SDK finding: retrofitting pagination onto an
    // already-shipped unbounded endpoint is real, avoidable extra work).
    fun findByStatusOrderByCreatedAtDesc(status: ListingStatus, pageable: Pageable): Page<Listing>
    fun findByStatusAndCategoryOrderByCreatedAtDesc(status: ListingStatus, category: String, pageable: Pageable): Page<Listing>
    fun findBySellerIdOrderByCreatedAtDesc(sellerId: String, pageable: Pageable): Page<Listing>
}
