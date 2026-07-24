package rw.itunda.core.repository

import org.springframework.data.jpa.repository.JpaRepository
import rw.itunda.core.domain.AutoTransfer
import rw.itunda.core.domain.AutoTransferStatus
import java.time.Instant

interface AutoTransferRepository : JpaRepository<AutoTransfer, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<AutoTransfer>
    fun findByIdAndUserId(id: String, userId: String): AutoTransfer?

    // Real due-query, same shape as SavingsService.getGoalsDueForAutoContribution --
    // a fixed-delay poll re-checks this often, so "due" is a plain <= now comparison,
    // not a tight window.
    fun findByStatusAndNextExecutionAtLessThanEqual(status: AutoTransferStatus, now: Instant): List<AutoTransfer>
}
