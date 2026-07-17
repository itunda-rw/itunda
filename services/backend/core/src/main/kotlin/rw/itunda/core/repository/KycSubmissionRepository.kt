package rw.itunda.core.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.KycSubmission

interface KycSubmissionRepository : JpaRepository<KycSubmission, String> {
    fun findByUserIdOrderBySubmittedAtDesc(userId: String): List<KycSubmission>
    // Paginated -- see PageResponse.kt's doc comment; the KYC/KYB review queue is an
    // unbounded admin list with the same shape as the partner mini-app queue.
    fun findByStatusOrderBySubmittedAtAsc(status: String, pageable: Pageable): Page<KycSubmission>
}
