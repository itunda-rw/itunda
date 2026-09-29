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

    // Real gap closed 2026-09-07 (Identity product-completeness pass) -- documentNumber
    // has no unique DB constraint, so nothing anywhere previously checked whether a
    // document number was already verified under a DIFFERENT account before approving a
    // new submission for it. Used at decide()-approval time, not submit() (a PENDING
    // submission can't yet cause harm, and rejecting at submit would leak "this document
    // is already registered" to a stranger for no real benefit).
    fun findByDocumentTypeAndDocumentNumberAndStatus(documentType: String, documentNumber: String, status: String): List<KycSubmission>
}
