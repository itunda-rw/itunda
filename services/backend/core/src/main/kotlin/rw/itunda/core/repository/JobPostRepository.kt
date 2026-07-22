package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.JobPost
import rw.itunda.core.domain.JobPostStatus

interface JobPostRepository : JpaRepository<JobPost, String> {
    fun findByStatusOrderByCreatedAtDesc(status: JobPostStatus, pageable: Pageable): Page<JobPost>
    fun findByStatusAndCategoryOrderByCreatedAtDesc(status: JobPostStatus, category: String, pageable: Pageable): Page<JobPost>
    fun findByPosterIdOrderByCreatedAtDesc(posterId: String, pageable: Pageable): Page<JobPost>

    // Real proximity "near me" browse -- same bounded-candidate-set-then-Haversine-in-app
    // shape ListingRepository/CommunityPostRepository's own notes already established.
    fun findByStatusAndLatitudeIsNotNullAndLongitudeIsNotNull(status: JobPostStatus): List<JobPost>

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see JobPostService.
    // myNeighborhood and User.neighborhood's own doc comments.
    fun findByStatusAndNeighborhoodOrderByCreatedAtDesc(status: JobPostStatus, neighborhood: String, pageable: Pageable): Page<JobPost>
    fun findByStatusAndNeighborhoodAndCategoryOrderByCreatedAtDesc(
        status: JobPostStatus,
        neighborhood: String,
        category: String,
        pageable: Pageable,
    ): Page<JobPost>

    // Real Karrot-Score-style trust badge input (2026-07-21) -- see
    // rw.itunda.core.trust.TrustScoreService's own doc comment; identical shape to
    // ListingRepository.countBySellerIdAndStatus.
    fun countByPosterIdAndStatus(posterId: String, status: JobPostStatus): Long
}
