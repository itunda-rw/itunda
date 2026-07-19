package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.CommunityComment

interface CommunityCommentRepository : JpaRepository<CommunityComment, String> {
    fun findByPostIdOrderByCreatedAtAsc(postId: String, pageable: Pageable): Page<CommunityComment>
}
