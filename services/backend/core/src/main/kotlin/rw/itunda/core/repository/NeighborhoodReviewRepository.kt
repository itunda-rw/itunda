package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.NeighborhoodReview

interface NeighborhoodReviewRepository : JpaRepository<NeighborhoodReview, String> {
    fun findByNeighborhoodOrderByCreatedAtDesc(neighborhood: String): List<NeighborhoodReview>
    fun existsByUserIdAndNeighborhood(userId: String, neighborhood: String): Boolean
}
