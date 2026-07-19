package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.CommunityPost
import rw.itunda.core.domain.CommunityPostStatus

interface CommunityPostRepository : JpaRepository<CommunityPost, String> {
    fun findByStatusOrderByCreatedAtDesc(status: CommunityPostStatus, pageable: Pageable): Page<CommunityPost>
    fun findByStatusAndCategoryOrderByCreatedAtDesc(status: CommunityPostStatus, category: String, pageable: Pageable): Page<CommunityPost>
    fun findByAuthorIdOrderByCreatedAtDesc(authorId: String, pageable: Pageable): Page<CommunityPost>

    // Real proximity "near me" browse (see CommunityPost's own doc comment) -- same
    // bounded-candidate-set-then-Haversine-in-app shape ListingRepository's own note
    // already established, not a real geospatial DB index.
    fun findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(status: CommunityPostStatus): List<CommunityPost>
}
