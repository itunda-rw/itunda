package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.KycSubmission

interface KycSubmissionRepository : JpaRepository<KycSubmission, String> {
    fun findByUserIdOrderBySubmittedAtDesc(userId: String): List<KycSubmission>
    fun findByStatusOrderBySubmittedAtAsc(status: String): List<KycSubmission>
}
