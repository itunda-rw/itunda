package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.JobPostFavorite

interface JobPostFavoriteRepository : JpaRepository<JobPostFavorite, String> {
    fun findByUserIdAndJobPostId(userId: String, jobPostId: String): JobPostFavorite?

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<JobPostFavorite>

    fun deleteByUserIdAndJobPostId(userId: String, jobPostId: String): Long

    // Real 당근알바 job-closed notification (2026-08-17) -- see
    // JobPostFavoriteService.notifyFavoritersOfClosure's own doc comment. Every user who
    // favorited this job post, resolved in one query so the closure fan-out never N+1s,
    // same discipline EatsFavoriteRepository.findByRestaurantId already establishes.
    fun findByJobPostId(jobPostId: String): List<JobPostFavorite>
}
