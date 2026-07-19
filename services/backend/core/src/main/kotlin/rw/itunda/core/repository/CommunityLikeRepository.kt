package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.CommunityLike

interface CommunityLikeRepository : JpaRepository<CommunityLike, String> {
    fun findByPostIdAndUserId(postId: String, userId: String): CommunityLike?
    fun existsByPostIdAndUserId(postId: String, userId: String): Boolean
}
