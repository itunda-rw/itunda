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
}
