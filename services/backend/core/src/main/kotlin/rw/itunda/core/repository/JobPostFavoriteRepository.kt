package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.JobPostFavorite

interface JobPostFavoriteRepository : JpaRepository<JobPostFavorite, String> {
    fun findByUserIdAndJobPostId(userId: String, jobPostId: String): JobPostFavorite?

    fun findByUserIdOrderByCreatedAtDesc(userId: String, pageable: Pageable): Page<JobPostFavorite>

    fun deleteByUserIdAndJobPostId(userId: String, jobPostId: String): Long
}
